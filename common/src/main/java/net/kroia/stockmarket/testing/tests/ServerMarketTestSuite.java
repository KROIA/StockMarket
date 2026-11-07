package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.NbtCompat;

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
import net.kroia.stockmarket.testing.TestFixture;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class ServerMarketTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        ServerMarketTestSuite.backend = backend;
    }

    /** Owns the scratch market/account this suite creates; drained in {@link #teardown()}. */
    private final TestFixture fixture = new TestFixture("ServerMarket");

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
        addTest("putOrder_marketMaintenance_rejected", this::test_putOrder_marketMaintenance_rejected);
        addTest("putOrder_alreadyFilled_rejected", this::test_putOrder_alreadyFilled_rejected);
        addTest("putOrder_wrongItemID_rejected", this::test_putOrder_wrongItemID_rejected);
        addTest("putOrder_buyMarket_goesToBuffer", this::test_putOrder_buyMarket_goesToBuffer);
        addTest("putOrder_sellMarket_goesToBuffer", this::test_putOrder_sellMarket_goesToBuffer);
        addTest("putOrder_buyLimit_goesToBuffer", this::test_putOrder_buyLimit_goesToBuffer);
        addTest("putOrder_sellLimit_goesToBuffer", this::test_putOrder_sellLimit_goesToBuffer);

        // Market Open/Close
        addTest("update_marketMaintenance_noop", this::test_update_marketMaintenance_noop);
        addTest("update_notMaintenance_processesOrders", this::test_update_notMaintenance_processesOrders);

        // Default Volume Provider
        addTest("defaultVolumeProvider_nullFunction", this::test_defaultVolumeProvider_nullFunction);
        addTest("defaultVolumeProvider_functionReturnsNull", this::test_defaultVolumeProvider_functionReturnsNull);

        // Save/Load
        addTest("save_load_roundTrip", this::test_save_load_roundTrip);
        addTest("load_missingOrderbook", this::test_load_missingOrderbook);
        addTest("load_restoresCandleState", this::test_load_restoresCandleState);
        addTest("save_load_maintenancePersisted", this::test_save_load_maintenancePersisted);

        // Candle Tracking
        addTest("getCurrentMarketPriceStructAndReset_resets", this::test_getCurrentMarketPriceStructAndReset_resets);

        // Cancel Order
        addTest("cancelOrder_existing_limit", this::test_cancelOrder_existingLimit);
        addTest("cancelOrder_nonexistent", this::test_cancelOrder_nonexistent);
        addTest("cancelOrder_wrong_executor", this::test_cancelOrder_wrongExecutor);

        // Trading hours (T-158)
        addTest("tradingHours_closed_rejectsAllOrders", this::test_tradingHours_closed_rejectsAllOrders);
        addTest("tradingHours_closed_staticPrice_restingSurvives", this::test_tradingHours_closed_staticPrice_restingSurvives);
        addTest("tradingHours_override_clearedOnTransition", this::test_tradingHours_override_clearedOnTransition);
        addTest("tradingHours_setTradingHours_normalizes", this::test_tradingHours_setTradingHours_normalizes);
        addTest("tradingHours_save_load_roundTrip_and_oldNbt", this::test_tradingHours_saveLoad);
        addTest("save_load_closedState_freshMarket", this::test_saveLoad_closedState_freshMarket);
        addTest("maxOrderVolume_saveLoad_and_oldNbt", this::test_maxOrderVolume_saveLoad);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("ServerMarketTestSuite requires backend to be set");
        }
        fixture.setBackend(backend);
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        // Scratch market on a synthetic ItemID — never the player's live gold market, so
        // the setMaintenance(true) calls below can never put a market under maintenance a player trades on.
        serverMarket = fixture.createMarket("main");
        itemID = serverMarket.getItemID();

        // Scratch account — never a fixed account number, which would squat on a real player's account.
        bankAccount = fixture.createBankAccount("trader");
        bankAccountNr = bankAccount.getAccountNumber();
        bankAccount.createBank(itemID, 100);
        bankAccount.createBank(moneyID, 10000);
    }

    @Override
    public void teardown() {
        // End maintenance + clear so deleteMarket sees no dangling orders, then drop everything.
        if (serverMarket != null) {
            serverMarket.setMaintenance(false);
            resetTradingHours();
            serverMarket.test_clearOrderbook();
            serverMarket.test_clearIncomingOrderBuffers();
        }
        serverMarket = null;
        fixture.cleanup();
    }

    private void resetMarket(long price, boolean open) {
        serverMarket.test_setCurrentMarketPrice(price);
        serverMarket.test_clearOrderbook();
        serverMarket.test_clearIncomingOrderBuffers();
        serverMarket.setMaintenance(!open);
        resetTradingHours();
        bankAccount.getBank(itemID).setBalance(100);
        bankAccount.getBank(moneyID).setBalance(10000);
    }

    /**
     * Back to 00:00-24:00, open, no override. A closed-window evaluation followed by the
     * always-open one forces a scheduled transition, which clears any override.
     */
    private void resetTradingHours() {
        serverMarket.setTradingHours(0, 1);
        serverMarket.updateTradingHours(5);
        serverMarket.setTradingHours(0, 1440);
        serverMarket.updateTradingHours(0);
    }

    /** Closes the market via its schedule: window 10:00-12:00 evaluated at 00:00. */
    private void closeBySchedule() {
        serverMarket.setTradingHours(600, 720);
        serverMarket.updateTradingHours(0);
    }

    // ── Order Routing ────────────────────────────────────────────────────────

    private TestResult test_putOrder_marketMaintenance_rejected() {
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

    private TestResult test_update_marketMaintenance_noop() {
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

    private TestResult test_update_notMaintenance_processesOrders() {
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

            long savedPrice = NbtCompat.getLong(tag, "currentMarketPrice");
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

    /**
     * The maintenance flag survives save/load; NBT without a "maintenance" key (pre-T-158 worlds)
     * loads as not under maintenance.
     */
    private TestResult test_save_load_maintenancePersisted() {
        try {
            resetMarket(100, false); // under maintenance
            CompoundTag tag = new CompoundTag();
            TestResult r = assertTrue("Save should succeed", ((ServerMarket) serverMarket).save(tag));
            if (!r.passed()) return r;

            serverMarket.setMaintenance(false);
            ((ServerMarket) serverMarket).load(tag);
            r = assertTrue("Maintenance flag should be restored by load", serverMarket.isUnderMaintenance());
            if (!r.passed()) return r;

            tag.remove("maintenance");
            ((ServerMarket) serverMarket).load(tag);
            r = assertFalse("Missing maintenance key should load as not under maintenance", serverMarket.isUnderMaintenance());
            if (!r.passed()) return r;
            return pass("Maintenance flag persists and old NBT defaults to not under maintenance");
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

    // ── Trading hours (T-158) ───────────────────────────────────────────────

    /** Outside trading hours putOrder rejects player AND bot orders. */
    private TestResult test_tradingHours_closed_rejectsAllOrders() {
        try {
            resetMarket(100, true);
            closeBySchedule();
            TestResult r = assertFalse("market should be closed", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            r = assertEquals("state CLOSED", MarketSettings.TradingState.CLOSED, serverMarket.getTradingState());
            if (!r.passed()) return r;

            Order player = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, UUID.randomUUID(), bankAccountNr);
            r = assertFalse("player order rejected while closed", serverMarket.putOrder(player));
            if (!r.passed()) return r;
            Order bot = new Order(itemID, Order.Type.MARKET, 5, 100, 0);
            r = assertFalse("bot order rejected while closed", serverMarket.putOrder(bot));
            if (!r.passed()) return r;

            serverMarket.updateTradingHours(650); // inside 10:00-12:00
            r = assertTrue("reopens inside the window", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            r = assertTrue("player order accepted after reopen", serverMarket.putOrder(
                    new Order(itemID, Order.Type.LIMIT, 5, 90, 0, UUID.randomUUID(), bankAccountNr)));
            if (!r.passed()) return r;
            return pass("Closed market rejects all new orders, reopen accepts again");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            resetMarket(100, true);
        }
    }

    /** Closed: update() does not match (static price), resting orders survive and stay cancellable. */
    private TestResult test_tradingHours_closed_staticPrice_restingSurvives() {
        try {
            resetMarket(100, true);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            UUID owner = UUID.randomUUID();
            Order resting = new Order(itemID, Order.Type.LIMIT, 5, 90, 0, owner, bankAccountNr);
            serverMarket.getOrderbook().putOrder(resting);
            // Buffered while open; must not execute once closed.
            Order buffered = new Order(itemID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), bankAccountNr);
            serverMarket.putOrder(buffered);

            closeBySchedule();
            long priceBefore = serverMarket.getCurrentMarketPrice();
            serverMarket.update();
            TestResult r = assertEquals("price static while closed", priceBefore, serverMarket.getCurrentMarketPrice());
            if (!r.passed()) return r;
            r = assertEquals("buffered order untouched while closed", 0L, buffered.getFilledVolume());
            if (!r.passed()) return r;
            r = assertTrue("resting order survives closing", serverMarket.getLimitOrders().contains(resting));
            if (!r.passed()) return r;
            r = assertTrue("cancelOrder works while closed", serverMarket.cancelOrder(
                    owner, resting.getTime(), Order.Type.LIMIT, resting.getStartPrice(), resting.getTargetVolume()));
            if (!r.passed()) return r;
            return pass("Closed market keeps a static price, resting orders survive and are cancellable");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            resetMarket(100, true);
        }
    }

    /** A manual override holds until the next scheduled transition, then the schedule wins. */
    private TestResult test_tradingHours_override_clearedOnTransition() {
        try {
            resetMarket(100, true);
            closeBySchedule(); // 10:00-12:00, now 00:00 -> closed

            serverMarket.setTradingOverride(true);
            TestResult r = assertFalse("override applied only on next evaluation", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            serverMarket.updateTradingHours(1);
            r = assertTrue("forced open outside the window", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            serverMarket.updateTradingHours(300);
            r = assertTrue("still forced open (no transition yet)", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            serverMarket.updateTradingHours(650); // schedule opens -> override cleared
            r = assertNull("override cleared on transition", ((ServerMarket) serverMarket).getTradingOverride());
            if (!r.passed()) return r;
            serverMarket.updateTradingHours(720); // schedule closes
            r = assertFalse("schedule closes again after override was cleared", serverMarket.isTradingOpen());
            if (!r.passed()) return r;

            // Forced close inside the window
            serverMarket.updateTradingHours(650);
            serverMarket.setTradingOverride(false);
            serverMarket.updateTradingHours(651);
            r = assertFalse("forced closed inside the window", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            serverMarket.updateTradingHours(720);
            serverMarket.updateTradingHours(600); // next day's open
            r = assertTrue("opens by schedule on the next day", serverMarket.isTradingOpen());
            if (!r.passed()) return r;

            // Maintenance wins over trading hours for the display state
            serverMarket.setMaintenance(true);
            r = assertEquals("MAINTENANCE wins", MarketSettings.TradingState.MAINTENANCE, serverMarket.getTradingState());
            if (!r.passed()) return r;
            return pass("Override holds until the next scheduled transition");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            resetMarket(100, true);
        }
    }

    private TestResult test_tradingHours_setTradingHours_normalizes() {
        try {
            resetMarket(100, true);
            MarketSettings s = serverMarket.getSettings();
            serverMarket.setTradingHours(500, 500);
            TestResult r = assertTrue("open == close -> 00:00-24:00", s.tradingOpenMinute == 0 && s.tradingCloseMinute == 1440);
            if (!r.passed()) return r;
            serverMarket.setTradingHours(-5, 2000);
            r = assertTrue("out of range clamped -> 00:00-24:00", s.tradingOpenMinute == 0 && s.tradingCloseMinute == 1440);
            if (!r.passed()) return r;
            serverMarket.setTradingHours(1440, 600);
            r = assertTrue("open 24:00 -> 00:00", s.tradingOpenMinute == 0 && s.tradingCloseMinute == 600);
            if (!r.passed()) return r;

            // setSettings copies the window with the same normalization
            MarketSettings incoming = new MarketSettings(false, s.defaultPrice, s.naturalAbundance);
            incoming.tradingOpenMinute = 1320;
            incoming.tradingCloseMinute = 360;
            serverMarket.setSettings(incoming);
            r = assertTrue("setSettings copies the window", s.tradingOpenMinute == 1320 && s.tradingCloseMinute == 360);
            if (!r.passed()) return r;
            return pass("Trading window is clamped and normalized");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            resetMarket(100, true);
        }
    }

    /**
     * T-166: maxOrderVolume persists (0 stays 0); NBT without the key derives it from the loaded abundance.
     */
    private TestResult test_maxOrderVolume_saveLoad() {
        ServerMarket market = (ServerMarket) serverMarket;
        MarketSettings s = serverMarket.getSettings();
        float prevAbundance = s.naturalAbundance;
        long prevMax = s.maxOrderVolume;
        try {
            s.naturalAbundance = 5f;
            s.maxOrderVolume = 123;
            CompoundTag tag = new CompoundTag();
            TestResult r = assertTrue("save succeeds", market.save(tag));
            if (!r.passed()) return r;
            s.maxOrderVolume = 999;
            market.load(tag);
            r = assertEquals("stored value restored", 123L, s.maxOrderVolume);
            if (!r.passed()) return r;

            s.maxOrderVolume = 0;
            tag = new CompoundTag();
            market.save(tag);
            s.maxOrderVolume = 999;
            market.load(tag);
            r = assertEquals("0 (unlimited) stays 0", 0L, s.maxOrderVolume);
            if (!r.passed()) return r;

            // Old NBT (pre-T-166): no key -> derived from the loaded abundance (5 -> 320)
            tag.remove("maxOrderVolume");
            market.load(tag);
            r = assertEquals("old NBT -> derived from abundance", 320L, s.maxOrderVolume);
            if (!r.passed()) return r;
            return pass("maxOrderVolume persists and old NBT derives it from abundance");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            s.naturalAbundance = prevAbundance;
            s.maxOrderVolume = prevMax;
        }
    }

    /** Window + override + last schedule persist; NBT without the keys loads as always open. */
    private TestResult test_tradingHours_saveLoad() {
        try {
            resetMarket(100, true);
            ServerMarket market = (ServerMarket) serverMarket;
            serverMarket.setTradingHours(1320, 360);
            serverMarket.updateTradingHours(0);      // inside overnight window -> open
            serverMarket.setTradingOverride(false);  // forced closed until 06:00
            CompoundTag tag = new CompoundTag();
            TestResult r = assertTrue("save succeeds", market.save(tag));
            if (!r.passed()) return r;

            resetMarket(100, true);
            market.load(tag);
            MarketSettings s = serverMarket.getSettings();
            r = assertTrue("window restored", s.tradingOpenMinute == 1320 && s.tradingCloseMinute == 360);
            if (!r.passed()) return r;
            r = assertEquals("override restored", Boolean.FALSE, market.getTradingOverride());
            if (!r.passed()) return r;
            r = assertFalse("loaded closed by override", serverMarket.isTradingOpen());
            if (!r.passed()) return r;

            // Old NBT (pre-T-158): no trading-hours keys -> always open, no override
            tag.remove("tradingOpenMinute");
            tag.remove("tradingCloseMinute");
            tag.remove("tradingOverride");
            tag.remove("lastScheduledOpen");
            market.load(tag);
            r = assertTrue("old NBT -> always open window", serverMarket.getSettings().isAlwaysOpen());
            if (!r.passed()) return r;
            r = assertNull("old NBT -> no override", market.getTradingOverride());
            if (!r.passed()) return r;
            r = assertTrue("old NBT -> open", serverMarket.isTradingOpen());
            if (!r.passed()) return r;
            return pass("Trading hours persist and old NBT loads as always open");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            resetMarket(100, true);
        }
    }

    /**
     * T-165: a closed market must stay closed across a restart. Maintenance and a forced-closed
     * trading override are saved, then loaded into a separate, freshly created market (as a server
     * start would), and must both survive. A tag without the "maintenance" key (pre-2.2.0 world)
     * must load as not under maintenance.
     */
    private TestResult test_saveLoad_closedState_freshMarket() {
        IServerMarket fresh = null;
        try {
            resetMarket(100, false);                // under maintenance
            serverMarket.setTradingOverride(false); // forced closed
            serverMarket.updateTradingHours(0);     // apply the override
            CompoundTag tag = new CompoundTag();
            TestResult r = assertTrue("save succeeds", ((ServerMarket) serverMarket).save(tag));
            if (!r.passed()) return r;

            fresh = fixture.createMarket("fresh");
            r = assertFalse("fresh market starts not under maintenance", fresh.isUnderMaintenance());
            if (!r.passed()) return r;
            ServerMarket loaded = (ServerMarket) fresh;
            loaded.load(tag);
            r = assertTrue("maintenance survives load into a fresh market", fresh.isUnderMaintenance());
            if (!r.passed()) return r;
            r = assertEquals("forced-closed override survives load", Boolean.FALSE, loaded.getTradingOverride());
            if (!r.passed()) return r;
            r = assertFalse("fresh market loads closed", fresh.isTradingOpen());
            if (!r.passed()) return r;

            tag.remove("maintenance");
            loaded.load(tag);
            r = assertFalse("missing maintenance key loads as open (not under maintenance)", fresh.isUnderMaintenance());
            if (!r.passed()) return r;
            return pass("Maintenance + override persist into a fresh market; old NBT loads open");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            if (fresh != null) fresh.setMaintenance(false);
            resetMarket(100, true);
        }
    }
}
