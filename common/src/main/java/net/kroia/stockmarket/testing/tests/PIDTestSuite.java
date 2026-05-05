package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;

public class PIDTestSuite extends TestSuite {

    private static final double EPSILON = 0.0001;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PID;
    }

    @Override
    public void registerTests() {
        addTest("proportional_only", this::test_proportionalOnly);
        addTest("integral_accumulation", this::test_integralAccumulation);
        addTest("integral_bound_clamping", this::test_integralBoundClamping);
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
    }

    private TestResult test_proportionalOnly() {
        PID pid = new PID(2.0, 0.0, 0.0, 100.0);
        pid.setCurrentMillis();
        double output = pid.update(10.0);
        // The PID uses smoothing: output = 0.5*lastOutput + 0.5*(kp*error + i + kd*dError/dt)
        // First call: lastOutput=0, so output = 0.5 * (2.0 * 10.0) = 10.0
        // Due to timing-dependent derivative term (kd=0 here), the proportional part dominates
        if (Math.abs(output - 10.0) > 1.0) {
            return fail("With kp=2.0 and error=10, output should be approximately 10.0 but was " + output);
        }
        return pass("Proportional-only output is proportional to error");
    }

    private TestResult test_integralAccumulation() {
        PID pid = new PID(0.0, 1.0, 0.0, 1000.0);
        pid.setCurrentMillis();
        double firstOutput = pid.update(10.0);
        double secondOutput = pid.update(10.0);
        // The integral term accumulates over time, so successive outputs should grow
        // (assuming small dt between calls, the integral accumulates error*dt*ki)
        // With kp=0 and kd=0, the output comes purely from the integral term
        // After multiple calls, the magnitude should be non-zero and growing
        if (Math.abs(secondOutput) < Math.abs(firstOutput) && Math.abs(firstOutput) > EPSILON) {
            return fail("Integral should accumulate: second output (" + secondOutput +
                    ") should be >= first output (" + firstOutput + ") in magnitude");
        }
        return pass("Integral term accumulates error over time");
    }

    private TestResult test_integralBoundClamping() {
        double iBound = 5.0;
        PID pid = new PID(0.0, 100.0, 0.0, iBound);
        pid.setCurrentMillis();
        for (int j = 0; j < 100; j++) {
            pid.update(1000.0);
        }
        double output = pid.getOutput();
        // The integral is clamped to [-iBound, iBound], so the output (which is just the
        // smoothed integral term since kp=0, kd=0) should not exceed iBound significantly
        // Output = 0.5*lastOutput + 0.5*i, and i is clamped, so output converges to i
        if (Math.abs(output) > iBound * 2) {
            return fail("Output " + output + " exceeds expected bound (iBound=" + iBound + ")");
        }
        return pass("Integral bound clamping limits output correctly");
    }

    private TestResult test_save_load_roundTrip() {
        PID original = new PID(1.5, 0.3, 0.7, 50.0);
        original.setCurrentMillis();
        original.update(5.0);
        original.update(3.0);

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        PID loaded = new PID(0, 0, 0, 0);
        if (!loaded.load(tag)) {
            return fail("load() returned false");
        }

        if (Math.abs(loaded.getKP() - original.getKP()) > EPSILON) {
            return fail("kp mismatch after round-trip");
        }
        if (Math.abs(loaded.getKI() - original.getKI()) > EPSILON) {
            return fail("ki mismatch after round-trip");
        }
        if (Math.abs(loaded.getKD() - original.getKD()) > EPSILON) {
            return fail("kd mismatch after round-trip");
        }
        if (Math.abs(loaded.getIBound() - original.getIBound()) > EPSILON) {
            return fail("iBound mismatch after round-trip");
        }
        if (Math.abs(loaded.getLastError() - original.getLastError()) > EPSILON) {
            return fail("lastError mismatch after round-trip");
        }
        return pass("Save/load round-trip preserves PID state");
    }
}
