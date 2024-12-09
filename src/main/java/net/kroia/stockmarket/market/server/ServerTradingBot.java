package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;

/**
 * The ServerTradingBot simulates buy and sell orders to provide liquidity to the market.
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {

    private static long lastBotID = 0;
    private String botUUID;
    private String itemID;
    private int currentPrice;
    private MatchingEngine matchingEngine;

    private final int maxOrderCount = 100;

    private ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    private ArrayList<LimitOrder> sellOrders = new ArrayList<>();

    public ServerTradingBot(MatchingEngine matchingEngine, String itemID)
    {
        botUUID = "BOT" + lastBotID++;
        this.itemID = itemID;
        this.matchingEngine = matchingEngine;
    }

    public void setCurrentPrice(int price)
    {
        if(currentPrice != price)
        {
            currentPrice = price;
            //updateOrders();
          //  clearOrders();
          //  createOrders();
        }
        clearOrders();
        createOrders();
    }

    public void updateOrders()
    {
        // Check if orders are filled
        for(int i = 0; i < buyOrders.size(); i++)
        {
            LimitOrder order = buyOrders.get(i);
            if(order.isFilled())
            {
                buyOrders.remove(i);
                i--;
            }
        }
        for(int i = 0; i < sellOrders.size(); i++)
        {
            LimitOrder order = sellOrders.get(i);
            if(order.isFilled())
            {
                sellOrders.remove(i);
                i--;
            }
        }
    }

    public void clearOrders()
    {
        matchingEngine.removeBuyOrder_internal(buyOrders);
        matchingEngine.removeSellOrder_internal(sellOrders);
        buyOrders.clear();
        sellOrders.clear();
    }

    private void createOrders()
    {
        int priceIncerement = 1;
        for(int i=1; i<=maxOrderCount/2; i++)
        {
            int sellPrice = currentPrice + i*priceIncerement;
            int buyPrice = currentPrice - i*priceIncerement;



            int buyVolume = getAvailableVolume(buyPrice);
            if(buyVolume > 0) {
                LimitOrder buyOrder = new LimitOrder(botUUID, itemID, buyVolume, buyPrice, true);
                matchingEngine.addOrder(buyOrder);
                buyOrders.add(buyOrder);
            }

            int sellVolume = getAvailableVolume(sellPrice);
            if(sellVolume < 0) {
                LimitOrder sellOrder = new LimitOrder(botUUID, itemID, sellVolume, sellPrice, true);
                matchingEngine.addOrder(sellOrder);
                sellOrders.add(sellOrder);
            }




        }
    }

    public int getAvailableVolume(int price)
    {
        if(price < 0)
            return 0;
        return getVolumeDistribution(currentPrice - price);
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    public int getVolumeDistribution(int x)
    {
        double fX = (double)Math.abs(x);
        double scale = 100;
        double exp = Math.exp(-fX*0.05);
        double random = Math.random()+1;

        double volume = (scale*random) * (1 - exp) * exp;

        if(x < 0)
            return (int)-volume;
        return (int)volume;

        //return -x*;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("botUUID", botUUID);
        tag.putInt("currentPrice", currentPrice);
    }

    @Override
    public void load(CompoundTag tag) {
        botUUID = tag.getString("botUUID");
        currentPrice = tag.getInt("currentPrice");

        ArrayList<Order> orders = new ArrayList<>();
        matchingEngine.getOrders(botUUID, orders);
        for(Order order : orders)
        {
            if(order instanceof LimitOrder)
            {
                LimitOrder limitOrder = (LimitOrder) order;
                if(limitOrder.isBuy())
                {
                    buyOrders.add(limitOrder);
                }
                else
                {
                    sellOrders.add(limitOrder);
                }
            }
        }
    }
}
