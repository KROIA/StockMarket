package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin.FlowBand;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin.Settings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Tests for the pure {@link VolatilityPlugin#computeEquilibriumPrice} helper
 * and the backward-compatible {@link Settings#CODEC}.
 *
 * Sign convention under test (matches ServerMarket.trackPlayerNetFlow):
 * players net-SELL items into the market -> netPlayerItemFlow > 0 -> price DOWN.
 * Players net-BUY items from the market -> netPlayerItemFlow < 0 -> price UP.
 */
public class VolatilityPluginTestSuite extends TestSuite {

    private static final double EPSILON = 1e-9;
    private static final double DEFAULT_PRICE = 100.0;
    private static final float ABUNDANCE = 10f;

    /**
     * Settings with defaults for everything (flow influence enabled), except the T-204 deadband,
     * which is pinned to 0 explicitly: these tests exercise the raw flow math and must not depend on
     * the auto-deadband multiple k. The deadband has its own tests below.
     */
    private static Settings defaults() {
        return Settings.createDefault().withDeadbandVolume(0f);
    }

    /** Deadband (items) used by the T-204 deadband tests (= ~0.33 x capacity at the test abundance). */
    private static final float DEADBAND = 5000f;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VOLATILITY_PLUGIN;
    }

    @Override
    public void registerTests() {
        addTest("flow_zero_identity", this::test_flowZero_identity);
        addTest("disabled_identity", this::test_disabled_identity);
        addTest("null_settings_identity", this::test_nullSettings_identity);
        addTest("sell_lowers_price", this::test_sell_lowersPrice);
        addTest("buy_raises_price", this::test_buy_raisesPrice);
        addTest("monotonic_in_flow", this::test_monotonicInFlow);
        addTest("abundance_scaling", this::test_abundanceScaling);
        addTest("clamped_at_min_mult", this::test_clampedAtMinMult);
        addTest("clamped_at_max_mult", this::test_clampedAtMaxMult);
        addTest("extreme_flows_stay_positive_and_finite", this::test_extremeFlows_stayPositiveAndFinite);
        addTest("nan_flow_returns_default", this::test_nanFlow_returnsDefault);
        addTest("invalid_capacity_disables_flow_term", this::test_invalidCapacity_disablesFlowTerm);
        addTest("invalid_mults_are_sanitized", this::test_invalidMults_areSanitized);
        addTest("factor_e_at_capacity", this::test_factorE_atCapacity);
        addTest("codec_round_trip", this::test_codec_roundTrip);
        addTest("codec_legacy_payload_uses_defaults", this::test_codec_legacyPayload_usesDefaults);
        addTest("flow_band_bounds", this::test_flowBand_bounds);
        addTest("update_saturates_flow_and_buyback_moves_price", this::test_update_saturatesFlow_buybackMovesPrice);
        // T-204 deadband
        addTest("deadband_inside_band_default_price", this::test_deadband_insideBand_defaultPrice);
        addTest("deadband_edge_continuity", this::test_deadband_edgeContinuity);
        addTest("deadband_shifts_curve", this::test_deadband_shiftsCurve);
        addTest("deadband_buy_sell_symmetry", this::test_deadband_buySellSymmetry);
        addTest("deadband_cap_still_reachable", this::test_deadband_capStillReachable);
        addTest("deadband_codec_legacy_gets_auto", this::test_deadband_codecLegacy_getsAuto);
        addTest("deadband_auto_stays_while_k_zero", this::test_deadband_autoStaysWhileKZero);
        addTest("deadband_auto_resolves_and_persists", this::test_deadband_autoResolvesAndPersists);
        addTest("deadband_k_zero_end_to_end", this::test_deadband_kZero_endToEnd);
        // T-204 asymmetric flow smoothing
        addTest("smoothing_rise_slower_than_return", this::test_smoothing_riseSlowerThanReturn);
        addTest("smoothing_buy_sell_symmetric", this::test_smoothing_buySellSymmetric);
        addTest("smoothing_zero_times_exact", this::test_smoothing_zeroTimes_exact);
        addTest("smoothing_sign_flip_clamps_at_zero", this::test_smoothing_signFlip_clampsAtZero);
        addTest("smoothing_nbt_round_trip_and_legacy", this::test_smoothing_nbtRoundTrip_andLegacy);
    }

    /**
     * With zero player flow the equilibrium must be bit-identical to the
     * default price (same behavior as before the feature existed).
     */
    private TestResult test_flowZero_identity() {
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 0.0, ABUNDANCE, defaults());
        return assertTrue("Equilibrium must be exactly defaultPrice for flow=0, got: " + eq,
                eq == DEFAULT_PRICE);
    }

    /**
     * With flowInfluenceEnabled=false, even huge flows must not move the equilibrium.
     */
    private TestResult test_disabled_identity() {
        Settings s = new Settings(0.3f, false, 500f, 0.05f, 20f);
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1e12, ABUNDANCE, s);
        return assertTrue("Equilibrium must be exactly defaultPrice when disabled, got: " + eq,
                eq == DEFAULT_PRICE);
    }

    /**
     * Null settings must behave like a disabled flow term (defensive fallback).
     */
    private TestResult test_nullSettings_identity() {
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 5000.0, ABUNDANCE, null);
        return assertTrue("Equilibrium must be exactly defaultPrice for null settings, got: " + eq,
                eq == DEFAULT_PRICE);
    }

    /**
     * Players net-selling items (flow > 0) must lower the equilibrium price.
     */
    private TestResult test_sell_lowersPrice() {
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1000.0, ABUNDANCE, defaults());
        TestResult r = assertTrue("Sell flow must lower price, got: " + eq, eq < DEFAULT_PRICE);
        if (!r.passed()) return r;
        return assertTrue("Price must stay positive, got: " + eq, eq > 0.0);
    }

    /**
     * Players net-buying items (flow < 0) must raise the equilibrium price.
     */
    private TestResult test_buy_raisesPrice() {
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, -1000.0, ABUNDANCE, defaults());
        return assertTrue("Buy flow must raise price, got: " + eq, eq > DEFAULT_PRICE);
    }

    /**
     * The equilibrium must be monotonically non-increasing as the net sell flow grows.
     */
    private TestResult test_monotonicInFlow() {
        Settings s = defaults();
        double prev = Double.MAX_VALUE;
        for (double flow = -50000.0; flow <= 50000.0; flow += 2500.0) {
            double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, ABUNDANCE, s);
            if (eq > prev + EPSILON) {
                return fail("Equilibrium must not increase with more sell flow: flow=" + flow
                        + " eq=" + eq + " prev=" + prev);
            }
            prev = eq;
        }
        return pass("Equilibrium is monotonically non-increasing in net sell flow");
    }

    /**
     * A more abundant item must react less to the same absolute flow
     * (higher abundance -> larger capacity -> smaller price move).
     */
    private TestResult test_abundanceScaling() {
        Settings s = defaults();
        double flow = 2000.0;
        double eqRare = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, 5f, s);
        double eqCommon = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, 200f, s);
        // Both drop (sell flow), but the common item must drop less (stay closer to default)
        TestResult r = assertTrue("Rare item must drop, got: " + eqRare, eqRare < DEFAULT_PRICE);
        if (!r.passed()) return r;
        return assertTrue("Common item (abundance 200) must move less than rare item (abundance 5): common="
                + eqCommon + " rare=" + eqRare, eqCommon > eqRare);
    }

    /**
     * Extreme sell flow must clamp exactly at defaultPrice * minPriceMult.
     */
    private TestResult test_clampedAtMinMult() {
        Settings s = new Settings(0.3f, true, 500f, 0.05f, 20f);
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1e15, ABUNDANCE, s);
        double expected = DEFAULT_PRICE * 0.05;
        return assertTrue("Equilibrium must clamp at minPriceMult (" + expected + "), got: " + eq,
                Math.abs(eq - expected) < 1e-6);
    }

    /**
     * Extreme buy flow must clamp exactly at defaultPrice * maxPriceMult.
     */
    private TestResult test_clampedAtMaxMult() {
        Settings s = new Settings(0.3f, true, 500f, 0.05f, 20f);
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, -1e15, ABUNDANCE, s);
        double expected = DEFAULT_PRICE * 20.0;
        return assertTrue("Equilibrium must clamp at maxPriceMult (" + expected + "), got: " + eq,
                Math.abs(eq - expected) < 1e-6);
    }

    /**
     * Long.MAX_VALUE-scale and infinite flows must never produce 0, negative,
     * NaN or infinite prices (clamping happens before Math.exp).
     */
    private TestResult test_extremeFlows_stayPositiveAndFinite() {
        Settings s = defaults();
        double[] flows = {Long.MAX_VALUE, -(double) Long.MAX_VALUE, 1e300, -1e300,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double flow : flows) {
            double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, ABUNDANCE, s);
            if (!(eq > 0.0) || Double.isInfinite(eq) || Double.isNaN(eq)) {
                return fail("Equilibrium must stay positive and finite for flow=" + flow + ", got: " + eq);
            }
        }
        return pass("Extreme flows produce positive, finite equilibrium prices");
    }

    /**
     * NaN flow must fall back to the default price instead of propagating NaN.
     */
    private TestResult test_nanFlow_returnsDefault() {
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, Double.NaN, ABUNDANCE, defaults());
        return assertTrue("NaN flow must return exactly defaultPrice, got: " + eq, eq == DEFAULT_PRICE);
    }

    /**
     * Zero/negative abundance or sensitivity makes the capacity invalid;
     * the flow term must be disabled (default price returned) instead of
     * dividing by zero.
     */
    private TestResult test_invalidCapacity_disablesFlowTerm() {
        // abundance = 0 -> capacity 0
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1000.0, 0f, defaults());
        TestResult r = assertTrue("Zero abundance must return defaultPrice, got: " + eq, eq == DEFAULT_PRICE);
        if (!r.passed()) return r;

        // sensitivity = 0 -> capacity 0
        Settings zeroSens = new Settings(0.3f, true, 0f, 0.05f, 20f);
        eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1000.0, ABUNDANCE, zeroSens);
        r = assertTrue("Zero sensitivity must return defaultPrice, got: " + eq, eq == DEFAULT_PRICE);
        if (!r.passed()) return r;

        // negative sensitivity -> capacity < 0
        Settings negSens = new Settings(0.3f, true, -5f, 0.05f, 20f);
        eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1000.0, ABUNDANCE, negSens);
        r = assertTrue("Negative sensitivity must return defaultPrice, got: " + eq, eq == DEFAULT_PRICE);
        if (!r.passed()) return r;

        // NaN sensitivity -> capacity NaN
        Settings nanSens = new Settings(0.3f, true, Float.NaN, 0.05f, 20f);
        eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1000.0, ABUNDANCE, nanSens);
        return assertTrue("NaN sensitivity must return defaultPrice, got: " + eq, eq == DEFAULT_PRICE);
    }

    /**
     * Multipliers violating 0 < min < 1 < max must be sanitized to the defaults
     * instead of crashing or producing prices <= 0.
     */
    private TestResult test_invalidMults_areSanitized() {
        // min <= 0, max <= 1: both invalid -> defaults (0.05 / 20) must apply
        Settings bad = new Settings(0.3f, true, 500f, -1f, 0.5f);
        double eqLow = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1e15, ABUNDANCE, bad);
        TestResult r = assertTrue("Sanitized min clamp must be defaultPrice * 0.05, got: " + eqLow,
                Math.abs(eqLow - DEFAULT_PRICE * VolatilityPlugin.DEFAULT_MIN_PRICE_MULT) < 1e-6);
        if (!r.passed()) return r;

        double eqHigh = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, -1e15, ABUNDANCE, bad);
        r = assertTrue("Sanitized max clamp must be defaultPrice * 20, got: " + eqHigh,
                Math.abs(eqHigh - DEFAULT_PRICE * VolatilityPlugin.DEFAULT_MAX_PRICE_MULT) < 1e-6);
        if (!r.passed()) return r;

        // NaN multipliers must also be sanitized
        Settings nan = new Settings(0.3f, true, 500f, Float.NaN, Float.NaN);
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, 1e15, ABUNDANCE, nan);
        return assertTrue("NaN mults must be sanitized, price must stay positive/finite, got: " + eq,
                eq > 0.0 && !Double.isNaN(eq) && !Double.isInfinite(eq));
    }

    /**
     * Selling exactly one "capacity" of items must move the price by a factor of 1/e.
     * capacity = flowSensitivity * abundance / (1 + volatilityScale).
     */
    private TestResult test_factorE_atCapacity() {
        float volatilityScale = 0.3f;
        float sensitivity = 500f;
        Settings s = new Settings(volatilityScale, true, sensitivity, 0.001f, 1000f);
        double capacity = (double) sensitivity * ABUNDANCE / (1.0 + volatilityScale);
        double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, capacity, ABUNDANCE, s);
        double expected = DEFAULT_PRICE / Math.E;
        return assertTrue("Selling one capacity must divide the price by e, expected: " + expected
                + " got: " + eq, Math.abs(eq - expected) < 1e-6);
    }

    /**
     * The codec (8 fields since T-204) must round-trip all values.
     */
    private TestResult test_codec_roundTrip() {
        Settings original = new Settings(0.7f, false, 123.5f, 98765f, 60f, 10f, 0.1f, 42f);
        ByteBuf buf = Unpooled.buffer();
        try {
            Settings.CODEC.encode(buf, original);
            Settings decoded = Settings.CODEC.decode(buf);
            return assertEquals("Codec round-trip must preserve all fields", original, decoded);
        } finally {
            buf.release();
        }
    }

    /**
     * A legacy payload (single float, the old 1-field format from saved worlds)
     * must decode without crashing: volatilityScale is taken from the payload,
     * all new fields get their defaults.
     */
    private TestResult test_codec_legacyPayload_usesDefaults() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeFloat(0.55f); // old format: only volatilityScale
            Settings decoded = Settings.CODEC.decode(buf);
            TestResult r = assertEquals("volatilityScale must come from the legacy payload",
                    0.55f, decoded.volatilityScale());
            if (!r.passed()) return r;
            Settings expected = new Settings(0.55f,
                    VolatilityPlugin.DEFAULT_FLOW_INFLUENCE_ENABLED,
                    VolatilityPlugin.DEFAULT_FLOW_SENSITIVITY,
                    VolatilityPlugin.DEFAULT_DEADBAND_VOLUME,
                    VolatilityPlugin.DEFAULT_FLOW_RISE_TIME_SECONDS,
                    VolatilityPlugin.DEFAULT_FLOW_RETURN_TIME_SECONDS,
                    VolatilityPlugin.DEFAULT_MIN_PRICE_MULT,
                    VolatilityPlugin.DEFAULT_MAX_PRICE_MULT);
            return assertEquals("New fields must fall back to defaults for legacy payloads",
                    expected, decoded);
        } finally {
            buf.release();
        }
    }

    /**
     * T-174: the saturation band is exactly where the equilibrium hits the clamps
     * (upper = sell side -> floor, lower = buy side -> ceiling), and is null when the
     * flow term is inactive.
     */
    private TestResult test_flowBand_bounds() {
        Settings s = new Settings(0.3f, true, 500f, 0.05f, 20f);
        FlowBand band = VolatilityPlugin.flowBand(ABUNDANCE, s);
        if (band == null) return fail("Band must exist for valid settings");
        // Same float->double casts as the plugin (0.3f/0.05f are not exact in double).
        double capacity = (double) s.flowSensitivity() * ABUNDANCE / (1.0 + s.volatilityScale());
        double expUpper = -capacity * Math.log(s.minPriceMult());
        double expLower = -capacity * Math.log(s.maxPriceMult());
        if (!relClose(band.upper(), expUpper) || !(band.upper() > 0))
            return fail("upper must be +capacity*ln(1/minMult) = " + expUpper + ", got " + band.upper());
        if (!relClose(band.lower(), expLower) || !(band.lower() < 0))
            return fail("lower must be -capacity*ln(maxMult) = " + expLower + ", got " + band.lower());
        double floor = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, band.upper(), ABUNDANCE, s);
        double ceil = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, band.lower(), ABUNDANCE, s);
        if (!relClose(floor, DEFAULT_PRICE * s.minPriceMult()) || !relClose(ceil, DEFAULT_PRICE * s.maxPriceMult()))
            return fail("Band edges must map to floor/ceiling, got " + floor + " / " + ceil);
        if (VolatilityPlugin.flowBand(ABUNDANCE, new Settings(0.3f, false, 500f, 0.05f, 20f)) != null)
            return fail("Band must be null when flow influence is disabled");
        if (VolatilityPlugin.flowBand(0f, s) != null)
            return fail("Band must be null for invalid capacity");
        return pass("Flow band matches the equilibrium clamps");
    }

    /** Relative comparison (1e-9) for values derived from float settings. */
    private static boolean relClose(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-9 * Math.max(1.0, Math.abs(expected));
    }

    /**
     * T-174/T-207 scenario: a huge sell saturates only the plugin-private flow at the band edge;
     * the market's counter stays exact (uncapped). A 1-item buy-back still raises the
     * equilibrium immediately. Huge buy saturates symmetrically; an admin reset zeroes it.
     */
    private TestResult test_update_saturatesFlow_buybackMovesPrice() {
        ItemID id = new ItemID((short) 1);
        NewsPluginTestSuite.TestMarket market = new NewsPluginTestSuite.TestMarket(id, DEFAULT_PRICE);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        plugin.replaceCustomSettings(id, defaults()); // deadband 0: tests the T-174 saturation itself
        FlowBand band = VolatilityPlugin.flowBand(market.getNaturalAbundance(), defaults());
        List<MarketInterface> markets = List.of(new MarketInterface(market, null));
        double halfLife = ServerMarket.netFlowHalfLifeSeconds(); // the plugin assumes the market fades by one tick

        market.netFlow = 1e9; // huge net sell
        plugin.update(markets);
        if (market.netFlow != 1e9)
            return fail("Market counter must stay exact (1e9), got " + market.netFlow);
        if (plugin.getSaturatedFlow(id) != band.upper())
            return fail("Saturated flow must sit at " + band.upper() + ", got " + plugin.getSaturatedFlow(id));

        double atFloor = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, plugin.getSaturatedFlow(id), market.getNaturalAbundance(), defaults());
        // One market tick: fade, then buy back one item
        double expected = ServerMarket.fade(market.netFlow, halfLife, ServerMarket.TICK_SECONDS) - 1;
        market.netFlow = expected;
        plugin.update(markets);
        if (market.netFlow != expected)
            return fail("Market counter must stay exact (" + expected + "), got " + market.netFlow);
        double after = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, plugin.getSaturatedFlow(id), market.getNaturalAbundance(), defaults());
        if (!(after > atFloor))
            return fail("1-item buy-back must raise the equilibrium immediately: " + atFloor + " -> " + after);

        market.netFlow = -1e9; // huge net buy
        plugin.update(markets);
        if (market.netFlow != -1e9)
            return fail("Market counter must stay exact (-1e9), got " + market.netFlow);
        if (plugin.getSaturatedFlow(id) != band.lower())
            return fail("Saturated flow must sit at " + band.lower() + ", got " + plugin.getSaturatedFlow(id));

        market.netFlow = 0; // admin Reset
        plugin.update(markets);
        if (plugin.getSaturatedFlow(id) != 0)
            return fail("Admin reset must zero the saturated flow, got " + plugin.getSaturatedFlow(id));
        return pass("Market counter stays exact; saturated flow clamps at the band; buy-back moves the price immediately");
    }
    // ── T-204 deadband ──────────────────────────────────────────────────────

    /** Flow within +-deadband (edges included) leaves the equilibrium exactly at the default price. */
    private TestResult test_deadband_insideBand_defaultPrice() {
        Settings s = defaults().withDeadbandVolume(DEADBAND);
        for (double f : new double[]{1, -1, DEADBAND / 2, -DEADBAND / 2, DEADBAND, -DEADBAND}) {
            double eq = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, f, ABUNDANCE, s);
            if (eq != DEFAULT_PRICE) return fail("Flow " + f + " inside the deadband moved the price to " + eq);
        }
        return pass("Flow within +-" + DEADBAND + " items keeps the default price");
    }

    /** No jump at the edge: D - eps and D + eps give (almost) the same price, on both sides. */
    private TestResult test_deadband_edgeContinuity() {
        Settings s = defaults().withDeadbandVolume(DEADBAND);
        double capacity = VolatilityPlugin.capacity(ABUNDANCE, s);
        for (int sign : new int[]{1, -1}) {
            double below = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, sign * (DEADBAND - 1e-3), ABUNDANCE, s);
            double above = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, sign * (DEADBAND + 1e-3), ABUNDANCE, s);
            // 1e-3 items past the edge moves the price by ~DEFAULT_PRICE * 1e-3 / capacity
            double bound = 2 * DEFAULT_PRICE * 1e-3 / capacity;
            if (below != DEFAULT_PRICE || Math.abs(above - below) > bound)
                return fail("Jump at the edge (sign " + sign + "): " + below + " -> " + above);
        }
        return pass("Continuous at +-deadband");
    }

    /** Beyond the band the curve is the deadband-0 curve shifted by D: eq(D + x, D) == eq(x, 0). */
    private TestResult test_deadband_shiftsCurve() {
        Settings s0 = defaults();
        Settings sd = defaults().withDeadbandVolume(DEADBAND);
        for (double x : new double[]{1, 100, 1000, 20000, 1e6}) {
            for (int sign : new int[]{1, -1}) {
                double a = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, sign * (DEADBAND + x), ABUNDANCE, sd);
                double b = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, sign * x, ABUNDANCE, s0);
                if (!relClose(a, b)) return fail("eq(" + sign * (DEADBAND + x) + ", D) = " + a + " != eq(" + sign * x + ", 0) = " + b);
            }
        }
        return pass("eq(D + x, D) == eq(x, 0) on both sides");
    }

    /** Sell and buy of the same size beyond the band move the price symmetrically in log space. */
    private TestResult test_deadband_buySellSymmetry() {
        Settings s = new Settings(0.3f, true, 500f, DEADBAND, 0f, 0f, 0.001f, 1000f); // wide clamps
        for (double x : new double[]{10, 1000, 5000}) {
            double down = Math.log(VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, DEADBAND + x, ABUNDANCE, s) / DEFAULT_PRICE);
            double up = Math.log(VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, -(DEADBAND + x), ABUNDANCE, s) / DEFAULT_PRICE);
            if (!(down < 0) || Math.abs(down + up) > 1e-12) return fail("Asymmetric at x=" + x + ": ln " + down + " / " + up);
        }
        return pass("Buy and sell are symmetric in log space");
    }

    /** The saturation band widens by +-D, and its edges still map exactly to the price floor/cap. */
    private TestResult test_deadband_capStillReachable() {
        Settings s0 = new Settings(0.3f, true, 500f, 0.05f, 20f);
        Settings sd = s0.withDeadbandVolume(DEADBAND);
        FlowBand b0 = VolatilityPlugin.flowBand(ABUNDANCE, s0);
        FlowBand bd = VolatilityPlugin.flowBand(ABUNDANCE, sd);
        if (b0 == null || bd == null) return fail("Bands must exist");
        if (!relClose(bd.upper(), b0.upper() + DEADBAND) || !relClose(bd.lower(), b0.lower() - DEADBAND) || bd.deadband() != DEADBAND)
            return fail("Band must widen by +-D: " + b0 + " -> " + bd);
        double floor = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, bd.upper(), ABUNDANCE, sd);
        double ceil = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, bd.lower(), ABUNDANCE, sd);
        if (!relClose(floor, DEFAULT_PRICE * sd.minPriceMult()) || !relClose(ceil, DEFAULT_PRICE * sd.maxPriceMult()))
            return fail("Widened band edges must reach floor/cap, got " + floor + " / " + ceil);
        // With the old (unwidened) bound the cap would be out of reach.
        double atOld = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, b0.lower(), ABUNDANCE, sd);
        if (!(atOld < ceil)) return fail("Sanity: the old bound must stay below the cap with a deadband");
        return pass("Band widens by D; floor " + floor + ", cap " + ceil);
    }

    /** A pre-T-204 5-field payload decodes with the auto deadband (-1) and no smoothing. */
    private TestResult test_deadband_codecLegacy_getsAuto() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeFloat(0.3f);
            buf.writeBoolean(true);
            buf.writeFloat(2000f);
            buf.writeFloat(0.05f);
            buf.writeFloat(3f);
            Settings d = Settings.CODEC.decode(buf);
            return assertEquals("5-field payload", new Settings(0.3f, true, 2000f,
                    VolatilityPlugin.DEFAULT_DEADBAND_VOLUME, 0f, 0f, 0.05f, 3f), d);
        } finally {
            buf.release();
        }
    }

    /** Guard: with k == 0 the auto value is never resolved (same instance, -1 kept) and counts as 0. */
    private TestResult test_deadband_autoStaysWhileKZero() {
        Settings def = Settings.createDefault();
        if (def.deadbandVolume() != -1f) return fail("Default deadband must be the auto sentinel -1, got " + def.deadbandVolume());
        Settings r = VolatilityPlugin.resolveAutoDeadband(def, 50f, 0f);
        if (r != def) return fail("k = 0 must not resolve the auto deadband, got " + r);
        if (VolatilityPlugin.autoDeadband(VolatilityPlugin.capacity(50f, def), 0f) != 0f)
            return fail("Auto deadband at k = 0 must count as 0");
        // explicit values are never touched
        Settings explicit = def.withDeadbandVolume(123f);
        if (VolatilityPlugin.resolveAutoDeadband(explicit, 50f, 10f) != explicit)
            return fail("An explicit deadband must not be re-resolved");
        return pass("-1 stays stored while k == 0; explicit values untouched");
    }

    /**
     * With k > 0, -1 resolves to round(k x capacity), and the flowBand auto deadband for the same k is
     * exactly the value that would be stored (k passed explicitly; production k is
     * DEFAULT_DEADBAND_CAPACITY_MULT, see {@link #test_deadband_kZero_endToEnd}).
     */
    private TestResult test_deadband_autoResolvesAndPersists() {
        Settings def = Settings.createDefault();
        double cap = VolatilityPlugin.capacity(50f, def);
        Settings r = VolatilityPlugin.resolveAutoDeadband(def, 50f, 10f);
        if (r.deadbandVolume() != (float) Math.round(10 * cap))
            return fail("Expected round(10 x " + cap + "), got " + r.deadbandVolume());
        FlowBand auto = VolatilityPlugin.flowBand(50f, def, 10f);
        if (auto == null || auto.deadband() != r.deadbandVolume())
            return fail("flowBand auto deadband " + (auto == null ? null : auto.deadband()) + " != resolved " + r.deadbandVolume());
        FlowBand stored = VolatilityPlugin.flowBand(50f, r, 10f);
        if (!relClose(stored.upper(), auto.upper()) || !relClose(stored.lower(), auto.lower()))
            return fail("Resolved and auto settings must give the same band: " + stored + " vs " + auto);
        return pass("Resolved to " + r.deadbandVolume() + " (k 10, abundance 50); runtime auto value matches");
    }

    /**
     * End to end at the production k = 0 (DEFAULT_DEADBAND_CAPACITY_MULT): update() leaves the -1
     * stored (no write-back), the runtime deadband is 0, and the equilibrium equals the deadband-0 result.
     */
    private TestResult test_deadband_kZero_endToEnd() {
        if (VolatilityPlugin.DEFAULT_DEADBAND_CAPACITY_MULT != 0f)
            return skip("production k is " + VolatilityPlugin.DEFAULT_DEADBAND_CAPACITY_MULT + ", not 0");
        ItemID id = new ItemID((short) 1);
        NewsPluginTestSuite.TestMarket market = new NewsPluginTestSuite.TestMarket(id, DEFAULT_PRICE);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        Settings d = Settings.createDefault(); // deadband -1 (auto), smoothing 0/0
        Settings s = new Settings(0f, d.flowInfluenceEnabled(), d.flowSensitivity(), d.deadbandVolume(),
                d.flowRiseTimeSeconds(), d.flowReturnTimeSeconds(), d.minPriceMult(), d.maxPriceMult()); // volatility 0: target == equilibrium
        plugin.replaceCustomSettings(id, s);
        List<MarketInterface> markets = List.of(new MarketInterface(market, null));
        Settings noDeadband = s.withDeadbandVolume(0f);
        for (double flow : new double[]{1000, -5000, 20000}) {
            market.netFlow = flow;
            plugin.update(markets);
            if (plugin.getCustomSettings(id) != s)
                return fail("update() must not write back a resolved deadband at k = 0, got " + plugin.getCustomSettings(id));
            double expected = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, market.getNaturalAbundance(), noDeadband);
            if (market.targetPrice != expected || expected == DEFAULT_PRICE)
                return fail("flow " + flow + ": target " + market.targetPrice + ", deadband-0 equilibrium " + expected);
        }
        FlowBand band = VolatilityPlugin.flowBand(market.getNaturalAbundance(), s);
        if (band.deadband() != 0) return fail("Runtime deadband must be 0, got " + band.deadband());
        if (plugin.getCustomSettings(id).deadbandVolume() != -1f) return fail("-1 must stay stored");
        return pass("k = 0: -1 stays stored, runtime deadband 0, equilibrium == deadband-0 result");
    }

    // ── T-204 asymmetric flow smoothing ─────────────────────────────────────

    /** Runs {@code ticks} smoothing steps with a constant input. */
    private static double run(double eff, double in, int ticks, float rise, float ret) {
        for (int i = 0; i < ticks; i++) eff = VolatilityPlugin.smoothFlow(eff, in, ServerMarket.TICK_SECONDS, rise, ret);
        return eff;
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds / ServerMarket.TICK_SECONDS);
    }

    /** Rise 60 s / return 10 s: after one rise time ~63% is reached; going back is 6x faster. */
    private TestResult test_smoothing_riseSlowerThanReturn() {
        double up = run(0, 1000, ticks(60), 60f, 10f);
        if (Math.abs(up - 1000 * (1 - Math.exp(-1))) > 1) return fail("After 60 s expected ~632, got " + up);
        double up10 = run(0, 1000, ticks(10), 60f, 10f);
        double down = run(up, 0, ticks(10), 60f, 10f);
        if (Math.abs(down - up * Math.exp(-1)) > 1) return fail("After 10 s back expected ~" + up * Math.exp(-1) + ", got " + down);
        // in 10 s the rise covers ~15% of the way, the return ~63%
        if (!(up10 / 1000 < (up - down) / up)) return fail("Rise must be slower than return: " + up10 + " vs " + (up - down));
        return pass("rise 60 s: " + up + " after 60 s, " + up10 + " after 10 s; return 10 s: " + up + " -> " + down);
    }

    /** Buy and sell side are mirror images (bit-exact). */
    private TestResult test_smoothing_buySellSymmetric() {
        double[] in = {1000, 1000, 400, 0, -300, 2000, 0};
        double a = 0, b = 0;
        for (double x : in) {
            a = run(a, x, 200, 60f, 10f);
            b = run(b, -x, 200, 60f, 10f);
            if (a != -b) return fail("Not symmetric at input " + x + ": " + a + " vs " + b);
        }
        return pass("Sell and buy smoothing mirror each other");
    }

    /** rise == return == 0: eff == in exactly, and update() gives exactly the pre-T-204 equilibrium. */
    private TestResult test_smoothing_zeroTimes_exact() {
        double eff = 0;
        for (double x : new double[]{1234.5678, -98765.4321, 0.1, 0, -0.0, 1e9}) {
            eff = VolatilityPlugin.smoothFlow(eff, x, ServerMarket.TICK_SECONDS, 0f, 0f);
            if (Double.compare(eff, x) != 0) return fail("0/0 must give in exactly: " + x + " -> " + eff);
        }
        ItemID id = new ItemID((short) 1);
        NewsPluginTestSuite.TestMarket market = new NewsPluginTestSuite.TestMarket(id, DEFAULT_PRICE);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        Settings s = new Settings(0f, true, 2000f, 0.05f, 3f); // no random walk, deadband 0, 0/0
        plugin.replaceCustomSettings(id, s);
        List<MarketInterface> markets = List.of(new MarketInterface(market, null));
        for (double flow : new double[]{12345, -4321, 777, 0}) {
            market.netFlow = flow;
            plugin.update(markets);
            double expected = VolatilityPlugin.computeEquilibriumPrice(DEFAULT_PRICE, flow, market.getNaturalAbundance(), s);
            if (market.targetPrice != expected) return fail("flow " + flow + ": target " + market.targetPrice + " != " + expected);
        }
        return pass("0/0 reproduces the pre-T-204 equilibrium bit for bit");
    }

    /** Sign flip: the return step that crosses 0 clamps at 0, then the new side rises with the rise time. */
    private TestResult test_smoothing_signFlip_clampsAtZero() {
        double dt = ServerMarket.TICK_SECONDS;
        // instant return: the crossing tick lands exactly on 0, the next tick rises slowly
        double e1 = VolatilityPlugin.smoothFlow(500, -1000, dt, 60f, 0f);
        if (e1 != 0) return fail("Crossing step must clamp to 0, got " + e1);
        double e2 = VolatilityPlugin.smoothFlow(e1, -1000, dt, 60f, 0f);
        double exp2 = -1000 * (1 - Math.exp(-dt / 60));
        if (Math.abs(e2 - exp2) > 1e-9) return fail("After the clamp the new side must rise slowly: " + exp2 + " vs " + e2);
        // slow return: never negative before hitting exactly 0
        double eff = 500;
        boolean hitZero = false;
        for (int i = 0; i < ticks(120) && !hitZero; i++) {
            eff = VolatilityPlugin.smoothFlow(eff, -1000, dt, 60f, 10f);
            if (eff < 0) return fail("Went negative without the clamp at tick " + i + ": " + eff);
            hitZero = eff == 0;
        }
        if (!hitZero) return fail("Never reached 0, eff " + eff);
        // rise 0: the crossing tick gives the input directly
        double e3 = VolatilityPlugin.smoothFlow(500, -1000, dt, 0f, 10f);
        if (e3 != 500 + (-1500) * (1 - Math.exp(-dt / 10))) {
            // not crossing yet with return 10 s; force a crossing with instant return
            return fail("Unexpected non-crossing step " + e3);
        }
        double e4 = VolatilityPlugin.smoothFlow(500, -1000, dt, 0f, 0f);
        if (e4 != -1000) return fail("rise 0: crossing must give the input, got " + e4);
        return pass("Clamp at 0, then slow rise " + e2 + " on the new side");
    }

    /** effFlow and the new settings survive save/load; a pre-T-204 tag loads as -1 / 0 / 0, effFlow 0. */
    private TestResult test_smoothing_nbtRoundTrip_andLegacy() {
        ItemID id = ItemID.getFromItemStack(new ItemStack(Items.DIAMOND));
        if (id == null || !id.isValid()) return skip("diamond has no registered ItemID");
        NewsPluginTestSuite.TestMarket market = new NewsPluginTestSuite.TestMarket(id, DEFAULT_PRICE);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        Settings s = new Settings(0f, true, 2000f, 777f, 60f, 10f, 0.05f, 3f);
        plugin.replaceCustomSettings(id, s);
        market.netFlow = 50000;
        List<MarketInterface> markets = List.of(new MarketInterface(market, null));
        for (int i = 0; i < 100; i++) plugin.update(markets);
        double eff = plugin.getEffectiveFlow(id);
        if (!(eff > 0 && eff < 50000)) return fail("effFlow must be rising, got " + eff);

        CompoundTag tag = new CompoundTag();
        plugin.save(tag);
        VolatilityPlugin loaded = new VolatilityPlugin();
        loaded.onMarketSubscribed(id);
        loaded.load(tag);
        if (loaded.getEffectiveFlow(id) != eff) return fail("effFlow " + eff + " loaded as " + loaded.getEffectiveFlow(id));
        // the loaded settings drive the equilibrium: same deadband and times -> same next step
        plugin.update(markets);
        loaded.update(markets);
        if (loaded.getEffectiveFlow(id) != plugin.getEffectiveFlow(id))
            return fail("Loaded settings differ: next step " + loaded.getEffectiveFlow(id) + " vs " + plugin.getEffectiveFlow(id));

        // legacy: strip the T-204 keys
        ListTag list = (ListTag) tag.get("marketData");
        CompoundTag m = list.getCompound(0);
        for (String k : new String[]{"deadbandVolume", "flowRiseTimeSeconds", "flowReturnTimeSeconds", "effFlow", "satFlow", "lastFaded"}) m.remove(k); // T-207 keys too
        VolatilityPlugin legacy = new VolatilityPlugin();
        legacy.onMarketSubscribed(id);
        legacy.load(tag);
        if (legacy.getEffectiveFlow(id) != 0) return fail("Legacy effFlow must be 0");
        // probe the legacy settings through the pure helpers via a 1-tick update with flow 0 -> 0/0 means eff = in
        market.netFlow = 1234;
        legacy.update(markets);
        if (legacy.getEffectiveFlow(id) != 1234) return fail("Legacy times must be 0/0 (eff = in), got " + legacy.getEffectiveFlow(id));
        return pass("effFlow " + eff + " and settings round-trip; legacy tag loads with 0/0 and effFlow 0");
    }
}
