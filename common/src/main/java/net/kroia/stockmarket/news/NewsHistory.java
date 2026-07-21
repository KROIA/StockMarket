package net.kroia.stockmarket.news;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Master-side capped chronological store of published {@link NewsRecord}s
 * (NewsEventSystem plan §3, task T-072; chunked disk layout added by task T-110).
 * <p>
 * The history's <b>public API is unchanged</b>: {@link #append} adds the newest record at
 * the end and prunes the oldest entries beyond {@link #getMaxEntries()}. Records are
 * appended by the production {@link NewsPublisher} ({@link ServerNewsPublisher}) in
 * publish order; the {@code newsUid}s are generated monotonically by the NewsPlugin so
 * the buffer is ordered oldest-first / ascending uid — this class only stores what it is
 * given and never renumbers.
 * <p>
 * <b>Storage layout (T-110):</b> internally the history is split into fixed-size chunks
 * of {@value NewsHistoryChunkStore#CHUNK_SIZE} records persisted under
 * {@code world/data/StockMarket/News/history/}. Each chunk has a companion sidecar
 * listing the picture hashes it references, so {@link #referencedPictureHashes()} can
 * union across all chunks without loading their record data. Older (non-newest) chunks
 * stay on disk after world load and are lazy-loaded through a small LRU when
 * {@link #getPage} paginates across the boundary. See {@link NewsHistoryChunkStore} for
 * the full disk-layout, migration and cap-enforcement contracts.
 * <p>
 * <b>Cap handling:</b> the cap originates from the news library's scheduler config
 * ({@code historyMaxEntries}, default {@value NewsEventLibrary.SchedulerConfig#DEFAULT_HISTORY_MAX_ENTRIES}
 * — raised from 500 to 1000 by T-110, ≈10 chunks). To keep this class decoupled from the
 * library, the cap is pushed in via {@link #setMaxEntries(int)} — the
 * {@link ServerNewsPublisher} reads it from the library and sets it before every append.
 * Cap changes (config reload) apply <b>lazily</b>: a shrunken cap does NOT re-prune
 * existing entries immediately, pruning happens on the next {@link #append}. Loading
 * never prunes either — the buffer was pruned when it was saved.
 * <p>
 * <b>Pagination convention</b> (the {@code NewsHistoryRequest} of T-073 codes against
 * {@link #getPage(long, int)} exactly as documented there): pages are newest-first,
 * keyed by {@code newsUid} strictly less than the {@code beforeUid} cursor;
 * {@code beforeUid <= 0} and {@link Long#MAX_VALUE} both mean "start at the newest
 * record". Pagination now spans chunk boundaries transparently — the {@code getPage}
 * contract is preserved verbatim.
 * <p>
 * <b>Testing:</b> {@link #save(CompoundTag)} / {@link #load(CompoundTag)} continue to
 * work as an in-memory flat snapshot for unit tests that never call
 * {@link #setDirectory(Path)}. When a directory IS wired, they still round-trip via
 * {@link NewsHistoryChunkStore#allRecordsOldestFirst}/{@link NewsHistoryChunkStore#restoreFromList}
 * so live-world snapshots (e.g. the {@code NewsHistoryRequestTestSuite}) remain consistent.
 * <p>
 * Not thread-safe: like the rest of the market/plugin state, call it from the server
 * thread only (publishes come from the plugin update loop, request handlers run on the
 * main thread as well).
 */
public class NewsHistory implements ServerSaveable {

    /** Lower clamp of {@link #getPage}'s {@code maxResults} parameter. */
    public static final int MIN_PAGE_SIZE = 1;
    /** Upper clamp of {@link #getPage}'s {@code maxResults} parameter (bounds response payloads). */
    public static final int MAX_PAGE_SIZE = 100;

    // ── NBT keys ─────────────────────────────────────────────────────────

    /** Root list of records in the in-memory {@link #save(CompoundTag)} snapshot. */
    private static final String KEY_RECORDS = "records";

    // ── State ────────────────────────────────────────────────────────────

    /** Chunked disk-layout backend. Always non-null — a fresh instance is detached. */
    private final NewsHistoryChunkStore chunkStore = new NewsHistoryChunkStore();

    /**
     * Current entry cap. Defaults to the scheduler-config default so a history that is
     * loaded before the news library exists is still bounded.
     */
    private int maxEntries = NewsEventLibrary.SchedulerConfig.DEFAULT_HISTORY_MAX_ENTRIES;

    // ── Directory wiring (T-110) ─────────────────────────────────────────

    /**
     * Wires the chunk directory (typically {@code world/data/StockMarket/News/history})
     * and triggers an initial load of the on-disk state — migrating the pre-T-110 single
     * {@code history.nbt} file when applicable (see {@link NewsHistoryChunkStore} for
     * the migration contract).
     * <p>
     * <b>Idempotent:</b> a subsequent call with the same directory is a no-op — the
     * in-memory state is not reloaded (that would clobber records appended since the
     * first call). This matches how {@code DataManager} re-invokes this method on
     * autosave/server-stop after {@link #append} has been mutating the store.
     *
     * @param directory      the {@code history/} directory
     * @param oldSingleFile  the pre-T-110 {@code history.nbt} path used for migration
     *                       (may be null to disable migration in test contexts)
     */
    public void setDirectory(@NotNull Path directory, @Nullable Path oldSingleFile) {
        Path existing = chunkStore.getDirectory();
        if (existing != null && existing.equals(directory)) return;
        chunkStore.setDirectory(directory);
        chunkStore.loadFromDirectory(oldSingleFile);
    }

    /** @return the {@code history/} directory, or null when detached (test contexts) */
    public @Nullable Path getDirectory() {
        return chunkStore.getDirectory();
    }

    /** @return the chunk-store helper — for tests only (do not use in production paths) */
    public NewsHistoryChunkStore getChunkStoreForTests() {
        return chunkStore;
    }

    // ── Cap ──────────────────────────────────────────────────────────────

    /**
     * Sets the entry cap (from the library's {@code historyMaxEntries} scheduler config).
     * <p>
     * Applied <b>lazily</b>: a cap smaller than the current size does not prune anything
     * now — the excess entries are pruned on the next {@link #append}. This keeps a pure
     * config reload side-effect free.
     *
     * @param maxEntries the new cap; values &lt; 1 are clamped to 1
     */
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** @return the current entry cap (see {@link #setMaxEntries}) */
    public int getMaxEntries() {
        return maxEntries;
    }

    // ── Mutation ─────────────────────────────────────────────────────────

    /**
     * Appends one published record as the newest entry and prunes the oldest chunk
     * files while the total record count exceeds {@link #getMaxEntries()}.
     * <p>
     * The caller must append records in publish order (ascending {@code newsUid} — the
     * NewsPlugin's uid counter is monotonic and persisted), because {@link #getPage}
     * treats the buffer order as the chronological order.
     * <p>
     * <b>Cap-drop granularity (T-110):</b> when the total crosses the cap, the entire
     * oldest chunk file (up to {@value NewsHistoryChunkStore#CHUNK_SIZE} records) is
     * dropped atomically — no partial-chunk rewrite. When the cap is not a multiple
     * of {@value NewsHistoryChunkStore#CHUNK_SIZE}, retained count fluctuates slightly
     * (documented caveat in {@code configuration.md}).
     *
     * @param record the freshly published record; null is ignored
     */
    public void append(@Nullable NewsRecord record) {
        chunkStore.append(record, maxEntries);
    }

    /** Removes all stored records (the cap is kept). Also deletes on-disk chunk files. */
    public void clear() {
        chunkStore.clear();
    }

    // ── Queries ──────────────────────────────────────────────────────────

    /** @return the number of stored records (sum across all chunks) */
    public int size() {
        return chunkStore.size();
    }

    /** @return true if no records are stored */
    public boolean isEmpty() {
        return chunkStore.isEmpty();
    }

    /** @return the {@code newsUid} of the newest stored record, or 0 if the history is empty */
    public long getNewestUid() {
        return chunkStore.getNewestUid();
    }

    /**
     * Collects the picture hashes of all stored records (T-088, T-110) — the reference
     * set that drives the {@link NewsPictureStore} garbage collection
     * ({@code retainOnly}): a stored picture survives exactly as long as at least one
     * history record still points at it.
     * <p>
     * <b>T-110 implementation:</b> the union is computed from per-chunk sidecar files
     * (small: record-count + uid bounds + hash list) — no full chunk record data is
     * loaded. Corrupt/missing sidecars are recovered by loading the chunk once during
     * {@code loadFromDirectory} and rebuilding the sidecar (WARN log + skip).
     *
     * @return the non-null 20-byte SHA-1 hashes of every referenced picture
     *         (deduplicated, possibly empty, never null)
     */
    public @NotNull List<byte[]> referencedPictureHashes() {
        return chunkStore.referencedPictureHashes();
    }

    /**
     * Returns one page of history for the paginated {@code NewsHistoryRequest} (T-073).
     * <p>
     * <b>Pagination convention (verbatim contract):</b>
     * <ul>
     *   <li>The result is ordered <b>newest-first</b> (descending {@code newsUid}).</li>
     *   <li>Only records with {@code newsUid} <b>strictly less than</b> {@code beforeUid}
     *       are returned.</li>
     *   <li>{@code beforeUid <= 0} is treated as {@link Long#MAX_VALUE} — both mean
     *       "start at the newest record" (first page).</li>
     *   <li>{@code maxResults} is clamped to
     *       {@code [}{@value #MIN_PAGE_SIZE}{@code , }{@value #MAX_PAGE_SIZE}{@code ]};
     *       fewer records are returned when the history runs out.</li>
     *   <li><b>Next page:</b> pass the {@code newsUid} of the <i>last</i> (oldest) element
     *       of the previous page as the new {@code beforeUid}. An empty result means the
     *       end was reached.</li>
     *   <li>Never returns null; an empty history or an exhausted cursor yields an
     *       empty list.</li>
     *   <li><b>T-110:</b> pagination transparently spans chunk boundaries — older chunks
     *       are lazy-loaded through a small LRU cache as the cursor walks backwards.</li>
     * </ul>
     *
     * @param beforeUid  exclusive upper uid cursor; {@code <= 0} or {@link Long#MAX_VALUE}
     *                   for "from newest"
     * @param maxResults requested page size (clamped, see above)
     * @return the page, newest-first (possibly empty, never null)
     */
    public @NotNull List<NewsRecord> getPage(long beforeUid, int maxResults) {
        int limit = Math.min(Math.max(maxResults, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        return chunkStore.getPage(beforeUid, limit);
    }

    // ── NBT persistence (ServerSaveable — retained for test snapshots) ───

    /**
     * Serializes the current in-memory history (all chunks, walked oldest-first)
     * into the given tag. The cap itself is NOT persisted — it is configuration
     * owned by the news library, not world state.
     * <p>
     * <b>Testing-oriented:</b> production save/load is directory-based via
     * {@link #setDirectory(Path, Path)}. This CompoundTag form remains for the
     * {@code NewsHistoryTestSuite} unit tests and the {@code NewsHistoryRequestTestSuite}
     * snapshot/restore setup that predate T-110.
     *
     * @param tag the tag to populate
     * @return true (this save cannot fail)
     */
    @Override
    public boolean save(CompoundTag tag) {
        ListTag recordsTag = new ListTag();
        for (NewsRecord record : chunkStore.allRecordsOldestFirst()) {
            CompoundTag recordTag = new CompoundTag();
            record.save(recordTag);
            recordsTag.add(recordTag);
        }
        tag.put(KEY_RECORDS, recordsTag);
        return true;
    }

    /**
     * Restores the buffer saved by {@link #save(CompoundTag)}, replacing the current
     * content. Malformed entries are skipped (skip-and-continue). When a directory is
     * wired, the on-disk chunk layout is rewritten to match the restored records.
     * <p>
     * See {@link #save(CompoundTag)} for the testing-oriented nature of this API.
     *
     * @param tag the tag to read from
     * @return true (a partially readable history is better than none)
     */
    @Override
    public boolean load(CompoundTag tag) {
        ListTag recordsTag = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        List<NewsRecord> restored = new ArrayList<>(recordsTag.size());
        for (int i = 0; i < recordsTag.size(); i++) {
            NewsRecord record = NewsRecord.createFromTag(recordsTag.getCompound(i));
            if (record != null) restored.add(record);
        }
        chunkStore.restoreFromList(restored);
        return true;
    }
}
