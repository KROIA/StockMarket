package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.orders.InterMarketOrder;
import net.kroia.stockmarket.market.orders.Order;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class Orderbook implements ServerSaveable
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        VirtualOrderbook.setBackend(backend);
    }
    public static class LongPair {
        public long first;
        public long second;
    }

    private static final int TIMEOUT_COUNT = 10000;




    private final ItemID itemID;
    private long currentMarketPrice = 0;

    private final VirtualOrderbook virtualOrderbook;
    private final PriorityQueue<Order>  buyLimitOrders = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellLimitOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<InterMarketOrder> interMarketBuyOrders = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));


    private final @NotNull Consumer<Order> consumedOrderCallback;
    private final @NotNull Consumer<InterMarketOrder> consumedInterMarketOrderCallback;
    private final @NotNull Consumer<Order> cancelOrderCallback;
    private final @NotNull Consumer<InterMarketOrder> cancelInterMarketOrderCallback;

    public Orderbook(@NotNull ItemID id,
                     @NotNull Consumer<Order> consumedOrderCallback,
                     @NotNull Consumer<InterMarketOrder> consumedInterMarketOrderCallback,
                     @NotNull Consumer<Order> cancelOrderCallback,
                     @NotNull Consumer<InterMarketOrder> cancelInterMarketOrderCallback,
                     @Nullable Function<Long, Float> volumeProvider)
    {
        this.itemID = id;
        this.consumedOrderCallback = consumedOrderCallback;
        this.consumedInterMarketOrderCallback = consumedInterMarketOrderCallback;
        this.cancelOrderCallback = cancelOrderCallback;
        this.cancelInterMarketOrderCallback = cancelInterMarketOrderCallback;
        this.virtualOrderbook = new VirtualOrderbook();
        this.virtualOrderbook.setDefaultVolumeProvider(volumeProvider);
    }

    //public void setDefaultVolumeProvider(@Nullable Function<Long, Float> volumeProvider)
    //{
    //    virtualOrderbook.setDefaultVolumeProvider(volumeProvider);
    //}
    public void resetVirtualVolumeDistribution()
    {
        virtualOrderbook.resetVolumeDistribution();
    }

    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        virtualOrderbook.setCurrentMarketPrice(currentMarketPrice);
    }


    public boolean putOrder(Order order)
    {
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
        interMarketBuyOrders.add(order);
        return true;
    }

    public PriorityQueue<Order> getBuyLimitOrders()
    {
        return buyLimitOrders;
    }
    public PriorityQueue<Order> getSellLimitOrders()
    {
        return sellLimitOrders;
    }
    PriorityQueue<InterMarketOrder>  getInterMarketBuyOrders()
    {
        return interMarketBuyOrders;
    }
    public void clear()
    {
        buyLimitOrders.clear();
        sellLimitOrders.clear();
        interMarketBuyOrders.clear();
    }




    public float getVolume(long price)
    {
        float volume = virtualOrderbook.getVolume(price);

        if(currentMarketPrice > price) {
            // The searched price is inside the buy orders since the market price is higher than the searched price
            for(Order order : buyLimitOrders)
            {
                long startPrice = order.getStartPrice();
                if(startPrice == price) {
                    volume += order.getRemainingVolume();
                    break;
                }
            }
        }
        else if(currentMarketPrice < price){
            // The searched price is inside the sell orders
            for (Order order : sellLimitOrders) {
                if (order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                    break;
                }
            }
        }
        else
        {
            // Can be a sell or a buy order, laying exactly on the current price
            if(!sellLimitOrders.isEmpty())
            {
                Order order = sellLimitOrders.peek();
                if(order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                }
            }
            else if(!buyLimitOrders.isEmpty())
            {
                Order order = buyLimitOrders.peek();
                if(order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                }
            }
        }
        return volume;
    }
    public long getVolumeRounded(long price)
    {
        long volume = VirtualOrderbook.roundConservative(getVolume(price));
        if(currentMarketPrice > price) {
            // The searched price is inside the buy orders since the market price is higher than the searched price
            for(Order order : buyLimitOrders)
            {
                long startPrice = order.getStartPrice();
                if(startPrice == price) {
                    volume += order.getRemainingVolume();
                    break;
                }
            }
        }
        else if(currentMarketPrice < price){
            // The searched price is inside the sell orders
            for (Order order : sellLimitOrders) {
                if (order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                    break;
                }
            }
        }
        else
        {
            // Can be a sell or a buy order, laying exactly on the current price
            if(!sellLimitOrders.isEmpty())
            {
                Order order = sellLimitOrders.peek();
                if(order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                }
            }
            else if(!buyLimitOrders.isEmpty())
            {
                Order order = buyLimitOrders.peek();
                if(order.getStartPrice() == price) {
                    volume += order.getRemainingVolume();
                }
            }
        }
        return volume;
    }

    public float getVirtualVolume(long price)
    {
        return virtualOrderbook.getVolume(price);
    }
    public long getVirtualVolumeRounded(long price)
    {
        return VirtualOrderbook.roundConservative(getVirtualVolume(price));
    }


    public float getVolume(long startPrice, long endPrice)
    {
        float volume = virtualOrderbook.getVolume(startPrice, endPrice); // = virtualOrderbook.getVolume(price);
        for(Order order : buyLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume();
            else
                break;
        }
        for(Order order : sellLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume();
            else
                break;
        }
        return volume;
    }
    public float getVolumeRounded(long startPrice, long endPrice)
    {
        long volume = virtualOrderbook.getVolumeRounded(startPrice, endPrice); // = virtualOrderbook.getVolume(price);
        for(Order order : buyLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume();
            else
                break;
        }
        for(Order order : sellLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume();
            else
                break;
        }
        return volume;
    }

    /**
     * Retains the money-value of the volume at the given price
     * Calculated using: Floor(volume * price)
     * @param price
     * @return money
     */
    public float getCapital(long price)
    {
        float volume = getVolume(price);
        return volume * price;
    }
    public long getCapitalRounded(long price)
    {
        long volume = VirtualOrderbook.roundConservative(getVolume(price));
        return volume * price;
    }


    public float getCapital(long startPrice, long endPrice)
    {
        float capital = virtualOrderbook.getCapital(startPrice, endPrice); // = virtualOrderbook.getVolume(price);
        for(Order order : buyLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                capital += order.getRemainingVolume() * orderPrice;
            else
                break;
        }
        for(Order order : sellLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                capital += order.getRemainingVolume() * orderPrice;
            else
                break;
        }
        return capital;
    }
    public long getCapitalRounded(long startPrice, long endPrice)
    {
        long volume = virtualOrderbook.getCapitalRounded(startPrice, endPrice); // = virtualOrderbook.getVolume(price);
        for(Order order : buyLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume() *  orderPrice;
            else
                break;
        }
        for(Order order : sellLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(orderPrice >= startPrice && orderPrice <= endPrice)
                volume += order.getRemainingVolume() *  orderPrice;
            else
                break;
        }
        return volume;
    }


    /**
     * Returns the market price that will be if the given volume gets
     * consumed by the orders.
     * @param volume can be positive for consuming the sell orders
     *               can be negative for consuming the buy orders
     * @param resultOut a pair object, who's elements are filled by the function call
     *                  resultOut.first contains the new market price
     *                  resultOut.second contains the amount of money that would flow out or into the market.
     *                                   For positive volume -> consuming sell orders -> resultOut.second is negative.
     *                                   For negative volume -> consuming buy orders -> resultOut.second is positive.
     * @return true if success
     *         false if run into a timeout, meaning there is not enough volume to consume without increasing the
     *               price too much
     */
    public boolean getPriceWhenConsumingVolume(long volume, @NotNull LongPair resultOut)
    {
        long newMarketPrice = currentMarketPrice;
        long transferedMoney = 0;
        int timeoutCount = TIMEOUT_COUNT; // If it runs into that timeout level
                                          // It means that the price would move by this amount and more to fill the volume
                                          // If that is legit, increase this value to not encounter problems.


        if(volume > 0)
        {
            // Consuming the sell orders -> moving price up

            // max only necessary for the current market price since there can be a sell and a buy order
            // We only want sell orders
            // Flipping the orderbook volume to be positive for easy comparison with the function
            // provided volume variable
            long currentVolume = Math.max(0, -getVolumeRounded(newMarketPrice));
            do
            {
                if(currentVolume >= volume)
                {
                    // Can be fully consumed at this price level
                    transferedMoney -= volume * newMarketPrice;
                    resultOut.first = newMarketPrice;
                    resultOut.second = transferedMoney;
                    return true;
                }
                else
                {
                    // Fully consume the available market volume
                    transferedMoney -= currentVolume * newMarketPrice;
                    volume -= currentVolume;
                    newMarketPrice++;
                }
                timeoutCount--;
                currentVolume = -getVolumeRounded(newMarketPrice);
            }while(timeoutCount > 0);
        }
        else
        {
            // Consuming the buy orders -> moving price down
            // Flipping the orderbook volume to be negatice for easy comparison with the function
            // provided volume variable
            long currentVolume = Math.min(0, -getVolumeRounded(newMarketPrice));
            do
            {
                if(currentVolume <= volume)
                {
                    // Can be fully consumed
                    transferedMoney -= volume * newMarketPrice;
                    resultOut.first = newMarketPrice;
                    resultOut.second = transferedMoney;
                    return true;
                }
                else
                {
                    // Fully consume the available market volume
                    transferedMoney -= currentVolume * newMarketPrice;
                    volume -= currentVolume;
                    newMarketPrice--;
                }
                timeoutCount--;
                currentVolume = -getVolumeRounded(newMarketPrice);
            }while(timeoutCount > 0);
        }

        warn("Timeout reached on function call: getPriceWhenConsumingVolume("+volume+")");
        resultOut.first = currentMarketPrice;
        resultOut.second = 0;
        return false;
    }



    public List<Order> getBuyOrders(long startPrice, long endPrice)
    {
        List<Order> orders = new ArrayList<>();
        for(Order order : buyLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(startPrice <= orderPrice && orderPrice <= endPrice)
                orders.add(order);
        }
        return orders;
    }

    public List<Order> getSellOrders(long startPrice, long endPrice)
    {
        List<Order> orders = new ArrayList<>();
        for(Order order : sellLimitOrders)
        {
            long orderPrice = order.getStartPrice();
            if(startPrice <= orderPrice && orderPrice <= endPrice)
                orders.add(order);
        }
        return orders;
    }


    public long fillVirtual(long price, long volume)
    {
        long currentVolume = virtualOrderbook.getVolumeRounded(price, price);
        if(currentVolume > 0 && volume < 0)
        {
            long newVolume = currentVolume + volume;
            if(newVolume < 0)
                newVolume = 0;

            virtualOrderbook.setVolume(price, newVolume);
            return newVolume - currentVolume;
        }
        else if(currentVolume < 0 && volume > 0)
        {
            long newVolume = currentVolume + volume;
            if(newVolume > 0)
                newVolume = 0;

            virtualOrderbook.setVolume(price, newVolume);
            return newVolume - currentVolume;
        }
        return 0;
    }


    public boolean removeOrder(Order order)
    {
        if(order.isBuyOrder())
            return buyLimitOrders.remove(order);
        else
            return sellLimitOrders.remove(order);
    }
    public boolean removeOrder(InterMarketOrder order)
    {
        return interMarketBuyOrders.remove(order);
    }


    /*public long fillVolume(long price, long volume)
    {
        if(volume > 0)
        {
            // use positive volume
            List<Order> orders = getSellOrders(price, price);
            for(Order order : orders)
            {
                long pendingVolume = order.getRemainingVolume(); // negative value
                long newPendingVolume = pendingVolume + volume;
                if(pendingVolume * newPendingVolume >= 0)
                {
                    // Same sign

                }
            }
        }
    }*/

    /*public long fillVolume(long volume)
    {
        long marketPrice = currentMarketPrice;
        if(volume > 0)
        {
            // fill sell orders
            int timeout = TIMEOUT_COUNT;
            do {
                List<Order> orders = getSellOrders(marketPrice, marketPrice);
                for(Order order : orders)
                {

                }


                timeout--;
            }while(timeout > 0);
        }
        else
        {

        }
        return marketPrice;
    }*/



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



    private void orderConsumed(Order order)
    {
        consumedOrderCallback.accept(order);
    }
    private void orderConsumed(InterMarketOrder order)
    {
        consumedInterMarketOrderCallback.accept(order);
    }
    private void orderCanceled(Order order)
    {
        cancelOrderCallback.accept(order);
    }
    private void orderCanceled(InterMarketOrder order)
    {
        cancelInterMarketOrderCallback.accept(order);
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[Orderbook:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[Orderbook:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[Orderbook]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[Orderbook:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[Orderbook:"+itemID+"]: "+message);
    }
}
