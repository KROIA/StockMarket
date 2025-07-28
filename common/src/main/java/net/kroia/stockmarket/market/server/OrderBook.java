package net.kroia.stockmarket.market.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;

public class OrderBook implements ServerSaveable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    // Create a sorted queue for buy and sell orders, sorted by price.
    private final PriorityQueue<LimitOrder> limitBuyOrders = new PriorityQueue<>((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
    private final PriorityQueue<LimitOrder> limitSellOrders = new PriorityQueue<>(Comparator.comparingDouble(LimitOrder::getPrice));

    private final List<Order> incommingOrders = new ArrayList<>();

    private final GhostOrderBook ghostOrderBook;

    public OrderBook()
    {
        this.ghostOrderBook = new GhostOrderBook(0);
    }
    public OrderBook(int initialPrice)
    {
        this.ghostOrderBook = new GhostOrderBook(initialPrice);
    }

    public void updateGhostOrderBookVolume(int currentPrice)
    {
        this.ghostOrderBook.updateVolume(currentPrice);
    }
    public GhostOrderBook getGhostOrderBook()
    {
        return ghostOrderBook;
    }



    public void addIncommingOrder(Order order)
    {
        if(incommingOrders.contains(order))
            return; // Order already exists in the incoming orders list.
        incommingOrders.add(order);
    }
    public List<Order> getIncommingOrders()
    {
        return incommingOrders;
    }
    public List<Order> getAndClearIncommingOrders()
    {
        List<Order> orders = new ArrayList<>(incommingOrders);
        incommingOrders.clear();
        return orders;
    }
    public void clearIncommingOrders()
    {
        incommingOrders.clear();
    }
    public void placeLimitOrder(LimitOrder order)
    {
        if(order.isBuy())
            limitBuyOrders.add(order);
        else
            limitSellOrders.add(order);
        order.notifyPlayer();
    }
    public boolean removeOrder(LimitOrder order)
    {
        if(order.isBuy())
            return limitBuyOrders.remove(order);
        else
            return limitSellOrders.remove(order);
    }
    public LimitOrder removeOrder(long orderID)
    {
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getOrderID() == orderID)
            {
                limitBuyOrders.remove(order);
                return order;
            }
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getOrderID() == orderID)
            {
                limitSellOrders.remove(order);
                return order;
            }
        }
        return null;
    }
    public List<LimitOrder> removeAllOrders(UUID playerUUID)
    {
        List<LimitOrder> removedOrders = new ArrayList<>();
        Iterator<LimitOrder> buyIterator = limitBuyOrders.iterator();
        while(buyIterator.hasNext())
        {
            LimitOrder order = buyIterator.next();
            if(order.getPlayerUUID().equals(playerUUID))
            {
                buyIterator.remove();
                removedOrders.add(order);
            }
        }
        Iterator<LimitOrder> sellIterator = limitSellOrders.iterator();
        while(sellIterator.hasNext())
        {
            LimitOrder order = sellIterator.next();
            if(order.getPlayerUUID().equals(playerUUID))
            {
                sellIterator.remove();
                removedOrders.add(order);
            }
        }
        return removedOrders;
    }
    public List<LimitOrder> removeAllOrders()
    {
        List<LimitOrder> removedOrders = new ArrayList<>(limitBuyOrders);
        limitBuyOrders.clear();
        removedOrders.addAll(limitSellOrders);
        limitSellOrders.clear();
        return removedOrders;
    }

    public List<LimitOrder> removeAllBotOrders()
    {
        List<LimitOrder> removedOrders = new ArrayList<>();
        Iterator<LimitOrder> buyIterator = limitBuyOrders.iterator();
        while(buyIterator.hasNext())
        {
            LimitOrder order = buyIterator.next();
            if(order.isBot())
            {
                buyIterator.remove();
                removedOrders.add(order);
            }
        }
        Iterator<LimitOrder> sellIterator = limitSellOrders.iterator();
        while(sellIterator.hasNext())
        {
            LimitOrder order = sellIterator.next();
            if(order.isBot())
            {
                sellIterator.remove();
                removedOrders.add(order);
            }
        }
        return removedOrders;
    }
    public boolean orderExists(LimitOrder order)
    {
        if(order.isBuy())
            return limitBuyOrders.contains(order);
        else
            return limitSellOrders.contains(order);
    }

    public LimitOrder getOrder(long orderID)
    {
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getOrderID() == orderID)
                return order;
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getOrderID() == orderID)
                return order;
        }
        return null;
    }
    public List<LimitOrder> getOrders(List<Long> orderIDs)
    {
        List<LimitOrder> orders = new ArrayList<>();
        for(long orderID : orderIDs)
        {
            LimitOrder order = getOrder(orderID);
            if(order != null)
                orders.add(order);
        }
        return orders;
    }

    public ArrayList<Order> getOrders()
    {
        ArrayList<Order> orders = new ArrayList<>();
        orders.addAll(limitBuyOrders);
        orders.addAll(limitSellOrders);
        return orders;
    }

    public List<LimitOrder> getOrders(UUID playerUUID)
    {
        ArrayList<LimitOrder> orders = new ArrayList<>();
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getPlayerUUID().equals(playerUUID))
                orders.add(order);
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getPlayerUUID().equals(playerUUID))
                orders.add(order);
        }
        return orders;
    }
    public PriorityQueue<LimitOrder> getBuyOrders()
    {
        return limitBuyOrders;
    }
    public PriorityQueue<LimitOrder> getSellOrders()
    {
        return limitSellOrders;
    }


    /**
     * Returns a volume heatmap of the order book in the given price range.
     * The volume is divided into the given number of tiles.
     * @param tiles The number of tiles to divide the price range into.
     * @param minPrice The minimum price of the heatmap.
     * @param maxPrice The maximum price of the heatmap.
     * @return An array of integers representing the volume in each tile.
     */
    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        OrderbookVolume orderbookVolume = new OrderbookVolume(tiles, minPrice, maxPrice);
        int priceRange = maxPrice - minPrice;
        float priceStep = (float)priceRange / (float)tiles;
        int[] volume = new int[tiles];
        for(LimitOrder order : limitBuyOrders)
        {
            int index = (int)((float)(order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }
        for(LimitOrder order : limitSellOrders)
        {
            int index = (int)((float)(order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }

        for(int i=minPrice; i<maxPrice; i++)
        {
            int index = (int)((float)(i - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += ghostOrderBook.getAmount(i);
        }

        orderbookVolume.setVolume(volume);
        return orderbookVolume;
    }

    public long getVolume(int price, int currentMarketPrice)
    {
        long volume = 0;
        if(price < currentMarketPrice)
        {
            for(LimitOrder order : limitBuyOrders)
            {
                if(order.getPrice() == price)
                    volume += order.getPendingAmount();
            }
        }
        else
        {
            for(LimitOrder order : limitSellOrders)
            {
                if(order.getPrice() == price)
                    volume += order.getPendingAmount();
            }
        }
        volume += ghostOrderBook.getAmount(price);
        return volume;
    }
    public long getVolumeInRange(int minPrice, int maxPrice)
    {
        long volume = 0;
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getPrice() >= minPrice && order.getPrice() <= maxPrice)
                volume += order.getPendingAmount();
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getPrice() >= minPrice && order.getPrice() <= maxPrice)
                volume += order.getPendingAmount();
        }
        for(int i=minPrice; i<=maxPrice; i++)
        {
            volume += ghostOrderBook.getAmount(i);
        }
        return volume;
    }



    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        ListTag buyOrdersList = new ListTag();
        ListTag sellOrdersList = new ListTag();
        for(LimitOrder order : limitBuyOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            success &= order.save(orderTag);
            buyOrdersList.add(orderTag);
        }

        for(LimitOrder order : limitSellOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            success &= order.save(orderTag);
            sellOrdersList.add(orderTag);
        }
        tag.put("buy_orders", buyOrdersList);
        tag.put("sell_orders", sellOrdersList);

        CompoundTag ghostOrderBookTag = new CompoundTag();
        success &= ghostOrderBook.save(ghostOrderBookTag);
        tag.put("ghost_order_book", ghostOrderBookTag);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("buy_orders") ||
                !tag.contains("sell_orders") ||
                !tag.contains("ghost_order_book"))
            return false;
        boolean success = true;

        ListTag buyOrdersList = tag.getList("buy_orders", CompoundTag.TAG_COMPOUND);
        ListTag sellOrdersList = tag.getList("sell_orders", CompoundTag.TAG_COMPOUND);
        for(int i = 0; i < buyOrdersList.size(); i++)
        {
            CompoundTag orderTag = buyOrdersList.getCompound(i);
            LimitOrder order = LimitOrder.loadFromTag(orderTag);
            if(order == null)
                success = false;
            else
                limitBuyOrders.add(order);
        }

        for(int i = 0; i < sellOrdersList.size(); i++)
        {
            CompoundTag orderTag = sellOrdersList.getCompound(i);
            LimitOrder order = LimitOrder.loadFromTag(orderTag);
            if(order == null)
                success = false;
            else
                limitSellOrders.add(order);
        }

        if(tag.contains("ghost_order_book"))
        {
            CompoundTag ghostOrderBookTag = tag.getCompound("ghost_order_book");
            success &= ghostOrderBook.load(ghostOrderBookTag);
        }
        else
        {
            ghostOrderBook.cleanup();
        }
        return success;
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("buyOrderCount", limitBuyOrders.size());
        metadata.addProperty("sellOrderCount", limitSellOrders.size());
        metadata.addProperty("incommingOrderCount", incommingOrders.size());
        jsonObject.add("metadata", metadata);

        jsonObject.add("ghostOrderBook", ghostOrderBook.toJson());
        return jsonObject;
    }
    public String toJsonString()
    {
        return GSON.toJson(toJson());
    }

    @Override
    public String toString() {
        return toJsonString();
    }
}
