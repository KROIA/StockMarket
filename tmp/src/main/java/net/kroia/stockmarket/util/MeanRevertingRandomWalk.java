package net.kroia.stockmarket.util;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Random;

public class MeanRevertingRandomWalk {
    private double currentValue;
    private final double stepSize;
    private final double meanReversionStrength;
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

/*

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        // Example usage
        MeanRevertingRandomWalk randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);

        // Open file to store the data
        PrintWriter file = new PrintWriter("random_walk.csv", "UTF-8");

        // Generate and print 100 random walk values
        for (int i = 0; i < 10000; i++) {
            // plot to file
            double value = randomWalk.nextValue();
            file.println(i + ";" + value);
        }
        file.close();
    }*/
}