package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.modutilities.NbtCompat;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongToDoubleFunction;

/**
 * T-198 / T-199: low-resolution "memory" of the virtual depth OUTSIDE the hi-res array window of a
 * {@link VirtualOrderbook}.
 * <p>
 * When the window moves, the ticks leaving it are aggregated into coarse slots, so depth that
 * was consumed stays thin after it left the window (and comes back thin when the price returns).
 * <ul>
 *   <li><b>Grid:</b> one fixed, global, log-spaced grid. Slot {@code k} covers the raw prices
 *       {@code [ceil(1.02^k), ceil(1.02^(k+1)))}, i.e. 2% of the price per slot (T-210, was 1%). The grid never
 *       re-centres. At very low raw prices several slots are empty; only non-empty slots
 *       (those returned by {@link #slotOf(long)}) are ever used, neighbours are found by price.</li>
 *   <li><b>Value (T-199):</b> the REAL summed absolute raw volume {@code V} of the part of slot
 *       {@code k} that lies outside the window (the "segment", length {@code S} from the geometry).
 *       No sign is stored: the caller signs it by the current price at read time. Stored sparse:
 *       a missing slot side means "read the default provider" (full default depth).</li>
 *   <li><b>Sides:</b> each slot keeps one value for its part BELOW the window and one for its part
 *       ABOVE it. Above 50x the window size a slot (2% of the price) is wider than the window
 *       and has outside ticks on both sides at once; one shared value would smear the consumed
 *       side's deficit onto the untouched side. Map key = {@code 2k + (above ? 1 : 0)}.</li>
 *   <li><b>Profile:</b> see {@link Segment}: density {@code rho = V / S} spread as two linear pieces
 *       whose cells sum to exactly {@code V}.</li>
 *   <li><b>Recovery:</b> {@code V} fades toward the slot's current default sum {@code D}
 *       ({@link #fade}); a slot side is dropped (= back to the provider) once
 *       {@code |V - D| < }{@link #DROP_TOLERANCE}{@code  * max(D, 1)}. No read cap: the stored volume
 *       is used as it is (it may be thicker than today's default until it faded).</li>
 * </ul>
 * Not thread-safe; used on the server thread like the rest of the orderbook.
 */
public final class DepthMemory
{
    /**
     * Growth factor between two slot starts (2% of the price per slot). Constant by design (T-198);
     * T-210 raised it from 1.01 to 1.02 (half as many slots). Saved with the memory ({@link #save}); a save
     * from another grid is dropped on load, because its slot indices mean other price ranges.
     */
    public static final double SLOT_GROWTH = 1.02;
    private static final double LN_GROWTH = Math.log(SLOT_GROWTH);
    /** A slot side whose volume is within this fraction of its default sum (at least 1 raw unit) is dropped. */
    public static final double DROP_TOLERANCE = 0.005;

    /**
     * The default depth the memory recovers toward, owned by the {@link VirtualOrderbook} (provider at the
     * current centre, floor 1, ask ceiling).
     */
    public interface DefaultDepth
    {
        /**
         * @param price raw price
         * @return |default raw volume| at {@code price}; 0 below 1 and above the ask ceiling
         */
        double at(long price);

        /**
         * @param lo first raw price (inclusive)
         * @param hi last raw price (inclusive)
         * @return sum of {@link #at} over {@code [lo, hi]} (may be a sampled approximation for long ranges)
         */
        double sum(long lo, long hi);
    }

    /** Sparse slot-side volumes, key {@link #key}; a missing key means "default provider". */
    private final HashMap<Integer, Float> volumes = new HashMap<>();
    /** Incremented on every change, so callers can cache {@link Segment}s. */
    private int modCount;

    // ── Grid ───────────────────────────────────────────────────────────────

    /**
     * @param k slot index (&ge; 0)
     * @return the first raw price of slot {@code k}: {@code ceil(SLOT_GROWTH^k)}
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

    /**
     * Segment geometry: the part of slot {@code k} below ({@code above = false}) or above the window.
     *
     * @return {@code {lo, hi}} (inclusive); empty if {@code hi < lo}
     */
    public static long[] segmentRange(int k, boolean above, long winStart, long winEnd)
    {
        long s = slotStart(k), e = slotEnd(k);
        return above ? new long[]{ Math.max(s, winEnd + 1), e } : new long[]{ s, Math.min(e, winStart - 1) };
    }

