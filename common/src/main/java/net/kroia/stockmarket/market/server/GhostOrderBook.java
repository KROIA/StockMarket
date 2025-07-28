package net.kroia.stockmarket.market.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;


/**
 * This class represents a ghost order book that is used to simulate the order book of a stock market.
 * Players can buy or sell into the ghost orders.
 */
public class GhostOrderBook implements ServerSaveable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private int currentMarketPrice = 0;
    private final DynamicIndexedArray virtualOrderVolumeDistribution;
    private long lastMillis;
    private float volumeScale = 100f;
    private float nearMarketVolumeScale = 2f;
    private float volumeAccumulationRate = 0.1f;
    private float volumeFastAccumulationRate = 0.5f;
    private float volumeDecumulationRate = 0.01f;
    public GhostOrderBook(int initialPrice) {
        virtualOrderVolumeDistribution = new DynamicIndexedArray(1000, this::getTargetAmount);
        lastMillis = System.currentTimeMillis()-1000;
        setCurrentPrice(initialPrice);
        updateVolume(initialPrice);
    }

    public void cleanup() {
        virtualOrderVolumeDistribution.clear();
    }

    public void updateVolume(int currentPrice) {
        long currentMillis = System.currentTimeMillis();
        double deltaT = Math.min((currentMillis - lastMillis) / 1000.0, 1000.0);
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
                    scale = volumeDecumulationRate;
                }
                float deltaAmount = (targetAmount - currentVal) * (float) deltaT * scale;
                if(deltaAmount < 0 && currentVal > 0 && -deltaAmount > currentVal)
                {
                    deltaAmount = -currentVal;
                }
                else if(deltaAmount > 0 && currentVal < 0 && deltaAmount > -currentVal)
                {
                    deltaAmount = -currentVal;
                }
                virtualOrderVolumeDistribution.add(virtualIndex, deltaAmount);

            }
        }
    }

    public void setCurrentPrice(int currentMarketPrice) {
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

    public void setNearMarketVolumeScale(float nearMarketVolumeScale) {
        this.nearMarketVolumeScale = nearMarketVolumeScale;
    }
    public float getNearMarketVolumeScale() {
        return nearMarketVolumeScale;
    }
    public void setVolumeAccumulationRate(float volumeAccumulationRate) {
        this.volumeAccumulationRate = volumeAccumulationRate;
        if(volumeAccumulationRate <= 0)
            this.volumeAccumulationRate = 0.00001f;
    }
    public float getVolumeAccumulationRate() {
        return volumeAccumulationRate;
    }
    public void setVolumeFastAccumulationRate(float volumeFastAccumulationRate) {
        this.volumeFastAccumulationRate = volumeFastAccumulationRate;
        if(volumeFastAccumulationRate <= 0)
            this.volumeFastAccumulationRate = 0.00001f;
    }
    public float getVolumeFastAccumulationRate() {
        return volumeFastAccumulationRate;
    }
    public void setVolumeDecumulationRate(float volumeDecumulationRate) {
        this.volumeDecumulationRate = volumeDecumulationRate;
        if(volumeDecumulationRate <= 0)
            this.volumeDecumulationRate = 0.00001f;
    }
    public float getVolumeDecumulationRate() {
        return volumeDecumulationRate;
    }



    /**
     * Get the amount of items to buy or sell based on the price difference
     * @param price on which the amount should be based
     * @return the amount of items to buy or sell. Negative values indicate selling
     */
    public long getAmount(int price)
    {
        if(virtualOrderVolumeDistribution.isInRange(price))
        {
            return (long)virtualOrderVolumeDistribution.get(price);
        }
        return (long)getTargetAmount(price);
    }
    public void removeAmount(int price, long amount)
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
            amount += (float) constant1 * nearMarketVolumeScale * sqrt * (float) Math.exp(-Math.abs(relativePrice*relativePrice*0.05));
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

    @Override
    public boolean load(CompoundTag tag) {
        volumeScale = tag.getFloat("volumeScale");
        nearMarketVolumeScale = tag.getFloat("nearMarketVolumeStrength");
        volumeAccumulationRate = tag.getFloat("volumeAccumulationRate");
        volumeFastAccumulationRate = tag.getFloat("volumeFastAccumulationRate");
        volumeDecumulationRate = tag.getFloat("volumeDecumulationRate");

        if(volumeAccumulationRate <= 0)
            this.volumeAccumulationRate = 0.00001f;
        if(volumeFastAccumulationRate <= 0)
            this.volumeFastAccumulationRate = 0.00001f;
        if(volumeDecumulationRate <= 0)
            this.volumeDecumulationRate = 0.00001f;

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

        return true;
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
        tag.putFloat("nearMarketVolumeStrength", nearMarketVolumeScale);
        tag.putFloat("volumeAccumulationRate", volumeAccumulationRate);
        tag.putFloat("volumeFastAccumulationRate", volumeFastAccumulationRate);
        tag.putFloat("volumeDecumulationRate", volumeDecumulationRate);
        tag.putInt("arraySize", size);
        tag.putInt("currentIndexOffset", virtualOrderVolumeDistribution.getIndexOffset());
        tag.putIntArray("volumeArray", intArray);
        return true;
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("volumeScale", volumeScale);
        jsonObject.addProperty("nearMarketVolumeScale", nearMarketVolumeScale);
        jsonObject.addProperty("volumeAccumulationRate", volumeAccumulationRate);
        jsonObject.addProperty("volumeFastAccumulationRate", volumeFastAccumulationRate);
        jsonObject.addProperty("volumeDecumulationRate", volumeDecumulationRate);
        jsonObject.addProperty("currentMarketPrice", currentMarketPrice);
        jsonObject.addProperty("lastMillis", lastMillis);
        jsonObject.addProperty("virtualOrderVolumeDistributionSize", virtualOrderVolumeDistribution.getSize());
        /*JsonObject volumeDistribution = new JsonObject();
        for(int i=0; i<virtualOrderVolumeDistribution.getSize(); i++)
        {
            int virtualIndex = virtualOrderVolumeDistribution.getVirtualIndex(i);
            if(virtualIndex < 0)
                continue;
            volumeDistribution.addProperty(String.valueOf(virtualIndex), virtualOrderVolumeDistribution.get(virtualIndex));
        }*/
        return jsonObject;
    }
    public String toJsonString() {
        return GSON.toJson(toJson());
    }
    @Override
    public String toString() {
        return toJsonString();
    }
}
