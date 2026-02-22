package net.kroia.stockmarket.api;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAdjustmentFactorsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public interface IClientMarketManager {


    /**
     * Returns the client market for the given trading pair.
     * If the market does not exist, it will return null.
     *
     * @param pair The trading pair to get the market for.
     * @return The client market for the given trading pair, or null if it does not exist.
     */
    @Nullable IClientMarket getClientMarket(TradingPair pair);


    /**
     * Requests the creation of a market for the given trading pair.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pair the trading pair for which to create the market.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive true if the market was created successfully, false otherwise.
     */
    void requestCreateMarket(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback);

    /**
     * Requests the creation of markets for the given list of trading pairs.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param setupData is a structure with full data needed to create a parametrize a market.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of booleans indicating whether each market was created successfully.
     */
    void requestCreateMarket(@NotNull MarketFactory.DefaultMarketSetupData setupData, @NotNull Consumer<Boolean> callback);

    /**
     * Requests the creation of markets for the given list of setup data.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param setupDataList is a list of structures with full data needed to create and parametrize markets.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of booleans indicating whether each market was created successfully.
     */
    void requestCreateMarkets(@NotNull List<MarketFactory.DefaultMarketSetupData> setupDataList, @NotNull Consumer<List<Boolean>> callback);

    /**
     * Requests the removal of a market for the given trading pair.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pair the trading pair for which to remove the market.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive true if the market was removed successfully, false otherwise.
     */
    void requestRemoveMarket(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback);

    /**
     * Requests the removal of markets for the given list of trading pairs.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pairs is a list of trading pairs for which to remove the markets.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of booleans indicating whether each market was removed successfully.
     */
    void requestRemoveMarket(@NotNull List<TradingPair> pairs, @NotNull Consumer<List<Boolean>> callback );

    /**
     * Requests a reset of the chart data for the given trading pair.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pairs the trading pairs for which to reset the chart data.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive true if the chart was reset successfully, false otherwise.
     */
    void requestChartReset(@NotNull List<TradingPair> pairs, @NotNull Consumer<List<Boolean>> callback);

    /**
     * Requests to set the market open state for a specific trading pair.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pair the trading pair for which to set the market open state.
     * @param open true to open the market, false to close it.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive true if the market state was set successfully, false otherwise.
     */
    void requestSetMarketOpen(@NotNull TradingPair pair, boolean open, @NotNull Consumer<Boolean> callback);

    /**
     * Requests to set the market open state for a list of trading pairs.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pairs a list of tuples containing trading pairs and their desired open state.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of booleans indicating whether each market state was set successfully.
     */
    void requestSetMarketOpen(@NotNull List<Tuple<TradingPair, Boolean>> pairs, @NotNull Consumer<List<Boolean>> callback);

    /**
     * Requests to set the market open state for a list of trading pairs.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param pairs a list of trading pairs to set the market open state for.
     * @param allOpen true to open all markets, false to close all markets.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of booleans indicating whether each market state was set successfully.
     */
    void requestSetMarketOpen(@NotNull List<TradingPair> pairs, boolean allOpen, @NotNull Consumer<List<Boolean>> callback);

    /**
     * Requests the trading pairs available in the market.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of trading pairs.
     */
    void requestTradingPairs(@NotNull Consumer<List<TradingPair>> callback);

    /**
     * Requests the market categories available on the servers filesystem.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of market categories.
     */
    void requestMarketCategories(@NotNull Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback);

    /**
     * Request to check if a given trading pair is allowed to be traded or not.
     * @param pair the trading pair to check.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive true if the trading pair is allowed, false otherwise.
     *                 It does not take into account if the market already exists or not.
     */
    void requestIsTradingPairAllowed(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback);

    /**
     * Requests the recommended price for a given trading pair.
     * This is used for setup of new markets.
     * @param pair the trading pair for which to request the recommended price.
     * @param callback a callback that will be called with the result of the request.
     */
    void requestRecommendedPrice(@NotNull TradingPair pair, @NotNull Consumer<Float> callback);

    /**
     * Requests the potential trade items based on the search text.
     * That are items that are allowed to be traded in the market.
     * This is used for creating new markets.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param searchText the text to search for potential trade items.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive a list of ItemIDs that match the search criteria.
     */
    void requestPotentialTradeItems(@NotNull String searchText, @NotNull Consumer<List<ItemID>> callback);

    /**
     * Requests the default price adjustment factors.
     * These factors are used to create a mathematical function on which the price of given trading pairs can be
     * adjusted.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param callback a callback that will be called with the result of the request.
     *                 The callback will receive the DefaultPriceAdjustmentFactorsData.
     */
    void requestDefaultPriceAdjustmentFactors(@NotNull Consumer<DefaultPriceAdjustmentFactorsData> callback);

    /**
     * Saves the default price adjustment factors to the server.
     * This is used to update the factors that are used to adjust the price of trading pairs.
     * @Note This method should only be called by players with admin permissions.
     *       For non-admin players, this will have no effect.
     * @param data the DefaultPriceAdjustmentFactorsData containing the new factors.
     * @param callback a callback that will be called with the result of the request.
     */
    void updateDefaultPriceAdjustmentFactors(@NotNull DefaultPriceAdjustmentFactorsData data, @NotNull Consumer<DefaultPriceAdjustmentFactorsData> callback);


    /**
     * Retrieves the order history for the given pair
     * @param pair The trading pair to retrieve history for
     * @return the order history for that trading pair (default implementation will return the most recent orders if pair is null.
     */
    @NotNull List<OrderDataRecord> getOrderHistoryForMarket(TradingPair pair);


    /**
     * puts a new order into the history
     * @param pair the market to add the order to
     * @param order the order to add to the market history
     */
    boolean logNewOrderToHistory(TradingPair pair, Order order);



    /**
     * Gets the time in milliseconds when the server was started the first time using this mod.
     * @return the absolute server first startup time in milliseconds.
     */
    long getAbsoluteServerFirstStartupTimeMillis();






    /**
     * Handles the SyncTradeItemsPacket received from the server.
     * This method is called when the client receives a packet containing the trading pairs data.
     * @param packet the SyncTradeItemsPacket containing the trading pairs data.
     */
    void handlePacket(SyncTradeItemsPacket packet);
}
