package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.ResponseOrderPacket;
import net.kroia.stockmarket.util.OrderbookVolume;

import java.util.*;

/*
    * The MatchingEngine class is responsible for matching buy and sell orders.
 */
public class MatchingEngine {


    //private int buyPrice;
    //private int sellPrice;
    private int price;
    private int tradeVolume;

    // Create a sorted queue for buy and sell orders, sorted by price.
    private final PriorityQueue<LimitOrder> spotBuyOrders = new PriorityQueue<>((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
    private final PriorityQueue<LimitOrder> spotSellOrders = new PriorityQueue<>(Comparator.comparingDouble(LimitOrder::getPrice));

    public MatchingEngine(int initalPrice) {
        this.price = initalPrice;
        tradeVolume = 0;
    }


    public void addOrder(Order order)
    {
        if(order.getAmount() == 0)
        {
            order.markAsProcessed();
            return;
        }
        if (order instanceof LimitOrder)
        {
            LimitOrder spotOrder = (LimitOrder) order;
            spotOrder.setAveragePrice(spotOrder.getPrice());
            if(!processSpotOrder(spotOrder))
            {
                if (spotOrder.isBuy())
                {
                    // Add the spot order to the buyOrders queue
                    spotBuyOrders.add(spotOrder);
                }
                else
                {
                    // Add the spot order to the sellOrders queue
                    spotSellOrders.add(spotOrder);
                }
                spotOrder.notifyPlayer();
            }
        } else if (order instanceof MarketOrder) {
            MarketOrder marketOrder = (MarketOrder) order;
            processMarketOrder(marketOrder);
            //marketOrder.notifyPlayer();
        } else {
            throw new IllegalArgumentException("Invalid order type");
        }
    }

    private boolean processMarketOrder(MarketOrder marketOrder)
    {
        // Process the market order
        int amount = -marketOrder.getAmount();
        PriorityQueue<LimitOrder> spotOrders = marketOrder.isBuy() ? spotSellOrders : spotBuyOrders;
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        int volume = 0;
        for(LimitOrder spotOrder : spotOrders)
        {
            int currentAmount = amount;
            amount = spotOrder.fill(amount);
            int deltaVolume = currentAmount - amount;
            volume += deltaVolume;
            price = spotOrder.getPrice();
            if(spotOrder.isFilled())
                toRemove.add(spotOrder);
            if(amount == 0)
            {
                marketOrder.changeAveragePrice(deltaVolume, price);
                marketOrder.fill(marketOrder.getAmount());
                break;
            }

        }
        spotOrders.removeAll(toRemove);
        tradeVolume += Math.abs(volume);
        if(amount == 0)
            return true;

        marketOrder.markAsInvalid();
        StockMarketMod.LOGGER.warn("Market order not fully processed: " + marketOrder.toString());
        return false;
    }
    private boolean processSpotOrder(LimitOrder spotOrder)
    {
        // Process the spot order
        int amount = -spotOrder.getAmount();
        PriorityQueue<LimitOrder> spotOrders = spotOrder.isBuy() ? spotSellOrders : spotBuyOrders;
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

    /*public int getBuyPrice() {
        return buyPrice;
    }
    public int getSellPrice() {
        return sellPrice;
    }

    public int getPrice() {
        return (int)((float)(buyPrice + sellPrice) * 0.5f);
    }
    public int getSpread() {
        return sellPrice - buyPrice;
    }*/

    public ArrayList<LimitOrder> getSpotBuyOrders() {
        return new ArrayList<>(spotBuyOrders);
    }
    public ArrayList<LimitOrder> getSpotSellOrders() {
        return new ArrayList<>(spotSellOrders);
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

    public String toString()
    {
        return "MatchingEngine{ Price: " + price + " TradeVolume: " + tradeVolume +
                "Sell Orders: "+spotSellOrders.size()+" Buy Orders: "+spotBuyOrders.size()+" }";
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
        int priceStep = priceRange / tiles;
        int[] volume = new int[tiles];
        for(LimitOrder order : spotBuyOrders)
        {
            int index = (order.getPrice() - minPrice) / priceStep;
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount();
        }
        for(LimitOrder order : spotSellOrders)
        {
            int index = (order.getPrice() - minPrice) / priceStep;
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount();
        }
        orderbookVolume.setVolume(volume);
        return orderbookVolume;
    }


}
