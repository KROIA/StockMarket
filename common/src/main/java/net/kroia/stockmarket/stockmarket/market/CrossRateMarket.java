package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.util.PriceHistoryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic price data provider for cross-rate (item/item) pairs.
 * Derives OHLC candles from two underlying ClientMarkets using the formula:
 *   rate = wantPrice / havePrice
 *
 * For each candle period the two underlying markets' candles are matched by
 * timestamp (two-pointer merge with half-period tolerance), and the OHLC
 * sample points are combined to produce a synthetic candle.
 *
 * Managed by ClientMarketManager; the CandlestickChart consumes this
 * through the IPriceDataProvider interface without knowing it is synthetic.
 */
public class CrossRateMarket implements IPriceDataProvider {

    /** Scale factor applied to the cross-rate ratio to store it as a long. */
    private static final int CROSS_RATE_SCALE = 1_000_000;

    private final ClientMarket haveMarket;
    private final ClientMarket wantMarket;

    /**
     * Per-time-delta cache of computed cross-rate candle data.
     * Only the active delta is incrementally ticked each frame;
     * inactive deltas are recomputed lazily on access.
     */
    private final Map<Long, CachedCrossRateData> cacheByTimeDelta = new HashMap<>();

    /** The candle time delta currently being displayed (set by getPriceHistoryData). */
    private long activeTimeDelta = ClientMarket.CANDLE_TIME_1_MIN;

    /**
     * Cache state for a single candle time delta.
     * Tracks the underlying market sizes and newest timestamp
     * to detect when a full recompute is needed vs. an incremental update.
     */
    private static class CachedCrossRateData {
        List<PriceHistoryData.Candle> syntheticCandles = null;
        PriceHistoryData priceHistoryData = null;
        int cachedHaveSize = -1;
        int cachedWantSize = -1;
        long cachedNewestTs = -1;
    }

    /**
     * Creates a new cross-rate market between two underlying markets.
     *
     * @param haveMarket the market whose item is "held" (denominator)
     * @param wantMarket the market whose item is "wanted" (numerator)
     */
    public CrossRateMarket(ClientMarket haveMarket, ClientMarket wantMarket) {
        this.haveMarket = haveMarket;
        this.wantMarket = wantMarket;
        // Pre-create cache entries for all available time deltas
        for (long delta : ClientMarket.getAvailableCandleTimeDeltas()) {
            cacheByTimeDelta.put(delta, new CachedCrossRateData());
        }
    }

    // --- IPriceDataProvider implementation ---

    /**
     * {@inheritDoc}
     * Sets the active time delta and returns the cached (or freshly computed)
     * cross-rate candle data for that period.
     */
    @Override
    public @Nullable PriceHistoryData getPriceHistoryData(long candleTimeDelta) {
        activeTimeDelta = candleTimeDelta;
        CachedCrossRateData cache = cacheByTimeDelta.get(candleTimeDelta);
        if (cache == null) return null;
        // Ensure data is computed on first access
        if (cache.priceHistoryData == null) {
            recomputeCrossRateData(candleTimeDelta, cache);
        }
        return cache.priceHistoryData;
    }

    /**
     * {@inheritDoc}
     * Returns the live cross-rate: wantPrice / havePrice.
     */
    @Override
    public double getCurrentMarketRealPrice() {
        double havePrice = haveMarket.getCurrentMarketRealPrice();
        double wantPrice = wantMarket.getCurrentMarketRealPrice();
        return (havePrice > 0) ? wantPrice / havePrice : 0;
    }

    /**
     * {@inheritDoc}
     * Returns the "have" market's item ID.
     */
    @Override
    public @NotNull ItemID getItemID() {
        return haveMarket.getItemID();
    }

    /**
     * {@inheritDoc}
     * Returns a unique key combining both markets so viewport state
     * is stored per pair, not per individual market.
     */
    @Override
    public @NotNull String getViewportKey() {
        return "pair:" + haveMarket.getItemID().getName() + "/" + wantMarket.getItemID().getName();
    }

    // --- Accessors for the underlying markets (used by TradeScreen for display/trading) ---

    /** Returns the underlying "have" (denominator) market. */
    public ClientMarket getHaveMarket() { return haveMarket; }

    /** Returns the underlying "want" (numerator) market. */
    public ClientMarket getWantMarket() { return wantMarket; }

