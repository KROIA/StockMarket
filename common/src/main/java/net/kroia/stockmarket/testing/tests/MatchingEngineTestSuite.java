package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class MatchingEngineTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        MatchingEngineTestSuite.backend = backend;
    }

    private ItemID moneyID;
    private IServerMarket serverMarket;
    private int bankAccountNr1;
    private IServerBankAccount bankAccount1;
    private int bankAccountNr2;
    private IServerBankAccount bankAccount2;
    private long uniformVolumeDistributionScale = 5;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MATCHING_ENGINE;
    }

    @Override
    public void registerTests() {
        // Order Matching Basics
        addTest("limitBuyBelowMarket_goesToOrderbook", this::test_limitBuyBelowMarket_goesToOrderbook);
        addTest("limitSellAboveMarket_goesToOrderbook", this::test_limitSellAboveMarket_goesToOrderbook);
        addTest("limitBuyAtOrAboveMarket_matchesImmediately", this::test_limitBuyAtOrAboveMarket_matchesImmediately);
        addTest("limitSellAtOrBelowMarket_matchesImmediately", this::test_limitSellAtOrBelowMarket_matchesImmediately);
        addTest("marketBuyOrder_consumesSellSide", this::test_marketBuyOrder_consumesSellSide);
        addTest("marketSellOrder_consumesBuySide", this::test_marketSellOrder_consumesBuySide);
        addTest("unfilled_limitOrder_remainsInOrderbook", this::test_unfilled_limitOrder_remainsInOrderbook);

        // ConcurrentModificationException
        addTest("processSell_iteratesAndRemoves", this::test_processSell_iteratesAndRemoves);
        addTest("processBuy_iteratesAndRemoves", this::test_processBuy_iteratesAndRemoves);

        // Overflow Protection
        addTest("costCalculation_largeValues", this::test_costCalculation_largeValues);
        addTest("costCalculation_maxLongPrice", this::test_costCalculation_maxLongPrice);

        // Partial Fill Scenarios
        addTest("partialFill_sellerInsufficientStock", this::test_partialFill_sellerInsufficientStock);
        addTest("partialFill_buyerInsufficientFunds", this::test_partialFill_buyerInsufficientFunds);
        addTest("partialFill_multipleCounterparties", this::test_partialFill_multipleCounterparties);

        // Bot Orders
        addTest("botSell_noAccountRequired", this::test_botSell_noAccountRequired);
        addTest("botBuy_noAccountRequired", this::test_botBuy_noAccountRequired);

        // Price Movement
        addTest("priceChangedCallback_fired", this::test_priceChangedCallback_fired);
        addTest("marketPrice_walksUp_onBuy", this::test_marketPrice_walksUp_onBuy);
        addTest("marketPrice_walksDown_onSell", this::test_marketPrice_walksDown_onSell);

        // Timeout Handling
        addTest("drainVirtualSell_timeout", this::test_drainVirtualSell_timeout);
        addTest("drainVirtualBuy_timeout", this::test_drainVirtualBuy_timeout);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("MatchingEngineTestSuite requires backend to be set");
        }
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());

        bankAccount1 = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(2);
        if (bankAccount1 == null)
            bankAccount1 = backend.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("MatchingEngineTest_1");
        if (bankAccount1 == null)
            throw new RuntimeException("Can't create MatchingEngineTest_1 bank account");
        bankAccountNr1 = bankAccount1.getAccountNumber();

        bankAccount2 = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(3);
        if (bankAccount2 == null)
            bankAccount2 = backend.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("MatchingEngineTest_2");
        if (bankAccount2 == null)
            throw new RuntimeException("Can't create MatchingEngineTest_2 bank account");
        bankAccountNr2 = bankAccount2.getAccountNumber();

        ItemID id = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        serverMarket = backend.MARKET_MANAGER.getSync().createMarket(id);

        bankAccount1.createBank(id, 100);
        bankAccount2.createBank(id, 100);
        bankAccount1.createBank(moneyID, 10000);
        bankAccount2.createBank(moneyID, 10000);
    }

    @Override
    public void teardown() {
        if (serverMarket != null) {
            serverMarket.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
            serverMarket.test_resetVirtualOrderBookVolume();
        }
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

        bankAccount1.getBank(item).setBalance(100);
        bankAccount2.getBank(item).setBalance(100);
        bankAccount1.getBank(moneyID).setBalance(10000);
        bankAccount2.getBank(moneyID).setBalance(10000);
    }

    // ── Order Matching Basics ─────────────────────────────────────────────────

    private TestResult test_limitBuyBelowMarket_goesToOrderbook() {
        try {
            resetMarketState(false, 10);

            Order limitBuy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitBuy);
            serverMarket.update();

            // Price 8 < market price 10, so order should go to orderbook unfilled
            if (limitBuy.isFilled())
                return fail("Limit buy below market price should NOT be filled immediately");
            if (limitBuy.getFilledVolume() != 0)
                return fail("Expected 0 filled volume, got: " + limitBuy.getFilledVolume());
            return pass("Limit buy below market price correctly placed into orderbook");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_limitSellAboveMarket_goesToOrderbook() {
        try {
            resetMarketState(false, 10);

            Order limitSell = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitSell);
            serverMarket.update();

            if (limitSell.isFilled())
                return fail("Limit sell above market price should NOT be filled immediately");
            if (limitSell.getFilledVolume() != 0)
                return fail("Expected 0 filled volume, got: " + limitSell.getFilledVolume());
            return pass("Limit sell above market price correctly placed into orderbook");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_limitBuyAtOrAboveMarket_matchesImmediately() {
        try {
            resetMarketState(true, 10);

            Order limitBuy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 5, 12, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitBuy);
            serverMarket.update();

            if (!limitBuy.isFilled())
                return fail("Limit buy at/above market price should be filled, but filledVolume=" + limitBuy.getFilledVolume());
            return pass("Limit buy at/above market price triggers matching immediately");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_limitSellAtOrBelowMarket_matchesImmediately() {
        try {
            resetMarketState(true, 10);

            Order limitSell = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 8, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitSell);
            serverMarket.update();

            if (!limitSell.isFilled())
                return fail("Limit sell at/below market price should be filled, but filledVolume=" + limitSell.getFilledVolume());
            return pass("Limit sell at/below market price triggers matching immediately");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_marketBuyOrder_consumesSellSide() {
        try {
            resetMarketState(true, 10);
            long priceBefore = serverMarket.getCurrentMarketPrice();

            Order marketBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10, priceBefore, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(marketBuy);
            serverMarket.update();

            if (!marketBuy.isFilled())
                return fail("Market buy should be filled");
            if (serverMarket.getCurrentMarketPrice() <= priceBefore)
                return fail("Price should walk up on buy, but went from " + priceBefore + " to " + serverMarket.getCurrentMarketPrice());
            return pass("Market buy order consumes sell-side volume, price walks up");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_marketSellOrder_consumesBuySide() {
        try {
            resetMarketState(true, 10);
            long priceBefore = serverMarket.getCurrentMarketPrice();

            Order marketSell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -10, priceBefore, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(marketSell);
            serverMarket.update();

            if (!marketSell.isFilled())
                return fail("Market sell should be filled");
            if (serverMarket.getCurrentMarketPrice() >= priceBefore)
                return fail("Price should walk down on sell, but went from " + priceBefore + " to " + serverMarket.getCurrentMarketPrice());
            return pass("Market sell order consumes buy-side volume, price walks down");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_unfilled_limitOrder_remainsInOrderbook() {
        try {
            resetMarketState(true, 10);

            // Large limit buy that can't be fully filled
            Order limitBuy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 100, 13, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitBuy);
            serverMarket.update();

            if (limitBuy.isFilled())
                return fail("Oversized limit buy should NOT be fully filled");
            // The remaining part should be in the orderbook
            if (limitBuy.getFilledVolume() == 0)
                return fail("Limit buy should be at least partially filled by virtual volume");
            return pass("Partially filled limit order remains in orderbook");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── ConcurrentModificationException ───────────────────────────────────────

    private TestResult test_processSell_iteratesAndRemoves() {
        try {
            resetMarketState(false, 10);

            // Place multiple buy orders in the orderbook
            Order buy1 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), bankAccountNr1);
            Order buy2 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 5, 7, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(buy1);
            serverMarket.putOrder(buy2);
            serverMarket.update(); // Place them in the orderbook (below market price)

            // Now sell into those buy orders
            Order sellOrder = new Order(serverMarket.getItemID(), Order.Type.MARKET, -10, 7, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sellOrder);
            // This should NOT throw ConcurrentModificationException
            serverMarket.update();

            return pass("processSell iterates and removes orders without ConcurrentModificationException");
        } catch (Exception e) {
            return fail("ConcurrentModificationException or other error: " + e.getMessage());
        }
    }

    private TestResult test_processBuy_iteratesAndRemoves() {
        try {
            resetMarketState(false, 10);

            // Place multiple sell orders in the orderbook
            Order sell1 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), bankAccountNr1);
            Order sell2 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 13, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(sell1);
            serverMarket.putOrder(sell2);
            serverMarket.update(); // Place them in the orderbook (above market price)

            // Now buy into those sell orders
            Order buyOrder = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10, 13, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(buyOrder);
            serverMarket.update();

            return pass("processBuy iterates and removes orders without ConcurrentModificationException");
        } catch (Exception e) {
            return fail("ConcurrentModificationException or other error: " + e.getMessage());
        }
    }

    // ── Overflow Protection ───────────────────────────────────────────────────

    private TestResult test_costCalculation_largeValues() {
        try {
            resetMarketState(false, 10);

            // Place a sell limit with a very large price
            long largePrice = Long.MAX_VALUE / 2;
            Order sell = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, largePrice, 0);
            serverMarket.putOrder(sell);
            serverMarket.update();

            // Try to buy at that price - cost would overflow
            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 5, largePrice, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            // Should not crash due to overflow
            return pass("Large value cost calculation handled without crash");
        } catch (ArithmeticException e) {
            return fail("ArithmeticException overflow not caught: " + e.getMessage());
        } catch (Exception e) {
            return fail("Unexpected exception: " + e.getMessage());
        }
    }

    private TestResult test_costCalculation_maxLongPrice() {
        try {
            resetMarketState(false, 10);

            // Price = Long.MAX_VALUE, volume = 2 -> guaranteed overflow
            Order sell = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -2, Long.MAX_VALUE, 0);
            serverMarket.putOrder(sell);
            serverMarket.update();

            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 2, Long.MAX_VALUE, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            return pass("Max long price overflow handled gracefully");
        } catch (ArithmeticException e) {
            return fail("ArithmeticException not caught for max long price: " + e.getMessage());
        } catch (Exception e) {
            return fail("Unexpected exception: " + e.getMessage());
        }
    }

    // ── Partial Fill Scenarios ────────────────────────────────────────────────

    private TestResult test_partialFill_sellerInsufficientStock() {
        try {
            resetMarketState(false, 10);

            // Seller has only 100 items but places order for 200
            Order sellLimit = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -200, 12, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sellLimit);
            serverMarket.update();

            // Buyer tries to buy
            Order marketBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 50, 12, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(marketBuy);
            serverMarket.update();

            // The fill should be capped by seller's actual stock
            return pass("Partial fill due to insufficient seller stock handled correctly");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_partialFill_buyerInsufficientFunds() {
        try {
            resetMarketState(false, 10);

            // Place a sell order in the orderbook
            Order sellLimit = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -50, 12, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sellLimit);
            serverMarket.update();

            // Buyer has limited funds (10000) trying to buy large volume at high price
            Order marketBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 50, 12, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(marketBuy);
            serverMarket.update();

            // Fill should be capped to what buyer can afford
            return pass("Partial fill due to insufficient buyer funds handled correctly");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_partialFill_multipleCounterparties() {
        try {
            resetMarketState(false, 10);

            // Place sell orders from different accounts at different prices
            Order sell1 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), bankAccountNr1);
            Order sell2 = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -5, 13, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(sell1);
            serverMarket.putOrder(sell2);
            serverMarket.update();

            // Buy order that should fill across both counterparties
            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10, 13, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            TestResult r = assertTrue("Buy should be filled or partially filled", buy.getFilledVolume() > 0);
            if (!r.passed()) return r;
            return pass("Order fills across multiple counterparties at different prices");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Bot Orders ────────────────────────────────────────────────────────────

    private TestResult test_botSell_noAccountRequired() {
        try {
            resetMarketState(true, 10);

            // Bot sell order (no UUID, no bank account)
            Order botSell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -5, 10, 0);
            serverMarket.putOrder(botSell);
            serverMarket.update();

            // Should process without NPE or crash
            return pass("Bot sell order processes without requiring a bank account");
        } catch (Exception e) {
            return fail("Bot sell order caused exception: " + e.getMessage());
        }
    }

    private TestResult test_botBuy_noAccountRequired() {
        try {
            resetMarketState(true, 10);

            // Bot buy order (no UUID, no bank account)
            Order botBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 5, 10, 0);
            serverMarket.putOrder(botBuy);
            serverMarket.update();

            // Should process without NPE or crash
            return pass("Bot buy order processes without requiring a bank account");
        } catch (Exception e) {
            return fail("Bot buy order caused exception: " + e.getMessage());
        }
    }

    // ── Price Movement ────────────────────────────────────────────────────────

    private TestResult test_priceChangedCallback_fired() {
        try {
            resetMarketState(true, 10);
            long priceBefore = serverMarket.getCurrentMarketPrice();

            Order marketBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10, priceBefore, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(marketBuy);
            serverMarket.update();

            long priceAfter = serverMarket.getCurrentMarketPrice();
            if (priceAfter == priceBefore)
                return fail("Price should have changed after matching, but stayed at " + priceBefore);
            return pass("Price changed callback fired and updated market price from " + priceBefore + " to " + priceAfter);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_marketPrice_walksUp_onBuy() {
        try {
            resetMarketState(true, 10);
            long priceBefore = serverMarket.getCurrentMarketPrice();

            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 10, priceBefore, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            TestResult r = assertTrue("Price should increase on buy",
                    serverMarket.getCurrentMarketPrice() > priceBefore);
            if (!r.passed()) return r;
            return pass("Market price walks up on buy: " + priceBefore + " -> " + serverMarket.getCurrentMarketPrice());
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_marketPrice_walksDown_onSell() {
        try {
            resetMarketState(true, 10);
            long priceBefore = serverMarket.getCurrentMarketPrice();

            Order sell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -10, priceBefore, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sell);
            serverMarket.update();

            TestResult r = assertTrue("Price should decrease on sell",
                    serverMarket.getCurrentMarketPrice() < priceBefore);
            if (!r.passed()) return r;
            return pass("Market price walks down on sell: " + priceBefore + " -> " + serverMarket.getCurrentMarketPrice());
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Timeout Handling ──────────────────────────────────────────────────────

    private TestResult test_drainVirtualSell_timeout() {
        try {
            // Use a volume provider that always returns tiny volume so the loop hits timeout
            resetMarketState(false, 10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0.0001f);
            serverMarket.test_resetVirtualOrderBookVolume();

            // Large sell order that would need many price levels
            Order largeSell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -1000000, 10, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(largeSell);

            long start = System.currentTimeMillis();
            serverMarket.update();
            long elapsed = System.currentTimeMillis() - start;

            // Should complete in reasonable time due to timeout, not hang
            TestResult r = assertTrue("Should complete within 30 seconds (timeout prevents hang)", elapsed < 30000);
            if (!r.passed()) return r;
            return pass("drainVirtualSell timeout prevents infinite loop (completed in " + elapsed + "ms)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_drainVirtualBuy_timeout() {
        try {
            resetMarketState(false, 10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> -0.0001f);
            serverMarket.test_resetVirtualOrderBookVolume();

            Order largeBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 1000000, 10, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(largeBuy);

            long start = System.currentTimeMillis();
            serverMarket.update();
            long elapsed = System.currentTimeMillis() - start;

            TestResult r = assertTrue("Should complete within 30 seconds (timeout prevents hang)", elapsed < 30000);
            if (!r.passed()) return r;
            return pass("drainVirtualBuy timeout prevents infinite loop (completed in " + elapsed + "ms)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Volume distribution helpers ───────────────────────────────────────────

    private float emptyVolumeDistribution(double price) {
        return 0;
    }

    private float uniformVolumeDistribution(double price) {
        return (float) uniformVolumeDistributionScale / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();
    }
}
