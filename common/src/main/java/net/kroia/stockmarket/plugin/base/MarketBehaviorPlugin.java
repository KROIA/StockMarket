package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.ServerMarketPluginManager;
import net.kroia.stockmarket.plugin.base.cache.MarketBehaviorPluginCache;
import net.kroia.stockmarket.plugin.base.cache.MarketCache;
import net.kroia.stockmarket.plugin.interaction.MarketInterfaces;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class MarketBehaviorPlugin extends Plugin implements INetworkPayloadConverter
{
    private ServerMarketPluginManager manager = null;
    private final MarketBehaviorPluginCache cache = new MarketBehaviorPluginCache();
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
    private State state = State.NONE;

    private int networkStreamPacketTickInterval = 20;

    public MarketBehaviorPlugin(String name) {
        super(name);
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     UPDATE LOOP
     * --------------------------------------------------------------------------------------------------------------*/

    public abstract void init();
    public abstract void deInit();
    public abstract void update(List<MarketInterfaces> markets);
    public abstract void finalize(List<MarketInterfaces> markets);




    public final List<MarketInterfaces> getMarketInterfaces()
    {
        return cache.getInterfaces();
    }
    public final @Nullable MarketInterfaces getMarketInterface(TradingPair tradingPair)
    {
        List<MarketInterfaces>  marketInterfaces = getMarketInterfaces();
        for(MarketInterfaces marketInterface : marketInterfaces)
            if(marketInterface.market.getTradingPair().equals(tradingPair))
                return marketInterface;
        return null;
    }


    /* ----------------------------------------------------------------------------------------------------------------
     *                     EVENTS
     * --------------------------------------------------------------------------------------------------------------*/
    public abstract void onMarketSubscribed(TradingPair tradingPair);
    public abstract void onMarketUnsubscribed(TradingPair tradingPair);
    public abstract void onEnable();
    public abstract void onDisable();







    /* ----------------------------------------------------------------------------------------------------------------
     *                     DATA HANDLING
     * --------------------------------------------------------------------------------------------------------------*/



    /**
     * Save data to disk
     * @param tag
     * @return true if the tag can be stored
     */
    public abstract boolean saveData(CompoundTag tag);

    /**
     * Load data from disk
     * @param tag
     * @return return true if reading was successfully
     */
    public abstract boolean loadData(CompoundTag tag);

    /**
     * Networking data encoder to send data & settings to the client for plugin management
     * @param buf The buffer to write the packet data to.
     */
    public void encodeNetworkData(FriendlyByteBuf buf)
    {
        /*
         * Dummy implementation that uses the save methode to send all data over the network
         * For small data chunks, this is fine but if there is the need of sending custom
         * data, overwrite this function. In that case, also overwrite the decodeNetworkData() function!
         */
        CompoundTag tag = new CompoundTag();
        save(tag);
        buf.writeNbt(tag);
    }

    /**
     * Networking data decoder for receiving data & settings from the client
     * @param buf The buffer to read the packet data from.
     */
    public void decodeNetworkData(FriendlyByteBuf buf)
    {
        CompoundTag tag = buf.readNbt();
        load(tag);
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     MANAGEMENT
     * --------------------------------------------------------------------------------------------------------------*/
    public void subscribeToMarket(TradingPair tradingPair)
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
        MarketCache marketCache = manager.createCache(tradingPair);
        if(marketCache == null)
        {
            error("Can't subscribe to market: " + tradingPair + " could not create cache");
            return;
        }
        if(cache.putCache(tradingPair, marketCache))
        {
            onMarketSubscribed(tradingPair);
        }
    }
    public void unsubscribeFromMarket(TradingPair tradingPair)
    {
        if(state != State.NONE)
        {
            // todo: create a temp cache to apply these changes after the update loop
            error("Unsubscribing from a market is not allowed from inside the plugin update loop!");
            return;
        }
        if(cache.removeMarketCache(tradingPair))
        {
            onMarketUnsubscribed(tradingPair);
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


    /* ----------------------------------------------------------------------------------------------------------------
     *                     INTERNAL  METHODS
     * --------------------------------------------------------------------------------------------------------------*/

    @Override
    public final void setEnabled(boolean enabled)
    {
        if(enabled == isEnabled())
            return;
        super.setEnabled(enabled);
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


    /*public final void apply_internal(IServerMarketManager manager)
    {
        state  = State.EXEC_APPLY;
        cache.apply(manager);
        state  = State.NONE;
    }*/

    public final void setManager(ServerMarketPluginManager manager)
    {
        this.manager = manager;
    }
    public final ServerMarketPluginManager getManager()
    {
        return manager;
    }

    @Override
    public final boolean save(CompoundTag tag) {
        state  = State.EXEC_SAVE;
        super.save(tag);
        CompoundTag userData = new CompoundTag();
        if(saveData(tag))
            tag.put("UserData", userData);
        state  = State.NONE;
        return true;
    }

    @Override
    public final boolean load(CompoundTag tag) {
        state  = State.EXEC_LOAD;
        if (!super.load(tag))
        {
            state  = State.NONE;
            return false;
        }
        if(tag.contains("UserData")) {
            CompoundTag userData = tag.getCompound("UserData");
            boolean result = loadData(userData);
            state  = State.NONE;
            return result;
        }
        state  = State.NONE;
        return true;
    }

    @Override
    public final void encode(FriendlyByteBuf buf)
    {
        state = State.EXEC_ENCODING;
        encodeNetworkData(buf);
        state = State.NONE;
    }

    @Override
    public final void decode(FriendlyByteBuf buf)
    {
        state = State.EXEC_DECODING;
        decodeNetworkData(buf);
        state = State.NONE;
    }
}
