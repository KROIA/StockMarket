package net.kroia.stockmarket.api.plugin.interaction;

public interface IVolumeDistributionCalculator {


    /**
     * Function that defines the volume in the virtual order book for a given real price
     * The real price is the price that has already been scaled, for example: 0.58 Dollar
     *
     * The returned volume defines the amount of virtual buy limit orders (if positive) or
     * the virtual sell limit order (if negative)
     * @param marketPrice, the current price of the market -> the volume at this position should be around 0
     * @param volumePickPrice, the price on which the volume gets measured and returned
     *                         the returned value can be positive or negative,
     *                         volume below the current market price will always count as positive
     *                         volume above the current market price will always count as negative
     * @return Virtual order book volume at the given price
     */
    float getVolume(double marketPrice, double volumePickPrice);

    /**
     * T-210: lower bound of the real price range where this calculator's ask volume only shrinks.
     * <p><b>Contract:</b> for every pair of real prices {@code bound <= p1 <= p2},
     * {@code |getVolume(marketPrice, p2)| <= |getVolume(marketPrice, p1)|}: the ask volume is
     * non-increasing upward from the returned price.</p>
     * <p>The matching walk relies on it: once a price level at or above the bound holds less than half a
     * raw unit of default volume, every default level above it rounds to 0, so the walk jumps straight to
     * the next remembered depth (or ends) instead of reading up to millions of empty levels.</p>
     * <p>The default {@link Double#POSITIVE_INFINITY} means "no such bound known": nothing is skipped
     * and every level is read, which is always exact, only slower. Override it only with a value the
     * formula provably satisfies. A bound that is too low (volume rises again above it) breaks matching
     * exactness: the walk skips ask volume that exists, so orders fill at worse prices or not at all.</p>
     *
     * @param marketPrice the current real market price (same value {@link #getVolume} receives)
     * @return real price from which the ask volume is non-increasing, or {@link Double#POSITIVE_INFINITY}
     */
    default double askMonotoneFrom(double marketPrice)
    {
        return Double.POSITIVE_INFINITY;
    }

}
