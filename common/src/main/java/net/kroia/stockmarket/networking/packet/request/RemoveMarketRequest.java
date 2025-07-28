package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RemoveMarketRequest extends StockMarketGenericRequest<TradingPairData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return RemoveMarketRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            if(input != null) {
                // Attempt to remove the trading pair from the market
                return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeTradeItem(input.toTradingPair());
            }
        }
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
