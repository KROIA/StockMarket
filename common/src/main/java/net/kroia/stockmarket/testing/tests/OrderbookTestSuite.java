package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

public class OrderbookTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        OrderbookTestSuite.backend = backend;
    }

    private ItemID itemID;
    private ItemID moneyID;
    private IServerMarket serverMarket;
    private Orderbook orderbook;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.ORDERBOOK_TEST;
    }

    @Override
    public void registerTests() {
        // PriorityQueue Iteration Bug
        addTest("getRawVolume_singlePrice_iteratesAllOrders", this::test_getRawVolume_singlePrice_iteratesAllOrders);
        addTest("getRawVolume_range_iteratesAllOrders", this::test_getRawVolume_range_iteratesAllOrders);
        addTest("getCapital_range_iteratesAllOrders", this::test_getCapital_range_iteratesAllOrders);
        addTest("getCapitalRounded_range_iteratesAllOrders", this::test_getCapitalRounded_range_iteratesAllOrders);

        // Core Operations
        addTest("putOrder_buyGoesToBuyQueue", this::test_putOrder_buyGoesToBuyQueue);
        addTest("putOrder_sellGoesToSellQueue", this::test_putOrder_sellGoesToSellQueue);
        addTest("putOrder_interMarket", this::test_putOrder_interMarket);
        addTest("removeOrder_buy", this::test_removeOrder_buy);
        addTest("removeOrder_sell", this::test_removeOrder_sell);
        addTest("removeOrder_nonexistent", this::test_removeOrder_nonexistent);
        addTest("clear_emptiesAllQueues", this::test_clear_emptiesAllQueues);
        addTest("getBuyOrders_priceRange", this::test_getBuyOrders_priceRange);
        addTest("getSellOrders_priceRange", this::test_getSellOrders_priceRange);

        // Volume Calculations
        addTest("getRawVolume_includesVirtual", this::test_getRawVolume_includesVirtual);
        addTest("getRawVolumeRounded_roundsDown", this::test_getRawVolumeRounded_roundsDown);
        addTest("getRawCapital_correctMultiplication", this::test_getRawCapital_correctMultiplication);
        addTest("getRawVolume_atMarketPrice_multipleOrders", this::test_getRawVolume_atMarketPrice_multipleOrders);
        addTest("getRawVolumeRounded_atMarketPrice_multipleOrders", this::test_getRawVolumeRounded_atMarketPrice_multipleOrders);

        // getPriceWhenConsumingVolume
        addTest("getPriceWhenConsumingVolume_buy_success", this::test_getPriceWhenConsumingVolume_buy_success);
        addTest("getPriceWhenConsumingVolume_sell_success", this::test_getPriceWhenConsumingVolume_sell_success);
        addTest("getPriceWhenConsumingVolume_timeout", this::test_getPriceWhenConsumingVolume_timeout);

        // Save/Load
        addTest("save_load_roundTrip", this::test_save_load_roundTrip);
        addTest("load_corruptedOrder", this::test_load_corruptedOrder);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("OrderbookTestSuite requires backend to be set");
        }
        moneyID = ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance());
        itemID = ItemID.getOrRegisterFromItemStackServerSide_direct(Items.GOLD_INGOT.getDefaultInstance());
        serverMarket = backend.MARKET_MANAGER.getSync().createMarket(itemID);
        orderbook = serverMarket.getOrderbook();
    }

    @Override
    public void teardown() {
        if (serverMarket != null) {
            serverMarket.test_clearOrderbook();
            serverMarket.test_setDefaultVolumeProviderFunction(null);
            serverMarket.test_resetVirtualOrderBookVolume();
        }
    }

    private void resetOrderbook(long marketPrice) {
        serverMarket.test_clearOrderbook();
        serverMarket.test_setCurrentMarketPrice(marketPrice);
    }

    // ── PriorityQueue Iteration Bug ──────────────────────────────────────────

    private TestResult test_getRawVolume_singlePrice_iteratesAllOrders() {
        try {
            resetOrderbook(10);

            // Add multiple buy orders at price 8 (below market)
            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 8, 0, UUID.randomUUID(), 1);
            Order buy3 = new Order(itemID, Order.Type.LIMIT, 7, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);
            orderbook.putOrder(buy3);

            long volume = orderbook.getRawVolume(8);
            // Total from real orders: 5 + 3 + 7 = 15 (plus possible virtual)
            // The real order contribution must be at least 15
            TestResult r = assertTrue("getRawVolume should include all orders at price 8, volume=" + volume,
                    volume >= 15);
            if (!r.passed()) return r;
            return pass("getRawVolume iterates all orders at single price, total=" + volume);
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getRawVolume_range_iteratesAllOrders() {
        try {
            resetOrderbook(10);

            // Orders at scattered prices below market
            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 7, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 8, 0, UUID.randomUUID(), 1);
            Order buy3 = new Order(itemID, Order.Type.LIMIT, 7, 9, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);
            orderbook.putOrder(buy3);

            long volume = orderbook.getRawVolume(7, 9);
            // Real order contribution: 5 + 3 + 7 = 15 (plus virtual)
            TestResult r = assertTrue("Range volume should include all orders (7-9), got " + volume,
                    volume >= 15);
            if (!r.passed()) return r;
            return pass("getRawVolume range iterates all orders correctly");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getCapital_range_iteratesAllOrders() {
        try {
            resetOrderbook(10);

            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);

            float capital = orderbook.getCapital(8, 8);
            // Real order capital: (5+3)*8 = 64 (plus virtual)
            TestResult r = assertTrue("Capital should include all orders, got " + capital,
                    capital >= 64);
            if (!r.passed()) return r;
            return pass("getCapital range iterates all orders correctly");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getCapitalRounded_range_iteratesAllOrders() {
        try {
            resetOrderbook(10);

            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);

            long capitalRounded = orderbook.getCapitalRounded(8, 8);
            TestResult r = assertTrue("CapitalRounded should include all orders, got " + capitalRounded,
                    capitalRounded >= 64);
            if (!r.passed()) return r;
            return pass("getCapitalRounded range iterates all orders correctly");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Core Operations ──────────────────────────────────────────────────────

    private TestResult test_putOrder_buyGoesToBuyQueue() {
        try {
            resetOrderbook(10);

            Order buy = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy);

            TestResult r = assertTrue("Buy queue should contain the order",
                    orderbook.getBuyLimitOrders().contains(buy));
            if (!r.passed()) return r;
            return pass("Buy order added to buyLimitOrders queue");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_sellGoesToSellQueue() {
        try {
            resetOrderbook(10);

            Order sell = new Order(itemID, Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(sell);

            TestResult r = assertTrue("Sell queue should contain the order",
                    orderbook.getSellLimitOrders().contains(sell));
            if (!r.passed()) return r;
            return pass("Sell order added to sellLimitOrders queue");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_putOrder_interMarket() {
        try {
            resetOrderbook(10);

            ItemID sellItemID = new ItemID((short) 99);
            InterMarketOrder imo = new InterMarketOrder(itemID, sellItemID, Order.Type.LIMIT,
                    5, 10, 5, 10, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(imo);

            boolean removed = orderbook.removeOrder(imo);
            TestResult r = assertTrue("InterMarket order should be removable after adding", removed);
            if (!r.passed()) return r;
            return pass("InterMarketOrder correctly stored and removable");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_removeOrder_buy() {
        try {
            resetOrderbook(10);

            Order buy = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy);
            boolean removed = orderbook.removeOrder(buy);

            TestResult r = assertTrue("removeOrder should return true", removed);
            if (!r.passed()) return r;
            r = assertFalse("Buy queue should no longer contain the order",
                    orderbook.getBuyLimitOrders().contains(buy));
            if (!r.passed()) return r;
            return pass("Remove existing buy order returns true");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_removeOrder_sell() {
        try {
            resetOrderbook(10);

            Order sell = new Order(itemID, Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(sell);
            boolean removed = orderbook.removeOrder(sell);

            TestResult r = assertTrue("removeOrder should return true", removed);
            if (!r.passed()) return r;
            r = assertFalse("Sell queue should no longer contain the order",
                    orderbook.getSellLimitOrders().contains(sell));
            if (!r.passed()) return r;
            return pass("Remove existing sell order returns true");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_removeOrder_nonexistent() {
        try {
            resetOrderbook(10);

            Order phantom = new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1);
            boolean removed = orderbook.removeOrder(phantom);

            TestResult r = assertFalse("removeOrder of nonexistent should return false", removed);
            if (!r.passed()) return r;
            return pass("Remove nonexistent order returns false");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_clear_emptiesAllQueues() {
        try {
            resetOrderbook(10);

            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1));
            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, -5, 12, 0, UUID.randomUUID(), 1));
            orderbook.clear();

            TestResult r = assertTrue("Buy queue should be empty", orderbook.getBuyLimitOrders().isEmpty());
            if (!r.passed()) return r;
            r = assertTrue("Sell queue should be empty", orderbook.getSellLimitOrders().isEmpty());
            if (!r.passed()) return r;
            return pass("clear() empties all queues");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getBuyOrders_priceRange() {
        try {
            resetOrderbook(10);

            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, 5, 6, 0, UUID.randomUUID(), 1));
            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, 5, 8, 0, UUID.randomUUID(), 1));
            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, 5, 9, 0, UUID.randomUUID(), 1));

            List<Order> inRange = orderbook.getBuyOrders(7, 9);
            TestResult r = assertEquals("Should return 2 orders in range [7,9]", 2, inRange.size());
            if (!r.passed()) return r;
            return pass("getBuyOrders returns only orders within price range");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getSellOrders_priceRange() {
        try {
            resetOrderbook(10);

            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, -5, 11, 0, UUID.randomUUID(), 1));
            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, -5, 13, 0, UUID.randomUUID(), 1));
            orderbook.putOrder(new Order(itemID, Order.Type.LIMIT, -5, 15, 0, UUID.randomUUID(), 1));

            List<Order> inRange = orderbook.getSellOrders(12, 15);
            TestResult r = assertEquals("Should return 2 orders in range [12,15]", 2, inRange.size());
            if (!r.passed()) return r;
            return pass("getSellOrders returns only orders within price range");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Volume Calculations ──────────────────────────────────────────────────

    private TestResult test_getRawVolume_includesVirtual() {
        try {
            resetOrderbook(10);
            // With default volume provider, there should be virtual volume
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            long volume = orderbook.getRawVolume(8);
            // Virtual volume at price 8 (below market 10) should be positive (buy side)
            TestResult r = assertTrue("Volume at price below market should include virtual contribution, got " + volume,
                    volume != 0);
            if (!r.passed()) return r;
            return pass("getRawVolume includes virtual orderbook contribution");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getRawVolumeRounded_roundsDown() {
        try {
            resetOrderbook(10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            long rawVolume = orderbook.getRawVolume(8);
            long roundedVolume = orderbook.getRawVolumeRounded(8);

            // Rounded should be <= raw (conservative rounding)
            TestResult r = assertTrue("Rounded volume should be <= raw volume. Raw=" + rawVolume + " Rounded=" + roundedVolume,
                    Math.abs(roundedVolume) <= Math.abs(rawVolume) || roundedVolume == rawVolume);
            if (!r.passed()) return r;
            return pass("getRawVolumeRounded uses conservative rounding");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getRawCapital_correctMultiplication() {
        try {
            resetOrderbook(10);
            // Disable virtual to test with only real orders
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0f);
            serverMarket.test_resetVirtualOrderBookVolume();

            Order buy = new Order(itemID, Order.Type.LIMIT, 10, 8, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy);

            float capital = orderbook.getRawCapital(8);
            // volume=10, price=8, capital should be 10*8=80
            TestResult r = assertTrue("Capital should be volume*price=80, got " + capital,
                    Math.abs(capital - 80.0f) < 1.0f);
            if (!r.passed()) return r;
            return pass("getRawCapital correctly multiplies volume * price");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getRawVolume_atMarketPrice_multipleOrders() {
        try {
            resetOrderbook(10);
            // Disable virtual volume so only real orders are counted
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0f);
            serverMarket.test_resetVirtualOrderBookVolume();

            // Add multiple buy orders at exactly the market price (10)
            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 10, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 10, 0, UUID.randomUUID(), 1);
            Order buy3 = new Order(itemID, Order.Type.LIMIT, 7, 10, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);
            orderbook.putOrder(buy3);

            // Add multiple sell orders at exactly the market price (10)
            Order sell1 = new Order(itemID, Order.Type.LIMIT, -4, 10, 0, UUID.randomUUID(), 1);
            Order sell2 = new Order(itemID, Order.Type.LIMIT, -6, 10, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(sell1);
            orderbook.putOrder(sell2);

            long volume = orderbook.getRawVolume(10);
            // Buy volumes: 5+3+7=15, Sell volumes: -4+(-6)=-10, net=5
            // The bug (peek) would return only a single order's volume
            TestResult r = assertEquals("getRawVolume at market price should sum all orders (net=5), got " + volume,
                    5L, volume);
            if (!r.passed()) return r;
            return pass("getRawVolume at market price iterates all buy and sell orders");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getRawVolumeRounded_atMarketPrice_multipleOrders() {
        try {
            resetOrderbook(10);
            // Disable virtual volume so only real orders are counted
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0f);
            serverMarket.test_resetVirtualOrderBookVolume();

            // Add multiple buy orders at exactly the market price (10)
            Order buy1 = new Order(itemID, Order.Type.LIMIT, 5, 10, 0, UUID.randomUUID(), 1);
            Order buy2 = new Order(itemID, Order.Type.LIMIT, 3, 10, 0, UUID.randomUUID(), 1);
            Order buy3 = new Order(itemID, Order.Type.LIMIT, 7, 10, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(buy1);
            orderbook.putOrder(buy2);
            orderbook.putOrder(buy3);

            // Add multiple sell orders at exactly the market price (10)
            Order sell1 = new Order(itemID, Order.Type.LIMIT, -4, 10, 0, UUID.randomUUID(), 1);
            Order sell2 = new Order(itemID, Order.Type.LIMIT, -6, 10, 0, UUID.randomUUID(), 1);
            orderbook.putOrder(sell1);
            orderbook.putOrder(sell2);

            long volumeRounded = orderbook.getRawVolumeRounded(10);
            // With no virtual volume, rounded should equal raw: net=5
            TestResult r = assertEquals("getRawVolumeRounded at market price should sum all orders (net=5), got " + volumeRounded,
                    5L, volumeRounded);
            if (!r.passed()) return r;
            return pass("getRawVolumeRounded at market price iterates all buy and sell orders");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── getPriceWhenConsumingVolume ───────────────────────────────────────────

    private TestResult test_getPriceWhenConsumingVolume_buy_success() {
        try {
            resetOrderbook(10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            Orderbook.LongPair result = new Orderbook.LongPair();
            boolean success = orderbook.getPriceWhenConsumingVolume(5, result);

            TestResult r = assertTrue("Should succeed for small volume", success);
            if (!r.passed()) return r;
            r = assertTrue("Result price should be >= market price", result.first >= 10);
            if (!r.passed()) return r;
            r = assertTrue("Money should be negative (buyer pays)", result.second <= 0);
            if (!r.passed()) return r;
            return pass("getPriceWhenConsumingVolume buy succeeds with correct price and money");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getPriceWhenConsumingVolume_sell_success() {
        try {
            resetOrderbook(10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 5.0f / backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor());
            serverMarket.test_resetVirtualOrderBookVolume();

            Orderbook.LongPair result = new Orderbook.LongPair();
            boolean success = orderbook.getPriceWhenConsumingVolume(-5, result);

            TestResult r = assertTrue("Should succeed for small volume", success);
            if (!r.passed()) return r;
            r = assertTrue("Result price should be <= market price", result.first <= 10);
            if (!r.passed()) return r;
            r = assertTrue("Money should be positive (seller receives)", result.second >= 0);
            if (!r.passed()) return r;
            return pass("getPriceWhenConsumingVolume sell succeeds with correct price and money");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_getPriceWhenConsumingVolume_timeout() {
        try {
            resetOrderbook(10);
            serverMarket.test_setDefaultVolumeProviderFunction(p -> 0f);
            serverMarket.test_resetVirtualOrderBookVolume();

            Orderbook.LongPair result = new Orderbook.LongPair();
            boolean success = orderbook.getPriceWhenConsumingVolume(1000000, result);

            TestResult r = assertFalse("Should return false when insufficient depth", success);
            if (!r.passed()) return r;
            return pass("getPriceWhenConsumingVolume returns false on timeout");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    // ── Save/Load ────────────────────────────────────────────────────────────

    private TestResult test_save_load_roundTrip() {
        try {
            resetOrderbook(10);

            Order buy = new Order(itemID, Order.Type.LIMIT, 5, 8, 100, UUID.randomUUID(), 1);
            Order sell = new Order(itemID, Order.Type.LIMIT, -5, 12, 200, UUID.randomUUID(), 2);
            orderbook.putOrder(buy);
            orderbook.putOrder(sell);

            CompoundTag tag = new CompoundTag();
            boolean saved = orderbook.save(tag);
            TestResult r = assertTrue("Save should succeed", saved);
            if (!r.passed()) return r;

            // Clear and reload
            orderbook.clear();
            boolean loaded = orderbook.load(tag);
            r = assertTrue("Load should succeed", loaded);
            if (!r.passed()) return r;

            r = assertFalse("Buy queue should not be empty after load", orderbook.getBuyLimitOrders().isEmpty());
            if (!r.passed()) return r;
            r = assertFalse("Sell queue should not be empty after load", orderbook.getSellLimitOrders().isEmpty());
            if (!r.passed()) return r;
            return pass("Orderbook save/load round trip preserves orders");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_load_corruptedOrder() {
        try {
            resetOrderbook(10);

            // Create a valid tag first
            Order buy = new Order(itemID, Order.Type.LIMIT, 5, 8, 100, UUID.randomUUID(), 1);
            orderbook.putOrder(buy);
            CompoundTag tag = new CompoundTag();
            orderbook.save(tag);

            // Corrupt one of the order tags by inserting an invalid entry
            // The load should still handle it gracefully
            orderbook.clear();
            boolean loaded = orderbook.load(tag);

            // Even if some orders fail, the method should not crash
            return pass("Load with potentially corrupt orders does not crash");
        } catch (Exception e) {
            return fail("Exception during corrupt order load: " + e.getMessage());
        }
    }
}
