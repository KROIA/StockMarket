package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous access to one market. On the master this is the market itself ({@link IServerMarket}) and every
 * future is already complete. On a slave or a client it is a proxy that sends each call to the master and
 * completes the future with the answer.
 * <p>
 * Prices and volumes are raw backend values unless the method name says {@code Real}.
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
 * unknown outcome, or any other exception, a caller must <b>never undo, refund or resend</b> a write or an order
 * automatically: re-read the state instead. A failed read may be retried later.
 * <p>
 * Not failures: a slave without a master connection sends nothing and completes at once with a fallback
 * ({@code false}, {@code 0}, an empty list or a new default object; {@link #isUnderMaintenanceAsync()} gives
 * {@code true}). A call the master refuses completes normally with {@code false}, or with {@code null} when it is
 * refused before it reaches the market (untrusted slave, a function clients may not call, unknown market); treat
 * {@code null} like {@code false}.
 * <p>
 * Details and a handling example: {@code documentation/developer-guide/api/async-market-api.md}, section 3.
 */
public interface IAsyncMarket {

    /**
     * @return the item this market trades; known locally, so this sends no request and is not a future
     */
    ItemID getItemIDAsync();

    /**
     * @return future with the market's default (reference) price, raw
     */
    CompletableFuture<Long> getDefaultPriceAsync();

    /**
     * @return future with the current market price, raw
     */
    CompletableFuture<Long> getCurrentMarketPriceAsync();

    /**
     * @return future with the master's current wall-clock time in milliseconds
     */
    CompletableFuture<Long> getCurrentTimeAsync();

    /**
     * Gets the order book volume at one price: virtual volume plus resting limit orders.
     * @param price the price level, raw
     * @return future with the volume, raw
     */
    CompletableFuture<Long> getRawVolumeAsync(long price);

    /**
     * Gets the order book volume in a price range: virtual volume plus resting limit orders.
     * @param startPrice lower end of the range, raw, inclusive
     * @param endPrice   upper end of the range, raw, inclusive
     * @return future with the summed volume, raw
     */
    CompletableFuture<Long> getRawVolumeAsync(long startPrice, long endPrice);

    /**
     * Real-unit variant of {@link #getRawVolumeAsync(long)}.
     * @param price the price level in real units
     * @return future with the volume in real units
     */
    CompletableFuture<Float> getRealVolumeAsync(double price);

    /**
     * Real-unit variant of {@link #getRawVolumeAsync(long, long)}.
     * @param startPrice lower end of the range in real units, inclusive
     * @param endPrice   upper end of the range in real units, inclusive
     * @return future with the summed volume in real units
     */
    CompletableFuture<Float> getRealVolumeAsync(double startPrice, double endPrice);

    /**
     * Places an order on this market.
     * <p>
     * <b>On the master</b> this is {@link ISyncServerMarket#putOrder(Order)}: the order is only queued for the next
     * market update, and refused if it is already filled, for another market, outside the trading hours, or a player
     * order during maintenance. Nothing is checked or locked for the player; the caller must have done that.
     * <p>
     * <b>On a slave</b> (2.2.0) the master places it like a player order from the trading screen. It uses only the
     * order's market, player, bank account, type, volume and price, and checks that the market exists and is open,
     * the volume is not 0 and the price not negative, the max order size and the player's trade cooldown, and that
     * the player is a member of the account with the WITHDRAW and DEPOSIT permissions (or its personal owner) with
     * enough money (buy) or items (sell). The money or items are locked for the order and released again if the
     * market refuses it. Bot orders (no player), inter-market orders and orders for a different market than this
     * one are refused, as are calls from an untrusted slave. Clients cannot call it.
     * <p>
     * After a {@code RequestOutcomeUnknownException} the order may have been placed: never resend it.
     *
     * @param order the order; on a slave its fill state, time and ID are ignored
     * @return future resolving to {@code true} if the order was placed, {@code false} (or {@code null}) on any
     *         refusal; the reason is not reported ({@code ClientMarket.createOrder} reports it)
     */
    CompletableFuture<Boolean> putOrderAsync(Order order);

    /**
     * Gets the limit orders resting in this market's order book. Clients cannot call it; refused from an untrusted
     * slave.
     * @return future with the resting limit orders (player and bot orders)
     */
    CompletableFuture<List<Order>> getLimitOrdersAsync();
    /**
     * Queries whether this market is under maintenance (admin/integration maintenance mode).
     * @return future resolving to {@code true} if under maintenance
     */
    CompletableFuture<Boolean> isUnderMaintenanceAsync();
    /**
     * Enables or disables maintenance mode. Enabling it cancels all player orders and inter-market orders
     * referencing this market; bots keep trading.
     * @param maintenance {@code true} to enter maintenance, {@code false} to end it
     * @return future resolving to {@code true} on success
     */
    CompletableFuture<Boolean> setMaintenanceAsync(boolean maintenance);

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#setTradingHours(int, int)}.
     * Admin-gated for players; refused when forwarded from an untrusted slave.
     * @param openMinute  opening minute of the day (0..1440)
     * @param closeMinute closing minute of the day (0..1440)
     * @return future resolving to {@code true} on success, {@code false} when denied
     */
    CompletableFuture<Boolean> setTradingHoursAsync(int openMinute, int closeMinute);

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#setTradingOverride(boolean)}.
     * Server-side only (not callable by clients); refused when forwarded from an untrusted slave.
     * @param open {@code true} to force open, {@code false} to force closed until the next scheduled transition
     * @return future resolving to {@code true} on success, {@code false} when denied
     */
    CompletableFuture<Boolean> setTradingOverrideAsync(boolean open);

    /**
     * @return future with the current, still open price candle (open, low, high, start time, traded volume)
     */
    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync();

    /**
     * Gets the current price candle and starts a new one. StockMarket's tick calls this when a candle ends; calling
     * it yourself cuts the candle short. Clients cannot call it; refused from an untrusted slave.
     * @return future with the candle that was just closed
     */
    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync();

    /**
     * Gets the market's settings (the data of the Market Settings screen). A player without StockMarket admin mode
     * gets a new default object instead.
     * @return future with the settings
     */
    CompletableFuture<MarketSettings> getSettingsAsync();

    /**
     * Replaces the market's settings. Admin-gated for players; refused from an untrusted slave.
     * @param settings the new settings
     * @return future resolving to {@code true} when applied, {@code false} when denied
     */
    CompletableFuture<Boolean> setSettingsAsync(MarketSettings settings);

    /**
     * Sets the market's real and faded net player item flow to 0 (the NetFlowPlugin also resets its long-term
     * anchor). Admin-gated for players; refused from an untrusted slave.
     * @return future resolving to {@code true} when reset, {@code false} when denied
     */
    CompletableFuture<Boolean> resetNetPlayerItemFlowAsync();

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#resetVirtualOrderbook()}.
     * Refills the virtual orderbook from the default distribution and drops the sticky-clear
     * flag. Admin-gated server-side; the future resolves to false when denied.
     */
    CompletableFuture<Boolean> resetVirtualOrderbookAsync();

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#clearVirtualOrderbook()}.
     * Zeroes the virtual orderbook and sets the sticky-clear flag so shift-fill stays at 0.
     * Admin-gated server-side; the future resolves to false when denied.
     */
    CompletableFuture<Boolean> clearVirtualOrderbookAsync();

}
