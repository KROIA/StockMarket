package net.kroia.stockmarket.api;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IServerMarketManager  {


    /**
     * Gets the settings data for the bot for a given market, if available.
     * @Note The BotSettingsData is used as payload for sending it to the client over the network.
     * @return the BotSettingsData if available, when no bot exists it returns null.
     */
    @Nullable BotSettingsData getBotSettingsData(@NotNull TradingPair pair);

    /**
     * Gets the settings data for the virtual order book for the given market, if available.
     * @Note The VirtualOrderBookSettingsData is used as payload for sending it to the client over the network.
     * @return the VirtualOrderBookSettingsData if available, when no virtual order book exists it returns null.
     */
    @Nullable VirtualOrderBookSettingsData getVirtualOrderBookSettingsData(@NotNull TradingPair pair);

    /**
     * Gets the trading pair data for the given market.
     * @Note The TradingPairData is used as payload for sending it to the client over the network.
     * @return the TradingPairData for the market.
     */
    @Nullable TradingPairData getTradingPairData(@NotNull TradingPair pair);

    /**
     * Gets the histogram data of the order book in a specific price range for the given market.
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
    @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair, int historyViewCount, float minPrice, float maxPrice, int tileCount);


    /**
     * Retrieves the order history for the given pair
     * @param pair The trading pair to retrieve history for
     * @return the order history for that trading pair (default implementation will return the most recent orders if pair is null.
     */
    @Nullable Order[] getOrderHistoryForMarket(TradingPair pair);


    /**
     * puts a new order into the history
     * @param pair the market to add the order to
     * @param order the order to add to the market history
     */
    boolean logNewOrderToHistory(TradingPair pair, Order order);



    /**
     * Gets the histogram data of the order book in an optimal price range for a given market.
     * @Note The OrderBookVolumeData is used as payload for sending it to the client over the network.
     * @return OrderBookVolumeData containing the min and max price, and the volume array.
     */
    @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair);

    /**
     * Gets the metadata for a specific order in a given market.
     * @Note The OrderReadData is used as payload for sending it to the client over the network.
     * @param orderID the ID of the order to get the metadata for.
     * @return OrderReadData containing the metadata for the order.
     */
    @Nullable OrderReadData getOrderReadData(@NotNull TradingPair pair, long orderID);

    /**
     * Gets the metadata for a list of orders in a given market.
     * @Note The OrderReadListData is used as payload for sending it to the client over the network.
     * @param orderIDs the IDs of the orders to get the metadata for.
     * @return OrderReadListData containing the metadata for the orders.
     */
    @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, @NotNull List<@NotNull Long> orderIDs);

    /**
     * Gets the metadata for the orders of a specific player in a given market.
     * It will include all orders that belong to the player.
     * @Note The OrderReadListData is used as payload for sending it to the client over the network.
     * @param playerUUID the UUID of the player to get the orders for.
     * @return OrderReadListData containing the metadata for the player's orders.
     */
    @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, @NotNull UUID playerUUID);

    /**
     * Gets the price history data for the given market.
     * This data is used to visualize the candlestick chart.
     * @Note The PriceHistoryData is used as payload for sending it to the client over the network.
     * @param maxHistoryPointCount the maximum number of history points to return.
     *                             If set to -1, all history points are returned.
     * @return PriceHistoryData containing the price history data.
     */
    @Nullable PriceHistoryData getPriceHistoryData(@NotNull TradingPair pair, int maxHistoryPointCount);

    /**
     * Gets the trading view data for the given market.
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
    @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair,
                                                 int bankAccountNumber,
                                                 int maxHistoryPointCount,
                                                 float minVisiblePrice,
                                                 float maxVisiblePrice,
                                                 int orderBookTileCount,
                                                 boolean requestBotTargetPrice);

    /**
     * Gets the trading view data for the given market.
     * @Note The TradingViewData is used as payload for sending it to the client over the network.
     * @param bankAccountNumber the bankAccountNumber for which the orders get requested to be visualized in the trading view.
     * @return TradingViewData containing the trading view data.
     */
    @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair, int bankAccountNumber);

    /**
     * Gets the settings data for the given market.
     * @return the ServerMarketSettingsData containing the market settings.
     */
    @Nullable ServerMarketSettingsData getMarketSettingsData(@NotNull TradingPair pair);

    /**
     * Gets the list of all trading pairs available in the market.
     * @Note The TradingPairListData is used as payload for sending it to the client over the network.
     * @return TradingPairListData containing the list of trading pairs.
     */
    @NotNull TradingPairListData getTradingPairListData();

    /**
     * Sets the market settings data for the given market.
     * @param pair the trading pair to set the settings for.
     * @param settingsData the ServerMarketSettingsData to set, can be null to remove the settings.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setMarketSettingsData(@NotNull TradingPair pair, @Nullable ServerMarketSettingsData settingsData);

    /**
     * Sets the bot settings data for the given market.
     * @param pair the trading pair to set the bot settings for.
     * @param botSettingsData the BotSettingsData to set, can be null to remove the bot settings.
     * @return true if the bot settings were successfully set, false otherwise.
     */
    boolean setBotSettingsData(@NotNull TradingPair pair, @Nullable BotSettingsData botSettingsData);

    /**
     * Sets the settings for the virtual order book for a given market.
     * @param virtualOrderBookSettingsData the settings data to set.
     * @return true if the settings were successfully set, false otherwise.
     */
    boolean setVirtualOrderBookSettingsData(@NotNull TradingPair pair, @Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData);

    /**
     * Handles the order creation data sent by the client.
     * It will create the order according to the data provided.
     * @param orderCreateData the OrderCreateData containing the order details.
     * @param sender the player who sent the order creation request.
     * @return true if the order was successfully created, false otherwise.
     */
    boolean handleOrderCreateData(@NotNull OrderCreateData orderCreateData, @NotNull ServerPlayer sender);

    /**
     * Handles the order change data sent by the client.
     * It will change the order according to the data provided.
     * @param orderChangeData the OrderChangeData containing the order details.
     * @param sender the player who sent the order change request.
     * @return true if the order was successfully changed, false otherwise.
     */
    boolean handleOrderChangeData(@NotNull OrderChangeData orderChangeData, @NotNull ServerPlayer sender);

    /**
     * Handles the order cancel data sent by the client.
     * It will cancel the order according to the data provided.
     * @param orderCancelData the OrderCancelData containing the order details.
     * @param sender the player who sent the order cancel request.
     * @return true if the order was successfully canceled, false otherwise.
     */
    boolean handleOrderCancelData(@NotNull OrderCancelData orderCancelData, @NotNull ServerPlayer sender);


    /**
     * Overwrites the candlestick time interval for all markets.
     * @param shiftPriceCandleIntervalMS the new time interval in milliseconds for shifting the price candles.
     */
    void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS);


    /**
     * Sets the market open state for all markets.
     * @param open true to set all markets open, false to close them.
     */
    void setAllMarketsOpen(boolean open);

    /**
     * Sets the market open state for a specific market.
     * @param pair the trading pair to set the market open state for.
     * @param open true to set the market open, false to close it.
     * @return true if the market state was successfully set, false otherwise.
     */
    boolean setMarketOpen(@NotNull TradingPair pair, boolean open);

    /**
     * Sets the market open state for a list of trading pairs.
     * @param pairsAndOpenStates a list of tuples containing the trading pair and the open state to set.
     * @return a list of booleans indicating whether the market state was successfully set for each pair.
     */
    List<Boolean> setMarketOpen(@NotNull List<@NotNull Tuple<@NotNull TradingPair, @NotNull Boolean>> pairsAndOpenStates);

    /**
     * Gets the default currency item ID used for trading.
     * @return the default currency ItemID.
     */
    ItemID getDefaultCurrencyItemID();

    /**
     * Checks if the given item is allowed for trading.
     * @param item the ItemID to check.
     * @return true if the item is allowed for trading, false otherwise.
     */
    boolean isItemAllowedForTrading(@NotNull ItemID item);

    /**
     * Gets a set of item IDs that are not tradable.
     * @return a set of ItemIDs that are not allowed for trading.
     */
    Set<ItemID> getNotTradableItems();

    /**
     * Checks if the given trading pair is allowed for trading.
     * @param pair the TradingPair to check.
     * @return true if the trading pair is allowed for trading, false otherwise.
     */
    boolean isTradingPairAllowedForTrading(@NotNull TradingPair pair);

    /**
     * Gets the market instance for the given market.
     * @param pair the TradingPair to get the market for.
     * @return the IServerMarket instance for the trading pair.
     */
    IServerMarket getMarket(@NotNull TradingPair pair);

    /**
     * Gets a list of all trading pairs available in the market.
     * @return a list of TradingPair objects representing all trading pairs.
     */
    List<TradingPair> getTradingPairs();

    /**
     * Gets a list of items that cam be used to create a new market.
     * This is used for the market creation GUI.
     * @param searchQuery the search query to filter the items.
     *                    If empty, all items are returned.
     *                    If not empty, only items that match the search query are returned. (like in the creative mode search)
     * @return a list of ItemID objects representing the items that can be used for trading.
     */
    List<ItemID> getPotentialTradingItems(@NotNull String searchQuery);

    /**
     * Checks if a market exists for the given trading pair.
     * @param pair the TradingPair to check.
     * @return true if the market exists, false otherwise.
     */
    boolean marketExists(@NotNull TradingPair pair);

    /**
     * Gets the recommended price for a given trading pair.
     * This is used to determine the initial price for a new market.
     * @param pair the TradingPair to get the recommended price for.
     * @return the recommended price as a float.
     */
    float getRecommendedPrice(TradingPair pair);


    /**
     * Creates a new market with the given default market setup data.
     * @param defaultMarketSetupData data needed to create a new market.
     * @return true if the market was successfully created, false otherwise.
     */
    boolean createMarket(MarketFactory.DefaultMarketSetupData defaultMarketSetupData);

    /**
     * Creates a bunch of markets with the given default market setup data.
     * @param category that contains multiple market setup data.
     * @return true if all markets were successfully created, false otherwise.
     */
    boolean createMarket(MarketFactory.DefaultMarketSetupDataGroup category);

    /**
     * Creates a new market for the given trading pair with the specified start price.
     * @param pair the TradingPair to create the market for.
     * @param startPrice the initial price for the market.
     * @return true if the market was successfully created, false otherwise.
     */
    boolean createMarket(@NotNull TradingPair pair, int startPrice);

    /**
     * Creates a new market for the given item and currency with the specified start price.
     * @param itemID the ItemID of the item to create the market for.
     * @param currency the ItemID of the currency to use for trading.
     * @param startPrice the initial price for the market.
     * @return true if the market was successfully created, false otherwise.
     */
    boolean createMarket(@NotNull ItemID itemID, @NotNull ItemID currency, int startPrice);

    /**
     * Creates a new market for the given item with the specified start price.
     * The default currency is used for payments in this market.
     * @param itemID the ItemID of the item to create the market for.
     * @param startPrice the initial price for the market.
     * @return true if the market was successfully created, false otherwise.
     */
    boolean createMarket(@NotNull ItemID itemID, int startPrice);

    /**
     * Creates a new market using the given server market settings data.
     * @param settingsData the ServerMarketSettingsData containing the market settings.
     * @return true if the market was successfully created, false otherwise.
     */
    boolean createMarket(@NotNull ServerMarketSettingsData settingsData);

    /**
     * Creates multiple markets using the given list of default market setup data.
     * @param defaultMarketSetupDataList a list of DefaultMarketSetupData containing the market setup data.
     * @return a list of booleans indicating whether each market was successfully created.
     */
    List<Boolean> createMarkets(@NotNull List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList);

    /**
     * Removes a market for the given trading pair.
     * @param pair the TradingPair to remove the market for.
     * @return true if the market was successfully removed, false otherwise.
     */
    boolean removeTradeItem(@NotNull TradingPair pair);

    /**
     * Removes a market for the given item and currency.
     * @param itemID the ItemID of the item to remove the market for.
     * @param currency the ItemID of the currency to remove the market for.
     * @return true if the market was successfully removed, false otherwise.
     */
    boolean removeTradeItem(@NotNull ItemID itemID, @NotNull ItemID currency);






}
