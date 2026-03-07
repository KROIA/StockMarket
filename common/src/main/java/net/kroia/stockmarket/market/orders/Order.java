package net.kroia.stockmarket.market.orders;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Order implements ServerSaveable
{
    public enum Type
    {
        MARKET,
        LIMIT,
        INTER_MARKET,
    }

    @NotNull ItemID itemID;     // The ID for which Market the item gets traded

    @Nullable UUID orderExecutor; // The players UUID who executed the order. If null -> bot order
    int bankAccountNr;          // The bank account on which this order gets executed on

    Type type;                  // The type of order

    long targetVolume;          // The amount of items being traded.
                                //   Positive value -> buy order.
                                //   Negative value -> sell order.

    long filledVolume;          // The amount of items that have been transferred from/to the market

    long startPrice;            // The price on which the order has been placed
                                //   For Limit-Orders this is the limit price
                                //   For Market-Orders this is the price the market had when the order has
                                //   been placed

    long time;                  // The timestamp when the order was placed

    long transferredMoney;      // The amount of money that has been transferred while executing the order
                                //   Negative value -> buy order.
                                //   Positive value -> sell order.


    /**
     * ----------------------------------------------
     *          Note to volumes values
     * ----------------------------------------------
     * All volume values are always in respect to the owners bank account.
     * A positive volume means that the value on the players bank account grows.
     * A negative volume means that the value on the players bank account shrinks.
     *
     * - The targetVolume is positive for buy orders
     * - The filledVolume is a value in between [0, targetVolume]
     * - The transferedMoney is negative for buy orders (assuming the price for the item is positive)
     *
     *
     */


    // Player order
    public Order(@NotNull ItemID itemID, @NotNull Type type, long volume, long price, long time, @NotNull UUID orderExecutor, int bankAccountNr)
    {
        this.itemID = itemID;
        this.type = type;
        this.targetVolume = volume;
        this.startPrice = price;
        this.time = time;
        this.orderExecutor = orderExecutor;
        this.bankAccountNr = bankAccountNr;
    }

    // Bot order
    public Order(@NotNull ItemID itemID, @NotNull Type type, long volume, long price, long time) // Bot order
    {
        this.itemID = itemID;
        this.type = type;
        this.targetVolume = volume;
        this.startPrice = price;
        this.time = time;
        this.orderExecutor = null;
        this.bankAccountNr = 0;
    }


    private Order()
    {

    }
    public static Order createFromNBT(CompoundTag tag)
    {
        Order order = new Order();
        if(order.load(tag))
            return order;
        return null;
    }


    public OrderRecordStruct getHistoricalRecord()
    {
        short itemID_raw = itemID.getShort();
        int typeValue = type.ordinal();
        long averageExecPrice = getAverageExecutionPrice();
        OrderRecordStruct recordStruct = new OrderRecordStruct(itemID_raw, bankAccountNr, orderExecutor, typeValue, filledVolume, averageExecPrice, time);
        return recordStruct;
    }

    public @NotNull ItemID getItemID()
    {
        return itemID;
    }
    public @Nullable UUID getExecutorPlayerUUID()
    {
        return orderExecutor;
    }
    public int getBankAccountNr()
    {
        return bankAccountNr;
    }
    public boolean isBotOrder()
    {
        return orderExecutor == null;
    }
    public boolean isPlayerOrder()
    {
        return orderExecutor != null;
    }
    public Type getType()
    {
        return type;
    }
    public long getTargetVolume()
    {
        return targetVolume;
    }
    public long getFilledVolume()
    {
        return filledVolume;
    }
    public long getRemainingVolume()
    {
        return targetVolume - filledVolume;
    }
    public long getStartPrice()
    {
        return startPrice;
    }
    public long getTime()
    {
        return time;
    }
    public long getTransferredMoney()
    {
        return transferredMoney;
    }

    public long getAverageExecutionPrice()
    {
        return -transferredMoney / filledVolume;
    }

    public boolean isBuyOrder()
    {
        return targetVolume > 0;
    }
    public boolean isSellOrder()
    {
        return targetVolume < 0;
    }
    public boolean isFilled()
    {
        return filledVolume == targetVolume;
    }
    public boolean isMarketOrder()
    {
        return type == Type.MARKET;
    }
    public boolean isLimitOrder()
    {
        return type == Type.LIMIT;
    }


    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(itemID.toString());
        sb.append(", ");
        sb.append(type.toString());
        sb.append(", TargetVolume=");
        sb.append(targetVolume);
        sb.append(", FilledVolume=");
        sb.append(filledVolume);
        sb.append(", StartPrice=");
        sb.append(startPrice);
        sb.append(", Time=");
        sb.append(time);
        sb.append(", TransferredMoney=");
        sb.append(transferredMoney);
        return sb.toString();
    }

    /**
     * For saving pending orders
     * @param tag
     * @return true if succeeded
     */
    @Override
    public boolean save(CompoundTag tag) {
        tag.putShort("ItemID", itemID.getShort());
        if(orderExecutor != null)
            tag.putUUID("orderExecutor", orderExecutor);
        tag.putInt("Type", type.ordinal());
        tag.putLong("TargetVolume", targetVolume);
        tag.putLong("FilledVolume", filledVolume);
        tag.putLong("StartPrice", startPrice);
        tag.putLong("Time", time);
        tag.putLong("TransferredMoney", transferredMoney);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;

        if(!tag.contains("ItemID") ||
           !tag.contains("Type") ||
           !tag.contains("TargetVolume") ||
           !tag.contains("FilledVolume") ||
           !tag.contains("StartPrice") ||
           !tag.contains("Time") ||
           !tag.contains("TransferredMoney"))
            return false;

        itemID = new ItemID(tag.getShort("ItemID"));
        if(tag.contains("orderExecutor"))
            orderExecutor = tag.getUUID("orderExecutor");
        else
            orderExecutor = null;
        type = Type.values()[tag.getInt("Type")];
        targetVolume = tag.getLong("TargetVolume");
        filledVolume = tag.getLong("FilledVolume");
        startPrice = tag.getLong("StartPrice");
        time = tag.getLong("Time");
        transferredMoney = tag.getLong("TransferredMoney");
        return true;
    }




    //
    // --------------------------------------------------------------------------------
    //
    //                      Market Internal Functions below
    //                DO NOT USE THEM OUTSIDE THE CORE MARKET CODE!
    //
    // --------------------------------------------------------------------------------
    //



    /**
     * This function is used to edit this order after a fill has taken pace
     * @param addFilledVolume
     * @param addTransferedMoney
     */
    public void edit(long addFilledVolume, long addTransferedMoney)
    {
        filledVolume += addFilledVolume;
        transferredMoney += addTransferedMoney;
    }

    /**
     * In special usecases (Inter-Market-Trades) this access is needed
     * @param newTargetVolume
     */
    public void editTargetVolume(long newTargetVolume)
    {
        targetVolume = newTargetVolume;
    }
}
