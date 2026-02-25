package net.kroia.stockmarket.plugin.base;

import net.kroia.stockmarket.StockMarketModBackend;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class Plugin
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private PluginRegistryObject registrar;
    private UUID instanceID;
    private String name;
    private String description;
    private boolean loggerEnabled;
    private boolean enabled;

    public Plugin(UUID instanceID)
    {
        this.instanceID = instanceID;
        loggerEnabled = false;
        enabled = false;
    }


    public void setLoggerEnabled(boolean enabled)
    {
        this.loggerEnabled = enabled;
    }
    public boolean isLoggerEnabled()
    {
        return loggerEnabled;
    }

    public void setName(String name)
    {
        this.name = name;
    }
    public String getName()
    {
        return name;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
    public String getDescription()
    {
        return description;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    public final boolean isEnabled()
    {
        return enabled;
    }

    protected final void setInstanceID(UUID id)
    {
        this.instanceID = id;
    }
    public final UUID getInstanceID()
    {
        return instanceID;
    }
    public final void setRegistrar(PluginRegistryObject registrar)
    {
        this.registrar = registrar;
        if(registrar != null)
        {
            name = registrar.getPluginName();
            description = registrar.getPluginDescription();
        }
    }
    public final @Nullable PluginRegistryObject getRegistrar()
    {
        return registrar;
    }
    public final @Nullable String getPluginTypeID()
    {
        if(registrar != null)
        {
            return registrar.getPluginTypeID();
        }
        return null;
    }







    protected final void info(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.info(getLogPrefix() + msg);
    }
    protected final void error(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg);
    }
    protected final void error(String msg, Throwable e)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg, e);
    }
    protected final void warn(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.warn(getLogPrefix() + msg);
    }
    protected final void debug(String msg)
    {
        if(loggerEnabled)
            BACKEND_INSTANCES.LOGGER.debug(getLogPrefix() + msg);
    }
    private String getLogPrefix() {
        return "[Plugin: "+name+ "] ";
    }
}
