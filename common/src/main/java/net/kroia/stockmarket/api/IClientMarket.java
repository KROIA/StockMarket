package net.kroia.stockmarket.api;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.MarketFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface IClientMarket {

    /**
     * @return the TradingPair associated with this market.
     */
    TradingPair getTradingPair();

    /**
     * @return true if this client market instance is alive,
     * Do not use it if it returns false, as it means the market may be destroyed.
     */
    boolean isAlive();


    void requestBotSettingsData(Consumer<BotSettingsData> callback);
    void requestOrderBookVolume(int maxHistoryPointCount,
                                float minimalVisiblePrice,
                                float maximalVisiblePrice,
                                int tileCount,
                                boolean requestBotTargetPrice,
                                Consumer<OrderBookVolumeData> callback);
    void requestOrderBookVolume(Consumer<OrderBookVolumeData> callback);
    void requestCancelOrder(long orderID,
                            Consumer<Boolean> callback);
    void requestChangeOrder(long orderID, float newPrice,
                            Consumer<Boolean> callback);
    void requestCreateMarketOrder(int bankAccountNumber, float volume,
                                  Consumer<Boolean> callback);
    void requestCreateMarketOrder(UUID orderOwnerPlayerUUID, int bankAccountNumber, float volume,
                                  Consumer<Boolean> callback);
    void requestCreateLimitOrder(int bankAccountNumber, float volume, float limitPrice,
                                 Consumer<Boolean> callback);

    void requestCreateLimitOrder(UUID orderOwnerPlayerUUID, int bankAccountNumber, float volume, float limitPrice,
                                 Consumer<Boolean> callback);

    void requestPlayerOrderReadDataList(Consumer<OrderReadListData> callback);
    void requestPlayerOrderReadDataList(UUID orderOwnerPlayerUUID,
                                        Consumer<OrderReadListData> callback);

    void requestPriceHistory(int maxHistoryPointCount, Consumer<PriceHistoryData> callback);
    void requestGetMarketSettings(Consumer<ServerMarketSettingsData> callback);
    void requestSetMarketSettings(ServerMarketSettingsData settings, Consumer<Boolean> callback);

    void requestTradingViewData(int bankAccountNumber,
                                int maxHistoryPointCount,
                                float minimalVisiblePrice,
                                float maximalVisiblePrice,
                                int tileCount,
                                boolean requestBotTargetPrice,
                                Consumer<TradingViewData> callback);
    void requestTradingViewData(int bankAccountNumber, Consumer<TradingViewData> callback, boolean requestBotTargetPrice);
    void requestTradingViewData(int bankAccountNumber, Consumer<TradingViewData> callback);

    void requestBotTargetPrice(Consumer<Float> callback);

    void requestDefaultMarketSetupDataGroups(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback);

    void requestDefaultMarketSetupDataGroup(String groupName, Consumer<MarketFactory.DefaultMarketSetupDataGroup> callback);
    void requestDefaultMarketSetupData(Consumer<List<MarketFactory.DefaultMarketSetupData>> callback);

    void requestChartReset(Consumer<Boolean> callback);
    void requestMarketOpen(boolean open, Consumer<Boolean> callback);

}
