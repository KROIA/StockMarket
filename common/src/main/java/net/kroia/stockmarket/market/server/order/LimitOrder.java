package net.kroia.stockmarket.market.server.order;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/*
    * The LimitOrder class represents a spot order.
 */
public class LimitOrder extends Order implements ServerSaveable {
    private int price;


    LimitOrder(UUID playerUUID, int bankAccountNumber, long amount, int price, long lockedMoney) {
        super(playerUUID, bankAccountNumber, amount, lockedMoney);
        this.price = price;
    }
    LimitOrder(UUID playerUUID, int bankAccountNumber, long amount, int price, long lockedMoney, long alreadyFilledAmount) {
        super(playerUUID, bankAccountNumber, amount, lockedMoney);
        this.price = price;
        this.filledAmount = alreadyFilledAmount;
    }
    LimitOrder(long amount, int price) {
        super(amount);
        this.price = price;
    }
    private LimitOrder()
    {
        super();
    }

    public void setPrice(int price) {
        this.price = price;
    }
    public static LimitOrder loadFromTag(CompoundTag tag)
    {
        LimitOrder order = new LimitOrder();
        if(order.load(tag))
            return order;
        return null;
    }

    public LimitOrder(FriendlyByteBuf buf)
    {
        super(buf);
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
        String playerName = ServerPlayerList.getPlayerName(playerUUID);
        if(playerName == null || playerName.isEmpty())
            playerName = playerUUID.toString();

        return "LimitOrder{\n  Owner: " + playerName +
                "\n  OrderID: " + orderID +
                "\n  Amount: " + amount +
                "\n  Filled: " + filledAmount +
                "\n  Price: " + price +
                "\n  AveragePrice: " + getAveragePrice() +
                "\n  TransferedMoney: $" + transferedMoney +
                "\n  Status:" + status+
                (status==Status.INVALID?" Invalid reason: \n    "+invalidReason:"")+"\n}";
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        Type type = Type.LIMIT;
        buf.writeUtf(type.toString());
        super.encode(buf);
        buf.writeInt(price);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        super.decode(buf);
        price = buf.readInt();
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
    public boolean save(CompoundTag tag) {
        if(!super.save(tag))
            return false;
        tag.putInt("price", price);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!super.load(tag))
            return false;


        if(tag == null)
            return false;
        if(!tag.contains("price"))
            return false;

        price = tag.getInt("price");
        return true;
    }


    @Override
    public JsonElement toJson() {
        JsonObject json = (JsonObject) super.toJson();
        json.addProperty("type", Type.LIMIT.toString());
        json.addProperty("price", price);
        return json;
    }


    @Override
    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[LimitOrder] " + msg);
    }
    @Override
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[LimitOrder] " + msg);
    }
    @Override
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[LimitOrder] " + msg, e);
    }
    @Override
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[LimitOrder] " + msg);
    }
    @Override
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[LimitOrder] " + msg);
    }
}
