package net.kroia.stockmarket.plugin;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.OrderBook;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.plugin.base.Plugin;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.kroia.stockmarket.util.Stopwatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ServerPluginManager {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        Plugin.setBackend(backend);
        StockMarketGenericStream.setBackend(backend);
    }
    public class MarketPluginInstanceData implements ServerSaveable
    {
        public static class VirtualOrderBookVolumeManipulationData
        {
            public enum ManipulationType
            {
                SET,
                ADD,
            }
            public final float minPrice, maxPrice, volume;
            public final ManipulationType type;

            public VirtualOrderBookVolumeManipulationData(float minPrice, float maxPrice, float volume, ManipulationType type) {
                this.minPrice = minPrice;
                this.maxPrice = maxPrice;
                this.volume = volume;
                this.type = type;
            }
        }
        public static class VirtualOrderBookVolumeManipulationRealArrayData
        {
            public enum ManipulationType
            {
                SET,
                ADD,
            }
            public final int backendStartPrice;
            public final float[] volume;
            public final ManipulationType type;

            private VirtualOrderBookVolumeManipulationRealArrayData(int backendStartPrice, float[] volume, ManipulationType type) {
                this.backendStartPrice = backendStartPrice;
                this.volume = volume;
                this.type = type;
            }
        }

        public class MarketPluginInterface  {
            public class MarketInterface implements IMarketPluginInterface {
                @Override
                public @NotNull TradingPair getTradingPair() {
                    return marketData.market.getTradingPair();
                }

                @Override
                public int getStreamPacketSendTickInterval()
                {
                    return streamPacketSendTickInterval;
                }

                @Override
                public void setStreamPacketSendTickInterval(int interval)
                {
                    streamPacketSendTickInterval = Math.max(1, interval);
                }

                @Override
                public float getDefaultPrice()
                {
                    return marketData.market.getDefaultRealPrice();
                }
                @Override
                public float getPrice() {
                    return marketData.currentPrice;
                }

                @Override
                public float convertBackendPriceToRealPrice(int backendPrice)
                {
                    return marketData.market.mapToRealPrice(backendPrice);
                }

                @Override
                public int convertRealPriceToBackendPrice(float realPrice)
                {
                    return marketData.market.mapToRawPrice(realPrice);
                }

                @Override
                public float getTargetPrice() {
                    return marketData.lastTargetPrice;
                }

                @Override
                public void addToTargetPrice(float delta) {
                    nextTargetPriceDelta += delta;
                }

                @Override
                public long placeOrder(float amount, float price) {
                    LimitOrder order = marketData.market.createBotLimitOrder(amount, price);
                    nextLimitOrders.add(order);
                    return order.getOrderID();
                }

                @Override
                public void placeOrder(float amount) {
                    nextCreatingMarketOrderVolume += amount;
                }


                @Override
                public boolean marketPluginExists(PluginRegistry.MarketPluginRegistrationObject registrationObject)
                {
                    return marketData.pluginExists(registrationObject.pluginTypeID);
                }

                @Override
                public boolean marketPluginExists(String pluginTypeID)
                {
                    return marketData.pluginExists(pluginTypeID);
                }

                @Override
                public MarketPlugin getMarketPlugin(PluginRegistry.MarketPluginRegistrationObject registrationObject)
                {
                    return marketData.getPlugin(registrationObject.pluginTypeID);
                }

                @Override
                public MarketPlugin getMarketPlugin(String pluginTypeID)
                {
                    return marketData.getPlugin(pluginTypeID);
                }


                public class OrderBookInterfaceInterface implements IMarketPluginInterface.OrderBookInterface {


                    @Override
                    public @NotNull List<LimitOrder> getBuyOrders() {
                        return marketData.buyOrders;
                    }
                    @Override
                    public @NotNull List<LimitOrder> getSellOrders() {
                        return marketData.sellOrders;
                    }


                    @Override
                    public @NotNull List<Order> getNewOrders() {
                        return marketData.newOrders;
                    }

                    @Override
                    public float getVolume(float minPrice, float maxPrice) {
                        if(minPrice > maxPrice)
                        {
                            float tmp = minPrice;
                            minPrice = maxPrice;
                            maxPrice = tmp;
                        }
                        return marketData.market.getOrderBook().getVolumeInRealRange(minPrice, maxPrice);
                    }

                    @Override
                    public float getVolume(int backendPrice)
                    {
                        return marketData.market.getOrderBook().getVolumeRawPrice(backendPrice, marketData.market.mapToRawPrice(marketData.currentPrice));
                    }

                    @Override
                    public @NotNull Tuple<@NotNull Float, @NotNull Float> getEditablePriceRange() {
                        return marketData.market.getOrderBook().getEditablePriceRange();
                    }

                    @Override
                    public @NotNull Tuple<@NotNull Integer,@NotNull  Integer> getEditableBackendPriceRange()
                    {
                        return marketData.market.getOrderBook().getEditableBackendPriceRange();
                    }

                    @Override
                    public void setVolume(float minPrice, float maxPrice, float volume) {
                        virtualOrderBookVolumeManipulations.add(new VirtualOrderBookVolumeManipulationData(minPrice, maxPrice, volume, VirtualOrderBookVolumeManipulationData.ManipulationType.SET));
                    }

                    @Override
                    public void setVolume(int backendStartPrice, float[] volume)
                    {
                        virtualOrderBookVolumeArrayManipulations.add(new VirtualOrderBookVolumeManipulationRealArrayData(backendStartPrice, volume, VirtualOrderBookVolumeManipulationRealArrayData.ManipulationType.SET));
                    }
                    @Override
                    public void addVolume(float minPrice, float maxPrice, float volume) {
                        virtualOrderBookVolumeManipulations.add(new VirtualOrderBookVolumeManipulationData(minPrice, maxPrice, volume, VirtualOrderBookVolumeManipulationData.ManipulationType.ADD));
                    }
                    @Override
                    public void addVolume(int backendStartPrice, float[] volume)
                    {
                        virtualOrderBookVolumeArrayManipulations.add(new VirtualOrderBookVolumeManipulationRealArrayData(backendStartPrice, volume, VirtualOrderBookVolumeManipulationRealArrayData.ManipulationType.ADD));
                    }
                    @Override
                    public void registerDefaultVolumeDistributionFunction(Function<Float, Float> volumeDistributionFunction_) {
                        volumeDistributionFunction = volumeDistributionFunction_;
                    }
                    @Override
                    public void unregisterDefaultVolumeDistributionFunction() {
                        volumeDistributionFunction = null;
                    }
                }

                public final OrderBookInterfaceInterface orderBookInterface = new OrderBookInterfaceInterface();
                @Override
                @NotNull
                public IMarketPluginInterface.OrderBookInterface getOrderBook()
                {
                    return orderBookInterface;
                }
            }
            public final MarketInterface marketInterface = new MarketInterface();

            public MarketPluginInterface() {

            }
        }

        public final MarketPlugin plugin;
        public final List<VirtualOrderBookVolumeManipulationData> virtualOrderBookVolumeManipulations = new java.util.ArrayList<>();
        public final List<VirtualOrderBookVolumeManipulationRealArrayData> virtualOrderBookVolumeArrayManipulations = new java.util.ArrayList<>();
        public Function<Float, Float> volumeDistributionFunction = null;
        public float nextTargetPriceDelta = 0;
        public List<LimitOrder> nextLimitOrders = new java.util.ArrayList<>();
        public float nextCreatingMarketOrderVolume = 0;
        public int streamPacketSendTickInterval = 20; // ticks/packet


        // Performance tracking
        public final Stopwatch performanceTrackWatch = new Stopwatch();
        public long lastUpdateDurationNs = 0;
        public long lastFinalizeDurationNs = 0;


        public final PluginMarket marketData;


        public MarketPluginInstanceData(MarketPlugin plugin, PluginMarket marketData) {
            this.plugin = plugin;
            this.marketData = marketData;
            MarketPluginInterface marketPluginInterface = new MarketPluginInterface();
            this.plugin.setInterface(marketPluginInterface.marketInterface);
        }
        public void clear()
        {
            virtualOrderBookVolumeManipulations.clear();
            virtualOrderBookVolumeArrayManipulations.clear();
            nextTargetPriceDelta = 0;
            nextLimitOrders.clear();
            nextCreatingMarketOrderVolume = 0;
        }
        @Override
        public boolean save(CompoundTag tag) {
            CompoundTag pluginTag = new CompoundTag();
            plugin.saveToFilesystem_internal(pluginTag);
            tag.put("pluginData", pluginTag);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(!tag.contains("pluginData"))
                return false;
            CompoundTag pluginTag = tag.getCompound("pluginData");
            return plugin.loadFromFilesystem_internal(pluginTag);
        }
    }


    public class PluginMarket implements ServerSaveableChunked
    {
        private final IServerMarket market;
        private final List<MarketPluginInstanceData> pluginsData = new ArrayList<>(); // pluginTypeID -> data
        //private final Map<String, MarketPluginInstanceData> pluginsData = new HashMap<>(); // pluginTypeID -> data

        // Runtime data
        private final List<LimitOrder> buyOrders = new java.util.ArrayList<>();
        private final List<LimitOrder> sellOrders = new java.util.ArrayList<>();
        private final List<Order> newOrders = new java.util.ArrayList<>();
        private float currentPrice = 0;
        private float lastTargetPrice = 0;
        private float nextTargetPrice = 0;



        // Performance tracking
        public final Stopwatch performanceTrackWatch = new Stopwatch();
        public long lastUpdateDurationNs = 0;
        public long lastFinalizeDurationNs = 0;
        public final Stopwatch defaultVolumeDistributionCalculationTrackWatch = new Stopwatch();
        public long lastDefaultVolumeDistributionCalculationTimeNs = 0;


        public PluginMarket(IServerMarket market) {
            this.market = market;
            this.market.getOrderBook().setDefaultVirtualVolumeDistributionFunction(this::getDefaultVolumeDistribution);
        }
        public void update()
        {
            performanceTrackWatch.start();
            currentPrice = market.getCurrentRealPrice();
            nextTargetPrice = market.getDefaultRealPrice();

            OrderBook orderBook = market.getOrderBook();
            buyOrders.clear();
            sellOrders.clear();
            newOrders.clear();
            buyOrders.addAll(orderBook.getBuyOrders());
            sellOrders.addAll(orderBook.getSellOrders());
            newOrders.addAll(orderBook.getIncommingOrders());


            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                if(pluginData.plugin.isPluginEnabled()) {
                    pluginData.performanceTrackWatch.start();
                    pluginData.plugin.update_internal();
                    nextTargetPrice += pluginData.nextTargetPriceDelta;
                    pluginData.lastUpdateDurationNs = pluginData.performanceTrackWatch.stop();
                }
            }

            lastUpdateDurationNs = performanceTrackWatch.stop();
        }
        public void finalizeUpdate()
        {
            performanceTrackWatch.start();
            lastTargetPrice = nextTargetPrice;
            nextTargetPrice = 0;
            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                if(!pluginData.plugin.isPluginEnabled())
                    continue;

                pluginData.performanceTrackWatch.start();
                // Placing orders
                for (LimitOrder order : pluginData.nextLimitOrders) {
                    market.placeOrder(order);
                }
                if(pluginData.nextCreatingMarketOrderVolume != 0)
                    market.createAndPlaceBotMarketOrder(pluginData.nextCreatingMarketOrderVolume);

                // Manipulating order book volume
                OrderBook orderBook = market.getOrderBook();
                for (MarketPluginInstanceData.VirtualOrderBookVolumeManipulationData manipulation : pluginData.virtualOrderBookVolumeManipulations) {
                    switch (manipulation.type) {
                        case SET:
                                orderBook.setVirtualOrderBookVolume(manipulation.minPrice, manipulation.maxPrice, manipulation.volume);
                                break;
                        case ADD:
                                orderBook.addVirtualOrderBookVolume(manipulation.minPrice, manipulation.maxPrice, manipulation.volume);
                                break;
                    }
                }
                for (MarketPluginInstanceData.VirtualOrderBookVolumeManipulationRealArrayData manipulation : pluginData.virtualOrderBookVolumeArrayManipulations) {
                    switch (manipulation.type) {
                        case SET:
                                orderBook.setVirtualOrderBookVolume(manipulation.backendStartPrice, manipulation.volume);
                                break;
                        case ADD:
                                orderBook.addVirtualOrderBookVolume(manipulation.backendStartPrice, manipulation.volume);
                                break;
                    }
                }


                pluginData.clear();
                pluginData.lastFinalizeDurationNs = pluginData.performanceTrackWatch.stop();

            }
            lastFinalizeDurationNs = performanceTrackWatch.stop();
        }

        public float getDefaultVolumeDistribution(float price)
        {
            defaultVolumeDistributionCalculationTrackWatch.start();
            float totalVolume = 0;
            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                if(pluginData.volumeDistributionFunction != null && pluginData.plugin.isPluginEnabled())
                {
                    totalVolume += pluginData.volumeDistributionFunction.apply(price);
                }
            }
            lastDefaultVolumeDistributionCalculationTimeNs = defaultVolumeDistributionCalculationTrackWatch.stop();
            return totalVolume;
        }

        @Nullable
        public MarketPluginInstanceData createMarketPlugin(String pluginID)
        {
            // Check if pluginID already exists
            if(pluginExists(pluginID))
            {
                warn("MarketPlugin with ID '"+pluginID+"' already exists for market "+market.getTradingPair().getUltraShortDescription());
                return null;
            }
            MarketPlugin pluginInstance = PluginRegistry.createServerMarketPluginInstance(pluginID);
            MarketPluginInstanceData instanceData = new MarketPluginInstanceData(pluginInstance, this);
            pluginsData.add(instanceData);
            instanceData.plugin.setup_interal();
            return instanceData;
        }

        public void removeMarketPlugin(String pluginID)
        {
            int index = getPluginIndex(pluginID);
            if(index == -1)
                return;
            MarketPluginInstanceData  pluginInstance = pluginsData.remove(index);
            pluginInstance.plugin.setPluginEnabled(false);
        }


        /**
         * - Removes existing plugins if they are not contained in the given pluginIDs list
         * - Creates new plugin instances if they are not already existing.
         * @param pluginIDs
         */
        public void setUsedMarketPlugins(List<String> pluginIDs)
        {
            Set<String> currentlyUsedPlugins = new HashSet<>();
            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                currentlyUsedPlugins.add(pluginData.plugin.getPluginTypeID());
            }

            // Remove plugins that are not in pluginIDs list
            for(String currentlyUsed : currentlyUsedPlugins)
            {
                if(!pluginIDs.contains(currentlyUsed))
                {
                    removeMarketPlugin(currentlyUsed);
                }
            }

            // Create plugins
            for(String pluginID : pluginIDs)
            {
                if(!currentlyUsedPlugins.contains(pluginID))
                {
                    MarketPluginInstanceData createdPlugin = createMarketPlugin(pluginID);

                    if(createdPlugin != null) {
                        // Disable new created plugins so they do not impact the market until they are enabled
                        createdPlugin.plugin.setPluginEnabled(false);
                    }
                }
            }

            // Sort the plugins according to the same order of the pluginIDs
            List<MarketPluginInstanceData> oldPluginData = new ArrayList<>(pluginsData);
            pluginsData.clear();
            for(String pluginID : pluginIDs)
            {
                for(MarketPluginInstanceData pluginInstance : oldPluginData)
                {
                    if(pluginID.compareTo(pluginInstance.plugin.getPluginTypeID()) == 0)
                    {
                        pluginsData.add(pluginInstance);
                        break;
                    }
                }
            }

        }
        public int getPluginCount()
        {
            return pluginsData.size();
        }

        boolean pluginExists(String pluginTypeID)
        {
            int index = getPluginIndex(pluginTypeID);
            return index >= 0;
        }
        int getPluginIndex(String pluginTypeID)
        {
            int index = 0;
            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                if(pluginData.plugin.getPluginTypeID().equals(pluginTypeID))
                {
                    return index;
                }
                index++;
            }
            return -1;
        }
        MarketPluginInstanceData getPlugin(int index)
        {
            if(index >= 0 && index < pluginsData.size())
            {
                return pluginsData.get(index);
            }
            return null;
        }
        MarketPlugin getPlugin(String pluginTypeID)
        {
            MarketPluginInstanceData data = getPlugin(getPluginIndex(pluginTypeID));
            if(data == null)
                return null;
            return data.plugin;
        }

        public List<String> getPluginTypes()
        {
            List<String> types = new ArrayList<>();
            for(MarketPluginInstanceData data :  pluginsData)
                types.add(data.plugin.getPluginTypeID());
            return types;
        }

        @Override
        public boolean save(Map<String, ListTag> listTags) {
            ListTag marketTag = new ListTag();
            for(MarketPluginInstanceData pluginData : pluginsData)
            {
                CompoundTag pluginTag = new CompoundTag();
                pluginTag.putString("pluginTypeID", pluginData.plugin.getPluginTypeID());
                if(pluginData.save(pluginTag))
                {
                    marketTag.add(pluginTag);
                }
            }
            UUID tradingPairUUID = market.getTradingPair().getUUID();
            listTags.put(tradingPairUUID.toString(), marketTag);
            return true;
        }

        @Override
        public boolean load(Map<String, ListTag> listTags) {
            String tradingPairUUIDStr = market.getTradingPair().getUUID().toString();
            if(!listTags.containsKey(tradingPairUUIDStr))
                return false;
            ListTag marketTag = listTags.get(tradingPairUUIDStr);
            for(int i = 0; i < marketTag.size(); i++)
            {
                CompoundTag pluginTag = marketTag.getCompound(i);
                if(!pluginTag.contains("pluginTypeID"))
                    continue;
                String pluginTypeID = pluginTag.getString("pluginTypeID");
                MarketPluginInstanceData pluginData = getPlugin(getPluginIndex(pluginTypeID));
                if(pluginData == null)
                {
                    MarketPlugin pluginInstance = PluginRegistry.createServerMarketPluginInstance(pluginTypeID);
                    //if(pluginInstance == null)
                    //{
                    //    warn("Failed to load MarketPlugin with unknown ID '"+pluginTypeID+"' for market "+market.getTradingPair().getUltraShortDescription());
                    //    continue;
                    //}
                    pluginData = new MarketPluginInstanceData(pluginInstance, this);
                    pluginsData.add(pluginData);
                    pluginData.plugin.setup_interal();
                }
                pluginData.load(pluginTag);
            }
            return true;
        }
    }





    private final Path saveFolder;

    /**
     * Group the plugins in the same chunks like the markets
     */
    private final List<List<PluginMarket>> marketsDataChunks = new ArrayList<>();
    private final Map<TradingPair, PluginMarket> allMarketsData = new java.util.HashMap<>();
    private int marketUpdateChunkIndex = 0;



    // Performance tracking
    public final Stopwatch performanceTrackWatch = new Stopwatch();
    public long lastUpdateDurationNs = 0;
    public long lastFinalizeDurationNs = 0;
    private int dbgCounter = 0;
    private double averageUpdateDurationMs = 0;
    private double averageFinalizeDurationMs = 0;



    public ServerPluginManager(Path saveFolder)
    {
        this.saveFolder = saveFolder;



    }

    public void setup()
    {
        //BACKEND_INSTANCES.SERVER_MARKET_MANAGER.
    }

    public void update()
    {
        performanceTrackWatch.start();
        marketUpdateChunkIndex = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarketUpdateChunkIndex();
        if(marketUpdateChunkIndex < marketsDataChunks.size())
        {
            List<PluginMarket> marketChunk = marketsDataChunks.get(marketUpdateChunkIndex);
            for(PluginMarket marketData : marketChunk)
            {
                marketData.update();
            }
        }
        lastUpdateDurationNs = performanceTrackWatch.stop();
        averageUpdateDurationMs = (averageUpdateDurationMs * 0.9) + ((lastUpdateDurationNs / 1_000_000.0) * 0.1);
    }
    public void finalizeUpdate() {
        performanceTrackWatch.start();
        if(marketUpdateChunkIndex < marketsDataChunks.size())
        {
            List<PluginMarket> marketChunk = marketsDataChunks.get(marketUpdateChunkIndex);
            for(PluginMarket marketData : marketChunk)
            {
                marketData.finalizeUpdate();
            }
        }
        lastFinalizeDurationNs = performanceTrackWatch.stop();
        averageFinalizeDurationMs = (averageFinalizeDurationMs * 0.9) + ((lastFinalizeDurationNs / 1_000_000.0) * 0.1);
        dbgCounter++;
        if(dbgCounter >= 20)
        {
            dbgCounter = 0;
            debug(String.format("[%s] Plugins, Average Update Duration: %.3f ms, Average Finalize Duration: %.3f ms", getPluginCount(), averageUpdateDurationMs, averageFinalizeDurationMs));
        }
    }

    public void onServerMarketUpdateChunksChanged(ArrayList<ArrayList<ServerMarket>> chunks)
    {
        marketsDataChunks.clear();
        Set<TradingPair> toRemove = new HashSet<>(allMarketsData.keySet());
        for(ArrayList<ServerMarket> chunk : chunks)
        {
            List<PluginMarket> pluginMarketChunk = new ArrayList<>();
            for(ServerMarket market : chunk)
            {
                PluginMarket marketData = allMarketsData.get(market.getTradingPair());
                if(marketData == null)
                {
                    marketData = new PluginMarket(market);
                    allMarketsData.put(market.getTradingPair(), marketData);
                }
                pluginMarketChunk.add(marketData);
                toRemove.remove(market.getTradingPair());
            }
            marketsDataChunks.add(pluginMarketChunk);
        }

        for(TradingPair pair : toRemove)
        {
            allMarketsData.remove(pair);
        }
    }


    public void createMarketPlugin(TradingPair market, String pluginID)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            error("Can't create MarketPlugin '"+pluginID+"' for unknown market: " + market.getUltraShortDescription());
            return;
        }
        marketData.createMarketPlugin(pluginID);
    }
    public void setUsedMarketPlugins(TradingPair market, List<String> pluginIDs)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            error("Can't set MarketPlugins for unknown market: " + market.getUltraShortDescription());
            return;
        }
        marketData.setUsedMarketPlugins(pluginIDs);
    }
    public void createMarketPlugin(TradingPair market, PluginRegistry.MarketPluginRegistrationObject pluginReg)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            error("Can't create MarketPlugin '"+pluginReg.pluginTypeID+"' for unknown market: " + market.getUltraShortDescription());
            return;
        }
        marketData.createMarketPlugin(pluginReg.pluginTypeID);
    }

    public int getPluginCount(TradingPair market)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            return 0;
        }
        return marketData.getPluginCount();
    }
    public int getPluginCount()
    {
        int total = 0;
        for(PluginMarket marketData : allMarketsData.values())
        {
            total += marketData.getPluginCount();
        }
        return total;
    }
    public boolean encodeClientStreamData(TradingPair pair, String pluginTypeID, FriendlyByteBuf buf)
    {
        PluginMarket marketData = allMarketsData.get(pair);
        if(marketData == null)
        {
            return false;
        }
        MarketPluginInstanceData pluginData = marketData.getPlugin(marketData.getPluginIndex(pluginTypeID));
        if(pluginData == null)
        {
            return false;
        }
        // Write data to buf
        pluginData.plugin.encodeClientStreamData(buf);
        return true;
    }

    public MarketPluginInstanceData getMarketPluginInstanceData(TradingPair market, String pluginTypeID)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            return null;
        }
        return marketData.getPlugin(marketData.getPluginIndex(pluginTypeID));
    }
    public List<String> getPluginTypes(TradingPair market)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            return Collections.emptyList();
        }
        return marketData.getPluginTypes();
    }
    public MarketPlugin getMarketPlugin(TradingPair market, String pluginTypeID)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            return null;
        }
        MarketPluginInstanceData pluginData = marketData.getPlugin(marketData.getPluginIndex(pluginTypeID));
        if(pluginData == null)
        {
            return null;
        }
        return pluginData.plugin;
    }

    public boolean save() {
        boolean success = true;
        Map<String, ListTag> dataMapList = new HashMap<>();
        for(PluginMarket marketData : allMarketsData.values())
        {
            if(!marketData.save(dataMapList))
            {
                error("Failed to save plugin market data for market: " + marketData.market.getTradingPair().getUltraShortDescription());
                success = false;
            }
        }
        success &= BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveDataCompoundListMap(saveFolder, dataMapList);
        return success;
    }


    public boolean load() {
        boolean success = true;
        Map<String, ListTag> dataMapList = BACKEND_INSTANCES.SERVER_DATA_HANDLER.readDataCompoundListMap(saveFolder);
        if(dataMapList == null)
        {
            warn("No saved plugin data found.");
            return true;
        }
        for(PluginMarket marketData : allMarketsData.values())
        {
            if(!marketData.load(dataMapList))
            {
                error("Failed to load plugin market data for market: " + marketData.market.getTradingPair().getUltraShortDescription());
                success = false;
            }
        }
        //setup();
        return false;
    }



    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ServerPluginManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPluginManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPluginManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ServerPluginManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ServerPluginManager] " + msg);
    }
}
