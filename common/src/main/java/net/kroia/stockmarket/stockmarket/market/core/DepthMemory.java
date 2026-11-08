package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.modutilities.NbtCompat;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongToDoubleFunction;

/**
 * T-198: low-resolution "memory" of the virtual depth OUTSIDE the hi-res array window of a
 * {@link VirtualOrderbook}.
 * <p>
 * When the window moves, the ticks leaving it are aggregated into coarse slots, so depth that
 * was consumed stays thin after it left the window (and comes back thin when the price returns).
 * <ul>
 *   <li><b>Grid:</b> one fixed, global, log-spaced grid. Slot {@code k} covers the raw prices
 *       {@code [ceil(1.01^k), ceil(1.01^(k+1)))}, i.e. 1% of the price per slot. The grid never
 *       re-centres. At very low raw prices several slots are empty; only non-empty slots
 *       (those returned by {@link #slotOf(long)}) are ever used, neighbours are found by price.</li>
 *   <li><b>Value:</b> a fill RATIO {@code r_k} in [0, 1] per slot = remaining / default volume,
 *       side-less. The volume read at a tick is {@code default(p, current centre) * ratio(p)};
 *       the caller applies the sign. Stored sparse: a missing slot means ratio 1 (full default).</li>
 *   <li><b>Meaning:</b> {@code r_k} is the MEAN ratio over the part of slot {@code k} that lies
 *       outside the window (the "segment"). Inside the window the array is the only truth.</li>
 *   <li><b>Sides:</b> each slot keeps one ratio for its part BELOW the window and one for its part
 *       ABOVE it. Above roughly 50x the window size a slot (1% of the price) is wider than the window
 *       and has outside ticks on both sides at once; one shared ratio would smear the consumed
 *       side's deficit onto the untouched side (a walk through a wide slot would read the asks
 *       ahead of it thinner). Map key = {@code 2k + (above ? 1 : 0)}.</li>
 *   <li><b>Profile:</b> see {@link Segment}: two linear pieces whose mean is exactly {@code r_k}.</li>
 *   <li><b>Recovery:</b> the deficit {@code d = 1 - r} fades exponentially ({@link #fade(double)});
 *       a slot is dropped (= back to full default) once {@code d < }{@link #DROP_DEFICIT}.</li>
 * </ul>
 * Not thread-safe; used on the server thread like the rest of the orderbook.
 */
public final class DepthMemory
{
    /** Growth factor between two slot starts (1% of the price per slot). Constant by design (T-198). */
    public static final double SLOT_GROWTH = 1.01;
    private static final double LN_GROWTH = Math.log(SLOT_GROWTH);
    /** A slot whose deficit {@code 1 - r} falls below this value is dropped (treated as full default). */
    public static final double DROP_DEFICIT = 0.005;

    /** Sparse slot-side ratios, key {@link #key}; a missing key means ratio 1. */
    private final HashMap<Integer, Float> ratios = new HashMap<>();
    /** Incremented on every change, so callers can cache {@link Segment}s. */
    private int modCount;

    // ── Grid ───────────────────────────────────────────────────────────────

    /**
     * @param k slot index (&ge; 0)
     * @return the first raw price of slot {@code k}: {@code ceil(1.01^k)}
     */
    public static long slotStart(int k)
    {
        return (long) Math.ceil(Math.pow(SLOT_GROWTH, k));
    }

    /**
     * @param price raw price, must be &ge; 1
     * @return the (non-empty) slot that contains {@code price}: the largest {@code k} with
     *         {@link #slotStart(int) slotStart(k)} &le; price
     */
    public static int slotOf(long price)
    {
        int k = (int) Math.floor(Math.log(price) / LN_GROWTH);
        // The log estimate can be off by one through floating point error; fix it with the exact bounds.
        while (slotStart(k + 1) <= price) k++;
        while (k > 0 && slotStart(k) > price) k--;
        return k;
    }

    /**
     * @param k a non-empty slot
     * @return the last raw price of slot {@code k} (inclusive)
     */
    public static long slotEnd(int k)
    {
        return slotStart(k + 1) - 1;
    }

    // ── Ratios ─────────────────────────────────────────────────────────────

    /** @return true if no slot is remembered (every ratio is 1) */
    public boolean isEmpty()
    {
        return ratios.isEmpty();
    }

    /** @return number of remembered slots */
    public int size()
    {
        return ratios.size();
    }

