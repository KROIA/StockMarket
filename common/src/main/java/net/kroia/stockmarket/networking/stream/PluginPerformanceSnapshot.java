package net.kroia.stockmarket.networking.stream;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Manager-wide plugin timing payload delivered by {@link PluginPerformanceStream}
 * from the master server to trusted admin clients.
 * <p>
 * One snapshot carries:
 * <ul>
 *     <li>the server's per-tick time budget in nanoseconds
 *         ({@link #TICK_BUDGET_NS}, exposed as {@link #tickBudgetNs} so the
 *         client does not have to hardcode it), and</li>
 *     <li>a list of {@link Entry} rows, one per plugin. Each row is
 *         self-describing (UUID + display name) so the UI never needs a side
 *         lookup on the client.</li>
 * </ul>
 * The stream serialises one snapshot per broadcast (default cadence 500&nbsp;ms);
 * see {@link PluginPerformanceStream} for gating and cadence details.
 */
public final class PluginPerformanceSnapshot {

    /**
     * Vanilla Minecraft server target tick length: 50&nbsp;ms per tick = 20&nbsp;Hz.
     * The plugin loop's per-tick budget is capped by this value; a stacked
     * plugin-timing bar treats one tick (this many nanoseconds) as its full width.
     */
    public static final long TICK_BUDGET_NS = 50_000_000L;

    public static final StreamCodec<RegistryFriendlyByteBuf, PluginPerformanceSnapshot> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, PluginPerformanceSnapshot data) {
            ByteBufCodecs.VAR_LONG.encode(buf, data.tickBudgetNs);
            // T-137: effective (post-non-plugin-work) budget, transmitted right
            // after the hard-cap tickBudgetNs so an old client that ignored
            // this field would still read entries[] from the correct offset —
            // decoders MUST read it back symmetrically. Both server and client
            // ship together, so no legacy fallback is required.
            ByteBufCodecs.VAR_LONG.encode(buf, data.effectiveBudgetNs);
            ByteBufCodecs.VAR_INT.encode(buf, data.entries.size());
            for (Entry e : data.entries) {
                Entry.STREAM_CODEC.encode(buf, e);
            }
        }

        @Override
        public PluginPerformanceSnapshot decode(RegistryFriendlyByteBuf buf) {
            long tickBudgetNs = ByteBufCodecs.VAR_LONG.decode(buf);
            long effectiveBudgetNs = ByteBufCodecs.VAR_LONG.decode(buf);
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            List<Entry> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(Entry.STREAM_CODEC.decode(buf));
            }
            return new PluginPerformanceSnapshot(tickBudgetNs, effectiveBudgetNs, list);
        }
    };

    /**
     * One plugin's timing row inside a {@link PluginPerformanceSnapshot}. All
     * durations are in nanoseconds; {@code latest} is the last single-tick
     * sample, {@code avg} is a rolling average over the tracker window, and
     * {@code peak} is the max sample currently in the window.
     */
    public static final class Entry {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, Entry e) {
                UUIDUtil.STREAM_CODEC.encode(buf, e.instanceID);
                ByteBufCodecs.STRING_UTF8.encode(buf, e.displayName);
                ByteBufCodecs.VAR_LONG.encode(buf, e.updateLatestNs);
                ByteBufCodecs.VAR_LONG.encode(buf, e.updateAvgNs);
                ByteBufCodecs.VAR_LONG.encode(buf, e.updatePeakNs);
                ByteBufCodecs.VAR_LONG.encode(buf, e.finalizeLatestNs);
                ByteBufCodecs.VAR_LONG.encode(buf, e.finalizeAvgNs);
                ByteBufCodecs.VAR_LONG.encode(buf, e.finalizePeakNs);
                ByteBufCodecs.VAR_INT.encode(buf, e.subscribedMarketCount);
                ByteBufCodecs.BOOL.encode(buf, e.enabled);
            }

            @Override
            public Entry decode(RegistryFriendlyByteBuf buf) {
                UUID instanceID = UUIDUtil.STREAM_CODEC.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                long ul = ByteBufCodecs.VAR_LONG.decode(buf);
                long ua = ByteBufCodecs.VAR_LONG.decode(buf);
                long up = ByteBufCodecs.VAR_LONG.decode(buf);
                long fl = ByteBufCodecs.VAR_LONG.decode(buf);
                long fa = ByteBufCodecs.VAR_LONG.decode(buf);
                long fp = ByteBufCodecs.VAR_LONG.decode(buf);
                int subCount = ByteBufCodecs.VAR_INT.decode(buf);
                boolean enabled = ByteBufCodecs.BOOL.decode(buf);
                return new Entry(instanceID, name, ul, ua, up, fl, fa, fp, subCount, enabled);
            }
        };

        /** Stable plugin instance UUID (same key used in {@code ServerPluginManager.getPlugins()}). */
        public final UUID instanceID;
        /** Plugin display name — the value shown in the plugin list UI. */
        public final String displayName;

        /** Latest single-tick <em>normal update</em> pass duration in nanoseconds. */
        public final long updateLatestNs;
        /** Rolling-window average of the <em>normal update</em> pass in nanoseconds. */
        public final long updateAvgNs;
        /** Rolling-window peak of the <em>normal update</em> pass in nanoseconds. */
        public final long updatePeakNs;

        /** Latest single-tick <em>finalisation</em> pass duration in nanoseconds. */
        public final long finalizeLatestNs;
        /** Rolling-window average of the <em>finalisation</em> pass in nanoseconds. */
        public final long finalizeAvgNs;
        /** Rolling-window peak of the <em>finalisation</em> pass in nanoseconds. */
        public final long finalizePeakNs;

        /** Number of markets this plugin is currently subscribed to. */
        public final int subscribedMarketCount;
        /** Whether the plugin is enabled on the server at snapshot time. */
        public final boolean enabled;

        public Entry(UUID instanceID, String displayName,
                     long updateLatestNs, long updateAvgNs, long updatePeakNs,
                     long finalizeLatestNs, long finalizeAvgNs, long finalizePeakNs,
                     int subscribedMarketCount, boolean enabled) {
            this.instanceID = instanceID;
            this.displayName = displayName;
            this.updateLatestNs = updateLatestNs;
            this.updateAvgNs = updateAvgNs;
            this.updatePeakNs = updatePeakNs;
            this.finalizeLatestNs = finalizeLatestNs;
            this.finalizeAvgNs = finalizeAvgNs;
            this.finalizePeakNs = finalizePeakNs;
            this.subscribedMarketCount = subscribedMarketCount;
            this.enabled = enabled;
        }

        /** Sum of latest update + latest finalize passes for this plugin (ns). */
        public long totalLatestNs() { return updateLatestNs + finalizeLatestNs; }
        /** Sum of rolling-average update + finalize passes for this plugin (ns). */
        public long totalAvgNs() { return updateAvgNs + finalizeAvgNs; }
    }

    /** Server tick budget in nanoseconds — clients use this as the bar's hard-cap reference (50 ms). */
    public final long tickBudgetNs;

    /**
     * {@code clamp(TICK_BUDGET_NS − avgNonPluginTimeNs, 0, TICK_BUDGET_NS)}.
     * This is the budget plugins actually have available after subtracting
     * the master server's rolling-average non-plugin CPU work per tick
     * (world tick, entities, networking, etc.). Clients scale the bar
     * against this value, not against {@link #tickBudgetNs}, so the bar
     * reflects the CPU plugins can genuinely spend before the tick loop
     * overruns. Equal to {@link #tickBudgetNs} on cold start (no non-plugin
     * samples yet).
     */
    public final long effectiveBudgetNs;

    /**
     * One row per plugin currently living in the server-side manager. Order is
     * the manager's iteration order (i.e. the {@code LinkedHashMap} order used
     * by the actual execution loop), so the client can render segments in the
     * same order plugins actually run.
     */
    public final List<Entry> entries;

    public PluginPerformanceSnapshot(long tickBudgetNs, long effectiveBudgetNs, List<Entry> entries) {
        this.tickBudgetNs = tickBudgetNs;
        this.effectiveBudgetNs = effectiveBudgetNs;
        this.entries = entries == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(entries);
    }

    /**
     * An empty snapshot with the default tick budget — safe first-load default
     * for clients. Effective budget defaults to the full tick, so a bar built
     * from this snapshot never renders as pre-overloaded before any real
     * server sample has arrived.
     */
    public static PluginPerformanceSnapshot empty() {
        return new PluginPerformanceSnapshot(TICK_BUDGET_NS, TICK_BUDGET_NS, Collections.emptyList());
    }

    /** Sum of {@link Entry#totalLatestNs()} across every entry. */
    public long totalLatestNs() {
        long sum = 0L;
        for (Entry e : entries) sum += e.totalLatestNs();
        return sum;
    }

    /** Sum of {@link Entry#totalAvgNs()} across every entry. */
    public long totalAvgNs() {
        long sum = 0L;
        for (Entry e : entries) sum += e.totalAvgNs();
        return sum;
    }
}
