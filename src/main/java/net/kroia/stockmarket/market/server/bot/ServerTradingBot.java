package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.market.server.MatchingEngine;
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

    protected static long lastBotID = 0;
    protected String botUUID;
    protected String itemID;
    protected MatchingEngine matchingEngine;

    protected final int maxOrderCount = 100;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();

    public ServerTradingBot(MatchingEngine matchingEngine, String itemID)
    {
        botUUID = "BOT" + lastBotID++;
        this.itemID = itemID;
        this.matchingEngine = matchingEngine;
    }

    public void update()
    {
        clearOrders();
        createOrders();
    }


    protected void clearOrders()
    {
        matchingEngine.removeBuyOrder_internal(buyOrders);
        matchingEngine.removeSellOrder_internal(sellOrders);
        buyOrders.clear();
        sellOrders.clear();
    }

    protected void createOrders()
    {
        int priceIncerement = 1;
        int currentPrice = matchingEngine.getPrice();
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

    protected int getAvailableVolume(int price)
    {
        if(price < 0)
            return 0;
        int currentPrice = matchingEngine.getPrice();
        return getVolumeDistribution(currentPrice - price);
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    protected int getVolumeDistribution(int x)
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
    }

    @Override
    public void load(CompoundTag tag) {
        botUUID = tag.getString("botUUID");

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
