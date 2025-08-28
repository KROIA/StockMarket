package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.MarketPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PluginRegistry {

    public static class MarketPluginRegistrationObject
    {
        public final String pluginTypeID;
        private final Supplier<MarketPlugin> serverPluginSupplier;
        private final Supplier<ClientMarketPlugin> clientPluginSupplier;

        public MarketPluginRegistrationObject(String pluginTypeID,
                                              Supplier<MarketPlugin> serverPluginSupplier,
                                              Supplier<ClientMarketPlugin> clientPluginSupplier) {
            if(serverPluginSupplier == null)
                throw new IllegalArgumentException("Plugin supplier cannot be null");
            if(pluginTypeID == null || pluginTypeID.isEmpty())
                throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
            if(clientPluginSupplier == null)
                throw new IllegalArgumentException("Client plugin supplier cannot be null");
            this.clientPluginSupplier = clientPluginSupplier;
            this.pluginTypeID = pluginTypeID;
            this.serverPluginSupplier = serverPluginSupplier;
        }
    }
    private static final Map<String, MarketPluginRegistrationObject> REGISTERED_PLUGINS = new java.util.HashMap<>();


    public static MarketPluginRegistrationObject registerPlugin(String pluginTypeID, Supplier<MarketPlugin> pluginSupplier, Supplier<ClientMarketPlugin> clientPluginSupplier)
    {
        if(pluginSupplier == null)
            throw new IllegalArgumentException("Plugin supplier cannot be null");
        if(pluginTypeID.isEmpty())
            throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
        if(REGISTERED_PLUGINS.containsKey(pluginTypeID))
            throw new IllegalArgumentException("Plugin type ID already registered: " + pluginTypeID);
        MarketPluginRegistrationObject registrationObject = new MarketPluginRegistrationObject(pluginTypeID, pluginSupplier, clientPluginSupplier);
        REGISTERED_PLUGINS.put(pluginTypeID, registrationObject);
        return registrationObject;
    }
    public static MarketPlugin createServerPluginInstance(String pluginTypeID)
    {
        MarketPluginRegistrationObject registryObj = REGISTERED_PLUGINS.get(pluginTypeID);
        if(registryObj == null)
            throw new IllegalArgumentException("Plugin type ID not registered: " + pluginTypeID);
        MarketPlugin plugin = registryObj.serverPluginSupplier.get();
        plugin.setPluginTypeID(pluginTypeID);
        return plugin;
    }
    public static MarketPlugin createServerPluginInstance(MarketPluginRegistrationObject registrationObject)
    {
        return registrationObject.serverPluginSupplier.get();
    }
    public static ClientMarketPlugin createClientPluginInstance(String pluginTypeID)
    {
        MarketPluginRegistrationObject registryObj = REGISTERED_PLUGINS.get(pluginTypeID);
        if(registryObj == null)
            throw new IllegalArgumentException("Plugin type ID not registered: " + pluginTypeID);
        ClientMarketPlugin plugin = registryObj.clientPluginSupplier.get();
        plugin.setPluginTypeID(pluginTypeID);
        return plugin;
    }
    public static ClientMarketPlugin createClientPluginInstance(MarketPluginRegistrationObject registrationObject)
    {
        ClientMarketPlugin plugin = registrationObject.clientPluginSupplier.get();
        plugin.setPluginTypeID(registrationObject.pluginTypeID);
        return plugin;
    }
    public static Map<String, MarketPluginRegistrationObject> getRegisteredPlugins() {
        return REGISTERED_PLUGINS;
    }
    public static int getRegisteredPluginCount() {
        return REGISTERED_PLUGINS.size();
    }
    public static List<String> getRegisteredPluginTypeIDs() {
        return REGISTERED_PLUGINS.keySet().stream().toList();
    }

}
