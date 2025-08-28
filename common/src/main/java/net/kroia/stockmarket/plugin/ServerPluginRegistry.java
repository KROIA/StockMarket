package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.plugin.base.MarketPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ServerPluginRegistry {

    public static class MarketPluginRegistrationObject
    {
        public final String pluginTypeID;
        public final Supplier<MarketPlugin> pluginSupplier;

        public MarketPluginRegistrationObject(String pluginTypeID, Supplier<MarketPlugin> pluginSupplier) {
            if(pluginSupplier == null)
                throw new IllegalArgumentException("Plugin supplier cannot be null");
            if(pluginTypeID == null || pluginTypeID.isEmpty())
                throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
            this.pluginTypeID = pluginTypeID;
            this.pluginSupplier = pluginSupplier;
        }
    }
    private static final Map<String, Supplier<MarketPlugin>> REGISTERED_PLUGINS = new java.util.HashMap<>();


    public static MarketPluginRegistrationObject registerPlugin(String pluginTypeID, Supplier<MarketPlugin> pluginSupplier)
    {
        if(pluginSupplier == null)
            throw new IllegalArgumentException("Plugin supplier cannot be null");
        if(pluginTypeID == null || pluginTypeID.isEmpty())
            throw new IllegalArgumentException("Plugin type ID cannot be null or empty");
        if(REGISTERED_PLUGINS.containsKey(pluginTypeID))
            throw new IllegalArgumentException("Plugin type ID already registered: " + pluginTypeID);
        REGISTERED_PLUGINS.put(pluginTypeID, pluginSupplier);
        return new MarketPluginRegistrationObject(pluginTypeID, pluginSupplier);
    }
    public static MarketPlugin createPluginInstance(String pluginTypeID)
    {
        Supplier<MarketPlugin> pluginSupplier = REGISTERED_PLUGINS.get(pluginTypeID);
        if(pluginSupplier == null)
            throw new IllegalArgumentException("Plugin type ID not registered: " + pluginTypeID);
        return pluginSupplier.get();
    }
    public static MarketPlugin createPluginInstance(MarketPluginRegistrationObject registrationObject)
    {
        return registrationObject.pluginSupplier.get();
    }
    public static Map<String, Supplier<MarketPlugin>> getRegisteredPlugins() {
        return REGISTERED_PLUGINS;
    }
    public static int getRegisteredPluginCount() {
        return REGISTERED_PLUGINS.size();
    }
    public static List<String> getRegisteredPluginTypeIDs() {
        return REGISTERED_PLUGINS.keySet().stream().toList();
    }

}
