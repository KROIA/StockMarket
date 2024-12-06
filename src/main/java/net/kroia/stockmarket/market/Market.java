package net.kroia.stockmarket.market;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.order.Order;
import net.kroia.stockmarket.util.PriceHistory;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Market
{
    private static Map<String, MarketManager> marketManagers = new HashMap<>();
    private static MarketData marketData;
    public static int shiftPriceHistoryInterval = 10; // in minutes

    public static void init()
    {
        marketData = new MarketData();
        Map<String, PriceHistory> prices = marketData.getPrices();
        for(String itemID : prices.keySet())
        {
            PriceHistory priceHistory = prices.get(itemID);
            MarketManager marketManager = new MarketManager(itemID, priceHistory.getCurrentPrice(), priceHistory);
            marketManagers.put(itemID, marketManager);
        }
    }
    public static void shiftPriceHistory()
    {
        StockMarketMod.LOGGER.info("Shifting price history");
        for(MarketManager marketManager : marketManagers.values())
        {
            marketManager.shiftPriceHistory();
        }
    }
    public static Map<String, MarketManager> getMarketManagers()
    {
        return marketManagers;
    }
    public static MarketManager getMarketManager(String itemID)
    {
        return marketManagers.get(itemID);
    }

    public static void addOrder(String itemID, Order order)
    {
        MarketManager marketManager = marketManagers.get(itemID);
        if(marketManager != null)
        {
            marketManager.addOrder(order);
        }
    }

    public static ArrayList<Integer> getOrderBookVolume(String itemID, int tiles, int minPrice, int maxPrice)
    {
        MarketManager marketManager = marketManagers.get(itemID);
        if(marketManager != null)
        {
            return marketManager.getOrderBookVolume(tiles, minPrice, maxPrice);
        }
        return new ArrayList<>();
    }


    public static int getPrice(String itemID)
    {
        return marketData.getPrice(itemID);
    }

    public static void setPriceHistory(PriceHistory history)
    {
        marketData.setPriceHistory(history);
    }
    public static PriceHistory getPriceHistory(String itemID)
    {
        return marketData.getPriceHistory(itemID);
    }

}
