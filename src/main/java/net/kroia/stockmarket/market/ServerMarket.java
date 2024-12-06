package net.kroia.stockmarket.market;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.order.Order;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServerMarket
{
    private static final Map<String, MarketManager> marketManagers = new HashMap<>();
    private static final Map<String, ArrayList<ServerPlayer>> playerSubscriptions = new HashMap<>();
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
        notifySubscriber();
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
            notifySubscriber(itemID);
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

    /*public static void setPriceHistory(PriceHistory history)
    {
        marketData.setPriceHistory(history);
    }*/
    public static PriceHistory getPriceHistory(String itemID)
    {
        return marketData.getPriceHistory(itemID);
    }

    public static void addPlayerUpdateSubscription(String itemID, ServerPlayer player)
    {
        ArrayList<ServerPlayer> players = playerSubscriptions.computeIfAbsent(itemID, k -> new ArrayList<>());
        players.add(player);
        UpdatePricePacket.sendPacket(itemID, player);
    }
    public static void removePlayerUpdateSubscription(String itemID, ServerPlayer player)
    {
        ArrayList<ServerPlayer> players = playerSubscriptions.get(itemID);
        if(players != null)
        {
            players.remove(player);
        }
    }

    private static void notifySubscriber(String itemID)
    {
        ArrayList<ServerPlayer> players = playerSubscriptions.get(itemID);
        if(players != null)
        {
            for(ServerPlayer player : players)
            {
                UpdatePricePacket.sendPacket(itemID, player);
            }
        }
    }
    private static void notifySubscriber()
    {
        for(var entry : playerSubscriptions.entrySet())
        {
            String itemID = entry.getKey();
            ArrayList<ServerPlayer> players = entry.getValue();
            for(ServerPlayer player : players)
            {
                UpdatePricePacket.sendPacket(itemID, player);
            }
        }
    }

}
