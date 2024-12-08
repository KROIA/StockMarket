package net.kroia.stockmarket.market.client;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.ResponseOrderPacket;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.networking.packet.UpdateTradeItemsPacket;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientMarket {

    private static Map<String, ClientTradeItem> tradeItems = new HashMap<>();

    ClientMarket()
    {

    }

    public static void init()
    {
        RequestTradeItemsPacket.generateRequest();
    }

    public static int getPrice(String itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return 0;
        }
        return tradeItem.getPrice();
    }

    public static void handlePacket(UpdatePricePacket packet)
    {
        ClientTradeItem tradeItem = tradeItems.get(packet.getPriceHistory().getItemID());
        if(tradeItem == null)
        {
            msgTradeItemNotFound(packet.getPriceHistory().getItemID());
            return;
        }
        tradeItem.handlePacket(packet);
        TradeScreen.updatePlotsData();
    }
    public static void handlePacket(UpdateTradeItemsPacket packet)
    {
        Map<String, ClientTradeItem> tradeItems = new HashMap<>();
        ArrayList<UpdatePricePacket> updatePricePackets = packet.getUpdatePricePackets();
        StockMarketMod.LOGGER.info("Received " + updatePricePackets.size() + " trade items");
        for(UpdatePricePacket updatePricePacket : updatePricePackets)
        {
            ClientTradeItem tradeItem = new ClientTradeItem(updatePricePacket.getPriceHistory().getItemID());
            tradeItem.handlePacket(updatePricePacket);
            tradeItems.put(tradeItem.getItemID(), tradeItem);
            StockMarketMod.LOGGER.info("Trade item: " + tradeItem.getItemID());
        }
        ClientMarket.tradeItems = tradeItems;
        TradeScreen.onAvailableTradeItemsChanged();
    }

    public static void handlePacket(ResponseOrderPacket packet)
    {
        ClientTradeItem tradeItem = tradeItems.get(packet.getOrder().getItemID());
        if(tradeItem == null)
        {
            msgTradeItemNotFound(packet.getOrder().getItemID());
            return;
        }
        tradeItem.handlePacket(packet);
    }

    public static ClientTradeItem getTradeItem(String itemID)
    {
        return tradeItems.get(itemID);
    }
    /*public static PriceHistory getPriceHistory(String itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return null;
        }
        return tradeItem.getPriceHistory();
    }*/

    public static boolean createOrder(String itemID, int quantity, int price)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity, price);
    }
    public static boolean createOrder(String itemID, int quantity)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity);
    }

    public static Order getOrder(String itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return tradeItem.getOrder(orderID);
    }

    public static ArrayList<Order> getOrders(String itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        return tradeItem.getOrders();
    }
    public static void removeOrder(String itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.removeOrder(orderID);
    }

    public static void subscribeMarketUpdate(String itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.subscribe();
    }
    public static void unsubscribeMarketUpdate(String itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.unsubscribe();
    }

    private static void msgTradeItemNotFound(String itemID) {
        StockMarketMod.LOGGER.warn("[CLIENT] Trade item not found: " + itemID);
    }
}
