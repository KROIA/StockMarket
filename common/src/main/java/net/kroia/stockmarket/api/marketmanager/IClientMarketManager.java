package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
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

    /**
     * Requests the server to create a new market for the given item.
     *
     * @param itemID the item to create a market for
     * @return a future that completes with {@code true} if the market was created successfully
     */
    CompletableFuture<Boolean> requestCreateMarket(ItemID itemID);

    /**
     * Requests the server to delete an existing market and removes the local client market.
     * The server performs full cleanup: closing the market, canceling orders,
     * clearing the orderbook, and unsubscribing from plugins.
     *
     * @param marketID the market to delete
     * @return a future that completes with {@code true} if the market was deleted successfully
     */
    CompletableFuture<Boolean> requestDeleteMarket(ItemID marketID);

    /**
     * Removes all client-side state for a market that no longer exists on the server.
     * Called when the server broadcasts a market deletion
     * (see {@code MarketRemovedPacket}) and when {@link #requestMarkets()} detects
     * that a cached market is no longer reported by the server.
     * <p>
     * Cleanup includes: the cached {@link ClientMarket} instance, its market price
     * stream subscription (force-stopped, regardless of subscriber count), and any
     * cached cross-rate markets referencing the deleted market.
     * Safe to call for markets that are not cached (no-op).
     *
     * @param marketID the market that was deleted on the server
     */
    void onMarketRemoved(ItemID marketID);

    /**
     * Returns a synthetic cross-rate data provider for the given pair,
     * or null if either underlying market is unavailable.
     * The returned provider derives OHLC candles from the ratio wantPrice / havePrice.
     *
     * @param haveItemID the item being "held" (denominator in the rate)
     * @param wantItemID the item being "wanted" (numerator in the rate)
     * @return the cross-rate provider, or null if markets are unavailable
     */
    @Nullable IPriceDataProvider getCrossRateMarket(@NotNull ItemID haveItemID, @NotNull ItemID wantItemID);
}
