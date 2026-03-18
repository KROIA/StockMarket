package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MarketManager
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        Market.setBackend(backend);
    }

    private static final boolean ENABLE_DEBUG_PERFORMANCE = false;


    private final Map<ItemID, Market> markets = new HashMap<>();
    private long candleSaveTimer_intervalMs = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get();
    private long candleSaveTimer_lastMs = System.currentTimeMillis();
    private final Random random = new Random();
    private AtomicBoolean saveLock = new AtomicBoolean(false);
    private ItemID tradingCurrencyID = null;

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
            long rand = (random.nextLong()%10);
            currentPrice = Math.max(0, currentPrice + rand);
            m.test_setCurrentMarketPrice(currentPrice);
            m.update();
        }

        long time = System.currentTimeMillis();
        if(time - candleSaveTimer_lastMs > candleSaveTimer_intervalMs)
        {
            candleSaveTimer_lastMs = time;
            saveCandlesToSQL();
        }
    }

    public ItemID getTradingCurrencyID()
    {
        if(tradingCurrencyID == null)
        {
            tradingCurrencyID = ItemID.getOrRegisterFromItemStack(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
        }
        return tradingCurrencyID;
    }
    public List<ItemID> getAvailableMarketIDs()
    {
        List<ItemID> itemIDs = new ArrayList<>();
        for(Market m : markets.values())
        {
            itemIDs.add(m.getItemID());
        }
        return itemIDs;
    }

    private void saveCandlesToSQL()
    {
        if(!saveLock.compareAndSet(false, true))
        {
            warn("saveCandlesToSQL(): currently locked!");
            return;
        }
        MarketPriceManager manager = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER;
        long saveStartTime = System.nanoTime();
        List<MarketPriceStruct> candles = new ArrayList<>();
        for(Map.Entry<ItemID, Market> entry : markets.entrySet())
        {
            candles.add(entry.getValue().getCurrentMarketPriceStructAndReset());
        }
        long gatheringCandlesTime = System.nanoTime() -saveStartTime;
        if(ENABLE_DEBUG_PERFORMANCE)
            info("Gathering time: "+gatheringCandlesTime/1000000.0 + "ms");
        long finalSaveStartTime = System.nanoTime();

        manager.save(candles).thenRun(() -> {
            if(ENABLE_DEBUG_PERFORMANCE) {
                long finalSaveEndTime = System.nanoTime();
                long writeTime = finalSaveEndTime - finalSaveStartTime;
                info("Database write for " + candles.size() + " records took " + writeTime / 1000000.0 + " ms");
                info("saveCandlesToSQL: took " + (double) (finalSaveEndTime - saveStartTime) / 1000000.0 + " ms");
            }
            saveLock.set(false);
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
