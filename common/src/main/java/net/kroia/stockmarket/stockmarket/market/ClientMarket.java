package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.banking.bankmanager.BankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IClientMarket;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.networking.request.OrderbookVolumeRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ClientMarket implements IClientMarket, IPriceDataProvider
{
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    /**
     * Fallback used when no server-side setting override is discoverable on the
     * client (e.g. dedicated server without the setting yet propagated). Matches
     * the default in {@code StockMarketModSettings.Market.PRICE_HISTORY_INITIAL_LOAD_CANDLES}.
     */
    private static final int DEFAULT_INITIAL_LOAD_CANDLES = 512;

    /**
     * Debounce window for {@link #requestOlderWindow(long, long)}. A second
     * request within this window resolves to {@code false} immediately so we
     * don't hammer the server while the user drags the chart.
     */
    private static final long OLDER_REQUEST_DEBOUNCE_MS = 250L;

    private final ItemID itemID;
    private final AsyncMarket asyncMarket;

    /**
     * Per-market client-side pagination cache. Populated by
     * {@link #requestInitialWindow(long)} and extended by
     * {@link #requestOlderWindow(long, long)}. Composed here (not inherited)
     * so the cache lifetime matches the ClientMarket exactly.
     */
    private final PriceHistoryCache historyCache = new PriceHistoryCache(AVAILABLE_CANDLE_TIME_DELTAS);

    /**
     * In-flight pagination futures keyed by candleDeltaMs. If a caller asks
     * for older data for a delta while a previous request for the same delta
     * is still pending, the same future is returned so callers get consistent
     * results and the server sees at most one outstanding request per delta.
     */
    private final Map<Long, CompletableFuture<Boolean>> inFlightOlder = new HashMap<>();

    /**
     * Wall-clock timestamp (client) of the last successfully-sent older-data
     * request per delta. Used together with {@link #OLDER_REQUEST_DEBOUNCE_MS}
     * to swallow rapid-fire re-requests as the user drags the chart.
     */
    private final Map<Long, Long> lastOlderRequestAtMs = new HashMap<>();

    public static class PriceHistoryContainer
    {
        public final static class ServerRelativeTimer extends TimerMillis
        {
            public static long timeOffsetMS = 0;
            public ServerRelativeTimer(boolean autoRestart) {
                super(autoRestart);
            }
            @Override
            public long currentTimeMillis()
            {
                return timeOffsetMS + System.currentTimeMillis();
            }
        }
        private final ServerRelativeTimer timer;
        public final PriceHistoryData history;
        /**
         * Optional cache attached at construction time by the owning ClientMarket.
         * When present, {@link #update(long)} coordinates live-candle promotion
         * with the cache so its immutable list stays in sync with what the
         * container renders.
         */
        private final @Nullable PriceHistoryCache attachedCache;
        /** Candle delta this container serves; needed for cache lookups. */
        private final long candleDeltaMs;
        /** Market this container belongs to; retained so {@link #loadFromCache} can build a synthesized {@link PriceHistoryData}. */
        private final ItemID itemID;
        /** Price fraction scaling factor mirrored from the container's {@link #history}. */
        private final int itemScaleFactor;

        public PriceHistoryContainer(ItemID itemID, int itemScaleFactor, long deltaT)
        {
            this(itemID, itemScaleFactor, deltaT, null);
        }
        /**
         * Creates a price-history container tied to a per-market pagination cache.
         * The cache (if non-null) is notified when candle boundaries cross so
         * its immutable list stays aligned with the container's rendered candles.
         *
         * @param itemID          the market this container belongs to
         * @param itemScaleFactor price fraction scaling factor
         * @param deltaT          the candle bucket size in milliseconds
         * @param cache           optional pagination cache; may be {@code null}
         *                        for callers that don't need pagination
         */
        public PriceHistoryContainer(ItemID itemID, int itemScaleFactor, long deltaT, @Nullable PriceHistoryCache cache)
        {
            timer = new ServerRelativeTimer(true);
            timer.start(deltaT);
            history = new PriceHistoryData(itemID, itemScaleFactor,  new ArrayList<>(), 0);
            this.attachedCache = cache;
            this.candleDeltaMs = deltaT;
            this.itemID = itemID;
            this.itemScaleFactor = itemScaleFactor;
        }
        public void loadFrom(PriceHistoryData other, long currentServerTime, boolean createEmptyCandleForTimeGaps)
        {
            history.loadFrom(other, timer.getDuration(), currentServerTime, other.getCurrentMarketPrice(), createEmptyCandleForTimeGaps);
            // After the container's history has been (re)built, the tip candle
            // — if any — is the live one; register it with the cache so the
            // stream-driven mutations to it stay properly attributed.
            if (attachedCache != null)
            {
                attachedCache.setLiveCandle(candleDeltaMs, history.getCurrentCandle());
            }
        }

        /**
         * Rebuilds this container's {@link #history} from the {@link #attachedCache}
         * — the FULL merged range across every page loaded so far, plus the
         * currently-tracked live candle if present — reusing the existing
         * {@link PriceHistoryData#loadFrom} gap-fill machinery.
         * <p>
         * Must be called after every successful
         * {@link PriceHistoryCache#mergeChunk(long, PriceHistoryData, long)} so
         * the chart observes the aggregated pagination range instead of just the
         * last-received chunk. Pan-left in particular relies on this: a paginated
         * older-chunk response is only a slice of the cache, and calling
         * {@link #loadFrom(PriceHistoryData, long, boolean)} directly with that
         * slice would drop the previously-visible newer candles.
         * <p>
         * The synthesized source combines {@code attachedCache.getCandles(delta)}
         * with {@code attachedCache.getLiveCandle(delta)} (appended iff strictly
         * newer than the immutable list's tail). Gap-fill candles are NOT stored
         * in the cache — they are regenerated on every rebuild.
         * <p>
         * No-op when {@link #attachedCache} is {@code null} (legacy callers that
         * don't use the pagination cache should keep using {@link #loadFrom}).
         *
         * @param currentServerTime          the current server-relative time (ms)
         * @param currentMarketPrice         seed price for the tail live candle
         *                                   and any gap-fill emitted at the end;
         *                                   callers should pass the last-known
         *                                   stream price (falling back to the
         *                                   response's currentMarketPrice on
         *                                   initial load, when the stream has
         *                                   not fired yet)
         * @param createEmptyCandleForTimeGaps whether to synthesize empty candles
         *                                     across time gaps (mirrors the
         *                                     client "fill missing candlesticks"
         *                                     setting)
         */
        public void loadFromCache(long currentServerTime, long currentMarketPrice, boolean createEmptyCandleForTimeGaps)
        {
            if (attachedCache == null)
                return;

            List<PriceHistoryData.Candle> synthesizedCandles = new ArrayList<>(attachedCache.getCandles(candleDeltaMs));
            PriceHistoryData.Candle live = attachedCache.getLiveCandle(candleDeltaMs);
            // The live candle is tracked separately from the immutable list, so
            // append it explicitly. Guarded against a duplicate openTimestamp in
            // case a promote just happened and the cache also carries the same
            // candle in its immutable list.
            if (live != null
                    && (synthesizedCandles.isEmpty()
                            || synthesizedCandles.get(synthesizedCandles.size() - 1).openTimestamp < live.openTimestamp))
            {
                synthesizedCandles.add(live);
            }

            // Wrap the full merged range in a transient PriceHistoryData so we
            // can reuse the existing loadFrom resampling/gap-fill code path.
            // itemID/itemScaleFactor mirror the container's own history so the
            // wrapper is a faithful placeholder — loadFrom itself doesn't read
            // itemID, but keeping them aligned is future-proof.
            PriceHistoryData source = new PriceHistoryData(
                    itemID, itemScaleFactor, synthesizedCandles, currentMarketPrice);
            loadFrom(source, currentServerTime, createEmptyCandleForTimeGaps);
        }
        public void update(long serverTime)
        {
            if(timer.check())
            {
                // Boundary crossed: promote the previous live candle into the
                // cache's immutable list BEFORE opening the new one, so the
                // MARKET_PRICE_STREAM handler can't mutate a candle that has
                // already been counted as immutable history.
                if (attachedCache != null)
                {
                    attachedCache.promoteLiveCandle(candleDeltaMs);
                }
                history.startNewCandle(serverTime);
                if (attachedCache != null)
                {
                    attachedCache.setLiveCandle(candleDeltaMs, history.getCurrentCandle());
                }
            }
        }
        public long getDeltaT()
        {
            return timer.getDuration();
        }
    }
    /*public static class OrderbookVolumeContainer
    {

    }*/
    private final Map<Long, PriceHistoryContainer> priceHistoryDataMap = new HashMap<>(); // Map with an individual buffer for the key: candle delta time
    public static final long CANDLE_TIME_1_MIN = 1000*60;
    public static final long CANDLE_TIME_5_MIN = CANDLE_TIME_1_MIN * 5;
    public static final long CANDLE_TIME_15_MIN = CANDLE_TIME_1_MIN * 15;
    public static final long CANDLE_TIME_1_HOUR = CANDLE_TIME_1_MIN * 60;
    public static final long CANDLE_TIME_4_HOUR = CANDLE_TIME_1_HOUR * 4;
    public static final long CANDLE_TIME_1_DAY = CANDLE_TIME_1_HOUR * 24;
    public static final long[] AVAILABLE_CANDLE_TIME_DELTAS =
            {
                    CANDLE_TIME_1_MIN,
                    CANDLE_TIME_5_MIN,
                    CANDLE_TIME_15_MIN,
                    CANDLE_TIME_1_HOUR,
                    CANDLE_TIME_4_HOUR,
                    CANDLE_TIME_1_DAY
            };

    private @Nullable UUID marketPriceUpdateStreamID = null;
    private int streamSubscriberCount = 0;

    private final int itemScaleFactor;
    private long currentMarketPrice;

    public ClientMarket(@NotNull ItemID itemID, int itemScaleFactor)
    {
        this.itemScaleFactor =  itemScaleFactor;
        this.itemID = itemID;
        this.asyncMarket = AsyncMarket.createClientMarket(itemID);

        for (long delta : AVAILABLE_CANDLE_TIME_DELTAS) {
            priceHistoryDataMap.put(delta, new PriceHistoryContainer(itemID, itemScaleFactor, delta, historyCache));
        }
    }

    public void update(long serverTime)
    {
        if(marketPriceUpdateStreamID != null) {

            for(PriceHistoryContainer priceHistoryContainer : priceHistoryDataMap.values())
            {
                priceHistoryContainer.update(serverTime);
            }
        }
    }


    public int getItemFractionScaleFactor()
    {
        return itemScaleFactor;
    }
    /** {@inheritDoc} */
    @Override
    public @NotNull ItemID getItemID()
    {
        return itemID;
    }

    public static long[] getAvailableCandleTimeDeltas()
    {
        return AVAILABLE_CANDLE_TIME_DELTAS;
    }
    /** {@inheritDoc} */
    @Override
    public @Nullable PriceHistoryData getPriceHistoryData(long candleTimeDelta)
    {
        PriceHistoryContainer historyContainer = priceHistoryDataMap.get(candleTimeDelta);
        if(historyContainer == null)
            return null;
        return historyContainer.history;
    }
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
    /** {@inheritDoc} */
    @Override
    public double getCurrentMarketRealPrice()
    {
        return BankManager.convertToRealAmountStatic(currentMarketPrice, itemScaleFactor);
    }
    /**
     * @return the current server-relative time in milliseconds (client wall
     *         clock adjusted by the offset synced on player-join)
     */
    private static long currentServerTimeMs()
    {
        return PriceHistoryContainer.ServerRelativeTimer.timeOffsetMS + System.currentTimeMillis();
    }

    /**
     * Best-effort read of the server-side "initial candles per chart open"
     * setting. When the client is co-located with the master (integrated
     * server / single-player) this reflects the real value; on a dedicated
     * client (where {@code SERVER_INSTANCES} is null in this JVM), the setting
     * is not reachable and {@link #DEFAULT_INITIAL_LOAD_CANDLES} is returned.
     * <p>
     * The setting is deliberately NOT propagated to slaves/clients (see
     * {@code StockMarketModSettings.Market.PRICE_HISTORY_INITIAL_LOAD_CANDLES}),
     * so using the constant fallback on a dedicated client is expected behaviour.
     * The reflective lookup exists purely to give integrated-server single-player
     * the admin-tuned value without needing a new sync packet.
     */
    private int getInitialLoadCandlesSafe()
    {
        try
        {
            java.lang.reflect.Field f = StockMarketModBackend.class.getDeclaredField("SERVER_INSTANCES");
            f.setAccessible(true);
            Object srvObj = f.get(null);
            if (srvObj instanceof StockMarketModBackend.ServerInstances srv
                    && srv.SERVER_SETTINGS != null
                    && srv.SERVER_SETTINGS.MARKET != null)
            {
                return srv.SERVER_SETTINGS.MARKET.getPriceHistoryInitialLoadCandles();
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to the fallback.
        }
        return DEFAULT_INITIAL_LOAD_CANDLES;
    }

    /**
     * Kicks off the initial paginated load for every candle delta this market
     * cares about. Called once when the market's price stream is first
     * subscribed to (see {@link #subscribeToMarketPriceUpdate()}).
     */
    public void requestInitialWindowForAllDeltas()
    {
        for (long delta : AVAILABLE_CANDLE_TIME_DELTAS)
        {
            requestInitialWindow(delta);
        }
    }

    /**
     * Requests the initial history window for a single candle delta. The
     * window's size in candles is {@link #getInitialLoadCandlesSafe()} — with
     * the default this is 512 candles ending at "now".
     * <p>
     * The response is merged into the per-market {@link PriceHistoryCache} and
     * the corresponding {@link PriceHistoryContainer} is rebuilt so the chart
     * observes the freshly-loaded history through
     * {@link #getPriceHistoryData(long)}.
     *
     * @param candleDeltaMs the candle bucket size to load
     */
    public void requestInitialWindow(long candleDeltaMs)
    {
        int initialCandles = getInitialLoadCandlesSafe();
        long endTime = currentServerTimeMs();
        long startTime = endTime - candleDeltaMs * (long) initialCandles;

        MarketPriceHistoryRequest.InputData input =
                new MarketPriceHistoryRequest.InputData(itemID, candleDeltaMs, startTime, endTime, 0);
        BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.sendRequestToServer(input).thenAccept((historyData) ->
        {
            info("Initial price window received for: " + itemID + " delta=" + candleDeltaMs);
            applyHistoryResponse(candleDeltaMs, historyData);
        });
    }

    /**
     * Requests the next-older page of candles for the given delta. Uses an
     * in-flight guard and a {@value #OLDER_REQUEST_DEBOUNCE_MS}-ms debounce so
     * concurrent / rapid-fire callers don't stack up on the server.
     * <p>
     * Semantics:
     * <ul>
     *   <li>If we've already walked back to the server's floor for this delta,
     *       the future completes immediately with {@code false}.</li>
     *   <li>If a request for the same delta is already in flight, its future
     *       is returned to the caller.</li>
     *   <li>If the last request for this delta was fewer than
     *       {@value #OLDER_REQUEST_DEBOUNCE_MS} ms ago, the call is dropped and
     *       {@code completedFuture(false)} is returned.</li>
     *   <li>Otherwise a request is sent and the returned future resolves to
     *       {@code true} iff at least one new candle was merged into the cache.</li>
     * </ul>
     *
     * @param candleDeltaMs   the candle bucket to page
     * @param beforeTimestamp the exclusive upper bound (openTimestamp) — the
     *                        server returns candles strictly older than this
     * @return future resolving to whether new data was appended
     */
    public CompletableFuture<Boolean> requestOlderWindow(long candleDeltaMs, long beforeTimestamp)
    {
        // Server-start guard: nothing older exists.
        if (historyCache.atServerStart(candleDeltaMs))
            return CompletableFuture.completedFuture(false);

        // Coalesce with in-flight future, if any.
        CompletableFuture<Boolean> existing = inFlightOlder.get(candleDeltaMs);
        if (existing != null)
            return existing;

        // Debounce: swallow rapid re-requests for the same delta.
        Long lastAt = lastOlderRequestAtMs.get(candleDeltaMs);
        long now = System.currentTimeMillis();
        if (lastAt != null && (now - lastAt) < OLDER_REQUEST_DEBOUNCE_MS)
            return CompletableFuture.completedFuture(false);

        int pageCandles = getInitialLoadCandlesSafe();

        // Decide the request's [minTs, maxTs] window.
        //
        // Normal pagination case: cache non-empty, caller passes a real
        // oldestLoadedCandle.openTimestamp → request the page immediately
        // older than that.
        //
        // Bootstrap case: cache empty for this delta. This happens when the
        // initial fixed-size window ([now - N*delta, now]) missed the real
        // data — e.g. world loaded days after last save, 1-min candles cover
        // ~8.5 h but the newest real candle is a day old. Anchoring the
        // request on beforeTimestamp would just produce another empty window;
        // instead, use what we already know about where the data lives:
        //   1. If the server reported a finite oldest-available timestamp
        //      earlier, target [serverOldest, currentServerTime] — the server
        //      will trim from the oldest side and return the newest N candles
        //      that actually exist.
        //   2. Otherwise (no response observed yet, or caller passed the
        //      Long.MAX_VALUE bootstrap sentinel), send an open-ended lower
        //      bound (minTs = -1) which the server treats as "from the
        //      earliest record" and again trims to the newest N.
        long minTs;
        long maxTs;
        boolean cacheEmpty = !historyCache.hasCache(candleDeltaMs);
        long serverOldest = historyCache.getServerOldestAvailableTimestamp(candleDeltaMs);
        boolean bootstrapSentinel = beforeTimestamp == Long.MAX_VALUE;

        if (cacheEmpty || bootstrapSentinel)
        {
            maxTs = currentServerTimeMs();
            if (serverOldest != PriceHistoryCache.SERVER_OLDEST_UNKNOWN
                    && serverOldest != PriceHistoryData.NO_OLDER_DATA)
            {
                // Jump directly to where the data lives.
                minTs = serverOldest;
            }
            else
            {
                // No hint yet — ask the server for its newest N candles
                // regardless of time range.
                minTs = -1L;
            }
        }
        else
        {
            maxTs = beforeTimestamp;
            minTs = beforeTimestamp - candleDeltaMs * (long) pageCandles;
        }

        MarketPriceHistoryRequest.InputData input =
                new MarketPriceHistoryRequest.InputData(itemID, candleDeltaMs, minTs, maxTs, 0);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        inFlightOlder.put(candleDeltaMs, future);
        lastOlderRequestAtMs.put(candleDeltaMs, now);

        BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.sendRequestToServer(input).whenComplete((historyData, throwable) ->
        {
            try
            {
                if (throwable != null)
                {
                    error("Older-window request failed for " + itemID + " delta=" + candleDeltaMs, throwable);
                    future.complete(false);
                    return;
                }
                boolean added = applyHistoryResponse(candleDeltaMs, historyData);
                future.complete(added);
            }
            finally
            {
                inFlightOlder.remove(candleDeltaMs);
            }
        });
        return future;
    }

    /**
     * Merges a price-history response into the cache for {@code candleDeltaMs}
     * and rebuilds the corresponding {@link PriceHistoryContainer} so charts
     * observe the new data on their next tick.
     *
     * @return {@code true} if at least one candle was appended to the cache
     */
    private boolean applyHistoryResponse(long candleDeltaMs, PriceHistoryData response)
    {
        PriceHistoryContainer container = priceHistoryDataMap.get(candleDeltaMs);
        long liveOpenTs = (container != null && container.history.getCurrentCandle() != null)
                ? container.history.getCurrentCandle().openTimestamp
                : Long.MAX_VALUE;

        boolean added = historyCache.mergeChunk(candleDeltaMs, response, liveOpenTs);

        if (container != null)
        {
            // Rebuild the container from the FULL cache (all merged pages so
            // far + the live candle if present), NOT from just this response.
            //
            // Historical bug: calling container.loadFrom(response, ...) worked
            // for the initial load (single meaningful response) but broke
            // pan-left. A paginated older-chunk response is only a slice; using
            // it as the source overwrote the container's history with just the
            // older candles, dropping the previously-visible newer window and
            // causing the chart to jump backward on every pan.
            //
            // loadFromCache pulls candles + live candle straight from the
            // pagination cache and feeds them into the same PriceHistoryData
            // resampling/gap-fill path loadFrom already uses.
            //
            // Price seed: prefer the stream's most recent price (this.currentMarketPrice)
            // so the tail live candle keeps its accumulated state; fall back to
            // the response's currentMarketPrice on the very first load when
            // the stream hasn't fired yet.
            long seedPrice = currentMarketPrice != 0 ? currentMarketPrice : response.getCurrentMarketPrice();
            container.loadFromCache(currentServerTimeMs(),
                    seedPrice,
                    BACKEND_INSTANCES.SETTINGS.isFillMissingCandlesticks());
        }

        // Bootstrap follow-up: when the response left the local cache empty
        // for this delta but the server confirmed it does have older data,
        // the fixed-size initial window ([now - N*delta, now]) simply missed
        // the range where the real candles live. Fire a targeted follow-up
        // — requestOlderWindow's bootstrap branch will jump to the server's
        // known oldest timestamp and pull back the newest N candles that
        // actually exist. Without this, a world whose last save is
        // hours/days older than the initial window shows an empty chart the
        // user can't even pan-left out of.
        //
        // Guarded by hasCache/atServerStart so it only fires when there IS
        // older data to fetch, and by the OLDER_REQUEST_DEBOUNCE_MS +
        // inFlightOlder machinery inside requestOlderWindow so we don't loop
        // if the follow-up itself returns empty for some reason.
        if (!historyCache.hasCache(candleDeltaMs) && !historyCache.atServerStart(candleDeltaMs))
        {
            long serverOldest = historyCache.getServerOldestAvailableTimestamp(candleDeltaMs);
            if (serverOldest != PriceHistoryCache.SERVER_OLDEST_UNKNOWN
                    && serverOldest != PriceHistoryData.NO_OLDER_DATA)
            {
                info("Initial window empty for delta=" + candleDeltaMs
                        + "; server reports oldest at " + serverOldest
                        + " — firing bootstrap follow-up.");
                // Fire-and-forget; the response will re-enter applyHistoryResponse.
                requestOlderWindow(candleDeltaMs, Long.MAX_VALUE);
            }
        }
        return added;
    }
    public boolean subscribeToMarketPriceUpdate()
    {
        streamSubscriberCount++;
        if(marketPriceUpdateStreamID != null)
            return true; // Stream already active, just counted the new subscriber

        // History is loaded lazily per-delta by the chart widget when it needs
        // it (see CandlestickChart#selectCandleTimeDeltaByIndex, T-136). Firing
        // requestInitialWindowForAllDeltas() here would multiply the initial
        // load by AVAILABLE_CANDLE_TIME_DELTAS.length (6x) per subscribed
        // market — causing hundreds of DB round-trips on world join with many
        // markets — while the user typically only ever views one delta.

        marketPriceUpdateStreamID = StreamSystem.startServerToClientStream(BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_STREAM, itemID, (price)->
        {
            currentMarketPrice = price.marketPrice;
            for(PriceHistoryContainer priceHistoryContainer : priceHistoryDataMap.values())
                priceHistoryContainer.history.setCurrentMarketPrice(currentMarketPrice, price.tradedVolume);
        },()->
        {
            // Stream stopped
            info("MARKET_PRICE_STREAM stopped for itemID: "+itemID);
            marketPriceUpdateStreamID  = null;
        });
        return marketPriceUpdateStreamID != null;
    }
    public boolean unsubscribeFromMarketPriceUpdate()
    {
        if(streamSubscriberCount > 0)
            streamSubscriberCount--;
        if(streamSubscriberCount > 0)
            return false; // Other subscribers still need the stream
        if(marketPriceUpdateStreamID == null)
            return false;
        StreamSystem.stopStream(marketPriceUpdateStreamID);
        return true;
    }

    /**
     * Force-stops the market price stream regardless of how many GUI elements are
     * still subscribed. Used when the market ceases to exist on the server
     * (market deleted) — keeping the stream open would leak an orphaned
     * subscription that can never deliver data again.
     */
    public void forceStopMarketPriceStream()
    {
        streamSubscriberCount = 0;
        if(marketPriceUpdateStreamID != null)
        {
            StreamSystem.stopStream(marketPriceUpdateStreamID);
            marketPriceUpdateStreamID = null;
        }
    }




    public CompletableFuture<CreateOrderRequest.OutputData> createLimitOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, volume, price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.max(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.min(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createMarketOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, volume, 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.max(0, volume), 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.min(0, volume), 0);
    }


    public CompletableFuture<CreateOrderRequest.OutputData> createOrder(int bankAccountNr, Order.Type type, double volume, double price)
    {
        CreateOrderRequest.InputData inputData = new CreateOrderRequest.InputData(itemID, bankAccountNr, type, volume, price);
        CompletableFuture<CreateOrderRequest.OutputData> future = new CompletableFuture<>();
        BACKEND_INSTANCES.NETWORKING.CREATE_ORDER_REQUEST.sendRequestToServer(inputData).thenAccept(future::complete);
        return future;
    }



    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr)
    {
        return requestPendingOrders(bankAccountNr, null, 0, Long.MAX_VALUE);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@NotNull UUID executorPlayerFilter)
    {
        return requestPendingOrders(-1, executorPlayerFilter, 0, Long.MAX_VALUE);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(long timeBegin, long timeEnd)
    {
        return requestPendingOrders(-1, null, timeBegin, timeEnd);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@NotNull UUID executorPlayerFilter, long timeBegin, long timeEnd)
    {
        return requestPendingOrders(-1, executorPlayerFilter, timeBegin, timeEnd);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr, long timeBegin, long timeEnd)
    {
        return requestPendingOrders(bankAccountNr, null, timeBegin, timeEnd);
    }

    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr,
                                                                                  @Nullable UUID executorPlayerFilter,
                                                                                  long timeBegin,
                                                                                  long timeEnd)
    {
        ActiveOrdersRequest.InputData inp = new ActiveOrdersRequest.InputData(itemID, bankAccountNr, executorPlayerFilter, timeBegin, timeEnd);
        return BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_REQUEST.sendRequestToServer(inp);
    }

    public CompletableFuture<OrderbookVolumeRequest.OutputData> requestOrderbookVolume(double startPrice, double endPrice)
    {
        return requestOrderbookVolume(startPrice, endPrice, 20);
    }
    public CompletableFuture<OrderbookVolumeRequest.OutputData> requestOrderbookVolume(double startPrice, double endPrice, int chunkCount)
    {
        OrderbookVolumeRequest.InputData inp = new OrderbookVolumeRequest.InputData(itemID, startPrice, endPrice, chunkCount);
        return BACKEND_INSTANCES.NETWORKING.ORDERBOOK_VOLUME_REQUEST.sendRequestToServer(inp);
    }


    public CompletableFuture<MarketSettings> getSettings()
    {
        return asyncMarket.getSettingsAsync();
    }
    public CompletableFuture<Boolean> setSettings(MarketSettings settings)
    {
        return asyncMarket.setSettingsAsync(settings);
    }
    public CompletableFuture<Boolean> isMarketOpenAsync()
    {
        return asyncMarket.isMarketOpenAsync();
    }
    public CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen)
    {
        return asyncMarket.setMarketOpenAsync(marketOpen);
    }
    public CompletableFuture<Boolean> resetNetPlayerItemFlow()
    {
        return asyncMarket.resetNetPlayerItemFlowAsync();
    }


    /**
     * {@inheritDoc}
     * Delegates to the per-market pagination cache. Debounced and coalesced
     * per candle delta — see {@link #requestOlderWindow(long, long)}.
     */
    @Override
    public @NotNull CompletableFuture<Boolean> requestOlderData(long candleDeltaMs, long beforeTimestamp)
    {
        return requestOlderWindow(candleDeltaMs, beforeTimestamp);
    }

    /**
     * {@inheritDoc}
     * {@code true} until the pagination cache has walked back to the server's
     * oldest recorded candle for this delta.
     */
    @Override
    public boolean hasMoreOlderData(long candleDeltaMs)
    {
        return !historyCache.atServerStart(candleDeltaMs);
    }

    /**
     * @return the per-market pagination cache. Package-private helper for
     *         tests and future in-package UI code; charts should go through
     *         {@link #getPriceHistoryData(long)} instead.
     */
    PriceHistoryCache getHistoryCache()
    {
        return historyCache;
    }

    @Override
    public String toString()
    {
        return "ClientMarket:" + itemID + " Price:" + BankManager.convertToRealAmountStatic(currentMarketPrice, itemScaleFactor);
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientMarket:"+itemID+"]: "+message);
    }
}
