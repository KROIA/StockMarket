package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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

            // Trying to sync using the server time to get the same candles like the server does
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
        StreamSystem.startServerToClientStream(BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST, priceChunkRequestData, (historyData) ->
        {
            info("Price chunck received");
            //lastCandleCreationTime = System.currentTimeMillis();
            priceHistoryData.loadFrom(historyData);
            PriceHistoryData.Candle lastCandle = priceHistoryData.getCurrentCandle();
            if(lastCandle != null) {
                lastCandleCreationTime = lastCandle.openTimestamp + newCandleInterval;
                currentServerTime = lastCandle.openTimestamp;
            }
        }, () ->
        {
            // Stream stopped
            info("MARKET_PRICE_HISTORY_REQUEST stopped");
        });
    }
    public boolean subscribeToMarketPriceUpdate()
    {
        if(marketPriceUpdateStreamID != null)
            return false;

        //if(priceHistoryData.getCandles().isEmpty())
        {
            // Request historical candle data first
            requestFullPriceHistoryUpdate(0, Long.MAX_VALUE);
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
