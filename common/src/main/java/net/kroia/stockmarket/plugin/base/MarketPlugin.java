package net.kroia.stockmarket.plugin.base;

import net.minecraft.network.FriendlyByteBuf;

public abstract class MarketPlugin extends Plugin {


    protected IMarketPluginInterface pluginInterface = null;
    public MarketPlugin() {
        super();
    }
    public final void setInterface(IMarketPluginInterface market)
    {
        this.pluginInterface = market;
        this.setTradingPair(market.getTradingPair());
    }

    public IMarketPluginInterface getPluginInterface() {
        return pluginInterface;
    }


    /**
     * Encode data tha the client needs when starting a stream
     * @param buf The buffer to write to
     */
    public abstract void encodeClientStreamData(FriendlyByteBuf buf);

    /*public static MarketPlugin createFromBuf(FriendlyByteBuf buf)
    {
        String typeID = buf.readUtf();
        MarketPlugin plugin = PluginRegistry.createServerPluginInstance(typeID);
        if(plugin == null)
        {
            BACKEND_INSTANCES.LOGGER.error("Failed to create MarketPlugin instance from buffer: unknown type ID " + typeID+
                    ".\nMake sure the plugin is registered on both client and server.");
            return null;
        }
        try{
            plugin.decode(buf);
        }catch(Exception e)
        {
            BACKEND_INSTANCES.LOGGER.error("Failed to decode MarketPlugin instance from buffer: " + typeID, e);
            return null;
        }
        return plugin;
    }*/





















}
