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
        addTest("get_outside_range", this::test_get_outsideRange);
        addTest("set_within_range", this::test_set_withinRange);
        addTest("get_array_index_overflow_long_to_int", this::test_getArrayIndex_overflowLongToInt);
        addTest("is_in_range_near_integer_max_boundary", this::test_isInRange_nearIntegerMaxBoundary);
        addTest("move_offset_small_negative", this::test_moveOffset_smallNegative);
        addTest("move_offset_negative_larger_than_array_length", this::test_moveOffset_negativeLargerThanArrayLength);
        addTest("move_offset_int_overflow", this::test_moveOffset_intOverflow);
        addTest("set_offset_delegates", this::test_setOffset_delegates);
        addTest("get_sum_partial_range", this::test_getSum_partialRange);
        addTest("get_sum_rounded", this::test_getSumRounded);
        addTest("get_sum_product", this::test_getSumProduct);
        addTest("set_float_array", this::test_set_floatArray);
        addTest("load_invalid_byte_array_length", this::test_load_invalidByteArrayLength);
        addTest("load_missing_fields", this::test_load_missingFields);
        addTest("reset_to_default_values", this::test_resetToDefaultValues);
        addTest("add_within_range", this::test_add_withinRange);
        addTest("multiply_within_range", this::test_multiply_withinRange);
        addTest("set_float_array_negative_start_index", this::test_set_floatArray_negativeStartIndex);
        addTest("add_float_array_negative_start_index", this::test_add_floatArray_negativeStartIndex);
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

    private TestResult test_get_outsideRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 42.0f);
        float val = arr.get(20); // outside range [0, 9]
        if (Math.abs(val - 42.0f) > DELTA) {
            return fail("get() outside range should return default value 42.0 but got " + val);
        }
        return pass("get() outside range returns default value from provider");
    }

    private TestResult test_set_withinRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        boolean result = arr.set(5, 3.14f);
        TestResult r = assertTrue("Setting within range should return true", result);
        if (!r.passed()) return r;
        float val = arr.get(5);
        if (Math.abs(val - 3.14f) > DELTA) {
            return fail("Value at index 5 should be 3.14 but was " + val);
        }
        return pass("set() within range succeeds and stores the value");
    }

    private TestResult test_getArrayIndex_overflowLongToInt() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> -1.0f);
        // Virtual index very far from offset (0) causes potential overflow
        // The fixed getArrayIndex should handle this by returning a clamped value
        long farIndex = (long) Integer.MAX_VALUE + 100L;
        boolean inRange = arr.isInRange(farIndex);
        TestResult r = assertFalse("Index far beyond int range should not be in range", inRange);
        if (!r.passed()) return r;
        // Should return default, not crash
        float val = arr.get(farIndex);
        if (Math.abs(val - (-1.0f)) > DELTA) {
            return fail("get() at overflow index should return default -1.0 but got " + val);
        }
        return pass("getArrayIndex handles long-to-int overflow safely");
    }

    private TestResult test_isInRange_nearIntegerMaxBoundary() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        // offset = 0, array length = 10, so valid range is [0, 9]
        // Test near Integer.MAX_VALUE
        long nearMax = (long) Integer.MAX_VALUE - 1;
        TestResult r = assertFalse("Index near Integer.MAX_VALUE should be out of range", arr.isInRange(nearMax));
        if (!r.passed()) return r;
        // Test that valid boundary works
        r = assertTrue("Index 0 should be in range", arr.isInRange(0));
        if (!r.passed()) return r;
        r = assertTrue("Index 9 should be in range", arr.isInRange(9));
        if (!r.passed()) return r;
        return assertFalse("Index 10 should be out of range", arr.isInRange(10));
    }

    private TestResult test_moveOffset_smallNegative() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> -1.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, (float) (i * 10)); // [0, 10, 20, 30, 40]
        }
        arr.moveOffset(-2);
        // After moveOffset(-2), offset becomes -2
        // Old data shifts right: virtual indices -2,-1 get defaults, 0->0, 1->10, 2->20
        float valNeg2 = arr.get(-2);
        if (Math.abs(valNeg2 - (-1.0f)) > DELTA) {
            return fail("After moveOffset(-2), get(-2) should be default -1.0 but was " + valNeg2);
        }
        float val0 = arr.get(0);
        if (Math.abs(val0 - 0.0f) > DELTA) {
            return fail("After moveOffset(-2), get(0) should be 0.0 but was " + val0);
        }
        float val1 = arr.get(1);
        if (Math.abs(val1 - 10.0f) > DELTA) {
            return fail("After moveOffset(-2), get(1) should be 10.0 but was " + val1);
        }
        return pass("moveOffset with small negative shifts data correctly");
    }

    private TestResult test_moveOffset_negativeLargerThanArrayLength() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 55.0f);
        for (int i = 0; i < 5; i++) {
            arr.set(i, (float) i);
        }
        arr.moveOffset(-10);
        long offset = arr.getIndexOffset();
        if (offset != -10) {
            return fail("Offset should be -10 but was " + offset);
        }
        for (int i = -10; i < -5; i++) {
            float val = arr.get(i);
            if (Math.abs(val - 55.0f) > DELTA) {
                return fail("After large negative moveOffset, index " + i + " should be default 55.0 but was " + val);
            }
        }
        return pass("moveOffset with large negative resets all elements to defaults");
    }

    private TestResult test_moveOffset_intOverflow() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        arr.set(0, 1.0f);
        // Move by a value larger than Integer.MAX_VALUE
        long bigOffset = (long) Integer.MAX_VALUE + 10L;
        arr.moveOffset(bigOffset);
        // All old data should be displaced; elements should be default
        long newOffset = arr.getIndexOffset();
        if (newOffset != bigOffset) {
            return fail("Offset should be " + bigOffset + " but was " + newOffset);
        }
        for (int i = 0; i < 5; i++) {
            float val = arr.get(newOffset + i);
            if (Math.abs(val) > DELTA) {
                return fail("After huge moveOffset, all elements should be default 0.0 but index " + (newOffset + i) + " was " + val);
            }
        }
        return pass("moveOffset larger than Integer.MAX_VALUE handles overflow correctly");
    }

    private TestResult test_setOffset_delegates() {
        DynamicIndexedArray arr1 = new DynamicIndexedArray(5, idx -> 0.0f);
        DynamicIndexedArray arr2 = new DynamicIndexedArray(5, idx -> 0.0f);
        for (int i = 0; i < 5; i++) {
            arr1.set(i, (float) (i + 1));
            arr2.set(i, (float) (i + 1));
        }
        // setOffset(x) should be equivalent to moveOffset(x - currentOffset)
        arr1.moveOffset(3); // offset 0 -> 3, so moveOffset(3)
        arr2.setOffset(3);  // should do the same thing

        TestResult r = assertEquals("offsets should match", arr1.getIndexOffset(), arr2.getIndexOffset());
        if (!r.passed()) return r;
        for (long i = 3; i < 8; i++) {
            float v1 = arr1.get(i);
            float v2 = arr2.get(i);
            if (Math.abs(v1 - v2) > DELTA) {
                return fail("Values differ at index " + i + ": moveOffset=" + v1 + ", setOffset=" + v2);
            }
        }
        return pass("setOffset(x) is equivalent to moveOffset(x - currentOffset)");
    }

    private TestResult test_getSum_partialRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        for (int i = 0; i < 10; i++) {
            arr.set(i, (float) i); // [0,1,2,3,4,5,6,7,8,9]
        }
        float sum = arr.getSum(2, 5); // 2+3+4 = 9
        if (Math.abs(sum - 9.0f) > DELTA) {
            return fail("Sum of indices [2,5) should be 9.0 but was " + sum);
        }
        return pass("getSum over partial range returns correct sum");
    }

    private TestResult test_getSumRounded() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        arr.set(0, 1.7f);  // roundConservative -> 1
        arr.set(1, -2.3f); // roundConservative -> -2
        arr.set(2, 3.9f);  // roundConservative -> 3
        arr.set(3, -0.5f); // roundConservative -> 0
        arr.set(4, 4.1f);  // roundConservative -> 4
        long sum = arr.getSumRounded(0, 5); // 1 + (-2) + 3 + 0 + 4 = 6
        return assertEquals("getSumRounded should individually round then sum", 6L, sum);
    }

    private TestResult test_getSumProduct() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        // offset=0, so virtualIndex == arrayIndex
        arr.set(0, 2.0f);
        arr.set(1, 3.0f);
        arr.set(2, 1.0f);
        arr.set(3, 0.0f);
        arr.set(4, 5.0f);
        // getSumProduct: sum of (element * virtualIndex)
        // = 2*0 + 3*1 + 1*2 + 0*3 + 5*4 = 0+3+2+0+20 = 25
        float sumProduct = arr.getSumProduct(0, 5);
        if (Math.abs(sumProduct - 25.0f) > DELTA) {
            return fail("getSumProduct should be 25.0 but was " + sumProduct);
        }
        return pass("getSumProduct correctly multiplies each element by its virtual index and sums");
    }

    private TestResult test_set_floatArray() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        float[] values = {10.0f, 20.0f, 30.0f};
        boolean result = arr.set(2, values);
        TestResult r = assertTrue("set(virtualIndex, float[]) should return true for valid range", result);
        if (!r.passed()) return r;
        if (Math.abs(arr.get(2) - 10.0f) > DELTA) {
            return fail("Index 2 should be 10.0 but was " + arr.get(2));
        }
        if (Math.abs(arr.get(3) - 20.0f) > DELTA) {
            return fail("Index 3 should be 20.0 but was " + arr.get(3));
        }
        if (Math.abs(arr.get(4) - 30.0f) > DELTA) {
            return fail("Index 4 should be 30.0 but was " + arr.get(4));
        }
        return pass("set(virtualIndex, float[]) overwrites range correctly");
    }

    private TestResult test_load_invalidByteArrayLength() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        CompoundTag tag = new CompoundTag();
        tag.putLong("indexOffset", 0);
        tag.putByteArray("array", new byte[]{1, 2, 3}); // 3 bytes, not divisible by 4
        boolean loaded = arr.load(tag);
        return assertFalse("load() with byte array length not divisible by 4 should return false", loaded);
    }

    private TestResult test_load_missingFields() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> 0.0f);
        CompoundTag tag = new CompoundTag();
        // Missing both indexOffset and array
        boolean loaded = arr.load(tag);
        TestResult r = assertFalse("load() with missing fields should return false", loaded);
        if (!r.passed()) return r;

        // Missing only array
        CompoundTag tag2 = new CompoundTag();
        tag2.putLong("indexOffset", 0);
        boolean loaded2 = arr.load(tag2);
        r = assertFalse("load() with missing array field should return false", loaded2);
        if (!r.passed()) return r;

        return pass("load() correctly rejects tags with missing required fields");
    }

    private TestResult test_resetToDefaultValues() {
        DynamicIndexedArray arr = new DynamicIndexedArray(5, idx -> (float) (idx * 2));
        // Set custom values
        for (int i = 0; i < 5; i++) {
            arr.set(i, 999.0f);
        }
        arr.resetToDefaultValues();
        // After reset, each element should be defaultValueProvider(i + offset)
        // offset=0, so element[i] = i*2
        for (int i = 0; i < 5; i++) {
            float expected = i * 2.0f;
            float actual = arr.get(i);
            if (Math.abs(actual - expected) > DELTA) {
                return fail("After reset, index " + i + " should be " + expected + " but was " + actual);
            }
        }
        return pass("resetToDefaultValues sets all elements to provider output");
    }

    private TestResult test_add_withinRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        arr.set(3, 10.0f);
        boolean result = arr.add(3, 5.0f);
        TestResult r = assertTrue("add() within range should return true", result);
        if (!r.passed()) return r;
        float val = arr.get(3);
        if (Math.abs(val - 15.0f) > DELTA) {
            return fail("After adding 5.0 to 10.0, value should be 15.0 but was " + val);
        }
        // Test add outside range
        boolean outsideResult = arr.add(20, 1.0f);
        return assertFalse("add() outside range should return false", outsideResult);
    }

    private TestResult test_multiply_withinRange() {
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        arr.set(4, 6.0f);
        boolean result = arr.multyply(4, 3.0f);
        TestResult r = assertTrue("multyply() within range should return true", result);
        if (!r.passed()) return r;
        float val = arr.get(4);
        if (Math.abs(val - 18.0f) > DELTA) {
            return fail("After multiplying 6.0 by 3.0, value should be 18.0 but was " + val);
        }
        // Test multiply outside range
        boolean outsideResult = arr.multyply(20, 2.0f);
        return assertFalse("multyply() outside range should return false", outsideResult);
    }

    private TestResult test_set_floatArray_negativeStartIndex() {
        // Create array of size 10, then move offset to 5 so valid range is [5, 14]
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        arr.setOffset(5);

        // set(3, ...) starts before offset 5, so virtualIndex 3 maps to arrayIndex -2
        // srcOffset should be 2, meaning we skip the first 2 elements of the input array
        // and start writing from value[2]=30 at array position 0 (virtualIndex 5)
        float[] values = {10.0f, 20.0f, 30.0f, 40.0f, 50.0f};
        boolean result = arr.set(3, values);
        TestResult r = assertTrue("set() with negative start should still return true", result);
        if (!r.passed()) return r;

        if (Math.abs(arr.get(5) - 30.0f) > DELTA) {
            return fail("arr.get(5) should be 30.0 but was " + arr.get(5));
        }
        if (Math.abs(arr.get(6) - 40.0f) > DELTA) {
            return fail("arr.get(6) should be 40.0 but was " + arr.get(6));
        }
        if (Math.abs(arr.get(7) - 50.0f) > DELTA) {
            return fail("arr.get(7) should be 50.0 but was " + arr.get(7));
        }
        // Indices before offset should remain default (0.0)
        if (Math.abs(arr.get(8) - 0.0f) > DELTA) {
            return fail("arr.get(8) should be unchanged default 0.0 but was " + arr.get(8));
        }
        return pass("set(float[]) with negative start index correctly applies srcOffset");
    }

    private TestResult test_add_floatArray_negativeStartIndex() {
        // Create array of size 10, then move offset to 5 so valid range is [5, 14]
        DynamicIndexedArray arr = new DynamicIndexedArray(10, idx -> 0.0f);
        arr.setOffset(5);

        // Set initial values at indices 5, 6, 7
        arr.set(5, 100.0f);
        arr.set(6, 200.0f);
        arr.set(7, 300.0f);

        // add(3, ...) starts before offset 5, so virtualIndex 3 maps to arrayIndex -2
        // srcOffset should be 2, meaning we skip the first 2 elements of the input array
        // and start adding from value[2]=3 at array position 0 (virtualIndex 5)
        // flipSignAboveVirtualIndex set to 100 so all values stay positive (no sign flip)
        float[] toAdd = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        boolean result = arr.add(3, toAdd, 100);
        TestResult r = assertTrue("add() with negative start should still return true", result);
        if (!r.passed()) return r;

        // arr[5] was 100, added value[2]=3 → 103
        if (Math.abs(arr.get(5) - 103.0f) > DELTA) {
            return fail("arr.get(5) should be 103.0 but was " + arr.get(5));
        }
        // arr[6] was 200, added value[3]=4 → 204
        if (Math.abs(arr.get(6) - 204.0f) > DELTA) {
            return fail("arr.get(6) should be 204.0 but was " + arr.get(6));
        }
        // arr[7] was 300, added value[4]=5 → 305
        if (Math.abs(arr.get(7) - 305.0f) > DELTA) {
            return fail("arr.get(7) should be 305.0 but was " + arr.get(7));
        }
        // Index 8 should be unchanged (0.0)
        if (Math.abs(arr.get(8) - 0.0f) > DELTA) {
            return fail("arr.get(8) should be unchanged default 0.0 but was " + arr.get(8));
        }
        return pass("add(float[]) with negative start index correctly applies srcOffset");
    }
}
