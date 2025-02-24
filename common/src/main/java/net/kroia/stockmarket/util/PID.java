package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

public class PID implements ServerSaveable {
    private float ki;
    private float kd;
    private float kp;

    private float i;
    private float lastError;
    private float iBound;

    private float output = 0;
    private float lastOutput = 0;

    private long lastMillis;
    public PID(float kp, float ki, float kd, float iBound)
    {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;

        this.iBound = iBound;
        this.i = 0;
        this.lastError = 0;
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putFloat("kp", kp);
        tag.putFloat("ki", ki);
        tag.putFloat("kd", kd);
        tag.putFloat("i", i);
        tag.putFloat("lastError", lastError);
        tag.putFloat("iBound", iBound);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(!tag.contains("kp") || !tag.contains("ki") || !tag.contains("kd") || !tag.contains("i") || !tag.contains("lastError") || !tag.contains("iBound"))
            return false;

        kp = tag.getFloat("kp");
        ki = tag.getFloat("ki");
        kd = tag.getFloat("kd");
        i = tag.getFloat("i");
        lastError = tag.getFloat("lastError");
        iBound = tag.getFloat("iBound");
        return true;
    }

    public void setKP(float kp) {
        this.kp = kp;
    }
    public float getKP() {
        return kp;
    }

    public void setKI(float ki) {
        this.ki = ki;
    }
    public float getKI() {
        return ki;
    }

    public void setKD(float kd) {
        this.kd = kd;
    }
    public float getKD() {
        return kd;
    }

    public void setIBound(float iBound) {
        this.iBound = iBound;
    }
    public float getIBound() {
        return iBound;
    }

    public float getLastError() {
        return lastError;
    }

    public float update(float error)
    {
        long millis = System.currentTimeMillis();
        float dt = (millis - lastMillis)/1000.0f;
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
    public float getOutput() {
        return output;
    }

}
