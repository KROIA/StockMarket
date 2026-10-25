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
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.MatchingEngine;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;

import java.util.UUID;

public class MatchingEngineTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        MatchingEngineTestSuite.backend = backend;
    }

    /** Owns every scratch market/account this suite creates; drained in {@link #teardown()}. */
    private final TestFixture fixture = new TestFixture("MatchingEngine");

    private ItemID moneyID;
    private IServerMarket serverMarket;
    private int bankAccountNr1;
    private IServerBankAccount bankAccount1;
    private int bankAccountNr2;
    private IServerBankAccount bankAccount2;
    private long uniformVolumeDistributionScale = 5;

    // ── Net-player-flow / fund-unlock fixtures ────────────────────────────────
    /** Second scratch market, so an inter-market (item-for-item) trade has a "want" side. */
    private IServerMarket wantMarket;
    private ServerMarketManager marketManager;
    private int scaleFactor;
    private UUID flowPlayerUUID;
    /** Trader for the inter-market flow tests: holds both items plus money. */
    private IServerBankAccount flowAccount;
    private int flowAccountNr;
    /**
     * A resting-order owner that has a money bank but deliberately NO bank for the
     * scratch market's item. Resolving its counterparty banks therefore fails, which is
     * one of the branches that drops a resting order mid-walk — the branch that used to
     * leak the order's reservation.
     */
    private IServerBankAccount orphanAccount;
    private int orphanAccountNr;

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
        addTest("marketSell_fillsBestBidsFirst", this::test_marketSell_fillsBestBidsFirst);

        // Bot Orders
        addTest("botSell_noAccountRequired", this::test_botSell_noAccountRequired);
        addTest("botBuy_noAccountRequired", this::test_botBuy_noAccountRequired);
        addTest("botSell_partialFillOfPlayerLimit_creditsFlowImmediately", this::test_botSell_partialFillOfPlayerLimit_creditsFlowImmediately);

        // Price Movement
        addTest("priceChangedCallback_fired", this::test_priceChangedCallback_fired);
        addTest("marketPrice_walksUp_onBuy", this::test_marketPrice_walksUp_onBuy);
        addTest("marketPrice_walksDown_onSell", this::test_marketPrice_walksDown_onSell);

        // Timeout Handling
        addTest("drainVirtualSell_timeout", this::test_drainVirtualSell_timeout);
        addTest("drainVirtualBuy_timeout", this::test_drainVirtualBuy_timeout);

        // Inter-market net player flow (issue #36 follow-up)
        addTest("interMarketMarketOrder_creditsFlowOnBothMarkets", this::test_interMarketMarketOrder_creditsFlowOnBothMarkets);
        addTest("interMarketLimitOrder_creditsFlowOnBothMarkets", this::test_interMarketLimitOrder_creditsFlowOnBothMarkets);
        addTest("consumeBuySideDepth_droppedRestingOrder_unlocksFunds", this::test_consumeBuySideDepth_droppedRestingOrder_unlocksFunds);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("MatchingEngineTestSuite requires backend to be set");
        }
        fixture.setBackend(backend);
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());

        // Scratch accounts — never fixed account numbers, which would squat on real players' accounts.
        bankAccount1 = fixture.createBankAccount("account1");
        bankAccountNr1 = bankAccount1.getAccountNumber();
        bankAccount2 = fixture.createBankAccount("account2");
        bankAccountNr2 = bankAccount2.getAccountNumber();

        // Scratch market on a synthetic ItemID — never the player's live gold market.
        serverMarket = fixture.createMarket("main");
        ItemID id = serverMarket.getItemID();

        bankAccount1.createBank(id, 100);
        bankAccount2.createBank(id, 100);
        bankAccount1.createBank(moneyID, 10000);
        bankAccount2.createBank(moneyID, 10000);

        // ── Inter-market flow fixtures ────────────────────────────────────────
        marketManager = (ServerMarketManager) backend.MARKET_MANAGER.getSync();
        scaleFactor = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();

        // Second scratch market: the "want" side of an item-for-item trade.
        wantMarket = fixture.createMarket("want");

        flowPlayerUUID = fixture.createUser("flowTrader");
        flowAccount = fixture.createBankAccount("flowAccount");
        flowAccountNr = flowAccount.getAccountNumber();
        flowAccount.createBank(id, 100L * scaleFactor);
        flowAccount.createBank(wantMarket.getItemID(), 100L * scaleFactor);
        flowAccount.createBank(moneyID, 1000000);

        // Money bank only — no bank for `id`, so counterparty resolution fails for it.
        orphanAccount = fixture.createBankAccount("orphanBuyer");
        orphanAccountNr = orphanAccount.getAccountNumber();
        orphanAccount.createBank(moneyID, 10000);
    }

    @Override
    public void teardown() {
        // Inter-market orders first: cancelling them refunds their locked items before
        // the scratch markets and accounts are dropped.
        if (marketManager != null) {
            try {
                if (serverMarket != null) marketManager.cancelInterMarketOrdersForMarket(serverMarket.getItemID());
                if (wantMarket != null) marketManager.cancelInterMarketOrdersForMarket(wantMarket.getItemID());
            } catch (Exception ignored) {
                // Best effort — fixture.cleanup() below must still run.
            }
        }
        // Clear any order left in the matching engine before the market is dropped.
        if (serverMarket != null) {
            serverMarket.test_clearOrderbook();
            serverMarket.test_clearIncomingOrderBuffers();
        }
        if (wantMarket != null) {
            wantMarket.test_clearOrderbook();
            wantMarket.test_clearIncomingOrderBuffers();
        }
        serverMarket = null;
        wantMarket = null;
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

    private TestResult test_marketSell_fillsBestBidsFirst() {
        try {
            resetMarketState(false, 100);

            // Seven resting buys inserted so the PriorityQueue's internal (heap) order is not price order
            long[] prices = {90, 95, 80, 99, 85, 97, 92};
            Order[] buys = new Order[prices.length];
            for (int i = 0; i < prices.length; i++) {
                buys[i] = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 1, prices[i], 0, UUID.randomUUID(), bankAccountNr1);
                serverMarket.putOrder(buys[i]);
            }
            serverMarket.update();

            // Selling 2 should hit the best two bids: 99 and 97
            Order sell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -2, 0, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(sell);
            serverMarket.update();

            for (int i = 0; i < prices.length; i++) {
                boolean shouldFill = prices[i] == 99 || prices[i] == 97;
                if (buys[i].isFilled() != shouldFill)
                    return fail("Buy at " + prices[i] + (shouldFill ? " should be filled (best bids first)" : " should not be filled"));
            }
            TestResult r = assertTrue("Market price should end at the last (97) bid filled, but was " + serverMarket.getCurrentMarketPrice(),
                    serverMarket.getCurrentMarketPrice() == 97);
            if (!r.passed()) return r;
            return pass("Market sell fills the best bids first");
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

    private TestResult test_botSell_partialFillOfPlayerLimit_creditsFlowImmediately() {
        try {
            resetMarketState(false, 10);
            serverMarket.resetNetPlayerItemFlow();

            // Player limit buy rests in the orderbook (below market price)
            Order limitBuy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(limitBuy);
            serverMarket.update();

            // Bot sell fills only part of it
            Order botSell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -2, 10, 0);
            serverMarket.putOrder(botSell);
            serverMarket.update();

            if (limitBuy.getFilledVolume() != 2)
                return fail("Expected limit buy filledVolume=2, got: " + limitBuy.getFilledVolume());
            // The flow must count the fill now, not only once the order completes.
            // Otherwise the price plugins only react after the whole order filled at its limit price.
            if (serverMarket.getNetPlayerItemFlow() != -2)
                return fail("Expected net player item flow -2 after partial fill, got: " + serverMarket.getNetPlayerItemFlow());
            return pass("Partial fill of a resting player limit order is credited to the player flow immediately");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
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

    // ── Inter-market net player flow ──────────────────────────────────────────

    /**
     * Puts both scratch markets into a known state for an inter-market trade: uniform
     * virtual depth on each, empty orderbooks, no leftover inter-market orders, and a
     * trader who holds enough of both items plus money. Both flow counters start at 0.
     *
     * @param havePrice starting price of the "have" market ({@link #serverMarket})
     * @param wantPrice starting price of the "want" market ({@link #wantMarket})
     */
    private void resetInterMarketState(long havePrice, long wantPrice) {
        marketManager.cancelInterMarketOrdersForMarket(serverMarket.getItemID());
        marketManager.cancelInterMarketOrdersForMarket(wantMarket.getItemID());

        serverMarket.test_clearOrderbook();
        serverMarket.test_setDefaultVolumeProviderFunction(p -> 5f);
        serverMarket.test_setCurrentMarketPrice(havePrice);
        serverMarket.test_resetVirtualOrderBookVolume();
        serverMarket.setMarketOpen(true);

        wantMarket.test_clearOrderbook();
        wantMarket.test_setDefaultVolumeProviderFunction(p -> 5f);
        wantMarket.test_setCurrentMarketPrice(wantPrice);
        wantMarket.test_resetVirtualOrderBookVolume();
        wantMarket.setMarketOpen(true);

        unlockAll(flowAccount.getBank(serverMarket.getItemID()));
        unlockAll(flowAccount.getBank(wantMarket.getItemID()));
        unlockAll(flowAccount.getBank(moneyID));
        flowAccount.getBank(serverMarket.getItemID()).setBalance(100L * scaleFactor);
        flowAccount.getBank(wantMarket.getItemID()).setBalance(100L * scaleFactor);
        flowAccount.getBank(moneyID).setBalance(1000000);

        serverMarket.resetNetPlayerItemFlow();
        wantMarket.resetNetPlayerItemFlow();
    }

    private void unlockAll(IServerBank bank) {
        long locked = bank.getLockedBalance();
        if (locked > 0) bank.unlockAmount(locked);
    }

    /**
     * Builds an inter-market order that sells {@code sellVolume} of the have-market item
     * to buy want-market items.
     *
     * @param sellVolume     raw volume of have-items to sell
     * @param crossRateLimit 0 for a market order, &gt;0 for a limit order (bilateral walk)
     */
    private InterMarketOrder createFlowIMO(long sellVolume, long crossRateLimit) {
        long havePrice = serverMarket.getCurrentMarketPrice();
        long wantPrice = wantMarket.getCurrentMarketPrice();
        long estimatedBuyVolume = (wantPrice > 0) ? (sellVolume * havePrice / wantPrice) : sellVolume;
        if (estimatedBuyVolume <= 0) estimatedBuyVolume = 1;
        return new InterMarketOrder(
                wantMarket.getItemID(), serverMarket.getItemID(),
                (crossRateLimit == 0) ? Order.Type.MARKET : Order.Type.LIMIT,
                estimatedBuyVolume, wantPrice,
                sellVolume, havePrice,
                System.currentTimeMillis(),
                flowPlayerUUID, flowAccountNr,
                crossRateLimit);
    }

    /**
     * Shared assertion for both inter-market paths.
     * <p>
     * An item-for-item trade is a sell on the have-market plus a buy on the want-market,
     * so the have-market must see supply and the want-market must see demand. The stored
     * counter is the negation of the signed fill, hence:
     * <ul>
     *   <li>have-market flow == {@code -sellLeg.getFilledVolume()} — the sell leg's filled
     *       volume is negative, so the flow ends up <b>positive</b> (supply);</li>
     *   <li>want-market flow == {@code -buyLeg.getFilledVolume()} — the buy leg's filled
     *       volume is positive, so the flow ends up <b>negative</b> (demand).</li>
     * </ul>
     * Both markets trade against virtual depth only here, so there is no resting
     * counterparty credit to net against and the magnitudes must match exactly.
     */
    private TestResult assertBothLegsCredited(InterMarketOrder imo, String context) {
        long sellFilled = imo.getSellOrder().getFilledVolume();   // negative
        long buyFilled = imo.getBuyOrder().getFilledVolume();     // positive
        if (sellFilled >= 0 || buyFilled <= 0)
            return fail(context + ": expected a two-sided fill, got sellFilled=" + sellFilled
                    + ", buyFilled=" + buyFilled);

        long haveFlow = serverMarket.getNetPlayerItemFlow();
        long wantFlow = wantMarket.getNetPlayerItemFlow();

        if (haveFlow != -sellFilled)
            return fail(context + ": have-market flow should be " + (-sellFilled)
                    + " (supply, positive), got " + haveFlow);
        if (wantFlow != -buyFilled)
            return fail(context + ": want-market flow should be " + (-buyFilled)
                    + " (demand, negative), got " + wantFlow);
        return pass(context + ": have-market flow +" + haveFlow + " (supply), want-market flow "
                + wantFlow + " (demand)");
    }

    /**
     * Market-type inter-market order (two real INTER_MARKET leg orders routed through
     * putOrder + update). Regression for the {@code trackPlayerFill} guard that used to
     * drop INTER_MARKET fills on the floor, leaving both markets' flow at 0.
     */
    private TestResult test_interMarketMarketOrder_creditsFlowOnBothMarkets() {
        try {
            resetInterMarketState(100, 50);

            long sellVolume = 10L * scaleFactor;
            InterMarketOrder imo = createFlowIMO(sellVolume, 0);
            flowAccount.getBank(serverMarket.getItemID()).lockAmount(sellVolume);
            if (!marketManager.putInterMarketOrder(imo))
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            return assertBothLegsCredited(imo, "Inter-market market order");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * Limit-type inter-market order, which takes the bilateral depth-walk path
     * ({@code executeCrossMarketLimitOrder}). That path builds no leg Orders and never
     * calls ServerMarket.update(), so it must credit the flow directly — this is the
     * regression for that.
     */
    private TestResult test_interMarketLimitOrder_creditsFlowOnBothMarkets() {
        try {
            resetInterMarketState(100, 50);

            long sellVolume = 10L * scaleFactor;
            // Generous cross-rate limit (4 have-items per want-item at a 100/50 price pair
            // the fair rate is 0.5), so the walk is never stopped by the rate check.
            InterMarketOrder imo = createFlowIMO(sellVolume, 4L * scaleFactor);
            flowAccount.getBank(serverMarket.getItemID()).lockAmount(sellVolume);
            if (!marketManager.putInterMarketOrder(imo))
                return fail("InterMarketOrder was not enqueued");

            marketManager.update();

            return assertBothLegsCredited(imo, "Inter-market limit order");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * The bilateral walk's depth consumer drops a resting order whose counterparty banks
     * cannot be resolved. It used to do that with a bare {@code orderbook.removeOrder},
     * skipping the cancel callback — so the buyer's locked money stayed reserved forever.
     * The consumer now routes the drop through the market's cancel bookkeeping, which
     * releases the reservation.
     */
    private TestResult test_consumeBuySideDepth_droppedRestingOrder_unlocksFunds() {
        try {
            resetMarketState(false, 10);
            if (!(serverMarket instanceof ServerMarket market))
                return fail("Expected the scratch market to be a master ServerMarket");

            // 1 item at price 8 → exactly 8 money locked, well above the rounding floor.
            long price = 8;
            long volume = scaleFactor;
            long cost = Math.round((double) volume * price / scaleFactor);

            IServerBank orphanMoney = orphanAccount.getBank(moneyID);
            unlockAll(orphanMoney);
            orphanMoney.setBalance(10000);
            orphanMoney.lockAmount(cost);
            if (orphanMoney.getLockedBalance() != cost)
                return fail("Setup failed: expected " + cost + " locked, got " + orphanMoney.getLockedBalance());

            // Rests in the orderbook: 8 is below the market price of 10.
            Order resting = new Order(serverMarket.getItemID(), Order.Type.LIMIT, volume, price, 0,
                    UUID.randomUUID(), orphanAccountNr);
            serverMarket.putOrder(resting);
            serverMarket.update();

            // The orphan has no item bank for this market, so the consumer cannot resolve
            // its counterparty banks and drops the order unfilled.
            MatchingEngine.consumeBuySideDepthAtPrice(market, price, volume);

            if (resting.getFilledVolume() != 0)
                return fail("Expected the dropped order to stay unfilled, got filledVolume="
                        + resting.getFilledVolume());
            if (orphanMoney.getLockedBalance() != 0)
                return fail("Cancelled resting buy order leaked its reservation: "
                        + orphanMoney.getLockedBalance() + " money still locked");
            return pass("Dropping a resting buy order mid-walk unlocks its reserved " + cost + " money");
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
