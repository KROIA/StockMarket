package net.kroia.stockmarket.plugin.base;

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
    private final Supplier<PluginGuiElement> pluginGuiElementFactory;


    public PluginRegistryObject(String pluginTypeID,
                                String pluginName,
                                String pluginDescription,
                                Supplier<ServerPlugin> serverPluginFactory,
                                Function<UUID, ClientPlugin> clientPluginFactory,
                                Supplier<PluginGuiElement> pluginGuiElementFactory)
    {
        this.pluginTypeID = pluginTypeID;
        this.pluginName = pluginName;
        this.pluginDescription = pluginDescription;
        this.serverPluginFactory = serverPluginFactory;
        this.clientPluginFactory = clientPluginFactory;
        this.pluginGuiElementFactory = pluginGuiElementFactory;
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
    public PluginGuiElement instantiatePluginGuiElement(UUID serversInstanceID)
    {
        PluginGuiElement pluginGuiElement = pluginGuiElementFactory.get();
        return pluginGuiElement;
    }
}
