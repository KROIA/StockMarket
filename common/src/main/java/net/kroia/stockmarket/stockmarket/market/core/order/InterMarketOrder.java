package net.kroia.stockmarket.stockmarket.market.core.order;

import com.ibm.icu.impl.Pair;
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

public class InterMarketOrder implements ServerSaveable
{
    private final Order buyOrder;
    private final Order sellOrder;

    // Cross-rate limit for limit inter-market orders (raw format, 0 = market order)
    private long crossRateLimit;

    // Unique ID linking both legs of this inter-market trade in order history
    private UUID interMarketGroupID;

    // Dollar buffer for bilateral matching: sells deposit here, buys withdraw.
    // On completion/cancellation, remaining balance is deposited to player's money bank.
    private long transactionMoneyBalance = 0;


    public static final StreamCodec<RegistryFriendlyByteBuf, InterMarketOrder> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, InterMarketOrder data) {
            Order.STREAM_CODEC.encode(buf, data.buyOrder);
            Order.STREAM_CODEC.encode(buf, data.sellOrder);
            ByteBufCodecs.VAR_LONG.encode(buf, data.crossRateLimit);
            UUIDUtil.STREAM_CODEC.encode(buf, data.interMarketGroupID);
            ByteBufCodecs.VAR_LONG.encode(buf, data.transactionMoneyBalance);
        }
        @Override
        public @NotNull InterMarketOrder decode(RegistryFriendlyByteBuf buf) {
            Order buyOrder = Order.STREAM_CODEC.decode(buf);
            Order sellOrder = Order.STREAM_CODEC.decode(buf);
            long crossRateLimit = ByteBufCodecs.VAR_LONG.decode(buf);
            UUID interMarketGroupID = UUIDUtil.STREAM_CODEC.decode(buf);
            long transactionMoneyBalance = ByteBufCodecs.VAR_LONG.decode(buf);
            InterMarketOrder result = new InterMarketOrder(buyOrder, sellOrder, crossRateLimit, interMarketGroupID);
            result.transactionMoneyBalance = transactionMoneyBalance;
            return result;
        }
    };


    // Player order (backward-compatible overload, defaults crossRateLimit to 0)
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time, @NotNull UUID ownerID, int bankAccountNr)
    {
        this(buyItemID, sellItemID, type, buyVolume, buyPrice,
             estimatedSellVolume, estimatedSellPrice, time, ownerID, bankAccountNr, 0);
    }

    // Player order with cross-rate limit
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time, @NotNull UUID ownerID, int bankAccountNr,
                            long crossRateLimit)
    {
        buyOrder = new Order(buyItemID, type, Math.abs(buyVolume), buyPrice, time, ownerID, bankAccountNr);
        sellOrder = new Order(sellItemID, type, -Math.abs(estimatedSellVolume), estimatedSellPrice, time, ownerID, bankAccountNr);
        this.crossRateLimit = crossRateLimit;
        this.interMarketGroupID = UUID.randomUUID();
    }

    // Bot order (backward-compatible overload, defaults crossRateLimit to 0)
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time)
    {
        this(buyItemID, sellItemID, type, buyVolume, buyPrice,
             estimatedSellVolume, estimatedSellPrice, time, 0);
    }

    // Bot order with cross-rate limit
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time,
                            long crossRateLimit)
    {
        buyOrder = new Order(buyItemID, type, Math.abs(buyVolume), buyPrice, time);
        sellOrder = new Order(sellItemID, type, -Math.abs(estimatedSellVolume), estimatedSellPrice, time);
        this.crossRateLimit = crossRateLimit;
        this.interMarketGroupID = UUID.randomUUID();
    }


    // Internal constructor used by createFromNBT and STREAM_CODEC
    private InterMarketOrder(Order buyOrder, Order sellOrder, long crossRateLimit, UUID interMarketGroupID)
    {
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.crossRateLimit = crossRateLimit;
        this.interMarketGroupID = interMarketGroupID;
    }

    public static InterMarketOrder createFromNBT(CompoundTag tag)
    {
        if(!tag.contains("buyOrder") || !tag.contains("sellOrder"))
            return null;

        Order buyOrder = Order.createFromNBT(tag.getCompound("buyOrder"));
        Order sellOrder = Order.createFromNBT(tag.getCompound("sellOrder"));
        if(buyOrder == null || sellOrder == null)
        {
            return null;
        }
        // Backward compatibility: missing fields get sensible defaults
        long crossRateLimit = tag.contains("crossRateLimit") ? tag.getLong("crossRateLimit") : 0;
        UUID groupID = tag.contains("interMarketGroupID") ? tag.getUUID("interMarketGroupID") : UUID.randomUUID();
        long transactionMoneyBalance = tag.contains("transactionMoneyBalance") ? tag.getLong("transactionMoneyBalance") : 0;
        InterMarketOrder result = new InterMarketOrder(buyOrder, sellOrder, crossRateLimit, groupID);
        result.transactionMoneyBalance = transactionMoneyBalance;
        return result;
    }

    public Pair<OrderRecordStruct, OrderRecordStruct> getHistoricalRecord()
    {
        OrderRecordStruct buyRecord = new OrderRecordStruct(
                buyOrder.getItemID().getShort(),
                buyOrder.getBankAccountNr(),
                buyOrder.getExecutorPlayerUUID(),
                buyOrder.getType().ordinal(),
                buyOrder.getFilledVolume(),
                buyOrder.getAverageExecutionPrice(),
                buyOrder.getTime(),
                interMarketGroupID);
        OrderRecordStruct sellRecord = new OrderRecordStruct(
                sellOrder.getItemID().getShort(),
                sellOrder.getBankAccountNr(),
                sellOrder.getExecutorPlayerUUID(),
                sellOrder.getType().ordinal(),
                sellOrder.getFilledVolume(),
                sellOrder.getAverageExecutionPrice(),
                sellOrder.getTime(),
                interMarketGroupID);
        return Pair.of(buyRecord, sellRecord);
    }



    public ItemID getBuyItemID()
    {
        return buyOrder.getItemID();
    }
    public ItemID getSellItemID()
    {
        return sellOrder.getItemID();
    }
    public long getTargetBuyVolume()
    {
        return buyOrder.getTargetVolume();
    }

    /**
     * Returns the target sell volume as a positive value (abs of the negative targetVolume on sellOrder).
     * This is the maximum number of items the player wants to sell in this inter-market trade.
     */
    public long getTargetSellVolume()
    {
        return -sellOrder.getTargetVolume();  // sellOrder.targetVolume is negative, negate to get positive
    }

    public long getTime()
    {
        return buyOrder.getTime();
    }


    public boolean isFilled()
    {
        return buyOrder.isFilled();
    }
    public boolean isMarketOrder()
    {
        return buyOrder.isMarketOrder();
    }

    // Returns the cross-rate limit price (raw format, 0 = market order)
    public long getCrossRateLimit() { return crossRateLimit; }

    // Returns the unique ID linking both legs of this inter-market trade
    public UUID getInterMarketGroupID() { return interMarketGroupID; }

    // Returns true if this is a limit inter-market order (crossRateLimit != 0)
    public boolean isLimitOrder() { return crossRateLimit != 0; }

    // Dollar buffer for bilateral matching — sells deposit here, buys withdraw
    public long getTransactionMoneyBalance() { return transactionMoneyBalance; }
    public void setTransactionMoneyBalance(long transactionMoneyBalance) { this.transactionMoneyBalance = transactionMoneyBalance; }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Accessors for InterMarketExecutor — expose inner order details needed
    //  for two-leg execution coordination
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns true if this is a bot order (no player executor on the buy order).
     */
    public boolean isBotOrder()
    {
        return buyOrder.isBotOrder();
    }

    /**
     * Returns the player UUID who placed this inter-market order, or null for bot orders.
     */
    public @Nullable UUID getOwnerUUID()
    {
        return buyOrder.getExecutorPlayerUUID();
    }

    /**
     * Returns the bank account number used for this inter-market order.
     */
    public int getBankAccountNr()
    {
        return buyOrder.getBankAccountNr();
    }

    /**
     * Returns the inner buy Order (want-item leg).
     * Used by InterMarketExecutor to read fill state after execution.
     */
    public Order getBuyOrder()
    {
        return buyOrder;
    }

    /**
     * Returns the inner sell Order (have-item leg).
     * Used by InterMarketExecutor to read fill state after execution.
     */
    public Order getSellOrder()
    {
        return sellOrder;
    }

    /**
     * For saving pending order
     * @param tag
     * @return true if succeeded
     */
    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag buyOrderTag =  new CompoundTag();
        CompoundTag sellOrderTag = new CompoundTag();
        if(buyOrder.save(buyOrderTag) && sellOrder.save(sellOrderTag))
        {
            tag.put("buyOrder", buyOrderTag);
            tag.put("sellOrder", sellOrderTag);
            tag.putLong("crossRateLimit", crossRateLimit);
            tag.putUUID("interMarketGroupID", interMarketGroupID);
            tag.putLong("transactionMoneyBalance", transactionMoneyBalance);
            return true;
        }
        return false;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("buyOrder") ||
           !tag.contains("sellOrder")) {
            StockMarketMod.LOGGER.error("[InterMarketOrder]: Can't load InterMarketOrder from NBT tag: missing required fields");
            return false;
        }

        boolean success = buyOrder.load(tag.getCompound("buyOrder")) &&
                           sellOrder.load(tag.getCompound("sellOrder"));
        if(success)
        {
            // Backward compatibility: missing fields get sensible defaults
            crossRateLimit = tag.contains("crossRateLimit") ? tag.getLong("crossRateLimit") : 0;
            interMarketGroupID = tag.contains("interMarketGroupID") ? tag.getUUID("interMarketGroupID") : UUID.randomUUID();
            transactionMoneyBalance = tag.contains("transactionMoneyBalance") ? tag.getLong("transactionMoneyBalance") : 0;
        }
        return success;
    }
}
