package net.kroia.stockmarket.market.server.order;

import net.kroia.modutilities.persistence.NBTFileParser;
import net.kroia.modutilities.persistence.archive.DataArchiveChunk;
import net.kroia.modutilities.persistence.archive.DataArchiveManager;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderHistory /*implements ServerSaveableChunked*/ {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }



    private static final class OrderHistoryDataArchiveChunk extends DataArchiveChunk {

        public final Map<TradingPair, List<OrderDataRecord>> marketsOrdersMap= new java.util.HashMap<>();
        public final List<OrderDataRecord> chronologicalOrderedOrderList = new java.util.ArrayList<>();

        public OrderHistoryDataArchiveChunk() {
            super();
        }
        public OrderHistoryDataArchiveChunk(long startTime) {
            super(startTime);
        }
        @Override
        protected boolean save(CompoundTag dataTag) {
            CompoundTag marketsOrdersTag = new CompoundTag();
            for (Map.Entry<TradingPair, List<OrderDataRecord>> entry : marketsOrdersMap.entrySet()) {
                CompoundTag marketTag = new CompoundTag();
                entry.getKey().save(marketTag);
                ListTag ordersList = new ListTag();
                for (OrderDataRecord order : entry.getValue()) {
                    CompoundTag orderTag = new CompoundTag();
                    order.save(orderTag);
                    ordersList.add(orderTag);
                }
                marketTag.put("orders", ordersList);
                marketsOrdersTag.put(entry.getKey().toString(), marketTag);
            }
            dataTag.put("marketsOrders", marketsOrdersTag);

            ListTag chronologicalOrdersList = new ListTag();
            for (OrderDataRecord order : chronologicalOrderedOrderList) {
                CompoundTag orderTag = new CompoundTag();
                order.save(orderTag);
                chronologicalOrdersList.add(orderTag);
            }
            dataTag.put("chronologicalOrders", chronologicalOrdersList);
            return true;
        }

        @Override
        protected boolean load(CompoundTag dataTag) {
            CompoundTag marketsOrdersTag = dataTag.getCompound("marketsOrders");
            for (String marketKey : marketsOrdersTag.getAllKeys()) {
                CompoundTag marketTag = marketsOrdersTag.getCompound(marketKey);
                TradingPair pair = new TradingPair();
                if (!pair.load(marketTag)) {
                    continue; // Skip if loading the trading pair fails
                }
                ListTag ordersList = marketTag.getList("orders", 10);
                List<OrderDataRecord> orders = new java.util.ArrayList<>();
                for (int i = 0; i < ordersList.size(); i++) {
                    CompoundTag orderTag = ordersList.getCompound(i);
                    OrderDataRecord order = new OrderDataRecord();
                    order.load(orderTag);
                    if (order != null) {
                        orders.add(order);
                    }
                }
                marketsOrdersMap.put(pair, orders);
            }

            ListTag chronologicalOrdersList = dataTag.getList("chronologicalOrders", 10);
            chronologicalOrderedOrderList.clear();
            for (int i = 0; i < chronologicalOrdersList.size(); i++) {
                CompoundTag orderTag = chronologicalOrdersList.getCompound(i);
                OrderDataRecord order = new OrderDataRecord();
                if (order.load(orderTag)) {
                    chronologicalOrderedOrderList.add(order);
                }
            }
            return true;

        }
        // This class can be used to store order history data in a chunked manner if needed
        // Currently, it is not implemented but can be extended in the future
    }
    private static final class OrderHistoryDataArchiveManager extends DataArchiveManager<OrderHistoryDataArchiveChunk>
    {

        private OrderHistoryDataArchiveChunk currentChunk;

        // Server constructor
        public OrderHistoryDataArchiveManager(Path archiveFolderPath) {
            super(archiveFolderPath, NBTFileParser.NbtFormat.COMPRESSED, OrderHistoryDataArchiveChunk::new);
            currentChunk = new OrderHistoryDataArchiveChunk();
        }

        public void hasChanged()
        {
            if(getChunkSizeUtilisationPercentage(currentChunk) > 80) {
                long endTime = currentChunk.updateEndTime();
                saveChunk(currentChunk);
                currentChunk = new OrderHistoryDataArchiveChunk(endTime+1);
            }
        }

        public OrderHistoryDataArchiveChunk getCurrentChunk() {
            return currentChunk;
        }

        public boolean save()
        {
            currentChunk.updateEndTime();
            return saveChunk(currentChunk);
        }
        public boolean load()
        {
            // load the most recent chunk
            List<DataArchiveChunk.TimeInterval> chunkIntervals = getStoredIntervals();
            if(!chunkIntervals.isEmpty())
            {
                var list = super.loadChunks(chunkIntervals.get(chunkIntervals.size()-1));
                if(list != null && !list.isEmpty())
                {
                    currentChunk = list.get(0);
                    return true;
                }
            }
            return false;
        }
    }



    /*static final int ORDER_CONTEXT = 50;

    protected Map<TradingPair, Order[]> orderData;
    protected Order[] mostRecentOrders;*/

    private final OrderHistoryDataArchiveManager orderHistoryDataArchiveManager;
    private final OrderHistoryDataArchiveChunk clientChunkData;


    // Server constructor
    public OrderHistory(Path orderHistoryFolder){
        orderHistoryDataArchiveManager = new OrderHistoryDataArchiveManager(orderHistoryFolder);
        clientChunkData = null;
        //orderData = new HashMap<>();
        //mostRecentOrders = new Order[ORDER_CONTEXT];
    }

    // Client constructor
    public OrderHistory(){
        orderHistoryDataArchiveManager = null;
        clientChunkData = new OrderHistoryDataArchiveChunk();
        //orderData = new HashMap<>();
        //mostRecentOrders = new Order[ORDER_CONTEXT];
    }

    public boolean putOrder(TradingPair pair, Order order){

        /*
        if(order.isBot())
        {
            // Don't add bot orders
            return false;
        }
        */


        if(orderHistoryDataArchiveManager != null)
        {
            orderHistoryDataArchiveManager.currentChunk.chronologicalOrderedOrderList.add(OrderDataRecord.fromOrder(order));
            List<OrderDataRecord> orders = orderHistoryDataArchiveManager.currentChunk.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
            orders.add(OrderDataRecord.fromOrder(order));
            BACKEND_INSTANCES.LOGGER.info("[OrderHistory::putOrder()]: Order added to history, OrderID: " + order.getOrderID());
            orderHistoryDataArchiveManager.hasChanged();
            return true;
        }

        if(clientChunkData != null)
        {
            clientChunkData.chronologicalOrderedOrderList.add(OrderDataRecord.fromOrder(order));
            List<OrderDataRecord> orders = clientChunkData.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
            orders.add(OrderDataRecord.fromOrder(order));
            return true;
        }
        return false;
    }

    public List<OrderDataRecord> getOrderHistoryForMarket(TradingPair pair){
        if(orderHistoryDataArchiveManager != null)
        {
            return orderHistoryDataArchiveManager.currentChunk.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
        }
        if(clientChunkData != null)
        {
            return clientChunkData.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
        }
        return new ArrayList<>();
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



    public boolean save()
    {
        if(orderHistoryDataArchiveManager != null)
        {
            return orderHistoryDataArchiveManager.save();
        }
        if(clientChunkData != null)
        {
            return true; // Indicate success
        }
        return false;
    }
    public boolean load()
    {
        if(orderHistoryDataArchiveManager != null)
        {
            return orderHistoryDataArchiveManager.load();
        }
        if(clientChunkData != null)
        {
            return true; // Indicate success
        }
        return false;
    }

    /*@Override
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
    }*/
}
