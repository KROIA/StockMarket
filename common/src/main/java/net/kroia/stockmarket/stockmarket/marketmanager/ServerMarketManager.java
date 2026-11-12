package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.modutilities.NbtCompat;

import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.integration.MarketConfig;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.compat.StockMarketDiscord;
import net.kroia.stockmarket.networking.packet.MarketChangedPacket;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.OrderLedger;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.InterMarketExecutor;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

public class ServerMarketManager implements ServerSaveableChunked, IServerMarketManager
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        ServerMarket.setBackend(backend);
    }


    /**
     * Using the player UUID as key
     */
    private final Map<UUID, User> userMap = new HashMap<>();

    private final Map<ItemID, ServerMarket> markets = new HashMap<>();
    // T-166 trade cooldown: player UUID -> time (ms) of the player's last ACCEPTED order.
    // Master only; lives as long as this manager (dropped on server stop). One entry per
    // trading player, never pruned on disconnect (slave players never "leave" the master).
    private final Map<UUID, Long> lastAcceptedPlayerOrderMillis = new HashMap<>();

    // Central queue for pending inter-market orders, sorted by time (FIFO)
    private final PriorityQueue<InterMarketOrder> pendingInterMarketOrders =
            new PriorityQueue<>(Comparator.comparingLong(InterMarketOrder::getTime));

    private long candleSaveTimer_lastMs = (System.currentTimeMillis()/60000)*60000;

    /** T-218: game ticks per in-game hour, the length of one stored candle in game-time mode. */
    public static final long GAME_CANDLE_TICKS = 1000L;
    /** T-218: cached CANDLE_USE_GAME_TIME (restart-required), read on first use; null = not read yet. */
    private @Nullable Boolean candleUseGameTime = null;
    /** T-218: overworld day time at the last game-time candle rotation; MIN_VALUE = not initialised. */
    private long gameCandleLastDayTime = Long.MIN_VALUE;
    /** T-218: server ticks since the last game-time candle rotation (frozen daylight fallback). */
    private long gameCandleTicks = 0;

    private final Random random = new Random();
    private ItemID tradingCurrencyID = null;

    // Trading-hours clock, evaluated once per update() tick (T-158). Cached so the price
    // stream / ServerTimeRequest can report the same minute the markets were evaluated with.
    private int currentTradingMinute = 0;
    /** T-191: server ticks since the last periodic ledger sweep (game time, not persisted). */
    private int ledgerSweepTicks = 0;
    private boolean tradingHoursUseRealTime = false;

    public ServerMarketManager()
    {

    }

    @Override
    public ItemID getTradingCurrencyID()
    {
        if(tradingCurrencyID == null)
        {
            tradingCurrencyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
        }
        return tradingCurrencyID;
    }
    @Override
    public CompletableFuture<ItemID> getTradingCurrencyIDAsync() {
        return CompletableFuture.completedFuture(getTradingCurrencyID());
    }





    @Override
    public List<ItemID> getAvailableMarketIDs()
    {
        List<ItemID> itemIDs = new ArrayList<>();
        for(ServerMarket m : markets.values())
        {
            itemIDs.add(m.getItemID());
        }
        return itemIDs;
    }
    @Override
    public CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync() {
        return CompletableFuture.completedFuture(getAvailableMarketIDs());
    }






    @Override
    public boolean marketExists(@NotNull ItemID marketID)
    {
        return markets.containsKey(marketID);
    }
    @Override
    public CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(marketExists(marketID));
    }







    @Override
    public @Nullable IServerMarket createMarket(@NotNull ItemID marketID)
    {
        ServerMarket m = markets.get(marketID);
        if (m == null)
        {
            // Reject blacklisted items
            IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
            if (bankManager != null && bankManager.isItemIDBlacklisted(marketID)) {
                warn("Cannot create market for blacklisted item: " + marketID);
                return null;
            }

            MarketPreset preset = BACKEND_INSTANCES.PRESET_MANAGER != null
                    ? BACKEND_INSTANCES.PRESET_MANAGER.getPreset(marketID) : null;
            long defaultPrice = (preset != null) ? MarketManager.convertToRawAmountStatic(preset.getDefaultPrice()) : 1000;
            // No preset: fallback abundance 100 (presets were raised x10 in T-185, see 8a2be930)
            float abundance = (preset != null) ? preset.getNaturalAbundance() : 100f;
            m = new ServerMarket(marketID, null, defaultPrice, abundance);
            // Propagate the preset's orderbookEnabled flag into the market's live settings.
            // Presets without the flag (legacy JSON) default to true via MarketPreset's field
            // default, matching the pre-existing behaviour for old markets.
            if (preset != null) {
                m.getSettings().virtualOrderbookEnabled = preset.isOrderbookEnabled();
                m.getSettings().ignorePluginAutosubscribe = preset.isIgnorePluginAutosubscribe();
                m.getSettings().maxOrderVolume = preset.getMaxOrderVolume();
                // Sync the runtime "disabled" flag on the underlying VirtualOrderbook so
                // a preset-disabled market immediately behaves as empty in the matching
                // engine — otherwise the flag would only take effect after the next
                // setSettings() call.
                m.getOrderbook().setVirtualDisabled(!preset.isOrderbookEnabled());
            }
            m.setMarketMaintenanceCallback(this::cancelInterMarketOrdersForMarket);
            markets.put(marketID, m);

            // Allow the item in the banking system so it can be traded
            if (bankManager != null) {
                bankManager.allowItemID(marketID);
            }

            // Auto-subscribe new market to plugins that opt in
            if (BACKEND_INSTANCES.PLUGIN_MANAGER != null) {
                var pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getSync();
                if (pluginManager != null) {
                    pluginManager.autoSubscribeNewMarket(marketID);
                }
            }
            // SM-304: tell every client (and slave players) about the new market. Sent only
            // for a real creation and after markets.put, so a racing requestMarkets sees it too.
            MarketChangedPacket.broadcast(marketID, MarketChangedPacket.Kind.ADDED);
        }
        return m;
    }
    @Override
    public CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(createMarket(marketID));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Preset-free counterpart to {@link #createMarket(ItemID)}: mirrors the same
     * blacklist check, plugin auto-subscribe, {@code allowItemID} call, and
     * settings plumbing, but reads price, abundance, and market flags from the
     * supplied {@link MarketConfig} instead of any matching preset.
     */
    @Override
    public @Nullable IServerMarket createMarketWithConfig(@NotNull ItemID marketID, @NotNull MarketConfig cfg)
    {
        if (markets.containsKey(marketID)) {
            return null;
        }

        // Reject blacklisted items — same guard as createMarket(ItemID).
        IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
        if (bankManager != null && bankManager.isItemIDBlacklisted(marketID)) {
            warn("Cannot create market for blacklisted item: " + marketID);
            return null;
        }

        long defaultPrice = MarketManager.convertToRawAmountStatic(cfg.defaultPrice());
        float abundance = cfg.naturalAbundance();
        // maxOrderVolume is derived from the abundance by the ServerMarket/MarketSettings constructor.
        ServerMarket m = new ServerMarket(marketID, null, defaultPrice, abundance);
        m.getSettings().virtualOrderbookEnabled = cfg.virtualOrderbookEnabled();
        m.getSettings().ignorePluginAutosubscribe = cfg.ignorePluginAutosubscribe();
        // Sync the runtime "disabled" flag on the underlying VirtualOrderbook so
        // a disabled market immediately behaves as empty in the matching engine.
        m.getOrderbook().setVirtualDisabled(!cfg.virtualOrderbookEnabled());
        m.setMarketMaintenanceCallback(this::cancelInterMarketOrdersForMarket);
        markets.put(marketID, m);

        if (bankManager != null) {
            bankManager.allowItemID(marketID);
        }

        // Auto-subscribe new market to plugins that opt in. The market's
        // ignorePluginAutosubscribe flag (set above) is consulted inside
        // autoSubscribeNewMarket, matching the preset-driven path exactly.
        if (BACKEND_INSTANCES.PLUGIN_MANAGER != null) {
            var pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getSync();
            if (pluginManager != null) {
                pluginManager.autoSubscribeNewMarket(marketID);
            }
        }
        MarketChangedPacket.broadcast(marketID, MarketChangedPacket.Kind.ADDED); // SM-304, see createMarket(ItemID)
        return m;
    }






    @Override
    public boolean deleteMarket(@NotNull ItemID marketID)
    {
        ServerMarket market = markets.get(marketID);
        if (market == null) return false;

        // Close market: cancels player orders and inter-market orders via callback
        if (!market.isUnderMaintenance()) {
            market.setMaintenance(true);
        }

        // Clear remaining orders (bot orders that survived close)
        market.getOrderbook().clear();

        // Unsubscribe from all plugins
        if (BACKEND_INSTANCES.PLUGIN_MANAGER != null) {
            var pluginManager = BACKEND_INSTANCES.PLUGIN_MANAGER.getSync();
            if (pluginManager != null) {
                pluginManager.removeCache(marketID);
            }
        }

        // Remove from map
        markets.remove(marketID);

        // Propagate the deletion to every connected client. deleteMarket only ever
        // executes here on the master (slaves and clients reach it through the ARRS
        // DeleteMarket request), so this is the single choke point for the broadcast:
        //  - all players connected to this server receive the packet directly,
        //  - all slave servers receive a relayed copy and forward it to their players.
        // Clients react by purging their cached ClientMarket (which otherwise only
        // grows and would keep the deleted market selectable in the trade screen)
        // and by deselecting the market in any open GUI.
        broadcastStates.remove(marketID);
        MarketChangedPacket.broadcast(marketID, MarketChangedPacket.Kind.REMOVED);
        return true;
    }
    @Override
    public CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(deleteMarket(marketID));
    }

    /**
     * Reconciles this manager's {@code markets} map against a BankSystem ItemID merge
     * by deleting any market keyed under a merged-away alias ID.
     * <p>
     * Reconcile policy per merge group ({aliasIDs} → canonicalID):
     * <ol>
     *   <li><b>Delete alias-keyed duplicates:</b> for every alias in the group that has
     *       an entry in {@code markets}, call {@link #deleteMarket(ItemID)} — which
     *       cancels the market's open player orders, refunds their locked funds,
     *       unsubscribes it from plugins, and broadcasts {@code MarketChangedPacket} REMOVED.</li>
     *   <li><b>Canonical-keyed market is left completely alone.</b> If a market for
     *       {@code canonicalID} exists, it is not a duplicate — it stays untouched.</li>
     *   <li><b>Never re-key.</b> An alias-keyed market is deleted outright rather than
     *       re-keyed to canonical. Plugin subscriptions, price history, and orderbook
     *       state are dropped along with it.</li>
     *   <li><b>Never auto-create.</b> If the merge group has no canonical-keyed market,
     *       reconcile does not synthesize one — merges only remove duplicates.</li>
     * </ol>
     * A single INFO log line per merge group with at least one deletion records
     * canonical, deleted alias IDs, and the count of refunded player orders per market.
     * <p>
     * <b>Idempotent:</b> once the alias-keyed markets are gone, subsequent invocations
     * with the same alias table are no-ops. This is what the load-time reconcile at the
     * end of {@link #load(Map)} relies on to handle merges that fired during BankSystem
     * startup before this manager registered its listener.
     * <p>
     * <b>Master-only.</b> Slaves receive the reconciled state via the existing sync
     * path — this method must not run on slave servers.
     *
     * @param aliasToCanonical unmodifiable alias→canonical map from
     *                         {@code IBankSystemEvents.getItemIDsMergedEvent()} or
     *                         {@link ItemIDManager#getItemIDAliasMap()}
     */
    public void consolidateMergedMarkets(@NotNull Map<ItemID, ItemID> aliasToCanonical)
    {
        if (aliasToCanonical.isEmpty())
            return;

        // Group aliases by canonical: canonical → set of alias IDs that resolve to it.
        Map<ItemID, Set<ItemID>> groups = new HashMap<>();
        for (Map.Entry<ItemID, ItemID> entry : aliasToCanonical.entrySet())
        {
            ItemID alias = entry.getKey();
            ItemID canonical = entry.getValue();
            if (alias == null || canonical == null || alias.equals(canonical))
                continue; // defensive: alias == canonical means nothing to consolidate
            groups.computeIfAbsent(canonical, k -> new HashSet<>()).add(alias);
        }

        for (Map.Entry<ItemID, Set<ItemID>> group : groups.entrySet())
        {
            ItemID canonical = group.getKey();
            Set<ItemID> aliases = group.getValue();

            // Delete every alias-keyed market in the group. A canonical-keyed market
            // (if any) is left completely alone — it is not a duplicate.
            StringBuilder deletedSummary = new StringBuilder();
            for (ItemID alias : aliases)
            {
                if (!markets.containsKey(alias))
                    continue;
                int refunded = countRefundableOrders(alias);
                deleteMarket(alias);
                if (deletedSummary.length() > 0) deletedSummary.append(", ");
                deletedSummary.append(alias).append(" (refunded ").append(refunded).append(" order(s))");
            }

            if (deletedSummary.length() > 0)
            {
                info("ItemID merge consolidation: canonical=" + canonical
                        + ", deleted alias-keyed duplicate(s)=" + deletedSummary);
            }
        }
    }

    /**
     * Counts the player limit orders on the given market that will be refunded when
     * {@link #deleteMarket(ItemID)} closes it. Bot orders are excluded — they never
     * had bank reservations. Used only for the merge consolidation log line.
     */
    private int countRefundableOrders(ItemID marketID)
    {
        ServerMarket market = markets.get(marketID);
        if (market == null)
            return 0;
        int count = 0;
        for (net.kroia.stockmarket.stockmarket.market.core.order.Order order : market.getLimitOrders())
        {
            if (!order.isBotOrder())
                count++;
        }
        return count;
    }







    @Override
    public @Nullable IServerMarket getMarket(@NotNull ItemID marketID)
    {
        return markets.get(marketID);
    }
    @Override
    public CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(getMarket(marketID));
    }





    @Override
    public void onPlayerJoin(UUID playerUUID, String playerName)
    {
        User user = userMap.get(playerUUID);
        if(user == null)
        {
            user = new User(playerUUID, playerName);
            userMap.put(playerUUID, user);
            net.kroia.stockmarket.util.AdminFlagSync.onUserAdded(playerUUID); // LuckPerms grant of a new user
        }
        else if(!user.getName().equals(playerName))
        {
            // Use createWithChangedName to preserve admin status and preferences
            user = User.createWithChangedName(user, playerName);
            userMap.put(playerUUID, user);
        }
        markOtherUsersNamedStale(playerUUID, playerName);
    }

    /**
     * SM-264: when a player joins with a name another stored user (different UUID) still holds, e.g. after a server
     * switched between online and offline mode, the other record is renamed to {@code "<name> (old <uuid prefix>)"}
     * so that name lookups ({@link #getPlayerUUID}) resolve to the player who is actually playing. Only the name
     * changes: admin flag, preferences and all other data of the old record stay as they are, and the old record
     * gets its name back if its UUID ever joins again.
     *
     * @param currentUUID the UUID that just joined with {@code playerName}
     * @param playerName  the joining player's name
     */
    private void markOtherUsersNamedStale(UUID currentUUID, String playerName) {
        for (User other : java.util.List.copyOf(userMap.values())) {
            if (!other.getUUID().equals(currentUUID) && other.getName().equals(playerName))
                userMap.put(other.getUUID(), User.createWithChangedName(other, staleName(playerName, other.getUUID())));
        }
    }

    /**
     * @param name the name the stale record held
     * @param uuid the stale record's UUID
     * @return the stale name; contains a space, so it can never be a real player name
     */
    public static String staleName(String name, UUID uuid) {
        return name + " (old " + uuid.toString().substring(0, 8) + ")";
    }
    /**
     * Removes a StockMarket user (admin flag and preferences). Used by test cleanup to drop fake users again;
     * a real player who rejoins is simply created anew by {@link #onPlayerJoin}.
     *
     * @param playerUUID the user to remove
     * @return true if the user existed
     */
    public boolean removeUser(UUID playerUUID) {
        return userMap.remove(playerUUID) != null;
    }

    /**
     * @param playerUUID the user to look up
     * @return the name stored for this StockMarket user (also for offline players), or null if unknown
     */
    public @Nullable String getUserName(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        return user == null ? null : user.getName();
    }

    /**
     * T-192: the StockMarket user record (persisted with the market data), e.g. for the per-admin alert toggle.
     *
     * @param playerUUID the user to look up
     * @return the user, or null if unknown
     */
    public @Nullable User getUser(UUID playerUUID) {
        return userMap.get(playerUUID);
    }

    /** @return true if a StockMarket user exists for this UUID */
    public boolean userExists(UUID playerUUID) {
        return userMap.containsKey(playerUUID);
    }

    @Override
    public void onPlayerJoinAsync(UUID playerUUID, String playerName)
    {
        onPlayerJoin(playerUUID, playerName);
    }




    /**
     * SM-264: resolves a name to a StockMarket user. A player online on this server wins; otherwise the stored users
     * are searched. If several stored users hold the name (see {@link #isUserNameAmbiguous}), this returns null
     * instead of guessing.
     */
    @Override
    public @Nullable UUID getPlayerUUID(String playerName)
    {
        MinecraftServer server = UtilitiesPlatform.getServer();
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayerByName(playerName);
        if (online != null && userMap.containsKey(online.getUUID()))
            return online.getUUID();
        UUID found = null;
        for(User user : userMap.values())
        {
            if(user.getName().equals(playerName))
            {
                if (found != null)
                    return null; // ambiguous
                found = user.getUUID();
            }
        }
        return found;
    }

    /**
     * SM-264: tells "unknown name" apart from "several stored users share the name" after
     * {@link #getPlayerUUID} returned null.
     *
     * @param playerName the exact stored name
     * @return true if more than one stored user holds this name
     */
    public boolean isUserNameAmbiguous(String playerName) {
        return userMap.values().stream().filter(u -> u.getName().equals(playerName)).count() > 1;
    }





    @Override
    public boolean setStockmarketAdminMode(UUID playerUUID, boolean isAdmin) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        user.setStockMarketAdmin(isAdmin);
        return true;
    }
    @Override
    public CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin) {
        return CompletableFuture.completedFuture(setStockmarketAdminMode(playerUUID, isAdmin));
    }





    /**
     * @return the UUIDs of every user whose StockMarket admin flag is set (used by the one-time LuckPerms migration)
     */
    public java.util.List<UUID> getStockmarketAdminUUIDs() {
        return userMap.values().stream().filter(User::isStockMarketAdmin).map(User::getUUID).toList();
    }

    @Override
    public boolean isStockmarketAdmin(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        return user.isStockMarketAdmin();
    }
    @Override
    public CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return CompletableFuture.completedFuture(false);
        return CompletableFuture.completedFuture(user.isStockMarketAdmin());
    }



    @Override
    public @NotNull PlayerPreferences getPlayerPreferences(UUID playerUUID) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return new PlayerPreferences();
        return user.getPreferences();
    }

    @Override
    public CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID) {
        return CompletableFuture.completedFuture(getPlayerPreferences(playerUUID));
    }

    @Override
    public boolean updatePlayerPreferences(UUID playerUUID, PlayerPreferences preferences) {
        User user = userMap.get(playerUUID);
        if (user == null)
            return false;
        user.setPreferences(preferences);
        return true;
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences) {
        return CompletableFuture.completedFuture(updatePlayerPreferences(playerUUID, preferences));
    }




    public ServerMarket getServerMarket(ItemID marketID)
    {
        return markets.get(marketID);
    }

    /**
     * T-166: remaining global trade cooldown of a player. The cooldown
     * ({@code MARKET.TRADE_COOLDOWN_MS}, read live) starts at the player's last ACCEPTED
     * order and is shared by all markets and both order paths (normal + inter-market).
     *
     * @param playerUUID the ordering player
     * @return milliseconds until the player may place the next order; 0 = may trade now
     */
    public long getTradeCooldownRemainingMs(UUID playerUUID)
    {
        long cooldown = getTradeCooldownMs();
        Long last = lastAcceptedPlayerOrderMillis.get(playerUUID);
        if (cooldown <= 0 || last == null)
            return 0;
        return Math.max(0, last + cooldown - System.currentTimeMillis());
    }

    /**
     * T-179: the configured global trade cooldown ({@code MARKET.TRADE_COOLDOWN_MS}, read live).
     * Also shipped to clients in the market price stream so the trade screen can grey out its buttons.
     *
     * @return cooldown in ms; 0 = off
     */
    public long getTradeCooldownMs()
    {
        Long cooldown = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.TRADE_COOLDOWN_MS.get();
        return cooldown == null ? 0 : Math.max(0, cooldown);
    }

    /**
     * T-179 (Mod Settings "Apply to all markets"): recomputes {@code maxOrderVolume} of EVERY market
     * from its natural abundance and the given factor via
     * {@link MarketSettings#defaultMaxOrderVolume(float, long)}. Custom per-market limits (including
     * 0 = unlimited) are overwritten. Persisted with the next market save (autosave/server stop),
     * like every other market-settings edit.
     *
     * @param factor items per abundance unit (the saved {@code MAX_ORDER_VOLUME_FACTOR})
     * @return number of markets updated
     */
    public int applyDefaultMaxOrderVolumeToAllMarkets(long factor)
    {
        for (ServerMarket market : markets.values()) {
            MarketSettings settings = market.getSettings();
            settings.maxOrderVolume = MarketSettings.defaultMaxOrderVolume(settings.naturalAbundance, factor);
        }
        return markets.size();
    }

    /**
     * T-166: records that a player order was accepted now, (re)starting the player's trade cooldown.
     * Call only after the order was actually put into the market; rejected orders must not call this.
     *
     * @param playerUUID the ordering player
     */
    public void markPlayerOrderAccepted(UUID playerUUID)
    {
        lastAcceptedPlayerOrderMillis.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * T-166 move credit: a chart "move" is cancel + re-create on the client. The re-created order
     * (and the restore after a failed move) must not be blocked by the max order size or the trade
     * cooldown, otherwise a lowered limit would make the player lose the order.
     * <p>
     * <b>Trust:</b> the exemption is never a client flag. A credit is only granted by the master
     * itself when it actually cancels one of the player's own LIMIT orders (CancelOrderRequest /
     * {@link #cancelInterMarketOrder}); it covers at most that order's remaining volume, the same
     * direction and the same market(s), expires after {@link #MOVE_CREDIT_TTL_MS} and is consumed by
     * the first accepted order it covers. So a client can only re-place volume that was already
     * accepted into the book, once per cancelled order — never more volume or more orders.
     *
     * @param market      market of the cancelled order (inter-market: the sell/"have" market)
     * @param otherMarket inter-market only: the buy/"want" market; null for normal orders
     * @param rawVolume   remaining raw volume (signed for normal orders, positive have-volume for inter-market)
     * @param expiresAtMs expiry time (System.currentTimeMillis based)
     */
    public record MoveCredit(ItemID market, @Nullable ItemID otherMarket, long rawVolume, long expiresAtMs) {}

    /** How long a move credit stays valid after the cancel. */
    public static final long MOVE_CREDIT_TTL_MS = 10_000L;
    // Player UUID -> unexpired move credits. Master only, dropped with the manager on server stop.
    private final Map<UUID, List<MoveCredit>> moveCredits = new HashMap<>();

    /**
     * Grants a move credit after the master cancelled one of the player's own LIMIT orders.
     * See {@link MoveCredit} for the trust reasoning.
     *
     * @param playerUUID  the order owner
     * @param market      market of the cancelled order (inter-market: sell/"have" market)
     * @param otherMarket inter-market: buy/"want" market; null for normal orders
     * @param rawVolume   remaining raw volume of the cancelled order
     */
    public void grantMoveCredit(UUID playerUUID, ItemID market, @Nullable ItemID otherMarket, long rawVolume)
    {
        if (rawVolume == 0) return;
        moveCredits.computeIfAbsent(playerUUID, k -> new ArrayList<>())
                .add(new MoveCredit(market, otherMarket, rawVolume, System.currentTimeMillis() + MOVE_CREDIT_TTL_MS));
    }

    /**
     * Finds an unexpired move credit covering a new LIMIT order: same market(s), same direction and
     * |rawVolume| not larger than the credit. Does not consume it — call {@link #consumeMoveCredit}
     * once the order is accepted, so a move that fails for another reason keeps the credit for the restore.
     *
     * @return the covering credit, or null (then the normal limits apply)
     */
    public @Nullable MoveCredit findMoveCredit(UUID playerUUID, ItemID market, @Nullable ItemID otherMarket, long rawVolume)
    {
        List<MoveCredit> credits = moveCredits.get(playerUUID);
        if (credits == null) return null;
        long now = System.currentTimeMillis();
        credits.removeIf(c -> c.expiresAtMs() < now);
        if (credits.isEmpty()) {
            moveCredits.remove(playerUUID);
            return null;
        }
        for (MoveCredit c : credits) {
            if (c.market().equals(market) && Objects.equals(c.otherMarket(), otherMarket)
                    && Long.signum(c.rawVolume()) == Long.signum(rawVolume)
                    && Math.abs(rawVolume) <= Math.abs(c.rawVolume()))
                return c;
        }
        return null;
    }

    /** Removes a move credit after the order it covered was accepted. */
    public void consumeMoveCredit(UUID playerUUID, MoveCredit credit)
    {
        List<MoveCredit> credits = moveCredits.get(playerUUID);
        if (credits != null && credits.remove(credit) && credits.isEmpty())
            moveCredits.remove(playerUUID);
    }


    /**
     * Validates and enqueues an inter-market order for processing in the next update tick.
     * Both the buy-leg and sell-leg markets must exist and be open.
     *
     * @param order the inter-market order to enqueue
     * @return true if the order was successfully enqueued, false if validation failed
     */
    public boolean putInterMarketOrder(InterMarketOrder order)
    {
        if (order == null)
        {
            warn("putInterMarketOrder: order is null");
            return false;
        }

        ServerMarket buyMarket = markets.get(order.getBuyItemID());
        ServerMarket sellMarket = markets.get(order.getSellItemID());

        if (buyMarket == null)
        {
            warn("putInterMarketOrder: buy market not found for " + order.getBuyItemID());
            return false;
        }
        if (sellMarket == null)
        {
            warn("putInterMarketOrder: sell market not found for " + order.getSellItemID());
            return false;
        }
        if (buyMarket.isUnderMaintenance())
        {
            warn("putInterMarketOrder: buy market is closed for " + order.getBuyItemID());
            return false;
        }
        if (sellMarket.isUnderMaintenance())
        {
            warn("putInterMarketOrder: sell market is closed for " + order.getSellItemID());
            return false;
        }
        // Outside trading hours no new inter-market orders are accepted (already queued ones
        // stay queued and are SKIPPED by the executor until both legs reopen).
        if (!buyMarket.isTradingOpen() || !sellMarket.isTradingOpen())
        {
            warn("putInterMarketOrder: a leg is outside its trading hours");
            return false;
        }

        pendingInterMarketOrders.add(order);
        return true;
    }








    @Override
    public List<MarketPriceStruct>  getCurrentMarketPricesAndStartNewCandle()
    {
        List<MarketPriceStruct> candles = new ArrayList<>();
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
        {
            candles.add(entry.getValue().getCurrentMarketPriceStructAndReset());
        }
        return candles;
    }



    private static double tmpValue = 100;
    private static final int MAX_CROSS_MARKET_ITERATIONS = 2;

    @Override
    public void update()
    {
        // Update individual markets first so regular orders execute and prices settle.
        for(ServerMarket m : markets.values())
        {
            m.update();
        }

        // Process cross-market orders against settled prices. When a fill occurs,
        // only re-update the two affected markets (not all), then retry once more.
        for (int i = 0; i < MAX_CROSS_MARKET_ITERATIONS; i++)
        {
            Set<ItemID> affected = processInterMarketOrders();
            if (affected.isEmpty())
                break;
            // Only re-settle markets that were involved in cross-market fills
            for (ItemID id : affected)
            {
                ServerMarket m = markets.get(id);
                if (m != null)
                    m.update();
            }
        }

        // T-191: every 10 minutes one ledger row per resting player order whose fill state changed.
        if (++ledgerSweepTicks >= OrderLedger.SWEEP_INTERVAL_TICKS)
        {
            ledgerSweepTicks = 0;
            logOpenOrdersToLedger(OrderLedger.PERIODIC);
        }

        if (isCandleUseGameTime())
        {
            // T-218: one candle per in-game hour. The first tick only initialises the clock.
            long dayTime = overworldDayTime(UtilitiesPlatform.getServer());
            gameCandleTicks++;
            if (gameCandleLastDayTime == Long.MIN_VALUE)
            {
                gameCandleLastDayTime = dayTime;
                gameCandleTicks = 0;
            }
            else if (shouldRotateGameCandle(gameCandleLastDayTime, dayTime, gameCandleTicks))
            {
                gameCandleLastDayTime = dayTime;
                gameCandleTicks = 0;
                BACKEND_INSTANCES.DATA_MANAGER.savePriceCandlesToSQL();
            }
        }
        else
        {
            long time = System.currentTimeMillis();
            long candleSaveIntervalMs = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get();
            if (time - candleSaveTimer_lastMs > candleSaveIntervalMs)
            {
                candleSaveTimer_lastMs = time;
                BACKEND_INSTANCES.DATA_MANAGER.savePriceCandlesToSQL();
            }
        }

        // Trading hours: flip open/closed only at the END of the tick, after all order buffers
        // and inter-market orders were processed. This manager only exists on the master.
        tradingHoursUseRealTime = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.TRADING_HOURS_USE_REAL_TIME.get();
        currentTradingMinute = tradingHoursUseRealTime ? realMinuteOfDay() : gameMinuteOfDay(UtilitiesPlatform.getServer());
        for (ServerMarket m : markets.values())
        {
            m.updateTradingHours(currentTradingMinute);
            broadcastStateChanges(m);
        }
    }

    /**
     * SM-310: last open/maintenance state broadcast per market, as {@link #STATE_OPEN}/{@link #STATE_MAINTENANCE}
     * bits. A market's first entry is recorded silently (clients learn the initial state from the price stream).
     */
    private final Map<ItemID, Integer> broadcastStates = new HashMap<>();
    private static final int STATE_OPEN = 1, STATE_MAINTENANCE = 2;

    /**
     * SM-310: broadcasts a {@link MarketChangedPacket} for each open/closed and maintenance edge of
     * {@code market} since the last tick. Runs at the end of every tick after the trading hours were
     * applied, so one place catches every source: schedule, manual override, admin maintenance toggle,
     * settings panel, BankSystem open/close.
     *
     * @param market the market to check
     */
    private void broadcastStateChanges(ServerMarket market)
    {
        int state = (market.isTradingOpen() ? STATE_OPEN : 0) | (market.isUnderMaintenance() ? STATE_MAINTENANCE : 0);
        Integer last = broadcastStates.put(market.getItemID(), state);
        if (last == null || last == state)
            return;
        int changed = last ^ state;
        if ((changed & STATE_MAINTENANCE) != 0)
            MarketChangedPacket.broadcast(market.getItemID(), (state & STATE_MAINTENANCE) != 0
                    ? MarketChangedPacket.Kind.MAINTENANCE_ON : MarketChangedPacket.Kind.MAINTENANCE_OFF);
        if ((changed & STATE_OPEN) != 0)
            MarketChangedPacket.broadcast(market.getItemID(), (state & STATE_OPEN) != 0
                    ? MarketChangedPacket.Kind.OPENED : MarketChangedPacket.Kind.CLOSED);
    }

    /**
     * @return the minute of the day (0..1439) the trading hours were last evaluated with,
     *         from the currently active clock source
     */
    public int getCurrentTradingMinute()
    {
        return currentTradingMinute;
    }

    /**
     * @return {@code true} if trading hours currently follow the server's real time,
     *         {@code false} for in-game time (value cached at the last update tick)
     */
    public boolean isTradingHoursUseRealTime()
    {
        return tradingHoursUseRealTime;
    }

    /**
     * @return the master server's local real time as minute of the day (0..1439)
     */
    public static int realMinuteOfDay()
    {
        LocalTime now = LocalTime.now();
        return now.getHour() * 60 + now.getMinute();
    }

    /**
     * Converts the overworld day time to a minute of the day. Day-time tick 0 is 06:00.
     * @param server the server, may be null (returns 0, e.g. in test contexts)
     * @return in-game minute of the day (0..1439)
     */
    public static int gameMinuteOfDay(@Nullable MinecraftServer server)
    {
        if (server == null || server.overworld() == null)
            return 0;
        long dayTime = server.overworld().getDayTime(); // mc<=1.21.11
        //long dayTime = server.overworld().getOverworldClockTime(); // mc>=26.1
        return (int) (((dayTime % 24000L + 6000L) % 24000L) * 1440L / 24000L);
    }

    /**
     * T-218: the master overworld's day time, stamped on every price candle as its in-game start.
     * Not wrapped at 24000: tick 0 is 06:00 of day 0, so candles keep their order across days.
     *
     * @param server the server, may be null (e.g. in test contexts)
     * @return the overworld day time in ticks, or {@link MarketPriceStruct#NO_GAME_TIME} when no
     *         overworld is available
     */
    public static long overworldDayTime(@Nullable MinecraftServer server)
    {
        if (server == null || server.overworld() == null)
            return MarketPriceStruct.NO_GAME_TIME;
        return server.overworld().getDayTime(); // mc<=1.21.11
        //return server.overworld().getOverworldClockTime(); // mc>=26.1
    }

    /**
     * T-218: the candle timebase, read once from {@code CANDLE_USE_GAME_TIME} and cached for the
     * server lifetime (restart-required). Read lazily because this manager is created before the
     * settings file is loaded.
     *
     * @return {@code true} if candles rotate on in-game hours, {@code false} for wall-clock time
     */
    public boolean isCandleUseGameTime()
    {
        if (candleUseGameTime == null)
            candleUseGameTime = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_USE_GAME_TIME.get();
        return candleUseGameTime;
    }

    /**
     * T-218: whether the live game-time candle must be closed. True when the in-game hour
     * changed (also when it jumped backwards through /time set) or when
     * {@link #GAME_CANDLE_TICKS} server ticks passed without that (daylight cycle frozen).
     *
     * @param lastDayTime        overworld day time at the last rotation
     * @param dayTime            current overworld day time
     * @param ticksSinceRotation server ticks since the last rotation
     * @return {@code true} to rotate now
     */
    public static boolean shouldRotateGameCandle(long lastDayTime, long dayTime, long ticksSinceRotation)
    {
        return Math.floorDiv(lastDayTime, GAME_CANDLE_TICKS) != Math.floorDiv(dayTime, GAME_CANDLE_TICKS)
                || ticksSinceRotation >= GAME_CANDLE_TICKS;
    }





    // ═══════════════════════════════════════════════════════════════════════════
    //  Inter-market order processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes all pending inter-market orders through the InterMarketExecutor.
     * Removes FILLED/CANCELED/ERROR orders (with appropriate callbacks), keeps
     * PARTIAL_FILL and SKIPPED orders in the queue for retry.
     *
     * @return set of market ItemIDs that were affected by fills (empty if nothing filled)
     */
    private Set<ItemID> processInterMarketOrders()
    {
        Set<ItemID> affectedMarkets = new HashSet<>();
        if (pendingInterMarketOrders.isEmpty())
            return affectedMarkets;

        // Bank account lookup: resolves account number to IServerBankAccount
        IntFunction<IServerBankAccount> bankLookup = (accountNr) ->
                BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(accountNr);

        Map<InterMarketOrder, InterMarketExecutor.ExecutionResult> results =
                InterMarketExecutor.processOrders(pendingInterMarketOrders, markets, bankLookup, getTradingCurrencyID());

        // Iterate results: remove terminal orders, keep retryable ones
        for (Map.Entry<InterMarketOrder, InterMarketExecutor.ExecutionResult> entry : results.entrySet())
        {
            InterMarketOrder order = entry.getKey();
            InterMarketExecutor.ExecutionResult result = entry.getValue();

            switch (result)
            {
                case FILLED:
                    pendingInterMarketOrders.remove(order);
                    onInterMarketOrderConsumed(order);
                    affectedMarkets.add(order.getBuyItemID());
                    affectedMarkets.add(order.getSellItemID());
                    break;

                case CANCELED:
                case ERROR:
                    pendingInterMarketOrders.remove(order);
                    onInterMarketOrderCanceled(order);
                    break;

                case PARTIAL_FILL:
                    onInterMarketOrderPartialFill(order);
                    affectedMarkets.add(order.getBuyItemID());
                    affectedMarkets.add(order.getSellItemID());
                    break;

                case SKIPPED:
                    // Keep in queue unchanged — retry next iteration
                    break;
            }
        }
        return affectedMarkets;
    }

    /**
     * Called when an inter-market order is fully filled.
     * Deposits any remaining transactionMoneyBalance, saves the historical record,
     * and unlocks any remaining sell-side funds.
     */
    private void onInterMarketOrderConsumed(InterMarketOrder order)
    {
        logInterMarketLegs(order, OrderLedger.CLOSE);
        depositTransactionMoneyBalance(order);
        saveInterMarketOrderRecord(order);
        unlockInterMarketRemainingFunds(order);
    }

    /**
     * Called when an inter-market order is canceled or encounters an error.
     * Deposits any remaining transactionMoneyBalance, saves the historical record,
     * and unlocks remaining sell-side funds.
     */
    private void onInterMarketOrderCanceled(InterMarketOrder order)
    {
        logInterMarketLegs(order, OrderLedger.CLOSE);
        depositTransactionMoneyBalance(order);
        saveInterMarketOrderRecord(order);
        unlockInterMarketRemainingFunds(order);
    }

    /**
     * Called when a limit inter-market order is partially filled.
     * The order remains in the queue for further processing.
     * History records are saved only on completion or cancellation to avoid duplicates.
     */
    private void onInterMarketOrderPartialFill(InterMarketOrder order)
    {
    }

    /**
     * T-191: writes one ledger row per leg (SELL have-item, BUY want-item) with the fills since the
     * leg's previous row, both under the ref {@code sm:im:<groupID>}. At close the money of all leg
     * rows nets out to the {@code transactionMoneyBalance} refund.
     *
     * @param order   the inter-market order (bot orders write nothing)
     * @param rowKind {@link OrderLedger#CLOSE}, {@link OrderLedger#PERIODIC} or {@link OrderLedger#SHUTDOWN}
     * @return the number of rows written
     */
    private int logInterMarketLegs(InterMarketOrder order, String rowKind)
    {
        if (order.isBotOrder()) return 0;
        String ref = OrderLedger.interMarketRef(order.getInterMarketGroupID());
        ItemID currency = getTradingCurrencyID();
        int rows = 0;
        if (OrderLedger.logDelta(order.getSellOrder(), ref, rowKind, currency)) rows++;
        if (OrderLedger.logDelta(order.getBuyOrder(), ref, rowKind, currency)) rows++;
        return rows;
    }

    /**
     * T-191: writes a ledger row for every open player order (resting limit orders of all markets and
     * pending inter-market orders) whose fill state changed since its last row. Runs every
     * {@link OrderLedger#SWEEP_INTERVAL_TICKS} ticks ({@link OrderLedger#PERIODIC}) and on server
     * stop before the save ({@link OrderLedger#SHUTDOWN}). Master only.
     *
     * @param rowKind {@link OrderLedger#PERIODIC} or {@link OrderLedger#SHUTDOWN}
     * @return the number of rows written
     */
    public int logOpenOrdersToLedger(String rowKind)
    {
        int rows = 0;
        for (ServerMarket m : markets.values())
            rows += m.logOpenOrdersToLedger(rowKind);
        for (InterMarketOrder order : pendingInterMarketOrders)
            rows += logInterMarketLegs(order, rowKind);
        return rows;
    }

    /**
     * Saves historical records for both legs of an inter-market order.
     * Bot orders and zero-amount records are skipped.
     */
    private void saveInterMarketOrderRecord(InterMarketOrder order)
    {
        if (order.isBotOrder()) return;

        // T-228: one Discord message for the whole order (sold, bought, leftover), as both legs are final here
        // (master, if enabled); skips orders where neither leg filled itself.
        StockMarketDiscord.postExchange(order, getTradingCurrencyID());
        InterMarketOrder.HistoricalRecordPair records = order.getHistoricalRecord();
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.ORDER_RECORD_MANAGER == null) return;

        // Save buy-leg record if it has fills
        OrderRecordStruct buyRecord = records.buyRecord();
        if (buyRecord != null && buyRecord.amount() != 0)
        {
            BACKEND_INSTANCES.ORDER_RECORD_MANAGER.save(buyRecord);
        }

        // Save sell-leg record if it has fills
        OrderRecordStruct sellRecord = records.sellRecord();
        if (sellRecord != null && sellRecord.amount() != 0)
        {
            BACKEND_INSTANCES.ORDER_RECORD_MANAGER.save(sellRecord);
        }
    }

    /**
     * Unlocks the sell-leg's remaining unfilled volume from the player's item bank.
     * For inter-market orders, the sell leg locks items at order creation time.
     * When the order completes (filled or canceled), any unfilled portion must be unlocked.
     */
    private void unlockInterMarketRemainingFunds(InterMarketOrder order)
    {
        if (order.isBotOrder()) return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.BANK_SYSTEM_API == null) return;

        // Get the sell order's remaining volume (negative for sells), negate to get positive unlock amount
        long toUnlock = -order.getSellOrder().getRemainingVolume();
        if (toUnlock <= 0) return;

        IServerBankAccount bankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API
                .getServerBankManager().getSync().getBankAccount(order.getBankAccountNr());
        if (bankAccount == null)
        {
            warn("Cannot unlock items for inter-market order: bank account " + order.getBankAccountNr() + " not found");
            return;
        }

        IServerBank itemBank = bankAccount.getBank(order.getSellItemID());
        if (itemBank == null)
        {
            warn("Cannot unlock items for inter-market order: item bank not found for " + order.getSellItemID()
                    + " on account " + order.getBankAccountNr());
            return;
        }

        BankStatus status = itemBank.unlockAmount(toUnlock);
        if (status != BankStatus.SUCCESS)
        {
            warn("Failed to unlock " + toUnlock + " items for inter-market order on account "
                    + order.getBankAccountNr() + ": " + status);
        }
    }

    /**
     * Deposits any remaining transactionMoneyBalance to the player's money bank.
     * Called on order completion or cancellation to return buffered dollars.
     */
    private void depositTransactionMoneyBalance(InterMarketOrder order)
    {
        if (order.isBotOrder())
            return;
        long balance = order.getTransactionMoneyBalance();
        if (balance <= 0)
            return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.BANK_SYSTEM_API == null)
            return;

        IServerBankAccount bankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API
                .getServerBankManager().getSync().getBankAccount(order.getBankAccountNr());
        if (bankAccount == null)
        {
            warn("Cannot deposit transactionMoneyBalance: bank account " + order.getBankAccountNr() + " not found");
            return;
        }

        ItemID currencyID = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID();
        IServerBank moneyBank = bankAccount.getBank(currencyID);
        if (moneyBank == null)
        {
            warn("Cannot deposit transactionMoneyBalance: money bank not found on account " + order.getBankAccountNr());
            return;
        }

        moneyBank.deposit(balance);
        order.setTransactionMoneyBalance(0);
    }

    /**
     * Cancels all pending inter-market orders that touch a given market (buy or sell leg).
     * Used when a market is closed to clean up any pending inter-market orders involving that market.
     *
     * @param marketID the market ItemID being closed
     * @return the number of orders that were canceled
     */
    public int cancelInterMarketOrdersForMarket(ItemID marketID)
    {
        int canceledCount = 0;
        Iterator<InterMarketOrder> it = pendingInterMarketOrders.iterator();
        while (it.hasNext())
        {
            InterMarketOrder order = it.next();
            if (order.getBuyItemID().equals(marketID) || order.getSellItemID().equals(marketID))
            {
                it.remove();
                onInterMarketOrderCanceled(order);
                canceledCount++;
            }
        }
        if (canceledCount > 0)
        {
            info("Canceled " + canceledCount + " inter-market orders touching market " + marketID);
        }
        return canceledCount;
    }

    /**
     * T-195: cancels every open order of one bank account across all markets, including its pending
     * inter-market orders. Called on the master when BankSystem freezes the account. Uses the normal
     * cancel bookkeeping, so the reserved money and items are unlocked and the CLOSE ledger rows are
     * written (BankSystem does not block these raw unlocks on a frozen account). Bot orders are untouched.
     *
     * @param bankAccountNr the bank account whose orders are cancelled
     * @return the number of cancelled orders (an inter-market order counts once)
     */
    public int cancelOrdersOfBankAccount(int bankAccountNr)
    {
        int count = 0;
        for (ServerMarket market : markets.values())
            count += market.cancelOrdersOfBankAccount(bankAccountNr);
        Iterator<InterMarketOrder> it = pendingInterMarketOrders.iterator();
        while (it.hasNext())
        {
            InterMarketOrder order = it.next();
            if (!order.isBotOrder() && order.getBankAccountNr() == bankAccountNr)
            {
                it.remove();
                onInterMarketOrderCanceled(order);
                count++;
            }
        }
        if (count > 0)
            info("Bank account " + bankAccountNr + " frozen: cancelled " + count + " open order(s)");
        return count;
    }

    /**
     * Cancels a specific pending inter-market order identified by its group ID.
     * Only cancels if the order belongs to the specified player.
     * @param groupID the interMarketGroupID of the order
     * @param playerUUID the player requesting the cancellation
     * @return true if found and canceled
     */
    public boolean cancelInterMarketOrder(UUID groupID, UUID playerUUID) {
        Iterator<InterMarketOrder> it = pendingInterMarketOrders.iterator();
        while (it.hasNext()) {
            InterMarketOrder order = it.next();
            if (order.getInterMarketGroupID().equals(groupID)
                    && !order.isBotOrder()
                    && playerUUID.equals(order.getOwnerUUID())) {
                // T-166: capture before the cancel callback touches the order. Limit orders only
                // (the ones a player can drag); allows re-placing this volume (see MoveCredit).
                long remainingHave = -order.getSellOrder().getRemainingVolume();
                it.remove();
                onInterMarketOrderCanceled(order);
                if (order.getCrossRateLimit() > 0)
                    grantMoveCredit(playerUUID, order.getSellItemID(), order.getBuyItemID(), remainingHave);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all pending inter-market orders belonging to a specific player.
     * Used by ActiveOrdersStream to include inter-market orders in the stream.
     * @param playerUUID the player's UUID
     * @return list of matching orders (may be empty, never null)
     */
    public List<InterMarketOrder> getPendingInterMarketOrdersForPlayer(UUID playerUUID) {
        List<InterMarketOrder> result = new ArrayList<>();
        for (InterMarketOrder order : pendingInterMarketOrders) {
            if (!order.isBotOrder() && playerUUID.equals(order.getOwnerUUID())) {
                result.add(order);
            }
        }
        return result;
    }


    @Override
    public boolean save(Map<String, ListTag> listTags) {
        boolean success = true;
        ListTag marketListTag = new ListTag();
        for(Map.Entry<ItemID, ServerMarket> entry : markets.entrySet())
        {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            entry.getValue().save(marketTag);
            marketListTag.add(marketTag);
        }
        listTags.put("markets", marketListTag);

        // Save pending inter-market orders
        ListTag interMarketOrdersTag = new ListTag();
        for (InterMarketOrder imo : pendingInterMarketOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            if (imo.save(orderTag))
            {
                interMarketOrdersTag.add(orderTag);
            }
            else
            {
                error("save(): Failed to save inter-market order");
                success = false;
            }
        }
        listTags.put("interMarketOrders", interMarketOrdersTag);

        ListTag userListTag = new ListTag();
        for(User user : userMap.values())
        {
            CompoundTag userTag = new CompoundTag();
            if(user.save(userTag))
                userListTag.add(userTag);
            else
                success = false;
        }
        listTags.put("users", userListTag);

        // T-191: order ID counter
        CompoundTag metaTag = new CompoundTag();
        metaTag.putLong("nextOrderId", Order.getNextOrderId());
        ListTag metaListTag = new ListTag();
        metaListTag.add(metaTag);
        listTags.put("meta", metaListTag);
        return success;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        boolean success = true;
        ListTag marketListTag =  listTags.get("markets");
        if(marketListTag == null)
        {
            error("markets list tag is null while loading Market Manager");
            return false;
        }

        Map<ItemID, ServerMarket> newMarkets = new HashMap<>();

        for (int i = 0; i < marketListTag.size(); i++)
        {
            CompoundTag marketTag = NbtCompat.getCompound(marketListTag, i);
            ItemID id = ItemID.createFromTag(marketTag);
            if(id.isValid())
            {
                ServerMarket market = new ServerMarket(id);
                if(market.load(marketTag))
                {
                    market.setMarketMaintenanceCallback(this::cancelInterMarketOrdersForMarket);
                    newMarkets.put(id, market);
                }
                else {
                    error("load(): Failed to load market for item: " + id);
                    success = false;
                }
            }
            else {
                error("load(): Invalid ItemID at index " + i + " while loading markets");
                success = false;
            }
        }
        markets.clear();
        markets.putAll(newMarkets);

        // T-191: order ID counter (absent in older saves -> starts at the clock floor)
        ListTag metaListTag = listTags.get("meta");
        long savedNextOrderId = 0;
        if (metaListTag != null && !metaListTag.isEmpty())
        {
            CompoundTag metaTag = NbtCompat.getCompound(metaListTag, 0);
            if (metaTag.contains("nextOrderId"))
                savedNextOrderId = NbtCompat.getLong(metaTag, "nextOrderId");
        }
        Order.restoreOrderIdCounter(savedNextOrderId);

        // Load pending inter-market orders
        pendingInterMarketOrders.clear();
        ListTag interMarketOrdersTag = listTags.get("interMarketOrders");
        if (interMarketOrdersTag != null)
        {
            for (int i = 0; i < interMarketOrdersTag.size(); i++)
            {
                CompoundTag orderTag = NbtCompat.getCompound(interMarketOrdersTag, i);
                InterMarketOrder imo = InterMarketOrder.createFromNBT(orderTag);
                if (imo != null)
                {
                    pendingInterMarketOrders.add(imo);
                }
                else
                {
                    error("load(): Failed to load inter-market order at index " + i);
                    success = false;
                }
            }
        }

        ListTag userListTag = listTags.get("users");
        if(userListTag != null)
        {
            userMap.clear();
            for(int i = 0; i < userListTag.size(); i++)
            {
                CompoundTag userTag = NbtCompat.getCompound(userListTag, i);
                User user = User.createFromTag(userTag);
                if(user != null)
                    userMap.put(user.getUUID(), user);
                else
                    success = false;
            }
        }

        // Catch any BankSystem ItemID merges that fired before StockMarket registered its
        // event listener (e.g. during BankSystem's own startup / world load). Reads the
        // current alias table and consolidates any markets still keyed under a merged ID.
        // Idempotent: on a fresh world with no aliases, or after a previous consolidation,
        // this is a no-op.
        consolidateMergedMarkets(ItemIDManager.getItemIDAliasMap());

        return success;
    }


    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerMarketManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarketManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarketManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerMarketManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerMarketManager]: "+message);
    }



}
