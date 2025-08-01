package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.request.PlayerOrderReadDataListRequest;
import net.kroia.stockmarket.networking.packet.request.PriceHistoryRequest;
import net.kroia.stockmarket.networking.packet.request.TradingViewDataRequest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientMarket {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final TradingPair tradingPair;
    private boolean isAlive = true;

    ClientMarket(TradingPair tradingPair) {
        this.tradingPair = tradingPair;
    }
    public TradingPair getTradingPair() {
        return tradingPair;
    }
    public boolean isAlive() {
        return isAlive;
    }
    public void setDead() {
        isAlive = false;
    }

    public void requestBotSettingsData(Consumer<BotSettingsData> callback)
    {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.BOT_SETTINGS_REQUEST.sendRequestToServer(tradingPair, callback);
    }
    public void requestOrderBookVolume(int maxHistoryPointCount, int minimalVisiblePrice, int maximalVisiblePrice, int tileCount,
                                       Consumer<OrderBookVolumeData> callback)
    {
        if(checkDeadAndDebug())
            return;
        TradingViewDataRequest.Input input = new TradingViewDataRequest.Input(tradingPair,maxHistoryPointCount, minimalVisiblePrice, maximalVisiblePrice, tileCount);
        StockMarketNetworking.ORDER_BOOK_VOLUME_REQUEST.sendRequestToServer(input, callback);
    }
    public void requestOrderBookVolume(Consumer<OrderBookVolumeData> callback)
    {
        if(checkDeadAndDebug())
            return;
        TradingViewDataRequest.Input input = new TradingViewDataRequest.Input(tradingPair);
        StockMarketNetworking.ORDER_BOOK_VOLUME_REQUEST.sendRequestToServer(input, callback);
    }
    public void requestCancelOrder(long orderID,
                                   Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        OrderCancelData cancelData = new OrderCancelData(tradingPair, orderID);
        StockMarketNetworking.ORDER_CANCEL_REQUEST.sendRequestToServer(cancelData, callback);
    }
    public void requestChangeOrder(long orderID, int newPrice,
                                   Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        OrderChangeData changeData = new OrderChangeData(tradingPair, orderID, newPrice);
        StockMarketNetworking.ORDER_CHANGE_REQUEST.sendRequestToServer(changeData, callback);
    }
    public void requestCreateMarketOrder(int volume,
                                         Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        requestCreateMarketOrder(getPlayerUUID(), volume, callback);
    }
    public void requestCreateMarketOrder(UUID orderOwnerPlayerUUID, int volume,
                                         Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        OrderCreateData createData = new OrderCreateData(orderOwnerPlayerUUID, tradingPair, volume);
        StockMarketNetworking.ORDER_CREATE_REQUEST.sendRequestToServer(createData, callback);
    }
    public void requestCreateLimitOrder(int volume, int limitPrice,
                                        Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        requestCreateLimitOrder(getPlayerUUID(), volume, limitPrice, callback);
    }

    public void requestCreateLimitOrder(UUID orderOwnerPlayerUUID, int volume, int limitPrice,
                                        Consumer<Boolean> callback)
    {
        if(checkDeadAndDebug())
            return;
        OrderCreateData createData = new OrderCreateData(orderOwnerPlayerUUID, tradingPair, volume, limitPrice);
        StockMarketNetworking.ORDER_CREATE_REQUEST.sendRequestToServer(createData, callback);
    }

    public void requestPlayerOrderReadDataList(Consumer<OrderReadListData> callback) {
        if(checkDeadAndDebug())
            return;
        requestPlayerOrderReadDataList(getPlayerUUID(), callback);
    }
    public void requestPlayerOrderReadDataList(UUID orderOwnerPlayerUUID,
                                               Consumer<OrderReadListData> callback) {
        if(checkDeadAndDebug())
            return;
        PlayerOrderReadDataListRequest.InputData inputData = new PlayerOrderReadDataListRequest.InputData(tradingPair, orderOwnerPlayerUUID);
        StockMarketNetworking.PLAYER_ORDER_READ_DATA_LIST_REQUEST.sendRequestToServer(inputData, callback);
    }

    public void requestPriceHistory(int maxHistoryPointCount, Consumer<PriceHistoryData> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.PRICE_HISTORY_REQUEST.sendRequestToServer(new PriceHistoryRequest.Input(tradingPair, maxHistoryPointCount), callback);
    }
    public void requestGetMarketSettings(Consumer<ServerMarketSettingsData> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.GET_SERVER_MARKET_SETTINGS_REQUEST.sendRequestToServer(new TradingPairData(tradingPair), callback);
    }
    public void requestSetMarketSettings(ServerMarketSettingsData settings, Consumer<Boolean> callback) {
        if(checkDeadAndDebug())
            return;
        if(settings.tradingPairData.toTradingPair().getUUID().compareTo(tradingPair.getUUID()) != 0) {
            throw new IllegalArgumentException("Settings trading tradingPair does not match the market trading tradingPair.\n"+
                                               "Did you copy the settings from another market?");
        }
        StockMarketNetworking.SET_SERVER_MARKET_SETTINGS_REQUEST.sendRequestToServer(settings, callback);
    }

    public void requestTradingViewData(int maxHistoryPointCount, int minimalVisiblePrice, int maximalVisiblePrice, int tileCount,
                                       Consumer<TradingViewData> callback) {
        if(checkDeadAndDebug())
            return;
        TradingViewDataRequest.Input input = new TradingViewDataRequest.Input(tradingPair, maxHistoryPointCount, minimalVisiblePrice, maximalVisiblePrice, tileCount);
        StockMarketNetworking.TRADING_VIEW_DATA_REQUEST.sendRequestToServer(input, callback);
    }
    public void requestTradingViewData(Consumer<TradingViewData> callback) {
        if(checkDeadAndDebug())
            return;
        TradingViewDataRequest.Input input = new TradingViewDataRequest.Input(tradingPair);
        StockMarketNetworking.TRADING_VIEW_DATA_REQUEST.sendRequestToServer(input, callback);
    }


    public void requestBotTargetPrice(Consumer<Integer> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.BOT_TARGET_PRICE_REQUEST.sendRequestToServer(new TradingPairData(tradingPair), callback);
    }


    public void requestDefaultMarketSetupDataGroups(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.DEFAULT_MARKET_SETUP_DATA_GROUPS_REQUEST.sendRequestToServer(true, callback);
    }

    public void requestDefaultMarketSetupDataGroup(String groupName, Consumer<MarketFactory.DefaultMarketSetupDataGroup> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.DEFAULT_MARKET_SETUP_DATA_GROUP_REQUEST.sendRequestToServer(groupName, callback);
    }

    public void requestDefaultMarketSetupData(Consumer<MarketFactory.DefaultMarketSetupData> callback) {
        if(checkDeadAndDebug())
            return;
        StockMarketNetworking.DEFAULT_MARKET_SETUP_DATA_REQUEST.sendRequestToServer(new TradingPairData(tradingPair), callback);
    }





    // Logging methods
    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ClientMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket: "+ tradingPair.getShortDescription() + "] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ClientMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ClientMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected boolean checkDeadAndDebug()
    {
        if(!isAlive) {
            debug("Market is dead, cannot perform operation.");
            return true;
        }
        return false;
    }

    private StockMarketNetworking getNetworking() {
        return BACKEND_INSTANCES.NETWORKING;
    }
    private UUID getPlayerUUID() {
        return ClientStockMarketManager.getLocalPlayerUUID();
    }
}
