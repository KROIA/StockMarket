package net.kroia.stockmarket.plugin.base.cache;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;

import java.util.ArrayList;
import java.util.List;

public class OrderCache {
    private final List<LimitOrder> limitOrders =  new ArrayList<>();
    private float marketOrderVolume = 0;


    public OrderCache()
    {

    }

    public void addLimitOrder(LimitOrder limitOrder)
    {
        this.limitOrders.add(limitOrder);
    }
    public void addMarketOrder(float amount)
    {
        marketOrderVolume += amount;
    }

    public void apply(IServerMarket serverMarket)
    {
        for (LimitOrder order : limitOrders) {
            serverMarket.placeOrder(order);
        }
        serverMarket.createAndPlaceBotMarketOrder(marketOrderVolume);

        marketOrderVolume = 0;
        limitOrders.clear();
    }
}
