package net.kroia.stockmarket.pluginsystem.registry;

import net.kroia.stockmarket.pluginsystem.plugin.ClientPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class PluginRegistryObject
{
    private final String pluginTypeID;
    private final String pluginName;
    private final String pluginDescription;
    private final Supplier<ServerPlugin> serverPluginFactory;

    private final Function<UUID, ClientPlugin> clientPluginFactory;


    public PluginRegistryObject(String pluginTypeID,
                                String pluginName,
                                String pluginDescription,
                                Supplier<ServerPlugin> serverPluginFactory,
                                Function<UUID, ClientPlugin> clientPluginFactory)
    {
        this.pluginTypeID = pluginTypeID;
        this.pluginName = pluginName;
        this.pluginDescription = pluginDescription;
        this.serverPluginFactory = serverPluginFactory;
        this.clientPluginFactory = clientPluginFactory;
    }

    public String getPluginTypeID()
    {
        return pluginTypeID;
    }
    public String getPluginName()
    {
        return pluginName;
    }
    public String getPluginDescription()
    {
        return pluginDescription;
    }
    public ServerPlugin instantiateServerPlugin()
    {
        ServerPlugin plugin = serverPluginFactory.get();
        plugin.setRegistrar(this);
        return plugin;
    }
    public ClientPlugin instantiateClientPlugin(UUID serversInstanceID)
    {
        ClientPlugin plugin = clientPluginFactory.apply(serversInstanceID);
        plugin.setRegistrar(this);
        return plugin;
    }
}