package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class IsTradingPairAllowedRequest extends StockMarketGenericRequest<TradingPair, Boolean> {
    @Override
    public String getRequestTypeID() {
        return IsTradingPairAllowedRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(TradingPair input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(TradingPair input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.isTradingPairAllowedForTrading(input);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        input.encode(buf); // Encode the TradingPair
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        TradingPair tradingPair = new TradingPair();
        tradingPair.decode(buf); // Decode the TradingPair
        return tradingPair;
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
