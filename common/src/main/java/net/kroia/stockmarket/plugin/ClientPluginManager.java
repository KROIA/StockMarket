package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.networking.PluginTypesRequest;
import net.kroia.stockmarket.plugin.networking.UpdateUsedMarketPluginsRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientPluginManager {
    private final StockMarketModBackend.Instances BACKEND_INSTANCES;

    private static class Market
    {
        private final TradingPair tradingPair;
        private final List<ClientMarketPlugin> plugins = new ArrayList<>();

        public Market(TradingPair tradingPair)
        {
            this.tradingPair = tradingPair;
        }

        public ClientMarketPlugin getOrCreatePlugin(String pluginTypeID)
        {
            ClientMarketPlugin plugin = null;
            for(ClientMarketPlugin plugin1 : plugins)
            {
                if(plugin1.getPluginTypeID().equals(pluginTypeID))
                {
                    plugin = plugin1;
                    break;
                }
            }
            if(plugin == null)
            {
                plugin = PluginRegistry.createClientMarketPluginInstance(tradingPair, pluginTypeID);
                plugins.add(plugin);
            }
            return plugin;
        }
        public void refreshMarketInstances(List<String> pluginTypeIDs)
        {
            // Remove plugins that are no longer in the list
            for(int i=0; i<plugins.size(); i++)
            {
                boolean found = pluginTypeIDs.contains(plugins.get(i).getPluginTypeID());
                if(!found)
                {
                    plugins.remove(i);
                    i--;
                }
            }


            // Add new plugins that are in the list but not in the map
            for(String pluginTypeID : pluginTypeIDs)
            {
                getOrCreatePlugin(pluginTypeID);
            }

            // Sort the plugins according to the same order of the pluginIDs
            List<ClientMarketPlugin> oldPluginData = new ArrayList<>(plugins);
            plugins.clear();
            for(String pluginID : pluginTypeIDs)
            {
                for(ClientMarketPlugin pluginInstance : oldPluginData)
                {
                    if(pluginID.compareTo(pluginInstance.getPluginTypeID()) == 0)
                    {
                        plugins.add(pluginInstance);
                        break;
                    }
                }
            }


        }
        public void removePlugin(String pluginTypeID)
        {
            int index = -1;
            for(int i=0; i<plugins.size(); i++)
            {
                if(plugins.get(i).getPluginTypeID().equals(pluginTypeID))
                {
                    index = i;
                    break;
                }
            }
            if(index >= 0)
            {
                plugins.get(index).stopStream();
                plugins.remove(index);
            }
        }
        public void clearPlugins()
        {
            for(ClientMarketPlugin plugin : plugins)
            {
                plugin.stopStream();
            }
            plugins.clear();
        }
        public List<ClientMarketPlugin> getPlugins() {
            return plugins;
        }
        public boolean hasPlugins() {
            return !plugins.isEmpty();
        }
        public int getPluginCount() {
            return plugins.size();
        }
        public boolean hasPlugin(String pluginTypeID) {
            int index = -1;
            for(int i=0; i<plugins.size(); i++)
            {
                if(plugins.get(i).getPluginTypeID().equals(pluginTypeID))
                {
                    return true;
                }
            }
            return false;
        }

        public TradingPair getTradingPair() {
            return tradingPair;
        }

    }


    private final Map<TradingPair, Market> markets;
    private PluginTypesRequest.ResponseData availablePlugins;
    public ClientPluginManager(StockMarketModBackend.Instances backend)
    {
        BACKEND_INSTANCES = backend;
        markets = new HashMap<>();
    }
    public List<ClientMarketPlugin> getMarketPlugins(TradingPair pair)
    {
        Market market = markets.get(pair);
        if(market == null)
            return List.of();
        return market.getPlugins();
    }


    public void requestMarketPluginTypes(TradingPair pair, Consumer<List<String>> callback)
    {
        StockMarketNetworking.MARKET_PLUGIN_TYPES_REQUEST.sendRequestToServer(pair, (response) -> {
            Market market = markets.get(pair);
            if(market == null)
            {
                market = new Market(pair);
                markets.put(pair, market);
            }
            market.refreshMarketInstances(response);
            callback.accept(response);
        });
    }

    /**
     * Updates the list of available plugins that can be used in a market.
     * @param callback
     */
    public void requestMarketPluginTypes(Consumer<PluginTypesRequest.ResponseData> callback)
    {
        StockMarketNetworking.PLUGIN_TYPES_REQUEST.sendRequestToServer(0, (response) -> {
            availablePlugins = response;
            debug("Received available plugins from server. Market plugins: " + availablePlugins.marketPlugins.size());
            callback.accept(response);
        });
    }
    public List<PluginTypesRequest.PluginInfo> getAvailableMarketPlugins()
    {
        if(availablePlugins == null) {
            warn("Available plugins have not been requested yet!");
            requestMarketPluginTypes((response) -> {});
            return List.of();
        }
        return availablePlugins.marketPlugins;
    }


    /**
     * - Removes plugins from the given market that are not in the pluginTypeIDs list.
     * - Creates plugins if they do not exist for the given market.
     * @param pair
     * @param pluginTypeIDs
     * @param callback
     */
    public void requestSetMarketPluginTypes(TradingPair pair, List<String> pluginTypeIDs,  Consumer<Boolean> callback)
    {
        UpdateUsedMarketPluginsRequest.SenderData senderData = new UpdateUsedMarketPluginsRequest.SenderData();
        senderData.tradingPair = pair;
        senderData.usedPlugins = pluginTypeIDs;
        StockMarketNetworking.UPDATE_USED_MARKET_PLUGINS_REQUEST.sendRequestToServer(senderData, callback);
    }

    /*public List<PluginTypesRequest.PluginInfo> getAvailableGlobalPlugins()
    {
        if(availablePlugins == null) {
            warn("Available plugins have not been requested yet!");
            requestMarketPluginTypes((response) -> {});
            return List.of();
        }
        return availablePlugins.globalPlugins;
    }*/





    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ClientPluginManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ClientPluginManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ClientPluginManager] " + msg);
    }
}
