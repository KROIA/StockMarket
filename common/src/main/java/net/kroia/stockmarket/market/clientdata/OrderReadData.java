package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class OrderReadData implements INetworkPayloadEncoder {


    public final long orderID;
    public final UUID playerUUID;
    public final long amount;
    public final long filledAmount;
    public final long transferedMoney;
    public final String invalidReason;
    public final long lockedMoney;
    public final Order.Status status;

    public final Order.Type type;


    // Only for limit orders
    public final int limitRawPrice;
    public final float limitPrice;

    public OrderReadData(@NotNull Order order, int priceScaleFactor)
    {
        this.orderID = order.getOrderID();
        this.playerUUID = order.getPlayerUUID();
        this.amount = order.getAmount();
        this.filledAmount = order.getFilledAmount();
        this.transferedMoney = order.getTransferedMoney();
        this.invalidReason = order.getInvalidReason();
        this.lockedMoney = order.getLockedMoney();
        this.status = order.getStatus();
        this.type = order.getType();

        if(order instanceof LimitOrder limitOrder)
        {
            this.limitRawPrice = limitOrder.getPrice();
            this.limitPrice = limitOrder.getPrice() / (float) priceScaleFactor; // Convert to float for client-side representation
        }
        else
        {
            this.limitRawPrice = 0; // Default value if not a LimitOrder
            this.limitPrice = 0; // Default value if not a LimitOrder
        }
    }

    public OrderReadData(long orderID,
                         @NotNull UUID playerUUID,
                         long amount,
                         long filledAmount,
                         long transferedMoney,
                         String invalidReason,
                         long lockedMoney,
                         Order.Status status,
                         Order.Type type,
                         int limitRawPrice,
                         float limitPrice) {
        this.orderID = orderID;
        this.playerUUID = playerUUID;
        this.amount = amount;
        this.filledAmount = filledAmount;
        this.transferedMoney = transferedMoney;
        this.invalidReason = invalidReason;
        this.lockedMoney = lockedMoney;
        this.status = status;
        this.type = type;
        this.limitRawPrice = limitRawPrice;
        this.limitPrice = limitPrice;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(orderID);
        buf.writeUUID(playerUUID);
        buf.writeLong(amount);
        buf.writeLong(filledAmount);
        buf.writeLong(transferedMoney);
        buf.writeUtf(invalidReason != null ? invalidReason : "");
        buf.writeLong(lockedMoney);
        buf.writeEnum(status);
        buf.writeEnum(type);
        buf.writeInt(limitRawPrice); // Write limit price, 0 if not a LimitOrder
        buf.writeFloat(limitPrice); // Write limit price, 0 if not a LimitOrder
    }

    public static OrderReadData decode(FriendlyByteBuf buf) {
        long orderID = buf.readLong();
        UUID playerUUID = buf.readUUID();
        long amount = buf.readLong();
        long filledAmount = buf.readLong();
        long transferedMoney = buf.readLong();
        String invalidReason = buf.readUtf();
        long lockedMoney = buf.readLong();
        Order.Status status = buf.readEnum(Order.Status.class);
        Order.Type type = buf.readEnum(Order.Type.class);
        int limitRawPrice = buf.readInt(); // Read limit price, 0 if not a LimitOrder
        float limitPrice = buf.readFloat(); // Read limit price, 0 if not a LimitOrder

        return new OrderReadData(orderID, playerUUID, amount, filledAmount, transferedMoney, invalidReason, lockedMoney, status, type, limitRawPrice, limitPrice);
    }


    public boolean isBuy() {
        return amount > 0;
    }
}
