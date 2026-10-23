package net.kroia.stockmarket.stockmarket.market.core;

/**
 * Price impact of cumulative player item flow, as a pure function (real units).
 * <p>
 * The price a flow level implies is {@code anchor * e^(clamp(-flow / capacity))}: net buying
 * (negative flow) raises it, net selling lowers it, bounded by {@code minMult}/{@code maxMult}.
 * <p>
 * Used by the matching engine to make a player pay for the price impact their own fills cause,
 * while they are filling. Without it the flow only moves the target price after the fills, and
 * a player can buy at the old price and sell into the new one.
 */
public final class FlowPriceCurve {
    private final double anchorPrice;
    private final double capacity;
    private final double logMin;
    private final double logMax;

    /**
     * @param anchorPrice price at zero flow under current conditions, real units, &gt; 0
     * @param capacity    items of flow that move the price by a factor of e, &gt; 0
     * @param minMult     lower clamp of the price multiplier, 0 &lt; minMult &lt; 1
     * @param maxMult     upper clamp of the price multiplier, maxMult &gt; 1
     */
    public FlowPriceCurve(double anchorPrice, double capacity, double minMult, double maxMult) {
        this.anchorPrice = anchorPrice;
        this.capacity = capacity;
        this.logMin = Math.log(minMult);
        this.logMax = Math.log(maxMult);
    }

    private double clampExponent(double exponent) {
        return Math.min(Math.max(exponent, logMin), logMax);
    }

    /** Price multiplier for a cumulative flow (real items). */
    public double multiplier(double flow) {
        return Math.exp(clampExponent(-flow / capacity));
    }

    /**
     * The price the curve demands once flow has moved to {@code flowEnd}, starting from a book
     * that sits at {@code bookPrice} while the flow stands at {@code flowStart}.
     * <p>
     * A book inside the curve's range is taken to reflect the flow that produces its price, so a
     * book that lags the real flow is caught up at once and a stale price can't be traded against.
     * A book outside the range (news, a misconfigured anchor) says nothing about flow, so only
     * the flow this order itself adds is charged.
     */
    public double priceAt(double bookPrice, double flowStart, double flowEnd) {
        double bookFlow = flowStart;
        if (bookPrice > 0.0) {
            double exponent = Math.log(bookPrice / anchorPrice);
            if (exponent > logMin && exponent < logMax)
                bookFlow = -capacity * exponent;
        }
        return bookPrice * multiplier(flowEnd) / multiplier(bookFlow);
    }

    /**
     * The cumulative flow at which the curve's price equals {@code price}, or NaN when the price is outside
     * the curve's range (news, a misconfigured anchor), where the curve says nothing useful.
     */
    public double flowForPrice(double price) {
        if (!(price > 0.0)) return Double.NaN;
        double exponent = Math.log(price / anchorPrice);
        if (!(exponent > logMin && exponent < logMax)) return Double.NaN;
        return -capacity * exponent;
    }
}
