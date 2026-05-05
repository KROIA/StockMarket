package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
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
        addTest("bot_order_null_executor", this::test_botOrder_nullExecutor);
    }

    private TestResult test_getAverageExecutionPrice_zeroDivision() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        return assertThrows(
                "Fresh order with filledVolume==0 should throw ArithmeticException on getAverageExecutionPrice",
                ArithmeticException.class,
                order::getAverageExecutionPrice
        );
    }

    private TestResult test_getAverageExecutionPrice_normalBuy() {
        Order order = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
        order.edit(5, -500);
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

        return assertThrows(
                "Loading an Order with Type ordinal=99 should throw ArrayIndexOutOfBoundsException",
                ArrayIndexOutOfBoundsException.class,
                () -> Order.createFromNBT(tag)
        );
    }

    private TestResult test_botOrder_nullExecutor() {
        Order botOrder = new Order(DUMMY_ITEM_ID, Order.Type.MARKET, 5, 50, 0);
        if (!botOrder.isBotOrder()) {
            return fail("Bot-constructed order should report isBotOrder() == true");
        }
        return assertNull("Bot order should have null executor UUID", botOrder.getExecutorPlayerUUID());
    }
}
