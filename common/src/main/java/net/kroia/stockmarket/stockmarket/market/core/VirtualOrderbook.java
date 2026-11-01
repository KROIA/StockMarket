package net.kroia.stockmarket.stockmarket.market.core;

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
    /**
     * Sticky-clear flag. When true the shift-fill provider ({@link #getDefaultVolume(long)})
     * returns 0 for every price level, so any array slot repopulated after a price move
     * lands at 0 instead of the plugin-provided default distribution. Toggled by
     * {@link #setCleared(boolean)}; consulted by both {@link #getDefaultVolume(long)} and
     * {@link #getDefaultVolumeRounded(long)}.
     */
    private boolean cleared = false;
    /**
     * Runtime "disabled" flag mirroring {@link net.kroia.stockmarket.stockmarket.market.MarketSettings#virtualOrderbookEnabled}
     * (inverted). When true, every read method returns 0 and every write method is a
     * no-op — the virtual orderbook contributes no liquidity to the matching engine
     * and cannot be populated by plugins. Kept in sync with the settings flag by
     * {@link net.kroia.stockmarket.stockmarket.market.ServerMarket#setSettings(net.kroia.stockmarket.stockmarket.market.MarketSettings)}.
     * Distinct from {@link #cleared} — cleared is a persistent admin choice, disabled
     * is the "off switch" for the whole subsystem.
     */
    private boolean disabled = false;

    public VirtualOrderbook(int arraySize)
    {
        dynamicArray = new DynamicIndexedArray(arraySize, this::getDefaultVolume);
    }
    public VirtualOrderbook()
    {
        int defaultSize = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get();
        dynamicArray = new DynamicIndexedArray(defaultSize, this::getDefaultVolume);
    }

    /**
     * Input: Long: raw price level (backend price)
     * Output: Float: raw volume (backend volume)
     * @param volumeProvider
     */
    public void setDefaultVolumeProvider(@Nullable Function<Long, Float> volumeProvider)
    {
        defaultVolumeProvider = volumeProvider;
    }


    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        long currentIndexOffset = dynamicArray.getIndexOffset();
        int sizeForth = dynamicArray.getSize()/4;
        if(currentMarketPrice > currentIndexOffset + sizeForth*3)
        {
            dynamicArray.setOffset(currentMarketPrice-dynamicArray.getSize()/2);
        }
        else if(currentMarketPrice < currentIndexOffset + sizeForth)
        {
            dynamicArray.setOffset(currentMarketPrice-dynamicArray.getSize()/2);
        }
    }

    /**
     * Sets the volume to its default distribution.
     * <p>
     * If the sticky-clear flag is set, every default lookup returns 0, so this call
     * zeroes the array while keeping shift-fill zeroed. Otherwise the plugin-provided
     * distribution refills the array normally.
     */
    public void resetVolumeDistribution()
    {
        dynamicArray.resetToDefaultValues();
    }

    /**
     * Zeroes the array in-place and sets the sticky-clear flag so any future
     * array shift also lands at 0. Distinct from {@link #resetVolumeDistribution()}:
     * "clear" is sticky through price moves, "reset" refills from the default
     * provider and drops the sticky flag.
     */
    public void clearVolume()
    {
        cleared = true;
        // resetToDefaultValues consults getDefaultVolume, which now returns 0 for every price.
        dynamicArray.resetToDefaultValues();
    }

    /**
     * Setter for the sticky-clear flag. Callers should pair a {@code true} write with
     * a follow-up {@link DynamicIndexedArray#resetToDefaultValues()} call to zero the
     * currently-visible slots (see {@link #clearVolume()}); {@code false} should be
     * paired with a full {@link #resetVolumeDistribution()} so slots are refilled.
     */
    public void setCleared(boolean cleared)
    {
        this.cleared = cleared;
    }

    public boolean isCleared()
    {
        return cleared;
    }

    /**
     * Sets the runtime "disabled" flag. When true, all reads return 0 and all writes
     * are dropped — the virtual orderbook effectively vanishes from the matching engine
     * and from every plugin's point of view. See field Javadoc for the flag's contract.
     */
    public void setDisabled(boolean disabled)
    {
        this.disabled = disabled;
    }

    public boolean isDisabled()
    {
        return disabled;
    }

    public long getMinEditablePrice()
    {
        return dynamicArray.getVirtualIndex(0);
    }
    public long getMaxEditablePrice()
    {
        return dynamicArray.getVirtualIndex(dynamicArray.getSize()-1);
    }


    /**
     * Sets the given volume at the given price.
     * The volume will be set positive if the given price is below the current stockmarket price
     * The colume will be set negative if the given price is above the current stockmarket price
     * @param price on which the volume gets applied
     * @param volume to apply. Use the abs value.
     */
    public void setVolume(long price, float volume)
    {
        // Disabled → drop every write. Matches the "must stay empty while disabled"
        // contract from Task 4; plugins are already gated on isVirtualOrderbookEnabled()
        // but this is defense-in-depth for any direct caller (tests, admin tools).
        if(disabled) return;
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
     * Flipping to sell order above the current stockmarket price is done automatically
     * @param startPrice on which the volume needs to be applied
     * @param endPrice to which (including) the volume gets applied
     * @param volume volume to apply. the abs(volume) is taken
     */
    public void setVolume(long startPrice, long endPrice, float volume)
    {
        if(disabled) return;
        int count = (int)(endPrice-startPrice);
        if(count <= 0)
            return;
        dynamicArray.set(startPrice, count, currentMarketPrice, volume);
    }

    /**
     * Sets the given volume to the array
     * @apiNote
     * The volume must match the requirements of having negative values above the current stockmarket price
     * and positive values below the stockmarket price!
     * @param startPrice to start overwriting the volume
     * @param volume that has positive values below the current stockmarket price and negative above the stockmarket price
     */
    public void setVolume(long startPrice, float[] volume)
    {
        if(disabled) return;
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
        if(disabled) return;
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
     * Above the stockmarket price, negative volume gets applied
     * @param startPrice on which the volume needs to be applied
     * @param endPrice to which (including) the volume gets applied
     * @param volume volume to apply. the abs(volume) is taken
     */
    public void addVolume(long startPrice, long endPrice, float volume)
    {
        if(disabled) return;
        int count = (int)(endPrice-startPrice);
        if(count <= 0)
            return;
        dynamicArray.add(startPrice, count, currentMarketPrice, volume);
    }

    /**
     * Adds the given volume to the array
     * @apiNote
     * The volume can be positive or negative on every element
     * The resulting volume after addition will be positive for prices below the current stockmarket price
     * and negative for prices above the current stockmarket price.
     * @param startPrice to start adding the volume
     * @param volume that contains positive and negative values
     */
    public void addVolume(long startPrice, float[] volume)
    {
        if(disabled) return;
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
        // Source-gate: when the VO subsystem is disabled every read returns 0 so the
        // matching engine (and every other consumer that goes through Orderbook) sees
        // no virtual liquidity at any price level. Chosen over per-caller gating
        // because all reads funnel through this small set of methods.
        if(disabled) return 0f;
        return dynamicArray.get(price);
    }
    public float getVolumeRounded(long price)
    {
        if(disabled) return 0f;
        return dynamicArray.getRounded(price);
    }

    /**
     * Gets the volume in the given price range
     * @apiNote
     * Reading the volume using a range so that the current stockmarket price lays in between the given range
     * will result in a wrong prediction since the buy and sell order cancel each other.
     * @param startPrice
     * @param endPrice inclusive
     * @return the sum of volume in between the given range
     */
    public float getVolume(long startPrice, long endPrice)
    {
        if(disabled) return 0f;
        return dynamicArray.getSum(startPrice, endPrice+1);
    }
    public float getVolumeInterpolated(long startPrice, long endPrice, int subdevisions)
    {
        if(disabled) return 0f;
        float volumeSum = 0;
        long deltaPrice = endPrice - startPrice;
        if(subdevisions >= Math.abs(deltaPrice))
        {
            return getVolume(startPrice, endPrice);
        }

        long priceIncrement = deltaPrice / (subdevisions);
        for(int i = 0; i < subdevisions; i++)
        {
            volumeSum += getVolume(startPrice + i*priceIncrement)*priceIncrement;
        }

        return volumeSum;
    }
    public long getVolumeRounded(long startPrice, long endPrice)
    {
        if(disabled) return 0L;
        return dynamicArray.getSumRounded(startPrice, endPrice+1);
    }


    public float getCapital(long startPrice, long endPrice)
    {
        if(disabled) return 0f;
        return dynamicArray.getSumProduct(startPrice, endPrice+1);
    }
    public long getCapitalRounded(long startPrice, long endPrice)
    {
        if(disabled) return 0L;
        return dynamicArray.getSumProductRounded(startPrice, endPrice+1);
    }










    /**
     * Gets the default raw volume at a given price
     * @apiNote
     * @param price on which the volume needs to be calculatet at
     * @return positive volume vor buy order, negative volume for sell order
     */
    public float getDefaultVolume(long price)
    {
        // Sticky-clear short-circuit: the shift-fill code path uses this method to
        // populate newly-visible array slots when the array recenters on a price move.
        // Returning 0 here keeps the "cleared" state persistent across such moves.
        // Disabled also short-circuits — a disabled VO shift-fills as 0.
        if(cleared || disabled) return 0f;
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
        // See getDefaultVolume — the sticky-clear flag must also short-circuit the
        // rounded path so any consumer that goes through this variant stays at zero.
        // Disabled behaves the same: return 0 so shifts land at 0.
        if(cleared || disabled) return 0L;
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
        return DynamicIndexedArray.roundConservative(value);
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
