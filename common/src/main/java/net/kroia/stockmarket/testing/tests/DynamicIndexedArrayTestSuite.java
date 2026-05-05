package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;

public class DynamicIndexedArrayTestSuite extends TestSuite {

    private static final float DELTA = 0.0001f;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.ARRAY;
    }

    @Override
    public void registerTests() {
        addTest("get_within_range", this::test_get_withinRange);
        addTest("set_outside_range", this::test_set_outsideRange);
        addTest("move_offset_zero", this::test_moveOffset_zero);
        addTest("move_offset_small_positive", this::test_moveOffset_smallPositive);
        addTest("move_offset_exact_array_length", this::test_moveOffset_exactArrayLength);
        addTest("move_offset_larger_than_array_length", this::test_moveOffset_largerThanArrayLength);
        addTest("get_sum_entire_range", this::test_getSum_entireRange);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
    }

    private TestResult test_get_withinRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        arr.set(3, 42.5f);
        float val = arr.get(3);
        if (Math.abs(val - 42.5f) > DELTA) {
            return fail("Expected 42.5 but got " + val);
        }
        return pass("Set and get within range works correctly");
    }

    private TestResult test_set_outsideRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        boolean result = arr.set(15, 1.0f);
        return assertFalse("Setting outside range should return false", result);
    }

    private TestResult test_moveOffset_zero() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> -1.0f);
        arr.set(0, 10.0f);
        arr.set(1, 20.0f);
        arr.moveOffset(0);
        float v0 = arr.get(0);
        float v1 = arr.get(1);
        if (Math.abs(v0 - 10.0f) > DELTA || Math.abs(v1 - 20.0f) > DELTA) {
            return fail("moveOffset(0) should not change data");
        }
        return pass("moveOffset(0) preserves data");
    }

    private TestResult test_moveOffset_smallPositive() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> -1.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, (float) (i * 10));
        }
        arr.moveOffset(2);
        float val0 = arr.get(2);
        float val1 = arr.get(3);
        if (Math.abs(val0 - 20.0f) > DELTA) {
            return fail("After moveOffset(2), get(2) should be 20.0 but was " + val0);
        }
        if (Math.abs(val1 - 30.0f) > DELTA) {
            return fail("After moveOffset(2), get(3) should be 30.0 but was " + val1);
        }
        float newSlot = arr.get(6);
        if (Math.abs(newSlot - (-1.0f)) > DELTA) {
            return fail("New slot at index 6 should have default value -1.0 but was " + newSlot);
        }
        return pass("moveOffset with small positive shifts data correctly");
    }

    private TestResult test_moveOffset_exactArrayLength() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 99.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, (float) i);
        }
        arr.moveOffset(5);
        for (int i = 5; i < 10; i++) {
            float val = arr.get(i);
            if (Math.abs(val - 99.0f) > DELTA) {
                return fail("After moveOffset(arrayLength), index " + i + " should be default 99.0 but was " + val);
            }
        }
        return pass("moveOffset equal to array length resets all elements to defaults");
    }

    private TestResult test_moveOffset_largerThanArrayLength() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 77.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, (float) i);
        }
        arr.moveOffset(10);
        long offset = arr.getIndexOffset();
        if (offset != 10) {
            return fail("Offset should be 10 but was " + offset);
        }
        for (int i = 10; i < 15; i++) {
            float val = arr.get(i);
            if (Math.abs(val - 77.0f) > DELTA) {
                return fail("After large moveOffset, index " + i + " should be default 77.0 but was " + val);
            }
        }
        return pass("moveOffset larger than array length resets all elements to defaults");
    }

    private TestResult test_getSum_entireRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, 2.0f);
        }
        float sum = arr.getSum(0, 5);
        if (Math.abs(sum - 10.0f) > DELTA) {
            return fail("Sum of 5 elements of value 2.0 should be 10.0 but was " + sum);
        }
        return pass("getSum over entire range returns correct sum");
    }

    private TestResult test_save_load_roundTrip() {
        DynamicIndexedArray original = new DynamicIndexedArray(5, idx -> 0.0f);
        original.moveOffset(100);
        for (long i = 100; i < 105; i++) {
            original.set(i, (float) (i - 100) * 3.14f);
        }

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        DynamicIndexedArray loaded = new DynamicIndexedArray(5, idx -> 0.0f);
        if (!loaded.load(tag)) {
            return fail("load() returned false");
        }

        if (loaded.getIndexOffset() != original.getIndexOffset()) {
            return fail("indexOffset mismatch after round-trip");
        }
        for (long i = 100; i < 105; i++) {
            float origVal = original.get(i);
            float loadVal = loaded.get(i);
            if (Math.abs(origVal - loadVal) > DELTA) {
                return fail("Value mismatch at index " + i + ": expected " + origVal + " got " + loadVal);
            }
        }
        return pass("Save/load round-trip preserves offset and data");
    }
}
