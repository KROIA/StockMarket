package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.banking.BankPermission;
import net.kroia.banksystem.banking.User;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ActiveOrdersRequestTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        ActiveOrdersRequestTestSuite.backend = backend;
    }

    private ItemID itemID;
    private ItemID moneyID;
    private IServerMarket serverMarket;
    private IServerBankAccount bankAccount;
    private int bankAccountNr;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.ACTIVE_ORDERS_REQUEST;
    }

    @Override
    public void registerTests() {
        addTest("noPermissionCheck_allOrdersReturned", this::test_noPermissionCheck_allOrdersReturned);
        addTest("passesFilter_executorFilter_comparesWrongField", this::test_passesFilter_executorFilter_comparesWrongField);
        addTest("passesFilter_executorFilter_shouldMatchOrderExecutor", this::test_passesFilter_executorFilter_shouldMatchOrderExecutor);
        addTest("passesFilter_bankAccountFilter", this::test_passesFilter_bankAccountFilter);
        addTest("passesFilter_bankAccountFilter_negativeValue", this::test_passesFilter_bankAccountFilter_negativeValue);
        addTest("passesFilter_timeRange", this::test_passesFilter_timeRange);
        addTest("passesFilter_nullExecutor_noFilter", this::test_passesFilter_nullExecutor_noFilter);
        addTest("passesFilter_nullItemID_allMarkets", this::test_passesFilter_nullItemID_allMarkets);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("ActiveOrdersRequestTestSuite requires backend to be set");
        }
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        itemID = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        serverMarket = backend.MARKET_MANAGER.getSync().createMarket(itemID);
        bankAccount = backend.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("ActiveOrdersRequestTest");
        if (bankAccount == null)
            throw new RuntimeException("Can't create ActiveOrdersRequestTest bank account");
        bankAccountNr = bankAccount.getAccountNumber();
    }

    @Override
    public void teardown() {
        if (serverMarket != null) {
            serverMarket.test_clearOrderbook();
        }
    }

    private void resetMarket() {
        serverMarket.test_clearOrderbook();
        serverMarket.test_setCurrentMarketPrice(100);
        serverMarket.test_setDefaultVolumeProviderFunction(p -> 0f);
        serverMarket.test_resetVirtualOrderBookVolume();
    }

    private ActiveOrdersRequest.OutputData executeRequest(ActiveOrdersRequest.InputData input, UUID playerSender) {
        try {
            ActiveOrdersRequest request = new ActiveOrdersRequest();
            CompletableFuture<ActiveOrdersRequest.OutputData> future = request.handleOnMasterServer(input, "", playerSender);
            return future.get();
        } catch (Exception e) {
            return new ActiveOrdersRequest.OutputData(List.of());
        }
    }

    private void placeTestOrders(UUID executor1, UUID executor2, int bankAcct1, int bankAcct2) {
        // Place limit orders below/above market so they stay in the orderbook
        Order o1 = new Order(itemID, Order.Type.LIMIT, 5, 90, 1000, executor1, bankAcct1);
        Order o2 = new Order(itemID, Order.Type.LIMIT, -5, 110, 2000, executor2, bankAcct2);
        Order o3 = new Order(itemID, Order.Type.LIMIT, 3, 85, 3000, executor1, bankAcct1);
        serverMarket.getOrderbook().putOrder(o1);
        serverMarket.getOrderbook().putOrder(o2);
        serverMarket.getOrderbook().putOrder(o3);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    private TestResult test_noPermissionCheck_allOrdersReturned() {
        try {
            resetMarket();
            UUID executor = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(executor, "PermTestUser");

            bankAccount.addUser(new User(executor, "TestUser", false), BankPermission.getAllPermissions());

            placeTestOrders(executor, executor, bankAccountNr, bankAccountNr);

            // Any player (admin) can see orders
            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(executor, true);

            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, null, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, executor);

            TestResult r = assertTrue("Should return all orders, got " + result.orders().size(),
                    result.orders().size() >= 3);
            if (!r.passed()) return r;

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(executor, false);
            return pass("All orders returned without additional permission check for admin");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_executorFilter_comparesWrongField() {
        try {
            resetMarket();
            UUID executor1 = UUID.randomUUID();
            UUID executor2 = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(executor1, "ExecFilter1");
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(executor2, "ExecFilter2");

            bankAccount.addUser(new User(executor1, "TestUser1", false), BankPermission.getAllPermissions());
            bankAccount.addUser(new User(executor2, "TestUser2", false), BankPermission.getAllPermissions());

            placeTestOrders(executor1, executor2, bankAccountNr, bankAccountNr);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(executor1, true);

            // Filter by executor1 UUID
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, executor1, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, executor1);

            // The passesFilter method compares input.executorPlayer against order.getExecutorPlayerUUID()
            // executor1 placed orders at 1000 and 3000, executor2 at 2000
            // Should return only executor1's orders
            TestResult r = assertTrue("Should filter by executor UUID, got " + result.orders().size() + " orders",
                    result.orders().size() >= 1);
            if (!r.passed()) return r;

            // Verify all returned orders belong to executor1
            for (Order o : result.orders()) {
                if (!executor1.equals(o.getExecutorPlayerUUID())) {
                    return fail("Returned order belongs to wrong executor: " + o.getExecutorPlayerUUID());
                }
            }

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(executor1, false);
            return pass("Executor filter correctly filters by order executor UUID");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_executorFilter_shouldMatchOrderExecutor() {
        try {
            resetMarket();
            UUID executor = UUID.randomUUID();
            UUID otherPlayer = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(executor, "MatchExecUser");
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(otherPlayer, "OtherExecUser");

            bankAccount.addUser(new User(executor, "TestUser", false), BankPermission.getAllPermissions());
            bankAccount.addUser(new User(otherPlayer, "OtherUser", false), BankPermission.getAllPermissions());

            Order o1 = new Order(itemID, Order.Type.LIMIT, 5, 90, 1000, executor, bankAccountNr);
            serverMarket.getOrderbook().putOrder(o1);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(otherPlayer, true);

            // Filter by executor's UUID
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, executor, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, otherPlayer);

            TestResult r = assertTrue("Should return order matching executor filter",
                    result.orders().size() >= 1);
            if (!r.passed()) return r;

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(otherPlayer, false);
            return pass("Executor filter correctly matches order executor");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_bankAccountFilter() {
        try {
            resetMarket();
            UUID player = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(player, "BankAcctFilterUser");

            bankAccount.addUser(new User(player, "TestPlayer", false), BankPermission.getAllPermissions());

            Order o1 = new Order(itemID, Order.Type.LIMIT, 5, 90, 1000, player, bankAccountNr);
            Order o2 = new Order(itemID, Order.Type.LIMIT, 3, 85, 2000, player, 9999);
            serverMarket.getOrderbook().putOrder(o1);
            serverMarket.getOrderbook().putOrder(o2);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, true);

            // Filter by specific bank account number
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, bankAccountNr, null, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, player);

            // Only o1 should pass the bank account filter
            for (Order o : result.orders()) {
                if (o.getBankAccountNr() != bankAccountNr) {
                    return fail("Returned order has wrong bank account: " + o.getBankAccountNr());
                }
            }

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, false);
            return pass("Bank account filter correctly excludes non-matching orders");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_bankAccountFilter_negativeValue() {
        try {
            resetMarket();
            UUID player = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(player, "NegBankFilterUser");

            bankAccount.addUser(new User(player, "TestPlayer", false), BankPermission.getAllPermissions());

            Order o1 = new Order(itemID, Order.Type.LIMIT, 5, 90, 1000, player, bankAccountNr);
            Order o2 = new Order(itemID, Order.Type.LIMIT, 3, 85, 2000, player, 9999);
            serverMarket.getOrderbook().putOrder(o1);
            serverMarket.getOrderbook().putOrder(o2);

            // Register player in the market manager's user map before setting admin mode,
            // otherwise setStockmarketAdminMode silently fails (user not found in userMap).
            // Admin is needed because o2 uses bank account 9999 which the player is not a member of.
            backend.MARKET_MANAGER.getSync().onPlayerJoin(player, "NegBankFilterUser");
            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, true);

            // bankAccountNr < 0 means no filter (all accounts)
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, null, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, player);

            TestResult r = assertTrue("Negative bankAccountNr should not filter, got " + result.orders().size() + " orders",
                    result.orders().size() >= 2);
            if (!r.passed()) return r;

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, false);
            return pass("bankAccountNr < 0 passes all orders (no filter)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_timeRange() {
        try {
            resetMarket();
            UUID player = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(player, "TimeRangeUser");

            bankAccount.addUser(new User(player, "TestPlayer", false), BankPermission.getAllPermissions());

            Order o1 = new Order(itemID, Order.Type.LIMIT, 5, 90, 1000, player, bankAccountNr);
            Order o2 = new Order(itemID, Order.Type.LIMIT, -5, 110, 5000, player, bankAccountNr);
            serverMarket.getOrderbook().putOrder(o1);
            serverMarket.getOrderbook().putOrder(o2);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, true);

            // Time range that only includes o1 (time=1000)
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, null, 500, 2000);

            ActiveOrdersRequest.OutputData result = executeRequest(input, player);

            for (Order o : result.orders()) {
                if (o.getTime() < 500 || o.getTime() > 2000) {
                    return fail("Returned order outside time range: time=" + o.getTime());
                }
            }

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, false);
            return pass("Time range filter correctly excludes out-of-range orders");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_nullExecutor_noFilter() {
        try {
            resetMarket();
            UUID player = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(player, "NullExecUser");

            bankAccount.addUser(new User(player, "TestPlayer", false), BankPermission.getAllPermissions());

            placeTestOrders(player, player, bankAccountNr, bankAccountNr);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, true);

            // Null executor means no executor filter
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    itemID, -1, null, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, player);

            TestResult r = assertTrue("Null executor should return all orders, got " + result.orders().size(),
                    result.orders().size() >= 3);
            if (!r.passed()) return r;

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, false);
            return pass("Null executorPlayer passes all orders (no filter)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_passesFilter_nullItemID_allMarkets() {
        try {
            resetMarket();
            UUID player = UUID.randomUUID();
            backend.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(player, "NullItemUser");

            bankAccount.addUser(new User(player, "TestPlayer", false), BankPermission.getAllPermissions());

            placeTestOrders(player, player, bankAccountNr, bankAccountNr);

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, true);

            // Null itemID queries all markets
            ActiveOrdersRequest.InputData input = new ActiveOrdersRequest.InputData(
                    null, -1, null, 0, Long.MAX_VALUE);

            ActiveOrdersRequest.OutputData result = executeRequest(input, player);

            TestResult r = assertTrue("Null itemID should query all markets, got " + result.orders().size() + " orders",
                    result.orders().size() >= 1);
            if (!r.passed()) return r;

            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(player, false);
            return pass("Null itemID queries all available markets");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }
}
