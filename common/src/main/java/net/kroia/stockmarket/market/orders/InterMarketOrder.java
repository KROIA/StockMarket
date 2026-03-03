package net.kroia.stockmarket.market.orders;

import com.ibm.icu.impl.Pair;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class InterMarketOrder implements ServerSaveable
{
    private final Order buyOrder;
    private final Order sellOrder;


    // Player order
    public InterMarketOrder(@NotNull ItemID buyItemID, @NotNull ItemID sellItemID, @NotNull Order.Type type,
                            long buyVolume, long buyPrice,
                            long estimatedSellVolume, long estimatedSellPrice,
                            long time, @NotNull UUID ownerID)
    {
        buyOrder = new Order(buyItemID, type, Math.abs(buyVolume), buyPrice, time, ownerID);
        sellOrder = new Order(sellItemID, type, -Math.abs(estimatedSellVolume), estimatedSellPrice, time, ownerID);
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
     * For saving pending orders
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
           !tag.contains("sellOrder"))
            return false;

        return  buyOrder.load(tag.getCompound("buyOrder")) &&
                sellOrder.load(tag.getCompound("sellOrder"));
    }
}
