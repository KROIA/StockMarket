package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.util.PriceHistoryData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}
