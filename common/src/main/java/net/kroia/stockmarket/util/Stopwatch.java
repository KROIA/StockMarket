package net.kroia.stockmarket.util;

public class Stopwatch {
    private long startTime;
    private long endTime;
    private boolean running;

    public Stopwatch() {
        this.running = false;
    }

    public void start() {
        this.startTime = System.nanoTime();
        this.running = true;
    }

    public long stop() {
        this.endTime = System.nanoTime();
        this.running = false;
        return getElapsedTime();
    }
    public boolean isRunning() {
        return running;
    }

    public long getElapsedTime() {
        long elapsed;
        if (running) {
            elapsed = System.nanoTime() - startTime;
        } else {
            elapsed = endTime - startTime;
        }
        return elapsed;
    }

    public double getElapsedTimeInSeconds() {
        return getElapsedTime() / 1_000_000_000.0;
    }
    public double getElapsedTimeInMilliseconds() {
        return getElapsedTime() / 1_000_000.0;
    }
    public double getElapsedTimeInMicroseconds() {
        return getElapsedTime() / 1_000.0;
    }

}
