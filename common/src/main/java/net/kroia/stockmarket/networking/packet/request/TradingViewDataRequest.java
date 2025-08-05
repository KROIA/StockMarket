package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.clientdata.TradingViewData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class TradingViewDataRequest extends StockMarketGenericRequest<TradingViewDataRequest.Input, TradingViewData> {

    public static class Input implements INetworkPayloadEncoder
    {
        public final TradingPairData tradingPairData;
        public final int maxHistoryPointCount;
        public final int minVisiblePrice;
        public final int maxVisiblePrice;
        public final int orderBookTileCount;
        public final boolean requestBotTargetPrice;

        public Input(TradingPair pair, int maxHistoryPointCount, int minVisiblePrice, int maxVisiblePrice, int orderBookTileCount, boolean requestBotTargetPrice) {
            this.tradingPairData = new TradingPairData(pair);
            this.maxHistoryPointCount = maxHistoryPointCount;
            this.minVisiblePrice = minVisiblePrice;
            this.maxVisiblePrice = maxVisiblePrice;
            this.orderBookTileCount = orderBookTileCount;
            this.requestBotTargetPrice = requestBotTargetPrice;
        }
        public Input(TradingPair pair, boolean requestBotTargetPrice) {
            this.tradingPairData = new TradingPairData(pair);
            this.maxHistoryPointCount = -1; // Default value, can be set later
            this.minVisiblePrice = 0;
            this.maxVisiblePrice = 0;
            this.orderBookTileCount = 0;
            this.requestBotTargetPrice = requestBotTargetPrice; // Default value, can be set later
        }
        private Input(TradingPairData tradingPairData, int maxHistoryPointCount, int minVisiblePrice, int maxVisiblePrice, int orderBookTileCount, boolean requestBotTargetPrice) {
            this.tradingPairData = tradingPairData;
            this.maxHistoryPointCount = maxHistoryPointCount;
            this.minVisiblePrice = minVisiblePrice;
            this.maxVisiblePrice = maxVisiblePrice;
            this.orderBookTileCount = orderBookTileCount;
            this.requestBotTargetPrice = requestBotTargetPrice;
        }

        public TradingPairData getTradingPairData() {
            return tradingPairData;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            tradingPairData.encode(buf);
            buf.writeInt(maxHistoryPointCount);
            buf.writeInt(minVisiblePrice);
            buf.writeInt(maxVisiblePrice);
            buf.writeInt(orderBookTileCount);
            buf.writeBoolean(requestBotTargetPrice);
        }

        public static Input decode(FriendlyByteBuf buf) {
            TradingPairData tradingPairData = TradingPairData.decode(buf);
            int maxHistoryPointCount = buf.readInt();
            int minVisiblePrice = buf.readInt();
            int maxVisiblePrice = buf.readInt();
            int orderBookTileCount = buf.readInt();
            boolean requestBotTargetPrice = buf.readBoolean();
            return new Input(tradingPairData, maxHistoryPointCount, minVisiblePrice, maxVisiblePrice, orderBookTileCount, requestBotTargetPrice);
        }
    }


    @Override
    public String getRequestTypeID() {
        return TradingViewDataRequest.class.getName();
    }

    @Override
    public TradingViewData handleOnClient(Input input) {
        return null;
    }

    @Override
    public TradingViewData handleOnServer(Input input, ServerPlayer sender) {
        boolean requestBotTargetPrice = input.requestBotTargetPrice && playerIsAdmin(sender);
        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getTradingViewData(
                input.tradingPairData.toTradingPair(),
                sender.getUUID(),
                input.maxHistoryPointCount,
                input.minVisiblePrice,
                input.maxVisiblePrice,
                input.orderBookTileCount,
                requestBotTargetPrice);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Input input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, TradingViewData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the TradingViewData
        }
    }

    @Override
    public Input decodeInput(FriendlyByteBuf buf) {
        return Input.decode(buf); // Decode the TradingViewDataRequest.Input
    }

    @Override
    public TradingViewData decodeOutput(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return TradingViewData.decode(buf); // Decode the TradingViewData
        }
        return null; // If no data was encoded, return null
    }
}
