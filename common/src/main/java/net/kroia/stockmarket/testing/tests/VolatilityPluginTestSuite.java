package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
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
import java.util.function.DoubleUnaryOperator;

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
        addTest("flow_band_bounds", this::test_flowBand_bounds);
        addTest("update_saturates_flow_and_buyback_moves_price", this::test_update_saturatesFlow_buybackMovesPrice);
        // T-208 permanent impact θ
        addTest("theta_bound_diamond", () -> test_thetaBound(160.0, 50f, new double[]{100, 3_200, 32_000, 192_000}));
        addTest("theta_bound_dirt", () -> test_thetaBound(0.1, 2000f, new double[]{128_000, 1_280_000, 12_800_000}));
        addTest("theta_codec_round_trip_and_legacy", this::test_theta_codec);
        addTest("theta_nbt_round_trip_and_legacy", this::test_theta_nbt);
    }

    // ── T-208 permanent impact θ ────────────────────────────────────────────

    /** Real price of one backend price level in these tests (scale 100). */
    private static final double TICK = 0.01;

    /**
     * End price of a one-shot market order of {@code volume} items walking the default book level by
     * level (asks at or above P0 for a buy, bids below it for a sell): the level where the order ends.
     * Stops at 1000 x / 1/1000 x P0 (or one tick) if the book runs out first.
     */
    private static double walkEndPrice(DoubleUnaryOperator volumeAt, double p0, double volume, boolean buy) {
        long k = Math.round(p0 / TICK);
        long kMax = Math.round(p0 * 1000 / TICK);
        // T-214: buys start at the first level above p0, sells at the first below (the level at p0 itself is the
        // spread boundary). Starting a buy at p0 let flows smaller than a 10% dirt step "walk" 0.
        k += buy ? 1 : -1;
        double rem = volume;
        while (k >= 1 && k <= kMax) {
            double v = Math.abs(volumeAt.applyAsDouble(k * TICK));
            if (rem <= v) return k * TICK;
            rem -= v;
            k += buy ? 1 : -1;
        }
        return Math.max(1, Math.min(k, kMax)) * TICK;
    }

    /**
     * T-208: for the default book of a market (production DistributionCalculator; T-214: depth scale sized to the
     * default max order of the abundance), the equilibrium log move of a flow F is at most θ x the log move of F walked through
     * that book in one go, buy and sell, at the default settings.
     */
    private TestResult test_thetaBound(double p0, float abundance, double[] flows) {
        DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc =
                new DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator(p0,
                        DefaultOrderbookVolumeDistributionPlugin.depthScaleFor(p0, MarketSettings.defaultMaxOrderVolume(abundance,
                                MarketSettings.DEFAULT_MAX_ORDER_VOLUME_FACTOR), 1f));
        DoubleUnaryOperator volumeAt = pick -> calc.getVolume(p0, pick);
        Settings s = defaults();
        double eFold = VolatilityPlugin.bookEFoldDepth(volumeAt, p0, TICK, Math.log(s.minPriceMult()), Math.log(s.maxPriceMult()));
        if (!(eFold > 0)) return fail("bookEFold must be > 0, got " + eFold);
        StringBuilder out = new StringBuilder("bookEFold " + Math.round(eFold) + ", capacity "
                + Math.round(VolatilityPlugin.flowBand(abundance, s, eFold).capacity()) + ";");
        for (double f : flows) {
            for (boolean buy : new boolean[]{true, false}) {
                double walk = Math.abs(Math.log(walkEndPrice(volumeAt, p0, f, buy) / p0));
                double eq = VolatilityPlugin.computeEquilibriumPrice(p0, buy ? -f : f, abundance, s, eFold);
                double move = Math.abs(Math.log(eq / p0));
                out.append(String.format(java.util.Locale.ROOT, " %s %.0f: T %.5f walk %.5f;", buy ? "buy" : "sell", f, move, walk));
                if (!(move <= s.permanentImpact() * walk + 1e-12))
                    return fail("θ bound broken:" + out);
            }
        }
        return pass(out.toString());
    }

    /**
     * θ survives the codec; pre-T-204 (5-field) and unreleased T-204 (8-field: deadband -1 lands in θ)
     * payloads get 0.5 and decode the other fields unchanged; invalid θ (0, 2, NaN) becomes 0.5.
     */
    private TestResult test_theta_codec() {
        Settings original = new Settings(0.7f, true, 123.5f, 0.1f, 4f, 0.3f);
        ByteBuf buf = Unpooled.buffer();
        try {
            Settings.CODEC.encode(buf, original);
            TestResult r = assertEquals("θ round-trip", original, Settings.CODEC.decode(buf));
            if (!r.passed()) return r;
            buf.clear();
            buf.writeFloat(0.3f).writeBoolean(true).writeFloat(2000f).writeFloat(0.05f).writeFloat(3f)
                    .writeFloat(-1f).writeFloat(0f).writeFloat(0f); // T-204 payload, no θ
            r = assertEquals("8-field payload decodes with the default θ", new Settings(0.3f, true, 2000f, 0.05f, 3f),
                    Settings.CODEC.decode(buf));
            if (!r.passed()) return r;
            if (buf.isReadable()) return fail("8-field payload: trailing floats must be skipped");
            buf.clear();
            buf.writeFloat(0.3f).writeBoolean(true).writeFloat(2000f).writeFloat(0.05f).writeFloat(3f); // pre-T-204
            r = assertEquals("5-field payload decodes with the default θ", new Settings(0.3f, true, 2000f, 0.05f, 3f),
                    Settings.CODEC.decode(buf));
            if (!r.passed()) return r;
            for (float bad : new float[]{0f, -0.5f, 2f, Float.NaN}) {
                r = assertEquals("invalid θ " + bad + " becomes the default", VolatilityPlugin.DEFAULT_PERMANENT_IMPACT,
                        defaults().withPermanentImpact(bad).permanentImpact());
                if (!r.passed()) return r;
            }
            return assertEquals("θ = 1 is valid", 1f, defaults().withPermanentImpact(1f).permanentImpact());
        } finally {
            buf.release();
        }
    }

    /** θ survives plugin save/load; a save without the {@code permanentImpact} key loads with 0.5. */
    private TestResult test_theta_nbt() {
        ItemID id = ItemID.getFromItemStack(new ItemStack(Items.DIAMOND));
        if (id == null || !id.isValid()) return skip("diamond has no registered ItemID");
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        plugin.replaceCustomSettings(id, defaults().withPermanentImpact(0.25f));
        CompoundTag tag = new CompoundTag();
        plugin.save(tag);
        VolatilityPlugin loaded = new VolatilityPlugin();
        loaded.onMarketSubscribed(id);
        loaded.load(tag);
        TestResult r = assertEquals("θ after load", 0.25f, loaded.getMarketSettings(id).permanentImpact());
        if (!r.passed()) return r;
        ((ListTag) tag.get("marketData")).getCompound(0).remove("permanentImpact");
        VolatilityPlugin legacy = new VolatilityPlugin();
        legacy.onMarketSubscribed(id);
        legacy.load(tag);
        return assertEquals("save without θ loads the default", VolatilityPlugin.DEFAULT_PERMANENT_IMPACT,
                legacy.getMarketSettings(id).permanentImpact());
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
     * The codec (6 fields since T-208) must round-trip all values.
     */
    private TestResult test_codec_roundTrip() {
        Settings original = new Settings(0.7f, false, 123.5f, 0.1f, 42f, 0.75f);
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
        plugin.replaceCustomSettings(id, defaults());
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
}
