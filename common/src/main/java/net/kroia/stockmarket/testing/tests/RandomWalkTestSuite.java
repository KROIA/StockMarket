package net.kroia.stockmarket.testing.tests;

import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;

/** SM-337: tests of the time-stepped Ornstein-Uhlenbeck walk {@link MeanRevertingRandomWalk}. */
public class RandomWalkTestSuite extends TestSuite {

    private static final double EPSILON = 0.0001;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.RANDOM;
    }

    @Override
    public boolean needsGame() {
        return false; // passes offline in gradlew :common:test
    }

    @Override
    public void registerTests() {
        addTest("stationary_mean_and_std", this::test_stationary);
        addTest("decay_independent_of_step_rate", this::test_decayStepRate);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("load_missing_fields_uses_defaults", this::test_load_missingFields_usesDefaults);
        addTest("zero_sigma_stays_zero", this::test_zeroSigma);
        addTest("zero_dt_no_change", this::test_zeroDt);
    }

    /** Over many correlation times the walk has mean ~0 and standard deviation ~sigma. */
    private TestResult test_stationary() {
        double sigma = 0.3;
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(sigma, 1000, new Random(42));
        double sum = 0, sumSq = 0;
        int n = 200_000; // 100 ms steps, 20000 correlation times
        for (int i = 0; i < n; i++) {
            double v = walk.step(100);
            sum += v;
            sumSq += v * v;
        }
        double mean = sum / n;
        double std = Math.sqrt(sumSq / n - mean * mean);
        TestResult r = assertTrue("mean near 0, was " + mean, Math.abs(mean) < 0.05);
        if (!r.passed()) return r;
        return assertTrue("std near sigma " + sigma + ", was " + std, Math.abs(std - sigma) < 0.03);
    }

    /** Without noise, ten 100 ms steps decay a value exactly like one 1000 ms step: e^(-1000/tau). */
    private TestResult test_decayStepRate() {
        CompoundTag one = new CompoundTag();
        one.putDouble("currentValue", 1.0);
        MeanRevertingRandomWalk fine = new MeanRevertingRandomWalk(0, 2000);
        MeanRevertingRandomWalk coarse = new MeanRevertingRandomWalk(0, 2000);
        fine.load(one);
        coarse.load(one);
        for (int i = 0; i < 10; i++) fine.step(100);
        coarse.step(1000);
        double expected = Math.exp(-0.5);
        TestResult r = assertTrue("fine " + fine.getCurrentValue() + " vs " + expected,
                Math.abs(fine.getCurrentValue() - expected) < 1e-9);
        if (!r.passed()) return r;
        return assertTrue("coarse " + coarse.getCurrentValue() + " vs " + expected,
                Math.abs(coarse.getCurrentValue() - expected) < 1e-9);
    }

    private TestResult test_save_load_roundTrip() {
        MeanRevertingRandomWalk original = new MeanRevertingRandomWalk(0.3, 1000);
        for (int i = 0; i < 50; i++) original.step(100);
        double savedValue = original.getCurrentValue();

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        MeanRevertingRandomWalk loaded = new MeanRevertingRandomWalk(0, 0);
        loaded.load(tag);

        if (Math.abs(loaded.getCurrentValue() - savedValue) > EPSILON) {
            return fail("currentValue mismatch: expected " + savedValue + " got " + loaded.getCurrentValue());
        }
        return pass("Save/load round-trip preserves random walk state");
    }

    private TestResult test_load_missingFields_usesDefaults() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.5, 1000);
        for (int i = 0; i < 10; i++) walk.step(100);
        TestResult r = assertTrue("load() should return true even with missing fields", walk.load(new CompoundTag()));
        if (!r.passed()) return r;
        if (Math.abs(walk.getCurrentValue()) > EPSILON) {
            return fail("Missing currentValue should default to 0.0 but was " + walk.getCurrentValue());
        }
        return pass("load() with missing fields uses default values");
    }

    /** sigma 0 from 0 never moves. */
    private TestResult test_zeroSigma() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.0, 1000);
        for (int i = 0; i < 100; i++) walk.step(500);
        return assertTrue("sigma 0 stays 0, was " + walk.getCurrentValue(), Math.abs(walk.getCurrentValue()) < EPSILON);
    }

    /** dt <= 0 leaves the value unchanged. */
    private TestResult test_zeroDt() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(1.0, 1000, new Random(1));
        walk.step(500);
        double v = walk.getCurrentValue();
        walk.step(0);
        walk.step(-5);
        return assertEquals("dt <= 0 is a no-op", v, walk.getCurrentValue());
    }
}
