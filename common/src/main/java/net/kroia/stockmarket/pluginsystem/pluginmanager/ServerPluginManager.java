package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.pluginsystem.Plugins;
import net.kroia.stockmarket.pluginsystem.interaction.PluginOrderBook;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
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
    private final Map<UUID, ServerPlugin> plugins = new HashMap<>();    // Contains all plugin instances

    private enum State
    {
        NONE,
        EXEC_INIT,
        EXEC_DEINIT,
        EXEC_UPDATE,
        EXEC_FINALIZE,
        //EXEC_APPLY,
        EXEC_SAVE,
        EXEC_LOAD,
        EXEC_ENCODING,
        EXEC_DECODING,
    }
    private State state = State.NONE;

    @Override
    public void update()
    {
        updatePlugins();
        finalizePlugis();
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     UPDATE LOOP
     * --------------------------------------------------------------------------------------------------------------*/
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
    private void finalizePlugis()
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


    public void addPlugin(@NotNull ServerPlugin plugin)
    {
        if(plugin.getManager() == this)
            return;
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot add a plugin inside an update loop!");
            return;
        }
        plugin.setManager(this);
        plugins.put(plugin.getInstanceID(), plugin);
        plugin.init_internal();
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

    /* ----------------------------------------------------------------------------------------------------------------
     *                     DATA HANDLING
     * --------------------------------------------------------------------------------------------------------------*/



    @Override
    public boolean save(Map<String, ListTag> listTags) {
        return false;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        List<ItemID> marketIDs =  BACKEND_INSTANCES.MARKET_MANAGER.getSync().getAvailableMarketIDs();


        ServerPlugin plugin1 = PluginRegistry.instantiateServerPlugin(Plugins.VOLATILITY_PLUGIN);
        ServerPlugin plugin2 = PluginRegistry.instantiateServerPlugin(Plugins.DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN);
        ServerPlugin plugin3 = PluginRegistry.instantiateServerPlugin(Plugins.TARGET_PRICE_BOT_PLUGIN);

        addPlugin(plugin1);
        addPlugin(plugin2);
        addPlugin(plugin3);

        if(!marketIDs.isEmpty())
        {
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