    /** @return number of raw prices of slot k below (above = false) or above the window [winStart, winEnd] */
    public static long outside(int k, boolean above, long winStart, long winEnd)
    {
        long[] r = segmentRange(k, above, winStart, winEnd);
        return Math.max(0, r[1] - r[0] + 1);
    }

    // ── Volumes ────────────────────────────────────────────────────────────

    /** @return true if no slot is remembered (every read uses the default provider) */
    public boolean isEmpty()
    {
        return volumes.isEmpty();
    }

    /** @return number of remembered slot sides */
    public int size()
    {
        return volumes.size();
    }

    /** Forgets every slot (all reads back to the default provider). */
    public void clear()
    {
        volumes.clear();
        modCount++;
    }

    /**
     * Replaces the slots with a copy of {@code other}'s (for scratch copies of an order book).
     * @param other memory to copy
     */
    public void copyFrom(DepthMemory other)
    {
        volumes.clear();
        volumes.putAll(other.volumes);
        modCount++;
    }

    /** @return a counter that changes whenever any volume changes (segment cache key) */
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
     * @return the remembered summed |raw volume| of that slot side, {@code NaN} if not remembered
     */
    public double getVolume(int k, boolean above)
    {
        Float v = volumes.get(key(k, above));
        return v == null ? Double.NaN : v;
    }

    /**
     * Stores the summed |raw volume| of one slot side as it is (no drop check; used by tests and NBT).
     *
     * @param k      slot index
     * @param above  true for the slot's part above the window, false for the part below it
     * @param volume summed absolute raw volume of that outside part, clamped to &ge; 0
     */
    public void setVolume(int k, boolean above, double volume)
    {
        modCount++;
        volumes.put(key(k, above), (float) Math.max(0, volume));
    }

    /**
     * T-210: the next remembered slot side above the window that holds volume.
     *
     * @param k slot index to search above (exclusive)
     * @return the smallest {@code k' > k} whose above-window side is remembered with a volume &gt; 0, or -1
     */
    public int nextStoredAbove(int k)
    {
        // ponytail: linear scan of the sparse map (a few hundred entries at most); a TreeMap if it grows
        int best = -1;
        for (var e : volumes.entrySet())
        {
            int key = e.getKey(), sk = key >> 1;
            if ((key & 1) == 1 && sk > k && e.getValue() > 0 && (best < 0 || sk < best)) best = sk;
        }
        return best;
    }

    /** Stores {@code v}, or removes the slot side if {@code v} is within {@link #DROP_TOLERANCE} of its default sum {@code d}. */
    private void putOrDrop(int key, double v, double d)
    {
        modCount++;
        if (Math.abs(v - d) < DROP_TOLERANCE * Math.max(d, 1)) volumes.remove(key);
        else volumes.put(key, (float) Math.max(0, v));
    }

    // ── Profile ────────────────────────────────────────────────────────────

