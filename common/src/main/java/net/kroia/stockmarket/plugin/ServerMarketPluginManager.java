package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairListData;
import net.kroia.stockmarket.plugin.base.MarketBehaviorPlugin;
import net.kroia.stockmarket.plugin.base.Plugin;
import net.kroia.stockmarket.plugin.base.cache.MarketCache;
import net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.plugin.plugins.TargetPriceBot;
import net.kroia.stockmarket.plugin.plugins.VolatilityPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

public class ServerMarketPluginManager
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        Plugin.setBackend(backend);
    }

    private boolean loggerEnabled = false;
    private final Path saveFolder;
    private final Map<TradingPair, MarketCache> marketCaches = new HashMap<>(); // Contains all caches, instance ownership belongs to this class
    private final Map<UUID, MarketBehaviorPlugin> plugins = new HashMap<>();    // Contains all plugin instances

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

    public ServerMarketPluginManager(Path saveFolder)
    {
        this.saveFolder = saveFolder;
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     UPDATE LOOP
     * --------------------------------------------------------------------------------------------------------------*/
    public void updatePlugins()
    {
        state = State.EXEC_INIT;
        for(MarketBehaviorPlugin plugin : plugins.values())
        {
            if(plugin.isEnabled())
            {
                plugin.update_internal();
            }
        }
        state = State.NONE;
    }
    public void finalizePlugis()
    {
        state = State.EXEC_FINALIZE;
        for(MarketBehaviorPlugin plugin : plugins.values())
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

    public @Nullable MarketCache createCache(TradingPair tradingPair)
    {
        if(marketCaches.containsKey(tradingPair))
        {
            return marketCaches.get(tradingPair);
        }
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot create market cache for trading pair " + tradingPair + " inside an update loop!");
            return null;
        }
        // Check if the trading pair is valid
        IServerMarket serverMarket = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(tradingPair);
        if(serverMarket == null)
        {
            error("Cannot create Market Cache for Trading Pair "+tradingPair + " since the market does not exist");
            return null;
        }
        MarketCache marketCache = new MarketCache(serverMarket);
        marketCaches.put(tradingPair, marketCache);
        return marketCache;
    }
    public MarketCache getCache(TradingPair tradingPair)
    {
        return marketCaches.get(tradingPair);
    }
    public void removeCache(TradingPair tradingPair)
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Cannot remove market cache for trading pair " + tradingPair +  " inside an update loop!");
            return;
        }
        for(MarketBehaviorPlugin plugin : plugins.values())
            plugin.unsubscribeFromMarket(tradingPair);
        marketCaches.remove(tradingPair);
        IServerMarket serverMarket = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(tradingPair);
        if(serverMarket != null)
        {
            serverMarket.getOrderBook().setDefaultVirtualVolumeDistributionFunction(null);
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
        List<TradingPair> keys = new ArrayList<>(marketCaches.keySet());
        for(TradingPair tradingPair : keys)
            removeCache(tradingPair);
    }


    public void addPlugin(@NotNull MarketBehaviorPlugin plugin)
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
        plugins.put(plugin.getID(), plugin);
        plugin.init();
    }

    public void removePlugin(@NotNull MarketBehaviorPlugin plugin)
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
        plugin.deInit();
        plugins.remove(plugin.getID());
        plugin.setManager(null);
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     DATA HANDLING
     * --------------------------------------------------------------------------------------------------------------*/

    public boolean save()
    {
        boolean success = true;
        return success;
    }

    public boolean load()
    {
        boolean success = true;
        // Dummy plugin creation
        TradingPairListData tradingPairs =  BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getTradingPairListData();

        DefaultOrderbookVolumeDistributionPlugin orderBookVolumePlugin = new DefaultOrderbookVolumeDistributionPlugin();
        TargetPriceBot targetPriceBot = new TargetPriceBot();
        VolatilityPlugin volatilityPlugin = new VolatilityPlugin();

        addPlugin(volatilityPlugin);
        addPlugin(orderBookVolumePlugin);
        addPlugin(targetPriceBot);

        if(tradingPairs.tradingPairs.size() > 0)
        {
            TradingPair pair = tradingPairs.tradingPairs.get(0).toTradingPair();
            orderBookVolumePlugin.subscribeToMarket(pair);
            volatilityPlugin.subscribeToMarket(pair);
            targetPriceBot.subscribeToMarket(pair);
        }
        /*for(TradingPairData pairData : tradingPairs.tradingPairs)
        {
            TradingPair pair = pairData.toTradingPair();
            orderBookVolumePlugin.subscribeToMarket(pair);
        }*/

        targetPriceBot.setEnabled(true);
        volatilityPlugin.setEnabled(true);
        orderBookVolumePlugin.setEnabled(true);


        return success;
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
