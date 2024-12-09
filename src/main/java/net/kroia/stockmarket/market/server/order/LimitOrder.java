package net.kroia.stockmarket.market.server.order;

import com.google.j2objc.annotations.ObjectiveCName;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/*
    * The LimitOrder class represents a spot order.
 */
public class LimitOrder extends Order implements ServerSaveable {
    private int price;

    public LimitOrder(String playerUUID, String itemID, int amount, int price) {
        super(playerUUID, itemID, amount);
        this.price = price;

        StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
    }
    public LimitOrder(String playerUUID, String itemID, int amount, int price, boolean isBot) {
        super(playerUUID, itemID, amount, isBot);
        this.price = price;

        StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
    }
    public LimitOrder(CompoundTag loadFromTag)
    {
        super();
        load(loadFromTag);
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
        String playerName;
        if(this.isBot) {
            playerName = playerUUID;
        }else {
            ServerPlayer player = StockMarketMod.getPlayerByUUID(playerUUID);
            playerName = player == null ? "UUID:" + playerUUID : player.getName().getString();
        }

        return "LimitOrder{ Owner: " + playerName + " Amount: " + amount + " Filled: " + filledAmount + " Price: " + price + " AveragePrice: " + averagePrice + " Status:" + status+
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


    @Override
    public void save(CompoundTag tag) {
        tag.putLong("orderID", orderID);
        tag.putString("itemID", itemID);
        tag.putString("playerUUID", playerUUID);
        tag.putInt("price", price);
        tag.putInt("amount", amount);
        tag.putInt("filledAmount", filledAmount);
        tag.putInt("averagePrice", averagePrice);
        tag.putString("status", status.toString());
        tag.putString("invalidReason", invalidReason);
        tag.putBoolean("isBot", isBot);
    }

    @Override
    public void load(CompoundTag tag) {
        orderID = tag.getLong("orderID");
        itemID = tag.getString("itemID");
        playerUUID = tag.getString("playerUUID");
        price = tag.getInt("price");
        amount = tag.getInt("amount");
        filledAmount = tag.getInt("filledAmount");
        averagePrice = tag.getInt("averagePrice");
        status = Status.valueOf(tag.getString("status"));
        invalidReason = tag.getString("invalidReason");
        isBot = tag.getBoolean("isBot");
    }


}
