package net.kroia.stockmarket.api;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.MarketFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface IClientMarket {
    TradingPair getTradingPair();
    boolean isAlive();
    void setDead();

    void requestBotSettingsData(Consumer<BotSettingsData> callback);
    void requestOrderBookVolume(int maxHistoryPointCount,
                                int minimalVisiblePrice,
                                int maximalVisiblePrice,
                                int tileCount,
                                boolean requestBotTargetPrice,
                                Consumer<OrderBookVolumeData> callback);
    void requestOrderBookVolume(Consumer<OrderBookVolumeData> callback);
    void requestCancelOrder(long orderID,
                            Consumer<Boolean> callback);
    void requestChangeOrder(long orderID, int newPrice,
                            Consumer<Boolean> callback);
    void requestCreateMarketOrder(int volume,
                                  Consumer<Boolean> callback);
    void requestCreateMarketOrder(UUID orderOwnerPlayerUUID, int volume,
                                  Consumer<Boolean> callback);
    void requestCreateLimitOrder(int volume, int limitPrice,
                                 Consumer<Boolean> callback);

    void requestCreateLimitOrder(UUID orderOwnerPlayerUUID, int volume, int limitPrice,
                                 Consumer<Boolean> callback);

    void requestPlayerOrderReadDataList(Consumer<OrderReadListData> callback);
    void requestPlayerOrderReadDataList(UUID orderOwnerPlayerUUID,
                                        Consumer<OrderReadListData> callback);

    void requestPriceHistory(int maxHistoryPointCount, Consumer<PriceHistoryData> callback);
    void requestGetMarketSettings(Consumer<ServerMarketSettingsData> callback);
    void requestSetMarketSettings(ServerMarketSettingsData settings, Consumer<Boolean> callback);

    void requestTradingViewData(int maxHistoryPointCount,
                                int minimalVisiblePrice,
                                int maximalVisiblePrice,
                                int tileCount,
                                boolean requestBotTargetPrice,
                                Consumer<TradingViewData> callback);
    void requestTradingViewData(Consumer<TradingViewData> callback, boolean requestBotTargetPrice);
    void requestTradingViewData(Consumer<TradingViewData> callback);

    void requestBotTargetPrice(Consumer<Integer> callback);

    void requestDefaultMarketSetupDataGroups(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback);

    void requestDefaultMarketSetupDataGroup(String groupName, Consumer<MarketFactory.DefaultMarketSetupDataGroup> callback);
    void requestDefaultMarketSetupData(Consumer<MarketFactory.DefaultMarketSetupData> callback);

    void requestChartReset(Consumer<Boolean> callback);
    void requestMarketOpen(boolean open, Consumer<Boolean> callback);

}
