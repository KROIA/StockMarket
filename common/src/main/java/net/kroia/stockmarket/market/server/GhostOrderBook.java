package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;


public class GhostOrderBook implements ServerSaveable {
    private int currentMarketPrice = 0;
    private final DynamicIndexedArray virtualOrderVolumeDistribution;
    private long lastMillis = System.currentTimeMillis();
    private float volumeScale = 100f;
    private float newVolumeDeltaScale1 = 0.1f;
    private float newVolumeDeltaScale2 = 0.5f;
    public GhostOrderBook() {
        virtualOrderVolumeDistribution = new DynamicIndexedArray(1000, this::getTargetAmount);
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
            float currentVal = Math.abs(virtualOrderVolumeDistribution.get(virtualIndex));
            if(currentVal<Math.abs(targetAmount)) {
                float scale = newVolumeDeltaScale1;
                if(currentVal < Math.abs(targetAmount)*0.1f)
                {
                    scale = newVolumeDeltaScale2;
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

    public void setNewVolumeDeltaScale(float newVolumeDeltaScale1, float newVolumeDeltaScale2) {
        this.newVolumeDeltaScale1 = newVolumeDeltaScale1;
        this.newVolumeDeltaScale2 = newVolumeDeltaScale2;
    }
    public float getNewVolumeDeltaScale1() {
        return newVolumeDeltaScale1;
    }
    public float getNewVolumeDeltaScale2() {
        return newVolumeDeltaScale2;
    }

    @Override
    public boolean load(CompoundTag tag) {
        volumeScale = tag.getFloat("volumeScale");
        newVolumeDeltaScale1 = tag.getFloat("newVolumeDeltaScale1");
        newVolumeDeltaScale2 = tag.getFloat("newVolumeDeltaScale2");
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
        tag.putFloat("newVolumeDeltaScale1", newVolumeDeltaScale1);
        tag.putFloat("newVolumeDeltaScale2", newVolumeDeltaScale2);
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
        float relativePrice = (currentPriceFloat - (float)price)/(currentPriceFloat+1);
        float width = 1f;

        float amount = 0;
        if(relativePrice < 20 && relativePrice > -20)
            amount += (float)Math.E * width * relativePrice * (float)Math.exp(-Math.abs(relativePrice*width));


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
