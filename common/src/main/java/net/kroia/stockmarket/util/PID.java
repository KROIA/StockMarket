package net.kroia.stockmarket.util;

import net.kroia.modutilities.NbtCompat;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.nbt.CompoundTag;

public class PID implements ServerSaveable {
    private double ki;
    private double kd;
    private double kp;

    private double i;
    private double lastError;
    private double iBound;

    private double output = 0;
    private double lastOutput = 0;
    /** Symmetric bound for the filtered output, see {@link #setOutputLimit(double)}. Unbounded by default. */
    private double outputLimit = Double.POSITIVE_INFINITY;

    private long lastMillis;
    public PID(double kp, double ki, double kd, double iBound)
    {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;

        this.iBound = iBound;
        this.i = 0;
        this.lastError = 0;
        lastMillis = System.currentTimeMillis();
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putDouble("kp", kp);
        tag.putDouble("ki", ki);
        tag.putDouble("kd", kd);
        tag.putDouble("i", i);
        tag.putDouble("lastError", lastError);
        tag.putDouble("iBound", iBound);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("kp") || !tag.contains("ki") || !tag.contains("kd") || !tag.contains("i") || !tag.contains("lastError") || !tag.contains("iBound")) {
            StockMarketMod.LOGGER.error("PID.load: missing required fields");
            return false;
        }

        kp = NbtCompat.getDouble(tag, "kp");
        ki = NbtCompat.getDouble(tag, "ki");
        kd = NbtCompat.getDouble(tag, "kd");
        i = NbtCompat.getDouble(tag, "i");
        lastError = NbtCompat.getDouble(tag, "lastError");
        iBound = NbtCompat.getDouble(tag, "iBound");
        return true;
    }

    public void setKP(double kp) {
        this.kp = kp;
    }
    public double getKP() {
        return kp;
    }

    public void setKI(double ki) {
        this.ki = ki;
    }
    public double getKI() {
        return ki;
    }

    public void setKD(double kd) {
        this.kd = kd;
    }
    public double getKD() {
        return kd;
    }

    public void setIBound(double iBound) {
        this.iBound = iBound;
    }
    public double getIBound() {
        return iBound;
    }

    /**
     * Clamps the filtered output (and therefore the value fed back into the output filter)
     * to {@code [-limit, limit]}.
     * <p>
     * Callers that only use a bounded range of the output must set this: otherwise a long-lasting
     * large error winds the filter memory up far beyond that range, and after the error changes sign
     * the output keeps the old sign for several updates (overshoot).
     *
     * @param limit maximum absolute output, must be &gt; 0; {@link Double#POSITIVE_INFINITY} disables the clamp
     */
    public void setOutputLimit(double limit) {
        this.outputLimit = limit;
    }

    public double getLastError() {
        return lastError;
    }
    public void setCurrentMillis()
    {
        lastMillis = System.currentTimeMillis();
    }

    public double update(double error)
    {
        long millis = System.currentTimeMillis();
        // Clamp dt to 1 s: after a long pause (market closed outside trading hours, server lag)
        // the elapsed time would otherwise saturate the integral term in a single step.
        double dt = Math.min((millis - lastMillis)/1000.0f, 1.0);
        lastMillis = millis;
        i += error*dt * ki;
        if(i > iBound)
            i = iBound;
        else if(i < -iBound)
            i = -iBound;

        double derivativeTerm = (dt == 0) ? 0 : kd*(error - lastError)/dt;
        output = 0.5f*lastOutput + 0.5f*(kp*error + i + derivativeTerm);
        // Clamp before storing as lastOutput so the filter memory can't wind up beyond the usable range
        output = Math.max(-outputLimit, Math.min(outputLimit, output));
        lastOutput = output;
        lastError = (error*0.5f + lastError*0.5f);
        return output;
    }
    public double getOutput() {
        return output;
    }

}
