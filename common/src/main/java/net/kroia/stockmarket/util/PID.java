package net.kroia.stockmarket.util;

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

        kp = tag.getDouble("kp");
        ki = tag.getDouble("ki");
        kd = tag.getDouble("kd");
        i = tag.getDouble("i");
        lastError = tag.getDouble("lastError");
        iBound = tag.getDouble("iBound");
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
        double dt = (millis - lastMillis)/1000.0f;
        lastMillis = millis;
        i += error*dt * ki;
        if(i > iBound)
            i = iBound;
        else if(i < -iBound)
            i = -iBound;

        output = 0.5f*lastOutput + 0.5f*(kp*error + i + kd*(error - lastError)/dt);
        lastOutput = output;
        lastError = (error*0.5f + lastError*0.5f);
        return output;
    }
    public double getOutput() {
        return output;
    }

}
