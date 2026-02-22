package net.kroia.stockmarket.plugin.networking;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class MarketPluginTypesRequest extends StockMarketGenericRequest<TradingPair, List<String>> {


    @Override
    public String getRequestTypeID() {
        return MarketPluginTypesRequest.class.getSimpleName();
    }

    @Override
    public List<String> handleOnServer(TradingPair input, ServerPlayer sender) {
        if(playerIsAdmin(sender))
            return BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.getPluginTypes(input);
        return List.of();
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<String> output) {
        buf.writeInt(output.size());
        for(String pluginTypeID : output)
        {
            buf.writeUtf(pluginTypeID);
        }
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        return new TradingPair(buf);
    }

    @Override
    public List<String> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> output = new java.util.ArrayList<>(size);
        for(int i = 0; i < size; i++)
        {
            output.add(buf.readUtf());
        }
        return output;
    }
}
