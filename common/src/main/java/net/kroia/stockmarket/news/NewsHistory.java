package net.kroia.stockmarket.news;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Master-side capped chronological store of published {@link NewsRecord}s
 * (NewsEventSystem plan §3, task T-072).
 * <p>
 * The history is an in-memory ring buffer: {@link #append} adds the newest record at the
 * end and prunes the oldest entries beyond {@link #getMaxEntries()}. Records are appended
 * by the production {@link NewsPublisher} ({@link ServerNewsPublisher}) in publish order;
 * the {@code newsUid}s are generated monotonically by the NewsPlugin, so the buffer is
 * ordered oldest-first / ascending uid — this class only stores what it is given and never
 * renumbers.
 * <p>
 * <b>Cap handling:</b> the cap originates from the news library's scheduler config
 * ({@code historyMaxEntries}, default {@value NewsEventLibrary.SchedulerConfig#DEFAULT_HISTORY_MAX_ENTRIES}).
 * To keep this class decoupled from the library, the cap is pushed in via
 * {@link #setMaxEntries(int)} — the {@link ServerNewsPublisher} reads it from the library
 * and sets it before every append. Cap changes (config reload) apply <b>lazily</b>: a
 * shrunken cap does NOT re-prune existing entries immediately, pruning happens on the next
 * {@link #append}. Loading never prunes either — the buffer was pruned when it was saved.
 * <p>
 * <b>Pagination convention</b> (the {@code NewsHistoryRequest} of T-073 codes against
 * {@link #getPage(long, int)} exactly as documented there): pages are newest-first,
 * keyed by {@code newsUid} strictly less than the {@code beforeUid} cursor;
 * {@code beforeUid <= 0} and {@code Long.MAX_VALUE} both mean "start at the newest record".
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

    private static final String KEY_RECORDS = "records";

    // ── State ────────────────────────────────────────────────────────────

    /** The stored records, oldest first / ascending {@code newsUid} (append order). */
    private final ArrayDeque<NewsRecord> records = new ArrayDeque<>();

    /**
     * Current entry cap. Defaults to the scheduler-config default so a history that is
     * loaded before the news library exists is still bounded.
     */
    private int maxEntries = NewsEventLibrary.SchedulerConfig.DEFAULT_HISTORY_MAX_ENTRIES;

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
     * Appends one published record as the newest entry and prunes the oldest entries
     * while the buffer exceeds {@link #getMaxEntries()}.
     * <p>
     * The caller must append records in publish order (ascending {@code newsUid} — the
     * NewsPlugin's uid counter is monotonic and persisted), because {@link #getPage}
     * treats the buffer order as the chronological order.
     *
     * @param record the freshly published record; null is ignored
     */
    public void append(@Nullable NewsRecord record) {
        if (record == null) return;
        records.addLast(record);
        while (records.size() > maxEntries) {
            records.removeFirst();
        }
    }

    /** Removes all stored records (the cap is kept). */
    public void clear() {
        records.clear();
    }

    // ── Queries ──────────────────────────────────────────────────────────

    /** @return the number of stored records */
    public int size() {
        return records.size();
    }

    /** @return true if no records are stored */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** @return the {@code newsUid} of the newest stored record, or 0 if the history is empty */
    public long getNewestUid() {
        NewsRecord newest = records.peekLast();
        return newest != null ? newest.getNewsUid() : 0;
    }

    /**
     * Collects the picture hashes of all stored records (T-088) — the reference set
     * that drives the {@link NewsPictureStore} garbage collection
     * ({@code retainOnly}): a stored picture survives exactly as long as at least one
     * history record still points at it. Text-only records (null hash) are skipped.
     * <p>
     * The list may contain duplicates when several records share one picture; the
     * store deduplicates internally.
     *
     * @return the non-null 20-byte SHA-1 hashes of every record in the buffer
     *         (possibly empty, never null)
     */
    public @NotNull List<byte[]> referencedPictureHashes() {
        List<byte[]> hashes = new ArrayList<>();
        for (NewsRecord record : records) {
            byte[] hash = record.getPictureHash();
            if (hash != null) hashes.add(hash);
        }
        return hashes;
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
     * </ul>
     *
     * @param beforeUid  exclusive upper uid cursor; {@code <= 0} or {@link Long#MAX_VALUE}
     *                   for "from newest"
     * @param maxResults requested page size (clamped, see above)
     * @return the page, newest-first (possibly empty, never null)
     */
    public @NotNull List<NewsRecord> getPage(long beforeUid, int maxResults) {
        long effectiveBefore = beforeUid <= 0 ? Long.MAX_VALUE : beforeUid;
        int limit = Math.min(Math.max(maxResults, MIN_PAGE_SIZE), MAX_PAGE_SIZE);

        List<NewsRecord> page = new ArrayList<>(Math.min(limit, records.size()));
        Iterator<NewsRecord> newestFirst = records.descendingIterator();
        while (newestFirst.hasNext() && page.size() < limit) {
            NewsRecord record = newestFirst.next();
            if (record.getNewsUid() < effectiveBefore) {
                page.add(record);
            }
        }
        return page;
    }

    // ── NBT persistence (ServerSaveable) ─────────────────────────────────

    /**
     * Writes the whole buffer (oldest-first) into the given tag. The cap itself is NOT
     * persisted — it is configuration owned by the news library, not world state.
     *
     * @param tag the tag to populate
     * @return true (this save cannot fail)
     */
    @Override
    public boolean save(CompoundTag tag) {
        ListTag recordsTag = new ListTag();
        for (NewsRecord record : records) {
            CompoundTag recordTag = new CompoundTag();
            record.save(recordTag);
            recordsTag.add(recordTag);
        }
        tag.put(KEY_RECORDS, recordsTag);
        return true;
    }

    /**
     * Restores the buffer saved by {@link #save(CompoundTag)}, replacing the current
     * content. Malformed entries are skipped (skip-and-continue); no pruning happens
     * here — the buffer was capped when it was saved, and a shrunken cap applies on
     * the next {@link #append}.
     *
     * @param tag the tag to read from
     * @return true (a partially readable history is better than none)
     */
    @Override
    public boolean load(CompoundTag tag) {
        records.clear();
        ListTag recordsTag = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < recordsTag.size(); i++) {
            NewsRecord record = NewsRecord.createFromTag(recordsTag.getCompound(i));
            if (record != null) {
                records.addLast(record);
            }
        }
        return true;
    }
}
