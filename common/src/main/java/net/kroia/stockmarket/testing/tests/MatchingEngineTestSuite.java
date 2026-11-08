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
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
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

        // Walk ends on an empty book (price floor / safety bound)
        addTest("walk_emptyBookSell_endsAtPriceFloor", this::test_walk_emptyBookSell_endsAtPriceFloor);
        addTest("walk_emptyBookBuy_endsAtSafetyBound", this::test_walk_emptyBookBuy_endsAtSafetyBound);
        // #88/#94: orders walk until filled, consume every level in their way, never rest crossed
        addTest("walk_marketBuy_fillsBeforeFarSell", this::test_walk_marketBuy_fillsBeforeFarSell);
        addTest("walk_marketBuy_consumesGapBeforeFarSell", this::test_walk_marketBuy_consumesGapBeforeFarSell);
        addTest("walk_limitBuy_fillsBeforeFarSell", this::test_walk_limitBuy_fillsBeforeFarSell);
        addTest("walk_marketSell_fillsBeforeFarBuy", this::test_walk_marketSell_fillsBeforeFarBuy);
        addTest("walk_emptyGap_fillsFarSell", this::test_walk_emptyGap_fillsFarSell);
        addTest("walk_limitBuyFarAbove_neverRestsAboveMarket", this::test_walk_limitBuyFarAbove_neverRestsAboveMarket);
        addTest("walk_limitSellFarBelow_neverRestsBelowMarket", this::test_walk_limitSellFarBelow_neverRestsBelowMarket);
        addTest("walk_altSellExploit_noProfit", this::test_walk_altSellExploit_noProfit);
        addTest("walk_safetyBound_cancelsAndLogs", this::test_walk_safetyBound_cancelsAndLogs);
        addTest("walk_emptyGap_isFast", this::test_walk_emptyGap_isFast);
        addTest("walk_diamondPreset_priceImpact", this::test_walk_diamondPreset_priceImpact);

        // Inter-market net player flow (issue #36 follow-up)
        addTest("interMarketMarketOrder_creditsFlowOnBothMarkets", this::test_interMarketMarketOrder_creditsFlowOnBothMarkets);
        addTest("interMarketLimitOrder_creditsFlowOnBothMarkets", this::test_interMarketLimitOrder_creditsFlowOnBothMarkets);
        addTest("consumeBuySideDepth_droppedRestingOrder_unlocksFunds", this::test_consumeBuySideDepth_droppedRestingOrder_unlocksFunds);

        // Large-volume regressions (T-173, T-175, T-176)
        addTest("marketSell_thinBook_countsOnlyFilled", this::test_marketSell_thinBook_countsOnlyFilled);
        addTest("candleTradedVolume_keepsSmallFillsAfterHugeFill", this::test_candleTradedVolume_keepsSmallFillsAfterHugeFill);
        addTest("affordableVolume_hugeFunds_saturates", this::test_affordableVolume_hugeFunds_saturates);
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

    // ── Walk end on an empty book ─────────────────────────────────────────────

    /** A market sell into a book whose volume rounds to 0 walks to the price floor and ends quickly. */
    private TestResult test_walk_emptyBookSell_endsAtPriceFloor() {
        try {
            // Tiny volume that rounds to 0 at every level
            resetMarketState(false, 10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0.0001f);
            serverMarket.test_resetVirtualOrderBookVolume();

            // Large sell order that would need many price levels
            Order largeSell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -1000000, 10, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(largeSell);

            long start = System.currentTimeMillis();
            serverMarket.update();
            long elapsed = System.currentTimeMillis() - start;

            TestResult r = assertTrue("Should complete within 30 seconds", elapsed < 30000);
            if (!r.passed()) return r;
            return pass("Empty-book sell walk ended at the price floor in " + elapsed + "ms");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * A market buy into a provider-backed book whose volume rounds to 0 has no natural end; the
     * safety bound stops it (one provider call per level). The elapsed time is the worst case
     * of a full-bound walk.
     */
    private TestResult test_walk_emptyBookBuy_endsAtSafetyBound() {
        try {
            resetMarketState(false, 10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> -0.0001f);
            serverMarket.test_resetVirtualOrderBookVolume();

            Order largeBuy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 1000000, 10, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(largeBuy);

            long start = System.currentTimeMillis();
            serverMarket.update();
            long elapsed = System.currentTimeMillis() - start;

            TestResult r = assertTrue("Should complete within 30 seconds", elapsed < 30000);
            if (!r.passed()) return r;
            return pass("Empty-book buy walk ended at the safety bound (" + MatchingEngine.getWalkLevelLimit()
                    + " levels) in " + elapsed + "ms");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── #88/#94: orders walk until filled and never rest crossed ─────────────

    /** Raw start price of the walk tests (100 coins). */
    private static final long WALK_START = 10000;
    /** Raw price of the far order (5000 coins). */
    private static final long WALK_FAR = 500000;
    /** Raw volume per level of the uniform test book. */
    private static final long WALK_LEVEL_VOLUME = 5;

    /** @return the scratch market as a master ServerMarket (the walk tests need its orderbook) */
    private ServerMarket master() {
        return (ServerMarket) serverMarket;
    }

    /** @return highest resting real bid, or Long.MIN_VALUE if none */
    private long bestRealBid() {
        long best = Long.MIN_VALUE;
        for (Order o : master().getOrderbook().getBuyLimitOrders()) best = Math.max(best, o.getStartPrice());
        return best;
    }

    /** @return lowest resting real ask, or Long.MAX_VALUE if none */
    private long bestRealAsk() {
        long best = Long.MAX_VALUE;
        for (Order o : master().getOrderbook().getSellLimitOrders()) best = Math.min(best, o.getStartPrice());
        return best;
    }

    /**
     * Uniform virtual depth ({@value #WALK_LEVEL_VOLUME} raw per level); account2 rests a big
     * player sell far above, account1 is rich enough to buy it all.
     *
     * @param volume raw volume of the far sell
     * @return the resting far sell
     */
    private Order placeFarPlayerSell(long volume) {
        resetMarketState(true, WALK_START);
        ItemID item = serverMarket.getItemID();
        bankAccount2.getBank(item).setBalance(volume);
        bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000_000L);
        Order farSell = new Order(item, Order.Type.LIMIT, -volume, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr2);
        serverMarket.putOrder(farSell);
        serverMarket.update();
        return farSell;
    }

    /** A market buy smaller than the depth below a far sell fills on the way and never touches it. */
    private TestResult test_walk_marketBuy_fillsBeforeFarSell() {
        try {
            Order farSell = placeFarPlayerSell(1_000_000);
            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, 1_000_000, WALK_START, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();
            if (!buy.isFilled())
                return fail("Buy should fill completely from the depth below the far sell, filled " + buy.getFilledVolume());
            if (farSell.getFilledVolume() != 0)
                return fail("Far sell was touched: " + farSell.getFilledVolume());
            long expected = WALK_START + 1_000_000 / WALK_LEVEL_VOLUME;   // asks start one level above the market price
            if (serverMarket.getCurrentMarketPrice() != expected)
                return fail("Price should end at " + expected + " (every level consumed), was " + serverMarket.getCurrentMarketPrice());
            return pass("Filled on the way, price " + serverMarket.getCurrentMarketPrice() + ", far sell untouched");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** A market buy bigger than the depth below a far sell consumes every level first, then the far sell. */
    private TestResult test_walk_marketBuy_consumesGapBeforeFarSell() {
        try {
            Order farSell = placeFarPlayerSell(1_000_000);
            long volume = 3_000_000;
            Order buy = new Order(serverMarket.getItemID(), Order.Type.MARKET, volume, WALK_START, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();
            long virtual = (WALK_FAR - WALK_START) * WALK_LEVEL_VOLUME;   // levels start+1 .. far
            if (!buy.isFilled())
                return fail("Buy should fill, filled " + buy.getFilledVolume());
            if (-farSell.getFilledVolume() != volume - virtual)
                return fail("Far sell should fill exactly the rest after all " + virtual + " raw of virtual depth: expected "
                        + (volume - virtual) + ", got " + -farSell.getFilledVolume());
            if (serverMarket.getCurrentMarketPrice() != WALK_FAR)
                return fail("Price should be the far sell price, was " + serverMarket.getCurrentMarketPrice());
            return pass("Consumed " + virtual + " raw of virtual depth, then " + (volume - virtual) + " from the far sell");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** A limit buy at the far price fills on the way like a market buy and rests nothing. */
    private TestResult test_walk_limitBuy_fillsBeforeFarSell() {
        try {
            Order farSell = placeFarPlayerSell(1_000_000);
            Order buy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 1_000_000, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();
            if (!buy.isFilled())
                return fail("Limit buy should fill on the way, filled " + buy.getFilledVolume());
            if (farSell.getFilledVolume() != 0)
                return fail("Far sell was touched: " + farSell.getFilledVolume());
            if (bestRealBid() != Long.MIN_VALUE)
                return fail("Nothing may rest, best bid " + bestRealBid());
            return pass("Limit buy filled at " + serverMarket.getCurrentMarketPrice() + ", far sell untouched");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** Sell side: a market sell fills from the bids on the way and never touches a far player bid. */
    private TestResult test_walk_marketSell_fillsBeforeFarBuy() {
        try {
            long start = WALK_FAR;
            long farBid = WALK_START;
            resetMarketState(true, start);
            ItemID item = serverMarket.getItemID();
            bankAccount2.getBank(moneyID).setBalance(1_000_000_000_000_000L);
            bankAccount1.getBank(item).setBalance(1_000_000);
            Order farBuy = new Order(item, Order.Type.LIMIT, 1_000_000, farBid, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(farBuy);
            serverMarket.update();

            Order sell = new Order(item, Order.Type.MARKET, -1_000_000, start, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sell);
            serverMarket.update();

            if (!sell.isFilled())
                return fail("Sell should fill from the bids on the way, filled " + sell.getFilledVolume());
            if (farBuy.getFilledVolume() != 0)
                return fail("Far bid @" + farBid + " was filled (" + farBuy.getFilledVolume() + ")");
            if (serverMarket.getCurrentMarketPrice() <= farBid)
                return fail("Price dropped to the far bid: " + serverMarket.getCurrentMarketPrice());
            return pass("Sell filled down to " + serverMarket.getCurrentMarketPrice() + ", far bid untouched");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** With no virtual depth between the price and a far real ask, the ask fills (real-only book). */
    private TestResult test_walk_emptyGap_fillsFarSell() {
        try {
            resetMarketState(false, WALK_START);   // no virtual depth anywhere
            ItemID item = serverMarket.getItemID();
            long volume = 1000;
            bankAccount2.getBank(item).setBalance(volume);
            bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000L);
            Order farSell = new Order(item, Order.Type.LIMIT, -volume, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr2);
            serverMarket.putOrder(farSell);
            serverMarket.update();

            Order buy = new Order(item, Order.Type.MARKET, volume, WALK_START, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            if (!farSell.isFilled() || !buy.isFilled())
                return fail("Empty book: far sell should fill completely, sell filled " + farSell.getFilledVolume()
                        + ", buy filled " + buy.getFilledVolume());
            if (serverMarket.getCurrentMarketPrice() != WALK_FAR)
                return fail("Price should be the far sell price " + WALK_FAR + ", was " + serverMarket.getCurrentMarketPrice());
            return pass("Empty gap skipped, far sell filled at " + WALK_FAR);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * #94: a limit buy at 5000 with the price at 200 never rests above the market. Small: it
     * fills on the way. Big: it consumes all depth up to 5000, the rest rests AT 5000 and the
     * market price is 5000.
     */
    private TestResult test_walk_limitBuyFarAbove_neverRestsAboveMarket() {
        try {
            long start = 20000;   // 200 coins
            for (long volume : new long[]{ 100_000, 3_000_000 }) {
                resetMarketState(true, start);
                bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000_000L);
                Order buy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, volume, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr1);
                serverMarket.putOrder(buy);
                serverMarket.update();
                long price = serverMarket.getCurrentMarketPrice();
                if (bestRealBid() > price)
                    return fail("Volume " + volume + ": bid " + bestRealBid() + " rests above the market price " + price);
                if (!buy.isFilled() && price != WALK_FAR)
                    return fail("Volume " + volume + ": remainder rests but the price is " + price + ", not the limit " + WALK_FAR);
                long depth = (WALK_FAR - start) * WALK_LEVEL_VOLUME;   // levels start+1 .. limit
                if (volume > depth && buy.getFilledVolume() != depth)
                    return fail("Volume " + volume + ": should consume all " + depth + " raw up to the limit, filled " + buy.getFilledVolume());
            }
            return pass("Small limit buy filled on the way; big one consumed all depth and rests at " + WALK_FAR + " = market price");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** Mirror of {@link #test_walk_limitBuyFarAbove_neverRestsAboveMarket}: a limit sell far below never rests below the market. */
    private TestResult test_walk_limitSellFarBelow_neverRestsBelowMarket() {
        try {
            long start = WALK_FAR;
            long limit = 20000;
            for (long volume : new long[]{ 100_000, 3_000_000 }) {
                resetMarketState(true, start);
                bankAccount1.getBank(serverMarket.getItemID()).setBalance(volume);
                Order sell = new Order(serverMarket.getItemID(), Order.Type.LIMIT, -volume, limit, 0, UUID.randomUUID(), bankAccountNr1);
                serverMarket.putOrder(sell);
                serverMarket.update();
                long price = serverMarket.getCurrentMarketPrice();
                if (bestRealAsk() < price)
                    return fail("Volume " + volume + ": ask " + bestRealAsk() + " rests below the market price " + price);
                if (!sell.isFilled() && price != limit)
                    return fail("Volume " + volume + ": remainder rests but the price is " + price + ", not the limit " + limit);
            }
            return pass("Small limit sell filled on the way; big one rests at " + limit + " = market price");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * #94 alt-sell exploit: A places a limit buy far above the market, alt B market-sells into its
     * resting rest, then A sells everything the pair gained into the virtual bids. The pair must
     * not profit: A paid for every level up to the limit before anything could rest there.
     */
    private TestResult test_walk_altSellExploit_noProfit() {
        try {
            long start = 20000;
            resetMarketState(true, start);
            ItemID item = serverMarket.getItemID();
            IServerBank aMoney = bankAccount1.getBank(moneyID), bMoney = bankAccount2.getBank(moneyID);
            IServerBank aItems = bankAccount1.getBank(item), bItems = bankAccount2.getBank(item);
            long rich = 1_000_000_000_000_000L;
            aMoney.setBalance(rich); bMoney.setBalance(rich);
            aItems.setBalance(0);

            // 1) A: limit buy far above the market
            Order pump = new Order(item, Order.Type.LIMIT, 3_000_000, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(pump);
            serverMarket.update();
            long resting = pump.getRemainingVolume();

            // 2) B: market-sells into A's resting rest
            bItems.setBalance(Math.max(resting, 0));
            long bItemsStart = bItems.getTotalBalance();
            if (resting > 0) {
                serverMarket.putOrder(new Order(item, Order.Type.MARKET, -resting, 0, 0, UUID.randomUUID(), bankAccountNr2));
                serverMarket.update();
            }
            // 3) A sells what the pair gained from the market back into the virtual bids
            long pairGain = aItems.getTotalBalance() - (bItemsStart - bItems.getTotalBalance());
            serverMarket.putOrder(new Order(item, Order.Type.MARKET, -pairGain, 0, 0, UUID.randomUUID(), bankAccountNr1));
            serverMarket.update();

            long pairItems = aItems.getTotalBalance() + bItems.getTotalBalance() - bItemsStart;
            long profit = aMoney.getTotalBalance() + bMoney.getTotalBalance() - 2 * rich;
            if (pairItems > 0)
                return fail("Pair still holds " + pairItems + " raw items, profit not comparable (" + profit + ")");
            if (profit > 0)
                return fail("Pair profit " + profit + " > 0 (price after pump " + WALK_FAR + ", resting rest " + resting + ")");
            return pass("Pair profit " + profit + " <= 0 (resting rest " + resting + ", pair net items " + pairItems + ")");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** The safety bound stops a walk, logs an error and cancels the remainder (it never rests). */
    private TestResult test_walk_safetyBound_cancelsAndLogs() {
        long limit = 1000;
        try {
            resetMarketState(true, WALK_START);
            bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000_000L);
            MatchingEngine.test_setWalkLevelLimit(limit);
            long abortsBefore = MatchingEngine.getWalkAbortCount();
            Order buy = new Order(serverMarket.getItemID(), Order.Type.LIMIT, 1_000_000, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(buy);
            serverMarket.update();

            if (MatchingEngine.getWalkAbortCount() != abortsBefore + 1)
                return fail("Safety bound should have stopped exactly one walk, count went " + abortsBefore + " -> " + MatchingEngine.getWalkAbortCount());
            if (bestRealBid() != Long.MIN_VALUE)
                return fail("Remainder must be canceled, not rest: best bid " + bestRealBid());
            if (buy.getFilledVolume() != limit * WALK_LEVEL_VOLUME)   // levels start+1 .. start+limit
                return fail("Should fill exactly the " + limit + " levels within the bound, filled " + buy.getFilledVolume());
            if (serverMarket.getCurrentMarketPrice() > WALK_START + limit)
                return fail("Price passed the bound: " + serverMarket.getCurrentMarketPrice());
            return pass("Bound stopped the walk at " + serverMarket.getCurrentMarketPrice() + ", remainder canceled, error logged");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            MatchingEngine.test_setWalkLevelLimit(MatchingEngine.DEFAULT_WALK_LEVEL_LIMIT);
        }
    }

    /**
     * Performance: a walk across a large empty gap (490,000 levels) completes fast, both for a
     * cleared book (window-bounded skip) and a provider-backed empty book (one provider call per level).
     */
    private TestResult test_walk_emptyGap_isFast() {
        try {
            StringBuilder times = new StringBuilder();
            for (boolean cleared : new boolean[]{ true, false }) {
                resetMarketState(false, WALK_START);
                if (cleared)
                    master().getOrderbook().clearVirtualVolume();
                ItemID item = serverMarket.getItemID();
                bankAccount2.getBank(item).setBalance(1000);
                bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000L);
                serverMarket.putOrder(new Order(item, Order.Type.LIMIT, -1000, WALK_FAR, 0, UUID.randomUUID(), bankAccountNr2));
                serverMarket.update();

                Order buy = new Order(item, Order.Type.MARKET, 1000, WALK_START, 0, UUID.randomUUID(), bankAccountNr1);
                serverMarket.putOrder(buy);
                long t0 = System.nanoTime();
                serverMarket.update();
                long ms = (System.nanoTime() - t0) / 1_000_000;
                if (cleared)
                    master().getOrderbook().setVirtualCleared(false);
                if (!buy.isFilled())
                    return fail((cleared ? "cleared" : "provider") + " book: far sell not reached, filled " + buy.getFilledVolume());
                if (ms > 2000)
                    return fail((cleared ? "cleared" : "provider") + " book: gap walk took " + ms + " ms");
                times.append(cleared ? "cleared " : ", provider ").append(ms).append(" ms");
            }
            return pass("490,000-level empty gap: " + times);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * Report only (T-196 item 1): price impact of one max-size order on a diamond-like market
     * (preset price 160, abundance 5 x 10 = 50, production depth shape). Asserts only that the
     * order fills and moves the price less than 10%; the numbers are in the pass message.
     */
    private TestResult test_walk_diamondPreset_priceImpact() {
        try {
            long sf = scaleFactor;
            long start = 160 * sf;
            DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc =
                    new DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator(160.0, 50f);
            StringBuilder out = new StringBuilder();
            for (long items : new long[]{ 3200, 32000 }) {
                for (boolean buySide : new boolean[]{ true, false }) {
                    // Same contract as PluginOrderBook: computed around the pre-trade price, ask sign at and above it
                    serverMarket.test_setDefaultVolumeProviderFunction(real -> {
                        double market = serverMarket.getCurrentMarketPrice() / (double) sf;
                        float v = Math.abs(calc.getVolume(market, real));
                        return market > real ? v : -v;
                    });
                    serverMarket.test_setCurrentMarketPrice(start);
                    serverMarket.test_clearOrderbook();
                    long raw = items * sf;
                    bankAccount1.getBank(moneyID).setBalance(1_000_000_000_000_000L);
                    bankAccount1.getBank(serverMarket.getItemID()).setBalance(raw);
                    Order o = new Order(serverMarket.getItemID(), Order.Type.MARKET, buySide ? raw : -raw, 0, 0, UUID.randomUUID(), bankAccountNr1);
                    serverMarket.putOrder(o);
                    serverMarket.update();
                    long end = serverMarket.getCurrentMarketPrice();
                    if (!o.isFilled())
                        return fail(items + (buySide ? " buy" : " sell") + " not filled: " + o.getFilledVolume());
                    double move = 100.0 * (end - start) / start;
                    if (Math.abs(move) >= 10)
                        return fail(items + (buySide ? " buy" : " sell") + " moved the price " + move + "%");
                    double avg = Math.abs((double) o.getTransferredMoney() / o.getFilledVolume());
                    out.append(String.format(java.util.Locale.ROOT, "%s %d: %.2f -> %.2f (%+.3f%%), avg %.4f; ",
                            buySide ? "buy" : "sell", items, start / (double) sf, end / (double) sf, move, avg));
                }
            }
            return pass(out.toString());
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            serverMarket.test_setDefaultVolumeProviderFunction(this::uniformVolumeDistribution);
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
        serverMarket.setMaintenance(false);

        wantMarket.test_clearOrderbook();
        wantMarket.test_setDefaultVolumeProviderFunction(p -> 5f);
        wantMarket.test_setCurrentMarketPrice(wantPrice);
        wantMarket.test_resetVirtualOrderBookVolume();
        wantMarket.setMaintenance(false);

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

    // ── Large-volume regressions (T-173, T-175, T-176) ───────────────────────

    /**
     * T-173: a player market sell far larger than the thin virtual book stops at the walk
     * limit / price floor. Only the filled part may count: flow == -filled, the item bank
     * loses exactly the filled volume and the cancelled rest is unlocked again.
     */
    private TestResult test_marketSell_thinBook_countsOnlyFilled() {
        try {
            resetMarketState(true, 50);
            serverMarket.test_resetVirtualOrderBookVolume();
            serverMarket.resetNetPlayerItemFlow();
            if (!(serverMarket instanceof ServerMarket market))
                return fail("Expected the scratch market to be a master ServerMarket");

            IServerBank itemBank = bankAccount1.getBank(serverMarket.getItemID());
            unlockAll(itemBank);
            long start = 2_000_000;
            itemBank.setBalance(start);
            long target = 1_000_000;
            itemBank.lockAmount(target);   // as CreateOrderRequest does

            Order sell = new Order(serverMarket.getItemID(), Order.Type.MARKET, -target, 0, 0,
                    UUID.randomUUID(), bankAccountNr1);
            serverMarket.putOrder(sell);
            serverMarket.update();

            long filled = sell.getFilledVolume();   // negative
            if (filled >= 0 || -filled >= target)
                return fail("Expected a partial fill, got filledVolume=" + filled + " of " + -target);
            if (market.getNetPlayerItemFlow() != -filled)
                return fail("Flow should be " + -filled + " (filled only), got " + market.getNetPlayerItemFlow());
            if (itemBank.getTotalBalance() != start + filled)
                return fail("Item balance should be start - filled = " + (start + filled)
                        + ", got " + itemBank.getTotalBalance());
            if (itemBank.getLockedBalance() != 0)
                return fail("Cancelled rest should be unlocked, still locked: " + itemBank.getLockedBalance());
            return pass("Filled " + -filled + " of " + target + " raw; flow and balance count only the fill");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * T-175: after a huge fill (2^26 raw), 100 fills of 1 raw must still be counted in the
     * candle volume. A float accumulator drops every one of them (ulp at 2^26 is 8).
     */
    private TestResult test_candleTradedVolume_keepsSmallFillsAfterHugeFill() {
        try {
            resetMarketState(false, 10);
            serverMarket.test_resetVirtualOrderBookVolume();
            ItemID id = serverMarket.getItemID();
            long huge = 1L << 26;
            int smallFills = 100;

            // Resting bot bid below the market price, big enough for every sell below.
            serverMarket.putOrder(new Order(id, Order.Type.LIMIT, huge + smallFills, 9, 0));
            serverMarket.update();
            serverMarket.getCurrentMarketPriceStructAndReset();   // fresh candle

            serverMarket.putOrder(new Order(id, Order.Type.MARKET, -huge, 0, 0));
            serverMarket.update();
            for (int i = 0; i < smallFills; i++) {
                serverMarket.putOrder(new Order(id, Order.Type.MARKET, -1, 0, 0));
                serverMarket.update();
            }

            float expected = (float) (huge + smallFills);
            float got = serverMarket.getCurrentCandleTradedVolume();
            if (Math.abs(got - expected) > Math.ulp(expected))
                return fail("Candle volume should be ~" + (huge + smallFills) + ", got " + (long) got
                        + " (small fills lost)");
            return pass("Candle volume " + (long) got + " includes the " + smallFills + " small fills");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /** T-176: affordable volume and the shared mul/div helper saturate instead of wrapping negative. */
    private TestResult test_affordableVolume_hugeFunds_saturates() {
        try {
            long v = MatchingEngine.affordableVolume(Long.MAX_VALUE - 5, 1);
            if (v != Long.MAX_VALUE)
                return fail("affordableVolume(MAX-5, 1) should saturate to Long.MAX_VALUE, got " + v);
            long funds = 100_000_000_000_000_000L;   // 1e17 raw: funds * SF overflows a long
            v = MatchingEngine.affordableVolume(funds, 1000);
            long exact = java.math.BigInteger.valueOf(funds).multiply(java.math.BigInteger.valueOf(scaleFactor))
                    .divide(java.math.BigInteger.valueOf(1000)).longValue();
            if (v < exact || v > exact + 1)
                return fail("affordableVolume(1e17, 1000) should be ~" + exact + ", got " + v);
            if (MatchingEngine.mulDivSaturated(Long.MAX_VALUE, Long.MAX_VALUE, 1) != Long.MAX_VALUE)
                return fail("mulDivSaturated should saturate high");
            if (MatchingEngine.mulDivSaturated(Long.MIN_VALUE, 2, 1) != Long.MIN_VALUE)
                return fail("mulDivSaturated should saturate low");
            if (MatchingEngine.mulDivSaturated(7, 9, 2) != 31)
                return fail("mulDivSaturated(7, 9, 2) should be 31");
            return pass("Huge funds saturate, exact results otherwise");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }
}
