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
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ClientMarketManager implements IClientMarketManager
{
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        ClientMarket.setBackend(backend);
    }
    private final IAsyncMarketManager asyncMarketManager;
    /** Reads BankSystem's item fraction scale factor; the client bank manager in production, a fake in tests. */
    private final Supplier<CompletableFuture<Integer>> factorRead;

    private final Map<ItemID, ClientMarket> clientMarkets = new HashMap<>();

    /** Composite key for caching cross-rate market instances. */
    private record CrossRateKey(ItemID haveItemID, ItemID wantItemID) {}

    /** Lazily-populated cache of cross-rate markets keyed by their item pair. */
    private final Map<CrossRateKey, CrossRateMarket> crossRateMarkets = new HashMap<>();

    public ClientMarketManager()
    {
        this(AsyncMarketManager.createClientManager(),
                () -> BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager().getItemFractionScaleFactorAsync());
    }
    private ClientMarketManager(IAsyncMarketManager asyncMarketManager, Supplier<CompletableFuture<Integer>> factorRead)
    {
        this.asyncMarketManager = asyncMarketManager;
        this.factorRead = factorRead;
    }

    /**
     * Test seam (T-233): a manager that talks to {@code asyncMarketManager} and reads the item fraction scale
     * factor from {@code factorRead} instead of the network and BankSystem.
     *
     * @param asyncMarketManager replaces the client's market manager (see {@link AsyncMarketManager#createForTesting})
     * @param factorRead         replaces BankSystem's item fraction scale factor read
     * @return the manager
     */
    public static ClientMarketManager createForTesting(IAsyncMarketManager asyncMarketManager,
                                                       Supplier<CompletableFuture<Integer>> factorRead)
    {
        return new ClientMarketManager(asyncMarketManager, factorRead);
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

    /** Tries for the join-time server time sync. The request only reads, so sending it again is safe. */
    private static final int SERVER_TIME_SYNC_ATTEMPTS = 3;

    @Override
    public void onPlayerJoin(@Nullable LocalPlayer localPlayer)
    {
        // Sync the server time offset once on join, then request markets.
        // Avoids per-market overwrites of the shared static timeOffsetMS (Issue #44).
        syncServerTime(SERVER_TIME_SYNC_ATTEMPTS);

        // Fetch player preferences (favorites, last market) from server.
        // The join-time news toast catch-up (T-077) is chained onto the fetch future
        // so it only ever runs with the player's REAL newsToastEnabled flag — never
        // against the pre-fetch default (which is off and would silently skip it,
        // or worse, a default of on would toast for opted-out players).
        StockMarketGuiElement.fetchPlayerPreferences()
                .thenAccept(prefs -> StockMarketClientHooks.runNewsToastCatchUp(BACKEND_INSTANCES, prefs));
    }

    /**
     * Measures the server time offset, then requests the markets. A failed request (T-227: no answer in
     * time, connection lost) is sent again, up to {@code attemptsLeft} times in total; ServerTimeRequest
     * changes nothing on the server, so a retry is safe.
     *
     * @param attemptsLeft tries left, including this one
     */
    private void syncServerTime(int attemptsLeft)
    {
        BACKEND_INSTANCES.NETWORKING.SERVER_TIME_REQUEST.sendRequestToServer(System.currentTimeMillis()).whenComplete((serverTime, failure) ->
        {
            if (failure != null || serverTime == null) {
                if (attemptsLeft > 1) {
                    warn("Server time request failed (" + failure + "), retrying");
                    syncServerTime(attemptsLeft - 1);
                } else {
                    error("Server time request failed, giving up; candle times may be off until the next join", failure);
                }
                return;
            }
            long currentTime = System.currentTimeMillis();
            long turnaroundTime = currentTime - serverTime.clientTimeEcho();
            long currentServerTime = serverTime.serverTime() + turnaroundTime / 2;
            ClientMarket.PriceHistoryContainer.ServerRelativeTimer.timeOffsetMS = currentServerTime - currentTime;
            ClientMarket.setCandleUseGameTime(serverTime.candleUseGameTime()); // T-218: before any ClientMarket exists
            // T-140: the master's initial chart window, before any chart can open (markets are requested below).
            if (BACKEND_INSTANCES != null && BACKEND_INSTANCES.SETTINGS != null)
                BACKEND_INSTANCES.SETTINGS.setInitialLoadCandles(serverTime.initialLoadCandles());
            requestMarkets();
        });
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

    /**
     * {@inheritDoc}
     * <p>
     * The returned future fails when the markets request fails (no answer in time, connection lost, or not
     * sent); the cached markets are then left as they are.
     */
    @Override
    public CompletableFuture<List<ItemID>> requestMarkets()
    {
        return factorRead.get().thenCompose((factor)->
            AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.MARKETS_REQUEST, 0).thenApply((response) ->
            {
                if(response == null) // not sent; must not be read as "the server has no markets" (that prunes all)
                    throw new IllegalStateException("Markets request could not be sent");
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
                return response;
            }));
    }

    @Override
    public CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@Nullable ItemID itemIDFilter,
                                                                                  int bankAccountNr,
                                                                                  @Nullable UUID executorPlayerFilter,
                                                                                  long timeBegin,
                                                                                  long timeEnd)
    {
        ActiveOrdersRequest.InputData inp = new ActiveOrdersRequest.InputData(itemIDFilter, bankAccountNr, executorPlayerFilter, timeBegin, timeEnd);
        // The ARRS future as is, so a failed request reaches the caller instead of leaving it pending.
        return BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_REQUEST.sendRequestToServer(inp);
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

    /**
     * {@inheritDoc}
     * <p>
     * T-233: once the server confirmed the market, the result is {@code true} even if the following item fraction
     * scale factor read fails (BankSystem request timeout). The local market is then not created here; the next
     * {@link #requestMarkets()} loads it. The create itself is never sent again (its outcome counts, see T-227).
     */
    @Override
    public CompletableFuture<Boolean> requestCreateMarket(ItemID itemID)
    {
        return asyncMarketManager.createMarketAsync(itemID).thenCompose(market -> {
            if (market != null) {
                info("Market created on server for: " + itemID);
                return factorRead.get().handle((factor, failure) -> {
                    if (failure != null)
                        warn("Market " + itemID + " was created, but reading the item fraction scale factor failed ("
                                + failure + "); the next market list request loads it");
                    else
                        createClientMarket(itemID, factor);
                    return true;
                });
            }
            warn("Server returned null for createMarketAsync: " + itemID);
            return CompletableFuture.completedFuture(false);
        }).exceptionally(ex -> {
            error("Exception during market creation for: " + itemID, ex);
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
            error("Exception during market deletion for: " + marketID, ex);
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
            // T-237: same rule as isValid(), plus the test seam; a market whose item this side cannot resolve
            // (an item only the master has) gets no ClientMarket, so it is hidden from every market list
            if(MultiServerUtils.isMarketResolvableOnThisServer(itemID)) {
                clientMarket = new ClientMarket(itemID, itemFractionScaleFactor);
                clientMarkets.put(itemID, clientMarket);
                // Do NOT pre-fetch any candle history here. With N markets and
                // 6 candle deltas this would fire 6*N history requests during
                // world join (T-135 regression), spamming the DB thread and
                // slowing world load noticeably. History is now loaded lazily
                // — the CandlestickChart's selectCandleTimeDeltaByIndex fires
                // a single-delta request on first view / delta switch when its
                // cache is empty (see T-136).
                info("Created ClientMarket with ID: " + itemID);
            }
            else
            {
                // Expected on a slave without the item's mod, so not an error
                info("No ClientMarket for " + itemID + ": its item cannot be resolved here");
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

    // The log helpers skip logging without a client backend (offline tests, dedicated server test runs).
    protected void info(String message) {
        if (BACKEND_INSTANCES != null) BACKEND_INSTANCES.LOGGER.info("[ClientMarketManager]: "+message);
    }
    protected void error(String message) {
        if (BACKEND_INSTANCES != null) BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        if (BACKEND_INSTANCES != null) BACKEND_INSTANCES.LOGGER.error("[ClientMarketManager]: "+message, throwable);
    }
    protected void warn(String message) {
        if (BACKEND_INSTANCES != null) BACKEND_INSTANCES.LOGGER.warn("[ClientMarketManager]: "+message);
    }
    protected void debug(String message) {
        if (BACKEND_INSTANCES != null) BACKEND_INSTANCES.LOGGER.debug("[ClientMarketManager]: "+message);
    }
}
