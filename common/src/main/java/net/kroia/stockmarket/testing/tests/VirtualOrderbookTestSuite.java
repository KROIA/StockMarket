package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.stockmarket.market.core.DepthMemory;
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
        addTest("jump_up_zeroes_swept_range", this::test_jumpUp_zeroesSweptRange);
        addTest("jump_down_zeroes_swept_range", this::test_jumpDown_zeroesSweptRange);
        // T-198: depth memory
        addTest("depth_memory_profile_mean_nonneg_continuous", this::test_depthMemory_profile);
        addTest("depth_memory_partial_eviction_conserves", this::test_depthMemory_partialEviction);
        addTest("depth_memory_shift_back_fill_equals_read", this::test_depthMemory_shiftBack);
        addTest("depth_memory_conservation_flat_and_production", this::test_depthMemory_conservation);
        addTest("depth_memory_jump_gap_recorded_zero", this::test_depthMemory_jumpGap);
        addTest("depth_memory_fade_half_life_and_off", this::test_depthMemory_fade);
        addTest("array_borders_match_layout", this::test_arrayBorders);
        addTest("depth_memory_nbt_round_trip", this::test_depthMemory_nbt);
        addTest("depth_memory_reset_clears", this::test_depthMemory_resetClears);
        // T-199: real volume
        addTest("depth_memory_materialises_default_plus_leaving", this::test_depthMemory_materialise);
        addTest("depth_memory_reentering_slot_sums_to_volume", this::test_depthMemory_reentering);
        addTest("depth_memory_crossing_keeps_signs", this::test_depthMemory_crossing);
        addTest("side_volume_one_side_only", this::test_sideVolume_oneSideOnly);
        addTest("side_volume_matches_level_scan_with_memory", this::test_sideVolume_matchesLevelScan);
        addTest("side_volume_cap_early_exit", this::test_sideVolume_capEarlyExit);
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

    /**
     * T-164: a jump past the upper window edge must not mirror the old ask shape as bids.
     * The provider emulates production, where it still sees the stale price (50): above 50 it
     * returns the large "ask" magnitude 7, below it the "bid" magnitude 3.
     * After jumping to 200 the swept slots [150,200) must be 0, slots above 200 sell-side.
     */
    private TestResult test_jumpUp_zeroesSweptRange() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setDefaultVolumeProvider((price) -> price > 50 ? 7.0f : 3.0f);
        vob.setCurrentMarketPrice(50);
        vob.resetVolumeDistribution();

        vob.setCurrentMarketPrice(200);
        for (long p = 150; p < 200; p++) {
            float vol = vob.getVolume(p);
            if (Math.abs(vol) > EPSILON)
                return fail("Swept slot " + p + " should be 0 after jump up, got: " + vol);
        }
        for (long p = 201; p < 250; p++) {
            float vol = vob.getVolume(p);
            if (vol >= 0)
                return fail("Slot " + p + " above new price should be sell-side (<0), got: " + vol);
        }
        return pass("Jump up zeroes swept range, keeps sell side above price");
    }

    /**
     * T-164 mirror: a jump past the lower window edge must not mirror the old bid shape as asks.
     * Window starts centred at 300 (provider stale price 300); jump to 150 → (150,200) must be 0,
     * slots below 150 buy-side.
     */
    private TestResult test_jumpDown_zeroesSweptRange() {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setDefaultVolumeProvider((price) -> price < 300 ? 7.0f : 3.0f);
        vob.setCurrentMarketPrice(300);
        vob.resetVolumeDistribution();

        vob.setCurrentMarketPrice(150);
        for (long p = 151; p < 200; p++) {
            float vol = vob.getVolume(p);
            if (Math.abs(vol) > EPSILON)
                return fail("Swept slot " + p + " should be 0 after jump down, got: " + vol);
        }
        for (long p = 100; p < 150; p++) {
            float vol = vob.getVolume(p);
            if (vol <= 0)
                return fail("Slot " + p + " below new price should be buy-side (>0), got: " + vol);
        }
        return pass("Jump down zeroes swept range, keeps buy side below price");
    }

    // ── T-198: depth memory ─────────────────────────────────────────────────

    /** Flat default depth used by the memory tests: 10 per raw tick. */
    private static final float FLAT = 10f;

    /** @return a book with a flat provider, memory on (half-life 30 min), price {@code price} */
    private VirtualOrderbook memoryBook(long price) {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.test_setDepthMemoryHalfLifeSeconds(1800.0);
        vob.setDefaultVolumeProvider(p -> FLAT);
        vob.setCurrentMarketPrice(price);
        vob.resetVolumeDistribution();
        return vob;
    }

    /** @return sum of |volume| over [from, to] */
    private static double absSum(VirtualOrderbook vob, long from, long to) {
        double s = 0;
        for (long p = from; p <= to; p++) s += Math.abs(vob.getVolume(p));
        return s;
    }

    /** Flat default depth for {@link DepthMemory} unit tests: {@link #FLAT} per tick from price 1. */
    private static final DepthMemory.DefaultDepth FLAT_DEPTH = new DepthMemory.DefaultDepth() {
        @Override public double at(long price) { return price < 1 ? 0 : FLAT; }
        @Override public double sum(long lo, long hi) { lo = Math.max(lo, 1); return hi < lo ? 0 : FLAT * (hi - lo + 1); }
    };

    /**
     * Profile of a segment (density units): never negative, cells sum to exactly the stored volume,
     * continuous across slot edges (remembered and missing neighbours) and at the window boundary;
     * also the shifted-kink case (large boundary edge).
     */
    private TestResult test_depthMemory_profile() {
        DepthMemory dm = new DepthMemory();
        int k0 = DepthMemory.slotOf(10000);
        long ws = DepthMemory.slotStart(k0) + 20, we = DepthMemory.slotStart(k0) + 60;
        double[] rho = { 3, 8, 4.5, 6, 9, 3.5, 7 };     // densities; slot k0 + 4 stays missing (default 10)
        for (int i = 0; i < rho.length; i++)
            for (boolean above : new boolean[]{ false, true })
                dm.setVolume(k0 - 3 + i, above, rho[i] * DepthMemory.outside(k0 - 3 + i, above, ws, we));
        java.util.function.LongToDoubleFunction bnd = p -> 5;
        long from = DepthMemory.slotStart(k0 - 3), to = DepthMemory.slotEnd(k0 + 3);
        double prev = Double.NaN;
        for (long p = from; p <= to; p++) {
            if (p >= ws && p <= we) { prev = Double.NaN; continue; }
            DepthMemory.Segment seg = dm.segmentAt(p, ws, we, bnd, FLAT_DEPTH);
            double v = seg.stored ? seg.cellValue(p) : FLAT_DEPTH.at(p);
            if (v < 0) return fail("Negative value " + v + " at " + p);
            if (!Double.isNaN(prev) && Math.abs(v - prev) > 0.5)
                return fail("Jump " + prev + " -> " + v + " at " + p);
            prev = v;
            if (p == seg.lo && seg.stored) {
                double sum = 0;
                for (long q = seg.lo; q <= seg.hi; q++) sum += seg.cellValue(q);
                double vol = dm.getVolume(seg.k, p > we);
                if (Math.abs(sum - vol) > 1e-4 * vol)
                    return fail("Segment " + seg.lo + ".." + seg.hi + " sums to " + sum + " != V " + vol);
            }
        }
        if (Math.abs(dm.segmentAt(ws - 1, ws, we, bnd, FLAT_DEPTH).cellValue(ws - 1) - 5) > 0.5
                || Math.abs(dm.segmentAt(we + 1, ws, we, bnd, FLAT_DEPTH).cellValue(we + 1) - 5) > 0.5)
            return fail("Profile not continuous at the window boundary");
        // Missing neighbour (slot k0 + 4) whose default (2) is thinner than the segment: the edge meets it
        DepthMemory.DefaultDepth thin = new DepthMemory.DefaultDepth() {
            @Override public double at(long price) { return 2; }
            @Override public double sum(long lo, long hi) { return 2.0 * (hi - lo + 1); }
        };
        double last = dm.segmentAt(to, ws, we, bnd, thin).cellValue(to);
        if (Math.abs(last - 2) > 0.5) return fail("Edge next to a missing neighbour reads " + last + ", default 2");
        // Shifted kink: tiny density next to a thick window boundary
        dm.setVolume(k0, false, 0.5 * DepthMemory.outside(k0, false, ws, we));
        DepthMemory.Segment seg = dm.segmentAt(ws - 1, ws, we, p -> 10.0, FLAT_DEPTH);
        double sum = 0;
        for (long q = seg.lo; q <= seg.hi; q++) {
            double v = seg.cellValue(q);
            if (v < 0) return fail("Negative value with shifted kink at " + q);
            sum += v;
        }
        double vol = dm.getVolume(k0, false);
        if (Math.abs(sum - vol) > 1e-4 * vol) return fail("Shifted-kink sum " + sum + " != V " + vol);
        return pass("Profile non-negative, cells sum to V, continuous at slot and window edges");
    }

    /**
     * Partial-overlap eviction: a move that evicts part of a slot (the rest is still in the window
     * or was outside already) keeps the slot's total exactly (flat provider); consumed slots read 0.
     */
    private TestResult test_depthMemory_partialEviction() {
        VirtualOrderbook vob = memoryBook(1000);           // window [950, 1049]
        for (long p = 1001; p <= 1049; p++) vob.setVolume(p, 0f);
        long lo = DepthMemory.slotStart(DepthMemory.slotOf(950));
        double before = absSum(vob, lo, 1029);
        vob.setCurrentMarketPrice(1080);                   // window [1030, 1129], leaving [950, 1029]
        double after = absSum(vob, lo, 1029);
        if (Math.abs(after - before) > 1e-3 * before)
            return fail("Not conserved: " + before + " -> " + after);
        int kFirstEmpty = DepthMemory.slotOf(1001) + 1;
        for (long p = DepthMemory.slotStart(kFirstEmpty); p < DepthMemory.slotStart(DepthMemory.slotOf(1029)); p++)
            if (Math.abs(vob.getVolume(p)) > EPSILON)
                return fail("Consumed tick " + p + " outside the window reads " + vob.getVolume(p));
        return pass("Eviction conserves " + before + " and keeps consumed slots at 0");
    }

    /** Shifting back fills the entering ticks with exactly what was read there before the shift. */
    private TestResult test_depthMemory_shiftBack() {
        VirtualOrderbook vob = memoryBook(1000);
        for (long p = 1001; p <= 1049; p++) vob.setVolume(p, 0f);
        for (long p = 960; p <= 990; p += 3) vob.setVolume(p, 4f);
        vob.setCurrentMarketPrice(1080);                   // window [1030, 1129]
        float[] read = new float[40];
        for (int i = 0; i < 40; i++) read[i] = vob.getVolume(990 + i);
        vob.setCurrentMarketPrice(1040);                   // window [990, 1089], entering [990, 1029]
        if (vob.getMinEditablePrice() != 990) return fail("Unexpected window start " + vob.getMinEditablePrice());
        for (int i = 0; i < 40; i++) {
            float v = vob.getVolume(990 + i);
            if (Math.abs(Math.abs(v) - Math.abs(read[i])) > EPSILON)
                return fail("Tick " + (990 + i) + ": read " + read[i] + " before, filled " + v);
        }
        return pass("Shift-fill equals the values read before the shift");
    }

    // ── T-200: one-sided volume to a target ────────────────────────────────

    /** Reference: sum of |rounded volume| of the asked side over every level from..to (no skipping). */
    private static long scanSide(VirtualOrderbook vob, long from, long to, boolean askSide) {
        long sum = 0;
        int step = to >= from ? 1 : -1;
        for (long p = from; ; p += step) {
            long v = (long) vob.getVolumeRounded(p);
            if (askSide ? v < 0 : v > 0) sum += Math.abs(v);
            if (p == to) return sum;
        }
    }

    /** The bid on the price tick is not counted for ask sizing (and vice versa). */
    private TestResult test_sideVolume_oneSideOnly() {
        VirtualOrderbook vob = memoryBook(1000);           // price tick 1000 holds a bid (+10)
        long asks = vob.sideVolume(1000, 1010, true, Long.MAX_VALUE);
        TestResult r = assertEquals("asks 1001..1010", 100L, asks);
        if (!r.passed()) return r;
        vob.setVolume(1000, -4f);                          // price tick turned into an ask
        long bids = vob.sideVolume(1000, 990, false, Long.MAX_VALUE);
        r = assertEquals("bids 990..999 only", 100L, bids);
        if (!r.passed()) return r;
        return assertEquals("ask on the price tick counted for a buy", 104L, vob.sideVolume(1000, 1010, true, Long.MAX_VALUE));
    }

    /**
     * With remembered empty depth outside the window (consumed asks, then the price moved up), the
     * skipping sum equals a level-by-level scan both ways, and the empty stretch adds nothing.
     */
    private TestResult test_sideVolume_matchesLevelScan() {
        VirtualOrderbook vob = memoryBook(1000);           // window [950, 1049]
        for (long p = 1001; p <= 1049; p++) vob.setVolume(p, 0f);
        vob.setCurrentMarketPrice(1080);                   // window [1030, 1129], [1001, 1049] empty bids now
        for (long[] c : new long[][]{ {1080, 800}, {1080, 1001}, {1080, 1200}, {1080, 3000} }) {
            boolean ask = c[1] > c[0];
            long fast = vob.sideVolume(c[0], c[1], ask, Long.MAX_VALUE);
            long ref = scanSide(vob, c[0], c[1], ask);
            if (fast != ref) return fail("sideVolume(" + c[0] + ", " + c[1] + ") = " + fast + ", level scan " + ref);
        }
        // Same fully consumed slots as depth_memory_partial_eviction_conserves
        long gapHi = DepthMemory.slotStart(DepthMemory.slotOf(1029)) - 1;
        long gapLo = DepthMemory.slotStart(DepthMemory.slotOf(1001) + 1);
        if (gapHi < gapLo) return fail("No fully consumed slot between 1001 and 1029");
        long gap = vob.sideVolume(gapHi, gapLo, false, Long.MAX_VALUE);
        return assertEquals("consumed stretch " + gapLo + ".." + gapHi + " holds no bids", 0L, gap);
    }

    /** The sum stops at the first level that reaches the cap. */
    private TestResult test_sideVolume_capEarlyExit() {
        VirtualOrderbook vob = memoryBook(1000);
        return assertEquals("3 levels of 10 reach cap 25", 30L, vob.sideVolume(1000, 1049, true, 25));
    }

    /** Runs the step-move conservation scenario; returns {before, after} of the total |volume|. */
    private double[] conservationRun(java.util.function.Function<Long, Float> provider) {
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.test_setDepthMemoryHalfLifeSeconds(1800.0);
        vob.setDefaultVolumeProvider(provider);
        vob.setCurrentMarketPrice(1000);
        vob.resetVolumeDistribution();
        for (long p = 1001; p <= 1040; p++) vob.setVolume(p, 0.3f * Math.abs(provider.apply(p)));
        for (long p = 960; p <= 990; p++) vob.setVolume(p, 0f);
        long lo = DepthMemory.slotStart(DepthMemory.slotOf(900));
        long hi = DepthMemory.slotEnd(DepthMemory.slotOf(1800));
        double before = absSum(vob, lo, hi);
        for (long price = 1040; price <= 1600; price += 40)   // small steps: no T-164 zeroing
            vob.setCurrentMarketPrice(price);
        return new double[]{ before, absSum(vob, lo, hi) };
    }

    /**
     * Conservation without fade: total depth over whole slots is unchanged by window moves;
     * T-199 (real volume): exact for the flat provider and for the production shape.
     */
    private TestResult test_depthMemory_conservation() {
        double[] flat = conservationRun(p -> FLAT);
        if (Math.abs(flat[1] - flat[0]) > 1e-3 * flat[0])
            return fail("Flat provider not conserved: " + flat[0] + " -> " + flat[1]);
        DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc =
                new DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator(1000, 50f);
        double[] prod = conservationRun(p -> calc.getVolume(1000, p));
        double err = Math.abs(prod[1] - prod[0]) / prod[0];
        if (err > 1e-3)
            return fail("Production shape off by " + (100 * err) + "%: " + prod[0] + " -> " + prod[1]);
        return pass(String.format(java.util.Locale.ROOT, "flat %.1f -> %.1f, production %.1f -> %.1f (%.3f%%)",
                flat[0], flat[1], prod[0], prod[1], 100 * err));
    }

    /** A jump records the range between both windows as consumed (0); mirror for a down-jump. */
    private TestResult test_depthMemory_jumpGap() {
        VirtualOrderbook up = memoryBook(1000);
        up.setCurrentMarketPrice(5000);                    // gap [1050, 4949]
        VirtualOrderbook down = memoryBook(5000);
        down.setCurrentMarketPrice(1000);                  // gap [1050, 4949]
        for (long p = 1100; p <= 4900; p++) {
            if (Math.abs(up.getVolume(p)) > EPSILON) return fail("Up-jump gap tick " + p + " reads " + up.getVolume(p));
            if (Math.abs(down.getVolume(p)) > EPSILON) return fail("Down-jump gap tick " + p + " reads " + down.getVolume(p));
        }
        if (Math.abs(up.getVolume(900) - FLAT) > EPSILON || Math.abs(down.getVolume(5200) + FLAT) > EPSILON)
            return fail("Untouched depth changed: " + up.getVolume(900) + ", " + down.getVolume(5200));
        return pass("Jump gap recorded as 0 both ways, untouched depth unchanged");
    }

    /**
     * T-209: the display borders match the real layout: linear window edges, the log slots straddling them,
     * log entries off without memory, and the re-centre band edges are exactly where the window starts to move.
     */
    private TestResult test_arrayBorders() {
        long[] b = VirtualOrderbook.arrayBorders(1000, 200, 100_000, true);
        if (b[VirtualOrderbook.BORDER_LINEAR_LO] != 1000 || b[VirtualOrderbook.BORDER_LINEAR_HI] != 1199)
            return fail("Linear edges wrong: " + b[VirtualOrderbook.BORDER_LINEAR_LO] + ".." + b[VirtualOrderbook.BORDER_LINEAR_HI]);
        if (b[VirtualOrderbook.BORDER_RECENTER_LO] != 1050 || b[VirtualOrderbook.BORDER_RECENTER_HI] != 1150)
            return fail("Re-centre band wrong: " + b[VirtualOrderbook.BORDER_RECENTER_LO] + ".." + b[VirtualOrderbook.BORDER_RECENTER_HI]);
        long below = b[VirtualOrderbook.BORDER_LOG_SLOT_BELOW], above = b[VirtualOrderbook.BORDER_LOG_SLOT_ABOVE];
        if (below > 999 || DepthMemory.slotEnd(DepthMemory.slotOf(below)) < 999 || DepthMemory.slotStart(DepthMemory.slotOf(below)) != below)
            return fail("Log slot below the window wrong: " + below);
        if (above < 1200 || DepthMemory.slotStart(DepthMemory.slotOf(above)) > 1200 || DepthMemory.slotEnd(DepthMemory.slotOf(above)) != above)
            return fail("Log slot above the window wrong: " + above);
        if (b[VirtualOrderbook.BORDER_LOG_FLOOR] != 1 || b[VirtualOrderbook.BORDER_ASK_CEILING] != 100_000)
            return fail("Outer edges wrong");
        long[] off = VirtualOrderbook.arrayBorders(1000, 200, 100_000, false);
        if (off[VirtualOrderbook.BORDER_LOG_FLOOR] != VirtualOrderbook.NO_BORDER || off[VirtualOrderbook.BORDER_LOG_SLOT_ABOVE] != VirtualOrderbook.NO_BORDER)
            return fail("Log borders must be NO_BORDER without memory");

        // The band edges are the last prices that keep the window in place.
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.setCurrentMarketPrice(1000);
        long[] w = vob.getArrayBorders();
        long start = vob.getMinEditablePrice();
        if (w[VirtualOrderbook.BORDER_LINEAR_LO] != start || w[VirtualOrderbook.BORDER_LINEAR_HI] != vob.getMaxEditablePrice())
            return fail("Live linear edges do not match the window");
        vob.setCurrentMarketPrice(w[VirtualOrderbook.BORDER_RECENTER_HI]);
        vob.setCurrentMarketPrice(w[VirtualOrderbook.BORDER_RECENTER_LO]);
        if (vob.getMinEditablePrice() != start) return fail("Window moved inside the re-centre band");
        vob.setCurrentMarketPrice(w[VirtualOrderbook.BORDER_RECENTER_HI] + 1);
        if (vob.getMinEditablePrice() == start) return fail("Window did not move above the re-centre band");
        return pass("Borders match the layout");
    }

    /**
     * One half-life halves |V - D|; the target D follows the current default (here: the provider doubles);
     * time below 1 s is accumulated; half-life 0 is memory off (nothing recorded, existing memory wiped).
     */
    private TestResult test_depthMemory_fade() {
        float[] scale = { 1f };
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.test_setDepthMemoryHalfLifeSeconds(60.0);
        vob.setDefaultVolumeProvider(p -> FLAT * scale[0]);
        vob.setCurrentMarketPrice(1000);
        vob.resetVolumeDistribution();
        vob.setCurrentMarketPrice(5000);
        int k = DepthMemory.slotOf(2000);
        double s = DepthMemory.slotEnd(k) - DepthMemory.slotStart(k) + 1;   // whole slot below the window
        DepthMemory dm = vob.getDepthMemory();
        if (dm.getVolume(k, false) != 0) return fail("Gap slot should start at 0, is " + dm.getVolume(k, false));
        vob.fadeDepthMemory(0.5);
        if (dm.getVolume(k, false) != 0) return fail("Half a second must not fade yet");
        vob.fadeDepthMemory(59.5);
        double v = dm.getVolume(k, false);
        if (Math.abs(v - 0.5 * FLAT * s) > 1e-3 * FLAT * s) return fail("After one half-life V should be " + 0.5 * FLAT * s + ", is " + v);
        scale[0] = 2f;                                     // default doubles: V moves halfway toward 2D
        vob.fadeDepthMemory(60.0);
        v = dm.getVolume(k, false);
        double want = 2 * FLAT * s + (0.5 * FLAT * s - 2 * FLAT * s) / 2;
        if (Math.abs(v - want) > 1e-3 * want) return fail("Target should follow the default: V " + v + ", want " + want);
        vob.test_setDepthMemoryHalfLifeSeconds(0.0);
        vob.fadeDepthMemory(0.05);
        if (!vob.getDepthMemory().isEmpty()) return fail("Half-life 0 must wipe the memory");

        VirtualOrderbook off = memoryBook(1000);
        off.test_setDepthMemoryHalfLifeSeconds(0.0);
        off.setCurrentMarketPrice(5000);
        if (!off.getDepthMemory().isEmpty()) return fail("Memory off must record nothing");
        if (Math.abs(Math.abs(off.getVolume(2000)) - FLAT) > EPSILON) return fail("Memory off: gap must read the default");
        return pass("|V - D| halves per half-life toward the current default; half-life 0 = off");
    }

    /** NBT round trip keeps the slots; a save without "lowRes" or in the T-198 ratio format loads empty. */
    private TestResult test_depthMemory_nbt() {
        VirtualOrderbook vob = memoryBook(1000);
        for (long p = 1001; p <= 1049; p++) vob.setVolume(p, 2f);
        vob.setCurrentMarketPrice(5000);
        CompoundTag tag = new CompoundTag();
        vob.save(tag);
        VirtualOrderbook loaded = memoryBook(5000);
        loaded.load(tag);
        DepthMemory a = vob.getDepthMemory(), b = loaded.getDepthMemory();
        if (a.isEmpty() || a.size() != b.size()) return fail("Slot count " + a.size() + " -> " + b.size());
        for (int k = DepthMemory.slotOf(900); k <= DepthMemory.slotOf(5000); k++)
            for (boolean above : new boolean[]{ false, true })
                if (Double.compare(a.getVolume(k, above), b.getVolume(k, above)) != 0)
                    return fail("Slot " + k + "/" + above + ": " + a.getVolume(k, above) + " -> " + b.getVolume(k, above));
        // T-198 ratio format: "k" + "r" -> dropped, empty memory
        CompoundTag old = new CompoundTag();
        old.putIntArray("k", new int[]{ 2 * DepthMemory.slotOf(2000) });
        old.putIntArray("r", new int[]{ Float.floatToIntBits(0.3f) });
        tag.put("lowRes", old);
        loaded.load(tag);
        if (!loaded.getDepthMemory().isEmpty()) return fail("Ratio-format save must load with empty memory");
        tag.remove("lowRes");
        loaded.load(memoryTag(vob));
        loaded.load(tag);
        if (!loaded.getDepthMemory().isEmpty()) return fail("Old save must load with empty memory");
        return pass("NBT round trip of " + a.size() + " slots");
    }

    /** @return a fresh save of {@code vob} */
    private static CompoundTag memoryTag(VirtualOrderbook vob) {
        CompoundTag t = new CompoundTag();
        vob.save(t);
        return t;
    }

    /** Reset and clear of the distribution wipe the memory. */
    private TestResult test_depthMemory_resetClears() {
        VirtualOrderbook vob = memoryBook(1000);
        vob.setCurrentMarketPrice(5000);
        if (vob.getDepthMemory().isEmpty()) return fail("Jump should have created memory");
        vob.resetVolumeDistribution();
        if (!vob.getDepthMemory().isEmpty()) return fail("Reset must wipe the memory");
        vob.setCurrentMarketPrice(1000);
        vob.clearVolume();
        if (!vob.getDepthMemory().isEmpty()) return fail("Clear must wipe the memory");
        VirtualOrderbook swap = memoryBook(1000);
        swap.setCurrentMarketPrice(5000);
        swap.setDefaultVolumeProvider(p -> 2f);
        if (!swap.getDepthMemory().isEmpty()) return fail("A new provider must wipe the memory");
        return pass("Reset, clear and a provider swap wipe the memory");
    }

    // ── T-199: real volume ──────────────────────────────────────────────────

    /**
     * A slot side without memory that ticks leave into materialises as its old default sum plus the real
     * leaving volume (within 0.1%; wide slots at price 10,000, so the Simpson default sum is exercised).
     */
    private TestResult test_depthMemory_materialise() {
        DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc =
                new DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator(10000, 50f);
        java.util.function.Function<Long, Float> prov = p -> calc.getVolume(10000, p);
        VirtualOrderbook vob = new VirtualOrderbook(ARRAY_SIZE);
        vob.test_setDepthMemoryHalfLifeSeconds(1800.0);
        vob.setDefaultVolumeProvider(prov);
        vob.setCurrentMarketPrice(10000);                  // window [9950, 10049]
        vob.resetVolumeDistribution();
        for (long p = 9950; p < 10000; p++) vob.setVolume(p, 0.25f * Math.abs(prov.apply(p)));
        int k = DepthMemory.slotOf(9950);
        long s = DepthMemory.slotStart(k), e = Math.min(DepthMemory.slotEnd(k), 10019);
        if (s >= 9950) return fail("Slot " + k + " starts inside the window, pick another price");
        double want = 0;
        for (long p = s; p < 9950; p++) want += Math.abs(prov.apply(p));                   // D(G_old), exact
        for (long p = 9950; p <= e; p++) want += Math.abs(vob.getVolume(p));               // L, real leaving volume
        vob.setCurrentMarketPrice(10070);                  // window [10020, 10119], leaving [9950, 10019]
        double v = vob.getDepthMemory().getVolume(k, false);
        if (Double.isNaN(v)) return fail("Slot " + k + " did not materialise");
        if (Math.abs(v - want) > 1e-3 * want) return fail("Materialised V " + v + ", want D(G_old) + L = " + want);
        return pass(String.format(java.util.Locale.ROOT, "V %.2f vs %.2f", v, want));
    }

    /**
     * After a stored slot partly re-enters the window, the cells of its remaining segment sum to the stored
     * volume, are non-negative (bids below the price) and continuous.
     */
    private TestResult test_depthMemory_reentering() {
        VirtualOrderbook vob = memoryBook(1000);
        for (long p = 960; p <= 999; p++) vob.setVolume(p, (p % 7) + 1f);
        vob.setCurrentMarketPrice(1200);                   // window [1150, 1249], everything below remembered
        vob.setCurrentMarketPrice(1040);                   // window [990, 1089], [990, 1149] re-entered
        long ws = vob.getMinEditablePrice();
        DepthMemory dm = vob.getDepthMemory();
        for (int k = DepthMemory.slotOf(900); k <= DepthMemory.slotOf(ws - 1); k++) {
            double vol = dm.getVolume(k, false);
            if (Double.isNaN(vol)) continue;
            long lo = DepthMemory.slotStart(k), hi = Math.min(DepthMemory.slotEnd(k), ws - 1);
            double sum = 0, prev = Double.NaN;
            for (long p = lo; p <= hi; p++) {
                float v = vob.getVolume(p);
                if (v < -EPSILON) return fail("Negative bid " + v + " at " + p);
                if (!Double.isNaN(prev) && Math.abs(v - prev) > 2) return fail("Jump " + prev + " -> " + v + " at " + p);
                prev = v;
                sum += v;
            }
            if (Math.abs(sum - vol) > 1e-3 * Math.max(vol, 1))
                return fail("Slot " + k + " cells sum to " + sum + ", V " + vol);
        }
        return pass("Re-entered slots: cells sum to V, non-negative, continuous");
    }

    /**
     * Price crossing remembered depth down and up again never reads flipped or negative volume outside the
     * window (memory values are signed by the current price; inside the window the array is the truth).
     */
    private TestResult test_depthMemory_crossing() {
        VirtualOrderbook vob = memoryBook(1000);
        for (long p = 1001; p <= 1049; p++) vob.setVolume(p, 0f);   // asks consumed by a buy
        for (long p = 960; p <= 990; p++) vob.setVolume(p, 2f);     // thin bids
        for (long price : new long[]{ 1080, 900, 700, 1100, 1300, 1000 }) {
            vob.setCurrentMarketPrice(price);
            for (long p = 600; p <= 1400; p++) {
                if (p >= vob.getMinEditablePrice() && p <= vob.getMaxEditablePrice()) continue;
                float v = vob.getVolume(p);
                if (p > price && v > EPSILON) return fail("Price " + price + ": bid " + v + " above at " + p);
                if (p < price && v < -EPSILON) return fail("Price " + price + ": ask " + v + " below at " + p);
                if (Float.isNaN(v)) return fail("NaN at " + p);
            }
        }
        return pass("Signs follow the current price through down and up crossings");
    }
}
