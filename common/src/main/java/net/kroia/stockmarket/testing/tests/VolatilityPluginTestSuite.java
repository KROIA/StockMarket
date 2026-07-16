package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin.Settings;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

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

    /** Settings with defaults for everything (flow influence enabled). */
    private static Settings defaults() {
        return Settings.createDefault();
    }

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
     * The new 5-field codec must round-trip all values.
     */
    private TestResult test_codec_roundTrip() {
        Settings original = new Settings(0.7f, false, 123.5f, 0.1f, 42f);
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
                    VolatilityPlugin.DEFAULT_MIN_PRICE_MULT,
                    VolatilityPlugin.DEFAULT_MAX_PRICE_MULT);
            return assertEquals("New fields must fall back to defaults for legacy payloads",
                    expected, decoded);
        } finally {
            buf.release();
        }
    }
}
