package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.clientdata.BotSettingsData;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestManageTradingItemPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderChangePacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class ClientStockMarketManager {

    protected StockMarketModBackend.Instances BACKEND_INSTANCES;

    private final Map<ItemID, ClientTradeItem> tradeItems = new HashMap<>();
    private ItemID CURRENCY_ITEM = null;

    private SyncBotSettingsPacket syncBotSettingsPacket;
    private SyncBotTargetPricePacket syncBotTargetPricePacket;
    //privatc SyncTradeItemsPacket syncTradeItemsPacket;
    private boolean syncTradeItemsChanged;

    private boolean syncBotSettingsPacketChanged = false;
    private boolean syncBotTargetPricePacketChanged = false;

    public ClientStockMarketManager(StockMarketModBackend.Instances backendInstances)
    {
        this.BACKEND_INSTANCES = backendInstances;
    }
    public void clear()
    {
        tradeItems.clear();
        syncBotSettingsPacket = null;
        syncTradeItemsChanged = false;
        syncBotSettingsPacketChanged = false;
        syncBotTargetPricePacketChanged = false;
        syncBotTargetPricePacket = null;
    }

    public void requestTradeItems()
    {
        RequestTradeItemsPacket.generateRequest();
    }
    public void requestAllowNewTradingItem(ItemID itemID, int startPrice)
    {
        RequestManageTradingItemPacket.sendRequestAllowNewTradingItem(itemID, startPrice);
    }
    public void requestRemoveTradingItem(ItemID itemID)
    {
        RequestManageTradingItemPacket.sendRequestRemoveTradingItem(itemID);
    }
    public int getPrice(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return 0;
        }
        return tradeItem.getPrice();
    }

    /*public SyncTradeItemsPacket getSyncTradeItemsPacket()
    {
        return syncTradeItemsPacket;
    }*/
    public boolean hasSyncTradeItemsChanged()
    {
        if(syncTradeItemsChanged)
        {
            syncTradeItemsChanged = false;
            return true;
        }
        return false;
    }

    public ItemID getCurrencyItem()
    {
        return CURRENCY_ITEM;
    }

    public void handlePacket(SyncPricePacket packet)
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
    public void handlePacket(SyncTradeItemsPacket packet)
    {
        //syncTradeItemsPacket = packet;
        syncTradeItemsChanged = true;
        CURRENCY_ITEM = packet.getBaseCurrencyItemID();
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
        ClientTradeItem orgInstance = tradeItems.get(syncPricePacket.getPriceHistory().getItemID());
        ClientTradeItem tradeItem;
        if(orgInstance != null)
        {
            tradeItems.put(syncPricePacket.getPriceHistory().getItemID(), orgInstance);
            orgInstance.handlePacket(syncPricePacket);
            tradeItem = orgInstance;
            return;
        }
        else {
            tradeItem = new ClientTradeItem(syncPricePacket.getPriceHistory().getItemID(), syncPricePacket.getPriceHistory().getCurrencyItemID());
            tradeItem.handlePacket(syncPricePacket);
            tradeItems.put(tradeItem.getItemID(), tradeItem);
        }

        BACKEND_INSTANCES.LOGGER.info("Trade item: "+ tradeItem.getItemID());
        if(Objects.equals(syncPricePacket.getPriceHistory().getItemID(), TradeScreen.getItemID()))
        {
            TradeScreen.updatePlotsData();
        }
    }

    public void handlePacket(SyncOrderPacket packet)
    {
        ClientTradeItem tradeItem = tradeItems.get(packet.getOrder().getItemID());
        if(tradeItem == null)
        {
            msgTradeItemNotFound(packet.getOrder().getItemID());
            return;
        }
        tradeItem.handlePacket(packet);
    }




    public ClientTradeItem getTradeItem(ItemID itemID)
    {
        return tradeItems.get(itemID);
    }

    public boolean createOrder(ItemID itemID, int quantity, int price)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity, price);
    }
    public boolean createOrder(ItemID itemID, int quantity)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return tradeItem.createOrder(quantity);
    }
    public void changeOrderPrice(ItemID itemID, long orderID, int newPrice)
    {
        RequestOrderChangePacket.sendRequest(itemID, orderID, newPrice);
    }

    public Order getOrder(ItemID itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return tradeItem.getOrder(orderID);
    }

    public ArrayList<Order> getOrders(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        return tradeItem.getOrders();
    }
    public void removeOrder(ItemID itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.removeOrder(orderID);
    }

    public void cancelOrder(Order order)
    {
        cancelOrder(order.getItemID(), order.getOrderID());
    }
    public void cancelOrder(ItemID itemID, long orderID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.cancelOrder(orderID);
    }

    public void subscribeMarketUpdate(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.subscribe();
    }
    public void unsubscribeMarketUpdate(ItemID itemID)
    {
        ClientTradeItem tradeItem = tradeItems.get(itemID);
        if(tradeItem == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        tradeItem.unsubscribe();
    }


    public void handlePacket(SyncBotSettingsPacket packet)
    {
        //syncBotSettingsPacket = packet;
        syncBotSettingsPacketChanged = true;
    }
    public boolean hasSyncBotSettingsPacketChanged()
    {
        if(syncBotSettingsPacketChanged)
        {
            syncBotSettingsPacketChanged = false;
            return true;
        }
        return false;
    }
    public void handlePacket(SyncBotTargetPricePacket packet)
    {
        syncBotTargetPricePacket = packet;
        syncBotTargetPricePacketChanged = true;
    }

    public boolean hasSyncBotTargetPricePacketChanged()
    {
        if(syncBotTargetPricePacketChanged)
        {
            syncBotTargetPricePacketChanged = false;
            return true;
        }
        return false;
    }
    public int getBotTargetPrice()
    {
        if(syncBotTargetPricePacket != null)
        {
            return syncBotTargetPricePacket.getTargetPrice();
        }
        return 0;
    }

    public ServerVolatilityBot.Settings getBotSettings(ItemID itemID)
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
    public void requestBotSettings(ItemID itemID)
    {
        RequestBotSettingsPacket.sendPacket(itemID);
    }
    public ItemID getBotSettingsItemID()
    {
        if(syncBotSettingsPacket != null)
        {
            return syncBotSettingsPacket.getItemID();
        }
        return null;
    }
    public boolean botExists()
    {
        if(syncBotSettingsPacket != null)
        {
            return syncBotSettingsPacket.botExists();
        }
        return false;
    }
    public SyncBotSettingsPacket getSyncBotSettingsPacket()
    {
        return syncBotSettingsPacket;
    }

    private void msgTradeItemNotFound(ItemID itemID) {
        BACKEND_INSTANCES.LOGGER.warn("[CLIENT] Trade item not found: " + itemID);
    }

    public ArrayList<ItemID> getAvailableTradeItemIdList()
    {
        return new ArrayList<>(tradeItems.keySet());
    }




    public void requestBotSettings(ItemID itemID, Consumer<BotSettingsData> callback) {
        //RequestBotSettingsPacket.sendPacket(itemID, callback);
    }
}
