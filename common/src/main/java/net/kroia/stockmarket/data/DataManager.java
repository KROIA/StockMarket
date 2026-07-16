package net.kroia.stockmarket.data;

import dev.architectury.platform.Platform;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.persistence.DataPersistence;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsPictureStore;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final boolean ENABLE_DEBUG_PERFORMANCE = StockMarketMod.ENABLE_DEV_FEATURES;


    public static final Path BASE_PATH = Path.of("data", "StockMarket");
    public static final Path SQL_DATABASE = BASE_PATH.resolve("Database");

    private static final String MARKET_MANAGER_FOLDER = "MarketManager";
    private static final String PLUGIN_MANAGER_FOLDER = "PluginManager";
    private static final String NEWS_FOLDER = "News";
    private static final String NEWS_HISTORY_FILE = "history.nbt";
    private static final String NEWS_REGISTRY_FILE = "registry.nbt";
    private static final String NEWS_PICTURES_FOLDER = "pictures";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String STARTER_KIT_FILE = "starterkit_claims.json";
    private static final String PRESET_DIR = "StockMarket/market_presets";

    public static Path getPresetPath() {
        return Platform.getConfigFolder().resolve(PRESET_DIR);
    }


    private StarterKitData starterKitData = new StarterKitData();

    /**
     * Master-side news history (T-072). The DataManager only exists on the master
     * (see StockMarketModBackend), so the history is master-only state by construction.
     * Appended to by the {@link net.kroia.stockmarket.news.ServerNewsPublisher} that
     * ServerPluginManager installs on every NewsPlugin instance.
     */
    private final NewsHistory newsHistory = new NewsHistory();

    /**
     * Master-side published-picture store (T-088, picture plan §2 layer 2), living
     * beside the news history: content-addressed snapshots of every published record's
     * picture under {@code world/data/StockMarket/News/pictures/<sha1hex>.png}.
     * Master-only by construction (same argument as {@link #newsHistory}); its
     * directory is wired in {@link #saveNews()}/{@link #loadNews()} once the world
     * save path is known, and it is garbage-collected against the history's
     * referenced hashes after every load (the {@code ServerNewsPublisher} GCs it
     * after every publish/prune).
     */
    private final NewsPictureStore newsPictureStore = new NewsPictureStore();

    /**
     * Master-side news world-event registry (T-096, sequences plan §3), living beside
     * {@link #newsHistory}/{@link #newsPictureStore}: per-event fire records (auto-recorded
     * on publish, T-098) plus the capped custom key/value store written by event
     * {@code "records"} declarations. Master-only by construction (same argument as
     * {@link #newsHistory}); persisted to {@code world/data/StockMarket/News/registry.nbt}
     * in {@link #saveNews()}/{@link #loadNews()}. Read by the T-097 requirement
     * predicates and the T-099 registry admin ops.
     */
    private final NewsWorldRegistry newsWorldRegistry = new NewsWorldRegistry();
    private final AtomicBoolean candleDataSQL_saveLock = new AtomicBoolean(false);

    public DataManager() {
        super(JsonFormat.PRETTY, NbtFormat.COMPRESSED, BASE_PATH);
        setLogger(this::error, this::error, this::info, this::warn);
    }

    public StarterKitData getStarterKitData() {
        return starterKitData;
    }

    /** @return the master-side news history store (see {@link #newsHistory}) */
    public NewsHistory getNewsHistory() {
        return newsHistory;
    }

    /**
     * @return the master-side published-picture store (see {@link #newsPictureStore});
     *         serves the picture bytes for every hash carried by a history record
     *         (used by the {@code ServerNewsPublisher} snapshot and the T-089
     *         {@code NewsPictureRequest} handler)
     */
    public NewsPictureStore getNewsPictureStore() {
        return newsPictureStore;
    }

    /**
     * @return the master-side news world-event registry (see {@link #newsWorldRegistry});
     *         written by the {@code ServerNewsPublisher} fire hook (T-098), read by the
     *         news requirement predicates (T-097) and the registry admin ops (T-099)
     */
    public NewsWorldRegistry getNewsWorldRegistry() {
        return newsWorldRegistry;
    }
    public boolean save(MinecraftServer server)
    {
        setLevelSavePath(server.getWorldPath(LevelResource.ROOT));
        createSaveFolder();

        boolean success = true;
        success &= saveSettings();
        success &= saveMarketManager();
        success &= savePluginManager();
        success &= savePresets();
        success &= saveStarterKit();
        success &= saveNews();

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
        success &= loadPresets();
        success &= loadStarterKit();
        success &= loadNews();

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


    /**
     * Saves ONLY the settings.json file (no market/plugin/preset NBT data).
     * <p>
     * Used by {@code ModSettingsRequest} to persist in-game settings edits:
     * a targeted settings-only write avoids the (potentially large) full
     * market/plugin save on every "Apply" click. The periodic autosave and
     * server-stop save still perform the full {@link #save(MinecraftServer)}.
     *
     * @param server the running server (used to resolve the world save path)
     * @return true if settings.json was written successfully
     */
    public boolean saveSettingsToFile(MinecraftServer server)
    {
        setLevelSavePath(server.getWorldPath(LevelResource.ROOT));
        createSaveFolder();
        return saveSettings();
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


    public boolean savePresets()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.PRESET_MANAGER == null) {
            error("savePresets(): Backend is not set up to call this method.");
            return false;
        }
        BACKEND_INSTANCES.PRESET_MANAGER.saveAll(getPresetPath());
        return true;
    }
    private boolean loadPresets()
    {
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.PRESET_MANAGER == null) {
            error("loadPresets(): Backend is not set up to call this method.");
            return false;
        }
        net.minecraft.server.MinecraftServer server = UtilitiesPlatform.getServer();
        BACKEND_INSTANCES.PRESET_MANAGER.loadOrGenerate(getPresetPath(), server != null ? server.registryAccess() : null);
        return true;
    }

    // Starter kit claim persistence
    public boolean saveStarterKit()
    {
        Path path = getAbsoluteSavePath(STARTER_KIT_FILE);
        if(!saveAsJson(starterKitData.getClaimedPlayerUUIDs(), path))
        {
            error("saveStarterKit(): Failed to save starter kit claims to Json file");
            return false;
        }
        return true;
    }
    private boolean loadStarterKit()
    {
        Path path = getAbsoluteSavePath(STARTER_KIT_FILE);
        if(!Files.exists(path))
        {
            // First run — no claims file yet, keep the empty set
            return true;
        }
        HashSet<String> claimed = loadFromJson(path, new TypeToken<HashSet<String>>(){}.getType());
        if(claimed != null)
        {
            starterKitData.setClaimedPlayerUUIDs(claimed);
        }
        else
        {
            error("loadStarterKit(): Failed to load starter kit claims from Json file");
            return false;
        }
        return true;
    }

    /**
     * News persistence (T-072/T-096, pattern: {@link #saveStarterKit}).
     * Writes the whole capped history buffer to
     * {@code world/data/StockMarket/News/history.nbt} and the world-event registry to
     * {@code world/data/StockMarket/News/registry.nbt}.
     * Master-only by construction — the DataManager is only created on the master.
     * Both files are always attempted — a failing history write does not skip the
     * registry write (and vice versa).
     *
     * @return true if both news files were written successfully
     */
    public boolean saveNews()
    {
        Path folderPath = getAbsoluteSavePath(NEWS_FOLDER);
        if(!createFolder(folderPath))
        {
            error("saveNews(): Failed to create news save folder");
            return false;
        }
        // Keep the picture store pointed at the current world save (idempotent; the
        // store persists its snapshots immediately on put, so saving the history is
        // all this method has left to do for pictures).
        newsPictureStore.setDirectory(folderPath.resolve(NEWS_PICTURES_FOLDER));
        boolean success = true;
        CompoundTag tag = new CompoundTag();
        newsHistory.save(tag);
        if(!saveDataCompound(folderPath.resolve(NEWS_HISTORY_FILE), tag))
        {
            error("saveNews(): Failed to save news history to NBT file");
            success = false;
        }
        CompoundTag registryTag = new CompoundTag();
        newsWorldRegistry.save(registryTag);
        if(!saveDataCompound(folderPath.resolve(NEWS_REGISTRY_FILE), registryTag))
        {
            error("saveNews(): Failed to save news world registry to NBT file");
            success = false;
        }
        return success;
    }

    /**
     * Restores the news history from {@code world/data/StockMarket/News/history.nbt}.
     * A missing file is not an error (first run / pre-news world) — the empty history
     * is kept. The entry cap is applied lazily on the next append (see
     * {@link NewsHistory#setMaxEntries}).
     *
     * @return true if the history was restored (or no file existed yet)
     */
    private boolean loadNews()
    {
        // Wire the picture store to this world's save before anything can publish
        // (T-088): publishes snapshot picture bytes into this directory.
        Path newsFolder = getAbsoluteSavePath(NEWS_FOLDER);
        newsPictureStore.setDirectory(newsFolder.resolve(NEWS_PICTURES_FOLDER));

        // Restore the world-event registry first (T-096). Deliberately failure-tolerant:
        // a missing or corrupt registry file never blocks the news load — the registry
        // simply starts empty (requirements re-evaluate against a blank memory).
        loadNewsRegistry(newsFolder);

        Path path = newsFolder.resolve(NEWS_HISTORY_FILE);
        if(!Files.exists(path))
        {
            // First run — no news published yet, keep the empty history. Any leftover
            // pictures (e.g. the history file was deleted by hand) are unreferenced.
            newsPictureStore.retainOnly(newsHistory.referencedPictureHashes());
            return true;
        }
        CompoundTag tag = readDataCompound(path);
        if(tag == null)
        {
            // Do NOT GC the picture store here: the unreadable history may still
            // reference stored pictures and might be recoverable.
            error("loadNews(): Failed to load news history from NBT file");
            return false;
        }
        boolean success = newsHistory.load(tag);
        // GC after the load: drop every stored picture no history record references
        // anymore (records pruned/removed while the store was offline).
        newsPictureStore.retainOnly(newsHistory.referencedPictureHashes());
        return success;
    }

    /**
     * Restores the news world-event registry from
     * {@code world/data/StockMarket/News/registry.nbt} (T-096). Failure-tolerant by
     * design: a missing file (first run / pre-registry world) or an unreadable file
     * only logs — the registry is left empty and the surrounding news load continues
     * unaffected, which is why this method returns nothing.
     *
     * @param newsFolder the resolved {@code News} world-data folder
     */
    private void loadNewsRegistry(Path newsFolder)
    {
        // Start from a clean slate so a re-load never keeps stale entries around.
        newsWorldRegistry.clearAll();
        Path path = newsFolder.resolve(NEWS_REGISTRY_FILE);
        if(!Files.exists(path))
        {
            // First run — nothing recorded yet, keep the empty registry.
            return;
        }
        CompoundTag tag = readDataCompound(path);
        if(tag == null)
        {
            error("loadNewsRegistry(): Failed to read news world registry from NBT file — continuing with an empty registry");
            return;
        }
        newsWorldRegistry.load(tag);
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
            debug("savePriceCandlesToSQL(): Gathering time: "+gatheringCandlesTime/1000000.0 + "ms");
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
                debug("savePriceCandlesToSQL async(): Database write for " + candles.size() + " records took " + writeTime / 1000000.0 + " ms\n"+
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
