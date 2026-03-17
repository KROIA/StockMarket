package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.RequestManager;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.order.Order;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ClientMarket
{
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        StockMarketGuiElement.setBackend(backend);
    }

    private final ItemID itemID;
    private final PriceHistoryData priceHistoryData;
    private @Nullable UUID marketPriceUpdateStreamID = null;

    private long lastCandleCreationTime = System.currentTimeMillis();
    private long currentServerTime = lastCandleCreationTime;
    private long newCandleInterval = BACKEND_INSTANCES.SETTINGS.getCandleTimeMs();

    public ClientMarket(@NotNull ItemID itemID)
    {
        this.itemID = itemID;
        this.priceHistoryData = new PriceHistoryData(itemID);
    }

    public void update()
    {

        if(marketPriceUpdateStreamID != null) {
            //long now = System.currentTimeMillis();

            // Trying to sync using the server time to get the same candles as the server does
            // But it does not work 100%as intended
            if (currentServerTime - lastCandleCreationTime > newCandleInterval) {
                lastCandleCreationTime = currentServerTime;
                newCandleInterval = BACKEND_INSTANCES.SETTINGS.getCandleTimeMs();
                priceHistoryData.startNewCandle();
            }
        }
    }


    public @NotNull ItemID getItemID()
    {
        return itemID;
    }
    public @NotNull PriceHistoryData getPriceHistoryData()
    {
        return priceHistoryData;
    }
    public void requestFullPriceHistoryUpdate()
    {
        requestFullPriceHistoryUpdate(0, Long.MAX_VALUE);
    }
    public void requestFullPriceHistoryUpdate(long startTime, long endTime)
    {
        MarketPriceHistoryRequest.InputData priceChunkRequestData = new MarketPriceHistoryRequest.InputData(itemID, startTime, endTime);
        BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.sendRequestToServer(priceChunkRequestData, (historyData) ->
        {
            info("Price chunck received for: "+itemID);
            //lastCandleCreationTime = System.currentTimeMillis();
            priceHistoryData.loadFrom(historyData);
            PriceHistoryData.Candle lastCandle = priceHistoryData.getCurrentCandle();
            if(lastCandle != null) {
                lastCandleCreationTime = lastCandle.openTimestamp + newCandleInterval;
                currentServerTime = lastCandle.openTimestamp;
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
            info("Price received: " + price.marketPrice);
            currentServerTime = price.timestamp;
            priceHistoryData.setCurrentMarketPrice(price.marketPrice);
        },()->
        {
            // Stream stopped
            info("MARKET_PRICE_STREAM stopped");
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




    public CompletableFuture<CreateOrderRequest.OutputData> createLimitOrder(int bankAccountNr, long volume, long price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, volume, price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, long volume, long price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.max(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, long volume, long price)
    {
        return createOrder(bankAccountNr, Order.Type.LIMIT, Math.min(0, volume), price);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createMarketOrder(int bankAccountNr, long volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, volume, 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitBuyOrder(int bankAccountNr, long volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.max(0, volume), 0);
    }
    public CompletableFuture<CreateOrderRequest.OutputData> createLimitSellOrder(int bankAccountNr, long volume)
    {
        return createOrder(bankAccountNr, Order.Type.MARKET, Math.min(0, volume), 0);
    }


    public CompletableFuture<CreateOrderRequest.OutputData> createOrder(int bankAccountNr, Order.Type type, long volume, long price)
    {
        CreateOrderRequest.InputData inputData = new CreateOrderRequest.InputData(itemID, bankAccountNr, type, volume, price);
        CompletableFuture<CreateOrderRequest.OutputData> future = new CompletableFuture<>();
        BACKEND_INSTANCES.NETWORKING.CREATE_ORDER_REQUEST.sendRequestToServer(inputData, future::complete);
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
        CompletableFuture<ActiveOrdersRequest.OutputData> future = new CompletableFuture<>();
        BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_REQUEST.sendRequestToServer(inp, future::complete);
        return future;
    }



    @Override
    public String toString()
    {
        return "Market:" + itemID + " Price:" + priceHistoryData.getCurrentMarketPrice();
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[Market:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[Market:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[Market:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[Market:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[Market:"+itemID+"]: "+message);
    }
}
