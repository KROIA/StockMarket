package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous access to the market manager, from {@link IMarketManager#getAsync()}. On the master it is the
 * {@link IServerMarketManager} itself and every future is already complete; on a slave every call is a request to
 * the master.
 *
 * <h2>Request failures (2.2.0)</h2>
 * A future that waits for the master's answer can fail with one of ModUtilities' request exceptions:
 * <ul>
 *   <li>{@link net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException}: the call was
 *       sent, but no answer came in time or the connection was lost. The master <b>may have applied it</b>.</li>
 *   <li>{@link net.kroia.modutilities.networking.client_server.arrs.RequestNotSentException}: the call was never
 *       sent (no master connection); nothing changed on the master.</li>
 * </ul>
 * Callbacks often get the cause wrapped in a {@link java.util.concurrent.CompletionException}; unwrap it. After an
 * unknown outcome, or any other exception, a caller must <b>never undo, refund or resend</b> a write
 * automatically: re-read the state instead. A failed read may be retried later.
 * <p>
 * Not failures: a slave without a master connection sends nothing and completes at once with a fallback
 * ({@code false}, {@code null}, {@link ItemID#INVALID_ID}, an empty list or a new default object; check
 * {@link IMarketManager#hasAsyncAccess()} to tell "no master" from "no data"). A write refused by the master
 * (untrusted slave, a function clients may not call) completes normally with {@code false} or {@code null}.
 * <p>
 * Details and a handling example: {@code documentation/developer-guide/api/async-market-api.md}, section 3.
 */
public interface IAsyncMarketManager {

    /**
     * @return future with the currency item all markets trade against
     */
    CompletableFuture<ItemID> getTradingCurrencyIDAsync();

    /**
     * @return future with the items of all existing markets
     */
    CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync();

    /**
     * @param marketID the market's item
     * @return future resolving to {@code true} if a market for the item exists
     */
    CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID);

    /**
     * Creates a market for the item from its matching preset (fallback values without one), allows the item in
     * BankSystem and subscribes the market to the plugins that auto-subscribe. Refused from an untrusted slave.
     * @param marketID the market's item
     * @return future with the market, also if it already existed; {@code null} if it was refused (for example a
     *         blacklisted item)
     */
    CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID);

    /**
     * Deletes a market: puts it under maintenance (cancels and refunds player and inter-market orders), clears its
     * order book, unsubscribes it from all plugins and tells every client to drop it. Refused from an untrusted
     * slave.
     * @param marketID the market's item
     * @return future resolving to {@code true} if a market was deleted, {@code false} if none existed
     */
    CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID);

    /**
     * @param marketID the market's item
     * @return future with the market (on a slave a proxy to the master's market), or {@code null} if none exists
     */
    CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID);

    /**
     * Sets or clears a player's StockMarket admin flag. Only the flag: unlike {@code /stockmarket op|deop}, this
     * sends no messages and does not mirror the change to LuckPerms. Clients cannot call it; refused from an
     * untrusted slave.
     * @param playerUUID the player
     * @param isAdmin    the new flag
     * @return future resolving to {@code true} if the player is a known StockMarket user and the flag was set
     */
    CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin);

    /**
     * @param playerUUID the player
     * @return future resolving to {@code true} if the player has StockMarket admin mode; {@code false} for unknown
     *         players
     */
    CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID);

    /**
     * Asynchronously gets the player's trading preferences.
     * @param playerUUID the player to get preferences for
     * @return future containing the preferences
     */
    CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID);

    /**
     * Asynchronously updates the player's trading preferences.
     * @param playerUUID the player to update
     * @param preferences the new preferences
     * @return future containing true if successful
     */
    CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences);

    /**
     * Registers a joining player as a StockMarket user, or updates the stored name of a known one. StockMarket
     * calls this on join. Fire and forget: no answer, and a failed request is not reported.
     * @param playerUUID the player
     * @param playerName the player's current name
     */
    void onPlayerJoinAsync(UUID playerUUID, String playerName);


}