    // --- Update (called by ClientMarketManager each tick) ---

    /**
     * Incrementally updates the active time delta's live candle.
     * Triggers a full recompute only when underlying candle counts change
     * or when a new candle period boundary is crossed.
     *
     * @param serverTime the current server-relative time in milliseconds
     */
    public void update(long serverTime) {
        CachedCrossRateData cache = cacheByTimeDelta.get(activeTimeDelta);
        if (cache == null) return;

        PriceHistoryData haveData = haveMarket.getPriceHistoryData(activeTimeDelta);
        PriceHistoryData wantData = wantMarket.getPriceHistoryData(activeTimeDelta);
        if (haveData == null || wantData == null) {
            cache.priceHistoryData = null;
            cache.syntheticCandles = null;
            return;
        }

        int newHaveSize = haveData.getCandles().size();
        int newWantSize = wantData.getCandles().size();

        // Full recompute when cached data is stale (size changed or never computed)
        if (cache.syntheticCandles == null
                || cache.priceHistoryData == null
                || newHaveSize != cache.cachedHaveSize
                || newWantSize != cache.cachedWantSize) {
            cache.cachedHaveSize = newHaveSize;
            cache.cachedWantSize = newWantSize;
            recomputeCrossRateData(activeTimeDelta, cache);
            if (cache.syntheticCandles != null && !cache.syntheticCandles.isEmpty())
                cache.cachedNewestTs = cache.syntheticCandles.getLast().openTimestamp;
            return;
        }

        // Compute live cross-rate price for incremental accumulation
        double havePrice = haveMarket.getCurrentMarketRealPrice();
        double wantPrice = wantMarket.getCurrentMarketRealPrice();
        long currentRateRaw = (havePrice > 0) ? (long) ((wantPrice / havePrice) * CROSS_RATE_SCALE) : 0;

        if (!cache.syntheticCandles.isEmpty() && currentRateRaw > 0) {
            PriceHistoryData.Candle newest = cache.syntheticCandles.getLast();
            if (newest.openTimestamp != cache.cachedNewestTs) {
                // New candle period without a size change — force full recompute
                cache.cachedHaveSize = -1;
                recomputeCrossRateData(activeTimeDelta, cache);
                if (cache.syntheticCandles != null && !cache.syntheticCandles.isEmpty())
                    cache.cachedNewestTs = cache.syntheticCandles.getLast().openTimestamp;
                return;
            }
        }

        // Update the live price in place — setCurrentMarketPrice handles
        // currentMarketPrice AND newest candle high/low accumulation
        if (cache.priceHistoryData != null && currentRateRaw > 0) {
            cache.priceHistoryData.setCurrentMarketPrice(currentRateRaw);
        }
    }

    // --- Full recompute ---

