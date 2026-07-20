package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.networking.stream.PluginPerformanceSnapshot;
import net.kroia.stockmarket.news.ServerNewsPublisher;
import net.kroia.stockmarket.pluginsystem.Plugins;
import net.kroia.stockmarket.pluginsystem.interaction.PluginOrderBook;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * ServerPluginManager runs on the master server and holds the instances of the plugins.
 * It updates the plugins
 *
 */
public class ServerPluginManager implements ServerSaveableChunked, IServerPluginManager{

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        ServerPlugin.setBackend(backend);
        PluginOrderBook.setBackend(backend);
    }

    private boolean loggerEnabled = false;
    private final Map<ItemID, MarketCache> marketCaches = new HashMap<>(); // Contains all caches, instance ownership belongs to this class
    @SuppressWarnings("rawtypes")
    private Map<UUID, ServerPlugin> plugins = new LinkedHashMap<>();    // Contains all plugin instances. UUID: plugin instanceID

    /**
     * Nanosecond timing storage for each plugin's two update-loop passes.
     * Populated by {@link #updatePlugins()} / {@link #finalizePlugins()} on every
     * server tick, consumed at the broadcast cadence by
     * {@link net.kroia.stockmarket.networking.stream.PluginPerformanceStream}
     * via {@link #buildPerformanceSnapshot()}. Master-only — never exists on
     * slave servers because the slave's plugin manager is never a
     * {@code ServerPluginManager}.
     */
    private final PluginPerformanceTracker performanceTracker = new PluginPerformanceTracker();

    /**
     * Sum of every plugin's update+finalize pass time (ns) inside the currently
     * running server tick. Reset to 0 at the top of {@link #update()},
     * accumulated as each plugin pass finishes, and copied into
     * {@link #lastTickPluginTotalNs} once the tick is done — so external
     * callers (the tick hook that measures total tick CPU work) can read a
     * stable value without racing with the accumulator. Master-only; single
     * thread — no synchronisation needed.
     */
    private long currentTickPluginNs = 0L;

    /**
     * Snapshot of {@link #currentTickPluginNs} at the end of the most recent
     * {@link #update()} call. Read by {@link #recordNonPluginTickNs(long)} to
     * subtract plugin time from the total tick work reported by the server
     * tick hook, yielding the non-plugin (vanilla) tick work sample that
     * feeds the tracker's rolling window.
     */
    private long lastTickPluginTotalNs = 0L;

    private enum State
    {
        NONE,
        EXEC_INIT,
        EXEC_DEINIT,
        EXEC_UPDATE,
        EXEC_FINALIZE,
    }
    private State state = State.NONE;



    /* ----------------------------------------------------------------------------------------------------------------
     *                     UPDATE LOOP
     * --------------------------------------------------------------------------------------------------------------*/
    @Override
    public void update()
    {
        // Reset the per-tick plugin-time accumulator BEFORE running any pass.
        // updatePlugins() and finalizePlugins() add to it as each plugin pass
        // finishes; the total is snapshotted into lastTickPluginTotalNs once
        // both passes are done, so the tick hook can later compute
        // (total tick work) − (plugin time) as the non-plugin tick sample.
        currentTickPluginNs = 0L;
        updatePlugins();
        finalizePlugins();
        lastTickPluginTotalNs = currentTickPluginNs;
    }
    private void updatePlugins()
    {
        state = State.EXEC_INIT;
        for(ServerPlugin plugin : plugins.values())
        {
            if(plugin.isEnabled())
            {
                // Per-plugin nanosecond timing of the normal update pass.
                // System.nanoTime() has negligible overhead on all supported
                // JVMs (≪ 100&nbsp;ns/call); the ring-buffer push is O(1).
                long t0 = System.nanoTime();
                plugin.update_internal();
                long deltaNs = System.nanoTime() - t0;
                performanceTracker.recordUpdate(plugin.getInstanceID(), deltaNs);
                // Reuse the same delta — do NOT call nanoTime twice — so the
                // per-tick plugin-time accumulator stays consistent with the
                // per-plugin ring the profiler UI reads (T-137).
                currentTickPluginNs += deltaNs;
            }
        }
        state = State.NONE;
    }
    private void finalizePlugins()
    {
        state = State.EXEC_FINALIZE;
        for(ServerPlugin plugin : plugins.values())
        {
            if(plugin.isEnabled())
            {
                // Per-plugin nanosecond timing of the finalisation pass — the
                // second half of the two-pass update loop. Timed separately
                // from the normal update pass so the profiler UI can show them
                // as two independent segments per plugin.
                long t0 = System.nanoTime();
                plugin.finalize_internal();
                long deltaNs = System.nanoTime() - t0;
                performanceTracker.recordFinalize(plugin.getInstanceID(), deltaNs);
                // Same delta reused for the per-tick accumulator (T-137).
                currentTickPluginNs += deltaNs;
            }
        }

        for(MarketCache marketCache : marketCaches.values())
        {
            marketCache.apply();
        }
        state = State.NONE;
    }

    /**
     * Returns the total plugin CPU time (update + finalize passes, summed
     * across every enabled plugin) recorded during the most recent
     * {@link #update()} call, in nanoseconds. Zero before the first tick.
     * <p>
     * Read by {@link net.kroia.stockmarket.StockMarketModBackend}'s tick hook
     * to derive the non-plugin tick-time sample.
     *
     * @return plugin CPU nanoseconds for the last completed tick
     */
    public long getLastTickPluginTotalNs() {
        return lastTickPluginTotalNs;
    }

    /**
     * Records one server tick's total non-plugin CPU work into the tracker's
     * rolling window.
     * <p>
     * The caller supplies the wall-clock nanoseconds between
     * {@code TickEvent.SERVER_PRE} and the end of {@code TickEvent.SERVER_POST}
     * for the same tick (this is CPU work per tick — NOT the wall-clock tick
     * period, which would include the vanilla inter-tick sleep). This method
     * subtracts the plugin time recorded by the most recent {@link #update()},
     * clamps to non-negative, and pushes the result to the tracker.
     * <p>
     * Consumed at broadcast cadence by {@link #buildPerformanceSnapshot()}
     * via {@link PluginPerformanceTracker#getNonPluginAvgNs()}.
     *
     * @param totalTickWorkNs total tick CPU work in nanoseconds
     *                        (SERVER_PRE → end of SERVER_POST for the same tick)
     */
    public void recordNonPluginTickNs(long totalTickWorkNs) {
        long nonPluginNs = Math.max(0L, totalTickWorkNs - lastTickPluginTotalNs);
        performanceTracker.recordNonPluginTickNs(nonPluginNs);
    }

    /**
     * Builds a fresh {@link PluginPerformanceSnapshot} for broadcast to trusted
     * admin clients. One row per plugin currently registered with this manager,
     * in the manager's iteration order (the same order plugins are executed in
     * every tick, so the client renders segments in execution order).
     * <p>
     * Rows include the plugin's display name, its enabled state, its subscribed
     * market count, and the latest/avg/peak nanoseconds for both the update and
     * finalize passes. Disabled plugins are included with a {@code false}
     * enabled flag and their last-known timings (or zero if they never ran),
     * so the client UI can choose whether to render them.
     *
     * @return a new snapshot instance — safe to hand off to the stream layer.
     */
    public PluginPerformanceSnapshot buildPerformanceSnapshot()
    {
        java.util.List<PluginPerformanceSnapshot.Entry> rows = new java.util.ArrayList<>(plugins.size());
        for (ServerPlugin plugin : plugins.values()) {
            UUID id = plugin.getInstanceID();
            rows.add(new PluginPerformanceSnapshot.Entry(
                    id,
                    plugin.getName() == null ? "" : plugin.getName(),
                    performanceTracker.getUpdateLatestNs(id),
                    performanceTracker.getUpdateAvgNs(id),
                    performanceTracker.getUpdatePeakNs(id),
                    performanceTracker.getFinalizeLatestNs(id),
                    performanceTracker.getFinalizeAvgNs(id),
                    performanceTracker.getFinalizePeakNs(id),
                    plugin.getSubscribedMarkets().size(),
                    plugin.isEnabled()
            ));
        }
        // Effective plugin budget (T-137): the fixed 50 ms tick budget minus a
        // rolling average of the non-plugin CPU work per tick, clamped to
        // [0, TICK_BUDGET_NS]. Computed once per snapshot (i.e. at broadcast
        // cadence, ~500 ms) rather than every tick — the tracker maintains the
        // rolling average continuously; this line just reads it.
        long avgNonPluginNs = performanceTracker.getNonPluginAvgNs();
        long effectiveBudgetNs = Math.max(0L, Math.min(
                PluginPerformanceSnapshot.TICK_BUDGET_NS,
                PluginPerformanceSnapshot.TICK_BUDGET_NS - avgNonPluginNs));
        return new PluginPerformanceSnapshot(
                PluginPerformanceSnapshot.TICK_BUDGET_NS,
                effectiveBudgetNs,
                rows);
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     MANAGEMENT
     * --------------------------------------------------------------------------------------------------------------*/

    public @Nullable MarketCache createCache(ItemID marketID)
    {
        if(marketCaches.containsKey(marketID))
        {
            return marketCaches.get(marketID);
        }
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot create market cache for trading pair " + marketID + " inside an update loop!");
            return null;
        }
        // Check if the trading pair is valid
        ServerMarket serverMarket = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager().getServerMarket(marketID);
        if(serverMarket == null)
        {
            error("Cannot create Market Cache for Trading Pair "+marketID + " since the market does not exist");
            return null;
        }
        MarketCache marketCache = new MarketCache(serverMarket);
        marketCaches.put(marketID, marketCache);
        return marketCache;
    }
    public MarketCache getCache(ItemID marketID)
    {
        return marketCaches.get(marketID);
    }
    public void removeCache(ItemID marketID)
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot remove market cache for trading pair " + marketID +  " inside an update loop!");
            return;
        }
        for(ServerPlugin plugin : plugins.values())
            plugin.unsubscribeFromMarket(marketID);
        marketCaches.remove(marketID);
        IServerMarket serverMarket = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getMarket(marketID);
        if(serverMarket != null)
        {
            serverMarket.test_setDefaultVolumeProviderFunction(null);
        }
    }
    public void clearCache()
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot clear the cache inside an update loop!");
            return;
        }
        List<ItemID> keys = new ArrayList<>(marketCaches.keySet());
        for(ItemID marketID : keys)
            removeCache(marketID);
    }


    /**
     * Auto-subscribes a newly created market to all plugins that opt in,
     * sorted by subscriptionOrder (0 = earliest, ties resolved by list order).
     *
     * @param marketID the ID of the newly created market
     */
    @Override
    public void autoSubscribeNewMarket(ItemID marketID)
    {
        List<ServerPlugin> sorted = new ArrayList<>(plugins.values());
        sorted.sort(Comparator.comparingInt(ServerPlugin::getSubscriptionOrder));
        for (ServerPlugin plugin : sorted)
        {
            if (plugin.getAutoSubscribeNewMarkets())
            {
                plugin.subscribeToMarket(marketID);
            }
        }
    }

    public ServerPlugin addPlugin(@NotNull PluginRegistryObject pluginRegistryObject)
    {
        ServerPlugin plugin = PluginRegistry.instantiateServerPlugin(pluginRegistryObject);
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot add a plugin inside an update loop!");
            return null;
        }
        plugin.setManager(this);
        plugins.put(plugin.getInstanceID(), plugin);
        plugin.init_internal();
        installProductionSeams(plugin);
        return plugin;
    }

    public void removePlugin(@NotNull ServerPlugin plugin)
    {
        if(plugin.getManager() != this)
            return; // Does not belong to this manager or is not in a manager

        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot remove a plugin inside an update loop!");
            return;
        }
        plugin.setEnabled(false);
        plugin.deInit_internal();
        plugins.remove(plugin.getInstanceID());
        // Drop timing state so a re-added plugin (or a UUID collision after
        // long uptime) never carries stale samples.
        performanceTracker.remove(plugin.getInstanceID());
        plugin.setManager(null);
    }


    public Map<UUID, ServerPlugin> getPlugins()
    {
        return plugins;
    }

    /**
     * Reorders a plugin in the execution order.
     * @param instanceID The UUID of the plugin to move
     * @param direction -1 = move up (earlier), +1 = move down (later)
     * @return true if reorder succeeded, false if already at boundary or plugin not found
     */
    public boolean reorderPlugin(UUID instanceID, int direction)
    {
        if(!plugins.containsKey(instanceID))
            return false;

        List<UUID> keys = new ArrayList<>(plugins.keySet());
        int index = keys.indexOf(instanceID);
        int newIndex = index + direction;
        if(newIndex < 0 || newIndex >= keys.size())
            return false;

        // Swap
        UUID temp = keys.get(newIndex);
        keys.set(newIndex, keys.get(index));
        keys.set(index, temp);

        // Rebuild the LinkedHashMap in new order
        Map<UUID, ServerPlugin> reordered = new LinkedHashMap<>();
        for(UUID key : keys)
            reordered.put(key, plugins.get(key));
        plugins = reordered;
        return true;
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     DATA HANDLING
     * --------------------------------------------------------------------------------------------------------------*/



    @Override
    public boolean save(Map<String, ListTag> listTags) {
        ListTag pluginsTag = new ListTag();
        for (ServerPlugin<?, ?> plugin : plugins.values()) {
            CompoundTag pluginTag = new CompoundTag();

            // Generic data (pluginTypeID, instanceID, name, description, enabled, loggerEnabled)
            plugin.getGenericPluginData().save(pluginTag);

            // Subscribed markets
            ListTag marketsTag = new ListTag();
            for (ItemID marketID : plugin.getSubscribedMarkets()) {
                CompoundTag marketTag = new CompoundTag();
                marketID.save(marketTag);
                marketsTag.add(marketTag);
            }
            pluginTag.put("subscribedMarkets", marketsTag);

            // Per-market custom settings
            Map<ItemID, byte[]> customSettingsMap = plugin.encodeAllCustomSettings();
            if (customSettingsMap != null && !customSettingsMap.isEmpty()) {
                ListTag settingsListTag = new ListTag();
                for (Map.Entry<ItemID, byte[]> entry : customSettingsMap.entrySet()) {
                    CompoundTag entryTag = new CompoundTag();
                    entry.getKey().save(entryTag);
                    entryTag.putByteArray("settingsData", entry.getValue());
                    settingsListTag.add(entryTag);
                }
                pluginTag.put("customSettingsMap", settingsListTag);
            }

            // Plugin-specific NBT data
            CompoundTag pluginDataTag = new CompoundTag();
            plugin.save(pluginDataTag);
            if (!pluginDataTag.isEmpty()) {
                pluginTag.put("pluginData", pluginDataTag);
            }

            pluginsTag.add(pluginTag);
        }
        listTags.put("plugins", pluginsTag);

        // Manager-level flags. "newsPluginAutoCreateDone" marks that this world has been
        // through the one-time NewsPlugin auto-create migration (or was created with the
        // plugin in loadDefaults). It is written unconditionally on every save, so once a
        // world has been saved by this version, the migration can never run again — an
        // admin who deliberately deletes the NewsPlugin will not get it re-created.
        ListTag managerDataTag = new ListTag();
        CompoundTag flagsTag = new CompoundTag();
        flagsTag.putBoolean("newsPluginAutoCreateDone", true);
        managerDataTag.add(flagsTag);
        listTags.put("managerData", managerDataTag);
        return true;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        ListTag pluginsTag = listTags.get("plugins");

        // First-run fallback: no save data exists, create default plugins
        if (pluginsTag == null || pluginsTag.isEmpty()) {
            return loadDefaults();
        }

        boolean success = true;
        for (int i = 0; i < pluginsTag.size(); i++) {
            CompoundTag pluginTag = pluginsTag.getCompound(i);

            // Read plugin type ID to find the registry entry
            if (!pluginTag.contains("pluginTypeID")) {
                warn("load(): Plugin at index " + i + " missing pluginTypeID, skipping");
                success = false;
                continue;
            }
            String pluginTypeID = pluginTag.getString("pluginTypeID");
            PluginRegistryObject registryObject = PluginRegistry.findPlugin(pluginTypeID);
            if (registryObject == null) {
                warn("load(): Unknown plugin type '" + pluginTypeID + "', skipping");
                success = false;
                continue;
            }

            // Read saved instanceID
            UUID savedInstanceID = pluginTag.contains("instanceID") ? pluginTag.getUUID("instanceID") : null;

            // Instantiate and register the plugin
            ServerPlugin plugin = addPluginFromSave(registryObject, savedInstanceID);
            if (plugin == null) {
                warn("load(): Failed to instantiate plugin '" + pluginTypeID + "'");
                success = false;
                continue;
            }

            // Restore generic data fields (name, description, loggerEnabled, auto-subscribe — NOT enabled yet)
            GenericPluginData genericData = plugin.getGenericPluginData();
            if (pluginTag.contains("name")) plugin.setName(pluginTag.getString("name"));
            if (pluginTag.contains("description")) plugin.setDescription(pluginTag.getString("description"));
            if (pluginTag.contains("loggerEnabled")) plugin.setLoggerEnabled(pluginTag.getBoolean("loggerEnabled"));
            if (pluginTag.contains("autoSubscribeNewMarkets")) plugin.setAutoSubscribeNewMarkets(pluginTag.getBoolean("autoSubscribeNewMarkets"));
            if (pluginTag.contains("subscriptionOrder")) plugin.setSubscriptionOrder(pluginTag.getInt("subscriptionOrder"));

            // Restore subscribed markets
            if (pluginTag.contains("subscribedMarkets")) {
                ListTag marketsTag = pluginTag.getList("subscribedMarkets", 10); // 10 = CompoundTag type
                for (int j = 0; j < marketsTag.size(); j++) {
                    CompoundTag marketTag = marketsTag.getCompound(j);
                    ItemID marketID = ItemID.createFromTag(marketTag);
                    if (marketID != null && marketID.isValid()) {
                        plugin.subscribeToMarket(marketID);
                    } else {
                        warn("load(): Invalid market at plugin '" + pluginTypeID + "' index " + j + ", skipping");
                    }
                }
            }

            // Restore per-market custom settings (with backwards compatibility for legacy single-settings format)
            if (pluginTag.contains("customSettingsMap")) {
                ListTag settingsListTag = pluginTag.getList("customSettingsMap", 10);
                for (int j = 0; j < settingsListTag.size(); j++) {
                    CompoundTag entryTag = settingsListTag.getCompound(j);
                    ItemID marketID = ItemID.createFromTag(entryTag);
                    if (marketID != null && marketID.isValid() && entryTag.contains("settingsData")) {
                        byte[] settingsBytes = entryTag.getByteArray("settingsData");
                        plugin.decodeAndApplyCustomSettings(marketID, settingsBytes);
                    }
                }
            } else if (pluginTag.contains("customSettings")) {
                // Legacy: single settings applied to all subscribed markets
                byte[] legacySettings = pluginTag.getByteArray("customSettings");
                plugin.decodeAndApplyCustomSettingsLegacy(legacySettings);
            }

            // Restore plugin-specific NBT data
            if (pluginTag.contains("pluginData")) {
                CompoundTag pluginDataTag = pluginTag.getCompound("pluginData");
                plugin.load(pluginDataTag);
            }

            // Set enabled state LAST (triggers onEnable which may need markets subscribed)
            if (pluginTag.contains("enabled")) {
                plugin.setEnabled(pluginTag.getBoolean("enabled"));
            }
        }

        // One-time NewsPlugin migration for worlds saved before the news system existed
        // (T-071, user decision: auto-create enabled). Only runs when the save carries no
        // "newsPluginAutoCreateDone" flag — i.e. the world was last saved by a pre-news
        // version. Every save() of this version writes the flag, so a deliberate deletion
        // of the plugin survives all future load cycles.
        if (!readNewsAutoCreateFlag(listTags)) {
            migrateCreateNewsPlugin();
        }
        return success;
    }

    /** Reads the manager-level "newsPluginAutoCreateDone" flag from the save data. */
    private static boolean readNewsAutoCreateFlag(Map<String, ListTag> listTags) {
        ListTag managerDataTag = listTags.get("managerData");
        if (managerDataTag == null || managerDataTag.isEmpty()) return false;
        CompoundTag flagsTag = managerDataTag.getCompound(0);
        return flagsTag.getBoolean("newsPluginAutoCreateDone");
    }

    /**
     * Creates the NewsPlugin instance on an existing (pre-news) world: enabled by default
     * and subscribed to all current markets, mirroring what auto-subscription would have
     * done had the plugin existed when the markets were created. Skipped if an instance
     * of the plugin type already exists (defensive — the flag should already prevent that).
     */
    private void migrateCreateNewsPlugin() {
        for (ServerPlugin plugin : plugins.values()) {
            if (plugin instanceof net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin) {
                return; // already present, nothing to migrate
            }
        }
        ServerPlugin newsPlugin = addPlugin(Plugins.NEWS_PLUGIN);
        if (newsPlugin == null) {
            warn("migrateCreateNewsPlugin(): failed to instantiate the NewsPlugin");
            return;
        }
        List<ItemID> marketIDs = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getAvailableMarketIDs();
        for (ItemID marketID : marketIDs) {
            newsPlugin.subscribeToMarket(marketID);
        }
        newsPlugin.setEnabled(true);
        info("Existing world without a NewsPlugin detected — created one (enabled, "
                + marketIDs.size() + " market(s) subscribed). Delete it via the plugin"
                + " management UI if unwanted; it will not be re-created.");
    }

    /**
     * Instantiates a plugin from save data, setting the instanceID before map insertion.
     */
    @Nullable ServerPlugin addPluginFromSave(@NotNull PluginRegistryObject registryObject, @Nullable UUID savedInstanceID) {
        ServerPlugin plugin = PluginRegistry.instantiateServerPlugin(registryObject);
        if (plugin == null) return null;
        if (savedInstanceID != null) {
            plugin.setInstanceID(savedInstanceID);
        }
        plugin.setManager(this);
        plugins.put(plugin.getInstanceID(), plugin);
        plugin.init_internal();
        installProductionSeams(plugin);
        return plugin;
    }

    /**
     * Wires master-only production seams into a freshly instantiated plugin. Runs for
     * every creation path (loadDefaults, save-load, NewsPlugin migration and runtime
     * creation via the plugin management UI), so no instance is ever left with a stub.
     * <p>
     * Currently: installs the production {@link ServerNewsPublisher} on NewsPlugin
     * instances (T-072 history append; T-073 adds the broadcast inside the publisher;
     * T-088 adds the publish-time picture snapshot into the DataManager's
     * published-picture store). The history and picture store live in the master's
     * DataManager; the history cap and the news library (definition/picture lookup)
     * are supplied lazily from the plugin. If the DataManager is not available
     * (unit-test contexts), the plugin keeps its default logging publisher.
     */
    private void installProductionSeams(@Nullable ServerPlugin plugin) {
        if (!(plugin instanceof NewsPlugin newsPlugin)) return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.DATA_MANAGER == null) return;
        // T-098: wire the registry supplier for requirement checks and chain logic.
        newsPlugin.setRegistrySupplier(
                () -> BACKEND_INSTANCES.DATA_MANAGER.getNewsWorldRegistry());
        newsPlugin.setPublisher(new ServerNewsPublisher(
                BACKEND_INSTANCES.DATA_MANAGER.getNewsHistory(),
                () -> newsPlugin.getLibrary().getSchedulerConfig().getHistoryMaxEntries(),
                BACKEND_INSTANCES.DATA_MANAGER.getNewsPictureStore(),
                newsPlugin::getLibrary,
                () -> BACKEND_INSTANCES.DATA_MANAGER.getNewsWorldRegistry()));
    }

    /**
     * Creates the default set of plugins for a fresh world (no save data).
     */
    private boolean loadDefaults() {
        List<ItemID> marketIDs = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getAvailableMarketIDs();

        ServerPlugin plugin1 = addPlugin(Plugins.VOLATILITY_PLUGIN);
        ServerPlugin plugin2 = addPlugin(Plugins.DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN);
        ServerPlugin plugin3 = addPlugin(Plugins.TARGET_PRICE_BOT_PLUGIN);
        ServerPlugin plugin4 = addPlugin(Plugins.NEWS_PLUGIN);

        if (!marketIDs.isEmpty()) {
            ItemID pair = marketIDs.getFirst();
            plugin1.subscribeToMarket(pair);
            plugin2.subscribeToMarket(pair);
            plugin3.subscribeToMarket(pair);
            plugin4.subscribeToMarket(pair);
        }

        plugin1.setEnabled(true);
        plugin2.setEnabled(true);
        plugin3.setEnabled(true);
        plugin4.setEnabled(true);
        return true;
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     INTERNAL  METHODS
     * --------------------------------------------------------------------------------------------------------------*/




    protected final void info(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.info(getLogPrefix() + msg);
    }
    protected final void error(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg);
    }
    protected final void error(String msg, Throwable e)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg, e);
    }
    protected final void warn(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.warn(getLogPrefix() + msg);
    }
    protected final void debug(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.debug(getLogPrefix() + msg);
    }
    private String getLogPrefix() {
        return "[PluginManager] ";
    }
}
