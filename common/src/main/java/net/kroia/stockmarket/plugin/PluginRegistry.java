package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.MarketPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PluginRegistry {

    public static class MarketPluginRegistrationObject
    {
        public final String pluginTypeID;
        public final String name;
        public final String description;
        private final Supplier<MarketPlugin> serverPluginSupplier;
        private final BiFunction<TradingPair, String, ClientMarketPlugin> clientPluginSupplier;

        public MarketPluginRegistrationObject(String pluginTypeID,
                                              Supplier<MarketPlugin> serverPluginSupplier,
                                              BiFunction<TradingPair, String, ClientMarketPlugin> clientPluginSupplier,
                                              String name, String description) {
            if(serverPluginSupplier == null)
                throw new IllegalArgumentException("Plugin supplier cannot be null");
            if(pluginTypeID == null || pluginTypeID.isEmpty())
                throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
            if(clientPluginSupplier == null)
                throw new IllegalArgumentException("Client plugin supplier cannot be null");
            this.clientPluginSupplier = clientPluginSupplier;
            this.pluginTypeID = pluginTypeID;
            this.serverPluginSupplier = serverPluginSupplier;
            this.name = name;
            this.description = description;
        }
    }
    private static final Map<String, MarketPluginRegistrationObject> REGISTERED_MARKET_PLUGINS = new java.util.HashMap<>();


    public static MarketPluginRegistrationObject registerPlugin(String pluginTypeID,
                                                                Supplier<MarketPlugin> pluginSupplier,
                                                                BiFunction<TradingPair, String, ClientMarketPlugin> clientPluginSupplier,
                                                                String name, String description)
    {
        if(pluginSupplier == null)
            throw new IllegalArgumentException("Plugin supplier cannot be null");
        if(pluginTypeID.isEmpty())
            throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
        if(REGISTERED_MARKET_PLUGINS.containsKey(pluginTypeID))
            throw new IllegalArgumentException("Plugin type ID already registered: " + pluginTypeID);
        MarketPluginRegistrationObject registrationObject = new MarketPluginRegistrationObject(pluginTypeID, pluginSupplier, clientPluginSupplier, name, description);
        REGISTERED_MARKET_PLUGINS.put(pluginTypeID, registrationObject);
        return registrationObject;
    }
    public static MarketPlugin createServerMarketPluginInstance(String pluginTypeID)
    {
        MarketPluginRegistrationObject registryObj = REGISTERED_MARKET_PLUGINS.get(pluginTypeID);
        if(registryObj == null)
            throw new IllegalArgumentException("Plugin type ID not registered: " + pluginTypeID);
        MarketPlugin plugin = registryObj.serverPluginSupplier.get();
        plugin.setPluginTypeID(pluginTypeID);
        plugin.setName(registryObj.name);
        return plugin;
    }
    public static MarketPlugin createServerMarketPluginInstance(MarketPluginRegistrationObject registrationObject)
    {
        MarketPlugin plugin = registrationObject.serverPluginSupplier.get();
        plugin.setPluginTypeID(registrationObject.pluginTypeID);
        plugin.setName(registrationObject.name);
        return plugin;
    }
    public static ClientMarketPlugin createClientMarketPluginInstance(TradingPair tradingPair, String pluginTypeID)
    {
        MarketPluginRegistrationObject registryObj = REGISTERED_MARKET_PLUGINS.get(pluginTypeID);
        if(registryObj == null)
            throw new IllegalArgumentException("Plugin type ID not registered: " + pluginTypeID);
        ClientMarketPlugin plugin = registryObj.clientPluginSupplier.apply(tradingPair , pluginTypeID);
        plugin.setName(registryObj.name);
        return plugin;
    }
    public static ClientMarketPlugin createClientMarketPluginInstance(MarketPluginRegistrationObject registrationObject, TradingPair tradingPair)
    {
        ClientMarketPlugin plugin = registrationObject.clientPluginSupplier.apply(tradingPair, registrationObject.pluginTypeID);
        plugin.setName(registrationObject.name);
        return plugin;
    }
    public static Map<String, MarketPluginRegistrationObject> getRegisteredMarketPlugins() {
        return REGISTERED_MARKET_PLUGINS;
    }
    public static int getRegisteredMarketPluginCount() {
        return REGISTERED_MARKET_PLUGINS.size();
    }
    public static List<String> getRegisteredMarketPluginTypeIDs() {
        return REGISTERED_MARKET_PLUGINS.keySet().stream().toList();
    }

}
