package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.integration.MarketConfig;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Synchronous access to the master's market manager, where the market data lives. Server thread only; only
 * available on the master (see {@link IMarketManager#getSync()}).
 */
public interface ISyncServerMarketManager {

    /**
     * @return the currency item all markets trade against (Mod Setting {@code MARKET.CURRENCY})
     */
    ItemID getTradingCurrencyID();

    /**
     * @return the items of all existing markets, as a new list
     */
    List<ItemID> getAvailableMarketIDs();

    /**
     * @param marketID the market's item
     * @return true if a market for the item exists
     */
    boolean marketExists(@NotNull ItemID marketID);

    /**
     * Creates a market for the item from its matching preset (default price, natural abundance, order book flags,
     * max order size); without a preset it uses fallback values. Allows the item in BankSystem and subscribes the
     * market to the plugins that auto-subscribe.
     *
     * @param marketID the market's item
     * @return the new market, or the existing one if the item already has a market; {@code null} for a blacklisted
     *         item
     */
    @Nullable IServerMarket createMarket(@NotNull ItemID marketID);

    /**
     * Creates a market from an explicit {@link MarketConfig}, bypassing the
     * preset system. Mirrors {@link #createMarket(ItemID)} but sources initial
     * price, natural abundance, and market flags from the passed config
     * instead of any matching preset. Blacklist check, plugin auto-subscribe
     * (unless the config opts out), and BankSystem {@code allowItemID} run the
     * same way.
     * <p>
     * Returns {@code null} when a market for {@code marketID} already exists;
     * callers must distinguish this from other failures with a
     * {@link #marketExists(ItemID)} pre-check if the distinction matters.
     * Server-thread only.
     *
     * @param marketID the ItemID to create a market for (never null)
     * @param cfg      the configuration to apply to the new market (never null)
     * @return the newly created market, or {@code null} if one already existed
     *         or creation was rejected (e.g. blacklisted item)
     */
    @Nullable IServerMarket createMarketWithConfig(@NotNull ItemID marketID, @NotNull MarketConfig cfg);

    /**
     * Deletes a market: puts it under maintenance (cancels and refunds player and inter-market orders), clears its
     * order book, unsubscribes it from all plugins and tells every client and slave to drop it.
     *
     * @param marketID the market's item
     * @return true if a market was deleted, false if none existed
     */
    boolean deleteMarket(@NotNull ItemID marketID);

    /**
     * @param marketID the market's item
     * @return the market, or {@code null} if none exists for the item
     */
    @Nullable IServerMarket getMarket(@NotNull ItemID marketID);

    /**
     * Looks up a StockMarket user by name (also offline players who joined before).
     *
     * @param playerName the exact stored name
     * @return the user's UUID (a player online on this server wins), or {@code null} if no user or several stored
     *         users have that name
     */
    @Nullable UUID getPlayerUUID(String playerName);

    /**
     * Sets or clears a user's StockMarket admin flag. Only the flag: unlike {@code /stockmarket op|deop}, this sends
     * no messages and does not mirror the change to LuckPerms.
     *
     * @param playerUUID the user
     * @param isAdmin    the new flag
     * @return true if the user exists and the flag was set
     */
    boolean setStockmarketAdminMode(UUID playerUUID, boolean isAdmin);

    /**
     * @param playerUUID the user
     * @return true if the user has StockMarket admin mode; false for unknown users
     */
    boolean isStockmarketAdmin(UUID playerUUID);

    /**
     * Gets the player's trading preferences (favorites, last market, etc.)
     * @param playerUUID the player to get preferences for
     * @return the preferences, or a new empty preferences if the player is unknown
     */
    @NotNull PlayerPreferences getPlayerPreferences(UUID playerUUID);

    /**
     * Updates the player's trading preferences.
     * @param playerUUID the player to update
     * @param preferences the new preferences
     * @return true if the player exists and preferences were saved
     */
    boolean updatePlayerPreferences(UUID playerUUID, PlayerPreferences preferences);

    /**
     * Call this function when a player joins the server to setup its bank account
     * @param playerUUID
     * @param playerName
     */
    void onPlayerJoin(UUID playerUUID, String playerName);

    /**
     * Closes the current price candle of every market and starts a new one. Called by StockMarket's tick when a
     * candle ends; do not call it yourself, it cuts the candles short.
     *
     * @return the closed candles, one per market
     */
    List<MarketPriceStruct>  getCurrentMarketPricesAndStartNewCandle();

    /**
     * Runs one market tick: updates every market (matching), processes the inter-market orders, and runs the
     * periodic jobs (ledger rows, in-game candles). Called by StockMarket's server tick; do not call it yourself.
     */
    void update();
}
