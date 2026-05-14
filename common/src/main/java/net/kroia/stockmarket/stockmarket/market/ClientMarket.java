package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.banking.bankmanager.BankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IClientMarket;
import net.kroia.stockmarket.networking.request.OrderbookVolumeRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ClientMarket implements IClientMarket
{
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final ItemID itemID;
    private final AsyncMarket asyncMarket;
    public static class PriceHistoryContainer
    {
        public final static class ServerRelativeTimer extends TimerMillis
        {
            public static long timeOffsetMS = 0;
            public ServerRelativeTimer(boolean autoRestart) {
                super(autoRestart);
            }
            @Override
            public long currentTimeMillis()
            {
                return timeOffsetMS + System.currentTimeMillis();
            }
        }
        private final ServerRelativeTimer timer;
        public final PriceHistoryData history;

        public PriceHistoryContainer(ItemID itemID, int itemScaleFactor, long deltaT)
        {
            timer = new ServerRelativeTimer(true);
            timer.start(deltaT);
            history = new PriceHistoryData(itemID, itemScaleFactor,  new ArrayList<>(), 0);
        }
        public void loadFrom(PriceHistoryData other, long currentServerTime, boolean createEmptyCandleForTimeGaps)
        {
            history.loadFrom(other, timer.getDuration(), currentServerTime, other.getCurrentMarketPrice(), createEmptyCandleForTimeGaps);
        }
        public void update(long serverTime)
        {
            if(timer.check())
            {
                history.startNewCandle(serverTime);
            }
        }
        public long getDeltaT()
        {
            return timer.getDuration();
        }
    }
    /*public static class OrderbookVolumeContainer
    {

    }*/
    private final Map<Long, PriceHistoryContainer> priceHistoryDataMap = new HashMap<>(); // Map with an individual buffer for the key: candle delta time
    public static final long CANDLE_TIME_1_MIN = 1000*60;
    public static final long CANDLE_TIME_5_MIN = CANDLE_TIME_1_MIN * 5;
    public static final long CANDLE_TIME_15_MIN = CANDLE_TIME_1_MIN * 15;
    public static final long CANDLE_TIME_1_HOUR = CANDLE_TIME_1_MIN * 60;
    public static final long CANDLE_TIME_4_HOUR = CANDLE_TIME_1_HOUR * 4;
    public static final long CANDLE_TIME_1_DAY = CANDLE_TIME_1_HOUR * 24;
    public static final long[] AVAILABLE_CANDLE_TIME_DELTAS =
            {
                    CANDLE_TIME_1_MIN,
                    CANDLE_TIME_5_MIN,
                    CANDLE_TIME_15_MIN,
                    CANDLE_TIME_1_HOUR,
                    CANDLE_TIME_4_HOUR,
                    CANDLE_TIME_1_DAY
            };

    private @Nullable UUID marketPriceUpdateStreamID = null;

    private final int itemScaleFactor;
    private long currentMarketPrice;

    public ClientMarket(@NotNull ItemID itemID, int itemScaleFactor)
    {
        this.itemScaleFactor =  itemScaleFactor;
        this.itemID = itemID;
        this.asyncMarket = AsyncMarket.createClientMarket(itemID);

        for (long delta : AVAILABLE_CANDLE_TIME_DELTAS) {
            priceHistoryDataMap.put(delta, new PriceHistoryContainer(itemID, itemScaleFactor, delta));
        }
    }

    public void update(long serverTime)
    {
        if(marketPriceUpdateStreamID != null) {

            for(PriceHistoryContainer priceHistoryContainer : priceHistoryDataMap.values())
            {
                priceHistoryContainer.update(serverTime);
            }
        }
    }


    public int getItemFractionScaleFactor()
    {
        return itemScaleFactor;
    }
    public @NotNull ItemID getItemID()
    {
        return itemID;
    }

    public static long[] getAvailableCandleTimeDeltas()
    {
        return AVAILABLE_CANDLE_TIME_DELTAS;
    }
    public @Nullable PriceHistoryData getPriceHistoryData(long candleTimeDelta)
    {
        PriceHistoryContainer historyContainer = priceHistoryDataMap.get(candleTimeDelta);
        if(historyContainer == null)
            return null;
        return historyContainer.history;
    }
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
    public double getCurrentMarketRealPrice()
    {
        return BankManager.convertToRealAmountStatic(currentMarketPrice, itemScaleFactor);
    }
    public void requestFullPriceHistoryUpdate()
    {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - CANDLE_TIME_1_DAY;
        //requestFullPriceHistoryUpdate(0, Long.MAX_VALUE);
        requestFullPriceHistoryUpdate(startTime, endTime);
    }
    public void requestFullPriceHistoryUpdate(long startTime, long endTime)
    {
        // timeOffsetMS is synced once at player join (see ClientMarketManager.onPlayerJoin).
        MarketPriceHistoryRequest.InputData priceChunkRequestData = new MarketPriceHistoryRequest.InputData(itemID, startTime, endTime);
        BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.sendRequestToServer(priceChunkRequestData).thenAccept((historyData) ->
        {
            info("Price chunk received for: "+itemID);
            for(PriceHistoryContainer priceHistoryContainer : priceHistoryDataMap.values())
            {
                priceHistoryContainer.loadFrom(historyData,
                        PriceHistoryContainer.ServerRelativeTimer.timeOffsetMS + System.currentTimeMillis(),
                        BACKEND_INSTANCES.SETTINGS.isFillMissingCandlesticks());
            }
        });



    }
    public boolean subscribeToMarketPriceUpdate()
    {
        if(marketPriceUpdateStreamID != null)
            return false;

        //if(priceHistoryData.getCandles().isEmpty())
        {
            // Request historical candle data first
            requestFullPriceHistoryUpdate(); // lazy update since it replaces all history data points
        }

        marketPriceUpdateStreamID = StreamSystem.startServerToClientStream(BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_STREAM, itemID, (price)->
        {
            currentMarketPrice = price.marketPrice;
            for(PriceHistoryContainer priceHistoryContainer : priceHistoryDataMap.values())
                priceHistoryContainer.history.setCurrentMarketPrice(currentMarketPrice, price.tradedVolume);
        },()->
        {
            // Stream stopped
            info("MARKET_PRICE_STREAM stopped for itemID: "+itemID);
            marketPriceUpdateStreamID  = null;
        });
        return marketPriceUpdateStreamID != null;
    }
    public boolean unsubscribeFromMarketPriceUpdate()
    {
        if(marketPriceUpdateStreamID == null)
            return false;
        StreamSystem.stopStream(marketPriceUpdateStreamID);
        return true;
    }




    public CompletableFuture<CreateOrderRequest.OutputData> createLimitOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, volume, price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.max(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, double volume, double price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.min(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createMarketOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, volume, 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.max(0, volume), 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, double volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.min(0, volume), 0);
    }


    public CompletableFuture<CreateOrderRequest.OutputData> createOrder(int bankAccountNr, Order.Type type, double volume, double price)
    {
        CreateOrderRequest.InputData inputData = new CreateOrderRequest.InputData(itemID, bankAccountNr, type, volume, price);
        CompletableFuture<CreateOrderRequest.OutputData> future = new CompletableFuture<>();
        BACKEND_INSTANCES.NETWORKING.CREATE_ORDER_REQUEST.sendRequestToServer(inputData).thenAccept(future::complete);
        return future;
    }



    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr)
    {
        return requestPendingOrders(bankAccountNr, null, 0, Long.MAX_VALUE);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@NotNull UUID executorPlayerFilter)
    {
        return requestPendingOrders(-1, executorPlayerFilter, 0, Long.MAX_VALUE);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(long timeBegin, long timeEnd)
    {
        return requestPendingOrders(-1, null, timeBegin, timeEnd);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@NotNull UUID executorPlayerFilter, long timeBegin, long timeEnd)
    {
        return requestPendingOrders(-1, executorPlayerFilter, timeBegin, timeEnd);
    }
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr, long timeBegin, long timeEnd)
    {
        return requestPendingOrders(bankAccountNr, null, timeBegin, timeEnd);
    }

    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(int bankAccountNr,
                                                                                  @Nullable UUID executorPlayerFilter,
                                                                                  long timeBegin,
                                                                                  long timeEnd)
    {
        ActiveOrdersRequest.InputData inp = new ActiveOrdersRequest.InputData(itemID, bankAccountNr, executorPlayerFilter, timeBegin, timeEnd);
        return BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_REQUEST.sendRequestToServer(inp);
    }

    public CompletableFuture<OrderbookVolumeRequest.OutputData> requestOrderbookVolume(double startPrice, double endPrice)
    {
        return requestOrderbookVolume(startPrice, endPrice, 20);
    }
    public CompletableFuture<OrderbookVolumeRequest.OutputData> requestOrderbookVolume(double startPrice, double endPrice, int chunkCount)
    {
        OrderbookVolumeRequest.InputData inp = new OrderbookVolumeRequest.InputData(itemID, startPrice, endPrice, chunkCount);
        return BACKEND_INSTANCES.NETWORKING.ORDERBOOK_VOLUME_REQUEST.sendRequestToServer(inp);
    }


    public CompletableFuture<MarketSettings> getSettings()
    {
        return asyncMarket.getSettingsAsync();
    }
    public CompletableFuture<Boolean> setSettings(MarketSettings settings)
    {
        return asyncMarket.setSettingsAsync(settings);
    }
    public CompletableFuture<Boolean> isMarketOpenAsync()
    {
        return asyncMarket.isMarketOpenAsync();
    }
    public CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen)
    {
        return asyncMarket.setMarketOpenAsync(marketOpen);
    }


    @Override
    public String toString()
    {
        return "ClientMarket:" + itemID + " Price:" + BankManager.convertToRealAmountStatic(currentMarketPrice, itemScaleFactor);
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarket:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientMarket:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientMarket:"+itemID+"]: "+message);
    }
}