    /** Forgets every slot (all ratios back to 1). */
    public void clear()
    {
        ratios.clear();
        modCount++;
    }

    /** @return a counter that changes whenever any ratio changes (segment cache key) */
    public int modCount()
    {
        return modCount;
    }

    /** @return map key of one side of slot {@code k} */
    private static int key(int k, boolean above)
    {
        return 2 * k + (above ? 1 : 0);
    }

    /**
     * @param k     slot index
     * @param above true for the slot's part above the window, false for the part below it
     * @return the remembered ratio of that slot side, 1 if not remembered
     */
    public float getRatio(int k, boolean above)
    {
        Float r = ratios.get(key(k, above));
        return r == null ? 1f : r;
    }

    /**
     * Sets the ratio of one slot side (used by tests and by {@link #applyMove}). The value is
     * clamped to [0, 1]; a side with a deficit below {@link #DROP_DEFICIT} is removed.
     *
     * @param k     slot index
     * @param above true for the slot's part above the window, false for the part below it
     * @param ratio remaining / default volume of that outside part
     */
    public void setRatio(int k, boolean above, double ratio)
    {
        modCount++;
        double r = Math.max(0, Math.min(1, ratio));
        if (1 - r < DROP_DEFICIT) ratios.remove(key(k, above));
        else ratios.put(key(k, above), (float) r);
    }

    /** @return ratio of the given side of the slot that contains {@code price}, 1 for prices below 1 */
    private float ratioOfSlotAt(long price, boolean above)
    {
        return price < 1 ? 1f : getRatio(slotOf(price), above);
    }

    // ── Profile ────────────────────────────────────────────────────────────

    /**
     * The part of one slot that lies outside the window, with its ratio profile.
     * <p>
     * Shape: two linear pieces {@code eL -> m -> eR} over the continuous price coordinate
     * {@code [lo, hi + 1)}, kink at {@code c}. The edges are {@code eL = min(r_{k-1}, r_k)} and
     * {@code eR = min(r_k, r_{k+1})} (missing neighbour = 1), so adjacent slots meet at the same
     * value; an edge that touches the window uses the boundary tick's ratio instead, so the profile
     * also continues the array. With the kink in the middle, {@code m = 2 r - (eL + eR) / 2} gives a
     * mean of exactly {@code r} (each piece's mean is the average of its ends:
     * {@code ((eL + m) / 2 + (m + eR) / 2) / 2 = r}) and {@code m <= 2r}.
     * {@code m} can only become negative when a window-boundary edge is large (the other edge is
     * {@code <= r}); then {@code m = 0} and the kink moves to {@code x = (eR - 2r) / (eR - eL)} of the
     * length, which keeps the mean at {@code r} ({@code (eL x + eR (1 - x)) / 2 = r}) and the profile
     * non-negative. Each tick's value is the exact integral of the profile over its cell
     * {@code [p, p + 1)}, so the segment's tick values average to exactly {@code r}.
     * If {@code r = 0} every tick reads 0 ({@link #isZero()}).
     */
    public static final class Segment
    {
        /** Slot index. */
        public final int k;
        /** First and last raw price of the segment (inclusive). */
        public final long lo, hi;
        /** Mean ratio of the segment. */
        public final double r;
        /** Edge ratios and kink value. */
        public final double eL, m, eR;
        /** Kink position, relative to {@code lo} (0 .. hi - lo + 1). */
        private final double c;
        private final double len;

        Segment(int k, long lo, long hi, double r, double eL, double eR)
        {
            this.k = k;
            this.lo = lo;
            this.hi = hi;
            this.r = r;
            this.eL = eL;
            this.eR = eR;
            this.len = hi - lo + 1;
            double mid = 2 * r - (eL + eR) / 2;
            if (mid >= 0)
            {
                this.m = mid;
                this.c = len / 2;
            }
            else
            {
                // Large boundary edge: hold the mean with m = 0 and a shifted kink (see class doc)
                this.m = 0;
                double x = eR == eL ? 0.5 : (eR - 2 * r) / (eR - eL);
                this.c = len * Math.max(0, Math.min(1, x));
            }
        }

        /** @return true if every tick of the segment reads 0, so a search may skip it in one step */
        public boolean isZero()
        {
            return r <= 0;
        }

        /** @return profile value at the relative continuous coordinate {@code x} (0 .. len) */
        private double valueAt(double x)
        {
            if (x <= c)
                return c <= 0 ? m : eL + (m - eL) * (x / c);
            return c >= len ? m : m + (eR - m) * ((x - c) / (len - c));
        }

