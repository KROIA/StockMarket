package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.DefaultNewsPictures;
import net.kroia.stockmarket.news.NewsEventLibrary;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsPictureStore;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.ServerNewsPublisher;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests for the published news-picture layer (T-088, picture plan §10, category
 * {@code sm_news_picture_store}): {@link NewsPictureStore} put/get idempotency,
 * hash verification, {@code retainOnly} garbage collection (foreign files untouched),
 * the optional {@link NewsRecord} picture hash (NBT backward compatibility and the
 * append-only STREAM_CODEC extension inside list codecs), and the
 * {@link ServerNewsPublisher} publish-time snapshot semantics (record hash + store
 * bytes before broadcast; config swap keeps old records servable; prune GC).
 * All folder tests run against temp directories, never the live world/config folders.
 */
public class NewsPictureStoreTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_PICTURE_STORE;
    }

    @Override
    public void registerTests() {
        // NewsPictureStore basics
        addTest("store_put_get_idempotent", this::test_store_putGetIdempotent);
        addTest("store_put_hash_mismatch_rejected", this::test_store_putHashMismatchRejected);
        addTest("store_put_invalid_hash_rejected", this::test_store_putInvalidHashRejected);
        addTest("store_get_unknown_hash_is_null", this::test_store_getUnknownHashIsNull);
        addTest("store_unwired_is_safe_noop", this::test_store_unwiredIsSafeNoop);

        // Garbage collection
        addTest("store_retain_only_deletes_unreferenced_only", this::test_store_retainOnly_deletesUnreferencedOnly);
        addTest("store_retain_only_foreign_files_untouched", this::test_store_retainOnly_foreignFilesUntouched);

        // NewsRecord picture hash — persistence & wire
        addTest("record_nbt_roundtrip_with_hash", this::test_record_nbtRoundTripWithHash);
        addTest("record_nbt_without_hash_stays_hashless", this::test_record_nbtWithoutHashStaysHashless);
        addTest("record_old_nbt_loads_null_hash", this::test_record_oldNbtLoadsNullHash);
        addTest("record_setter_rejects_wrong_length", this::test_record_setterRejectsWrongLength);
        addTest("record_stream_codec_list_mixed_hashes", this::test_record_streamCodecListMixedHashes);

        // Publish-path snapshot (ServerNewsPublisher + history + store + library)
        addTest("publish_snapshots_hash_and_bytes", this::test_publish_snapshotsHashAndBytes);
        addTest("publish_missing_picture_stays_textonly", this::test_publish_missingPictureStaysTextOnly);
        addTest("publish_config_swap_keeps_old_record_served", this::test_publish_configSwapKeepsOldRecordServed);
        addTest("publish_prune_gc_drops_unreferenced_picture", this::test_publish_pruneGc_dropsUnreferencedPicture);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Encodes a small valid grayscale PNG with a seed-dependent deterministic pattern. */
    private static byte[] validPng(int width, int height, int seed) {
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = (byte) ((x * 7 + y * 13 + seed * 31) & 0xFF);
            }
        }
        return DefaultNewsPictures.encodeGrayscalePng(width, height, pixels);
    }

    /** Creates a temp directory for store/publish tests (never a live folder). */
    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_picture_store_test");
    }

    /** Best-effort recursive cleanup of a temp directory. */
    private static void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /** A store wired to a fresh subfolder of the given temp dir. */
    private static NewsPictureStore storeIn(Path tempDir) {
        NewsPictureStore store = new NewsPictureStore();
        store.setDirectory(tempDir.resolve("store"));
        return store;
    }

    /** Builds a minimal record with the given uid and event id (publish-path tests). */
    private static NewsRecord record(long uid, String eventId) {
        return new NewsRecord(uid, eventId, 1_720_000_000_000L + uid, 100L + uid,
                Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                List.of(), "shock", 0.5f, "ramp", 60);
    }

    /**
     * One minimal valid event JSON with the given id and an optional picture reference
     * (same shape the NewsPictureLibraryTestSuite uses).
     */
    private static String eventJson(String id, String pictureOrNull) {
        return "{\"id\":\"" + id + "\","
                + (pictureOrNull != null ? "\"picture\":\"" + pictureOrNull + "\"," : "")
                + "\"headline\":\"Test headline\",\"text\":\"Test text\","
                + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5},"
                + "\"markets\":[{\"item\":\"minecraft:diamond\",\"weightFactor\":1.0}]}";
    }

    /**
     * Prepares a news config folder in the temp dir: an events file plus a pre-created
     * (thus non-empty → defaults extraction stays away) pictures folder containing the
     * given fileName → bytes pairs. Returns the config folder for {@code reload()}.
     */
    private static Path newsConfig(Path tempDir, String eventsJsonArray,
                                   Map<String, byte[]> pictures) throws IOException {
        Path config = tempDir.resolve("config");
        Path picturesDir = config.resolve(NewsPictureLibrary.PICTURES_DIR_NAME);
        Files.createDirectories(picturesDir);
        for (Map.Entry<String, byte[]> picture : pictures.entrySet()) {
            Files.write(picturesDir.resolve(picture.getKey()), picture.getValue());
        }
        Files.writeString(config.resolve("events.json"),
                "{\"events\":[" + eventsJsonArray + "]}");
        return config;
    }

    // ========================================================================
    // NewsPictureStore basics
    // ========================================================================

    /** put() stores exactly once under the hex name; get() returns the exact bytes. */
    private TestResult test_store_putGetIdempotent() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] png = validPng(32, 32, 1);
            byte[] hash = NewsPictureLibrary.sha1(png);

            TestResult r = assertTrue("first put must succeed", store.put(hash, png));
            if (!r.passed()) return r;
            r = assertTrue("get must return the stored bytes",
                    Arrays.equals(png, store.get(hash)));
            if (!r.passed()) return r;
            r = assertTrue("repeated put of the same content must stay true (idempotent)",
                    store.put(hash, png));
            if (!r.passed()) return r;
            r = assertEquals("store must hold exactly one file", 1, store.listHashes().size());
            if (!r.passed()) return r;
            return assertTrue("listHashes must contain the hex hash",
                    store.listHashes().contains(NewsPictureLibrary.toHex(hash)));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A hash that is not the SHA-1 of the bytes must be rejected (nothing written). */
    private TestResult test_store_putHashMismatchRejected() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] png = validPng(32, 32, 2);
            byte[] wrongHash = NewsPictureLibrary.sha1("something else".getBytes(StandardCharsets.UTF_8));

            TestResult r = assertFalse("mismatching hash must be rejected",
                    store.put(wrongHash, png));
            if (!r.passed()) return r;
            r = assertNull("nothing must be servable under the wrong hash",
                    store.get(wrongHash));
            if (!r.passed()) return r;
            return assertEquals("store must stay empty after the rejection",
                    0, store.listHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** null / wrong-length hashes and null bytes must be rejected, never throw. */
    private TestResult test_store_putInvalidHashRejected() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] png = validPng(16, 16, 3);

            TestResult r = assertFalse("null hash must be rejected", store.put(null, png));
            if (!r.passed()) return r;
            r = assertFalse("19-byte hash must be rejected", store.put(new byte[19], png));
            if (!r.passed()) return r;
            r = assertFalse("21-byte hash must be rejected", store.put(new byte[21], png));
            if (!r.passed()) return r;
            r = assertFalse("null bytes must be rejected",
                    store.put(NewsPictureLibrary.sha1(png), null));
            if (!r.passed()) return r;
            return assertEquals("store must stay empty", 0, store.listHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** get() of an unknown (but well-formed) hash is null, not an error. */
    private TestResult test_store_getUnknownHashIsNull() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] unknown = NewsPictureLibrary.sha1("never stored".getBytes(StandardCharsets.UTF_8));
            TestResult r = assertNull("unknown hash must yield null", store.get(unknown));
            if (!r.passed()) return r;
            return assertNull("malformed hash must yield null", store.get(new byte[5]));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Before setDirectory() every operation must be a safe no-op (never throw). */
    private TestResult test_store_unwiredIsSafeNoop() {
        NewsPictureStore store = new NewsPictureStore();
        byte[] png = validPng(16, 16, 4);
        byte[] hash = NewsPictureLibrary.sha1(png);

        TestResult r = assertFalse("put on an unwired store must fail cleanly",
                store.put(hash, png));
        if (!r.passed()) return r;
        r = assertNull("get on an unwired store must be null", store.get(hash));
        if (!r.passed()) return r;
        store.retainOnly(List.of(hash)); // must not throw
        return assertEquals("listHashes on an unwired store must be empty",
                0, store.listHashes().size());
    }

    // ========================================================================
    // Garbage collection
    // ========================================================================

    /** retainOnly keeps referenced pictures and deletes unreferenced ones. */
    private TestResult test_store_retainOnly_deletesUnreferencedOnly() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] keepPng = validPng(32, 32, 5);
            byte[] dropPng = validPng(32, 32, 6);
            byte[] keepHash = NewsPictureLibrary.sha1(keepPng);
            byte[] dropHash = NewsPictureLibrary.sha1(dropPng);
            store.put(keepHash, keepPng);
            store.put(dropHash, dropPng);

            store.retainOnly(List.of(keepHash));

            TestResult r = assertTrue("referenced picture must survive the GC",
                    Arrays.equals(keepPng, store.get(keepHash)));
            if (!r.passed()) return r;
            r = assertNull("unreferenced picture must be deleted", store.get(dropHash));
            if (!r.passed()) return r;
            r = assertEquals("exactly one file must remain", 1, store.listHashes().size());
            if (!r.passed()) return r;

            // An empty reference set clears the whole store.
            store.retainOnly(List.of());
            return assertEquals("empty reference set must clear the store",
                    0, store.listHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** GC must only ever touch regular *.png files named as 40 lowercase hex chars. */
    private TestResult test_store_retainOnly_foreignFilesUntouched() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] png = validPng(32, 32, 7);
            store.put(NewsPictureLibrary.sha1(png), png); // one real, soon-unreferenced entry

            Path storeDir = store.getDirectory();
            // Foreign files the GC must never delete:
            Files.write(storeDir.resolve("readme.txt"),
                    "admin note".getBytes(StandardCharsets.UTF_8));
            Files.write(storeDir.resolve("custom_backup.png"), png); // non-hex name
            String upperHex = NewsPictureLibrary.toHex(
                    NewsPictureLibrary.sha1(new byte[]{1})).toUpperCase(java.util.Locale.ROOT);
            Files.write(storeDir.resolve(upperHex + ".png"), png); // uppercase hex = foreign
            String hex39 = NewsPictureLibrary.toHex(
                    NewsPictureLibrary.sha1(new byte[]{2})).substring(0, 39);
            Files.write(storeDir.resolve(hex39 + ".png"), png); // 39 hex chars = foreign
            Files.write(storeDir.resolve(NewsPictureLibrary.toHex(
                    NewsPictureLibrary.sha1(new byte[]{3})) + ".txt"), png); // hex but not .png

            store.retainOnly(List.of()); // nothing referenced → only the real entry goes

            TestResult r = assertEquals("the store's own entry must be gone",
                    0, store.listHashes().size());
            if (!r.passed()) return r;
            try (Stream<Path> files = Files.list(storeDir)) {
                return assertEquals("all 5 foreign files must be untouched",
                        5L, files.count());
            }
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // NewsRecord picture hash — persistence & wire
    // ========================================================================

    /** NBT save + load must reproduce the picture hash exactly. */
    private TestResult test_record_nbtRoundTripWithHash() {
        NewsRecord original = record(1, "pic_event");
        byte[] hash = NewsPictureLibrary.sha1(validPng(32, 32, 8));
        original.setPictureHash(hash);

        CompoundTag tag = new CompoundTag();
        TestResult r = assertTrue("save must succeed", original.save(tag));
        if (!r.passed()) return r;
        NewsRecord loaded = NewsRecord.createFromTag(tag);
        r = assertNotNull("load must succeed", loaded);
        if (!r.passed()) return r;
        r = assertTrue("picture hash must survive the NBT round-trip",
                Arrays.equals(hash, loaded.getPictureHash()));
        if (!r.passed()) return r;
        return assertEquals("all other fields must survive too (equals incl. hash)",
                original, loaded);
    }

    /** Text-only records write no picture tag and load with a null hash. */
    private TestResult test_record_nbtWithoutHashStaysHashless() {
        NewsRecord original = record(2, "text_event");
        CompoundTag tag = new CompoundTag();
        original.save(tag);

        TestResult r = assertFalse("no pictureHash tag must be written for text-only records",
                tag.contains("pictureHash"));
        if (!r.passed()) return r;
        NewsRecord loaded = NewsRecord.createFromTag(tag);
        r = assertNotNull("load must succeed", loaded);
        if (!r.passed()) return r;
        return assertNull("loaded record must have a null hash", loaded.getPictureHash());
    }

    /**
     * A tag in the exact pre-T-088 shape (no pictureHash key — which is precisely what
     * an old history.nbt contains) must load unchanged with a null hash.
     */
    private TestResult test_record_oldNbtLoadsNullHash() {
        // Simulate an old on-disk record: save a hashless record (identical bytes to a
        // pre-picture save), then load it into a record that HAD a hash — the load
        // must clear it (no stale state).
        NewsRecord oldFormat = record(3, "legacy_event");
        CompoundTag tag = new CompoundTag();
        oldFormat.save(tag);

        NewsRecord target = record(99, "other");
        target.setPictureHash(NewsPictureLibrary.sha1(new byte[]{42}));
        TestResult r = assertTrue("load of an old-format tag must succeed", target.load(tag));
        if (!r.passed()) return r;
        r = assertNull("old-format tag must yield a null hash (contains() guard)",
                target.getPictureHash());
        if (!r.passed()) return r;
        return assertEquals("the rest of the record must load normally",
                "legacy_event", target.getEventId());
    }

    /** The setter must reject any non-20-byte hash (kept null). */
    private TestResult test_record_setterRejectsWrongLength() {
        NewsRecord record = record(4, "e");
        record.setPictureHash(new byte[19]);
        TestResult r = assertNull("19-byte hash must be rejected", record.getPictureHash());
        if (!r.passed()) return r;
        record.setPictureHash(new byte[21]);
        r = assertNull("21-byte hash must be rejected", record.getPictureHash());
        if (!r.passed()) return r;
        record.setPictureHash(NewsPictureLibrary.sha1(new byte[]{1}));
        r = assertNotNull("20-byte hash must be accepted", record.getPictureHash());
        if (!r.passed()) return r;
        record.setPictureHash(null);
        return assertNull("null must clear the hash", record.getPictureHash());
    }

    /**
     * The codec must round-trip a LIST of records with mixed null/non-null hashes —
     * exactly the {@code NewsHistoryRequest.OutputData} usage. This is the regression
     * guard for the append-only/unconditional-presence-bool rule: a
     * {@code buf.isReadable()} trailing-field trick would corrupt every element but
     * the last one.
     */
    private TestResult test_record_streamCodecListMixedHashes() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("STREAM_CODEC list round-trip skipped (no registry access in this context)");
        }
        // Same list codec NewsHistoryRequest.OutputData is built on.
        StreamCodec<RegistryFriendlyByteBuf, List<NewsRecord>> listCodec =
                ExtraCodecUtils.listStreamCodec(NewsRecord.STREAM_CODEC);

        NewsRecord withHash = record(10, "a");
        withHash.setPictureHash(NewsPictureLibrary.sha1(validPng(24, 24, 9)));
        NewsRecord withoutHash = record(11, "b");
        NewsRecord withOtherHash = record(12, "c");
        withOtherHash.setPictureHash(NewsPictureLibrary.sha1(validPng(24, 24, 10)));
        List<NewsRecord> original = new ArrayList<>(List.of(withHash, withoutHash, withOtherHash));

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            listCodec.encode(buf, original);
            List<NewsRecord> decoded = listCodec.decode(buf);
            TestResult r = assertEquals("list size must survive", original.size(), decoded.size());
            if (!r.passed()) return r;
            for (int i = 0; i < original.size(); i++) {
                if (!original.get(i).equals(decoded.get(i))) {
                    return fail("element " + i + " corrupted by the list round-trip: "
                            + decoded.get(i));
                }
                if (!Arrays.equals(original.get(i).getPictureHash(),
                        decoded.get(i).getPictureHash())) {
                    return fail("picture hash of element " + i + " corrupted");
                }
            }
            return assertEquals("buffer must be fully consumed (no trailing bytes)",
                    0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    // ========================================================================
    // Publish-path snapshot
    // ========================================================================

    /** Publishing an event with a picture stamps the hash and fills the store. */
    private TestResult test_publish_snapshotsHashAndBytes() {
        Path dir = null;
        try {
            dir = createTempDir();
            byte[] png = validPng(64, 64, 11);
            Path config = newsConfig(dir, eventJson("pic_event", "pic.png"),
                    Map.of("pic.png", png));
            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(config);

            NewsHistory history = new NewsHistory();
            NewsPictureStore store = storeIn(dir);
            ServerNewsPublisher publisher =
                    new ServerNewsPublisher(history, () -> 500, store, () -> library, null);

            NewsRecord record = record(1, "pic_event");
            publisher.publish(record);

            byte[] expectedHash = NewsPictureLibrary.sha1(png);
            TestResult r = assertTrue("record must carry the picture's SHA-1",
                    Arrays.equals(expectedHash, record.getPictureHash()));
            if (!r.passed()) return r;
            r = assertTrue("store must serve the snapshotted bytes",
                    Arrays.equals(png, store.get(expectedHash)));
            if (!r.passed()) return r;
            r = assertEquals("record must be in the history", 1, history.size());
            if (!r.passed()) return r;
            return assertEquals("history must report the referenced hash",
                    1, history.referencedPictureHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A missing picture file never blocks the publish — the record goes out text-only. */
    private TestResult test_publish_missingPictureStaysTextOnly() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Pictures folder is non-empty (defaults stay away) but does NOT contain
            // the referenced missing.png.
            Path config = newsConfig(dir, eventJson("ghost_event", "missing.png"),
                    Map.of("unrelated.png", validPng(32, 32, 12)));
            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(config);

            NewsHistory history = new NewsHistory();
            NewsPictureStore store = storeIn(dir);
            ServerNewsPublisher publisher =
                    new ServerNewsPublisher(history, () -> 500, store, () -> library, null);

            NewsRecord record = record(1, "ghost_event");
            publisher.publish(record); // must not throw
            publisher.publish(record(2, "ghost_event")); // repeat: warn-once path, no spam crash

            TestResult r = assertNull("record must stay text-only", record.getPictureHash());
            if (!r.passed()) return r;
            r = assertEquals("both publishes must reach the history", 2, history.size());
            if (!r.passed()) return r;
            return assertEquals("nothing must be snapshotted", 0, store.listHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The two-layer core promise: after the admin swaps the config picture and rescans,
     * a NEW publish carries the new hash — while the OLD record's hash still resolves
     * against the store (the published snapshot is immutable).
     */
    private TestResult test_publish_configSwapKeepsOldRecordServed() {
        Path dir = null;
        try {
            dir = createTempDir();
            byte[] oldPng = validPng(64, 64, 13);
            byte[] newPng = validPng(64, 64, 14);
            Path config = newsConfig(dir, eventJson("swap_event", "pic.png"),
                    Map.of("pic.png", oldPng));
            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(config);

            NewsHistory history = new NewsHistory();
            NewsPictureStore store = storeIn(dir);
            ServerNewsPublisher publisher =
                    new ServerNewsPublisher(history, () -> 500, store, () -> library, null);

            NewsRecord oldRecord = record(1, "swap_event");
            publisher.publish(oldRecord);

            // Admin swaps the picture bytes and reloads (rescans the picture library).
            Files.write(config.resolve(NewsPictureLibrary.PICTURES_DIR_NAME).resolve("pic.png"),
                    newPng);
            library.reload(config);

            NewsRecord newRecord = record(2, "swap_event");
            publisher.publish(newRecord);

            byte[] oldHash = NewsPictureLibrary.sha1(oldPng);
            byte[] newHash = NewsPictureLibrary.sha1(newPng);
            TestResult r = assertTrue("old record must keep the old content hash",
                    Arrays.equals(oldHash, oldRecord.getPictureHash()));
            if (!r.passed()) return r;
            r = assertTrue("new record must carry the swapped content hash",
                    Arrays.equals(newHash, newRecord.getPictureHash()));
            if (!r.passed()) return r;
            r = assertFalse("the two hashes must differ",
                    Arrays.equals(oldRecord.getPictureHash(), newRecord.getPictureHash()));
            if (!r.passed()) return r;
            r = assertTrue("OLD record's picture must STILL be served from the store",
                    Arrays.equals(oldPng, store.get(oldHash)));
            if (!r.passed()) return r;
            return assertTrue("new record's picture must be served too",
                    Arrays.equals(newPng, store.get(newHash)));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * When the history prunes a record (cap), the publisher's post-append GC must drop
     * the picture nothing references anymore — and keep the surviving one.
     */
    private TestResult test_publish_pruneGc_dropsUnreferencedPicture() {
        Path dir = null;
        try {
            dir = createTempDir();
            byte[] pngA = validPng(48, 48, 15);
            byte[] pngB = validPng(48, 48, 16);
            Path config = newsConfig(dir,
                    eventJson("event_a", "a.png") + "," + eventJson("event_b", "b.png"),
                    Map.of("a.png", pngA, "b.png", pngB));
            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(config);

            NewsHistory history = new NewsHistory();
            NewsPictureStore store = storeIn(dir);
            // Cap 1: the second publish prunes the first record out of the history.
            ServerNewsPublisher publisher =
                    new ServerNewsPublisher(history, () -> 1, store, () -> library, null);

            publisher.publish(record(1, "event_a"));
            publisher.publish(record(2, "event_b"));

            TestResult r = assertEquals("cap 1 must leave one record", 1, history.size());
            if (!r.passed()) return r;
            r = assertNull("pruned record's picture must be garbage-collected",
                    store.get(NewsPictureLibrary.sha1(pngA)));
            if (!r.passed()) return r;
            r = assertTrue("surviving record's picture must remain served",
                    Arrays.equals(pngB, store.get(NewsPictureLibrary.sha1(pngB))));
            if (!r.passed()) return r;
            return assertEquals("store must hold exactly the surviving picture",
                    1, store.listHashes().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }
}
