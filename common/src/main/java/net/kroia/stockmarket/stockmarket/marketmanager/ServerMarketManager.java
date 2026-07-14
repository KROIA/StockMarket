package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.packet.MarketRemovedPacket;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.InterMarketExecutor;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    // Central queue for pending inter-market orders, sorted by time (FIFO)
    private final PriorityQueue<InterMarketOrder> pendingInterMarketOrders =
            new PriorityQueue<>(Comparator.comparingLong(InterMarketOrder::getTime));

    private long candleSaveTimer_lastMs = (System.currentTimeMillis()/60000)*60000;
    private final Random random = new Random();
    private ItemID tradingCurrencyID = null;

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
            float abundance = (preset != null) ? preset.getNaturalAbundance() : 10f;
            m = new ServerMarket(marketID, null, defaultPrice, abundance);
            m.setMarketClosedCallback(this::cancelInterMarketOrdersForMarket);
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
        }
        return m;
    }
    @Override
    public CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID) {
        return CompletableFuture.completedFuture(createMarket(marketID));
    }






    @Override
    public boolean deleteMarket(@NotNull ItemID marketID)
    {
        ServerMarket market = markets.get(marketID);
        if (market == null) return false;

        // Close market: cancels player orders and inter-market orders via callback
        if (market.isMarketOpen()) {
            market.setMarketOpen(false);
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
        MarketRemovedPacket.broadcast(marketID);
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
     *       unsubscribes it from plugins, and broadcasts {@code MarketRemovedPacket}.</li>
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
            return;
        }
        if(!user.getName().equals(playerName))
        {
            // Use createWithChangedName to preserve admin status and preferences
            user = User.createWithChangedName(user, playerName);
            userMap.put(playerUUID, user);
        }
    }
    @Override
    public void onPlayerJoinAsync(UUID playerUUID, String playerName)
    {
        onPlayerJoin(playerUUID, playerName);
    }




    @Override
    public @Nullable UUID getPlayerUUID(String playerName)
    {
        for(User user : userMap.values())
        {
            if(user.getName().equals(playerName))
            {
                return user.getUUID();
            }
        }
        return null;
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
        if (!buyMarket.isMarketOpen())
        {
            warn("putInterMarketOrder: buy market is closed for " + order.getBuyItemID());
            return false;
        }
        if (!sellMarket.isMarketOpen())
        {
            warn("putInterMarketOrder: sell market is closed for " + order.getSellItemID());
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

        long time = System.currentTimeMillis();
        long candleSaveIntervalMs = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get();
        if(time - candleSaveTimer_lastMs > candleSaveIntervalMs)
        {
            candleSaveTimer_lastMs = time;
            BACKEND_INSTANCES.DATA_MANAGER.savePriceCandlesToSQL();
        }
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
     * Saves historical records for both legs of an inter-market order.
     * Bot orders and zero-amount records are skipped.
     */
    private void saveInterMarketOrderRecord(InterMarketOrder order)
    {
        if (order.isBotOrder()) return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.ORDER_RECORD_MANAGER == null) return;

        InterMarketOrder.HistoricalRecordPair records = order.getHistoricalRecord();

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
                it.remove();
                onInterMarketOrderCanceled(order);
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
            CompoundTag marketTag = marketListTag.getCompound(i);
            ItemID id = ItemID.createFromTag(marketTag);
            if(id.isValid())
            {
                ServerMarket market = new ServerMarket(id);
                if(market.load(marketTag))
                {
                    market.setMarketClosedCallback(this::cancelInterMarketOrdersForMarket);
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

        // Load pending inter-market orders
        pendingInterMarketOrders.clear();
        ListTag interMarketOrdersTag = listTags.get("interMarketOrders");
        if (interMarketOrdersTag != null)
        {
            for (int i = 0; i < interMarketOrdersTag.size(); i++)
            {
                CompoundTag orderTag = interMarketOrdersTag.getCompound(i);
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
                CompoundTag userTag = userListTag.getCompound(i);
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
