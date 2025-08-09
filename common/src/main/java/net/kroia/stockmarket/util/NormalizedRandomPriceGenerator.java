package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

public class NormalizedRandomPriceGenerator implements ServerSaveable {

    private MeanRevertingRandomWalk[] randomWalk;
    private double[] coefficients;
    private long counter = 0;
    private double currentValue = 0.0;

    public NormalizedRandomPriceGenerator(int order)
    {
        if(order <= 0)
            order = 1;
        randomWalk = new MeanRevertingRandomWalk[order];
        coefficients = new double[order];

        double sumCoeff = 0;
        for (int i = 0; i < order; i++) {
            // Initialize each random walk with a step size and mean reversion strength
            randomWalk[i] = new MeanRevertingRandomWalk(0.05, 0.05);
            coefficients[i] = i+1; // Equal coefficients for normalization
            sumCoeff += coefficients[i];
        }

        // Normalize coefficients to ensure they sum to 1
        for (int i = 0; i < order; i++) {
            coefficients[i] = coefficients[i] * (double)order / sumCoeff;
        }
    }


    public double getNextValue() {
        double sum = 0.0;
        counter++;
        for(int i=0; i<randomWalk.length; i++) {
            if(counter % ((long) 10*i * i + 1) == 0) {
                randomWalk[i].nextValue(); // Update each random walk
            }
            double value = randomWalk[i].getCurrentValue();
            sum += value * coefficients[i]; // Apply the coefficient to each random walk's value
        }
        currentValue = sum; // Store the current value for potential future use
        return sum; // Normalize by the number of walks
    }
    public double getCurrentValue()
    {
        return currentValue;
    }


    @Override
    public boolean save(CompoundTag tag) {
        tag.putInt("order", randomWalk.length);
        for (int i = 0; i < randomWalk.length; i++) {
            CompoundTag walkTag = new CompoundTag();
            randomWalk[i].save(walkTag);
            tag.put("walk_" + i, walkTag);
            tag.putDouble("coefficient_" + i, coefficients[i]);
        }
        tag.putLong("counter", counter);
        tag.putDouble("currentValue", currentValue);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        int order = tag.getInt("order");
        if (order <= 0) {
            return false; // Invalid order
        }
        randomWalk = new MeanRevertingRandomWalk[order];
        coefficients = new double[order];

        for (int i = 0; i < order; i++) {
            CompoundTag walkTag = tag.getCompound("walk_" + i);
            randomWalk[i] = new MeanRevertingRandomWalk(0.1, 0.05); // Default step size and mean reversion strength
            randomWalk[i].load(walkTag);
            coefficients[i] = tag.getDouble("coefficient_" + i);
        }
        counter = tag.getLong("counter");
        currentValue = tag.getDouble("currentValue");
        return true;
    }

    public void testToFile(int steps)
    {
        String fileName = "normalized_random_walk.csv";

        StringBuilder sb = new StringBuilder();
        sb.append("Step;");
        for(int i=0; i<randomWalk.length; i++)
        {
            sb.append("Walk_").append(i).append(";");
        }
        sb.append("sum\n");

        for(int i=0; i<steps; i++)
        {
            sb.append(i).append(";");
            for(int j=0; j<randomWalk.length; j++)
            {
                double value = randomWalk[j].getCurrentValue() * coefficients[j];
                sb.append(value).append(";");
            }
            sb.append(getNextValue()).append("\n");
        }
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(fileName), sb.toString().getBytes());
            System.out.println("Data written to " + fileName);
        } catch (java.io.IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
