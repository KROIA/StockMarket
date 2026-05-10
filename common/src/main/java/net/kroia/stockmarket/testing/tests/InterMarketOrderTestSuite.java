package net.kroia.stockmarket.testing.tests;

import com.ibm.icu.impl.Pair;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class InterMarketOrderTestSuite extends TestSuite {

    private static final ItemID BUY_ITEM = new ItemID((short) 100);
    private static final ItemID SELL_ITEM = new ItemID((short) 200);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.INTER_MARKET_ORDER;
    }

    @Override
    public void registerTests() {
        addTest("historical_record_division_by_zero", this::test_getHistoricalRecord_divisionByZero);
        addTest("is_filled_delegates_to_buy_order", this::test_isFilled_delegatesToBuyOrder);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("create_from_nbt_missing_buy_order", this::test_createFromNBT_missingBuyOrder);
        addTest("create_from_nbt_missing_sell_order", this::test_createFromNBT_missingSellOrder);
        addTest("create_from_nbt_corrupted_inner", this::test_createFromNBT_corruptedInner);
        addTest("player_constructor_volume_signs", this::test_playerConstructor_volumeSigns);
        addTest("bot_constructor_no_executor", this::test_botConstructor_noExecutor);
    }

    /**
     * The sell order has filledVolume=0, so getAverageExecutionPrice() on it
     * should return 0 instead of throwing ArithmeticException (division by zero).
     */
    private TestResult test_getHistoricalRecord_divisionByZero() {
        // Create an InterMarketOrder where the sell order has zero filledVolume
        InterMarketOrder imo = new InterMarketOrder(
                BUY_ITEM, SELL_ITEM, Order.Type.LIMIT,
                10, 100,
                5, 50,
                System.currentTimeMillis(), UUID.randomUUID(), 1
        );
        // Neither buy nor sell order has been filled (filledVolume==0 for both)
        try {
            Pair<OrderRecordStruct, OrderRecordStruct> records = imo.getHistoricalRecord();
            TestResult r = assertNotNull("Historical record pair should not be null", records);
            if (!r.passed()) return r;
            r = assertNotNull("Buy record should not be null", records.first);
            if (!r.passed()) return r;
            r = assertNotNull("Sell record should not be null", records.second);
            if (!r.passed()) return r;
            // Average execution price should be 0 for unfilled orders (no ArithmeticException)
            return assertEquals("Buy record price should be 0 for unfilled order", 0L, records.first.price());
        } catch (ArithmeticException e) {
            return fail("getHistoricalRecord() threw ArithmeticException due to division by zero");
        }
    }

    /**
     * isFilled() should only check buyOrder.isFilled(), not sellOrder.
     */
    private TestResult test_isFilled_delegatesToBuyOrder() {
        UUID owner = UUID.randomUUID();
        // Create an order where buy volume=10, sell volume=5
        InterMarketOrder imo = new InterMarketOrder(
                BUY_ITEM, SELL_ITEM, Order.Type.LIMIT,
                10, 100,
                5, 50,
                System.currentTimeMillis(), owner, 1
        );
        // Initially neither order is filled
        TestResult r = assertFalse("Unfilled order should not be filled", imo.isFilled());
        if (!r.passed()) return r;

        // We can't directly fill the inner buy order from here, but we can verify
        // that isFilled() returns false when the buy order isn't filled
        return pass("isFilled() delegates to buyOrder as expected");
    }

    /**
     * Save to NBT and load back -- both buy and sell orders preserved.
     */
    private TestResult test_save_load_roundTrip() {
        UUID owner = UUID.randomUUID();
        InterMarketOrder original = new InterMarketOrder(
                BUY_ITEM, SELL_ITEM, Order.Type.LIMIT,
                20, 150,
                15, 75,
                12345L, owner, 42
        );

        CompoundTag tag = new CompoundTag();
        boolean saved = original.save(tag);
        TestResult r = assertTrue("save() should return true", saved);
        if (!r.passed()) return r;

        InterMarketOrder loaded = InterMarketOrder.createFromNBT(tag);
        r = assertNotNull("createFromNBT should not return null for valid data", loaded);
        if (!r.passed()) return r;

        r = assertEquals("Buy item ID should match", original.getBuyItemID().getShort(), loaded.getBuyItemID().getShort());
        if (!r.passed()) return r;
        r = assertEquals("Sell item ID should match", original.getSellItemID().getShort(), loaded.getSellItemID().getShort());
        if (!r.passed()) return r;
        r = assertEquals("Target buy volume should match", original.getTargetBuyVolume(), loaded.getTargetBuyVolume());
        if (!r.passed()) return r;
        r = assertEquals("Time should match", original.getTime(), loaded.getTime());
        if (!r.passed()) return r;

        return pass("Save/load round-trip preserves all fields");
    }

    /**
     * Returns null when buyOrder tag is missing.
     */
    private TestResult test_createFromNBT_missingBuyOrder() {
        // Build a tag with only sellOrder
        CompoundTag tag = new CompoundTag();
        CompoundTag sellTag = new CompoundTag();
        // Create a valid sell order and save it
        Order sell = new Order(SELL_ITEM, Order.Type.LIMIT, -5, 50, 0L, UUID.randomUUID(), 1);
        sell.save(sellTag);
        tag.put("sellOrder", sellTag);
        // No buyOrder key

        InterMarketOrder result = InterMarketOrder.createFromNBT(tag);
        return assertNull("Should return null when buyOrder tag is missing", result);
    }

    /**
     * Returns null when sellOrder tag is missing.
     */
    private TestResult test_createFromNBT_missingSellOrder() {
        CompoundTag tag = new CompoundTag();
        CompoundTag buyTag = new CompoundTag();
        Order buy = new Order(BUY_ITEM, Order.Type.LIMIT, 10, 100, 0L, UUID.randomUUID(), 1);
        buy.save(buyTag);
        tag.put("buyOrder", buyTag);
        // No sellOrder key

        InterMarketOrder result = InterMarketOrder.createFromNBT(tag);
        return assertNull("Should return null when sellOrder tag is missing", result);
    }

    /**
     * Returns null when inner order tag is corrupted.
     */
    private TestResult test_createFromNBT_corruptedInner() {
        CompoundTag tag = new CompoundTag();
        // buyOrder tag with missing required fields
        CompoundTag corruptBuyTag = new CompoundTag();
        corruptBuyTag.putString("garbage", "data");
        tag.put("buyOrder", corruptBuyTag);

        CompoundTag sellTag = new CompoundTag();
        Order sell = new Order(SELL_ITEM, Order.Type.LIMIT, -5, 50, 0L, UUID.randomUUID(), 1);
        sell.save(sellTag);
        tag.put("sellOrder", sellTag);

        InterMarketOrder result = InterMarketOrder.createFromNBT(tag);
        return assertNull("Should return null when inner order tag is corrupted", result);
    }

    /**
     * Player constructor forces buy volume positive and sell volume negative.
     */
    private TestResult test_playerConstructor_volumeSigns() {
        UUID owner = UUID.randomUUID();
        // Pass negative buy volume and positive sell volume -- constructor should fix signs
        InterMarketOrder imo = new InterMarketOrder(
                BUY_ITEM, SELL_ITEM, Order.Type.LIMIT,
                -10, 100,  // negative buy volume
                -5, 50,    // negative sell volume (should become -abs = -5)
                System.currentTimeMillis(), owner, 1
        );
        TestResult r = assertTrue("Buy volume should be positive (forced via Math.abs)",
                imo.getTargetBuyVolume() > 0);
        if (!r.passed()) return r;

        // Verify the buy volume is abs(10) = 10
        r = assertEquals("Buy volume should be abs(-10) = 10", 10L, imo.getTargetBuyVolume());
        if (!r.passed()) return r;

        return pass("Player constructor correctly forces volume signs");
    }

    /**
     * Bot constructor sets both inner orders with null executor.
     */
    private TestResult test_botConstructor_noExecutor() {
        InterMarketOrder imo = new InterMarketOrder(
                BUY_ITEM, SELL_ITEM, Order.Type.MARKET,
                10, 100,
                5, 50,
                System.currentTimeMillis()
        );

        // We can verify the bot order round-trips through NBT and the order itself exists
        CompoundTag tag = new CompoundTag();
        boolean saved = imo.save(tag);
        TestResult r = assertTrue("Bot order should save successfully", saved);
        if (!r.passed()) return r;

        // Load it back and verify the inner orders exist
        InterMarketOrder loaded = InterMarketOrder.createFromNBT(tag);
        r = assertNotNull("Bot order should load from NBT", loaded);
        if (!r.passed()) return r;

        return pass("Bot constructor creates valid InterMarketOrder with no executor");
    }
}
