package net.kroia.stockmarket.networking.request;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.news.ActiveNewsEvent;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsImpactEnvelope;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsRequirement;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One admin-gated request that serves every news admin operation (NewsEventSystem plan §5,
 * task T-076) — used by both the {@code /stockmarket news ...} commands and the
 * {@code NewsPluginGuiElement} admin buttons, so slave routing works uniformly for all of
 * them via {@link #needsRoutingToMaster()}.
 * <p>
 * <b>Operations</b> ({@link Op}):
 * <ul>
 *   <li>{@link Op#RELOAD} — {@link NewsPlugin#reloadLibrary()}; the response carries the
 *       {@link ValidationReport} rendered to display lines (grouped per file, errors before
 *       warnings, plus a summary line). The library itself guarantees that a reload which
 *       produces no valid events keeps the previously loaded definitions.</li>
 *   <li>{@link Op#TRIGGER} — fires a specific event immediately, bypassing cooldown,
 *       weight, {@code adminOnly} and the activity caps (see
 *       {@link NewsPlugin#resolveAdminTriggerMarkets}). The optional {@code market} input
 *       restricts the impact to a single subscribed market for testing. Every manual
 *       trigger is audited to the other online admins via {@code broadcastToAdmins}
 *       (insider-trading trail, plan §6.4b).</li>
 *   <li>{@link Op#LIST} — loaded definition ids (with adminOnly/active/cooldown markers)
 *       and the active events (phase, remaining time, published state, impacted markets
 *       with their current factor), rendered to display lines.</li>
 *   <li>{@link Op#STOP} — <b>hard-stops</b> one active event (or {@code "all"}): the
 *       event terminates in any phase — including reversal/recovery — its price
 *       influence is removed and its full cooldown restarts from the stop
 *       ({@link NewsPlugin#stopEvent}, semantics hardened in T-093). {@code reversal:none}
 *       events are cancelled without baking their permanent shift (stop = cancel; use
 *       SKIP_PHASE to finalize early instead). Audited like TRIGGER.</li>
 *   <li>{@link Op#INFO} — full details of one definition rendered to display lines (T-081):
 *       headline/text, matchers, matched∩subscribed markets, impact envelope, cooldown,
 *       enabled state and — if currently active — live phase/remaining/published.</li>
 *   <li>{@link Op#SET_ENABLED} — enables/disables one event id (T-081,
 *       {@link NewsPlugin#setEventEnabled}). A disabled event can never activate: the
 *       scheduler skips it and TRIGGER refuses it. Audited like TRIGGER.</li>
 *   <li>{@link Op#SET_SCHEDULER} — sets/clears the admin scheduler overrides (T-082,
 *       {@link NewsPlugin#applySchedulerOverrides}) or, with a pure query payload,
 *       returns the current state + upcoming timeline. Changes are audited like TRIGGER;
 *       queries are not.</li>
 *   <li>{@link Op#RESET_COOLDOWN} — clears one event's remaining activation cooldown
 *       (T-085, {@link NewsPlugin#resetCooldown}) so it is immediately eligible again.
 *       Audited like TRIGGER when a cooldown was actually cleared.</li>
 *   <li>{@link Op#STOP_EVENT} — hard-stops exactly one active event (T-093, the GUI's
 *       per-event Stop button). Same semantics as STOP with a single id, but a
 *       not-active target is a clean no-op status instead of an error. Audited like
 *       TRIGGER when an event was actually stopped.</li>
 *   <li>{@link Op#SKIP_PHASE} — fast-forwards one active event to the start of its next
 *       phase (T-093, {@link NewsPlugin#skipPhase}): pending → ramp-up → hold →
 *       reversal/recovery → ended. Skipping the last phase ends the event normally
 *       ({@code reversal:none}: the permanent shift bakes like a natural completion).
 *       Audited like TRIGGER when a phase was actually skipped.</li>
 *   <li>{@link Op#REGISTRY_LIST} — renders the {@link NewsWorldRegistry} content to
 *       display lines (T-099): per-event fire records (count, first/last fire time,
 *       game day) and all custom key/value pairs. A pure query — not audited.</li>
 *   <li>{@link Op#REGISTRY_CLEAR} — deletes registry state (T-099): everything
 *       ({@code eventId = "all"}), one event's fire record, or one custom key
 *       ({@code market = }{@value #REGISTRY_CLEAR_KEY_MODE}). Audited like TRIGGER
 *       when something was actually cleared.</li>
 * </ul>
 * <p>
 * <b>Structured GUI payload:</b> every successful response additionally carries
 * {@link OutputData#details()} — one {@link EventDetails} entry per loaded library
 * definition (T-081, consumed by the T-083 news management GUI). Building it is cheap
 * (definitions are KB-scale, the whole pool stays far below the ~1 MiB S2C payload cap)
 * and shipping it on every op means any admin action response doubles as a fresh
 * full-state snapshot for the GUI — no follow-up LIST round-trip needed.
 * <p>
 * <b>Permission model:</b> the acting player must be a StockMarket admin
 * ({@code /stockmarket op}, checked via {@code playerIsAdmin}). The check runs on the
 * master against the acting player's UUID, so it works for players connected to slave
 * servers too (unlike an op-level lookup, which needs the player on the master's list).
 * <p>
 * <b>Actor identity:</b>
 * <ul>
 *   <li>Client-originated requests (GUI buttons; master-local or slave-routed) always carry
 *       a transport-verified {@code playerSender} — the payload's
 *       {@link InputData#commandExecutor()} is ignored for them, so a client can never
 *       spoof another player's identity.</li>
 *   <li>Server-originated requests (a slave server forwarding a command via
 *       {@link #sendRequestToMaster}) arrive with {@code playerSender == null}; only then
 *       is the payload {@code commandExecutor} trusted, because only server code can
 *       produce a null transport sender (same trust model as
 *       {@code AsyncForwardingRequest}).</li>
 * </ul>
 * <p>
 * <b>Response text:</b> the {@code message} and {@code lines} are server-generated plain
 * strings (validation report entries, event/market names, factors) and are deliberately
 * not localized — like the {@link ValidationReport} itself, they are admin diagnostics.
 */
public class NewsAdminRequest extends StockMarketGenericRequest<NewsAdminRequest.InputData, NewsAdminRequest.OutputData> {

    /** The special {@code eventId} value that makes {@link Op#STOP} stop every active event. */
    public static final String STOP_ALL = "all";

    /**
     * The special {@code eventId} value that makes {@link Op#REGISTRY_CLEAR} wipe the
     * whole registry (fire records AND custom keys) — same keyword as {@link #STOP_ALL}.
     */
    public static final String REGISTRY_CLEAR_ALL = STOP_ALL;

    /**
     * {@link InputData#market()} value marking a {@link Op#REGISTRY_CLEAR} target as a
     * <b>custom registry key</b> instead of an event id
     * ({@code /stockmarket news registry clear key <key>}). The {@code market} field is
     * unused by the registry ops otherwise, so reusing it avoids growing the wire
     * payload for a two-state discriminator.
     */
    public static final String REGISTRY_CLEAR_KEY_MODE = "key";

    /**
     * The news admin operations.
     * <p>
     * <b>Wire format caveat:</b> values are encoded as their <b>ordinal byte</b> — new
     * operations must only ever be <b>appended at the end</b> of this enum, never
     * inserted or reordered, or old ordinals would silently map to different ops.
     */
    public enum Op {
        /** Reload the JSON definition library and return the validation report. */
        RELOAD,
        /** Fire a specific event now (bypasses cooldown/weights/adminOnly/caps). */
        TRIGGER,
        /** List loaded definitions + active events + cooldown state. */
        LIST,
        /**
         * Hard-stop one active event (or "all"): influence removed in any phase,
         * full cooldown restarted (T-093 semantics, see the class Javadoc).
         */
        STOP,
        /**
         * Full details of one definition rendered to display lines (T-081): headline,
         * text, matchers, matched∩subscribed markets, impact envelope, cooldown,
         * enabled state and live phase info when active.
         */
        INFO,
        /**
         * Enable or disable one event id (T-081, {@code eventId} + {@code enabled}
         * inputs). A disabled event can never activate — neither randomly nor via
         * TRIGGER ({@link NewsPlugin#setEventEnabled}).
         */
        SET_ENABLED,
        /**
         * Set/clear the admin scheduler overrides and/or query the scheduler state
         * (T-082, {@link InputData#scheduler()} input,
         * {@link NewsPlugin#applySchedulerOverrides}). An all-{@code null} non-reset
         * payload changes nothing and just returns the current state + upcoming
         * timeline ({@code /stockmarket news scheduler show}). Only calls that actually
         * change something are audited.
         */
        SET_SCHEDULER,
        /**
         * Clear one event id's remaining activation cooldown (T-085, {@code eventId}
         * input, {@link NewsPlugin#resetCooldown}). The event becomes immediately
         * eligible again (all other eligibility rules still apply). Appended at the
         * enum end — wire ordinal 7 (see the append-only caveat above).
         */
        RESET_COOLDOWN,
        /**
         * Hard-stop exactly one active event (T-093, {@code eventId} input,
         * {@link NewsPlugin#stopEvent}): influence removed in any phase — including
         * reversal/recovery — full cooldown restarted; {@code reversal:none} shifts
         * are cancelled, not baked. A not-active target is a clean no-op status.
         * Appended at the enum end — wire ordinal 8 (see the append-only caveat above).
         */
        STOP_EVENT,
        /**
         * Fast-forward one active event to the start of its next phase (T-093,
         * {@code eventId} input, {@link NewsPlugin#skipPhase}): pending → ramp-up →
         * hold → reversal/recovery → ended. Skipping the last phase ends the event
         * normally (normal cooldown handling; {@code reversal:none} bakes like a
         * natural completion). A not-active target is a clean no-op status.
         * Appended at the enum end — wire ordinal 9 (see the append-only caveat above).
         */
        SKIP_PHASE,
        /**
         * Render the news world-event registry to display lines (T-099,
         * {@code /stockmarket news registry list}): per-event fire records with count,
         * first/last fire timestamps (+ relative age) and game day, plus all custom
         * key/value pairs. Pure query — not audited. Appended at the enum end — wire
         * ordinal 10 (see the append-only caveat above).
         */
        REGISTRY_LIST,
        /**
         * Delete news world-event registry state (T-099,
         * {@code /stockmarket news registry clear ...}): the whole registry
         * ({@code eventId = }{@link #REGISTRY_CLEAR_ALL}), one event's fire record
         * ({@code eventId} = the id — the event counts as "never fired" again for the
         * requirement predicates), or one custom key ({@code eventId} = the key,
         * {@code market = }{@link #REGISTRY_CLEAR_KEY_MODE}). Audited like TRIGGER
         * when something was actually cleared. Appended at the enum end — wire
         * ordinal 11 (see the append-only caveat above).
         */
        REGISTRY_CLEAR;

        /** Wire codec: encoded as the ordinal byte (same shape as ModSettingsRequest.Action). */
        public static final StreamCodec<RegistryFriendlyByteBuf, Op> STREAM_CODEC =
                ExtraCodecUtils.enumStreamCodec(Op.class);
    }

    /**
     * Scheduler-override change payload for {@link Op#SET_SCHEDULER} (T-082). Mirrors the
     * parameter semantics of {@link NewsPlugin#applySchedulerOverrides} exactly:
     * <ul>
     *   <li>{@code null} field — leave that value unchanged,</li>
     *   <li>negative field — clear that override (the JSON file value applies again),</li>
     *   <li>non-negative field — set that override (server-side validated:
     *       {@code 0 < min <= max}, caps {@code >= 1}).</li>
     *   <li>{@code resetAll} — clear all four overrides first (explicit fields in the
     *       same payload apply on top).</li>
     * </ul>
     * An all-{@code null}, non-reset payload is a pure query (nothing changes, the
     * response carries the current state).
     *
     * @param minSecondsBetweenEvents  change for the minimum seconds between activations
     * @param maxSecondsBetweenEvents  change for the maximum seconds between activations
     * @param maxActiveEventsGlobal    change for the global active-event cap
     * @param maxActiveEventsPerMarket change for the per-market active-event cap
     * @param resetAll                 true to reset all overrides to the file values
     */
    public record SchedulerInput(@Nullable Long minSecondsBetweenEvents,
                                 @Nullable Long maxSecondsBetweenEvents,
                                 @Nullable Integer maxActiveEventsGlobal,
                                 @Nullable Integer maxActiveEventsPerMarket,
                                 boolean resetAll) {

        /** @return a payload that changes nothing (pure state query, "scheduler show") */
        public static SchedulerInput query() {
            return new SchedulerInput(null, null, null, null, false);
        }

        /**
         * Hand-written wire codec (no version byte — see {@link EventDetails#CODEC} for
         * the lockstep caveat). Each nullable field is a presence bool followed by the
         * value; then the {@code resetAll} bool.
         */
        public static final StreamCodec<ByteBuf, SchedulerInput> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buf, SchedulerInput input) {
                encodeNullableLong(buf, input.minSecondsBetweenEvents());
                encodeNullableLong(buf, input.maxSecondsBetweenEvents());
                encodeNullableInt(buf, input.maxActiveEventsGlobal());
                encodeNullableInt(buf, input.maxActiveEventsPerMarket());
                buf.writeBoolean(input.resetAll());
            }

            @Override
            public SchedulerInput decode(ByteBuf buf) {
                Long min = decodeNullableLong(buf);
                Long max = decodeNullableLong(buf);
                Integer global = decodeNullableInt(buf);
                Integer perMarket = decodeNullableInt(buf);
                return new SchedulerInput(min, max, global, perMarket, buf.readBoolean());
            }

            private void encodeNullableLong(ByteBuf buf, @Nullable Long value) {
                buf.writeBoolean(value != null);
                if (value != null) buf.writeLong(value);
            }

            private @Nullable Long decodeNullableLong(ByteBuf buf) {
                return buf.readBoolean() ? buf.readLong() : null;
            }

            private void encodeNullableInt(ByteBuf buf, @Nullable Integer value) {
                buf.writeBoolean(value != null);
                if (value != null) buf.writeInt(value);
            }

            private @Nullable Integer decodeNullableInt(ByteBuf buf) {
                return buf.readBoolean() ? buf.readInt() : null;
            }
        };
    }

    /**
     * Input payload.
     *
     * @param op              the operation to perform
     * @param eventId         the event definition id (TRIGGER/STOP/INFO/SET_ENABLED/
     *                        RESET_COOLDOWN/STOP_EVENT/SKIP_PHASE; for STOP the value
     *                        {@value #STOP_ALL} stops everything); for REGISTRY_CLEAR the
     *                        clear target (event id, custom key, or {@value #STOP_ALL} for
     *                        the whole registry — T-099); empty string for
     *                        RELOAD/LIST/REGISTRY_LIST
     * @param market          optional market restriction for TRIGGER — the market's registry
     *                        name (e.g. {@code "minecraft:diamond"}) or its numeric ItemID as
     *                        a string; empty string = no restriction. For REGISTRY_CLEAR the
     *                        value {@value #REGISTRY_CLEAR_KEY_MODE} marks the {@code eventId}
     *                        as a custom registry key instead of an event id (T-099)
     * @param commandExecutor the acting player for <b>server-originated</b> requests (slave
     *                        command forwarding); ignored whenever the transport supplies a
     *                        player sender — see the class Javadoc security note. Null for
     *                        client-originated (GUI) requests
     * @param enabled         the target state for {@link Op#SET_ENABLED} (true = enable the
     *                        event, false = disable it); ignored by all other operations
     * @param scheduler       the override change for {@link Op#SET_SCHEDULER} (T-082);
     *                        null for all other operations
     */
    public record InputData(Op op, String eventId, String market, @Nullable UUID commandExecutor,
                            boolean enabled, @Nullable SchedulerInput scheduler) {

        /**
         * Convenience constructor for the operations that take no {@code enabled} flag
         * and no scheduler payload (everything except {@link Op#SET_ENABLED} and
         * {@link Op#SET_SCHEDULER}) — keeps pre-T-081 call sites source compatible.
         */
        public InputData(Op op, String eventId, String market, @Nullable UUID commandExecutor) {
            this(op, eventId, market, commandExecutor, false, null);
        }

        /**
         * Convenience constructor without the T-082 scheduler payload — keeps pre-T-082
         * call sites ({@link Op#SET_ENABLED} and older) source compatible.
         */
        public InputData(Op op, String eventId, String market, @Nullable UUID commandExecutor,
                         boolean enabled) {
            this(op, eventId, market, commandExecutor, enabled, null);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                Op.STREAM_CODEC, InputData::op,
                ByteBufCodecs.STRING_UTF8, InputData::eventId,
                ByteBufCodecs.STRING_UTF8, InputData::market,
                ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC), InputData::commandExecutor,
                ByteBufCodecs.BOOL, InputData::enabled,
                ExtraCodecUtils.nullable(SchedulerInput.CODEC), InputData::scheduler,
                InputData::new
        );
    }

    /**
     * Structured per-definition details payload for the admin GUI (T-081, consumed by the
     * T-083 news management screen). One entry exists per <b>loaded library definition</b>;
     * disabled ids whose definition is currently absent from the library are not listed
     * (they only appear in the LIST text output).
     * <p>
     * The full headline/text <b>translation maps</b> are shipped (insertion-ordered) so
     * the client resolves the display language itself at render time via
     * {@link NewsTranslations} (exact language → {@code en_us} → first entry).
     *
     * @param id                 the unique definition id
     * @param enabled            false if an admin disabled the event ({@link NewsPlugin#isEventEnabled})
     * @param adminOnly          true if the event only fires via admin trigger, never randomly
     * @param active             true if an instance of this event is currently running
     * @param cooldownRemainingMs remaining activation cooldown in ticking ms (0 = off cooldown)
     * @param weight             the scheduler's weighted-random-pick weight
     * @param headline           full language-code → headline map (insertion-ordered)
     * @param text               full language-code → newspaper text map (insertion-ordered)
     * @param announceMinMs      inclusive lower bound of the announce-delay sampling range (ms)
     * @param announceMaxMs      inclusive upper bound of the announce-delay sampling range (ms)
     * @param peakFactor         peak multiplicative influence (0.5 = +50% at peak, negative = drop)
     * @param rampUpSeconds      seconds the impact ramps linearly from 0 to peak
     * @param durationSeconds    seconds the impact holds at peak
     * @param reversal           reversal mode JSON name ({@code "ramp"}, {@code "exponential"}
     *                           or {@code "none"} = permanent shift)
     * @param reversalSeconds    reversal length / exponential time constant in seconds
     *                           (meaningless for {@code "none"})
     * @param markets            the markets a trigger would currently impact — the matched ∩
     *                           subscribed ∩ news-enabled subset with per-market weight factors
     * @param pictureFileName    the definition's {@code picture} file reference (T-091,
     *                           shown/diagnostic value); empty string for text-only events
     * @param pictureHash        the picture's 20-byte SHA-1 content hash resolved through the
     *                           config-layer picture library at snapshot time (T-091 — the
     *                           details screen fetches/renders the picture from it), or null
     *                           when the event is text-only or the referenced file is
     *                           currently missing/invalid
     * @param sequences          the event's sequence definitions (T-099): the parsed
     *                           {@code sequences[]} entries, or — for legacy {@code impact}
     *                           events — the ONE implicit normalized "impact" sequence
     *                           (never empty; the T-100 details screen renders its step
     *                           list from this and treats the legacy descriptor fields
     *                           above as display fallback only)
     * @param requirements       per-requirement rendered description + live met/unmet
     *                           status, evaluated against the world registry at snapshot
     *                           time (T-099; feeds the T-100 admin trigger "Trigger
     *                           anyway?" confirmation popup, plan §10.1); empty when the
     *                           event has no {@code requires[]}
     * @param chains             the event's {@code chains[]} entries rendered to one
     *                           display line each (T-099); empty when the event has none
     */
    public record EventDetails(String id, boolean enabled, boolean adminOnly, boolean active,
                               long cooldownRemainingMs, float weight,
                               Map<String, String> headline, Map<String, String> text,
                               long announceMinMs, long announceMaxMs,
                               double peakFactor, long rampUpSeconds, long durationSeconds,
                               String reversal, long reversalSeconds,
                               List<MarketImpact> markets,
                               String pictureFileName, byte @Nullable [] pictureHash,
                               List<SequenceInfo> sequences,
                               List<RequirementStatus> requirements,
                               List<String> chains) {

        /**
         * Backward-compatible convenience constructor (pre-T-091 call sites, e.g.
         * existing test suites): no picture reference, no sequence/requirement/chain
         * block.
         */
        public EventDetails(String id, boolean enabled, boolean adminOnly, boolean active,
                            long cooldownRemainingMs, float weight,
                            Map<String, String> headline, Map<String, String> text,
                            long announceMinMs, long announceMaxMs,
                            double peakFactor, long rampUpSeconds, long durationSeconds,
                            String reversal, long reversalSeconds,
                            List<MarketImpact> markets) {
            this(id, enabled, adminOnly, active, cooldownRemainingMs, weight, headline, text,
                    announceMinMs, announceMaxMs, peakFactor, rampUpSeconds, durationSeconds,
                    reversal, reversalSeconds, markets, "", null);
        }

        /**
         * Backward-compatible convenience constructor (pre-T-099 call sites): no
         * sequence/requirement/chain block.
         */
        public EventDetails(String id, boolean enabled, boolean adminOnly, boolean active,
                            long cooldownRemainingMs, float weight,
                            Map<String, String> headline, Map<String, String> text,
                            long announceMinMs, long announceMaxMs,
                            double peakFactor, long rampUpSeconds, long durationSeconds,
                            String reversal, long reversalSeconds,
                            List<MarketImpact> markets,
                            String pictureFileName, byte @Nullable [] pictureHash) {
            this(id, enabled, adminOnly, active, cooldownRemainingMs, weight, headline, text,
                    announceMinMs, announceMaxMs, peakFactor, rampUpSeconds, durationSeconds,
                    reversal, reversalSeconds, markets, pictureFileName, pictureHash,
                    List.of(), List.of(), List.of());
        }

        /**
         * One market a trigger of the event would currently impact.
         *
         * @param marketId     the market's ItemID short (client-side resolvable via
         *                     ItemIDManager; <b>never persisted</b> — this value only
         *                     lives on the wire)
         * @param displayName  the market item's registry name, captured server-side so the
         *                     GUI can render without resolving the id (falls back to the
         *                     numeric id string for broken markets)
         * @param weightFactor the effective matcher weight factor for this market
         *                     (negative inverts the impact direction)
         */
        public record MarketImpact(short marketId, String displayName, float weightFactor) {}

        /**
         * One sequence of the event as defined (T-099, sequences plan §5) — unresolved:
         * durations are still {@code [min, max]} ranges; the concrete roll happens at
         * activation and is not part of this definition snapshot.
         *
         * @param name   the sequence name ({@code "impact"} for the implicit legacy
         *               normalization)
         * @param weight the activation-time weighted-pick weight
         * @param steps  the sequence's steps in order (never empty)
         */
        public record SequenceInfo(String name, float weight, List<StepInfo> steps) {}

        /**
         * One step of a {@link SequenceInfo} (T-099).
         *
         * @param name          the step name (UI display, chains' {@code onStep} anchor)
         * @param durationMinMs inclusive lower duration bound in ms (== max when fixed)
         * @param durationMaxMs inclusive upper duration bound in ms (== min when fixed)
         * @param targetFactor  the signed influence level the step reaches at its end
         *                      ({@code 0.3} = +30%)
         * @param curve         the interpolation curve's JSON name ({@code "linear"},
         *                      {@code "instant"}, {@code "exponential"}, {@code "hold"})
         * @param permanent     true if this (always last) step bakes its final value
         *                      into the default price at sequence end
         * @param markets       the step's OWN resolved market subset (matched ∩
         *                      subscribed, with weight factors) when the step declares
         *                      its own {@code markets[]}; <b>empty = the step inherits
         *                      the event-level markets</b>
         */
        public record StepInfo(String name, long durationMinMs, long durationMaxMs,
                               double targetFactor, String curve, boolean permanent,
                               List<MarketImpact> markets) {}

        /**
         * One trigger requirement of the event with its live evaluation result (T-099,
         * plan §10.1) — the T-100 admin trigger confirmation popup lists the unmet
         * entries ("Trigger anyway?"), the details screen renders all of them.
         *
         * @param description the human-readable requirement text
         *                    ({@link NewsRequirement#describe()})
         * @param met         whether the requirement held against the world registry at
         *                    snapshot time
         */
        public record RequirementStatus(String description, boolean met) {}

        /**
         * Hand-written wire codec (style of {@code NewsPlugin.RuntimeStreamData.CODEC}).
         * <p>
         * <b>No version byte:</b> encoder and decoder ship lockstep in the same mod jar
         * (the client and server halves of one build; master and slave servers must run
         * the same mod version anyway). Any field change must therefore update
         * {@code encode} and {@code decode} together — there is no cross-version
         * compatibility layer.
         * <p>
         * Translation maps are written as an int count followed by key/value UTF-8 pairs
         * and decoded into a {@link LinkedHashMap}, preserving the insertion order the
         * {@link NewsTranslations} first-entry fallback relies on.
         * <p>
         * Appended for T-091 (after the market list): {@code pictureFileName} (UTF-8,
         * may be empty) and an unconditional {@code pictureHash} presence bool followed
         * by 20 raw SHA-1 bytes when present.
         * <p>
         * Appended for T-099 (after the picture hash, all unconditional): the sequence
         * block ({@code seqCount}; per sequence {@code name}/{@code weight}/{@code
         * stepCount}; per step {@code name}/{@code durMinMs}/{@code durMaxMs}/{@code
         * targetFactor}/{@code curve}/{@code permanent} + per-step market-impact list),
         * the requirement statuses ({@code count}; per entry {@code description} +
         * {@code met} bool) and the rendered chain lines ({@code count} + UTF-8 lines).
         */
        public static final StreamCodec<ByteBuf, EventDetails> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buf, EventDetails details) {
                ByteBufCodecs.STRING_UTF8.encode(buf, details.id());
                buf.writeBoolean(details.enabled());
                buf.writeBoolean(details.adminOnly());
                buf.writeBoolean(details.active());
                buf.writeLong(details.cooldownRemainingMs());
                buf.writeFloat(details.weight());
                encodeTextMap(buf, details.headline());
                encodeTextMap(buf, details.text());
                buf.writeLong(details.announceMinMs());
                buf.writeLong(details.announceMaxMs());
                buf.writeDouble(details.peakFactor());
                buf.writeLong(details.rampUpSeconds());
                buf.writeLong(details.durationSeconds());
                ByteBufCodecs.STRING_UTF8.encode(buf, details.reversal());
                buf.writeLong(details.reversalSeconds());
                buf.writeInt(details.markets().size());
                for (MarketImpact market : details.markets()) {
                    buf.writeShort(market.marketId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, market.displayName());
                    buf.writeFloat(market.weightFactor());
                }
                // Appended T-091 (news pictures): picture file reference (may be
                // empty) + unconditional presence flag + optional 20 raw hash bytes
                // (lockstep convention — encode and decode always change together).
                ByteBufCodecs.STRING_UTF8.encode(buf, details.pictureFileName());
                buf.writeBoolean(details.pictureHash() != null);
                if (details.pictureHash() != null) {
                    buf.writeBytes(details.pictureHash());
                }
                // Appended T-099 (sequences plan §5), all unconditional. Layout:
                // int seqCount; per sequence: name, weight, int stepCount; per step:
                // name, durMinMs, durMaxMs, targetFactor, curve, permanent, int
                // marketCount + per market (short id, displayName, weightFactor);
                // then int requirementCount + per entry (description, met bool);
                // then int chainLineCount + per entry (rendered line).
                buf.writeInt(details.sequences().size());
                for (SequenceInfo sequence : details.sequences()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, sequence.name());
                    buf.writeFloat(sequence.weight());
                    buf.writeInt(sequence.steps().size());
                    for (StepInfo step : sequence.steps()) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, step.name());
                        buf.writeLong(step.durationMinMs());
                        buf.writeLong(step.durationMaxMs());
                        buf.writeDouble(step.targetFactor());
                        ByteBufCodecs.STRING_UTF8.encode(buf, step.curve());
                        buf.writeBoolean(step.permanent());
                        buf.writeInt(step.markets().size());
                        for (MarketImpact market : step.markets()) {
                            buf.writeShort(market.marketId());
                            ByteBufCodecs.STRING_UTF8.encode(buf, market.displayName());
                            buf.writeFloat(market.weightFactor());
                        }
                    }
                }
                buf.writeInt(details.requirements().size());
                for (RequirementStatus requirement : details.requirements()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, requirement.description());
                    buf.writeBoolean(requirement.met());
                }
                buf.writeInt(details.chains().size());
                for (String chainLine : details.chains()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, chainLine);
                }
            }

            @Override
            public EventDetails decode(ByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean enabled = buf.readBoolean();
                boolean adminOnly = buf.readBoolean();
                boolean active = buf.readBoolean();
                long cooldownRemainingMs = buf.readLong();
                float weight = buf.readFloat();
                Map<String, String> headline = decodeTextMap(buf);
                Map<String, String> text = decodeTextMap(buf);
                long announceMinMs = buf.readLong();
                long announceMaxMs = buf.readLong();
                double peakFactor = buf.readDouble();
                long rampUpSeconds = buf.readLong();
                long durationSeconds = buf.readLong();
                String reversal = ByteBufCodecs.STRING_UTF8.decode(buf);
                long reversalSeconds = buf.readLong();
                int marketCount = buf.readInt();
                List<MarketImpact> markets = new ArrayList<>(Math.max(0, marketCount));
                for (int i = 0; i < marketCount; i++) {
                    markets.add(new MarketImpact(buf.readShort(),
                            ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat()));
                }
                // Appended T-091 (news pictures): file reference + optional hash.
                String pictureFileName = ByteBufCodecs.STRING_UTF8.decode(buf);
                byte[] pictureHash = null;
                if (buf.readBoolean()) {
                    pictureHash = new byte[NewsPictureLibrary.SHA1_LENGTH];
                    buf.readBytes(pictureHash);
                }
                // Appended T-099: sequence block + requirement statuses + chain lines
                // (unconditional, lockstep with encode — see the layout note there).
                int seqCount = buf.readInt();
                List<SequenceInfo> sequences = new ArrayList<>(Math.max(0, seqCount));
                for (int i = 0; i < seqCount; i++) {
                    String seqName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    float seqWeight = buf.readFloat();
                    int stepCount = buf.readInt();
                    List<StepInfo> steps = new ArrayList<>(Math.max(0, stepCount));
                    for (int j = 0; j < stepCount; j++) {
                        String stepName = ByteBufCodecs.STRING_UTF8.decode(buf);
                        long durMinMs = buf.readLong();
                        long durMaxMs = buf.readLong();
                        double targetFactor = buf.readDouble();
                        String curve = ByteBufCodecs.STRING_UTF8.decode(buf);
                        boolean permanent = buf.readBoolean();
                        int stepMarketCount = buf.readInt();
                        List<MarketImpact> stepMarkets = new ArrayList<>(Math.max(0, stepMarketCount));
                        for (int k = 0; k < stepMarketCount; k++) {
                            stepMarkets.add(new MarketImpact(buf.readShort(),
                                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat()));
                        }
                        steps.add(new StepInfo(stepName, durMinMs, durMaxMs, targetFactor,
                                curve, permanent, stepMarkets));
                    }
                    sequences.add(new SequenceInfo(seqName, seqWeight, steps));
                }
                int requirementCount = buf.readInt();
                List<RequirementStatus> requirements = new ArrayList<>(Math.max(0, requirementCount));
                for (int i = 0; i < requirementCount; i++) {
                    requirements.add(new RequirementStatus(
                            ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean()));
                }
                int chainCount = buf.readInt();
                List<String> chains = new ArrayList<>(Math.max(0, chainCount));
                for (int i = 0; i < chainCount; i++) {
                    chains.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                }
                return new EventDetails(id, enabled, adminOnly, active, cooldownRemainingMs,
                        weight, headline, text, announceMinMs, announceMaxMs, peakFactor,
                        rampUpSeconds, durationSeconds, reversal, reversalSeconds, markets,
                        pictureFileName, pictureHash, sequences, requirements, chains);
            }

            /** Writes a translation map as int count + key/value UTF-8 pairs (insertion order). */
            private void encodeTextMap(ByteBuf buf, Map<String, String> map) {
                buf.writeInt(map.size());
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
                }
            }

            /** Reads a translation map into an insertion-ordered LinkedHashMap. */
            private Map<String, String> decodeTextMap(ByteBuf buf) {
                int size = buf.readInt();
                Map<String, String> map = new LinkedHashMap<>(Math.max(0, size));
                for (int i = 0; i < size; i++) {
                    String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                    map.put(key, ByteBufCodecs.STRING_UTF8.decode(buf));
                }
                return map;
            }
        };
    }

    /**
     * Output payload.
     *
     * @param success        true if the operation succeeded (RELOAD: no validation errors)
     * @param message        one-line human-readable result/failure summary (never null)
     * @param lines          detail lines for the sender (validation report / list / info
     *                       output; STOP: one outcome line per stopped event, T-085);
     *                       empty for TRIGGER/SET_ENABLED/RESET_COOLDOWN
     * @param definitionIds  ids of all currently loaded definitions (GUI trigger picker /
     *                       refresh after RELOAD); empty on failure
     * @param activeEventIds definition ids of the currently active events; empty on failure
     * @param details        structured per-definition snapshot for the admin GUI (T-081/T-083,
     *                       see {@link EventDetails}); populated on every successful op,
     *                       empty on failure
     */
    public record OutputData(boolean success, String message, List<String> lines,
                             List<String> definitionIds, List<String> activeEventIds,
                             List<EventDetails> details) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
                ByteBufCodecs.STRING_UTF8, OutputData::message,
                ExtraCodecUtils.listStreamCodec(ByteBufCodecs.STRING_UTF8), OutputData::lines,
                ExtraCodecUtils.listStreamCodec(ByteBufCodecs.STRING_UTF8), OutputData::definitionIds,
                ExtraCodecUtils.listStreamCodec(ByteBufCodecs.STRING_UTF8), OutputData::activeEventIds,
                ExtraCodecUtils.listStreamCodec(EventDetails.CODEC), OutputData::details,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return NewsAdminRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return failure("Request failed");
    }

    /**
     * Entry point for the server-side {@code /stockmarket news ...} command executors.
     * On the master the request is handled directly (the command thread <i>is</i> the
     * master main thread); on a slave it is forwarded to the master with the executing
     * player's UUID embedded in the payload (see the class Javadoc actor-identity note),
     * so slave admins work transparently.
     *
     * @param sender  the player who ran the command
     * @param op      the operation
     * @param eventId the event id argument, or null/empty when the op takes none
     * @param market  the market restriction argument, or null/empty when absent
     * @return a future completing with the operation result (may complete exceptionally
     *         on a slave when the master connection times out)
     */
    public CompletableFuture<OutputData> sendFromServerCommand(ServerPlayer sender, Op op,
                                                               @Nullable String eventId,
                                                               @Nullable String market) {
        return sendFromServerCommand(sender, op, eventId, market, false);
    }

    /**
     * Same as {@link #sendFromServerCommand(ServerPlayer, Op, String, String)} but with
     * the {@link InputData#enabled()} flag for {@link Op#SET_ENABLED}
     * ({@code /stockmarket news enable|disable <eventId>}, T-081).
     *
     * @param sender  the player who ran the command
     * @param op      the operation
     * @param eventId the event id argument, or null/empty when the op takes none
     * @param market  the market restriction argument, or null/empty when absent
     * @param enabled the target enabled state (only read by {@link Op#SET_ENABLED})
     * @return a future completing with the operation result (may complete exceptionally
     *         on a slave when the master connection times out)
     */
    public CompletableFuture<OutputData> sendFromServerCommand(ServerPlayer sender, Op op,
                                                               @Nullable String eventId,
                                                               @Nullable String market,
                                                               boolean enabled) {
        InputData input = new InputData(op,
                eventId == null ? "" : eventId,
                market == null ? "" : market,
                sender.getUUID(),
                enabled);
        if (!needsRoutingToMaster()) {
            return handleOnMasterServer(input, "", sender.getUUID());
        }
        return sendRequestToMaster(input);
    }

    /**
     * Entry point for the {@code /stockmarket news scheduler ...} command executors
     * (T-082, {@link Op#SET_SCHEDULER}): sets/clears scheduler overrides or — with a
     * {@link SchedulerInput#query()} payload — just returns the current state. Same
     * master routing as {@link #sendFromServerCommand(ServerPlayer, Op, String, String)}.
     *
     * @param sender the player who ran the command
     * @param change the override change (or query) payload; never null
     * @return a future completing with the operation result (may complete exceptionally
     *         on a slave when the master connection times out)
     */
    public CompletableFuture<OutputData> sendSchedulerFromServerCommand(ServerPlayer sender,
                                                                        SchedulerInput change) {
        InputData input = new InputData(Op.SET_SCHEDULER, "", "", sender.getUUID(), false, change);
        if (!needsRoutingToMaster()) {
            return handleOnMasterServer(input, "", sender.getUUID());
        }
        return sendRequestToMaster(input);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // Defense in depth: even a misrouted request must never run on a non-master.
        if (getServerMarketManager() == null) {
            return CompletableFuture.completedFuture(
                    failure("News admin operations are only available on the master server"));
        }

        // Actor resolution — see the class Javadoc security note: the payload identity is
        // only trusted when the transport supplied no player (server-originated request).
        UUID actor = playerSender != null ? playerSender : input.commandExecutor();
        if (actor == null) {
            return CompletableFuture.completedFuture(failure("No player identity attached to the request"));
        }
        if (!playerIsAdmin(actor)) {
            warn("Rejected news admin " + input.op() + " from " + getPlayerName(actor)
                    + ": not a StockMarket admin");
            return CompletableFuture.completedFuture(
                    failure("You are not a StockMarket admin (ask for /stockmarket op)"));
        }

        NewsPlugin plugin = findNewsPlugin();
        if (plugin == null) {
            return CompletableFuture.completedFuture(
                    failure("No NewsPlugin instance exists on this server"));
        }

        OutputData result = switch (input.op()) {
            case RELOAD -> handleReload(plugin, actor);
            case TRIGGER -> handleTrigger(plugin, input, actor);
            case LIST -> handleList(plugin);
            case STOP -> handleStop(plugin, input, actor);
            case INFO -> handleInfo(plugin, input);
            case SET_ENABLED -> handleSetEnabled(plugin, input, actor);
            case SET_SCHEDULER -> handleSetScheduler(plugin, input, actor);
            case RESET_COOLDOWN -> handleResetCooldown(plugin, input, actor);
            case STOP_EVENT -> handleStopEvent(plugin, input, actor);
            case SKIP_PHASE -> handleSkipPhase(plugin, input, actor);
            case REGISTRY_LIST -> handleRegistryList(plugin);
            case REGISTRY_CLEAR -> handleRegistryClear(plugin, input, actor);
        };
        return CompletableFuture.completedFuture(result);
    }

    // ── Operation handlers ───────────────────────────────────────────────

    /** RELOAD: reload the library, render the report, audit to the other admins. */
    private OutputData handleReload(NewsPlugin plugin, UUID actor) {
        ValidationReport report = plugin.reloadLibrary();
        int loaded = plugin.getLibrary().getDefinitionCount();
        String summary = loaded + " event(s) loaded, " + report.errorCount() + " error(s), "
                + report.warningCount() + " warning(s)";
        broadcastToAdmins(actor, getPlayerName(actor) + " reloaded the news event definitions ("
                + summary + ")");
        info("News definitions reloaded by " + getPlayerName(actor) + ": " + summary);
        return new OutputData(!report.hasErrors(), summary, renderReport(report),
                definitionIds(plugin), activeEventIds(plugin),
                buildEventDetails(plugin, plugin.getMarketInterfaces()));
    }

    /**
     * TRIGGER: fire the event immediately, bypassing cooldown/weight/adminOnly/caps.
     * With a market restriction the event must still match that market and the market
     * must be subscribed (+ news-enabled) — otherwise an error message is returned.
     */
    private OutputData handleTrigger(NewsPlugin plugin, InputData input, UUID actor) {
        if (!plugin.isEnabled()) {
            return failure("The NewsPlugin is disabled — enable it before triggering events");
        }
        String eventId = input.eventId().trim();
        NewsEventDefinition definition = plugin.getLibrary().getDefinition(eventId);
        if (definition == null) {
            return failure("Unknown news event id '" + eventId + "' (see /stockmarket news list)");
        }
        // Hard requirement (T-081): a disabled event can NEVER activate, not even via
        // a manual admin trigger. NewsPlugin.activate() re-checks this (defense in depth).
        if (!plugin.isEventEnabled(eventId)) {
            return failure("News event '" + eventId + "' is disabled — enable it first "
                    + "(/stockmarket news enable " + eventId + ")");
        }
        if (plugin.isEventActive(eventId)) {
            return failure("News event '" + eventId + "' is already active");
        }

        List<MarketInterface> markets = plugin.getMarketInterfaces();

        // Optional single-market restriction: resolve the identifier against the
        // subscribed markets (registry name or numeric ItemID, like /stockmarket <market> remove).
        Set<ItemID> restrictTo = null;
        String marketArg = input.market().trim();
        if (!marketArg.isEmpty()) {
            restrictTo = findSubscribedMarkets(markets, marketArg);
            if (restrictTo.isEmpty()) {
                return failure("Market '" + marketArg + "' is not subscribed to the NewsPlugin");
            }
        }

        Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(definition, markets, restrictTo);
        if (resolved.isEmpty()) {
            return failure(restrictTo == null
                    ? "News event '" + eventId + "' matches no subscribed (news-enabled) market"
                    : "News event '" + eventId + "' does not match market '" + marketArg + "'");
        }

        ActiveNewsEvent event = plugin.activate(definition, resolved);
        if (event == null) {
            return failure("Failed to activate news event '" + eventId + "'");
        }

        // Insider-trading audit trail (plan §6.4b): every manual trigger is broadcast.
        String audit = getPlayerName(actor) + " manually triggered news event '" + eventId + "'"
                + (restrictTo != null ? " restricted to market '" + marketArg + "'" : "")
                + " (" + resolved.size() + " market(s), delay " + event.getSampledDelayMs() + " ms)";
        broadcastToAdmins(actor, audit);
        info(audit);

        return success(plugin, "Triggered news event '" + eventId + "' on "
                + resolved.size() + " market(s)", List.of());
    }

    /** LIST: loaded definitions (+ markers) and active events rendered to display lines. */
    private OutputData handleList(NewsPlugin plugin) {
        List<String> lines = new ArrayList<>();

        Map<String, NewsEventDefinition> definitions = plugin.getLibrary().getDefinitions();
        lines.add("Definitions (" + definitions.size() + "):");
        for (NewsEventDefinition definition : definitions.values()) {
            StringBuilder line = new StringBuilder("- ").append(definition.getId());
            if (!plugin.isEventEnabled(definition.getId())) line.append(" [disabled]");
            if (definition.isAdminOnly()) line.append(" [adminOnly]");
            if (plugin.isEventActive(definition.getId())) line.append(" [active]");
            long cooldownMs = plugin.getCooldownRemainingMs(definition.getId());
            if (cooldownMs > 0) {
                line.append(" [cooldown ").append(NewsUiFormatting.formatRemainingTime(cooldownMs)).append("]");
            }
            lines.add(line.toString());
        }
        // Disabled ids whose definition is currently absent (file removed) stay visible
        // here so admins can find and re-enable them (T-081).
        for (String disabledId : plugin.getDisabledEventIds()) {
            if (!definitions.containsKey(disabledId)) {
                lines.add("- " + disabledId + " [disabled] (not loaded)");
            }
        }

        List<ActiveNewsEvent> activeEvents = plugin.getActiveEvents();
        lines.add("Active events (" + activeEvents.size() + "):");
        for (ActiveNewsEvent event : activeEvents) {
            // T-095: phaseName() covers PENDING/PERMANENT/EXPIRED, the legacy phase
            // names for impact events and the current step name for sequence events.
            String phase = event.phaseName();
            lines.add("- " + event.getDefinitionId() + " (uid " + event.getNewsUid() + ") "
                    + phase + ", " + NewsUiFormatting.formatRemainingTime(event.remainingMillis())
                    + " remaining, " + (event.isPublished() ? "published" : "pending publication"));
            for (ItemID marketID : event.getMarketWeights().keySet()) {
                lines.add("    " + marketDisplayName(marketID) + " "
                        + NewsUiFormatting.formatFactorPercent(plugin.currentEventFactor(event, marketID)));
            }
        }

        return success(plugin, definitions.size() + " definition(s), "
                + activeEvents.size() + " active event(s)", lines);
    }

    /** STOP: stop one active event or {@value #STOP_ALL}; audited like TRIGGER. */
    private OutputData handleStop(NewsPlugin plugin, InputData input, UUID actor) {
        String eventId = input.eventId().trim();
        List<ActiveNewsEvent> targets = new ArrayList<>();
        if (eventId.equalsIgnoreCase(STOP_ALL)) {
            targets.addAll(plugin.getActiveEvents());
            if (targets.isEmpty()) {
                return failure("No active news events to stop");
            }
        } else {
            ActiveNewsEvent event = plugin.findActiveEvent(eventId);
            if (event == null) {
                return failure("No active news event with id '" + eventId + "'");
            }
            targets.add(event);
        }

        List<String> outcomes = new ArrayList<>(targets.size());
        for (ActiveNewsEvent event : targets) {
            NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
            outcomes.add(event.getDefinitionId() + ": " + describeOutcome(outcome));
        }

        String audit = getPlayerName(actor) + " stopped news event(s): " + String.join("; ", outcomes);
        broadcastToAdmins(actor, audit);
        info(audit);

        // T-085: the message stays a short one-liner (GUI status label), the per-event
        // outcomes travel as detail lines (GUI dialog / one chat line each for commands).
        return success(plugin, "Stopped " + targets.size() + " event(s)", outcomes);
    }

    /**
     * Result of one testable admin action core ({@link #performStopEvent} /
     * {@link #performSkipPhase}) — the request handlers wrap it into the wire
     * {@link OutputData} and audit it, the in-game test suite asserts on it directly.
     *
     * @param success false only for a hard input error (unknown event id)
     * @param message the one-line human-readable status (never null)
     * @param changed true when the action actually changed plugin state (only then do
     *                the handlers broadcast the admin audit line)
     */
    public record AdminActionResult(boolean success, String message, boolean changed) {}

    /**
     * Core of {@link Op#STOP_EVENT} (T-093): hard-stops exactly one active event via
     * {@link NewsPlugin#stopEvent} — influence removed in any phase, full cooldown
     * restarted, {@code reversal:none} cancelled without a bake. Unknown ids are an
     * error; a known-but-not-active id is a clean no-op status (nothing to stop).
     * Static (plugin passed in) so the in-game test suite can drive it without the
     * request/network machinery — same pattern as {@link #renderEventInfo}.
     *
     * @param plugin  the news plugin (active-event source)
     * @param eventId the definition id of the event to stop
     * @return the action result (never null)
     */
    public static AdminActionResult performStopEvent(NewsPlugin plugin, String eventId) {
        String id = eventId.trim();
        ActiveNewsEvent event = plugin.findActiveEvent(id);
        if (event == null) {
            if (plugin.getLibrary().getDefinition(id) == null) {
                return new AdminActionResult(false,
                        "Unknown news event id '" + id + "' (see /stockmarket news list)", false);
            }
            return new AdminActionResult(true,
                    "News event '" + id + "' is not active — nothing to stop", false);
        }
        NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
        return new AdminActionResult(true,
                "Stopped news event '" + id + "': " + describeOutcome(outcome), true);
    }

    /**
     * Core of {@link Op#SKIP_PHASE} (T-093): fast-forwards one active event to the start
     * of its next phase via {@link NewsPlugin#skipPhase} and immediately realizes the
     * consequences ({@code advanceTime(0)} runs due publishes, {@code reversal:none}
     * bakes and retirements), so the caller's response snapshot already reflects the new
     * state. Unknown ids are an error; a known-but-not-active id — and an event already
     * in a terminal phase — is a clean no-op status. Static for in-game testability
     * (same pattern as {@link #performStopEvent}).
     *
     * @param plugin  the news plugin (active-event source)
     * @param eventId the definition id of the event to fast-forward
     * @param markets the plugin's subscribed markets (normally
     *                {@code plugin.getMarketInterfaces()}) — needed to realize bakes
     * @return the action result (never null)
     */
    public static AdminActionResult performSkipPhase(NewsPlugin plugin, String eventId,
                                                     List<MarketInterface> markets) {
        String id = eventId.trim();
        ActiveNewsEvent event = plugin.findActiveEvent(id);
        if (event == null) {
            if (plugin.getLibrary().getDefinition(id) == null) {
                return new AdminActionResult(false,
                        "Unknown news event id '" + id + "' (see /stockmarket news list)", false);
            }
            return new AdminActionResult(true,
                    "News event '" + id + "' is not active — no phase to skip", false);
        }
        NewsPlugin.SkipOutcome outcome = plugin.skipPhase(event);
        if (outcome == NewsPlugin.SkipOutcome.NOTHING_TO_SKIP) {
            return new AdminActionResult(true, "News event '" + id
                    + "' is already in its terminal phase — nothing to skip", false);
        }
        // T-095: sequence-authored events carry the reached step's name in the message
        // (SKIPPED_TO_STEP is generic — the enum constant cannot carry the name).
        String description = outcome == NewsPlugin.SkipOutcome.SKIPPED_TO_STEP
                ? describeSkip(outcome) + " — now in step '" + event.currentStepName() + "'"
                : describeSkip(outcome);
        // Realize due publishes/bakes/retirements now instead of one tick later, so the
        // response's active-event snapshot is already consistent with the skip.
        plugin.advanceTime(0, markets);
        return new AdminActionResult(true,
                "Skipped phase of news event '" + id + "': " + description, true);
    }

    /**
     * STOP_EVENT (T-093): the per-event GUI Stop button. Wraps {@link #performStopEvent}
     * into the wire response; only an actual stop is audited (same policy as
     * RESET_COOLDOWN — a no-op must not spam the other admins).
     */
    private OutputData handleStopEvent(NewsPlugin plugin, InputData input, UUID actor) {
        AdminActionResult result = performStopEvent(plugin, input.eventId());
        if (!result.success()) {
            return failure(result.message());
        }
        if (result.changed()) {
            // Same insider-trading audit trail as TRIGGER/STOP.
            String audit = getPlayerName(actor) + " " + result.message();
            broadcastToAdmins(actor, audit);
            info(audit);
        }
        return success(plugin, result.message(), List.of());
    }

    /**
     * SKIP_PHASE (T-093): the per-event GUI Skip-phase button /
     * {@code /stockmarket news skipphase}. Wraps {@link #performSkipPhase} into the
     * wire response; only an actual skip is audited.
     */
    private OutputData handleSkipPhase(NewsPlugin plugin, InputData input, UUID actor) {
        AdminActionResult result = performSkipPhase(plugin, input.eventId(),
                plugin.getMarketInterfaces());
        if (!result.success()) {
            return failure(result.message());
        }
        if (result.changed()) {
            // Skipping a phase changes the market impact timing — audited like STOP.
            String audit = getPlayerName(actor) + " " + result.message();
            broadcastToAdmins(actor, audit);
            info(audit);
        }
        return success(plugin, result.message(), List.of());
    }

    // ── Registry ops (T-099) ─────────────────────────────────────────────

    /** Timestamp format of {@link #performRegistryList} lines (UTC wall clock). */
    private static final DateTimeFormatter REGISTRY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC);

    /**
     * Core of {@link Op#REGISTRY_LIST} (T-099): renders the world-event registry to
     * human-readable display lines — the fire records (event id, fire count, first/last
     * fire as UTC timestamp, the last fire additionally as a relative age, and the
     * in-game day of the last fire) followed by the custom key/value pairs. Static and
     * pure (registry + clock injected) so the in-game test suite can drive it without
     * the request/network machinery; output is admin diagnostics and deliberately not
     * localized (same policy as LIST/INFO).
     *
     * @param registry   the world-event registry to render
     * @param nowEpochMs the current wall-clock time in epoch ms (for the relative ages)
     * @return the rendered display lines (never empty — both section headers always
     *         appear, with their entry counts)
     */
    public static List<String> performRegistryList(NewsWorldRegistry registry, long nowEpochMs) {
        List<String> lines = new ArrayList<>();
        Map<String, NewsWorldRegistry.FireInfo> fireInfos = registry.fireInfos();
        lines.add("Fire records (" + fireInfos.size() + "):");
        for (Map.Entry<String, NewsWorldRegistry.FireInfo> entry : fireInfos.entrySet()) {
            NewsWorldRegistry.FireInfo info = entry.getValue();
            StringBuilder line = new StringBuilder("- ").append(entry.getKey())
                    .append(": fired ").append(info.fireCount())
                    .append(info.fireCount() == 1 ? " time" : " times");
            if (info.fireCount() > 1) {
                line.append(", first ").append(formatEpoch(info.firstFiredEpochMs()));
            }
            line.append(", last ").append(formatEpoch(info.lastFiredEpochMs()))
                    .append(" (").append(formatAge(nowEpochMs - info.lastFiredEpochMs()))
                    .append(" ago), game day ").append(info.lastFiredGameDay());
            lines.add(line.toString());
        }
        Map<String, String> customValues = registry.customValues();
        lines.add("Custom values (" + customValues.size() + "):");
        for (Map.Entry<String, String> entry : customValues.entrySet()) {
            lines.add("- " + entry.getKey() + " = " + entry.getValue());
        }
        return lines;
    }

    /**
     * Core of {@link Op#REGISTRY_CLEAR} (T-099): deletes registry state and reports
     * what was cleared. Three variants (matching {@link NewsWorldRegistry#clearAll}/
     * {@link NewsWorldRegistry#clearEvent}/{@link NewsWorldRegistry#clearKey}):
     * <ul>
     *   <li>{@code target = }{@value #REGISTRY_CLEAR_ALL}{@code , keyMode = false} —
     *       wipe everything (fire records AND custom keys);</li>
     *   <li>{@code keyMode = false} — delete one event's fire record; the event counts
     *       as "never fired" again for the requirement predicates. An id without a
     *       record is a clean no-op status (records legitimately outlive definitions,
     *       so unknown ids are not an error);</li>
     *   <li>{@code keyMode = true} — delete one custom key/value pair; an absent key
     *       is a clean no-op status.</li>
     * </ul>
     * Static (registry passed in) so the in-game test suite can drive it without the
     * request/network machinery — same pattern as {@link #performStopEvent}.
     *
     * @param registry the world-event registry to mutate
     * @param target   the clear target: {@value #REGISTRY_CLEAR_ALL}, an event id, or
     *                 (with {@code keyMode}) a custom key
     * @param keyMode  true when {@code target} names a custom registry key instead of
     *                 an event id ({@link #REGISTRY_CLEAR_KEY_MODE})
     * @return the action result (never null); {@code changed()} is true only when
     *         something was actually deleted (only then do the handlers audit)
     */
    public static AdminActionResult performRegistryClear(NewsWorldRegistry registry,
                                                         String target, boolean keyMode) {
        String trimmed = target == null ? "" : target.trim();
        if (trimmed.isEmpty()) {
            return new AdminActionResult(false, "Registry clear needs a target: an event id, "
                    + "'key <key>' or '" + REGISTRY_CLEAR_ALL + "'", false);
        }
        if (keyMode) {
            String previous = registry.getValue(trimmed);
            if (!registry.clearKey(trimmed)) {
                return new AdminActionResult(true, "No custom registry key '" + trimmed
                        + "' — nothing to clear", false);
            }
            return new AdminActionResult(true, "Cleared custom registry key '" + trimmed
                    + "' (was '" + previous + "')", true);
        }
        if (trimmed.equalsIgnoreCase(REGISTRY_CLEAR_ALL)) {
            if (registry.isEmpty()) {
                return new AdminActionResult(true,
                        "The news registry is already empty — nothing to clear", false);
            }
            int fireRecords = registry.fireInfos().size();
            int customKeys = registry.customValues().size();
            registry.clearAll();
            return new AdminActionResult(true, "Cleared the whole news registry ("
                    + fireRecords + " fire record(s), " + customKeys + " custom key(s))", true);
        }
        NewsWorldRegistry.FireInfo info = registry.getFireInfo(trimmed);
        if (!registry.clearEvent(trimmed)) {
            return new AdminActionResult(true, "No fire record for event '" + trimmed
                    + "' — nothing to clear", false);
        }
        return new AdminActionResult(true, "Cleared the fire record of event '" + trimmed
                + "' (" + info.fireCount() + " fire(s)) — it counts as never fired again", true);
    }

    /**
     * REGISTRY_LIST (T-099): renders the registry via {@link #performRegistryList}.
     * A pure query — not audited (same policy as LIST / scheduler show).
     */
    private OutputData handleRegistryList(NewsPlugin plugin) {
        NewsWorldRegistry registry = plugin.getNewsWorldRegistry();
        if (registry == null) {
            return failure("The news world registry is not available on this server");
        }
        List<String> lines = performRegistryList(registry, System.currentTimeMillis());
        return success(plugin, registry.fireInfos().size() + " fire record(s), "
                + registry.customValues().size() + " custom key(s)", lines);
    }

    /**
     * REGISTRY_CLEAR (T-099): wraps {@link #performRegistryClear} into the wire
     * response; only an actual deletion is audited (same policy as STOP_EVENT — a
     * no-op must not spam the other admins). Clearing changes what can fire next
     * (requirement predicates flip back), hence the TRIGGER-style audit trail.
     */
    private OutputData handleRegistryClear(NewsPlugin plugin, InputData input, UUID actor) {
        NewsWorldRegistry registry = plugin.getNewsWorldRegistry();
        if (registry == null) {
            return failure("The news world registry is not available on this server");
        }
        boolean keyMode = REGISTRY_CLEAR_KEY_MODE.equalsIgnoreCase(input.market().trim());
        AdminActionResult result = performRegistryClear(registry, input.eventId(), keyMode);
        if (!result.success()) {
            return failure(result.message());
        }
        if (result.changed()) {
            // Same insider-trading audit trail as TRIGGER/STOP: clearing registry
            // state re-arms requirement-gated events.
            String audit = getPlayerName(actor) + " " + result.message();
            broadcastToAdmins(actor, audit);
            info(audit);
        }
        return success(plugin, result.message(), List.of());
    }

    /** Formats an epoch-ms wall-clock timestamp as {@code yyyy-MM-dd HH:mm:ss} UTC. */
    private static String formatEpoch(long epochMs) {
        return REGISTRY_TIME_FORMAT.format(Instant.ofEpochMilli(epochMs)) + " UTC";
    }

    /**
     * Formats a millisecond age compactly for {@link #performRegistryList}: the two
     * most significant units of {@code d/h/m/s} (e.g. {@code "3d 4h"}, {@code "2h 5m"},
     * {@code "42s"}); negative inputs (clock skew) clamp to {@code "0s"}.
     */
    private static String formatAge(long ageMs) {
        long totalSeconds = Math.max(0, ageMs) / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        if (hours > 0) return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        if (minutes > 0) return minutes + "m" + (seconds > 0 ? " " + seconds + "s" : "");
        return seconds + "s";
    }

    /**
     * RESET_COOLDOWN (T-085): clears one event's remaining activation cooldown via
     * {@link NewsPlugin#resetCooldown}, making it immediately eligible again. Valid
     * targets are loaded definitions plus any id that currently has a cooldown (a
     * cooldown can outlive its definition file). Only an actual clear is audited —
     * resetting an already-ready event just reports that nothing changed.
     */
    private OutputData handleResetCooldown(NewsPlugin plugin, InputData input, UUID actor) {
        String eventId = input.eventId().trim();
        boolean knownId = plugin.getLibrary().getDefinition(eventId) != null
                || plugin.getCooldownRemainingMs(eventId) > 0;
        if (!knownId) {
            return failure("Unknown news event id '" + eventId + "' (see /stockmarket news list)");
        }

        long remainingMs = plugin.getCooldownRemainingMs(eventId);
        if (!plugin.resetCooldown(eventId)) {
            return success(plugin, "News event '" + eventId + "' was not on cooldown", List.of());
        }

        // Same audit trail as TRIGGER/STOP/SET_ENABLED: resetting a cooldown changes
        // what can fire next, so the other online admins get to see it.
        String audit = getPlayerName(actor) + " reset the cooldown of news event '" + eventId
                + "' (" + NewsUiFormatting.formatRemainingTime(remainingMs) + " remaining)";
        broadcastToAdmins(actor, audit);
        info(audit);

        return success(plugin, "Cooldown of news event '" + eventId + "' reset ("
                + NewsUiFormatting.formatRemainingTime(remainingMs) + " was remaining)", List.of());
    }

    /**
     * INFO: full details of one definition rendered to display lines (T-081) —
     * headline/text, matchers, matched∩subscribed markets, impact envelope, cooldown,
     * enabled state and live phase info when active.
     */
    private OutputData handleInfo(NewsPlugin plugin, InputData input) {
        String eventId = input.eventId().trim();
        NewsEventDefinition definition = plugin.getLibrary().getDefinition(eventId);
        if (definition == null) {
            return failure(plugin.isEventEnabled(eventId)
                    ? "Unknown news event id '" + eventId + "' (see /stockmarket news list)"
                    : "News event '" + eventId + "' is not loaded (file removed?) but still "
                            + "marked disabled — /stockmarket news enable " + eventId
                            + " clears that state");
        }
        return success(plugin, "Details for news event '" + eventId + "'",
                renderEventInfo(plugin, definition, plugin.getMarketInterfaces()));
    }

    /**
     * SET_ENABLED: enables/disables one event id (T-081). Valid targets are loaded
     * definitions plus already-disabled absent ids (so a stale disabled state stays
     * clearable after its file was removed). Audited like TRIGGER. Disabling does not
     * stop an already-active run — the response points the admin at
     * {@code /stockmarket news stop} in that case.
     */
    private OutputData handleSetEnabled(NewsPlugin plugin, InputData input, UUID actor) {
        String eventId = input.eventId().trim();
        boolean enabled = input.enabled();
        boolean knownId = plugin.getLibrary().getDefinition(eventId) != null
                || !plugin.isEventEnabled(eventId);
        if (!knownId) {
            return failure("Unknown news event id '" + eventId + "' (see /stockmarket news list)");
        }
        if (plugin.isEventEnabled(eventId) == enabled) {
            return success(plugin, "News event '" + eventId + "' is already "
                    + (enabled ? "enabled" : "disabled"), List.of());
        }

        plugin.setEventEnabled(eventId, enabled);

        // Same audit trail as TRIGGER/STOP: every enable/disable is visible to the
        // other online admins, with executor, event id and the new state.
        String audit = getPlayerName(actor) + (enabled ? " enabled" : " disabled")
                + " news event '" + eventId + "'";
        broadcastToAdmins(actor, audit);
        info(audit);

        String message = "News event '" + eventId + "' is now "
                + (enabled ? "enabled" : "disabled");
        if (!enabled && plugin.isEventActive(eventId)) {
            message += " (the already-active run keeps playing out — /stockmarket news stop "
                    + eventId + " ends it)";
        }
        return success(plugin, message, List.of());
    }

    /**
     * SET_SCHEDULER (T-082): applies the {@link SchedulerInput} override change via
     * {@link NewsPlugin#applySchedulerOverrides} (validation lives there — on rejection
     * nothing changes and the message is returned as the failure). The response lines
     * always render the resulting scheduler state + upcoming timeline, so a pure query
     * payload doubles as {@code /stockmarket news scheduler show}. Only calls that
     * actually changed a value are audited (a show must not spam the other admins).
     */
    private OutputData handleSetScheduler(NewsPlugin plugin, InputData input, UUID actor) {
        SchedulerInput change = input.scheduler();
        if (change == null) {
            return failure("SET_SCHEDULER requires a scheduler payload");
        }

        NewsPlugin.RuntimeStreamData.SchedulerState before = plugin.getSchedulerState();
        String error = plugin.applySchedulerOverrides(
                change.minSecondsBetweenEvents(), change.maxSecondsBetweenEvents(),
                change.maxActiveEventsGlobal(), change.maxActiveEventsPerMarket(),
                change.resetAll(), plugin.getMarketInterfaces());
        if (error != null) {
            return failure(error);
        }

        NewsPlugin.RuntimeStreamData.SchedulerState after = plugin.getSchedulerState();
        boolean changed = !after.equals(before);
        if (changed) {
            // Same audit trail as TRIGGER/STOP/SET_ENABLED: scheduler changes shape the
            // whole market pacing, so the other online admins get to see them.
            String audit = getPlayerName(actor) + " changed the news scheduler: interval "
                    + after.minSecondsBetweenEvents() + ".." + after.maxSecondsBetweenEvents()
                    + " s, caps " + after.maxActiveEventsGlobal() + " global / "
                    + after.maxActiveEventsPerMarket() + " per market";
            broadcastToAdmins(actor, audit);
            info(audit);
        }

        return success(plugin,
                changed ? "News scheduler updated" : "News scheduler unchanged",
                renderSchedulerState(plugin));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Renders the effective scheduler state + the upcoming-activations timeline to
     * display lines for {@code /stockmarket news scheduler ...} (T-082). Static
     * (plugin passed in) so the in-game test suite can drive it without the
     * request/network machinery; output is admin diagnostics and deliberately not
     * localized (same policy as LIST/INFO).
     *
     * @param plugin the news plugin (scheduler state + planned queue source)
     * @return the rendered display lines (never empty)
     */
    public static List<String> renderSchedulerState(NewsPlugin plugin) {
        NewsPlugin.RuntimeStreamData.SchedulerState state = plugin.getSchedulerState();
        List<String> lines = new ArrayList<>();
        lines.add("Scheduler (override > file value):");
        lines.add("  minSecondsBetweenEvents: " + state.minSecondsBetweenEvents()
                + sourceMarker(state.minOverridden()));
        lines.add("  maxSecondsBetweenEvents: " + state.maxSecondsBetweenEvents()
                + sourceMarker(state.maxOverridden()));
        lines.add("  maxActiveEventsGlobal: " + state.maxActiveEventsGlobal()
                + sourceMarker(state.globalOverridden()));
        lines.add("  maxActiveEventsPerMarket: " + state.maxActiveEventsPerMarket()
                + sourceMarker(state.perMarketOverridden()));

        List<NewsPlugin.RuntimeStreamData.UpcomingEvent> upcoming = plugin.getPlannedActivations();
        lines.add("Upcoming activations (" + upcoming.size() + "):");
        int slot = 1;
        for (NewsPlugin.RuntimeStreamData.UpcomingEvent entry : upcoming) {
            lines.add("  " + slot++ + ". in " + NewsUiFormatting.formatRemainingTime(entry.etaMs())
                    + ": " + (entry.eventId().isEmpty()
                            ? "(event picked at fire time)" : entry.eventId()));
        }
        return lines;
    }

    /** @return the value-source suffix for {@link #renderSchedulerState} lines */
    private static String sourceMarker(boolean overridden) {
        return overridden ? " [override]" : " [file]";
    }

    /** @return a short human-readable description of a hard-stop outcome (T-093) */
    private static String describeOutcome(NewsPlugin.StopOutcome outcome) {
        return switch (outcome) {
            case CANCELLED_BEFORE_IMPACT ->
                    "cancelled before its impact started (cooldown restarted)";
            case CANCELLED_PERMANENT ->
                    "reversal:none cancelled — the permanent shift was NOT baked (cooldown restarted)";
            case STOPPED -> "stopped — price influence removed (cooldown restarted)";
        };
    }

    /** @return a short human-readable description of a skip-phase outcome (T-093/T-095) */
    private static String describeSkip(NewsPlugin.SkipOutcome outcome) {
        return switch (outcome) {
            case SKIPPED_TO_RAMP_UP -> "announce delay skipped — the impact starts now";
            case SKIPPED_TO_HOLD -> "ramp-up skipped — now holding at peak influence";
            case SKIPPED_TO_REVERSAL -> "hold skipped — now reverting to normal";
            case SKIPPED_TO_PERMANENT ->
                    "step skipped — permanent end, the full permanent shift bakes now";
            case SKIPPED_TO_END -> "last step skipped — the event ends now";
            // T-095: sequence-authored events — performSkipPhase appends the reached
            // step name (the enum constant cannot carry it).
            case SKIPPED_TO_STEP -> "step skipped";
            case NOTHING_TO_SKIP -> "already in its terminal phase"; // handled by the caller
        };
    }

    /**
     * Builds the standard success response: message + lines plus the id lists and the
     * structured {@link EventDetails} snapshot for the GUI (fresh on every successful op).
     */
    private OutputData success(NewsPlugin plugin, String message, List<String> lines) {
        return new OutputData(true, message, lines, definitionIds(plugin), activeEventIds(plugin),
                buildEventDetails(plugin, plugin.getMarketInterfaces()));
    }

    /**
     * Renders one definition's full details to human-readable display lines for
     * {@code /stockmarket news info <eventId>} (T-081): enabled/adminOnly/active markers,
     * headline + text (server-side language pick: {@code en_us}, else the first
     * translation entry), scheduling parameters, the impact envelope (legacy events —
     * rendered byte-identical to pre-T-099) or the authored sequence/step breakdown
     * (sequence events, T-099), the portable matchers, the trigger requirements with
     * their live met/unmet status and the chain entries (both only when authored,
     * T-099), the markets a trigger would currently impact (matched ∩ subscribed ∩
     * news-enabled, with weight factors and the resulting peak influence) and — when an
     * instance is running — its live phase/remaining/published state.
     * <p>
     * Static (plugin/markets passed in) so the in-game test suite can drive it without
     * the request/network machinery. Output strings are admin diagnostics and
     * deliberately not localized (same policy as LIST/renderReport).
     *
     * @param plugin     the news plugin (enabled/active/cooldown state source)
     * @param definition the definition to render
     * @param markets    the plugin's subscribed markets (normally
     *                   {@code plugin.getMarketInterfaces()})
     * @return the rendered display lines (never empty)
     */
    public static List<String> renderEventInfo(NewsPlugin plugin, NewsEventDefinition definition,
                                               List<MarketInterface> markets) {
        List<String> lines = new ArrayList<>();
        String id = definition.getId();

        StringBuilder header = new StringBuilder("Event '").append(id).append("'");
        if (!plugin.isEventEnabled(id)) header.append(" [disabled]");
        if (definition.isAdminOnly()) header.append(" [adminOnly]");
        if (plugin.isEventActive(id)) header.append(" [active]");
        lines.add(header.toString());

        lines.add("  Headline: " + serverDisplayText(definition.getHeadline()));
        lines.add("  Text: " + serverDisplayText(definition.getText()));
        lines.add("  Category: " + definition.getCategory() + ", weight " + definition.getWeight());

        long cooldownMs = plugin.getCooldownRemainingMs(id);
        lines.add("  Cooldown: " + definition.getCooldownSeconds() + " s"
                + (cooldownMs > 0
                        ? " (" + NewsUiFormatting.formatRemainingTime(cooldownMs) + " remaining)"
                        : ""));

        NewsEventDefinition.AnnounceDelayRange delay = definition.getAnnounceDelayMs();
        lines.add("  Announce delay: " + delay.minMs() + " .. " + delay.maxMs()
                + " ms (positive = headline first, negative = impact first)");

        // T-099 NPE fix: getImpact() is null for sequences[]-authored events (T-094) —
        // only legacy impact events render the classic (byte-identical) Impact line;
        // sequence events render their authored sequence/step breakdown instead.
        NewsImpactEnvelope impact = definition.getImpact();
        if (impact != null) {
            boolean permanent = impact.getReversal() == NewsImpactEnvelope.ReversalMode.NONE;
            lines.add("  Impact: " + impact.getType().jsonName()
                    + ", peak " + NewsUiFormatting.formatFactorPercent(1.0 + impact.getPeakFactor())
                    + " (peakFactor " + impact.getPeakFactor() + ")"
                    + ", rampUp " + (impact.getRampUpMillis() / 1000L) + " s"
                    + ", hold " + (impact.getHoldMillis() / 1000L) + " s"
                    + ", reversal " + impact.getReversal().jsonName()
                    + (permanent ? " (permanent shift bakes into the default price)"
                            : " (" + (impact.getReversalMillis() / 1000L) + " s)"));
        } else {
            List<NewsEventDefinition.SequenceDefinition> sequences = definition.getSequences();
            lines.add("  Sequences (" + sequences.size() + ", one picked by weight at activation):");
            for (NewsEventDefinition.SequenceDefinition sequence : sequences) {
                lines.add("    - '" + sequence.getName() + "' weight " + sequence.getWeight()
                        + ", " + sequence.getStepCount() + " step(s):");
                int index = 1;
                for (NewsEventDefinition.StepDefinition step : sequence.getSteps()) {
                    StringBuilder line = new StringBuilder("        ").append(index++)
                            .append(". '").append(step.getName()).append("' ")
                            .append(formatStepDuration(step))
                            .append(" -> ").append(NewsUiFormatting.formatFactorPercent(
                                    1.0 + step.getTargetFactor()))
                            .append(" (targetFactor ").append(step.getTargetFactor()).append(")")
                            .append(", curve ").append(step.getCurve().jsonName());
                    if (step.isPermanent()) line.append(" [permanent]");
                    if (step.getMarkets() != null) {
                        line.append(" [own markets: ").append(step.getMarkets().size())
                                .append(" matcher(s)]");
                    }
                    lines.add(line.toString());
                }
            }
        }

        List<NewsEventDefinition.MarketMatcher> matchers = definition.getMarkets();
        lines.add("  Matchers (" + matchers.size() + "):");
        for (NewsEventDefinition.MarketMatcher matcher : matchers) {
            lines.add("    - " + matcher.getKind().name().toLowerCase(Locale.ROOT)
                    + " '" + matcher.getPattern() + "' weightFactor " + matcher.getWeightFactor());
        }

        // T-099: trigger requirements with their live met/unmet status (plan §3/§10.1)
        // and chain entries (plan §4). Both blocks only appear when authored, so
        // events without them keep rendering byte-identical to pre-T-099.
        List<NewsRequirement> requirements = definition.getRequirements();
        if (!requirements.isEmpty()) {
            NewsWorldRegistry registry = registryOrEmpty(plugin);
            long nowEpochMs = System.currentTimeMillis();
            lines.add("  Requires (" + requirements.size() + ", ALL must hold):");
            for (NewsRequirement requirement : requirements) {
                lines.add("    - [" + (requirement.test(registry, nowEpochMs) ? "met" : "UNMET")
                        + "] " + requirement.describe());
            }
        }
        List<NewsEventDefinition.ChainDefinition> chains = definition.getChains();
        if (!chains.isEmpty()) {
            lines.add("  Chains (" + chains.size() + "):");
            for (NewsEventDefinition.ChainDefinition chain : chains) {
                lines.add("    - " + describeChain(chain));
            }
        }

        // What a trigger would do right now: matched ∩ subscribed ∩ news-enabled markets.
        // The peak factor is the envelope's for legacy events (unchanged rendering) and
        // the largest-magnitude step target across all sequences otherwise.
        double peakFactor = impact != null ? impact.getPeakFactor()
                : definitionPeakFactor(definition);
        Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(definition, markets, null);
        lines.add("  Would impact " + resolved.size() + " subscribed market(s) when triggered:");
        for (Map.Entry<ItemID, Float> entry : resolved.entrySet()) {
            double peakTerm = NewsPlugin.eventFactorTerm(1.0, peakFactor,
                    entry.getValue(), 1.0, 0);
            lines.add("    " + marketDisplayName(entry.getKey())
                    + " weightFactor " + entry.getValue()
                    + " (peak " + NewsUiFormatting.formatFactorPercent(peakTerm) + ")");
        }

        ActiveNewsEvent active = plugin.findActiveEvent(id);
        if (active != null) {
            String phase = active.phaseName(); // T-095: incl. step names for sequences
            lines.add("  Currently active (uid " + active.getNewsUid() + "): " + phase + ", "
                    + NewsUiFormatting.formatRemainingTime(active.remainingMillis())
                    + " remaining, " + (active.isPublished() ? "published" : "pending publication"));
        }
        return lines;
    }

    /**
     * Builds the structured {@link EventDetails} snapshot for every loaded library
     * definition (T-081, consumed by the T-083 GUI). Static (plugin/markets passed in)
     * so the in-game test suite can drive it without the request/network machinery.
     *
     * @param plugin  the news plugin (enabled/active/cooldown state source)
     * @param markets the plugin's subscribed markets (normally
     *                {@code plugin.getMarketInterfaces()})
     * @return one entry per loaded definition, in library (file) order
     */
    public static List<EventDetails> buildEventDetails(NewsPlugin plugin,
                                                       List<MarketInterface> markets) {
        // T-099: requirement statuses are evaluated against the LIVE registry at
        // snapshot time (plan §10.1 — the client trigger-confirm popup renders them).
        // The clock and registry are shared across all definitions of one snapshot.
        NewsWorldRegistry registry = registryOrEmpty(plugin);
        long nowEpochMs = System.currentTimeMillis();
        // Per-step matcher lists resolve against the subscribed market set (display
        // snapshot — the activation-time resolution additionally honors newsEnabled).
        Set<ItemID> subscribedIds = new LinkedHashSet<>();
        for (MarketInterface marketInterface : markets) {
            subscribedIds.add(marketInterface.market.getMarketID());
        }

        List<EventDetails> details = new ArrayList<>(plugin.getLibrary().getDefinitionCount());
        for (NewsEventDefinition definition : plugin.getLibrary().getDefinitions().values()) {
            String id = definition.getId();

            Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(definition, markets, null);
            List<EventDetails.MarketImpact> marketImpacts = new ArrayList<>(resolved.size());
            for (Map.Entry<ItemID, Float> entry : resolved.entrySet()) {
                marketImpacts.add(new EventDetails.MarketImpact(entry.getKey().getShort(),
                        marketDisplayName(entry.getKey()), entry.getValue()));
            }

            // T-091: resolve the definition's picture reference through the
            // config-layer picture library (same lookup as the publish-time snapshot).
            // A missing/invalid file yields a null hash — the details screen then
            // simply shows no picture box.
            String pictureFileName = definition.getPictureFileName() != null
                    ? definition.getPictureFileName() : "";
            NewsPictureLibrary.Entry pictureEntry =
                    plugin.getLibrary().getPictureLibrary().get(definition.getPictureFileName());
            byte[] pictureHash = pictureEntry != null ? pictureEntry.getSha1() : null;

            // T-099 NPE fix: getImpact() is null for sequences[]-authored events
            // (T-094). Legacy events keep their exact envelope descriptor values;
            // sequence events fill the legacy fields with safe analogues (peak =
            // largest-magnitude step target, reversal = "sequence") — clients must
            // render sequence events from the sequences() block instead.
            NewsImpactEnvelope impact = definition.getImpact();
            double peakFactor;
            long rampUpSeconds;
            long durationSeconds;
            String reversal;
            long reversalSeconds;
            if (impact != null) {
                peakFactor = impact.getPeakFactor();
                rampUpSeconds = impact.getRampUpMillis() / 1000L;
                durationSeconds = impact.getHoldMillis() / 1000L;
                reversal = impact.getReversal().jsonName();
                reversalSeconds = impact.getReversalMillis() / 1000L;
            } else {
                peakFactor = definitionPeakFactor(definition);
                rampUpSeconds = 0L;
                durationSeconds = 0L;
                reversal = "sequence";
                reversalSeconds = 0L;
            }

            // T-099: per-requirement live status + rendered chain lines.
            List<EventDetails.RequirementStatus> requirementStatus =
                    new ArrayList<>(definition.getRequirements().size());
            for (NewsRequirement requirement : definition.getRequirements()) {
                requirementStatus.add(new EventDetails.RequirementStatus(
                        requirement.describe(), requirement.test(registry, nowEpochMs)));
            }
            List<String> chainLines = new ArrayList<>(definition.getChains().size());
            for (NewsEventDefinition.ChainDefinition chain : definition.getChains()) {
                chainLines.add(describeChain(chain));
            }

            details.add(new EventDetails(id,
                    plugin.isEventEnabled(id),
                    definition.isAdminOnly(),
                    plugin.isEventActive(id),
                    plugin.getCooldownRemainingMs(id),
                    definition.getWeight(),
                    definition.getHeadline(),
                    definition.getText(),
                    definition.getAnnounceDelayMs().minMs(),
                    definition.getAnnounceDelayMs().maxMs(),
                    peakFactor,
                    rampUpSeconds,
                    durationSeconds,
                    reversal,
                    reversalSeconds,
                    marketImpacts,
                    pictureFileName,
                    pictureHash,
                    buildSequenceInfos(definition, subscribedIds),
                    requirementStatus,
                    chainLines));
        }
        return details;
    }

    /**
     * Builds the {@link EventDetails.SequenceInfo} block for one definition (T-099):
     * every event exposes its behavior uniformly via
     * {@link NewsEventDefinition#getSequences()} — legacy {@code impact} events yield
     * their ONE implicit normalized "impact" sequence, so the T-100 details screen
     * renders a step list for every event. Steps with their own {@code markets[]}
     * carry the matched∩subscribed resolution; steps without one carry an empty list
     * (= "inherits the event-level markets").
     *
     * @param definition    the definition to snapshot
     * @param subscribedIds the currently subscribed market ids (per-step matcher
     *                      resolution candidates)
     * @return one entry per sequence, in JSON order (never empty for a loaded event)
     */
    private static List<EventDetails.SequenceInfo> buildSequenceInfos(
            NewsEventDefinition definition, Set<ItemID> subscribedIds) {
        List<EventDetails.SequenceInfo> sequences =
                new ArrayList<>(definition.getSequences().size());
        for (NewsEventDefinition.SequenceDefinition sequence : definition.getSequences()) {
            List<EventDetails.StepInfo> steps = new ArrayList<>(sequence.getStepCount());
            for (NewsEventDefinition.StepDefinition step : sequence.getSteps()) {
                List<EventDetails.MarketImpact> stepMarkets = List.of();
                if (step.getMarkets() != null) {
                    Map<ItemID, Float> resolved =
                            NewsEventDefinition.resolveMatchers(step.getMarkets(), subscribedIds);
                    stepMarkets = new ArrayList<>(resolved.size());
                    for (Map.Entry<ItemID, Float> entry : resolved.entrySet()) {
                        stepMarkets.add(new EventDetails.MarketImpact(entry.getKey().getShort(),
                                marketDisplayName(entry.getKey()), entry.getValue()));
                    }
                }
                steps.add(new EventDetails.StepInfo(step.getName(),
                        step.getDurationMinMs(), step.getDurationMaxMs(),
                        step.getTargetFactor(), step.getCurve().jsonName(),
                        step.isPermanent(), stepMarkets));
            }
            sequences.add(new EventDetails.SequenceInfo(sequence.getName(),
                    sequence.getWeight(), steps));
        }
        return sequences;
    }

    /**
     * The largest-magnitude (signed) step target across ALL of a definition's
     * sequences — the peak-factor analogue for events without a legacy envelope
     * (same derivation as {@link NewsPlugin#peakSequenceValue} on the resolved
     * sequence; for legacy events this equals the envelope's {@code peakFactor}
     * by normalization).
     *
     * @param definition the definition to scan
     * @return the target value with the largest absolute magnitude (0 if none)
     */
    private static double definitionPeakFactor(NewsEventDefinition definition) {
        double peak = 0;
        for (NewsEventDefinition.SequenceDefinition sequence : definition.getSequences()) {
            for (NewsEventDefinition.StepDefinition step : sequence.getSteps()) {
                if (Math.abs(step.getTargetFactor()) > Math.abs(peak)) {
                    peak = step.getTargetFactor();
                }
            }
        }
        return peak;
    }

    /**
     * Renders one chain entry to a display line for INFO/EventDetails (T-099), e.g.
     * {@code "on step 'hold' -> gold_rush_rumor (chance 0.5, delay 300..900 s, same markets)"}.
     * Static and pure for testability; not localized (admin diagnostics policy).
     *
     * @param chain the chain definition to render
     * @return the rendered line (never null/blank)
     */
    public static String describeChain(NewsEventDefinition.ChainDefinition chain) {
        StringBuilder sb = new StringBuilder("on ").append(chain.on().jsonName());
        if (chain.on() == NewsEventDefinition.ChainTriggerMoment.STEP
                && chain.stepName() != null) {
            sb.append(" '").append(chain.stepName()).append("'");
        }
        sb.append(" -> ").append(chain.targetEventId())
                .append(" (chance ").append(chain.chance());
        if (chain.delayMaxMs() > 0) {
            sb.append(", delay ").append(chain.delayMinMs() / 1000L)
                    .append("..").append(chain.delayMaxMs() / 1000L).append(" s");
        }
        if (chain.sameMarkets()) sb.append(", same markets");
        return sb.append(")").toString();
    }

    /**
     * Formats a step's duration for {@link #renderEventInfo}: {@code "10 s"} for fixed
     * durations, {@code "10..30 s"} for ranges (whole seconds — authoring granularity).
     */
    private static String formatStepDuration(NewsEventDefinition.StepDefinition step) {
        long minSeconds = step.getDurationMinMs() / 1000L;
        long maxSeconds = step.getDurationMaxMs() / 1000L;
        return minSeconds == maxSeconds ? minSeconds + " s" : minSeconds + ".." + maxSeconds + " s";
    }

    /**
     * The plugin's world-event registry, or a fresh empty one when none is wired
     * (unit-test contexts / defensive) — requirement predicates then evaluate against
     * "nothing ever fired, no keys", which is deterministic and side-effect free.
     */
    private static NewsWorldRegistry registryOrEmpty(NewsPlugin plugin) {
        NewsWorldRegistry registry = plugin.getNewsWorldRegistry();
        return registry != null ? registry : new NewsWorldRegistry();
    }

    /**
     * Server-side language pick for translation maps in INFO lines: {@code en_us}, else
     * the first map entry ({@link NewsTranslations#resolve} without an exact language —
     * INFO output is admin diagnostics; the GUI resolves the real client locale from the
     * full maps in {@link EventDetails} instead).
     */
    private static String serverDisplayText(Map<String, String> translations) {
        return NewsTranslations.resolve(translations, null);
    }

    /**
     * Renders a {@link ValidationReport} to display lines: grouped per file (directory-level
     * entries first under a generic header), errors before warnings within each file.
     * Static and pure for testability.
     *
     * @param report the reload report
     * @return the rendered lines (empty list for a clean report)
     */
    public static List<String> renderReport(ValidationReport report) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<ValidationReport.Entry>> fileEntry : report.byFile().entrySet()) {
            lines.add((fileEntry.getKey().isEmpty() ? "(news folder)" : fileEntry.getKey()) + ":");
            // Errors first, then warnings, preserving recording order within each group.
            for (ValidationReport.Severity severity : ValidationReport.Severity.values()) {
                for (ValidationReport.Entry entry : fileEntry.getValue()) {
                    if (entry.severity() != severity) continue;
                    StringBuilder line = new StringBuilder("  [").append(severity).append("]");
                    if (entry.eventId() != null) {
                        line.append(" (event '").append(entry.eventId()).append("')");
                    }
                    line.append(" ").append(entry.message());
                    lines.add(line.toString());
                }
            }
        }
        return lines;
    }

    /**
     * Finds all subscribed markets matching a market identifier — the registry name
     * (component variants share it) or the unique numeric ItemID as a string. Mirrors
     * the matching of {@code /stockmarket <market> remove}.
     */
    private static Set<ItemID> findSubscribedMarkets(List<MarketInterface> markets, String marketArg) {
        Set<ItemID> matches = new LinkedHashSet<>();
        for (MarketInterface marketInterface : markets) {
            ItemID marketID = marketInterface.market.getMarketID();
            if (marketDisplayName(marketID).equals(marketArg)
                    || String.valueOf(marketID.getShort()).equals(marketArg)) {
                matches.add(marketID);
            }
        }
        return matches;
    }

    /** @return the market's registry name, falling back to the numeric id on failure */
    private static String marketDisplayName(ItemID marketID) {
        try {
            return marketID.getName();
        } catch (Exception e) {
            return String.valueOf(marketID.getShort());
        }
    }

    /** @return the ids of all loaded definitions, in file order */
    private static List<String> definitionIds(NewsPlugin plugin) {
        return new ArrayList<>(plugin.getLibrary().getDefinitions().keySet());
    }

    /** @return the definition ids of the currently active events, in activation order */
    private static List<String> activeEventIds(NewsPlugin plugin) {
        List<String> ids = new ArrayList<>();
        for (ActiveNewsEvent event : plugin.getActiveEvents()) {
            ids.add(event.getDefinitionId());
        }
        return ids;
    }

    /** @return the master's NewsPlugin instance, or null if none exists */
    private @Nullable NewsPlugin findNewsPlugin() {
        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        if (pluginManager == null) return null;
        for (ServerPlugin<?, ?> plugin : pluginManager.getPlugins().values()) {
            if (plugin instanceof NewsPlugin newsPlugin) {
                return newsPlugin;
            }
        }
        return null;
    }

    /** @return a failed response with the given message and empty lists */
    private static OutputData failure(String message) {
        return new OutputData(false, message, List.of(), List.of(), List.of(), List.of());
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
