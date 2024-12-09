package net.kroia.stockmarket.market.server.order;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.ResponseOrderPacket;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/*
    * The Order class represents a buy or sell order.
 */
public abstract class Order {
    private static long lastOrderID = 0;

    protected long orderID;
    protected String itemID;
    protected String playerUUID;
    //private final String itemID;
    protected int amount;
    protected int filledAmount = 0;

    protected int averagePrice = 0;

    protected String invalidReason = "";

    public enum Status {
        PENDING,
        PROCESSED,
        PARTIAL,
        CANCELLED,
        INVALID
    }
    public enum Type {
        MARKET,
        LIMIT
    }
    protected Status status = Status.PENDING;

    public Order(String playerUUID, String itemID, int amount) {
        this.itemID = itemID;
        this.orderID = uniqueOrderID();
        this.playerUUID = playerUUID;
        //this.itemID = itemID;
        this.amount = amount;
    }
    protected Order()
    {

    }

    public static Order construct(FriendlyByteBuf buf)
    {
        Type type = Type.valueOf(buf.readUtf());
        return switch (type) {
            case MARKET -> new MarketOrder(buf);
            case LIMIT -> new LimitOrder(buf);
            default -> throw new IllegalArgumentException("Invalid order type");
        };
    }
    protected Order(FriendlyByteBuf buf)
    {
        orderID = buf.readLong();
        itemID = buf.readUtf();
        playerUUID = buf.readUtf();
        amount = buf.readInt();
        filledAmount = buf.readInt();
        averagePrice = buf.readInt();
        status = Status.valueOf(buf.readUtf());
        invalidReason = buf.readUtf();
    }

    public void copyFrom(Order other)
    {
        orderID = other.orderID;
        itemID = other.itemID;
        playerUUID = other.playerUUID;
        amount = other.amount;
        filledAmount = other.filledAmount;
        averagePrice = other.averagePrice;
        status = other.status;
        invalidReason = other.invalidReason;
    }

    public static long uniqueOrderID()
    {
        long oderID = System.currentTimeMillis();
        if(oderID <= lastOrderID)
        {
            oderID = lastOrderID + 1;
            while(oderID <= lastOrderID)
            {
                oderID++;
            }
        }
        lastOrderID = oderID;
        return oderID;
    }

    boolean isEqual(Order other)
    {
        return  orderID == other.orderID &&
                itemID.equals(other.itemID) &&
                playerUUID.compareTo(other.playerUUID)==0 &&
                amount == other.amount &&
                filledAmount == other.filledAmount &&
                averagePrice == other.averagePrice &&
                status == other.status;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }
    public long getOrderID() {
        return orderID;
    }
    public String getItemID() {
        return itemID;
    }

    public int getAmount() {
        return amount;
    }
    public String getInvalidReason() {
        return invalidReason;
    }

    public boolean isBuy() {
        return amount > 0;
    }
    public boolean isSell() {
        return amount < 0;
    }

    public void markAsProcessed() {
        StockMarketMod.LOGGER.info("Order processed: " + toString());
        setStatus(Status.PROCESSED);
    }
    public void markAsInvalid(String reason) {
        invalidReason = reason;
        StockMarketMod.LOGGER.info("Order invalid: " + toString());
        setStatus(Status.INVALID);
    }
    public void markAsCancelled() {
        StockMarketMod.LOGGER.info("Order canceled: " + toString());
        setStatus(Status.CANCELLED);
    }

    private void setStatus(Status status) {
        if(status == this.status)
            return;
        this.status = status;
        if(StockMarketMod.isServer())
        {
            ResponseOrderPacket.sendResponse(this);
        }
    }

    public void notifyPlayer() {
        if(StockMarketMod.isServer())
        {
            ResponseOrderPacket.sendResponse(this);
        }
        else
        {
            StockMarketMod.LOGGER.error("notifyPlayer() called on client");
        }
    }

    public Status getStatus() {
        return status;
    }

    public int getAveragePrice() {
        return averagePrice;
    }
    public void setAveragePrice(int averagePrice) {
        this.averagePrice = averagePrice;
    }
    public void changeAveragePrice(int filledAmount, int fillPrice) {
        if(filledAmount == 0)
            return;
        averagePrice = (Math.abs(this.filledAmount) * averagePrice + Math.abs(filledAmount) * fillPrice) / Math.abs(this.amount);
    }

    /**
     * Fills the order with the given amount.
     * @param amount The amount to fill the order with.
     * @return The remaining amount that could not be filled.
     */
    public int fill(int amount)
    {
        int fillAmount = this.amount - filledAmount;
        if(Math.abs(fillAmount) > Math.abs(amount))
            fillAmount = amount;
        filledAmount += fillAmount;
        if(filledAmount != 0 && status == Status.PENDING)
        {
            setStatus(Status.PARTIAL);
        }

        if(isFilled())
        {
            markAsProcessed();
        }
        return amount - fillAmount;
    }

    public boolean isFilled() {
        return Math.abs(filledAmount) >= Math.abs(amount);
    }

    public int getFilledAmount() {
        return filledAmount;
    }

    // pure virtual function
    public abstract String toString();

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(orderID);
        buf.writeUtf(itemID);
        buf.writeUtf(playerUUID);
        buf.writeInt(amount);
        buf.writeInt(filledAmount);
        buf.writeInt(averagePrice);
        buf.writeUtf(status.toString());
        buf.writeUtf(invalidReason);
    }

}
