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


    public static final StreamCodec<RegistryFriendlyByteBuf, InterMarketOrder> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, InterMarketOrder data) {
            Order.STREAM_CODEC.encode(buf, data.buyOrder);
            Order.STREAM_CODEC.encode(buf, data.sellOrder);
        }
        @Override
        public @NotNull InterMarketOrder decode(RegistryFriendlyByteBuf buf) {
            Order buyOrder = Order.STREAM_CODEC.decode(buf);
            Order sellOrder = Order.STREAM_CODEC.decode(buf);
            return new InterMarketOrder(buyOrder, sellOrder);
        }
    };


    // Player order
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time, @NotNull UUID ownerID, int bankAccountNr)
    {
        buyOrder = new Order(buyItemID, type, Math.abs(buyVolume), buyPrice, time, ownerID, bankAccountNr);
        sellOrder = new Order(sellItemID, type, -Math.abs(estimatedSellVolume), estimatedSellPrice, time, ownerID, bankAccountNr);
    }

    // Bot order
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time)
    {
        buyOrder = new Order(buyItemID, type, Math.abs(buyVolume), buyPrice, time);
        sellOrder = new Order(sellItemID, type, -Math.abs(estimatedSellVolume), estimatedSellPrice, time);
    }


    private InterMarketOrder(Order buyOrder, Order sellOrder)
    {
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
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
        return new InterMarketOrder(buyOrder, sellOrder);
    }

    public Pair<OrderRecordStruct, OrderRecordStruct> getHistoricalRecord()
    {
        OrderRecordStruct buyRecord = buyOrder.getHistoricalRecord();
        OrderRecordStruct sellRecord = sellOrder.getHistoricalRecord();
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

        return  buyOrder.load(tag.getCompound("buyOrder")) &&
                sellOrder.load(tag.getCompound("sellOrder"));
    }
}