    /**
     * Recomputes synthetic OHLC candle data from the two underlying markets.
     * Uses a two-pointer merge on candle timestamps with half-period tolerance.
     * For each matching pair the cross-rate is computed at OHLC sample points:
     *   rateOpen = wantOpen / haveOpen
     *   rateHH   = wantHigh / haveHigh
     *   rateLL   = wantLow  / haveLow
     *   rateHigh = max(rateOpen, rateHH, rateLL)
     *   rateLow  = min(rateOpen, rateHH, rateLL)
     *
     * After the merge a post-pass extends each candle's high/low to include
     * the implied close price (= next candle's open).
     *
     * @param candleTimeDelta the candle period duration in milliseconds
     * @param cache           the cache entry to populate
     */
    private void recomputeCrossRateData(long candleTimeDelta, CachedCrossRateData cache) {
        PriceHistoryData haveData = haveMarket.getPriceHistoryData(candleTimeDelta);
        PriceHistoryData wantData = wantMarket.getPriceHistoryData(candleTimeDelta);
        if (haveData == null || wantData == null) {
            cache.priceHistoryData = null;
            cache.syntheticCandles = null;
            return;
        }

        List<PriceHistoryData.Candle> haveCandles = haveData.getCandles();
        List<PriceHistoryData.Candle> wantCandles = wantData.getCandles();
        if (haveCandles.isEmpty() || wantCandles.isEmpty()) {
            cache.priceHistoryData = null;
            cache.syntheticCandles = null;
            return;
        }

        List<PriceHistoryData.Candle> syntheticCandles = new ArrayList<>();

        // Two-pointer merge on candle timestamps (both lists are chronological, oldest first)
        long tolerance = candleTimeDelta / 2;
        int hi = 0;
        int wi = 0;
        while (hi < haveCandles.size() && wi < wantCandles.size()) {
            PriceHistoryData.Candle hc = haveCandles.get(hi);
            PriceHistoryData.Candle wc = wantCandles.get(wi);
            long timeDiff = hc.openTimestamp - wc.openTimestamp;

            if (Math.abs(timeDiff) <= tolerance) {
                // Timestamps match within tolerance — compute cross-rate OHLC
                double haveOpen  = haveData.toRealPrice(hc.open);
                double haveHigh  = haveData.toRealPrice(hc.high);
                double haveLow   = haveData.toRealPrice(hc.low);
                double wantOpen  = wantData.toRealPrice(wc.open);
                double wantHigh  = wantData.toRealPrice(wc.high);
                double wantLow   = wantData.toRealPrice(wc.low);

                if (haveOpen > 0 && haveHigh > 0 && haveLow > 0) {
                    double rateOpen = wantOpen / haveOpen;
                    double rateHH = wantHigh / haveHigh;
                    double rateLL = wantLow / haveLow;
                    double rateHigh = Math.max(Math.max(rateOpen, rateHH), rateLL);
                    double rateLow = Math.min(Math.min(rateOpen, rateHH), rateLL);

                    long rawOpen = (long)(rateOpen * CROSS_RATE_SCALE);
                    long rawHigh = (long)(rateHigh * CROSS_RATE_SCALE);
                    long rawLow = (long)(rateLow * CROSS_RATE_SCALE);
                    syntheticCandles.add(new PriceHistoryData.Candle(
                            hc.openTimestamp, rawOpen, rawHigh, rawLow, 0f));
                }
                hi++;
                wi++;
            } else if (timeDiff > 0) {
                // Have candle is newer — advance the want pointer
                wi++;
            } else {
                // Want candle is newer — advance the have pointer
                hi++;
            }
        }

        if (syntheticCandles.isEmpty()) {
            cache.priceHistoryData = null;
            cache.syntheticCandles = null;
            return;
        }

        // Compute current cross-rate as the live "market price"
        double havePrice = haveMarket.getCurrentMarketRealPrice();
        double wantPrice = wantMarket.getCurrentMarketRealPrice();
        long currentRateRaw = 0;
        if (havePrice > 0) {
            currentRateRaw = (long)((wantPrice / havePrice) * CROSS_RATE_SCALE);
        }
        // If live prices aren't available yet (async load), fall back to the
        // newest synthetic candle's open so the chart doesn't show a crash to 0
        if (currentRateRaw <= 0 && !syntheticCandles.isEmpty()) {
            currentRateRaw = syntheticCandles.getLast().open;
        }

        // Post-pass: extend historical candle high/low to include the close price.
        // Candles are chronological (oldest first at index 0, newest last).
        // Close of candle[i] = open of candle[i+1].
        for (int i = 0; i < syntheticCandles.size() - 1; i++) {
            PriceHistoryData.Candle c = syntheticCandles.get(i);
            long close = syntheticCandles.get(i + 1).open;
            c.high = Math.max(c.high, Math.max(c.open, close));
            c.low  = Math.min(c.low, Math.min(c.open, close));
        }

        // Newest candle: carry forward accumulated high/low from previous cache
        // to preserve intra-period extremes across recomputes
        if (!syntheticCandles.isEmpty() && currentRateRaw > 0) {
            PriceHistoryData.Candle newest = syntheticCandles.getLast();
            if (cache.syntheticCandles != null && !cache.syntheticCandles.isEmpty()) {
                PriceHistoryData.Candle prev = cache.syntheticCandles.getLast();
                if (prev.openTimestamp == newest.openTimestamp) {
                    newest.high = Math.max(newest.high, prev.high);
                    newest.low  = Math.min(newest.low,  prev.low);
                }
            }
            newest.high = Math.max(newest.high, currentRateRaw);
            newest.low  = Math.min(newest.low,  currentRateRaw);
        }

        cache.syntheticCandles = syntheticCandles;
        cache.priceHistoryData = new PriceHistoryData(
                haveMarket.getItemID(),
                CROSS_RATE_SCALE,
                syntheticCandles,
                currentRateRaw
        );
    }
}
