package net.kroia.stockmarket.api.integration;

import net.kroia.banksystem.util.ItemID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-process integration SPI for creating, querying, and closing markets from
 * outside the StockMarket mod (other mods, scripting bridges, admin tooling).
 * <p>
 * All methods on this interface are <b>server-thread only</b>. On a pure client
 * JVM the surrounding {@code StockMarketAPI.getIntegration()} returns
 * {@code null} — obtain the integration only after the dedicated/integrated
 * server has started. Slave-server callers can use the same API; the
 * StockMarket internals dispatch requests to the master via the existing ARRS
 * request path automatically.
 * <p>
 * This is a pure in-process SPI: no packets, no networking, no threading
 * primitives. Callers that need to bridge to another thread must marshal onto
 * the server thread themselves.
 */
public interface IStockMarketIntegration {

    /**
     * Opens (creates) a market for the given subject item using the supplied
     * config. Sources price, abundance, and market flags from {@code cfg} —
     * the preset system is intentionally bypassed so callers get deterministic
     * behaviour regardless of any preset matching the subject.
     * <p>
     * Preconditions checked in order:
     * <ol>
     *   <li>If a market already exists for {@code subject} →
     *       {@link MarketOpenResult.Status#ALREADY_EXISTS}.</li>
     *   <li>If the banking system reports the item blacklisted →
     *       {@link MarketOpenResult.Status#ITEM_BLACKLISTED}.</li>
     *   <li>Otherwise the market is created; a null return from the underlying
     *       manager surfaces as {@link MarketOpenResult.Status#FAILED} with a
     *       short reason string.</li>
     * </ol>
     * Server-thread only.
     *
     * @param subject the ItemID for which to open the market (never null)
     * @param cfg     the market configuration (never null); see {@link MarketConfig#defaults()}
     * @return a non-null {@link MarketOpenResult.Result} describing the outcome
     */
    @NotNull MarketOpenResult.Result openMarket(@NotNull ItemID subject, @NotNull MarketConfig cfg);

    /**
     * Returns whether a market currently exists for the given subject.
     * Server-thread only.
     *
     * @param subject the ItemID to check (never null)
     * @return {@code true} iff a market is registered for {@code subject}
     */
    boolean marketExistsFor(@NotNull ItemID subject);

    /**
     * Closes (deletes) the market for the given subject. Cancels every open
     * player order (refunding any locked balances via the banking system),
     * unsubscribes the market from all plugins, and broadcasts the removal to
     * clients — all through the existing {@code ServerMarketManager.deleteMarket}
     * path. If no market exists for {@code subject}, this is a no-op.
     * <p>
     * Server-thread only.
     *
     * @param subject the ItemID whose market should be closed (never null)
     */
    void closeMarket(@NotNull ItemID subject);

    /**
     * Opens or closes the market for the given subject for trading. This maps to the
     * market's <b>Maintenance</b> state: {@code open=true} = end maintenance
     * ({@code maintenance=false}), {@code open=false} = maintenance ({@code maintenance=true}).
     * Maintenance blocks players only; bots keep trading. The maintenance
     * flag is persisted across restarts. The method name is kept for compatibility —
     * BankSystem calls it reflectively by name. This does <b>not</b> create or delete
     * the market — see {@link #openMarket(ItemID, MarketConfig)} and
     * {@link #closeMarket(ItemID)} for lifecycle management.
     * <p>
     * <b>WARNING — {@code open=false} is DESTRUCTIVE.</b> Closing a market:
     * <ul>
     *   <li>cancels every open <i>player</i> order, refunding any locked balances
     *       via the banking system (bot orders are left intact);</li>
     *   <li>rejects new player orders while closed. The market stays listed on the
     *       client trade screen, which shows an "Under Maintenance" banner.</li>
     * </ul>
     * Price history is preserved and the order book is emptied of player orders.
     * Reopening the market ({@code open=true}) lets players trade again, starting
     * from a book without player orders. If no market exists for {@code subject},
     * this is a no-op.
     * <p>
     * Server-thread only.
     *
     * @param subject the ItemID whose market should be opened/closed (never null)
     * @param open    {@code true} to open the market for trading, {@code false} to
     *                close it (destructive — see above)
     */
    void setMarketOpen(@NotNull ItemID subject, boolean open);

    /**
     * SM-354: like {@link #setMarketOpen(ItemID, boolean)}, but closing also stores a close reason.
     * The reason is persisted with the maintenance flag, synced to clients (slaves included) and shown
     * in the trade screen's maintenance banner via {@code Component.translatable(reasonKey)} instead
     * of the plain "Under Maintenance" text. Any reopen ({@code open=true}, from any path: this API,
     * the admin panel, commands) clears it. The 2-arg overload is this method with {@code null}.
     * Player orders and bots behave exactly as with the 2-arg overload.
     * <p>
     * Server-thread only.
     *
     * @param subject   the ItemID whose market should be opened/closed (never null)
     * @param open      {@code true} to open the market (clears the reason), {@code false} to close it
     * @param reasonKey translation key of the close reason (the caller owns its lang text), or
     *                  {@code null} for the plain maintenance text; ignored when {@code open=true}
     */
    void setMarketOpen(@NotNull ItemID subject, boolean open, @Nullable String reasonKey);

    /**
     * SM-354: returns the close reason set by {@link #setMarketOpen(ItemID, boolean, String)}.
     * <p>
     * Server-thread only.
     *
     * @param subject the ItemID whose market to query (never null)
     * @return the reason translation key, or {@code null} if the market is open, was closed
     *         without a reason, or does not exist
     */
    @Nullable String getMarketCloseReason(@NotNull ItemID subject);

    /**
     * Returns whether the market for the given subject is currently open for
     * trading, i.e. it is <b>not under maintenance</b> ({@code = !maintenance}; see
     * {@link #setMarketOpen(ItemID, boolean)}). Name kept for BankSystem reflection. Returns {@code false} if
     * the market is closed <b>or</b> if no market exists for {@code subject}.
     * <p>
     * Server-thread only.
     *
     * @param subject the ItemID whose market state to query (never null)
     * @return {@code true} iff a market exists for {@code subject} and it is open
     *         for trading
     */
    boolean isMarketOpen(@NotNull ItemID subject);
}
