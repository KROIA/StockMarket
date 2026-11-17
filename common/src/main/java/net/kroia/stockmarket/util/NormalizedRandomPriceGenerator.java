package net.kroia.stockmarket.util;

import net.kroia.modutilities.NbtCompat;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;

/**
 * SM-337: the noise of the VolatilityPlugin, the sum of four mean-reverting random walks
 * ({@link MeanRevertingRandomWalk}, Ornstein-Uhlenbeck) with real-time correlation times.
 * <p>
 * Components (correlation time tau, stationary standard deviation sigma in noise units; the plugin multiplies
 * the noise by {@code volatilityScale}, 0.3 by default, so the price share is sigma x 0.3):
 * <ul>
 *   <li>tau 5 min, sigma 0.02 (0.6% of the price at 0.3): short-term jitter</li>
 *   <li>tau 1 h, sigma 0.04 (1.2%): intraday moves</li>
 *   <li>tau 8 h, sigma 0.12 (3.6%): swings over a day</li>
 *   <li>tau 60 h, sigma 0.45 (13.5%): multi-day up/down trends</li>
 * </ul>
 * Total stationary sigma ~0.47 noise (~14% of the price at 0.3, so swings of roughly +-20-30% that last days);
 * the standard deviation of the hourly change is ~0.11 noise (~3.4% of the price at 0.3).
 * Most of the variance sits in the slow component on purpose: the market trends instead of jittering.
 * <p>
 * The walks are stepped by elapsed real time ({@link #step(long)}), so the curve is the same at any step rate.
 * A fresh generator starts every walk at 0 (no deviation).
 */
public class NormalizedRandomPriceGenerator implements ServerSaveable {

    /** Correlation times of the components in ms (5 min, 1 h, 8 h, 60 h). */
    static final double[] TAU_MS = {5 * 60_000.0, 3_600_000.0, 8 * 3_600_000.0, 60 * 3_600_000.0};
    /** Stationary standard deviations of the components (noise units). */
    static final double[] SIGMA = {0.02, 0.04, 0.12, 0.45};
    /** Save format of the time-based walks; saves without it are the pre-SM-337 step-count walks. */
    private static final int FORMAT = 2;

    private final MeanRevertingRandomWalk[] walks = new MeanRevertingRandomWalk[TAU_MS.length];
    private double currentValue = 0.0;

    /** Generator with unseeded RNGs. */
    public NormalizedRandomPriceGenerator() {
        this(null);
    }

    /**
     * @param seedSource SM-336: if not null, each walk gets its own RNG seeded from it (deterministic tests);
     *                   null = unseeded RNGs
     */
    public NormalizedRandomPriceGenerator(Random seedSource) {
        for (int i = 0; i < walks.length; i++) {
            walks[i] = new MeanRevertingRandomWalk(SIGMA[i], TAU_MS[i],
                    seedSource != null ? new Random(seedSource.nextLong()) : new Random());
        }
    }

    /**
     * Advances every component by the elapsed time.
     *
     * @param dtMs elapsed real time since the last step in ms
     * @return the new noise value (sum of the components)
     */
    public double step(long dtMs) {
        double sum = 0.0;
        for (MeanRevertingRandomWalk walk : walks) sum += walk.step(dtMs);
        currentValue = sum;
        return sum;
    }

    /** @return the noise value after the last step (0 for a fresh generator) */
    public double getCurrentValue() {
        return currentValue;
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putInt("format", FORMAT);
        for (int i = 0; i < walks.length; i++) {
            CompoundTag walkTag = new CompoundTag();
            walks[i].save(walkTag);
            tag.put("walk_" + i, walkTag);
        }
        tag.putDouble("currentValue", currentValue);
        return true;
    }

    /**
     * Loads the walk values. A pre-SM-337 save ({@code order}, {@code walk_i}, {@code coefficient_i},
     * {@code counter}, no {@code format}) has no meaning for the new components: the walk starts fresh at 0.
     */
    @Override
    public boolean load(CompoundTag tag) {
        boolean current = tag.contains("format") && NbtCompat.getInt(tag, "format") == FORMAT;
        CompoundTag empty = new CompoundTag();
        double sum = 0.0;
        for (int i = 0; i < walks.length; i++) {
            walks[i].load(current && tag.contains("walk_" + i) ? NbtCompat.getCompound(tag, "walk_" + i) : empty);
            sum += walks[i].getCurrentValue();
        }
        currentValue = sum;
        return true;
    }
}
