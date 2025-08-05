package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientStockMarketManager {

    protected StockMarketModBackend.Instances BACKEND_INSTANCES;
    public ClientStockMarketManager(StockMarketModBackend.Instances backendInstances)
    {
        this.BACKEND_INSTANCES = backendInstances;
        ClientMarket.setBackend(backendInstances);
    }

    private final List<ClientMarket> clientMarkets = new ArrayList<>();
    private boolean initialized = false;

    public void init()
    {
        if(!initialized)
        {
            debug("Initializing client stock market manager.");
            requestTradingPairs((result) -> {
                populateClientMarkets(result); // populate the client markets with the received trading pairs
                initialized = true;
                debug("Client markets initialized with " + clientMarkets.size() + " trading pairs.");
            });
        }
    }

    public ClientMarket getClientMarket(TradingPair pair)
    {
        if(pair == null)
            return null;
        for(ClientMarket market : clientMarkets)
        {
            if(market.getTradingPair().equals(pair))
            {
                return market;
            }
        }
        if(!initialized)
        {
            init();
        }
        return null;
    }


    public void requestCreateMarket(TradingPair pair, Consumer<Boolean> callback )
    {
        StockMarketNetworking.CREATE_MARKET_REQUEST.sendRequestToServer(new TradingPairData(pair), (result) -> {
            if(result)
            {
                ClientMarket clientMarket = new ClientMarket(pair);
                clientMarkets.add(clientMarket);
            }
            callback.accept(result);
        });
    }
    public void requestCreateMarket(MarketFactory.DefaultMarketSetupData setupData, Consumer<Boolean> callback )
    {
        StockMarketNetworking.CREATE_MARKETS_REQUEST.sendRequestToServer(List.of(setupData), (result) -> {
            if(result.size() == 1 && result.get(0)) // Check if the market was successfully created
            {
                callback.accept(true);
            }
            callback.accept(false);
        });
    }
    public void requestCreateMarkets(List<MarketFactory.DefaultMarketSetupData> setupDataList, Consumer<List<Boolean>> callback)
    {
        StockMarketNetworking.CREATE_MARKETS_REQUEST.sendRequestToServer(setupDataList, (result) -> {
            for(int i=0; i<setupDataList.size(); i++)
            {
                if(result.get(i)) {
                    TradingPair pair = setupDataList.get(i).tradingPair;
                    ClientMarket clientMarket = getClientMarket(pair);
                    if (clientMarket == null) // Only add if the market was successfully created
                    {
                        clientMarket = new ClientMarket(pair);
                        clientMarkets.add(clientMarket);
                    }
                }
            }
            callback.accept(result);
        });
    }
    public void requestRemoveMarket(TradingPair pair, Consumer<Boolean> callback)
    {
        StockMarketNetworking.REMOVE_MARKET_REQUEST.sendRequestToServer(List.of(pair), (result) -> {
            if(result.size() == 1)
            {
                ClientMarket clientMarket = getClientMarket(pair);
                if(clientMarket != null)
                {
                    clientMarkets.remove(clientMarket);
                    clientMarket.setDead();
                }
                callback.accept(result.get(0));
            }
            else
            {
                callback.accept(false);
            }
        });
    }
    public void requestRemoveMarket(List<TradingPair> pairs, Consumer<List<Boolean>> callback )
    {
        StockMarketNetworking.REMOVE_MARKET_REQUEST.sendRequestToServer(pairs, (result) -> {
            if(pairs.size() == result.size()) {
                for (int i = 0; i < pairs.size(); i++) {
                    if(!result.get(i)) {
                        continue; // Skip if the removal was not successful
                    }
                    ClientMarket clientMarket = getClientMarket(pairs.get(i));
                    if (clientMarket != null) {
                        clientMarkets.remove(clientMarket);
                        clientMarket.setDead();
                    }
                }
            }
            callback.accept(result);
        });
    }

    public void requestChartReset(List<TradingPair> pairs, Consumer<List<Boolean>> callback) {
        StockMarketNetworking.CHART_RESET_REQUEST.sendRequestToServer(pairs, callback);
    }
    public void requestSetMarketOpen(TradingPair pair, boolean open, Consumer<Boolean> callback)
    {
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(List.of(new Tuple<>(pair, open)), (result) -> {
            if(result.size() == 1)
                callback.accept(result.get(0));
            else
                callback.accept(false);
        });
    }
    public void requestSetMarketOpen(List<Tuple<TradingPair, Boolean>> pairs, Consumer<List<Boolean>> callback)
    {
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(pairs, callback);
    }
    public void requestSetMarketOpen(List<TradingPair> pairs, boolean allOpen, Consumer<List<Boolean>> callback)
    {
        List<Tuple<TradingPair, Boolean>> pairsTuples = new ArrayList<>();
        for(TradingPair pair : pairs)
        {
            pairsTuples.add(new Tuple<>(pair, allOpen));
        }
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(pairsTuples, callback);
    }


    public void requestTradingPairs(Consumer<List<TradingPair>> callback )
    {
        StockMarketNetworking.TRADING_PAIR_LIST_REQUEST.sendRequestToServer(false, (result)->{
            List<TradingPair> receivedPairs = new ArrayList<>();
            for(TradingPairData tradingPairData : result.tradingPairs)
            {
                receivedPairs.add(tradingPairData.toTradingPair());
            }
            populateClientMarkets(receivedPairs); // populate the client markets with the received trading pairs
            callback.accept(receivedPairs);
        });
    }
    public void requestMarketCategories(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback)
    {
        StockMarketNetworking.DEFAULT_MARKET_SETUP_DATA_GROUPS_REQUEST.sendRequestToServer(false, callback);
    }
    public void requestIsTradingPairAllowed(TradingPair pair, Consumer<Boolean> callback )
    {
        StockMarketNetworking.IS_TRADING_PAIR_ALLOWED_REQUEST.sendRequestToServer(pair, callback);
    }
    public void requestRecommendedPrice(TradingPair pair, Consumer<Integer> callback )
    {
        StockMarketNetworking.GET_RECOMMENDED_PRICE_REQUEST.sendRequestToServer(pair, callback);
    }
    public void requestPotentialTradeItems(String searchText, Consumer<List<ItemID>> callback) {
        StockMarketNetworking.POTENTIAL_TRADING_ITEMS_REQUEST.sendRequestToServer(searchText, callback);
    }





    public void handlePacket(SyncTradeItemsPacket packet)
    {
        List<TradingPair> receivedPairs = new ArrayList<>();
        for(TradingPairData tradingPairData : packet.data.tradingPairs)
        {
            receivedPairs.add(tradingPairData.toTradingPair());
        }
        populateClientMarkets(receivedPairs);
    }





    private void populateClientMarkets(List<TradingPair> receivedPairs)
    {
        List<ClientMarket> toRemove = new ArrayList<>(clientMarkets);

        for(TradingPair tradingPair : receivedPairs)
        {
            ClientMarket clientMarket = getClientMarket(tradingPair);
            if(clientMarket == null)
            {
                clientMarket = new ClientMarket(tradingPair);
                clientMarkets.add(clientMarket);
            }
            else {
                toRemove.remove(clientMarket);
            }
        }

        for(ClientMarket clientMarket : toRemove)
        {
            clientMarkets.remove(clientMarket);
        }
        initialized = true;
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ClientStockMarketManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientStockMarketManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerStockMarketManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ClientStockMarketManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ClientStockMarketManager] " + msg);
    }

    private StockMarketNetworking getNetworking() {
        return BACKEND_INSTANCES.NETWORKING;
    }
    public static UUID getLocalPlayerUUID()
    {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
    }



   /* private final Map<ItemID, ClientTradeItem> tradeItems = new HashMap<>();
    private ItemID CURRENCY_ITEM = null;

    private SyncBotSettingsPacket syncBotSettingsPacket;
    private SyncBotTargetPricePacket syncBotTargetPricePacket;
    //privatc SyncTradeItemsPacket syncTradeItemsPacket;
    private boolean syncTradeItemsChanged;

    private boolean syncBotSettingsPacketChanged = false;
    private boolean syncBotTargetPricePacketChanged = false;


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
    }*/
}
