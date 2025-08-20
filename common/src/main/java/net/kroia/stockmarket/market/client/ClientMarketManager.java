package net.kroia.stockmarket.market.client;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IClientMarket;
import net.kroia.stockmarket.api.IClientMarketManager;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAdjustmentFactorsData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientMarketManager implements IClientMarketManager {

    protected StockMarketModBackend.Instances BACKEND_INSTANCES;
    public ClientMarketManager(StockMarketModBackend.Instances backendInstances)
    {
        this.BACKEND_INSTANCES = backendInstances;
        ClientMarket.setBackend(backendInstances);
    }

    private final List<ClientMarket> clientMarkets = new ArrayList<>();
    private boolean initialized = false;
    //@Override
    private void init()
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
    @Override
    public @Nullable IClientMarket getClientMarket(TradingPair pair)
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

    @Override
    public void requestCreateMarket(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback )
    {
        StockMarketNetworking.CREATE_MARKET_REQUEST.sendRequestToServer(new TradingPairData(pair), (result) -> {
            if(result)
            {
                addClientMarket(pair); // Add the market to the client markets if it was successfully created
            }
            callback.accept(result);
        });
    }
    @Override
    public void requestCreateMarket(@NotNull MarketFactory.DefaultMarketSetupData setupData, @NotNull Consumer<Boolean> callback )
    {
        StockMarketNetworking.CREATE_MARKETS_REQUEST.sendRequestToServer(List.of(setupData), (result) -> {
            if(result.size() == 1 && result.get(0)) // Check if the market was successfully created
                callback.accept(true);
            else
                callback.accept(false);
        });
    }
    @Override
    public void requestCreateMarkets(@NotNull List<MarketFactory.DefaultMarketSetupData> setupDataList, @NotNull Consumer<List<Boolean>> callback)
    {
        StockMarketNetworking.CREATE_MARKETS_REQUEST.sendRequestToServer(setupDataList, (result) -> {
            for(int i=0; i<setupDataList.size(); i++)
            {
                if(result.get(i)) {
                    TradingPair pair = setupDataList.get(i).tradingPair;
                    addClientMarket(pair); // Add the market to the client markets if it was successfully created
                }
            }
            callback.accept(result);
        });
    }
    @Override
    public void requestRemoveMarket(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback)
    {
        StockMarketNetworking.REMOVE_MARKET_REQUEST.sendRequestToServer(List.of(pair), (result) -> {
            if(result.size() == 1)
            {
                removeClientMarket(pair); // Remove the market from the client markets if it was successfully removed
                callback.accept(result.get(0));
            }
            else
            {
                callback.accept(false);
            }
        });
    }
    @Override
    public void requestRemoveMarket(@NotNull List<TradingPair> pairs, @NotNull Consumer<List<Boolean>> callback )
    {
        StockMarketNetworking.REMOVE_MARKET_REQUEST.sendRequestToServer(pairs, (result) -> {
            if(pairs.size() == result.size()) {
                for (int i = 0; i < pairs.size(); i++) {
                    if(!result.get(i)) {
                        continue; // Skip if the removal was not successful
                    }
                    removeClientMarket(pairs.get(i)); // Remove the market from the client markets if it was successfully removed
                }
            }
            callback.accept(result);
        });
    }

    @Override
    public void requestChartReset(@NotNull List<TradingPair> pairs, @NotNull Consumer<List<Boolean>> callback) {
        StockMarketNetworking.CHART_RESET_REQUEST.sendRequestToServer(pairs, callback);
    }
    @Override
    public void requestSetMarketOpen(TradingPair pair, boolean open, Consumer<Boolean> callback)
    {
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(List.of(new Tuple<>(pair, open)), (result) -> {
            if(result.size() == 1)
                callback.accept(result.get(0));
            else
                callback.accept(false);
        });
    }
    @Override
    public void requestSetMarketOpen(@NotNull List<Tuple<TradingPair, Boolean>> pairs, @NotNull Consumer<List<Boolean>> callback)
    {
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(pairs, callback);
    }
    @Override
    public void requestSetMarketOpen(@NotNull List<TradingPair> pairs, boolean allOpen, @NotNull Consumer<List<Boolean>> callback)
    {
        List<Tuple<TradingPair, Boolean>> pairsTuples = new ArrayList<>();
        for(TradingPair pair : pairs)
        {
            pairsTuples.add(new Tuple<>(pair, allOpen));
        }
        StockMarketNetworking.SET_MARKET_OPEN_REQUEST.sendRequestToServer(pairsTuples, callback);
    }


    @Override
    public void requestTradingPairs(@NotNull Consumer<List<TradingPair>> callback )
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
    @Override
    public void requestMarketCategories(@NotNull Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback)
    {
        StockMarketNetworking.DEFAULT_MARKET_SETUP_DATA_GROUPS_REQUEST.sendRequestToServer(false, callback);
    }
    @Override
    public void requestIsTradingPairAllowed(@NotNull TradingPair pair, @NotNull Consumer<Boolean> callback )
    {
        StockMarketNetworking.IS_TRADING_PAIR_ALLOWED_REQUEST.sendRequestToServer(pair, callback);
    }
    @Override
    public void requestRecommendedPrice(@NotNull TradingPair pair, @NotNull Consumer<Float> callback )
    {
        StockMarketNetworking.GET_RECOMMENDED_PRICE_REQUEST.sendRequestToServer(pair, callback);
    }
    @Override
    public void requestPotentialTradeItems(@NotNull String searchText, @NotNull Consumer<List<ItemID>> callback) {
        StockMarketNetworking.POTENTIAL_TRADING_ITEMS_REQUEST.sendRequestToServer(searchText, callback);
    }

    @Override
    public void requestDefaultPriceAdjustmentFactors(@NotNull Consumer<DefaultPriceAdjustmentFactorsData> callback)
    {
        StockMarketNetworking.DEFAULT_PRICE_ADJUSTMENT_FACTORS_REQUEST.sendRequestToServer(null, callback);
    }
    @Override
    public void updateDefaultPriceAdjustmentFactors(@NotNull DefaultPriceAdjustmentFactorsData data, @NotNull Consumer<DefaultPriceAdjustmentFactorsData> callback)
    {
        StockMarketNetworking.DEFAULT_PRICE_ADJUSTMENT_FACTORS_REQUEST.sendRequestToServer(data, callback);
    }






    @Override
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
        List<TradingPair> toRemove = new ArrayList<>(clientMarkets.size());
        for(ClientMarket market : clientMarkets)
        {
            toRemove.add(market.getTradingPair()); // Collect markets that are dead or null
        }

        for(TradingPair tradingPair : receivedPairs)
        {
            if(tradingPair == null)
                continue; // Skip null trading pairs
            if(!marketExists(tradingPair))
            {
                addClientMarket(tradingPair); // Add the market to the client markets if it does not exist
            }
            toRemove.remove(tradingPair); // Remove the trading pair from the toRemove list if it exists
        }

        for(TradingPair clientMarket : toRemove)
        {
            removeClientMarket(clientMarket); // Remove the markets that are no longer in the received pairs
        }
        initialized = true;
    }

    private boolean marketExists(TradingPair pair)
    {
        for(ClientMarket market : clientMarkets)
        {
            if(market.getTradingPair().equals(pair))
            {
                return true;
            }
        }
        return false;
    }

    private void addClientMarket(TradingPair pair)
    {
        for(ClientMarket market : clientMarkets)
        {
            if(market.getTradingPair().equals(pair))
            {
                return;
            }
        }
        ClientMarket newMarket = new ClientMarket(pair);
        clientMarkets.add(newMarket);
    }
    private void removeClientMarket(TradingPair pair)
    {
        for(ClientMarket market : clientMarkets)
        {
            if(market.getTradingPair().equals(pair))
            {
                clientMarkets.remove(market);
                market.setDead();
                return;
            }
        }
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
