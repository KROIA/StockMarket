package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
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
    private final IAsyncMarketManager asyncServerMarketManager;


    private final Map<ItemID, ClientMarket> clientMarkets = new HashMap<>();

    public ClientMarketManager()
    {
        asyncServerMarketManager = AsyncMarketManager.createClientManager();
    }

    public void onPlayerJoin(@Nullable LocalPlayer localPlayer)
    {
        requestMarkets();
    }

    public void onPlayerLeave(@Nullable LocalPlayer localPlayer)
    {

    }

    public void update()
    {
        for(ClientMarket clientMarket : clientMarkets.values())
        {
            clientMarket.update();
        }
    }

    public @Nullable ClientMarket getMarket(ItemID itemID)
    {
        return clientMarkets.get(itemID);
    }

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


    public List<ItemID> getAvailableMarkets()
    {
        return clientMarkets.keySet().stream().toList();
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

    public CompletableFuture<ItemID> getTradingCurrencyIDAsync()
    {
        return asyncServerMarketManager.getTradingCurrencyIDAsync();
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
