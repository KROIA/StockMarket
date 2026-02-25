package net.kroia.stockmarket.plugin.base;

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
    float getVolume(float marketPrice, float volumePickPrice);

}
