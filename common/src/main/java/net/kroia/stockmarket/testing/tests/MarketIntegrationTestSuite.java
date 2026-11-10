package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;

import java.util.UUID;

public class MarketIntegrationTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    /** Owns every scratch market/account this suite creates; drained in {@link #teardown()}. */
    private final TestFixture fixture = new TestFixture("MarketIntegration");

    private ItemID moneyID;
    private IServerMarket serverMarket;
    private int bankAccountNr1;
    private IServerBankAccount bankAccount1;
    private int bankAccountNr2;
    private IServerBankAccount bankAccount2;
    private long uniformVolumeDistributionScale = 5;
    private int scaleFactor;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.INTEGRATION;
    }

    @Override
    public void registerTests() {
        addTest("market_order_sell_against_virtual", this::marketOrderTest_1);
        addTest("market_order_buy_against_virtual", this::marketOrderTest_2);
        addTest("market_order_buy_partial_against_limit", this::marketOrderTest_3);
        addTest("market_order_buy_full_against_limit", this::marketOrderTest_4);
        addTest("market_order_buy_overfill_against_limit", this::marketOrderTest_5);
        addTest("limit_order_buy_filled_by_virtual", this::limitOrderTest_1);
        addTest("limit_order_buy_partial_by_virtual", this::limitOrderTest_2);
        addTest("limit_order_sell_filled_by_virtual", this::limitOrderTest_3);
        addTest("limit_order_sell_partial_by_virtual", this::limitOrderTest_4);
        addTest("mixed_limit_and_market_order", this::mixedOrderTest_1);
        addTest("create_market_non_admin_refused", this::test_createMarket_nonAdminRefused);
        addTest("delete_market_non_admin_refused", this::test_deleteMarket_nonAdminRefused);
        addTest("get_preferences_other_player_refused", this::test_getPreferences_otherPlayerRefused);
        addTest("update_preferences_other_player_refused", this::test_updatePreferences_otherPlayerRefused);
        addTest("slave_forwarded_legit_calls_allowed", this::test_slaveForwarded_legitCallsAllowed);
        addTest("untrusted_slave_market_settings_refused", this::test_untrustedSlave_marketSettingsRefused);
        addTest("is_admin_query_refused", this::test_isAdminQuery_refused);
    }

    @Override
    public void setup() {
        if (BACKEND_INSTANCES == null) {
            throw new RuntimeException("MarketIntegrationTestSuite requires BACKEND_INSTANCES to be set");
        }

        fixture.setBackend(BACKEND_INSTANCES);
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());

        // Scratch accounts — never fixed account numbers, which would squat on real players' accounts.
        bankAccount1 = fixture.createBankAccount("account1");
        bankAccountNr1 = bankAccount1.getAccountNumber();
        bankAccount2 = fixture.createBankAccount("account2");
        bankAccountNr2 = bankAccount2.getAccountNumber();

        // Scratch market on a synthetic ItemID — never the player's live gold market.
        serverMarket = fixture.createMarket("main");
        ItemID id = serverMarket.getItemID();
        scaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();

        bankAccount1.createBank(id, 100 * scaleFactor);
        bankAccount2.createBank(id, 100 * scaleFactor);
        bankAccount1.createBank(moneyID, 1000);
        bankAccount2.createBank(moneyID, 1000);
    }

    @Override
    public void teardown() {
        // Clear any order left in the matching engine before the market is dropped.
        if (serverMarket != null) {
            serverMarket.test_clearOrderbook();
            serverMarket.test_clearIncomingOrderBuffers();
        }
        serverMarket = null;
        fixture.cleanup();
    }

    private void resetMarketState(boolean useUniformVolume, long price) {
        if (useUniformVolume) {
            serverMarket.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
            uniformVolumeDistributionScale = 5;
        } else {
            serverMarket.test_setDefaultVolumeProviderFunction(this::emptyVolumeDistribution);
        }
        serverMarket.test_setCurrentMarketPrice(price);
        serverMarket.test_clearOrderbook();
        ItemID item = serverMarket.getItemID();

        bankAccount1.getBank(item).setBalance(100 * scaleFactor);
        bankAccount2.getBank(item).setBalance(100 * scaleFactor);
        bankAccount1.getBank(moneyID).setBalance(1000);
        bankAccount2.getBank(moneyID).setBalance(1000);
    }

    private TestResult marketOrderTest_1() {
        resetMarketState(true, 10);

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, -10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != 95)
            return fail("5x10$ + 5x9$ should be 95$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 90 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 9)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should still be filled after extra updates");
        if (o1.getTransferredMoney() != 95)
            return fail("TransferredMoney changed after extra updates: " + o1.getTransferredMoney());

        return pass("Market sell order against virtual orderbook fills correctly");
    }

    private TestResult marketOrderTest_2() {
        resetMarketState(true, 10);

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -115)
            return fail("-5x11$ - 5x12$ should be -115$ but was: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Order should still be filled after extra updates");
        if (o1.getTransferredMoney() != -115)
            return fail("TransferredMoney changed after extra updates: " + o1.getTransferredMoney());

        return pass("Market buy order against virtual orderbook fills correctly");
    }

    private TestResult marketOrderTest_3() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 5 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 105 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (limitOrder.getFilledVolume() + o1.getFilledVolume() != 0)
            return fail("Filled volumes should cancel out");
        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled (only partially matched)");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should still be filled after extra updates");

        return pass("Market buy order partially matches against limit sell order");
    }

    private TestResult marketOrderTest_4() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should be filled. Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (limitOrder.getFilledVolume() + o1.getFilledVolume() != 0)
            return fail("Filled volumes should cancel out");
        if (!limitOrder.isFilled())
            return fail("Limit order should be filled");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!o1.isFilled())
            return fail("Market order should still be filled after extra updates");
        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Market buy order fully matches against limit sell order");
    }

    private TestResult marketOrderTest_5() {
        resetMarketState(false, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 12, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        Order o1 = new Order(serverMarket.getItemID(), Order.Type.MARKET, 15 * scaleFactor, serverMarket.getCurrentMarketPrice(), 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(o1);
        serverMarket.update();

        if (o1.isFilled())
            return fail("Market order should NOT be filled (not enough supply). Order: " + o1);
        if (o1.getTransferredMoney() != -Math.round((double)limitOrder.getStartPrice() * o1.getFilledVolume() / scaleFactor))
            return fail("Transferred money mismatch: " + o1.getTransferredMoney());

        long newItemBalance = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - o1.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());
        if (!limitOrder.isFilled())
            return fail("Limit order should be filled");

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (o1.isFilled())
            return fail("Market order should still NOT be filled after extra updates");
        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Market buy order with larger volume than available limit supply is partially filled");
    }

    private TestResult limitOrderTest_1() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 10 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != -(11 * 5 + 12 * 5))
            return fail("Expected " + -(11 * 5 + 12 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 110 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 12)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Limit buy order filled by virtual orderbook");
    }

    private TestResult limitOrderTest_2() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 30 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != -(11 * 5 + 12 * 5 + 13 * 5))
            return fail("Expected " + -(11 * 5 + 12 * 5 + 13 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 115 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 13)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should still NOT be filled after extra updates");

        return pass("Limit buy order partially filled by virtual orderbook, remainder placed in orderbook");
    }

    private TestResult limitOrderTest_3() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -10 * scaleFactor, 7, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != (10 * 5 + 9 * 5))
            return fail("Expected " + (10 * 5 + 9 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 90 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 9)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");

        return pass("Limit sell order filled by virtual orderbook");
    }

    private TestResult limitOrderTest_4() {
        resetMarketState(true, 10);

        Order limitOrder = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -30 * scaleFactor, 7, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should NOT be filled. Order: " + limitOrder);
        if (limitOrder.getTransferredMoney() != (10 * 5 + 9 * 5 + 8 * 5 + 7 * 5))
            return fail("Expected " + (10 * 5 + 9 * 5 + 8 * 5 + 7 * 5) + "$ but was: " + limitOrder.getTransferredMoney());

        long newItemBalance = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance = bankAccount1.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money amount transferred: " + newMoneyBalance);
        if (newItemBalance != 80 * scaleFactor)
            return fail("Wrong end balance of items: " + newItemBalance);
        if (serverMarket.getCurrentMarketPrice() != 7)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (limitOrder.isFilled())
            return fail("Limit order should still NOT be filled after extra updates");

        return pass("Limit sell order partially filled by virtual orderbook, remainder placed in orderbook");
    }

    private TestResult mixedOrderTest_1() {
        resetMarketState(false, 10);

        ItemID item = serverMarket.getItemID();
        Order limitOrder = new Order(item, Order.Type.LIMIT, -10 * scaleFactor, 13, 0, UUID.randomUUID(), bankAccountNr1);
        serverMarket.putOrder(limitOrder);

        Order marketOrder = new Order(item, Order.Type.MARKET, 10 * scaleFactor, 10, 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(marketOrder);
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should be filled. Order: " + limitOrder);
        if (!marketOrder.isFilled())
            return fail("Market order should be filled. Order: " + marketOrder);
        if (limitOrder.getTransferredMoney() != (10 * 13))
            return fail("Limit transferred money should be " + (10 * 13) + "$ but was: " + limitOrder.getTransferredMoney());
        if (marketOrder.getTransferredMoney() != -(10 * 13))
            return fail("Market transferred money should be " + -(10 * 13) + "$ but was: " + marketOrder.getTransferredMoney());

        long newItemBalance1 = bankAccount1.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance1 = bankAccount1.getBank(moneyID).getTotalBalance();
        long newItemBalance2 = bankAccount2.getBank(serverMarket.getItemID()).getTotalBalance();
        long newMoneyBalance2 = bankAccount2.getBank(moneyID).getTotalBalance();

        if (newMoneyBalance1 - limitOrder.getTransferredMoney() != 1000)
            return fail("Wrong money transferred for limit order: " + newMoneyBalance1);
        if (newItemBalance1 != 90 * scaleFactor)
            return fail("Wrong item balance for limit order account: " + newItemBalance1);
        if (newMoneyBalance2 - marketOrder.getTransferredMoney() != 1000)
            return fail("Wrong money transferred for market order: " + newMoneyBalance2);
        if (newItemBalance2 != 110 * scaleFactor)
            return fail("Wrong item balance for market order account: " + newItemBalance2);
        if (serverMarket.getCurrentMarketPrice() != 13)
            return fail("Wrong market end price: " + serverMarket.getCurrentMarketPrice());

        serverMarket.update();
        serverMarket.update();
        serverMarket.update();

        if (!limitOrder.isFilled())
            return fail("Limit order should still be filled after extra updates");
        if (!marketOrder.isFilled())
            return fail("Market order should still be filled after extra updates");

        return pass("Mixed limit sell and market buy orders match correctly");
    }

    // ---- T-238: player rules of the market manager request (a modified client sends these directly) ----

    /**
     * A market manager whose calls run on this master's request handler as if {@code playerSender} sent them from
     * a client (null = server-internal call), through the real encode, handler and decode path.
     */
    private static AsyncMarketManager managerAs(UUID playerSender) {
        return managerAs("", playerSender);
    }

    /** Like {@link #managerAs(UUID)}, but as if slave {@code slaveID} forwarded the client request. */
    private static AsyncMarketManager managerAs(String slaveID, UUID playerSender) {
        return AsyncMarketManager.createForTesting(input ->
                AsyncMarketManager.Request.instance.handleOnMasterServer(input, slaveID, playerSender));
    }

    /**
     * Legit callers keep working through a trusted slave, which forwards a client request with the player's UUID:
     * an admin creates and deletes a market, a player reads and writes their own preferences.
     */
    private TestResult test_slaveForwarded_legitCallsAllowed() {
        ServerMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager();
        ISyncServerBankManager bank = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
        String slaveID = "t238_test_slave";
        boolean wasTrusted = bank.isSlaveServerTrusted(slaveID);
        UUID admin = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        bank.trustSlaveServer(slaveID);
        manager.onPlayerJoin(admin, "T238Admin");
        manager.setStockmarketAdminMode(admin, true);
        manager.onPlayerJoin(player, "T238Player");
        try {
            ItemID id = fixture.createMarket("t238_delete").getItemID(); // fixture deletes it if the test does not
            TestResult r = assertNotNull("admin create answered", managerAs(slaveID, admin).createMarketAsync(id).join());
            if (!r.passed()) return r;
            r = assertEquals("admin delete answered", Boolean.TRUE, managerAs(slaveID, admin).deleteMarketAsync(id).join());
            if (!r.passed()) return r;
            r = assertFalse("market deleted", manager.marketExists(id));
            if (!r.passed()) return r;
            r = assertNotNull("own preferences read", managerAs(slaveID, player).getPlayerPreferencesAsync(player).join());
            if (!r.passed()) return r;
            return assertEquals("own preferences written", Boolean.TRUE,
                    managerAs(slaveID, player).updatePlayerPreferencesAsync(player, new PlayerPreferences()).join());
        } finally {
            manager.removeUser(admin);
            manager.removeUser(player);
            if (!wasTrusted)
                bank.untrustSlaveServer(slaveID);
        }
    }

    /** A player without admin mode cannot create a market; a server-internal call still can. */
    private TestResult test_createMarket_nonAdminRefused() {
        ItemID id = serverMarket.getItemID(); // exists, so an allowed create changes nothing
        TestResult r = assertNull("non-admin player is refused", managerAs(UUID.randomUUID()).createMarketAsync(id).join());
        if (!r.passed()) return r;
        return assertNotNull("server-internal create still answers", managerAs(null).createMarketAsync(id).join());
    }

    /** A player without admin mode cannot delete a market; a server-internal call still can. */
    private TestResult test_deleteMarket_nonAdminRefused() {
        ItemID id = serverMarket.getItemID();
        TestResult r = assertNull("non-admin player is refused", managerAs(UUID.randomUUID()).deleteMarketAsync(id).join());
        if (!r.passed()) return r;
        r = assertTrue("market still exists", BACKEND_INSTANCES.MARKET_MANAGER.getSync().marketExists(id));
        if (!r.passed()) return r;
        // A missing market, so the allowed delete changes nothing.
        return assertEquals("server-internal delete still answers", Boolean.FALSE,
                managerAs(null).deleteMarketAsync(ItemID.INVALID_ID).join());
    }

    /** A player can read only their own preferences. */
    private TestResult test_getPreferences_otherPlayerRefused() {
        UUID player = UUID.randomUUID();
        TestResult r = assertNull("other player's preferences refused", managerAs(player).getPlayerPreferencesAsync(UUID.randomUUID()).join());
        if (!r.passed()) return r;
        return assertNotNull("own preferences answered", managerAs(player).getPlayerPreferencesAsync(player).join());
    }

    /** A player can write only their own preferences (unknown users, so an allowed write changes nothing). */
    private TestResult test_updatePreferences_otherPlayerRefused() {
        UUID player = UUID.randomUUID();
        TestResult r = assertNull("other player's preferences refused",
                managerAs(player).updatePlayerPreferencesAsync(UUID.randomUUID(), new PlayerPreferences()).join());
        if (!r.passed()) return r;
        return assertEquals("own preferences answered", Boolean.FALSE,
                managerAs(player).updatePlayerPreferencesAsync(player, new PlayerPreferences()).join());
    }

    // ---- T-239: an untrusted slave sets the forwarded player UUID itself ----

    /**
     * T-239: an untrusted slave cannot read a market's settings, even when it names an admin; the admin directly on
     * the master can. Settings are admin data, so GetSettings is no longer allowed for untrusted slaves.
     */
    private TestResult test_untrustedSlave_marketSettingsRefused() {
        ServerMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager();
        String slaveID = "t239_untrusted_slave";
        UUID admin = UUID.randomUUID();
        manager.onPlayerJoin(admin, "T239Admin");
        manager.setStockmarketAdminMode(admin, true);
        try {
            TestResult r = assertFalse("test slave is untrusted",
                    BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().isSlaveServerTrusted(slaveID));
            if (!r.passed()) return r;
            r = assertNotNull("admin reads settings on the master", marketAs("", admin).getSettingsAsync().join());
            if (!r.passed()) return r;
            return assertNull("settings refused through an untrusted slave", marketAs(slaveID, admin).getSettingsAsync().join());
        } finally {
            manager.removeUser(admin);
        }
    }

    /**
     * T-239: a player may ask only whether they themselves are a StockMarket admin, and an untrusted slave gets no
     * answer. A server-internal call (null sender, like the slave's /stockmarket manage check) still answers through
     * a trusted slave.
     */
    private TestResult test_isAdminQuery_refused() {
        ISyncServerBankManager bank = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
        String untrustedSlaveID = "t239_untrusted_slave";
        String trustedSlaveID = "t239_trusted_slave";
        boolean wasTrusted = bank.isSlaveServerTrusted(trustedSlaveID);
        UUID player = UUID.randomUUID();
        bank.trustSlaveServer(trustedSlaveID);
        try {
            TestResult r = assertNull("other player's admin flag refused",
                    managerAs(player).isStockmarketAdminAsync(UUID.randomUUID()).join());
            if (!r.passed()) return r;
            r = assertNotNull("own admin flag answered", managerAs(player).isStockmarketAdminAsync(player).join());
            if (!r.passed()) return r;
            r = assertFalse("test slave is untrusted", bank.isSlaveServerTrusted(untrustedSlaveID));
            if (!r.passed()) return r;
            r = assertNull("untrusted slave gets no answer",
                    managerAs(untrustedSlaveID, null).isStockmarketAdminAsync(player).join());
            if (!r.passed()) return r;
            return assertNotNull("server-internal call through a trusted slave answered",
                    managerAs(trustedSlaveID, null).isStockmarketAdminAsync(player).join());
        } finally {
            if (!wasTrusted)
                bank.untrustSlaveServer(trustedSlaveID);
        }
    }

    /**
     * A market whose calls run on this master's request handler as if slave {@code slaveID} ("" = master-local)
     * forwarded them from {@code playerSender}, through the real encode, handler and decode path.
     */
    private AsyncMarket marketAs(String slaveID, UUID playerSender) {
        return AsyncMarket.createForTesting(serverMarket.getItemID(), input ->
                AsyncMarket.Request.instance.handleOnMasterServer(input, slaveID, playerSender));
    }

    private float emptyVolumeDistribution(double price) {
        return 0;
    }

    private float uniformVolumeDistribution(double price) {
        return (float) uniformVolumeDistributionScale;
    }
}
