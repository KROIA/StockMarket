package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.MoneyFormat;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.minecraft.compat.StockMarketPlaceholders;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ClientMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.MultiServerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * T-237: a slave hides markets for items it cannot resolve (items whose mod only the master has).
 * <p>
 * {@link MultiServerUtils#setUnresolvableForTesting} makes a real item of this server's registry look
 * missing, so no second mod loadout is needed. Items are only looked up, never registered (an untrusted
 * slave may not register items). The fake-transport tests run on both sides, the filters are the same code
 * there; the live market list test runs on a slave and the master test on the master. T-240 adds the
 * portfolio placeholder, which a slave sums without those markets.
 */
public class MarketVisibilityTestSuite extends TestSuite {

    /** Wait for a request to the master. */
    private static final long WAIT_MS = 10_000;

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    /**
     * @param backend the instances of the running server, master or slave
     */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_VISIBILITY;
    }

    @Override
    public void registerTests() {
        addTest("async_manager_hides_unresolvable_market", this::test_asyncManagerHidesUnresolvableMarket);
        addTest("client_manager_hides_unresolvable_market_and_pair", this::test_clientManagerHidesUnresolvableMarketAndPair);
        addTest("put_order_refused_without_forwarding", this::test_putOrderRefusedWithoutForwarding);
        addTest("slave_list_hides_unresolvable_master_market", this::test_slaveListHidesUnresolvableMasterMarket);
        addTest("master_still_lists_unresolvable_market", this::test_masterStillListsUnresolvableMarket);
        addTest("slave_portfolio_excludes_unresolvable_market", this::test_slavePortfolioExcludesUnresolvableMarket);
    }

    /**
     * @return two items this server resolves, from the live registry (lookup only); null if it has fewer
     */
    private static @Nullable List<ItemID> twoResolvableItems() {
        List<ItemID> items = ItemIDManager.getItemIDMap().keySet().stream()
                .filter(MultiServerUtils::isMarketResolvableOnThisServer)
                .limit(2)
                .toList();
        return items.size() == 2 ? items : null;
    }

    /** The slave's market list (commands, tab completion) leaves out the hidden market and keeps the other one. */
    private TestResult test_asyncManagerHidesUnresolvableMarket() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AsyncMarketManager manager = AsyncMarketManager.createForTesting(input -> CompletableFuture.completedFuture(
                AsyncMarketManager.OutputData.of(input.function, List.of(shown, hidden))));
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            return assertEquals("only the resolvable market is listed", List.of(shown),
                    manager.getAvailableMarketIDsAsync().join());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /**
     * The client gets no market object for the hidden market, so every market list leaves it out and no
     * item-for-item pair with it exists. The resolvable market is still there.
     */
    private TestResult test_clientManagerHidesUnresolvableMarketAndPair() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AsyncMarketManager creating = AsyncMarketManager.createForTesting(input -> CompletableFuture.completedFuture(
                AsyncMarketManager.OutputData.of(input.function, true)));
        ClientMarketManager manager = ClientMarketManager.createForTesting(creating, () -> CompletableFuture.completedFuture(1));
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            manager.requestCreateMarket(shown).join();
            manager.requestCreateMarket(hidden).join();
            TestResult r = assertTrue("resolvable market listed", manager.getAvailableMarkets().contains(shown));
            if (!r.passed()) return r;
            r = assertFalse("unresolvable market not listed", manager.getAvailableMarkets().contains(hidden));
            if (!r.passed()) return r;
            r = assertNull("no market object for it", manager.getMarket(hidden));
            if (!r.passed()) return r;
            return assertNull("no item-for-item pair with it", manager.getCrossRateMarket(shown, hidden));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** An order for the hidden market is refused at once and never sent; the resolvable market still sends. */
    private TestResult test_putOrderRefusedWithoutForwarding() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AtomicInteger sends = new AtomicInteger();
        Function<AsyncMarket.InputData, CompletableFuture<AsyncMarket.OutputData>> transport = input -> {
            sends.incrementAndGet();
            return CompletableFuture.completedFuture(AsyncMarket.OutputData.of(input.function, true));
        };
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            CompletableFuture<Boolean> refused = AsyncMarket.createForTesting(hidden, transport).putOrderAsync(order(hidden));
            TestResult r = assertTrue("answered at once", refused.isDone());
            if (!r.passed()) return r;
            r = assertEquals("refused", false, refused.join());
            if (!r.passed()) return r;
            r = assertEquals("not sent", 0, sends.get());
            if (!r.passed()) return r;
            r = assertEquals("resolvable market still sends", true,
                    AsyncMarket.createForTesting(shown, transport).putOrderAsync(order(shown)).join());
            if (!r.passed()) return r;
            return assertEquals("sent once", 1, sends.get());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** Slave only: the master's market list, as this slave reads it, leaves out a market this slave cannot resolve. */
    private TestResult test_slaveListHidesUnresolvableMasterMarket() {
        if (BACKEND_INSTANCES == null || !BACKEND_INSTANCES.MARKET_MANAGER.isSlave())
            return skip("slave only");
        if (!MultiServerUtils.canInteractWithStockMarket())
            return skip("not connected to the master");
        IAsyncMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getAsync();
        List<ItemID> listed = await(manager.getAvailableMarketIDsAsync(), WAIT_MS);
        if (listed == null || listed.isEmpty())
            return skip("the master has no market this slave can resolve");
        ItemID hidden = listed.get(0);
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            return assertFalse("market for an item this slave cannot resolve not listed",
                    await(manager.getAvailableMarketIDsAsync(), WAIT_MS).contains(hidden));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** Master only: the master lists a market even if its item counts as unresolvable; nothing is hidden there. */
    private TestResult test_masterStillListsUnresolvableMarket() {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER.isSlave())
            return skip("master only");
        TestFixture fixture = new TestFixture("MarketVisibility");
        fixture.setBackend(BACKEND_INSTANCES);
        ItemID marketID = null;
        try {
            marketID = fixture.createMarket("hidden").getItemID();
            MultiServerUtils.setUnresolvableForTesting(marketID, true);
            TestResult r = assertTrue("MarketsRequest lists it",
                    BACKEND_INSTANCES.NETWORKING.MARKETS_REQUEST.handleOnMasterServer(0, "", null).join().contains(marketID));
            if (!r.passed()) return r;
            return assertTrue("the market manager lists it",
                    await(BACKEND_INSTANCES.MARKET_MANAGER.getAsync().getAvailableMarketIDsAsync(), WAIT_MS).contains(marketID));
        } finally {
            if (marketID != null)
                MultiServerUtils.setUnresolvableForTesting(marketID, false);
            fixture.cleanup();
        }
    }

    /**
     * T-240: the portfolio placeholder the master computes keeps every market; the slave's copy sums it again
     * without the market it cannot resolve, and keeps the master's values when every market resolves.
     */
    private TestResult test_slavePortfolioExcludesUnresolvableMarket() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        Map<String, String> master = StockMarketPlaceholders.playerValueMap(0, Map.of(shown, 300L, hidden, 700L));
        TestResult r = assertTrue("all markets resolve: master values kept",
                StockMarketPlaceholders.withoutUnresolvableMarkets(master) == master);
        if (!r.passed()) return r;
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            r = assertEquals("master keeps both markets", MoneyFormat.formatMoneyRaw(1000), master.get("portfolio_raw"));
            if (!r.passed()) return r;
            Map<String, String> slave = StockMarketPlaceholders.withoutUnresolvableMarkets(master);
            r = assertEquals("slave raw value without the hidden market", MoneyFormat.formatMoneyRaw(300), slave.get("portfolio_raw"));
            if (!r.passed()) return r;
            return assertEquals("slave formatted value without the hidden market", MoneyFormat.formatMoney(300), slave.get("portfolio"));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** @return a market order for {@code marketID} */
    private static Order order(ItemID marketID) {
        return new Order(marketID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
    }
}
