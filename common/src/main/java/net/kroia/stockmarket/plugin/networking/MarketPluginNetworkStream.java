package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.FriendlyByteBuf;

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


    private static final int updateInterval = 20; //ticks
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

    }

    @Override
    public void onStopStreamSendingOnServer() {

    }

    @Override
    protected void updateOnServer(){
        tickCounter++;
        if(tickCounter >= updateInterval)
        {
            tickCounter = 0;
            sendPacket();
        }
    }

    @Override
    public FriendlyByteBuf provideStreamPacketOnServer()  {
        StartData context = getContextData();
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        if(BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.encodeClientStreamData(context.tradingPair, context.pluginTypeID, buf))
            return buf;
        return null;
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
