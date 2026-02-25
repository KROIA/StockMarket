package net.kroia.stockmarket.plugin.base.interaction;

import net.kroia.stockmarket.market.TradingPair;
import org.jetbrains.annotations.NotNull;

public interface IPluginMarket {
    /**
     * Gets the trading pair associated with this market.
     * @return The trading pair of the market.
     */
    @NotNull TradingPair getTradingPair();


    /**
     * Gets the interval in ticks at which the plugin should send stream packets to clients.
     * @return The tick interval for sending stream packets.
     */
    //int getStreamPacketSendTickInterval();

    /**
     * Sets the interval in ticks at which the plugin should send stream packets to clients.
     * @param interval The tick interval for sending stream packets.
     */
    //void setStreamPacketSendTickInterval(int interval);


    /**
     * Gets the default price of the market as a real price value.
     * @return The default price of the market.
     */
    float getDefaultPrice();

    /**
     * Gets the current price of the market as a real price value.
     * Already scaled using the internal scaling factor.
     * @return The current price of the market.
     */
    float getPrice();


    /**
     * Gets the current price of the specified trading pair market as a real price value.
     * Already scaled using the internal scaling factor.
     * @param pair for which market the price should be returned
     * @return the current market price of the trading pair market
     */
    //float getPriceOf(TradingPair pair);


    /**
     * Converts the internal backend price to a real price value.
     * The backend price is an integer value used for calculations and storage.
     * The real price is a float value that is scaled using the internal scaling factor.
     * @param backendPrice The internal backend price to convert.
     * @return The converted real price value.
     */
    float convertBackendPriceToRealPrice(int backendPrice);

    /**
     * Converts a real price value to the internal backend price.
     * The backend price is an integer value used for calculations and storage.
     * The real price is a float value that is scaled using the internal scaling factor.
     * @param realPrice The real price value to convert.
     * @return The converted internal backend price.
     */
    int convertRealPriceToBackendPrice(float realPrice);

    /**
     * Gets the target-price value where the market is moving towards.
     * This value must be secret from non-admin players.
     * It is the target-price value used the last time the market was updated.
     * @return The target price of the market.
     */
    float getPreviousTargetPrice();


    void setTargetPrice(float targetPrice);


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


    /**
     * Checks if a market plugin with the given registration object exists for this market.
     * @param registrationObject The registration object of the plugin to check.
     * @return True if the plugin exists, false otherwise.
     */
    //boolean marketPluginExists(PluginRegistry.MarketPluginRegistrationObject registrationObject);

    /**
     * Checks if a market plugin with the given type ID exists for this market.
     * @param pluginTypeID The type ID of the plugin to check.
     * @return True if the plugin exists, false otherwise.
     */
    //boolean marketPluginExists(String pluginTypeID);

    /**
     * Gets the market plugin with the given registration object.
     * @param registrationObject The registration object of the plugin to get.
     * @return The market plugin instance, or null if it does not exist.
     */
    //MarketPlugin getMarketPlugin(PluginRegistry.MarketPluginRegistrationObject registrationObject);

    /**
     * Gets the market plugin with the given type ID.
     * @param pluginTypeID The type ID of the plugin to get.
     * @return The market plugin instance, or null if it does not exist.
     */
    //MarketPlugin getMarketPlugin(String pluginTypeID);

}
