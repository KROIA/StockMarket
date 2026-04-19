package net.kroia.stockmarket.stockmarket.marketmanager;

import com.ibm.icu.impl.units.MeasureUnitImpl;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerMarketManager implements ServerSaveable, IServerMarketManager
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        ServerMarket.setBackend(backend);
    }

    private static final boolean ENABLE_DEBUG_PERFORMANCE = false;


    private final Map<ItemID, ServerMarket> markets = new HashMap<>();
    private final long candleSaveTimer_intervalMs = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get();
    private long candleSaveTimer_lastMs = (System.currentTimeMillis()/60000)*60000;
    private final Random random = new Random();
    private final AtomicBoolean saveLock = new AtomicBoolean(false);
    private ItemID tradingCurrencyID = null;

    public ServerMarketManager()
    {

    }

    @Override
    public ItemID getTradingCurrencyID()
    {
        if(tradingCurrencyID == null)
        {
            tradingCurrencyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
        }
        return tradingCurrencyID;
    }
    @Override
    public CompletableFuture<ItemID> getTradingCurrencyIDAsync() {
        return CompletableFuture.completedFuture(getTradingCurrencyID());
    }





    @Override
    public List<ItemID> getAvailableMarketIDs()
    {
        List<ItemID> itemIDs = new ArrayList<>();
        for(ServerMarket m : markets.values())
        {
            itemIDs.add(m.getItemID());
        }
        return itemIDs;
    }
    @Override
    public CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync() {
        return CompletableFuture.completedFuture(getAvailableMarketIDs());
    }






    @Override
    public boolean marketExists(@NotNull ItemID marketID)
    {
        return markets.containsKey(marketID);
    }
    @Override
    public CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(marketExists(marketID));
    }







    @Override
    public @Nullable IServerMarket createMarket(@NotNull ItemID marketID)
    {
        ServerMarket m = markets.get(marketID);
        if (m == null)
        {
            m = new ServerMarket(marketID);
            markets.put(marketID, m);
        }
        return m;
    }
    @Override
    public CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(createMarket(marketID));
    }






    @Override
    public boolean deleteMarket(@NotNull ItemID marketID)
    {
        return markets.remove(marketID) != null;
    }
    @Override
    public CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(deleteMarket(marketID));
    }







    @Override
    public @Nullable IServerMarket getMarket(@NotNull ItemID marketID)
    {
        return markets.get(marketID);
    }
    @Override
    public CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(getMarket(marketID));
    }

















    private static double tmpValue = 100;
    @Override
    public void update()
    {
        for(ServerMarket m : markets.values())
        {
            // Create random movement for testing
            tmpValue += 10+Math.sin((double)System.currentTimeMillis()/10000.0);
            long rand = (random.nextLong()%10);
            if(tmpValue > 1) {
                rand += 1;
                tmpValue -= 1;
            }
            else if(tmpValue < 0) {
                rand -= 1;
                tmpValue += 1;
            }
            long currentPrice = m.getCurrentMarketPrice();

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
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
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


    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        ListTag marketListTag = new ListTag();
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
        {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            entry.getValue().save(marketTag);
            marketListTag.add(marketTag);
        }
        tag.put("markets", marketListTag);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;
        if(!tag.contains("markets"))
            return false;
        ListTag marketListTag = tag.getList("markets", Tag.TAG_COMPOUND);
        for (int i = 0; i < marketListTag.size(); i++)
        {
            CompoundTag marketTag = marketListTag.getCompound(i);
            ItemID id = ItemID.createFromTag(marketTag);
            if(id.isValid())
            {
                ServerMarket market = markets.get(id);
                if(market != null)
                    success = false;
                else
                {
                    market = new ServerMarket(id);
                    if(market.load(marketTag))
                    {
                        markets.put(id, market);
                    }
                    else
                        success = false;
                }
            }
            else
                success = false;
        }
        return success;
    }


    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerMarketManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarketManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarketManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerMarketManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerMarketManager]: "+message);
    }



}
