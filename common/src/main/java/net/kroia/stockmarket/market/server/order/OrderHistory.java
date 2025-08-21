package net.kroia.stockmarket.market.server.order;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;

public class OrderHistory implements ServerSaveableChunked {

    static final int ORDER_CONTEXT = 50;

    protected Map<TradingPair, Order[]> orderData;
    protected Order[] mostRecentOrders;

    public OrderHistory(){
        orderData = new HashMap<>();
        mostRecentOrders = new Order[ORDER_CONTEXT];
    }

    public boolean putOrder(TradingPair pair, Order order){
        if(orderData.containsKey(pair)){
            logOrder(order, orderData.get(pair));
        }
        else{
            Order[] newOrderLog = new Order[ORDER_CONTEXT];
            logOrder(order, newOrderLog);
            orderData.put(pair, newOrderLog);
        }
        logOrder(order, mostRecentOrders);
        return true;
    }

    public Order[] getOrderHistoryForMarket(TradingPair pair){
        return orderData.getOrDefault(pair, mostRecentOrders);
    }

    public void logOrder(Order order, Order[] orders){
        for(int i = 0; i < orders.length; ++i){
            if(orders[i] == null){
                orders[i] = order;
                return;
            }
        }
        //History is full, drop off the oldest order
        System.arraycopy(orders, 1, orders, 0, orders.length-1);
        orders[orders.length-1] = order;
    }

    @Override
    public boolean save(Map<String, ListTag> listTags) {
        boolean success = true;
        ListTag historyTag = new ListTag();
        CompoundTag tag = new CompoundTag();
        success = writeMarketOrderHistory(mostRecentOrders, tag);
        historyTag.add(tag);
        orderData.forEach((key, value) -> {
            CompoundTag orderTag = new CompoundTag();
            key.save(orderTag);
            writeMarketOrderHistory(value, orderTag);
            historyTag.add(orderTag);
        });

        listTags.put("orderHistory", historyTag);

        return success;
    }

    public boolean writeMarketOrderHistory(Order[] orders, CompoundTag tag){
        boolean success = true;
        int i = 0;
        ListTag list = new ListTag();
        while(orders[i] != null){
            CompoundTag orderTag = new CompoundTag();
            success &= orders[i].save(orderTag);
            orderTag.putString("type", orders[i].getType().toString());
            list.add(orderTag);
        }
        tag.put("orders", list);
        return success;
    }

    public boolean readMarketOrderHistory(Order[] orders, CompoundTag tag){
        boolean success = true;
        ListTag list = tag.getList("orders", 10);
        for(int i = 0; i < Math.min(ORDER_CONTEXT, list.size()); ++i){
            CompoundTag orderTag = list.getCompound(i);
            Order order;
            if(orderTag.getString("type").equals("limit")){
                order = LimitOrder.loadFromTag(orderTag);
            }
            else{
                order = MarketOrder.loadFromTag(tag);
            }
            orders[i] = order;

        }
        return success;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        if(!listTags.containsKey("orderHistory")){
            return false;
        }
        ListTag historyTag = listTags.get("orderHistory");
        for(int i = 0; i < historyTag.size(); ++i){
            CompoundTag tag = historyTag.getCompound(i);
            TradingPair pair = new TradingPair();
            Order[] orders = new Order[ORDER_CONTEXT];
            readMarketOrderHistory(orders, tag);
            if(!pair.load(tag)){
                mostRecentOrders = orders;
            }
            else {
                orderData.put(pair, orders);
            }
        }
        return true;
    }
}
