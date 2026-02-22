package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.OrderCancelData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OrderCancelRequest extends StockMarketGenericRequest<OrderCancelData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return OrderCancelRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(OrderCancelData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(OrderCancelData input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.handleOrderCancelData(input, sender);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, OrderCancelData input) {
        input.encode(buf); // Encode the OrderCancelData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public OrderCancelData decodeInput(FriendlyByteBuf buf) {
        return OrderCancelData.decode(buf); // Decode the OrderCancelData
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
