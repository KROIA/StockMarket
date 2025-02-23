package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;


public class GhostOrderBook implements ServerSaveable {
    private int currentMarketPrice = 0;
    private final DynamicIndexedArray virtualOrderVolumeDistribution;
    private long lastMillis;
    private float volumeScale = 100f;
    private float nearMarketVolumeStrength = 2f;
    private float volumeAccumulationRate = 0.1f;
    private float volumeFastAccumulationRate = 0.5f;
    private float volumeDecumulationRate = 0.01f;
    public GhostOrderBook(int initialPrice) {
        virtualOrderVolumeDistribution = new DynamicIndexedArray(1000, this::getTargetAmount);
        lastMillis = System.currentTimeMillis()-1000;
        setCurrentMarketPrice(initialPrice);
        updateVolume(initialPrice);
    }

    public void cleanup() {
    }

    public void updateVolume(int currentPrice) {
        long currentMillis = System.currentTimeMillis();
        double deltaT = (currentMillis - lastMillis) / 1000.0;
        lastMillis = currentMillis;

        for(int i=0; i<virtualOrderVolumeDistribution.getSize(); i++)
        {
            int virtualIndex = virtualOrderVolumeDistribution.getVirtualIndex(i);
            if(virtualIndex < 0)
                continue;
            float targetAmount = getTargetAmount(virtualIndex);
            float currentVal = virtualOrderVolumeDistribution.get(virtualIndex);
            if((currentVal<targetAmount) || (currentVal>targetAmount)) {
                float scale = volumeAccumulationRate;
                if(Math.abs(currentVal) < Math.abs(targetAmount)*0.1f)
                {
                    scale = volumeFastAccumulationRate;
                }
                else if(Math.abs(currentVal) > Math.abs(targetAmount))
                {
                    scale = -volumeDecumulationRate;
                }
                virtualOrderVolumeDistribution.add(virtualIndex, targetAmount * (float) deltaT * scale);
            }
        }
    }

    public void setCurrentMarketPrice(int currentMarketPrice) {
        this.currentMarketPrice = currentMarketPrice;
        int currentIndexOffset = virtualOrderVolumeDistribution.getIndexOffset();
        int sizeForth = virtualOrderVolumeDistribution.getSize()/4;
        if(currentMarketPrice > currentIndexOffset + sizeForth*3)
        {
            virtualOrderVolumeDistribution.setOffset(currentMarketPrice-virtualOrderVolumeDistribution.getSize()/2);
        }
        else if(currentMarketPrice < currentIndexOffset + sizeForth)
        {
            virtualOrderVolumeDistribution.setOffset(currentMarketPrice-virtualOrderVolumeDistribution.getSize()/2);
        }
    }

    public void setVolumeScale(float volumeScale) {
        this.volumeScale = volumeScale;
    }
    public float getVolumeScale() {
        return volumeScale;
    }

    public void setNearMarketVolumeStrength(float nearMarketVolumeStrength) {
        this.nearMarketVolumeStrength = nearMarketVolumeStrength;
    }
    public float getNearMarketVolumeStrength() {
        return nearMarketVolumeStrength;
    }
    public void setVolumeAccumulationRate(float volumeAccumulationRate) {
        this.volumeAccumulationRate = volumeAccumulationRate;
    }
    public float getVolumeAccumulationRate() {
        return volumeAccumulationRate;
    }
    public void setVolumeFastAccumulationRate(float volumeFastAccumulationRate) {
        this.volumeFastAccumulationRate = volumeFastAccumulationRate;
    }
    public float getVolumeFastAccumulationRate() {
        return volumeFastAccumulationRate;
    }
    public void setVolumeDecumulationRate(float volumeDecumulationRate) {
        this.volumeDecumulationRate = volumeDecumulationRate;
    }
    public float getVolumeDecumulationRate() {
        return volumeDecumulationRate;
    }

