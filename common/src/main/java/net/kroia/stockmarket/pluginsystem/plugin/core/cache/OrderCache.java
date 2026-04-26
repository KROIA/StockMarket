package net.kroia.stockmarket.pluginsystem.plugin.core.cache;


import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;

import java.util.ArrayList;
import java.util.List;

public class OrderCache {
    private final List<Order> limitOrders =  new ArrayList<>();
    private double marketOrderVolume = 0;


    public OrderCache()
    {

    }

    public void addLimitOrder(Order limitOrder)
    {
        this.limitOrders.add(limitOrder);
    }
    public void addMarketOrder(double amount)
    {
        marketOrderVolume += amount;
    }

    public void apply(IServerMarket serverMarket) {
        for (Order order : limitOrders) {
            serverMarket.putOrder(order);
        }
        if (marketOrderVolume != 0) {
            long rawVolume = MarketManager.convertToRawAmountStatic(marketOrderVolume);
            Order botOrder = new Order(serverMarket.getItemID(), Order.Type.MARKET, rawVolume, serverMarket.getCurrentMarketPrice(), System.currentTimeMillis());
            serverMarket.putOrder(botOrder);
        }

        marketOrderVolume = 0;
        limitOrders.clear();
    }
}
