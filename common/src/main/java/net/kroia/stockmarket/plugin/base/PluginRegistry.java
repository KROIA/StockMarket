package net.kroia.stockmarket.plugin.base;

import net.kroia.stockmarket.StockMarketModBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class PluginRegistry
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        Plugin.setBackend(backend);
    }

    private static final Map<String, PluginRegistryObject> registryObjects = new HashMap<>();

    public static PluginRegistryObject registerPlugin(String pluginTypeID,
                                      String pluginName,
                                      String pluginDescription,
                                      Supplier<ServerPlugin> serverPluginFactory,
                                      Function<UUID, ClientPlugin> clientPluginFactory,
                                                      Supplier<PluginGuiElement> pluginGuiElementFactory)
    {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if (registryObject != null)
        {
            return registryObject;
        }
        registryObject = new PluginRegistryObject(
                pluginTypeID,
                pluginName,
                pluginDescription,
                serverPluginFactory,
                clientPluginFactory,
                pluginGuiElementFactory);
        registryObjects.put(pluginTypeID, registryObject);
        return registryObject;
    }

    public static PluginRegistryObject findPlugin(String pluginTypeID)
    {
        return registryObjects.get(pluginTypeID);
    }

    public static @Nullable ServerPlugin instantiateServerPlugin(String pluginTypeID)
    {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if (registryObject != null)
        {
            return registryObject.instantiateServerPlugin();
        }
        return null;
    }
    public static @Nullable ServerPlugin instantiateServerPlugin(PluginRegistryObject registryObject)
    {
        // Check if the given registry object exists in this registry
        for(Map.Entry<String,PluginRegistryObject> entry : registryObjects.entrySet())
        {
            if(entry.getValue().equals(registryObject))
            {
                return registryObject.instantiateServerPlugin();
            }
        }
        return null;
    }

    public static @Nullable ClientPlugin instantiateClientPlugin(String pluginTypeID, UUID serverInstanceID)
    {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if(registryObject != null)
        {
            return registryObject.instantiateClientPlugin(serverInstanceID);
        }
        return null;
    }
    public static @Nullable ClientPlugin instantiateClientPlugin(PluginRegistryObject registryObject, UUID serverInstanceID)
    {
        // Check if the given registry object exists in this registry
        for(Map.Entry<String,PluginRegistryObject> entry : registryObjects.entrySet())
        {
            if(entry.getValue().equals(registryObject))
            {
                return registryObject.instantiateClientPlugin(serverInstanceID);
            }
        }
        return null;
    }

    public static @NotNull Map<String, PluginRegistryObject>  getRegistryObjects()
    {
        return registryObjects;
    }
}
