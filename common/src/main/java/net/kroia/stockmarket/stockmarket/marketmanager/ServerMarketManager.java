package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerMarketManager implements ServerSaveableChunked, IServerMarketManager
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        ServerMarket.setBackend(backend);
    }


    /**
     * Using the player UUID as key
     */
    private final Map<UUID, User> userMap = new HashMap<>();

    private final Map<ItemID, ServerMarket> markets = new HashMap<>();

    private long candleSaveTimer_lastMs = (System.currentTimeMillis()/60000)*60000;
    private final Random random = new Random();
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
            MarketPreset preset = BACKEND_INSTANCES.PRESET_MANAGER != null
                    ? BACKEND_INSTANCES.PRESET_MANAGER.getPreset(marketID) : null;
            long defaultPrice = (preset != null) ? (long) preset.defaultPrice() : 1000;
            float abundance = (preset != null) ? preset.naturalAbundance() : 10f;
            m = new ServerMarket(marketID, null, defaultPrice, abundance);
            markets.put(marketID, m);

            // Auto-subscribe new market to plugins that opt in
            if (BACKEND_INSTANCES.PLUGIN_MANAGER != null) {
                var pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getSync();
                if (pluginManager != null) {
                    pluginManager.autoSubscribeNewMarket(marketID);
                }
            }
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





    @Override
    public void onPlayerJoin(UUID playerUUID, String playerName)
    {
        User user = userMap.get(playerUUID);
        if(user == null)
        {
            user = new User(playerUUID, playerName);
            userMap.put(playerUUID, user);
            return;
        }
        if(!user.getName().equals(playerName))
        {
            // Use createWithChangedName to preserve admin status and preferences
            user = User.createWithChangedName(user, playerName);
            userMap.put(playerUUID, user);
        }
    }
    @Override
    public void onPlayerJoinAsync(UUID playerUUID, String playerName)
    {
        onPlayerJoin(playerUUID, playerName);
    }




    @Override
    public @Nullable UUID getPlayerUUID(String playerName)
    {
        for(User user : userMap.values())
        {
            if(user.getName().equals(playerName))
            {
                return user.getUUID();
            }
        }
        return null;
    }





    @Override
    public boolean setStockmarketAdminMode(UUID playerUUID, boolean isAdmin) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        user.setStockMarketAdmin(isAdmin);
        return true;
    }
    @Override
    public CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin) {
        return CompletableFuture.completedFuture(setStockmarketAdminMode(playerUUID, isAdmin));
    }





    @Override
    public boolean isStockmarketAdmin(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        return user.isStockMarketAdmin();
    }
    @Override
    public CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return CompletableFuture.completedFuture(false);
        return CompletableFuture.completedFuture(user.isStockMarketAdmin());
    }



    @Override
    public @NotNull PlayerPreferences getPlayerPreferences(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return new PlayerPreferences();
        return user.getPreferences();
    }

    @Override
    public CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID) {
        return CompletableFuture.completedFuture(getPlayerPreferences(playerUUID));
    }

    @Override
    public boolean updatePlayerPreferences(UUID playerUUID, PlayerPreferences preferences) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        user.setPreferences(preferences);
        return true;
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences) {
        return CompletableFuture.completedFuture(updatePlayerPreferences(playerUUID, preferences));
    }




    public ServerMarket getServerMarket(ItemID marketID)
    {
        return markets.get(marketID);
    }










    @Override
    public List<MarketPriceStruct>  getCurrentMarketPricesAndStartNewCandle()
    {
        List<MarketPriceStruct> candles = new ArrayList<>();
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
        {
            candles.add(entry.getValue().getCurrentMarketPriceStructAndReset());
        }
        return candles;
    }



    private static double tmpValue = 100;
    @Override
    public void update()
    {
        for(ServerMarket m : markets.values())
        {
            // Create random movement for testing
            /*tmpValue += Math.sin((double)System.currentTimeMillis()/10000.0);
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
            m.test_resetVirtualOrderBookVolume();*/
            m.update();
        }

        long time = System.currentTimeMillis();
        long candleSaveIntervalMs = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get();
        if(time - candleSaveTimer_lastMs > candleSaveIntervalMs)
        {
            candleSaveTimer_lastMs = time;
            BACKEND_INSTANCES.DATA_MANAGER.savePriceCandlesToSQL();
        }
    }





    @Override
    public boolean save(Map<String, ListTag> listTags) {
        boolean success = true;
        ListTag marketListTag = new ListTag();
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
        {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            entry.getValue().save(marketTag);
            marketListTag.add(marketTag);
        }
        listTags.put("markets", marketListTag);

        ListTag userListTag = new ListTag();
        for(User user : userMap.values())
        {
            CompoundTag userTag = new CompoundTag();
            if(user.save(userTag))
                userListTag.add(userTag);
            else
                success = false;
        }
        listTags.put("users", userListTag);
        return success;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        boolean success = true;
        ListTag marketListTag =  listTags.get("markets");
        if(marketListTag == null)
        {
            error("markets list tag is null while loading Market Manager");
            return false;
        }

        Map<ItemID, ServerMarket> newMarkets = new HashMap<>();

        for (int i = 0; i < marketListTag.size(); i++)
        {
            CompoundTag marketTag = marketListTag.getCompound(i);
            ItemID id = ItemID.createFromTag(marketTag);
            if(id.isValid())
            {
                ServerMarket market = new ServerMarket(id);
                if(market.load(marketTag))
                {
                    newMarkets.put(id, market);
                }
                else {
                    error("load(): Failed to load market for item: " + id);
                    success = false;
                }
            }
            else {
                error("load(): Invalid ItemID at index " + i + " while loading markets");
                success = false;
            }
        }
        markets.clear();
        markets.putAll(newMarkets);

        ListTag userListTag = listTags.get("users");
        if(userListTag != null)
        {
            userMap.clear();
            for(int i = 0; i < userListTag.size(); i++)
            {
                CompoundTag userTag = userListTag.getCompound(i);
                User user = User.createFromTag(userTag);
                if(user != null)
                    userMap.put(user.getUUID(), user);
                else
                    success = false;
            }
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
