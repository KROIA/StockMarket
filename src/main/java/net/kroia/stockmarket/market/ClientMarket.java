package net.kroia.stockmarket.market;

import net.kroia.stockmarket.util.PriceHistory;

import java.util.Map;

public class ClientMarket {
    private static MarketData marketData;


    public static void init()
    {
        marketData = new MarketData();
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
