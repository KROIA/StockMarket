package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class VirtualOrderbook implements ServerSaveable
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }



    private final DynamicIndexedArray dynamicArray;
    private Function<Long, Float> defaultVolumeProvider = null;
    private long currentMarketPrice = 0;

    public VirtualOrderbook(int arraySize)
    {
        dynamicArray = new DynamicIndexedArray(arraySize, this::getDefaultVolume);
    }
    public VirtualOrderbook()
    {
        int defaultSize = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get();
        dynamicArray = new DynamicIndexedArray(defaultSize, this::getDefaultVolume);
    }

    public void setDefaultVolumeProvider(@Nullable Function<Long, Float> volumeProvider)
    {
        defaultVolumeProvider = volumeProvider;
    }


    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
    }

    /**
     * Sets the volume to its default distribution
     */
    public void resetVolumeDistribution()
    {
        dynamicArray.resetToDefaultValues();
    }


    /**
     * Sets the given volume at the given price.
     * The volume will be set positive if the given price is below the current market price
     * The colume will be set negative if the given price is above the current market price
     * @param price on which the volume gets applied
     * @param volume to apply. Use the abs value.
     */
    public void setVolume(long price, float volume)
    {
        if(price > currentMarketPrice)
        {
            dynamicArray.set(price, -Math.abs(volume));
        } else if(price < currentMarketPrice)
        {
            dynamicArray.set(price, Math.abs(volume));
        }
        else
        {
            dynamicArray.set(price, volume);
        }
    }

    /**
     * Sets the volume onto the given range.
     * The absolute value of the volume is taken
     * Flipping to sell order above the current market price is done automatically
     * @param startPrice on which the volume needs to be applied
     * @param endPrice to which (including) the volume gets applied
     * @param volume volume to apply. the abs(volume) is taken
     */
    public void setVolume(long startPrice, long endPrice, float volume)
    {
        int count = (int)(endPrice-startPrice);
        if(count <= 0)
            return;
        dynamicArray.set(startPrice, count, currentMarketPrice, volume);
    }

    /**
     * Sets the given volume to the array
     * @apiNote
     * The volume must match the requirements of having negative values above the current market price
     * and positive values below the market price!
     * @param startPrice to start overwriting the volume
     * @param volume that has positive values below the current market price and negative above the market price
     */
    public void setVolume(long startPrice, float[] volume)
    {
        dynamicArray.set(startPrice, volume);
    }

    /**
     * Sets the given volume at the given price.
     * The given volume can be positive or negative.
     * The volume will be added in such a way that in the end the buy side is positive and
     * the sell side is negative.
     * @param price on which the volume gets applied
     * @param volume to apply. positive or negative
     */
    public void addVoume(long price, float volume)
    {
        float currentVolume = dynamicArray.get(price);
        if(price > currentMarketPrice)
        {
            dynamicArray.set(price, Math.min(0, currentVolume + volume));
        }
        else if(price < currentMarketPrice)
        {
            dynamicArray.set(price, Math.max(0, currentVolume + volume));
        }
        else
        {
            dynamicArray.set(price, currentVolume + volume);
        }
    }

    /**
     * Adds the absolute value to the existing volume
     * Above the market price, negative volume gets applied
     * @param startPrice on which the volume needs to be applied
     * @param endPrice to which (including) the volume gets applied
     * @param volume volume to apply. the abs(volume) is taken
     */
    public void addVolume(long startPrice, long endPrice, float volume)
    {
        int count = (int)(endPrice-startPrice);
        if(count <= 0)
            return;
        dynamicArray.add(startPrice, count, currentMarketPrice, volume);
    }

    /**
     * Adds the given volume to the array
     * @apiNote
     * The volume can be positive or negative on every element
     * The resulting volume after addition will be positive for prices below the current market price
     * and negative for prices above the current market price.
     * @param startPrice to start adding the volume
     * @param volume that contains positive and negative values
     */
    public void addVolume(long startPrice, float[] volume)
    {
        dynamicArray.add(startPrice, volume, currentMarketPrice);
    }


    /**
     * Gets the volume at the given price
     * @param price
     * @return positive value for buy order
     *         negative value for sell order
     */
    public float getVolume(long price)
    {
        return dynamicArray.get(price);
    }
    public float getVolumeRounded(long price)
    {
        return dynamicArray.getRounded(price);
    }

    /**
     * Gets the volume in the given price range
     * @apiNote
     * Reading the volume using a range so that the current market price lays in between the given range
     * will result in a wrong prediction since the buy and sell order cancel each other.
     * @param startPrice
     * @param endPrice inclusive
     * @return the sum of volume inbetween the given range
     */
    public float getVolume(long startPrice, long endPrice)
    {
        return dynamicArray.getSum(startPrice, endPrice+1);
    }
    public long getVolumeRounded(long startPrice, long endPrice)
    {
        return dynamicArray.getSumRounded(startPrice, endPrice+1);
    }


    public float getCapital(long startPrice, long endPrice)
    {
        return dynamicArray.getSumProduct(startPrice, endPrice+1);
    }
    public long getCapitalRounded(long startPrice, long endPrice)
    {
        return dynamicArray.getSumProductRounded(startPrice, endPrice+1);
    }










    /**
     * Gets the default volume at a given price
     * @apiNote
     * @param price on which the volume needs to be calculatet at
     * @return positive volume vor buy order, negative volume for sell order
     */
    public float getDefaultVolume(long price)
    {
        if(defaultVolumeProvider != null)
        {
            float result = defaultVolumeProvider.apply(price);
            if(price > currentMarketPrice)
                return -Math.abs(result);
            else if(price < currentMarketPrice)
                return Math.abs(result);
            else
                return result;
        }
        return 0;
    }
    public long getDefaultVolumeRounded(long price)
    {
        if(defaultVolumeProvider != null)
        {
            long result = roundConservative(defaultVolumeProvider.apply(price));
            if(price > currentMarketPrice)
                return -Math.abs(result);
            else if(price < currentMarketPrice)
                return Math.abs(result);
            else
                return result;
        }
        return 0;
    }


    /**
     * Rounds the given value in such a way that the absolute value of the returned
     * long is always less or equal than the input:
     *                      |result| <= |input| && sign(result) == sign(input)
     * @param value
     * @return
     */
    public static long roundConservative(float value)
    {
        if(value < 0)
            return (long)Math.ceil(value);
        else
            return (long)Math.floor(value);
    }

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        CompoundTag arrayTag = new CompoundTag();
        success &= dynamicArray.save(arrayTag);
        tag.put("array", arrayTag);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;

        if(!tag.contains("array")) {
            success = false;
            error("Can't load VirtualOrderbook from NBT tag");
        }
        else
        {
            CompoundTag arrayTag = tag.getCompound("array");
            success &= dynamicArray.load(arrayTag);
        }
        return success;
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[VirtualOrderbook]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[VirtualOrderbook]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[VirtualOrderbook]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[VirtualOrderbook]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[VirtualOrderbook]: "+message);
    }
}
