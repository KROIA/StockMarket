package net.kroia.stockmarket.pluginsystem.registry;

import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Holds registration data for a plugin type.
 * Server factory is set at registration time (safe for dedicated server).
 * GUI element factory is set separately in clientSetup() to avoid loading
 * client-only classes on the server.
 */
public class PluginRegistryObject {
    private final String pluginTypeID;
    private final String pluginName;
    private final String pluginDescription;
    private final Supplier<ServerPlugin> serverPluginFactory;

    // Set separately via clientSetup() to avoid loading client classes on dedicated server
    private @Nullable Supplier<PluginGuiElement> guiElementFactory = null;

    public PluginRegistryObject(String pluginTypeID,
                                String pluginName,
                                String pluginDescription,
                                Supplier<ServerPlugin> serverPluginFactory) {
        this.pluginTypeID = pluginTypeID;
        this.pluginName = pluginName;
        this.pluginDescription = pluginDescription;
        this.serverPluginFactory = serverPluginFactory;
    }

    public String getPluginTypeID() { return pluginTypeID; }
    public String getPluginName() { return pluginName; }
    public String getPluginDescription() { return pluginDescription; }

    /**
     * Sets the GUI element factory for this plugin.
     * Must be called from clientSetup() only — never from shared code.
     *
     * @param factory supplier that creates the plugin's PluginGuiElement, or null for default
     */
    public void setGuiElementFactory(@Nullable Supplier<PluginGuiElement> factory) {
        this.guiElementFactory = factory;
    }

    /** Returns the GUI element factory, or null if none was registered. */
    public @Nullable Supplier<PluginGuiElement> getGuiElementFactory() {
        return guiElementFactory;
    }

    /** Creates a new server plugin instance. */
    public ServerPlugin instantiateServerPlugin() {
        ServerPlugin plugin = serverPluginFactory.get();
        plugin.setRegistrar(this);
        return plugin;
    }

    /**
     * Creates a PluginGuiElement for this plugin type.
     * Returns null if no GUI element factory was registered (server-only or no custom UI).
     */
    public @Nullable PluginGuiElement createGuiElement() {
        if (guiElementFactory != null) {
            return guiElementFactory.get();
        }
        return new PluginGuiElement();
    }
}
