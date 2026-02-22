package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class UpdateUsedMarketPluginsRequest extends StockMarketGenericRequest<UpdateUsedMarketPluginsRequest.SenderData, Boolean> {

    public static class SenderData implements INetworkPayloadConverter
    {
        public TradingPair tradingPair = null;
        public List<String> usedPlugins = null; // Plugin ID's



        @Override
        public void encode(FriendlyByteBuf buf) {
            tradingPair.encode(buf);
            buf.writeInt(usedPlugins.size());
            for(String pluginTypeID : usedPlugins)
            {
                buf.writeUtf(pluginTypeID);
            }
        }
        @Override
        public void decode(FriendlyByteBuf buf) {
            if(tradingPair == null)
                tradingPair = new TradingPair();
            tradingPair.decode(buf);
            int size = buf.readInt();
            usedPlugins = new java.util.ArrayList<>(size);
            for(int i = 0; i < size; i++)
            {
                usedPlugins.add(buf.readUtf());
            }
        }
    }

    @Override
    public String getRequestTypeID() {
        return UpdateUsedMarketPluginsRequest.class.getSimpleName();
    }

    @Override
    public Boolean handleOnServer(SenderData input, ServerPlayer sender)
    {
        if(!playerIsAdmin(sender))
            return false;

        BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.setUsedMarketPlugins(input.tradingPair, input.usedPlugins);
        return true;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, SenderData input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output);
    }

    @Override
    public SenderData decodeInput(FriendlyByteBuf buf) {
        SenderData  senderData = new SenderData();
        senderData.decode(buf);
        return senderData;
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }
}
