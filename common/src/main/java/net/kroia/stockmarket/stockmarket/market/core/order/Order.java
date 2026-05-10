package net.kroia.stockmarket.stockmarket.market.core.order;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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

    //private static long orderIDCounter = System.currentTimeMillis();


    //long orderID;               // The ID for this individual order instance
    @NotNull ItemID itemID;     // The ID for which ServerMarket the item gets traded

    @Nullable UUID orderExecutor; // The players UUID who executed the order. If null -> bot order
    int bankAccountNr;          // The bank account on which this order gets executed on

    Type type;                  // The type of order

    long targetVolume;          // The amount of items being traded.
                                //   Positive value -> buy order.
                                //   Negative value -> sell order.

    long filledVolume;          // The amount of items that have been transferred from/to the stockmarket

    long startPrice;            // The price on which the order has been placed
                                //   For Limit-Orders this is the limit price
                                //   For ServerMarket-Orders this is the price the stockmarket had when the order has
                                //   been placed

    long time;                  // The timestamp when the order was placed

    long transferredMoney;      // The amount of money that has been transferred while executing the order
                                //   Negative value -> buy order.
                                //   Positive value -> sell order.


    /**
     * ----------------------------------------------
     *          Note to volume values
     * ----------------------------------------------
     * All volume values are always in respect to the owners bank account.
     * A positive volume means that the value on the players bank account grows.
     * A negative volume means that the value on the players bank account shrinks.
     *
     * - The targetVolume is positive for buy order
     * - The filledVolume is a value in between [0, targetVolume]
     * - The transferredMoney is negative for buy order (assuming the price for the item is positive)
     *
     *
     */


    public static final StreamCodec<RegistryFriendlyByteBuf, Order> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, Order data) {
            //ByteBufCodecs.VAR_LONG.encode(buf, data.getOrderID());
            ItemID.STREAM_CODEC.encode(buf, data.getItemID());
            ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC).encode(buf, data.getExecutorPlayerUUID());
            ByteBufCodecs.INT.encode(buf, data.getBankAccountNr());
            ExtraCodecUtils.enumStreamCodec(Type.class).encode(buf, data.getType());
            ByteBufCodecs.VAR_LONG.encode(buf, data.getTargetVolume());
            ByteBufCodecs.VAR_LONG.encode(buf, data.getStartPrice());
            ByteBufCodecs.VAR_LONG.encode(buf, data.getFilledVolume());
            ByteBufCodecs.VAR_LONG.encode(buf, data.getTransferredMoney());
            ByteBufCodecs.VAR_LONG.encode(buf, data.getTime());
        }
        @Override
        public @NotNull Order decode(RegistryFriendlyByteBuf buf) {
            //long orderID = buf.readLong();
            ItemID itemID = ItemID.STREAM_CODEC.decode(buf);
            @Nullable UUID orderExecutor = ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC).decode(buf);
            int bankAccountNr = ByteBufCodecs.INT.decode(buf);
            Type type = ExtraCodecUtils.enumStreamCodec(Type.class).decode(buf);
            long volume = ByteBufCodecs.VAR_LONG.decode(buf);
            long price = ByteBufCodecs.VAR_LONG.decode(buf);
            long filledVolume = ByteBufCodecs.VAR_LONG.decode(buf);
            long transferredMoney = ByteBufCodecs.VAR_LONG.decode(buf);
            long time = ByteBufCodecs.VAR_LONG.decode(buf);
            return new Order(/*orderID, */itemID, orderExecutor, bankAccountNr, type, volume, filledVolume, price, time, transferredMoney);
        }
    };
   /* public record Data(ItemID itemID, UUID orderExecutor, int bankAccountNr, Type type, long volume, long price,
                       long filledVolume, long transferredMoney, long time)
    {

    }*/

    // Player order
    public Order(@NotNull ItemID itemID, @NotNull Type type, long volume, long price, long time, @NotNull UUID orderExecutor, int bankAccountNr)
    {
        this(/*orderIDCounter++, */itemID, orderExecutor, bankAccountNr, type, volume,0,price,time,0);
    }

    // Bot order
    public Order(@NotNull ItemID itemID, @NotNull Type type, long volume, long price, long time) // Bot order
    {
        this(/*orderIDCounter++,*/ itemID, null, 0, type, volume,0,price,time,0);
    }
    private Order(/*long orderID, */@NotNull ItemID itemID, @Nullable UUID orderExecutor, int bankAccountNr, Type type, long targetVolume, long filledVolume, long startPrice, long time, long transferredMoney )
    {
        //this.orderID = orderID;
        this.itemID = itemID;
        this.type = type;
        this.targetVolume = targetVolume;
        this.filledVolume = filledVolume;
        this.startPrice = startPrice;
        this.time = time;
        this.orderExecutor = orderExecutor;
        this.bankAccountNr = bankAccountNr;
        this.transferredMoney = transferredMoney;
    }



    private Order()
    {
        itemID = null;
    }

    /*public Order.Data  getData()
    {
        return new Data(itemID, orderExecutor, bankAccountNr, type, targetVolume,
                startPrice, filledVolume, transferredMoney, time);
    }*/

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
        OrderRecordStruct recordStruct = new OrderRecordStruct(/*orderID, */itemID_raw, bankAccountNr, orderExecutor, typeValue, filledVolume, averageExecPrice, time);
        return recordStruct;
    }

    /*public long getOrderID()
    {
        return orderID;
    }*/
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
        if (filledVolume == 0) return 0;
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
     * For saving pending order
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
        if(tag == null) {
            StockMarketMod.LOGGER.error("[Order]: Can't load Order from NBT tag: tag is null");
            return false;
        }

        if(!tag.contains("ItemID") ||
           !tag.contains("Type") ||
           !tag.contains("TargetVolume") ||
           !tag.contains("FilledVolume") ||
           !tag.contains("StartPrice") ||
           !tag.contains("Time") ||
           !tag.contains("TransferredMoney")) {
            StockMarketMod.LOGGER.error("[Order]: Can't load Order from NBT tag: missing required fields");
            return false;
        }

        itemID = new ItemID(tag.getShort("ItemID"));
        if(tag.contains("orderExecutor"))
            orderExecutor = tag.getUUID("orderExecutor");
        else
            orderExecutor = null;
        int typeOrdinal = tag.getInt("Type");
        if (typeOrdinal < 0 || typeOrdinal >= Type.values().length)
            return false;
        type = Type.values()[typeOrdinal];
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
    //                      ServerMarket Internal Functions below
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
     * In special usecases (Inter-ServerMarket-Trades) this access is needed
     * @param newTargetVolume
     */
    public void editTargetVolume(long newTargetVolume)
    {
        targetVolume = newTargetVolume;
    }
}
