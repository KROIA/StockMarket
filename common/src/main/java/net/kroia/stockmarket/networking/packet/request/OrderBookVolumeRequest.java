package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.OrderBookVolumeData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OrderBookVolumeRequest extends StockMarketGenericRequest<TradingViewDataRequest.Input, OrderBookVolumeData> {

    @Override
    public String getRequestTypeID() {
        return OrderBookVolumeRequest.class.getName();
    }

    @Override
    public OrderBookVolumeData handleOnClient(TradingViewDataRequest.Input input) {
        return null;
    }

    @Override
    public OrderBookVolumeData handleOnServer(TradingViewDataRequest.Input input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getOrderBookVolumeData(input.tradingPairData.toTradingPair(),
                    input.maxHistoryPointCount, input.minVisiblePrice, input.maxVisiblePrice, input.orderBookTileCount);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingViewDataRequest.Input input) {
        input.encode(buf); // Encode the TradingViewDataRequest.Input
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, OrderBookVolumeData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the OrderBookVolumeData
        }
    }

    @Override
    public TradingViewDataRequest.Input decodeInput(FriendlyByteBuf buf) {
        return TradingViewDataRequest.Input.decode(buf); // Decode the TradingViewDataRequest.Input
    }

    @Override
    public OrderBookVolumeData decodeOutput(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return OrderBookVolumeData.decode(buf); // Decode the OrderBookVolumeData
        }
        return null; // If no data was encoded, return null
    }
}
