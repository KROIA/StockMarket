package net.kroia.stockmarket.util;

import net.kroia.modutilities.NbtCompat;


import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;

/**
 * SM-337: a mean-reverting random walk (Ornstein-Uhlenbeck process) around 0, stepped by elapsed real time.
 * <p>
 * Each {@link #step(long)} uses the exact OU transition for the elapsed time {@code dt}:
 * {@code x = x * e^(-dt/tau) + sigma * sqrt(1 - e^(-2 dt/tau)) * N(0,1)}.
 * So the walk has the same statistics whatever the step rate: its stationary standard deviation is
 * {@code sigma}, and its correlation time (how long a deviation lasts) is {@code tau}.
 * Ten 100 ms steps and one 1000 ms step give the same distribution.
 * <p>
 * Only the current value is saved; sigma and tau come from the owner ({@link NormalizedRandomPriceGenerator}).
 */
public class MeanRevertingRandomWalk implements ServerSaveable {
    private double currentValue;
    private final double sigma;
    private final double tauMs;
    private final Random random;

    /**
     * @param sigma stationary standard deviation of the walk
     * @param tauMs correlation time in ms (time for a deviation to decay to 1/e); <= 0 = white noise
     */
    public MeanRevertingRandomWalk(double sigma, double tauMs) {
        this(sigma, tauMs, new Random());
    }

    /**
     * SM-336: same, with a given RNG (seeded in deterministic tests).
     *
     * @param sigma  stationary standard deviation of the walk
     * @param tauMs  correlation time in ms (time for a deviation to decay to 1/e); <= 0 = white noise
     * @param random source of the steps
     */
    public MeanRevertingRandomWalk(double sigma, double tauMs, Random random) {
        this.currentValue = 0.0;
        this.sigma = sigma;
        this.tauMs = tauMs;
        this.random = random;
    }

    /**
     * Advances the walk by the elapsed time.
     *
     * @param dtMs elapsed time since the last step in ms; <= 0 leaves the walk unchanged
     * @return the new value
     */
    public double step(long dtMs) {
        if (dtMs <= 0) return currentValue;
        double decay = tauMs > 0 ? Math.exp(-dtMs / tauMs) : 0.0;
        currentValue = currentValue * decay + sigma * Math.sqrt(1 - decay * decay) * random.nextGaussian();
        return currentValue;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putDouble("currentValue", currentValue);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        currentValue = tag.contains("currentValue") ? NbtCompat.getDouble(tag, "currentValue") : 0.0;
        if (!Double.isFinite(currentValue)) currentValue = 0.0;
        return true;
    }
}
