package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestManageTradingItemPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderChangePacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.*;

public class ClientMarket {

    private static final Map<ItemID, ClientTradeItem> tradeItems = new HashMap<>();

    private static SyncBotSettingsPacket syncBotSettingsPacket;
    private static SyncBotTargetPricePacket syncBotTargetPricePacket;
    //private static SyncTradeItemsPacket syncTradeItemsPacket;
    private static boolean syncTradeItemsChanged;

    private static boolean syncBotSettingsPacketChanged = false;
    private static boolean syncBotTargetPricePacketChanged = false;



    ClientMarket()
    {

    }

    public static void clear()
    {
        tradeItems.clear();
        syncBotSettingsPacket = null;
        syncTradeItemsChanged = false;
        syncBotSettingsPacketChanged = false;
        syncBotTargetPricePacketChanged = false;
        syncBotTargetPricePacket = null;
    }

    public static void requestTradeItems()
    {
        RequestTradeItemsPacket.generateRequest();
    }
    public static void requestAllowNewTradingItem(ItemID itemID, int startPrice)
    {
        RequestManageTradingItemPacket.sendRequestAllowNewTradingItem(itemID, startPrice);
    }
    public static void requestRemoveTradingItem(ItemID itemID)
    {
        RequestManageTradingItemPacket.sendRequestRemoveTradingItem(itemID);
    }
    public static int getPrice(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return 0;
        }
        return tradeItem.getPrice();
    }

    /*public static SyncTradeItemsPacket getSyncTradeItemsPacket()
    {
        return syncTradeItemsPacket;
    }*/
    public static boolean hasSyncTradeItemsChanged()
    {
        if(syncTradeItemsChanged)
        {
            syncTradeItemsChanged = false;
            return true;
        }
        return false;
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
        //syncTradeItemsPacket = packet;
        syncTradeItemsChanged = true;

        if(packet.getCommand() == SyncTradeItemsPacket.Command.STILL_AVAILABLE)
        {
            ArrayList<ItemID> stillAvailableItems = packet.getStillAvailableItems();
            HashMap<ItemID, Boolean> stillAvailableItemsMap = new HashMap<>();
            for(ItemID itemID : stillAvailableItems)
            {
                stillAvailableItemsMap.put(itemID, true);
            }
            ArrayList<ItemID> toRemove = new ArrayList<>();
            for(ItemID itemID : tradeItems.keySet())
            {
                if(!stillAvailableItemsMap.containsKey(itemID))
                {
                    toRemove.add(itemID);
                }
            }
            for(ItemID itemID : toRemove)
            {
                tradeItems.remove(itemID);
            }
            return;
        }

        SyncPricePacket syncPricePacket = packet.getSyncPricePacket();
        ClientTradeItem orgInstance = ClientMarket.tradeItems.get(syncPricePacket.getPriceHistory().getItemID());
        ClientTradeItem tradeItem;
        if(orgInstance != null)
        {
            tradeItems.put(syncPricePacket.getPriceHistory().getItemID(), orgInstance);
            orgInstance.handlePacket(syncPricePacket);
            tradeItem = orgInstance;
            return;
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




    public static ClientTradeItem getTradeItem(ItemID itemID)
    {
        return tradeItems.get(itemID);
    }

    public static boolean createOrder(ItemID itemID, int quantity, int price)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity, price);
    }
    public static boolean createOrder(ItemID itemID, int quantity)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity);
    }
    public static void changeOrderPrice(ItemID itemID, long orderID, int newPrice)
    {
        RequestOrderChangePacket.sendRequest(itemID, orderID, newPrice);
    }

    public static Order getOrder(ItemID itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return tradeItem.getOrder(orderID);
    }

    public static ArrayList<Order> getOrders(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        return tradeItem.getOrders();
    }
    public static void removeOrder(ItemID itemID, long orderID)
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
    public static void cancelOrder(ItemID itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.cancelOrder(orderID);
    }

    public static void subscribeMarketUpdate(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.subscribe();
    }
    public static void unsubscribeMarketUpdate(ItemID itemID)
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
    public static void handlePacket(SyncBotTargetPricePacket packet)
    {
        syncBotTargetPricePacket = packet;
        syncBotTargetPricePacketChanged = true;
    }

    public static boolean hasSyncBotTargetPricePacketChanged()
    {
        if(syncBotTargetPricePacketChanged)
        {
            syncBotTargetPricePacketChanged = false;
            return true;
        }
        return false;
    }
    public static int getBotTargetPrice()
    {
        if(syncBotTargetPricePacket != null)
        {
            return syncBotTargetPricePacket.getTargetPrice();
        }
        return 0;
    }

    public static ServerVolatilityBot.Settings getBotSettings(ItemID itemID)
    {
        if(syncBotSettingsPacket != null)
        {
            if(syncBotSettingsPacket.getItemID().equals(itemID))
            {
                return syncBotSettingsPacket.getSettings();
            }
        }
        requestBotSettings(itemID);
        return null;
    }
    public static void requestBotSettings(ItemID itemID)
    {
        RequestBotSettingsPacket.sendPacket(itemID);
    }
    public static ItemID getBotSettingsItemID()
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
    public static SyncBotSettingsPacket getSyncBotSettingsPacket()
    {
        return syncBotSettingsPacket;
    }

    private static void msgTradeItemNotFound(ItemID itemID) {
        StockMarketMod.LOGGER.warn("[CLIENT] Trade item not found: " + itemID);
    }

    public static ArrayList<ItemID> getAvailableTradeItemIdList()
    {
        return new ArrayList<>(tradeItems.keySet());
    }
}
