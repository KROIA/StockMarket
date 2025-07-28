package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class OrderReadListData implements INetworkPayloadEncoder {


    public final List<OrderReadData> orders = new ArrayList<>();

    public OrderReadListData(@NotNull List<@NotNull Order> orders) {
        for (Order order : orders) {
            this.orders.add(new OrderReadData(order));
        }
    }
    private OrderReadListData() {
        // For decoding
    }



    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(orders.size());
        for (OrderReadData order : orders) {
            order.encode(buf);
        }
    }

    public static OrderReadListData decode(FriendlyByteBuf buf) {
        OrderReadListData data = new OrderReadListData();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            data.orders.add(OrderReadData.decode(buf));
        }
        return data;
    }
}