        /**
         * @param price raw price inside {@code [lo, hi]}
         * @return the ratio of that tick: the profile integrated over the tick's cell
         */
        public double cellRatio(long price)
        {
            if (r <= 0) return 0;
            double a = price - lo, b = a + 1;
            double v;
            if (b <= c || a >= c)
                v = (valueAt(a) + valueAt(b)) / 2;
            else  // the kink lies inside this cell: integrate both parts
                v = ((c - a) * (valueAt(a) + m) + (b - c) * (m + valueAt(b))) / 2;
            return Math.max(0, v);
        }
    }

    /**
     * Builds the segment that contains {@code price}.
     *
     * @param price         raw price outside the window, &ge; 1
     * @param winStart      first raw price of the array window
     * @param winEnd        last raw price of the array window
     * @param boundaryRatio ratio of a window boundary tick (|array| / |default|), only called when
     *                      the segment touches the window
     * @return the segment
     */
    public Segment segmentAt(long price, long winStart, long winEnd, LongToDoubleFunction boundaryRatio)
    {
        int k = slotOf(price);
        long s = slotStart(k), e = slotEnd(k);
        boolean above = price > winEnd;
        double r = getRatio(k, above);
        long lo, hi;
        double eL, eR;
        // Neighbour edges use the same side: a neighbour slot on this side of the window lies
        // entirely on this side, so its ratio for this side is the one that applies.
        if (!above)
        {
            lo = s;
            hi = Math.min(e, winStart - 1);
            eL = Math.min(r, ratioOfSlotAt(s - 1, false));
            eR = hi == winStart - 1 ? clamp01(boundaryRatio.applyAsDouble(winStart)) : Math.min(r, ratioOfSlotAt(e + 1, false));
        }
        else
        {
            lo = Math.max(s, winEnd + 1);
            hi = e;
            eL = lo == winEnd + 1 ? clamp01(boundaryRatio.applyAsDouble(winEnd)) : Math.min(r, ratioOfSlotAt(s - 1, true));
            eR = Math.min(r, ratioOfSlotAt(e + 1, true));
        }
        return new Segment(k, lo, hi, r, eL, eR);
    }

    private static double clamp01(double v)
    {
        return Double.isNaN(v) ? 1 : Math.max(0, Math.min(1, v));
    }

    // ── Window moves ───────────────────────────────────────────────────────

    /**
     * Per-slot input of one window move, collected by the caller (who owns the array and the
     * default provider). Use {@link #leave}, {@link #enter} and {@link #gap}, then
     * {@link DepthMemory#applyMove}.
     */
    public static final class MoveLedger
    {
        /** Per slot side ({@link DepthMemory#key}): { sum |array| of leaving ticks, sum |default| of
         *  leaving ticks, leaving ticks, sum of entering tick ratios, gap ticks taken from this side }. */
        private final HashMap<Integer, double[]> slots = new HashMap<>();

        private double[] of(int k, boolean above)
        {
            return slots.computeIfAbsent(key(k, above), x -> new double[5]);
        }

        /**
         * A tick leaves the window.
         * @param price  raw price (&ge; 1)
         * @param above  side it leaves to: true on a down move (it is now above the window)
         * @param absArr |array value| of the tick
         * @param absDef |default volume| of the tick at the current centre (no ratio)
         */
        public void leave(long price, boolean above, double absArr, double absDef)
        {
            double[] d = of(slotOf(price), above);
            d[0] += absArr;
            d[1] += absDef;
            d[2] += 1;
        }

        /**
         * A tick enters the window; it carries away its profile ratio (read with the old geometry).
         * @param price raw price (&ge; 1)
         * @param above side it comes from: true on an up move (it was above the window)
         * @param ratio the tick's ratio
         */
        public void enter(long price, boolean above, double ratio)
        {
            of(slotOf(price), above)[3] += ratio;
        }

        /**
         * A price range outside both the old and new window was jumped over; it is recorded as
         * consumed (ratio 0). It moves from the side it was on ({@code fromAbove}) to the other one:
         * the source side loses it at its average ratio, the target side gains it with ratio 0.
         * O(slots in range).
         * @param from      first raw price (inclusive)
         * @param to        last raw price (inclusive)
         * @param fromAbove true for an up jump (the range was above the old window)
         */
        public void gap(long from, long to, boolean fromAbove)
        {
            from = Math.max(from, 1);
            for (long p = from; p <= to; )
            {
                int k = slotOf(p);
                long end = Math.min(slotEnd(k), to);
                of(k, fromAbove)[4] += end - p + 1;
                of(k, !fromAbove);   // the target side must be re-normalised: its S grows by the gap
                p = end + 1;
            }
        }
    }

