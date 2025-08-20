package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class OrderReadDataRequest extends StockMarketGenericRequest<OrderReadDataRequest.Input, List<OrderReadData>> {
    public static class Input implements INetworkPayloadEncoder
    {
        public final TradingPair tradingPair;
        public final float startPrice;
        public final float endPrice;

        public Input(TradingPair tradingPair, float startPrice, float endPrice) {
            this.tradingPair = tradingPair;
            this.startPrice = startPrice;
            this.endPrice = endPrice;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            tradingPair.encode(buf);
            buf.writeFloat(startPrice);
            buf.writeFloat(endPrice);
        }

        public static Input decode(FriendlyByteBuf buf) {
            TradingPair tradingPair = new TradingPair(buf);
            float startPrice = buf.readFloat();
            float endPrice = buf.readFloat();
            return new Input(tradingPair, startPrice, endPrice);
        }
    }


    @Override
    public String getRequestTypeID() {
        return OrderReadDataRequest.class.getName();
    }

    @Override
    public List<OrderReadData> handleOnClient(Input input) {
        return null;
    }

    @Override
    public List<OrderReadData> handleOnServer(Input input, ServerPlayer sender) {
        List<OrderReadData> orders = new ArrayList<>();
        IServerMarket market = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(input.tradingPair);
        if(market == null)
            return orders;
        var rawOrders = market.getOrderBook().getOrders();
        int priceScaleFactor = market.getPriceScaleFactor();
        int rawStartPrice = market.mapToRawPrice(input.startPrice);
        int rawEndPrice = market.mapToRawPrice(input.endPrice);
        boolean getAllOrders = input.endPrice < 0;
        if(getAllOrders) {
            for (var order : rawOrders) {
                orders.add(new OrderReadData(order, priceScaleFactor));
            }
        }
        else {
            for (var order : rawOrders) {
                if (order instanceof LimitOrder limitOrder) {
                    if (limitOrder.getPrice() >= rawStartPrice && limitOrder.getPrice() <= rawEndPrice) {
                        orders.add(new OrderReadData(order, priceScaleFactor));
                    }
                }
            }
        }
        return orders;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Input input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<OrderReadData> output) {
        buf.writeInt(output.size());
        for (OrderReadData order : output) {
            order.encode(buf);
        }
    }

    @Override
    public Input decodeInput(FriendlyByteBuf buf) {
        return Input.decode(buf);
    }

    @Override
    public List<OrderReadData> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<OrderReadData> orders = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            orders.add(OrderReadData.decode(buf));
        }
        return orders;
    }




}
