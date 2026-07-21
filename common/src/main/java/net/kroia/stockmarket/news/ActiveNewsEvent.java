package net.kroia.stockmarket.news;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable runtime state of one activated news event, owned by the NewsPlugin (T-071,
 * sequence runtime since T-095).
 * <p>
 * An instance is a <b>self-contained snapshot</b> of everything the plugin needs to run
 * the event to completion: the fully resolved {@link NewsSequence} (sequence picked by
 * weight, every step duration already rolled), the per-step resolved market weight maps,
 * the headline/text translation maps and the union market map are all frozen at
 * activation time. This is deliberate — if the definition disappears or changes during a
 * library reload, the already-active event still finishes gracefully with its original
 * parameters, and a server restart can never re-roll anything (plan §2.5 / sequences
 * plan §2).
 * <p>
 * <b>Time basis (plan §6.9):</b> all progress is tracked in {@link #getAgeMillis() ageMs},
 * the accumulated milliseconds <i>while the server was ticking</i> since activation —
 * never raw wall-clock time. The plugin advances it via {@link #advance(long)} once per
 * tick, so events do not silently expire while the server is paused or the world is
 * unloaded. Persisting the accumulators (instead of absolute timestamps) makes a restart
 * inside any phase — including the pending-publish window — resume exactly where it left
 * off.
 * <p>
 * <b>Announce-delay semantics (plan §1/§2.4):</b> at activation one delay is sampled
 * uniformly from the definition's {@code announceDelayMs} range. The delay is the impact
 * start relative to the headline publish, mapped onto the age timeline as two moments:
 * <pre>
 *   delay &gt;= 0:  publishAgeMs = 0,      impactStartAgeMs = delay   (headline first)
 *   delay &lt;  0:  publishAgeMs = -delay, impactStartAgeMs = 0       (impact first)
 * </pre>
 * {@link #activeMillis()} (the sequence input) is {@code ageMs - impactStartAgeMs} and is
 * negative while the impact has not started yet, which {@link NewsSequence#value} maps to
 * zero influence.
 * <p>
 * <b>Per-step markets (sequences plan §2):</b> every step carries its own resolved
 * {@code market → weightFactor} map ({@link #currentStepWeights()} feeds the influence
 * application); the {@link #getMarketWeights() union} of all step maps drives the
 * activity caps, the published record and the unsubscribe handling. Steps without their
 * own {@code markets[]} inherited the event-level resolution at activation.
 * <p>
 * <b>Bake bookkeeping (permanent last step, sequences plan §2):</b> when a sequence with
 * a {@code permanent} last step ends, the plugin bakes the final influence value into
 * each market of the <b>last step's</b> map ({@link #getPendingBakeWeights()}) and calls
 * {@link #markBaked(ItemID)}, which moves the market out of the union and all step maps
 * into the {@link #getBakedMarkets() baked map}. Because all maps are persisted, a market
 * can never be baked twice across restarts: baking only ever happens for entries still
 * present in the pending-bake map.
 * <p>
 * <b>Legacy descriptor:</b> events activated from a legacy {@code impact} block
 * additionally keep their resolved {@link NewsImpactEnvelope} as a <i>descriptor
 * snapshot</i> ({@link #getLegacyEnvelope()}) — it no longer drives any math (the
 * normalized sequence does, with bit-equivalent values), but it preserves the record
 * fields ({@code impactType}/{@code peakFactor}/{@code reversal}) and the legacy phase
 * names ({@code RAMPING}/{@code HOLDING}/{@code REVERTING}) across restarts.
 */
public final class ActiveNewsEvent implements ServerSaveable {

    // ── NBT keys ─────────────────────────────────────────────────────────

    private static final String KEY_DEFINITION_ID = "definitionId";
    private static final String KEY_NEWS_UID = "newsUid";
    private static final String KEY_HEADLINE = "headline";
    private static final String KEY_TEXT = "text";
    private static final String KEY_SAMPLED_DELAY = "sampledDelayMs";
    private static final String KEY_AGE = "ageMs";
    private static final String KEY_PUBLISHED = "published";
    private static final String KEY_MARKET_WEIGHTS = "marketWeights";
    private static final String KEY_BAKED_MARKETS = "bakedMarkets";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_LANG = "lang";
    private static final String KEY_VALUE = "value";
    // Legacy envelope parameters (descriptor snapshot, seconds like the JSON schema).
    // Written only for legacy-impact events; pre-T-095 saves are read through the same
    // keys and normalized into an implicit sequence (see load()).
    private static final String KEY_IMPACT_TYPE = "impactType";
    private static final String KEY_PEAK_FACTOR = "peakFactor";
    private static final String KEY_RAMP_UP_SECONDS = "rampUpSeconds";
    private static final String KEY_DURATION_SECONDS = "durationSeconds";
    private static final String KEY_REVERSAL = "reversal";
    private static final String KEY_REVERSAL_SECONDS = "reversalSeconds";
    private static final String KEY_NOISE = "noise";
    // Resolved sequence snapshot (T-095): the picked sequence name and one entry per
    // step with the ROLLED duration — a restart must never re-roll.
    private static final String KEY_SEQUENCE_NAME = "sequenceName";
    private static final String KEY_STEPS = "steps";
    private static final String KEY_STEP_NAME = "name";
    private static final String KEY_STEP_DURATION_MS = "durationMs";
    private static final String KEY_STEP_TARGET = "targetValue";
    private static final String KEY_STEP_CURVE = "curve";
    private static final String KEY_STEP_NOISE = "noise";
    private static final String KEY_STEP_PERMANENT = "permanent";
    /** Per-step market map; absent = the step map equals the union map (storage saving). */
    private static final String KEY_STEP_MARKETS = "markets";

    /** Sequence name used for legacy {@code impact} events (mirrors the implicit definition). */
    public static final String LEGACY_SEQUENCE_NAME = "impact";

    // ── State ────────────────────────────────────────────────────────────

    private String definitionId = "";
    private long newsUid;
    /** Headline snapshot (insertion-ordered language map) taken at activation. */
    private Map<String, String> headline = new LinkedHashMap<>();
    /** Newspaper-text snapshot (insertion-ordered language map) taken at activation. */
    private Map<String, String> text = new LinkedHashMap<>();
    /** Legacy impact-envelope descriptor snapshot; null for sequence-authored events. */
    private @Nullable NewsImpactEnvelope legacyEnvelope;
    /** Name of the sequence picked at activation (persisted, shown in the UI). */
    private String sequenceName = LEGACY_SEQUENCE_NAME;
    /** The fully resolved sequence (durations rolled at activation, immutable). */
    private NewsSequence sequence;
    /** The delay sampled from the definition's announceDelayMs range at activation. */
    private long sampledDelayMs;
    /** Age of the event on the "ticking milliseconds" timeline (see class Javadoc). */
    private long ageMs;
    /** Age at which the impact starts ({@code max(sampledDelayMs, 0)}). */
    private long impactStartAgeMs;
    /** Age at which the headline publishes ({@code max(-sampledDelayMs, 0)}). */
    private long publishAgeMs;
    /** Whether the NewsRecord for this event has been published already. */
    private boolean published;
    /** Union of all step maps: every market this event still references → weight factor. */
    private Map<ItemID, Float> marketWeights = new LinkedHashMap<>();
    /** One resolved market → weight map per sequence step (same order as the steps). */
    private List<Map<ItemID, Float>> stepMarketWeights = new ArrayList<>();
    /** Markets whose permanent shift was already baked into the default price. */
    private Map<ItemID, Float> bakedMarkets = new LinkedHashMap<>();

    /** Empty instance for the {@code load(tag)} path. */
    private ActiveNewsEvent() {
    }

    /**
     * Creates the runtime state for a freshly activated event (T-095 primary form).
     * <p>
     * Everything passed in must already be fully resolved — this constructor never rolls
     * anything: the sequence index was picked by weight and every step duration was
     * rolled by the plugin before {@code SequenceDefinition.createSequence} produced the
     * {@code sequence}. The union market map is derived from the step maps here (first
     * occurrence wins for the union weight).
     *
     * @param definitionId      the id of the definition this event was activated from
     * @param newsUid           the monotonic news uid assigned at activation (stable across
     *                          restarts even when the publish happens after a reload)
     * @param headline          headline translation map (copied)
     * @param text              newspaper-text translation map (copied)
     * @param legacyEnvelope    the legacy impact-envelope descriptor for {@code impact}
     *                          events (record/phase-name fidelity), or null for
     *                          sequence-authored events
     * @param sequenceName      the name of the picked sequence definition
     * @param sequence          the resolved sequence (durations rolled, immutable, shared)
     * @param stepMarketWeights one resolved market → weight map per step, in step order
     *                          (each copied; missing trailing entries default to empty maps)
     * @param sampledDelayMs    the announce delay sampled for this activation (see class Javadoc)
     */
    public ActiveNewsEvent(String definitionId, long newsUid,
                           Map<String, String> headline, Map<String, String> text,
                           @Nullable NewsImpactEnvelope legacyEnvelope,
                           String sequenceName, NewsSequence sequence,
                           List<Map<ItemID, Float>> stepMarketWeights, long sampledDelayMs) {
        this.definitionId = definitionId;
        this.newsUid = newsUid;
        this.headline = new LinkedHashMap<>(headline);
        this.text = new LinkedHashMap<>(text);
        this.legacyEnvelope = legacyEnvelope;
        this.sequenceName = (sequenceName == null || sequenceName.isEmpty())
                ? LEGACY_SEQUENCE_NAME : sequenceName;
        this.sequence = sequence;
        this.sampledDelayMs = sampledDelayMs;
        this.impactStartAgeMs = Math.max(sampledDelayMs, 0);
        this.publishAgeMs = Math.max(-sampledDelayMs, 0);

        // Copy the per-step maps (defensively padded/truncated to the step count) and
        // derive the union in step order — first occurrence wins for the union weight.
        this.stepMarketWeights = new ArrayList<>(sequence.stepCount());
        for (int i = 0; i < sequence.stepCount(); i++) {
            Map<ItemID, Float> stepMap = (stepMarketWeights != null && i < stepMarketWeights.size()
                    && stepMarketWeights.get(i) != null)
                    ? new LinkedHashMap<>(stepMarketWeights.get(i))
                    : new LinkedHashMap<>();
            this.stepMarketWeights.add(stepMap);
            for (Map.Entry<ItemID, Float> entry : stepMap.entrySet()) {
                this.marketWeights.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Legacy convenience constructor ({@code impact}-envelope form): normalizes the
     * envelope into its implicit sequence ({@link NewsSequence#fromLegacyEnvelope}) and
     * applies the single resolved market map to every step — exactly what the pre-T-095
     * runtime did with its one envelope-wide map.
     *
     * @param definitionId   the id of the definition this event was activated from
     * @param newsUid        the monotonic news uid assigned at activation
     * @param headline       headline translation map (copied)
     * @param text           newspaper-text translation map (copied)
     * @param envelope       the resolved legacy impact envelope (kept as descriptor)
     * @param sampledDelayMs the announce delay sampled for this activation
     * @param marketWeights  the resolved matched∩subscribed market subset → weight factor
     */
    public ActiveNewsEvent(String definitionId, long newsUid,
                           Map<String, String> headline, Map<String, String> text,
                           NewsImpactEnvelope envelope, long sampledDelayMs,
                           Map<ItemID, Float> marketWeights) {
        this(definitionId, newsUid, headline, text, envelope, LEGACY_SEQUENCE_NAME,
                NewsSequence.fromLegacyEnvelope(envelope),
                replicateMap(marketWeights, NewsSequence.fromLegacyEnvelope(envelope).stepCount()),
                sampledDelayMs);
    }

    /** @return {@code count} independent copies of the given map (legacy per-step maps). */
    private static List<Map<ItemID, Float>> replicateMap(Map<ItemID, Float> map, int count) {
        List<Map<ItemID, Float>> maps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            maps.add(new LinkedHashMap<>(map));
        }
        return maps;
    }

    // ── Timeline ─────────────────────────────────────────────────────────

    /**
     * Advances the event's age. Called by the plugin once per server tick with the
     * elapsed (clamped) tick delta — never with wall-clock gaps.
     *
     * @param elapsedMs the ticked milliseconds to add (values &lt;= 0 are ignored)
     */
    public void advance(long elapsedMs) {
        if (elapsedMs > 0) ageMs += elapsedMs;
    }

    /**
     * @return the sequence input time: accumulated ticking milliseconds since the impact
     *         start; negative while the impact is still pending (positive announce delay)
     */
    public long activeMillis() {
        return ageMs - impactStartAgeMs;
    }

    /**
     * @return true if the publish moment has been reached but the record has not been
     *         published yet
     */
    public boolean isPublishDue() {
        return !published && ageMs >= publishAgeMs;
    }

    /** Marks the NewsRecord of this event as published (exactly once per event). */
    public void markPublished() {
        published = true;
    }

    // ── Phase / step state (T-095) ───────────────────────────────────────

    /**
     * The display phase name of this event, generalizing the pre-sequence phase
     * vocabulary (sequences plan §2):
     * <ul>
     *   <li>{@code "PENDING"} — the impact has not started yet (positive announce delay);</li>
     *   <li>{@code "PERMANENT"} / {@code "EXPIRED"} — the sequence is over (terminal);</li>
     *   <li>running <b>legacy</b> events keep their classic names ({@code "RAMPING"} /
     *       {@code "HOLDING"} / {@code "REVERTING"}, mapped from the implicit step names)
     *       so the existing GUI/lang rendering stays byte-identical;</li>
     *   <li>running <b>sequence-authored</b> events report the current step's name
     *       (the dedicated stepName/stepIndex stream fields are T-099's).</li>
     * </ul>
     *
     * @return the phase display name (never null/empty)
     */
    public String phaseName() {
        if (activeMillis() < 0) return "PENDING";
        if (isSequenceOver()) return sequence.isPermanent() ? "PERMANENT" : "EXPIRED";
        String stepName = currentStepName();
        if (legacyEnvelope != null) {
            // Legacy events: map the implicit normalized step names back onto the
            // pre-T-095 envelope phase names (GUI colors/lang keys key off these).
            switch (stepName) {
                case "ramp": return "RAMPING";
                case "hold": return "HOLDING";
                case "reversal": return "REVERTING";
                default: break; // fall through to the raw step name (defensive)
            }
        }
        return stepName;
    }

    /** @return true once the sequence timeline is over ({@code activeMillis >= totalDurationMs}) */
    public boolean isSequenceOver() {
        return activeMillis() >= sequence.totalDurationMs();
    }

    /**
     * @return true if this event's sequence ends in a {@code permanent} step: at sequence
     *         end the final influence value bakes into the default price instead of
     *         snapping off (successor of {@code reversal:none})
     */
    public boolean isPermanent() {
        return sequence.isPermanent();
    }

    /**
     * @return true when the permanent shift must be baked <b>now</b>: the sequence is
     *         over, its last step is {@code permanent} and at least one of the last
     *         step's markets has not been baked yet (the plugin bakes and then calls
     *         {@link #markBaked}, which empties this condition exactly once per market)
     */
    public boolean isPermanentShiftDue() {
        return activeMillis() >= 0 && isSequenceOver() && sequence.isPermanent()
                && !getPendingBakeWeights().isEmpty();
    }

    /**
     * @return the index of the step active at the current time (clamped — see
     *         {@link NewsSequence#stepIndexAt}); step 0 while the impact is PENDING
     */
    public int currentStepIndex() {
        return sequence.stepIndexAt(activeMillis());
    }

    /** @return the name of the step active at the current time */
    public String currentStepName() {
        return sequence.getStep(currentStepIndex()).name();
    }

    /**
     * @return the resolved market → weight map of the <b>current</b> step — the map the
     *         price-influence application must use (unmodifiable). After the sequence
     *         end this is the last step's map (relevant for permanent sequences whose
     *         influence persists until the bake)
     */
    public Map<ItemID, Float> currentStepWeights() {
        return Collections.unmodifiableMap(stepMarketWeights.get(currentStepIndex()));
    }

    /**
     * @return the resolved market → weight map of the <b>last</b> step — the markets a
     *         {@code permanent} sequence bakes into at its end (unmodifiable; already
     *         baked/dropped markets are removed by {@link #markBaked}/{@link #dropMarket})
     */
    public Map<ItemID, Float> getPendingBakeWeights() {
        return Collections.unmodifiableMap(stepMarketWeights.get(stepMarketWeights.size() - 1));
    }

    /**
     * @param stepIndex the step index (clamped to valid indices)
     * @return the resolved market → weight map of that step (unmodifiable)
     */
    public Map<ItemID, Float> getStepWeights(int stepIndex) {
        if (stepIndex < 0) stepIndex = 0;
        if (stepIndex >= stepMarketWeights.size()) stepIndex = stepMarketWeights.size() - 1;
        return Collections.unmodifiableMap(stepMarketWeights.get(stepIndex));
    }

    /**
     * @return milliseconds of sequence lifetime left, including a still-pending impact
     *         start; 0 once the sequence is over (or permanent). Display value for the
     *         admin GUI runtime stream.
     */
    public long remainingMillis() {
        return Math.max(0, sequence.totalDurationMs() - activeMillis());
    }

    // ── Bake bookkeeping ─────────────────────────────────────────────────

    /**
     * Moves a market into the baked map after the plugin has baked the permanent shift
     * into the market's default price: the market is removed from the union map and from
     * <b>every</b> step map (so neither the influence application nor a second bake pass
     * can ever see it again). Idempotent: a market that is not (or no longer) referenced
     * is ignored, which is the double-bake guard (see class Javadoc). The stored baked
     * weight is the last step's weight (the one that was baked) when available, else the
     * union weight.
     *
     * @param marketID the market that was just baked
     */
    public void markBaked(ItemID marketID) {
        Float finalStepWeight = stepMarketWeights.get(stepMarketWeights.size() - 1).get(marketID);
        Float unionWeight = marketWeights.remove(marketID);
        for (Map<ItemID, Float> stepMap : stepMarketWeights) {
            stepMap.remove(marketID);
        }
        Float stored = finalStepWeight != null ? finalStepWeight : unionWeight;
        if (stored != null) {
            bakedMarkets.put(marketID, stored);
        }
    }

    /**
     * Removes a market from this event entirely (unsubscribe mid-event, plan §6.7):
     * union map, all step maps and the baked bookkeeping.
     *
     * @param marketID the market to drop
     */
    public void dropMarket(ItemID marketID) {
        marketWeights.remove(marketID);
        for (Map<ItemID, Float> stepMap : stepMarketWeights) {
            stepMap.remove(marketID);
        }
        bakedMarkets.remove(marketID);
    }

    /** @return true if this event influences or references no market anymore */
    public boolean hasNoMarkets() {
        return marketWeights.isEmpty() && bakedMarkets.isEmpty();
    }

    // ── Accessors ────────────────────────────────────────────────────────

    /** @return the id of the definition this event was activated from */
    public String getDefinitionId() { return definitionId; }

    /** @return the monotonic news uid assigned at activation */
    public long getNewsUid() { return newsUid; }

    /** @return the headline translation map snapshot (unmodifiable, insertion-ordered) */
    public Map<String, String> getHeadline() { return Collections.unmodifiableMap(headline); }

    /** @return the newspaper-text translation map snapshot (unmodifiable, insertion-ordered) */
    public Map<String, String> getText() { return Collections.unmodifiableMap(text); }

    /**
     * @return the legacy impact-envelope descriptor snapshot for events activated from
     *         an {@code impact} block (record fields + legacy phase names), or null for
     *         sequence-authored events. <b>Not</b> a math source anymore — the
     *         {@link #getSequence() sequence} drives all influence values
     */
    public @Nullable NewsImpactEnvelope getLegacyEnvelope() { return legacyEnvelope; }

    /** @return the name of the sequence picked at activation ({@value #LEGACY_SEQUENCE_NAME} for legacy events) */
    public String getSequenceName() { return sequenceName; }

    /** @return the fully resolved influence sequence (durations rolled at activation, immutable) */
    public NewsSequence getSequence() { return sequence; }

    /** @return the announce delay sampled at activation (impact start relative to publish) */
    public long getSampledDelayMs() { return sampledDelayMs; }

    /** @return the accumulated ticking milliseconds since activation */
    public long getAgeMillis() { return ageMs; }

    /** @return the age at which the impact starts */
    public long getImpactStartAgeMillis() { return impactStartAgeMs; }

    /** @return the age at which the headline publishes */
    public long getPublishAgeMillis() { return publishAgeMs; }

    /** @return whether the NewsRecord of this event was already published */
    public boolean isPublished() { return published; }

    /**
     * @return the <b>union</b> of all step maps: every market this event still references
     *         → weight factor (unmodifiable). Drives the activity caps, record building
     *         and unsubscribe handling; the influence application uses
     *         {@link #currentStepWeights()} instead
     */
    public Map<ItemID, Float> getMarketWeights() { return Collections.unmodifiableMap(marketWeights); }

    /** @return markets whose permanent shift was already baked (unmodifiable) */
    public Map<ItemID, Float> getBakedMarkets() { return Collections.unmodifiableMap(bakedMarkets); }

    // ── Persistence ──────────────────────────────────────────────────────

    /**
     * Writes the full event state into the tag: the common fields, the resolved step
     * list (names, <b>rolled</b> durations, target values, curves, noise, permanent
     * flag) and — only where a step map differs from the union map — the per-step
     * market weights. Legacy-impact events additionally keep their envelope descriptor
     * keys so a restart preserves the published record's impact fields. Age accumulators
     * are stored as-is because they are ticking-time based, not wall-clock based.
     *
     * @param tag the tag to populate
     * @return true (this save cannot fail)
     */
    @Override
    public boolean save(CompoundTag tag) {
        tag.putString(KEY_DEFINITION_ID, definitionId);
        tag.putLong(KEY_NEWS_UID, newsUid);
        tag.put(KEY_HEADLINE, saveTextMap(headline));
        tag.put(KEY_TEXT, saveTextMap(text));
        tag.putLong(KEY_SAMPLED_DELAY, sampledDelayMs);
        tag.putLong(KEY_AGE, ageMs);
        tag.putBoolean(KEY_PUBLISHED, published);
        tag.put(KEY_MARKET_WEIGHTS, saveWeightMap(marketWeights));
        tag.put(KEY_BAKED_MARKETS, saveWeightMap(bakedMarkets));

        // Legacy envelope descriptor — only for impact-authored events. Stored in whole
        // seconds (lossless: the parser only ever produces whole-second values).
        if (legacyEnvelope != null) {
            tag.putString(KEY_IMPACT_TYPE, legacyEnvelope.getType().jsonName());
            tag.putDouble(KEY_PEAK_FACTOR, legacyEnvelope.getPeakFactor());
            tag.putLong(KEY_RAMP_UP_SECONDS, legacyEnvelope.getRampUpMillis() / 1000L);
            tag.putLong(KEY_DURATION_SECONDS, legacyEnvelope.getHoldMillis() / 1000L);
            tag.putString(KEY_REVERSAL, legacyEnvelope.getReversal().jsonName());
            tag.putLong(KEY_REVERSAL_SECONDS, legacyEnvelope.getReversalMillis() / 1000L);
            tag.putDouble(KEY_NOISE, legacyEnvelope.getNoise());
        }

        // Resolved sequence snapshot (T-095). startValue is NOT stored — NewsSequence
        // recomputes the start chain from the target values on load.
        tag.putString(KEY_SEQUENCE_NAME, sequenceName);
        ListTag stepsTag = new ListTag();
        List<NewsSequence.Step> steps = sequence.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            NewsSequence.Step step = steps.get(i);
            CompoundTag stepTag = new CompoundTag();
            stepTag.putString(KEY_STEP_NAME, step.name());
            stepTag.putLong(KEY_STEP_DURATION_MS, step.durationMs());
            stepTag.putDouble(KEY_STEP_TARGET, step.targetValue());
            stepTag.putString(KEY_STEP_CURVE, step.curve().jsonName());
            stepTag.putDouble(KEY_STEP_NOISE, step.noise());
            stepTag.putBoolean(KEY_STEP_PERMANENT, step.permanent());
            // Per-step markets are only written when they differ from the union —
            // legacy events (all maps identical) stay as lean as before.
            Map<ItemID, Float> stepMap = stepMarketWeights.get(i);
            if (!stepMap.equals(marketWeights)) {
                stepTag.put(KEY_STEP_MARKETS, saveWeightMap(stepMap));
            }
            stepsTag.add(stepTag);
        }
        tag.put(KEY_STEPS, stepsTag);
        return true;
    }

    /**
     * Restores the full event state. Contains-guards per project convention:
     * <ul>
     *   <li><b>New format</b> ({@code steps} present): the sequence is rebuilt from the
     *       persisted resolved steps — the rolled durations are read back verbatim, a
     *       restart never re-rolls. Steps without a {@code markets} sub-list inherit the
     *       union map (that is exactly when the save omitted it).</li>
     *   <li><b>Legacy format</b> ({@code steps} absent, pre-T-095 save): the envelope is
     *       rebuilt from the classic keys and normalized via
     *       {@link NewsSequence#fromLegacyEnvelope} — a mid-event restart across the
     *       upgrade resumes at the same age with the same sampled announce delay.</li>
     * </ul>
     * Either way the event keeps running even if its definition vanished from the
     * library after a reload (self-contained snapshot doctrine).
     *
     * @param tag the tag to read from
     * @return false if mandatory fields are missing (event should be discarded)
     */
    @Override
    public boolean load(CompoundTag tag) {
        boolean hasSteps = tag.contains(KEY_STEPS);
        if (!tag.contains(KEY_DEFINITION_ID) || (!hasSteps && !tag.contains(KEY_IMPACT_TYPE))) {
            return false;
        }
        definitionId = tag.getString(KEY_DEFINITION_ID);
        newsUid = tag.getLong(KEY_NEWS_UID);
        headline = loadTextMap(tag.getList(KEY_HEADLINE, Tag.TAG_COMPOUND));
        text = loadTextMap(tag.getList(KEY_TEXT, Tag.TAG_COMPOUND));
        sampledDelayMs = tag.getLong(KEY_SAMPLED_DELAY);
        ageMs = tag.getLong(KEY_AGE);
        impactStartAgeMs = Math.max(sampledDelayMs, 0);
        publishAgeMs = Math.max(-sampledDelayMs, 0);
        published = tag.getBoolean(KEY_PUBLISHED);
        marketWeights = loadWeightMap(tag.getList(KEY_MARKET_WEIGHTS, Tag.TAG_COMPOUND));
        bakedMarkets = loadWeightMap(tag.getList(KEY_BAKED_MARKETS, Tag.TAG_COMPOUND));

        // Legacy envelope descriptor (present for legacy-impact events, both formats).
        legacyEnvelope = null;
        if (tag.contains(KEY_IMPACT_TYPE)) {
            NewsImpactEnvelope.ImpactType type =
                    NewsImpactEnvelope.ImpactType.fromString(tag.getString(KEY_IMPACT_TYPE));
            NewsImpactEnvelope.ReversalMode reversal =
                    NewsImpactEnvelope.ReversalMode.fromString(tag.getString(KEY_REVERSAL));
            legacyEnvelope = new NewsImpactEnvelope(type, tag.getDouble(KEY_PEAK_FACTOR),
                    tag.getLong(KEY_RAMP_UP_SECONDS), tag.getLong(KEY_DURATION_SECONDS),
                    reversal, tag.getLong(KEY_REVERSAL_SECONDS), tag.getDouble(KEY_NOISE));
        }

        stepMarketWeights = new ArrayList<>();
        if (hasSteps) {
            // New format: resolved steps with their ROLLED durations (never re-rolled).
            sequenceName = tag.contains(KEY_SEQUENCE_NAME)
                    ? tag.getString(KEY_SEQUENCE_NAME) : LEGACY_SEQUENCE_NAME;
            ListTag stepsTag = tag.getList(KEY_STEPS, Tag.TAG_COMPOUND);
            List<NewsSequence.StepSpec> specs = new ArrayList<>(stepsTag.size());
            List<@Nullable Map<ItemID, Float>> stepMaps = new ArrayList<>(stepsTag.size());
            for (int i = 0; i < stepsTag.size(); i++) {
                CompoundTag stepTag = stepsTag.getCompound(i);
                NewsSequence.Curve curve = NewsSequence.Curve.fromString(stepTag.getString(KEY_STEP_CURVE));
                specs.add(new NewsSequence.StepSpec(
                        stepTag.getString(KEY_STEP_NAME),
                        stepTag.getLong(KEY_STEP_DURATION_MS),
                        stepTag.getDouble(KEY_STEP_TARGET),
                        curve != null ? curve : NewsSequence.Curve.LINEAR,
                        stepTag.getDouble(KEY_STEP_NOISE),
                        stepTag.getBoolean(KEY_STEP_PERMANENT)));
                // Absent per-step markets = the step map equaled the union at save time.
                stepMaps.add(stepTag.contains(KEY_STEP_MARKETS)
                        ? loadWeightMap(stepTag.getList(KEY_STEP_MARKETS, Tag.TAG_COMPOUND))
                        : null);
            }
            sequence = NewsSequence.create(specs);
            for (int i = 0; i < sequence.stepCount(); i++) {
                Map<ItemID, Float> stepMap = (i < stepMaps.size() && stepMaps.get(i) != null)
                        ? stepMaps.get(i) : new LinkedHashMap<>(marketWeights);
                stepMarketWeights.add(stepMap);
            }
        } else {
            // Legacy format (pre-T-095): normalize the envelope into the implicit
            // sequence; every step inherits the single persisted market map.
            sequenceName = LEGACY_SEQUENCE_NAME;
            sequence = NewsSequence.fromLegacyEnvelope(legacyEnvelope);
            for (int i = 0; i < sequence.stepCount(); i++) {
                stepMarketWeights.add(new LinkedHashMap<>(marketWeights));
            }
        }
        return true;
    }

    /**
     * Convenience factory mirroring {@code ItemID.createFromTag}.
     *
     * @param tag the tag to read from
     * @return the loaded event, or null if the tag is malformed
     */
    public static @Nullable ActiveNewsEvent createFromTag(CompoundTag tag) {
        ActiveNewsEvent event = new ActiveNewsEvent();
        return event.load(tag) ? event : null;
    }

    /** Serializes a translation map into an order-preserving ListTag of {lang, value} pairs. */
    private static ListTag saveTextMap(Map<String, String> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.putString(KEY_LANG, entry.getKey());
            pair.putString(KEY_VALUE, entry.getValue());
            list.add(pair);
        }
        return list;
    }

    /** Restores a translation map from the order-preserving ListTag form. */
    private static Map<String, String> loadTextMap(ListTag list) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pair = list.getCompound(i);
            if (pair.contains(KEY_LANG) && pair.contains(KEY_VALUE)) {
                map.put(pair.getString(KEY_LANG), pair.getString(KEY_VALUE));
            }
        }
        return map;
    }

    /** Serializes a market → weight map into a ListTag of {ItemID..., weight} compounds. */
    private static ListTag saveWeightMap(Map<ItemID, Float> map) {
        ListTag list = new ListTag();
        for (Map.Entry<ItemID, Float> entry : map.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entry.getKey().save(entryTag);
            entryTag.putFloat(KEY_WEIGHT, entry.getValue());
            list.add(entryTag);
        }
        return list;
    }

    /**
     * Restores a market → weight map. Entries are intentionally NOT filtered through
     * {@code ItemID.isValid()} here: the short id round-trips on its own, and validity
     * needs a populated item registry which may not exist in test contexts. Truly
     * dangling markets are cleaned up naturally when the plugin drops unsubscribed
     * markets from active events.
     */
    private static Map<ItemID, Float> loadWeightMap(ListTag list) {
        Map<ItemID, Float> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ItemID marketID = ItemID.createFromTag(entryTag);
            if (marketID != null && marketID.getShort() != 0 && entryTag.contains(KEY_WEIGHT)) {
                map.put(marketID, entryTag.getFloat(KEY_WEIGHT));
            }
        }
        return map;
    }

    @Override
    public String toString() {
        return "ActiveNewsEvent{id='" + definitionId + "', uid=" + newsUid
                + ", sequence='" + sequenceName + "' (" + sequence.stepCount() + " steps)"
                + ", ageMs=" + ageMs + ", delayMs=" + sampledDelayMs
                + ", published=" + published
                + ", markets=" + marketWeights.size()
                + ", baked=" + bakedMarkets.size() + "}";
    }
}
