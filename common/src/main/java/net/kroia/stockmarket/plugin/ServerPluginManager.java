package net.kroia.stockmarket.plugin;

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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

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
    private class MarketPluginInstanceData
    {
        private class VirtualOrderBookVolumeManipulationData
        {
            enum ManipulationType
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
        private class VirtualOrderBookVolumeManipulationRealArrayData
        {
            enum ManipulationType
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

        private final MarketPluginInterface marketPluginInterface = new MarketPluginInterface();
        public final MarketPlugin plugin;
        public final List<VirtualOrderBookVolumeManipulationData> virtualOrderBookVolumeManipulations = new java.util.ArrayList<>();
        public final List<VirtualOrderBookVolumeManipulationRealArrayData> virtualOrderBookVolumeArrayManipulations = new java.util.ArrayList<>();
        public Function<Float, Float> volumeDistributionFunction = null;
        public float nextTargetPriceDelta = 0;
        public List<LimitOrder> nextLimitOrders = new java.util.ArrayList<>();
        public float nextCreatingMarketOrderVolume = 0;


        // Performance tracking
        public final Stopwatch performanceTrackWatch = new Stopwatch();
        public long lastUpdateDurationNs = 0;
        public long lastFinalizeDurationNs = 0;


        public final PluginMarket marketData;


        public MarketPluginInstanceData(MarketPlugin plugin, PluginMarket marketData) {
            this.plugin = plugin;
            this.marketData = marketData;
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
    }


    private class PluginMarket
    {
        private final IServerMarket market;
        private final Map<String, MarketPluginInstanceData> pluginsData = new HashMap<>(); // pluginTypeID -> data

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

            OrderBook orderBook = market.getOrderBook();
            buyOrders.clear();
            sellOrders.clear();
            newOrders.clear();
            buyOrders.addAll(orderBook.getBuyOrders());
            sellOrders.addAll(orderBook.getSellOrders());
            newOrders.addAll(orderBook.getIncommingOrders());

            for(MarketPluginInstanceData pluginData : pluginsData.values())
            {
                pluginData.performanceTrackWatch.start();
                pluginData.plugin.update_internal();
                nextTargetPrice += pluginData.nextTargetPriceDelta;
                pluginData.lastUpdateDurationNs = pluginData.performanceTrackWatch.stop();
            }

            lastUpdateDurationNs = performanceTrackWatch.stop();
        }
        public void finalizeUpdate()
        {
            performanceTrackWatch.start();
            lastTargetPrice = nextTargetPrice;
            nextTargetPrice = 0;
            for(MarketPluginInstanceData pluginData : pluginsData.values())
            {
                pluginData.performanceTrackWatch.start();
                // Placing orders
                for(LimitOrder order : pluginData.nextLimitOrders)
                {
                    market.placeOrder(order);
                }
                market.createAndPlaceBotMarketOrder(pluginData.nextCreatingMarketOrderVolume);

                // Manipulating order book volume
                OrderBook orderBook = market.getOrderBook();
                for(MarketPluginInstanceData.VirtualOrderBookVolumeManipulationData manipulation : pluginData.virtualOrderBookVolumeManipulations)
                {
                    switch (manipulation.type)
                    {
                        case SET -> orderBook.setVirtualOrderBookVolume(manipulation.minPrice, manipulation.maxPrice, manipulation.volume);
                        case ADD -> orderBook.addVirtualOrderBookVolume(manipulation.minPrice, manipulation.maxPrice, manipulation.volume);
                    }
                }
                for(MarketPluginInstanceData.VirtualOrderBookVolumeManipulationRealArrayData manipulation : pluginData.virtualOrderBookVolumeArrayManipulations)
                {
                    switch (manipulation.type)
                    {
                        case SET -> orderBook.setVirtualOrderBookVolume(manipulation.backendStartPrice, manipulation.volume);
                        case ADD -> orderBook.addVirtualOrderBookVolume(manipulation.backendStartPrice, manipulation.volume);
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
            for(MarketPluginInstanceData pluginData : pluginsData.values())
            {
                if(pluginData.volumeDistributionFunction != null)
                {
                    totalVolume += pluginData.volumeDistributionFunction.apply(price);
                }
            }
            lastDefaultVolumeDistributionCalculationTimeNs = defaultVolumeDistributionCalculationTrackWatch.stop();
            return totalVolume;
        }

        public void createMarketPlugin(String pluginID, String name)
        {
            // Check if pluginID already exists
            if(pluginsData.containsKey(pluginID))
            {
                warn("MarketPlugin with ID '"+pluginID+"' already exists for market "+market.getTradingPair().getUltraShortDescription());
                return;
            }
            MarketPlugin pluginInstance = PluginRegistry.createServerPluginInstance(pluginID);
            pluginInstance.setName(name);
            MarketPluginInstanceData instanceData = new MarketPluginInstanceData(pluginInstance, this);
            pluginsData.put(pluginID, instanceData);
            instanceData.plugin.setup_interal();
        }
        public int getPluginCount()
        {
            return pluginsData.size();
        }
    }





    private final Path saveFolder;
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


    public void createMarketPlugin(TradingPair market, String pluginID, String name)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            error("Can't create MarketPlugin '"+pluginID+"' for unknown market: " + market.getUltraShortDescription());
            return;
        }
        marketData.createMarketPlugin(pluginID, name);
    }
    public void createMarketPlugin(TradingPair market, PluginRegistry.MarketPluginRegistrationObject pluginReg, String name)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            error("Can't create MarketPlugin '"+pluginReg.pluginTypeID+"' for unknown market: " + market.getUltraShortDescription());
            return;
        }
        marketData.createMarketPlugin(pluginReg.pluginTypeID, name);
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
        MarketPluginInstanceData pluginData = marketData.pluginsData.get(pluginTypeID);
        if(pluginData == null)
        {
            return false;
        }
        // Write data to buf
        pluginData.plugin.encodeClientStreamData(buf);
        return true;
    }
    public List<String> getPluginTypes(TradingPair market)
    {
        PluginMarket marketData = allMarketsData.get(market);
        if(marketData == null)
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(marketData.pluginsData.keySet());
    }

    public boolean save() {
        return false;
    }


    public boolean load() {


        setup();
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
