package net.kroia.stockmarket.pluginsystem.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.ActiveNewsEvent;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsEventLibrary;
import net.kroia.stockmarket.news.NewsImpactEnvelope;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsPublisher;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsRequirement;
import net.kroia.stockmarket.news.NewsSequence;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Server plugin that randomly schedules news events and applies their price impact to
 * the subscribed markets (NewsEventSystem plan §2, task T-071).
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li><b>Library ownership:</b> owns the {@link NewsEventLibrary} and loads it lazily on
 *       the first {@link #update} tick — never in the constructor — because matcher
 *       resolution and {@code ItemID.getName()} are only reliable after BankSystem setup
 *       completes (plan §6.11). {@link #reloadLibrary()} exposes admin reloads (T-076).</li>
 *   <li><b>Scheduler (T-082 pre-scheduled queue):</b> the next {@link #PLANNED_QUEUE_SIZE}
 *       activations are planned ahead as a persisted queue of {@code (delta-ms, planned
 *       event id)} slots — intervals sampled uniformly from the <b>effective</b>
 *       {@code [minSecondsBetweenEvents, maxSecondsBetweenEvents]} (admin override over
 *       the JSON value, see {@link #applySchedulerOverrides}), event ids weighted-picked
 *       at planning time (empty id = time-only slot). At fire time the planned id is
 *       <b>re-validated</b>: if it is no longer eligible the slot repicks freshly, and if
 *       nothing is eligible it lapses. Eligible = off cooldown, not currently active, not
 *       {@code adminOnly}, not admin-disabled, and its matchers resolve to a non-empty
 *       subset of the subscribed-and-news-enabled markets after the per-market activity
 *       cap ({@code maxActiveEventsPerMarket}); the global cap {@code maxActiveEventsGlobal}
 *       still gates every attempt at fire time. Manual admin triggers never consume queue
 *       slots.</li>
 *   <li><b>Announce delay:</b> per activation one delay is sampled uniformly from the
 *       definition's {@code announceDelayMs} range. Positive delay → publish immediately,
 *       impact starts {@code delay} ms later; negative → impact starts immediately, publish
 *       {@code |delay|} ms later. Both moments live on the event's ticking-time age line
 *       (see {@link ActiveNewsEvent}) and therefore survive restarts inside the pending
 *       window.</li>
 *   <li><b>Publishing:</b> at the publish moment a {@link NewsRecord} is built (monotonic
 *       persisted uid, epoch timestamp, game day, text snapshots, actually-impacted market
 *       subset) and handed to the {@link NewsPublisher} seam. T-072 (history storage) and
 *       T-073 (broadcast packet) plug in via {@link #setPublisher}; until then a default
 *       publisher logs the publish at info level.</li>
 *   <li><b>Price influence — applied in {@link #finalize(List)}, NOT {@link #update(List)}.</b>
 *       This is load-bearing: {@code VolatilityPlugin.update()} sets the target price
 *       <i>absolutely</i> each tick, and {@code ServerPluginManager} runs all plugins'
 *       {@code update()} first, then all {@code finalize()}, and only then commits the
 *       market cache. Multiplying the target in finalize therefore works regardless of
 *       plugin execution order (admins can reorder plugins). Any future plugin that calls
 *       {@code setTargetPrice} in its own {@code finalize()} would clobber this — don't.</li>
 *   <li><b>Sequences (T-095):</b> every event runs as ONE resolved {@link NewsSequence}
 *       — picked by per-sequence weight at activation, every step duration rolled from
 *       its {@code [min, max]} range, per-step market maps resolved — all frozen into
 *       the {@link ActiveNewsEvent} so restarts never re-roll and definition reloads
 *       never affect a running event. Legacy {@code impact} events are normalized into
 *       an implicit sequence with bit-equivalent values (single runtime code path).</li>
 *   <li><b>Permanent bake-in (successor of {@code reversal:none}):</b> when a sequence
 *       whose last step is {@code permanent} ends, the final influence value is baked
 *       into each last-step market's <i>default price</i> exactly once
 *       ({@code defaultPrice *= clamp(1 + finalValue * weightFactor * sensitivity)}) and
 *       the market is retired from the event. Simply dropping the multiplicative factor
 *       would snap the price back on the next tick, because the VolatilityPlugin
 *       re-derives its target from the default price. The per-market baked bookkeeping
 *       is persisted with the event, so a restart can never double-bake
 *       (see {@link ActiveNewsEvent}).</li>
 *   <li><b>Time basis:</b> all event/scheduler/cooldown progress advances by the real
 *       elapsed milliseconds per tick (clamped to {@link #MAX_TICK_ADVANCE_MS}), measured
 *       with a ModUtilities {@link TimerMillis} — so nothing expires while the server is
 *       paused or stopped (plan §6.9).</li>
 * </ul>
 * <p>
 * <b>Per-market custom settings</b> ({@link Settings}): {@code newsEnabled=false} removes a
 * market from both eligibility and impact; {@code sensitivity} scales the impact per market.
 * Editable through the existing plugin custom-settings UI machinery.
 * <p>
 * Disabling the plugin freezes all influence and all timers automatically, because the
 * plugin manager skips disabled plugins' update/finalize entirely.
 */
public class NewsPlugin extends ServerPlugin<NewsPlugin.Settings, NewsPlugin.RuntimeStreamData> {

    // ── Constants ────────────────────────────────────────────────────────

    /** Lower clamp of the combined per-market news factor (plan §6.2: stacked crashes must never floor a market). */
    public static final double MIN_COMBINED_FACTOR = 0.1;
    /** Upper clamp of the combined per-market news factor. */
    public static final double MAX_COMBINED_FACTOR = 10.0;
    /**
     * Maximum milliseconds a single tick may advance the news timeline. Normal ticks are
     * ~50 ms; a larger measured gap means the server lagged, was suspended or paused —
     * in that case the timeline advances by at most this amount instead of fast-forwarding
     * through (and silently expiring) events.
     */
    public static final long MAX_TICK_ADVANCE_MS = 1000;

    /** Default for {@link Settings#newsEnabled()}: markets take part in news events. */
    public static final boolean DEFAULT_NEWS_ENABLED = true;
    /** Default for {@link Settings#sensitivity()}: impacts apply at full strength. */
    public static final float DEFAULT_SENSITIVITY = 1.0f;

    // ── Custom per-market settings ───────────────────────────────────────

    /**
     * Custom per-market settings for the news impact behavior.
     *
     * @param newsEnabled if false, this market is excluded from news events entirely:
     *                    it is removed from event eligibility (an event whose matched
     *                    markets are all news-disabled can never fire) AND from the price
     *                    impact of already-active events
     * @param sensitivity per-market scale of the news impact (1.0 = full strength,
     *                    0.5 = half, 2.0 = double). Also scales the permanent
     *                    {@code reversal:none} bake-in. Values are not sign-checked —
     *                    a negative sensitivity inverts impacts, like a negative
     *                    matcher weightFactor
     */
    public record Settings(boolean newsEnabled, float sensitivity) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Settings::newsEnabled,
                ByteBufCodecs.FLOAT, Settings::sensitivity,
                Settings::new
        );

        /** Creates a Settings instance with all default values. */
        public static Settings createDefault() {
            return new Settings(DEFAULT_NEWS_ENABLED, DEFAULT_SENSITIVITY);
        }
    }

    // ── Runtime data stream (consumed by the T-075 plugin GUI) ──────────

    /**
     * Snapshot of all currently active news events plus the scheduler timeline for the
     * admin GUI, streamed via the existing plugin runtime-data framework
     * ({@code PluginRuntimeDataStream}).
     * <p>
     * Codec shape (all via {@link RuntimeStreamData#CODEC}): an int event count, then per
     * event: {@code eventId} (UTF-8), display {@code headline} ({@code en_us} or first
     * translation entry), {@code phaseName} (one of {@code PENDING} — impact not started
     * yet — or a {@link NewsImpactEnvelope.Phase} name), {@code remainingMs} (long,
     * envelope time left incl. a pending impact start; 0 when over/permanent),
     * {@code published} (bool), then an int market count and per market the ItemID short
     * and the market's current factor term (float, noise-free; 1.0 = no influence yet),
     * then (appended for T-091) a {@code pictureHash} presence bool followed by 20 raw
     * SHA-1 bytes when present, then (appended for T-099, unconditional) the step info:
     * {@code stepName} (UTF-8), {@code stepIndex} (int, 0-based), {@code stepCount}
     * (int) and {@code stepRemainingMs} (long; -1 sentinel while PENDING/terminal).
     * Baked ({@code reversal:none}) markets are no longer listed — they left the event.
     * <p>
     * Appended for T-082 (after the event list, in this order): an int upcoming count,
     * then per {@link UpcomingEvent} the planned {@code eventId} (UTF-8, may be empty for
     * a time-only slot whose event is decided at fire time) and its {@code etaMs} (long,
     * ticking-ms until the slot fires); then the {@link SchedulerState} — the four
     * <b>effective</b> scheduler values, each followed by its {@code overridden} flag
     * (long min + bool, long max + bool, int global cap + bool, int per-market cap + bool).
     * The GUI resolves display headlines from the {@code EventDetails} snapshot of the
     * admin request (T-081), not from this stream — only ids travel here.
     *
     * @param events   the currently active events (see {@link ActiveEventInfo})
     * @param upcoming the planned scheduler timeline, soonest first (T-082)
     * @param scheduler the effective scheduler values + per-value override flags (T-082)
     */
    public record RuntimeStreamData(List<ActiveEventInfo> events,
                                    List<UpcomingEvent> upcoming,
                                    SchedulerState scheduler) {

        /**
         * Backward-compatible convenience constructor (pre-T-082 call sites, e.g. the
         * GUI's empty placeholder): no upcoming slots, default scheduler values.
         */
        public RuntimeStreamData(List<ActiveEventInfo> events) {
            this(events, new ArrayList<>(), SchedulerState.createDefault());
        }

        /**
         * One market currently influenced by an active event.
         *
         * @param marketId      the market's ItemID short (resolve via ItemIDManager client-side)
         * @param currentFactor the event's current multiplicative factor term for this
         *                      market (noise-free), e.g. 1.25 = +25% right now
         */
        public record MarketFactor(short marketId, float currentFactor) {}

        /**
         * One active news event.
         *
         * @param eventId     the definition id
         * @param headline    display headline ({@code en_us} or first translation entry)
         * @param phaseName   {@code "PENDING"} while the impact has not started and
         *                    {@code "PERMANENT"}/{@code "EXPIRED"} once the sequence is
         *                    over (unchanged legacy semantics); while running, legacy
         *                    events keep the classic {@link NewsImpactEnvelope.Phase}
         *                    names and sequence-authored events send the current step's
         *                    name (T-095, see {@link ActiveNewsEvent#phaseName()})
         * @param remainingMs sequence time left in ms (incl. pending impact start)
         * @param published   whether the news record has been published already
         * @param markets     the actively influenced markets with their current factors
         * @param pictureHash the event picture's 20-byte SHA-1 content hash (T-091,
         *                    resolved via the library at snapshot time — see
         *                    {@code resolvePictureHash}), or null for text-only events;
         *                    the Active-tab rows fetch/render the thumbnail from it
         * @param stepName    the name of the sequence step active right now (T-099);
         *                    while PENDING the first step's name, once terminal the
         *                    last step's name (render state off {@code phaseName} /
         *                    {@code stepRemainingMs})
         * @param stepIndex   0-based index of the current step (clamped like
         *                    {@link NewsSequence#stepIndexAt}) — the "phase i of n"
         *                    display numerator is {@code stepIndex + 1}
         * @param stepCount   total number of steps of the event's resolved sequence
         *                    (≥ 1; legacy impact events report their implicit 3)
         * @param stepRemainingMs milliseconds until the current step ends, or
         *                    <b>{@code -1} while the impact is PENDING and once the
         *                    sequence is terminal</b> (PERMANENT/EXPIRED) — the -1
         *                    sentinel is unambiguous because a running step's
         *                    remaining time is never negative
         */
        public record ActiveEventInfo(String eventId, String headline, String phaseName,
                                      long remainingMs, boolean published,
                                      List<MarketFactor> markets,
                                      byte @Nullable [] pictureHash,
                                      String stepName, int stepIndex, int stepCount,
                                      long stepRemainingMs) {

            /**
             * Backward-compatible convenience constructor (pre-T-091 call sites,
             * e.g. existing test suites): no picture, no step info.
             */
            public ActiveEventInfo(String eventId, String headline, String phaseName,
                                   long remainingMs, boolean published,
                                   List<MarketFactor> markets) {
                this(eventId, headline, phaseName, remainingMs, published, markets, null);
            }

            /**
             * Backward-compatible convenience constructor (pre-T-099 call sites):
             * no step info — defaults to a single unnamed step with the terminal
             * {@code -1} remaining-time sentinel.
             */
            public ActiveEventInfo(String eventId, String headline, String phaseName,
                                   long remainingMs, boolean published,
                                   List<MarketFactor> markets,
                                   byte @Nullable [] pictureHash) {
                this(eventId, headline, phaseName, remainingMs, published, markets,
                        pictureHash, "", 0, 1, -1L);
            }
        }

        /**
         * One planned scheduler slot of the upcoming-events timeline (T-082).
         *
         * @param eventId the definition id planned for this slot, or an <b>empty string</b>
         *                for a time-only slot (nothing was eligible at planning time — the
         *                event is decided at fire time instead)
         * @param etaMs   ticking-ms until this slot fires (frozen while the server pauses,
         *                same time basis as everything else in this plugin)
         */
        public record UpcomingEvent(String eventId, long etaMs) {}

        /**
         * The four <b>effective</b> scheduler values plus a per-value flag telling whether
         * the value comes from an admin override (T-082) or from the JSON library config
         * ({@link NewsEventLibrary.SchedulerConfig}).
         *
         * @param minSecondsBetweenEvents effective minimum seconds between two random activations
         * @param minOverridden           true if an admin override supplies the min value
         * @param maxSecondsBetweenEvents effective maximum seconds between two random activations
         * @param maxOverridden           true if an admin override supplies the max value
         * @param maxActiveEventsGlobal   effective cap of simultaneously active events overall
         * @param globalOverridden        true if an admin override supplies the global cap
         * @param maxActiveEventsPerMarket effective cap of simultaneously active events per market
         * @param perMarketOverridden     true if an admin override supplies the per-market cap
         */
        public record SchedulerState(long minSecondsBetweenEvents, boolean minOverridden,
                                     long maxSecondsBetweenEvents, boolean maxOverridden,
                                     int maxActiveEventsGlobal, boolean globalOverridden,
                                     int maxActiveEventsPerMarket, boolean perMarketOverridden) {

            /** @return the built-in library defaults, nothing overridden (compat placeholder) */
            public static SchedulerState createDefault() {
                return new SchedulerState(
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MIN_SECONDS_BETWEEN_EVENTS, false,
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MAX_SECONDS_BETWEEN_EVENTS, false,
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL, false,
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MAX_ACTIVE_EVENTS_PER_MARKET, false);
            }
        }

        /**
         * Hand-written wire codec.
         * <p>
         * <b>No version byte:</b> encoder and decoder ship lockstep in the same mod jar
         * (server and client halves of one build), so appending fields — like the T-082
         * upcoming/scheduler block — is safe as long as {@code encode} and {@code decode}
         * are always updated together. There is no cross-version compatibility layer.
         */
        public static final StreamCodec<ByteBuf, RuntimeStreamData> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buf, RuntimeStreamData data) {
                buf.writeInt(data.events().size());
                for (ActiveEventInfo event : data.events()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, event.eventId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, event.headline());
                    ByteBufCodecs.STRING_UTF8.encode(buf, event.phaseName());
                    buf.writeLong(event.remainingMs());
                    buf.writeBoolean(event.published());
                    buf.writeInt(event.markets().size());
                    for (MarketFactor market : event.markets()) {
                        buf.writeShort(market.marketId());
                        buf.writeFloat(market.currentFactor());
                    }
                    // Appended T-091 (news pictures): unconditional presence flag +
                    // optional 20 raw hash bytes per event (lockstep convention, see
                    // the codec Javadoc — encode and decode always change together).
                    buf.writeBoolean(event.pictureHash() != null);
                    if (event.pictureHash() != null) {
                        buf.writeBytes(event.pictureHash());
                    }
                    // Appended T-099 (sequences): unconditional per-event step info —
                    // stepName, 0-based stepIndex, stepCount, stepRemainingMs
                    // (-1 sentinel while PENDING/terminal, see ActiveEventInfo).
                    ByteBufCodecs.STRING_UTF8.encode(buf, event.stepName());
                    buf.writeInt(event.stepIndex());
                    buf.writeInt(event.stepCount());
                    buf.writeLong(event.stepRemainingMs());
                }
                // Appended T-082: upcoming timeline + effective scheduler state.
                buf.writeInt(data.upcoming().size());
                for (UpcomingEvent upcoming : data.upcoming()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, upcoming.eventId());
                    buf.writeLong(upcoming.etaMs());
                }
                SchedulerState scheduler = data.scheduler();
                buf.writeLong(scheduler.minSecondsBetweenEvents());
                buf.writeBoolean(scheduler.minOverridden());
                buf.writeLong(scheduler.maxSecondsBetweenEvents());
                buf.writeBoolean(scheduler.maxOverridden());
                buf.writeInt(scheduler.maxActiveEventsGlobal());
                buf.writeBoolean(scheduler.globalOverridden());
                buf.writeInt(scheduler.maxActiveEventsPerMarket());
                buf.writeBoolean(scheduler.perMarketOverridden());
            }

            @Override
            public RuntimeStreamData decode(ByteBuf buf) {
                int eventCount = buf.readInt();
                List<ActiveEventInfo> events = new ArrayList<>(Math.max(0, eventCount));
                for (int i = 0; i < eventCount; i++) {
                    String eventId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String headline = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String phaseName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    long remainingMs = buf.readLong();
                    boolean published = buf.readBoolean();
                    int marketCount = buf.readInt();
                    List<MarketFactor> markets = new ArrayList<>(Math.max(0, marketCount));
                    for (int j = 0; j < marketCount; j++) {
                        markets.add(new MarketFactor(buf.readShort(), buf.readFloat()));
                    }
                    // Appended T-091 (news pictures): optional 20-byte picture hash.
                    byte[] pictureHash = null;
                    if (buf.readBoolean()) {
                        pictureHash = new byte[NewsPictureLibrary.SHA1_LENGTH];
                        buf.readBytes(pictureHash);
                    }
                    // Appended T-099 (sequences): per-event step info (unconditional,
                    // lockstep with encode).
                    String stepName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int stepIndex = buf.readInt();
                    int stepCount = buf.readInt();
                    long stepRemainingMs = buf.readLong();
                    events.add(new ActiveEventInfo(eventId, headline, phaseName,
                            remainingMs, published, markets, pictureHash,
                            stepName, stepIndex, stepCount, stepRemainingMs));
                }
                // Appended T-082: upcoming timeline + effective scheduler state.
                int upcomingCount = buf.readInt();
                List<UpcomingEvent> upcoming = new ArrayList<>(Math.max(0, upcomingCount));
                for (int i = 0; i < upcomingCount; i++) {
                    upcoming.add(new UpcomingEvent(
                            ByteBufCodecs.STRING_UTF8.decode(buf), buf.readLong()));
                }
                SchedulerState scheduler = new SchedulerState(
                        buf.readLong(), buf.readBoolean(),
                        buf.readLong(), buf.readBoolean(),
                        buf.readInt(), buf.readBoolean(),
                        buf.readInt(), buf.readBoolean());
                return new RuntimeStreamData(events, upcoming, scheduler);
            }
        };
    }

    /**
     * One event that is currently allowed to fire, paired with its resolved
     * matched∩subscribed∩news-enabled market subset.
     *
     * @param definition      the eligible definition
     * @param resolvedMarkets the markets the event would impact → effective weight factor
     */
    public record EligibleEvent(NewsEventDefinition definition, Map<ItemID, Float> resolvedMarkets) {}

    // ── State ────────────────────────────────────────────────────────────

    /** The definition library (loaded lazily post-BankSystem-setup, see class Javadoc). */
    private final NewsEventLibrary library = new NewsEventLibrary();
    private boolean libraryLoaded = false;

    /** Currently active events, in activation order. */
    private final List<ActiveNewsEvent> activeEvents = new ArrayList<>();

    /**
     * Remaining cooldown per definition id, in <b>ticking milliseconds</b> (same time
     * basis as the events — cooldowns freeze while the server is paused/stopped, which
     * is why remaining-ms is persisted instead of an epoch timestamp).
     */
    private final Map<String, Long> cooldownRemainingMs = new HashMap<>();

    /**
     * Definition ids an admin has disabled (T-081). A disabled event can <b>never</b>
     * activate — the random scheduler skips it ({@link #computeEligibleEvents}) and
     * {@link #activate} refuses it, which also covers the manual admin trigger.
     * <p>
     * Ids are kept even when the definition is currently absent from the library, so a
     * temporarily removed event file keeps its disabled state when re-added. Persisted
     * in {@link #save}/{@link #load} — <b>never</b> in the JSON event files, which stay
     * portable across servers.
     */
    private final Set<String> disabledEventIds = new LinkedHashSet<>();

    /** Number of pre-planned scheduler slots kept in {@link #plannedQueue} (T-082). */
    public static final int PLANNED_QUEUE_SIZE = 5;

    /**
     * One mutable slot of the pre-scheduled activation queue (T-082).
     * <p>
     * {@code offsetMs} is the ticking-ms delta to the <b>previous</b> queue entry's fire
     * moment (for the head entry: the remaining ms until it fires — only the head ticks
     * down). Storing deltas instead of absolute times keeps every later slot's ETA
     * consistent when the head fires or when the head countdown is manipulated.
     * <p>
     * {@code eventId} is the definition id planned for the slot at planning time, or an
     * empty string for a time-only slot (nothing was eligible when the slot was planned —
     * the event is decided at fire time). Planned ids are only a <i>prediction</i>: fire
     * time re-validates them and repicks/lapses as needed (see {@link #firePlannedSlot}).
     */
    private static final class PlannedSlot {
        long offsetMs;
        String eventId;

        PlannedSlot(long offsetMs, String eventId) {
            this.offsetMs = offsetMs;
            this.eventId = eventId == null ? "" : eventId;
        }
    }

    /**
     * The pre-scheduled activation queue (T-082): the next {@link #PLANNED_QUEUE_SIZE}
     * planned scheduler firings, soonest first. Replaces the single pre-T-082 countdown
     * ({@code schedulerRemainingMs}); persisted in {@link #save}/{@link #load} and
     * exposed to the admin GUI via the runtime stream ({@link #getPlannedActivations}).
     */
    private final List<PlannedSlot> plannedQueue = new ArrayList<>();

    /**
     * How often (ticking ms) time-only queue slots are re-offered to the event picker
     * (see {@link #upgradeTimeOnlySlots}). Throttled because every attempt runs
     * {@link #computeEligibleEvents} (matcher resolution per definition).
     */
    public static final long TIME_ONLY_UPGRADE_INTERVAL_MS = 5000;

    /** Ticking-ms accumulator for the throttled time-only slot upgrade. */
    private long timeOnlyUpgradeAccumulatorMs = 0;

    // Optional admin overrides of the library scheduler config (T-082). Null = no
    // override, the JSON file value applies (see the getEffective* accessors). The
    // overrides live in the plugin NBT — never in the JSON files, which stay portable —
    // and therefore survive library reloads by construction.
    private @Nullable Long overrideMinSecondsBetweenEvents = null;
    private @Nullable Long overrideMaxSecondsBetweenEvents = null;
    private @Nullable Integer overrideMaxActiveEventsGlobal = null;
    private @Nullable Integer overrideMaxActiveEventsPerMarket = null;

    /** Monotonic uid counter for published news records (persisted). */
    private long newsUidCounter = 0;

    // ── Chains runtime (T-098, sequences plan §4) ───────────────────────

    /** Maximum chain hop depth — prevents unbounded A→B→C→D→E→… chains. */
    public static final int MAX_CHAIN_DEPTH = 4;

    /**
     * One pending chain activation: a chain entry whose chance roll succeeded and
     * whose activation delay is ticking down. Persisted in the plugin's NBT so
     * scheduled chains survive server restarts.
     */
    public static final class PendingChainActivation {
        /** The target event id to activate when the delay expires. */
        final String targetEventId;
        /** Remaining ticking-ms until the target should fire. */
        long remainingMs;
        /** The definition id of the source event that enqueued this chain. */
        final String sourceEventId;
        /** The chain hop depth (0 = scheduler-fired source, 1 = first chain hop, etc.). */
        final int depth;
        /** The ancestry set of event ids (prevents A→B→A cycles). */
        final Set<String> ancestry;
        /**
         * Market override when {@code sameMarkets} was true in the chain definition:
         * the source event's markets captured at trigger time. Null when the target
         * should resolve markets normally at fire time.
         */
        final @Nullable Map<ItemID, Float> resolvedMarketsOverride;

        /**
         * @param targetEventId          the event id to activate when the delay expires
         * @param remainingMs            the remaining delay in ticking-ms
         * @param sourceEventId          the source event's definition id
         * @param depth                  the chain hop depth
         * @param ancestry               the set of event ids in this chain's ancestry
         * @param resolvedMarketsOverride market override for sameMarkets chains, or null
         */
        public PendingChainActivation(String targetEventId, long remainingMs,
                                       String sourceEventId, int depth,
                                       Set<String> ancestry,
                                       @Nullable Map<ItemID, Float> resolvedMarketsOverride) {
            this.targetEventId = targetEventId;
            this.remainingMs = remainingMs;
            this.sourceEventId = sourceEventId;
            this.depth = depth;
            this.ancestry = ancestry != null ? new LinkedHashSet<>(ancestry) : new LinkedHashSet<>();
            this.resolvedMarketsOverride = resolvedMarketsOverride != null
                    ? new LinkedHashMap<>(resolvedMarketsOverride) : null;
        }

        /** @return the target event id */
        public String getTargetEventId() { return targetEventId; }
        /** @return remaining ticking-ms until fire */
        public long getRemainingMs() { return remainingMs; }
        /** @return the source event id */
        public String getSourceEventId() { return sourceEventId; }
        /** @return the chain hop depth */
        public int getDepth() { return depth; }
        /** @return the ancestry event ids (unmodifiable) */
        public Set<String> getAncestry() { return Collections.unmodifiableSet(ancestry); }
        /** @return the market override, or null if the target resolves normally */
        public @Nullable Map<ItemID, Float> getResolvedMarketsOverride() {
            return resolvedMarketsOverride != null
                    ? Collections.unmodifiableMap(resolvedMarketsOverride) : null;
        }
    }

    /**
     * Pending chain activations: chains whose chance roll succeeded and whose delay
     * is ticking down. Persisted in the plugin's NBT (T-098).
     */
    private final List<PendingChainActivation> pendingChainActivations = new ArrayList<>();

    /**
     * Last-seen step index per active event's news uid — used to detect step
     * transitions in the tick/advance path (step-start chains fire when the index
     * changes, including steps fast-forwarded past by skipPhase). Keyed by news uid
     * (not definition id) because the same event id could theoretically re-activate
     * after one retires. NOT persisted: on load, each active event's current step
     * index is treated as already-seen (no re-fire of step-start chains for already-
     * passed steps).
     */
    private final Map<Long, Integer> lastSeenStepIndices = new HashMap<>();

    /**
     * Chain hop depth per active event's news uid — keyed only for chain-fired events
     * (depth > 0). Scheduler/admin-fired events are absent and default to depth 0.
     * Used by {@link #triggerChains} to compute the next hop's depth. Persisted in the
     * plugin's NBT so chains survive restarts.
     */
    private final Map<Long, Integer> eventChainDepth = new HashMap<>();

    /**
     * Chain ancestry per active event's news uid — keyed only for chain-fired events.
     * Contains the set of event ids in this event's chain lineage (prevents A→B→A).
     * Scheduler/admin-fired events are absent and default to an empty set. Persisted.
     */
    private final Map<Long, Set<String>> eventChainAncestry = new HashMap<>();

    /** Measures the real elapsed time between update ticks (honors TimerMillis.TIMER_OFFSET_MS). */
    private final TimerMillis tickDeltaTimer = new TimerMillis(false);

    /**
     * Supplier for the master-side world-event registry (T-098). Wired by
     * {@code ServerPluginManager} during production setup; null in unit tests
     * (requirement checks and registry recording are then skipped).
     */
    private @Nullable java.util.function.Supplier<NewsWorldRegistry> registrySupplier = null;

    private Random random = new Random();

    /**
     * The publish seam (see {@link NewsPublisher}). Defaults to an unconditional info log
     * so publishes are observable before T-072/T-073 install the real publisher.
     */
    private NewsPublisher publisher = record -> StockMarketMod.LOGGER.info(
            "[NewsPlugin] Published news '{}' (uid {}, day {}) affecting {} market(s)",
            record.getEventId(), record.getNewsUid(), record.getGameDay(),
            record.getAffectedMarkets().size());

    public NewsPlugin() {
        super();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void init() {
        // Intentionally empty: the library must NOT load here. init() can run during
        // plugin-manager load, and matcher/ItemID resolution is only safe once the
        // update loop is ticking (post onBankSystemSetupComplete, plan §6.11).
    }

    @Override
    public void deInit() {
    }

    @Override
    public void onEnable() {
        // Reset the tick-delta measurement so the disabled period does not count as
        // elapsed time (it would be clamped to MAX_TICK_ADVANCE_MS anyway, but a clean
        // 0 keeps "disabled = frozen" exact).
        tickDeltaTimer.stop();
    }

    @Override
    public void onDisable() {
        // Nothing to do: the plugin manager stops calling update()/finalize() for
        // disabled plugins, which freezes all influence, event progress and timers.
    }

    // ── Update loop ──────────────────────────────────────────────────────

    /**
     * Advances the news timeline (scheduler, event ages, publishes, bake-ins).
     * The price influence itself is applied in {@link #finalize(List)} — see class Javadoc.
     */
    @Override
    public void update(List<MarketInterface> markets) {
        ensureLibraryLoaded();
        advanceTime(measureTickDelta(), markets);
    }

    /**
     * Applies the combined multiplicative news factor to every influenced market's
     * target price.
     * <p>
     * <b>Why finalize():</b> the plugin manager runs all plugins' {@code update()} first
     * (where VolatilityPlugin writes its absolute target), then all {@code finalize()},
     * and only afterwards commits the market cache. Applying the multiplication here
     * makes the result independent of plugin ordering. Invariant for future plugins:
     * never call {@code setTargetPrice} from {@code finalize()} — it would overwrite
     * the news influence (and any other finalize-stage modifier).
     */
    @Override
    public void finalize(List<MarketInterface> markets) {
        if (activeEvents.isEmpty()) return;
        for (MarketInterface marketInterface : markets) {
            ItemID marketID = marketInterface.market.getMarketID();
            Settings settings = settingsFor(marketID);
            if (!settings.newsEnabled()) continue;

            double combined = combinedFactorFor(marketID, settings.sensitivity(), true);
            if (combined == 1.0) continue;
            // Multiplicative on whatever target the update() stage produced:
            // newTarget = oldTarget * combined  <=>  += oldTarget * (combined - 1)
            marketInterface.market.addToTargetPrice(
                    marketInterface.market.getTargetPrice() * (combined - 1.0));
        }
    }

    /**
     * Advances every time-based part of the plugin by the given ticked milliseconds:
     * cooldowns, event ages (incl. due publishes and {@code reversal:none} bake-ins),
     * event retirement and the scheduler countdown.
     * <p>
     * Public so tests can drive the timeline deterministically instead of sleeping;
     * production code must only call it from {@link #update(List)}.
     *
     * @param elapsedMs the ticked milliseconds to advance (values &lt;= 0 are ignored
     *                  except for running due publishes/retirements)
     * @param markets   the subscribed markets of the current update cycle
     */
    public void advanceTime(long elapsedMs, List<MarketInterface> markets) {
        // T-098: capture pre-advance step indices for step-start chain detection.
        Map<Long, Integer> preAdvanceSteps = new HashMap<>();
        for (ActiveNewsEvent event : activeEvents) {
            preAdvanceSteps.put(event.getNewsUid(), event.currentStepIndex());
        }

        if (elapsedMs > 0) {
            // 1. Cooldowns tick down on the same paused-safe time basis as the events.
            cooldownRemainingMs.entrySet().removeIf(entry -> {
                long remaining = entry.getValue() - elapsedMs;
                if (remaining <= 0) return true;
                entry.setValue(remaining);
                return false;
            });

            // 2. Events age.
            for (ActiveNewsEvent event : activeEvents) {
                event.advance(elapsedMs);
            }
        }

        // 3. Due publishes (delay >= 0 publishes at age 0; delay < 0 publishes later).
        //    The publish-moment chain trigger (T-098) fires inside publishEvent() so that
        //    immediate publishes from activate() also fire their chains.
        for (ActiveNewsEvent event : activeEvents) {
            if (event.isPublishDue()) {
                publishEvent(event);
            }
        }

        // 3b. T-098: step-start chain triggers — detect step index changes per event.
        for (ActiveNewsEvent event : activeEvents) {
            long uid = event.getNewsUid();
            int currentIdx = event.currentStepIndex();
            // The last-seen index: on first tick after activation or load, the current
            // index is stored as "already seen" (no step-start fires for the initial step
            // on activation — publish fires instead; on load, already-passed steps are
            // not re-fired).
            Integer lastSeen = lastSeenStepIndices.get(uid);
            if (lastSeen == null) {
                // First encounter: record without firing (covers activation + load).
                lastSeenStepIndices.put(uid, preAdvanceSteps.getOrDefault(uid, currentIdx));
                lastSeen = lastSeenStepIndices.get(uid);
            }
            // If the step index changed, fire step-start chains for every crossed index.
            // This handles both normal transitions (lastSeen→lastSeen+1) and skips
            // (lastSeen→lastSeen+N, e.g. from skipPhase fast-forwarding past steps).
            if (currentIdx > lastSeen && event.activeMillis() >= 0) {
                for (int stepIdx = lastSeen + 1; stepIdx <= currentIdx; stepIdx++) {
                    String stepName = event.getSequence().getStep(stepIdx).name();
                    triggerChains(event, NewsEventDefinition.ChainTriggerMoment.STEP,
                            stepName);
                }
                lastSeenStepIndices.put(uid, currentIdx);
            }
        }

        // 4. Permanent last step (successor of reversal:none) — bake permanent shifts
        //    into the default price exactly once per market.
        for (ActiveNewsEvent event : activeEvents) {
            if (event.isPermanentShiftDue()) {
                bakePermanentShift(event, markets);
            }
        }

        // 5. Retirement. Events are kept until published even if their impact is already
        //    over (a strongly negative announce delay can outlive the whole sequence —
        //    allowed and flagged by validation). A permanent sequence stays until every
        //    market of its last step is baked (markets absent from the current tick's
        //    interface list keep the bake pending). Events that lost all their markets
        //    (unsubscribes) are dropped regardless.
        Iterator<ActiveNewsEvent> iterator = activeEvents.iterator();
        while (iterator.hasNext()) {
            ActiveNewsEvent event = iterator.next();
            boolean influenceOver = event.activeMillis() >= 0
                    && event.isSequenceOver()
                    && (!event.isPermanent() || event.getPendingBakeWeights().isEmpty());
            if ((event.isPublished() && influenceOver) || event.hasNoMarkets()) {
                // T-098: completion-moment chain trigger (natural retirement only).
                if (event.isPublished() && influenceOver) {
                    triggerChains(event, NewsEventDefinition.ChainTriggerMoment.COMPLETION,
                            null);
                }
                iterator.remove();
                lastSeenStepIndices.remove(event.getNewsUid());
                eventChainDepth.remove(event.getNewsUid());
                eventChainAncestry.remove(event.getNewsUid());
                info("News event '" + event.getDefinitionId() + "' (uid "
                        + event.getNewsUid() + ") retired");
            }
        }

        // 5b. T-098: tick and fire mature pending chain activations.
        if (elapsedMs > 0 && !pendingChainActivations.isEmpty()) {
            tickPendingChains(elapsedMs, markets);
        }

        // 6. Pre-scheduled queue (T-082): tick the head slot down, fire due slots, then
        //    refill the queue back to PLANNED_QUEUE_SIZE. A due slot fires exactly once —
        //    whether it activates something or lapses (caps, cooldowns, nothing eligible),
        //    the next attempt waits for the next planned slot instead of retrying every
        //    tick (same policy as the pre-T-082 single countdown).
        if (libraryLoaded) {
            if (elapsedMs > 0 && !plannedQueue.isEmpty()) {
                plannedQueue.get(0).offsetMs -= elapsedMs;
                while (!plannedQueue.isEmpty() && plannedQueue.get(0).offsetMs <= 0) {
                    PlannedSlot due = plannedQueue.remove(0);
                    // Deltas: the next slot's offset is relative to this slot's fire
                    // moment, so any overshoot (offsetMs went below 0) carries over.
                    if (!plannedQueue.isEmpty()) {
                        plannedQueue.get(0).offsetMs += due.offsetMs;
                    }
                    firePlannedSlot(due.eventId, markets);
                }
            }
            refillPlannedQueue(markets);

            // T-085: slots planned while nothing was eligible (server start before
            // markets subscribed, everything on cooldown, ...) stay time-only forever
            // even after events become eligible — which left the admin timeline without
            // any event names. Periodically re-offer such slots to the picker (keeping
            // their fire times); firePlannedSlot re-validates the pick anyway.
            if (elapsedMs > 0) {
                timeOnlyUpgradeAccumulatorMs += elapsedMs;
                if (timeOnlyUpgradeAccumulatorMs >= TIME_ONLY_UPGRADE_INTERVAL_MS) {
                    timeOnlyUpgradeAccumulatorMs = 0;
                    upgradeTimeOnlySlots(markets);
                }
            }
        }
    }

    /**
     * Assigns a planned event id to every time-only queue slot that can get one now
     * (T-085): each empty slot receives a fresh weighted pick among the currently
     * eligible events (excluding ids already planned in other slots — same rule as
     * {@link #refillPlannedQueue}). Fire times are kept; slots may stay time-only when
     * nothing (else) is eligible. Public so the scheduler test suite can drive it
     * directly without waiting for the {@link #TIME_ONLY_UPGRADE_INTERVAL_MS} throttle.
     *
     * @param markets the subscribed markets (pick eligibility source)
     */
    public void upgradeTimeOnlySlots(List<MarketInterface> markets) {
        for (PlannedSlot slot : plannedQueue) {
            if (!slot.eventId.isEmpty()) continue;
            slot.eventId = pickPlannedEventId(markets);
        }
    }

    // ── Scheduling ───────────────────────────────────────────────────────

    /**
     * Computes the events that are currently allowed to fire randomly.
     * Filters: not adminOnly, not admin-disabled ({@link #isEventEnabled}), weight &gt; 0,
     * off cooldown, not already active, and the matchers resolve to a non-empty subset
     * of the candidate markets (subscribed, news-enabled, below the per-market
     * active-event cap).
     * <p>
     * Public because the future admin tooling (T-076 {@code /stockmarket news list})
     * reuses it; also the unit-test entry point for eligibility rules.
     *
     * @param markets the subscribed markets of the current update cycle
     * @return all eligible events with their resolved market subsets (never null)
     */
    public List<EligibleEvent> computeEligibleEvents(List<MarketInterface> markets) {
        List<EligibleEvent> eligible = new ArrayList<>();
        List<ItemID> candidates = candidateMarkets(markets);
        if (candidates.isEmpty()) return eligible;

        // T-098: requirement-based eligibility — evaluate against the world registry
        // at wall-clock now. Requirements are checked both here (planning time) and
        // at fire time (firePlannedSlot re-calls this method; chain fires also re-check).
        NewsWorldRegistry registry = getNewsWorldRegistry();
        long nowEpochMs = System.currentTimeMillis();

        for (NewsEventDefinition definition : library.getDefinitions().values()) {
            if (definition.isAdminOnly()) continue;
            if (!isEventEnabled(definition.getId())) continue; // admin-disabled (T-081)
            if (definition.getWeight() <= 0) continue;
            if (isEventActive(definition.getId())) continue;
            if (cooldownRemainingMs.containsKey(definition.getId())) continue;
            // T-098: requirement filter — all requirements must hold right now.
            if (registry != null && !NewsRequirement.allMet(definition.getRequirements(), registry, nowEpochMs)) {
                continue;
            }
            Map<ItemID, Float> resolved = resolveEventMarkets(definition, candidates);
            if (!resolved.isEmpty()) {
                eligible.add(new EligibleEvent(definition, resolved));
            }
        }
        return eligible;
    }

    /**
     * The markets random events may currently impact: subscribed, {@code newsEnabled}
     * and below the <b>effective</b> {@code maxActiveEventsPerMarket} cap (admin override
     * over the file value, T-082).
     */
    private List<ItemID> candidateMarkets(List<MarketInterface> markets) {
        int perMarketCap = getEffectiveMaxActiveEventsPerMarket();
        List<ItemID> candidates = new ArrayList<>();
        for (MarketInterface marketInterface : markets) {
            ItemID marketID = marketInterface.market.getMarketID();
            if (!settingsFor(marketID).newsEnabled()) continue;
            if (activeEventCountOn(marketID) >= perMarketCap) continue;
            candidates.add(marketID);
        }
        return candidates;
    }

    /** @return the number of active events currently influencing the given market */
    private int activeEventCountOn(ItemID marketID) {
        int count = 0;
        for (ActiveNewsEvent event : activeEvents) {
            if (event.getMarketWeights().containsKey(marketID)) count++;
        }
        return count;
    }

    /** @return true if an event with the given definition id is currently active */
    public boolean isEventActive(String definitionId) {
        for (ActiveNewsEvent event : activeEvents) {
            if (event.getDefinitionId().equals(definitionId)) return true;
        }
        return false;
    }

    /**
     * Looks up the active event for a definition id (admin seam for T-076
     * {@code /stockmarket news stop}).
     *
     * @param definitionId the definition id
     * @return the active event, or null if no event with that id is running
     */
    public @Nullable ActiveNewsEvent findActiveEvent(String definitionId) {
        for (ActiveNewsEvent event : activeEvents) {
            if (event.getDefinitionId().equals(definitionId)) return event;
        }
        return null;
    }

    /**
     * Remaining cooldown of a definition id on the ticking time basis (admin seam for
     * T-076 {@code /stockmarket news list}).
     *
     * @param definitionId the definition id
     * @return the remaining cooldown in ticking milliseconds, or 0 when off cooldown
     */
    public long getCooldownRemainingMs(String definitionId) {
        return cooldownRemainingMs.getOrDefault(definitionId, 0L);
    }

    /**
     * Clears the remaining activation cooldown of one definition id (T-085, admin seam
     * for {@code NewsAdminRequest.Op.RESET_COOLDOWN} / the GUI's per-row reset button).
     * After the reset the event is immediately eligible again (subject to all other
     * eligibility rules — enabled state, activity caps, matched markets).
     *
     * @param definitionId the definition id whose cooldown should be cleared
     * @return true if a running cooldown was cleared, false if the id had no cooldown
     */
    public boolean resetCooldown(String definitionId) {
        return cooldownRemainingMs.remove(definitionId) != null;
    }

    /**
     * Whether an event id is enabled for activation (T-081). Unknown ids are enabled by
     * default — only ids explicitly disabled via {@link #setEventEnabled} return false.
     *
     * @param definitionId the definition id
     * @return true if the event may activate (both randomly and via admin trigger)
     */
    public boolean isEventEnabled(String definitionId) {
        return !disabledEventIds.contains(definitionId);
    }

    /**
     * Enables or disables one event id (T-081, {@code /stockmarket news enable|disable}).
     * Disabling only prevents <b>future</b> activations — the scheduler skips the event
     * and {@link #activate} refuses it; an already-active run keeps playing out (use
     * {@link #stopEvent} to end it). The id does not have to exist in the library: the
     * disabled state is kept for absent ids so a temporarily removed event file keeps
     * its state when re-added.
     *
     * @param definitionId the definition id
     * @param enabled      true to (re-)enable, false to disable
     */
    public void setEventEnabled(String definitionId, boolean enabled) {
        if (definitionId == null || definitionId.isEmpty()) return;
        if (enabled) {
            disabledEventIds.remove(definitionId);
        } else {
            disabledEventIds.add(definitionId);
        }
    }

    /**
     * @return an unmodifiable snapshot of the currently disabled definition ids (T-081);
     *         may contain ids whose definition is currently absent from the library
     */
    public Set<String> getDisabledEventIds() {
        return Set.copyOf(disabledEventIds);
    }

    /**
     * Resolves the market subset a <b>manual admin trigger</b> (T-076) would impact.
     * <p>
     * Unlike the random-scheduler eligibility ({@link #computeEligibleEvents}), this
     * deliberately bypasses cooldowns, the {@code adminOnly} flag, the event weight and
     * both activity caps — an admin trigger is an explicit override. What it still
     * respects: the event's matchers (matched subset only), the plugin subscription
     * (only subscribed markets are candidates) and the per-market {@code newsEnabled}
     * custom setting (an explicitly news-disabled market is never impacted, not even
     * manually).
     *
     * @param definition the definition to trigger
     * @param markets    the subscribed markets of the current cycle
     * @param restrictTo optional market restriction (test trigger on specific markets):
     *                   when non-null, the resolved subset is intersected with this
     *                   collection; null applies no restriction
     * @return the markets the trigger would impact → effective weight factor (insertion
     *         ordered, possibly empty; empty also when {@code restrictTo} excludes every
     *         matched market)
     */
    public Map<ItemID, Float> resolveAdminTriggerMarkets(@NotNull NewsEventDefinition definition,
                                                         List<MarketInterface> markets,
                                                         @Nullable Collection<ItemID> restrictTo) {
        List<ItemID> candidates = new ArrayList<>();
        for (MarketInterface marketInterface : markets) {
            ItemID marketID = marketInterface.market.getMarketID();
            if (!settingsFor(marketID).newsEnabled()) continue;
            candidates.add(marketID);
        }
        Map<ItemID, Float> resolved = resolveEventMarkets(definition, candidates);
        if (restrictTo != null) {
            resolved.keySet().retainAll(restrictTo);
        }
        return resolved;
    }

    /** Result of an admin {@link #stopEvent(ActiveNewsEvent)} call (T-076, hard-stop semantics since T-093). */
    public enum StopOutcome {
        /**
         * The impact had not started yet (pending positive announce delay): the event was
         * cancelled outright — no price influence was ever applied.
         */
        CANCELLED_BEFORE_IMPACT,
        /**
         * The event's sequence ends in a {@code permanent} step (legacy:
         * {@code reversal:none}) and its permanent shift had <b>not</b> been baked yet:
         * stop = cancel, so the pending shift was <b>reverted instead of baked</b> —
         * the default price stays untouched and the market snaps back to its pre-event
         * level (to <i>finalize</i> such an event early instead, fast-forward it with
         * {@link #skipPhase}).
         */
        CANCELLED_PERMANENT,
        /**
         * The event was terminated in whatever step it was in (legacy ramp-up/hold/
         * reversal or any sequence step): its price influence is removed immediately
         * and the market returns to its pre-event target on its own.
         */
        STOPPED
    }

    /**
     * <b>Hard-stops</b> one active event (admin seam for {@code /stockmarket news stop},
     * the GUI's Stop-all header button and the per-event Stop button; T-076, semantics
     * hardened in T-093). Stopping means <b>cancel, regardless of phase</b> — including
     * the reversal/recovery phase, which the pre-T-093 graceful stop used to leave
     * running:
     * <ul>
     *   <li>The event is removed from the active list immediately. Its multiplicative
     *       factor term disappears from the next {@link #finalize(List)} pass, and since
     *       the VolatilityPlugin re-derives its target from the (unchanged) default
     *       price every tick, the market price returns to its pre-event level on its
     *       own — no reversal fast-forward is needed anymore.</li>
     *   <li><b>Cooldown restarts from the stop moment:</b> the definition's full
     *       {@code cooldownSeconds} is re-armed, so the event must wait the entire
     *       cooldown again before it can fire. (If the definition has meanwhile vanished
     *       from the library, the full length is unknown — the activation-time cooldown
     *       simply keeps ticking as-is.)</li>
     *   <li><b>Permanent sequences (legacy {@code reversal:none}) are cancelled, not
     *       finalized:</b> a not-yet baked permanent shift is reverted — nothing bakes
     *       into the default price
     *       ({@link StopOutcome#CANCELLED_PERMANENT}). Markets whose bake already
     *       committed on an earlier tick keep it (a committed default-price change is
     *       not rolled back); to finalize a permanent event early on purpose, use
     *       {@link #skipPhase} instead — skip means fast-forward, stop means cancel.</li>
     * </ul>
     * <b>Publication suppression:</b> if the stopped event has not published its headline
     * yet (pending negative announce delay, or cancelled before a positive delay elapsed),
     * the publication never happens — players never get news about an event an admin
     * cancelled. Already-published events keep their history record; only the price
     * influence is removed.
     *
     * @param event the active event to stop (must belong to this plugin)
     * @return what the stop did (never null)
     */
    public StopOutcome stopEvent(@NotNull ActiveNewsEvent event) {
        // Hard removal (T-093): the event's factor term vanishes from the next
        // finalize() pass and can never publish/bake afterwards. Suppressed
        // publication is implicit — a removed event is never offered to the publisher.
        boolean suppressedPublication = !event.isPublished();
        activeEvents.remove(event);

        // T-098: discard pending chain activations whose source is this event and
        // clean up chain context maps.
        pendingChainActivations.removeIf(
                pca -> pca.sourceEventId.equals(event.getDefinitionId()));
        lastSeenStepIndices.remove(event.getNewsUid());
        eventChainDepth.remove(event.getNewsUid());
        eventChainAncestry.remove(event.getNewsUid());

        // Cooldown restarts from the stop moment (T-093): full length again, so the
        // event has to wait the entire cooldown before its next activation.
        NewsEventDefinition definition = library.getDefinition(event.getDefinitionId());
        if (definition != null && definition.getCooldownSeconds() > 0) {
            cooldownRemainingMs.put(event.getDefinitionId(),
                    definition.getCooldownSeconds() * 1000L);
        }

        StopOutcome outcome;
        if (event.activeMillis() < 0) {
            outcome = StopOutcome.CANCELLED_BEFORE_IMPACT;
        } else if (event.isPermanent() && !event.getPendingBakeWeights().isEmpty()) {
            // Un-baked markets of the permanent last step remain: their pending
            // permanent shift is reverted (T-093 doctrine holds for sequences too —
            // stop = cancel, regardless of which step is running).
            outcome = StopOutcome.CANCELLED_PERMANENT;
        } else {
            outcome = StopOutcome.STOPPED;
        }
        info("Admin stop terminated news event '" + event.getDefinitionId() + "' (uid "
                + event.getNewsUid() + "): " + outcome
                + (suppressedPublication ? ", publication suppressed" : "")
                + (definition != null && definition.getCooldownSeconds() > 0
                        ? ", cooldown restarted (" + definition.getCooldownSeconds() + " s)" : ""));
        return outcome;
    }

    /** Result of an admin {@link #skipPhase(ActiveNewsEvent)} call (T-093, step-based since T-095). */
    public enum SkipOutcome {
        /** Pending announce delay skipped — the impact (legacy ramp-up) starts now. */
        SKIPPED_TO_RAMP_UP,
        /** Legacy ramp-up skipped — the event now holds at its full peak influence. */
        SKIPPED_TO_HOLD,
        /** Legacy hold skipped — the influence now decays through its reversal/recovery. */
        SKIPPED_TO_REVERSAL,
        /**
         * The sequence was fast-forwarded past its <b>permanent</b> last step (legacy:
         * hold of a {@code reversal:none} event skipped), so the <b>full permanent shift
         * bakes into the default price on the next advance pass</b>, exactly like a
         * natural completion (skip = fast-forward, unlike stop = cancel; T-093 doctrine).
         */
        SKIPPED_TO_PERMANENT,
        /** The last step was skipped — the sequence is over, the event retires normally. */
        SKIPPED_TO_END,
        /**
         * A sequence-authored event was advanced to the start of its next step (T-095).
         * The reached step's name is available via
         * {@link ActiveNewsEvent#currentStepName()} — the outcome message must carry it.
         * Legacy events keep reporting the classic {@code SKIPPED_TO_*} constants.
         */
        SKIPPED_TO_STEP,
        /** The event is already in a terminal state (PERMANENT/EXPIRED) — nothing changed. */
        NOTHING_TO_SKIP
    }

    /**
     * Fast-forwards one active event out of its <b>current step</b> to the start of its
     * next step (T-093, generalized to sequences in T-095; admin seam for
     * {@code /stockmarket news skipphase} and the GUI's per-event Skip-phase button).
     * The skippable timeline:
     * <pre>
     *   PENDING (announce delay) → step 0 → step 1 → … → last step → ended
     * </pre>
     * Implemented by advancing the event's <b>age</b> ({@link ActiveNewsEvent#advance})
     * to the next step boundary ({@code stepStartMs(currentIndex + 1)}), so the whole
     * sequence math stays consistent — the publish moment lives on the same age line and
     * fast-forwards with it (a skipped-over publish becomes due and publishes on the
     * next advance pass; skip means fast-forward, so nothing is suppressed — unlike
     * {@link #stopEvent}). A price-factor jump at the boundary is expected and fine
     * (consumers anchor a target price); {@code instant}/{@code hold} curves jump at
     * boundaries by design.
     * <ul>
     *   <li>A pending announce delay skips to age 0 — the impact starts now (unchanged).</li>
     *   <li>Skipping the <b>last step</b> ends the event normally: it retires on the
     *       next advance pass and its activation-time cooldown keeps ticking unchanged
     *       (normal end-of-event handling — the cooldown is NOT re-armed, unlike a stop).</li>
     *   <li>Fast-forwarding past a <b>permanent</b> last step bakes the full permanent
     *       shift like a natural completion ({@link SkipOutcome#SKIPPED_TO_PERMANENT};
     *       T-093 doctrine: skip = fast-forward, stop = cancel).</li>
     *   <li>Terminal states (PERMANENT/EXPIRED) have no next step —
     *       {@link SkipOutcome#NOTHING_TO_SKIP}, the event is untouched.</li>
     * </ul>
     * Legacy {@code impact} events report the classic boundary constants
     * ({@code SKIPPED_TO_RAMP_UP/HOLD/REVERSAL}) mapped from their implicit step names;
     * sequence-authored events report {@link SkipOutcome#SKIPPED_TO_STEP} with the
     * reached step name readable via {@link ActiveNewsEvent#currentStepName()}.
     * <p>
     * The caller should follow up with {@code advanceTime(0, markets)} to realize due
     * publishes, bakes and retirements immediately instead of one tick later.
     *
     * @param event the active event to fast-forward (must belong to this plugin)
     * @return which boundary the event was advanced to (never null)
     */
    public SkipOutcome skipPhase(@NotNull ActiveNewsEvent event) {
        NewsSequence sequence = event.getSequence();
        long active = event.activeMillis();

        // Target active-time of the next step start. Every branch below yields a
        // target strictly greater than the current active time, so the age advance
        // is always positive (stepIndexAt never reports 0-duration steps, hence
        // stepStartMs(index + 1) > active holds inside the sequence).
        long targetActiveMs;
        if (active < 0) {
            targetActiveMs = 0; // pending announce delay → impact start
        } else if (active >= sequence.totalDurationMs()) {
            // PERMANENT/EXPIRED: terminal — there is no next step to skip to.
            return SkipOutcome.NOTHING_TO_SKIP;
        } else {
            targetActiveMs = sequence.stepStartMs(sequence.stepIndexAt(active) + 1);
        }
        event.advance(targetActiveMs - active);

        // Describe the boundary actually reached — zero-length steps collapse (e.g.
        // skipping a pending delay of a rampUp=0 legacy event lands directly in the hold).
        SkipOutcome outcome = describeReachedBoundary(event);
        info("Admin skip advanced news event '" + event.getDefinitionId() + "' (uid "
                + event.getNewsUid() + ") to " + outcome
                + (outcome == SkipOutcome.SKIPPED_TO_STEP
                        ? " '" + event.currentStepName() + "'" : ""));
        return outcome;
    }

    /**
     * Maps the state a skip landed in onto its {@link SkipOutcome}: terminal states
     * yield {@code SKIPPED_TO_PERMANENT}/{@code SKIPPED_TO_END}; running legacy events
     * map their implicit normalized step names back onto the classic constants
     * (byte-identical pre-T-095 behavior); running sequence-authored events yield
     * {@link SkipOutcome#SKIPPED_TO_STEP}.
     */
    private static SkipOutcome describeReachedBoundary(ActiveNewsEvent event) {
        if (event.isSequenceOver()) {
            return event.isPermanent()
                    ? SkipOutcome.SKIPPED_TO_PERMANENT : SkipOutcome.SKIPPED_TO_END;
        }
        if (event.getLegacyEnvelope() != null) {
            switch (event.currentStepName()) {
                case "ramp": return SkipOutcome.SKIPPED_TO_RAMP_UP;
                case "hold": return SkipOutcome.SKIPPED_TO_HOLD;
                case "reversal": return SkipOutcome.SKIPPED_TO_REVERSAL;
                default: break; // defensive — fall through to the generic step outcome
            }
        }
        return SkipOutcome.SKIPPED_TO_STEP;
    }

    /**
     * The current noise-free multiplicative factor term one active event contributes to
     * one market, including the market's sensitivity setting (display seam for the T-076
     * {@code /stockmarket news list} output; same math as the runtime stream).
     *
     * @param event    the active event
     * @param marketID the market
     * @return the factor term (1.0 = the event does not influence this market right now)
     */
    public double currentEventFactor(@NotNull ActiveNewsEvent event, ItemID marketID) {
        // Per-step markets (T-095): only the CURRENT step's map contributes influence —
        // a market that is merely in the event's union (other steps) sits at 1.0 now.
        Float weightFactor = event.currentStepWeights().get(marketID);
        if (weightFactor == null) return 1.0;
        double value = event.getSequence().value(event.activeMillis());
        return sequenceFactorTerm(value, weightFactor, settingsFor(marketID).sensitivity(), 0);
    }

    // ── Scheduler overrides + pre-scheduled queue (T-082) ───────────────

    /** @return effective minimum seconds between two random activations (override > file) */
    public long getEffectiveMinSecondsBetweenEvents() {
        return overrideMinSecondsBetweenEvents != null
                ? overrideMinSecondsBetweenEvents
                : library.getSchedulerConfig().getMinSecondsBetweenEvents();
    }

    /** @return effective maximum seconds between two random activations (override > file) */
    public long getEffectiveMaxSecondsBetweenEvents() {
        return overrideMaxSecondsBetweenEvents != null
                ? overrideMaxSecondsBetweenEvents
                : library.getSchedulerConfig().getMaxSecondsBetweenEvents();
    }

    /** @return effective cap of simultaneously active events overall (override > file) */
    public int getEffectiveMaxActiveEventsGlobal() {
        return overrideMaxActiveEventsGlobal != null
                ? overrideMaxActiveEventsGlobal
                : library.getSchedulerConfig().getMaxActiveEventsGlobal();
    }

    /** @return effective cap of simultaneously active events per market (override > file) */
    public int getEffectiveMaxActiveEventsPerMarket() {
        return overrideMaxActiveEventsPerMarket != null
                ? overrideMaxActiveEventsPerMarket
                : library.getSchedulerConfig().getMaxActiveEventsPerMarket();
    }

    /**
     * Snapshot of the four effective scheduler values + per-value override flags, in the
     * shape the runtime stream ships to the GUI (T-082).
     *
     * @return the current effective scheduler state (never null)
     */
    public RuntimeStreamData.SchedulerState getSchedulerState() {
        return new RuntimeStreamData.SchedulerState(
                getEffectiveMinSecondsBetweenEvents(), overrideMinSecondsBetweenEvents != null,
                getEffectiveMaxSecondsBetweenEvents(), overrideMaxSecondsBetweenEvents != null,
                getEffectiveMaxActiveEventsGlobal(), overrideMaxActiveEventsGlobal != null,
                getEffectiveMaxActiveEventsPerMarket(), overrideMaxActiveEventsPerMarket != null);
    }

    /**
     * Applies an admin scheduler-override change atomically (T-082, driven by
     * {@code NewsAdminRequest.Op.SET_SCHEDULER}).
     * <p>
     * <b>Per-value semantics</b> (each parameter independently):
     * <ul>
     *   <li>{@code null} — leave this value unchanged,</li>
     *   <li>{@code < 0} — clear the override, the JSON file value applies again,</li>
     *   <li>{@code >= 0} — set the override to this value (validated below).</li>
     * </ul>
     * {@code resetAll} clears all four overrides first; explicit values in the same call
     * are applied on top of the reset.
     * <p>
     * <b>Validation</b> (on the <i>resulting effective</i> values, so a min-only override
     * is also checked against the file's max): {@code 0 < min <= max} and both caps
     * {@code >= 1}. On any violation <b>nothing changes</b> and the violation message is
     * returned. When the effective min/max interval changed, the planned queue is
     * resampled ({@link #resamplePlannedQueue}) so the timeline reflects the new pacing
     * immediately.
     *
     * @param minSeconds   change for {@code minSecondsBetweenEvents} (see semantics above)
     * @param maxSeconds   change for {@code maxSecondsBetweenEvents}
     * @param maxGlobal    change for {@code maxActiveEventsGlobal}
     * @param maxPerMarket change for {@code maxActiveEventsPerMarket}
     * @param resetAll     true to clear all overrides before applying the explicit values
     * @param markets      the subscribed markets (used to re-plan the queue on interval change)
     * @return null on success, otherwise a human-readable rejection message (state unchanged)
     */
    public @Nullable String applySchedulerOverrides(@Nullable Long minSeconds, @Nullable Long maxSeconds,
                                                    @Nullable Integer maxGlobal, @Nullable Integer maxPerMarket,
                                                    boolean resetAll, List<MarketInterface> markets) {
        // 1. Compute the prospective override state without touching the fields yet.
        Long newMin = resetAll ? null : overrideMinSecondsBetweenEvents;
        Long newMax = resetAll ? null : overrideMaxSecondsBetweenEvents;
        Integer newGlobal = resetAll ? null : overrideMaxActiveEventsGlobal;
        Integer newPerMarket = resetAll ? null : overrideMaxActiveEventsPerMarket;
        if (minSeconds != null) newMin = minSeconds < 0 ? null : minSeconds;
        if (maxSeconds != null) newMax = maxSeconds < 0 ? null : maxSeconds;
        if (maxGlobal != null) newGlobal = maxGlobal < 0 ? null : maxGlobal;
        if (maxPerMarket != null) newPerMarket = maxPerMarket < 0 ? null : maxPerMarket;

        // 2. Validate the resulting EFFECTIVE values (override where set, file otherwise).
        NewsEventLibrary.SchedulerConfig config = library.getSchedulerConfig();
        long effMin = newMin != null ? newMin : config.getMinSecondsBetweenEvents();
        long effMax = newMax != null ? newMax : config.getMaxSecondsBetweenEvents();
        int effGlobal = newGlobal != null ? newGlobal : config.getMaxActiveEventsGlobal();
        int effPerMarket = newPerMarket != null ? newPerMarket : config.getMaxActiveEventsPerMarket();
        if (effMin <= 0) {
            return "minSecondsBetweenEvents must be > 0 (got " + effMin + ")";
        }
        if (effMin > effMax) {
            return "minSecondsBetweenEvents (" + effMin + ") must be <= maxSecondsBetweenEvents ("
                    + effMax + ")";
        }
        if (effGlobal < 1) {
            return "maxActiveEventsGlobal must be >= 1 (got " + effGlobal + ")";
        }
        if (effPerMarket < 1) {
            return "maxActiveEventsPerMarket must be >= 1 (got " + effPerMarket + ")";
        }

        // 3. Commit. Resample the timeline when the sampling interval changed.
        boolean intervalChanged = effMin != getEffectiveMinSecondsBetweenEvents()
                || effMax != getEffectiveMaxSecondsBetweenEvents();
        overrideMinSecondsBetweenEvents = newMin;
        overrideMaxSecondsBetweenEvents = newMax;
        overrideMaxActiveEventsGlobal = newGlobal;
        overrideMaxActiveEventsPerMarket = newPerMarket;
        if (intervalChanged) {
            resamplePlannedQueue(markets);
        }
        return null;
    }

    /**
     * Snapshot of the pre-scheduled activation queue for display (runtime stream / admin
     * command output), soonest first. ETAs are cumulative ticking-ms until each slot
     * fires (the stored per-slot deltas are summed up).
     *
     * @return the planned slots as {@code (eventId — empty for time-only slots, etaMs)}
     */
    public List<RuntimeStreamData.UpcomingEvent> getPlannedActivations() {
        List<RuntimeStreamData.UpcomingEvent> upcoming = new ArrayList<>(plannedQueue.size());
        long etaMs = 0;
        for (PlannedSlot slot : plannedQueue) {
            etaMs += slot.offsetMs;
            upcoming.add(new RuntimeStreamData.UpcomingEvent(slot.eventId, etaMs));
        }
        return upcoming;
    }

    /**
     * Discards the whole planned queue and re-plans it from scratch with freshly sampled
     * intervals (T-082). Used when the effective min/max interval changes
     * ({@link #applySchedulerOverrides}) — keeping the old fire times would pace the
     * timeline with the outdated interval for up to {@link #PLANNED_QUEUE_SIZE} slots.
     *
     * @param markets the subscribed markets (planning-time eligibility source)
     */
    public void resamplePlannedQueue(List<MarketInterface> markets) {
        plannedQueue.clear();
        if (libraryLoaded) {
            refillPlannedQueue(markets);
        }
    }

    /**
     * Re-validates the planned event ids against the current library state (T-082),
     * <b>keeping every slot's fire time</b>. A planned id is invalid when its definition
     * is no longer loaded, admin-disabled, {@code adminOnly} or has weight 0 — such a
     * slot gets a fresh weighted repick among the currently eligible events (excluding
     * ids already planned in other slots) or becomes a time-only slot when nothing is
     * eligible. Called by {@link #reloadLibrary()}; public so tests can drive it after a
     * direct {@code getLibrary().reload(dir)}.
     *
     * @param markets the subscribed markets (repick eligibility source)
     */
    public void revalidatePlannedQueue(List<MarketInterface> markets) {
        if (plannedQueue.isEmpty()) return;
        for (PlannedSlot slot : plannedQueue) {
            if (slot.eventId.isEmpty() || isPlannableEventId(slot.eventId)) continue;
            slot.eventId = pickPlannedEventId(markets);
        }
    }

    /**
     * Whether an event id may (still) be planned into a queue slot: loaded, not
     * admin-disabled, not {@code adminOnly} and weight &gt; 0. Deliberately ignores
     * cooldown/active/cap state — those are transient and re-checked at fire time.
     */
    private boolean isPlannableEventId(String eventId) {
        NewsEventDefinition definition = library.getDefinition(eventId);
        return definition != null
                && isEventEnabled(eventId)
                && !definition.isAdminOnly()
                && definition.getWeight() > 0;
    }

    /**
     * Tops the planned queue back up to {@link #PLANNED_QUEUE_SIZE} slots. Each new
     * slot's delta is sampled uniformly from the effective min/max interval and its
     * event id is a weighted pick among the currently eligible events — excluding ids
     * already planned in earlier slots, so the visible timeline does not predict the
     * same event five times. When nothing (else) is eligible the slot is planned
     * time-only (empty id, event decided at fire time).
     */
    private void refillPlannedQueue(List<MarketInterface> markets) {
        while (plannedQueue.size() < PLANNED_QUEUE_SIZE) {
            plannedQueue.add(new PlannedSlot(sampleSchedulerIntervalMs(), pickPlannedEventId(markets)));
        }
    }

    /**
     * Planning-time event pick for one queue slot: weighted among the currently eligible
     * events, minus the ids already planned in the queue.
     *
     * @return the picked definition id, or an empty string for a time-only slot
     */
    private String pickPlannedEventId(List<MarketInterface> markets) {
        List<EligibleEvent> eligible = computeEligibleEvents(markets);
        eligible.removeIf(candidate -> {
            for (PlannedSlot slot : plannedQueue) {
                if (slot.eventId.equals(candidate.definition().getId())) return true;
            }
            return false;
        });
        EligibleEvent picked = pickWeighted(eligible, random.nextDouble());
        return picked != null ? picked.definition().getId() : "";
    }

    /**
     * Fires one due queue slot (T-082): re-validates the planned event id against the
     * <i>current</i> state and activates it — or repicks/lapses:
     * <ul>
     *   <li>The effective global cap gates the whole attempt (unchanged fire-time rule).</li>
     *   <li>The planned id must still be eligible <b>now</b> (enabled, off cooldown, not
     *       active, matched markets still subscribed/news-enabled/under the per-market
     *       cap) — {@link #computeEligibleEvents} re-checks all of that.</li>
     *   <li>A no-longer-eligible or time-only slot falls back to a fresh weighted pick
     *       among the currently eligible events (pre-T-082 behavior).</li>
     *   <li>If nothing is eligible, the slot lapses — no retry until the next slot.</li>
     * </ul>
     */
    private void firePlannedSlot(String plannedEventId, List<MarketInterface> markets) {
        if (activeEvents.size() >= getEffectiveMaxActiveEventsGlobal()) {
            return; // slot lapses at the global cap
        }
        List<EligibleEvent> eligible = computeEligibleEvents(markets);
        EligibleEvent chosen = null;
        if (!plannedEventId.isEmpty()) {
            for (EligibleEvent candidate : eligible) {
                if (candidate.definition().getId().equals(plannedEventId)) {
                    chosen = candidate;
                    break;
                }
            }
        }
        if (chosen == null) {
            // Planned event no longer eligible (or time-only slot): repick at fire time.
            chosen = pickWeighted(eligible, random.nextDouble());
        }
        if (chosen != null) {
            // Per-step matchers resolve against the same candidate set the eligibility
            // pass used: subscribed ∩ news-enabled ∩ under the per-market cap (T-095).
            activate(chosen.definition(), chosen.resolvedMarkets(), candidateMarkets(markets));
        }
    }

    /**
     * Weighted random pick: each event's probability is proportional to its
     * {@code weight}. Pure function for unit testing.
     *
     * @param eligible the candidates (weights must be &gt;= 0)
     * @param roll     a uniform random number in {@code [0, 1)}
     * @return the picked event, or null if the list is empty or all weights are 0
     */
    public static @Nullable EligibleEvent pickWeighted(List<EligibleEvent> eligible, double roll) {
        double totalWeight = 0;
        for (EligibleEvent candidate : eligible) {
            totalWeight += Math.max(0, candidate.definition().getWeight());
        }
        if (!(totalWeight > 0)) return null;

        double target = Math.min(Math.max(roll, 0), Math.nextDown(1.0)) * totalWeight;
        double cumulative = 0;
        for (EligibleEvent candidate : eligible) {
            cumulative += Math.max(0, candidate.definition().getWeight());
            if (target < cumulative) return candidate;
        }
        return eligible.get(eligible.size() - 1); // numeric edge: fall back to the last entry
    }

    /**
     * Activates an event on the given resolved market subset (convenience form).
     * Per-step matcher lists resolve against the currently subscribed news-enabled
     * markets ({@link #getMarketInterfaces()} — the admin-trigger candidate set, no
     * per-market-cap filter) united with the given resolved subset; the scheduler path
     * uses the explicit-candidate overload with its capped candidate set instead.
     *
     * @param definition      the event definition to activate
     * @param resolvedMarkets the event-level market subset → effective weight factor
     * @return the created active event, or null (see the main overload)
     */
    public @Nullable ActiveNewsEvent activate(@NotNull NewsEventDefinition definition,
                                              @NotNull Map<ItemID, Float> resolvedMarkets) {
        return activate(definition, resolvedMarkets, defaultStepCandidates(resolvedMarkets));
    }

    /**
     * Activates an event on the given resolved market subset (T-095 sequence runtime):
     * <ol>
     *   <li>picks ONE of the definition's sequences by per-sequence weight,</li>
     *   <li>rolls every step's concrete duration uniformly from its {@code [min, max]}
     *       range,</li>
     *   <li>resolves every step's own {@code markets[]} matchers against
     *       {@code stepCandidates} (steps without own matchers inherit the event-level
     *       {@code resolvedMarkets}),</li>
     *   <li>samples the announce delay, assigns the monotonic news uid and freezes
     *       everything into a self-contained {@link ActiveNewsEvent} — a restart never
     *       re-rolls and a library reload never affects the running event,</li>
     *   <li>starts the cooldown and — for non-negative delays — publishes immediately.</li>
     * </ol>
     * All rolls use the plugin RNG ({@link #test_setRandom}); single-sequence events and
     * fixed durations consume <b>no</b> random values, so legacy events draw the exact
     * same RNG stream as before T-095 (announce delay only).
     * <p>
     * Public seam for the admin trigger (T-076) and for tests. Callers are responsible
     * for eligibility; this method only refuses an id that is already active or that an
     * admin has <b>disabled</b> ({@link #setEventEnabled}) — the disabled check lives
     * here (defense in depth, T-081) so no caller, present or future, can activate a
     * disabled event. It also refuses when no market at all resolves for any step
     * (an event without a single impacted market must not run).
     *
     * @param definition      the event definition to activate
     * @param resolvedMarkets the event-level market subset the event impacts
     *                        → effective weight factor (inherited by steps without
     *                        their own matcher list)
     * @param stepCandidates  the candidate markets per-step matcher lists resolve
     *                        against (scheduler: subscribed ∩ news-enabled ∩ under the
     *                        per-market cap; admin trigger: subscribed ∩ news-enabled)
     * @return the created active event, or null if the id is already active, disabled,
     *         the market subset is empty or no step resolves any market
     */
    public @Nullable ActiveNewsEvent activate(@NotNull NewsEventDefinition definition,
                                              @NotNull Map<ItemID, Float> resolvedMarkets,
                                              @NotNull Collection<ItemID> stepCandidates) {
        if (resolvedMarkets.isEmpty() || isEventActive(definition.getId())) return null;
        if (!isEventEnabled(definition.getId())) {
            warn("Refused to activate disabled news event '" + definition.getId() + "'");
            return null;
        }
        List<NewsEventDefinition.SequenceDefinition> sequences = definition.getSequences();
        if (sequences.isEmpty()) {
            // Cannot happen for parsed definitions (getSequences() is never empty) —
            // defensive against hand-built test definitions.
            warn("Refused to activate news event '" + definition.getId() + "' without sequences");
            return null;
        }

        // 1. Pick ONE sequence by weight. Single-sequence events (all legacy events)
        //    consume no RNG value — keeps the legacy random stream unchanged.
        int sequenceIndex = sequences.size() == 1
                ? 0 : pickSequenceIndex(sequences, random.nextDouble());
        NewsEventDefinition.SequenceDefinition sequenceDefinition = sequences.get(sequenceIndex);

        // 2. Roll every step's concrete duration (fixed durations consume no RNG value)
        //    and resolve every step's market map. Both are frozen into the event below.
        List<NewsEventDefinition.StepDefinition> steps = sequenceDefinition.getSteps();
        long[] rolledDurationsMs = new long[steps.size()];
        List<Map<ItemID, Float>> stepMarkets = new ArrayList<>(steps.size());
        boolean anyMarket = false;
        for (int i = 0; i < steps.size(); i++) {
            NewsEventDefinition.StepDefinition step = steps.get(i);
            rolledDurationsMs[i] = sampleUniformMs(step.getDurationMinMs(), step.getDurationMaxMs());
            Map<ItemID, Float> stepMap =
                    resolveStepMarkets(definition, step, resolvedMarkets, stepCandidates);
            stepMarkets.add(stepMap);
            anyMarket |= !stepMap.isEmpty();
        }
        if (!anyMarket) {
            warn("Refused to activate news event '" + definition.getId()
                    + "': no step resolves any market");
            return null;
        }
        NewsSequence sequence = sequenceDefinition.createSequence(rolledDurationsMs);

        // 3. Announce delay + freeze. The legacy envelope (null for sequence-authored
        //    events) rides along as a record/phase-name descriptor snapshot.
        long delayMs = sampleAnnounceDelayMs(definition.getAnnounceDelayMs());
        ActiveNewsEvent event = new ActiveNewsEvent(definition.getId(), ++newsUidCounter,
                definition.getHeadline(), definition.getText(), definition.getImpact(),
                sequenceDefinition.getName(), sequence, stepMarkets, delayMs);
        activeEvents.add(event);

        // Cooldown starts at activation ("after firing"), on the ticking time basis.
        if (definition.getCooldownSeconds() > 0) {
            cooldownRemainingMs.put(definition.getId(), definition.getCooldownSeconds() * 1000L);
        }

        info("Activated news event '" + definition.getId() + "' (uid " + event.getNewsUid()
                + ", sequence '" + sequenceDefinition.getName() + "' with " + steps.size()
                + " step(s), delay " + delayMs + " ms) on "
                + event.getMarketWeights().size() + " market(s)");

        // Non-negative delay = headline first: publish right away, impact follows later.
        if (event.isPublishDue()) {
            publishEvent(event);
        }
        return event;
    }

    /**
     * Weighted random pick of a sequence index — the per-event counterpart of
     * {@link #pickWeighted} for the {@code sequences[]} weights (T-095). Pure function
     * for unit testing.
     *
     * @param sequences the event's sequence definitions (weights are {@code > 0} by
     *                  parse validation; non-positive weights are treated as 0 here)
     * @param roll      a uniform random number in {@code [0, 1)}
     * @return the picked index in {@code [0, sequences.size())}; 0 when all weights are 0
     */
    public static int pickSequenceIndex(List<NewsEventDefinition.SequenceDefinition> sequences,
                                        double roll) {
        double totalWeight = 0;
        for (NewsEventDefinition.SequenceDefinition sequence : sequences) {
            totalWeight += Math.max(0, sequence.getWeight());
        }
        if (!(totalWeight > 0)) return 0;

        double target = Math.min(Math.max(roll, 0), Math.nextDown(1.0)) * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < sequences.size(); i++) {
            cumulative += Math.max(0, sequences.get(i).getWeight());
            if (target < cumulative) return i;
        }
        return sequences.size() - 1; // numeric edge: fall back to the last entry
    }

    /**
     * Resolves one step's market map at activation time (T-095): a step with its own
     * {@code markets[]} list resolves it against the candidate markets (same
     * first-match-wins semantics as the event level,
     * {@link NewsEventDefinition#resolveMatchers}); a step without one inherits the
     * event-level resolution. Protected so tests can bypass the item-registry-dependent
     * matcher resolution (same seam pattern as {@link #resolveEventMarkets}).
     *
     * @param definition          the definition being activated (context for overrides)
     * @param step                the step whose markets to resolve
     * @param eventLevelResolved  the event-level resolved subset (inheritance source)
     * @param stepCandidates      the candidate markets for per-step matcher lists
     * @return the step's resolved market → weight map (never null, possibly empty)
     */
    protected Map<ItemID, Float> resolveStepMarkets(NewsEventDefinition definition,
                                                    NewsEventDefinition.StepDefinition step,
                                                    Map<ItemID, Float> eventLevelResolved,
                                                    Collection<ItemID> stepCandidates) {
        if (step.getMarkets() == null) {
            return new LinkedHashMap<>(eventLevelResolved); // inherit event-level markets
        }
        return NewsEventDefinition.resolveMatchers(step.getMarkets(), stepCandidates);
    }

    /**
     * The default per-step candidate set for the convenience {@code activate} overload
     * (admin trigger / tests): every subscribed news-enabled market, united with the
     * event-level resolved subset (covers contexts without a live market cache).
     */
    private Collection<ItemID> defaultStepCandidates(Map<ItemID, Float> resolvedMarkets) {
        Set<ItemID> candidates = new LinkedHashSet<>(resolvedMarkets.keySet());
        try {
            for (MarketInterface marketInterface : getMarketInterfaces()) {
                ItemID marketID = marketInterface.market.getMarketID();
                if (settingsFor(marketID).newsEnabled()) {
                    candidates.add(marketID);
                }
            }
        } catch (Exception ignored) {
            // No market cache (unit-test context) — the resolved subset is enough.
        }
        return candidates;
    }

    /** Samples one uniform random delay (ms) from the definition's announce range. */
    private long sampleAnnounceDelayMs(NewsEventDefinition.AnnounceDelayRange range) {
        return sampleUniformMs(range.minMs(), range.maxMs());
    }

    /**
     * Samples one uniform random value from {@code [minMs, maxMs]} (inclusive).
     * Degenerate ranges ({@code min >= max}) return {@code min} <b>without consuming a
     * random value</b> — load-bearing for the legacy RNG-stream compatibility of
     * {@link #activate} (fixed step durations must not shift the announce-delay roll).
     */
    private long sampleUniformMs(long minMs, long maxMs) {
        if (minMs >= maxMs) return minMs;
        double span = (double) maxMs - (double) minMs + 1.0;
        long sampled = minMs + (long) Math.floor(random.nextDouble() * span);
        return Math.min(sampled, maxMs);
    }

    /**
     * Samples one uniform random scheduler interval (ms) from the <b>effective</b>
     * min/max seconds (admin override where set, JSON file value otherwise — T-082).
     */
    private long sampleSchedulerIntervalMs() {
        long minMs = getEffectiveMinSecondsBetweenEvents() * 1000L;
        long maxMs = getEffectiveMaxSecondsBetweenEvents() * 1000L;
        if (minMs >= maxMs) return Math.max(1, minMs);
        double span = (double) maxMs - (double) minMs + 1.0;
        long sampled = minMs + (long) Math.floor(random.nextDouble() * span);
        return Math.max(1, Math.min(sampled, maxMs));
    }

    // ── Publishing ───────────────────────────────────────────────────────

    /**
     * Builds the {@link NewsRecord} for an event, hands it to the publisher seam and
     * fires the publish-moment chain triggers (T-098). The chain trigger lives here
     * instead of in {@link #advanceTime} so that immediate publishes from
     * {@link #activate(NewsEventDefinition, Map, Collection)} — where
     * {@link #advanceTime}'s publish loop has already run — also fire their chains.
     */
    private void publishEvent(ActiveNewsEvent event) {
        NewsRecord record = buildRecord(event);
        event.markPublished();
        try {
            publisher.publish(record);
        } catch (Exception e) {
            // The publisher must never break the plugin update loop.
            StockMarketMod.LOGGER.error("[NewsPlugin] News publisher failed for event '"
                    + event.getDefinitionId() + "'", e);
        }

        // T-098: publish-moment chain trigger. Runs for every publish path —
        // scheduled (advanceTime → isPublishDue), immediate (activate → delay-0),
        // and chain-fired (activateChainTarget → activate → delay-0).
        triggerChains(event, NewsEventDefinition.ChainTriggerMoment.PUBLISH, null);
    }

    /**
     * Builds the publishable record from an active event's snapshots. The affected-market
     * list is the actually-impacted <b>union</b> subset (all steps' markets, active +
     * already-baked) with the item registry names captured now, so history renders after
     * market deletion.
     * <p>
     * Impact descriptor fields (T-095): legacy {@code impact} events keep their exact
     * pre-sequence values (type/peakFactor/reversal from the envelope descriptor
     * snapshot). Sequence-authored events have no envelope — they publish
     * {@code impactType = "sequence"}, the largest-magnitude step target as the peak
     * (signed), {@code reversal = "none"} for permanent sequences (the shift bakes) or
     * {@code "sequence"} otherwise, and the sequence's total length as the duration
     * (drives the history LIVE badge).
     * <p>
     * T-099: the record additionally carries the picked sequence's <b>name</b> —
     * empty for legacy events, whose implicit "impact" sequence is a normalization
     * detail rather than authored content — and the resolved <b>step count</b>
     * (legacy events report their implicit 3 normalization steps).
     */
    private NewsRecord buildRecord(ActiveNewsEvent event) {
        List<NewsRecord.AffectedMarket> affected = new ArrayList<>();
        Map<ItemID, Float> allMarkets = new LinkedHashMap<>(event.getMarketWeights());
        allMarkets.putAll(event.getBakedMarkets());
        for (Map.Entry<ItemID, Float> entry : allMarkets.entrySet()) {
            String itemName;
            try {
                itemName = entry.getKey().getName();
            } catch (Exception e) {
                itemName = String.valueOf(entry.getKey().getShort());
            }
            affected.add(new NewsRecord.AffectedMarket(entry.getKey(), itemName, entry.getValue()));
        }

        NewsImpactEnvelope envelope = event.getLegacyEnvelope();
        String impactType;
        float peakFactor;
        String reversal;
        if (envelope != null) {
            // Legacy impact event: byte-identical record fields to pre-T-095.
            impactType = envelope.getType().jsonName();
            peakFactor = (float) envelope.getPeakFactor();
            reversal = envelope.getReversal().jsonName();
        } else {
            NewsSequence sequence = event.getSequence();
            impactType = "sequence";
            peakFactor = (float) peakSequenceValue(sequence);
            reversal = sequence.isPermanent() ? "none" : "sequence";
        }
        int totalDurationSeconds = (int) Math.min(Integer.MAX_VALUE,
                event.getSequence().totalDurationMs() / 1000L);
        // T-099: sequence descriptor — the name only for genuinely authored sequences
        // (legacy events publish an empty name), the step count always.
        String sequenceName = envelope != null ? "" : event.getSequenceName();
        return new NewsRecord(event.getNewsUid(), event.getDefinitionId(),
                currentEpochMs(), currentGameDay(),
                event.getHeadline(), event.getText(), affected,
                impactType, peakFactor, reversal, totalDurationSeconds,
                sequenceName, event.getSequence().stepCount());
    }

    /**
     * The largest-magnitude (signed) step target of a sequence — the closest analogue
     * of the legacy {@code peakFactor} for sequence-authored records. Pure function.
     *
     * @param sequence the resolved sequence
     * @return the target value with the largest absolute magnitude across all steps
     */
    public static double peakSequenceValue(NewsSequence sequence) {
        double peak = 0;
        for (NewsSequence.Step step : sequence.getSteps()) {
            if (Math.abs(step.targetValue()) > Math.abs(peak)) {
                peak = step.targetValue();
            }
        }
        return peak;
    }

    /**
     * Replaces the publish seam. T-072/T-073 install the production publisher here
     * (history append + {@code NewsPublishedPacket} broadcast) during master setup.
     *
     * @param publisher the new publisher; null restores nothing and is ignored
     */
    public void setPublisher(@Nullable NewsPublisher publisher) {
        if (publisher != null) this.publisher = publisher;
    }

    // ── Chains runtime (T-098) ─────────────────────────────────────────

    /**
     * Evaluates the chain entries of a source event at a given trigger moment and
     * enqueues {@link PendingChainActivation}s for those whose chance roll succeeds.
     * Called from {@link #advanceTime} at three moments:
     * <ol>
     *   <li>{@link NewsEventDefinition.ChainTriggerMoment#PUBLISH PUBLISH} — when the
     *       event publishes its headline.</li>
     *   <li>{@link NewsEventDefinition.ChainTriggerMoment#STEP STEP} — when the event
     *       crosses a step boundary (including steps skipped by
     *       {@link #skipPhase}).</li>
     *   <li>{@link NewsEventDefinition.ChainTriggerMoment#COMPLETION COMPLETION} — when
     *       the event retires naturally (NOT on admin stop).</li>
     * </ol>
     * Chains invalidated by post-merge validation ({@link NewsEventLibrary#isChainValid})
     * are silently skipped.
     *
     * @param sourceEvent the active event whose chains to evaluate
     * @param moment      the trigger moment
     * @param stepName    the step name for {@code STEP} triggers, null otherwise
     */
    private void triggerChains(ActiveNewsEvent sourceEvent,
                               NewsEventDefinition.ChainTriggerMoment moment,
                               @Nullable String stepName) {
        NewsEventDefinition definition = library.getDefinition(sourceEvent.getDefinitionId());
        if (definition == null) return;

        List<NewsEventDefinition.ChainDefinition> chains = definition.getChains();
        if (chains.isEmpty()) return;

        // Source event's chain context: scheduler/admin-fired = depth 0, empty ancestry.
        int sourceDepth = eventChainDepth.getOrDefault(sourceEvent.getNewsUid(), 0);
        Set<String> sourceAncestry = eventChainAncestry.getOrDefault(
                sourceEvent.getNewsUid(), Collections.emptySet());

        for (int i = 0; i < chains.size(); i++) {
            NewsEventDefinition.ChainDefinition chain = chains.get(i);

            // Skip chains invalidated by post-merge validation.
            if (!library.isChainValid(sourceEvent.getDefinitionId(), i)) continue;

            // Match trigger moment.
            if (chain.on() != moment) continue;

            // For STEP triggers, match the step name.
            if (moment == NewsEventDefinition.ChainTriggerMoment.STEP
                    && (chain.stepName() == null || !chain.stepName().equals(stepName))) {
                continue;
            }

            // Depth check: the new PCA's depth is sourceDepth + 1.
            int newDepth = sourceDepth + 1;
            if (newDepth > MAX_CHAIN_DEPTH) {
                info("Chain " + sourceEvent.getDefinitionId() + " -> "
                        + chain.targetEventId() + " blocked: depth " + newDepth
                        + " exceeds MAX_CHAIN_DEPTH " + MAX_CHAIN_DEPTH);
                continue;
            }

            // Ancestry check: target must not appear in the ancestry (prevents A->B->A).
            if (sourceAncestry.contains(chain.targetEventId())) {
                info("Chain " + sourceEvent.getDefinitionId() + " -> "
                        + chain.targetEventId() + " blocked: target is in ancestry "
                        + sourceAncestry);
                continue;
            }

            // Roll chance. Chance of 1.0 always passes (no random consumed).
            if (chain.chance() < 1.0 && random.nextDouble() >= chain.chance()) continue;

            // Roll delay from the chain's [min, max] range.
            long delayMs = sampleUniformMs(chain.delayMinMs(), chain.delayMaxMs());

            // Build the new ancestry: source's ancestry + source event's own id.
            Set<String> newAncestry = new LinkedHashSet<>(sourceAncestry);
            newAncestry.add(sourceEvent.getDefinitionId());

            // Capture market override when sameMarkets is true.
            @Nullable Map<ItemID, Float> marketsOverride = chain.sameMarkets()
                    ? new LinkedHashMap<>(sourceEvent.getMarketWeights()) : null;

            pendingChainActivations.add(new PendingChainActivation(
                    chain.targetEventId(), delayMs,
                    sourceEvent.getDefinitionId(), newDepth,
                    newAncestry, marketsOverride));

            info("Chain enqueued: " + sourceEvent.getDefinitionId() + " -> "
                    + chain.targetEventId() + " (depth " + newDepth
                    + ", delay " + delayMs + " ms"
                    + (chain.sameMarkets() ? ", sameMarkets" : "") + ")");
        }
    }

    /**
     * Ticks down all pending chain activations and fires those whose delay has expired.
     * Matured activations are collected first to avoid {@link java.util.ConcurrentModificationException}
     * when a chain-fired event's own publish-moment chains add new PCAs during activation.
     *
     * @param elapsedMs the ticked milliseconds to subtract from each PCA's delay
     * @param markets   the subscribed markets (for target event market resolution)
     */
    private void tickPendingChains(long elapsedMs, List<MarketInterface> markets) {
        // Collect matured activations before processing to avoid ConcurrentModificationException:
        // activateChainTarget may call triggerChains which adds to pendingChainActivations.
        List<PendingChainActivation> matured = new ArrayList<>();
        Iterator<PendingChainActivation> iter = pendingChainActivations.iterator();
        while (iter.hasNext()) {
            PendingChainActivation pca = iter.next();
            pca.remainingMs -= elapsedMs;
            if (pca.remainingMs <= 0) {
                iter.remove();
                matured.add(pca);
            }
        }
        // Fire mature PCAs. This may add new PCAs to pendingChainActivations (via
        // triggerChains on the newly activated event if it publishes immediately);
        // those will be ticked on the next advanceTime call.
        for (PendingChainActivation pca : matured) {
            activateChainTarget(pca, markets);
        }
    }

    /**
     * Activates a chain target event from a matured {@link PendingChainActivation}.
     * <p>
     * <b>Caps bypass (plan §10.2 decision):</b> chain-fired events ignore both the global
     * and per-market activity caps — a chain is an explicit causal consequence, not a
     * random scheduler roll.
     * <p>
     * <b>AdminOnly block (plan §10.3 decision):</b> chain-fired events may NOT target
     * {@code adminOnly} definitions — those are reserved for manual admin triggers only.
     * <p>
     * Re-checks at fire time: target exists in library, enabled, not adminOnly, not
     * already active, requirements met (wall-clock now), depth &lt; MAX_CHAIN_DEPTH,
     * target not in ancestry, resolved markets non-empty.
     *
     * @param pca     the matured pending chain activation
     * @param markets the subscribed markets of the current update cycle
     * @return the activated event, or null if any check failed
     */
    private @Nullable ActiveNewsEvent activateChainTarget(PendingChainActivation pca,
                                                           List<MarketInterface> markets) {
        // Look up the target definition.
        NewsEventDefinition definition = library.getDefinition(pca.targetEventId);
        if (definition == null) {
            info("Chain target '" + pca.targetEventId + "' not found in library — skipping");
            return null;
        }

        // AdminOnly block (plan §10.3).
        if (definition.isAdminOnly()) {
            info("Chain target '" + pca.targetEventId + "' is adminOnly — skipping");
            return null;
        }

        // Enabled check (admin-disabled events cannot be chain-fired either).
        if (!isEventEnabled(pca.targetEventId)) {
            info("Chain target '" + pca.targetEventId + "' is disabled — skipping");
            return null;
        }

        // Already-active check (an event cannot have two simultaneous instances).
        if (isEventActive(pca.targetEventId)) {
            info("Chain target '" + pca.targetEventId + "' is already active — skipping");
            return null;
        }

        // Re-check depth at fire time (could have changed if ancestry was modified).
        if (pca.depth > MAX_CHAIN_DEPTH) {
            info("Chain target '" + pca.targetEventId + "' blocked: depth " + pca.depth
                    + " exceeds MAX_CHAIN_DEPTH " + MAX_CHAIN_DEPTH);
            return null;
        }

        // Ancestry cycle guard.
        if (pca.ancestry.contains(pca.targetEventId)) {
            info("Chain target '" + pca.targetEventId
                    + "' blocked: target is in ancestry " + pca.ancestry);
            return null;
        }

        // Re-check requirements at fire time (wall-clock now).
        NewsWorldRegistry registry = getNewsWorldRegistry();
        long nowEpochMs = System.currentTimeMillis();
        if (registry != null && !NewsRequirement.allMet(
                definition.getRequirements(), registry, nowEpochMs)) {
            info("Chain target '" + pca.targetEventId + "' requirements not met — skipping");
            return null;
        }

        // Resolve markets. sameMarkets override takes precedence; otherwise resolve
        // against ALL subscribed + news-enabled markets (no per-market cap — caps bypass).
        Map<ItemID, Float> resolvedMarkets;
        if (pca.resolvedMarketsOverride != null && !pca.resolvedMarketsOverride.isEmpty()) {
            resolvedMarkets = new LinkedHashMap<>(pca.resolvedMarketsOverride);
        } else {
            List<ItemID> allCandidates = new ArrayList<>();
            for (MarketInterface mi : markets) {
                ItemID marketID = mi.market.getMarketID();
                if (settingsFor(marketID).newsEnabled()) {
                    allCandidates.add(marketID);
                }
            }
            resolvedMarkets = resolveEventMarkets(definition, allCandidates);
        }

        if (resolvedMarkets.isEmpty()) {
            info("Chain target '" + pca.targetEventId
                    + "' has no resolved markets — skipping");
            return null;
        }

        // Activate. The regular activate() handles sequence picking, delay sampling,
        // cooldown arming and immediate publishing. It does NOT check caps (that's the
        // scheduler's job in firePlannedSlot), so calling it here achieves caps bypass.
        ActiveNewsEvent event = activate(definition, resolvedMarkets);
        if (event == null) {
            info("Chain target '" + pca.targetEventId + "' activation refused — skipping");
            return null;
        }

        // Track chain context for the newly activated event so its own chains carry the
        // correct depth and ancestry forward.
        eventChainDepth.put(event.getNewsUid(), pca.depth);
        eventChainAncestry.put(event.getNewsUid(), new LinkedHashSet<>(pca.ancestry));

        info("Chain activated '" + pca.targetEventId + "' (uid " + event.getNewsUid()
                + ", depth " + pca.depth + ", source '" + pca.sourceEventId + "')");

        // If the event published immediately (delay-0), its publish-moment chains
        // already fired inside publishEvent() — no need to trigger again here.

        return event;
    }

    // ── Price math ───────────────────────────────────────────────────────

    /**
     * Combined multiplicative news factor for one market:
     * {@code F = Π over active events (1 + sequence.value(t) * weightFactor * sensitivity * (1 + jitter))},
     * clamped to {@code [}{@link #MIN_COMBINED_FACTOR}{@code , }{@link #MAX_COMBINED_FACTOR}{@code ]}.
     * <p>
     * T-095: {@code sequence.value(t)} is the SIGNED influence level (the old
     * {@code envelope(t) × peakFactor} split collapsed into it — legacy events produce
     * identical numbers by the {@link NewsSequence#fromLegacyEnvelope} equivalence
     * contract). Each event contributes through its <b>current step's</b> market map
     * ({@link ActiveNewsEvent#currentStepWeights()}), so one step can hit gold while the
     * next hits emeralds; the jitter amplitude is the current step's {@code noise}.
     *
     * @param marketID    the market
     * @param sensitivity the market's sensitivity setting
     * @param withNoise   if true, each event whose current step has {@code noise > 0}
     *                    gets a fresh uniform jitter in {@code [-noise, +noise]} on its
     *                    factor term (per tick); false yields the deterministic factor
     *                    (runtime stream display)
     * @return the combined factor; exactly 1.0 when nothing influences the market
     */
    public double combinedFactorFor(ItemID marketID, float sensitivity, boolean withNoise) {
        double combined = 1.0;
        boolean anyInfluence = false;
        for (ActiveNewsEvent event : activeEvents) {
            Float weightFactor = event.currentStepWeights().get(marketID);
            if (weightFactor == null) continue;
            long activeMs = event.activeMillis();
            double value = event.getSequence().value(activeMs);
            if (value == 0) continue; // no influence right now (incl. pending impacts)

            double jitter = 0;
            double noise = event.getSequence().noiseAt(activeMs);
            if (withNoise && noise > 0) {
                jitter = (random.nextDouble() * 2.0 - 1.0) * noise;
            }
            combined *= sequenceFactorTerm(value, weightFactor, sensitivity, jitter);
            anyInfluence = true;
        }
        if (!anyInfluence) return 1.0;
        return clampCombinedFactor(combined);
    }

    /**
     * One event's multiplicative factor term for one market — legacy envelope form.
     * Pure function for unit tests (and the pre-sequence admin rendering); the runtime
     * itself uses {@link #sequenceFactorTerm} with the collapsed signed value.
     *
     * @param envelopeFactor the normalized envelope value in [0, 1]
     * @param peakFactor     the event's peak multiplicative influence
     * @param weightFactor   the market's effective matcher weight (negative inverts)
     * @param sensitivity    the market's sensitivity setting
     * @param jitter         the sampled noise jitter (0 = none)
     * @return {@code 1 + envelopeFactor * peakFactor * weightFactor * sensitivity * (1 + jitter)}
     */
    public static double eventFactorTerm(double envelopeFactor, double peakFactor,
                                         double weightFactor, double sensitivity, double jitter) {
        return 1.0 + envelopeFactor * peakFactor * weightFactor * sensitivity * (1.0 + jitter);
    }

    /**
     * One event's multiplicative factor term for one market — sequence form (T-095).
     * The signed influence {@code value} already carries what used to be
     * {@code envelope(t) × peakFactor}. Pure function for unit tests.
     *
     * @param value        the sequence's signed influence level at the current time
     *                     ({@code 0.3} = +30%, see {@link NewsSequence#value})
     * @param weightFactor the market's effective matcher weight (negative inverts)
     * @param sensitivity  the market's sensitivity setting
     * @param jitter       the sampled noise jitter (0 = none)
     * @return {@code 1 + value * weightFactor * sensitivity * (1 + jitter)}
     */
    public static double sequenceFactorTerm(double value, double weightFactor,
                                            double sensitivity, double jitter) {
        return 1.0 + value * weightFactor * sensitivity * (1.0 + jitter);
    }

    /**
     * Clamps a combined news factor to the safety band (plan §6.2) and sanitizes
     * non-finite values to 1 (no influence). Pure function for unit tests.
     *
     * @param factor the raw combined factor
     * @return the clamped factor in {@code [MIN_COMBINED_FACTOR, MAX_COMBINED_FACTOR]}
     */
    public static double clampCombinedFactor(double factor) {
        if (!Double.isFinite(factor)) return 1.0;
        return Math.min(Math.max(factor, MIN_COMBINED_FACTOR), MAX_COMBINED_FACTOR);
    }

    /**
     * Bakes a completed <b>permanent</b> sequence's final influence value into the
     * default price of every market of its last step and retires those markets from the
     * event (successor of the {@code reversal:none} bake; sequences plan §2).
     * <p>
     * Contract (plan §2 item 4): the bake happens <b>exactly once per market per event</b>.
     * {@link ActiveNewsEvent#markBaked} moves the market out of every weight map, and all
     * maps are persisted — so neither later ticks nor a restart can re-apply the shift.
     * Markets not present in the current interface list stay pending and are baked on a
     * later tick (or dropped by unsubscription). The bake factor is
     * {@code 1 + finalValue * weight * sensitivity} — for legacy events {@code finalValue
     * == peakFactor}, byte-identical to the pre-T-095 bake.
     */
    private void bakePermanentShift(ActiveNewsEvent event, List<MarketInterface> markets) {
        double finalValue = event.getSequence().finalValue();
        for (MarketInterface marketInterface : markets) {
            ItemID marketID = marketInterface.market.getMarketID();
            Float weightFactor = event.getPendingBakeWeights().get(marketID);
            if (weightFactor == null) continue;

            float sensitivity = settingsFor(marketID).sensitivity();
            // Same clamp band as the live influence, so the permanent shift can never
            // exceed what the event was allowed to do while running.
            double bakeFactor = clampCombinedFactor(
                    sequenceFactorTerm(finalValue, weightFactor, sensitivity, 0));
            double newDefault = marketInterface.market.getDefaultRealPrice() * bakeFactor;
            if (newDefault > 0 && Double.isFinite(newDefault)) {
                marketInterface.market.setDefaultRealPrice(newDefault);
            }
            event.markBaked(marketID);
            info("Baked permanent news shift of event '" + event.getDefinitionId()
                    + "' into market " + marketID + " (factor " + bakeFactor + ")");
        }
    }

    // ── Library ──────────────────────────────────────────────────────────

    /** Loads the definition library once, lazily, from the ticking update loop. */
    private void ensureLibraryLoaded() {
        if (libraryLoaded) return;
        library.reload();
        libraryLoaded = true;
    }

    /**
     * Reloads the news definition library from {@code config/StockMarket/news/}.
     * Active events are unaffected — they run on parameter snapshots (see
     * {@link ActiveNewsEvent}). Admin-facing seam for T-076.
     * <p>
     * T-082: the admin scheduler overrides are plugin state, not library state, so a
     * reload keeps them by construction. The planned queue keeps its fire times; only
     * the planned event ids are re-validated against the fresh library
     * ({@link #revalidatePlannedQueue} — vanished/disabled ids are repicked).
     *
     * @return the validation report of the reload pass
     */
    public ValidationReport reloadLibrary() {
        ValidationReport report = library.reload();
        libraryLoaded = true;
        revalidatePlannedQueue(getMarketInterfaces());
        return report;
    }

    /** @return the definition library owned by this plugin */
    public NewsEventLibrary getLibrary() {
        return library;
    }

    /** @return an unmodifiable snapshot view of the currently active events */
    public List<ActiveNewsEvent> getActiveEvents() {
        return List.copyOf(activeEvents);
    }

    // ── Market events ────────────────────────────────────────────────────

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        // No per-market runtime state to create: influence is computed from the active
        // events, and the custom settings map is handled by the ServerPlugin base class.
    }

    /**
     * Drops the market from every active event silently (plan §6.7); events left with
     * no markets at all are retired immediately (unpublished ones are discarded without
     * publishing — news about nothing would confuse players).
     */
    @Override
    public void onMarketUnsubscribed(ItemID marketID) {
        for (ActiveNewsEvent event : activeEvents) {
            event.dropMarket(marketID);
        }
        activeEvents.removeIf(ActiveNewsEvent::hasNoMarkets);
    }

    // ── Time / environment seams (overridable for deterministic tests) ──

    /**
     * Measures the real elapsed milliseconds since the previous update tick, clamped to
     * {@code [0, }{@link #MAX_TICK_ADVANCE_MS}{@code ]}. Uses {@link TimerMillis} so the
     * global test offset ({@code TimerMillis.TIMER_OFFSET_MS}) is honored.
     */
    private long measureTickDelta() {
        long elapsed = tickDeltaTimer.getStartTime() == 0 ? 0 : tickDeltaTimer.getElapsedTime();
        tickDeltaTimer.start(0);
        return Math.max(0, Math.min(elapsed, MAX_TICK_ADVANCE_MS));
    }

    /**
     * Per-market settings lookup with default fallback. Protected so tests can inject
     * settings without the plugin-manager subscription machinery.
     */
    protected @NotNull Settings settingsFor(ItemID marketID) {
        Settings settings = getCustomSettings(marketID);
        return settings != null ? settings : Settings.createDefault();
    }

    /**
     * Resolves a definition's matchers against the candidate markets. Protected so tests
     * can bypass the item-registry-dependent matcher resolution.
     */
    protected Map<ItemID, Float> resolveEventMarkets(NewsEventDefinition definition,
                                                     Collection<ItemID> candidates) {
        return definition.resolveMarkets(candidates);
    }

    /** @return the wall-clock publish timestamp (epoch ms). Protected for tests. */
    protected long currentEpochMs() {
        return System.currentTimeMillis();
    }

    /** @return the overworld game day at publish time, or 0 outside a server context. */
    protected long currentGameDay() {
        try {
            MinecraftServer server = UtilitiesPlatform.getServer();
            if (server != null && server.overworld() != null) {
                return server.overworld().getDayTime() / 24000L;
            }
        } catch (Exception ignored) {
            // No server (unit-test context) — day 0.
        }
        return 0;
    }

    /**
     * Wires the world-event registry supplier (T-098). Called by
     * {@code ServerPluginManager.installProductionSeams()} during master setup.
     *
     * @param supplier supplier of the master's world-event registry, or null to clear
     */
    public void setRegistrySupplier(@Nullable java.util.function.Supplier<NewsWorldRegistry> supplier) {
        this.registrySupplier = supplier;
    }

    /**
     * Retrieves the master-side world-event registry (T-098). Returns null in unit-test
     * contexts where no registry supplier has been wired. Public since T-099 so the
     * {@code NewsAdminRequest} registry ops and requirement-status rendering can read
     * it; still overridable so tests can substitute a test-local registry.
     *
     * @return the registry, or null if not available
     */
    public @Nullable NewsWorldRegistry getNewsWorldRegistry() {
        if (registrySupplier == null) return null;
        try {
            return registrySupplier.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Test hooks (naming convention: see IServerMarket.test_*) ────────

    /** Test hook: replaces the RNG for deterministic delay/pick/noise sampling. */
    public void test_setRandom(@NotNull Random random) {
        this.random = random;
    }

    /**
     * Test hook: marks the library as loaded so tests can populate it via
     * {@code getLibrary().reload(tempDir)} without touching the real config folder.
     */
    public void test_setLibraryLoaded(boolean loaded) {
        this.libraryLoaded = loaded;
    }

    /**
     * Test hook: remaining ticking-ms until the <b>next planned slot</b> fires (the queue
     * head's offset since T-082), or -1 when the queue is empty (not planned yet).
     */
    public long test_getSchedulerRemainingMs() {
        return plannedQueue.isEmpty() ? -1 : plannedQueue.get(0).offsetMs;
    }

    /**
     * Test hook: forces the next planned firing (e.g. 1 ms to fire on the next advance).
     * Since T-082 this sets the queue head's offset — the head's planned event id is
     * kept; an empty queue gets a time-only head slot.
     */
    public void test_setSchedulerRemainingMs(long remainingMs) {
        if (plannedQueue.isEmpty()) {
            plannedQueue.add(new PlannedSlot(remainingMs, ""));
        } else {
            plannedQueue.get(0).offsetMs = remainingMs;
        }
    }

    /** Test hook: remaining cooldown of a definition id in ticking ms (0 = off cooldown). */
    public long test_getCooldownRemainingMs(String definitionId) {
        return getCooldownRemainingMs(definitionId);
    }

    /** Test hook: the persisted monotonic news uid counter. */
    public long test_getNewsUidCounter() {
        return newsUidCounter;
    }

    /** Test hook: returns the list of pending chain activations (unmodifiable snapshot). */
    public List<PendingChainActivation> test_getPendingChainActivations() {
        return Collections.unmodifiableList(pendingChainActivations);
    }

    /** Test hook: clears all pending chain activations. */
    public void test_clearPendingChainActivations() {
        pendingChainActivations.clear();
    }

    /**
     * Test hook: sets the chain context (depth + ancestry) for an active event's
     * news uid. Useful for testing chain depth and ancestry guards.
     *
     * @param newsUid  the active event's news uid
     * @param depth    the chain depth
     * @param ancestry the ancestry event ids
     */
    public void test_setChainContext(long newsUid, int depth, Set<String> ancestry) {
        if (depth > 0) {
            eventChainDepth.put(newsUid, depth);
        } else {
            eventChainDepth.remove(newsUid);
        }
        if (ancestry != null && !ancestry.isEmpty()) {
            eventChainAncestry.put(newsUid, new LinkedHashSet<>(ancestry));
        } else {
            eventChainAncestry.remove(newsUid);
        }
    }

    /**
     * Test hook: returns the chain depth of an active event (0 for scheduler/admin-fired).
     *
     * @param newsUid the active event's news uid
     * @return the chain depth
     */
    public int test_getChainDepth(long newsUid) {
        return eventChainDepth.getOrDefault(newsUid, 0);
    }

    /**
     * Test hook: returns the chain ancestry of an active event (empty for scheduler-fired).
     *
     * @param newsUid the active event's news uid
     * @return the ancestry set (unmodifiable)
     */
    public Set<String> test_getChainAncestry(long newsUid) {
        Set<String> ancestry = eventChainAncestry.get(newsUid);
        return ancestry != null ? Collections.unmodifiableSet(ancestry) : Collections.emptySet();
    }

    // ── Persistence ──────────────────────────────────────────────────────

    /**
     * Persists the full news state: uid counter, the pre-scheduled activation queue
     * (T-082, as per-slot {@code offsetMs} deltas + planned event ids), the optional
     * admin scheduler overrides (T-082, only the set ones), per-event cooldowns (as
     * remaining ticking-ms, see {@link #cooldownRemainingMs}), the admin-disabled
     * event ids (T-081) and all active events with their resolved envelope snapshots,
     * age accumulators, pending-publish state and baked-market bookkeeping.
     * <p>
     * The pre-T-082 {@code schedulerRemainingMs} key is no longer written — {@link #load}
     * still understands it (legacy migration).
     */
    @Override
    public boolean save(CompoundTag tag) {
        tag.putLong("newsUidCounter", newsUidCounter);

        // Pre-scheduled queue (T-082): per slot the delta to the previous slot's fire
        // moment and the planned event id ("" = time-only slot).
        ListTag plannedTag = new ListTag();
        for (PlannedSlot slot : plannedQueue) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putLong("offsetMs", slot.offsetMs);
            slotTag.putString("eventId", slot.eventId);
            plannedTag.add(slotTag);
        }
        tag.put("plannedActivations", plannedTag);

        // Admin scheduler overrides (T-082): a key is only present while its override is
        // set — absence means "use the JSON file value".
        if (overrideMinSecondsBetweenEvents != null)
            tag.putLong("schedulerOverrideMinSeconds", overrideMinSecondsBetweenEvents);
        if (overrideMaxSecondsBetweenEvents != null)
            tag.putLong("schedulerOverrideMaxSeconds", overrideMaxSecondsBetweenEvents);
        if (overrideMaxActiveEventsGlobal != null)
            tag.putInt("schedulerOverrideMaxGlobal", overrideMaxActiveEventsGlobal);
        if (overrideMaxActiveEventsPerMarket != null)
            tag.putInt("schedulerOverrideMaxPerMarket", overrideMaxActiveEventsPerMarket);

        ListTag cooldownsTag = new ListTag();
        for (Map.Entry<String, Long> entry : cooldownRemainingMs.entrySet()) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldownTag.putString("eventId", entry.getKey());
            cooldownTag.putLong("remainingMs", entry.getValue());
            cooldownsTag.add(cooldownTag);
        }
        tag.put("cooldowns", cooldownsTag);

        // Admin-disabled event ids (T-081) — persisted with the plugin state, never in
        // the JSON event files. Ids of currently absent definitions are kept on purpose.
        ListTag disabledTag = new ListTag();
        for (String id : disabledEventIds) {
            disabledTag.add(StringTag.valueOf(id));
        }
        tag.put("disabledEvents", disabledTag);

        ListTag eventsTag = new ListTag();
        for (ActiveNewsEvent event : activeEvents) {
            CompoundTag eventTag = new CompoundTag();
            event.save(eventTag);
            eventsTag.add(eventTag);
        }
        tag.put("activeEvents", eventsTag);

        // T-098: chain context per active event (depth + ancestry). Only entries with
        // depth > 0 or non-empty ancestry are saved (scheduler-fired events default to
        // depth 0 and empty ancestry on load).
        ListTag chainContextTag = new ListTag();
        for (ActiveNewsEvent event : activeEvents) {
            long uid = event.getNewsUid();
            int depth = eventChainDepth.getOrDefault(uid, 0);
            Set<String> ancestry = eventChainAncestry.getOrDefault(uid, Collections.emptySet());
            if (depth > 0 || !ancestry.isEmpty()) {
                CompoundTag ctxTag = new CompoundTag();
                ctxTag.putLong("uid", uid);
                ctxTag.putInt("depth", depth);
                ListTag ancestryTag = new ListTag();
                for (String id : ancestry) {
                    ancestryTag.add(StringTag.valueOf(id));
                }
                ctxTag.put("ancestry", ancestryTag);
                chainContextTag.add(ctxTag);
            }
        }
        tag.put("chainContext", chainContextTag);

        // T-098: pending chain activations.
        ListTag pendingChainsTag = new ListTag();
        for (PendingChainActivation pca : pendingChainActivations) {
            CompoundTag pcaTag = new CompoundTag();
            pcaTag.putString("targetEventId", pca.targetEventId);
            pcaTag.putLong("remainingMs", pca.remainingMs);
            pcaTag.putString("sourceEventId", pca.sourceEventId);
            pcaTag.putInt("depth", pca.depth);
            ListTag ancestryTag = new ListTag();
            for (String id : pca.ancestry) {
                ancestryTag.add(StringTag.valueOf(id));
            }
            pcaTag.put("ancestry", ancestryTag);
            // resolvedMarketsOverride: only present for sameMarkets chains.
            if (pca.resolvedMarketsOverride != null) {
                ListTag marketsTag = new ListTag();
                for (Map.Entry<ItemID, Float> entry : pca.resolvedMarketsOverride.entrySet()) {
                    CompoundTag marketTag = new CompoundTag();
                    marketTag.putShort("id", entry.getKey().getShort());
                    marketTag.putFloat("weight", entry.getValue());
                    marketsTag.add(marketTag);
                }
                pcaTag.put("marketsOverride", marketsTag);
            }
            pendingChainsTag.add(pcaTag);
        }
        tag.put("pendingChains", pendingChainsTag);

        return true;
    }

    /**
     * Restores the state saved by {@link #save(CompoundTag)}.
     * <p>
     * Queue migration (T-082): when the {@code plannedActivations} key is absent the
     * queue is left empty and rebuilt from scratch on the next tick; a legacy
     * {@code schedulerRemainingMs} value &gt; 0 (pre-T-082 save) is converted into a
     * time-only head slot so the old countdown still fires when it was due — the
     * remaining slots refill behind it.
     */
    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("newsUidCounter")) newsUidCounter = tag.getLong("newsUidCounter");

        plannedQueue.clear();
        if (tag.contains("plannedActivations")) {
            ListTag plannedTag = tag.getList("plannedActivations", Tag.TAG_COMPOUND);
            for (int i = 0; i < plannedTag.size(); i++) {
                CompoundTag slotTag = plannedTag.getCompound(i);
                // Sanitize: stored offsets are always > 0 (due slots fire immediately);
                // clamp any malformed value instead of discarding the slot.
                plannedQueue.add(new PlannedSlot(Math.max(1, slotTag.getLong("offsetMs")),
                        slotTag.getString("eventId")));
            }
        } else if (tag.contains("schedulerRemainingMs")) {
            // Legacy single countdown (pre-T-082): convert into a time-only head slot.
            // Values <= 0 meant "not sampled yet" — leave the queue empty to rebuild.
            long legacyRemainingMs = tag.getLong("schedulerRemainingMs");
            if (legacyRemainingMs > 0) {
                plannedQueue.add(new PlannedSlot(legacyRemainingMs, ""));
            }
        }

        // Scheduler overrides (T-082): absent key = no override (pre-T-082 compatible).
        overrideMinSecondsBetweenEvents = tag.contains("schedulerOverrideMinSeconds")
                ? tag.getLong("schedulerOverrideMinSeconds") : null;
        overrideMaxSecondsBetweenEvents = tag.contains("schedulerOverrideMaxSeconds")
                ? tag.getLong("schedulerOverrideMaxSeconds") : null;
        overrideMaxActiveEventsGlobal = tag.contains("schedulerOverrideMaxGlobal")
                ? tag.getInt("schedulerOverrideMaxGlobal") : null;
        overrideMaxActiveEventsPerMarket = tag.contains("schedulerOverrideMaxPerMarket")
                ? tag.getInt("schedulerOverrideMaxPerMarket") : null;

        cooldownRemainingMs.clear();
        if (tag.contains("cooldowns")) {
            ListTag cooldownsTag = tag.getList("cooldowns", 10);
            for (int i = 0; i < cooldownsTag.size(); i++) {
                CompoundTag cooldownTag = cooldownsTag.getCompound(i);
                String eventId = cooldownTag.getString("eventId");
                long remainingMs = cooldownTag.getLong("remainingMs");
                if (!eventId.isEmpty() && remainingMs > 0) {
                    cooldownRemainingMs.put(eventId, remainingMs);
                }
            }
        }

        // Backward compatible (T-081): saves from before the per-event enable/disable
        // feature have no "disabledEvents" key — everything stays enabled (default).
        disabledEventIds.clear();
        if (tag.contains("disabledEvents")) {
            ListTag disabledTag = tag.getList("disabledEvents", Tag.TAG_STRING);
            for (int i = 0; i < disabledTag.size(); i++) {
                String id = disabledTag.getString(i);
                if (!id.isEmpty()) disabledEventIds.add(id);
            }
        }

        activeEvents.clear();
        if (tag.contains("activeEvents")) {
            ListTag eventsTag = tag.getList("activeEvents", 10);
            for (int i = 0; i < eventsTag.size(); i++) {
                ActiveNewsEvent event = ActiveNewsEvent.createFromTag(eventsTag.getCompound(i));
                if (event != null) {
                    activeEvents.add(event);
                } else {
                    warn("load(): Discarded malformed active news event at index " + i);
                }
            }
        }

        // T-098: chain context per active event (depth + ancestry). Missing entries
        // default to depth 0 and empty ancestry (backward compatible with pre-T-098 saves).
        eventChainDepth.clear();
        eventChainAncestry.clear();
        if (tag.contains("chainContext")) {
            ListTag chainContextTag = tag.getList("chainContext", Tag.TAG_COMPOUND);
            for (int i = 0; i < chainContextTag.size(); i++) {
                CompoundTag ctxTag = chainContextTag.getCompound(i);
                long uid = ctxTag.getLong("uid");
                int depth = ctxTag.getInt("depth");
                if (depth > 0) {
                    eventChainDepth.put(uid, depth);
                }
                Set<String> ancestry = new LinkedHashSet<>();
                if (ctxTag.contains("ancestry")) {
                    ListTag ancestryTag = ctxTag.getList("ancestry", Tag.TAG_STRING);
                    for (int j = 0; j < ancestryTag.size(); j++) {
                        String id = ancestryTag.getString(j);
                        if (!id.isEmpty()) ancestry.add(id);
                    }
                }
                if (!ancestry.isEmpty()) {
                    eventChainAncestry.put(uid, ancestry);
                }
            }
        }

        // T-098: pending chain activations. Backward compatible: absent key = no
        // pending chains (pre-T-098 saves).
        pendingChainActivations.clear();
        if (tag.contains("pendingChains")) {
            ListTag pendingChainsTag = tag.getList("pendingChains", Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingChainsTag.size(); i++) {
                CompoundTag pcaTag = pendingChainsTag.getCompound(i);
                String targetEventId = pcaTag.getString("targetEventId");
                long remainingMs = pcaTag.getLong("remainingMs");
                String sourceEventId = pcaTag.getString("sourceEventId");
                int depth = pcaTag.getInt("depth");
                Set<String> ancestry = new LinkedHashSet<>();
                if (pcaTag.contains("ancestry")) {
                    ListTag ancestryTag = pcaTag.getList("ancestry", Tag.TAG_STRING);
                    for (int j = 0; j < ancestryTag.size(); j++) {
                        String id = ancestryTag.getString(j);
                        if (!id.isEmpty()) ancestry.add(id);
                    }
                }
                @Nullable Map<ItemID, Float> marketsOverride = null;
                if (pcaTag.contains("marketsOverride")) {
                    ListTag marketsTag = pcaTag.getList("marketsOverride", Tag.TAG_COMPOUND);
                    marketsOverride = new LinkedHashMap<>();
                    for (int j = 0; j < marketsTag.size(); j++) {
                        CompoundTag marketTag = marketsTag.getCompound(j);
                        marketsOverride.put(new ItemID(marketTag.getShort("id")),
                                marketTag.getFloat("weight"));
                    }
                }
                if (!targetEventId.isEmpty() && remainingMs > 0) {
                    pendingChainActivations.add(new PendingChainActivation(
                            targetEventId, remainingMs, sourceEventId, depth,
                            ancestry, marketsOverride));
                }
            }
        }

        // T-098: initialize lastSeenStepIndices for loaded active events — each event's
        // current step is treated as "already seen" so step-start chains do not re-fire
        // for already-passed steps after a restart.
        lastSeenStepIndices.clear();
        for (ActiveNewsEvent event : activeEvents) {
            lastSeenStepIndices.put(event.getNewsUid(), event.currentStepIndex());
        }

        return true;
    }

    // ── Framework wiring ─────────────────────────────────────────────────

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideDefaultCustomSettings() {
        return Settings.createDefault();
    }

    @Override
    protected StreamCodec<ByteBuf, RuntimeStreamData> runtimeDataCodec() {
        return RuntimeStreamData.CODEC;
    }

    /**
     * Builds the live snapshot for the admin GUI (T-075). An empty event list is sent
     * deliberately (instead of null/no packet) so the GUI can clear itself when the last
     * event ends. Since T-082 the snapshot additionally carries the upcoming-events
     * timeline and the effective scheduler values with their override flags.
     * <p>
     * T-095: the streamed markets are the event's <b>union</b> set (all steps), but each
     * market's current factor comes from the <b>current step's</b> map — a market only
     * touched by a later step shows 1.0 until its step starts. The phase field keeps
     * its legacy semantics for PENDING/PERMANENT (and legacy events keep
     * RAMPING/HOLDING/REVERTING); running sequence-authored events send the current
     * step's name (see {@link ActiveNewsEvent#phaseName()}).
     * <p>
     * T-099: each event additionally carries the dedicated step-progress fields —
     * current step name/index, total step count and the milliseconds left in the
     * current step ({@code -1} sentinel while the impact is PENDING and once the
     * sequence is terminal) — the Active-tab "phase i of n" line renders from these.
     */
    @Override
    protected RuntimeStreamData provideRuntimeData() {
        List<RuntimeStreamData.ActiveEventInfo> events = new ArrayList<>(activeEvents.size());
        for (ActiveNewsEvent event : activeEvents) {
            long activeMs = event.activeMillis();
            double value = event.getSequence().value(activeMs);
            Map<ItemID, Float> currentStepWeights = event.currentStepWeights();

            List<RuntimeStreamData.MarketFactor> markets =
                    new ArrayList<>(event.getMarketWeights().size());
            for (Map.Entry<ItemID, Float> entry : event.getMarketWeights().entrySet()) {
                Float stepWeight = currentStepWeights.get(entry.getKey());
                float sensitivity = settingsFor(entry.getKey()).sensitivity();
                float currentFactor = stepWeight == null ? 1.0f
                        : (float) sequenceFactorTerm(value, stepWeight, sensitivity, 0);
                markets.add(new RuntimeStreamData.MarketFactor(
                        entry.getKey().getShort(), currentFactor));
            }
            // T-099 step progress: remaining time in the CURRENT step = time until the
            // next step boundary. -1 sentinel while no step is actually running
            // (PENDING impact / terminal sequence) — a running step is never negative.
            int stepIndex = event.currentStepIndex();
            long stepRemainingMs = (activeMs < 0 || event.isSequenceOver()) ? -1L
                    : Math.max(0L, event.getSequence().stepStartMs(stepIndex + 1) - activeMs);
            events.add(new RuntimeStreamData.ActiveEventInfo(event.getDefinitionId(),
                    displayHeadline(event.getHeadline()), event.phaseName(),
                    event.remainingMillis(), event.isPublished(), markets,
                    resolvePictureHash(event.getDefinitionId()),
                    event.currentStepName(), stepIndex,
                    event.getSequence().stepCount(), stepRemainingMs));
        }
        return new RuntimeStreamData(events, getPlannedActivations(), getSchedulerState());
    }

    /**
     * Resolves the picture content hash for an active event's Active-tab thumbnail
     * (T-091): definition → {@code picture} file reference → config-layer
     * {@link NewsPictureLibrary} entry. This is the same lookup the publish-time
     * snapshot performs ({@code ServerNewsPublisher.snapshotPicture}), so the streamed
     * hash matches the published record's hash; before publication it previews the
     * picture the publish would snapshot right now. Any miss along the chain
     * (definition removed by a reload, text-only event, picture file missing/invalid)
     * is a clean null — the row simply renders without a thumbnail.
     *
     * @param eventId the active event's definition id
     * @return the 20-byte SHA-1 of the event's picture, or null when there is none
     */
    private byte @Nullable [] resolvePictureHash(String eventId) {
        NewsEventDefinition definition = library.getDefinition(eventId);
        if (definition == null) return null;
        NewsPictureLibrary.Entry entry = library.getPictureLibrary().get(definition.getPictureFileName());
        return entry != null ? entry.getSha1() : null;
    }

    /** Resolves the display headline: {@code en_us} entry, else the first map entry. */
    private static String displayHeadline(Map<String, String> headline) {
        String enUs = headline.get("en_us");
        if (enUs != null) return enUs;
        for (String value : headline.values()) return value;
        return "";
    }
}
