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

    /**
     * Requests the list of markets from the server, loads new ones into the local cache and drops cached markets the
     * server no longer has.
     * <p>
     * The future fails when the request fails (2.2.0): with ModUtilities'
     * {@link net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException} when no answer came
     * in time or the connection was lost, or with an {@link IllegalStateException} when the request could not be sent;
     * the cache is then left as it is. Callbacks may get the cause wrapped in a
     * {@link java.util.concurrent.CompletionException}. A failure is never "no markets". See
     * {@code documentation/developer-guide/api/async-market-api.md}, section 3.
     *
     * @return future with the items of all markets on the server
     */
    CompletableFuture<List<ItemID>> requestMarkets();

    /**
     * Requests the open (resting limit) orders matching the filters from the server. The server returns only orders the player may
     * see (accounts they are a member of, all orders for an admin).
     * <p>
     * The future fails with ModUtilities'
     * {@link net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException} (2.2.0) when no
     * answer came in time or the connection was lost, often wrapped in a
     * {@link java.util.concurrent.CompletionException}; show the list as unavailable and let the player reload it.
     *
     * @param itemIDFilter         only orders of this market, or null for all markets
     * @param bankAccountNr        only orders of this bank account; 0 or less for all accounts
     * @param executorPlayerFilter only orders placed by this player, or null for all players
     * @param timeBegin            only orders placed at or after this time (ms)
     * @param timeEnd              only orders placed at or before this time (ms)
     * @return future with the matching orders
     */
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
     * @return a future that completes with {@code true} once the server created the market, even when loading it
     *         locally failed (the next {@link #requestMarkets()} loads it), and {@code false} otherwise
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
     * Adds a market the server just created to the client cache (SM-304), without a full
     * {@link #requestMarkets()}. Called by {@code MarketCreatedPacket} on the client main thread.
     * <p>
     * Once the market is cached the open StockMarket screen is told to refresh its market
     * lists. A market whose item this side cannot resolve is skipped; the next
     * {@link #requestMarkets()} picks it up if it becomes resolvable. Safe to call for an
     * already cached market (no-op, no screen refresh).
     *
     * @param marketID the market that was created on the server
     */
    void onMarketAdded(ItemID marketID);

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
