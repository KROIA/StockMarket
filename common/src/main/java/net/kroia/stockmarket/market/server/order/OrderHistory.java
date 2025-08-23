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

public class OrderHistory {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }



    private static final class OrderHistoryDataArchiveChunk extends DataArchiveChunk {
        public static final class KEY
        {
            public static final String TRADING_PAIRS = "t";
            public static final String CHRONOLOGICAL_ORDERS = "c";
        }
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
            ListTag tradingPairs = new ListTag();
            Map<TradingPair, Integer> pairIDMap = new java.util.HashMap<>();
            for (Map.Entry<TradingPair, List<OrderDataRecord>> entry : marketsOrdersMap.entrySet()) {
                CompoundTag marketTag = new CompoundTag();
                entry.getKey().save(marketTag);
                pairIDMap.put(entry.getKey(), tradingPairs.size());
                tradingPairs.add(marketTag);

            }
            dataTag.put(KEY.TRADING_PAIRS, tradingPairs);

            ListTag chronologicalOrdersList = new ListTag();
            for (OrderDataRecord order : chronologicalOrderedOrderList) {
                CompoundTag orderTag = new CompoundTag();
                order.save(orderTag);
                orderTag.putInt(OrderDataRecord.KEY.TRADING_PAIR, pairIDMap.get(order.getTradingPair())); // Store the trading pair ID
                chronologicalOrdersList.add(orderTag);
            }
            dataTag.put(KEY.CHRONOLOGICAL_ORDERS, chronologicalOrdersList);
            return true;
        }

        @Override
        protected boolean load(CompoundTag dataTag) {

            marketsOrdersMap.clear();
            chronologicalOrderedOrderList.clear();

            ListTag tradingPairs = dataTag.getList(KEY.TRADING_PAIRS, 10);
            List<TradingPair> tradingPairsList = new ArrayList<>(tradingPairs.size());
            for (int i = 0; i < tradingPairs.size(); i++) {
                CompoundTag marketTag = tradingPairs.getCompound(i);
                TradingPair pair = new TradingPair();
                if (!pair.load(marketTag)) {
                    continue; // Skip if loading the trading pair fails
                }
                marketsOrdersMap.put(pair, new ArrayList<>());
                tradingPairsList.add(pair);
            }

            ListTag ordersList = dataTag.getList(KEY.CHRONOLOGICAL_ORDERS, 10);
            for(int i=0; i<ordersList.size(); i++)
            {
                CompoundTag orderTag = ordersList.getCompound(i);
                int pairID = orderTag.getInt(OrderDataRecord.KEY.TRADING_PAIR);
                if(pairID < 0 || pairID >= tradingPairsList.size())
                {
                    continue; // Invalid pair ID, skip this order
                }
                TradingPair pair = tradingPairsList.get(pairID);
                OrderDataRecord order = OrderDataRecord.loadFromTag(orderTag, pair);
                if(order == null)
                {
                    continue; // Skip if loading the order fails
                }
                marketsOrdersMap.get(pair).add(order);
                chronologicalOrderedOrderList.add(order);
            }
            return true;
        }
    }
    private static final class OrderHistoryDataArchiveManager extends DataArchiveManager<OrderHistoryDataArchiveChunk>
    {

        private OrderHistoryDataArchiveChunk currentChunk;
        private int learnedOrderCountSizeCheckEstimation = 0;


        /*
        // Performance tracking variables
        private long savingTimesAccumul = 0;
        private long loadingTimesAccumul = 0;
        private long changeCheckTimesAccumul = 0;
        private final List<Double> savingTimes = new ArrayList<>();
        private final List<Double> changeCheckTimes = new ArrayList<>();
        private final List<Double> loadingTimes = new ArrayList<>();
        private boolean saveTimes = false; // Set this variable to true inside the debugger to trigger a dump to a csv file
        */


        // Server constructor
        public OrderHistoryDataArchiveManager(Path archiveFolderPath) {
            super(archiveFolderPath, NBTFileParser.NbtFormat.COMPRESSED, OrderHistoryDataArchiveChunk::new);
            setLogger(BACKEND_INSTANCES.LOGGER::error, BACKEND_INSTANCES.LOGGER::error, BACKEND_INSTANCES.LOGGER::debug, BACKEND_INSTANCES.LOGGER::warn);
            currentChunk = new OrderHistoryDataArchiveChunk();

            /*
            // Performance tracking
            TickEvent.SERVER_POST.register(this::onClientTick);
             */
        }



        public void hasChanged()
        {
            boolean needsNewChunk = false;
            /*
            // Performance tracking
            long startTime = System.nanoTime();
             */
            if(learnedOrderCountSizeCheckEstimation != 0)
            {
                if(learnedOrderCountSizeCheckEstimation <= currentChunk.chronologicalOrderedOrderList.size())
                {
                    int currentOrderCount = currentChunk.chronologicalOrderedOrderList.size();
                    float currentChunkSizePercentage = getChunkSizeUtilisationPercentage(currentChunk);
                    learnedOrderCountSizeCheckEstimation = (int)(currentOrderCount / currentChunkSizePercentage * 80);
                    needsNewChunk = currentChunkSizePercentage > 80;
                }
            }
            else {
                float currentChunkSizePercentage = getChunkSizeUtilisationPercentage(currentChunk);
                if(currentChunkSizePercentage>20)
                {
                    int currentOrderCount = currentChunk.chronologicalOrderedOrderList.size();
                    learnedOrderCountSizeCheckEstimation = (int)(currentOrderCount / currentChunkSizePercentage * 80);
                }
                needsNewChunk = currentChunkSizePercentage > 80;
            }
            /*
            // Performance tracking
            changeCheckTimesAccumul += System.nanoTime() - startTime;
            */


            if(needsNewChunk)
            {
                long endTime = currentChunk.updateEndTime();
                /*
                // Performance tracking
                long start = System.nanoTime();
                 */
                saveChunk(currentChunk);

                /*
                // Performance tracking
                long end = System.nanoTime();
                savingTimesAccumul += (end - start);


                // dummy load for performance tracking
                load();
                 */

                learnedOrderCountSizeCheckEstimation = currentChunk.chronologicalOrderedOrderList.size();
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
            /*
            // Performance tracking
            long start = System.nanoTime();
             */

            // load the most recent chunk
            List<DataArchiveChunk.TimeInterval> chunkIntervals = getStoredIntervals();
            if(!chunkIntervals.isEmpty())
            {
                var list = super.loadChunks(chunkIntervals.get(chunkIntervals.size()-1));
                if(list != null && !list.isEmpty())
                {
                    currentChunk = list.get(0);
                    /*
                    // Performance tracking
                    long end = System.nanoTime();
                    loadingTimesAccumul += (end - start);
                     */
                    return true;
                }
            }
            return false;
        }

        /*
        // Performance tracking
        private void onClientTick(MinecraftServer server) {
            if(savingTimesAccumul > 0) {
                savingTimes.add(savingTimesAccumul / 1_000_000_000.0); // Convert to seconds
                savingTimesAccumul = 0;
            }


            if(loadingTimesAccumul > 0) {
                loadingTimes.add(loadingTimesAccumul / 1_000_000_000.0); // Convert to seconds
                loadingTimesAccumul = 0;
            }


            // if(changeCheckTimesAccumul > 0) {
            //     changeCheckTimes.add(changeCheckTimesAccumul / 1_000_000.0); // Convert to milliseconds
            //     changeCheckTimesAccumul = 0;
            // }

            // Put a breakpoint before the if statement if you want to save the performance data to a CSV file.
            // Set the variable to true in the debugger.
            // The variable gets set to false after the data is saved.
            if(saveTimes)
            {
                savePerformanceTimes();
            }
        }

        private void savePerformanceTimes()
        {
            saveTimes = false; // Reset the flag after saving times
            // create csv file with saving and loading times
            StringBuilder csvBuilder = new StringBuilder();
            csvBuilder.append("Check Times (ms); Saving Times (seconds); Loading Times (seconds)\n");
            int maxSize = Math.max(Math.max(savingTimes.size(), loadingTimes.size()), changeCheckTimes.size());
            for(int i = 0; i < maxSize; i++)
            {
                if(i < changeCheckTimes.size())
                {
                    csvBuilder.append(changeCheckTimes.get(i));
                }
                csvBuilder.append(";");
                if(i < savingTimes.size())
                {
                    csvBuilder.append(savingTimes.get(i));
                }
                csvBuilder.append(";");
                if(i < loadingTimes.size())
                {
                    csvBuilder.append(loadingTimes.get(i));
                }
                csvBuilder.append("\n");
            }
            Path csvPath = getArchiveFolderPath().resolve("../saving_loading_times.csv");
            try {
                java.nio.file.Files.writeString(csvPath, csvBuilder.toString());
            } catch (java.io.IOException e) {
                BACKEND_INSTANCES.LOGGER.error("Failed to write saving/loading times to CSV file: " + csvPath, e);
            }
            savingTimes.clear();
            loadingTimes.clear();
            changeCheckTimes.clear();
        }
        */
    }

    private final OrderHistoryDataArchiveManager orderHistoryDataArchiveManager;
    private final OrderHistoryDataArchiveChunk clientChunkData;


    // Server constructor
    public OrderHistory(Path orderHistoryFolder){
        orderHistoryDataArchiveManager = new OrderHistoryDataArchiveManager(orderHistoryFolder);
        clientChunkData = null;
    }

    // Client constructor
    public OrderHistory(){
        orderHistoryDataArchiveManager = null;
        clientChunkData = new OrderHistoryDataArchiveChunk();
    }

    public boolean putOrder(TradingPair pair, Order order){

        /*
        IMPORTANT!!
        If you enable this code section to remove the bot orders from being saved,
        you must also remove the TEST_DUMMY_UUID from the OrderDataRecord class because that was just a workaround for testing purposes.

         */


        if(order.isBot())
        {
            // Don't add bot orders
            return false;
        }



        if(orderHistoryDataArchiveManager != null)
        {
            OrderDataRecord orderDataRecord = OrderDataRecord.fromOrder(order, pair);
            if(orderDataRecord != null) {
                orderHistoryDataArchiveManager.currentChunk.chronologicalOrderedOrderList.add(orderDataRecord);
                List<OrderDataRecord> orders = orderHistoryDataArchiveManager.currentChunk.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
                orders.add(orderDataRecord);
                orderHistoryDataArchiveManager.hasChanged();
            }
            return true;
        }

        if(clientChunkData != null)
        {
            OrderDataRecord orderDataRecord = OrderDataRecord.fromOrder(order, pair);
            if(orderDataRecord != null) {
                clientChunkData.chronologicalOrderedOrderList.add(orderDataRecord);
                List<OrderDataRecord> orders = clientChunkData.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
                orders.add(orderDataRecord);
            }
            return true;
        }
        return false;
    }

    public List<OrderDataRecord> getOrderHistoryForMarket(TradingPair pair){
        if(orderHistoryDataArchiveManager != null)
        {
            if(pair==null){
                return orderHistoryDataArchiveManager.currentChunk.chronologicalOrderedOrderList;
            }

            return orderHistoryDataArchiveManager.currentChunk.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
        }
        if(clientChunkData != null)
        {
            return clientChunkData.marketsOrdersMap.computeIfAbsent(pair, k -> new java.util.ArrayList<>());
        }
        return new ArrayList<>();
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
}
