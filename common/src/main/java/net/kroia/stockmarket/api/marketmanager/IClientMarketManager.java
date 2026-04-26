package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IClientMarketManager {

    void update();
    void onPlayerJoin(@Nullable LocalPlayer localPlayer);
    void onPlayerLeave(@Nullable LocalPlayer localPlayer);
    @Nullable ClientMarket getMarket(ItemID itemID);
    CompletableFuture<List<ItemID>> requestMarkets();
    CompletableFuture<ActiveOrdersRequest.OutputData> requestPendingOrders(@Nullable ItemID itemIDFilter,
                                                                           int bankAccountNr,
                                                                           @Nullable UUID executorPlayerFilter,
                                                                           long timeBegin,
                                                                           long timeEnd);
    List<ItemID> getAvailableMarkets();
    CompletableFuture<ItemID> getTradingCurrencyIDAsync();
}
