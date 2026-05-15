package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bank.IServerBank;
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
import net.minecraft.nbt.CompoundTag;
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

        // Rate enforcement tests
        addTest("limit_order_rate_not_exceeded", this::test_limit_order_rate_not_exceeded);
        addTest("limit_order_rate_at_boundary", this::test_limit_order_rate_at_boundary);
        addTest("limit_order_rate_slightly_unfavorable", this::test_limit_order_rate_slightly_unfavorable);

        // Balance verification tests
        addTest("limit_order_correct_balances", this::test_limit_order_correct_balances);
        addTest("market_order_exact_balances", this::test_market_order_exact_balances);

        // Partial fill tests
        addTest("limit_order_partial_fill_accumulates", this::test_limit_order_partial_fill_accumulates);
        addTest("partial_fill_balances_consistent", this::test_partial_fill_balances_consistent);

        // Overfill / completion test
        addTest("order_completes_when_overfilled", this::test_order_completes_when_overfilled);

        // Persistence tests
        addTest("order_survives_save_load", this::test_order_survives_save_load);
        addTest("bankAccountNr_preserved_after_save_load", this::test_bankAccountNr_preserved_after_save_load);

        // Direction test
        addTest("reverse_direction_sell_B_buy_A", this::test_reverse_direction_sell_B_buy_A);

        // Edge case tests
        addTest("zero_depth_market_skips", this::test_zero_depth_market_skips);
        addTest("very_small_volume_one_unit", this::test_very_small_volume_one_unit);
        addTest("both_markets_closed_cancels", this::test_both_markets_closed_cancels);

        // Price floor/cap enforcement tests
        addTest("sell_price_floor_prevents_rate_overshoot", this::test_sell_price_floor_prevents_rate_overshoot);
        addTest("buy_price_cap_prevents_rate_overshoot", this::test_buy_price_cap_prevents_rate_overshoot);
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
        // Cancel any leftover inter-market orders from previous tests
        marketManager.cancelInterMarketOrdersForMarket(itemA_ID);
        marketManager.cancelInterMarketOrdersForMarket(itemB_ID);

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

        // Unlock any locked funds from previous tests before resetting balance
        unlockAll(bankAccount.getBank(itemA_ID));
        unlockAll(bankAccount.getBank(itemB_ID));
        unlockAll(bankAccount.getBank(moneyID));
        bankAccount.getBank(itemA_ID).setBalance(100 * scaleFactor);
        bankAccount.getBank(itemB_ID).setBalance(100 * scaleFactor);
        bankAccount.getBank(moneyID).setBalance(100000);
    }

    private void unlockAll(IServerBank bank) {
        long locked = bank.getLockedBalance();
        if (locked > 0) bank.unlockAmount(locked);
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper: compute effective cross-rate from balance changes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Computes the effective cross-rate as the ratio of items sold (A) to items received (B).
     * A lower rate means a more favorable trade (fewer A items per B item).
     *
     * @param aDelta absolute change in A balance (positive = items sold)
     * @param bDelta absolute change in B balance (positive = items received)
     * @return the effective rate, or MAX_VALUE if no B items were received
     */
    private double computeEffectiveRate(long aDelta, long bDelta) {
        if (bDelta == 0) return Double.MAX_VALUE;
        return (double) Math.abs(aDelta) / Math.abs(bDelta);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper: create IMO in reverse direction (sell B to buy A)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an InterMarketOrder that sells B-items to buy A-items (reverse of createIMO).
     *
     * @param sellVolume volume of B-items to sell (in scaled units)
     * @param crossRateLimit 0 for market order, >0 for limit order (raw format)
     */
    private InterMarketOrder createReverseIMO(long sellVolume, long crossRateLimit) {
        long havePrice = marketB.getCurrentMarketPrice();
        long wantPrice = marketA.getCurrentMarketPrice();
        // Estimate how many A-items we can buy with the proceeds from selling B-items
        long estimatedBuyVolume = (wantPrice > 0) ? (sellVolume * havePrice / wantPrice) : sellVolume;
        if (estimatedBuyVolume <= 0) estimatedBuyVolume = 1;
        Order.Type type = (crossRateLimit == 0) ? Order.Type.MARKET : Order.Type.LIMIT;
        return new InterMarketOrder(
                itemA_ID, itemB_ID, type,
                estimatedBuyVolume, wantPrice,
                sellVolume, havePrice,
                System.currentTimeMillis(),
                testPlayerUUID, bankAccountNr,
                crossRateLimit);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 9: Limit order rate not exceeded
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With priceA=100, priceB=80, the cross-rate = 80/100 = 0.8 which is below the
     * limit of 1.0. The order should execute and the effective rate must not exceed 1.0.
     */
    private TestResult test_limit_order_rate_not_exceeded() {
        try {
            resetState(100, 80);

            long sellVolume = 10 * scaleFactor;
            // Limit = 1.0 * scaleFactor (rate must be at or below this to execute)
            long crossRateLimit = (long)(1.0 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // Order should have executed: A decreased, B increased
            if (finalA >= initialA)
                return fail("A balance should have decreased. Initial: " + initialA + ", Final: " + finalA);
            if (finalB <= initialB)
                return fail("B balance should have increased. Initial: " + initialB + ", Final: " + finalB);

            // Verify effective rate does not exceed limit (with 1% rounding tolerance)
            long aDelta = initialA - finalA;
            long bDelta = finalB - initialB;
            double effectiveRate = computeEffectiveRate(aDelta, bDelta);
            if (effectiveRate > 1.0 * 1.01)
                return fail("Effective rate " + effectiveRate + " exceeds limit 1.0 (with 1% tolerance)");

            return pass("Rate not exceeded. Effective rate: " + effectiveRate + " <= 1.0");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 10: Limit order rate at boundary
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With priceA=100, priceB=100, the cross-rate = 100/100 = 1.0 which equals the
     * limit of 1.0. The order should still execute at the boundary.
     */
    private TestResult test_limit_order_rate_at_boundary() {
        try {
            resetState(100, 100);

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // At boundary rate, order should execute
            if (finalA >= initialA)
                return fail("A balance should have decreased at boundary rate. Initial: " + initialA + ", Final: " + finalA);
            if (finalB <= initialB)
                return fail("B balance should have increased at boundary rate. Initial: " + initialB + ", Final: " + finalB);

            // Verify effective rate is at or below the limit (with 1% tolerance)
            long aDelta = initialA - finalA;
            long bDelta = finalB - initialB;
            double effectiveRate = computeEffectiveRate(aDelta, bDelta);
            if (effectiveRate > 1.0 * 1.01)
                return fail("Effective rate " + effectiveRate + " exceeds boundary limit 1.0 (with 1% tolerance)");

            return pass("Boundary rate executed. Effective rate: " + effectiveRate);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 11: Limit order slightly unfavorable — should be skipped
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With priceA=100, priceB=101, the cross-rate = 101/100 = 1.01 which exceeds the
     * limit of 1.0. The order should NOT execute; balances remain unchanged.
     */
    private TestResult test_limit_order_rate_slightly_unfavorable() {
        try {
            resetState(100, 101);

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // Unfavorable rate — balances should remain unchanged
            if (finalA != initialA)
                return fail("A balance should be unchanged (rate unfavorable). Initial: " + initialA + ", Final: " + finalA);
            if (finalB != initialB)
                return fail("B balance should be unchanged (rate unfavorable). Initial: " + initialB + ", Final: " + finalB);

            return pass("Slightly unfavorable rate correctly skipped. Balances unchanged.");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 12: Limit order correct balances
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With priceA=100, priceB=50, selling 10*SF of A should yield approximately
     * 20*SF of B (ratio 100/50 = 2). Verifies A decreased, B increased by expected
     * amount within tolerance, and money is approximately conserved.
     */
    private TestResult test_limit_order_correct_balances() {
        try {
            resetState(100, 50);

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(0.6 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();
            long initialMoney = bankAccount.getBank(moneyID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();
            long finalMoney = bankAccount.getBank(moneyID).getTotalBalance();

            // A should decrease by approximately sellVolume
            long aDelta = initialA - finalA;
            if (aDelta <= 0)
                return fail("A should have decreased. Delta: " + aDelta);

            // B should increase by approximately 20*SF (= sellVolume * priceA/priceB = 10 * 100/50 = 20)
            long expectedBIncrease = 20 * scaleFactor;
            long bDelta = finalB - initialB;
            if (bDelta <= 0)
                return fail("B should have increased. Delta: " + bDelta);

            // Allow 15% tolerance for depth-walking slippage
            double tolerance = 0.15;
            if (Math.abs(bDelta - expectedBIncrease) > expectedBIncrease * tolerance)
                return fail("B increase " + bDelta + " deviates more than 15% from expected " + expectedBIncrease);

            // Money should be approximately unchanged (rounding dust only)
            long moneyDelta = Math.abs(finalMoney - initialMoney);
            long dollarVolume = 10 * 100; // approximate sell proceeds
            long moneyTolerance = Math.max(dollarVolume / 10, 10);
            if (moneyDelta > moneyTolerance)
                return fail("Money delta " + moneyDelta + " exceeds tolerance " + moneyTolerance);

            return pass("Balances correct. A delta: -" + aDelta + ", B delta: +" + bDelta
                    + " (expected ~" + expectedBIncrease + "), money delta: " + moneyDelta);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 13: Market order exact balances
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With priceA=200, priceB=100, selling 5*SF of A via market order should yield
     * approximately 10*SF of B (ratio 200/100 = 2). Verifies B increase within 20% of expected.
     */
    private TestResult test_market_order_exact_balances() {
        try {
            resetState(200, 100);

            long sellVolume = 5 * scaleFactor;
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, 0); // market order
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();
            long bDelta = finalB - initialB;

            // Expected B increase = 5 * 200/100 = 10 items * scaleFactor
            long expectedBIncrease = 10 * scaleFactor;

            if (bDelta <= 0)
                return fail("B should have increased. Delta: " + bDelta);

            // Allow 20% tolerance for depth-walking slippage
            double tolerance = 0.20;
            if (Math.abs(bDelta - expectedBIncrease) > expectedBIncrease * tolerance)
                return fail("B increase " + bDelta + " deviates more than 20% from expected " + expectedBIncrease);

            return pass("Market order balances correct. B delta: +" + bDelta + " (expected ~" + expectedBIncrease + ")");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 14: Partial fill accumulates over multiple ticks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With low virtual depth (0.5), a large order cannot fill in a single tick.
     * After multiple update() calls, partial fills should accumulate:
     * A decreases and B increases.
     */
    private TestResult test_limit_order_partial_fill_accumulates() {
        try {
            resetState(100, 80);

            // Set LOW virtual depth so fills happen gradually
            marketA.test_setDefaultVolumeProviderFunction(p -> 0.5f);
            marketA.test_resetVirtualOrderBookVolume();
            marketB.test_setDefaultVolumeProviderFunction(p -> 0.5f);
            marketB.test_resetVirtualOrderBookVolume();

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor); // favorable rate 0.8 < 1.0

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            // Process multiple ticks to allow partial fills to accumulate
            for (int i = 0; i < 20; i++) {
                marketManager.update();
            }

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // Some fill should have happened over the 20 ticks
            if (finalA >= initialA)
                return fail("A balance should have decreased after partial fills. Initial: " + initialA + ", Final: " + finalA);
            if (finalB <= initialB)
                return fail("B balance should have increased after partial fills. Initial: " + initialB + ", Final: " + finalB);

            return pass("Partial fills accumulated. A: " + initialA + " -> " + finalA
                    + ", B: " + initialB + " -> " + finalB);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 15: Partial fill balances stay consistent across ticks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With low depth, checks that after each update tick the total portfolio value
     * (A*priceA + B*priceB + money) does not change by more than a tolerance.
     * This guards against value leaking or being created during partial fills.
     */
    private TestResult test_partial_fill_balances_consistent() {
        try {
            resetState(100, 80);

            // Set LOW virtual depth for gradual filling
            marketA.test_setDefaultVolumeProviderFunction(p -> 0.5f);
            marketA.test_resetVirtualOrderBookVolume();
            marketB.test_setDefaultVolumeProviderFunction(p -> 0.5f);
            marketB.test_resetVirtualOrderBookVolume();

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor);

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            // Compute initial portfolio value using initial prices
            long priceA = 100;
            long priceB = 80;
            long prevA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long prevB = bankAccount.getBank(itemB_ID).getTotalBalance();
            long prevMoney = bankAccount.getBank(moneyID).getTotalBalance();
            long prevValue = prevA * priceA + prevB * priceB + prevMoney * scaleFactor;

            // Allow 10% tolerance for price movement during depth-walking
            long valueTolerance = Math.abs(prevValue / 10);

            for (int i = 0; i < 10; i++) {
                marketManager.update();

                long curA = bankAccount.getBank(itemA_ID).getTotalBalance();
                long curB = bankAccount.getBank(itemB_ID).getTotalBalance();
                long curMoney = bankAccount.getBank(moneyID).getTotalBalance();
                // Use initial prices for consistent comparison (price movement is tested elsewhere)
                long curValue = curA * priceA + curB * priceB + curMoney * scaleFactor;

                long valueDelta = Math.abs(curValue - prevValue);
                if (valueDelta > valueTolerance)
                    return fail("Portfolio value shifted too much on tick " + i
                            + ". Previous: " + prevValue + ", Current: " + curValue
                            + ", Delta: " + valueDelta + ", Tolerance: " + valueTolerance);

                prevValue = curValue;
            }

            return pass("Portfolio value stayed consistent across partial fill ticks");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 16: Order completes when overfilled
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Directly tests Order.isFilled() with overfill scenarios:
     * - A buy order with targetVolume=100 and filledVolume=105 should be considered filled.
     * - A sell order with targetVolume=-100 and filledVolume=-105 should be considered filled.
     * This guards against the >= vs == comparison in isFilled().
     */
    private TestResult test_order_completes_when_overfilled() {
        try {
            // Buy order: targetVolume=100, overfilled to 105
            Order buyOrder = new Order(itemA_ID, Order.Type.MARKET, 100, 50, System.currentTimeMillis(),
                    testPlayerUUID, bankAccountNr);
            buyOrder.edit(105, -5250); // filled 105 items, paid 5250 money
            TestResult r = assertTrue("Buy order with overfill (105/100) should be filled", buyOrder.isFilled());
            if (!r.passed()) return r;

            // Sell order: targetVolume=-100, overfilled to -105
            Order sellOrder = new Order(itemA_ID, Order.Type.MARKET, -100, 50, System.currentTimeMillis(),
                    testPlayerUUID, bankAccountNr);
            sellOrder.edit(-105, 5250); // filled -105 items, received 5250 money
            r = assertTrue("Sell order with overfill (-105/-100) should be filled", sellOrder.isFilled());
            if (!r.passed()) return r;

            return pass("Overfilled orders correctly report isFilled()=true");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 17: InterMarketOrder survives save/load via NBT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an InterMarketOrder with known values, saves to CompoundTag, loads back,
     * and verifies all fields match: buyItemID, sellItemID, crossRateLimit,
     * interMarketGroupID, targetBuyVolume, targetSellVolume.
     */
    private TestResult test_order_survives_save_load() {
        try {
            long crossRateLimit = 500;
            long sellVolume = 25;
            long buyVolume = 50;
            long buyPrice = 100;
            long sellPrice = 200;

            InterMarketOrder original = new InterMarketOrder(
                    itemB_ID, itemA_ID, Order.Type.LIMIT,
                    buyVolume, buyPrice,
                    sellVolume, sellPrice,
                    System.currentTimeMillis(),
                    testPlayerUUID, bankAccountNr,
                    crossRateLimit);

            // Save to NBT
            CompoundTag tag = new CompoundTag();
            boolean saved = original.save(tag);
            TestResult r = assertTrue("save() should return true", saved);
            if (!r.passed()) return r;

            // Load from NBT
            InterMarketOrder loaded = InterMarketOrder.createFromNBT(tag);
            r = assertNotNull("createFromNBT should not return null for valid data", loaded);
            if (!r.passed()) return r;

            // Verify all fields match
            r = assertEquals("Buy item ID should match",
                    original.getBuyItemID().getShort(), loaded.getBuyItemID().getShort());
            if (!r.passed()) return r;

            r = assertEquals("Sell item ID should match",
                    original.getSellItemID().getShort(), loaded.getSellItemID().getShort());
            if (!r.passed()) return r;

            r = assertEquals("CrossRateLimit should match",
                    original.getCrossRateLimit(), loaded.getCrossRateLimit());
            if (!r.passed()) return r;

            r = assertEquals("InterMarketGroupID should match",
                    original.getInterMarketGroupID(), loaded.getInterMarketGroupID());
            if (!r.passed()) return r;

            r = assertEquals("Target buy volume should match",
                    original.getTargetBuyVolume(), loaded.getTargetBuyVolume());
            if (!r.passed()) return r;

            r = assertEquals("Target sell volume should match",
                    original.getTargetSellVolume(), loaded.getTargetSellVolume());
            if (!r.passed()) return r;

            return pass("InterMarketOrder survives save/load with all fields intact");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 18: bankAccountNr preserved after save/load
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an InterMarketOrder using the test bankAccountNr, saves and loads
     * via NBT, and verifies the loaded order has the same bankAccountNr.
     */
    private TestResult test_bankAccountNr_preserved_after_save_load() {
        try {
            InterMarketOrder original = new InterMarketOrder(
                    itemB_ID, itemA_ID, Order.Type.MARKET,
                    10, 100,
                    5, 200,
                    System.currentTimeMillis(),
                    testPlayerUUID, bankAccountNr,
                    0);

            // Verify the original has the correct bankAccountNr
            TestResult r = assertEquals("Original bankAccountNr should match",
                    bankAccountNr, original.getBankAccountNr());
            if (!r.passed()) return r;

            // Save and load via NBT
            CompoundTag tag = new CompoundTag();
            boolean saved = original.save(tag);
            r = assertTrue("save() should return true", saved);
            if (!r.passed()) return r;

            InterMarketOrder loaded = InterMarketOrder.createFromNBT(tag);
            r = assertNotNull("createFromNBT should not return null", loaded);
            if (!r.passed()) return r;

            // Verify bankAccountNr survives round-trip
            r = assertEquals("bankAccountNr should be preserved after save/load",
                    bankAccountNr, loaded.getBankAccountNr());
            if (!r.passed()) return r;

            return pass("bankAccountNr preserved: " + bankAccountNr);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 19: Reverse direction — sell B to buy A
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Uses createReverseIMO to sell B-items and buy A-items. With priceA=50 and priceB=100,
     * selling B is profitable (each B item buys ~2 A items). After execution, A should
     * increase and B should decrease.
     */
    private TestResult test_reverse_direction_sell_B_buy_A() {
        try {
            resetState(50, 100);

            long sellVolume = 10 * scaleFactor;
            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createReverseIMO(sellVolume, 0); // market order
            // Lock B-items (we are selling B in this direction)
            bankAccount.getBank(itemB_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("Reverse InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // A should increase (bought A items)
            if (finalA <= initialA)
                return fail("A balance should have increased (buying A). Initial: " + initialA + ", Final: " + finalA);

            // B should decrease (sold B items)
            if (finalB >= initialB)
                return fail("B balance should have decreased (selling B). Initial: " + initialB + ", Final: " + finalB);

            return pass("Reverse direction executed. A: " + initialA + " -> " + finalA
                    + ", B: " + initialB + " -> " + finalB);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 20: Zero depth market skips order
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With virtual depth set to 0 for both markets, there is no liquidity.
     * A limit order at a favorable rate should still not fill because there are
     * no virtual order book entries to match against.
     */
    private TestResult test_zero_depth_market_skips() {
        try {
            resetState(100, 80);

            // Set virtual depth to 0 — no liquidity at all
            marketA.test_setDefaultVolumeProviderFunction(p -> 0f);
            marketA.test_resetVirtualOrderBookVolume();
            marketB.test_setDefaultVolumeProviderFunction(p -> 0f);
            marketB.test_resetVirtualOrderBookVolume();

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor); // favorable rate 0.8 < 1.0

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // With zero depth, no fill should occur — balances unchanged
            if (finalA != initialA)
                return fail("A balance should be unchanged with zero depth. Initial: " + initialA + ", Final: " + finalA);
            if (finalB != initialB)
                return fail("B balance should be unchanged with zero depth. Initial: " + initialB + ", Final: " + finalB);

            return pass("Zero depth correctly prevented order execution. Balances unchanged.");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 21: Very small volume — one unit
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sells exactly 1 unit (the smallest possible volume, NOT 1*scaleFactor) to verify
     * the system handles tiny orders without crashing or throwing exceptions.
     */
    private TestResult test_very_small_volume_one_unit() {
        try {
            resetState(100, 50);

            long sellVolume = 1; // literal smallest possible volume
            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, 0); // market order
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder for 1 unit was not enqueued");

            // Should not throw any exceptions
            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();

            // A should have decreased or stayed the same (fill is not guaranteed for 1 unit)
            if (finalA > initialA)
                return fail("A balance should not increase. Initial: " + initialA + ", Final: " + finalA);

            return pass("Very small volume (1 unit) handled without errors. A: " + initialA + " -> " + finalA);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 22: Both markets closed — order should be canceled
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Closes both markets before the update tick. The market close callback should
     * cancel the inter-market order and unlock all reserved items.
     */
    private TestResult test_both_markets_closed_cancels() {
        try {
            resetState(100, 50);

            long sellVolume = 10 * scaleFactor;
            InterMarketOrder imo = createIMO(sellVolume, 0); // market order
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            // Close BOTH markets — should trigger cancel callbacks
            marketA.setMarketOpen(false);
            marketB.setMarketOpen(false);

            // Process after close
            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long lockedA = bankAccount.getBank(itemA_ID).getLockedBalance();

            // Locked items should be fully unlocked after cancel
            if (lockedA != 0)
                return fail("Locked A balance should be 0 after both markets closed. Got: " + lockedA);

            // Total A balance should be restored to initial value
            long expectedA = 100 * scaleFactor;
            if (finalA != expectedA)
                return fail("Total A balance should be restored to " + expectedA + " after cancel. Got: " + finalA);

            return pass("Both markets closed correctly canceled order and unlocked items");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 23: Sell price floor prevents rate overshoot
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With thin depth on market A (sell side), the price floor should prevent selling
     * items below minSellPrice. With priceA=100, priceB=95, the cross-rate is 0.95
     * which is favorable (< 1.0 limit). Even with thin depth, the effective rate
     * should stay within bounds thanks to the sell price floor.
     */
    private TestResult test_sell_price_floor_prevents_rate_overshoot() {
        try {
            resetState(100, 95);

            // Set thin depth on market A (sell side) to stress the price floor
            marketA.test_setDefaultVolumeProviderFunction(p -> 1f);
            marketA.test_resetVirtualOrderBookVolume();

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // If any fill happened, verify the effective rate stays within bounds
            long aDelta = initialA - finalA;
            long bDelta = finalB - initialB;
            if (aDelta > 0 && bDelta > 0) {
                double effectiveRate = computeEffectiveRate(aDelta, bDelta);
                // The sell price floor should keep the rate at or below 1.0 (with tolerance)
                if (effectiveRate > 1.0 * 1.01)
                    return fail("Effective rate " + effectiveRate + " overshoots limit despite sell price floor");

                return pass("Sell price floor held. Effective rate: " + effectiveRate + " <= 1.0");
            }

            // If no fill happened (depth too thin), that is also acceptable — no overshoot possible
            return pass("No fill occurred with thin sell depth (price floor prevented unsafe execution)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Test 24: Buy price cap prevents rate overshoot
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * With thin depth on market B (buy side), the buy price cap should prevent buying
     * items above maxBuyPrice. With priceA=100, priceB=95, the cross-rate is 0.95
     * which is favorable (< 1.0 limit). Even with thin depth on the buy side, the
     * effective rate should stay within bounds thanks to the buy price cap.
     */
    private TestResult test_buy_price_cap_prevents_rate_overshoot() {
        try {
            resetState(100, 95);

            // Set thin depth on market B (buy side) to stress the buy price cap
            marketB.test_setDefaultVolumeProviderFunction(p -> 1f);
            marketB.test_resetVirtualOrderBookVolume();

            long sellVolume = 10 * scaleFactor;
            long crossRateLimit = (long)(1.0 * scaleFactor);

            long initialA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long initialB = bankAccount.getBank(itemB_ID).getTotalBalance();

            InterMarketOrder imo = createIMO(sellVolume, crossRateLimit);
            bankAccount.getBank(itemA_ID).lockAmount(sellVolume);
            boolean enqueued = marketManager.putInterMarketOrder(imo);
            if (!enqueued)
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            long finalA = bankAccount.getBank(itemA_ID).getTotalBalance();
            long finalB = bankAccount.getBank(itemB_ID).getTotalBalance();

            // If any fill happened, verify the effective rate stays within bounds
            long aDelta = initialA - finalA;
            long bDelta = finalB - initialB;
            if (aDelta > 0 && bDelta > 0) {
                double effectiveRate = computeEffectiveRate(aDelta, bDelta);
                // The buy price cap should keep the rate at or below 1.0 (with tolerance)
                if (effectiveRate > 1.0 * 1.01)
                    return fail("Effective rate " + effectiveRate + " overshoots limit despite buy price cap");

                return pass("Buy price cap held. Effective rate: " + effectiveRate + " <= 1.0");
            }

            // If no fill happened (depth too thin), that is also acceptable — no overshoot possible
            return pass("No fill occurred with thin buy depth (price cap prevented unsafe execution)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }
}
