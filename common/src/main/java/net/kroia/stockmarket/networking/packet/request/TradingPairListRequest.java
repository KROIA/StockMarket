package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.TradingPairListData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

// Input Boolean is not needed
public class TradingPairListRequest extends StockMarketGenericRequest<Boolean, TradingPairListData> {
    @Override
    public String getRequestTypeID() {
        return TradingPairListRequest.class.getName();
    }

    @Override
    public TradingPairListData handleOnClient(Boolean input) {
        return null;
    }

    @Override
    public TradingPairListData handleOnServer(Boolean input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradingPairListData();
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Boolean input) {
        buf.writeBoolean(input); // Encode the boolean input
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, TradingPairListData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the TradingPairListData
        }
    }

    @Override
    public Boolean decodeInput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the boolean input
    }

    @Override
    public TradingPairListData decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean() ? TradingPairListData.decode(buf) : null; // Decode the TradingPairListData
    }
}
