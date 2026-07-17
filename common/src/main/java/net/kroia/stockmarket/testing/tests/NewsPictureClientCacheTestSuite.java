package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.networking.request.NewsPictureRequest;
import net.kroia.stockmarket.networking.request.NewsPictureRequest.Picture;
import net.kroia.stockmarket.news.ClientNewsPictureCache;
import net.kroia.stockmarket.news.ClientNewsPictureCache.LoadedPicture;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tests for {@link ClientNewsPictureCache} (T-090, picture plan §3/§5, category
 * {@code sm_news_picture_client}).
 * <p>
 * The cache's pure logic — the per-hash state machine, the two-priority batch queue,
 * the single-in-flight rule, the doubling backoff / give-up handling, the LRU texture
 * eviction and {@code releaseAll} — is driven end-to-end through an injected
 * {@link FakeFetcher} (captures batches, responds on demand) and {@link FakeSink}
 * (records register/release calls), with a fully controlled clock. No GL, no
 * networking, no Minecraft context is touched, so the suite runs on BOTH server types.
 * <p>
 * The newsprint pixel conversion ({@link ClientNewsPictureCache#newsprintPixelAbgr})
 * is verified on known pixels in NativeImage's ABGR channel order: black→ink,
 * white→paper, mid-gray→lerp midpoint, alpha preservation and the Rec.601 luminance
 * weighting of pure red/green/blue.
 */
public class NewsPictureClientCacheTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_PICTURE_CLIENT;
    }

    @Override
    public void registerTests() {
        // Queueing & batching
        addTest("invalid_hash_inputs_are_safe", this::test_invalidHashInputsAreSafe);
        addTest("get_texture_enqueues_high_once", this::test_getTextureEnqueuesHighOnce);
        addTest("prefetch_enqueues_background_deduped", this::test_prefetchEnqueuesBackgroundDeduped);
        addTest("high_drains_before_background", this::test_highDrainsBeforeBackground);
        addTest("background_promoted_to_high_on_get", this::test_backgroundPromotedToHighOnGet);
        addTest("batch_capped_at_eight_single_in_flight", this::test_batchCappedAtEightSingleInFlight);

        // Response handling
        addTest("response_loads_and_fires_listener_once", this::test_responseLoadsAndFiresListenerOnce);
        addTest("hash_mismatch_marks_unavailable", this::test_hashMismatchMarksUnavailable);
        addTest("decode_failure_marks_unavailable", this::test_decodeFailureMarksUnavailable);

        // Backoff & give-up
        addTest("missing_from_response_backoff_doubles", this::test_missingFromResponseBackoffDoubles);
        addTest("attempt_cap_gives_up_until_get_texture", this::test_attemptCapGivesUpUntilGetTexture);

        // Texture lifecycle
        addTest("lru_evicts_least_recently_used", this::test_lruEvictsLeastRecentlyUsed);
        addTest("evicted_hash_is_refetchable", this::test_evictedHashIsRefetchable);
        addTest("release_all_frees_everything_and_is_terminal", this::test_releaseAllFreesEverything);

        // Newsprint conversion math
        addTest("newsprint_black_white_endpoints", this::test_newsprintBlackWhiteEndpoints);
        addTest("newsprint_midgray_lerp_and_alpha", this::test_newsprintMidgrayLerpAndAlpha);
        addTest("newsprint_rgb_luminance_weights", this::test_newsprintRgbLuminanceWeights);
    }

    // ========================================================================
    // Fakes & fixture
    // ========================================================================

    /** Captures every sent batch (as hex keys) and lets the test respond on demand. */
    private static final class FakeFetcher implements ClientNewsPictureCache.PictureFetcher {
        final List<List<String>> sentBatches = new ArrayList<>();
        final List<Consumer<List<Picture>>> callbacks = new ArrayList<>();

        @Override
        public void fetch(List<byte[]> hashes, Consumer<List<Picture>> onResponse) {
            List<String> hex = new ArrayList<>(hashes.size());
            for (byte[] hash : hashes) {
                hex.add(NewsPictureLibrary.toHex(hash));
            }
            sentBatches.add(hex);
            callbacks.add(onResponse);
        }

        int requestCount() {
            return sentBatches.size();
        }

        List<String> lastBatch() {
            return sentBatches.get(sentBatches.size() - 1);
        }

        /** Delivers the response of the newest outstanding request (test = "main thread"). */
        void respondLast(List<Picture> pictures) {
            callbacks.get(callbacks.size() - 1).accept(pictures);
        }
    }

    /** Records register/release calls; {@code failKeys} simulates corrupt PNG decodes. */
    private static final class FakeSink implements ClientNewsPictureCache.TextureSink {
        final List<String> registered = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final Set<String> failKeys = new HashSet<>();

        @Override
        public @Nullable LoadedPicture register(String sha1Hex, byte[] pngBytes) {
            if (failKeys.contains(sha1Hex)) return null; // simulated decode failure
            registered.add(sha1Hex);
            return new LoadedPicture(
                    ResourceLocation.fromNamespaceAndPath("stockmarket",
                            ClientNewsPictureCache.TEXTURE_PATH_PREFIX + sha1Hex),
                    32, 24, null);
        }

        @Override
        public void release(LoadedPicture picture) {
            released.add(picture.location().getPath()
                    .substring(ClientNewsPictureCache.TEXTURE_PATH_PREFIX.length()));
        }
    }

    /** Cache + fakes + controllable clock bundled per test. */
    private static final class Fixture {
        final long[] clock = {1_000_000L};
        final FakeFetcher fetcher = new FakeFetcher();
        final FakeSink sink = new FakeSink();
        final ClientNewsPictureCache cache;

        Fixture() {
            this(ClientNewsPictureCache.DEFAULT_MAX_LOADED_TEXTURES);
        }

        Fixture(int lruCap) {
            cache = new ClientNewsPictureCache(fetcher, sink, () -> clock[0], lruCap);
        }

        void advance(long millis) {
            clock[0] += millis;
        }
    }

    /** Deterministic fake PNG payload (the fake sink never decodes it). */
    private static byte[] pngBytes(int seed) {
        byte[] bytes = new byte[128];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 7 + seed * 13) & 0xFF);
        }
        return bytes;
    }

    /** Content hash of {@link #pngBytes}. */
    private static byte[] hashOf(int seed) {
        return NewsPictureLibrary.sha1(pngBytes(seed));
    }

    private static String hex(byte[] hash) {
        return NewsPictureLibrary.toHex(hash);
    }

    /** A correctly-hashed served picture for the given seed. */
    private static Picture served(int seed) {
        return new Picture(hashOf(seed), pngBytes(seed));
    }

    // ========================================================================
    // Queueing & batching
    // ========================================================================

    /** Null and wrong-length hashes are cheap no-ops everywhere — nothing is fetched. */
    private TestResult test_invalidHashInputsAreSafe() {
        Fixture fx = new Fixture();
        TestResult r = assertNull("null hash must yield null", fx.cache.getTexture(null));
        if (!r.passed()) return r;
        r = assertNull("19-byte hash must yield null", fx.cache.getTexture(new byte[19]));
        if (!r.passed()) return r;
        r = assertNull("21-byte hash must yield null", fx.cache.getTexture(new byte[21]));
        if (!r.passed()) return r;
        fx.cache.prefetch(null);
        fx.cache.prefetch(new byte[5]);
        fx.cache.prefetchAll(null);
        List<byte[]> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add(new byte[3]);
        fx.cache.prefetchAll(mixed);
        fx.cache.clientTick();
        return assertEquals("no request may be sent for invalid inputs",
                0, fx.fetcher.requestCount());
    }

    /** Repeated getTexture calls enqueue the hash exactly once (HIGH). */
    private TestResult test_getTextureEnqueuesHighOnce() {
        Fixture fx = new Fixture();
        byte[] hash = hashOf(1);
        TestResult r = assertNull("first call must return null (not yet loaded)",
                fx.cache.getTexture(hash));
        if (!r.passed()) return r;
        fx.cache.getTexture(hash); // duplicate — must not enqueue twice
        fx.cache.getTexture(hash.clone()); // equal content, different array identity
        fx.cache.clientTick();
        r = assertEquals("exactly one request must be sent", 1, fx.fetcher.requestCount());
        if (!r.passed()) return r;
        return assertEquals("the batch must contain the hash exactly once",
                List.of(hex(hash)), fx.fetcher.lastBatch());
    }

    /** prefetch/prefetchAll dedup against every known state. */
    private TestResult test_prefetchEnqueuesBackgroundDeduped() {
        Fixture fx = new Fixture();
        byte[] hash = hashOf(2);
        fx.cache.prefetch(hash);
        fx.cache.prefetch(hash);
        fx.cache.prefetchAll(List.of(hash, hash.clone()));
        fx.cache.clientTick();
        TestResult r = assertEquals("exactly one request must be sent", 1, fx.fetcher.requestCount());
        if (!r.passed()) return r;
        return assertEquals("the batch must contain the hash exactly once",
                List.of(hex(hash)), fx.fetcher.lastBatch());
    }

    /** HIGH (getTexture) hashes drain strictly before BACKGROUND (prefetch) hashes. */
    private TestResult test_highDrainsBeforeBackground() {
        Fixture fx = new Fixture();
        byte[] bgA = hashOf(10);
        byte[] bgB = hashOf(11);
        byte[] high = hashOf(12);
        fx.cache.prefetch(bgA);
        fx.cache.prefetch(bgB);
        fx.cache.getTexture(high); // enqueued later but at HIGH priority
        fx.cache.clientTick();
        return assertEquals("HIGH must precede BACKGROUND in the batch",
                List.of(hex(high), hex(bgA), hex(bgB)), fx.fetcher.lastBatch());
    }

    /** A background-queued hash is promoted to HIGH when a widget asks for it. */
    private TestResult test_backgroundPromotedToHighOnGet() {
        Fixture fx = new Fixture();
        byte[] first = hashOf(20);
        byte[] second = hashOf(21);
        fx.cache.prefetch(first);
        fx.cache.prefetch(second);
        fx.cache.getTexture(second); // promote: second must now outrank first
        fx.cache.clientTick();
        return assertEquals("the promoted hash must drain first (stale BG key skipped)",
                List.of(hex(second), hex(first)), fx.fetcher.lastBatch());
    }

    /**
     * A batch never exceeds {@value NewsPictureRequest#MAX_HASHES_PER_REQUEST} hashes,
     * only one request is in flight at a time, and the remainder follows after the
     * response.
     */
    private TestResult test_batchCappedAtEightSingleInFlight() {
        Fixture fx = new Fixture();
        int total = 12;
        for (int i = 0; i < total; i++) {
            fx.cache.prefetch(hashOf(30 + i));
        }
        fx.cache.clientTick();
        TestResult r = assertEquals("first batch must be capped",
                NewsPictureRequest.MAX_HASHES_PER_REQUEST, fx.fetcher.lastBatch().size());
        if (!r.passed()) return r;

        fx.cache.clientTick(); // previous request still unanswered
        r = assertEquals("no second request while one is in flight",
                1, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        List<Picture> found = new ArrayList<>();
        for (int i = 0; i < NewsPictureRequest.MAX_HASHES_PER_REQUEST; i++) {
            found.add(served(30 + i));
        }
        fx.fetcher.respondLast(found);
        fx.cache.clientTick();
        r = assertEquals("the remainder must be sent after the response",
                2, fx.fetcher.requestCount());
        if (!r.passed()) return r;
        return assertEquals("the second batch must hold the remaining hashes",
                total - NewsPictureRequest.MAX_HASHES_PER_REQUEST, fx.fetcher.lastBatch().size());
    }

    // ========================================================================
    // Response handling
    // ========================================================================

    /** Served pictures become LOADED, the listener fires exactly once per batch. */
    private TestResult test_responseLoadsAndFiresListenerOnce() {
        Fixture fx = new Fixture();
        int[] listenerCalls = {0};
        fx.cache.setChangeListener(() -> listenerCalls[0]++);

        byte[] hashA = hashOf(40);
        byte[] hashB = hashOf(41);
        fx.cache.getTexture(hashA);
        fx.cache.getTexture(hashB);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(40), served(41)));

        TestResult r = assertEquals("listener must fire exactly once for the batch",
                1, listenerCalls[0]);
        if (!r.passed()) return r;
        r = assertEquals("both textures must be loaded", 2, fx.cache.loadedCount());
        if (!r.passed()) return r;

        LoadedPicture picture = fx.cache.getTexture(hashA);
        r = assertNotNull("the loaded picture must now be returned", picture);
        if (!r.passed()) return r;
        r = assertEquals("texture location must be the stable hash path",
                ClientNewsPictureCache.TEXTURE_PATH_PREFIX + hex(hashA),
                picture.location().getPath());
        if (!r.passed()) return r;
        r = assertEquals("width must come from the sink", 32, picture.width());
        if (!r.passed()) return r;
        return assertEquals("height must come from the sink", 24, picture.height());
    }

    /** Bytes that do not hash to the requested value are rejected (defense in depth). */
    private TestResult test_hashMismatchMarksUnavailable() {
        Fixture fx = new Fixture();
        byte[] hash = hashOf(50);
        fx.cache.getTexture(hash);
        fx.cache.clientTick();
        // Server echoes the requested hash but serves DIFFERENT bytes.
        fx.fetcher.respondLast(List.of(new Picture(hash, pngBytes(999))));

        TestResult r = assertTrue("mismatched bytes must never reach the sink",
                fx.sink.registered.isEmpty());
        if (!r.passed()) return r;
        r = assertNull("the hash must not be loaded", fx.cache.getTexture(hash));
        if (!r.passed()) return r;
        // Long park: neither the getTexture above nor ticks may re-request it yet.
        fx.cache.clientTick();
        r = assertEquals("no retry before the give-up park expired",
                1, fx.fetcher.requestCount());
        if (!r.passed()) return r;
        // After the park a getTexture re-enqueues it (at HIGH).
        fx.advance(ClientNewsPictureCache.GIVE_UP_RETRY_MILLIS + 1);
        fx.cache.getTexture(hash);
        fx.cache.clientTick();
        return assertEquals("after the park an explicit request must refetch",
                2, fx.fetcher.requestCount());
    }

    /** A decode failure (sink returns null) parks the hash; the listener stays silent. */
    private TestResult test_decodeFailureMarksUnavailable() {
        Fixture fx = new Fixture();
        int[] listenerCalls = {0};
        fx.cache.setChangeListener(() -> listenerCalls[0]++);

        byte[] hash = hashOf(60);
        fx.sink.failKeys.add(hex(hash)); // simulate a corrupt PNG
        fx.cache.getTexture(hash);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(60)));

        TestResult r = assertEquals("nothing loaded → listener must not fire",
                0, listenerCalls[0]);
        if (!r.passed()) return r;
        r = assertEquals("nothing may be loaded", 0, fx.cache.loadedCount());
        if (!r.passed()) return r;
        // Corrupt entries are parked long — no automatic retry storm.
        fx.advance(ClientNewsPictureCache.MAX_RETRY_DELAY_MILLIS + 1);
        fx.cache.clientTick();
        return assertEquals("no automatic retry for a corrupt picture",
                1, fx.fetcher.requestCount());
    }

    // ========================================================================
    // Backoff & give-up
    // ========================================================================

    /** A hash missing from the response retries automatically with a doubling delay. */
    private TestResult test_missingFromResponseBackoffDoubles() {
        Fixture fx = new Fixture();
        byte[] hash = hashOf(70);
        fx.cache.prefetch(hash);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of()); // attempt 1 failed → retry in 5 s

        fx.cache.clientTick();
        TestResult r = assertEquals("no immediate re-request", 1, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        fx.advance(ClientNewsPictureCache.INITIAL_RETRY_DELAY_MILLIS - 1);
        fx.cache.clientTick();
        r = assertEquals("still parked just before the first backoff expires",
                1, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        fx.advance(1); // first backoff (5 s) expired
        fx.cache.clientTick();
        r = assertEquals("first automatic retry after 5 s", 2, fx.fetcher.requestCount());
        if (!r.passed()) return r;
        r = assertEquals("the retry must request the same hash",
                List.of(hex(hash)), fx.fetcher.lastBatch());
        if (!r.passed()) return r;

        fx.fetcher.respondLast(List.of()); // attempt 2 failed → retry in 10 s
        fx.advance(ClientNewsPictureCache.INITIAL_RETRY_DELAY_MILLIS);
        fx.cache.clientTick();
        r = assertEquals("second backoff must have doubled (5 s is not enough)",
                2, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        fx.advance(ClientNewsPictureCache.INITIAL_RETRY_DELAY_MILLIS); // total 10 s
        fx.cache.clientTick();
        return assertEquals("second automatic retry after the doubled 10 s",
                3, fx.fetcher.requestCount());
    }

    /**
     * After {@value ClientNewsPictureCache#MAX_FETCH_ATTEMPTS} failed attempts the hash
     * stops retrying automatically; only a {@link ClientNewsPictureCache#getTexture}
     * call after the long park re-enqueues it.
     */
    private TestResult test_attemptCapGivesUpUntilGetTexture() {
        Fixture fx = new Fixture();
        byte[] hash = hashOf(80);
        fx.cache.prefetch(hash);

        // Burn through all attempts; advancing past the max backoff each round.
        for (int attempt = 1; attempt <= ClientNewsPictureCache.MAX_FETCH_ATTEMPTS; attempt++) {
            fx.cache.clientTick();
            TestResult r = assertEquals("attempt " + attempt + " must send a request",
                    attempt, fx.fetcher.requestCount());
            if (!r.passed()) return r;
            fx.fetcher.respondLast(List.of());
            fx.advance(ClientNewsPictureCache.MAX_RETRY_DELAY_MILLIS + 1);
        }

        // Given up: no automatic retry even though every backoff delay passed.
        fx.cache.clientTick();
        TestResult r = assertEquals("after the attempt cap no automatic retry may happen",
                ClientNewsPictureCache.MAX_FETCH_ATTEMPTS, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        // getTexture before the long park expired: cheap null, still no request.
        r = assertNull("given-up hash must answer null", fx.cache.getTexture(hash));
        if (!r.passed()) return r;
        fx.cache.clientTick();
        r = assertEquals("getTexture inside the park must not refetch",
                ClientNewsPictureCache.MAX_FETCH_ATTEMPTS, fx.fetcher.requestCount());
        if (!r.passed()) return r;

        // getTexture after the park: re-enqueued at HIGH.
        fx.advance(ClientNewsPictureCache.GIVE_UP_RETRY_MILLIS + 1);
        fx.cache.getTexture(hash);
        fx.cache.clientTick();
        return assertEquals("getTexture after the park must trigger a refetch",
                ClientNewsPictureCache.MAX_FETCH_ATTEMPTS + 1, fx.fetcher.requestCount());
    }

    // ========================================================================
    // Texture lifecycle
    // ========================================================================

    /** The LRU evicts the least-recently-USED texture (touch via getTexture counts). */
    private TestResult test_lruEvictsLeastRecentlyUsed() {
        Fixture fx = new Fixture(3); // tiny cap for the test
        byte[] hashA = hashOf(90);
        byte[] hashB = hashOf(91);
        byte[] hashC = hashOf(92);
        fx.cache.prefetch(hashA);
        fx.cache.prefetch(hashB);
        fx.cache.prefetch(hashC);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(90), served(91), served(92)));

        TestResult r = assertEquals("three textures loaded", 3, fx.cache.loadedCount());
        if (!r.passed()) return r;

        fx.cache.getTexture(hashA); // touch A → LRU order is now B, C, A

        fx.cache.prefetch(hashOf(93)); // fourth picture overflows the cap of 3
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(93)));

        r = assertEquals("cap must hold after the overflow", 3, fx.cache.loadedCount());
        if (!r.passed()) return r;
        r = assertEquals("exactly one texture must have been released",
                1, fx.sink.released.size());
        if (!r.passed()) return r;
        r = assertEquals("the least-recently-used texture (B, not the touched A) must go",
                hex(hashB), fx.sink.released.get(0));
        if (!r.passed()) return r;
        return assertNotNull("the touched texture must survive", fx.cache.getTexture(hashA));
    }

    /** An evicted hash falls back to ABSENT and is transparently re-fetchable. */
    private TestResult test_evictedHashIsRefetchable() {
        Fixture fx = new Fixture(1);
        byte[] first = hashOf(100);
        byte[] second = hashOf(101);
        fx.cache.getTexture(first);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(100)));
        fx.cache.getTexture(second);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(101))); // evicts 'first' (cap 1)

        TestResult r = assertEquals("the first texture must have been released",
                List.of(hex(first)), fx.sink.released);
        if (!r.passed()) return r;
        r = assertNull("the evicted hash must read as not-loaded again",
                fx.cache.getTexture(first)); // ← also re-enqueues it at HIGH
        if (!r.passed()) return r;
        fx.cache.clientTick();
        return assertEquals("the evicted hash must be re-requested",
                List.of(hex(first)), fx.fetcher.lastBatch());
    }

    /** releaseAll frees every texture and turns the cache into a terminal no-op. */
    private TestResult test_releaseAllFreesEverything() {
        Fixture fx = new Fixture();
        byte[] hashA = hashOf(110);
        byte[] hashB = hashOf(111);
        fx.cache.getTexture(hashA);
        fx.cache.getTexture(hashB);
        fx.cache.clientTick();
        fx.fetcher.respondLast(List.of(served(110), served(111)));

        fx.cache.releaseAll();
        TestResult r = assertEquals("both textures must be released",
                2, fx.sink.released.size());
        if (!r.passed()) return r;
        r = assertTrue("the cache must report itself terminal", fx.cache.isReleased());
        if (!r.passed()) return r;
        r = assertEquals("no textures may remain", 0, fx.cache.loadedCount());
        if (!r.passed()) return r;

        // Terminal: nothing may be handed out or fetched anymore.
        r = assertNull("getTexture after releaseAll must be null",
                fx.cache.getTexture(hashA));
        if (!r.passed()) return r;
        fx.cache.prefetch(hashOf(112));
        fx.cache.clientTick();
        return assertEquals("no request may be sent after releaseAll",
                1, fx.fetcher.requestCount());
    }

    // ========================================================================
    // Newsprint conversion math (pure ints, ABGR channel order)
    // ========================================================================

    /** Packs one ABGR pixel the way NativeImage RGBA-format pixel access sees it. */
    private static int abgr(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static int alphaOf(int abgr) { return (abgr >>> 24) & 0xFF; }
    private static int blueOf(int abgr)  { return (abgr >>> 16) & 0xFF; }
    private static int greenOf(int abgr) { return (abgr >>> 8) & 0xFF; }
    private static int redOf(int abgr)   { return abgr & 0xFF; }

    /** Black maps to exactly COLOR_INK, white to exactly COLOR_ENTRY_PAPER (ARGB→ABGR). */
    private TestResult test_newsprintBlackWhiteEndpoints() {
        int black = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0xFF, 0, 0, 0));
        int ink = ClientNewsPictureCache.COLOR_INK; // ARGB
        TestResult r = assertEquals("black red channel must be ink red",
                (ink >>> 16) & 0xFF, redOf(black));
        if (!r.passed()) return r;
        r = assertEquals("black green channel must be ink green",
                (ink >>> 8) & 0xFF, greenOf(black));
        if (!r.passed()) return r;
        r = assertEquals("black blue channel must be ink blue",
                ink & 0xFF, blueOf(black));
        if (!r.passed()) return r;
        r = assertEquals("black alpha must be preserved", 0xFF, alphaOf(black));
        if (!r.passed()) return r;

        int white = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0xFF, 255, 255, 255));
        int paper = ClientNewsPictureCache.COLOR_ENTRY_PAPER; // ARGB
        r = assertEquals("white red channel must be paper red",
                (paper >>> 16) & 0xFF, redOf(white));
        if (!r.passed()) return r;
        r = assertEquals("white green channel must be paper green",
                (paper >>> 8) & 0xFF, greenOf(white));
        if (!r.passed()) return r;
        return assertEquals("white blue channel must be paper blue",
                paper & 0xFF, blueOf(white));
    }

    /** Mid-gray lands on the ink→paper lerp midpoint; a translucent alpha survives. */
    private TestResult test_newsprintMidgrayLerpAndAlpha() {
        // lum(127,127,127) = (127·299 + 127·587 + 127·114 + 500)/1000 = 127
        // R: 0x1E=30  + (0xF3=243 − 30)·127/255 = 30 + 106 = 136
        // G: 0x1B=27  + (0xED=237 − 27)·127/255 = 27 + 104 = 131
        // B: 0x16=22  + (0xDE=222 − 22)·127/255 = 22 +  99 = 121
        int gray = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0x80, 127, 127, 127));
        TestResult r = assertEquals("mid-gray red must hit the lerp midpoint", 136, redOf(gray));
        if (!r.passed()) return r;
        r = assertEquals("mid-gray green must hit the lerp midpoint", 131, greenOf(gray));
        if (!r.passed()) return r;
        r = assertEquals("mid-gray blue must hit the lerp midpoint", 121, blueOf(gray));
        if (!r.passed()) return r;
        return assertEquals("translucent alpha must be preserved unchanged",
                0x80, alphaOf(gray));
    }

    /** Pure red/green/blue map through the Rec.601 weights (0.299/0.587/0.114). */
    private TestResult test_newsprintRgbLuminanceWeights() {
        // lum(red)   = (255·299 + 500)/1000 =  76 → R: 30 + 213· 76/255 = 30 +  63 =  93
        // lum(green) = (255·587 + 500)/1000 = 150 → R: 30 + 213·150/255 = 30 + 125 = 155
        // lum(blue)  = (255·114 + 500)/1000 =  29 → R: 30 + 213· 29/255 = 30 +  24 =  54
        int red = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0xFF, 255, 0, 0));
        int green = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0xFF, 0, 255, 0));
        int blue = ClientNewsPictureCache.newsprintPixelAbgr(abgr(0xFF, 0, 0, 255));

        TestResult r = assertEquals("pure red must map through lum 76", 93, redOf(red));
        if (!r.passed()) return r;
        r = assertEquals("pure green must map through lum 150", 155, redOf(green));
        if (!r.passed()) return r;
        r = assertEquals("pure blue must map through lum 29", 54, redOf(blue));
        if (!r.passed()) return r;
        // The weight ordering must show as brightness: green > red > blue.
        r = assertTrue("green must come out brighter than red", redOf(green) > redOf(red));
        if (!r.passed()) return r;
        return assertTrue("red must come out brighter than blue", redOf(red) > redOf(blue));
    }
}
