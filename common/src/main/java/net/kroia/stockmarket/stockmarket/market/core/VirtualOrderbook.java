package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.modutilities.NbtCompat;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
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

    /**
     * T-198: low-res memory of the depth outside the array window (thin where volume was consumed).
     * Read through {@link #getDefaultVolume(long)}; written when the window moves.
     */
    private final DepthMemory depthMemory = new DepthMemory();
    /** True while the array shifts on the memory path: the shift-fill is overwritten right after, so skip it. */
    private boolean suppressDefaults = false;
    /** Test hook: overrides the memory half-life setting (seconds; null = use the setting). */
    private Double halfLifeOverrideSeconds = null;

    /** Fallback of {@code MARKET.VIRTUAL_DEPTH_MAX_PRICE_MULTIPLIER} when the settings are not available. */
    public static final float DEFAULT_MAX_PRICE_MULTIPLIER = 100f;

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
        cachedSeg = null;
    }


    /**
     * Updates the market price and recenters the array window when the price leaves the
     * middle half of the window.
     * <p>
     * T-164: when the price jumps past the old window edge, the slots between the old edge
     * and the new price were swept by the jump. Their shift-fill values come from the
     * default provider, which at this point still sees the OLD market price and therefore
     * returns the opposite side's shape; {@link #getDefaultVolume(long)} then re-signs it for
     * the new side (e.g. old ask volume reappears as bid volume below the new price). The
     * swept range is zeroed after the shift so no phantom liquidity is left behind; the
     * volume distribution plugin refills it gradually.
     * Normal drift never triggers this: the window recenters while the price is still inside
     * it, so the swept range is empty.
     * @param currentMarketPrice new raw market price
     */
    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        updateDynamicArrayOffset(currentMarketPrice);
    }


    /**
     * Re-centres the array window on {@code newMarketPrice} when it left the middle half.
     * <p>
     * T-198 (memory on, see {@link #isDepthMemoryEnabled()}): before the shift the ticks that will
     * enter the window are read with the OLD geometry (exactly what {@link #findVolume} and the walk
     * saw), the ticks that leave it are aggregated into {@link DepthMemory} (fill ratio
     * |array| / |default|), and a range jumped over outside both windows is recorded as consumed.
     * After the shift the entering ticks get the values read before, so a remembered thin region
     * comes back thin. Memory off: the plain shift-fill from the default provider (pre-T-198).
     * The T-164 zeroing of the swept range inside the new window applies in both cases.
     *
     * @param newMarketPrice raw price the window should contain
     */
    public void updateDynamicArrayOffset(long newMarketPrice)
    {
        long oldOffset = dynamicArray.getIndexOffset();
        int size = dynamicArray.getSize();
        int sizeForth = size/4;
        if(newMarketPrice > oldOffset + sizeForth*3 || newMarketPrice < oldOffset + sizeForth)
        {
            long newOffset = newMarketPrice - size/2;
            if(isDepthMemoryEnabled() && hasDefaultVolume())
                shiftWithMemory(oldOffset, newOffset, size);
            else
                dynamicArray.setOffset(newOffset);
            if(newOffset > oldOffset)
            {
                // Up move: zero [max(oldEnd, newOffset), price)
                for(long p = Math.max(oldOffset + size, newOffset); p < newMarketPrice; p++)
                    dynamicArray.set(p, 0f);
            }
            else
            {
                // Down move: zero (price, min(oldOffset, newEnd))
                for(long p = newMarketPrice + 1; p < Math.min(oldOffset, newOffset + size); p++)
                    dynamicArray.set(p, 0f);
            }
        }
    }

    /**
     * Memory path of a window move (see {@link #updateDynamicArrayOffset}).
     * Old window {@code [oldOffset, oldOffset+size)}, new window {@code [newOffset, newOffset+size)};
     * leaving = old \ new, entering = new \ old, gap = between both windows (only on a jump).
     */
    private void shiftWithMemory(long oldOffset, long newOffset, int size)
    {
        long oldEnd = oldOffset + size - 1, newEnd = newOffset + size - 1;
        long leaveLo, leaveHi, enterLo, enterHi, gapLo, gapHi;
        boolean up = newOffset > oldOffset;   // up: enter from above, leave to below; down: the mirror
        if(up)
        {
            leaveLo = oldOffset;                        leaveHi = Math.min(oldEnd, newOffset - 1);
            enterLo = Math.max(newOffset, oldEnd + 1);  enterHi = newEnd;
            gapLo = oldEnd + 1;                         gapHi = newOffset - 1;
        }
        else
        {
            leaveLo = Math.max(oldOffset, newEnd + 1);  leaveHi = oldEnd;
            enterLo = newOffset;                        enterHi = Math.min(newEnd, oldOffset - 1);
            gapLo = newEnd + 1;                         gapHi = oldOffset - 1;
        }
        DepthMemory.MoveLedger ledger = new DepthMemory.MoveLedger();

        // 1. Entering ticks, read with the old geometry (window not moved yet)
        float[] entering = new float[(int)(enterHi - enterLo + 1)];
        DepthMemory.Segment seg = null;
        for(long p = enterLo; p <= enterHi; p++)
        {
            double ratio = 1;
            if(p >= 1 && !depthMemory.isEmpty())
            {
                if(seg == null || p < seg.lo || p > seg.hi)
                    seg = depthMemory.segmentAt(p, oldOffset, oldEnd, this::boundaryRatio);
                ratio = seg.cellRatio(p);
            }
            entering[(int)(p - enterLo)] = defaultVolume(p, ratio);
            if(p >= 1) ledger.enter(p, up, ratio);
        }
        // 2. Leaving ticks: fill ratio against the default at the current centre (no memory ratio)
        for(long p = Math.max(leaveLo, 1); p <= leaveHi; p++)
            ledger.leave(p, !up, Math.abs(dynamicArray.get(p)), Math.abs(defaultVolumeProvider.apply(p)));
        // 3. Range jumped over outside both windows: consumed
        if(gapLo <= gapHi)
            ledger.gap(gapLo, gapHi, up);
        depthMemory.applyMove(oldOffset, oldEnd, newOffset, newEnd, ledger);

        // 4. Shift without the (discarded) provider fill, then write the entering values
        suppressDefaults = true;
        try { dynamicArray.setOffset(newOffset); }
        finally { suppressDefaults = false; }
        dynamicArray.set(enterLo, entering);
    }

    /**
     * Fades the depth memory by one step of {@code dtSeconds} (deficit {@code 1 - r} halves every
     * {@code MARKET.VIRTUAL_DEPTH_RECOVERY_HALF_LIFE_MINUTES}). With the half-life 0 (memory off) the
     * memory is wiped. Called every server tick, also while the market is closed.
     *
     * @param dtSeconds elapsed time
     */
    public void fadeDepthMemory(double dtSeconds)
    {
        cachedSeg = null;   // the provider may have changed its shape since the last tick
        if(depthMemory.isEmpty()) return;
        double halfLife = depthMemoryHalfLifeSeconds();
        if(!(halfLife > 0))
            depthMemory.clear();
        else
            depthMemory.fade(ServerMarket.fade(1.0, halfLife, dtSeconds));
    }

    /** @return true if the depth memory is on (half-life &gt; 0) */
    public boolean isDepthMemoryEnabled()
    {
        return depthMemoryHalfLifeSeconds() > 0;
    }

    /** @return the depth memory (read access for tests and tools) */
    public DepthMemory getDepthMemory()
    {
        return depthMemory;
    }

    /**
     * Test hook: overrides the recovery half-life setting for this book only.
     * @param seconds half-life in seconds (0 = memory off), or null to use the setting again
     */
    public void test_setDepthMemoryHalfLifeSeconds(@Nullable Double seconds)
    {
        halfLifeOverrideSeconds = seconds;
    }

    /** @return the memory recovery half-life in seconds, 0 = off (also before the settings exist) */
    private double depthMemoryHalfLifeSeconds()
    {
        if(halfLifeOverrideSeconds != null) return halfLifeOverrideSeconds;
        if(BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null) return 0;
        Float minutes = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_DEPTH_RECOVERY_HALF_LIFE_MINUTES.get();
        return minutes == null ? 0 : minutes * 60.0;
    }

    /**
     * T-198 price ceiling: no default ask volume exists above {@code multiplier x centre price}
     * ({@code MARKET.VIRTUAL_DEPTH_MAX_PRICE_MULTIPLIER}). It is what ends a walk through an
     * otherwise endless provider-backed ask side.
     *
     * @return highest raw price that can hold default ask volume
     */
    public long getAskCeiling()
    {
        float mult = DEFAULT_MAX_PRICE_MULTIPLIER;
        if(BACKEND_INSTANCES != null && BACKEND_INSTANCES.SERVER_SETTINGS != null)
        {
            Float m = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_DEPTH_MAX_PRICE_MULTIPLIER.get();
            if(m != null && m >= 1) mult = m;
        }
        double ceiling = (double) mult * Math.max(currentMarketPrice, 1);
        return ceiling >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) ceiling;
    }

    /**
     * Ratio of a window boundary tick: |array| / |default| at the current centre, clamped to [0, 1]
     * (1 if the default is 0). Used as the profile edge where a memory segment touches the window.
     */
    private double boundaryRatio(long price)
    {
        float def = defaultVolumeProvider == null ? 0f : Math.abs(defaultVolumeProvider.apply(price));
        if(def <= 0) return 1;
        return Math.min(1, Math.abs(dynamicArray.get(price)) / def);
    }

    /**
     * @return the memory ratio at {@code price}: 1 inside the window, without memory or for prices
     *         below 1; otherwise the profile of the remembered slot
     */
    private double memoryRatio(long price)
    {
        if(depthMemory.isEmpty() || price < 1) return 1;
        long winStart = dynamicArray.getIndexOffset();
        long winEnd = winStart + dynamicArray.getSize() - 1;
        if(price >= winStart && price <= winEnd) return 1;
        if(!isDepthMemoryEnabled()) return 1;
        return segmentAt(price, winStart, winEnd).cellRatio(price);
    }

    // Last built memory segment and the inputs it depends on (window, centre, memory state and the
    // two boundary array values). Sequential reads (findVolume, getVolume over a range) hit it, so
    // a tick costs one profile evaluation instead of a segment build (log/pow + provider call).
    private DepthMemory.Segment cachedSeg;
    private long cachedWinStart, cachedWinEnd, cachedCentre;
    private int cachedMod;
    private float cachedBoundLo, cachedBoundHi;

    /** {@link DepthMemory#segmentAt} through a one-entry cache (see the fields above). */
    private DepthMemory.Segment segmentAt(long price, long winStart, long winEnd)
    {
        float boundLo = dynamicArray.get(winStart), boundHi = dynamicArray.get(winEnd);
        DepthMemory.Segment seg = cachedSeg;
        if(seg != null && price >= seg.lo && price <= seg.hi && winStart == cachedWinStart && winEnd == cachedWinEnd
                && currentMarketPrice == cachedCentre && depthMemory.modCount() == cachedMod
                && boundLo == cachedBoundLo && boundHi == cachedBoundHi)
            return seg;
        seg = depthMemory.segmentAt(price, winStart, winEnd, this::boundaryRatio);
        cachedSeg = seg;
        cachedWinStart = winStart;
        cachedWinEnd = winEnd;
        cachedCentre = currentMarketPrice;
        cachedMod = depthMemory.modCount();
        cachedBoundLo = boundLo;
        cachedBoundHi = boundHi;
        return seg;
    }

    /**
     * Shared default read (T-198): {@code provider(price) x ratio}, signed by the current price, 0
     * above the ask ceiling, when cleared/disabled or without provider.
     */
    private float defaultVolume(long price, double ratio)
    {
        if(cleared || disabled || defaultVolumeProvider == null) return 0f;
        if(price > currentMarketPrice && price > getAskCeiling()) return 0f;
        float result = (float)(defaultVolumeProvider.apply(price) * ratio);
        if(price > currentMarketPrice)
            return -Math.abs(result);
        else if(price < currentMarketPrice)
            return Math.abs(result);
        return result;
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
        depthMemory.clear();   // T-198: a reset starts from the full default everywhere
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
        depthMemory.clear();
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

    /** Returned by {@link #findVolume} when no level in the range holds volume of the asked side. */
    public static final long NO_VOLUME = Long.MIN_VALUE;

    /**
     * Exact search for the first price level, walking from {@code from} toward {@code to}
     * (both inclusive, upward if {@code to >= from}, else downward), whose virtual volume,
     * rounded like the matching walk reads it, lies on the requested side.
     * <p>Used by the matching walk (#88/#94) to skip empty levels in one step instead of
     * iterating raw ticks. Every level is checked, so the walk can never jump over volume:</p>
     * <ul>
     *   <li>Disabled: no level holds volume.</li>
     *   <li>Inside the array window the stored values are checked.</li>
     *   <li>Outside the window every read is the default volume (what the walk would see after
     *       re-centring the window there). Cleared or without a provider that is 0, so those
     *       stretches are skipped in O(1); with a provider each level is evaluated once.</li>
     * </ul>
     * There is no cheaper exact way for a provider-backed book: the provider is an arbitrary
     * plugin function, so only a provider that declared where its volume ends could be skipped
     * faster. T-198: a remembered empty memory segment (ratio 0) is skipped in one step, and an
     * upward search never passes {@code max(window end, }{@link #getAskCeiling()}{@code )}: there is
     * no default ask volume above the ceiling. A downward search ends at the caller's floor (&ge; 1),
     * so every search is finite.
     *
     * @param from    first raw price level to check
     * @param to      last raw price level to check
     * @param askSide true: look for sell-side (negative) volume, false: buy-side (positive) volume
     * @return the first matching level, or {@link #NO_VOLUME}
     */
    public long findVolume(long from, long to, boolean askSide)
    {
        if(disabled) return NO_VOLUME;
        int step = to >= from ? 1 : -1;
        long winStart = dynamicArray.getIndexOffset();
        long winEnd = winStart + dynamicArray.getSize() - 1;
        boolean defaults = hasDefaultVolume();
        if(step > 0 && defaults)
        {
            // T-198: no default asks above the ceiling, so an upward search ends there (or at the
            // window end). Downward searches end at the price floor 1 (bids below it read 0 anyway).
            long cap = Math.max(winEnd, getAskCeiling());
            if(to > cap)
            {
                to = cap;
                if(from > to) return NO_VOLUME;
            }
        }
        boolean memory = defaults && !depthMemory.isEmpty() && isDepthMemoryEnabled();
        for(long p = from; ; p += step)
        {
            if(!defaults && (p < winStart || p > winEnd))
            {
                // Outside the window without default volume: jump to the window or stop
                if(step > 0 && p < winStart && winStart <= to) p = winStart;
                else if(step < 0 && p > winEnd && winEnd >= to) p = winEnd;
                else return NO_VOLUME;
            }
            else if(memory && p >= 1 && (p < winStart || p > winEnd))
            {
                // T-198: a remembered empty segment (ratio 0) reads 0 at every tick: skip it in one step
                DepthMemory.Segment seg = segmentAt(p, winStart, winEnd);
                if(seg.isZero())
                {
                    long edge = step > 0 ? seg.hi : seg.lo;
                    if(step > 0 ? edge >= to : edge <= to) return NO_VOLUME;
                    p = edge;   // the loop step moves past the segment
                    continue;
                }
            }
            long v = dynamicArray.getRounded(p);
            if(askSide ? v < 0 : v > 0) return p;
            if(p == to) return NO_VOLUME;
        }
    }

    /**
     * @return true if levels outside the array window can hold volume (a default provider is
     *         set and the book is neither disabled nor cleared). Only then is a search with
     *         {@link #findVolume} linear in the distance; otherwise it is bounded by the window.
     */
    public boolean hasDefaultVolume()
    {
        return !disabled && !cleared && defaultVolumeProvider != null;
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
        // suppressDefaults: the memory path overwrites the shift-fill anyway (see shiftWithMemory).
        // T-198: the value is provider x memory ratio (1 inside the window / without memory),
        // and 0 for asks above the ceiling (getAskCeiling).
        if(cleared || disabled || suppressDefaults || defaultVolumeProvider == null) return 0f;
        if(price > currentMarketPrice && price > getAskCeiling()) return 0f;
        return defaultVolume(price, memoryRatio(price));
    }
    public long getDefaultVolumeRounded(long price)
    {
        // See getDefaultVolume — the sticky-clear flag must also short-circuit the
        // rounded path so any consumer that goes through this variant stays at zero.
        // Disabled behaves the same: return 0 so shifts land at 0.
        return roundConservative(getDefaultVolume(price));
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
        CompoundTag lowResTag = new CompoundTag();
        depthMemory.save(lowResTag);
        tag.put("lowRes", lowResTag);
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
            CompoundTag arrayTag = NbtCompat.getCompound(tag, "array");
            success &= dynamicArray.load(arrayTag);
        }
        // T-198: old saves have no "lowRes" -> empty memory
        if(tag.contains("lowRes"))
            depthMemory.load(NbtCompat.getCompound(tag, "lowRes"));
        else
            depthMemory.clear();
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
