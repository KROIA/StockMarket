package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.pluginsystem.Plugins;
import net.kroia.stockmarket.pluginsystem.interaction.PluginOrderBook;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
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
        updatePlugins();
        finalizePlugins();
    }
    private void updatePlugins()
    {
        state = State.EXEC_INIT;
        for(ServerPlugin plugin : plugins.values())
        {
            if(plugin.isEnabled())
            {
                plugin.update_internal();
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
                plugin.finalize_internal();
            }
        }

        for(MarketCache marketCache : marketCaches.values())
        {
            marketCache.apply();
        }
        state = State.NONE;
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
            if (plugin.getAutoSubscribeNewMarkets() && plugin.isEnabled())
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
        return success;
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
        return plugin;
    }

    /**
     * Creates the default set of plugins for a fresh world (no save data).
     */
    private boolean loadDefaults() {
        List<ItemID> marketIDs = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getAvailableMarketIDs();

        ServerPlugin plugin1 = addPlugin(Plugins.VOLATILITY_PLUGIN);
        ServerPlugin plugin2 = addPlugin(Plugins.DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN);
        ServerPlugin plugin3 = addPlugin(Plugins.TARGET_PRICE_BOT_PLUGIN);

        if (!marketIDs.isEmpty()) {
            ItemID pair = marketIDs.getFirst();
            plugin1.subscribeToMarket(pair);
            plugin2.subscribeToMarket(pair);
            plugin3.subscribeToMarket(pair);
        }

        plugin1.setEnabled(true);
        plugin2.setEnabled(true);
        plugin3.setEnabled(true);
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
