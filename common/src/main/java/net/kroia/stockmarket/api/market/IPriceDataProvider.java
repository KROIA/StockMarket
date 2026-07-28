package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.util.PriceHistoryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Provides price history data for display in the CandlestickChart.
 * Implemented by both ClientMarket (single item/money market) and
 * CrossRateMarket (synthetic item/item cross-rate pairs).
 *
 * The chart treats all providers uniformly through this interface,
 * without knowing whether the data comes from a real market or
 * a synthetic cross-rate computation.
 */
public interface IPriceDataProvider {

    /**
     * Returns the candle data for the given time delta, or null if not available.
     *
     * @param candleTimeDelta the candle period duration in milliseconds
     *                        (e.g. ClientMarket.CANDLE_TIME_1_MIN)
     * @return the price history data for that period, or null if unavailable
     */
    @Nullable PriceHistoryData getPriceHistoryData(long candleTimeDelta);

    /**
     * Returns the current live price as a display-ready real value.
     * For single markets this is the money price; for cross-rates
     * this is the ratio wantPrice/havePrice.
     *
     * @return the current real price
     */
    double getCurrentMarketRealPrice();

    /**
     * Identifies this data source (used for viewport persistence in the chart).
     *
     * @return the item ID associated with this provider
     */
    @NotNull ItemID getItemID();

    /**
     * Returns a unique string key for viewport state persistence in the chart.
     * Different data sources that share the same ItemID (e.g. cross-rate pairs
     * using the same "have" market) must return distinct keys.
     *
     * @return a unique viewport cache key for this provider
     */
    default @NotNull String getViewportKey() {
        return getItemID().getName();
    }

    /**
     * Request older candle data for a chart's current candle-delta. The provider
     * is expected to page backwards from {@code beforeTimestamp} using its own
     * cache/pagination policy, then complete the returned future once the
     * response has been merged into whatever the {@link #getPriceHistoryData(long)}
     * chain will observe.
     * <p>
     * Providers may debounce or coalesce concurrent calls for the same
     * {@code candleDeltaMs}; implementations should document their choice.
     *
     * @param candleDeltaMs   the candle bucket size the caller is viewing
     * @param beforeTimestamp fetch candles strictly older than this
     *                        {@code openTimestamp}
     * @return future resolving to {@code true} if new data was appended to the
     *         provider's cache, {@code false} if the server has no more data
     *         older than what is already loaded (or the request was
     *         debounced / de-duplicated to a no-op)
     */
    default @NotNull CompletableFuture<Boolean> requestOlderData(long candleDeltaMs, long beforeTimestamp)
    {
        // Default: providers without a pagination cache have nothing older to
        // fetch (e.g. synthetic cross-rate markets whose history is derived
        // from underlying markets). Implementers with a real cache MUST
        // override.
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Query whether the provider believes the server has older data available
     * for the given candle bucket. Used by the chart to hide "load older"
     * affordances once we've walked back to the server's floor.
     *
     * @param candleDeltaMs the candle bucket size
     * @return {@code true} if older data likely exists on the server for this
     *         delta; {@code false} once we've reached the server-start floor
     */
    default boolean hasMoreOlderData(long candleDeltaMs)
    {
        // Default: no pagination — nothing to page back to.
        return false;
    }
}
