package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.banking.bankaccount.ServerBankAccount;
import net.kroia.banksystem.banking.bankmanager.ServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.banksystem.util.VolatileItemComponents;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Master-only in-game tests for {@link ServerMarketManager#consolidateMergedMarkets(Map)} —
 * the StockMarket-side consumer of BankSystem's {@code ITEM_IDS_MERGED} event
 * (task T-061 / ISSUES.md #62).
 * <p>
 * Uses the same production-safe pattern as
 * {@link net.kroia.banksystem.testing.tests.ItemIDMergeGuardTests}: register
 * synthetic marker-tagged item variants so the tests never collide with real
 * player state, drive merges through the explicit-set overload of
 * {@code ItemIDManager.renormalizeAndMerge(Collection)} so the globally applied
 * volatile-component set stays untouched, and clean up every created market and
 * bank account in {@code try/finally}.
 * <p>
 * <b>Shared-state teardown:</b> every test takes a {@link StateSnapshot} up front and
 * restores it in the outermost {@code finally}. Three separate leaks made this necessary:
 * <ul>
 *   <li>the suite permanently minted two synthetic paper ItemIDs per test (12 per run),
 *       piling up as indistinguishable "Paper" rows in the BankSystem manage screen;</li>
 *   <li>{@code createMarket} calls {@code allowItemID}, so every test also left an
 *       allowed-set entry pointing at an ItemID the teardown then dropped — an
 *       unresolvable filter entry renders as a stray "air" row;</li>
 *   <li>the event-driven test's {@code renormalizeAndMerge} pass ran against the LIVE
 *       registry, collapsing every real item pair that differs only by
 *       {@code minecraft:repair_cost} (and consolidating the corresponding player bank
 *       balances — an effect nothing can undo).</li>
 * </ul>
 */
public class MarketMergeConsolidationTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        MarketMergeConsolidationTestSuite.backend = backend;
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MERGE_CONSOLIDATION;
    }

    @Override
    public void registerTests() {
        addTest("direct_call_deletes_alias_when_no_canonical", this::test_directCall_deletesAliasWhenNoCanonical);
        addTest("direct_call_keeps_canonical_deletes_alias", this::test_directCall_keepsCanonicalDeletesAlias);
        addTest("direct_call_is_idempotent", this::test_directCall_isIdempotent);
        addTest("direct_call_never_auto_creates", this::test_directCall_neverAutoCreates);
        addTest("event_driven_merge_consolidates", this::test_eventDrivenMerge_consolidates);
        addTest("deleted_market_refunds_locked_funds", this::test_deletedMarket_refundsLockedFunds);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Bit-exact snapshot of the shared server state this suite mutates: the
     * {@link ItemIDManager} registry plus BankSystem's item filter sets.
     * <p>
     * Taken before a test registers synthetic templates or creates a market, and restored in
     * {@code finally}. Without it every run of this suite permanently minted two synthetic
     * paper ItemIDs per test (12 per run — all rendering as an indistinguishable "Paper" row
     * in the BankSystem manage screen), left an allowed-set entry behind per created market,
     * and the event-driven test additionally collapsed real item pairs registry-wide. Same
     * teardown pattern BankSystem's own {@code ItemIDMergeGuardTests} uses around
     * {@code renormalizeAndMerge(Collection)}.
     *
     * @param items      copy of the ItemID → template map
     * @param aliases    copy of the alias → canonical map
     * @param quarantined copy of the quarantined-alias map
     * @param counter    value of the short minting counter
     * @param filters    copy of the allowed set + runtime blacklist, or null when the
     *                   concrete master bank manager is unavailable
     */
    private record StateSnapshot(Map<ItemID, ItemStack> items,
                                 Map<ItemID, ItemID> aliases,
                                 Map<ItemID, ItemID> quarantined,
                                 int counter,
                                 ServerBankManager.ItemFilterSnapshot filters) {}

    /** Captures the current shared state for a later {@link #restoreState(StateSnapshot)}. */
    private static StateSnapshot snapshotState() {
        ServerBankManager concrete = concreteBankManager();
        return new StateSnapshot(
                new HashMap<>(ItemIDManager.getItemIDMap()),
                new HashMap<>(ItemIDManager.getItemIDAliasMap()),
                ItemIDManager.getQuarantinedAliases_forTesting(),
                ItemIDManager.getNextShortCounter_forTesting(),
                concrete == null ? null : concrete.snapshotItemFilters());
    }

    /**
     * Restores a snapshot, discarding every ItemID minted, every alias created and every
     * filter-set entry added since it was taken. Never throws — it runs inside
     * {@code finally} blocks.
     * <p>
     * Filters are restored <b>before</b> the registry so a restored filter entry can never
     * reference an ItemID the registry restore has already dropped — an unresolvable filter
     * entry renders as a stray "air" row in the BankSystem manage screen.
     * <p>
     * Note this deliberately does not use {@code disallowItemID} to undo the
     * {@code allowItemID} calls {@code createMarket} makes: that method is not an inverse, it
     * runtime-<b>blacklists</b> the ID (and clears holder banks without refund). The
     * snapshot/restore pair is the only way back to "neither allowed nor blacklisted".
     *
     * @param snapshot the state to reinstall
     */
    private static void restoreState(StateSnapshot snapshot) {
        if (snapshot.filters() != null) {
            try {
                ServerBankManager concrete = concreteBankManager();
                if (concrete != null) concrete.restoreItemFilters(snapshot.filters());
            } catch (Throwable t) {
                StockMarketMod.LOGGER.error("Failed to restore the BankSystem item filters after a merge-consolidation test", t);
            }
        }
        try {
            ItemIDManager.replaceState_forTesting(snapshot.items(), snapshot.aliases(), snapshot.counter());
            ItemIDManager.restoreQuarantinedAliases_forTesting(snapshot.quarantined());
        } catch (Throwable t) {
            StockMarketMod.LOGGER.error("Failed to restore the ItemID registry after a merge-consolidation test", t);
        }
    }

    /**
     * The concrete master bank manager, or null if unavailable / not the master impl.
     * {@code snapshotItemFilters} and {@code restoreItemFilters} are test-support methods on
     * the implementation, not on the sync interface.
     */
    private static ServerBankManager concreteBankManager() {
        if (backend == null || backend.BANK_SYSTEM_API == null)
            return null;
        IServerBankManager bankManager = backend.BANK_SYSTEM_API.getServerBankManager().getSync();
        return bankManager instanceof ServerBankManager concrete ? concrete : null;
    }

    /**
     * Fixed marker value for the synthetic templates. Deliberately <b>not</b> a random UUID:
     * a stable marker means an aborted run (JVM killed between mint and teardown) leaves at
     * most this one pair behind, and the next run's
     * {@code registerItemStackServerSide_direct} returns those same two IDs instead of
     * minting a fresh pair. Random markers made the residue grow by 12 IDs every run.
     * <p>
     * It cannot collide with a real player's paper: the {@code stockmarket_merge_test}
     * custom_data key is not written by any production path, so only an item deliberately
     * crafted with this exact NBT would match.
     */
    private static final String TEST_MARKER = "market_merge_consolidation_suite";

    /** Creates a paper stack carrying the test marker inside minecraft:custom_data. */
    private static ItemStack paperWithMarker(String marker) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("stockmarket_merge_test", marker);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return stack;
    }

    /**
     * Registers two synthetic paper templates that differ only in
     * {@code minecraft:repair_cost} — distinct IDs under the server's real
     * volatile set, colliding as soon as repair_cost is added to the set.
     * Both templates carry the same {@link #TEST_MARKER} so the pair maps to one
     * "logical" item post-merge.
     * <p>
     * <b>Registers into the LIVE registry</b> — callers MUST bracket the call with
     * {@link #snapshotState()} / {@link #restoreState(StateSnapshot)} or the two
     * minted IDs stay in the world's ItemID table forever.
     *
     * @return the two registered IDs, index 0 = plain, index 1 = with repair_cost
     */
    private ItemID[] registerRepairCostPair() {
        ItemStack a = paperWithMarker(TEST_MARKER);
        ItemStack b = a.copy();
        b.set(DataComponents.REPAIR_COST, 7);
        ItemID idA = ItemIDManager.registerItemStackServerSide_direct(a);
        ItemID idB = ItemIDManager.registerItemStackServerSide_direct(b);
        return new ItemID[]{idA, idB};
    }

    /** Copy of {@code base} with {@code minecraft:repair_cost} appended and sorted. */
    private static List<String> withRepairCost(List<String> base) {
        List<String> grown = new ArrayList<>(base);
        if (!grown.contains("minecraft:repair_cost"))
            grown.add("minecraft:repair_cost");
        grown.sort(String::compareTo);
        return grown;
    }

    /** Convenience: returns the master's concrete {@link ServerMarketManager} or fails the test. */
    private ServerMarketManager getServerMarketManagerOrFail() {
        return backend.MARKET_MANAGER.getServerMarketManager();
    }

    private static ItemID lowerShort(ItemID a, ItemID b) {
        return a.getShort() < b.getShort() ? a : b;
    }
    private static ItemID higherShort(ItemID a, ItemID b) {
        return a.getShort() < b.getShort() ? b : a;
    }

    // ========================================================================
    // Tests — direct-call unit tests (no BankSystem merge involved)
    // ========================================================================

    /**
     * With no market keyed under the canonical ID, {@code consolidateMergedMarkets}
     * must delete the alias-keyed market outright — it never re-keys, and it never
     * auto-creates a canonical replacement. Deleting the only market in the group
     * leaves the group with no market at all, which is the intended outcome under
     * the "delete-duplicates-only" policy.
     */
    private TestResult test_directCall_deletesAliasWhenNoCanonical() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            // Simulate BankSystem's canonical selection: lowest short wins.
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            try {
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (aliasMarket == null) return fail("failed to create the alias-keyed market");

                // No canonical market exists — reconcile must delete the alias market,
                // leaving the group empty (no auto-created canonical replacement).
                smm.consolidateMergedMarkets(Map.of(alias, canonical));

                TestResult r = assertFalse("alias key removed from markets map",
                        smm.marketExists(alias));
                if (!r.passed()) return r;
                r = assertFalse("no canonical market auto-created",
                        smm.marketExists(canonical));
                if (!r.passed()) return r;
                return pass("consolidateMergedMarkets deleted the alias market and did not create a canonical one");
            } finally {
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }
        } finally {
            restoreState(snapshot);
        }
    }

    /**
     * With a market keyed under the canonical ID <b>and</b> a duplicate market keyed
     * under the alias ID, {@code consolidateMergedMarkets} must keep the canonical
     * market unchanged and delete the alias-keyed duplicate.
     */
    private TestResult test_directCall_keepsCanonicalDeletesAlias() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            try {
                IServerMarket canonicalMarket = smm.createMarket(canonical);
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (canonicalMarket == null || aliasMarket == null)
                    return fail("failed to create the two test markets");

                smm.consolidateMergedMarkets(Map.of(alias, canonical));

                TestResult r = assertTrue("canonical-keyed market still exists",
                        smm.marketExists(canonical));
                if (!r.passed()) return r;
                r = assertFalse("alias-keyed duplicate deleted",
                        smm.marketExists(alias));
                if (!r.passed()) return r;
                // The surviving market must be the original canonical instance, not the alias one.
                r = assertTrue("survivor is the original canonical instance",
                        smm.getMarket(canonical) == canonicalMarket);
                if (!r.passed()) return r;
                return pass("consolidateMergedMarkets kept canonical and deleted alias duplicate");
            } finally {
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }
        } finally {
            restoreState(snapshot);
        }
    }

    /**
     * Running {@code consolidateMergedMarkets} twice must be a no-op the second time:
     * once the alias-keyed markets are already gone, there is nothing left to
     * consolidate. This is exactly the property the load-time reconcile relies on.
     * <p>
     * The canonical market (created up front) is used as the stable identity to check
     * against — under the "delete-only, never re-key" policy the survivor must be the
     * original canonical instance both times.
     */
    private TestResult test_directCall_isIdempotent() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            try {
                IServerMarket canonicalMarket = smm.createMarket(canonical);
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (canonicalMarket == null || aliasMarket == null)
                    return fail("failed to create the two test markets");

                Map<ItemID, ItemID> aliasMap = Map.of(alias, canonical);
                smm.consolidateMergedMarkets(aliasMap);
                IServerMarket afterFirst = smm.getMarket(canonical);
                if (afterFirst == null) return fail("canonical market missing after first reconcile");
                if (afterFirst != canonicalMarket)
                    return fail("first reconcile replaced the canonical instance — it should not");
                if (smm.marketExists(alias))
                    return fail("alias market survived first reconcile");

                // Second call: nothing to change. Same identity, same canonical key.
                smm.consolidateMergedMarkets(aliasMap);
                IServerMarket afterSecond = smm.getMarket(canonical);
                TestResult r = assertTrue("canonical market still present after 2nd reconcile",
                        afterSecond != null);
                if (!r.passed()) return r;
                r = assertTrue("same market instance survives the 2nd reconcile",
                        afterSecond == afterFirst);
                if (!r.passed()) return r;
                r = assertFalse("alias-keyed market still absent",
                        smm.marketExists(alias));
                if (!r.passed()) return r;
                return pass("consolidateMergedMarkets is idempotent");
            } finally {
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }
        } finally {
            restoreState(snapshot);
        }
    }

    /**
     * The "never auto-create" property has two flavours under the delete-only policy:
     * <ol>
     *   <li>If the group has zero markets, reconcile creates nothing.</li>
     *   <li>If the group has <b>only</b> alias-keyed markets (no canonical), reconcile
     *       deletes them and still creates nothing — no canonical replacement is
     *       synthesized from the deleted alias market's state.</li>
     * </ol>
     * Both flavours are checked here.
     */
    private TestResult test_directCall_neverAutoCreates() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            // Defensive: nothing should exist under either ID at this point (fresh templates).
            // If a stale entry from a previous test run exists, clean it before the assertion.
            smm.deleteMarket(canonical);
            smm.deleteMarket(alias);

            // Case 1: empty group — reconcile must not create either market.
            smm.consolidateMergedMarkets(Map.of(alias, canonical));

            TestResult r = assertFalse("empty group: no canonical market auto-created",
                    smm.marketExists(canonical));
            if (!r.passed()) return r;
            r = assertFalse("empty group: no alias market auto-created", smm.marketExists(alias));
            if (!r.passed()) return r;

            // Case 2: alias-only group — reconcile must delete the alias and still not
            // create a canonical replacement (this is the case that would have triggered
            // the removed re-key path under the old policy).
            try {
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (aliasMarket == null) return fail("failed to create the alias-keyed market");

                smm.consolidateMergedMarkets(Map.of(alias, canonical));

                r = assertFalse("alias-only group: alias deleted", smm.marketExists(alias));
                if (!r.passed()) return r;
                r = assertFalse("alias-only group: no canonical market auto-created",
                        smm.marketExists(canonical));
                if (!r.passed()) return r;
            } finally {
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }

            return pass("consolidateMergedMarkets never auto-creates markets");
        } finally {
            restoreState(snapshot);
        }
    }

    /**
     * Integration test: create two markets, then drive a real BankSystem merge via
     * the explicit-set overload of {@code ItemIDManager.renormalizeAndMerge}. This
     * fires the {@code ITEM_IDS_MERGED} event; the StockMarket listener installed in
     * {@code onBankSystemSetupComplete} must consolidate the two markets down to one.
     */
    private TestResult test_eventDrivenMerge_consolidates() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        List<String> realSet = VolatileItemComponents.getEffectiveComponentIds();
        if (realSet.contains("minecraft:repair_cost"))
            return pass("skipped: minecraft:repair_cost is already volatile on this server — "
                    + "the collapse scenario cannot be constructed");

        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            // BankSystem's canonical rule: lowest short wins.
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            try {
                IServerMarket canonicalMarket = smm.createMarket(canonical);
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (canonicalMarket == null || aliasMarket == null)
                    return fail("failed to create the two test markets");

                // Install a fixture registry holding ONLY the synthetic pair before applying
                // the grown set. renormalizeAndMerge() re-normalizes and merges EVERY template
                // in the registry, so running it against the live registry would permanently
                // collapse real item pairs that differ only by repair_cost (and consolidate the
                // corresponding player bank balances — an effect no restore can undo). With the
                // fixture in place the only alias pair produced is ours, so the ITEM_IDS_MERGED
                // consolidation is scoped to the synthetic templates.
                Map<ItemID, ItemStack> fixture = new HashMap<>();
                fixture.put(canonical, ItemIDManager.getItemStackTemplate(canonical).copy());
                fixture.put(alias, ItemIDManager.getItemStackTemplate(alias).copy());
                ItemIDManager.replaceState_forTesting(fixture, Map.of(), snapshot.counter());

                // Apply the merge under the explicitly grown set — repair_cost becomes volatile,
                // the two paper variants collapse in the BankSystem registry, the ITEM_IDS_MERGED
                // event fires, and our listener runs consolidateMergedMarkets.
                ItemIDManager.renormalizeAndMerge(withRepairCost(realSet));

                TestResult r = assertTrue("canonical-keyed market still exists post-merge",
                        smm.marketExists(canonical));
                if (!r.passed()) return r;
                r = assertFalse("alias-keyed market deleted by the event listener",
                        smm.marketExists(alias));
                if (!r.passed()) return r;
                // Alias must now resolve to canonical in the BankSystem registry.
                r = assertEquals("BankSystem resolves alias to canonical",
                        canonical, ItemIDManager.resolveAlias(alias));
                if (!r.passed()) return r;
                return pass("BankSystem merge event triggered market consolidation");
            } finally {
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }
        } finally {
            // Undoes both the fixture swap and the merge: the live registry, alias table,
            // quarantine map and short counter all go back to their pre-test values.
            restoreState(snapshot);
        }
    }

    /**
     * When {@code consolidateMergedMarkets} deletes an alias-keyed duplicate whose
     * orderbook holds a player limit order, the existing {@code deleteMarket} path
     * must fire — closing the market cancels open orders and unlocks the reserved
     * bank funds through {@code ServerMarket.cancelAllPlayerOrders} +
     * {@code unlockRemainingFunds}. We check the locked balance instead of the log
     * text: locked→0 after the reconcile means the refund path ran end to end.
     */
    private TestResult test_deletedMarket_refundsLockedFunds() {
        ServerMarketManager smm = getServerMarketManagerOrFail();
        if (smm == null) return fail("ServerMarketManager unavailable — not master?");
        IServerBankManager bankManager = backend.BANK_SYSTEM_API.getServerBankManager().getSync();
        if (bankManager == null) return fail("ServerBankManager unavailable");

        StateSnapshot snapshot = snapshotState();
        try {
            ItemID[] pair = registerRepairCostPair();
            if (!pair[0].isValid() || !pair[1].isValid() || pair[0].equals(pair[1]))
                return fail("failed to register the two synthetic paper templates as distinct IDs");
            ItemID canonical = lowerShort(pair[0], pair[1]);
            ItemID alias = higherShort(pair[0], pair[1]);

            int accountNr = ServerBankAccount.INVALID_ACCOUNT_NUMBER;
            try {
                if (!bankManager.allowItemID(canonical) || !bankManager.allowItemID(alias))
                    return fail("could not allow the synthetic paper templates for banking");

                IServerBankAccount account = bankManager.createBankAccount("MarketMergeRefundTest");
                if (account == null) return fail("failed to create the test bank account");
                accountNr = account.getAccountNumber();

                // Give the account 500 items under BOTH the alias and canonical, lock 200 on alias —
                // simulates a player limit-sell order sitting on the alias-keyed market.
                IServerBank canonicalBank = account.createBank(canonical, 500);
                IServerBank aliasBank = account.createBank(alias, 500);
                if (canonicalBank == null || aliasBank == null)
                    return fail("failed to create the two per-item banks");
                if (aliasBank.lockAmount(200) != BankStatus.SUCCESS)
                    return fail("failed to lock 200 items on the alias bank");
                TestResult r = assertEquals("precondition: 200 items locked on alias bank",
                        200L, aliasBank.getLockedBalance());
                if (!r.passed()) return r;

                // Both markets exist as separate entries.
                IServerMarket canonicalMarket = smm.createMarket(canonical);
                IServerMarket aliasMarket = smm.createMarket(alias);
                if (canonicalMarket == null || aliasMarket == null)
                    return fail("failed to create the two test markets");

                // Feed alias-keyed market a player limit sell order that references the
                // locked amount. It goes straight into the orderbook (bypassing the input
                // buffer / matching engine) so cancelAllPlayerOrders sees it during close.
                UUID playerUUID = UUID.randomUUID();
                net.kroia.stockmarket.stockmarket.market.core.order.Order sellOrder =
                        new net.kroia.stockmarket.stockmarket.market.core.order.Order(
                                alias,
                                net.kroia.stockmarket.stockmarket.market.core.order.Order.Type.LIMIT,
                                -200, 100, 0, playerUUID, accountNr);
                ((ServerMarket) aliasMarket).getOrderbook().putOrder(sellOrder);

                // Trigger reconciliation — alias market is deleted, its player order canceled,
                // its locked funds unlocked via the standard deleteMarket path.
                smm.consolidateMergedMarkets(Map.of(alias, canonical));

                r = assertFalse("alias-keyed market deleted", smm.marketExists(alias));
                if (!r.passed()) return r;
                // BankSystem's getBank(alias) resolves through the alias table if a merge
                // happened, but here we bypassed the merge and called consolidate directly —
                // the alias bank still lives under its original key. Read it back through
                // the account directly to avoid alias-resolution surprises.
                r = assertEquals("locked balance on alias bank refunded to 0",
                        0L, aliasBank.getLockedBalance());
                if (!r.passed()) return r;
                return pass("deleteMarket path refunded the locked funds on the deleted duplicate");
            } finally {
                if (accountNr != ServerBankAccount.INVALID_ACCOUNT_NUMBER)
                    bankManager.deleteBankAccount(accountNr);
                // NO disallowItemID here: it is not the inverse of allowItemID. It delegates
                // to disallowItemIDAndReport, which runtime-BLACKLISTS the ID (and clears the
                // item's bank from every holder account without refunding). The blacklist rows
                // then outlived the registry restore below and showed up as stray "air" entries
                // in the manage screen. restoreState() puts both filter sets back verbatim.
                smm.deleteMarket(canonical);
                smm.deleteMarket(alias);
            }
        } finally {
            restoreState(snapshot);
        }
    }
}
