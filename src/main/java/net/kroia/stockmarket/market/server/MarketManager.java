package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.Timestamp;

import java.util.ArrayList;

public class MarketManager {
    private final String itemID;

    private MatchingEngine matchingEngine;

    private PriceHistory priceHistory;
    private int lowPrice;
    private int highPrice;

    public MarketManager(String itemID, int initialPrice, PriceHistory history)
    {
        this.itemID = itemID;
        matchingEngine = new MatchingEngine(initialPrice);
        priceHistory = history;

        lowPrice = initialPrice;
        highPrice = initialPrice;
    }

    public void addOrder(Order order)
    {
        StockMarketMod.LOGGER.info("Adding order: " + order.toString());
        matchingEngine.addOrder(order);

        int price = matchingEngine.getPrice();
        lowPrice = Math.min(lowPrice, price);
        highPrice = Math.max(highPrice, price);
        priceHistory.setCurrentPrice(price);
    }
    public boolean cancelOrder(long orderID)
    {
        return matchingEngine.cancelOrder(orderID);
    }
    public ArrayList<Order> getOrders()
    {
        return matchingEngine.getOrders();
    }

    public void shiftPriceHistory()
    {
        int currentPrice = matchingEngine.getPrice();
        lowPrice = currentPrice;
        highPrice = currentPrice;
        priceHistory.addPrice(lowPrice, highPrice, currentPrice, new Timestamp());
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }

    public int getLowPrice() {
        return lowPrice;
    }

    public int getHighPrice() {
        return highPrice;
    }

    public int getCurrentPrice() {
        return matchingEngine.getPrice();
    }

    public int getVolume() {
        return matchingEngine.getTradeVolume();
    }

    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        return matchingEngine.getOrderBookVolume(tiles, minPrice, maxPrice);
    }
}
