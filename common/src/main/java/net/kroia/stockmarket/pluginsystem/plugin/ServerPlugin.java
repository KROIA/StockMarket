package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ServerPlugin implements ServerSaveable, IServerPlugin {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }




    private enum State
    {
        NONE,
        EXEC_INIT,
        EXEC_DEINIT,
        EXEC_UPDATE,
        EXEC_FINALIZE,
        //EXEC_APPLY,
        EXEC_SAVE,
        EXEC_LOAD,
        EXEC_ENCODING,
        EXEC_DECODING,
    }
    private @Nullable ServerPluginManager manager = null;

    private State state = State.NONE;
    private int networkStreamPacketTickInterval = 20;

    private final GenericPluginData genericPluginData;

    public ServerPlugin()
    {
        genericPluginData = new GenericPluginData(UUID.randomUUID());
        genericPluginData.setLoggerEnabled(false);
        genericPluginData.setEnabled(false);
    }


    public void setLoggerEnabled(boolean enabled)
    {
        this.genericPluginData.setLoggerEnabled(enabled);
    }
    public boolean isLoggerEnabled()
    {
        return genericPluginData.isLoggerEnabled();
    }
    public void setName(String name)
    {
        genericPluginData.setName(name);
    }
    public String getName()
    {
        return genericPluginData.getName();
    }
    public void setDescription(String description)
    {
        genericPluginData.setDescription(description);
    }
    public String getDescription()
    {
        return genericPluginData.getDescription();
    }
    public void setEnabled(boolean enabled)
    {
        genericPluginData.setEnabled(enabled);
    }
    public final boolean isEnabled()
    {
        return genericPluginData.isEnabled();
    }
    public final UUID getInstanceID()
    {
        return genericPluginData.getInstanceID();
    }
    public final void setRegistrar(PluginRegistryObject registrar)
    {
        genericPluginData.setRegistrar(registrar);
    }
    public final @Nullable String getPluginTypeID()
    {
        return genericPluginData.getPluginTypeID();
    }


    public final void setManager(ServerPluginManager manager)
    {
        this.manager = manager;
    }
    public final ServerPluginManager getManager()
    {
        return manager;
    }


    @Override
    public boolean save(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean load(CompoundTag tag) {
        return false;
    }


    protected final void info(String msg)
    {
        if(genericPluginData.isLoggerEnabled())
            BACKEND_INSTANCES.LOGGER.info(getLogPrefix() + msg);
    }
    protected final void error(String msg)
    {
        if(genericPluginData.isLoggerEnabled())
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg);
    }
    protected final void error(String msg, Throwable e)
    {
        if(genericPluginData.isLoggerEnabled())
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg, e);
    }
    protected final void warn(String msg)
    {
        if(genericPluginData.isLoggerEnabled())
            BACKEND_INSTANCES.LOGGER.warn(getLogPrefix() + msg);
    }
    protected final void debug(String msg)
    {
        if(genericPluginData.isLoggerEnabled())
            BACKEND_INSTANCES.LOGGER.debug(getLogPrefix() + msg);
    }
    private String getLogPrefix() {
        return "[Plugin: "+genericPluginData.getName()+ "] ";
    }

}
