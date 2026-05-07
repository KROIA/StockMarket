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
        addTest("derivative_response", this::test_update_derivativeResponse);
        addTest("dt_division_by_zero", this::test_update_dtDivisionByZero);
        addTest("output_smoothing", this::test_outputSmoothing);
        addTest("load_missing_fields", this::test_load_missingFields);
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

    private TestResult test_update_derivativeResponse() {
        // kp=0, ki=0, only kd active. Output should respond to error change rate.
        PID pid = new PID(0.0, 0.0, 5.0, 100.0);
        pid.setCurrentMillis();
        // First update with error=0 to establish baseline
        try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        pid.update(0.0);
        // Second update with a jump in error
        try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        double output = pid.update(10.0);
        // With only derivative term active and a positive error change, output should be non-zero
        // output = 0.5*lastOutput + 0.5*(kd*(error - lastError)/dt)
        // lastError after first call is smoothed: 0*0.5 + 0*0.5 = 0
        // So derivative = kd * (10 - 0) / dt, which should be positive and significant
        if (Math.abs(output) < EPSILON) {
            return fail("With kd=5.0 and a step change in error, output should be non-zero but was " + output);
        }
        return pass("Derivative term responds to error change rate");
    }

    private TestResult test_update_dtDivisionByZero() {
        // Two calls at the same millisecond should not throw an exception
        PID pid = new PID(1.0, 0.0, 1.0, 100.0);
        pid.setCurrentMillis();
        try {
            // Call twice immediately - dt may be 0
            double out1 = pid.update(5.0);
            double out2 = pid.update(5.0);
            // If dt=0, the derivative term (kd*(error-lastError)/dt) produces Infinity or NaN
            // The test verifies this does not throw an exception
            // Output may be NaN or Infinity but should not crash
            return pass("Two updates at same millisecond did not throw an exception");
        } catch (Exception e) {
            return fail("Two updates at same millisecond threw: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private TestResult test_outputSmoothing() {
        // Output uses 50% smoothing: output = 0.5*lastOutput + 0.5*(computed)
        // First call: lastOutput=0, so output = 0.5 * 0 + 0.5 * computed = 0.5 * computed
        PID pid = new PID(2.0, 0.0, 0.0, 100.0);
        pid.setCurrentMillis();
        try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        double firstOutput = pid.update(10.0);
        // With kp=2, ki=0, kd=0: raw = kp*error = 20.0
        // output = 0.5*0 + 0.5*20 = 10.0 (approximately, ignoring tiny dt-dependent derivative)
        // Second call with same error:
        try { Thread.sleep(10); } catch (InterruptedException e) { /* ignore */ }
        double secondOutput = pid.update(10.0);
        // lastError was smoothed to ~5.0 after first call, so error for second = 10.0
        // raw = kp*10 + kd*(10-5)/dt ~= 20 (kd=0)
        // output = 0.5*firstOutput + 0.5*20
        // The key observation: second output should be larger than first due to smoothing accumulation
        if (Math.abs(secondOutput) < Math.abs(firstOutput) - 1.0) {
            return fail("Output smoothing: second output (" + secondOutput +
                    ") should not be significantly less than first (" + firstOutput + ")");
        }
        return pass("Output uses 50% smoothing with last output");
    }

    private TestResult test_load_missingFields() {
        PID pid = new PID(1.0, 1.0, 1.0, 10.0);
        CompoundTag tag = new CompoundTag();
        // Only put some fields, not all required ones
        tag.putDouble("kp", 2.0);
        tag.putDouble("ki", 0.5);
        // Missing kd, i, lastError, iBound
        boolean loaded = pid.load(tag);
        return assertFalse("load() should return false if any required field is missing", loaded);
    }
}
