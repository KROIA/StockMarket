package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ServerStockMarketManager implements ServerSaveable
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }



    private final Map<TradingPair, ServerMarket> markets = new HashMap<>();
    private final ArrayList<ArrayList<ServerMarket>> tradeItemsChunks = new ArrayList<>(); // For processing trade items in chunks
    private int tradeItemUpdateCallCounter = 0;

    public ServerStockMarketManager()
    {
        /*for(var item : BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.INITIAL_TRADABLE_ITEMS.get().entrySet())
        {
            addTradeItem(item.getKey(), item.getValue(), item.getValue()
        }
        rebuildTradeItemsChunks();*/
    }


    public @Nullable BotSettingsData getBotSettingsData(@NotNull TradingPair pair)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getBotSettingsData();
    }
    public @Nullable TradingPairData getTradingPairData(@NotNull TradingPair pair)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getTradingPairData();
    }
    public @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair, int historyViewCount, int minPrice, int maxPrice, int tileCount)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getOrderBookVolumeData(historyViewCount, minPrice, maxPrice, tileCount);
    }
    public @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getOrderBookVolumeData();
    }
    public @Nullable OrderReadData getOrderReadData(@NotNull TradingPair pair, long orderID)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getOrderReadData(orderID);
    }
    public @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, List<Long> orderIDs)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getOrderReadListData(orderIDs);
    }
    public @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, UUID playerUUID)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getOrderReadListData(playerUUID);
    }
    public @Nullable PriceHistoryData getPriceHistoryData(@NotNull TradingPair pair, int maxHistoryPointCount)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getPriceHistoryData(maxHistoryPointCount);
    }
    public @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair, UUID player,
                                                        int maxHistoryPointCount,
                                                        int minVisiblePrice,
                                                        int maxVisiblePrice,
                                                        int orderBookTileCount,
                                                        boolean requestBotTargetPrice)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getTradingViewData(player, maxHistoryPointCount, minVisiblePrice, maxVisiblePrice, orderBookTileCount, requestBotTargetPrice);
    }
    public @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair, UUID player)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getTradingViewData(player);
    }
    public @Nullable ServerMarketSettingsData getMarketSettingsData(@NotNull TradingPair pair)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return null;
        return market.getMarketSettingsData();
    }
    public @NotNull TradingPairListData getTradingPairListData()
    {
        return new TradingPairListData(getTradingPairs());
    }
    public boolean setMarketSettingsData(@NotNull TradingPair pair, @Nullable ServerMarketSettingsData settingsData)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return false;
        return market.setMarketSettingsData(settingsData);
    }
    public boolean setBotSettingsData(@NotNull TradingPair pair, @Nullable BotSettingsData botSettingsData)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
            return false;
        return market.setBotSettingsData(botSettingsData);
    }


    public boolean handleOrderCreateData(@NotNull OrderCreateData orderCreateData, ServerPlayer sender)
    {
        if(orderCreateData.owner.compareTo(sender.getUUID()) != 0) {
            if(!playerIsAdmin(sender)) {
                String ownerName = PlayerUtilities.getUUIDToNameMap().get(orderCreateData.owner);
                if(ownerName == null)
                    ownerName = orderCreateData.owner.toString();
                error("Player " + sender.getName().getString() + " tried to create order for " + ownerName + " but is not the owner or admin.");
                return false; // If the player is not an admin, return false
            }
        }

        TradingPair pair = orderCreateData.tradingPair.toTradingPair();
        if(pair == null || !pair.isValid())
        {
            error("Invalid trading pair in OrderCreateData: " + orderCreateData.tradingPair);
            return false;
        }

        ServerMarket market = markets.get(pair);
        if(market == null)
        {
            error("Market not found for trading pair: " + pair);
            return false;
        }

        if(orderCreateData.type == Order.Type.LIMIT)
        {
            return market.createLimitOrder(orderCreateData.owner, orderCreateData.volume, orderCreateData.limitPrice);
        }
        else if(orderCreateData.type == Order.Type.MARKET)
        {
            return market.createMarketOrder(orderCreateData.owner, orderCreateData.volume);
        }
        else
        {
            error("Unknown order type in OrderCreateData: " + orderCreateData.type);
        }
        return false;
    }
    public boolean handleOrderChangeData(@NotNull OrderChangeData orderChangeData, ServerPlayer sender)
    {
        TradingPair pair = orderChangeData.tradingPair.toTradingPair();
        if(pair == null || !pair.isValid())
        {
            error("Invalid trading pair in OrderChangeData: " + orderChangeData.tradingPair);
            return false;
        }

        ServerMarket market = markets.get(pair);
        if(market == null)
        {
            error("Market not found for trading pair: " + pair);
            return false;
        }
        // Check ownership
        Order order = market.getOrderBook().getOrder(orderChangeData.orderID);
        if(order == null)
            return false;
        if(!order.isOwner(sender))
        {
            if(!playerIsAdmin(sender))
            {
                error("Player " + sender.getName().getString() + " tried to change order " + orderChangeData.orderID + " but is not the owner or admin.");
                return false;
            }
        }
        return market.changeOrderPrice(orderChangeData.orderID, orderChangeData.newPrice);
    }
    public boolean handleOrderCancelData(@NotNull OrderCancelData orderCancelData, ServerPlayer sender)
    {
        TradingPair pair = orderCancelData.tradingPair.toTradingPair();
        if(pair == null || !pair.isValid())
        {
            error("Invalid trading pair in OrderCancelData: " + orderCancelData.tradingPair);
            return false;
        }

        ServerMarket market = markets.get(pair);
        if(market == null)
        {
            error("Market not found for trading pair: " + pair);
            return false;
        }

        // Check ownership
        Order order = market.getOrderBook().getOrder(orderCancelData.orderID);
        if(order == null)
            return false;
        if(!order.isOwner(sender))
        {
            if(!playerIsAdmin(sender))
            {
                error("Player " + sender.getName().getString() + " tried to cancel order " + orderCancelData.orderID + " but is not the owner or admin.");
                return false;
            }
        }

        return market.cancelOrder(orderCancelData.orderID);
    }


    public void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS) {
        for(ServerMarket market : markets.values())
        {
            market.setShiftPriceCandleIntervalMS(shiftPriceCandleIntervalMS);
        }
    }
    /*public void setNotifySubscriberIntervalMS(long notifySubscriberIntervalMS) {
        for(ServerMarket market : markets.values())
        {
            market.setNotifySubscriberIntervalMS(notifySubscriberIntervalMS);
        }
    }*/


    public void setAllMarketsOpen(boolean open)
    {
        for(ServerMarket market : markets.values())
        {
            market.setMarketOpen(open);
        }
    }
    public boolean setMarketOpen(@NotNull TradingPair pair, boolean open)
    {
        ServerMarket market = markets.get(pair);
        if(market == null)
        {
            warn("Market not found for trading pair: " + pair);
            return false;
        }
        market.setMarketOpen(open);
        return true;
    }
    public List<Boolean> setMarketOpen(List<Tuple<TradingPair, Boolean>> pairsAndOpenStates)
    {
        List<Boolean> results = new ArrayList<>(pairsAndOpenStates.size());
        for(Tuple<TradingPair, Boolean> pairAndOpenState : pairsAndOpenStates)
        {
            TradingPair pair = pairAndOpenState.getA();
            boolean open = pairAndOpenState.getB();
            boolean result = setMarketOpen(pair, open);
            results.add(result);
        }
        return results;
    }

    public ItemID getDefaultCurrencyItemID()
    {
        return new ItemID(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getCurrencyItem());
    }
    public boolean isItemAllowedForTrading(ItemID item)
    {
        return !getNotTradableItems().containsKey(item);
    }
    public Map<ItemID, Boolean> getNotTradableItems()
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getNotTradableItems();
    }
    public boolean isTradingPairAllowedForTrading(TradingPair pair)
    {
        pair.checkValidity(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getNotTradableItems());
        return pair.isValid();
    }

    public ServerMarket getMarket(@NotNull TradingPair pair)
    {
        return markets.get(pair);
    }
    public List<TradingPair> getTradingPairs()
    {
        return new ArrayList<>(markets.keySet());
    }
    public List<ItemID> getPotentialTradingItems(String searchQuery)
    {
        List<ItemID> items = new ArrayList<>();
        List<ItemStack> allItemStacks = ItemUtilities.getSearchCreativeItems(searchQuery);
        Map<ItemID, Boolean> blackList = getNotTradableItems();
        for(ItemStack itemStack : allItemStacks)
        {
            ItemID itemID = new ItemID(itemStack);
            /*String itemName = itemStack.getDisplayName().getString();
            if(itemName.contains("Dollar"))
            {
                // Skip items that are not allowed for trading, like money items
                debug("Skipping item: " + itemName + " because it is not allowed for trading");
            }*/
            if(itemID.isValid() && !blackList.containsKey(itemID))
            {
                items.add(itemID);
            }
        }
        return items;
    }
    public boolean marketExists(@NotNull TradingPair pair)
    {
        return markets.containsKey(pair);
    }

    public int getRecommendedPrice(TradingPair pair)
    {
        return 10;
    }


    public void onServerTick(MinecraftServer server)
    {
        if(tradeItemsChunks.isEmpty())
            return;


        long currentTime = System.currentTimeMillis();

        // For better performance when there are many trade items
        // The items are processed in chunks
        // Downside: The update rate is not every tick but every n't ticks depending on how many chunks there are
        ArrayList<ServerMarket> chunk = tradeItemsChunks.get(tradeItemUpdateCallCounter);
        tradeItemUpdateCallCounter = (tradeItemUpdateCallCounter + 1) % tradeItemsChunks.size();
        for(ServerMarket item : chunk)
        {
            item.update(server);
        }


        long currentTime2 = System.currentTimeMillis();
        if(currentTime2-currentTime > 5)
            BACKEND_INSTANCES.LOGGER.info("Market update time: " + (currentTime2-currentTime)+"ms");
    }



    public boolean createMarket(MarketFactory.DefaultMarketSetupData defaultMarketSetupData)
    {
        if(defaultMarketSetupData == null)
        {
            warn("Default market setup data is null");
            return false;
        }
        TradingPair pair = defaultMarketSetupData.tradingPair;
        if(pair == null)
        {
            warn("Trading pair is null in default market setup data:\n" + defaultMarketSetupData);
            return false;
        }
        if(!pair.isValid())
        {
            warn("Invalid trading pair in default market setup data:\n" + pair);
            return false;
        }
        if(markets.containsKey(pair))
        {
            warn("Trading pair: " + pair.getShortDescription() + " already exists");
            return true;
        }
        ServerMarket market = MarketFactory.createMarket(defaultMarketSetupData);
        if(market != null)
        {
            markets.put(market.getTradingPair(), market);
            onMarketAdded(market);
            rebuildTradeItemsChunks();
            return true;
        }
        return false;
    }
    public boolean createMarket(MarketFactory.DefaultMarketSetupDataGroup category)
    {
        if(category == null || category.marketSetupDataList.isEmpty())
        {
            warn("Category is null or empty: " + category);
            return false;
        }
        boolean success = true;
        for(MarketFactory.DefaultMarketSetupData data : category.marketSetupDataList)
        {
            TradingPair pair = data.tradingPair;
            if(pair == null)
            {
                warn("Trading pair is null in default market setup data:\n" + data);
                success = false;
                continue;
            }
            if(!pair.isValid())
            {
                warn("Invalid trading pair in default market setup data:\n" + pair);
                success = false;
                continue;
            }
            if(markets.containsKey(pair))
            {
                warn("Trading pair: " + pair.getShortDescription() + " already exists");
                success = false;
                continue;
            }
            ServerMarket market = MarketFactory.createMarket(data);
            if(market != null)
            {
                markets.put(market.getTradingPair(), market);
                onMarketAdded(market);
            }
        }
        rebuildTradeItemsChunks();
        return success;
    }

    public boolean createMarket(@NotNull TradingPair pair, int startPrice)
    {
        if(markets.containsKey(pair))
        {
            warn("Trading pair: " + pair.getShortDescription() + " already exists");
            return true;
        }
        ServerMarket market = MarketFactory.createMarket(pair, startPrice);
        if(market != null)
        {
            markets.put(market.getTradingPair(), market);
            onMarketAdded(market);
            rebuildTradeItemsChunks();
            return true;
        }
        return false;
    }
    public boolean createMarket(@NotNull ItemID itemID, @NotNull ItemID currency, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, currency);
        return createMarket(tradingPair, startPrice);
    }
    public boolean createMarket(@NotNull ItemID itemID, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, getDefaultCurrencyItemID());
        return createMarket(tradingPair, startPrice);
    }
    public boolean createMarket(@NotNull ServerMarketSettingsData settingsData)
    {
        TradingPair pair = settingsData.tradingPairData.toTradingPair();
        if(markets.containsKey(pair))
        {
            warn("Trading pair: " + pair.getShortDescription() + " already exists");
            return false;
        }
        ServerMarket market = MarketFactory.createMarket(settingsData);
        if(market != null)
        {
            markets.put(market.getTradingPair(), market);
            onMarketAdded(market);
            rebuildTradeItemsChunks();
            return true;
        }
        return false;
    }

    /*public boolean createMarkets(@NotNull List<ServerMarketSettingsData> settingsDataList)
    {
        List<ServerMarketSettingsData> cpy = new ArrayList<>(settingsDataList.size());
        boolean success = true;
        for(ServerMarketSettingsData settingsData : settingsDataList)
        {
            if(settingsData == null)
            {
                warn("Invalid market settings data: " + settingsData);
                success = false;
                continue;
            }
            if(markets.containsKey(settingsData.tradingPairData.toTradingPair()))
            {
                warn("Trading pair already exists: " + settingsData.tradingPairData.toTradingPair().getShortDescription());
                success = false;
                continue;
            }
            cpy.add(settingsData);
        }
        List<ServerMarket> markets = MarketFactory.createMarkets(cpy);
        for(ServerMarket market : markets)
        {
            this.markets.put(market.getTradingPair(), market);
            onMarketAdded(market);
        }
        success &= markets.size() == cpy.size();
        rebuildTradeItemsChunks();
        return success;
    }*/
    public List<Boolean> createMarkets(@NotNull List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList)
    {
        List<Boolean> results = new ArrayList<>(defaultMarketSetupDataList.size());
        for(MarketFactory.DefaultMarketSetupData data : defaultMarketSetupDataList)
        {
            if(marketExists(data.tradingPair))
            {
                warn("Market already exists for trading pair: " + data.tradingPair.getShortDescription());
                results.add(false);
                continue;
            }
            boolean result = createMarket(data);
            results.add(result);
        }
        return results;
    }

    public boolean removeTradeItem(@NotNull TradingPair pair)
    {
        if(!pair.isValid())
        {
            warn("Invalid trading pair: " + pair);
            return false;
        }
        ServerMarket market = markets.remove(pair);
        if(market == null)
        {
            warn("Market not found for trading pair: " + pair);
            return false;
        }

        onMarketRemoved(market);
        rebuildTradeItemsChunks();
        return true;
    }
    public boolean removeTradeItem(@NotNull ItemID itemID, @NotNull ItemID currency)
    {
        TradingPair pair = new TradingPair(itemID, currency);
        return removeTradeItem(pair);
    }



    protected void onMarketAdded(ServerMarket market)
    {

    }
    protected void onMarketRemoved(ServerMarket market)
    {
        //market.cleanup();
    }





    /*private boolean addTradeItem_internal(TradingPair pair, int startPrice)
    {
        ItemID item = pair.getItem();
        ItemID currency = pair.getCurrency();
        IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager();
        if(!bankManager.isItemIDAllowed(item))
        {
            if(!bankManager.allowItemID(item)){
                error("Pair: " + pair + " can't be allowed for trading because the item: "+ item +" is not allowed in the bank system");
                return false;
            }
        }
        if(!bankManager.isItemIDAllowed(currency))
        {
            if(!bankManager.allowItemID(currency)){
                error("Pair: " + pair + " can't be allowed for trading because the currency: "+ currency+" is not allowed in the bank system");
                return false;
            }
        }
        ServerMarket market = new ServerMarket(pair, startPrice);
        markets.put(pair, market);
        onMarketAdded(market);
        return true;
    }*/

    private void rebuildTradeItemsChunks()
    {
        tradeItemsChunks.clear();
        tradeItemUpdateCallCounter = 0;
        int tradeItemsChunkSize = BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.TRADE_ITEM_CHUNK_SIZE.get();

        int chunks = markets.size() / tradeItemsChunkSize;
        if(markets.size() % tradeItemsChunkSize != 0)
            chunks++;
        for(int i = 0; i < chunks; i++)
        {
            ArrayList<ServerMarket> chunk = new ArrayList<>();
            int start = i * tradeItemsChunkSize;
            int end = Math.min(start + tradeItemsChunkSize, markets.size());
            int index = 0;
            for(ServerMarket market : markets.values())
            {
                if(index >= start && index < end)
                {
                    chunk.add(market);
                }
                index++;
            }
            tradeItemsChunks.add(chunk);
        }
    }



    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        long startMillis = System.currentTimeMillis();

        ListTag tradeItems = new ListTag();
        for(ServerMarket tradeItem : this.markets.values())
        {
            CompoundTag tradeItemTag = new CompoundTag();
            success &= tradeItem.save(tradeItemTag);
            tradeItems.add(tradeItemTag);
        }
        tag.put("markets", tradeItems);
        long endMillis = System.currentTimeMillis();
        info("Saving ServerStockMarketManager took "+(endMillis-startMillis)+"ms");

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean loadSuccess = true;
        try {
            if(!tag.contains("markets"))
                return false;

            ListTag tradeItems = tag.getList("markets", CompoundTag.TAG_COMPOUND);
            Map<TradingPair, ServerMarket> tradeItemsMap = new HashMap<>();
            for(int i = 0; i < tradeItems.size(); i++)
            {
                CompoundTag tradeItemTag = tradeItems.getCompound(i);
                ServerMarket tradeItem = new ServerMarket();
                if(!tradeItem.load(tradeItemTag))
                {
                    error("Failed to load trade item from NBT: " + tradeItemTag);
                    loadSuccess = false;
                    continue;
                }
                if(!tradeItem.getTradingPair().isValid())
                    continue;
                tradeItemsMap.put(tradeItem.getTradingPair(), tradeItem);
            }
            this.markets.clear();
            this.markets.putAll(tradeItemsMap);
            rebuildTradeItemsChunks();
        } catch (Exception e) {
            error("Error loading ServerStockMarketManager from NBT", e);
            return false;
        }
        return loadSuccess;
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ServerStockMarketManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerStockMarketManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerStockMarketManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ServerStockMarketManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ServerStockMarketManager] " + msg);
    }


    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.playerIsAdmin(player);
    }
}
