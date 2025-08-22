package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.clientdata.OrderChangeData;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OrderChangeRequest extends StockMarketGenericRequest<OrderChangeData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return OrderChangeRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(OrderChangeData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(OrderChangeData input, ServerPlayer sender) {
        IServerMarket market = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(input.tradingPair.toTradingPair());
        if(market == null) {
            return false; // Market not found
        }
        Order order = market.getOrderBook().getOrder(input.orderID);
        if(order == null) {
            return false; // Order not found
        }
        if(order.getPlayerUUID() != sender.getUUID() && !playerIsAdmin(sender)) {
            return false; // Player does not own the order
        }

        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.handleOrderChangeData(input, sender);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, OrderChangeData input) {
        input.encode(buf); // Encode the OrderChangeData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public OrderChangeData decodeInput(FriendlyByteBuf buf) {
        return OrderChangeData.decode(buf); // Decode the OrderChangeData
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
