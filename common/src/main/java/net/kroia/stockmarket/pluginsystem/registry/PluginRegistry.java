package net.kroia.stockmarket.pluginsystem.registry;

import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Static registry for all plugin types.
 * Registration happens at class load time with server-safe factories only.
 * GUI element factories are registered separately via clientSetup().
 */
public class PluginRegistry {
    private static final Map<String, PluginRegistryObject> registryObjects = new HashMap<>();

    /**
     * Registers a plugin type with its server factory.
     * Safe to call from shared code — no client class references.
     * GUI element factory must be set separately via {@link PluginRegistryObject#setGuiElementFactory}.
     */
    public static PluginRegistryObject registerPlugin(String pluginTypeID,
                                                      String pluginName,
                                                      String pluginDescription,
                                                      Supplier<ServerPlugin> serverPluginFactory) {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if (registryObject != null) {
            return registryObject;
        }
        registryObject = new PluginRegistryObject(pluginTypeID, pluginName, pluginDescription, serverPluginFactory);
        registryObjects.put(pluginTypeID, registryObject);
        return registryObject;
    }

    /** Finds a registered plugin by its type ID. */
    public static @Nullable PluginRegistryObject findPlugin(String pluginTypeID) {
        return registryObjects.get(pluginTypeID);
    }

    /** Creates a server plugin instance by type ID. */
    public static @Nullable ServerPlugin instantiateServerPlugin(String pluginTypeID) {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if (registryObject != null) {
            return registryObject.instantiateServerPlugin();
        }
        return null;
    }

    /** Creates a server plugin instance from a registry object. */
    public static @Nullable ServerPlugin instantiateServerPlugin(PluginRegistryObject registryObject) {
        for (Map.Entry<String, PluginRegistryObject> entry : registryObjects.entrySet()) {
            if (entry.getValue().equals(registryObject)) {
                return registryObject.instantiateServerPlugin();
            }
        }
        return null;
    }

    /**
     * Creates a PluginGuiElement for the given plugin type.
     * Returns null if the plugin type is not registered.
     * Returns a default PluginGuiElement if no custom factory was set.
     */
    public static @Nullable PluginGuiElement createGuiElement(String pluginTypeID) {
        PluginRegistryObject registryObject = registryObjects.get(pluginTypeID);
        if (registryObject != null) {
            return registryObject.createGuiElement();
        }
        return null;
    }

    /** Returns all registered plugin types. */
    public static @NotNull Map<String, PluginRegistryObject> getRegistryObjects() {
        return registryObjects;
    }
}