    @Override
    public boolean load(CompoundTag tag) {
        volumeScale = tag.getFloat("volumeScale");
        nearMarketVolumeStrength = tag.getFloat("nearMarketVolumeStrength");
        volumeAccumulationRate = tag.getFloat("volumeAccumulationRate");
        volumeFastAccumulationRate = tag.getFloat("volumeFastAccumulationRate");
        volumeDecumulationRate = tag.getFloat("volumeDecumulationRate");

        int size = tag.getInt("arraySize");
        int[] intArray = tag.getIntArray("volumeArray");
        float[] array = new float[size];
        for(int i=0; i<size; i++)
        {
            array[i] = Float.intBitsToFloat(intArray[i]);
        }
        virtualOrderVolumeDistribution.setOffset(tag.getInt("currentIndexOffset"));
        for(int i=0; i<size; i++)
        {
            virtualOrderVolumeDistribution.set(virtualOrderVolumeDistribution.getVirtualIndex(i), array[i]);
        }

        return false;
    }
    @Override
    public boolean save(CompoundTag tag) {
        int size = virtualOrderVolumeDistribution.getSize();
        float[] array = virtualOrderVolumeDistribution.getArray();
        int[] intArray = new int[size];
        for(int i=0; i<size; i++)
        {
            intArray[i] = Float.floatToIntBits(array[i]);
        }
        tag.putFloat("volumeScale", volumeScale);
        tag.putFloat("nearMarketVolumeStrength", nearMarketVolumeStrength);
        tag.putFloat("volumeAccumulationRate", volumeAccumulationRate);
        tag.putFloat("volumeFastAccumulationRate", volumeFastAccumulationRate);
        tag.putFloat("volumeDecumulationRate", volumeDecumulationRate);
        tag.putInt("arraySize", size);
        tag.putInt("currentIndexOffset", virtualOrderVolumeDistribution.getIndexOffset());
        tag.putIntArray("volumeArray", intArray);
        return true;
    }

    /**
     * Get the amount of items to buy or sell based on the price difference
     * @param price on which the amount should be based
     * @return the amount of items to buy or sell. Negative values indicate selling
     */
    public int getAmount(int price)
    {
        if(virtualOrderVolumeDistribution.isInRange(price))
        {
            return (int)virtualOrderVolumeDistribution.get(price);
        }
        return (int)getTargetAmount(price);
    }
    public void removeAmount(int price, int amount)
    {
        if(virtualOrderVolumeDistribution.isInRange(price))
        {
            if(virtualOrderVolumeDistribution.get(price) > 0)
            {
                virtualOrderVolumeDistribution.add(price, -Math.abs(amount));
            }
            else if(virtualOrderVolumeDistribution.get(price) < 0)
            {
                virtualOrderVolumeDistribution.add(price, Math.abs(amount));
            }
        }
    }

    private float getTargetAmount(int price)
    {
        if(price < 0)
            return 0;
        // Calculate close price volume distribution
        float currentPriceFloat = (float)currentMarketPrice;
        //float relativePrice = (currentPriceFloat - (float)price)/(currentPriceFloat+1);
        float relativePrice = (currentPriceFloat - (float)price);
        float width = 1f;

        final float constant1 = (float)(2.0/Math.E);

        float amount = 0;
        if(relativePrice < 20 && relativePrice > -20) {
            //amount += (float) Math.E * width * relativePrice * (float) Math.exp(-Math.abs(relativePrice * width));
            float sqrt = (float) Math.sqrt(Math.abs(relativePrice)) * Math.signum(relativePrice);
            amount += (float) constant1 * nearMarketVolumeStrength * sqrt * (float) Math.exp(-Math.abs(relativePrice*relativePrice*0.05));
        }


        if(relativePrice > 0)
            amount += 0.1f;
        else if(relativePrice <= 0)
            amount += -0.1f;
        if(price == 0)
            amount += 0.2f;

        float lowPriceAccumulator = 1/(1+(float)price);
        if(relativePrice > 0)
            amount += lowPriceAccumulator*5;

        return (amount*volumeScale);
    }
}
