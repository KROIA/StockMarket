package net.kroia.stockmarket.plugin.base;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

public interface IMarketPluginInterface {
    @NotNull TradingPair getTradingPair();


    /**
     * Gets the current price of the market as a real price value.
     * Already scaled using the internal scaling factor.
     * @return The current price of the market.
     */
    float getPrice();

    /**
     * Gets the price value where the market is moving towards.
     * This value must be secret from non-admin players.
     * It is the value used the last time the market was updated.
     * @return The target price of the market.
     */
    float getTargetPrice();

    /**
     * Adds the given delta to the target price.
     * @param delta The delta to add to the target price.
     *              Can be positive or negative.
     */
    void addToTargetPrice(float delta);

    /**
     * Creates a Limit order since the price is given.
     * @param amount Amount of items to trade.
     *               For Buy: amount is positive
     *               For Sell: amount is negative
     * @param price Price per item.
     * @return The internal order ID of the created order.
     */
    long placeOrder(float amount, float price);

    /**
     * Creates a Market order since no price is given.
     * The order will be executed at the best available price in the order book.
     * This method will not return the Order id since the order can not be canceled once placed,
     * so that information can not be used int the next update for anything.
     * @param amount Amount of items to trade.
     *               For Buy: amount is positive
     *               For Sell: amount is negative
     */
    void placeOrder(float amount);



    interface OrderBookInterface
    {
        /**
         * Gets all buy orders, currently in the order book,
         * excluding the orders that are created since the last update.
         * @return List of all orders in the book.
         */

        @NotNull List<LimitOrder> getBuyOrders();
        /**
         * Gets all sell orders, currently in the order book,
         * excluding the orders that are created since the last update.
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
         * Gets the price range where it is possible to change the volume of the virtual order-book.
         * @return A tuple containing the minimum and maximum editable price.
         */
        @NotNull Tuple<@NotNull Float,@NotNull  Float> getEditablePriceRange();

        /**
         * Creates a uniform distribution of volume between the given price range (inclusive).
         * The area of the volume distribution is equal to the given volume.
         * @param minPrice Minimum price (inclusive)
         * @param maxPrice Maximum price (inclusive)
         * @param volume Total volume to distribute between the given price range.
         */
        void setVolume(float minPrice, float maxPrice, float volume);

        /**
         * Sets the volume distribution using the given array of volume values.
         * @param startPrice The price at which the first volume value is set.
         * @param volume Array of volume values.
         * @param priceStep The price step between each volume value in the array.
         */
        void setVolume(float startPrice, float[] volume, float priceStep);

        /**
         * Adds a uniform distribution of volume between the given price range (inclusive).
         * The area of the added volume distribution is equal to the given volume.
         * @param minPrice Minimum price (inclusive)
         * @param maxPrice Maximum price (inclusive)
         * @param volume Total volume to add between the given price range.
         */
        void addVolume(float minPrice, float maxPrice, float volume);

        /**
         * Adds the volume distribution using the given array of volume values.
         * @param startPrice The price at which the first volume value is added.
         * @param volume Array of volume values.
         * @param priceStep The price step between each volume value in the array.
         */
        void addVolume(float startPrice, float[] volume, float priceStep);


        /**
         * The provided function is used to calculate the order book volume that is not inside the editable price range,
         * since in that region volume values can not be stored.
         * @param volumeDistributionFunction that takes a price as an input and returns the volume at that price.
         *                                   The volume must be >= 0, The sign will be determined internally to match the correct
         *                                   side of the order book (Buy/Sell).
         *                                   Values <= 0 will be clamped to 0.
         */
        void registerDefaultVolumeDistributionFunction(Function<Float, Float> volumeDistributionFunction);

        /**
         * Removes the default volume distribution function from the system.
         */
        void unregisterDefaultVolumeDistributionFunction();
    }

    @NotNull IMarketPluginInterface.OrderBookInterface getOrderBook();
}
