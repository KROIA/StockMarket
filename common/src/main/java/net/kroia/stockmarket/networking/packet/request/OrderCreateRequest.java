package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.OrderCreateData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OrderCreateRequest extends StockMarketGenericRequest<OrderCreateData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return OrderCreateRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(OrderCreateData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(OrderCreateData input, ServerPlayer sender) {
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handleOrderCreateData(input, sender);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, OrderCreateData input) {
        input.encode(buf); // Encode the OrderCreateData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public OrderCreateData decodeInput(FriendlyByteBuf buf) {
        return OrderCreateData.decode(buf); // Decode the OrderCreateData
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
