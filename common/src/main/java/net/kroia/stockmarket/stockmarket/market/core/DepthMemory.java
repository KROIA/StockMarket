package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.modutilities.NbtCompat;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
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
 *       No sign is stored: the caller signs it by the current price at read time.</li>
 *   <li><b>Dense (T-211):</b> once initialised ({@link #init}) every slot side from the floor to the ask
 *       ceiling holds a stored volume, and only stored volume is read: the default provider is never
 *       read as volume, it is only the target the volumes recover toward. Slots above the ceiling at
 *       init time start at 0 and recover like any other. Before the init (memory just cleared, or a
 *       sparse T-210 save) a side may be "pending" ({@code NaN}): it still reads the provider until
 *       {@link #init} fills it once.</li>
 *   <li><b>Sides:</b> each slot keeps one value for its part BELOW the window and one for its part
 *       ABOVE it. Above 50x the window size a slot (2% of the price) is wider than the window
 *       and has outside ticks on both sides at once; one shared value would smear the consumed
 *       side's deficit onto the untouched side. Map key = {@code 2k + (above ? 1 : 0)}.</li>
 *   <li><b>Profile:</b> see {@link Segment}: density {@code rho = V / S} spread as two linear pieces
 *       whose cells sum to exactly {@code V}.</li>
 *   <li><b>Recovery:</b> {@code V} fades toward the slot's default sum {@code D} ({@link #fade}), with
 *       {@code D} taken from a cached table that is refreshed when the centre moved (see {@link #fade}).
 *       No slot is ever dropped. No read cap: the stored volume is used as it is.</li>
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

    /**
     * T-211: dense slot-side volumes, index = {@link #key}. {@code null} = nothing stored (every side
     * pending). {@code NaN} = pending side (reads the provider until {@link #init}). Indices past the end
     * read 0 once {@link #initialised}, else pending.
     */
    private float[] volumes = null;
    /** True after {@link #init}: sides never written read 0 (stored, empty) instead of pending. */
    private boolean initialised = false;
    /** T-211: cached full-slot default sums per key (recovery targets), NaN = not cached. See {@link #fade}. */
    private double[] targets = null;
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

    /** @return true if nothing is stored (every side is pending and reads the default provider) */
    public boolean isEmpty()
    {
        return volumes == null;
    }

    /** @return true once {@link #init} ran: no pending side is left in the initialised range */
    public boolean isInitialised()
    {
        return initialised;
    }

    /** @return number of stored (non-pending) slot sides */
    public int size()
    {
        if (volumes == null) return 0;
        int n = 0;
        for (float v : volumes) if (!Float.isNaN(v)) n++;
        return n;
    }

    /** Forgets every slot (all sides pending: reads go back to the default provider until {@link #init}). */
    public void clear()
    {
        volumes = null;
        targets = null;
        initialised = false;
        modCount++;
    }

    /**
     * Replaces the slots with a copy of {@code other}'s (for scratch copies of an order book).
     * @param other memory to copy
     */
    public void copyFrom(DepthMemory other)
    {
        volumes = other.volumes == null ? null : other.volumes.clone();
        targets = other.targets == null ? null : other.targets.clone();
        initialised = other.initialised;
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

    /** @return the stored value of a key: NaN if pending, 0 past the end once initialised */
    private float raw(int key)
    {
        if (volumes == null || key >= volumes.length) return initialised ? 0f : Float.NaN;
        return volumes[key];
    }

    /** Grows {@link #volumes} to hold {@code key}; new entries are 0 once initialised, else pending. */
    private void ensure(int key)
    {
        if (volumes != null && key < volumes.length) return;
        int old = volumes == null ? 0 : volumes.length;
        int len = Math.max(key + 1, old * 3 / 2 + 2);
        volumes = volumes == null ? new float[len] : Arrays.copyOf(volumes, len);
        Arrays.fill(volumes, old, len, initialised ? 0f : Float.NaN);
    }

    /** Stores {@code v} (clamped to &ge; 0) under {@code key}. */
    private void put(int key, double v)
    {
        ensure(key);
        volumes[key] = (float) Math.max(0, v);
        modCount++;
    }

    /**
     * @param k     slot index
     * @param above true for the slot's part above the window, false for the part below it
     * @return the stored summed |raw volume| of that slot side, {@code NaN} if pending
     */
    public double getVolume(int k, boolean above)
    {
        return raw(key(k, above));
    }

    /**
     * Stores the summed |raw volume| of one slot side as it is (used by tests and NBT).
     *
     * @param k      slot index
     * @param above  true for the slot's part above the window, false for the part below it
     * @param volume summed absolute raw volume of that outside part, clamped to &ge; 0
     */
    public void setVolume(int k, boolean above, double volume)
    {
        put(key(k, above), volume);
    }

    /**
     * T-211: fills every pending slot side from the floor up to slot {@code kTop} with its default sum at
     * the current window and centre, once; afterwards every side is stored (sides past {@code kTop} read 0
     * and recover from there). Stored sides keep their volume (sparse T-210 saves migrate this way).
     *
     * @param winStart first raw price of the array window
     * @param winEnd   last raw price of the array window
     * @param kTop     highest slot to initialise (the slot of the ask ceiling)
     * @param def      default depth at the current centre
     */
    public void init(long winStart, long winEnd, int kTop, DefaultDepth def)
    {
        ensure(key(kTop, true));
        for (int key = 0; key < volumes.length; key++)
        {
            if (!Float.isNaN(volumes[key])) continue;
            int k = key >> 1;
            boolean above = (key & 1) != 0;
            long[] g = segmentRange(k, above, winStart, winEnd);
            volumes[key] = k > kTop || g[1] < g[0] ? 0f : (float) def.sum(g[0], g[1]);
        }
        initialised = true;
        modCount++;
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

        /**
         * T-211: true if every tick of a stored segment holds less than one raw unit, so it reads 0 after
         * {@code roundConservative}. A cell is the mean of the piecewise-linear profile over the cell, so it
         * is at most the largest node value {@code max(eL, m, eR)}. The margin below 1 keeps a value that
         * the float cast would round up to 1.0f out of the skip.
         */
        public boolean readsZero()
        {
            return stored && !(Math.max(eL, Math.max(m, eR)) >= 0.999);
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
        float v = raw(key(k, above));
        if (Float.isNaN(v)) return new Segment(k, lo, hi);
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
     * for a stored neighbour, {@code rho} below price 1. Only a pending neighbour (before {@link #init})
     * uses {@code min(rho, |default(adj)|)}; once initialised the profile no longer depends on the centre.
     */
    private double neighbourEdge(double rho, long adj, boolean above, long winStart, long winEnd, DefaultDepth def)
    {
        if (adj < 1) return rho;
        int nk = slotOf(adj);
        float nv = raw(key(nk, above));
        if (Float.isNaN(nv)) return Math.min(rho, def.at(adj));
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
     * {@code S_new = 0} (the side is inside the window now, its volume moved into the array) stores 0.
     * T-211: once initialised every side is stored, so only the first line's {@code V} applies; the
     * {@code D(G_old)} base and the "stays missing" rule only matter for pending sides before {@link #init}.
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
            float stored = raw(e.getKey());
            boolean pending = Float.isNaN(stored);
            if (sNew <= 0)
            {
                if (volumes != null) put(e.getKey(), 0);
                continue;
            }
            if (pending && d[1] == 0 && d[4] == 0)
                continue;   // only entering ticks / source gap: the remaining part still reads the default
            double base;
            if (!pending) base = stored;
            else if (sOld > 0)
            {
                long[] g = segmentRange(k, above, oldStart, oldEnd);
                base = def.sum(g[0], g[1]);
            }
            else base = 0;
            double kept = sOld > 0 ? base * (1 - d[3] / sOld) : 0;
            double v = Math.max(0, kept - d[2] + d[0]);
            put(e.getKey(), v);
        }
    }

    // ── Recovery ───────────────────────────────────────────────────────────

    /**
     * Fades every stored volume toward its slot side's default sum {@code D}: {@code V <- D + (V - D) * factor}.
     * Pending sides are left alone ({@link #init} fills them). Once initialised, the sides up to {@code kTop}
     * are all faded, so slots that came into range above the init ceiling recover from 0.
     * <p>T-211: {@code D} of a whole slot comes from a cached table that is only rebuilt when
     * {@code refreshTargets} is set (the caller decides: centre moved, or periodically); a segment clipped by
     * the window is summed every time (only the few slots around the window).
     * Cost: a refresh is one {@link DefaultDepth#sum} per side (&le; 9 provider calls each for wide slots),
     * a plain step is one multiply-add per side.
     *
     * @param factor         multiplier for {@code V - D}, in [0, 1] (e.g. {@code 2^(-dt / halfLife)})
     * @param winStart       first raw price of the array window
     * @param winEnd         last raw price of the array window
     * @param kTop           highest slot to recover (slot of the ask ceiling)
     * @param refreshTargets true to rebuild the cached targets from {@code def}
     * @param def            default depth at the current centre
     */
    public void fade(double factor, long winStart, long winEnd, int kTop, boolean refreshTargets, DefaultDepth def)
    {
        fade(factor, factor, factor, factor, winStart, winEnd, kTop, refreshTargets, def);
    }

    /**
     * Trend accumulation: {@link #fade(double, long, long, int, boolean, DefaultDepth)} with its own factor per
     * side (below the window = bids, above = asks) and direction (V below its target = refill, above = excess
     * decay). Each factor multiplies {@code V - D} like the single factor does.
     *
     * @param bidRefill  factor for a bid side with {@code V < D}
     * @param bidExcess  factor for a bid side with {@code V >= D}
     * @param askRefill  factor for an ask side with {@code V < D}
     * @param askExcess  factor for an ask side with {@code V >= D}
     * @param winStart       first raw price of the array window
     * @param winEnd         last raw price of the array window
     * @param kTop           highest slot to recover (slot of the ask ceiling)
     * @param refreshTargets true to rebuild the cached targets from {@code def}
     * @param def            default depth at the current centre
     */
    public void fade(double bidRefill, double bidExcess, double askRefill, double askExcess,
                     long winStart, long winEnd, int kTop, boolean refreshTargets, DefaultDepth def)
    {
        if (volumes == null) return;
        if (initialised) ensure(key(kTop, true));
        if (targets == null || targets.length < volumes.length)
        {
            int old = targets == null ? 0 : targets.length;
            targets = targets == null ? new double[volumes.length] : Arrays.copyOf(targets, volumes.length);
            Arrays.fill(targets, old, targets.length, Double.NaN);
        }
        modCount++;
        for (int key = 0; key < volumes.length; key++)
        {
            float v = volumes[key];
            if (Float.isNaN(v)) continue;
            int k = key >> 1;
            boolean above = (key & 1) != 0;
            long[] g = segmentRange(k, above, winStart, winEnd);
            if (g[1] < g[0]) { volumes[key] = 0f; continue; }   // inside the window: the array holds it
            double d;
            if (g[0] != slotStart(k) || g[1] != slotEnd(k)) d = def.sum(g[0], g[1]);   // clipped by the window
            else
            {
                if (refreshTargets || Double.isNaN(targets[key])) targets[key] = k > kTop ? 0 : def.sum(g[0], g[1]);
                d = targets[key];
            }
            double factor = above ? (v < d ? askRefill : askExcess) : (v < d ? bidRefill : bidExcess);
            volumes[key] = (float) Math.max(0, d + (v - d) * factor);
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    /**
     * Writes the slots: "d" (T-211 dense format: float bits per key {@code 2k + above}, NaN = pending),
     * "i" (initialised) and "g" (grid growth).
     * @param tag target tag
     */
    public void save(CompoundTag tag)
    {
        int[] vs = new int[volumes == null ? 0 : volumes.length];
        for (int i = 0; i < vs.length; i++) vs[i] = Float.floatToIntBits(volumes[i]);
        tag.putIntArray("d", vs);
        tag.putBoolean("i", initialised);
        tag.putDouble("g", SLOT_GROWTH);   // T-210: grid of the keys, checked on load
    }

    /**
     * Replaces the slots with the ones in {@code tag} (see {@link #save}).
     * <ul>
     *   <li>Dense "d" (T-211): loaded as it is.</li>
     *   <li>Sparse "k" + "v" on the same grid (T-210): the stored sides are kept, every other side is
     *       pending and gets its default sum once at the next {@link #init}.</li>
     *   <li>Anything else (no "g" = the 1% grid before T-210, another grid, the T-198 ratio format "r", no
     *       memory): empty, so the whole memory is initialised from the default once.</li>
     * </ul>
     * @param tag source tag
     */
    public void load(CompoundTag tag)
    {
        clear();
        if (!tag.contains("g") || NbtCompat.getDouble(tag, "g") != SLOT_GROWTH) return;
        if (tag.contains("d"))
        {
            int[] vs = NbtCompat.getIntArray(tag, "d");
            if (vs.length > 0)
            {
                volumes = new float[vs.length];
                for (int i = 0; i < vs.length; i++) volumes[i] = Float.intBitsToFloat(vs[i]);
            }
            initialised = volumes != null && NbtCompat.getBoolean(tag, "i");
            return;
        }
        int[] ks = NbtCompat.getIntArray(tag, "k");
        int[] vs = NbtCompat.getIntArray(tag, "v");
        for (int i = 0; i < Math.min(ks.length, vs.length); i++)
            setVolume(ks[i] >> 1, (ks[i] & 1) != 0, Float.intBitsToFloat(vs[i]));
    }

}
