package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class GetRecommendedPriceRequest extends StockMarketGenericRequest<TradingPair, Integer> {
    @Override
    public String getRequestTypeID() {
        return GetRecommendedPriceRequest.class.getName();
    }

    @Override
    public Integer handleOnClient(TradingPair input) {
        return null;
    }

    @Override
    public Integer handleOnServer(TradingPair input, ServerPlayer sender) {
        if(playerIsAdmin(sender))
        {
            return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getRecommendedPrice(input);
        }
        return 0;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        input.encode(buf); // Encode the TradingPair
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Integer output) {
        buf.writeInt(output != null ? output : 0); // Encode the Integer output, defaulting to 0 if null
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        TradingPair pair = new TradingPair();
        pair.decode(buf); // Decode the TradingPair
        return pair;
    }

    @Override
    public Integer decodeOutput(FriendlyByteBuf buf) {
        return buf.readInt(); // Decode the Integer output
    }
}
