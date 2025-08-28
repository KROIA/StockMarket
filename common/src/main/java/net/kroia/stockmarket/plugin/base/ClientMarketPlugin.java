package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.plugin.networking.MarketPluginNetworkStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public abstract class ClientMarketPlugin extends Plugin{
    private UUID streamID = null;

    public ClientMarketPlugin() {
        super();
    }

    public final void startStream()
    {
        MarketPluginNetworkStream.StartData startData = new MarketPluginNetworkStream.StartData(getTradingPair(), getPluginTypeID());
        streamID = StreamSystem.startServerToClientStream(StockMarketNetworking.MARKET_PLUGIN_NETWORK_STREAM, startData, this::onStreamPacketReceived_internal,
                this::onStreamClosed);
    }


    protected abstract void onStreamPacketReceived(FriendlyByteBuf buf);



    public final void stopStream()
    {
        if(streamID == null)
            return;
        StreamSystem.stopStream(streamID);
    }
    public final boolean isStreamActive()
    {
        return streamID != null;
    }
    protected final void onStreamClosed()
    {
        streamID = null;
    }


    protected abstract ClientMarketPluginGuiElement getSettingsGuiElement();


    @Override
    protected final void update() {

    }
    @Override
    protected final boolean saveToFilesystem(CompoundTag tag) {
        return true;
    }

    @Override
    protected final boolean loadFromFilesystem(CompoundTag tag) {
        return true;
    }


    private void onStreamPacketReceived_internal(FriendlyByteBuf buf)
    {
        if(buf == null)
        {
            error("ClientMarketPlugin: Received null stream packet for plugin: "+this);
        }
        else
        {
            onStreamPacketReceived(buf);
        }
    }
}
