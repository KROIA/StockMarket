package net.kroia.stockmarket.pluginsystem.pluginmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Master-only server-side helper that records nanosecond execution times of the
 * plugin update loop, split per plugin and per pass.
 * <p>
 * The plugin update loop runs each plugin in two passes every server tick:
 * <ol>
 *     <li>the <em>normal update</em> pass ({@code ServerPlugin.update_internal()}), and</li>
 *     <li>the <em>finalisation</em> pass ({@code ServerPlugin.finalize_internal()}).</li>
 * </ol>
 * Both passes are timed with {@link System#nanoTime()} in {@link ServerPluginManager}
 * and fed into this tracker via {@link #recordUpdate(UUID, long)} /
 * {@link #recordFinalize(UUID, long)}.
 * <p>
 * <b>Storage layout.</b> Per plugin (keyed by {@code instanceID}) we keep a
 * {@link Entry} that holds two fixed-size ring buffers (one per pass), the latest
 * sample, the number of samples currently in the ring, and the write index.
 * The ring is a plain {@code long[]} of {@value #WINDOW_SIZE} slots — small
 * enough to avoid GC pressure, large enough (≈1&nbsp;s of ticks at 20&nbsp;Hz)
 * to yield a stable smoothed average and peak.
 * <p>
 * <b>Concurrency.</b> The tracker is written by the server tick thread inside
 * the plugin update loop and read by the server tick thread when the
 * {@code PluginPerformanceStream} builds its broadcast snapshot. Both happen on
 * the same thread; no synchronisation is required.
 * <p>
 * <b>Lifecycle.</b> Entries are created lazily on the first recorded sample.
 * They are removed via {@link #remove(UUID)} when the corresponding plugin is
 * deleted from the manager, so an old timings row does not linger after a
 * plugin is removed.
 */
public final class PluginPerformanceTracker {

    /**
     * Number of samples retained per pass, per plugin. At 20&nbsp;server-ticks
     * per second this is ≈1&nbsp;s of history — enough for a smoothed rolling
     * average and a short-window peak without noticeable memory cost
     * (2 &times; 20 &times; 8 = 320&nbsp;bytes per plugin).
     */
    public static final int WINDOW_SIZE = 20;

    /**
     * Per-plugin per-pass rolling storage. Each pass has its own ring buffer,
     * a latest-sample cache, a running-total (for O(1) average), a write index,
     * and a sample-count that saturates at {@link #WINDOW_SIZE}.
     */
    private static final class Entry {
        // --- update-pass ring ---
        final long[] updateSamples = new long[WINDOW_SIZE];
        long updateLatestNs;
        long updateSumNs;
        long updatePeakNs;
        int updateIndex;
        int updateCount;

        // --- finalize-pass ring ---
        final long[] finalizeSamples = new long[WINDOW_SIZE];
        long finalizeLatestNs;
        long finalizeSumNs;
        long finalizePeakNs;
        int finalizeIndex;
        int finalizeCount;

        void pushUpdate(long ns) {
            long evicted = updateCount == WINDOW_SIZE ? updateSamples[updateIndex] : 0L;
            updateSamples[updateIndex] = ns;
            updateIndex = (updateIndex + 1) % WINDOW_SIZE;
            if (updateCount < WINDOW_SIZE) updateCount++;
            updateSumNs = updateSumNs + ns - evicted;
            updateLatestNs = ns;
            // Peak is a windowed peak: when the largest sample gets evicted we
            // recompute; otherwise a cheap max keeps the peak fresh.
            if (updateCount == WINDOW_SIZE && evicted == updatePeakNs) {
                updatePeakNs = 0L;
                for (int i = 0; i < WINDOW_SIZE; i++) {
                    if (updateSamples[i] > updatePeakNs) updatePeakNs = updateSamples[i];
                }
            } else if (ns > updatePeakNs) {
                updatePeakNs = ns;
            }
        }

        void pushFinalize(long ns) {
            long evicted = finalizeCount == WINDOW_SIZE ? finalizeSamples[finalizeIndex] : 0L;
            finalizeSamples[finalizeIndex] = ns;
            finalizeIndex = (finalizeIndex + 1) % WINDOW_SIZE;
            if (finalizeCount < WINDOW_SIZE) finalizeCount++;
            finalizeSumNs = finalizeSumNs + ns - evicted;
            finalizeLatestNs = ns;
            if (finalizeCount == WINDOW_SIZE && evicted == finalizePeakNs) {
                finalizePeakNs = 0L;
                for (int i = 0; i < WINDOW_SIZE; i++) {
                    if (finalizeSamples[i] > finalizePeakNs) finalizePeakNs = finalizeSamples[i];
                }
            } else if (ns > finalizePeakNs) {
                finalizePeakNs = ns;
            }
        }

        long updateAvg() { return updateCount == 0 ? 0L : updateSumNs / updateCount; }
        long finalizeAvg() { return finalizeCount == 0 ? 0L : finalizeSumNs / finalizeCount; }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    // ------------------------------------------------------------------------
    // Non-plugin (server-only) tick-time rolling storage (T-137)
    // ------------------------------------------------------------------------
    //
    // Each server tick the manager reports the wall-clock time between
    // TickEvent.SERVER_PRE and the end of TickEvent.SERVER_POST minus the sum
    // of the plugin passes that ran inside that same tick. That "non-plugin"
    // delta is the CPU work the vanilla server does per tick (world tick,
    // entities, networking, etc.) — the time plugins do NOT get to spend.
    // A rolling average over the same WINDOW_SIZE window as the per-plugin
    // rings feeds PluginPerformanceSnapshot.effectiveBudgetNs so the client
    // scales the timing bar against the CPU budget plugins actually have
    // available, not the fixed 50 ms hard cap.

    /** Ring buffer of the last {@link #WINDOW_SIZE} non-plugin tick-time samples (ns). */
    private final long[] nonPluginSamples = new long[WINDOW_SIZE];
    /** Running sum of the values currently living in {@link #nonPluginSamples}. Kept for O(1) average. */
    private long nonPluginSumNs;
    /** Next write slot in {@link #nonPluginSamples} (mod {@link #WINDOW_SIZE}). */
    private int nonPluginIndex;
    /** Number of samples currently in {@link #nonPluginSamples}, saturating at {@link #WINDOW_SIZE}. */
    private int nonPluginCount;

    /**
     * Pushes one non-plugin tick-time sample into the rolling window.
     * <p>
     * Callers should compute the sample as
     * {@code max(0, totalTickWorkNs − pluginTotalNs)} — this method does not
     * do the subtraction itself. It merely maintains the O(1) ring and
     * running sum.
     *
     * @param ns the non-plugin tick-time in nanoseconds (already
     *           {@code max(0, ...)}-clamped by the caller)
     */
    public void recordNonPluginTickNs(long ns) {
        long evicted = nonPluginCount == WINDOW_SIZE ? nonPluginSamples[nonPluginIndex] : 0L;
        nonPluginSamples[nonPluginIndex] = ns;
        nonPluginIndex = (nonPluginIndex + 1) % WINDOW_SIZE;
        if (nonPluginCount < WINDOW_SIZE) nonPluginCount++;
        nonPluginSumNs = nonPluginSumNs + ns - evicted;
    }

    /**
     * Returns the rolling average of the non-plugin per-tick CPU work, in
     * nanoseconds, over the last {@link #WINDOW_SIZE} samples (or fewer if
     * the ring hasn't filled yet). Returns {@code 0L} when no sample has
     * ever been recorded — {@code TICK_BUDGET_NS − 0 = TICK_BUDGET_NS}, so
     * the effective budget defaults to the full tick at cold start.
     *
     * @return the rolling average non-plugin tick-time in nanoseconds
     */
    public long getNonPluginAvgNs() {
        return nonPluginCount == 0 ? 0L : nonPluginSumNs / nonPluginCount;
    }

    /**
     * Records one <em>normal update</em> pass execution time for a plugin.
     *
     * @param instanceID the plugin's stable instance UUID (map key in
     *                   {@link ServerPluginManager#getPlugins()})
     * @param ns         the measured pass duration in nanoseconds
     */
    public void recordUpdate(UUID instanceID, long ns) {
        entries.computeIfAbsent(instanceID, k -> new Entry()).pushUpdate(ns);
    }

    /**
     * Records one <em>finalisation</em> pass execution time for a plugin.
     *
     * @param instanceID the plugin's stable instance UUID
     * @param ns         the measured pass duration in nanoseconds
     */
    public void recordFinalize(UUID instanceID, long ns) {
        entries.computeIfAbsent(instanceID, k -> new Entry()).pushFinalize(ns);
    }

    /**
     * Drops all timing state for a plugin. Called when a plugin is removed from
     * the manager so a stale entry does not linger after the plugin's UUID is
     * no longer valid.
     */
    public void remove(UUID instanceID) {
        entries.remove(instanceID);
    }

    /** Returns {@code true} if this tracker has ever recorded a sample for the given plugin. */
    public boolean contains(UUID instanceID) {
        return entries.containsKey(instanceID);
    }

    /** Latest recorded update-pass nanoseconds for the plugin, or 0 if none. */
    public long getUpdateLatestNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.updateLatestNs;
    }
    /** Rolling average update-pass nanoseconds for the plugin, or 0 if none. */
    public long getUpdateAvgNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.updateAvg();
    }
    /** Rolling peak update-pass nanoseconds for the plugin, or 0 if none. */
    public long getUpdatePeakNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.updatePeakNs;
    }

    /** Latest recorded finalize-pass nanoseconds for the plugin, or 0 if none. */
    public long getFinalizeLatestNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.finalizeLatestNs;
    }
    /** Rolling average finalize-pass nanoseconds for the plugin, or 0 if none. */
    public long getFinalizeAvgNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.finalizeAvg();
    }
    /** Rolling peak finalize-pass nanoseconds for the plugin, or 0 if none. */
    public long getFinalizePeakNs(UUID instanceID) {
        Entry e = entries.get(instanceID); return e == null ? 0L : e.finalizePeakNs;
    }
}
