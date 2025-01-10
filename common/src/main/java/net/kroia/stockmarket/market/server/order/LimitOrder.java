package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.banking.bank.Bank;
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

    public static LimitOrder create(ServerPlayer player, String itemID, int amount, int price)
    {
        if(Order.tryReserveBankFund(player, itemID, amount, price))
            return new LimitOrder(player.getUUID(), itemID, amount, price);
        return null;
    }
    public static LimitOrder create(ServerPlayer player, String itemID, int amount, int price, int alreadyFilledAmount)
    {
        if(Order.tryReserveBankFund(player, itemID, amount-alreadyFilledAmount, price))
            return new LimitOrder(player.getUUID(), itemID, amount, price, alreadyFilledAmount);
        return null;
    }
    public static LimitOrder createBotOrder(UUID playerUUID, Bank botMoneyBank, Bank botItemBank, String itemID, int amount, int price)
    {
        if(Order.tryReserveBankFund(botMoneyBank, botItemBank, playerUUID, itemID, amount, price, null))
            return new LimitOrder(playerUUID, itemID, amount, price, true);
        return null;
    }
    protected LimitOrder(UUID playerUUID, String itemID, int amount, int price) {
        super(playerUUID, itemID, amount);
        this.price = price;
        if(amount > 0)
            this.lockedMoney = (long) amount * price;

        //StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
    }
    protected LimitOrder(UUID playerUUID, String itemID, int amount, int price, int alreadyFilledAmount) {
        super(playerUUID, itemID, amount);
        this.price = price;
        this.filledAmount = alreadyFilledAmount;
        if(amount > 0)
            this.lockedMoney = (long) Math.abs(amount-alreadyFilledAmount) * price;

        //StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
    }
    protected LimitOrder(UUID playerUUID, String itemID, int amount, int price, boolean isBot) {
        super(playerUUID, itemID, amount, isBot);
        this.price = price;
        if(amount > 0)
            this.lockedMoney = (long) amount * price;


        //StockMarketMod.LOGGER.info("LimitOrder created: " + toString());
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
        tag.putString("itemID", itemID);
        tag.putUUID("playerUUID", playerUUID);
        tag.putInt("price", price);
        tag.putInt("amount", amount);
        tag.putInt("filledAmount", filledAmount);
        tag.putLong("transferedMoney", transferedMoney);
        //tag.putInt("averagePrice", averagePrice);
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
                //!tag.contains("averagePrice") ||
                !tag.contains("status") ||
                !tag.contains("invalidReason") ||
                !tag.contains("isBot"))
            return false;
        orderID = tag.getLong("orderID");
        itemID = tag.getString("itemID");
        playerUUID = tag.getUUID("playerUUID");
        price = tag.getInt("price");
        amount = tag.getInt("amount");
        filledAmount = tag.getInt("filledAmount");
        transferedMoney = tag.getLong("transferedMoney");
        //averagePrice = tag.getInt("averagePrice");
        status = Status.valueOf(tag.getString("status"));
        invalidReason = tag.getString("invalidReason");
        isBot = tag.getBoolean("isBot");
        return true;
    }


}
