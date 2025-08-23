package net.kroia.stockmarket.market.server.order;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.persistence.NBTFileParser;
import net.kroia.modutilities.persistence.archive.DataArchiveChunk;
import net.kroia.modutilities.persistence.archive.DataArchiveManager;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderHistory {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }



    private final class OrderHistoryDataArchiveChunk extends DataArchiveChunk {
        public static final class KEY
        {
            public static final String TRADING_PAIRS = "t";
            public static final String CHRONOLOGICAL_ORDERS = "c";
        }
        private final Map<TradingPair, List<OrderDataRecord>> marketsOrdersMap= new java.util.HashMap<>();
        private final List<OrderDataRecord> chronologicalOrderedOrderList = new java.util.ArrayList<>();
        private long lastUsedTimeMillis = System.currentTimeMillis();

        public OrderHistoryDataArchiveChunk() {
            super();
        }
        public OrderHistoryDataArchiveChunk(long startTime) {
            super(startTime);
        }

        public Map<TradingPair, List<OrderDataRecord>> getMarketsOrdersMap() {
            lastUsedTimeMillis = System.currentTimeMillis();
            return marketsOrdersMap;
        }
        public List<OrderDataRecord> getChronologicalOrderedOrderList() {
            lastUsedTimeMillis = System.currentTimeMillis();
            return chronologicalOrderedOrderList;
        }
        public long getUnusedTimeMillis() {
            return System.currentTimeMillis() - lastUsedTimeMillis;
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
                order.save(orderTag, orderDataRecordUseFlags);
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
                OrderDataRecord order = OrderDataRecord.loadFromTag(orderTag, pair, orderDataRecordUseFlags);
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
    private final class OrderHistoryDataArchiveManager extends DataArchiveManager<OrderHistoryDataArchiveChunk>
    {
        private class ChunkArrayElement
        {
            public final long startTime;
            private OrderHistoryDataArchiveChunk chunk;
            public ChunkArrayElement(OrderHistoryDataArchiveChunk chunk) {
                this.startTime = chunk.getStartTime();
                this.chunk = chunk;
            }
            public ChunkArrayElement(long startTime) {
                this.startTime = startTime;
                this.chunk = null; // Initialize with no chunk
            }
            public void eraseChunk()
            {
                chunk = null; // Erase the chunk
            }
            public boolean load()
            {
                if(chunk == null)
                {
                    chunk = loadChunk(startTime);
                    return chunk != null; // Failed to load the chunk
                }
                return true; // Chunk is loaded successfully
            }
            boolean isLoaded()
            {
                return chunk != null;
            }
            public OrderHistoryDataArchiveChunk getChunk() {
                return chunk;
            }
        }
        private OrderHistoryDataArchiveChunk currentChunk;


        /**
         * List of chunks that are available, loaded or not loaded.
         * The start time of a chunk can be used to load the chunk from the file system.
         * Do not add or change data from chunks that are older than the current chunk.
         * The current chunk (highest index) is the chunk that us used to add data.
         */
        private final List<ChunkArrayElement> chunkArray = new ArrayList<>();

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


            // Performance tracking
            //TickEvent.SERVER_POST.register(this::onServerTick);


        }


        /**
         * Gets a list of orders in chronological order starting from the given index.
         * Index = 0 means the
         * @param startIndex
         * @param amount
         * @return
         */
        public List<OrderDataRecord> getChronological(int startIndex, int amount)
        {
            debug("getChronological called with startIndex: " + startIndex + ", amount: " + amount + " from: "+ this);
            List<OrderDataRecord> result = new ArrayList<>(amount);
            if(amount <= 0 || startIndex < 0)
            {
                return result; // Return empty list if invalid parameters
            }
            int endIndex = startIndex + amount;
            if(endIndex < startIndex)
                return result; // Overflow occurred

            int currentIndex = 0;
            int chunkBeginIndex = 0;
            for(int i=chunkArray.size()-1; i>=0; --i)
            {
                ChunkArrayElement chunkElement = chunkArray.get(i);
                if(!chunkElement.isLoaded())
                {
                    chunkElement.load();
                }
                OrderHistoryDataArchiveChunk chunk = chunkElement.getChunk();
                if(chunk == null) {
                    warn("Chunk at index " + i + " is null, skipping. Maybe the file got deleted? Start chunk time = " + chunkElement.startTime);
                    continue; // Skip if the chunk is not loaded
                }
                int chunkSize = chunk.chronologicalOrderedOrderList.size();
                if(currentIndex + chunkSize < startIndex)
                {
                    currentIndex += chunkSize;
                    chunkBeginIndex += chunkSize;
                    continue; // Skip this chunk if it doesn't contain the requested range
                }

                currentIndex = startIndex;
                for(; currentIndex < endIndex && currentIndex < chunkBeginIndex+chunkSize; ++currentIndex)
                {
                    result.add(chunk.chronologicalOrderedOrderList.get(chunkSize-(currentIndex - chunkBeginIndex)-1));
                }
                if(currentIndex >= endIndex)
                    break; // Stop if we reached the end index
                chunkBeginIndex += chunkSize;
            }
            debug("getChronological collected: "+result.size()+" elements from: "+ this);
            return result;
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
                chunkArray.add(new ChunkArrayElement(currentChunk));
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


            var storedIntervals = getStoredIntervals();
            for(DataArchiveChunk.TimeInterval interval : storedIntervals)
            {
                long startTime = interval.getStartTime();
                boolean found = false;
                for(int i=0; i<chunkArray.size(); ++i)
                {
                    if(chunkArray.get(i).startTime == startTime)
                    {
                        found = true;
                        break;
                    }
                }
                if(!found)
                {
                    insertToChunkList(new ChunkArrayElement(startTime));
                }
            }

            // load the most recent chunk
            List<DataArchiveChunk.TimeInterval> chunkIntervals = getStoredIntervals();
            if(!chunkIntervals.isEmpty())
            {
                var list = super.loadChunks(chunkIntervals.get(chunkIntervals.size()-1));
                if(list != null && !list.isEmpty())
                {
                    if(currentChunk != null)
                    {
                        chunkArray.removeIf(e -> e.getChunk() == currentChunk);
                    }
                    currentChunk = list.get(0);
                    insertToChunkList(currentChunk);
                    learnedOrderCountSizeCheckEstimation = currentChunk.chronologicalOrderedOrderList.size();

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
        private OrderHistoryDataArchiveChunk loadNext(OrderHistoryDataArchiveChunk currentChunk)
        {
            if(chunkArray.isEmpty())
            {
                load();
                return this.currentChunk;
            }
            for(int i=chunkArray.size()-1; i>=0; --i)
            {
                ChunkArrayElement chunkArrayElement = chunkArray.get(i);
                if(chunkArrayElement.startTime < currentChunk.getStartTime())
                {
                    chunkArrayElement.load();
                    return chunkArrayElement.getChunk();
                }
            }
            return null; // No more chunks to load
        }
        private void insertToChunkList(OrderHistoryDataArchiveChunk chunk)
        {
            // Insert the chunk into the chunkArray in sorted order by start time
            int index = 0;
            while(index < chunkArray.size() && chunkArray.get(index).startTime <= chunk.getStartTime())
            {
                if(chunkArray.get(index).startTime == chunk.getStartTime())
                {
                    // If a chunk with the same start time already exists, replace it
                    chunkArray.get(index).eraseChunk();
                    chunkArray.remove(index);
                    break;
                }
                index++;
            }
            chunkArray.add(index, new ChunkArrayElement(chunk));
        }
        private void insertToChunkList(ChunkArrayElement chunkElement)
        {
            // Insert the chunk into the chunkArray in sorted order by start time
            int index = 0;
            while(index < chunkArray.size() && chunkArray.get(index).startTime <= chunkElement.startTime)
            {
                if(chunkArray.get(index).startTime == chunkElement.startTime)
                {
                    // If a chunk with the same start time already exists, replace it
                    chunkArray.get(index).eraseChunk();
                    chunkArray.remove(index);
                    break;
                }
                index++;
            }
            chunkArray.add(index, chunkElement);
        }

        public JsonElement toJson()
        {
            JsonObject obj = new JsonObject();
            char[] loadedChunks = new char[chunkArray.size()];
            for(int i=0; i<chunkArray.size(); ++i)
            {
                ChunkArrayElement chunkElement = chunkArray.get(i);
                if(chunkElement.isLoaded())
                {
                    loadedChunks[i] = 'X'; // Mark as loaded
                }
                else
                {
                    loadedChunks[i] = ' '; // Mark as not loaded
                }
            }
            obj.addProperty("loadedChunks_old_to_new", String.valueOf(loadedChunks));

            return obj;
        }
        @Override
        public String toString()
        {
            return JsonUtilities.toPrettyString(toJson());
        }


        // Performance tracking
        private void onServerTick(MinecraftServer server) {

            /*boolean test = false;
            if(test)
            {
                int startIndex = 0;
                int amount = 1000; // Adjust the amount as needed
                List<OrderDataRecord> orders = getChronological(startIndex, amount);
                debug("Loaded " + orders.size() + " orders from the order history for testing purposes.");
            }*/

            /*if(savingTimesAccumul > 0) {
                savingTimes.add(savingTimesAccumul / 1_000_000_000.0); // Convert to seconds
                savingTimesAccumul = 0;
            }


            if(loadingTimesAccumul > 0) {
                loadingTimes.add(loadingTimesAccumul / 1_000_000_000.0); // Convert to seconds
                loadingTimesAccumul = 0;
            }


            if(changeCheckTimesAccumul > 0) {
                changeCheckTimes.add(changeCheckTimesAccumul / 1_000_000.0); // Convert to milliseconds
                changeCheckTimesAccumul = 0;
            }

            // Put a breakpoint before the if statement if you want to save the performance data to a CSV file.
            // Set the variable to true in the debugger.
            // The variable gets set to false after the data is saved.
            if(saveTimes)
            {
                savePerformanceTimes();
            }*/
        }
/*
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


    /**
     * Defines which elements get stored/loaded from the OrderDataRecord.
     */
    private byte orderDataRecordUseFlags =
            OrderDataRecord.USE_AMOUNT |
            OrderDataRecord.USE_PLAYER |
            OrderDataRecord.USE_TYPE |
            OrderDataRecord.USE_STATUS |
            OrderDataRecord.USE_TIMESTAMP;


    // Server constructor
    public OrderHistory(Path orderHistoryFolder){
        orderHistoryDataArchiveManager = new OrderHistoryDataArchiveManager(orderHistoryFolder);
        orderHistoryDataArchiveManager.setLogger(this::error, this::error, this::debug, this::warn);
        clientChunkData = null;
    }

    // Client constructor
    public OrderHistory(){
        orderHistoryDataArchiveManager = null;
        clientChunkData = new OrderHistoryDataArchiveChunk();
    }

    public void setOrderDataRecordUseFlags(byte flags) {
        this.orderDataRecordUseFlags = flags;
    }
    public byte getOrderDataRecordUseFlags() {
        return orderDataRecordUseFlags;
    }

    public boolean putOrder(TradingPair pair, Order order){

        /*
        IMPORTANT!!
        If you enable this code section to remove the bot orders from being saved,
        you must also remove the TEST_DUMMY_UUID from the OrderDataRecord class because that was just a workaround for testing purposes.


        if(order.isBot())
        {
            // Don't add bot orders
            return false;
        }
        */


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

    public void setPlayerUUIDForAllOrders(UUID playerUUID)
    {
        for(OrderDataRecord order : getChronologicalOrderList())
        {
            order.setPlayer(playerUUID);
        }
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
    public List<OrderDataRecord> getChronologicalOrderList(){
        if(orderHistoryDataArchiveManager != null)
        {
            return orderHistoryDataArchiveManager.currentChunk.chronologicalOrderedOrderList;
        }
        if(clientChunkData != null)
        {
            return clientChunkData.chronologicalOrderedOrderList;
        }
        return new ArrayList<>();
    }

    public List<OrderDataRecord> getChronological(int startIndex, int amount)
    {
        if(orderHistoryDataArchiveManager != null)
        {
            return orderHistoryDataArchiveManager.getChronological(startIndex, amount);
        }
        return new ArrayList<>(); // Return empty list if no order history manager is available
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




    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[OrderHistory] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[OrderHistory] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[OrderHistory] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[OrderHistory] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[OrderHistory] " + msg);
    }
}