    /**
     * The part of one slot that lies outside the window.
     * <p>
     * Not remembered ({@link #stored} false): the default provider applies, the profile is unused.
     * <p>
     * Remembered: a density profile in raw volume per tick, mean {@code rho = V / S}. Shape: two linear
     * pieces {@code eL -> m -> eR} over the continuous price coordinate {@code [lo, hi + 1)}, kink at
     * {@code c}. The edges are {@code eL = min(rho, rho_left)} and {@code eR = min(rho, rho_right)},
     * where a remembered same-side neighbour contributes its density and a missing neighbour the
     * |default| of its tick next to this segment, so adjacent segments meet at the same value; an edge
     * that touches the window uses the boundary tick's |array| instead (not capped), so the profile also
     * continues the array. With the kink in the middle, {@code m = 2 rho - (eL + eR) / 2} gives a mean of
     * exactly {@code rho} (each piece's mean is the average of its ends:
     * {@code ((eL + m) / 2 + (m + eR) / 2) / 2 = rho}).
     * {@code m} can only become negative when a window-boundary edge is large (the other edge is
     * {@code <= rho}); then {@code m = 0} and the kink moves to {@code x = (eR - 2 rho) / (eR - eL)} of the
     * length, which keeps the mean at {@code rho} ({@code (eL x + eR (1 - x)) / 2 = rho}) and the profile
     * non-negative. Each tick's value is the exact integral of the profile over its cell
     * {@code [p, p + 1)}, so the segment's tick values sum to exactly {@code V = rho * S}.
     * If {@code V = 0} every tick reads 0 ({@link #isZero()}).
     */
    public static final class Segment
    {
        /** Slot index. */
        public final int k;
        /** First and last raw price of the segment (inclusive). */
        public final long lo, hi;
        /** True if the slot side is remembered; false = read the default provider. */
        public final boolean stored;
        /** Mean density (raw volume per tick) of a remembered segment, {@code V / S}. */
        public final double rho;
        /** Edge densities and kink value. */
        public final double eL, m, eR;
        /** Kink position, relative to {@code lo} (0 .. hi - lo + 1). */
        private final double c;
        private final double len;

        /** A segment without memory (the default provider applies). */
        Segment(int k, long lo, long hi)
        {
            this.k = k;
            this.lo = lo;
            this.hi = hi;
            this.stored = false;
            this.rho = this.eL = this.m = this.eR = Double.NaN;
            this.len = hi - lo + 1;
            this.c = len / 2;
        }

        Segment(int k, long lo, long hi, double rho, double eL, double eR)
        {
            this.k = k;
            this.lo = lo;
            this.hi = hi;
            this.stored = true;
            this.rho = rho;
            this.eL = eL;
            this.eR = eR;
            this.len = hi - lo + 1;
            double mid = 2 * rho - (eL + eR) / 2;
            if (mid >= 0)
            {
                this.m = mid;
                this.c = len / 2;
            }
            else
            {
                // Large boundary edge: hold the mean with m = 0 and a shifted kink (see class doc)
                this.m = 0;
                double x = eR == eL ? 0.5 : (eR - 2 * rho) / (eR - eL);
                this.c = len * Math.max(0, Math.min(1, x));
            }
        }

        /** @return true if every tick of the segment reads 0, so a search may skip it in one step */
        public boolean isZero()
        {
            return stored && rho <= 0;
        }

        /** @return profile value at the relative continuous coordinate {@code x} (0 .. len) */
        private double valueAt(double x)
        {
            if (x <= c)
                return c <= 0 ? m : eL + (m - eL) * (x / c);
            return c >= len ? m : m + (eR - m) * ((x - c) / (len - c));
        }

