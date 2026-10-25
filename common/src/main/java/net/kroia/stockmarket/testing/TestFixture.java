package net.kroia.stockmarket.testing;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
import net.kroia.banksystem.banking.bankmanager.ServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.stockmarket.api.pluginmanager.ISyncServerPluginManager;
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
 * Scratch-resource allocator shared by the in-game test suites.
 * <p>
 * The in-game suites run against the player's live world. Anything they create
 * through the normal APIs — markets, bank accounts, bank users, plugin caches —
 * would otherwise survive the test run and pollute that world. Worse,
 * {@code createMarket(ItemID)} is get-or-create, so a suite that asks for a
 * vanilla item's market gets the player's <i>real</i> market handed back and
 * then corrupts it.
 * <p>
 * A fixture therefore hands out resources that provably do not exist in the
 * live world (markets are keyed on a synthetic, test-only {@link ItemID}; bank
 * accounts and users are freshly created, never looked up by a fixed number)
 * and records every one of them. {@link #cleanup()} drains the recorded
 * resources in dependency order, so a suite that allocates in {@code setup()}
 * and drains in {@code teardown()} leaves the world as it found it.
 * <p>
 * Registering a synthetic item mints a new ItemID, and BankSystem's ItemID table
 * has no deregister — a naive fixture would therefore leave one stray "Paper" row
 * in the bank manage screen per scratch item, forever. So the fixture also
 * snapshots the whole ItemID registry in {@link #setBackend} and reinstalls it as
 * the very last step of {@link #cleanup()}, discarding every ID minted in between.
 * This mirrors {@code MarketMergeConsolidationTestSuite} and BankSystem's own
 * {@code ItemIDMergeGuardTests}; persisted residue is zero, not merely stable.
 * <p>
 * Usage:
 * <pre>
 * private final TestFixture fixture = new TestFixture("MySuite");
 *
 * public void setup() {
 *     fixture.setBackend(backend);
 *     serverMarket = fixture.createMarket("main");
 *     account       = fixture.createBankAccount("trader");
 * }
 *
 * public void teardown() { fixture.cleanup(); }
 * </pre>
 */
public class TestFixture {

    /** Custom-data key marking a synthetic fixture item, so it can never collide with a real player item. */
    private static final String MARKER_KEY = "stockmarket_test_fixture";

    private final String label;
    private StockMarketModBackend.ServerInstances backend;

    private final List<ItemID> markets = new ArrayList<>();
    private final List<ItemID> pluginCaches = new ArrayList<>();
    private final List<Integer> accountNumbers = new ArrayList<>();
    private final List<UUID> userUUIDs = new ArrayList<>();

    /** Registry state as of {@link #setBackend}; null once restored, which makes cleanup idempotent. */
    private RegistrySnapshot registrySnapshot;

    /**
     * Allowed-set + runtime-blacklist state as of {@link #setBackend}; null once restored.
     * <p>
     * Needed because {@code createMarket} allow-lists its item, and the obvious undo —
     * {@code disallowItemID} — is <b>not</b> an undo: it removes the ID from the allowed set
     * but then adds it to the runtime blacklist (and wipes holder banks with no refund). A
     * blacklisted scratch ID makes the next run's {@code createMarket} fail outright. Only a
     * snapshot/restore pair can reach "neither allowed nor blacklisted"; BankSystem's own
     * {@code snapshotItemFilters} javadoc says exactly this.
     */
    private ServerBankManager.ItemFilterSnapshot filterSnapshot;

    /**
     * Copy of BankSystem's ItemID registry, taken before any synthetic ID is minted.
     * Same shape as the snapshot in {@code MarketMergeConsolidationTestSuite}.
     *
     * @param items       copy of the ItemID → template map
     * @param aliases     copy of the alias → canonical map
     * @param quarantined copy of the quarantined-alias map
     * @param counter     value of the short minting counter
     */
    private record RegistrySnapshot(Map<ItemID, ItemStack> items,
                                    Map<ItemID, ItemID> aliases,
                                    Map<ItemID, ItemID> quarantined,
                                    int counter) {}

    /**
     * @param label short suite identifier; becomes part of the synthetic item marker,
     *              so two suites never share a scratch market.
     */
    public TestFixture(String label) {
        this.label = label;
    }

    /**
     * Binds the fixture to the master backend and snapshots the ItemID registry.
     * Must be called from the suite's {@code setup()} before any allocation — the
     * snapshot has to predate the first {@link #scratchItemID(String)} for the
     * restore in {@link #cleanup()} to discard it.
     * <p>
     * Deliberately not done in the constructor: suites hold their fixture in a field
     * initializer, which runs at suite construction, potentially long before the
     * server's registry is loaded. Restoring such a snapshot would wipe the real
     * registry rather than the scratch IDs.
     *
     * @param backend the master server instances (never null)
     */
    public void setBackend(StockMarketModBackend.ServerInstances backend) {
        this.backend = backend;
        if (registrySnapshot == null) {
            registrySnapshot = new RegistrySnapshot(
                    new HashMap<>(ItemIDManager.getItemIDMap()),
                    new HashMap<>(ItemIDManager.getItemIDAliasMap()),
                    ItemIDManager.getQuarantinedAliases_forTesting(),
                    ItemIDManager.getNextShortCounter_forTesting());
        }
        if (filterSnapshot == null) {
            ServerBankManager bankManager = concreteBankManager();
            if (bankManager != null)
                filterSnapshot = bankManager.snapshotItemFilters();
        }
    }

    /**
     * Registers (or re-resolves) a synthetic {@link ItemID} that exists only for
     * tests: a paper stack carrying a {@code minecraft:custom_data} marker built
     * from this fixture's label and {@code name}. No player can ever hold such a
     * stack, so no live market, bank, or order can already be keyed on it.
     * <p>
     * The minted ID is temporary: {@link #cleanup()} reinstalls the registry
     * snapshot taken in {@link #setBackend}, dropping it again.
     * <p>
     * The marker is deterministic, so an earlier run that was killed before its
     * teardown leaves residue under this very ID. {@link #purgeResidue(ItemID)}
     * therefore scrubs it on the way out, making the fixture self-healing on a
     * world that is already dirty.
     *
     * @param name resource name, unique within the suite (e.g. {@code "itemA"})
     * @return the synthetic ItemID (never null)
     */
    public ItemID scratchItemID(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag nbt = new CompoundTag();
        nbt.putString(MARKER_KEY, label + "/" + name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        ItemID itemID = ItemIDManager.registerItemStackServerSide_direct(stack);
        purgeResidue(itemID);
        return itemID;
    }

    /**
     * Removes every trace of {@code itemID} left by an earlier aborted run, so this
     * run can use it and the restore at the end of {@link #cleanup()} does not put
     * the residue back.
     * <p>
     * A scratch ID is synthetic and belongs to this fixture alone, so it can never
     * legitimately appear in the player's filters or need to survive in the registry.
     * That makes all three scrubs unconditionally safe:
     * <ul>
     *   <li>drop it from the live runtime blacklist — otherwise {@code createMarket}
     *       refuses it and {@code setup()} throws (which, since the test runner skips
     *       {@code teardown()} on setup failure, would strand everything else too);</li>
     *   <li>drop it from both snapshot sets, so the filter restore does not reinstate
     *       a stale allowed/blacklisted entry;</li>
     *   <li>drop it from the snapshot registry, so the registry restore discards the
     *       stale ItemID instead of preserving it — this is what clears the leftover
     *       "Paper" rows from the bank manage screen.</li>
     * </ul>
     *
     * @param itemID the synthetic ID just minted or re-resolved
     */
    private void purgeResidue(ItemID itemID) {
        if (filterSnapshot != null) {
            filterSnapshot.allowed().remove(itemID);
            filterSnapshot.runtimeBlacklist().remove(itemID);
        }
        if (registrySnapshot != null)
            registrySnapshot.items().remove(itemID);
        try {
            ISyncServerBankManager bankManager = bankManager();
            if (bankManager != null && bankManager.isItemIDBlacklisted(itemID)) {
                // allowItemID() clears the runtime-blacklist entry (the disallow is
                // documented as reversible that way); the allowed-set side effect is
                // undone by the filter restore in cleanup().
                bankManager.allowItemID(itemID);
            }
        } catch (Exception e) {
            logFailure("stale blacklist entry for " + itemID, e);
        }
    }

    /**
     * Allocates a scratch market on a synthetic ItemID and records it for cleanup.
     *
     * @param name market name, unique within the suite
     * @return the created market
     * @throws RuntimeException if the market could not be created
     */
    public IServerMarket createMarket(String name) {
        ItemID itemID = scratchItemID(name);
        IServerMarket market = marketManager().createMarket(itemID);
        if (market == null)
            throw new RuntimeException("TestFixture[" + label + "]: could not create scratch market '" + name + "'");
        markets.add(itemID);
        return market;
    }

    /**
     * Allocates a brand-new bank account and records it for cleanup. Never reuses
     * a fixed account number, so it can never squat on a real player's account.
     *
     * @param name account name (a run-unique suffix is appended)
     * @return the created account
     * @throws RuntimeException if the account could not be created
     */
    public IServerBankAccount createBankAccount(String name) {
        IServerBankAccount account = bankManager().createBankAccount(label + "_" + name + "_" + System.nanoTime());
        if (account == null)
            throw new RuntimeException("TestFixture[" + label + "]: could not create scratch bank account '" + name + "'");
        accountNumbers.add(account.getAccountNumber());
        return account;
    }

    /**
     * Registers a fresh random bank user and records it for cleanup.
     *
     * @param name display name for the user
     * @return the new user's UUID
     */
    public UUID createUser(String name) {
        UUID uuid = UUID.randomUUID();
        bankManager().addUser(uuid, label + "_" + name);
        userUUIDs.add(uuid);
        return uuid;
    }

    /**
     * Records a bank user that was registered outside {@link #createUser(String)}
     * so it is removed in {@link #cleanup()}.
     *
     * @param userUUID the UUID that was passed to {@code addUser}
     */
    public void registerUser(UUID userUUID) {
        userUUIDs.add(userUUID);
    }

    /**
     * Records a plugin cache created outside {@link #createMarket(String)} so it is
     * dropped in {@link #cleanup()}. Caches created implicitly by a scratch market's
     * plugin auto-subscribe are already covered by the market's deletion.
     *
     * @param marketID the market the cache belongs to
     */
    public void registerPluginCache(ItemID marketID) {
        pluginCaches.add(marketID);
    }

    /**
     * Drains every recorded resource. Idempotent (a second call is a no-op) and
     * never throws — each resource is released in its own try/catch so one failure
     * cannot abort the rest of the drain.
     * <p>
     * Order is dependency order, not allocation order: plugin caches, then markets
     * (deleting a market cancels its orders and refunds locked funds into the bank
     * accounts, so it must happen before they are deleted), then bank accounts,
     * then bank users. The ItemID registry is reinstalled last of all, because the
     * drain above still needs the scratch IDs to resolve while it runs.
     */
    public void cleanup() {
        for (int i = pluginCaches.size() - 1; i >= 0; i--) {
            ItemID id = pluginCaches.get(i);
            try {
                ISyncServerPluginManager pluginManager = pluginManager();
                if (pluginManager != null) pluginManager.removeCache(id);
            } catch (Exception e) {
                logFailure("plugin cache " + id, e);
            }
        }
        pluginCaches.clear();

        for (int i = markets.size() - 1; i >= 0; i--) {
            ItemID id = markets.get(i);
            try {
                marketManager().deleteMarket(id);
            } catch (Exception e) {
                logFailure("market " + id, e);
            }
            // NOTE: no disallowItemID() here. createMarket() allow-listed the synthetic item,
            // but disallowItemID is not its inverse — it runtime-blacklists the ID (and wipes
            // holder banks without refund). The allow-list entry is reverted wholesale by the
            // filter restore at the end of this method instead.
        }
        markets.clear();

        for (int i = accountNumbers.size() - 1; i >= 0; i--) {
            int accountNr = accountNumbers.get(i);
            try {
                bankManager().deleteBankAccount(accountNr);
            } catch (Exception e) {
                logFailure("bank account " + accountNr, e);
            }
        }
        accountNumbers.clear();

        for (int i = userUUIDs.size() - 1; i >= 0; i--) {
            UUID uuid = userUUIDs.get(i);
            try {
                bankManager().removeUser(uuid);
            } catch (Exception e) {
                logFailure("bank user " + uuid, e);
            }
        }
        userUUIDs.clear();

        // Second to last: put the allowed set + runtime blacklist back exactly as found,
        // reverting createMarket()'s allow-list entries without blacklisting anything.
        // Done before the registry restore so no filter entry can be left pointing at an
        // ItemID the registry restore is about to drop (an unresolvable filter entry renders
        // as a stray "air" row in the manage screen).
        ServerBankManager.ItemFilterSnapshot filters = filterSnapshot;
        filterSnapshot = null;
        if (filters != null) {
            try {
                ServerBankManager bankManager = concreteBankManager();
                if (bankManager != null) bankManager.restoreItemFilters(filters);
            } catch (Throwable t) {
                StockMarketMod.LOGGER.error("TestFixture[{}]: failed to restore the item filters: {}", label, t.toString());
            }
        }

        // Last: discard every ItemID minted for this fixture's scratch items, so no
        // stray "Paper" row survives in the BankSystem manage screen. Nulled first so
        // a second cleanup() can never reinstall a now-stale registry over a later
        // suite's IDs.
        RegistrySnapshot snapshot = registrySnapshot;
        registrySnapshot = null;
        if (snapshot != null) {
            try {
                ItemIDManager.replaceState_forTesting(snapshot.items(), snapshot.aliases(), snapshot.counter());
                ItemIDManager.restoreQuarantinedAliases_forTesting(snapshot.quarantined());
            } catch (Throwable t) {
                StockMarketMod.LOGGER.error("TestFixture[{}]: failed to restore the ItemID registry: {}", label, t.toString());
            }
        }
    }

    private void logFailure(String what, Exception e) {
        StockMarketMod.LOGGER.error("TestFixture[{}]: failed to clean up {}: {}", label, what, e.toString());
    }

    private ISyncServerMarketManager marketManager() {
        return backend.MARKET_MANAGER.getSync();
    }

    private ISyncServerBankManager bankManager() {
        return backend.BANK_SYSTEM_API.getServerBankManager().getSync();
    }

    /**
     * The concrete master bank manager, or null if unavailable / not the master impl.
     * {@code snapshotItemFilters} and {@code restoreItemFilters} are test-support methods
     * on the implementation, not on {@code ISyncServerBankManager}.
     */
    private ServerBankManager concreteBankManager() {
        ISyncServerBankManager bankManager = bankManager();
        return bankManager instanceof ServerBankManager concrete ? concrete : null;
    }

    private ISyncServerPluginManager pluginManager() {
        return backend.PLUGIN_MANAGER == null ? null : backend.PLUGIN_MANAGER.getSync();
    }
}
