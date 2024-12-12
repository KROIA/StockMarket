package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderCancelPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestPricePacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerMarket implements ServerSaveable
{
    //private static final Map<String, MarketManager> marketManagers = new HashMap<>();
    //private static final Map<String, ArrayList<ServerPlayer>> playerSubscriptions = new HashMap<>();
    //private static MarketData marketData;
    public static long shiftPriceHistoryInterval = ModSettings.Market.SHIFT_PRICE_CANCLE_INTERVAL_MS; // in ms

    private static final Map<String, ServerTradeItem> tradeItems = new HashMap<>();

    public static void init()
    {

        //ServerBankManager.createUser(UUID.randomUUID(), new ArrayList<>(), true, 1000_000);
        for(var item : ModSettings.Market.TRADABLE_ITEMS.entrySet())
        {
            addTradeItemIfNotExists(item.getKey(), item.getValue());
        }

        if(ModSettings.MarketBot.ENABLED)
        {
            BankUser botUser = ServerBankManager.createBotUser();
            StockMarketMod.LOGGER.info("[SERVER] Creating trading bots");
            HashMap<String, ArrayList<ServerTradingBotFactory.BotBuilderContainer>> bots = ModSettings.MarketBot.createBots();

            for(var item : bots.entrySet())
            {
                int botCount = getBotCount(item.getKey());
                if(botCount > 0)
                    continue;
                for(ServerTradingBotFactory.BotBuilderContainer container : item.getValue())
                {
                    Bank itemBank = botUser.getBank(container.itemID);
                    if(itemBank == null)
                    {
                        itemBank = botUser.createItemBank(container.itemID, container.initialItemStock);
                    }
                    if(itemBank.getTotalBalance() < container.initialItemStock)
                    {
                        itemBank.setBalance(container.initialItemStock-itemBank.getLockedBalance());
                    }
                    addTradingBot(item.getKey(), container.bot);
                }
            }
        }

    }

    public static void addTradeItem(String itemID, int startPrice)
    {
        if(tradeItems.containsKey(itemID))
        {
            StockMarketMod.LOGGER.warn("[SERVER] Trade item already exists: " + itemID);
            return;
        }
        addTradeItem_internal(itemID, startPrice);
    }
    public static void addTradeItemIfNotExists(String itemID, int startPrice)
    {
        if(tradeItems.containsKey(itemID))
        {
            return;
        }
        addTradeItem_internal(itemID, startPrice);
    }
    private static void addTradeItem_internal(String itemID, int startPrice)
    {
        ServerTradeItem tradeItem = new ServerTradeItem(itemID, startPrice);
        tradeItems.put(itemID, tradeItem);
        //ServerBankManager.getBotUser().createItemBank(itemID, 1000_000);
    }

    public static ArrayList<String> getTradeItemIDs()
    {
        ArrayList<String> tradeItemsList = new ArrayList<>();
        for(ServerTradeItem tradeItem : tradeItems.values())
        {
            tradeItemsList.add(tradeItem.getItemID());
        }
        return tradeItemsList;
    }

    public static boolean hasItem(String itemID)
    {
        return tradeItems.containsKey(itemID);
    }

    public static void addTradingBot(String itemID, ServerTradingBot bot)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.addTradingBot(bot);
    }
    public static void removeTradingBot(String itemID, ServerTradingBot bot)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.removeTradingBot(bot);
    }
    public static void removeAllBots(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.removeAllBots();
    }
    public static int getBotCount(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return 0;
        }
        return item.getBotCount();
    }

    public static void shiftPriceHistory()
    {
        //StockMarketMod.LOGGER.info("Shifting price history");
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

    public static void addOrder(Order order)
    {
        if(order == null)
            return;
        ServerTradeItem item = tradeItems.get(order.getItemID());
        if(item == null)
        {
            msgTradeItemNotFound(order.getItemID());
            return;
        }
        item.addOrder(order);
        //item.updateBot();
    }
    public static void cancelOrder(long orderID)
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            if(item.cancelOrder(orderID))
                return;
        }
    }

    public static OrderbookVolume getOrderBookVolume(String itemID, int tiles, int minPrice, int maxPrice)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return item.getOrderBookVolume(tiles, minPrice, maxPrice);
    }

    public static ArrayList<Order> getOrders(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        return item.getOrders();
    }
    public static ArrayList<Order> getOrders(String itemID, UUID playerUUID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        ArrayList<Order> orders = new ArrayList<>();
        item.getOrders(playerUUID, orders);
        return orders;
    }
    public static ArrayList<Order> getOrdersFromUser(UUID playerUUID)
    {
        ArrayList<Order> orders = new ArrayList<>();
        for(ServerTradeItem item : tradeItems.values())
        {
            item.getOrders(playerUUID, orders);
        }
        return orders;
    }


    public static int getPrice(String itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
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
            msgTradeItemNotFound(itemID);
            return null;
        }
        return item.getPriceHistory();
    }

    public static void addPlayerUpdateSubscription(String itemID, ServerPlayer player)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.addSubscriber(player);
    }
    public static void removePlayerUpdateSubscription(String itemID, ServerPlayer player) {
        ServerTradeItem item = tradeItems.get(itemID);
        if (item == null) {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.removeSubscriber(player);
    }

    public static Map<String, ServerTradeItem> getTradeItems()
    {
        return tradeItems;
    }
    private static void msgTradeItemNotFound(String itemID) {
        StockMarketMod.LOGGER.warn("[SERVER] Trade item not found: " + itemID);
    }

    public static void handlePacket(ServerPlayer player, RequestOrderCancelPacket packet)
    {
        StockMarketMod.LOGGER.info("[SERVER] Receiving RequestOrderCancelPacket for order "+packet.getOrderID()+" from the player "+player.getName().getString());
        cancelOrder(packet.getOrderID());
    }
    public static void handlePacket(ServerPlayer player, RequestOrderPacket packet)
    {
        int amount = packet.getAmount();
        String itemID = packet.getItemID();
        String playerName = player.getName().getString();
        //MoneyBank playerBank = ServerBankManager.getBank(player.getUUID());
        RequestOrderPacket.OrderType orderType = packet.getOrderType();
        int price = packet.getPrice();
        StockMarketMod.LOGGER.info("[SERVER] Receiving RequestOrderPacket for item "+packet.getItemID()+" from the player "+playerName);

        /*if(playerBank == null)
        {
            StockMarketMod.LOGGER.error("[SERVER] Player "+playerName+" does not have a banking account");
            return;
        }*/

        if(amount < 0)
        {
            // Selling
            StockMarketMod.LOGGER.info("[SERVER] Player "+playerName+" is selling "+amount+" of "+itemID);
        }
        else if(amount > 0)
        {
            // Buying
            StockMarketMod.LOGGER.info("[SERVER] Player "+playerName+" is buying "+amount+" of "+itemID);
        }

        switch(orderType)
        {
            case limit:
                LimitOrder limitOrder = LimitOrder.create(player, itemID, amount, price);
                ServerMarket.addOrder(limitOrder);
                //StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is selling "+this.amount+" of "+this.itemID+" with a limit order");
                break;
            case market:
                MarketOrder marketOrder = MarketOrder.create(player, itemID, amount);
                ServerMarket.addOrder(marketOrder);
                //StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is selling "+this.amount+" of "+this.itemID+" with a market order");
                break;
        }
    }
    public static void handlePacket(ServerPlayer player, RequestTradeItemsPacket packet)
    {
        SyncTradeItemsPacket.sendResponse(player);
    }
    public static void handlePacket(ServerPlayer player, RequestPricePacket packet)
    {
        StockMarketMod.LOGGER.info("[SERVER] Receiving RequestPricePacket for item "+packet.getItemID()+" from the player "+player.getName().getString());

        // Send the packet to the client
        SyncPricePacket.sendPacket(packet.getItemID(), player);
    }
    public static void handlePacket(ServerPlayer player, UpdateSubscribeMarketEventsPacket packet)
    {
        boolean subscribe = packet.doesSubscribe();
        String itemID = packet.getItemID();

        StockMarketMod.LOGGER.info("[SERVER] Receiving UpdateSubscribeMarketEventsPacket for item "+itemID+
                " to "+(subscribe ? "subscribe" : "unsubscribe"));

        // Subscribe or unsubscribe the player
        if(subscribe) {
            ServerMarket.addPlayerUpdateSubscription(itemID, player);
        } else {
            ServerMarket.removePlayerUpdateSubscription(itemID, player);
        }
    }

   /* public static void updateBot()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.updateBot();
        }
    }*/

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        tag.putLong("shiftPriceHistoryInterval", shiftPriceHistoryInterval);

        ListTag tradeItems = new ListTag();
        for(ServerTradeItem tradeItem : ServerMarket.tradeItems.values())
        {
            CompoundTag tradeItemTag = new CompoundTag();
            success &= tradeItem.save(tradeItemTag);
            tradeItems.add(tradeItemTag);
        }
        tag.put("tradeItems", tradeItems);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean loadSuccess = true;
        try {
            if(!tag.contains("shiftPriceHistoryInterval") || !tag.contains("tradeItems"))
                return false;
            shiftPriceHistoryInterval = tag.getLong("shiftPriceHistoryInterval");

            ListTag tradeItems = tag.getList("tradeItems", 10);
            Map<String, ServerTradeItem> tradeItemsMap = new HashMap<>();
            for(int i = 0; i < tradeItems.size(); i++)
            {
                CompoundTag tradeItemTag = tradeItems.getCompound(i);
                ServerTradeItem tradeItem = ServerTradeItem.loadFromTag(tradeItemTag);
                if(tradeItem == null)
                {
                    loadSuccess = false;
                    continue;
                }
                tradeItemsMap.put(tradeItem.getItemID(), tradeItem);
            }
            ServerMarket.tradeItems.clear();
            ServerMarket.tradeItems.putAll(tradeItemsMap);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[SERVER] Error loading ServerMarket from NBT");
            e.printStackTrace();
            return false;
        }
        return loadSuccess;
    }
}
