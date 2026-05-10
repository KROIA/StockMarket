package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.PluginCache;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class ServerPlugin implements ServerSaveable, IServerPlugin {
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
    }
    private @Nullable ServerPluginManager manager = null;
    private final PluginCache cache = new PluginCache();

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
    /*public void setEnabled(boolean enabled)
    {
        genericPluginData.setEnabled(enabled);
    }*/
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
    public final @Nullable String getPluginTypeID() {
        return genericPluginData.getPluginTypeID();
    }
    public GenericPluginData getGenericPluginData() {
        return genericPluginData;
    }

    /**
     * Restores the plugin's instance ID from saved data.
     * Only used during load to preserve identity across server restarts.
     * Public for cross-package access from ServerPluginManager — not part of the plugin API.
     */
    public void setInstanceID(UUID id) {
        genericPluginData.setInstanceID(id);
    }



    /* ----------------------------------------------------------------------------------------------------------------
     *                     UPDATE LOOP
     * --------------------------------------------------------------------------------------------------------------*/

    public abstract void init();
    public abstract void deInit();
    public abstract void update(List<MarketInterface> markets);
    public abstract void finalize(List<MarketInterface> markets);




    public final List<MarketInterface> getMarketInterfaces()
    {
        return cache.getInterfaces();
    }
    public final @Nullable MarketInterface getMarketInterface(ItemID marketID)
    {
        List<MarketInterface>  marketInterfaces = getMarketInterfaces();
        for(MarketInterface marketInterface : marketInterfaces)
            if(marketInterface.market.getMarketID().equals(marketID))
                return marketInterface;
        return null;
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     EVENTS
     * --------------------------------------------------------------------------------------------------------------*/
    public abstract void onMarketSubscribed(ItemID marketID);
    public abstract void onMarketUnsubscribed(ItemID marketID);
    public abstract void onEnable();
    public abstract void onDisable();





    /* ----------------------------------------------------------------------------------------------------------------
     *                     MANAGEMENT
     * --------------------------------------------------------------------------------------------------------------*/
    public void subscribeToMarket(ItemID itemID)
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Subscribing to a market is not allowed from inside the plugin update loop!");
            return;
        }
        if(manager == null)
        {
            error("Market behavior plugin has attached to the manager");
            return;
        }
        MarketCache marketCache = manager.createCache(itemID);
        if(marketCache == null)
        {
            error("Can't subscribe to market: " + itemID + " could not create cache");
            return;
        }
        if(cache.putCache(itemID, marketCache))
        {
            onMarketSubscribed(itemID);
        }
    }
    public void unsubscribeFromMarket(ItemID itemID)
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Unsubscribing from a market is not allowed from inside the plugin update loop!");
            return;
        }
        if(cache.removeMarketCache(itemID))
        {
            onMarketUnsubscribed(itemID);
        }
    }


    void setNetworkStreamPacketTickInterval(int tickInterval)
    {
        this.networkStreamPacketTickInterval = tickInterval;
    }
    public int getNetworkStreamPacketTickInterval()
    {
        return networkStreamPacketTickInterval;
    }

    public List<ItemID> getSubscribedMarkets()
    {
        return cache.getMarketCaches().keySet().stream().toList();
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     INTERNAL  METHODS
     * --------------------------------------------------------------------------------------------------------------*/

    //@Override
    public void setEnabled(boolean enabled)
    {
        if(enabled == isEnabled())
            return;
        genericPluginData.setEnabled(enabled);
        //super.setEnabled(enabled);
        if(enabled)
        {
            onEnable();
        }
        else
        {
            onDisable();
        }
    }

    public final void init_internal()
    {
        state =  State.EXEC_INIT;
        init();
        state  = State.NONE;
    }
    public final void deInit_internal()
    {
        state  = State.EXEC_DEINIT;
        deInit();
        state  = State.NONE;
    }
    public final void update_internal()
    {
        state  = State.EXEC_UPDATE;
        update(cache.getInterfaces());
        state  = State.NONE;
    }
    public final void finalize_internal()
    {
        state  = State.EXEC_FINALIZE;
        finalize(cache.getInterfaces());
        state  = State.NONE;
    }

    public final void setManager(ServerPluginManager manager)
    {
        this.manager = manager;
    }
    public final ServerPluginManager getManager()
    {
        return manager;
    }
    

    /* ----------------------------------------------------------------------------------------------------------------
     *                     RUNTIME DATA STREAMING
     * --------------------------------------------------------------------------------------------------------------*/

    /**
     * Provides a snapshot of this plugin's runtime data for streaming to the client.
     * Override to send live debug/monitoring data to the PluginGuiElement.
     * Return null if this plugin has no runtime data to stream.
     *
     * @return encoded runtime data bytes, or null if no data available
     */
    public byte[] provideRuntimeData() {
        return null;
    }

    /**
     * Returns the update interval in milliseconds for the runtime data stream.
     * This is a server-side constant defined by the plugin developer.
     *
     * @return stream update interval in ms (default 500)
     */
    public long getRuntimeDataStreamInterval() {
        return 500;
    }

    /**
     * Provides a snapshot of this plugin's custom settings for network transfer.
     * Override to send plugin-specific settings to the client GUI.
     * Return null if this plugin has no custom settings.
     *
     * @return encoded custom settings bytes, or null if not supported
     */
    public byte[] provideCustomSettings() {
        return null;
    }

    /**
     * Applies custom settings received from the client GUI.
     * Override to decode and apply plugin-specific settings.
     *
     * @param payload the encoded custom settings bytes from the client
     * @return true if settings were applied successfully
     */
    public boolean applyCustomSettings(byte[] payload) {
        return false;
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     DATA HANDLING
     * --------------------------------------------------------------------------------------------------------------*/


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
