package net.kroia.stockmarket.market;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.PriceHistory;

import java.util.HashMap;
import java.util.Map;

public class MarketData
{
    private Map<String, PriceHistory> prices = new HashMap<>();

    MarketData()
    {
        setPrice("item.minecraft.diamond", 100);
        setPrice("item.minecraft.iron_ingot", 10);
        setPrice("item.minecraft.gold_ingot", 50);
    }
    public void setPrice(String itemID, int price)
    {
        PriceHistory priceHistory = prices.get(itemID);
        if (priceHistory == null)
        {
            priceHistory = new PriceHistory(itemID, price);
            prices.put(itemID, priceHistory);
        }
        //priceHistory.setCurrentPrice(price);
    }

    public int getPrice(String itemID)
    {
        PriceHistory priceHistory = prices.get(itemID);
        if (priceHistory != null)
        {
            return priceHistory.getCurrentPrice();
        }
        StockMarketMod.LOGGER.warn("Price not found for item: " + itemID);
        return 0;
    }

    public Map<String, PriceHistory> getPrices()
    {
        return prices;
    }

    public void setPrices(Map<String, PriceHistory> prices)
    {
        StockMarketMod.LOGGER.info("Setting prices: " + prices);
        prices = prices;
    }

    public void setPriceHistory(PriceHistory history)
    {
        StockMarketMod.LOGGER.info("Setting price history: " + history);
        prices.put(history.getItemID(), history);
    }
    public PriceHistory getPriceHistory(String itemID)
    {
        return prices.get(itemID);
    }

}
