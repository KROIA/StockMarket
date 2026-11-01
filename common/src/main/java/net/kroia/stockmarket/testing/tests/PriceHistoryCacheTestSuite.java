package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.market.PriceHistoryCache;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.PriceHistoryData.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game test suite for the client pagination cache
 * {@link PriceHistoryCache} (T-135).
 *
 * <p>Covered invariants:
 * <ul>
 *   <li><b>initial load</b> — an empty cache plus the first response sets
 *       {@code loadedMinTimestamp} / {@code loadedMaxTimestamp} to the response
 *       candles' range and flips {@code hasCache} to {@code true}.</li>
 *   <li><b>backward-extension dedupe</b> — a prepend chunk whose max
 *       {@code openTimestamp} equals the current {@code loadedMinTimestamp}
 *       merges without producing a duplicate boundary candle; the merged list
 *       stays strictly monotonic in {@code openTimestamp} and
 *       {@code loadedMinTimestamp} is extended.</li>
 *   <li><b>live-candle race guard</b> — every candle in the response whose
 *       {@code openTimestamp >= liveCandleOpenTimestamp} is stripped before
 *       merging, so the immutable list never overlaps the mutable live tip.</li>
 *   <li><b>promote-on-tick</b> — {@code promoteLiveCandle} moves the current
 *       live candle into the immutable list (extending
 *       {@code loadedMaxTimestamp}) and leaves the next live candle set through
 *       {@code setLiveCandle} as a separate reference.</li>
 *   <li><b>server-start floor</b> — {@code atServerStart} flips {@code true}
 *       when the response carries {@code NO_OLDER_DATA} or an
 *       {@code oldestAvailableTimestamp >= loadedMinTimestamp}, and stays
 *       {@code false} when the server still has strictly older data than the
 *       cache holds.</li>
 * </ul>
 *
 * <p><b>Deliberately not covered here:</b> the {@code inFlightGuardDedupes}
 * behaviour of {@code ClientMarket.requestOlderWindow} (two overlapping calls
 * returning the same future). That guard lives on
 * {@link net.kroia.stockmarket.stockmarket.market.ClientMarket} and depends on
 * a wired networking backend ({@code BACKEND_INSTANCES.NETWORKING
 * .MARKET_PRICE_HISTORY_REQUEST}) and an in-flight request future. The
 * ModUtilities harness runs pure-logic tests without spinning up a client +
 * packet stack, so exercising this guard would require standing up the entire
 * request/response infrastructure and is not a good fit for the harness. It is
 * validated in-game instead.
 */
public class PriceHistoryCacheTestSuite extends TestSuite
{
    /** Primary candle delta used across the tests (1 minute). */
    private static final long DELTA_MS = 60_000L;
    /** Secondary delta allocated in the cache so single-delta bugs don't hide. */
    private static final long OTHER_DELTA_MS = 300_000L; // 5 minutes
    private static final ItemID DUMMY_ITEM = new ItemID((short) 1);
    private static final int SCALE_FACTOR = 100;

    @Override
    public TestCategory getCategory()
    {
        return StockMarketTestCategories.PRICE_HISTORY_CACHE;
    }

    @Override
    public void registerTests()
    {
        addTest("initial_load_populates_cache", this::test_initialLoadPopulatesCache);
        addTest("backward_extension_merges_without_duplicates", this::test_backwardExtensionMergesWithoutDuplicates);
        addTest("live_candle_not_cached", this::test_liveCandleNotCached);
        addTest("promote_on_tick", this::test_promoteOnTick);
        addTest("server_start_floor_no_older_data", this::test_serverStartFloor_noOlderData);
        addTest("server_start_floor_oldest_covered", this::test_serverStartFloor_oldestCovered);
        addTest("server_start_floor_oldest_below_min", this::test_serverStartFloor_oldestBelowMin);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Builds a minimal Candle with a symmetric high/low around the given price. */
    private static Candle candle(long openTs, long price)
    {
        return new Candle(openTs, price, price + 10, price - 10);
    }

    /**
     * Builds a PriceHistoryData response using the full-arg constructor so the
     * pagination scalars ({@code oldestAvailableTimestamp}, {@code truncated})
     * are honoured by the cache merge.
     */
    private static PriceHistoryData responseWith(List<Candle> candles, long oldestAvailableTimestamp)
    {
        return new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100L, oldestAvailableTimestamp, false);
    }

    /** Fresh cache pre-allocated with two candle deltas. */
    private static PriceHistoryCache freshCache()
    {
        return new PriceHistoryCache(new long[]{DELTA_MS, OTHER_DELTA_MS});
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * Empty cache + first response: loadedMin / loadedMax reflect the candle
     * range; hasCache flips true. Server-start floor stays false when the
     * server still has older data than the response covers.
     */
    private TestResult test_initialLoadPopulatesCache()
    {
        PriceHistoryCache cache = freshCache();
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100_000L, 100));
        candles.add(candle(160_000L, 105));
        candles.add(candle(220_000L, 110));

