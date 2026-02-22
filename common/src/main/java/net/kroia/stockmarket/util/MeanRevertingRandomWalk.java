package net.kroia.stockmarket.util;


import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;

public class MeanRevertingRandomWalk implements ServerSaveable {
    private double currentValue;
    private double stepSize;
    private double meanReversionStrength;
    private final Random random;

    /**
     * Constructor for the MeanRevertingRandomWalk.
     *
     * @param stepSize              Maximum size of a single random step.
     * @param meanReversionStrength Strength of the pull back to zero.
     */
    public MeanRevertingRandomWalk(double stepSize, double meanReversionStrength) {
        this.currentValue = 0.0;
        this.stepSize = stepSize;
        this.meanReversionStrength = meanReversionStrength;
        this.random = new Random();
    }

    /**
     * Generates the next value in the random walk.
     *
     * @return The next value.
     */
    public double nextValue() {
        // Random step between -stepSize and +stepSize
        double step = (random.nextDouble() * 2 - 1) * stepSize;

        // Mean reversion term
        double reversion = -meanReversionStrength * currentValue;

        // Update current value
        currentValue += step + reversion;

        return currentValue;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putDouble("currentValue", currentValue);
        tag.putDouble("stepSize", stepSize);
        tag.putDouble("meanReversionStrength", meanReversionStrength);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag.contains("currentValue"))
            currentValue = tag.getDouble("currentValue");
        else
            currentValue = 0.0;
        if(tag.contains("stepSize"))
            stepSize = tag.getDouble("stepSize");
        else
            stepSize = 0.1; // Default value if not present
        if(tag.contains("meanReversionStrength"))
            meanReversionStrength = tag.getDouble("meanReversionStrength");
        else
            meanReversionStrength = 0.05; // Default value if not present
        return true;
    }
}