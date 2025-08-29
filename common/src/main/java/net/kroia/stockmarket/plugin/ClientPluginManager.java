package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientPluginManager {
    private final StockMarketModBackend.Instances BACKEND_INSTANCES;

    private static class Market
    {
        private final TradingPair tradingPair;
        private final Map<String, ClientMarketPlugin> plugins = new HashMap<>();

        public Market(TradingPair tradingPair)
        {
            this.tradingPair = tradingPair;
        }

        public ClientMarketPlugin getOrCreatePlugin(String pluginTypeID)
        {
            ClientMarketPlugin plugin = plugins.get(pluginTypeID);
            if(plugin != null)
            {

                plugin = PluginRegistry.createClientPluginInstance(tradingPair, pluginTypeID);
                plugins.put(pluginTypeID, plugin);
            }
            return plugin;
        }
        public void refreshMarketInstances(List<String> pluginTypeIDs)
        {
            // Remove plugins that are no longer in the list
            plugins.keySet().removeIf(pluginTypeID -> !pluginTypeIDs.contains(pluginTypeID));
            // Add new plugins that are in the list but not in the map
            for(String pluginTypeID : pluginTypeIDs)
            {
                if(!plugins.containsKey(pluginTypeID))
                {
                    ClientMarketPlugin plugin = PluginRegistry.createClientPluginInstance(tradingPair, pluginTypeID);
                    plugins.put(pluginTypeID, plugin);
                }
            }
        }
        public void removePlugin(String pluginTypeID)
        {
            ClientMarketPlugin plugin = plugins.get(pluginTypeID);
            if(plugin != null)
            {
                plugin.stopStream();
                plugins.remove(pluginTypeID);
            }
        }
        public void clearPlugins()
        {
            for(ClientMarketPlugin plugin : plugins.values())
            {
                plugin.stopStream();
            }
            plugins.clear();
        }
        public Map<String, ClientMarketPlugin> getPlugins() {
            return plugins;
        }
        public boolean hasPlugins() {
            return !plugins.isEmpty();
        }
        public int getPluginCount() {
            return plugins.size();
        }
        public boolean hasPlugin(String pluginTypeID) {
            return plugins.containsKey(pluginTypeID);
        }

        public TradingPair getTradingPair() {
            return tradingPair;
        }

    }


    private final Map<TradingPair, Market> markets;
    public ClientPluginManager(StockMarketModBackend.Instances backend)
    {
        BACKEND_INSTANCES = backend;
        markets = new HashMap<>();
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



}
