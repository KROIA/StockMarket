package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ServerMarketTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        ServerMarketTestSuite.backend = backend;
    }

    private ItemID itemID;
    private ItemID moneyID;
    private IServerMarket serverMarket;
    private int bankAccountNr;
    private IServerBankAccount bankAccount;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.SERVER_MARKET;
    }

    @Override
    public void registerTests() {
        // Order Routing
        addTest("putOrder_marketClosed_rejected", this::test_putOrder_marketClosed_rejected);
        addTest("putOrder_alreadyFilled_rejected", this::test_putOrder_alreadyFilled_rejected);
        addTest("putOrder_wrongItemID_rejected", this::test_putOrder_wrongItemID_rejected);
        addTest("putOrder_buyMarket_goesToBuffer", this::test_putOrder_buyMarket_goesToBuffer);
        addTest("putOrder_sellMarket_goesToBuffer", this::test_putOrder_sellMarket_goesToBuffer);
        addTest("putOrder_buyLimit_goesToBuffer", this::test_putOrder_buyLimit_goesToBuffer);
        addTest("putOrder_sellLimit_goesToBuffer", this::test_putOrder_sellLimit_goesToBuffer);

        // Market Open/Close
        addTest("update_marketClosed_noop", this::test_update_marketClosed_noop);
        addTest("update_marketOpen_processesOrders", this::test_update_marketOpen_processesOrders);

        // Default Volume Provider
        addTest("defaultVolumeProvider_nullFunction", this::test_defaultVolumeProvider_nullFunction);
        addTest("defaultVolumeProvider_functionReturnsNull", this::test_defaultVolumeProvider_functionReturnsNull);

        // Save/Load
        addTest("save_load_roundTrip", this::test_save_load_roundTrip);
        addTest("load_missingOrderbook", this::test_load_missingOrderbook);
        addTest("load_restoresCandleState", this::test_load_restoresCandleState);

        // Candle Tracking
        addTest("getCurrentMarketPriceStructAndReset_resets", this::test_getCurrentMarketPriceStructAndReset_resets);

        // Cancel Order
        addTest("cancelOrder_existing_limit", this::test_cancelOrder_existingLimit);
        addTest("cancelOrder_nonexistent", this::test_cancelOrder_nonexistent);
        addTest("cancelOrder_wrong_executor", this::test_cancelOrder_wrongExecutor);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("ServerMarketTestSuite requires backend to be set");
        }
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        itemID = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        serverMarket = backend.MARKET_MANAGER.getSync().createMarket(itemID);

        bankAccount = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(2);
        if (bankAccount == null)
            bankAccount = backend.BANK_SYSTEM_API.getServerBankManager().getSync().createBankAccount("ServerMarketTest");
        if (bankAccount == null)
            throw new RuntimeException("Can't create ServerMarketTest bank account");
        bankAccountNr = bankAccount.getAccountNumber();
        bankAccount.createBank(itemID, 100);
        bankAccount.createBank(moneyID, 10000);
    }

    @Override
    public void teardown() {
        if (serverMarket != null) {
            serverMarket.setMarketOpen(true);
            serverMarket.test_clearOrderbook();
            serverMarket.test_setDefaultVolumeProviderFunction(null);
            serverMarket.test_resetVirtualOrderBookVolume();
        }
    }

    private void resetMarket(long price, boolean open) {
        serverMarket.test_setCurrentMarketPrice(price);
        serverMarket.test_clearOrderbook();
        serverMarket.test_clearIncomingOrderBuffers();
        serverMarket.setMarketOpen(open);
        bankAccount.getBank(itemID).setBalance(100);
        bankAccount.getBank(moneyID).setBalance(10000);
    }

    // ── Order Routing ────────────────────────────────────────────────────────

    private TestResult test_putOrder_marketClosed_rejected() {
        try {
            resetMarket(100, false);

            Order order = new Order(itemID, Order.Type.MARKET, 5, 100, 0, UUID.randomUUID(), bankAccountNr);
            boolean result = serverMarket.putOrder(order);

            TestResult r = assertFalse("putOrder should return false when market is closed", result);
            if (!r.passed()) return r;
            return pass("Order correctly rejected when market is closed");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_alreadyFilled_rejected() {
        try {
            resetMarket(100, true);

            // Create an order that is already filled (filledVolume == targetVolume)
            Order order = new Order(itemID, Order.Type.MARKET, 5, 100, 0, UUID.randomUUID(), bankAccountNr);
            order.edit(5, -500); // Fill it completely

            boolean result = serverMarket.putOrder(order);

            TestResult r = assertFalse("putOrder should return false for already filled order", result);
            if (!r.passed()) return r;
            return pass("Already filled order correctly rejected");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_wrongItemID_rejected() {
        try {
            resetMarket(100, true);

            ItemID wrongItem = new ItemID((short) 9999);
            Order order = new Order(wrongItem, Order.Type.MARKET, 5, 100, 0, UUID.randomUUID(), bankAccountNr);

            boolean result = serverMarket.putOrder(order);

            TestResult r = assertFalse("putOrder should return false for wrong itemID", result);
            if (!r.passed()) return r;
            return pass("Order with wrong itemID correctly rejected");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_buyMarket_goesToBuffer() {
        try {
            resetMarket(100, true);

            ServerMarket sm = (ServerMarket) serverMarket;
            Order order = new Order(itemID, Order.Type.MARKET, 5, 100, 0, UUID.randomUUID(), bankAccountNr);
            boolean result = serverMarket.putOrder(order);

            TestResult r = assertTrue("putOrder should return true", result);
            if (!r.passed()) return r;
            r = assertTrue("Buy market buffer should contain the order",
                    sm.getIncomingBuyMarketOrders().contains(order));
            if (!r.passed()) return r;
            return pass("Buy market order added to correct input buffer");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_sellMarket_goesToBuffer() {
        try {
            resetMarket(100, true);

            ServerMarket sm = (ServerMarket) serverMarket;
            Order order = new Order(itemID, Order.Type.MARKET, -5, 100, 0, UUID.randomUUID(), bankAccountNr);
            boolean result = serverMarket.putOrder(order);

            TestResult r = assertTrue("putOrder should return true", result);
            if (!r.passed()) return r;
            r = assertTrue("Sell market buffer should contain the order",
                    sm.getIncomingSellMarketOrders().contains(order));
            if (!r.passed()) return r;
            return pass("Sell market order added to correct input buffer");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_buyLimit_goesToBuffer() {
        try {
            resetMarket(100, true);

            ServerMarket sm = (ServerMarket) serverMarket;
            Order order = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, UUID.randomUUID(), bankAccountNr);
            boolean result = serverMarket.putOrder(order);

            TestResult r = assertTrue("putOrder should return true", result);
            if (!r.passed()) return r;
            r = assertTrue("Buy limit buffer should contain the order",
                    sm.getIncomingBuyLimitOrders().contains(order));
            if (!r.passed()) return r;
            return pass("Buy limit order added to correct input buffer");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_sellLimit_goesToBuffer() {
        try {
            resetMarket(100, true);

            ServerMarket sm = (ServerMarket) serverMarket;
            Order order = new Order(itemID, Order.Type.LIMIT, -5, 110, 0, UUID.randomUUID(), bankAccountNr);
            boolean result = serverMarket.putOrder(order);

            TestResult r = assertTrue("putOrder should return true", result);
            if (!r.passed()) return r;
            r = assertTrue("Sell limit buffer should contain the order",
                    sm.getIncomingSellLimitOrders().contains(order));
            if (!r.passed()) return r;
            return pass("Sell limit order added to correct input buffer");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Market Open/Close ────────────────────────────────────────────────────

    private TestResult test_update_marketClosed_noop() {
        try {
            resetMarket(100, false);

            // Place an order via the orderbook directly
            Order order = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, UUID.randomUUID(), bankAccountNr);
            serverMarket.getOrderbook().putOrder(order);

            long priceBefore = serverMarket.getCurrentMarketPrice();
            serverMarket.update();
            long priceAfter = serverMarket.getCurrentMarketPrice();

            TestResult r = assertEquals("Price should not change when market is closed",
                    priceBefore, priceAfter);
            if (!r.passed()) return r;
            return pass("update() is a no-op when market is closed");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_update_marketOpen_processesOrders() {
        try {
            resetMarket(100, true);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            Order buy = new Order(itemID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), bankAccountNr);
            serverMarket.putOrder(buy);

            long priceBefore = serverMarket.getCurrentMarketPrice();
            serverMarket.update();

            // The matching engine should process the order and move the price
            TestResult r = assertTrue("Price should change after processing buy order",
                    serverMarket.getCurrentMarketPrice() != priceBefore || buy.getFilledVolume() > 0);
            if (!r.passed()) return r;
            return pass("update() processes orders when market is open");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Default Volume Provider ──────────────────────────────────────────────

    private TestResult test_defaultVolumeProvider_nullFunction() {
        try {
            resetMarket(100, true);

            // Set volume provider to null - should use fallback formula
            serverMarket.test_setDefaultVolumeProviderFunction(null);
            serverMarket.test_resetVirtualOrderBookVolume();

            // Try to get volume - should not crash
            long volume = serverMarket.getRawVolume(95);
            return pass("Null volume provider function uses fallback, volume at 95 = " + volume);
        } catch (Exception e) {
            return fail("Exception with null volume provider: " + e.getMessage());
        }
    }

    private TestResult test_defaultVolumeProvider_functionReturnsNull() {
        try {
            resetMarket(100, true);

            // Set provider that returns null
            serverMarket.test_setDefaultVolumeProviderFunction(p -> null);
            serverMarket.test_resetVirtualOrderBookVolume();

            // This should not throw NPE after the fix (returns 0 when null)
            long volume = serverMarket.getRawVolume(95);
            return pass("Volume provider returning null handled gracefully, volume = " + volume);
        } catch (NullPointerException e) {
            return fail("NPE when volume provider returns null (Issue #13): " + e.getMessage());
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Save/Load ────────────────────────────────────────────────────────────

    private TestResult test_save_load_roundTrip() {
        try {
            resetMarket(150, true);

            CompoundTag tag = new CompoundTag();
            boolean saved = ((ServerMarket) serverMarket).save(tag);
            TestResult r = assertTrue("Save should succeed", saved);
            if (!r.passed()) return r;

            // Verify the market price is in the saved data
            r = assertTrue("Tag should contain currentMarketPrice",
                    tag.contains("currentMarketPrice"));
            if (!r.passed()) return r;

            long savedPrice = tag.getLong("currentMarketPrice");
            r = assertEquals("Saved market price should be 150", 150L, savedPrice);
            if (!r.passed()) return r;

            // Load into the market
            boolean loaded = ((ServerMarket) serverMarket).load(tag);
            r = assertTrue("Load should succeed", loaded);
            if (!r.passed()) return r;

            r = assertEquals("Market price should be restored", 150L, serverMarket.getCurrentMarketPrice());
            if (!r.passed()) return r;
            return pass("ServerMarket save/load round trip preserves market price");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_load_missingOrderbook() {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putLong("currentMarketPrice", 100);
            // Do NOT put "orderbook" tag

            boolean loaded = ((ServerMarket) serverMarket).load(tag);
            TestResult r = assertFalse("Load should return false when orderbook is missing", loaded);
            if (!r.passed()) return r;
            return pass("Load with missing orderbook returns false and logs error");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_load_restoresCandleState() {
        try {
            resetMarket(250, true);

            CompoundTag tag = new CompoundTag();
            boolean saved = ((ServerMarket) serverMarket).save(tag);
            TestResult r = assertTrue("Save should succeed", saved);
            if (!r.passed()) return r;

            // Reset to a different price so candle fields differ
            resetMarket(100, true);

            boolean loaded = ((ServerMarket) serverMarket).load(tag);
            r = assertTrue("Load should succeed", loaded);
            if (!r.passed()) return r;

            MarketPriceStruct candle = serverMarket.getCurrentMarketPriceStruct();
            r = assertNotNull("Candle struct should not be null", candle);
            if (!r.passed()) return r;

            r = assertEquals("Candle open should be loaded price (250)", 250L, candle.open());
            if (!r.passed()) return r;
            r = assertEquals("Candle high should be loaded price (250)", 250L, candle.high());
            if (!r.passed()) return r;
            r = assertEquals("Candle low should be loaded price (250)", 250L, candle.low());
            if (!r.passed()) return r;
            return pass("load() correctly restores candle state fields from loaded price (Issue #48)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Candle Tracking ──────────────────────────────────────────────────────

    private TestResult test_getCurrentMarketPriceStructAndReset_resets() {
        try {
            resetMarket(100, true);

            // Set price and get candle
            serverMarket.test_setCurrentMarketPrice(120);
            serverMarket.update(); // Updates candle high/low

            MarketPriceStruct candle = serverMarket.getCurrentMarketPriceStructAndReset();

            TestResult r = assertNotNull("Candle struct should not be null", candle);
            if (!r.passed()) return r;

            // After reset, a new candle should start at the current price
            MarketPriceStruct newCandle = serverMarket.getCurrentMarketPriceStruct();
            r = assertNotNull("New candle should not be null", newCandle);
            if (!r.passed()) return r;

            // The new candle's open should be the current market price
            r = assertEquals("New candle open should be current market price",
                    serverMarket.getCurrentMarketPrice(), newCandle.open());
            if (!r.passed()) return r;
            return pass("getCurrentMarketPriceStructAndReset resets candle to current price");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Cancel Order ────────────────────────────────────────────────────────

    /**
     * Places a limit buy order directly in the orderbook, then cancels it via cancelOrder().
     * Verifies that cancelOrder returns true and the order is removed from the limit orders list.
     */
    private TestResult test_cancelOrder_existingLimit() {
        try {
            resetMarket(100, true);

            UUID executorUUID = UUID.randomUUID();
            Order order = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, executorUUID, bankAccountNr);

            // Place order directly in orderbook so it appears in getLimitOrders()
            serverMarket.getOrderbook().putOrder(order);

            // Verify order is present in limit orders
            TestResult r = assertTrue("Limit orders should contain the placed order",
                    serverMarket.getLimitOrders().size() >= 1);
            if (!r.passed()) return r;

            // Cancel the order using all identifying fields
            boolean cancelled = serverMarket.cancelOrder(
                    executorUUID, order.getTime(), Order.Type.LIMIT,
                    order.getStartPrice(), order.getTargetVolume());

            r = assertTrue("cancelOrder should return true for existing order", cancelled);
            if (!r.passed()) return r;

            // Verify order is removed
            for (Order remaining : serverMarket.getLimitOrders()) {
                if (remaining == order) {
                    return fail("Order should have been removed from limit orders list");
                }
            }
            return pass("cancelOrder successfully removes an existing limit order");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * Calls cancelOrder with a random UUID and arbitrary values on an empty orderbook.
     * Verifies that cancelOrder returns false when no matching order exists.
     */
    private TestResult test_cancelOrder_nonexistent() {
        try {
            resetMarket(100, true);

            // Attempt to cancel a non-existent order
            boolean cancelled = serverMarket.cancelOrder(
                    UUID.randomUUID(), System.currentTimeMillis(),
                    Order.Type.LIMIT, 100, 5);

            TestResult r = assertFalse("cancelOrder should return false for non-existent order", cancelled);
            if (!r.passed()) return r;
            return pass("cancelOrder returns false when no matching order exists");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * Places a limit order with UUID-A, then tries to cancel with UUID-B.
     * Verifies cancelOrder returns false and the original order remains in the orderbook.
     */
    private TestResult test_cancelOrder_wrongExecutor() {
        try {
            resetMarket(100, true);

            UUID ownerUUID = UUID.randomUUID();
            UUID wrongUUID = UUID.randomUUID();
            Order order = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, ownerUUID, bankAccountNr);

            // Place order directly in orderbook
            serverMarket.getOrderbook().putOrder(order);

            int sizeBefore = serverMarket.getLimitOrders().size();

            // Try to cancel with a different executor UUID
            boolean cancelled = serverMarket.cancelOrder(
                    wrongUUID, order.getTime(), Order.Type.LIMIT,
                    order.getStartPrice(), order.getTargetVolume());

            TestResult r = assertFalse("cancelOrder should return false for wrong executor", cancelled);
            if (!r.passed()) return r;

            // Verify order is still in the orderbook
            int sizeAfter = serverMarket.getLimitOrders().size();
            r = assertEquals("Limit orders size should be unchanged", sizeBefore, sizeAfter);
            if (!r.passed()) return r;

            return pass("cancelOrder with wrong executor UUID does not remove the order");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }
}
