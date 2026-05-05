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
}
