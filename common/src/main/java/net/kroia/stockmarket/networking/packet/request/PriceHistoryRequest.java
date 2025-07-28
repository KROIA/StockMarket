package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.PriceHistoryData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class PriceHistoryRequest extends StockMarketGenericRequest<TradingPairData, PriceHistoryData> {
    @Override
    public String getRequestTypeID() {
        return PriceHistoryRequest.class.getName();
    }

    @Override
    public PriceHistoryData handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public PriceHistoryData handleOnServer(TradingPairData input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getPriceHistoryData(input.toTradingPair());
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf); // Encode the TradingPairData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, PriceHistoryData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the PriceHistoryData
        }
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf); // Decode the TradingPairData
    }

    @Override
    public PriceHistoryData decodeOutput(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return PriceHistoryData.decode(buf); // Decode the PriceHistoryData
        }
        return null; // If no data was encoded, return null
    }
}
