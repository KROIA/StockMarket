package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.market.core.MatchingEngine;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ServerMarket implements ServerSaveable, IServerMarket {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        Orderbook.setBackend(backend);
        MatchingEngine.setBackend(backend);
    }


    private final ItemID itemID;
    private final Orderbook orderbook;
    private final MatchingEngine matchingEngine;
    private boolean marketOpen;
    private long currentMarketPrice;
    private @Nullable Function<Long, Float> defaultVolumeProviderFunction;

    private long candleStartTime = System.currentTimeMillis();
    private long candleOpenPrice;
    private long candleHighPrice;
    private long candleLowPrice;

    /**
     * Temporary order buffers for collecting order async and only process the order on update
     * These order are not saved to NBT
     */
    private final PriorityQueue<Order> buyMarketOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellMarketOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<Order> buyLimitOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellLimitOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<InterMarketOrder> interMarket_LimitBuyOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));
    private final PriorityQueue<InterMarketOrder> interMarket_MarketBuyOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));


    public ServerMarket(ItemID itemID, @Nullable Function<Long, Float> volumeProvider, long currentMarketPrice)
    {
        this.defaultVolumeProviderFunction =  volumeProvider;
        this.itemID = itemID;
        this.orderbook = new Orderbook(itemID,
                                        this::onOrderConsumed, this::onOrderConsumed,
                                        this::onOrderCanceled, this::onOrderCanceled,
                                        this::defaultVolumeProvider);
        this.matchingEngine = new MatchingEngine(this.itemID, this.orderbook,
                buyMarketOrders_inputBuffer,
                sellMarketOrders_inputBuffer,
                buyLimitOrders_inputBuffer,
                sellLimitOrders_inputBuffer,
                interMarket_LimitBuyOrders_inputBuffer,
                interMarket_MarketBuyOrders_inputBuffer,
                this::onOrderConsumed, this::onOrderConsumed,
                this::onOrderCanceled, this::onOrderCanceled,
                this::onPriceChanged);

        this.marketOpen = true;
        this.currentMarketPrice = currentMarketPrice;
        this.orderbook.setCurrentMarketPrice(currentMarketPrice);
        this.orderbook.resetVirtualVolumeDistribution();
        candleOpenPrice = this.currentMarketPrice;
        candleHighPrice = this.currentMarketPrice;
        candleLowPrice = this.currentMarketPrice;
    }

    public ServerMarket(ItemID itemID)
    {
        this(itemID, null, 10);
    }

    @Override
    public void test_resetVirtualVolumeDistribution()
    {
        orderbook.resetVirtualVolumeDistribution();
    }
    @Override
    public void test_setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        this.orderbook.setCurrentMarketPrice(currentMarketPrice);
    }
    @Override
    public void test_clearOrderbook()
    {
        orderbook.clear();
        orderbook.resetVirtualVolumeDistribution();
    }
    @Override
    public void test_setDefaultVolumeProviderFunction(Function<Long, Float> defaultVolumeProviderFunction)
    {
        this.defaultVolumeProviderFunction = defaultVolumeProviderFunction;
    }
    @Override
    public void test_resetVirtualOrderBookVolume()
    {
        orderbook.resetVirtualVolumeDistribution();
    }





    @Override
    public ItemID getItemID()
    {
        return itemID;
    }
    @Override
    public ItemID getItemIDAsync() {
        return itemID;
    }





    @Override
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
    @Override
    public CompletableFuture<Long> getCurrentMarketPriceAsync() {
        return CompletableFuture.completedFuture(currentMarketPrice);
    }






    @Override
    public long getCurrentTime()
    {
        return System.currentTimeMillis();
    }
    @Override
    public CompletableFuture<Long> getCurrentTimeAsync() {
        return CompletableFuture.completedFuture(getCurrentTime());
    }






    @Override
    public long getVolume(long price)
    {
        return orderbook.getVolumeRounded(price);
    }
    @Override
    public CompletableFuture<Long> getVolumeAsync(long price) {
        return CompletableFuture.completedFuture(getVolume(price));
    }



    @Override
    public float getVolume(long startPrice, long endPrice)
    {
        return orderbook.getVolume(startPrice, endPrice);
    }

    @Override
    public CompletableFuture<Float> getVolumeAsync(long startPrice, long endPrice)
    {
        return CompletableFuture.completedFuture(getVolume(startPrice, endPrice));
    }






    // Buffers the incoming order, execution will take place in the update()
    @Override
    public boolean putOrder(Order order)
    {
        if(order.isFilled() || !marketOpen)
            return false;
        if(!order.getItemID().equals(itemID))
            return false; // Wrong stockmarket for this order
        if(order.isBuyOrder())
        {
            if(order.isMarketOrder())
                buyMarketOrders_inputBuffer.add(order);
            else
                buyLimitOrders_inputBuffer.add(order);
        }
        else
        {
            if(order.isMarketOrder())
                sellMarketOrders_inputBuffer.add(order);
            else
                sellLimitOrders_inputBuffer.add(order);
        }
        return true;
    }
    @Override
    public CompletableFuture<Boolean> putOrderAsync(Order order) {
        return CompletableFuture.completedFuture(putOrder(order));
    }





    // Buffers the incoming order, execution will take place in the update()
    @Override
    public boolean putOrder(InterMarketOrder order)
    {
        if(order.isFilled() || !marketOpen)
            return false;
        if(!order.getBuyItemID().equals(itemID))
            return false; // Wrong stockmarket for this order

        if(order.isMarketOrder())
            interMarket_MarketBuyOrders_inputBuffer.add(order);
        else
            interMarket_LimitBuyOrders_inputBuffer.add(order);
        return true;
    }
    @Override
    public CompletableFuture<Boolean> putOrderAsync(InterMarketOrder order) {
        return CompletableFuture.completedFuture(putOrder(order));
    }




    @Override
    public List<Order> getLimitOrders()
    {
        return orderbook.getLimitOrders();
    }
    @Override
    public CompletableFuture<List<Order>> getLimitOrdersAsync() {
        return CompletableFuture.completedFuture(getLimitOrders());
    }





    @Override
    public boolean isMarketOpen()
    {
        return marketOpen;
    }
    @Override
    public CompletableFuture<Boolean> isMarketOpenAsync() {
        return CompletableFuture.completedFuture(marketOpen);
    }





    @Override
    public boolean setMarketOpen(boolean marketOpen)
    {
        this.marketOpen = marketOpen;
        return true;
    }
    @Override
    public CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen) {
        return CompletableFuture.completedFuture(setMarketOpen(marketOpen));
    }






    @Override
    public MarketPriceStruct getCurrentMarketPriceStruct()
    {
        return new MarketPriceStruct(itemID.getShort(), candleOpenPrice, candleLowPrice, candleHighPrice, candleStartTime);
    }
    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync() {
        return CompletableFuture.completedFuture(getCurrentMarketPriceStruct());
    }





    @Override
    public MarketPriceStruct getCurrentMarketPriceStructAndReset()
    {
        MarketPriceStruct  currentMarketPriceStruct = new MarketPriceStruct(itemID.getShort(), candleOpenPrice, candleLowPrice, candleHighPrice, candleStartTime);
        candleStartTime =  System.currentTimeMillis();
        candleOpenPrice = this.currentMarketPrice;
        candleHighPrice = this.currentMarketPrice;
        candleLowPrice = this.currentMarketPrice;
        return currentMarketPriceStruct;
    }
    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync() {
        return CompletableFuture.completedFuture(getCurrentMarketPriceStructAndReset());
    }









    @Override
    public void update()
    {
        if(!marketOpen)
            return;

        orderbook.setCurrentMarketPrice(currentMarketPrice);
        matchingEngine.update(currentMarketPrice);

        // Update the current candle
        candleLowPrice = Math.min(candleLowPrice, currentMarketPrice);
        candleHighPrice = Math.max(candleHighPrice, currentMarketPrice);
    }

    /**
     * Gets called when the provided order has been consumed
     * @param order
     */
    private void onOrderConsumed(InterMarketOrder order)
    {

    }
    /**
     * Gets called when the provided order has been consumed
     * @param order
     */
    private void onOrderConsumed(Order order)
    {

    }

    /**
     * Gets called when the provided order has been canceled
     * It may be partially filled
     * @param order
     */
    private void onOrderCanceled(InterMarketOrder order)
    {

    }
    /**
     * Gets called when the provided order has been canceled
     * It may be partially filled
     * @param order
     */
    private void onOrderCanceled(Order order)
    {

    }


    private void onPriceChanged(long newPrice)
    {
        currentMarketPrice = newPrice;
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }


    private float defaultVolumeProvider(long price)
    {
        if(defaultVolumeProviderFunction != null)
            return defaultVolumeProviderFunction.apply(price);
        float volume = Math.min(10, Math.max(-10, currentMarketPrice - price));
        return volume;
    }

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        CompoundTag orderbookTag = new CompoundTag();
        success &= orderbook.save(orderbookTag);
        tag.put("orderbook", orderbookTag);
        tag.putLong("currentMarketPrice", currentMarketPrice);



        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;

        if(tag.contains("orderbook"))
        {
            CompoundTag virtualOrderbookTag = tag.getCompound("orderbook");
            success &= orderbook.load(virtualOrderbookTag);
        }
        else
        {
            success = false;
            error("Can't load Orderbook from NBT tag");
        }

        if(tag.contains("currentMarketPrice"))
        {
            currentMarketPrice =  tag.getLong("currentMarketPrice");
        }


        return success;
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerMarket:"+itemID+"]: "+message);
    }


}
