package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class MarketPluginNetworkStream extends StockMarketGenericStream<MarketPluginNetworkStream.StartData, FriendlyByteBuf> {

    public static class StartData
    {
        TradingPair tradingPair;
        String pluginTypeID;
        public StartData(TradingPair tradingPair, String pluginTypeID)
        {
            this.tradingPair = tradingPair;
            this.pluginTypeID = pluginTypeID;
        }
    }


    ServerPluginManager.MarketPluginInstanceData instanceData = null;
    private int tickCounter = 0;

    @Override
    public GenericStream<StartData, FriendlyByteBuf> copy() {
        return new MarketPluginNetworkStream();
    }

    @Override
    public String getStreamTypeID() {
        return MarketPluginNetworkStream.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        instanceData = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.getMarketPluginInstanceData(getContextData().tradingPair, getContextData().pluginTypeID);
    }

    @Override
    public void onStopStreamSendingOnServer() {

    }

    @Override
    protected void updateOnServer(){
        tickCounter++;
        if(instanceData == null || instanceData.plugin == null)
        {
            instanceData = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.getMarketPluginInstanceData(getContextData().tradingPair, getContextData().pluginTypeID);
            if(instanceData == null || instanceData.plugin == null)
            {
                return;
            }
        }
        if(tickCounter >= instanceData.streamPacketSendTickInterval)
        {
            tickCounter = 0;
            ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(getRequestorPlayerUUID());
            if(player == null) {
                stopStream();
                return;
            }
            if(playerIsAdmin(player))
                sendPacket();
        }
    }

    @Override
    public FriendlyByteBuf provideStreamPacketOnServer()  {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        instanceData.plugin.encodeClientStreamData(buf);
        return buf;
    }

    @Override
    public void encodeContextData(FriendlyByteBuf buffer, StartData context) {
        context.tradingPair.encode(buffer);
        buffer.writeUtf(context.pluginTypeID);
    }

    @Override
    public StartData decodeContextData(FriendlyByteBuf buffer) {
        TradingPair tradingPair = new TradingPair(buffer);
        String pluginTypeID = buffer.readUtf();
        return  new StartData(tradingPair, pluginTypeID);
    }

    @Override
    public void encodeData(FriendlyByteBuf buffer, FriendlyByteBuf buf) {
        buffer.writeBoolean(buf != null);
        if(buf != null)
            buffer.writeBytes(buf);
    }

    @Override
    public FriendlyByteBuf decodeData(FriendlyByteBuf buffer) {
        if(buffer.readBoolean())
        {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeBytes(buffer);
            return buf;
        }
        return null;
    }



}
