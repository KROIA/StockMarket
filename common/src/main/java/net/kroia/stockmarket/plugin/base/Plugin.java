package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public abstract class Plugin implements ServerSaveable {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private UUID id;
    private String name;
    private boolean loggerEnabled;
    private boolean enabled;

    public Plugin(String name)
    {
        id  = UUID.randomUUID();
        this.name = name;
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
    public String getName()
    {
        return name;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    public final boolean isEnabled()
    {
        return enabled;
    }

    public final UUID getID()
    {
        return id;
    }


    @Override
    public boolean save(CompoundTag tag) {
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putBoolean("loggerEnabled", loggerEnabled);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("id") ||
           !tag.contains("name") ||
           !tag.contains("loggerEnabled"))
            return false;

        id = tag.getUUID("id");
        name = tag.getString("name");
        loggerEnabled = tag.getBoolean("loggerEnabled");
        return true;
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
