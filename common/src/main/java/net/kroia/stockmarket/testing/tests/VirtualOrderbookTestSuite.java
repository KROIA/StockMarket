package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.market.core.VirtualOrderbook;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;

public class VirtualOrderbookTestSuite extends TestSuite {

    private static final int ARRAY_SIZE = 100;
    private static final float EPSILON = 0.001f;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VIRTUAL_ORDERBOOK;
    }

    @Override
    public void registerTests() {
        addTest("set_volume_above_market_price_negative", this::test_setVolume_aboveMarketPrice_negative);
        addTest("set_volume_below_market_price_positive", this::test_setVolume_belowMarketPrice_positive);
        addTest("set_volume_at_market_price_preserved", this::test_setVolume_atMarketPrice_preserved);
        addTest("set_volume_range_flips_at_market_price", this::test_setVolume_range_flipsAtMarketPrice);
        addTest("add_volume_clamps_correctly", this::test_addVolume_clampsCorrectly);
        addTest("get_volume_delegates_to_dynamic_array", this::test_getVolume_delegatesToDynamicArray);
        addTest("get_volume_range_sums_correctly", this::test_getVolume_range_sumsCorrectly);
        addTest("get_volume_interpolated_subdivisions", this::test_getVolumeInterpolated_subdivisions);
        addTest("get_default_volume_with_provider", this::test_getDefaultVolume_withProvider);
        addTest("get_default_volume_no_provider", this::test_getDefaultVolume_noProvider);
        addTest("set_current_market_price_recenters_array", this::test_setCurrentMarketPrice_recentersArray);
        addTest("reset_volume_distribution", this::test_resetVolumeDistribution);
        addTest("round_conservative_positive", this::test_roundConservative_positive);
        addTest("round_conservative_negative", this::test_roundConservative_negative);
        addTest("round_conservative_zero", this::test_roundConservative_zero);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("get_capital_sums_products", this::test_getCapital_sumsProducts);
        addTest("duplicate_round_conservative_matches", this::test_duplicateRoundConservative_matchesDynamicIndexedArray);
    }

    private VirtualOrderbook createOrderbook(long marketPrice) {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setCurrentMarketPrice(marketPrice);
        return vob;
    }

    /**
     * Volume set above market price should be stored as negative (sell side).
     */
    private TestResult test_setVolume_aboveMarketPrice_negative() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(60, 10.0f);
        float vol = vob.getVolume(60);
        return assertTrue("Volume above market price should be negative, got: " + vol, vol < 0);
    }

    /**
     * Volume set below market price should be stored as positive (buy side).
     */
    private TestResult test_setVolume_belowMarketPrice_positive() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(40, 10.0f);
        float vol = vob.getVolume(40);
        return assertTrue("Volume below market price should be positive, got: " + vol, vol > 0);
    }

    /**
     * Volume set at exactly market price should be stored as-is.
     */
    private TestResult test_setVolume_atMarketPrice_preserved() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(50, 7.5f);
        float vol = vob.getVolume(50);
        return assertTrue("Volume at market price should be preserved as 7.5, got: " + vol,
                Math.abs(vol - 7.5f) < EPSILON);
    }

    /**
     * Range spanning market price has positive below, negative above.
     */
    private TestResult test_setVolume_range_flipsAtMarketPrice() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(45, 55, 10.0f);

        float volBelow = vob.getVolume(47);
        TestResult r = assertTrue("Volume below market price should be positive, got: " + volBelow,
                volBelow >= 0);
        if (!r.passed()) return r;

        float volAbove = vob.getVolume(53);
        r = assertTrue("Volume above market price should be negative, got: " + volAbove,
                volAbove <= 0);
        if (!r.passed()) return r;

        return pass("Range set correctly flips sign at market price");
    }

    /**
     * addVolume clamps: sell side <= 0, buy side >= 0.
     */
    private TestResult test_addVolume_clampsCorrectly() {
        VirtualOrderbook vob = createOrderbook(50);
        // Set initial volume above market price (sell side, negative)
        vob.setVolume(60, 10.0f); // becomes -10
        // Add positive volume to sell side -- should be clamped to <= 0
        vob.addVoume(60, 20.0f);
        float vol = vob.getVolume(60);
        TestResult r = assertTrue("Sell side should remain <= 0 after adding positive volume, got: " + vol,
                vol <= 0);
        if (!r.passed()) return r;

        // Set initial volume below market price (buy side, positive)
        vob.setVolume(40, 10.0f); // becomes +10
        // Add negative volume to buy side -- should be clamped to >= 0
        vob.addVoume(40, -20.0f);
        float volBuy = vob.getVolume(40);
        r = assertTrue("Buy side should remain >= 0 after adding negative volume, got: " + volBuy,
                volBuy >= 0);
        if (!r.passed()) return r;

        return pass("addVolume clamps correctly on both sides");
    }

    /**
     * getVolume for a single price should match what was set.
     */
    private TestResult test_getVolume_delegatesToDynamicArray() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(40, 15.0f); // buy side, becomes +15
        float vol = vob.getVolume(40);
        return assertTrue("Volume at price 40 should be 15.0, got: " + vol,
                Math.abs(vol - 15.0f) < EPSILON);
    }

    /**
     * Range volume sums correctly (start to end inclusive).
     */
    private TestResult test_getVolume_range_sumsCorrectly() {
        VirtualOrderbook vob = createOrderbook(50);
        // Set volume at several buy-side prices
        vob.setVolume(40, 5.0f);
        vob.setVolume(41, 3.0f);
        vob.setVolume(42, 2.0f);
        // Range sum of [40, 42] inclusive
        float rangeVol = vob.getVolume(40, 42);
        return assertTrue("Range volume [40,42] should be ~10.0, got: " + rangeVol,
                Math.abs(rangeVol - 10.0f) < EPSILON);
    }

    /**
     * Interpolation with fewer subdivisions than range.
     */
    private TestResult test_getVolumeInterpolated_subdivisions() {
        VirtualOrderbook vob = createOrderbook(50);
        // Set uniform volume on buy side
        for (long p = 30; p <= 49; p++) {
            vob.setVolume(p, 1.0f);
        }
        // Interpolate with 5 subdivisions over range [30, 49]
        float interpolated = vob.getVolumeInterpolated(30, 49, 5);
        // With 5 subdivisions, each sub covers (49-30)/5 = ~3.8 prices
        // The interpolation should produce a non-zero result
        return assertTrue("Interpolated volume should be non-zero, got: " + interpolated,
                Math.abs(interpolated) > EPSILON);
    }

    /**
     * Default volume provider called, result sign-flipped based on market price.
     */
    private TestResult test_getDefaultVolume_withProvider() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setDefaultVolumeProvider((price) -> 10.0f);
        vob.setCurrentMarketPrice(50);

        // Price below market -> positive
        float belowDefault = vob.getDefaultVolume(40);
        TestResult r = assertTrue("Default volume below market should be positive, got: " + belowDefault,
                belowDefault > 0);
        if (!r.passed()) return r;

        // Price above market -> negative
        float aboveDefault = vob.getDefaultVolume(60);
        r = assertTrue("Default volume above market should be negative, got: " + aboveDefault,
                aboveDefault < 0);
        if (!r.passed()) return r;

        return pass("Default volume provider correctly sign-flips based on market price");
    }

    /**
     * Returns 0 when no provider set.
     */
    private TestResult test_getDefaultVolume_noProvider() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        // No provider set (null by default)
        vob.setCurrentMarketPrice(50);
        float vol = vob.getDefaultVolume(40);
        return assertTrue("Default volume without provider should be 0, got: " + vol,
                Math.abs(vol) < EPSILON);
    }

    /**
     * Price outside 25%-75% band triggers array reposition.
     */
    private TestResult test_setCurrentMarketPrice_recentersArray() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setCurrentMarketPrice(50);
        long minBefore = vob.getMinEditablePrice();
        // Move market price far enough to trigger recentering
        vob.setCurrentMarketPrice(50 + ARRAY_SIZE);
        long minAfter = vob.getMinEditablePrice();
        return assertTrue("Array should be recentered after large price move (minBefore=" + minBefore + ", minAfter=" + minAfter + ")",
                minAfter > minBefore);
    }

    /**
     * resetVolumeDistribution resets all elements to default values.
     */
    private TestResult test_resetVolumeDistribution() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setDefaultVolumeProvider((price) -> 5.0f);
        vob.setCurrentMarketPrice(50);
        // Set custom volume
        vob.setVolume(40, 99.0f);
        // Reset
        vob.resetVolumeDistribution();
        // After reset, volume at price 40 (below market) should be the default (abs(5.0) = 5.0)
        float vol = vob.getVolume(40);
        return assertTrue("After reset, volume should return to default (~5.0), got: " + vol,
                Math.abs(vol - 5.0f) < EPSILON);
    }

    /**
     * roundConservative(3.7f) == 3
     */
    private TestResult test_roundConservative_positive() {
        long result = VirtualOrderbook.roundConservative(3.7f);
        return assertEquals("roundConservative(3.7f) should be 3", 3L, result);
    }

    /**
     * roundConservative(-3.7f) == -3
     */
    private TestResult test_roundConservative_negative() {
        long result = VirtualOrderbook.roundConservative(-3.7f);
        return assertEquals("roundConservative(-3.7f) should be -3", -3L, result);
    }

    /**
     * roundConservative(0f) == 0
     */
    private TestResult test_roundConservative_zero() {
        long result = VirtualOrderbook.roundConservative(0f);
        return assertEquals("roundConservative(0f) should be 0", 0L, result);
    }

    /**
     * Save/load preserves array data and offsets.
     */
    private TestResult test_save_load_roundTrip() {
        VirtualOrderbook original = new VirtualOrderbook(ARRAY_SIZE);
        original.setCurrentMarketPrice(50);
        original.setVolume(40, 10.0f);
        original.setVolume(60, 5.0f);

        CompoundTag tag = new CompoundTag();
        boolean saved = original.save(tag);
        TestResult r = assertTrue("save() should return true", saved);
        if (!r.passed()) return r;

        VirtualOrderbook loaded = new VirtualOrderbook(ARRAY_SIZE);
        loaded.setCurrentMarketPrice(50);
        boolean loadResult = loaded.load(tag);
        r = assertTrue("load() should return true", loadResult);
        if (!r.passed()) return r;

        float volAt40 = loaded.getVolume(40);
        r = assertTrue("Volume at 40 should be ~10.0 after load, got: " + volAt40,
                Math.abs(volAt40 - 10.0f) < EPSILON);
        if (!r.passed()) return r;

        float volAt60 = loaded.getVolume(60);
        r = assertTrue("Volume at 60 should be ~-5.0 after load, got: " + volAt60,
                Math.abs(volAt60 - (-5.0f)) < EPSILON);
        if (!r.passed()) return r;

        return pass("Save/load round-trip preserves array data");
    }

    /**
     * Capital = sum of (volume * price) across range.
     */
    private TestResult test_getCapital_sumsProducts() {
        VirtualOrderbook vob = createOrderbook(50);
        vob.setVolume(40, 2.0f); // buy side: +2 at price 40
        vob.setVolume(41, 3.0f); // buy side: +3 at price 41
        // Expected capital = 2*40 + 3*41 = 80 + 123 = 203
        float capital = vob.getCapital(40, 41);
        return assertTrue("Capital should be ~203.0, got: " + capital,
                Math.abs(capital - 203.0f) < EPSILON);
    }

    /**
     * Both VirtualOrderbook.roundConservative and DynamicIndexedArray.roundConservative
     * should produce the same results.
     */
    private TestResult test_duplicateRoundConservative_matchesDynamicIndexedArray() {
        float[] testValues = {3.7f, -3.7f, 0f, 1.0f, -1.0f, 99.99f, -0.01f};
        for (float v : testValues) {
            long vobResult = VirtualOrderbook.roundConservative(v);
            long diaResult = DynamicIndexedArray.roundConservative(v);
            if (vobResult != diaResult) {
                return fail("roundConservative(" + v + ") mismatch: VirtualOrderbook=" + vobResult
                        + " vs DynamicIndexedArray=" + diaResult);
            }
        }
        return pass("VirtualOrderbook.roundConservative matches DynamicIndexedArray.roundConservative");
    }
}
