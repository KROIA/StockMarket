package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class CreateMarketRequest extends StockMarketGenericRequest<TradingPairData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return CreateMarketRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            // If the player has admin permissions, create the market with the provided settings
            return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.createMarket(input.toTradingPair(), 0);
        }
        // If the player does not have admin permissions, return false
        return false;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf); // Encode the TradingPairData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf); // Decode the TradingPairData
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
