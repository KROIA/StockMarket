package net.kroia.stockmarket.api;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.OrderBook;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface IServerMarket {


    /**
     * Gets the settings data for the bot, if available.
     * @Note The BotSettingsData is used as payload for sending it to the client over the network.
     * @return the BotSettingsData if available, when no bot exists it returns null.
     */
    @Nullable BotSettingsData getBotSettingsData();

    /**
     * Gets the settings data for the virtual order book, if available.
     * @Note The VirtualOrderBookSettingsData is used as payload for sending it to the client over the network.
     * @return the VirtualOrderBookSettingsData if available, when no virtual order book exists it returns null.
     */
    @Nullable VirtualOrderBookSettingsData getVirtualOrderBookSettingsData();


    /**
     * Gets the trading pair data for the market.
     * @Note The TradingPairData is used as payload for sending it to the client over the network.
     * @return the TradingPairData for the market.
     */
    TradingPairData getTradingPairData();

    /**
     * Gets the histogram data of the order book in a specific price range.
     * This contains the buy and sell volume, split into tiles.
     * The tile count may be adjusted to fit the price range.
     * @Note The OrderBookVolumeData is used as payload for sending it to the client over the network.
     * @Note If the "minPrice" and "maxPrice" are both set to 0,
     *       an optimal range is determined based on the historyViewCount.
     *
     * @param historyViewCount this value is only used in case the "minPrice" and "maxPrice" are both set to 0.
     *                         In that case the historyViewCount is used to determine the price range.
     *                         The min and max price that occurred until the given time steps back
     *                         are used to determine the price range.
     *                         If set to -1, the entire history is used to determine the price range.
     *
     * @param minPrice         Start price for the histogram.
     * @param maxPrice         End price for the histogram.
     * @param tileCount        Number of tiles to split the price range into.
     *                         This is used for the volume array size but may be adjusted to fit the price range.
     *                         Do not relay on this value when reading the volume array.
     *                         Use the length of the volume array instead.
     *
     * @return OrderBookVolumeData containing the min and max price, and the volume array.
     */
    OrderBookVolumeData getOrderBookVolumeData(int historyViewCount, float minPrice, float maxPrice, int tileCount);

    /**
     * Gets the histogram data of the order book in an optimal price range.
     * @Note The OrderBookVolumeData is used as payload for sending it to the client over the network.
     * @return OrderBookVolumeData containing the min and max price, and the volume array.
     */
    OrderBookVolumeData getOrderBookVolumeData();

    /**
     * Gets the metadata for a specific order.
     * @Note The OrderReadData is used as payload for sending it to the client over the network.
     * @param orderID the ID of the order to get the metadata for.
     * @return OrderReadData containing the metadata for the order.
     */
    OrderReadData getOrderReadData(long orderID);

    /**
     * Gets the metadata for a list of orders.
     * @Note The OrderReadListData is used as payload for sending it to the client over the network.
     * @param orderIDs the IDs of the orders to get the metadata for.
     * @return OrderReadListData containing the metadata for the orders.
     */
    OrderReadListData getOrderReadListData(List<Long> orderIDs);

    /**
     * Gets the metadata for the orders of a specific player.
     * It will include all orders that belong to the player.
     * @Note The OrderReadListData is used as payload for sending it to the client over the network.
     * @param playerUUID the UUID of the player to get the orders for.
     * @return OrderReadListData containing the metadata for the player's orders.
     */
    OrderReadListData getOrderReadListData(UUID playerUUID);

    /**
     * Gets the price history data for the market.
     * This data is used to visualize the candlestick chart.
     * @Note The PriceHistoryData is used as payload for sending it to the client over the network.
     * @param maxHistoryPointCount the maximum number of history points to return.
     *                             If set to -1, all history points are returned.
     * @return PriceHistoryData containing the price history data.
     */
    PriceHistoryData getPriceHistoryData(int maxHistoryPointCount);

    /**
     * Gets the trading view data for the market.
     * This data is used to visualize the trading view chart.
     * @Note The TradingViewData is used as payload for sending it to the client over the network.
     * @param bankAccountNumber the bankAccountNumber for which the orders get requested to be visualized in the trading view.
     * @param maxHistoryPointCount the maximum number of history points to return.
     *                             If set to -1, all history points are returned.
     * @param minVisiblePrice minimum visible price for the chart. (can be 0)
     * @param maxVisiblePrice maximum visible price for the chart. (can be 0)
     * @param orderBookTileCount number of tiles to split the price range into for order book volume.
     * @param requestBotTargetPrice whether to include bot target price in the data.
     *                              (Do not send this information to non admin players)
     * @return TradingViewData containing the trading view data.
     */
    TradingViewData getTradingViewData(int bankAccountNumber,
                                       int maxHistoryPointCount,
                                       float minVisiblePrice,
                                       float maxVisiblePrice,
                                       int orderBookTileCount,
                                       boolean requestBotTargetPrice);

    /**
     * Gets the trading view data for the market.
     * @Note The TradingViewData is used as payload for sending it to the client over the network.
     * @param bankAccountNumber the bankAccountNumber for which the orders get requested to be visualized in the trading view.
     * @return TradingViewData containing the trading view data.
     */
    TradingViewData getTradingViewData(int bankAccountNumber);

    /**
     * Gets the settings data for this market.
     * @return the ServerMarketSettingsData containing the market settings.
     */
    ServerMarketSettingsData getMarketSettingsData();

    /**
     * Sets the market settings data for this market.
     * @param settingsData the ServerMarketSettingsData to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setMarketSettingsData(@Nullable ServerMarketSettingsData settingsData);

    /**
     * Sets the settings for the volatility bot.
     * @param botSettingsData bot settings data to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setBotSettingsData(@Nullable BotSettingsData botSettingsData);

    /**
     * Sets the settings for the volatility bot.
     * @param settings the settings to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setBotSettings(ServerVolatilityBot.Settings settings);

    /**
     * Sets the settings for the virtual order book.
     * @param virtualOrderBookSettingsData the settings data to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setVirtualOrderBookSettingsData(@Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData);

    /**
     * Sets the settings for the virtual order book.
     * @param settings the settings to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setVirtualOrderBookSettings(VirtualOrderBook.Settings settings);

    /**
     * Resets the virtual order book volume distribution.
     * This will clear the current volume distribution and reset it to the initial state.
     */
    void resetVirtualOrderBookVolumeDistribution();

    /**
     * Resets the historical market data.
     * This will clear the current price history and reset it to the current market price.
     */
    void resetHistoricalMarketData();


    /**
     * Gets the price scale factor for the market.
     * This is used to convert between raw prices and real prices.
     * @return the price scale factor.
     */
    int getPriceScaleFactor();

    /**
     * Gets the target price for the bot.
     * @return
     */
    float getBotTargetPrice();





    /**
     * Sets the interval for each candlestick in milliseconds.
     * After such a period, a new candlestick is created
     * @param shiftPriceCandleIntervalMS the interval in milliseconds.
     */
    void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS);

    /**
     * Gets the interval for each candle stick in milliseconds.
     * @return the interval in milliseconds.
     */
    long getShiftPriceCandleIntervalMS();







    /**
     * Creates a volatility bot with the given settings.
     * @param settings the settings for the bot.
     */
    void createVolatilityBot(ServerVolatilityBot.Settings settings);

    /**
     * Destroys the volatility bot if it exists.
     * @return true if the bot was successfully destroyed, false otherwise.
     *         It will also return false if there was no bot to destroy.
     */
    boolean destroyVolatilityBot();

    /**
     * Checks if a volatility bot exists.
     * @return true if a volatility bot exists, false otherwise.
     */
    boolean hasVolatilityBot();







    /**
     * Creates a virtual order book with the given settings.
     * @param realVolumeBookSize the size of the real volume book.
     *                           This is the size for which the virtual order book creates a real array
     *                           that is used to accumulate volume.
     *                           Volume outside this array is just virtual and can not track the amount
     *                           that gets sold or bought.
     *                           To large values may result in bad performance, since the virtual order book
     *                           needs to update each element in the array.
     * @param settings the settings for the virtual order book.
     */
    void createVirtualOrderBook(int realVolumeBookSize, VirtualOrderBook.Settings settings);

    /**
     * Destroys the virtual order book if it exists.
     * @return true if the virtual order book was successfully destroyed, false otherwise.
     *         It will also return false if there was no virtual order book to destroy.
     */
    boolean destroyVirtualOrderBook();

    /**
     * Checks if a virtual order book exists.
     * @return true if a virtual order book exists, false otherwise.
     */
    boolean hasVirtualOrderBook();






    /**
     * Gets the trading pair for the market.
     * @return the TradingPair for the market.
     */
    TradingPair getTradingPair();

    /**
     * Gets the order book for the market.
     * @Note The OrderBookInterface is used to manage the orders and their execution.
     * @return the OrderBookInterface for the market.
     */
    OrderBook getOrderBook();

    /**
     * Gets the current "raw" market price.
     * The raw market price is the price that is used internally by the market.
     * This may not be the same as the real price, since the raw price is scaled by the price scale factor.
     * @return the current raw market price.
     */
    int getCurrentRawPrice();

    /**
     * Gets the current "real" market price.
     * The real market price is the price that is used to display the price to the player.
     * This is the raw price scaled by the price scale factor.
     * @return the current real market price.
     */
    float getCurrentRealPrice();

    /**
     * Maps a real price to a raw price.
     * This is used to convert the real price to the raw price that is used internally by the market.
     * @param realPrice the real price to map.
     * @return the raw price that corresponds to the given real price.
     */
    int mapToRawPrice(float realPrice);

    /**
     * Maps a raw price to a real price.
     * This is used to convert the raw price to the real price that is displayed to the player.
     * @param rawPrice the raw price to map.
     * @return the real price that corresponds to the given raw price.
     */
    float mapToRealPrice(int rawPrice);

    /**
     * Checks if the market is currently open.
     * @return true if the market is open, false otherwise.
     */
    boolean isMarketOpen();


    /**
     * Opens the market.
     * Players can create new orders.
     */
    void openMarket();

    /**
     * Closes the market.
     * Players no longer can create new orders.
     * The bot is still able to trade.
     * Already placed orders from players are still valid and can be executed.
     */
    void closeMarket();

    /**
     * Sets the market open state.
     * @param marketOpen true to open the market, false to close it.
     */
    void setMarketOpen(boolean marketOpen);


    /**
     * Creates a limit order without placing it on the market.
     * @param playerUUID the UUID of the player creating the order.
     * @param bankAccountNumber the bank account number to use for the order.
     * @param amount the amount of items to buy or sell.
     * @param price the price per item in real currency.
     * @return the created LimitOrder, or null if the order could not be created.
     */
    //LimitOrder createLimitOrder(UUID playerUUID, int bankAccountNumber, float amount, float price);

    /**
     * Creates a market order without placing it on the market.
     * @param playerUUID the UUID of the player creating the order.
     * @param bankAccountNumber the bank account number to use for the order.
     * @param amount the amount of items to buy or sell.
     * @return the created MarketOrder, or null if the order could not be created.
     */
    //MarketOrder createMarketOrder(UUID playerUUID, int bankAccountNumber, float amount);

    /**
     * Creates a limit order for the bot without placing it on the market.
     * @param amount the amount of items to buy or sell.
     * @param price the price per item in real currency.
     * @return the created LimitOrder, or null if the order could not be created.
     */
    LimitOrder createBotLimitOrder(float amount, float price);

    /**
     * Creates a market order for the bot without placing it on the market.
     * @param amount the amount of items to buy or sell.
     * @return the created MarketOrder, or null if the order could not be created.
     */
    MarketOrder createBotMarketOrder(float amount);

    /**
     * Places an order on the market.
     * The order must be created using one of the create order functions.
     * @param order the order to place.
     * @return true if the order was successfully placed, false otherwise.
     */
    boolean placeOrder(Order order);


    /**
     * Creates a limit order for the player.
     * @param playerUUID the UUID of the player for which the order is created.
     * @param bankAccountNumber the bank account number used for the order.
     * @param amount the amount of items to buy or sell.
     *               Positive values indicate a buy order, negative values indicate a sell order.
     * @param price the price per item in real currency.
     * @return true if the order was successfully created, false otherwise.
     */
    boolean createAndPlaceLimitOrder(UUID playerUUID, int bankAccountNumber, float amount, float price);

    /**
     * Creates a market order for the player.
     * @param playerUUID the UUID of the player for which the order is created.
     * @param bankAccountNumber the bank account number used for the order.
     * @param amount the amount of items to buy or sell.
     *               Positive values indicate a buy order, negative values indicate a sell order.
     * @return true if the order was successfully created, false otherwise.
     */
    boolean createAndPlaceMarketOrder(UUID playerUUID, int bankAccountNumber, float amount);

    /**
     * Creates a limit order for the bot.
     * The bot does not have a bank account but is still able to place orders.
     * But this must be done with this function.
     * @param amount the amount of items to buy or sell.
     *               Positive values indicate a buy order, negative values indicate a sell order.
     * @param price the price per item in real currency.
     * @return true if the order was successfully created, false otherwise.
     */
    boolean createAndPlaceBotLimitOrder(float amount, float price);

    /**
     * Creates a market order for the bot.
     * The bot does not have a bank account but is still able to place orders.
     * But this must be done with this function.
     * @param amount the amount of items to buy or sell.
     *               Positive values indicate a buy order, negative values indicate a sell order.
     * @return true if the order was successfully created, false otherwise.
     */
    boolean createAndPlaceBotMarketOrder(float amount);



    /**
     * Cancels an order by its ID.
     * @param orderID the ID of the order to cancel.
     * @return true if the order was successfully canceled, false otherwise.
     */
    boolean cancelOrder(long orderID);

    /**
     * Cancels an order.
     * @param order the order to cancel.
     * @return true if the order was successfully canceled, false otherwise.
     */
    boolean cancelOrder(Order order);

    /**
     * Cancels all orders which belong to the bot.
     */
    void cancelAllBotOrders();


    /**
     * Gets the current item imbalance for the market.
     * This value indicates the difference between the total buys and sells that have been executed.
     * This is an important indicator to check if a item is over or underrated in its price.
     * For each item that gets bought by a player, the item imbalance is decreased by 1.
     * For each item that gets sold by a player, the item imbalance is increased by 1.
     * It reflects the amount of items that got "stored" by players inside the market.
     * @return the current item imbalance.
     */
    long getItemImbalance();

    /**
     * Adds an item imbalance to the market.
     * @param amount the amount of item imbalance to add. (positive or negative)
     */
    void addItemImbalance(long amount);

    /**
     * Sets the item imbalance for the market.
     * This will overwrite the current item imbalance.
     * @param itemImbalance the new item imbalance to set.
     */
    void setItemImbalance(long itemImbalance);




}
