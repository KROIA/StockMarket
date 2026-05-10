package net.kroia.stockmarket.data;

import net.kroia.modutilities.persistence.DataPersistence;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataManager extends DataPersistence {

    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        StockMarketModSettings.setBackend(backend);
    }
    private static final boolean ENABLE_DEBUG_PERFORMANCE = false;


    public static final Path BASE_PATH = Path.of("data", "StockMarket");
    public static final Path SQL_DATABASE = BASE_PATH.resolve("Database");

    private static final String MARKET_MANAGER_FOLDER = "MarketManager";
    private static final String PLUGIN_MANAGER_FOLDER = "PluginManager";
    private static final String SETTINGS_FILE = "settings.json";


    private final AtomicBoolean candleDataSQL_saveLock = new AtomicBoolean(false);

    public DataManager() {
        super(JsonFormat.PRETTY, NbtFormat.COMPRESSED, BASE_PATH);
        setLogger(this::error, this::error, this::info, this::warn);


    }
    public boolean save(MinecraftServer server)
    {
        setLevelSavePath(server.getWorldPath(LevelResource.ROOT));
        createSaveFolder();

        boolean success = true;
        success &= saveSettings();
        success &= saveMarketManager();
        success &= savePluginManager();


        if(!success)
        {
            error("Failed to save data");
        }
        return success;
    }
    public boolean load(MinecraftServer server)
    {
        setLevelSavePath(server.getWorldPath(LevelResource.ROOT));

        boolean success = true;
        success &= loadSettings();
        success &= loadMarketManager();
        success &= loadPluginManager();



        if(!success)
        {
            error("Failed to load data");
        }
        return success;
    }


    private boolean saveMarketManager()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null) {
            error("saveMarketManager(): Backend is not set up to call this method.");
            return false;
        }
        Map<String, ListTag> dataListMap = new HashMap<>();
        ServerSaveableChunked marketManager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManagerPersistenceInterface();
        if(marketManager == null) {
            error("saveMarketManager(): No MarketManager found!");
            return false;
        }
        if(!marketManager.save(dataListMap))
        {
            error("saveMarketManager(): Failed to save MarketManager! Can't save Markets to NBT data");
            return false;
        }
        Path absPath = getAbsoluteSavePath(MARKET_MANAGER_FOLDER);
        if(!saveDataCompoundListMap(absPath, dataListMap))
        {
            error("saveMarketManager(): Failed to save Markets to NBT files");
            return false;
        }
        return true;
    }
    private boolean loadMarketManager()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null) {
            error("loadMarketManager(): Backend is not set up to call this method.");
            return false;
        }
        ServerSaveableChunked marketManager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManagerPersistenceInterface();
        if(marketManager == null) {
            error("loadMarketManager(): No MarketManager found!");
            return false;
        }
        Path absPath = getAbsoluteSavePath(MARKET_MANAGER_FOLDER);
        Map<String, ListTag> dataListMap = readDataCompoundListMap(absPath);
        return marketManager.load(dataListMap);
    }

    private boolean savePluginManager()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.PLUGIN_MANAGER == null) {
            error("savePluginManager(): Backend is not set up to call this method.");
            return false;
        }
        Map<String, ListTag> dataListMap = new HashMap<>();
        ServerSaveableChunked pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getServerPluginManagerPersistenceInterface();
        if(pluginManager == null) {
            error("savePluginManager(): No PluginManager found!");
            return false;
        }
        if(!pluginManager.save(dataListMap))
        {
            error("savePluginManager(): Failed to save PluginManager! Can't save Plugins to NBT data");
            return false;
        }
        Path absPath = getAbsoluteSavePath(PLUGIN_MANAGER_FOLDER);
        if(!saveDataCompoundListMap(absPath, dataListMap))
        {
            error("savePluginManager(): Failed to save Plugins to NBT files");
            return false;
        }
        return true;
    }
    private boolean loadPluginManager()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.PLUGIN_MANAGER == null) {
            error("loadPluginManager(): Backend is not set up to call this method.");
            return false;
        }
        ServerSaveableChunked pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getServerPluginManagerPersistenceInterface();
        if(pluginManager == null) {
            error("loadPluginManager(): No PluginManager found!");
            return false;
        }
        Path absPath = getAbsoluteSavePath(PLUGIN_MANAGER_FOLDER);
        Map<String, ListTag> dataListMap = readDataCompoundListMap(absPath);
        return pluginManager.load(dataListMap);
    }


    private boolean saveSettings()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null) {
            error("saveSettings(): Backend is not set up to call this method.");
            return false;
        }

        Path path = getAbsoluteSavePath(SETTINGS_FILE);
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.saveSettings(path.toAbsolutePath().toString()))
        {
            error("saveSettings(): Failed to save settings to Json file");
            return false;
        }
        return true;
    }
    private boolean loadSettings()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null) {
            error("loadSettings(): Backend is not set up to call this method.");
            return false;
        }

        Path path = getAbsoluteSavePath(SETTINGS_FILE);
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.loadSettings(path.toAbsolutePath().toString()))
        {
            error("loadSettings(): Failed to load settings from Json file");
            return false;
        }
        return true;
    }


    public CompletableFuture<Boolean> savePriceCandlesToSQL()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null) {
            error("savePriceCandlesToSQL(): Backend is not set up to call this method.");
            return CompletableFuture.completedFuture(false);
        }

        if(!candleDataSQL_saveLock.compareAndSet(false, true))
        {
            warn("savePriceCandlesToSQL(): currently locked!");
            return CompletableFuture.completedFuture(false);
        }
        IServerMarketManager marketManager = BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        if(marketManager == null) {
            error("savePriceCandlesToSQL(): No marketmanager found!");
            candleDataSQL_saveLock.set(false);
            return CompletableFuture.completedFuture(false);
        }

        MarketPriceManager priceHistoryManager = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER;
        long saveStartTime = System.nanoTime();
        List<MarketPriceStruct> candles = marketManager.getCurrentMarketPricesAndStartNewCandle();
        long gatheringCandlesTime = System.nanoTime() - saveStartTime;
        if(ENABLE_DEBUG_PERFORMANCE)
            info("savePriceCandlesToSQL(): Gathering time: "+gatheringCandlesTime/1000000.0 + "ms");
        long finalSaveStartTime = System.nanoTime();

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        priceHistoryManager.save(candles).whenComplete((unused, throwable) -> {
            if(throwable != null) {
                error("savePriceCandlesToSQL(): Async save failed", throwable);
                candleDataSQL_saveLock.set(false);
                result.complete(false);
                return;
            }
            if(ENABLE_DEBUG_PERFORMANCE) {
                long finalSaveEndTime = System.nanoTime();
                long writeTime = finalSaveEndTime - finalSaveStartTime;
                info("savePriceCandlesToSQL async(): Database write for " + candles.size() + " records took " + writeTime / 1000000.0 + " ms\n"+
                        "saveCandlesToSQL: took " + (double) (finalSaveEndTime - saveStartTime) / 1000000.0 + " ms");
            }
            candleDataSQL_saveLock.set(false);
            result.complete(true);
        });
        return result;
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[DataManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[DataManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[DataManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[DataManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[DataManager] " + msg);
    }
}
