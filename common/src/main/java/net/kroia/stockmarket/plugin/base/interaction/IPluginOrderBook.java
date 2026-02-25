package net.kroia.stockmarket.plugin.base.interaction;

import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.plugin.base.IVolumeDistributionCalculator;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IPluginOrderBook {
    /**
     * Gets all buy orders, currently in the order book.
     * @return List of all orders in the book.
     */
    @NotNull List<LimitOrder> getBuyOrders();
    /**
     * Gets all sell orders, currently in the order book.
     * @return List of all orders in the book.
     */
    @NotNull List<LimitOrder> getSellOrders();

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
    float getVolume(float minPrice, float maxPrice);

    /**
     * Gets the total volume of orders at the given price.
     * @param backendPrice The price to get the volume at.
     *                     This is the internal unscaled price value!
     * @return Total volume of orders at the given price.
     */
    float getVolume(int backendPrice);


    /**
     * Gets the price range where it is possible to change the volume of the virtual order-book.
     * @return A tuple containing the minimum and maximum editable price.
     */
    @NotNull Tuple<@NotNull Float,@NotNull  Float> getEditablePriceRange();

    /**
     * Gets the price range where it is possible to change the volume of the virtual order-book,
     * using the internal unscaled price values.
     * @return A tuple containing the minimum and maximum editable price.
     */
    @NotNull Tuple<@NotNull Integer,@NotNull  Integer> getEditableBackendPriceRange();

    /**
     * Creates a uniform distribution of volume between the given price range (inclusive).
     * The area of the volume distribution is equal to the given volume.
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @param volume Total volume to distribute between the given price range.
     *               The volume is always positive.
     *               The backend will determine the correct sign to match the correct side of the order book (Buy/Sell).
     */
    void setVolume(float minPrice, float maxPrice, float volume);

    /**
     * Sets the volume distribution using the given array of volume values.
     * @param backendStartPrice The price at which the first volume value is set.
     *                          This is the internal unscaled price value!
     * @param volume Array of volume values.
     *               Each volume element can be positive or negative but if a negative volume is in the buy side of the order book,
     *               it will be set to 0 and vice versa for the sell side of the order book.
     */
    void setVolume(int backendStartPrice, float[] volume);


    /**
     * Adds a uniform distribution of volume between the given price range (inclusive).
     * The area of the added volume distribution is equal to the given volume.
     * @param minPrice Minimum price (inclusive)
     * @param maxPrice Maximum price (inclusive)
     * @param volume Total volume to add between the given price range.
     *               The volume is always positive.
     *               The backend will determine the correct sign to match the correct side of the order book (Buy/Sell).
     */
    void addVolume(float minPrice, float maxPrice, float volume);

    /**
     * Adds the volume distribution using the given array of volume values.
     * @param backendStartPrice The price at which the first volume value is added.
     *                          This is the internal unscaled price value!
     * @param volume Array of volume values.
     *               Each volume element can be positive or negative but if a negative volume is in the buy side of the order book,
     *               it will be set to 0 and vice versa for the sell side of the order book.
     */
    void addVolume(int backendStartPrice, float[] volume);




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
     * @return positive volume for buy orders, negative volume for sell orders
     */
    float getDefaultVolume(float pickPrice);
}
