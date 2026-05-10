package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IClientPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ClientPlugin implements IClientPlugin {
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final GenericPluginData genericPluginData;
    private PluginGuiElement guiElement;

    public ClientPlugin(UUID instanceID) {
        genericPluginData  = new GenericPluginData(instanceID);
    }

    public PluginGuiElement getGuiElement() {
        if(guiElement == null)
            guiElement = new PluginGuiElement();
        return guiElement;
    }

    public final void setRegistrar(PluginRegistryObject registrar)
    {
        genericPluginData.setRegistrar(registrar);
    }




    public boolean isLoggerEnabled()
    {
        return genericPluginData.isLoggerEnabled();
    }
    public String getName()
    {
        return genericPluginData.getName();
    }
    public String getDescription()
    {
        return genericPluginData.getDescription();
    }
    public final boolean isEnabled()
    {
        return genericPluginData.isEnabled();
    }
    public final UUID getInstanceID()
    {
        return genericPluginData.getInstanceID();
    }
    public final @Nullable String getPluginTypeID()
    {
        return genericPluginData.getPluginTypeID();
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientPlugin]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPlugin]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPlugin]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientPlugin]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientPlugin]: "+message);
    }
}
