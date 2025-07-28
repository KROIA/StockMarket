package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.clientdata.BotSettingsData;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.*;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerStockMarketManager implements ServerSaveable
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    private final Map<ItemID, ServerTradeItem> tradeItems = new HashMap<>();

    private final ArrayList<ArrayList<ServerTradeItem>> tradeItemsChunks = new ArrayList<>(); // For processing trade items in chunks
    private int tradeItemUpdateCallCounter = 0;



    //private UUID botUserUUID = UUID.nameUUIDFromBytes(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.USER_NAME.getBytes());

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public ServerStockMarketManager()
    {
        for(var item : BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.INITIAL_TRADABLE_ITEMS.get().entrySet())
        {
            addTradeItemIfNotExists(item.getKey(), item.getValue());
        }
    }

    public BotSettingsData getBotSettingsData(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            return null;
        }
        return item.getBotSettingsData();
    }
    public void setBotSettingsData(ItemID itemID, BotSettingsData botSettingsData)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            return;
        }
        item.setBotSettingsData(botSettingsData);
    }

    public void clear()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.clear();
        }
        tradeItems.clear();
        tradeItemsChunks.clear();
    }

    public void createDefaultBots()
    {
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ENABLED.get())
            return;

        HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> botBuilder  = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder().getBots();

        for(var itemData : botBuilder.entrySet())
        {
            createDefaultBot(itemData.getKey(), itemData.getValue());
        }
    }
    public boolean createDefaultBot(ItemID itemID)
    {
        return createDefaultBot(itemID, null);
    }
    public boolean createDefaultBots(String category)
    {
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ENABLED.get())
            return false;
        HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> botBuilder  = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder().getBots(category);
        for(var itemData : botBuilder.entrySet())
        {
            createDefaultBot(itemData.getKey(), itemData.getValue());
        }
        return true;
    }
    public boolean createDefaultBot(ItemID itemID, ServerTradingBotFactory.DefaultBotSettings botBuilder)
    {
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ENABLED.get())
            return false;
        //BankUser botUser = getBotUser();
        if(!BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isItemIDAllowed(itemID))
        {
            BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().allowItemID(itemID);
        }
        if(botBuilder == null)
            botBuilder = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder(itemID);
        if(botBuilder == null)
        {
            BACKEND_INSTANCES.LOGGER.error("[SERVER] No default bot settings available for item: "+itemID);
            return false;
        }
        if(!hasItem(itemID))
        {
            int initialPrice = botBuilder.getSettings().defaultPrice;
            addTradeItem(itemID,initialPrice);
        }
        ServerTradingBot bot = getTradingBot(itemID);
        if(bot == null)
        {
            BACKEND_INSTANCES.LOGGER.info("[SERVER] Creating trading bot for item "+itemID);
            bot = new ServerVolatilityBot();
            setTradingBot(itemID, bot);
        }
        ServerVolatilityBot.Settings settings = (ServerVolatilityBot.Settings)bot.getSettings();
        botBuilder.loadDefaultSettings(settings);
        bot.setSettings(settings);
        return true;
    }

    public boolean addTradeItem(ItemID itemID, int startPrice)
    {
        if(tradeItems.containsKey(itemID))
        {
            BACKEND_INSTANCES.LOGGER.warn("[SERVER] Trade item already exists: " + itemID);
            return true;
        }
        if(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getNotTradableItems().contains(itemID))
        {
            BACKEND_INSTANCES.LOGGER.warn("[SERVER] Item "+itemID+" is not allowed for trading");
            return false;
        }
        return addTradeItem_internal(itemID, startPrice);
    }
    public boolean addTradeItemIfNotExists(ItemID itemID, int startPrice)
    {
        if(tradeItems.containsKey(itemID))
        {
            return true;
        }
        if(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getNotTradableItems().contains(itemID))
        {
            BACKEND_INSTANCES.LOGGER.warn("[SERVER] Item "+itemID+" is not allowed for trading");
            return false;
        }
        return addTradeItem_internal(itemID, startPrice);
    }

    public void removeAllTradingItems()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.cleanup();
        }
        tradeItems.clear();
        tradeItemsChunks.clear();
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }
        PlayerList playerList = server.getPlayerList();
        for(ServerPlayer player : playerList.getPlayers())
        {
            SyncTradeItemsPacket.sendPacket(player);
        }
    }
    public void removeTradingItem(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.cleanup();
        tradeItems.remove(itemID);
        rebuildTradeItemsChunks();
    }
    public boolean removeTradingItems(String category)
    {
        HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> botBuilder  = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder().getBots(category);
        if(botBuilder == null)
            return false;
        for(var itemData : botBuilder.entrySet())
        {
            removeTradingItem(itemData.getKey());
        }
        return true;
    }
    private boolean addTradeItem_internal(ItemID itemID, int startPrice)
    {
        if(!BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().allowItemID(itemID))
        {
            BACKEND_INSTANCES.LOGGER.warn("[SERVER] Item "+itemID+" can't be allowed for trading because it is not allowed in the bank system");
            return false;
        }
        ItemID currencyItemID = getCurrencyItem();

        ServerTradeItem tradeItem = new ServerTradeItem(itemID, currencyItemID, startPrice);
        tradeItems.put(itemID, tradeItem);
        rebuildTradeItemsChunks();

        MinecraftServer server = UtilitiesPlatform.getServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        for(ServerPlayer player : playerList.getPlayers())
        {
            SyncPricePacket.sendPacket(itemID, player);
            //SyncTradeItemsPacket.sendPacket(player);
        }
        return true;
    }

    public ArrayList<ItemID> getTradeItemIDs()
    {
        ArrayList<ItemID> tradeItemsList = new ArrayList<>();
        for(ServerTradeItem tradeItem : tradeItems.values())
        {
            tradeItemsList.add(tradeItem.getItemID());
        }
        return tradeItemsList;
    }

    public boolean hasItem(ItemID itemID)
    {
        return tradeItems.containsKey(itemID);
    }

    public void setTradingBot(ItemID itemID, ServerTradingBot bot)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.setTradingBot(bot);
    }
    public void removeTradingBot(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.removeTradingBot();
    }
    public boolean hasTradingBot(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return item.hasTradingBot();
    }
    public ServerTradingBot getTradingBot(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return item.getTradingBot();
    }

    public void disableAllTradingBots()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            ServerTradingBot bot = item.getTradingBot();
            if(bot != null)
            {
                bot.setEnabled(false);
            }
        }
    }
    public void enableAllTradingBots()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            ServerTradingBot bot = item.getTradingBot();
            if(bot != null)
            {
                bot.setEnabled(true);
            }
        }
    }

    public void setMarketOpen(ItemID itemID, boolean open)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.setMarketOpen(open);
    }

    public void setAllMarketsOpen(boolean open)
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.setMarketOpen(open);
        }
    }
    public boolean isMarketOpen(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return false;
        }
        return item.isMarketOpen();
    }

    public void shiftPriceHistory()
    {
        for(ServerTradeItem marketManager : tradeItems.values())
        {
            marketManager.shiftPriceHistory();
        }
    }
    public void addOrder(Order order)
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
    public void cancelOrder(long orderID)
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            if(item.cancelOrder(orderID))
                return;
        }
    }

    public void cancelAllOrders(UUID playerUUID)
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.cancelAllOrders(playerUUID);
        }
    }
    public void cancelAllOrders(UUID playerUUID, ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.cancelAllOrders(playerUUID);
    }

    public OrderbookVolume getOrderBookVolume(ItemID itemID, int tiles, int minPrice, int maxPrice)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return item.getOrderBookVolume(tiles, minPrice, maxPrice);
    }

    public ArrayList<Order> getOrders(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return new ArrayList<>();
        }
        return item.getOrders();
    }
    public ArrayList<Order> getOrders(ItemID itemID, UUID playerUUID)
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
    public ArrayList<Order> getOrdersFromUser(UUID playerUUID)
    {
        ArrayList<Order> orders = new ArrayList<>();
        for(ServerTradeItem item : tradeItems.values())
        {
            item.getOrders(playerUUID, orders);
        }
        return orders;
    }


    public int getPrice(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return 0;
        }
        return item.getPrice();
    }
    public PriceHistory getPriceHistory(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return null;
        }
        return item.getPriceHistory();
    }
    public void resetPriceChart(ItemID itemID)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.resetPriceChart();
    }
    public void resetPriceChart()
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.resetPriceChart();
        }
    }

    public void addPlayerUpdateSubscription(ItemID itemID, ServerPlayer player)
    {
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.addSubscriber(player);
    }
    public void removePlayerUpdateSubscription(ItemID itemID, ServerPlayer player) {
        ServerTradeItem item = tradeItems.get(itemID);
        if (item == null) {
            msgTradeItemNotFound(itemID);
            return;
        }
        item.removeSubscriber(player);
    }

    public Map<ItemID, ServerTradeItem> getTradeItems()
    {
        return tradeItems;
    }
    private void msgTradeItemNotFound(ItemID itemID) {
        BACKEND_INSTANCES.LOGGER.warn("[SERVER] Trade item not found: " + itemID.getName());
    }

    public void handlePacket(ServerPlayer player, RequestOrderCancelPacket packet)
    {
        BACKEND_INSTANCES.LOGGER.info("[SERVER] Receiving RequestOrderCancelPacket for order "+packet.getOrderID()+" from the player "+player.getName().getString());
        cancelOrder(packet.getOrderID());
    }
    public void handlePacket(ServerPlayer player, RequestOrderPacket packet)
    {
        int amount = packet.getAmount();
        ItemID itemID = packet.getItemID();
        ItemID currencyItemID = packet.getCurrencyItemID();
        String playerName = player.getName().getString();
        //MoneyBank playerBank = ServerBankManager.getBank(player.getUUID());
        RequestOrderPacket.OrderType orderType = packet.getOrderType();
        int price = packet.getPrice();
        BACKEND_INSTANCES.LOGGER.info("[SERVER] Receiving RequestOrderPacket for item "+packet.getItemID()+" from the player "+playerName);

        if(amount < 0)
        {
            // Selling
            BACKEND_INSTANCES.LOGGER.info("[SERVER] Player "+playerName+" is selling "+amount+" of "+itemID);
        }
        else if(amount > 0)
        {
            // Buying
            BACKEND_INSTANCES.LOGGER.info("[SERVER] Player "+playerName+" is buying "+amount+" of "+itemID);
        }

        switch(orderType)
        {
            case limit:
                LimitOrder limitOrder = LimitOrder.create(player, itemID, currencyItemID, amount, price);
                addOrder(limitOrder);
                break;
            case market:
                MarketOrder marketOrder = MarketOrder.create(player, itemID, currencyItemID, amount);
                addOrder(marketOrder);
                break;
        }
    }
    public void handlePacket(ServerPlayer player, RequestTradeItemsPacket packet)
    {
        SyncTradeItemsPacket.sendPacket(player);
    }
    public void handlePacket(ServerPlayer player, RequestPricePacket packet)
    {
        BACKEND_INSTANCES.LOGGER.info("[SERVER] Receiving RequestPricePacket for item "+packet.getItemID()+" from the player "+player.getName().getString());

        // Send the packet to the client
        SyncPricePacket.sendPacket(packet.getItemID(), player);
    }
    public void handlePacket(ServerPlayer player, UpdateSubscribeMarketEventsPacket packet)
    {
        boolean subscribe = packet.doesSubscribe();
        ItemID itemID = packet.getItemID();

        BACKEND_INSTANCES.LOGGER.info("[SERVER] Receiving UpdateSubscribeMarketEventsPacket for item "+itemID+
                " to "+(subscribe ? "subscribe" : "unsubscribe"));

        // Subscribe or unsubscribe the player
        if(subscribe) {
            addPlayerUpdateSubscription(itemID, player);
        } else {
            removePlayerUpdateSubscription(itemID, player);
        }
    }
    public void handlePacket(ServerPlayer player, RequestOrderChangePacket packet)
    {
        ItemID itemID = packet.getItemID();
        long targetOrderID = packet.getTargetOrderID();
        int newPrice = packet.getNewPrice();
        ServerTradeItem item = tradeItems.get(itemID);
        if(item == null)
        {
            msgTradeItemNotFound(itemID);
            return;
        }
        if(item.changeOrderPrice(targetOrderID, newPrice))
        {
            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getOrderReplacedMessage());
        }
        else {
            PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getOrderNotReplacedMessage());
        }
    }
    public void removePlayerUpdateSubscription(ServerPlayer player)
    {
        for(ServerTradeItem item : tradeItems.values())
        {
            item.removeSubscriber(player);
        }
    }
    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        long startMillis = System.currentTimeMillis();

        ListTag tradeItems = new ListTag();
        for(ServerTradeItem tradeItem : this.tradeItems.values())
        {
            CompoundTag tradeItemTag = new CompoundTag();
            success &= tradeItem.save(tradeItemTag);
            tradeItems.add(tradeItemTag);
        }
        tag.put("tradeItems", tradeItems);
        long endMillis = System.currentTimeMillis();
        BACKEND_INSTANCES.LOGGER.info("[SERVER] Saving ServerStockMarketManager took "+(endMillis-startMillis)+"ms");

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean loadSuccess = true;
        try {
            if(!tag.contains("tradeItems"))
                return false;

            ListTag tradeItems = tag.getList("tradeItems", 10);
            Map<ItemID, ServerTradeItem> tradeItemsMap = new HashMap<>();
            for(int i = 0; i < tradeItems.size(); i++)
            {
                CompoundTag tradeItemTag = tradeItems.getCompound(i);
                ServerTradeItem tradeItem = ServerTradeItem.loadFromTag(tradeItemTag);
                if(tradeItem == null)
                {
                    loadSuccess = false;
                    continue;
                }
                if(!tradeItem.getItemID().isValid())
                    continue;
                tradeItemsMap.put(tradeItem.getItemID(), tradeItem);
            }
            this.tradeItems.clear();
            this.tradeItems.putAll(tradeItemsMap);
            rebuildTradeItemsChunks();
        } catch (Exception e) {
            BACKEND_INSTANCES.LOGGER.error("[SERVER] Error loading ServerStockMarketManager from NBT", e);
            return false;
        }
        return loadSuccess;
    }

    public void onServerTick(MinecraftServer server)
    {
        if(tradeItemsChunks.size() == 0)
            return;


        long currentTime = System.currentTimeMillis();

        // For better performance when there are many trade items
        // The items are processed in chunks
        // Downside: The update rate is not every tick but every n't ticks depending on how many chunks there are
        ArrayList<ServerTradeItem> chunk = tradeItemsChunks.get(tradeItemUpdateCallCounter);
        tradeItemUpdateCallCounter = (tradeItemUpdateCallCounter + 1) % tradeItemsChunks.size();
        for(ServerTradeItem item : chunk)
        {
            item.onServerTick(server);
        }


        long currentTime2 = System.currentTimeMillis();
        if(currentTime2-currentTime > 5)
            BACKEND_INSTANCES.LOGGER.info("Market update time: " + (currentTime2-currentTime)+"ms");
    }

    public ItemID getCurrencyItem()
    {
        return new ItemID(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getCurrencyItem());
    }

    private void rebuildTradeItemsChunks()
    {
        tradeItemsChunks.clear();
        int tradeItemsChunkSize = BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.TRADE_ITEM_CHUNK_SIZE.get();

        int chunks = tradeItems.size() / tradeItemsChunkSize;
        if(tradeItems.size() % tradeItemsChunkSize != 0)
            chunks++;
        for(int i = 0; i < chunks; i++)
        {
            ArrayList<ServerTradeItem> chunk = new ArrayList<>();
            int start = i * tradeItemsChunkSize;
            int end = Math.min(start + tradeItemsChunkSize, tradeItems.size());
            int index = 0;
            for(ServerTradeItem item : tradeItems.values())
            {
                if(index >= start && index < end)
                {
                    chunk.add(item);
                }
                index++;
            }
            tradeItemsChunks.add(chunk);
        }
    }


}