    /** @return number of raw prices of slot k below (above = false) or above the window [winStart, winEnd] */
    private static long outside(int k, boolean above, long winStart, long winEnd)
    {
        long s = slotStart(k), e = slotEnd(k);
        return above ? Math.max(0, e - Math.max(s, winEnd + 1) + 1)
                     : Math.max(0, Math.min(e, winStart - 1) - s + 1);
    }

    /**
     * Updates the slots for one window move from {@code [oldStart, oldEnd]} to
     * {@code [newStart, newEnd]}.
     * <p>
     * Per slot side, with {@code S_old} / {@code S_new} = ticks of that side outside the old / new window:
     * <pre>
     *   mass  = r * (S_old - gap)           old outside part, minus the gap that moved to the other side
     *         - sum(entering ratios)        ticks that moved into the array (exact, old profile)
     *         + q * leaving                 ticks that left the array, q = sum|array| / sum|default|
     *   r'    = clamp(mass / S_new, 0, 1)   (side removed if S_new = 0 or 1 - r' &lt; DROP_DEFICIT)
     * </pre>
     * Without gap and entering ticks this is the spec's {@code (r S + q n) / (S + n)}. Leaving ticks
     * whose default is 0 count with q (all-zero defaults: q = 1, nothing to remember).
     *
     * @param oldStart first price of the old window
     * @param oldEnd   last price of the old window
     * @param newStart first price of the new window
     * @param newEnd   last price of the new window
     * @param ledger   collected tick data
     */
    public void applyMove(long oldStart, long oldEnd, long newStart, long newEnd, MoveLedger ledger)
    {
        for (Map.Entry<Integer, double[]> e : ledger.slots.entrySet())
        {
            int k = e.getKey() >> 1;
            boolean above = (e.getKey() & 1) != 0;
            double[] d = e.getValue();
            long sOld = outside(k, above, oldStart, oldEnd);
            long sNew = outside(k, above, newStart, newEnd);
            if (sNew <= 0)
            {
                modCount++;
                ratios.remove(e.getKey());
                continue;
            }
            double q = d[1] > 0 ? Math.min(1, d[0] / d[1]) : 1;
            double mass = getRatio(k, above) * (sOld - d[4]) - d[3] + q * d[2];
            setRatio(k, above, mass / sNew);
        }
    }

    // ── Recovery ───────────────────────────────────────────────────────────

    /**
     * Fades every deficit {@code d = 1 - r} by {@code factor} (e.g. {@code 2^(-dt / halfLife)});
     * slots whose deficit drops below {@link #DROP_DEFICIT} are removed.
     *
     * @param factor multiplier for the deficit, in [0, 1]
     */
    public void fade(double factor)
    {
        modCount++;
        ratios.entrySet().removeIf(en -> {
            double d = (1 - en.getValue()) * factor;
            if (d < DROP_DEFICIT) return true;
            en.setValue((float) (1 - d));
            return false;
        });
    }

    // ── Persistence ────────────────────────────────────────────────────────

    /**
     * Writes the slots as two int arrays: "k" (slot-side keys {@code 2k + above}) and "r" (float bits of the ratios).
     * @param tag target tag
     */
    public void save(CompoundTag tag)
    {
        int[] ks = new int[ratios.size()];
        int[] rs = new int[ratios.size()];
        int i = 0;
        for (Map.Entry<Integer, Float> e : ratios.entrySet())
        {
            ks[i] = e.getKey();
            rs[i++] = Float.floatToIntBits(e.getValue());
        }
        tag.putIntArray("k", ks);
        tag.putIntArray("r", rs);
    }

    /**
     * Replaces the slots with the ones in {@code tag} (see {@link #save}). A tag without data
     * (old saves) leaves the memory empty.
     * @param tag source tag
     */
    public void load(CompoundTag tag)
    {
        clear();
        int[] ks = NbtCompat.getIntArray(tag, "k");
        int[] rs = NbtCompat.getIntArray(tag, "r");
        for (int i = 0; i < Math.min(ks.length, rs.length); i++)
            setRatio(ks[i] >> 1, (ks[i] & 1) != 0, Float.intBitsToFloat(rs[i]));
    }
}
