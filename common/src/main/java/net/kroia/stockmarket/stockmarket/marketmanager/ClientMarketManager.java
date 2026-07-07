package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.CrossRateMarket;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ClientMarketManager implements IClientMarketManager
{
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        ClientMarket.setBackend(backend);
    }
    private final IAsyncMarketManager asyncMarketManager;

    private final Map<ItemID, ClientMarket> clientMarkets = new HashMap<>();

    /** Composite key for caching cross-rate market instances. */
    private record CrossRateKey(ItemID haveItemID, ItemID wantItemID) {}

    /** Lazily-populated cache of cross-rate markets keyed by their item pair. */
    private final Map<CrossRateKey, CrossRateMarket> crossRateMarkets = new HashMap<>();

    public ClientMarketManager()
    {
        asyncMarketManager = AsyncMarketManager.createClientManager();
    }


    @Override
    public void update()
    {
        long serverTime = ClientMarket.PriceHistoryContainer.ServerRelativeTimer.timeOffsetMS + System.currentTimeMillis();
        for(ClientMarket clientMarket : clientMarkets.values())
        {
            clientMarket.update(serverTime);
        }
        // Tick all active cross-rate markets so their live candles stay current
        for(CrossRateMarket crossRateMarket : crossRateMarkets.values())
        {
            crossRateMarket.update(serverTime);
        }
    }

    @Override
    public void onPlayerJoin(@Nullable LocalPlayer localPlayer)
    {
        // Sync the server time offset once on join, then request markets.
        // Avoids per-market overwrites of the shared static timeOffsetMS (Issue #44).
        BACKEND_INSTANCES.NETWORKING.SERVER_TIME_REQUEST.sendRequestToServer(System.currentTimeMillis()).thenAccept(serverTime ->
        {
            long currentTime = System.currentTimeMillis();
            long turnaroundTime = currentTime - serverTime.clientTimeEcho();
            long currentServerTime = serverTime.serverTime() + turnaroundTime / 2;
            ClientMarket.PriceHistoryContainer.ServerRelativeTimer.timeOffsetMS = currentServerTime - currentTime;
            requestMarkets();
        });

        // Fetch player preferences (favorites, last market) from server
        StockMarketGuiElement.fetchPlayerPreferences();
    }

    @Override
    public void onPlayerLeave(@Nullable LocalPlayer localPlayer)
    {

    }

    @Override
    public @Nullable ClientMarket getMarket(ItemID itemID)
    {
        return clientMarkets.get(itemID);
    }

    @Override
    public CompletableFuture<List<ItemID>> requestMarkets()
    {
        CompletableFuture<List<ItemID>> future = new CompletableFuture<>();
        BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager().getItemFractionScaleFactorAsync().thenAccept((factor)->
        {
            AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.MARKETS_REQUEST, 0).thenAccept((response) ->
            {
                info("Markets request response received with "+response.size()+" markets");
                for(ItemID itemID : response) {
                    createClientMarket(itemID, factor);
                }
                // Prune cached markets the server no longer reports (deleted markets).
                // Without this the cache only ever grows, so a market deleted on the
                // server would remain selectable in every market list forever
                // (until relog). Acts as a safety net alongside MarketRemovedPacket.
                List<ItemID> staleMarkets = clientMarkets.keySet().stream()
                        .filter(cached -> !response.contains(cached))
                        .toList();
                for(ItemID staleID : staleMarkets) {
                    info("Removing stale ClientMarket (no longer on server): " + staleID);
                    onMarketRemoved(staleID);
                }
                future.complete(response);
            });
        });
        return future;
    }

    @Override
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@Nullable ItemID itemIDFilter,
                                                                                  int bankAccountNr,
                                                                                  @Nullable UUID executorPlayerFilter,
                                                                                  long timeBegin,
                                                                                  long timeEnd)
    {
        ActiveOrdersRequest.InputData inp = new ActiveOrdersRequest.InputData(itemIDFilter, bankAccountNr, executorPlayerFilter, timeBegin, timeEnd);
        CompletableFuture<ActiveOrdersRequest.OutputData> future = new CompletableFuture<>();
        BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_REQUEST.sendRequestToServer(inp).thenAccept(future::complete);
        return future;
    }

    @Override
    public List<ItemID> getAvailableMarkets()
    {
        return clientMarkets.keySet().stream().toList();
    }

    @Override
    public CompletableFuture<ItemID> getTradingCurrencyIDAsync()
    {
        return asyncMarketManager.getTradingCurrencyIDAsync();
    }

    @Override
    public CompletableFuture<Boolean> requestCreateMarket(ItemID itemID)
    {
        return asyncMarketManager.createMarketAsync(itemID).thenCompose(market -> {
            if (market != null) {
                info("Market created on server for: " + itemID);
                return BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager()
                        .getItemFractionScaleFactorAsync()
                        .thenApply(factor -> {
                            createClientMarket(itemID, factor);
                            return true;
                        });
            }
            warn("Server returned null for createMarketAsync: " + itemID);
            return CompletableFuture.completedFuture(false);
        }).exceptionally(ex -> {
            error("Exception during market creation for: " + itemID, (Exception) ex);
            return false;
        });
    }




    @Override
    public CompletableFuture<Boolean> requestDeleteMarket(ItemID marketID)
    {
        return asyncMarketManager.deleteMarketAsync(marketID).thenApply(success -> {
            if (success) {
                info("Market deleted on server for: " + marketID);
                // Remove the local client market, its stream subscription and any
                // cross-rate markets referencing it. The server additionally broadcasts
                // a MarketRemovedPacket to all clients (including this one) — the
                // cleanup is idempotent, so handling it twice is harmless.
                onMarketRemoved(marketID);
            } else {
                warn("Server returned false for deleteMarketAsync: " + marketID);
            }
            return success;
        }).exceptionally(ex -> {
            error("Exception during market deletion for: " + marketID, (Exception) ex);
            return false;
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is the client-side half of the market-deletion propagation:
     * the master server's {@code ServerMarketManager.deleteMarket} broadcasts a
     * {@code MarketRemovedPacket} to every connected client (relayed through slave
     * servers in a multi-server setup), whose handler calls this method on the
     * client main thread. It drops the cached {@link ClientMarket}, force-stops its
     * price stream so no orphaned subscription keeps polling the server, and evicts
     * every cross-rate market built on top of the deleted market.
     */
    @Override
    public void onMarketRemoved(ItemID marketID)
    {
        ClientMarket removedMarket = clientMarkets.remove(marketID);
        if (removedMarket != null) {
            // Stop the price stream even if GUI elements are still subscribed —
            // the server-side market is gone, the stream can never deliver again.
            removedMarket.forceStopMarketPriceStream();
            info("Removed ClientMarket for deleted market: " + marketID);
        }
        // Cross-rate markets derived from the deleted market are now dead as well.
        crossRateMarkets.entrySet().removeIf(entry ->
                entry.getKey().haveItemID().equals(marketID) || entry.getKey().wantItemID().equals(marketID));
    }

    /**
     * {@inheritDoc}
     * Lazily creates and caches the CrossRateMarket on first request.
     * Returns null if either underlying ClientMarket has not been created yet.
     */
    @Override
    public @Nullable IPriceDataProvider getCrossRateMarket(@NotNull ItemID haveItemID, @NotNull ItemID wantItemID) {
        CrossRateKey key = new CrossRateKey(haveItemID, wantItemID);
        CrossRateMarket existing = crossRateMarkets.get(key);
        if (existing != null) return existing;

        ClientMarket have = clientMarkets.get(haveItemID);
        ClientMarket want = clientMarkets.get(wantItemID);
        if (have == null || want == null) return null;

        CrossRateMarket crossRate = new CrossRateMarket(have, want);
        crossRateMarkets.put(key, crossRate);
        return crossRate;
    }

    private void createClientMarket(ItemID itemID, int itemFractionScaleFactor)
    {
        ClientMarket clientMarket = clientMarkets.get(itemID);
        if(clientMarket == null)
        {
            if(itemID.isValid()) {
                clientMarket = new ClientMarket(itemID, itemFractionScaleFactor);
                clientMarkets.put(itemID, clientMarket);
                clientMarket.requestFullPriceHistoryUpdate();
                info("Created ClientMarket with ID: " + itemID);
            }
            else
            {
                error("Can't create ClientMarket with ID: " + itemID + ". ID is invalid");
            }
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for(ClientMarket clientMarket : clientMarkets.values())
        {
            sb.append(clientMarket.toString()).append("\n");
        }
        return sb.toString();
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientMarketManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientMarketManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientMarketManager]: "+message);
    }
}
