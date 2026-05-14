package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Tests the full inter-market execution pipeline using ServerMarketManager.putInterMarketOrder()
 * and ServerMarketManager.update() to process two-leg item-to-item trades.
 *
 * Each test creates two markets (A=gold, B=iron), places an InterMarketOrder that sells A-items
 * to buy B-items, and verifies bank balances and order fill state after processing.
 */
public class InterMarketExecutorTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private ItemID moneyID, itemA_ID, itemB_ID;
    private IServerMarket marketA, marketB;
    private ServerMarketManager marketManager;
    private IServerBankAccount bankAccount;
    private int bankAccountNr;
    private int scaleFactor;
    private UUID testPlayerUUID;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.INTER_MARKET_EXECUTOR;
    }

    @Override
    public void registerTests() {
        addTest("market_order_basic_exchange", this::test_market_order_basic_exchange);
        addTest("market_order_money_conservation", this::test_market_order_money_conservation);
        addTest("limit_order_favorable_executes", this::test_limit_order_favorable_executes);
        addTest("limit_order_unfavorable_skipped", this::test_limit_order_unfavorable_skipped);
        addTest("limit_order_becomes_favorable", this::test_limit_order_becomes_favorable);
        addTest("cancel_unlocks_items", this::test_cancel_unlocks_items);
        addTest("market_close_cancels_inter_market_orders", this::test_market_close_cancels_inter_market_orders);
        addTest("price_movement_after_execution", this::test_price_movement_after_execution);
    }

    @Override
    public void setup() {
        if (BACKEND_INSTANCES == null) {
            throw new RuntimeException("InterMarketExecutorTestSuite requires BACKEND_INSTANCES to be set");
        }

        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        itemA_ID = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        itemB_ID = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.IRON_INGOT.getDefaultInstance());

        marketA = BACKEND_INSTANCES.MARKET_MANAGER.getSync().createMarket(itemA_ID);
        marketB = BACKEND_INSTANCES.MARKET_MANAGER.getSync().createMarket(itemB_ID);
        marketManager = (ServerMarketManager) BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        scaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();

        // Create test player and bank account
        testPlayerUUID = UUID.randomUUID();
        BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().addUser(testPlayerUUID, "IMTTestPlayer");
        bankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("IMTTestAccount");
        bankAccountNr = bankAccount.getAccountNumber();
        bankAccount.createBank(itemA_ID, 100 * scaleFactor);
        bankAccount.createBank(itemB_ID, 100 * scaleFactor);
        bankAccount.createBank(moneyID, 100000);
    }

    @Override
    public void teardown() {
        // Restore markets to a clean state with uniform volume
        if (marketA != null) {
            marketA.test_setDefaultVolumeProviderFunction(p -> 5f);
            marketA.test_resetVirtualOrderBookVolume();
        }
        if (marketB != null) {
            marketB.test_setDefaultVolumeProviderFunction(p -> 5f);
            marketB.test_resetVirtualOrderBookVolume();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper: reset both markets and bank to a known state
    // ═══════════════════════════════════════════════════════════════════════════

    private void resetState(long priceA, long priceB) {
        marketA.test_setCurrentMarketPrice(priceA);
        marketA.test_clearOrderbook();
        marketA.test_setDefaultVolumeProviderFunction(p -> 5f);
        marketA.test_resetVirtualOrderBookVolume();
        marketA.setMarketOpen(true);

        marketB.test_setCurrentMarketPrice(priceB);
        marketB.test_clearOrderbook();
        marketB.test_setDefaultVolumeProviderFunction(p -> 5f);
        marketB.test_resetVirtualOrderBookVolume();
        marketB.setMarketOpen(true);

        bankAccount.getBank(itemA_ID).setBalance(100 * scaleFactor);
        bankAccount.getBank(itemB_ID).setBalance(100 * scaleFactor);
        bankAccount.getBank(moneyID).setBalance(100000);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper: create an InterMarketOrder (sell A-items to buy B-items)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an InterMarketOrder that sells sellVolume of A-items to buy B-items.
     * @param sellVolume volume of A-items to sell (in scaled units)
     * @param crossRateLimit 0 for market order, >0 for limit order (raw format)
     */
    private InterMarketOrder createIMO(long sellVolume, long crossRateLimit) {
        long havePrice = marketA.getCurrentMarketPrice();
        long wantPrice = marketB.getCurrentMarketPrice();
        // Estimate buy volume based on current prices
        long estimatedBuyVolume = (wantPrice > 0) ? (sellVolume * havePrice / wantPrice) : sellVolume;
        if (estimatedBuyVolume <= 0) estimatedBuyVolume = 1;

        Order.Type type = (crossRateLimit == 0) ? Order.Type.MARKET : Order.Type.LIMIT;

        return new InterMarketOrder(
                itemB_ID,     // buyItemID (want B)
                itemA_ID,     // sellItemID (have A)
                type,
                estimatedBuyVolume, wantPrice,  // buy estimates
                sellVolume, havePrice,          // sell values
                System.currentTimeMillis(),
                testPlayerUUID, bankAccountNr,
                crossRateLimit);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 1: Basic market order exchange
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sell 10 A-items to get B-items via a market inter-market order.
     * Verify A balance decreases, B balance increases, and money is approximately unchanged.
     */
    private TestResult test_market_order_basic_exchange() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
        long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

        InterMarketOrder imo = createIMO(sellVolume, 0);

        // Lock sell items before enqueueing (simulates what PlaceInterMarketOrderRequest does)
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        // Process the order through the full pipeline
        marketManager.update();

        long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
        long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

        // A balance should have decreased (items sold)
        if (finalA >= initialA)
            return fail("A-item balance should have decreased. Initial: " + initialA + ", Final: " + finalA);

        // B balance should have increased (items bought)
        if (finalB <= initialB)
            return fail("B-item balance should have increased. Initial: " + initialB + ", Final: " + finalB);

        return pass("Market order basic exchange: A decreased by " + (initialA - finalA)
                + ", B increased by " + (finalB - initialB));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 2: Money conservation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * After an inter-market trade, the money bank should be approximately unchanged.
     * Only rounding dust (a few dollars) should remain as difference.
     */
    private TestResult test_market_order_money_conservation() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        long initialMoney = bankAccount.getBank(moneyID).getTotalBalance();

        InterMarketOrder imo = createIMO(sellVolume, 0);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        marketManager.update();

        long finalMoney = bankAccount.getBank(moneyID).getTotalBalance();
        long moneyDelta = Math.abs(finalMoney - initialMoney);

        // The money difference should be small (rounding dust only).
        // With priceA=100 and selling 10 items, the dollar volume is ~1000.
        // Allow up to 5% of dollar volume as rounding tolerance.
        long dollarVolume = 10 * 100; // approximate sell proceeds
        long tolerance = Math.max(dollarVolume / 20, 5);

        if (moneyDelta > tolerance)
            return fail("Money should be approximately conserved. Initial: " + initialMoney
                    + ", Final: " + finalMoney + ", Delta: " + moneyDelta + ", Tolerance: " + tolerance);

        return pass("Money conserved within tolerance. Delta: " + moneyDelta + " (tolerance: " + tolerance + ")");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 3: Limit order with favorable rate executes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * PriceA=100, PriceB=50. Current rate = wantPrice*SF/havePrice = 50*SF/100 = 0.5*SF.
     * Place limit at crossRateLimit = 0.6*SF (60 with SF=100). Rate 0.5 < 0.6, so should execute.
     */
    private TestResult test_limit_order_favorable_executes() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        // crossRateLimit = 0.6 * scaleFactor = 60 (with SF=100)
        long crossRateLimit = (long)(0.6 * scaleFactor);

        InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        marketManager.update();

        long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
        long initialA = 100 * scaleFactor;

        // Should have executed — A balance should decrease
        if (finalA >= initialA)
            return fail("Favorable limit order should have executed. A balance unchanged: " + finalA);

        long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();
        long initialB = 100 * scaleFactor;
        if (finalB <= initialB)
            return fail("Favorable limit order should have bought B-items. B balance unchanged: " + finalB);

        return pass("Favorable limit order executed. A: " + initialA + " -> " + finalA
                + ", B: " + initialB + " -> " + finalB);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 4: Limit order with unfavorable rate is skipped
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * PriceA=100, PriceB=50. Current rate = 0.5*SF.
     * Place limit at crossRateLimit = 0.3*SF (30 with SF=100). Rate 0.5 > 0.3, so should NOT execute.
     */
    private TestResult test_limit_order_unfavorable_skipped() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        // crossRateLimit = 0.3 * scaleFactor = 30 (with SF=100)
        long crossRateLimit = (long)(0.3 * scaleFactor);

        InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        marketManager.update();

        long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
        long initialA = 100 * scaleFactor;

        // Should NOT have executed — A balance should remain (locked portion still counted in total)
        // totalBalance = freeBalance + lockedBalance, so total should still be 100*SF
        if (finalA != initialA)
            return fail("Unfavorable limit order should NOT have executed. A balance: " + finalA
                    + " (expected " + initialA + ")");

        long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();
        long initialB = 100 * scaleFactor;
        if (finalB != initialB)
            return fail("Unfavorable limit order should NOT have bought B-items. B balance: " + finalB
                    + " (expected " + initialB + ")");

        return pass("Unfavorable limit order correctly skipped. Balances unchanged.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 5: Limit order becomes favorable after price change
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initially unfavorable: priceA=100, priceB=50, rate=0.5, limit=0.3 (skipped).
     * Then change priceB to 20: rate becomes 20*SF/100 = 0.2*SF. Now 0.2 < 0.3, should execute.
     */
    private TestResult test_limit_order_becomes_favorable() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        long crossRateLimit = (long)(0.3 * scaleFactor);

        InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        // First update — should be skipped (unfavorable)
        marketManager.update();

        long midA = bankAccount.getBank(itemA_ID).getTotalBalance();
        long initialA = 100 * scaleFactor;
        if (midA != initialA)
            return fail("Order should be skipped on first update. A balance: " + midA);

        // Change priceB to 20 — rate becomes 20*SF/100 = 0.2*SF, which is < 0.3*SF
        marketB.test_setCurrentMarketPrice(20);
        marketB.test_clearOrderbook();
        marketB.test_resetVirtualOrderBookVolume();

        // Second update — should now execute
        marketManager.update();

        long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
        if (finalA >= initialA)
            return fail("Order should execute after price change. A balance unchanged: " + finalA);

        long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();
        long initialB = 100 * scaleFactor;
        if (finalB <= initialB)
            return fail("Should have received B-items after price change. B balance: " + finalB);

        return pass("Limit order executed after price became favorable. A: " + initialA + " -> " + finalA
                + ", B: " + initialB + " -> " + finalB);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 6: Cancel unlocks locked items
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Place an unfavorable limit order (items locked), then cancel it.
     * Verify the locked items are returned to the free balance.
     */
    private TestResult test_cancel_unlocks_items() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        long crossRateLimit = (long)(0.3 * scaleFactor); // unfavorable, will be skipped

        InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        // Process once — should be skipped (unfavorable rate)
        marketManager.update();

        long freeBeforeCancel = bankAccount.getBank(itemA_ID).getBalance();
        long lockedBeforeCancel = bankAccount.getBank(itemA_ID).getLockedBalance();

        if (lockedBeforeCancel != sellVolume)
            return fail("Expected " + sellVolume + " locked before cancel, got " + lockedBeforeCancel);

        // Cancel the order
        UUID groupID = imo.getInterMarketGroupID();
        boolean canceled = marketManager.cancelInterMarketOrder(groupID, testPlayerUUID);
        if (!canceled)
            return fail("cancelInterMarketOrder returned false");

        long freeAfterCancel = bankAccount.getBank(itemA_ID).getBalance();
        long lockedAfterCancel = bankAccount.getBank(itemA_ID).getLockedBalance();

        // Locked balance should be 0 after cancel (items unlocked)
        if (lockedAfterCancel != 0)
            return fail("Locked balance should be 0 after cancel, got " + lockedAfterCancel);

        // Free balance should have increased by the sell volume
        if (freeAfterCancel != freeBeforeCancel + sellVolume)
            return fail("Free balance should increase by " + sellVolume + " after cancel. Before: "
                    + freeBeforeCancel + ", After: " + freeAfterCancel);

        return pass("Cancel correctly unlocked " + sellVolume + " items");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 7: Market close cancels inter-market orders
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Place an unfavorable limit order, then close marketA.
     * The market close callback should cancel all inter-market orders touching that market
     * and unlock the reserved items.
     */
    private TestResult test_market_close_cancels_inter_market_orders() {
        resetState(100, 50);

        long sellVolume = 10 * scaleFactor;
        long crossRateLimit = (long)(0.3 * scaleFactor); // unfavorable, will be skipped

        InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        // Process once — should be skipped (unfavorable)
        marketManager.update();

        long lockedBeforeClose = bankAccount.getBank(itemA_ID).getLockedBalance();
        if (lockedBeforeClose != sellVolume)
            return fail("Expected " + sellVolume + " locked before close, got " + lockedBeforeClose);

        // Close marketA — should trigger cancelInterMarketOrdersForMarket
        marketA.setMarketOpen(false);

        long lockedAfterClose = bankAccount.getBank(itemA_ID).getLockedBalance();
        long freeAfterClose = bankAccount.getBank(itemA_ID).getBalance();
        long totalAfterClose = bankAccount.getBank(itemA_ID).getTotalBalance();

        // Items should be unlocked after market close canceled the order
        if (lockedAfterClose != 0)
            return fail("Locked balance should be 0 after market close, got " + lockedAfterClose);

        // Total balance should remain unchanged
        if (totalAfterClose != 100 * scaleFactor)
            return fail("Total A balance should be unchanged after cancel. Got " + totalAfterClose);

        return pass("Market close correctly canceled inter-market order and unlocked items");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 8: Price movement after execution
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Execute a market inter-market order and verify that both markets' prices moved:
     * selling A-items should push A's price down, and buying B-items should push B's price up.
     */
    private TestResult test_price_movement_after_execution() {
        resetState(100, 50);

        long priceA_before = marketA.getCurrentMarketPrice();
        long priceB_before = marketB.getCurrentMarketPrice();

        long sellVolume = 10 * scaleFactor;
        InterMarketOrder imo = createIMO(sellVolume, 0);
        bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
        boolean enqueued = marketManager.putInterMarketOrder(imo);
        if (!enqueued)
            return fail("InterMarketOrder was not enqueued");

        marketManager.update();

        long priceA_after = marketA.getCurrentMarketPrice();
        long priceB_after = marketB.getCurrentMarketPrice();

        // Selling A-items should push A's price down (or at least not increase)
        if (priceA_after > priceA_before)
            return fail("A price should not increase after selling. Before: " + priceA_before
                    + ", After: " + priceA_after);

        // Buying B-items should push B's price up (or at least not decrease)
        if (priceB_after < priceB_before)
            return fail("B price should not decrease after buying. Before: " + priceB_before
                    + ", After: " + priceB_after);

        // At least one should have moved
        if (priceA_after == priceA_before && priceB_after == priceB_before)
            return fail("At least one market price should have moved. A: " + priceA_before
                    + " -> " + priceA_after + ", B: " + priceB_before + " -> " + priceB_after);

        return pass("Prices moved correctly. A: " + priceA_before + " -> " + priceA_after
                + ", B: " + priceB_before + " -> " + priceB_after);
    }
}
