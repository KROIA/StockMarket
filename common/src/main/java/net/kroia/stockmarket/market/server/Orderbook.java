package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.orders.InterMarketOrder;
import net.kroia.stockmarket.market.orders.Order;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Comparator;
import java.util.PriorityQueue;

public class Orderbook implements ServerSaveable
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        VirtualOrderbook.setBackend(backend);
    }

    private ItemID itemID;

    private final VirtualOrderbook virtualOrderbook;
    private final PriorityQueue<Order>  buyLimitOrders = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellLimitOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<InterMarketOrder> interMarketBuyOrders = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));


    public Orderbook()
    {
        virtualOrderbook = new VirtualOrderbook();
    }

    public void setItemID(ItemID id)
    {
        itemID = id;
    }

    public void update(long currentMarketPrice)
    {
        virtualOrderbook.update(currentMarketPrice);
    }


    public boolean putOrder(Order order)
    {
        if(order.isFilled())
            return false;
        if(!order.getItemID().equals(itemID))
            return false; // Wrong market for this order
        if(order.isBuyOrder())
        {
            buyLimitOrders.add(order);
        }
        else
        {
            sellLimitOrders.add(order);
        }
        return true;
    }
    public boolean putOrder(InterMarketOrder order)
    {
        if(order.isFilled())
            return false;
        if(!order.getBuyItemID().equals(itemID))
            return false; // Wrong market for this order
        interMarketBuyOrders.add(order);
        return true;
    }



    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        CompoundTag virtualOrderbookTag = new CompoundTag();
        success &= virtualOrderbook.save(virtualOrderbookTag);
        tag.put("virtualOrderbook", virtualOrderbookTag);

        ListTag buyLimitOrdersTag = new ListTag();
        for(Order order : this.buyLimitOrders)
        {
            CompoundTag buyLimitOrderTag = new CompoundTag();
            if(order.save(buyLimitOrderTag)){
                buyLimitOrdersTag.add(buyLimitOrderTag);
            }
            else  {
                error(String.format("Can't save order %s", order));
                success = false;
            }
        }
        tag.put("buyLimitOrders", buyLimitOrdersTag);


        ListTag sellLimitOrdersTag = new ListTag();
        for(Order order : this.sellLimitOrders)
        {
            CompoundTag sellLimitOrderTag = new CompoundTag();
            if(order.save(sellLimitOrderTag)){
                sellLimitOrdersTag.add(sellLimitOrderTag);
            }
            else  {
                error(String.format("Can't save order %s", order));
            }
        }
        tag.put("sellLimitOrders", sellLimitOrdersTag);


        ListTag interMarketBuyOrdersTag = new ListTag();
        for(InterMarketOrder order : this.interMarketBuyOrders)
        {
            CompoundTag interMarketBuyOrderTag = new CompoundTag();
            if(order.save(interMarketBuyOrderTag)){
                interMarketBuyOrdersTag.add(interMarketBuyOrderTag);
            }
            else  {
                error(String.format("Can't save order %s", order));
            }
        }
        tag.put("interMarketBuyOrders", interMarketBuyOrdersTag);

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;

        if(tag.contains("virtualOrderbook"))
        {
            CompoundTag virtualOrderbookTag = tag.getCompound("virtualOrderbook");
            success &= virtualOrderbook.load(virtualOrderbookTag);
        }
        else
        {
            success = false;
            error("Can't load Orderbook from NBT tag");
        }


        buyLimitOrders.clear();
        if(tag.contains("buyLimitOrders"))
        {

            ListTag buyLimitOrdersTag = tag.getList("buyLimitOrders", CompoundTag.TAG_COMPOUND);
            for(int i = 0; i < buyLimitOrdersTag.size(); i++)
            {
                CompoundTag buyLimitOrderTag = buyLimitOrdersTag.getCompound(i);
                Order order = Order.createFromNBT(buyLimitOrderTag);
                if(order != null)
                {
                    buyLimitOrders.add(order);
                }
                else
                {
                    error(String.format("Can't load buyLimitOrder [%s] from tag  %s", i, buyLimitOrderTag));
                    success = false;
                }
            }
        }

        sellLimitOrders.clear();
        if(tag.contains("sellLimitOrders"))
        {

            ListTag sellLimitOrdersTag =  tag.getList("sellLimitOrders", CompoundTag.TAG_COMPOUND);
            for(int i = 0; i < sellLimitOrdersTag.size(); i++)
            {
                CompoundTag sellLimitOrderTag = sellLimitOrdersTag.getCompound(i);
                Order order = Order.createFromNBT(sellLimitOrderTag);
                if(order != null)
                {
                    sellLimitOrders.add(order);
                }
                else
                {
                    error(String.format("Can't load sellLimitOrder [%s] from tag  %s", i, sellLimitOrderTag));
                    success = false;
                }
            }
        }


        interMarketBuyOrders.clear();
        if(tag.contains("interMarketBuyOrders"))
        {
            ListTag orders  = tag.getList("interMarketBuyOrders", CompoundTag.TAG_COMPOUND);
            for(int i = 0; i < orders.size(); i++)
            {
                CompoundTag orderTag = orders.getCompound(i);
                InterMarketOrder interMarketOrder = InterMarketOrder.createFromNBT(orderTag);
                if(interMarketOrder != null)
                {
                    interMarketBuyOrders.add(interMarketOrder);
                }
                else
                {
                    error(String.format("Can't load interMarketBuyOrder [%s] from tag  %s", i, orderTag));
                    success = false;
                }
            }
        }
        return success;
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[Orderbook]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[Orderbook]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[Orderbook]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[Orderbook]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[Orderbook]: "+message);
    }
}
