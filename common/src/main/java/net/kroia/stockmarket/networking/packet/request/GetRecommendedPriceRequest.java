package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class GetRecommendedPriceRequest extends StockMarketGenericRequest<TradingPair, Float> {
    @Override
    public String getRequestTypeID() {
        return GetRecommendedPriceRequest.class.getName();
    }

    @Override
    public Float handleOnClient(TradingPair input) {
        return null;
    }

    @Override
    public Float handleOnServer(TradingPair input, ServerPlayer sender) {
        if(playerIsAdmin(sender))
        {
            return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getRecommendedPrice(input);
        }
        return 0.f;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        input.encode(buf); // Encode the TradingPair
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Float output) {
        buf.writeFloat(output != null ? output : 0); // Encode the Integer output, defaulting to 0 if null
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        TradingPair pair = new TradingPair();
        pair.decode(buf); // Decode the TradingPair
        return pair;
    }

    @Override
    public Float decodeOutput(FriendlyByteBuf buf) {
        return buf.readFloat(); // Decode the Integer output
    }
}
