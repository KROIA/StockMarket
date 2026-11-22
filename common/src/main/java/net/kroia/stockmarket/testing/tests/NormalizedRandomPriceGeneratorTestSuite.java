package net.kroia.stockmarket.testing.tests;

import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;

/** SM-337: tests of the time-stepped multi-component noise {@link NormalizedRandomPriceGenerator}. */
public class NormalizedRandomPriceGeneratorTestSuite extends TestSuite {

    private static final double EPSILON = 0.0001;
    private static final long HOUR_MS = 3_600_000L;

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
        addTest("fresh_generator_starts_at_zero", this::test_freshAtZero);
        addTest("seeded_is_deterministic", this::test_seededDeterministic);
        addTest("slow_trends_low_hourly_noise", this::test_slowTrends);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("load_pre_sm337_save_starts_fresh", this::test_loadOldFormat);
    }

    private TestResult test_freshAtZero() {
        return assertEquals("fresh value", 0.0, new NormalizedRandomPriceGenerator().getCurrentValue());
    }

    /** Same seed source, same steps -> same values. */
    private TestResult test_seededDeterministic() {
        NormalizedRandomPriceGenerator a = new NormalizedRandomPriceGenerator(new Random(7));
        NormalizedRandomPriceGenerator b = new NormalizedRandomPriceGenerator(new Random(7));
        for (int i = 0; i < 1000; i++) {
            long dt = 100 + (i * 37L) % 1001;
            if (a.step(dt) != b.step(dt)) return fail("diverged at step " + i);
        }
        return pass("seeded generators match");
    }

    /**
     * 30 simulated days at 600 ms steps: the level spread is large (slow components, theory ~0.47 noise)
     * and the hourly change small (theory ~0.11 noise), i.e. trends rather than jitter.
     */
    private TestResult test_slowTrends() {
        NormalizedRandomPriceGenerator gen = new NormalizedRandomPriceGenerator(new Random(1234));
        int hours = 30 * 24;
        long stepsPerHour = HOUR_MS / 600;
        double[] level = new double[hours + 1];
        for (int h = 1; h <= hours; h++) {
            for (long s = 0; s < stepsPerHour; s++) gen.step(600);
            level[h] = gen.getCurrentValue();
        }
        double sum = 0, sumSq = 0, chSq = 0;
        for (int h = 1; h <= hours; h++) {
            sum += level[h];
            sumSq += level[h] * level[h];
            double ch = level[h] - level[h - 1];
            chSq += ch * ch;
        }
        double mean = sum / hours;
        double levelStd = Math.sqrt(sumSq / hours - mean * mean);
        double hourlyStd = Math.sqrt(chSq / hours);
        String msg = String.format(java.util.Locale.ROOT, "level std %.3f, hourly change std %.3f", levelStd, hourlyStd);
        TestResult r = assertTrue("level std in [0.2, 0.8]: " + msg, levelStd > 0.2 && levelStd < 0.8);
        if (!r.passed()) return r;
        r = assertTrue("hourly change std in [0.06, 0.2]: " + msg, hourlyStd > 0.06 && hourlyStd < 0.2);
        if (!r.passed()) return r;
        return assertTrue("hourly change well below the level spread: " + msg, hourlyStd < 0.5 * levelStd);
    }

    /** Full state preserved through save/load. */
    private TestResult test_save_load_roundTrip() {
        NormalizedRandomPriceGenerator original = new NormalizedRandomPriceGenerator();
        for (int i = 0; i < 100; i++) original.step(60_000);
        double savedValue = original.getCurrentValue();

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        NormalizedRandomPriceGenerator loaded = new NormalizedRandomPriceGenerator();
        TestResult r = assertTrue("load() should return true", loaded.load(tag));
        if (!r.passed()) return r;
        return assertTrue("currentValue should match after load, expected: " + savedValue + " got: " + loaded.getCurrentValue(),
                Math.abs(loaded.getCurrentValue() - savedValue) < EPSILON);
    }

    /** A pre-SM-337 save (order, walk_i, coefficient_i, counter) loads without error and starts at 0. */
    private TestResult test_loadOldFormat() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("order", 5);
        for (int i = 0; i < 5; i++) {
            CompoundTag walk = new CompoundTag();
            walk.putDouble("currentValue", 0.7);
            walk.putDouble("stepSize", 0.05);
            walk.putDouble("meanReversionStrength", 0.05);
            tag.put("walk_" + i, walk);
            tag.putDouble("coefficient_" + i, (i + 1) / 3.0);
        }
        tag.putLong("counter", 12345);
        tag.putDouble("currentValue", 1.5);

        NormalizedRandomPriceGenerator gen = new NormalizedRandomPriceGenerator();
        TestResult r = assertTrue("old save loads", gen.load(tag));
        if (!r.passed()) return r;
        r = assertEquals("old save starts fresh", 0.0, gen.getCurrentValue());
        if (!r.passed()) return r;
        gen.step(1000);
        return assertTrue("steps after loading an old save", Double.isFinite(gen.getCurrentValue()));
    }
}
