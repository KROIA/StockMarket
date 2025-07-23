package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/*
    * The LimitOrder class represents a spot order.
 */
public class LimitOrder extends Order implements ServerSaveable {
    private int price;

    public static LimitOrder create(ServerPlayer player, ItemID itemID, ItemID currencyItemID, int amount, int price)
    {
        if(Order.tryReserveBankFund(player, itemID, amount, price))
            return new LimitOrder(player.getUUID(), itemID, currencyItemID, amount, price);
        return null;
    }
    public static LimitOrder create(ServerPlayer player, ItemID itemID, ItemID currencyItemID, int amount, int price, int alreadyFilledAmount)
    {
        if(Order.tryReserveBankFund(player, itemID, amount-alreadyFilledAmount, price))
            return new LimitOrder(player.getUUID(), itemID, currencyItemID, amount, price, alreadyFilledAmount);
        return null;
    }
    public static LimitOrder createBotOrder(ItemID itemID, ItemID currencyItemID, int amount, int price)
    {
        return new LimitOrder(null, itemID, currencyItemID, amount, price, true);
    }
    protected LimitOrder(UUID playerUUID, ItemID itemID, ItemID currencyItemID, int amount, int price) {
        super(playerUUID, itemID, currencyItemID, amount);
        this.price = price;
        if(amount > 0)
            this.lockedMoney = (long) amount * price;
    }
    protected LimitOrder(UUID playerUUID, ItemID itemID, ItemID currencyItemID, int amount, int price, int alreadyFilledAmount) {
        super(playerUUID, itemID, currencyItemID, amount);
        this.price = price;
        this.filledAmount = alreadyFilledAmount;
        if(amount > 0)
            this.lockedMoney = (long) Math.abs(amount-alreadyFilledAmount) * price;
    }
    protected LimitOrder(UUID playerUUID, ItemID itemID, ItemID currencyItemID, int amount, int price, boolean isBot) {
        super(playerUUID, itemID, currencyItemID, amount, isBot);
        this.price = price;
        if(amount > 0)
            this.lockedMoney = (long) amount * price;
    }
    private LimitOrder()
    {
        super();
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
    public boolean save(CompoundTag tag) {
        tag.putLong("orderID", orderID);
        CompoundTag itemIDTag = new CompoundTag();
        itemID.save(itemIDTag);
        tag.put("itemID", itemIDTag);
        tag.putUUID("playerUUID", playerUUID);
        tag.putInt("price", price);
        tag.putInt("amount", amount);
        tag.putInt("filledAmount", filledAmount);
        tag.putLong("transferedMoney", transferedMoney);
        tag.putString("status", status.toString());
        tag.putString("invalidReason", invalidReason);
        tag.putBoolean("isBot", isBot);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("orderID") ||
                !tag.contains("itemID") ||
                !tag.contains("playerUUID") ||
                !tag.contains("price") ||
                !tag.contains("amount") ||
                !tag.contains("filledAmount") ||
                !tag.contains("transferedMoney") ||
                !tag.contains("status") ||
                !tag.contains("invalidReason") ||
                !tag.contains("isBot"))
            return false;
        orderID = tag.getLong("orderID");

        // Backward compatibility
        String oldItemID = tag.getString("itemID");
        if(oldItemID.compareTo("")==0) {
            CompoundTag itemIDTag = tag.getCompound("itemID");
            itemID = new ItemID(itemIDTag);
        }
        else
            itemID = new ItemID(oldItemID);

        playerUUID = tag.getUUID("playerUUID");
        price = tag.getInt("price");
        amount = tag.getInt("amount");
        filledAmount = tag.getInt("filledAmount");
        transferedMoney = tag.getLong("transferedMoney");
        status = Status.valueOf(tag.getString("status"));
        invalidReason = tag.getString("invalidReason");
        isBot = tag.getBoolean("isBot");
        return true;
    }


}
