package net.kroia.stockmarket.api.plugin.interaction;

import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IPluginOrderBook {
    /**
     * Gets all buy orders, currently in the order book.
     * @return List of all orders in the book.
     */
    @NotNull List<Order> getBuyOrders();
    /**
     * Gets all sell orders, currently in the order book.
     * @return List of all orders in the book.
     */
    @NotNull List<Order> getSellOrders();

    /**
     * Gets all orders, that are created since the last update.
     * @return List of new orders.
     */
    @NotNull List<Order> getNewOrders();

    /**
     * Gets the total volume of orders in the book,
     * between the given price range (inclusive).
     * Prices are defined as real price, not the internal unscaled price.
     * Meaning it can be 0.1f while the internal price is 10 (if scale is 100).
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @return Total volume of orders in the given price range.
     *         The volume is also scaled.
     *         For Items that can be traded in fractions, the volume can be a fraction of 1.
     */
    float getRealVolume(double minPrice, double maxPrice);

    /**
     * Gets the total volume of orders at the given price.
     * @param backendPrice The price to get the volume at.
     *                     This is the internal unscaled price value!
     * @return Total volume of orders at the given price.
     */
    float getRealVolume(long backendPrice);

    float getRealVirtualVolume(long backendPrice);

    long getRawVolume(long backendPrice);

    float getRawVirtualVolume(long backendPrice);


    /**
     * Gets the price range where it is possible to change the volume of the virtual order-book.
     * @return A tuple containing the minimum and maximum editable price.
     */
    @NotNull Tuple<@NotNull Double,@NotNull  Double> getEditablePriceRange();

    /**
     * Gets the price range where it is possible to change the volume of the virtual order-book,
     * using the internal unscaled price values.
     * @return A tuple containing the minimum and maximum editable price.
     */
    @NotNull Tuple<@NotNull Long,@NotNull  Long> getEditableBackendPriceRange();

    /**
     * Creates a uniform distribution of volume between the given price range (inclusive).
     * The area of the volume distribution is equal to the given volume.
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @param volume Total volume to distribute between the given price range.
     *               The volume is always positive.
     *               The backend will determine the correct sign to match the correct side of the order book (Buy/Sell).
     */
    void setRawVolume(double minPrice, double maxPrice, float volume);

    /**
     * Sets the volume distribution using the given array of volume values.
     * @param backendStartPrice The price at which the first volume value is set.
     *                          This is the internal unscaled price value!
     * @param volume Array of volume values.
     *               Each volume element can be positive or negative but if a negative volume is in the buy side of the order book,
     *               it will be set to 0 and vice versa for the sell side of the order book.
     */
    void setRawVolume(long backendStartPrice, float[] volume);


    /**
     * Adds a uniform distribution of volume between the given price range (inclusive).
     * The area of the added volume distribution is equal to the given volume.
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @param volume Total volume to add between the given price range.
     *               The volume is always positive.
     *               The backend will determine the correct sign to match the correct side of the order book (Buy/Sell).
     */
    void addRawVolume(double minPrice, double maxPrice, float volume);

    /**
     * Adds the volume distribution using the given array of volume values.
     * @param backendStartPrice The price at which the first volume value is added.
     *                          This is the internal unscaled price value!
     * @param volume Array of volume values.
     *               Each volume element can be positive or negative but if a negative volume is in the buy side of the order book,
     *               it will be set to 0 and vice versa for the sell side of the order book.
     */
    void addRawVolume(long backendStartPrice, float[] volume);


    void resetVirtualVolume();



    /**
     * The provided function is used to calculate the order book volume that is not inside the editable price range,
     * since in that region volume values can not be stored.
     * @param distributionCalculator that takes a price as an input and returns the volume at that price.
     *                                   The volume must be >= 0, The sign will be determined internally to match the correct
     *                                   side of the order book (Buy/Sell).
     *                                   Values <= 0 will be clamped to 0.
     */
    void registerDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator);

    /**
     * Removes the default volume distribution function from the system.
     */
    void unregisterDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator);

    /**
     * Gets the volume at the given price
     * The value is based on the IVolumeDistributionCalculator objects
     *
     * @param pickPrice the real value price at which the volume gets measured
     * @return positive volume for buy orders, negative volume for sell orders.
     *         The real volume at the given price
     */
    float getDefaultRealVolume(double pickPrice);
    float getDefaultRawVolume(double pickPrice);

    /**
     * Batch variant of {@link #getDefaultRawVolume(double)}.
     * Computes the default raw volume for {@code count} consecutive backend price levels,
     * starting at {@code backendStartPrice}.
     * Loop-invariant values (current market price, item fraction scale factor) are fetched
     * only once for the whole range, which makes this considerably cheaper than calling
     * {@link #getDefaultRawVolume(double)} once per price level.
     *
     * @param backendStartPrice the first backend (internal unscaled) price level to sample
     * @param count the number of consecutive backend price levels to sample
     * @return an array of length {@code count} where index {@code i} holds the default raw
     *         volume at backend price {@code backendStartPrice + i}.
     *         Positive volume for buy orders (below the current market price),
     *         negative volume for sell orders (at or above the current market price).
     */
    float[] getDefaultRawVolume(long backendStartPrice, int count);

    /**
     * Gets the total capital (volume × price) in the given backend price range.
     * Useful for measuring monetary depth in a price region.
     * @param startPrice the start of the price range (backend/unscaled price)
     * @param endPrice the end of the price range (backend/unscaled price)
     * @return the total capital in the price range
     */
    float getCapital(long startPrice, long endPrice);

    /**
     * Gets all buy orders within the given backend price range.
     * @param startPrice the minimum price (backend/unscaled, inclusive)
     * @param endPrice the maximum price (backend/unscaled, inclusive)
     * @return list of buy orders in the price range
     */
    @NotNull List<Order> getBuyOrders(long startPrice, long endPrice);

    /**
     * Gets all sell orders within the given backend price range.
     * @param startPrice the minimum price (backend/unscaled, inclusive)
     * @param endPrice the maximum price (backend/unscaled, inclusive)
     * @return list of sell orders in the price range
     */
    @NotNull List<Order> getSellOrders(long startPrice, long endPrice);
}
