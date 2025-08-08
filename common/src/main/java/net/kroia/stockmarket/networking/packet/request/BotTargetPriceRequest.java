package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class BotTargetPriceRequest extends StockMarketGenericRequest<TradingPairData, Float> {
    @Override
    public String getRequestTypeID() {
        return BotTargetPriceRequest.class.getName();
    }

    @Override
    public Float handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public Float handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            if(input != null) {
                // Get the target price for the trading pair
                IServerMarket market = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(input.toTradingPair());
                if(market != null)
                    return market.getBotTargetPriceF();
            }
        }
        return 0.f;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf); // Encode the TradingPairData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Float output) {
        buf.writeFloat(output != null ? output : 0); // Encode the Integer output, defaulting to 0 if null
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf); // Decode the TradingPairData
    }

    @Override
    public Float decodeOutput(FriendlyByteBuf buf) {
        return buf.readFloat(); // Decode the Integer output
    }
}
