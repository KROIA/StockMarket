package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Disk-layout helper for {@link NewsHistory} (task T-110) — encapsulates the chunked
 * on-disk storage under {@code world/data/StockMarket/News/history/}, keeping the
 * public NewsHistory API oblivious to chunking.
 *
 * <h2>On-disk layout</h2>
 * <pre>
 *   history/
 *     000.nbt          # chunk 0 (oldest surviving)
 *     000.hashes.nbt   # sidecar 0: recordCount + oldestUid + newestUid + referenced picture hashes
 *     001.nbt
 *     001.hashes.nbt
 *     ...
 *     NNN.nbt          # chunk NNN (newest — the only mutable chunk)
 *     NNN.hashes.nbt
 * </pre>
 *
 * <h2>Chunking rules</h2>
 * <ul>
 *   <li><b>Chunk size:</b> {@value #CHUNK_SIZE} records per chunk. When the newest
 *       chunk fills, a new one is opened at {@code max(existing) + 1} — indices are
 *       <b>monotonically increasing</b> and never reused after a chunk drop.</li>
 *   <li><b>Cap enforcement:</b> after each append, while the total record count
 *       exceeds the supplied cap AND at least two chunks exist, the <b>oldest</b>
 *       chunk file (and its sidecar) is deleted atomically. The newest chunk is
 *       never dropped — a cap smaller than {@value #CHUNK_SIZE} therefore causes
 *       retention to fluctuate (documented caveat in {@code configuration.md}).</li>
 *   <li><b>Load path:</b> on {@link #loadFromDirectory}, every sidecar is loaded
 *       into memory (they are small — a few hundred bytes each) and the newest
 *       chunk's record data is loaded. Older chunk data stays on disk and is
 *       lazy-loaded via {@link #ensureOlderChunkLoaded} into a small LRU cache
 *       ({@value #LRU_CAPACITY} entries) when {@link #getPage} paginates past
 *       the newest chunk.</li>
 *   <li><b>Sidecars:</b> per-chunk metadata file listing the picture hashes
 *       referenced by that chunk plus record-count/uid bounds. Union across
 *       sidecars powers {@link #referencedPictureHashes} without loading any
 *       full chunk from disk. Rebuilt whenever the chunk is rewritten; on a
 *       missing/corrupt sidecar the store loads the chunk once, rebuilds the
 *       sidecar, and continues.</li>
 * </ul>
 *
 * <h2>Migration from the pre-T-110 single-file layout</h2>
 * If, at {@link #loadFromDirectory} time, the old {@code history.nbt} file
 * exists next to the {@code history/} directory AND the directory contains no
 * chunk files, the single file is split into {@value #CHUNK_SIZE}-record chunks
 * (chronological — oldest 100 → chunk {@code 000}), sidecars are built, every
 * chunk is verified on disk, and only then is the old single file deleted. A
 * WARN log line records the migration.
 * <p>
 * If both the old single file AND chunk files exist (interrupted migration or
 * user tampering), the safe conservative branch runs: the old file is left in
 * place, an ERROR is logged, and the store continues with the on-disk chunks.
 *
 * <h2>Failure tolerance</h2>
 * All IO problems are logged and swallowed — a broken history store must never
 * break publishing. Before {@link #setDirectory(Path)} is called (test contexts)
 * every operation is an in-memory no-op relative to disk. The store is not
 * thread-safe: the news system runs on the server thread only.
 */
public final class NewsHistoryChunkStore {

    /** Number of records per chunk file. Locked design decision (task T-110). */
    public static final int CHUNK_SIZE = 100;

    /**
     * How many older (immutable) chunks to keep resident in the LRU cache. The
     * newest chunk is always in memory in addition to these.
     */
    public static final int LRU_CAPACITY = 3;

    // ── File-name constants ──────────────────────────────────────────────

    /** Suffix of chunk data files ({@code NNN.nbt}). */
    static final String CHUNK_SUFFIX = ".nbt";
    /** Suffix of chunk sidecar files ({@code NNN.hashes.nbt}). */
    static final String SIDECAR_SUFFIX = ".hashes.nbt";

    // ── NBT keys ─────────────────────────────────────────────────────────

    /** Sidecar key: total record count in the referenced chunk. */
    static final String KEY_RECORD_COUNT = "recordCount";
    /** Sidecar key: uid of the chronologically-oldest record in the chunk. */
    static final String KEY_OLDEST_UID = "oldestUid";
    /** Sidecar key: uid of the chronologically-newest record in the chunk. */
    static final String KEY_NEWEST_UID = "newestUid";
    /** Sidecar key: list of {@code byte[]} SHA-1 hashes referenced by the chunk. */
    static final String KEY_HASHES = "hashes";
    /** Chunk key: the record list (matches the pre-T-110 single-file format). */
    static final String KEY_RECORDS = "records";

    // ── State ────────────────────────────────────────────────────────────

    /** The {@code history/} directory; null in test contexts (in-memory only). */
    private @Nullable Path directory;

    /** Chunk metadata by chunk index, ascending. Populated on load / append. */
    private final TreeMap<Integer, ChunkMeta> metadata = new TreeMap<>();

    /** Newest chunk data (mutable, in memory). Empty when the store is empty. */
    private final ArrayList<NewsRecord> newestChunk = new ArrayList<>();

    /**
     * LRU cache of older chunk data (immutable). Bounded to {@value #LRU_CAPACITY}
     * entries; evicted entries stay on disk and reload on demand.
     */
    private final LinkedHashMap<Integer, List<NewsRecord>> olderLru =
            new LinkedHashMap<>(LRU_CAPACITY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, List<NewsRecord>> eldest) {
                    return size() > LRU_CAPACITY;
                }
            };

    /** Running total of records across every chunk (kept in sync with {@link #metadata}). */
    private int totalRecords = 0;

    // ── Metadata record ─────────────────────────────────────────────────

    /**
     * In-memory summary of one chunk's sidecar (see {@link #SIDECAR_SUFFIX}).
     * All fields are updated in place whenever the chunk is rewritten.
     */
    static final class ChunkMeta {
        int recordCount;
        long oldestUid;
        long newestUid;
        /** Picture hashes referenced by any record in the chunk (deduplicated). */
        final Set<String> referencedHexHashes = new HashSet<>();
    }

    // ── Directory wiring ─────────────────────────────────────────────────

    /**
     * Assigns the {@code history/} directory. Call {@link #loadFromDirectory} after
     * setting to actually populate the in-memory state from disk. Passing null
     * detaches the store (all subsequent operations are in-memory-only).
     *
     * @param directory the chunk directory, or null to detach
     */
    public void setDirectory(@Nullable Path directory) {
        this.directory = directory;
    }

    /** @return the current chunk directory, or null when detached */
    public @Nullable Path getDirectory() {
        return directory;
    }

    // ── Load / migrate ───────────────────────────────────────────────────

    /**
     * Populates the in-memory state from the current {@link #getDirectory()}.
     * Performs the pre-T-110 single-file migration if applicable (see class
     * Javadoc). Safe to call multiple times — resets in-memory state first.
     *
     * @param oldSingleFile absolute path of the pre-T-110 {@code history.nbt}
     *                      file used for migration; null disables migration
     */
    public void loadFromDirectory(@Nullable Path oldSingleFile) {
        resetInMemory();
        Path dir = directory;
        if (dir == null) return;

        // Migration path (single-file → chunked). Idempotent by design.
        migrateFromSingleFileIfNeeded(dir, oldSingleFile);

        if (!Files.isDirectory(dir)) return;
        loadSidecars(dir);
        loadNewestChunkData(dir);
    }

    /** Resets ALL in-memory state (metadata, newest chunk data, LRU, totals). */
    private void resetInMemory() {
        metadata.clear();
        newestChunk.clear();
        olderLru.clear();
        totalRecords = 0;
    }

    /**
     * Migrates the pre-T-110 single-file layout to chunks if the conditions apply:
     * old file exists, chunk directory is missing or empty of chunk files. On
     * conflict (both present) leaves the old file alone and continues with the
     * chunks (safe conservative branch).
     */
    private void migrateFromSingleFileIfNeeded(Path dir, @Nullable Path oldSingleFile) {
        if (oldSingleFile == null || !Files.isRegularFile(oldSingleFile)) return;
        boolean chunksExist = hasAnyChunkFile(dir);
        if (chunksExist) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Both legacy '{}' AND chunk files exist in '{}'. " +
                            "Not migrating — leaving the legacy file alone and continuing with the chunk layout. " +
                            "Move or delete the legacy file manually to silence this message.",
                    oldSingleFile.getFileName(), dir.getFileName());
            return;
        }

        // Load the legacy single file.
        CompoundTag legacyTag;
        try {
            legacyTag = NbtIo.readCompressed(oldSingleFile, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to read legacy '{}' for migration — leaving it in place",
                    oldSingleFile, e);
            return;
        }
        if (legacyTag == null) {
            return;
        }

        ListTag recordsTag = legacyTag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        List<NewsRecord> loaded = new ArrayList<>(recordsTag.size());
        for (int i = 0; i < recordsTag.size(); i++) {
            NewsRecord record = NewsRecord.createFromTag(recordsTag.getCompound(i));
            if (record != null) loaded.add(record);
        }

        // Ensure the target directory exists.
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to create chunk directory '{}' — leaving legacy '{}' in place",
                    dir, oldSingleFile, e);
            return;
        }

        // Split into 100-record chunks (oldest → chunk 0, newest → highest index).
        int chunkCount = (loaded.size() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<Path> writtenChunkFiles = new ArrayList<>(chunkCount);
        List<Path> writtenSidecarFiles = new ArrayList<>(chunkCount);
        for (int chunkIdx = 0; chunkIdx < chunkCount; chunkIdx++) {
            int from = chunkIdx * CHUNK_SIZE;
            int to = Math.min(from + CHUNK_SIZE, loaded.size());
            List<NewsRecord> slice = loaded.subList(from, to);
            Path chunkFile = dir.resolve(formatIndex(chunkIdx) + CHUNK_SUFFIX);
            Path sidecarFile = dir.resolve(formatIndex(chunkIdx) + SIDECAR_SUFFIX);
            if (!writeChunkFile(chunkFile, slice)) {
                StockMarketMod.LOGGER.error(
                        "[NewsHistoryChunkStore] Migration aborted: could not write chunk '{}'. Leaving legacy '{}' in place.",
                        chunkFile.getFileName(), oldSingleFile);
                return;
            }
            if (!writeSidecarFile(sidecarFile, slice)) {
                StockMarketMod.LOGGER.error(
                        "[NewsHistoryChunkStore] Migration aborted: could not write sidecar '{}'. Leaving legacy '{}' in place.",
                        sidecarFile.getFileName(), oldSingleFile);
                return;
            }
            writtenChunkFiles.add(chunkFile);
            writtenSidecarFiles.add(sidecarFile);
        }

        // Verify each written chunk exists and is non-empty on disk before dropping
        // the legacy file (a light-weight fsync check — the OS may still buffer,
        // but Files.size > 0 + Files.exists rules out empty/missing artifacts).
        for (int i = 0; i < writtenChunkFiles.size(); i++) {
            if (!fileWrittenAndNonEmpty(writtenChunkFiles.get(i))
                    || !fileWrittenAndNonEmpty(writtenSidecarFiles.get(i))) {
                StockMarketMod.LOGGER.error(
                        "[NewsHistoryChunkStore] Migration verification failed for chunk index {}. Leaving legacy '{}' in place.",
                        i, oldSingleFile);
                return;
            }
        }

        // All chunks written and verified — safe to delete the legacy file.
        try {
            Files.deleteIfExists(oldSingleFile);
        } catch (IOException e) {
            StockMarketMod.LOGGER.warn(
                    "[NewsHistoryChunkStore] Migration wrote all chunks but failed to delete legacy '{}': {}. It will be ignored on subsequent loads because chunk files now exist.",
                    oldSingleFile, e.getMessage());
        }

        StockMarketMod.LOGGER.warn(
                "[NewsHistoryChunkStore] Migrated legacy history.nbt to chunk layout: {} record(s) → {} chunk(s) in '{}'.",
                loaded.size(), chunkCount, dir);
    }

    /** @return true when {@code dir} contains at least one {@code NNN.nbt} chunk file */
    private static boolean hasAnyChunkFile(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + CHUNK_SUFFIX)) {
            for (Path file : stream) {
                if (isChunkFile(file)) return true;
            }
        } catch (IOException ignored) {
            // Treat as "no chunks" — the load will simply start empty.
        }
        return false;
    }

    /** @return true if {@code file} is a regular file and its size is &gt; 0 */
    private static boolean fileWrittenAndNonEmpty(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Scans the directory for all sidecar files, populating {@link #metadata}. */
    private void loadSidecars(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + SIDECAR_SUFFIX)) {
            for (Path file : stream) {
                Integer idx = parseSidecarIndex(file);
                if (idx == null) continue;
                ChunkMeta meta = readSidecar(file);
                if (meta != null) {
                    metadata.put(idx, meta);
                    totalRecords += meta.recordCount;
                }
            }
        } catch (IOException e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to scan chunk directory '{}' — starting with an empty history",
                    dir, e);
        }

        // Recovery: for every chunk file that lacks a valid sidecar, load the chunk
        // once and rebuild the sidecar. Failure to rebuild leaves the chunk stranded
        // (skip-and-continue) — its GC contribution is missing until fixed manually.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + CHUNK_SUFFIX)) {
            for (Path file : stream) {
                Integer idx = parseChunkIndex(file);
                if (idx == null) continue;
                if (metadata.containsKey(idx)) continue;
                StockMarketMod.LOGGER.warn(
                        "[NewsHistoryChunkStore] Sidecar missing/corrupt for chunk '{}' — rebuilding from chunk data",
                        file.getFileName());
                List<NewsRecord> chunkData = readChunkFile(file);
                if (chunkData == null) continue;
                Path sidecarFile = dir.resolve(formatIndex(idx) + SIDECAR_SUFFIX);
                writeSidecarFile(sidecarFile, chunkData);
                ChunkMeta meta = summarize(chunkData);
                metadata.put(idx, meta);
                totalRecords += meta.recordCount;
            }
        } catch (IOException e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to rebuild missing sidecars in '{}'",
                    dir, e);
        }
    }

    /** Loads the newest chunk's record data into {@link #newestChunk}. */
    private void loadNewestChunkData(Path dir) {
        if (metadata.isEmpty()) return;
        int newestIdx = metadata.lastKey();
        Path chunkFile = dir.resolve(formatIndex(newestIdx) + CHUNK_SUFFIX);
        List<NewsRecord> data = readChunkFile(chunkFile);
        if (data != null) {
            newestChunk.addAll(data);
        } else {
            // Data unreadable: drop the corresponding metadata entry so the store
            // stays consistent (the sidecar without matching data is not
            // actionable). We warn but keep older chunks working.
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Newest chunk '{}' unreadable — dropping its metadata and continuing with older chunks",
                    chunkFile.getFileName());
            ChunkMeta stale = metadata.remove(newestIdx);
            if (stale != null) totalRecords -= stale.recordCount;
        }
    }

    // ── Append / mutate ──────────────────────────────────────────────────

    /**
     * Appends one published record to the newest chunk, rotating chunks when the
     * newest fills and dropping the oldest chunk file when the total record count
     * exceeds {@code cap} and at least two chunks exist.
     *
     * @param record the record to append; null is ignored
     * @param cap    the maximum retained record count (clamped to &ge; 1)
     */
    public void append(@Nullable NewsRecord record, int cap) {
        if (record == null) return;
        int effectiveCap = Math.max(1, cap);

        // Rotate if the current newest chunk is full.
        int newestIdx;
        if (metadata.isEmpty()) {
            newestIdx = 0;
            metadata.put(newestIdx, new ChunkMeta());
        } else {
            newestIdx = metadata.lastKey();
            if (newestChunk.size() >= CHUNK_SIZE) {
                // T-115 fix: snapshot the completed chunk into the LRU cache BEFORE
                // clearing it. Previously the just-completed chunk was only reachable
                // via a disk read (through ensureOlderChunkLoaded); in-memory-only
                // contexts (no setDirectory — unit tests) lost the data entirely, so
                // getPage across chunk boundaries returned only the newest chunk.
                // Disk-wired contexts also benefit by avoiding an immediate re-read.
                int completedIdx = newestIdx;
                olderLru.put(completedIdx, Collections.unmodifiableList(new ArrayList<>(newestChunk)));
                // Move to a new higher index (monotonic — max + 1, never reused).
                newestIdx = completedIdx + 1;
                newestChunk.clear();
                metadata.put(newestIdx, new ChunkMeta());
            }
        }

        newestChunk.add(record);
        ChunkMeta meta = summarize(newestChunk);
        metadata.put(newestIdx, meta);
        totalRecords++;

        // Persist the newest chunk + sidecar immediately.
        persistNewestChunk(newestIdx);

        // Cap enforcement: drop oldest chunk files while over-cap and there are
        // ≥ 2 chunks (never drop the sole newest chunk — task decision).
        while (totalRecords > effectiveCap && metadata.size() >= 2) {
            int oldestIdx = metadata.firstKey();
            ChunkMeta oldest = metadata.remove(oldestIdx);
            if (oldest == null) break;
            totalRecords -= oldest.recordCount;
            olderLru.remove(oldestIdx);
            deleteChunkFiles(oldestIdx);
        }
    }

    /** Persists the newest chunk's data file and sidecar. */
    private void persistNewestChunk(int newestIdx) {
        Path dir = directory;
        if (dir == null) return;
        Path chunkFile = dir.resolve(formatIndex(newestIdx) + CHUNK_SUFFIX);
        Path sidecarFile = dir.resolve(formatIndex(newestIdx) + SIDECAR_SUFFIX);
        writeChunkFile(chunkFile, newestChunk);
        writeSidecarFile(sidecarFile, newestChunk);
    }

    /**
     * Removes ALL in-memory state and deletes ALL on-disk chunk files (when the
     * directory is wired). Intended for test resets and administrative wipes.
     */
    public void clear() {
        // Snapshot before clearing so we can delete the corresponding files.
        List<Integer> indices = new ArrayList<>(metadata.keySet());
        resetInMemory();
        Path dir = directory;
        if (dir == null) return;
        for (int idx : indices) {
            deleteChunkFiles(idx);
        }
        // Belt-and-suspenders: sweep any stray files matching our naming that we
        // did not track in metadata (e.g. after a partially failed migration).
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                Integer chunkIdx = parseChunkIndex(file);
                Integer sidecarIdx = parseSidecarIndex(file);
                if (chunkIdx != null || sidecarIdx != null) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // Best effort — a lingering file is not correctness-critical.
                    }
                }
            }
        } catch (IOException ignored) {
            // Directory may not exist; nothing to sweep.
        }
    }

    /** Deletes both the {@code NNN.nbt} and {@code NNN.hashes.nbt} files, best-effort. */
    private void deleteChunkFiles(int idx) {
        Path dir = directory;
        if (dir == null) return;
        Path chunkFile = dir.resolve(formatIndex(idx) + CHUNK_SUFFIX);
        Path sidecarFile = dir.resolve(formatIndex(idx) + SIDECAR_SUFFIX);
        try {
            Files.deleteIfExists(chunkFile);
        } catch (IOException e) {
            StockMarketMod.LOGGER.warn(
                    "[NewsHistoryChunkStore] Failed to delete chunk '{}': {}",
                    chunkFile.getFileName(), e.getMessage());
        }
        try {
            Files.deleteIfExists(sidecarFile);
        } catch (IOException e) {
            StockMarketMod.LOGGER.warn(
                    "[NewsHistoryChunkStore] Failed to delete sidecar '{}': {}",
                    sidecarFile.getFileName(), e.getMessage());
        }
    }

    // ── Query ────────────────────────────────────────────────────────────

    /** @return the total number of records across every chunk (newest + older) */
    public int size() {
        return totalRecords;
    }

    /** @return true when no records are stored anywhere */
    public boolean isEmpty() {
        return totalRecords == 0;
    }

    /** @return the uid of the newest record, or 0 when empty */
    public long getNewestUid() {
        if (newestChunk.isEmpty()) return 0;
        return newestChunk.get(newestChunk.size() - 1).getNewsUid();
    }

    /**
     * Newest-first pagination across all chunks, matching {@link NewsHistory#getPage}
     * semantics exactly (strict {@code uid < beforeUid}, "from newest" conventions,
     * {@code maxResults} clamping). Older chunks are lazy-loaded through the LRU
     * cache as needed.
     *
     * @param beforeUid  exclusive upper uid cursor ({@code <= 0} = from newest)
     * @param maxResults requested page size (already clamped by caller)
     * @return a newest-first page (never null; possibly empty)
     */
    public @NotNull List<NewsRecord> getPage(long beforeUid, int maxResults) {
        long effectiveBefore = beforeUid <= 0 ? Long.MAX_VALUE : beforeUid;
        List<NewsRecord> page = new ArrayList<>();
        if (metadata.isEmpty() || maxResults <= 0) return page;

        // Walk chunks newest → oldest (descending index).
        for (Integer idx : metadata.descendingKeySet()) {
            ChunkMeta meta = metadata.get(idx);
            // Skip chunks whose newest uid is already < beforeUid? No — the
            // filter is per-record because uids can be non-contiguous (T-098
            // migrations, chain runtime). We DO however skip chunks whose oldest
            // uid is >= beforeUid — nothing in them can qualify.
            if (meta.oldestUid >= effectiveBefore && meta.recordCount > 0) {
                continue;
            }
            List<NewsRecord> chunkData = chunkDataFor(idx);
            if (chunkData == null) continue;
            for (int i = chunkData.size() - 1; i >= 0; i--) {
                NewsRecord record = chunkData.get(i);
                if (record.getNewsUid() < effectiveBefore) {
                    page.add(record);
                    if (page.size() >= maxResults) return page;
                }
            }
        }
        return page;
    }

    /**
     * Union of picture hashes referenced by every chunk (sidecar-based — no chunk
     * data is loaded). Corrupt sidecars are handled during {@link #loadSidecars}
     * — by this call every metadata entry has a valid hash set.
     *
     * @return the referenced 20-byte SHA-1 hashes (never null, deduplicated)
     */
    public @NotNull List<byte[]> referencedPictureHashes() {
        // Dedup by hex string across all chunks — a picture referenced by several
        // records surfaces once in the returned list. The store's retainOnly
        // deduplicates too, but this keeps allocations small.
        Set<String> seen = new HashSet<>();
        List<byte[]> hashes = new ArrayList<>();
        for (ChunkMeta meta : metadata.values()) {
            for (String hex : meta.referencedHexHashes) {
                if (seen.add(hex)) {
                    byte[] bytes = decodeHex(hex);
                    if (bytes != null) hashes.add(bytes);
                }
            }
        }
        return hashes;
    }

    // ── Iteration for backup/snapshot ────────────────────────────────────

    /**
     * Iterates every stored record in chronological (oldest-first) order across
     * all chunks. Lazy-loads older chunks through the LRU cache as needed.
     * Intended for full snapshots (test/backup use) — production paths use
     * {@link #getPage} instead.
     *
     * @return every stored record in ascending-uid order (never null)
     */
    public @NotNull List<NewsRecord> allRecordsOldestFirst() {
        List<NewsRecord> all = new ArrayList<>(totalRecords);
        for (Integer idx : metadata.keySet()) {
            List<NewsRecord> chunkData = chunkDataFor(idx);
            if (chunkData != null) all.addAll(chunkData);
        }
        return all;
    }

    /**
     * Replaces the entire store contents with the given records (chronological,
     * oldest-first). Rewrites the on-disk layout to match — used by test-only
     * snapshot restore. The supplied cap is applied lazily on the next append.
     *
     * @param records  the records to install (may be empty)
     */
    public void restoreFromList(@NotNull List<NewsRecord> records) {
        clear();
        // Deliberately use append() so the same rotation/persistence code path
        // runs — keeps the migration/restore logic single-sourced.
        for (NewsRecord record : records) {
            append(record, Integer.MAX_VALUE);
        }
    }

    // ── Chunk data helpers ───────────────────────────────────────────────

    /**
     * Fetches the record list of chunk {@code idx}. The newest chunk returns the
     * live in-memory buffer; older chunks load through the LRU (evicting on
     * capacity). Returns null when the chunk cannot be read.
     */
    private @Nullable List<NewsRecord> chunkDataFor(int idx) {
        if (metadata.isEmpty()) return null;
        if (idx == metadata.lastKey()) return newestChunk;
        return ensureOlderChunkLoaded(idx);
    }

    /** LRU-accesses (loads if missing) chunk {@code idx}; returns null on IO failure. */
    private @Nullable List<NewsRecord> ensureOlderChunkLoaded(int idx) {
        List<NewsRecord> cached = olderLru.get(idx);
        if (cached != null) return cached;
        Path dir = directory;
        if (dir == null) return null;
        Path chunkFile = dir.resolve(formatIndex(idx) + CHUNK_SUFFIX);
        List<NewsRecord> data = readChunkFile(chunkFile);
        if (data == null) return null;
        olderLru.put(idx, Collections.unmodifiableList(data));
        return data;
    }

    // ── Chunk file IO ────────────────────────────────────────────────────

    /**
     * Writes a chunk data file (temp-file + atomic move). NBT compression matches
     * the {@code DataPersistence} default the DataManager uses elsewhere.
     */
    private static boolean writeChunkFile(Path chunkFile, List<NewsRecord> records) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (NewsRecord record : records) {
            CompoundTag rt = new CompoundTag();
            record.save(rt);
            list.add(rt);
        }
        tag.put(KEY_RECORDS, list);
        return writeCompoundAtomic(chunkFile, tag);
    }

    /** Reads a chunk data file; returns null when the file is missing or malformed. */
    private static @Nullable List<NewsRecord> readChunkFile(Path chunkFile) {
        if (!Files.isRegularFile(chunkFile)) return null;
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(chunkFile, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to read chunk '{}'",
                    chunkFile.getFileName(), e);
            return null;
        }
        if (tag == null) return null;
        ListTag recordsTag = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        List<NewsRecord> out = new ArrayList<>(recordsTag.size());
        for (int i = 0; i < recordsTag.size(); i++) {
            NewsRecord record = NewsRecord.createFromTag(recordsTag.getCompound(i));
            if (record != null) out.add(record);
        }
        return out;
    }

    /**
     * Writes a sidecar file (record count + uid bounds + referenced picture
     * hashes). Also updates the corresponding {@link ChunkMeta} in memory (via
     * {@link #summarize}) when appropriate — callers of {@link #persistNewestChunk}
     * already updated {@link #metadata}; migration writes them explicitly here.
     */
    private static boolean writeSidecarFile(Path sidecarFile, List<NewsRecord> records) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_RECORD_COUNT, records.size());
        tag.putLong(KEY_OLDEST_UID, records.isEmpty() ? 0L : records.get(0).getNewsUid());
        tag.putLong(KEY_NEWEST_UID, records.isEmpty() ? 0L : records.get(records.size() - 1).getNewsUid());
        ListTag hashes = new ListTag();
        Set<String> seen = new HashSet<>();
        for (NewsRecord record : records) {
            byte[] hash = record.getPictureHash();
            if (hash == null) continue;
            String hex = NewsPictureLibrary.toHex(hash);
            if (seen.add(hex)) {
                CompoundTag hashTag = new CompoundTag();
                hashTag.putByteArray("h", hash);
                hashes.add(hashTag);
            }
        }
        tag.put(KEY_HASHES, hashes);
        return writeCompoundAtomic(sidecarFile, tag);
    }

    /** Reads a sidecar file; returns null on malformed/missing input. */
    private static @Nullable ChunkMeta readSidecar(Path sidecarFile) {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(sidecarFile, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to read sidecar '{}'",
                    sidecarFile.getFileName(), e);
            return null;
        }
        if (tag == null) return null;
        ChunkMeta meta = new ChunkMeta();
        meta.recordCount = Math.max(0, tag.getInt(KEY_RECORD_COUNT));
        meta.oldestUid = tag.getLong(KEY_OLDEST_UID);
        meta.newestUid = tag.getLong(KEY_NEWEST_UID);
        ListTag hashes = tag.getList(KEY_HASHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < hashes.size(); i++) {
            CompoundTag hashTag = hashes.getCompound(i);
            byte[] hash = hashTag.getByteArray("h");
            if (hash != null && hash.length == NewsPictureLibrary.SHA1_LENGTH) {
                meta.referencedHexHashes.add(NewsPictureLibrary.toHex(hash));
            }
        }
        return meta;
    }

    /** Builds a fresh in-memory {@link ChunkMeta} from a chunk's record list. */
    private static ChunkMeta summarize(List<NewsRecord> records) {
        ChunkMeta meta = new ChunkMeta();
        meta.recordCount = records.size();
        if (!records.isEmpty()) {
            meta.oldestUid = records.get(0).getNewsUid();
            meta.newestUid = records.get(records.size() - 1).getNewsUid();
        }
        for (NewsRecord record : records) {
            byte[] hash = record.getPictureHash();
            if (hash != null) {
                meta.referencedHexHashes.add(NewsPictureLibrary.toHex(hash));
            }
        }
        return meta;
    }

    /**
     * Atomic write of a CompoundTag via temp file + move (same technique as
     * {@link NewsPictureStore#put}). Directories are created lazily.
     */
    private static boolean writeCompoundAtomic(Path target, CompoundTag tag) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "chunk_", ".tmp");
            try {
                NbtIo.writeCompressed(tag, temp);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsHistoryChunkStore] Failed to write '{}'",
                    target.getFileName(), e);
            return false;
        }
    }

    // ── Name / index parsing ────────────────────────────────────────────

    /** Formats a chunk index into its zero-padded 3-digit file-name stem (or wider for &ge; 1000). */
    static String formatIndex(int idx) {
        return String.format("%03d", idx);
    }

    /** Parses the leading integer from a chunk file name ({@code NNN.nbt}); null on mismatch. */
    static @Nullable Integer parseChunkIndex(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(SIDECAR_SUFFIX)) return null; // NNN.hashes.nbt is not a chunk
        if (!name.endsWith(CHUNK_SUFFIX)) return null;
        return parseNumericStem(name.substring(0, name.length() - CHUNK_SUFFIX.length()));
    }

    /** Parses the leading integer from a sidecar file name ({@code NNN.hashes.nbt}); null on mismatch. */
    static @Nullable Integer parseSidecarIndex(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(SIDECAR_SUFFIX)) return null;
        return parseNumericStem(name.substring(0, name.length() - SIDECAR_SUFFIX.length()));
    }

    /** Parses a positive integer stem; null on any non-digit character. */
    private static @Nullable Integer parseNumericStem(String stem) {
        if (stem.isEmpty()) return null;
        for (int i = 0; i < stem.length(); i++) {
            if (!Character.isDigit(stem.charAt(i))) return null;
        }
        try {
            return Integer.parseInt(stem);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return true if {@code file} is a regular chunk data file (this store's naming). */
    private static boolean isChunkFile(Path file) {
        return parseChunkIndex(file) != null && Files.isRegularFile(file);
    }

    /** Decodes a lowercase-hex SHA-1 back to its 20-byte representation. */
    private static byte @Nullable [] decodeHex(String hex) {
        if (hex.length() != NewsPictureLibrary.SHA1_LENGTH * 2) return null;
        byte[] out = new byte[NewsPictureLibrary.SHA1_LENGTH];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    // ── Debug / test hooks ───────────────────────────────────────────────

    /** @return the currently loaded chunk indices (ascending) — for tests only */
    public java.util.SortedSet<Integer> knownChunkIndices() {
        return new java.util.TreeSet<>(metadata.keySet());
    }

    /** @return the number of chunks currently in the LRU cache — for tests only */
    public int lruSize() {
        return olderLru.size();
    }
}