        // oldest strictly less than loadedMin (100_000) → not at server start.
        PriceHistoryData response = responseWith(candles, 40_000L);

        boolean added = cache.mergeChunk(DELTA_MS, response, Long.MAX_VALUE);

        TestResult r = assertTrue("mergeChunk should report new candles added", added);
        if (!r.passed()) return r;
        r = assertTrue("hasCache should be true after the initial load",
                cache.hasCache(DELTA_MS));
        if (!r.passed()) return r;
        r = assertEquals("loadedMinTimestamp should equal the first candle openTs",
                100_000L, cache.loadedMinTimestamp(DELTA_MS));
        if (!r.passed()) return r;
        r = assertEquals("loadedMaxTimestamp should equal the last candle openTs",
                220_000L, cache.loadedMaxTimestamp(DELTA_MS));
        if (!r.passed()) return r;
        r = assertFalse("atServerStart should stay false when oldest < loadedMin",
                cache.atServerStart(DELTA_MS));
        if (!r.passed()) return r;
        return pass("Initial load populates loadedMin/loadedMax and hasCache");
    }

    /**
     * Prepend a chunk whose max openTimestamp equals the current
     * loadedMinTimestamp: no duplicate boundary candle, loadedMin extended,
     * list monotonic in openTimestamp.
     */
    private TestResult test_backwardExtensionMergesWithoutDuplicates()
    {
        PriceHistoryCache cache = freshCache();

        // Initial (newer) chunk — sets the loadedMin/loadedMax window.
        List<Candle> initial = new ArrayList<>();
        initial.add(candle(200_000L, 100));
        initial.add(candle(260_000L, 105));
        initial.add(candle(320_000L, 110));
        cache.mergeChunk(DELTA_MS, responseWith(initial, 40_000L), Long.MAX_VALUE);

        // Prepend chunk — its max openTs (200_000) equals the current
        // loadedMinTimestamp (200_000). The boundary candle appears in both
        // chunks and must be deduped on merge.
        List<Candle> prepend = new ArrayList<>();
        prepend.add(candle(80_000L, 95));
        prepend.add(candle(140_000L, 97));
        prepend.add(candle(200_000L, 100));
        cache.mergeChunk(DELTA_MS, responseWith(prepend, 40_000L), Long.MAX_VALUE);

        List<Candle> merged = cache.getCandles(DELTA_MS);

        TestResult r = assertEquals("Merged list should hold 5 candles (3+3 with one deduped)",
                5, merged.size());
        if (!r.passed()) return r;
        r = assertEquals("loadedMinTimestamp should be extended to 80_000",
                80_000L, cache.loadedMinTimestamp(DELTA_MS));
        if (!r.passed()) return r;
        r = assertEquals("loadedMaxTimestamp should stay at 320_000",
                320_000L, cache.loadedMaxTimestamp(DELTA_MS));
        if (!r.passed()) return r;

        // Strictly monotonic ascending; boundary openTs appears exactly once.
        long prev = Long.MIN_VALUE;
        int boundaryOccurrences = 0;
        for (Candle c : merged)
        {
            if (c.openTimestamp <= prev)
                return fail("Merged list is not strictly monotonic at openTs="
                        + c.openTimestamp + " (previous=" + prev + ")");
            if (c.openTimestamp == 200_000L)
                boundaryOccurrences++;
            prev = c.openTimestamp;
        }
        r = assertEquals("Boundary openTs must appear exactly once",
                1, boundaryOccurrences);
        if (!r.passed()) return r;
        return pass("Backward extension merges without duplicates, list stays monotonic");
    }

    /**
     * Response candles at or after {@code liveCandleOpenTimestamp} must be
     * dropped — the immutable list must never overlap the mutable live tip.
     */
    private TestResult test_liveCandleNotCached()
    {
        PriceHistoryCache cache = freshCache();

        long liveOpenTs = 500_000L;

        List<Candle> incoming = new ArrayList<>();
        incoming.add(candle(380_000L, 100));  // strictly before live → keep
        incoming.add(candle(440_000L, 101));  // strictly before live → keep
        incoming.add(candle(500_000L, 102));  // at live tip → drop
        incoming.add(candle(560_000L, 103));  // after live tip → drop

        cache.mergeChunk(DELTA_MS,
                responseWith(incoming, PriceHistoryData.NO_OLDER_DATA), liveOpenTs);

        List<Candle> cached = cache.getCandles(DELTA_MS);
        for (Candle c : cached)
        {
            if (c.openTimestamp >= liveOpenTs)
                return fail("Cache contains a candle at/after live tip: openTs="
                        + c.openTimestamp);
        }

        TestResult r = assertEquals("Only candles strictly before the live tip should remain",
                2, cached.size());
        if (!r.passed()) return r;
        r = assertEquals("loadedMaxTimestamp should be the last candle strictly before the live tip",
                440_000L, cache.loadedMaxTimestamp(DELTA_MS));
        if (!r.passed()) return r;
        return pass("Candles at/after the live tip are dropped from the merged cache");
    }

    /**
     * Simulate a candle-boundary tick: set a live candle, promote it, then
     * open a new live candle. The previous live is now the newest immutable
     * candle, {@code loadedMaxTimestamp} moved up, and the freshly-set live
     * candle is stored separately (not in the immutable list).
     */
    private TestResult test_promoteOnTick()
    {
        PriceHistoryCache cache = freshCache();

        // Seed with one immutable candle so promotion is not the first entry.
        List<Candle> seed = new ArrayList<>();
        seed.add(candle(100_000L, 100));
        cache.mergeChunk(DELTA_MS,
                responseWith(seed, PriceHistoryData.NO_OLDER_DATA), Long.MAX_VALUE);

        // Open the first live candle at t=160_000.
        Candle firstLive = candle(160_000L, 105);
        cache.setLiveCandle(DELTA_MS, firstLive);

        // Boundary crossed → promote, then open the next live candle at t=220_000.
        cache.promoteLiveCandle(DELTA_MS);
        Candle secondLive = candle(220_000L, 110);
        cache.setLiveCandle(DELTA_MS, secondLive);

        List<Candle> cached = cache.getCandles(DELTA_MS);
        TestResult r = assertEquals("Cache should hold seed + promoted candle (2 total)",
                2, cached.size());
        if (!r.passed()) return r;
        Candle newestImmutable = cached.get(cached.size() - 1);
        r = assertEquals("Newest immutable candle should be the promoted one",
                160_000L, newestImmutable.openTimestamp);
        if (!r.passed()) return r;
        r = assertEquals("loadedMaxTimestamp should reflect the promoted candle",
                160_000L, cache.loadedMaxTimestamp(DELTA_MS));
        if (!r.passed()) return r;

        Candle live = cache.getLiveCandle(DELTA_MS);
        r = assertNotNull("The freshly-set live candle should be present", live);
        if (!r.passed()) return r;
        r = assertEquals("Live candle openTs should be the new one, not the promoted one",
                220_000L, live.openTimestamp);
        if (!r.passed()) return r;

        // Sanity: the new live candle must NOT already be in the immutable list.
        for (Candle c : cached)
        {
            if (c.openTimestamp == 220_000L)
                return fail("New live candle leaked into the immutable list");
        }
        return pass("promoteLiveCandle moves live into cache and keeps the next live separate");
    }

    /**
     * Server-start floor case A: response carries {@code NO_OLDER_DATA}
     * → {@code atServerStart} flips true regardless of the cache extent.
     */
    private TestResult test_serverStartFloor_noOlderData()
    {
        PriceHistoryCache cache = freshCache();
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100_000L, 100));
        candles.add(candle(160_000L, 105));

        cache.mergeChunk(DELTA_MS,
                responseWith(candles, PriceHistoryData.NO_OLDER_DATA), Long.MAX_VALUE);

        return assertTrue("atServerStart should be true when response reports NO_OLDER_DATA",
                cache.atServerStart(DELTA_MS));
    }

    /**
     * Server-start floor case B: response's {@code oldestAvailableTimestamp}
     * is greater than or equal to {@code loadedMinTimestamp} — the cache
     * already covers back to (or past) the server's oldest recorded candle.
     */
    private TestResult test_serverStartFloor_oldestCovered()
    {
        PriceHistoryCache cache = freshCache();
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100_000L, 100));
        candles.add(candle(160_000L, 105));

        // loadedMin=100_000 <= oldest=150_000 → we already cover the floor.
        cache.mergeChunk(DELTA_MS, responseWith(candles, 150_000L), Long.MAX_VALUE);

        return assertTrue("atServerStart should be true when oldest >= loadedMin",
                cache.atServerStart(DELTA_MS));
    }

    /**
     * Negative case: response's {@code oldestAvailableTimestamp} is strictly
     * less than {@code loadedMinTimestamp} — server has older data than the
     * cache holds, so the floor flag must stay false.
     */
    private TestResult test_serverStartFloor_oldestBelowMin()
    {
        PriceHistoryCache cache = freshCache();
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100_000L, 100));
        candles.add(candle(160_000L, 105));

        cache.mergeChunk(DELTA_MS, responseWith(candles, 40_000L), Long.MAX_VALUE);

        return assertFalse("atServerStart should stay false when oldest < loadedMin",
                cache.atServerStart(DELTA_MS));
    }
}
