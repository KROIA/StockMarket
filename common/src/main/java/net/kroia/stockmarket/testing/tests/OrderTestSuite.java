package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class OrderTestSuite extends TestSuite {

    private static final ItemID DUMMY_ITEM_ID = new ItemID((short) 999);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.ORDER;
    }

    @Override
    public void registerTests() {
        addTest("average_execution_price_zero_division", this::test_getAverageExecutionPrice_zeroDivision);
        addTest("average_execution_price_normal_buy", this::test_getAverageExecutionPrice_normalBuy);
        addTest("remaining_volume", this::test_getRemainingVolume);
        addTest("is_filled_exact_match", this::test_isFilled_exactMatch);
        addTest("is_filled_not_yet", this::test_isFilled_notYet);
        addTest("is_buy_order", this::test_isBuyOrder);
        addTest("is_sell_order", this::test_isSellOrder);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("load_enum_out_of_bounds", this::test_load_enumOutOfBounds);
        addTest("load_enum_negative_ordinal", this::test_load_enumNegativeOrdinal);
        addTest("bot_order_null_executor", this::test_botOrder_nullExecutor);
        addTest("average_execution_price_normal_sell", this::test_getAverageExecutionPrice_normalSell);
        addTest("remaining_volume_unfilled", this::test_getRemainingVolume_unfilled);
        addTest("remaining_volume_fully_filled", this::test_getRemainingVolume_fullyFilled);
        addTest("is_buy_order_zero_volume", this::test_isBuyOrder_zeroVolume);
        addTest("edit_accumulates_correctly", this::test_edit_accumulatesCorrectly);
        addTest("edit_target_volume", this::test_editTargetVolume);
        addTest("player_order_non_null_executor", this::test_playerOrder_nonNullExecutor);
        addTest("create_from_nbt_valid", this::test_createFromNBT_valid);
        addTest("create_from_nbt_missing_fields", this::test_createFromNBT_missingFields);
        addTest("get_historical_record_normal_order", this::test_getHistoricalRecord_normalOrder);
    }

    private TestResult test_getAverageExecutionPrice_zeroDivision() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        long avgPrice = order.getAverageExecutionPrice();
        return assertEquals(
                "Fresh order with filledVolume==0 should return 0 instead of throwing ArithmeticException",
                0L,
                avgPrice
        );
    }

    private TestResult test_getAverageExecutionPrice_normalBuy() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(5, -5);
        long avgPrice = order.getAverageExecutionPrice();
        return assertEquals("Average execution price should be 100", 100L, avgPrice);
    }

    private TestResult test_getRemainingVolume() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(3, -300);
        return assertEquals("Remaining volume should be 7", 7L, order.getRemainingVolume());
    }

    private TestResult test_isFilled_exactMatch() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(10, -1000);
        return assertTrue("Order should be filled when filledVolume == targetVolume", order.isFilled());
    }

    private TestResult test_isFilled_notYet() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(5, -500);
        return assertFalse("Order should not be filled when filledVolume < targetVolume", order.isFilled());
    }

    private TestResult test_isBuyOrder() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        return assertTrue("Positive targetVolume should be a buy order", order.isBuyOrder());
    }

    private TestResult test_isSellOrder() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, -10, 100, 0, UUID.randomUUID(), 1);
        return assertTrue("Negative targetVolume should be a sell order", order.isSellOrder());
    }

    private TestResult test_save_load_roundTrip() {
        UUID executor = UUID.randomUUID();
        Order original = new Order(DUMMY_ITEM_ID, Order.Type.LIMIT, 15, 200, 12345L, executor, 42);
        original.edit(7, -1400);

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        Order loaded = Order.createFromNBT(tag);
        if (loaded == null) {
            return fail("Order.createFromNBT returned null");
        }

        if (loaded.getTargetVolume() != original.getTargetVolume()) {
            return fail("targetVolume mismatch after round-trip");
        }
        if (loaded.getFilledVolume() != original.getFilledVolume()) {
            return fail("filledVolume mismatch after round-trip");
        }
        if (loaded.getStartPrice() != original.getStartPrice()) {
            return fail("startPrice mismatch after round-trip");
        }
        if (loaded.getTime() != original.getTime()) {
            return fail("time mismatch after round-trip");
        }
        if (loaded.getTransferredMoney() != original.getTransferredMoney()) {
            return fail("transferredMoney mismatch after round-trip");
        }
        if (loaded.getType() != original.getType()) {
            return fail("type mismatch after round-trip");
        }
        return pass("Save/load round-trip preserves all fields");
    }

    private TestResult test_load_enumOutOfBounds() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("ItemID", (short) 1);
        tag.putInt("Type", 99);
        tag.putLong("TargetVolume", 10);
        tag.putLong("FilledVolume", 0);
        tag.putLong("StartPrice", 100);
        tag.putLong("Time", 0);
        tag.putLong("TransferredMoney", 0);

        Order loaded = Order.createFromNBT(tag);
        return assertNull(
                "Loading an Order with out-of-bounds Type ordinal should return null instead of crashing",
                loaded
        );
    }

    private TestResult test_load_enumNegativeOrdinal() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("ItemID", (short) 1);
        tag.putInt("Type", -1);
        tag.putLong("TargetVolume", 10);
        tag.putLong("FilledVolume", 0);
        tag.putLong("StartPrice", 100);
        tag.putLong("Time", 0);
        tag.putLong("TransferredMoney", 0);

        Order loaded = Order.createFromNBT(tag);
        return assertNull(
                "Loading an Order with negative Type ordinal should return null instead of crashing",
                loaded
        );
    }

    private TestResult test_botOrder_nullExecutor() {
        Order botOrder = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 5, 50, 0);
        if (!botOrder.isBotOrder()) {
            return fail("Bot-constructed order should report isBotOrder() == true");
        }
        return assertNull("Bot order should have null executor UUID", botOrder.getExecutorPlayerUUID());
    }

    private TestResult test_getAverageExecutionPrice_normalSell() {
        // Sell order: targetVolume=-10, filledVolume negative, transferredMoney positive
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, -10, 100, 0, UUID.randomUUID(), 1);
        order.edit(-5, 5); // sold 5 items, received 5 money
        long avgPrice = order.getAverageExecutionPrice();
        // getAverageExecutionPrice = -transferredMoney / filledVolume = -500 / -5 = 100
        return assertEquals("Average execution price for sell should be 100", 100L, avgPrice);
    }

    private TestResult test_getRemainingVolume_unfilled() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        return assertEquals("Fresh order remaining volume should equal targetVolume", 10L, order.getRemainingVolume());
    }

    private TestResult test_getRemainingVolume_fullyFilled() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(10, -1000);
        return assertEquals("Fully filled order remaining volume should be 0", 0L, order.getRemainingVolume());
    }

    private TestResult test_isBuyOrder_zeroVolume() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 0, 100, 0, UUID.randomUUID(), 1);
        TestResult r = assertFalse("Zero volume should not be a buy order", order.isBuyOrder());
        if (!r.passed()) return r;
        return assertFalse("Zero volume should not be a sell order", order.isSellOrder());
    }

    private TestResult test_edit_accumulatesCorrectly() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 20, 100, 0, UUID.randomUUID(), 1);
        order.edit(5, -500);
        order.edit(3, -360);
        order.edit(2, -180);
        TestResult r = assertEquals("filledVolume should be 10 after 3 edits", 10L, order.getFilledVolume());
        if (!r.passed()) return r;
        r = assertEquals("transferredMoney should be -1040 after 3 edits", -1040L, order.getTransferredMoney());
        if (!r.passed()) return r;
        return pass("Multiple edit() calls accumulate filledVolume and transferredMoney correctly");
    }

    private TestResult test_editTargetVolume() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(3, -300);
        order.editTargetVolume(20);
        TestResult r = assertEquals("targetVolume should be changed to 20", 20L, order.getTargetVolume());
        if (!r.passed()) return r;
        r = assertEquals("filledVolume should remain 3", 3L, order.getFilledVolume());
        if (!r.passed()) return r;
        r = assertEquals("transferredMoney should remain -300", -300L, order.getTransferredMoney());
        if (!r.passed()) return r;
        return pass("editTargetVolume changes targetVolume without affecting other fields");
    }

    private TestResult test_playerOrder_nonNullExecutor() {
        UUID playerUUID = UUID.randomUUID();
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.LIMIT, 5, 50, 0, playerUUID, 42);
        TestResult r = assertTrue("Player order should report isPlayerOrder() == true", order.isPlayerOrder());
        if (!r.passed()) return r;
        r = assertFalse("Player order should report isBotOrder() == false", order.isBotOrder());
        if (!r.passed()) return r;
        return assertEquals("Executor UUID should match", playerUUID, order.getExecutorPlayerUUID());
    }

    private TestResult test_createFromNBT_valid() {
        UUID executor = UUID.randomUUID();
        Order original = new Order(DUMMY_ITEM_ID, Order.Type.LIMIT, 25, 300, 9999L, executor, 7);
        original.edit(10, -3000);

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        Order loaded = Order.createFromNBT(tag);
        TestResult r = assertNotNull("createFromNBT with valid tag should not return null", loaded);
        if (!r.passed()) return r;
        r = assertEquals("targetVolume", 25L, loaded.getTargetVolume());
        if (!r.passed()) return r;
        r = assertEquals("filledVolume", 10L, loaded.getFilledVolume());
        if (!r.passed()) return r;
        r = assertEquals("startPrice", 300L, loaded.getStartPrice());
        if (!r.passed()) return r;
        r = assertEquals("time", 9999L, loaded.getTime());
        if (!r.passed()) return r;
        r = assertEquals("transferredMoney", -3000L, loaded.getTransferredMoney());
        if (!r.passed()) return r;
        return assertEquals("type", Order.Type.LIMIT, loaded.getType());
    }

    private TestResult test_createFromNBT_missingFields() {
        // Tag missing required fields
        CompoundTag tag = new CompoundTag();
        tag.putShort("ItemID", (short) 1);
        tag.putInt("Type", 0);
        // Missing TargetVolume, FilledVolume, StartPrice, Time, TransferredMoney

        Order loaded = Order.createFromNBT(tag);
        return assertNull("createFromNBT with missing fields should return null", loaded);
    }

    private TestResult test_getHistoricalRecord_normalOrder() {
        UUID executor = UUID.randomUUID();
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 5000L, executor, 3);
        order.edit(10, -10); // fully filled, avg price = 100

        OrderRecordStruct record = order.getHistoricalRecord();
        TestResult r = assertNotNull("Historical record should not be null", record);
        if (!r.passed()) return r;
        r = assertEquals("itemID", DUMMY_ITEM_ID.getShort(), record.itemID());
        if (!r.passed()) return r;
        r = assertEquals("bankaccountID", 3, record.bankaccountID());
        if (!r.passed()) return r;
        r = assertEquals("user UUID", executor, record.user());
        if (!r.passed()) return r;
        r = assertEquals("type ordinal", Order.Type.MARKET.ordinal(), record.type());
        if (!r.passed()) return r;
        r = assertEquals("amount (filledVolume)", 10L, record.amount());
        if (!r.passed()) return r;
        r = assertEquals("price (averageExecutionPrice)", 100L, record.price());
        if (!r.passed()) return r;
        r = assertEquals("time", 5000L, record.time());
        if (!r.passed()) return r;
        return pass("getHistoricalRecord returns correct OrderRecordStruct fields");
    }
}
