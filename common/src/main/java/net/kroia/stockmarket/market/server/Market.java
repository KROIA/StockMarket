package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.orders.InterMarketOrder;
import net.kroia.stockmarket.market.orders.Order;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Function;

public class Market implements ServerSaveable {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
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

    /**
     * Temporary order buffers for collecting orders async and only process the orders on update
     * These orders are not saved to NBT
     */
    private final PriorityQueue<Order> buyMarketOders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellMarketOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<Order> buyLimitOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellLimitOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<InterMarketOrder> interMarket_LimitBuyOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));
    private final PriorityQueue<InterMarketOrder> interMarket_MarketBuyOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));


    public Market(ItemID itemID, @Nullable Function<Long, Float> volumeProvider, long currentMarketPrice)
    {
        this.defaultVolumeProviderFunction =  volumeProvider;
        this.itemID = itemID;
        this.orderbook = new Orderbook(itemID,
                                        this::onOrderConsumed, this::onOrderConsumed,
                                        this::onOrderCanceled, this::onOrderCanceled,
                                        this::defaultVolumeProvider);
        this.matchingEngine = new MatchingEngine(this.itemID, this.orderbook,
                buyMarketOders_inputBuffer,
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
    }
    public Market(ItemID itemID)
    {
        this(itemID, null, 10);
    }

    public void test_resetVirtualVolumeDistribution()
    {
        orderbook.resetVirtualVolumeDistribution();
    }
    public void test_setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        this.orderbook.setCurrentMarketPrice(currentMarketPrice);
    }
    public void test_clearOrderbook()
    {
        orderbook.clear();
        orderbook.resetVirtualVolumeDistribution();
    }
    public void test_setDefaultVolumeProviderFunction(Function<Long, Float> defaultVolumeProviderFunction)
    {
        this.defaultVolumeProviderFunction = defaultVolumeProviderFunction;
    }

    public ItemID getItemID()
    {
        return itemID;
    }
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }

    // Buffers the incoming order, execution will take place in the update()
    public boolean putOrder(Order order)
    {
        if(order.isFilled() || !marketOpen)
            return false;
        if(!order.getItemID().equals(itemID))
            return false; // Wrong market for this order
        if(order.isBuyOrder())
        {
            if(order.isMarketOrder())
                buyMarketOders_inputBuffer.add(order);
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
    // Buffers the incoming order, execution will take place in the update()
    public boolean putOrder(InterMarketOrder order)
    {
        if(order.isFilled() || !marketOpen)
            return false;
        if(!order.getBuyItemID().equals(itemID))
            return false; // Wrong market for this order

        if(order.isMarketOrder())
            interMarket_MarketBuyOrders_inputBuffer.add(order);
        else
            interMarket_LimitBuyOrders_inputBuffer.add(order);
        return true;
    }

    public boolean isMarketOpen()
    {
        return marketOpen;
    }
    public void setMarketOpen(boolean marketOpen)
    {
        this.marketOpen = marketOpen;
    }

    public void update()
    {
        if(!marketOpen)
            return;
        for(Order order : buyLimitOrders_inputBuffer)
            orderbook.putOrder(order);
        for(Order order : sellLimitOrders_inputBuffer)
            orderbook.putOrder(order);
        buyLimitOrders_inputBuffer.clear();
        sellLimitOrders_inputBuffer.clear();

        orderbook.setCurrentMarketPrice(currentMarketPrice);
        matchingEngine.update(currentMarketPrice);
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



        return success;
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[Market:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[Market:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[Market:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[Market:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[Market:"+itemID+"]: "+message);
    }
}
