package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.plugin.base.ClientPlugin;
import net.kroia.stockmarket.plugin.base.PluginRegistry;
import net.kroia.stockmarket.plugin.networking.PluginInstancesRequest;
import net.kroia.stockmarket.plugin.networking.PluginTypesRequest;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class ClientPluginManager
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final List<PluginTypesRequest.PluginTypeData> pluginTypes = new ArrayList<>();
    private final List<PluginInstancesRequest.PluginInstanceData> existingPlugins = new ArrayList<>();
    private final Map<UUID, ClientPlugin> plugins = new HashMap<>();

    public ClientPluginManager()
    {

    }



    public void requestPluginTypes()
    {
        StockMarketNetworking.PLUGIN_TYPES_REQUEST.sendRequestToServer(true, (response) ->
        {
            pluginTypes.clear();
            pluginTypes.addAll(response);
        });
    }
    public void requestPluginTypes(@NotNull Consumer<List<PluginTypesRequest.PluginTypeData>> callback)
    {
        StockMarketNetworking.PLUGIN_TYPES_REQUEST.sendRequestToServer(true, (response) ->
        {
            pluginTypes.clear();
            pluginTypes.addAll(response);
            callback.accept(response);
        });
    }
    public @NotNull List<PluginTypesRequest.PluginTypeData> getPluginTypesCached()
    {
        return pluginTypes;
    }




    public void requestPluginInstances()
    {
        StockMarketNetworking.PLUGIN_INSTANCES_REQUEST.sendRequestToServer(true, (response) ->
        {
            existingPlugins.clear();
            updatePluginInstances();
            existingPlugins.addAll(response);
        });
    }
    public void requestPluginInstances(@NotNull Consumer<List<PluginInstancesRequest.PluginInstanceData>> callback)
    {
        StockMarketNetworking.PLUGIN_INSTANCES_REQUEST.sendRequestToServer(true, (response) ->
        {
            existingPlugins.clear();
            existingPlugins.addAll(response);
            updatePluginInstances();
            callback.accept(response);
        });
    }
    public @NotNull List<PluginInstancesRequest.PluginInstanceData> getExistingPluginsCached()
    {
        return existingPlugins;
    }





    private void updatePluginInstances()
    {
        // Check for removed plugin instances
        Map<UUID, PluginInstancesRequest.PluginInstanceData> existingPluginsMap = new HashMap<>();
        for (PluginInstancesRequest.PluginInstanceData pluginInstanceData : existingPlugins)
        {
            existingPluginsMap.put(pluginInstanceData.instanceID, pluginInstanceData);
        }

        List<UUID> instanceIDsToRemove = new ArrayList<>();
        for(Map.Entry<UUID, ClientPlugin> instance : plugins.entrySet())
        {
            UUID instanceID = instance.getKey();
            if(!existingPluginsMap.containsKey(instanceID))
            {
                instance.getValue().setRemoved();
                instanceIDsToRemove.add(instanceID);
            }
        }
        instanceIDsToRemove.forEach(plugins.keySet()::remove);

        // Check for new plugins
        for(PluginInstancesRequest.PluginInstanceData  pluginInstanceData : existingPlugins)
        {
            UUID instanceID = pluginInstanceData.instanceID;
            ClientPlugin plugin = plugins.get(instanceID);
            if(plugin == null)
            {
                plugin = PluginRegistry.instantiateClientPlugin(pluginInstanceData.typeID, pluginInstanceData.instanceID);
                if(plugin != null)
                {
                    plugins.put(instanceID, plugin);
                }
            }
        }
    }
}
