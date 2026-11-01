package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsHistoryChunkStore;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Tests for the chunked disk layout added by task T-110
 * ({@link NewsHistoryChunkStore}, category {@code sm_news_history_chunks}):
 *
 * <ul>
 *   <li>Chunk rotation after exactly {@value NewsHistoryChunkStore#CHUNK_SIZE} records.</li>
 *   <li>Cap-driven oldest-chunk drop when the total record count exceeds
 *       {@code historyMaxEntries}; monotonic chunk index counter (never reused).</li>
 *   <li>Pre-T-110 single-file migration: legacy {@code history.nbt} split into
 *       {@value NewsHistoryChunkStore#CHUNK_SIZE}-record chunks, verified, then
 *       deleted. Conflict branch when both legacy and chunk layouts exist.</li>
 *   <li>Lazy older-chunk loading through the LRU cache when {@link NewsHistory#getPage}
 *       paginates past the newest-chunk boundary.</li>
 *   <li>Sidecar equivalence: sidecar-based {@link NewsHistory#referencedPictureHashes()}
 *       equals a full in-memory scan across every chunk.</li>
 *   <li>{@link NewsHistory#save(CompoundTag)}/{@link NewsHistory#load(CompoundTag)}
 *       round-trip across a chunk-backed history (used by
 *       {@code NewsHistoryRequestTestSuite} for live-world snapshot/restore).</li>
 * </ul>
 *
 * All tests use throw-away temporary directories and never touch the live world save.
 */
public class NewsHistoryChunksTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_HISTORY_CHUNKS;
    }

    @Override
    public void registerTests() {
        addTest("rotation_after_chunk_size_records", this::test_rotationAfterChunkSizeRecords);
        addTest("cap_drops_oldest_chunk_atomically", this::test_capDropsOldestChunkAtomically);
        addTest("monotonic_chunk_index_never_reused", this::test_monotonicChunkIndexNeverReused);
        addTest("small_cap_below_chunk_size_keeps_newest", this::test_smallCapBelowChunkSizeKeepsNewest);
        addTest("migration_splits_legacy_file_into_chunks", this::test_migrationSplitsLegacyFileIntoChunks);
        addTest("migration_deletes_legacy_after_success", this::test_migrationDeletesLegacyAfterSuccess);
        addTest("migration_conflict_leaves_legacy_alone", this::test_migrationConflictLeavesLegacyAlone);
        addTest("migration_empty_legacy_file_no_chunks", this::test_migrationEmptyLegacyFileNoChunks);
        addTest("lazy_load_across_chunk_boundary", this::test_lazyLoadAcrossChunkBoundary);
        addTest("lru_evicts_beyond_capacity", this::test_lruEvictsBeyondCapacity);
        addTest("sidecar_referenced_hashes_equal_full_scan", this::test_sidecarReferencedHashesEqualFullScan);
        addTest("sidecar_recovery_rebuilds_from_chunk", this::test_sidecarRecoveryRebuildsFromChunk);
        addTest("save_load_compound_tag_round_trip", this::test_saveLoadCompoundTagRoundTrip);
        addTest("reload_from_directory_restores_state", this::test_reloadFromDirectoryRestoresState);
        addTest("clear_deletes_all_chunk_files", this::test_clearDeletesAllChunkFiles);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Builds a minimal record with the given uid. Optional picture hash (null = text-only). */
    private static NewsRecord record(long uid, byte @org.jetbrains.annotations.Nullable [] pictureHash) {
        NewsRecord r = new NewsRecord(uid, "event_" + uid, 1_720_000_000_000L + uid, uid,
                Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                List.of(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f)),
                "shock", 0.25f, "ramp", 60);
        if (pictureHash != null) r.setPictureHash(pictureHash);
        return r;
    }

    private static NewsRecord record(long uid) {
        return record(uid, null);
    }

    /** Deterministic dummy SHA-1: 20 bytes derived from the low byte of the uid. */
    private static byte[] fakeHash(long uid) {
        byte[] hash = new byte[NewsPictureLibrary.SHA1_LENGTH];
        for (int i = 0; i < hash.length; i++) {
            hash[i] = (byte) ((uid + i) & 0xFF);
        }
        return hash;
    }

    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_history_chunks_test");
    }

    /** Recursively deletes a temp directory (best effort). */
    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteRecursive(entry);
                } else {
                    Files.deleteIfExists(entry);
                }
            }
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
        }
    }

    /** Builds a NewsHistory wired to a fresh chunk directory. */
    private static NewsHistory freshHistory(Path baseDir) {
        NewsHistory history = new NewsHistory();
        history.setDirectory(baseDir.resolve("history"), null);
        return history;
    }

    // ========================================================================
    // Rotation + cap
    // ========================================================================

    /** After exactly {@value NewsHistoryChunkStore#CHUNK_SIZE} records, chunk {@code 001} appears. */
    private TestResult test_rotationAfterChunkSizeRecords() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            for (long uid = 1; uid <= NewsHistoryChunkStore.CHUNK_SIZE; uid++) {
                history.append(record(uid));
            }
            SortedSet<Integer> indices = history.getChunkStoreForTests().knownChunkIndices();
            TestResult r = assertEquals("exactly one chunk after the chunk-size boundary is reached", 1, indices.size());
            if (!r.passed()) return r;

            // Cross the boundary — the (CHUNK_SIZE+1)-th record forces a rotation.
            history.append(record(NewsHistoryChunkStore.CHUNK_SIZE + 1L));
            indices = history.getChunkStoreForTests().knownChunkIndices();
            r = assertEquals("second chunk exists after crossing the boundary", 2, indices.size());
            if (!r.passed()) return r;
            r = assertTrue("chunk 001 exists on disk",
                    Files.exists(base.resolve("history").resolve("001.nbt")));
            if (!r.passed()) return r;
            return assertEquals("newest chunk is index 1", Integer.valueOf(1), indices.last());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** With cap = 100 and 101 records, chunk 000 is dropped entirely, chunk 001 survives. */
    private TestResult test_capDropsOldestChunkAtomically() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(NewsHistoryChunkStore.CHUNK_SIZE); // = 100
            for (long uid = 1; uid <= NewsHistoryChunkStore.CHUNK_SIZE + 1L; uid++) {
                history.append(record(uid));
            }
            // 101 total > cap 100 && chunks == 2 → drop chunk 000.
            SortedSet<Integer> indices = history.getChunkStoreForTests().knownChunkIndices();
            TestResult r = assertEquals("only one chunk remains after cap drop", 1, indices.size());
            if (!r.passed()) return r;
            r = assertEquals("the surviving chunk is index 001", Integer.valueOf(1), indices.first());
            if (!r.passed()) return r;
            r = assertTrue("chunk 000 file deleted",
                    !Files.exists(base.resolve("history").resolve("000.nbt")));
            if (!r.passed()) return r;
            r = assertTrue("sidecar 000 also deleted",
                    !Files.exists(base.resolve("history").resolve("000.hashes.nbt")));
            if (!r.passed()) return r;
            return assertEquals("record count reflects the drop", 1, history.size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** After a cap drop, new chunks use {@code max + 1} — never reuse 000. */
    private TestResult test_monotonicChunkIndexNeverReused() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(NewsHistoryChunkStore.CHUNK_SIZE);
            // Fill chunk 0, cross to chunk 1, drop chunk 0.
            for (long uid = 1; uid <= NewsHistoryChunkStore.CHUNK_SIZE + 1L; uid++) {
                history.append(record(uid));
            }
            // Fill chunk 1, cross to chunk 2, drop chunk 1. Verify no chunk 0 reuse.
            for (long uid = NewsHistoryChunkStore.CHUNK_SIZE + 2L; uid <= 2L * NewsHistoryChunkStore.CHUNK_SIZE + 1L; uid++) {
                history.append(record(uid));
            }
            SortedSet<Integer> indices = history.getChunkStoreForTests().knownChunkIndices();
            TestResult r = assertEquals("still exactly one chunk after two rotations + two drops", 1, indices.size());
            if (!r.passed()) return r;
            r = assertEquals("surviving chunk is index 002 (monotonic — 000 not reused)",
                    Integer.valueOf(2), indices.first());
            if (!r.passed()) return r;
            r = assertTrue("chunk 002 exists",
                    Files.exists(base.resolve("history").resolve("002.nbt")));
            if (!r.passed()) return r;
            return assertTrue("chunk 000 was not recreated",
                    !Files.exists(base.resolve("history").resolve("000.nbt")));
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /**
     * With cap &lt; chunk size, the newest chunk is never dropped — cap becomes a
     * soft ceiling ("cap-alignment caveat" documented in {@code configuration.md}).
     */
    private TestResult test_smallCapBelowChunkSizeKeepsNewest() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(50); // deliberately half a chunk
            for (long uid = 1; uid <= NewsHistoryChunkStore.CHUNK_SIZE; uid++) {
                history.append(record(uid));
            }
            // 100 records, 1 chunk. Cap = 50 is exceeded, but the newest chunk is
            // the only one; it must survive.
            TestResult r = assertEquals("newest chunk kept despite exceeding cap",
                    NewsHistoryChunkStore.CHUNK_SIZE, history.size());
            if (!r.passed()) return r;
            return assertEquals("still exactly one chunk on disk", 1,
                    history.getChunkStoreForTests().knownChunkIndices().size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    // ========================================================================
    // Migration
    // ========================================================================

    /** Legacy single file with 250 records → 3 chunks (100/100/50), chunks numbered 000..002. */
    private TestResult test_migrationSplitsLegacyFileIntoChunks() {
        Path base = null;
        try {
            base = createTempDir();
            Path legacy = base.resolve("history.nbt");
            writeLegacySingleFile(legacy, 250);

            NewsHistory history = new NewsHistory();
            history.setDirectory(base.resolve("history"), legacy);

            TestResult r = assertEquals("migrated record count matches legacy", 250, history.size());
            if (!r.passed()) return r;
            SortedSet<Integer> indices = history.getChunkStoreForTests().knownChunkIndices();
            r = assertEquals("three chunks after 250-record migration", 3, indices.size());
            if (!r.passed()) return r;
            r = assertEquals("indices are 0..2", List.of(0, 1, 2), new ArrayList<>(indices));
            if (!r.passed()) return r;
            r = assertTrue("chunk 002 exists (newest, partial 50)",
                    Files.exists(base.resolve("history").resolve("002.nbt")));
            if (!r.passed()) return r;
            r = assertTrue("sidecar 002 exists",
                    Files.exists(base.resolve("history").resolve("002.hashes.nbt")));
            if (!r.passed()) return r;
            // Newest-first pagination still visits all records.
            List<NewsRecord> firstPage = history.getPage(0, 100);
            r = assertEquals("first page is newest-first, size 100", 100, firstPage.size());
            if (!r.passed()) return r;
            return assertEquals("first page starts at uid 250", 250L, firstPage.get(0).getNewsUid());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** After a successful migration, the legacy {@code history.nbt} is deleted. */
    private TestResult test_migrationDeletesLegacyAfterSuccess() {
        Path base = null;
        try {
            base = createTempDir();
            Path legacy = base.resolve("history.nbt");
            writeLegacySingleFile(legacy, 42);

            NewsHistory history = new NewsHistory();
            history.setDirectory(base.resolve("history"), legacy);

            TestResult r = assertTrue("legacy file deleted after migration", !Files.exists(legacy));
            if (!r.passed()) return r;
            return assertEquals("all records migrated", 42, history.size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** Conflict branch: both legacy and chunks exist → migration is skipped, legacy left alone. */
    private TestResult test_migrationConflictLeavesLegacyAlone() {
        Path base = null;
        try {
            base = createTempDir();
            Path legacy = base.resolve("history.nbt");
            writeLegacySingleFile(legacy, 5);

            // Pre-seed a chunk file so migration should refuse.
            Path chunksDir = base.resolve("history");
            Files.createDirectories(chunksDir);
            NewsHistory seed = new NewsHistory();
            seed.setDirectory(chunksDir, null);
            seed.append(record(999L));

            NewsHistory history = new NewsHistory();
            history.setDirectory(chunksDir, legacy);

            TestResult r = assertTrue("legacy file still present after conflict-branch load",
                    Files.exists(legacy));
            if (!r.passed()) return r;
            r = assertEquals("only the pre-seeded record is visible (legacy NOT merged)",
                    1, history.size());
            if (!r.passed()) return r;
            return assertEquals("pre-seeded record survives", 999L, history.getNewestUid());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** Legacy file with 0 records → migration creates no chunks, but still deletes the legacy file. */
    private TestResult test_migrationEmptyLegacyFileNoChunks() {
        Path base = null;
        try {
            base = createTempDir();
            Path legacy = base.resolve("history.nbt");
            writeLegacySingleFile(legacy, 0);

            NewsHistory history = new NewsHistory();
            history.setDirectory(base.resolve("history"), legacy);

            TestResult r = assertEquals("no chunks created for an empty legacy", 0,
                    history.getChunkStoreForTests().knownChunkIndices().size());
            if (!r.passed()) return r;
            r = assertTrue("legacy file deleted (empty migration still succeeds)",
                    !Files.exists(legacy));
            if (!r.passed()) return r;
            return assertEquals("history size is 0", 0, history.size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** Writes {@code count} records into a pre-T-110 single {@code history.nbt} file. */
    private static void writeLegacySingleFile(Path file, int count) throws IOException {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (long uid = 1; uid <= count; uid++) {
            CompoundTag recordTag = new CompoundTag();
            record(uid).save(recordTag);
            list.add(recordTag);
        }
        root.put("records", list);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }

    // ========================================================================
    // Lazy loading + pagination
    // ========================================================================

    /** getPage across a chunk boundary returns correct records in order. */
    private TestResult test_lazyLoadAcrossChunkBoundary() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            // 3 full chunks + 20 records.
            long total = 3L * NewsHistoryChunkStore.CHUNK_SIZE + 20L;
            for (long uid = 1; uid <= total; uid++) {
                history.append(record(uid));
            }

            // Simulate a fresh world load: reload the directory. Older chunks
            // should NOT be in memory yet.
            NewsHistory reloaded = new NewsHistory();
            reloaded.setDirectory(base.resolve("history"), null);
            TestResult r = assertEquals("LRU starts empty on fresh load", 0,
                    reloaded.getChunkStoreForTests().lruSize());
            if (!r.passed()) return r;

            // Cursor walk should visit every record newest-first.
            List<Long> collected = new ArrayList<>();
            long cursor = 0;
            int safety = 0;
            while (safety++ < 100) {
                List<NewsRecord> page = reloaded.getPage(cursor, 50);
                if (page.isEmpty()) break;
                for (NewsRecord rec : page) collected.add(rec.getNewsUid());
                cursor = page.get(page.size() - 1).getNewsUid();
            }
            r = assertEquals("cursor walk visited every record", (int) total, collected.size());
            if (!r.passed()) return r;
            r = assertEquals("newest uid first", total, collected.get(0).longValue());
            if (!r.passed()) return r;
            r = assertEquals("oldest uid last", 1L, collected.get(collected.size() - 1).longValue());
            if (!r.passed()) return r;
            return assertTrue("LRU populated after crossing chunk boundaries",
                    reloaded.getChunkStoreForTests().lruSize() > 0);
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** LRU cache never exceeds its bounded capacity. */
    private TestResult test_lruEvictsBeyondCapacity() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            // 10 chunks (100 records each) — plenty older than the LRU capacity.
            for (long uid = 1; uid <= 10L * NewsHistoryChunkStore.CHUNK_SIZE; uid++) {
                history.append(record(uid));
            }
            NewsHistory reloaded = new NewsHistory();
            reloaded.setDirectory(base.resolve("history"), null);
            // Walk all pages, forcing older chunks through the LRU.
            long cursor = 0;
            int safety = 0;
            while (safety++ < 200) {
                List<NewsRecord> page = reloaded.getPage(cursor, 50);
                if (page.isEmpty()) break;
                cursor = page.get(page.size() - 1).getNewsUid();
            }
            return assertTrue("LRU size never exceeds capacity ("
                            + NewsHistoryChunkStore.LRU_CAPACITY + ")",
                    reloaded.getChunkStoreForTests().lruSize() <= NewsHistoryChunkStore.LRU_CAPACITY);
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    // ========================================================================
    // Sidecar / picture GC
    // ========================================================================

    /** Sidecar union equals a full in-memory scan across every chunk. */
    private TestResult test_sidecarReferencedHashesEqualFullScan() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            // Populate 3 chunks; every 3rd record has a picture.
            long total = 3L * NewsHistoryChunkStore.CHUNK_SIZE;
            for (long uid = 1; uid <= total; uid++) {
                byte[] hash = uid % 3 == 0 ? fakeHash(uid) : null;
                history.append(record(uid, hash));
            }

            // Sidecar-based union.
            Set<String> sidecarHex = new HashSet<>();
            for (byte[] hash : history.referencedPictureHashes()) {
                sidecarHex.add(NewsPictureLibrary.toHex(hash));
            }

            // Full-scan union (via chunk store's all-records iterator).
            Set<String> fullScanHex = history.getChunkStoreForTests().allRecordsOldestFirst().stream()
                    .map(NewsRecord::getPictureHash)
                    .filter(h -> h != null)
                    .map(NewsPictureLibrary::toHex)
                    .collect(Collectors.toSet());

            return assertEquals("sidecar-based hash union equals full-scan union",
                    fullScanHex, sidecarHex);
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** Deleting a sidecar and reloading rebuilds it from the chunk. */
    private TestResult test_sidecarRecoveryRebuildsFromChunk() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            for (long uid = 1; uid <= 5; uid++) {
                history.append(record(uid, fakeHash(uid)));
            }
            // Wipe the sidecar to simulate corruption.
            Path sidecar = base.resolve("history").resolve("000.hashes.nbt");
            Files.deleteIfExists(sidecar);

            // Reload — the store should rebuild the sidecar from the chunk.
            NewsHistory reloaded = new NewsHistory();
            reloaded.setDirectory(base.resolve("history"), null);
            TestResult r = assertTrue("sidecar rebuilt after reload", Files.exists(sidecar));
            if (!r.passed()) return r;
            r = assertEquals("record count preserved", 5, reloaded.size());
            if (!r.passed()) return r;
            return assertEquals("all 5 picture hashes recovered", 5,
                    reloaded.referencedPictureHashes().size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    // ========================================================================
    // Round-trip + reload
    // ========================================================================

    /** save/load(CompoundTag) round-trips across chunk-backed histories. */
    private TestResult test_saveLoadCompoundTagRoundTrip() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            for (long uid = 1; uid <= 150; uid++) {
                history.append(record(uid));
            }

            CompoundTag snapshot = new CompoundTag();
            history.save(snapshot);

            // Clear (deletes on-disk chunks and in-memory state) and restore.
            history.clear();
            TestResult r = assertEquals("history empty after clear", 0, history.size());
            if (!r.passed()) return r;
            r = assertTrue("no chunk files after clear", !Files.exists(
                    base.resolve("history").resolve("000.nbt")));
            if (!r.passed()) return r;

            history.load(snapshot);
            r = assertEquals("record count restored", 150, history.size());
            if (!r.passed()) return r;
            r = assertEquals("2 chunks restored", 2,
                    history.getChunkStoreForTests().knownChunkIndices().size());
            if (!r.passed()) return r;
            return assertEquals("chunks rewritten to disk starting from 000",
                    Integer.valueOf(0),
                    history.getChunkStoreForTests().knownChunkIndices().first());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** A fresh NewsHistory instance restores state from the existing chunk directory. */
    private TestResult test_reloadFromDirectoryRestoresState() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory writer = freshHistory(base);
            writer.setMaxEntries(1000);
            for (long uid = 1; uid <= 42; uid++) {
                writer.append(record(uid, uid == 7 ? fakeHash(7) : null));
            }

            NewsHistory reader = new NewsHistory();
            reader.setDirectory(base.resolve("history"), null);
            TestResult r = assertEquals("record count restored", 42, reader.size());
            if (!r.passed()) return r;
            r = assertEquals("newest uid restored", 42L, reader.getNewestUid());
            if (!r.passed()) return r;
            return assertEquals("picture hash reference restored via sidecar",
                    1, reader.referencedPictureHashes().size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }

    /** clear() deletes every chunk data + sidecar file. */
    private TestResult test_clearDeletesAllChunkFiles() {
        Path base = null;
        try {
            base = createTempDir();
            NewsHistory history = freshHistory(base);
            history.setMaxEntries(1000);
            for (long uid = 1; uid <= NewsHistoryChunkStore.CHUNK_SIZE + 5; uid++) {
                history.append(record(uid));
            }
            // Sanity: two chunks exist.
            TestResult r = assertTrue("chunk 000 exists before clear",
                    Files.exists(base.resolve("history").resolve("000.nbt")));
            if (!r.passed()) return r;

            history.clear();
            r = assertTrue("chunk 000 deleted",
                    !Files.exists(base.resolve("history").resolve("000.nbt")));
            if (!r.passed()) return r;
            r = assertTrue("sidecar 000 deleted",
                    !Files.exists(base.resolve("history").resolve("000.hashes.nbt")));
            if (!r.passed()) return r;
            r = assertTrue("chunk 001 deleted",
                    !Files.exists(base.resolve("history").resolve("001.nbt")));
            if (!r.passed()) return r;
            return assertEquals("size is 0 after clear", 0, history.size());
        } catch (IOException e) {
            return fail("IO error: " + e.getMessage());
        } finally {
            deleteRecursive(base);
        }
    }
}
