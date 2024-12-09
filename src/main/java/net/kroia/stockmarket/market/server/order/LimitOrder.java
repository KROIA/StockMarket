package net.kroia.stockmarket.market.server.order;

import com.google.j2objc.annotations.ObjectiveCName;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/*
    * The LimitOrder class represents a spot order.
 */
public class LimitOrder extends Order {
    private int price;

    public LimitOrder(ServerPlayer player, String itemID, int amount, int price) {
        super(player, itemID, amount);
        this.price = price;

        StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
    }

    public LimitOrder(FriendlyByteBuf buf)
    {
        super(buf);
        price = buf.readInt();
    }

    @Override
    boolean isEqual(Order other)
    {
        if(other instanceof LimitOrder)
        {
            LimitOrder otherLimitOrder = (LimitOrder) other;
            return otherLimitOrder.price == price && super.isEqual(other);
        }
        return false;
    }

    public int getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "LimitOrder{ Owner: " + player.getName() + " Amount: " + amount + " Filled: " + filledAmount + " Price: " + price + " AveragePrice: " + averagePrice + " Status:" + status+
                (status==Status.INVALID?" Invalid reason: "+invalidReason:"")+" }";
    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        Type type = Type.LIMIT;
        buf.writeUtf(type.toString());
        super.toBytes(buf);
        buf.writeInt(price);
    }

    @Override
    public void copyFrom(Order other) {
        super.copyFrom(other);
        if(other instanceof LimitOrder)
        {
            LimitOrder otherLimitOrder = (LimitOrder) other;
            this.price = otherLimitOrder.price;
        }
    }


}
