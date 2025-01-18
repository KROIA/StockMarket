package net.kroia.stockmarket.market.client;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderChangePacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncOrderPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.*;

public class ClientMarket {

    private static Map<String, ClientTradeItem> tradeItems = new HashMap<>();

    private static SyncBotSettingsPacket syncBotSettingsPacket;
    private static boolean syncBotSettingsPacketChanged = false;

    ClientMarket()
    {

    }

    public static void clear()
    {
        tradeItems.clear();
    }

    public static void requestTradeItems()
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

    public static void handlePacket(SyncPricePacket packet)
    {
        ClientTradeItem tradeItem = tradeItems.get(packet.getPriceHistory().getItemID());
        if(tradeItem == null)
        {
            msgTradeItemNotFound(packet.getPriceHistory().getItemID());
            return;
        }
        tradeItem.handlePacket(packet);
        TradeScreen.updatePlotsData();
        BotSettingsScreen.updatePlotsData();
    }
    public static void handlePacket(SyncTradeItemsPacket packet)
    {
        Map<String, ClientTradeItem> tradeItems = new HashMap<>();
        ArrayList<SyncPricePacket> syncPricePackets = packet.getUpdatePricePackets();
        StockMarketMod.LOGGER.info("Received " + syncPricePackets.size() + " trade items");
        for(SyncPricePacket syncPricePacket : syncPricePackets)
        {
            ClientTradeItem orgInstance = ClientMarket.tradeItems.get(syncPricePacket.getPriceHistory().getItemID());
            ClientTradeItem tradeItem;
            if(orgInstance != null)
            {
                tradeItems.put(syncPricePacket.getPriceHistory().getItemID(), orgInstance);
                orgInstance.handlePacket(syncPricePacket);
                tradeItem = orgInstance;
                continue;
            }
            else {
                tradeItem = new ClientTradeItem(syncPricePacket.getPriceHistory().getItemID());
                tradeItem.handlePacket(syncPricePacket);
                tradeItems.put(tradeItem.getItemID(), tradeItem);
            }

            StockMarketMod.LOGGER.info("Trade item: {}", tradeItem.getItemID());
            if(Objects.equals(syncPricePacket.getPriceHistory().getItemID(), TradeScreen.getItemID()))
            {
                TradeScreen.updatePlotsData();
            }
        }
        ClientMarket.tradeItems = tradeItems;
        TradeScreen.onAvailableTradeItemsChanged();

    }

    public static void handlePacket(SyncOrderPacket packet)
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
    public static void changeOrderPrice(String itemID, long orderID, int newPrice)
    {
        RequestOrderChangePacket.sendRequest(itemID, orderID, newPrice);
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

    public static void cancelOrder(Order order)
    {
        cancelOrder(order.getItemID(), order.getOrderID());
    }
    public static void cancelOrder(String itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.cancelOrder(orderID);
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


    public static void handlePacket(SyncBotSettingsPacket packet)
    {
        syncBotSettingsPacket = packet;
        syncBotSettingsPacketChanged = true;
    }
    public static boolean hasSyncBotSettingsPacketChanged()
    {
        if(syncBotSettingsPacketChanged)
        {
            syncBotSettingsPacketChanged = false;
            return true;
        }
        return false;
    }
    public static ServerVolatilityBot.Settings getBotSettings(String itemID)
    {
        if(syncBotSettingsPacket != null)
        {
            if(syncBotSettingsPacket.getItemID().equals(itemID))
            {
                return syncBotSettingsPacket.getSettings();
            }
        }
        RequestBotSettingsPacket.sendPacket(itemID);
        return null;
    }
    public static String getBotSettingsItemID()
    {
        if(syncBotSettingsPacket != null)
        {
            return syncBotSettingsPacket.getItemID();
        }
        return null;
    }
    public static boolean botExists()
    {
        if(syncBotSettingsPacket != null)
        {
            return syncBotSettingsPacket.botExists();
        }
        return false;
    }
    public static UUID getBotUUID()
    {
        if(syncBotSettingsPacket != null)
        {
            return syncBotSettingsPacket.getBotUUID();
        }
        return null;
    }
    public static SyncBotSettingsPacket getSyncBotSettingsPacket()
    {
        return syncBotSettingsPacket;
    }

    private static void msgTradeItemNotFound(String itemID) {
        StockMarketMod.LOGGER.warn("[CLIENT] Trade item not found: " + itemID);
    }

    public static ArrayList<String> getAvailableTradeItemIdList()
    {
        return new ArrayList<>(tradeItems.keySet());
    }
}
