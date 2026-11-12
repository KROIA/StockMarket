package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.screen.uiElements.OrderHistoryPanel;
import net.kroia.stockmarket.screen.uiElements.TransactionHistoryPanel;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.PriceHistoryCache;
import net.kroia.stockmarket.testing.ClientThread;
import net.kroia.stockmarket.testing.PacketTap;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.PriceHistoryData.Candle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    public boolean needsGame() {
        return false; // passes offline in gradlew :common:test
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
        // SM-275: client-side, sp cells only
        addTest("display_chart_provider_loads_history", this::test_displayChartProviderLoadsHistory);
        addTest("history_panels_keep_unchanged_rows", this::test_historyPanelsKeepUnchangedRows);
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

    // ------------------------------------------------------------------
    // SM-275: client-side checks (sp cells only; skip offline and elsewhere)
    // ------------------------------------------------------------------

    private static final long CLIENT_WAIT_MS = 10_000L;

    /** @return null when this JVM runs the client next to the server (sp), else the skip result */
    private @Nullable TestResult skipUnlessSp()
    {
        MinecraftServer server = getServer();
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (server == null || server.isDedicatedServer() || client == null || client.MARKET_MANAGER == null)
            return skip("sp only (needs the client in this JVM)");
        if (server.getPlayerList().getPlayers().isEmpty())
            return skip("no player online");
        return null;
    }

    /** Runs {@code task} on the client thread and waits for it (the server keeps ticking meanwhile). */
    private <T> T onClient(Supplier<T> task)
    {
        return await(ClientThread.submit(task), CLIENT_WAIT_MS);
    }

    /** @return a market the client has cached, or null (client thread) */
    private static @Nullable ClientMarket anyClientMarket()
    {
        IClientMarketManager manager = StockMarketModBackend.getClientInstances().MARKET_MANAGER;
        for (ItemID id : manager.getAvailableMarkets())
        {
            ClientMarket market = manager.getMarket(id);
            if (market != null)
                return market;
        }
        return null;
    }

    /**
     * SM-275 part 1 (T-162): the display chart's provider wrapper forwards history paging to its market. For the
     * 5m, 15m and 1h deltas: right after the chart connects, the wrapper's hasMoreOlderData matches the market's;
     * the initial window fills the market's PriceHistoryCache; a pan-left page through the wrapper either reaches
     * the server (seen through a {@link PacketTap}) and extends the cache, or, at the server's oldest candle,
     * completes with nothing added.
     */
    private TestResult test_displayChartProviderLoadsHistory()
    {
        TestResult s = skipUnlessSp();
        if (s != null) return s;
        long[] deltas = {ClientMarket.CANDLE_TIME_5_MIN, ClientMarket.CANDLE_TIME_15_MIN, ClientMarket.CANDLE_TIME_1_HOUR};
        long[] available = ClientMarket.getAvailableCandleTimeDeltas();
        for (long d : deltas)
            if (Arrays.stream(available).noneMatch(a -> a == d))
                return skip("game-time candles active, real-time deltas not in use");
        ClientMarket market = onClient(PriceHistoryCacheTestSuite::anyClientMarket);
        if (market == null)
            return skip("the client has no market cached");

        MinecraftServer server = getServer();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        if (srv == null || srv.NETWORKING == null)
            return skip("no server networking");
        PriceHistoryCache cache = market.getHistoryCache();
        DisplayCandlestickChart[] chart = new DisplayCandlestickChart[1];
        try (PacketTap tap = PacketTap.open(server, player))
        {
            if (tap == null) return fail("no network connection for the player");
            // One client-thread task: no history response can land between connect and the checks.
            String forwardError = onClient(() -> {
                chart[0] = DisplayCandlestickChart.createEmbedded(market.getItemID());
                IPriceDataProvider p = chart[0].getPriceDataProvider();
                if (p == null) return "display chart did not connect";
                if (p == market) return "provider is the market itself, not the display wrapper";
                for (long d : deltas)
                    if (p.hasMoreOlderData(d) != market.hasMoreOlderData(d))
                        return "hasMoreOlderData(" + d + ") not forwarded";
                return null;
            });
            if (forwardError != null) return fail(forwardError);
            IPriceDataProvider provider = onClient(() -> chart[0].getPriceDataProvider());

            StringBuilder atStart = new StringBuilder();
            for (long d : deltas)
            {
                onClient(() -> { market.requestInitialWindow(d); return null; });
                if (!pumpUntil(() -> cache.hasCache(d), CLIENT_WAIT_MS))
                    return fail("the initial window for delta " + d + " put no candles into the cache");
                long before = cache.loadedMinTimestamp(d);
                boolean more = onClient(() -> provider.hasMoreOlderData(d));
                Boolean added = await(onClient(() -> provider.requestOlderData(d, before)), CLIENT_WAIT_MS);
                if (!more)
                {
                    TestResult r = assertFalse("delta " + d + ": no older page at the server's oldest candle", added);
                    if (!r.passed()) return r;
                    atStart.append(' ').append(d);
                    continue;
                }
                TestResult r = assertTrue("delta " + d + ": the pan-left request reached the server",
                        pumpUntil(() -> olderRequestSeen(srv, server, tap, d, before), CLIENT_WAIT_MS));
                if (!r.passed()) return r;
                if (added)
                {
                    r = assertTrue("delta " + d + ": the cache holds the older candles", cache.loadedMinTimestamp(d) < before);
                    if (!r.passed()) return r;
                }
            }
            return pass("display chart history loads through its provider"
                    + (atStart.length() > 0 ? "; already at the server's oldest candle (no page sent) for:" + atStart : ""));
        }
        finally
        {
            if (chart[0] != null)
                onClient(() -> { chart[0].close(); return null; });
        }
    }

    /** @return true if the server received a price-history request for {@code delta} ending at {@code before} */
    private static boolean olderRequestSeen(StockMarketModBackend.ServerInstances srv, MinecraftServer server,
                                            PacketTap tap, long delta, long before)
    {
        MarketPriceHistoryRequest request = srv.NETWORKING.MARKET_PRICE_HISTORY_REQUEST;
        for (byte[] bytes : tap.receivedRequests(request.getRequestTypeID()))
        {
            MarketPriceHistoryRequest.InputData in = request.decodeInput(
                    new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), server.registryAccess()));
            if (in.candleDeltaMs() == delta && in.maxTimestamp() == before)
                return true;
        }
        return false;
    }

    /**
     * SM-275 part 2 (T-163): OrderHistoryPanel and TransactionHistoryPanel keep their row widgets and scroll offset
     * when fed the same records again, and add a new record without losing the scroll offset. Built on the client
     * thread and never rendered (no auto-refresh request); rows are applied through {@code applyPendingRecords}.
     */
    private TestResult test_historyPanelsKeepUnchangedRows()
    {
        TestResult s = skipUnlessSp();
        if (s != null) return s;
        ClientMarket market = onClient(PriceHistoryCacheTestSuite::anyClientMarket);
        if (market == null)
            return skip("the client has no market cached");
        short item = market.getItemID().getShort();
        return onClient(() -> {
            OrderHistoryPanel orders = new OrderHistoryPanel();
            TestResult r = checkPanelKeepsRows("OrderHistoryPanel", orders, orders::updateRecords, orders::applyPendingRecords, item);
            if (!r.passed()) return r;
            TransactionHistoryPanel trades = new TransactionHistoryPanel();
            return checkPanelKeepsRows("TransactionHistoryPanel", trades, trades::updateRecords, trades::applyPendingRecords, item);
        });
    }

    /** Feeds 30 rows, the same 30 again, then 31; checks row reuse, the new row and the scroll offset (client thread). */
    private TestResult checkPanelKeepsRows(String name, GuiElement panel, Consumer<List<OrderRecordStruct>> update,
                                           Runnable apply, short item)
    {
        panel.setBounds(0, 0, 300, 120);
        VerticalListView list = null;
        for (GuiElement child : panel.getChilds())
            if (child instanceof VerticalListView v)
                list = v;
        if (list == null) return fail(name + ": no list view");

        UUID user = new UUID(0L, 275L);
        long now = System.currentTimeMillis() + 60_000L; // newer than any "cleared before" time of the player
        List<OrderRecordStruct> records = new ArrayList<>();
        for (int i = 0; i < 30; i++)
            records.add(new OrderRecordStruct(item, 0, user, 0, 100L * (i + 1), 1000L, now - i * 1000L));
        update.accept(records);
        apply.run();
        List<GuiElement> first = new ArrayList<>(list.getChilds());
        TestResult r = assertEquals(name + ": rows built", 30, first.size());
        if (!r.passed()) return r;
        list.setScrollOffset(48);
        int offset = list.getScrollOffset();
        r = assertTrue(name + ": the list scrolls (offset " + offset + ")", offset > 0);
        if (!r.passed()) return r;

        update.accept(new ArrayList<>(records));
        apply.run();
        List<GuiElement> second = list.getChilds();
        boolean reused = second.size() == first.size();
        for (int i = 0; reused && i < first.size(); i++)
            reused = second.get(i) == first.get(i);
        r = assertTrue(name + ": an unchanged update keeps the row widgets", reused);
        if (!r.passed()) return r;
        r = assertEquals(name + ": an unchanged update keeps the scroll offset", offset, list.getScrollOffset());
        if (!r.passed()) return r;

        List<OrderRecordStruct> more = new ArrayList<>(records);
        more.add(0, new OrderRecordStruct(item, 0, user, 0, 500L, 1000L, now + 1000L));
        update.accept(more);
        apply.run();
        r = assertEquals(name + ": the new row is shown", 31, list.getChilds().size());
        if (!r.passed()) return r;
        return assertEquals(name + ": a new row keeps the scroll offset", offset, list.getScrollOffset());
    }
}
