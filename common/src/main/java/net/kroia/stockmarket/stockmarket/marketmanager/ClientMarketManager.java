package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.player.LocalPlayer;
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
