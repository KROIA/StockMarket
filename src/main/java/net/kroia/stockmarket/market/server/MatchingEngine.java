package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;

/*
    * The MatchingEngine class is responsible for matching buy and sell orders.
 */
public class MatchingEngine implements ServerSaveable {
    private int price;
    private int tradeVolume;

    // Create a sorted queue for buy and sell orders, sorted by price.
    private final PriorityQueue<LimitOrder> limitBuyOrders = new PriorityQueue<>((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
    private final PriorityQueue<LimitOrder> limitSellOrders = new PriorityQueue<>(Comparator.comparingDouble(LimitOrder::getPrice));

    public MatchingEngine(int initialPrice) {
        this.price = initialPrice;
        tradeVolume = 0;
    }


    public void addOrder(Order order)
    {
        if(order.getAmount() == 0)
        {
            order.markAsProcessed();
            return;
        }
        if (order instanceof LimitOrder limitOrder)
        {
            limitOrder.setAveragePrice(limitOrder.getPrice());
            if(!processSpotOrder(limitOrder))
            {
                if (limitOrder.isBuy())
                {
                    // Add the spot order to the buyOrders queue
                    limitBuyOrders.add(limitOrder);
                }
                else
                {
                    // Add the spot order to the sellOrders queue
                    limitSellOrders.add(limitOrder);
                }
                limitOrder.notifyPlayer();
            }
        } else if (order instanceof MarketOrder marketOrder) {
            processMarketOrder(marketOrder);
            //marketOrder.notifyPlayer();
        } else {
            throw new IllegalArgumentException("Invalid order type");
        }
    }

    public ArrayList<Order> getOrders()
    {
        ArrayList<Order> orders = new ArrayList<>();
        orders.addAll(limitBuyOrders);
        orders.addAll(limitSellOrders);
        return orders;
    }
    private boolean processMarketOrder(MarketOrder marketOrder)
    {
        // Process the market order
        int amount = -marketOrder.getAmount();
        PriorityQueue<LimitOrder> limitOrders = marketOrder.isBuy() ? limitSellOrders : limitBuyOrders;
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        int volume = 0;
        for(LimitOrder limitOrder : limitOrders)
        {
            int currentAmount = amount;
            amount = limitOrder.fill(amount);
            int deltaVolume = currentAmount - amount;
            volume += deltaVolume;
            price = limitOrder.getPrice();
            if(limitOrder.isFilled())
                toRemove.add(limitOrder);
            if(amount == 0)
            {
                marketOrder.changeAveragePrice(deltaVolume, price);
                marketOrder.fill(marketOrder.getAmount());
                break;
            }

        }
        limitOrders.removeAll(toRemove);
        tradeVolume += Math.abs(volume);
        if(amount == 0)
            return true;

        marketOrder.markAsInvalid("Not enough "+(marketOrder.isBuy()?"sell":"buy")+ " orders to fill the market order");
        StockMarketMod.LOGGER.warn("Market order not fully processed: {}", marketOrder);
        return false;
    }
    private boolean processSpotOrder(LimitOrder spotOrder)
    {
        // Process the spot order
        int amount = -spotOrder.getAmount();
        PriorityQueue<LimitOrder> spotOrders = spotOrder.isBuy() ? limitSellOrders : limitBuyOrders;
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        for(LimitOrder otherOrder : spotOrders)
        {
            if(otherOrder.getPrice() == spotOrder.getPrice())
            {
                amount = otherOrder.fill(amount);
                price = otherOrder.getPrice();
                if(otherOrder.isFilled())
                    toRemove.add(otherOrder);
            }
            else
            {
                if(spotOrder.isBuy() && spotOrder.getPrice() > otherOrder.getPrice())
                {
                    amount = otherOrder.fill(amount);
                    price = otherOrder.getPrice();
                    if(otherOrder.isFilled())
                        toRemove.add(otherOrder);
                }
                else if(spotOrder.isSell() && spotOrder.getPrice() < otherOrder.getPrice())
                {
                    amount = otherOrder.fill(amount);
                    price = otherOrder.getPrice();
                    if(otherOrder.isFilled())
                        toRemove.add(otherOrder);
                }
            }

            if(amount == 0)
            {
                break;
            }
        }
        int deltaVolume = Math.abs(spotOrder.getAmount()+amount);


        spotOrder.changeAveragePrice(deltaVolume, price);
        spotOrder.fill(spotOrder.getAmount()+amount);
        tradeVolume += deltaVolume;
        spotOrders.removeAll(toRemove);
        return amount == 0;
    }

    public ArrayList<LimitOrder> getLimitBuyOrders() {
        return new ArrayList<>(limitBuyOrders);
    }
    public ArrayList<LimitOrder> getLimitSellOrders() {
        return new ArrayList<>(limitSellOrders);
    }

    public int getPrice() {
        return price;
    }
    public int getTradeVolume() {
        return tradeVolume;
    }
    public int resetTradeVolume() {
        int volume = tradeVolume;
        tradeVolume = 0;
        return volume;
    }

    public boolean cancelOrder(long orderID)
    {
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getOrderID() == orderID)
            {
                order.markAsCancelled();
                limitBuyOrders.remove(order);
                return true;
            }
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getOrderID() == orderID)
            {
                order.markAsCancelled();
                limitSellOrders.remove(order);
                return true;
            }
        }
        return false;
    }

    public String toString()
    {
        return "MatchingEngine{ Price: " + price + " TradeVolume: " + tradeVolume +
                "Sell Orders: "+ limitSellOrders.size()+" Buy Orders: "+ limitBuyOrders.size()+" }";
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
            int index = (int)((order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }
        for(LimitOrder order : limitSellOrders)
        {
            int index = (int)((order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }
        orderbookVolume.setVolume(volume);
        return orderbookVolume;
    }


    @Override
    public void save(CompoundTag tag) {
        ListTag buyOrdersList = new ListTag();
        ListTag sellOrdersList = new ListTag();
        for(LimitOrder order : limitBuyOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            order.save(orderTag);
            buyOrdersList.add(orderTag);
        }

        for(LimitOrder order : limitSellOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            order.save(orderTag);
            sellOrdersList.add(orderTag);
        }

        tag.putInt("price", price);
        tag.putInt("trade_volume", tradeVolume);
        tag.put("buy_orders", buyOrdersList);
        tag.put("sell_orders", sellOrdersList);
    }

    @Override
    public void load(CompoundTag tag) {
        price = tag.getInt("price");
        tradeVolume = tag.getInt("trade_volume");
        ListTag buyOrdersList = tag.getList("buy_orders", 10);
        ListTag sellOrdersList = tag.getList("sell_orders", 10);
        for(int i = 0; i < buyOrdersList.size(); i++)
        {
            CompoundTag orderTag = buyOrdersList.getCompound(i);
            LimitOrder order = new LimitOrder(orderTag);
            limitBuyOrders.add(order);
        }

        for(int i = 0; i < sellOrdersList.size(); i++)
        {
            CompoundTag orderTag = sellOrdersList.getCompound(i);
            LimitOrder order = new LimitOrder(orderTag);
            limitSellOrders.add(order);
        }

    }
}
