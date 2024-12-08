package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServerMarket
{
    //private static final Map<String, MarketManager> marketManagers = new HashMap<>();
    //private static final Map<String, ArrayList<ServerPlayer>> playerSubscriptions = new HashMap<>();
    //private static MarketData marketData;
    public static int shiftPriceHistoryInterval = 10; // in minutes

    private static final Map<String, ServerTradeItem> tradeItems = new HashMap<>();

    public static void init()
    {
        addTradeItem("minecraft:diamond", 50);
        addTradeItem("minecraft:iron_ingot", 10);
        addTradeItem("minecraft:gold_ingot", 25);
        /*marketData = new MarketData();
        Map<String, PriceHistory> prices = marketData.getPrices();
        for(String itemID : prices.keySet())
        {
            PriceHistory priceHistory = prices.get(itemID);
            MarketManager marketManager = new MarketManager(itemID, priceHistory.getCurrentPrice(), priceHistory);
            marketManagers.put(itemID, marketManager);
        }*/
    }

    public static void addTradeItem(String itemID, int startPrice)
    {
        if(tradeItems.containsKey(itemID))
        {
            StockMarketMod.LOGGER.warn("Trade item already exists: " + itemID);
            return;
        }
        ServerTradeItem tradeItem = new ServerTradeItem(itemID, startPrice);
        tradeItems.put(itemID, tradeItem);
    }

    public static boolean hasItem(String itemID)
    {
        return tradeItems.containsKey(itemID);
    }

    public static void shiftPriceHistory()
    {
        StockMarketMod.LOGGER.info("Shifting price history");
        for(ServerTradeItem marketManager : tradeItems.values())
        {
            marketManager.shiftPriceHistory();
        }
        //notifySubscriber();
    }
    /*public static Map<String, MarketManager> getMarketManagers()
    {
        return marketManagers;
    }
    public static MarketManager getMarketManager(String itemID)
    {
        return marketManagers.get(itemID);
    }*/

    public static void addOrder(String itemID, Order order)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }
        item.addOrder(order);
    }

    public static OrderbookVolume getOrderBookVolume(String itemID, int tiles, int minPrice, int maxPrice)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return null;
        }
        return item.getOrderBookVolume(tiles, minPrice, maxPrice);
    }


    public static int getPrice(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return 0;
        }
        return item.getPrice();
    }

    /*public static void setPriceHistory(PriceHistory history)
    {
        marketData.setPriceHistory(history);
    }*/
    public static PriceHistory getPriceHistory(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return null;
        }
        return item.getPriceHistory();
    }

    public static void addPlayerUpdateSubscription(String itemID, ServerPlayer player)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }
        item.addSubscriber(player);
    }
    public static void removePlayerUpdateSubscription(String itemID, ServerPlayer player) {
        ServerTradeItem item = tradeItems.get(itemID);
        if (item == null) {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }
        item.removeSubscriber(player);
    }

    public static Map<String, ServerTradeItem> getTradeItems()
    {
        return tradeItems;
    }

}
