package net.kroia.stockmarket.pluginsystem.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class ServerPlugin<TSettings, TRuntimeData> implements ServerSaveable, IServerPlugin {
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

    public boolean getAutoSubscribeNewMarkets() {
        return genericPluginData.getAutoSubscribeNewMarkets();
    }
    public void setAutoSubscribeNewMarkets(boolean autoSubscribe) {
        genericPluginData.setAutoSubscribeNewMarkets(autoSubscribe);
    }

    public int getSubscriptionOrder() {
        return genericPluginData.getSubscriptionOrder();
    }
    public void setSubscriptionOrder(int order) {
        genericPluginData.setSubscriptionOrder(order);
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
     * Returns the StreamCodec for this plugin's runtime data type.
     * Override to enable runtime data streaming to the client GUI.
     * Return null if this plugin does not stream runtime data.
     */
    protected @Nullable StreamCodec<ByteBuf, TRuntimeData> runtimeDataCodec() {
        return null;
    }

    /**
     * Provides the current runtime data snapshot for streaming to the client.
     * Only called if runtimeDataCodec() returns non-null.
     */
    protected @Nullable TRuntimeData provideRuntimeData() {
        return null;
    }

    /**
     * Returns the update interval in milliseconds for the runtime data stream.
     */
    public long getRuntimeDataStreamInterval() {
        return 500;
    }

    /**
     * Framework method: encodes runtime data to bytes using the plugin's codec.
     * Called by the streaming infrastructure — plugin developers should not call this directly.
     */
    public final byte[] encodeRuntimeData() {
        StreamCodec<ByteBuf, TRuntimeData> codec = runtimeDataCodec();
        TRuntimeData data = provideRuntimeData();
        if (codec == null || data == null) return null;
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, data);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     CUSTOM SETTINGS
     * --------------------------------------------------------------------------------------------------------------*/

    /**
     * Returns the StreamCodec for this plugin's custom settings type.
     * Override to enable custom settings editing via the management UI.
     * Return null if this plugin has no custom settings.
     */
    protected @Nullable StreamCodec<ByteBuf, TSettings> customSettingsCodec() {
        return null;
    }

    /**
     * Provides the current custom settings for network transfer to the client.
     * Only called if customSettingsCodec() returns non-null.
     */
    protected @Nullable TSettings provideCustomSettings() {
        return null;
    }

    /**
     * Applies custom settings received from the client GUI.
     * Only called if customSettingsCodec() returns non-null.
     */
    protected boolean applyCustomSettings(@NotNull TSettings settings) {
        return false;
    }

    /**
     * Framework method: encodes custom settings to bytes using the plugin's codec.
     * Called by the networking infrastructure — plugin developers should not call this directly.
     */
    public final byte[] encodeCustomSettings() {
        StreamCodec<ByteBuf, TSettings> codec = customSettingsCodec();
        TSettings settings = provideCustomSettings();
        if (codec == null || settings == null) return null;
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, settings);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    /**
     * Framework method: decodes and applies custom settings from bytes.
     * Called by the networking infrastructure — plugin developers should not call this directly.
     */
    public final boolean decodeAndApplyCustomSettings(byte[] data) {
        StreamCodec<ByteBuf, TSettings> codec = customSettingsCodec();
        if (codec == null || data == null) return false;
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        try {
            TSettings settings = codec.decode(buf);
            return applyCustomSettings(settings);
        } catch (Exception e) {
            error("Failed to decode custom settings", e);
            return false;
        } finally {
            buf.release();
        }
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