        /**
         * @param price raw price inside {@code [lo, hi]} of a {@link #stored} segment
         * @return the |raw volume| of that tick: the profile integrated over the tick's cell
         */
        public double cellValue(long price)
        {
            if (!(rho > 0)) return 0;
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
     * @param boundaryValue |array| of a window boundary tick, only called when the segment touches the window
     * @param def           default depth, for the edge next to a missing neighbour
     * @return the segment ({@link Segment#stored} false if the slot side is not remembered)
     */
    public Segment segmentAt(long price, long winStart, long winEnd, LongToDoubleFunction boundaryValue, DefaultDepth def)
    {
        int k = slotOf(price);
        boolean above = price > winEnd;
        long[] r = segmentRange(k, above, winStart, winEnd);
        long lo = r[0], hi = r[1];
        Float v = volumes.get(key(k, above));
        if (v == null) return new Segment(k, lo, hi);
        double rho = v / (double) (hi - lo + 1);
        double eL = lo == winEnd + 1 ? boundaryEdge(rho, boundaryValue.applyAsDouble(winEnd))
                                     : neighbourEdge(rho, lo - 1, above, winStart, winEnd, def);
        double eR = hi == winStart - 1 ? boundaryEdge(rho, boundaryValue.applyAsDouble(winStart))
                                       : neighbourEdge(rho, hi + 1, above, winStart, winEnd, def);
        return new Segment(k, lo, hi, rho, eL, eR);
    }

    /** Window edge: the boundary tick's |array| (&ge; 0, not capped). */
    private static double boundaryEdge(double rho, double value)
    {
        return Double.isNaN(value) ? rho : Math.max(0, value);
    }

    /**
     * Edge toward the neighbour tick {@code adj} on the same side of the window: {@code min(rho, rho_n)}
     * for a remembered neighbour, {@code min(rho, |default(adj)|)} for a missing one, {@code rho} below price 1.
     */
    private double neighbourEdge(double rho, long adj, boolean above, long winStart, long winEnd, DefaultDepth def)
    {
        if (adj < 1) return rho;
        int nk = slotOf(adj);
        Float nv = volumes.get(key(nk, above));
        if (nv == null) return Math.min(rho, def.at(adj));
        long ns = outside(nk, above, winStart, winEnd);
        return ns > 0 ? Math.min(rho, nv / (double) ns) : rho;
    }

    // ── Window moves ───────────────────────────────────────────────────────

    /**
     * Per-slot input of one window move, collected by the caller (who owns the array and the
     * default provider). Use {@link #leave}, {@link #enter} and {@link #gap}, then
     * {@link DepthMemory#applyMove}.
     */
    public static final class MoveLedger
    {
        /** Per slot side ({@link DepthMemory#key}): { L = sum |array| of leaving ticks, leaving ticks,
         *  E = sum of entering tick values, g = gap ticks taken from this side, 1 if gap target }. */
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
         */
        public void leave(long price, boolean above, double absArr)
        {
            double[] d = of(slotOf(price), above);
            d[0] += absArr;
            d[1] += 1;
        }

        /**
         * A tick enters the window; it carries away its value (read with the old geometry).
         * @param price raw price (&ge; 1)
         * @param above side it comes from: true on an up move (it was above the window)
         * @param value the tick's |raw volume| as the memory held it (profile cell or |default|)
         */
        public void enter(long price, boolean above, double value)
        {
            of(slotOf(price), above)[2] += value;
        }

        /**
         * A price range outside both the old and new window was jumped over; it is recorded as
         * consumed (volume 0). It moves from the side it was on ({@code fromAbove}) to the other one:
         * the source side loses it at its average density, the target side gains it with volume 0.
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
                of(k, fromAbove)[3] += end - p + 1;
                of(k, !fromAbove)[4] = 1;   // target side: materialise, its S grows by the gap at volume 0
                p = end + 1;
            }
        }
    }

    /**
     * Updates the slots for one window move from {@code [oldStart, oldEnd]} to
     * {@code [newStart, newEnd]}.
     * <p>
     * Per touched slot side, with {@code S_old} / {@code S_new} = ticks of that side outside the old / new
     * window and {@code G} the matching segment:
     * <pre>
     *   base = stored ? V : (S_old &gt; 0 ? D(G_old) : 0)      D = default sum ({@link DefaultDepth#sum})
     *   V'   = max(0, base * (1 - g / S_old) - E + L)
     * </pre>
     * {@code E} = values of the ticks that entered the window (exact old profile), {@code L} = |array| of
     * the ticks that left into this side, {@code g} = gap ticks taken from this side (the target side gains
     * them at volume 0). A missing side stays missing if only {@code E} and/or a source gap touched it (the
     * rest still reads the default); it materialises when ticks leave into it or it is a gap target.
     * {@code S_new = 0} removes the side; {@code V'} within {@link #DROP_TOLERANCE} of {@code D(G_new)} drops it.
     *
     * @param oldStart first price of the old window
     * @param oldEnd   last price of the old window
     * @param newStart first price of the new window
     * @param newEnd   last price of the new window
     * @param ledger   collected tick data
     * @param def      default depth at the current centre
     */
    public void applyMove(long oldStart, long oldEnd, long newStart, long newEnd, MoveLedger ledger, DefaultDepth def)
    {
        for (Map.Entry<Integer, double[]> e : ledger.slots.entrySet())
        {
            int k = e.getKey() >> 1;
            boolean above = (e.getKey() & 1) != 0;
            double[] d = e.getValue();
            long sOld = outside(k, above, oldStart, oldEnd);
            long sNew = outside(k, above, newStart, newEnd);
            Float stored = volumes.get(e.getKey());
            if (sNew <= 0)
            {
                modCount++;
                volumes.remove(e.getKey());
                continue;
            }
            if (stored == null && d[1] == 0 && d[4] == 0)
                continue;   // only entering ticks / source gap: the remaining part still reads the default
            double base;
            if (stored != null) base = stored;
            else if (sOld > 0)
            {
                long[] g = segmentRange(k, above, oldStart, oldEnd);
                base = def.sum(g[0], g[1]);
            }
            else base = 0;
            double kept = sOld > 0 ? base * (1 - d[3] / sOld) : 0;
            double v = Math.max(0, kept - d[2] + d[0]);
            long[] g = segmentRange(k, above, newStart, newEnd);
            putOrDrop(e.getKey(), v, def.sum(g[0], g[1]));
        }
    }

    // ── Recovery ───────────────────────────────────────────────────────────

    /**
     * Fades every remembered volume toward the slot side's current default sum
     * {@code D = D(G)} (segment at the current window and centre): {@code V <- D + (V - D) * factor}.
     * Sides within {@link #DROP_TOLERANCE} of {@code D} (or without outside ticks) are removed.
     * Cost: one {@link DefaultDepth#sum} per remembered side.
     *
     * @param factor   multiplier for {@code V - D}, in [0, 1] (e.g. {@code 2^(-dt / halfLife)})
     * @param winStart first raw price of the array window
     * @param winEnd   last raw price of the array window
     * @param def      default depth at the current centre
     */
    public void fade(double factor, long winStart, long winEnd, DefaultDepth def)
    {
        modCount++;
        volumes.entrySet().removeIf(en -> {
            int k = en.getKey() >> 1;
            boolean above = (en.getKey() & 1) != 0;
            long[] g = segmentRange(k, above, winStart, winEnd);
            if (g[1] < g[0]) return true;
            double d = def.sum(g[0], g[1]);
            double v = d + (en.getValue() - d) * factor;
            if (Math.abs(v - d) < DROP_TOLERANCE * Math.max(d, 1)) return true;
            en.setValue((float) Math.max(0, v));
            return false;
        });
    }

    // ── Persistence ────────────────────────────────────────────────────────

    /**
     * Writes the slots as two int arrays: "k" (slot-side keys {@code 2k + above}) and "v" (float bits of
     * the summed |raw volume|).
     * @param tag target tag
     */
    public void save(CompoundTag tag)
    {
        int[] ks = new int[volumes.size()];
        int[] vs = new int[volumes.size()];
        int i = 0;
        for (Map.Entry<Integer, Float> e : volumes.entrySet())
        {
            ks[i] = e.getKey();
            vs[i++] = Float.floatToIntBits(e.getValue());
        }
        tag.putIntArray("k", ks);
        tag.putIntArray("v", vs);
        tag.putDouble("g", SLOT_GROWTH);   // T-210: grid of the keys, checked on load
    }

    /**
     * Replaces the slots with the ones in {@code tag} (see {@link #save}). A tag without "v" (no memory,
     * or the T-198 ratio format "r", which is dropped without conversion) leaves the memory empty.
     * T-210: so does a save from another grid ("g" missing, i.e. the 1% grid before T-210, or not
     * {@link #SLOT_GROWTH}): its keys are slot indices of other price ranges. Every slot then reads the
     * default again, so depth consumed outside the window before the upgrade is restored once.
     * @param tag source tag
     */
    public void load(CompoundTag tag)
    {
        clear();
        if (!tag.contains("g") || NbtCompat.getDouble(tag, "g") != SLOT_GROWTH) return;
        int[] ks = NbtCompat.getIntArray(tag, "k");
        int[] vs = NbtCompat.getIntArray(tag, "v");
        for (int i = 0; i < Math.min(ks.length, vs.length); i++)
            setVolume(ks[i] >> 1, (ks[i] & 1) != 0, Float.intBitsToFloat(vs[i]));
    }
}
