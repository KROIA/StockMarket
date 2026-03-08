package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MarketManager
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        Market.setBackend(backend);
    }


    private final Map<ItemID, Market> markets = new HashMap<>();
    private long candleSaveTimer_intervalMs = 100;
    private long candleSaveTimer_lastMs = System.currentTimeMillis();
    private final Random random = new Random();

    public MarketManager()
    {

    }


    public Market createMarket(@NotNull ItemID itemID)
    {
        Market m = markets.get(itemID);
        if (m == null)
        {
            m = new Market(itemID);
            markets.put(itemID, m);
        }
        return m;
    }
    public @Nullable Market getMarket(@NotNull ItemID itemID)
    {
        return markets.get(itemID);
    }


    public void update()
    {
        for(Market m : markets.values())
        {
            // Create random movement for testing
            long currentPrice = m.getCurrentMarketPrice();
            long rand = (random.nextLong()%10)-5;
            m.test_setCurrentMarketPrice(currentPrice + rand);
            m.update();
        }

        long time = System.currentTimeMillis();
        if(time - candleSaveTimer_lastMs > candleSaveTimer_intervalMs)
        {
            candleSaveTimer_lastMs = time;
            saveCandlesToSQL();
        }
    }


    private void saveCandlesToSQL()
    {
        MarketPriceManager manager = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER;
        long saveStartTime = System.nanoTime();
        List<MarketPriceStruct> candles = new ArrayList<>();
        for(Map.Entry<ItemID, Market> entry : markets.entrySet())
        {
            candles.add(entry.getValue().getCurrentMarketPriceStructAndReset());
        }
        long gatheringCandlesTime = saveStartTime - System.nanoTime();
        long finalSaveStartTime = System.nanoTime();

        manager.save(candles).thenRun(() -> {
            long finalSaveEndTime = System.nanoTime();
            long writeTime = finalSaveEndTime - finalSaveStartTime;
            info("Database write for " + candles.size() + " records took " + writeTime + " ns");
            info("MarketManager::saveCandlesToSQL: took " + (finalSaveEndTime - saveStartTime) + " ns");
        });
    }





    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[MarketManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[MarketManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[MarketManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[MarketManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[MarketManager]: "+message);
    }
}
