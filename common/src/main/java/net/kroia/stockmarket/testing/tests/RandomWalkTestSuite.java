package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

public class RandomWalkTestSuite extends TestSuite {

    private static final double EPSILON = 0.0001;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.RANDOM;
    }

    @Override
    public void registerTests() {
        addTest("stays_near_zero", this::test_staysNearZero);
        addTest("mean_reversion", this::test_meanReversion);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("bounded_by_step_size", this::test_nextValue_boundedByStepSize);
        addTest("load_missing_fields_uses_defaults", this::test_load_missingFields_usesDefaults);
        addTest("zero_step_size", this::test_zeroStepSize);
        addTest("zero_mean_reversion", this::test_zeroMeanReversion);
    }

    private TestResult test_staysNearZero() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.1, 0.5);
        double sum = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            sum += walk.nextValue();
        }
        double mean = sum / iterations;
        if (Math.abs(mean) > 1.0) {
            return fail("Mean over " + iterations + " iterations should be near zero but was " + mean);
        }
        return pass("Mean over many iterations stays near zero");
    }

    private TestResult test_meanReversion() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.01, 0.5);
        // Manually set a large current value by iterating with a bias is not straightforward,
        // so we test that after many steps from zero, the value does not drift far
        // With stepSize=0.01 and meanReversionStrength=0.5, the walk is strongly mean-reverting
        for (int i = 0; i < 1000; i++) {
            walk.nextValue();
        }
        double finalValue = walk.getCurrentValue();
        if (Math.abs(finalValue) > 1.0) {
            return fail("With strong mean reversion, value should stay near zero but was " + finalValue);
        }
        return pass("Mean reversion keeps value bounded near zero");
    }

    private TestResult test_save_load_roundTrip() {
        MeanRevertingRandomWalk original = new MeanRevertingRandomWalk(0.3, 0.1);
        for (int i = 0; i < 50; i++) {
            original.nextValue();
        }
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

    private TestResult test_nextValue_boundedByStepSize() {
        double stepSize = 0.5;
        double meanReversion = 0.0; // no reversion, so first step = pure random step
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(stepSize, meanReversion);
        // First step from currentValue=0: step is in [-stepSize, +stepSize], reversion=0
        // So nextValue = 0 + step + 0, meaning |nextValue| <= stepSize
        double val = walk.nextValue();
        if (Math.abs(val) > stepSize + EPSILON) {
            return fail("First step should be bounded by stepSize " + stepSize + " but was " + val);
        }
        return pass("Single step from zero is bounded by stepSize");
    }

    private TestResult test_load_missingFields_usesDefaults() {
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.5, 0.3);
        // Run a few iterations to change state
        for (int i = 0; i < 10; i++) walk.nextValue();

        CompoundTag tag = new CompoundTag();
        // Empty tag - all fields missing
        boolean loaded = walk.load(tag);
        // load() always returns true but uses defaults for missing fields
        TestResult r = assertTrue("load() should return true even with missing fields", loaded);
        if (!r.passed()) return r;
        if (Math.abs(walk.getCurrentValue()) > EPSILON) {
            return fail("Missing currentValue should default to 0.0 but was " + walk.getCurrentValue());
        }
        return pass("load() with missing fields uses default values");
    }

    private TestResult test_zeroStepSize() {
        // With stepSize=0, the random step is always 0. Only reversion drives the walk.
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(0.0, 0.5);
        // From currentValue=0: step=0, reversion=-0.5*0=0, so nextValue=0
        double val = walk.nextValue();
        if (Math.abs(val) > EPSILON) {
            return fail("With stepSize=0 starting from 0, value should stay 0 but was " + val);
        }
        return pass("Zero stepSize means only reversion drives the walk");
    }

    private TestResult test_zeroMeanReversion() {
        // With meanReversionStrength=0, there is no pull back toward zero (pure random walk)
        MeanRevertingRandomWalk walk = new MeanRevertingRandomWalk(1.0, 0.0);
        // Run many iterations and track that it can drift away from zero
        // With no reversion, after enough steps the walk should have non-trivial variance
        double sumSq = 0;
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            double val = walk.nextValue();
            sumSq += val * val;
        }
        double rmsValue = Math.sqrt(sumSq / iterations);
        // A pure random walk with stepSize=1 should have non-trivial RMS after 1000 steps
        if (rmsValue < 0.1) {
            return fail("Pure random walk (no reversion) should have non-trivial RMS but was " + rmsValue);
        }
        return pass("Zero mean reversion allows free random walk without pull back");
    }
}
