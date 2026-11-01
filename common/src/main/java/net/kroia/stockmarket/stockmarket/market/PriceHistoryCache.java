package net.kroia.stockmarket.stockmarket.market;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.PriceHistoryData.Candle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-market, per-candle-delta cache of historical price candles used by the
 * client to page older data without re-requesting the full window every time.
 * <p>
 * <b>Immutable / live boundary invariant.</b> For each candle delta the cache
 * keeps two things:
 * <ul>
 *   <li>An immutable candle list ({@link #getCandles(long)}), sorted by
 *       {@code openTimestamp} ascending. Every candle in this list has finalized
 *       OHLC/volume and will never be mutated by the cache. Extending the list
 *       always goes through {@link #mergeChunk(long, PriceHistoryData, long)}
 *       or {@link #promoteLiveCandle(long)} — both preserve the ascending
 *       ordering and dedupe by {@code openTimestamp}.</li>
 *   <li>A separately-tracked <i>live</i> in-progress candle (see
 *       {@link #setLiveCandle(long, Candle)}). This candle is NOT in the
 *       immutable list; it is the tip that the price stream keeps updating.
 *       When a new-candle boundary crosses, {@link #promoteLiveCandle(long)}
 *       moves it into the immutable list and the caller opens a new live
 *       candle.</li>
 * </ul>
 * <p>
 * One instance is owned by each {@link ClientMarket}. All access happens on
 * the client main thread (chart tick + Architectury S2C handlers), so no
 * synchronization is required.
 */
public class PriceHistoryCache
{
    /**
     * Sentinel value for {@link DeltaState#serverOldestAvailableTimestamp}
     * meaning "no server response has been observed for this delta yet, so
     * we do not know where the server's oldest recorded candle lies". Distinct
     * from {@link PriceHistoryData#NO_OLDER_DATA} (which explicitly means
     * "server has no candles for this market at all").
     */
    public static final long SERVER_OLDEST_UNKNOWN = -1L;

    /**
     * Per-delta cache state. Encapsulates the immutable candle list, the
     * separately-tracked live candle, and the timestamps that mark the
     * inclusive-min / inclusive-max coverage of the immutable list.
     */
    private static final class DeltaState
    {
        final long candleDeltaMs;
        final List<Candle> candles = new ArrayList<>();
        @Nullable Candle liveCandle = null;
        long loadedMinTimestamp = Long.MAX_VALUE;
        long loadedMaxTimestamp = Long.MIN_VALUE;
        boolean atServerStart = false;

        /**
         * Snapshot of the most recently reported server-side oldest-available
         * timestamp for this delta. Retained even across empty responses so
         * downstream logic can still ask "does the server have older data than
         * what I have loaded?" when the cache is empty.
         * <p>
         * Values:
         * <ul>
         *   <li>{@link #SERVER_OLDEST_UNKNOWN} — no server response observed yet.</li>
         *   <li>{@link PriceHistoryData#NO_OLDER_DATA} — server has no candles
         *       older than what it returned (or the market has no rows at all).</li>
         *   <li>Any other value — the openTimestamp of the oldest raw row the
         *       server has on disk for this market.</li>
         * </ul>
         */
        long serverOldestAvailableTimestamp = SERVER_OLDEST_UNKNOWN;

        DeltaState(long candleDeltaMs)
        {
            this.candleDeltaMs = candleDeltaMs;
        }
    }

    private final Map<Long, DeltaState> byDelta = new HashMap<>();

    /**
     * Constructs a cache pre-populated with an empty {@link DeltaState} for
     * each candle-size the market cares about (six today: 1m / 5m / 15m /
     * 1h / 4h / 1d).
     *
     * @param candleDeltasMs the candle sizes to allocate slots for
     */
    public PriceHistoryCache(long[] candleDeltasMs)
    {
        for (long delta : candleDeltasMs)
        {
            byDelta.put(delta, new DeltaState(delta));
        }
    }

    // -----------------------------------------------------------------------
    // Merge API
    // -----------------------------------------------------------------------

    /**
     * Merges the server-side response for {@code candleDeltaMs} into the cache.
     * <p>
     * Race guard: any candle in {@code response.getCandles()} whose
     * {@code openTimestamp >= liveCandleOpenTimestamp} is dropped, because the
     * server may return a candle that overlaps the client's current live
     * candle. Keeping only strictly-older candles preserves the "immutable
     * list never touches the live tip" invariant.
     * <p>
     * Merge modes:
     * <ul>
     *   <li>Cache empty for this delta &rarr; initial store.</li>
     *   <li>{@code newChunk.maxOpenTs <= loadedMinTimestamp} &rarr; backward
     *       extension: prepend the new candles, dedupe boundary by
     *       {@code openTimestamp}.</li>
     *   <li>{@code newChunk.minOpenTs >= loadedMaxTimestamp} &rarr; forward
     *       extension: append, dedupe boundary.</li>
     *   <li>Otherwise (overlap-but-not-boundary or an interior gap) &rarr;
     *       log a warning and REPLACE the cache with the new chunk. This is a
     *       fallback; the normal pagination flow never produces such requests.</li>
     * </ul>
     * <p>
     * After merging, if the response indicates we have walked back to the
     * beginning of the server's on-disk history for this market, the cache's
     * {@code atServerStart} flag is set so future {@code requestOlderData}
     * calls can short-circuit.
     *
     * @param candleDeltaMs           the delta this response was for
     * @param response                the server response (candles + pagination scalars)
     * @param liveCandleOpenTimestamp openTimestamp of the client's current live
     *                                candle for this delta; any candle at or
     *                                above this is dropped from the incoming
     *                                chunk
     * @return {@code true} if at least one candle was added to the immutable
     *         list, {@code false} if the merge produced no new immutable data
     */
    public boolean mergeChunk(long candleDeltaMs, PriceHistoryData response, long liveCandleOpenTimestamp)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        if (s == null)
            return false;

        // Race guard: strip any candle at/after the live tip so the immutable
        // list never carries a mutable candle.
        List<Candle> incoming = new ArrayList<>();
        for (Candle c : response.getCandles())
        {
            if (c.openTimestamp < liveCandleOpenTimestamp)
                incoming.add(c);
        }

        int addedBefore = s.candles.size();

        if (incoming.isEmpty())
        {
            // No candle payload, but pagination metadata may still tell us
            // we're at the server-start floor.
            updateAtServerStart(s, response);
            return false;
        }

        long newMin = incoming.get(0).openTimestamp;
        long newMax = incoming.get(incoming.size() - 1).openTimestamp;

        if (s.candles.isEmpty())
        {
            // Initial store.
            s.candles.addAll(incoming);
            s.loadedMinTimestamp = newMin;
            s.loadedMaxTimestamp = newMax;
        }
        else if (newMax <= s.loadedMinTimestamp)
        {
            // Backward extension — prepend, dedupe on the boundary.
            List<Candle> merged = new ArrayList<>(incoming.size() + s.candles.size());
            merged.addAll(incoming);
            for (Candle c : s.candles)
            {
                if (c.openTimestamp > newMax)
                    merged.add(c);
            }
            s.candles.clear();
            s.candles.addAll(merged);
            s.loadedMinTimestamp = newMin;
        }
        else if (newMin >= s.loadedMaxTimestamp)
        {
            // Forward extension — append, dedupe on the boundary.
            long boundary = s.loadedMaxTimestamp;
            for (Candle c : incoming)
            {
                if (c.openTimestamp > boundary)
                    s.candles.add(c);
            }
            s.loadedMaxTimestamp = newMax;
        }
        else
        {
            // Overlap-but-not-boundary, or gap in the middle. This is not a
            // normal pagination outcome — log and reset the cache to the new
            // chunk to avoid inconsistent state.
            warn("PriceHistoryCache: unexpected non-contiguous chunk for delta="
                    + candleDeltaMs + " (cache[" + s.loadedMinTimestamp + ".."
                    + s.loadedMaxTimestamp + "] vs chunk[" + newMin + ".." + newMax
                    + "]). Replacing cache with new chunk.");
            s.candles.clear();
            s.candles.addAll(incoming);
            s.loadedMinTimestamp = newMin;
            s.loadedMaxTimestamp = newMax;
            s.atServerStart = false;
        }

        updateAtServerStart(s, response);
        return s.candles.size() > addedBefore;
    }

    /**
     * Applies the server's pagination floor signal AND records the server's
     * oldest-available timestamp so callers can target follow-up requests at
     * where the data actually lives even when the local cache is still empty.
     * <ul>
     *   <li>If {@code oldestAvailableTimestamp == NO_OLDER_DATA}, the server has
     *       no candles older than what it returned. When the cache is empty this
     *       means the market has no rows at all on disk (or the returned range
     *       already covers everything); either way we're at the floor.</li>
     *   <li>Otherwise, if the cache already covers back to (or past) the
     *       server's oldest timestamp, we're at the floor.</li>
     *   <li>Otherwise (empty cache + finite server oldest, i.e. our window
     *       missed the actual data range), {@code atServerStart} is left as-is —
     *       there IS older data to fetch, we just haven't reached it. This is
     *       the world-loaded-days-later gap case: initial {@code [now-N*delta,now]}
     *       returns empty even though real candles exist hours/days older.</li>
     * </ul>
     */
    private static void updateAtServerStart(DeltaState s, PriceHistoryData response)
    {
        long oldest = response.getOldestAvailableTimestamp();
        // Always record the server's most-recent-observed pagination floor so
        // downstream logic can still ask "does older data exist?" when the
        // local cache is empty. The initial-window response populates this
        // even when it returns zero candles.
        s.serverOldestAvailableTimestamp = oldest;
        if (oldest == PriceHistoryData.NO_OLDER_DATA)
        {
            s.atServerStart = true;
        }
        else if (s.loadedMinTimestamp != Long.MAX_VALUE && s.loadedMinTimestamp <= oldest)
        {
            s.atServerStart = true;
        }
    }

    // -----------------------------------------------------------------------
    // Query API
    // -----------------------------------------------------------------------

    /** @return {@code true} if any immutable candle has been cached for the delta. */
    public boolean hasCache(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s != null && !s.candles.isEmpty();
    }

    /** @return {@code true} once the cache has reached the server's oldest recorded candle for the delta. */
    public boolean atServerStart(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s != null && s.atServerStart;
    }

    /** @return openTimestamp of the oldest cached immutable candle, or {@link Long#MAX_VALUE} if empty. */
    public long loadedMinTimestamp(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s == null ? Long.MAX_VALUE : s.loadedMinTimestamp;
    }

    /** @return openTimestamp of the newest cached immutable candle, or {@link Long#MIN_VALUE} if empty. */
    public long loadedMaxTimestamp(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s == null ? Long.MIN_VALUE : s.loadedMaxTimestamp;
    }

    /**
     * @return the most recently observed server-side oldest-available
     *         timestamp for this delta. Distinguishes three states:
     *         <ul>
     *           <li>{@link #SERVER_OLDEST_UNKNOWN} ({@code -1}) — no response
     *               has been observed yet for this delta.</li>
     *           <li>{@link PriceHistoryData#NO_OLDER_DATA} — server has no
     *               candles older than what it returned (or none at all).</li>
     *           <li>Any other value — the openTimestamp of the oldest raw row
     *               the server has on disk. A pagination request targeting
     *               this timestamp is guaranteed to return non-empty candles
     *               (assuming the market wasn't deleted in the meantime).</li>
     *         </ul>
     */
    public long getServerOldestAvailableTimestamp(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s == null ? SERVER_OLDEST_UNKNOWN : s.serverOldestAvailableTimestamp;
    }

    /**
     * @return an unmodifiable view of the immutable candle list for the delta,
     *         or an empty list when the delta is unknown or nothing cached
     */
    public List<Candle> getCandles(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        if (s == null)
            return Collections.emptyList();
        return Collections.unmodifiableList(s.candles);
    }

    /** @return the current live (in-progress) candle for the delta, or {@code null} if none is set. */
    public @Nullable Candle getLiveCandle(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        return s == null ? null : s.liveCandle;
    }

    // -----------------------------------------------------------------------
    // Live-candle plumbing
    // -----------------------------------------------------------------------

    /**
     * Registers the current live candle for a delta. Called by the timer /
     * price-history container each time a new candle boundary is crossed and
     * a fresh live candle is opened.
     *
     * @param candleDeltaMs the delta bucket
     * @param liveCandle    the newly-opened live candle, or {@code null} to clear
     */
    public void setLiveCandle(long candleDeltaMs, @Nullable Candle liveCandle)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        if (s == null)
            return;
        s.liveCandle = liveCandle;
    }

    /**
     * Promotes the current live candle for the delta into the immutable list
     * (extending {@code loadedMaxTimestamp}) and clears the live reference.
     * <p>
     * Called on the tick when a new candle boundary is crossed, BEFORE the
     * caller opens a new live candle. The live candle's OHLC/volume state at
     * the moment of promotion becomes final.
     *
     * @param candleDeltaMs the delta whose live candle should be promoted
     */
    public void promoteLiveCandle(long candleDeltaMs)
    {
        DeltaState s = byDelta.get(candleDeltaMs);
        if (s == null || s.liveCandle == null)
            return;

        Candle promoted = s.liveCandle;
        s.liveCandle = null;

        // Guard against a duplicate openTimestamp (paranoia: shouldn't happen
        // because the caller only calls promote at a new-candle boundary).
        if (!s.candles.isEmpty() && s.candles.get(s.candles.size() - 1).openTimestamp >= promoted.openTimestamp)
            return;

        s.candles.add(promoted);
        if (s.candles.size() == 1)
            s.loadedMinTimestamp = promoted.openTimestamp;
        s.loadedMaxTimestamp = promoted.openTimestamp;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void warn(String message)
    {
        try
        {
            StockMarketModBackend.ClientInstances backend = getBackendUnsafe();
            if (backend != null && backend.LOGGER != null)
            {
                backend.LOGGER.warn(message);
                return;
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to System.err.
        }
        System.err.println(message);
    }

    /**
     * Best-effort access to the client-side backend for logging. Kept in one
     * place so the cache stays self-contained; callers don't need to inject a
     * logger.
     */
    private static StockMarketModBackend.@Nullable ClientInstances getBackendUnsafe()
    {
        try
        {
            java.lang.reflect.Field f = ClientMarket.class.getDeclaredField("BACKEND_INSTANCES");
            f.setAccessible(true);
            return (StockMarketModBackend.ClientInstances) f.get(null);
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }
}
