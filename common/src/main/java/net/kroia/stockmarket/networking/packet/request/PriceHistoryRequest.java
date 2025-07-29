package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.PriceHistoryData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class PriceHistoryRequest extends StockMarketGenericRequest<PriceHistoryRequest.Input, PriceHistoryData> {

    public static class Input implements INetworkPayloadEncoder
    {
        public final TradingPairData tradingPairData;
        public final int maxHistoryPointCount;

        public Input(TradingPair tradingPair, int maxHistoryPointCount) {
            this.tradingPairData = new TradingPairData(tradingPair);
            this.maxHistoryPointCount = maxHistoryPointCount;
        }
        private Input(TradingPairData tradingPairData, int maxHistoryPointCount) {
            this.tradingPairData = tradingPairData;
            this.maxHistoryPointCount = maxHistoryPointCount;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            tradingPairData.encode(buf); // Encode the TradingPairData
            buf.writeInt(maxHistoryPointCount); // Encode the maximum history point count
        }

        public static Input decode(FriendlyByteBuf buf) {
            TradingPairData tradingPairData = TradingPairData.decode(buf);
            int maxHistoryPointCount = buf.readInt(); // Read the maximum history point count
            return new Input(tradingPairData, maxHistoryPointCount);
        }
    }

    @Override
    public String getRequestTypeID() {
        return PriceHistoryRequest.class.getName();
    }

    @Override
    public PriceHistoryData handleOnClient(Input input) {
        return null;
    }

    @Override
    public PriceHistoryData handleOnServer(Input input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getPriceHistoryData(input.tradingPairData.toTradingPair(), input.maxHistoryPointCount);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Input input) {
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
    public Input decodeInput(FriendlyByteBuf buf) {
        return Input.decode(buf); // Decode the TradingPairData
    }

    @Override
    public PriceHistoryData decodeOutput(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return PriceHistoryData.decode(buf); // Decode the PriceHistoryData
        }
        return null; // If no data was encoded, return null
    }
}
