package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.networking.request.NewsPictureRequest;
import net.kroia.stockmarket.networking.request.NewsPictureRequest.InputData;
import net.kroia.stockmarket.networking.request.NewsPictureRequest.OutputData;
import net.kroia.stockmarket.networking.request.NewsPictureRequest.Picture;
import net.kroia.stockmarket.networking.request.NewsPictureRequest.RateLimiter;
import net.kroia.stockmarket.news.DefaultNewsPictures;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsPictureStore;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Master-only tests for {@link NewsPictureRequest} (T-089, picture plan §4.2/§10,
 * category {@code sm_news_picture_request}): hash-batch serving from a temp-dir
 * {@link NewsPictureStore} via the testable static core
 * {@link NewsPictureRequest#servePictures} — known/unknown hashes, full batch of
 * {@value NewsPictureRequest#MAX_HASHES_PER_REQUEST}, malformed hash lengths,
 * response byte-budget truncation (skip-and-continue), the per-player sliding-window
 * rate limiter (hash cap, byte cap, window expiry, player isolation, warn-once), the
 * anonymous-sender guard of the handler, and the defensive decode-side caps of the
 * hand-written wire codecs.
 * <p>
 * All store fixtures live in temp directories with private {@link RateLimiter}
 * instances and injected clock values — the world's real picture store and the live
 * limiter of the request singleton are never touched. The actual client→server(→master)
 * transport is verified in-game (task T-089 acceptance criteria).
 */
public class NewsPictureRequestTestSuite extends TestSuite {

    /** Fixed fake wall-clock base for all limiter tests (no sleeping). */
    private static final long NOW = 1_000_000L;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_PICTURE_REQUEST;
    }

    @Override
    public void registerTests() {
        // Serving basics
        addTest("known_hash_served", this::test_knownHashServed);
        addTest("unknown_hash_omitted", this::test_unknownHashOmitted);
        addTest("batch_of_eight_served", this::test_batchOfEightServed);
        addTest("malformed_hash_lengths_skipped", this::test_malformedHashLengthsSkipped);
        addTest("oversized_store_entry_omitted", this::test_oversizedStoreEntryOmitted);
        addTest("byte_budget_truncation_skip_and_continue", this::test_byteBudgetTruncation);

        // Rate limiter
        addTest("limiter_under_limit_passes", this::test_limiter_underLimitPasses);
        addTest("limiter_hash_count_exceeded_yields_empty", this::test_limiter_hashCountExceeded);
        addTest("limiter_byte_cap_exceeded_yields_empty", this::test_limiter_byteCapExceeded);
        addTest("limiter_window_expiry_serves_again", this::test_limiter_windowExpiryServesAgain);
        addTest("limiter_per_player_isolation", this::test_limiter_perPlayerIsolation);
        addTest("limiter_warns_once_per_window", this::test_limiter_warnsOncePerWindow);

        // Handler guard & wire format
        addTest("null_sender_gets_default_response", this::test_nullSenderGetsDefaultResponse);
        addTest("input_codec_caps_claimed_count", this::test_inputCodec_capsClaimedCount);
        addTest("wire_codec_roundtrip", this::test_wireCodecRoundTrip);
        addTest("output_codec_rejects_oversized_png", this::test_outputCodec_rejectsOversizedPng);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Creates a temp directory for store fixtures (never a live folder). */
    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_picture_request_test");
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

    /** Encodes a small valid grayscale PNG with a seed-dependent pattern. */
    private static byte[] validPng(int size, int seed) {
        byte[] pixels = new byte[size * size];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) ((i * 7 + seed * 31) & 0xFF);
        }
        return DefaultNewsPictures.encodeGrayscalePng(size, size, pixels);
    }

    /**
     * A deterministic opaque byte blob of an exact size — the request layer treats
     * pictures as opaque bytes and the store only verifies the SHA-1, so exact-size
     * blobs give precise budget/limiter arithmetic without PNG encoding overhead.
     */
    private static byte[] blob(int size, int seed) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i * 31 + seed * 131 + (i >> 8)) & 0xFF);
        }
        return data;
    }

    /** Puts the bytes into the store and returns their content hash. */
    private static byte[] put(NewsPictureStore store, byte[] bytes) {
        byte[] hash = NewsPictureLibrary.sha1(bytes);
        if (!store.put(hash, bytes)) {
            throw new IllegalStateException("test fixture put() failed");
        }
        return hash;
    }

    /** Shortcut to the testable static core. */
    private static OutputData serve(NewsPictureStore store, RateLimiter limiter,
                                    UUID player, List<byte[]> hashes, long now) {
        return NewsPictureRequest.servePictures(store, limiter, player, hashes, now);
    }

    /** The served hashes as lowercase hex, in response order. */
    private static List<String> hexOf(OutputData output) {
        List<String> hex = new ArrayList<>(output.pictures().size());
        for (Picture picture : output.pictures()) {
            hex.add(NewsPictureLibrary.toHex(picture.hash()));
        }
        return hex;
    }

    /** Total raw picture bytes of a response. */
    private static long bytesOf(OutputData output) {
        long total = 0;
        for (Picture picture : output.pictures()) {
            total += picture.pngBytes().length;
        }
        return total;
    }

    /** Stores {@code count} distinct small pictures and returns their hashes. */
    private static List<byte[]> smallPictures(NewsPictureStore store, int count) {
        List<byte[]> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hashes.add(put(store, blob(1024, 100 + i)));
        }
        return hashes;
    }

    // ========================================================================
    // Serving basics
    // ========================================================================

    /** A stored hash is answered with exactly its snapshotted bytes. */
    private TestResult test_knownHashServed() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] png = validPng(32, 1);
            byte[] hash = put(store, png);

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(),
                    List.of(hash), NOW);
            TestResult r = assertEquals("exactly one picture must be served",
                    1, output.pictures().size());
            if (!r.passed()) return r;
            r = assertTrue("the requested hash must be echoed back",
                    Arrays.equals(hash, output.pictures().get(0).hash()));
            if (!r.passed()) return r;
            return assertTrue("the exact stored PNG bytes must be served",
                    Arrays.equals(png, output.pictures().get(0).pngBytes()));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Unknown (but well-formed) hashes are silently omitted, known ones still served. */
    private TestResult test_unknownHashOmitted() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] known = put(store, blob(2048, 2));
            byte[] unknownA = NewsPictureLibrary.sha1(new byte[]{1, 2, 3});
            byte[] unknownB = NewsPictureLibrary.sha1(new byte[]{4, 5, 6});

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(),
                    List.of(unknownA, known, unknownB), NOW);
            TestResult r = assertEquals("only the known hash must be answered",
                    1, output.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("the answered entry must be the known hash",
                    NewsPictureLibrary.toHex(known), hexOf(output).get(0));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A full batch of 8 small pictures is served completely, in request order. */
    private TestResult test_batchOfEightServed() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> hashes = smallPictures(store, NewsPictureRequest.MAX_HASHES_PER_REQUEST);

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(), hashes, NOW);
            TestResult r = assertEquals("all 8 pictures must be served",
                    NewsPictureRequest.MAX_HASHES_PER_REQUEST, output.pictures().size());
            if (!r.passed()) return r;
            List<String> expected = new ArrayList<>();
            for (byte[] hash : hashes) expected.add(NewsPictureLibrary.toHex(hash));
            return assertEquals("response must preserve the request order",
                    expected, hexOf(output));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Malformed hash lengths (null / 0 / 19 / 21 bytes) are skipped, never throw. */
    private TestResult test_malformedHashLengthsSkipped() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] valid = put(store, blob(1024, 3));

            List<byte[]> request = new ArrayList<>();
            request.add(new byte[19]);
            request.add(null);
            request.add(valid);
            request.add(new byte[21]);
            request.add(new byte[0]);

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(), request, NOW);
            TestResult r = assertEquals("only the well-formed hash must be answered",
                    1, output.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("the answered entry must be the valid hash",
                    NewsPictureLibrary.toHex(valid), hexOf(output).get(0));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A store file larger than the library file cap (someone replaced it on disk) is
     * defensively never served — the client-side decode cap would reject it anyway.
     */
    private TestResult test_oversizedStoreEntryOmitted() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            byte[] oversized = put(store, blob(NewsPictureLibrary.MAX_FILE_BYTES + 1024, 4));
            byte[] normal = put(store, blob(1024, 5));

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(),
                    List.of(oversized, normal), NOW);
            TestResult r = assertEquals("the oversized entry must be omitted",
                    1, output.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("the normal entry must still be served",
                    NewsPictureLibrary.toHex(normal), hexOf(output).get(0));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Byte-budget truncation with skip-and-continue: five 120 KiB pictures fill the
     * 640 KiB budget, the sixth is omitted — but a later, smaller picture that still
     * fits IS included (the client re-requests only the omitted one).
     */
    private TestResult test_byteBudgetTruncation() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            int bigSize = 120 * 1024; // 5 × 120 KiB = 600 KiB ≤ 640 KiB, 6 × exceeds
            List<byte[]> request = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                request.add(put(store, blob(bigSize, 10 + i)));
            }
            byte[] small = put(store, blob(10 * 1024, 20));
            request.add(small); // 7 hashes total, still within the batch cap

            OutputData output = serve(store, new RateLimiter(), UUID.randomUUID(), request, NOW);
            List<String> served = hexOf(output);

            TestResult r = assertEquals("5 big + 1 small picture must fit the budget",
                    6, output.pictures().size());
            if (!r.passed()) return r;
            r = assertFalse("the over-budget 6th big picture must be omitted",
                    served.contains(NewsPictureLibrary.toHex(request.get(5))));
            if (!r.passed()) return r;
            r = assertTrue("the later small picture must still be included (skip-and-continue)",
                    served.contains(NewsPictureLibrary.toHex(small)));
            if (!r.passed()) return r;
            return assertTrue("total served bytes must respect the budget",
                    bytesOf(output) <= NewsPictureRequest.RESPONSE_BYTE_BUDGET);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Rate limiter
    // ========================================================================

    /** 7 full batches (56 hashes) stay under the 60-hash window — all served, no warn. */
    private TestResult test_limiter_underLimitPasses() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = smallPictures(store, 8);
            RateLimiter limiter = new RateLimiter();
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 7; i++) { // 7 × 8 = 56 ≤ 60
                OutputData output = serve(store, limiter, player, batch, NOW + i * 1000L);
                TestResult r = assertEquals("request " + (i + 1) + " must be fully served",
                        8, output.pictures().size());
                if (!r.passed()) return r;
            }
            return assertEquals("no rate-limit warning must have been emitted",
                    0, limiter.warningCount(player));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** The 8th full batch (64 > 60 hashes) in one window is denied with an empty response. */
    private TestResult test_limiter_hashCountExceeded() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = smallPictures(store, 8);
            RateLimiter limiter = new RateLimiter();
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 7; i++) { // uses up 56 of the 60 hashes
                serve(store, limiter, player, batch, NOW + i * 1000L);
            }
            OutputData denied = serve(store, limiter, player, batch, NOW + 7000L);
            TestResult r = assertEquals("the over-limit request must be answered empty",
                    0, denied.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("the denial must have warned exactly once",
                    1, limiter.warningCount(player));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The byte cap: 6 × 120 KiB pictures serve ~600 KiB per request; after 7 requests
     * the window holds ≥ 4 MiB served bytes (one-budget overshoot is by design), so the
     * 8th request is denied even though the hash count (48) is still under its cap.
     */
    private TestResult test_limiter_byteCapExceeded() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                batch.add(put(store, blob(120 * 1024, 30 + i)));
            }
            RateLimiter limiter = new RateLimiter();
            UUID player = UUID.randomUUID();

            OutputData last = null;
            for (int i = 0; i < 7; i++) { // each serves 5 × 120 KiB = 600 KiB
                last = serve(store, limiter, player, batch, NOW + i * 1000L);
            }
            TestResult r = assertEquals("the 7th request must still be served (bytes below cap before it)",
                    5, last.pictures().size());
            if (!r.passed()) return r;

            OutputData denied = serve(store, limiter, player, batch, NOW + 7000L);
            r = assertEquals("once the window bytes reached 4 MiB the request must be denied",
                    0, denied.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("the byte-cap denial must warn once",
                    1, limiter.warningCount(player));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** After the 60 s window slid past the old grants, the player is served again. */
    private TestResult test_limiter_windowExpiryServesAgain() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = smallPictures(store, 8);
            RateLimiter limiter = new RateLimiter();
            UUID player = UUID.randomUUID();

            for (int i = 0; i < 7; i++) {
                serve(store, limiter, player, batch, NOW);
            }
            OutputData denied = serve(store, limiter, player, batch, NOW + 1000L);
            TestResult r = assertEquals("the player must be rate-limited first",
                    0, denied.pictures().size());
            if (!r.passed()) return r;

            long afterWindow = NOW + NewsPictureRequest.RATE_WINDOW_MILLIS + 1000L;
            OutputData served = serve(store, limiter, player, batch, afterWindow);
            return assertEquals("after the window expired the player must be served again",
                    8, served.pictures().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Rate-limit state is keyed per player UUID — one player's denial never hits another. */
    private TestResult test_limiter_perPlayerIsolation() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = smallPictures(store, 8);
            RateLimiter limiter = new RateLimiter();
            UUID playerA = UUID.randomUUID();
            UUID playerB = UUID.randomUUID();

            for (int i = 0; i < 7; i++) {
                serve(store, limiter, playerA, batch, NOW);
            }
            OutputData deniedA = serve(store, limiter, playerA, batch, NOW + 1000L);
            TestResult r = assertEquals("player A must be rate-limited",
                    0, deniedA.pictures().size());
            if (!r.passed()) return r;

            OutputData servedB = serve(store, limiter, playerB, batch, NOW + 1000L);
            r = assertEquals("player B must be served at the same instant",
                    8, servedB.pictures().size());
            if (!r.passed()) return r;
            return assertEquals("player B must have no warning",
                    0, limiter.warningCount(playerB));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Repeated denials inside one window warn once; a fresh window may warn again. */
    private TestResult test_limiter_warnsOncePerWindow() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureStore store = storeIn(dir);
            List<byte[]> batch = smallPictures(store, 8);
            RateLimiter limiter = new RateLimiter();
            UUID player = UUID.randomUUID();

            // Exhaust the window, then get denied three times in a row.
            for (int i = 0; i < 7; i++) {
                serve(store, limiter, player, batch, NOW);
            }
            serve(store, limiter, player, batch, NOW + 1000L);
            serve(store, limiter, player, batch, NOW + 2000L);
            serve(store, limiter, player, batch, NOW + 3000L);
            TestResult r = assertEquals("three denials in one window must warn exactly once",
                    1, limiter.warningCount(player));
            if (!r.passed()) return r;

            // A fresh window: exhaust again, deny again — a second warn is due.
            long t1 = NOW + NewsPictureRequest.RATE_WINDOW_MILLIS + 5000L;
            for (int i = 0; i < 7; i++) {
                serve(store, limiter, player, batch, t1);
            }
            serve(store, limiter, player, batch, t1 + 100L);
            return assertEquals("a denial in the next window must warn a second time",
                    2, limiter.warningCount(player));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Handler guard & wire format
    // ========================================================================

    /**
     * Requests without a player sender get the empty default response — the guard
     * short-circuits before any backend/store access, so this is safe to call even
     * against the live server.
     */
    private TestResult test_nullSenderGetsDefaultResponse() {
        try {
            NewsPictureRequest request = new NewsPictureRequest();
            byte[] hash = NewsPictureLibrary.sha1(new byte[]{7});
            OutputData output = request.handleOnMasterServer(
                    new InputData(List.of(hash)), "", null).get();
            TestResult r = assertNotNull("response must never be null", output);
            if (!r.passed()) return r;
            return assertTrue("anonymous sender must receive the empty default response",
                    output.pictures().isEmpty());
        } catch (Exception e) {
            return fail("handler threw for a null sender: " + e);
        }
    }

    /**
     * Defensive decode: a buffer claiming far more hashes than the batch cap must only
     * ever yield {@value NewsPictureRequest#MAX_HASHES_PER_REQUEST} decoded entries
     * (the claimed count is never used for the allocation).
     */
    private TestResult test_inputCodec_capsClaimedCount() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("input codec cap test skipped (no registry access in this context)");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            int claimed = 20; // a malicious client claims 20 hashes
            buf.writeVarInt(claimed);
            for (int i = 0; i < claimed; i++) {
                buf.writeByteArray(NewsPictureLibrary.sha1(new byte[]{(byte) i}));
            }
            InputData decoded = InputData.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("decode must hard-cap the list at the batch maximum",
                    NewsPictureRequest.MAX_HASHES_PER_REQUEST, decoded.hashes().size());
            if (!r.passed()) return r;
            return assertTrue("the excess entries must remain unread (never allocated)",
                    buf.readableBytes() > 0);
        } finally {
            buf.release();
        }
    }

    /** Input and output codecs round-trip a normal batch bit-exactly. */
    private TestResult test_wireCodecRoundTrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("wire codec round-trip skipped (no registry access in this context)");
        }
        byte[] hashA = NewsPictureLibrary.sha1(new byte[]{1});
        byte[] hashB = NewsPictureLibrary.sha1(new byte[]{2});
        byte[] hashC = NewsPictureLibrary.sha1(new byte[]{3});

        // Input round-trip
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            InputData input = new InputData(List.of(hashA, hashB, hashC));
            InputData.STREAM_CODEC.encode(buf, input);
            InputData decodedInput = InputData.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("input hash count must survive",
                    3, decodedInput.hashes().size());
            if (!r.passed()) return r;
            for (int i = 0; i < 3; i++) {
                if (!Arrays.equals(input.hashes().get(i), decodedInput.hashes().get(i))) {
                    return fail("input hash " + i + " corrupted by the round-trip");
                }
            }
            r = assertEquals("input buffer must be fully consumed", 0, buf.readableBytes());
            if (!r.passed()) return r;
        } finally {
            buf.release();
        }

        // Output round-trip
        buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            OutputData output = new OutputData(List.of(
                    new Picture(hashA, validPng(24, 40)),
                    new Picture(hashB, blob(4096, 41))));
            OutputData.STREAM_CODEC.encode(buf, output);
            OutputData decodedOutput = OutputData.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("picture count must survive",
                    2, decodedOutput.pictures().size());
            if (!r.passed()) return r;
            for (int i = 0; i < 2; i++) {
                if (!Arrays.equals(output.pictures().get(i).hash(),
                        decodedOutput.pictures().get(i).hash())
                        || !Arrays.equals(output.pictures().get(i).pngBytes(),
                        decodedOutput.pictures().get(i).pngBytes())) {
                    return fail("picture " + i + " corrupted by the round-trip");
                }
            }
            return assertEquals("output buffer must be fully consumed", 0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    /**
     * Defensive decode: PNG bytes beyond the decode cap (file cap + slack) must make
     * the decoder throw instead of allocating a huge array from a hostile buffer.
     */
    private TestResult test_outputCodec_rejectsOversizedPng() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("oversized-png decode test skipped (no registry access in this context)");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            // Encode a picture whose bytes exceed the decode-side cap (the encoder
            // itself does not cap — the server never produces this, only an attacker).
            Picture oversized = new Picture(
                    NewsPictureLibrary.sha1(new byte[]{9}),
                    blob(NewsPictureRequest.MAX_PNG_DECODE_BYTES + 1024, 50));
            Picture.STREAM_CODEC.encode(buf, oversized);
            try {
                Picture decoded = Picture.STREAM_CODEC.decode(buf);
                return fail("oversized png bytes must be rejected, but decoded "
                        + decoded.pngBytes().length + " bytes");
            } catch (Exception expected) {
                return pass("oversized png bytes rejected: " + expected.getClass().getSimpleName());
            }
        } finally {
            buf.release();
        }
    }
}
