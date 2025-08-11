package net.kroia.stockmarket.market.server.order;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/*
    * The Order class represents a buy or sell order.
 */
public abstract class Order implements ServerSaveable, INetworkPayloadConverter {
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


    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    private static long lastOrderID = 0;



    protected long orderID;
    protected UUID playerUUID;
    protected long amount;
    protected long filledAmount = 0;
    protected long transferedMoney = 0;
    protected String invalidReason = "";
    protected long lockedMoney = 0;
    protected Status status = Status.PENDING;

    protected Order(UUID playerUUID, long amount, long lockedMoney) {
        this.orderID = uniqueOrderID();
        this.playerUUID = playerUUID;
        this.amount = amount;
        this.lockedMoney = lockedMoney;
    }


    // Bot order
    protected Order(long amount) {
        this.orderID = uniqueOrderID();
        this.playerUUID = null;
        this.amount = amount;
        this.lockedMoney = 0;
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
        decode(buf);
    }

    public void copyFrom(Order other)
    {
        orderID = other.orderID;
        playerUUID = other.playerUUID;
        amount = other.amount;
        filledAmount = other.filledAmount;
        lockedMoney = other.lockedMoney;
        transferedMoney = other.transferedMoney;
        status = other.status;
        invalidReason = other.invalidReason;
    }

    public void markAsBot() {
        playerUUID = null;
    }
    public boolean isBot() {
        return playerUUID == null;
    }

    public long getLockedMoney() {
        return lockedMoney;
    }

    public void setLockedMoney(long lockedMoney) {
        this.lockedMoney = lockedMoney;
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
        boolean playerSame = playerUUID == null && other.playerUUID == null;
        if(!playerSame)
        {
            if(playerUUID != null && other.playerUUID != null)
                playerSame = playerUUID.equals(other.playerUUID);
            else
                return false; // One is bot, the other is not
        }

        return  orderID == other.orderID &&
                amount == other.amount &&
                playerSame &&
                filledAmount == other.filledAmount &&
                lockedMoney == other.lockedMoney &&
                transferedMoney == other.transferedMoney &&
                status == other.status;
    }

    public boolean isOwner(@NotNull ServerPlayer player)
    {
        if(playerUUID == null)
            return false;
        return playerUUID.compareTo(player.getUUID()) == 0;
    }
    public boolean isOwner(@NotNull UUID playerUUID)
    {
        if(this.playerUUID == null)
            return false;
        return this.playerUUID.compareTo(playerUUID) == 0;
    }
    public @Nullable UUID getPlayerUUID() {
        return playerUUID;
    }
    public long getOrderID() {
        return orderID;
    }
    public long getTransferedMoney() {
        return transferedMoney;
    }
    public void addTransferedMoney(long money) {
        transferedMoney += money;

    }

    public long getAmount() {
        return amount;
    }
    public long getPendingAmount()
    {
        return amount - filledAmount;
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
        if(!isBot())
            debug("Order processed:\n" + toString());
        setStatus(Status.PROCESSED);
    }
    public void markAsInvalid(String reason) {
        invalidReason = reason;
        if(!isBot()) {
            debug("Order invalid:\n" + toString());

            ServerPlayerUtilities.printToClientConsole(getPlayerUUID(), StockMarketTextMessages.getOrderInvalidMessage(reason));
        }
        setStatus(Status.INVALID);
    }
    public void markAsCancelled() {
        if(!isBot())
            debug("Order canceled:\n" + toString());
        setStatus(Status.CANCELLED);
    }


    public void setStatus(Status status) {
        if(status == this.status)
            return;
        this.status = status;
        if(isBot())
            return;
        //SyncOrderPacket.sendResponse(this);
    }

    public void notifyPlayer() {
        if(isBot())
            return;
        //SyncOrderPacket.sendResponse(this);
    }

    public Status getStatus() {
        return status;
    }

    public int getAveragePrice() {
        return this.filledAmount == 0 ? 0 : (int)(Math.round(Math.abs((double)this.transferedMoney / this.filledAmount)));
    }

    public boolean isFilled() {
        return Math.abs(filledAmount) >= Math.abs(amount);
    }

    public void addFilledAmount(long amount) {
        filledAmount += amount;

    }
    public long getFilledAmount() {
        return filledAmount;
    }

    public Type getType()
    {
        if(this instanceof LimitOrder)
            return Type.LIMIT;
        return Type.MARKET;
    }
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(orderID);
        buf.writeLong(amount);
        buf.writeLong(filledAmount);
        buf.writeLong(lockedMoney);
        buf.writeLong(transferedMoney);
        buf.writeUtf(status.toString());
        buf.writeUtf(invalidReason);
        buf.writeBoolean(playerUUID != null);
        if(playerUUID != null)
            buf.writeUUID(playerUUID);
    }

    @Override
    public void decode(FriendlyByteBuf buf)
    {
        orderID = buf.readLong();
        amount = buf.readLong();
        filledAmount = buf.readLong();
        lockedMoney = buf.readLong();
        transferedMoney = buf.readLong();
        status = Status.valueOf(buf.readUtf());
        invalidReason = buf.readUtf();
        if(buf.readBoolean())
            playerUUID = buf.readUUID();
    }


    @Override
    public boolean save(CompoundTag tag) {
        tag.putLong("orderID", orderID);
        if(playerUUID == null) {
            tag.putBoolean("isBot", true);
        } else {
            tag.putUUID("playerUUID", playerUUID);
        }
        tag.putLong("amount", amount);
        tag.putLong("filledAmount", filledAmount);
        tag.putLong("transferedMoney", transferedMoney);
        tag.putString("status", status.toString());
        tag.putString("invalidReason", invalidReason);

        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("orderID") ||
                (!tag.contains("playerUUID") && !tag.contains("isBot")) ||
                !tag.contains("amount") ||
                !tag.contains("filledAmount") ||
                !tag.contains("transferedMoney") ||
                !tag.contains("status") ||
                !tag.contains("invalidReason"))
            return false;

        orderID = tag.getLong("orderID");
        if(tag.contains("playerUUID"))
            playerUUID = tag.getUUID("playerUUID");
        else
            playerUUID = null; // Bot order, no player UUID

        amount = tag.getLong("amount");
        filledAmount = tag.getLong("filledAmount");
        transferedMoney = tag.getLong("transferedMoney");
        status = Status.valueOf(tag.getString("status"));
        invalidReason = tag.getString("invalidReason");
        return true;
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("orderID", orderID);
        jsonObject.addProperty("playerUUID", playerUUID.toString());
        jsonObject.addProperty("amount", amount);
        jsonObject.addProperty("filledAmount", filledAmount);
        jsonObject.addProperty("transferedMoney", transferedMoney);
        jsonObject.addProperty("invalidReason", invalidReason);
        jsonObject.addProperty("status", status.toString());
        return jsonObject;
    }

    public String toJsonString() {
        return JsonUtilities.toPrettyString(toJson());
    }
    @Override
    public String toString() {
        return toJsonString();
    }



    protected abstract void info(String msg);
    protected abstract void error(String msg);
    protected abstract void error(String msg, Throwable e);
    protected abstract void warn(String msg);
    protected abstract void debug(String msg);
}
