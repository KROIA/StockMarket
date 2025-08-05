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

public class ClientMarketManager {

    protected StockMarketModBackend.Instances BACKEND_INSTANCES;
    public ClientMarketManager(StockMarketModBackend.Instances backendInstances)
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
        BACKEND_INSTANCES.LOGGER.info("[ClientMarketManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ClientMarketManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ClientMarketManager] " + msg);
    }

    private StockMarketNetworking getNetworking() {
        return BACKEND_INSTANCES.NETWORKING;
    }
    public static UUID getLocalPlayerUUID()
    {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
    }

}
