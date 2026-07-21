package net.kroia.stockmarket.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parsed, immutable representation of one news event from a
 * {@code config/StockMarket/news/*.json} file (schema: NewsEventSystem plan §1).
 * <p>
 * Instances are produced exclusively by {@link #parse(JsonObject, String, ValidationReport)}
 * during a {@link NewsEventLibrary#reload()} pass. Parsing follows the skip-and-continue
 * philosophy: every problem is recorded in the {@link ValidationReport}; an event with at
 * least one ERROR is skipped ({@code parse} returns null), warnings keep the event loaded.
 * <p>
 * <b>Portability rule:</b> market references are stored as portable matchers (registry name,
 * item tag, glob, optional component object) — the short {@link ItemID} value is <b>never</b>
 * persisted in JSON because it is a per-world running number and not portable across servers.
 * Matchers are resolved to live ItemIDs lazily via {@link #resolveMarkets(Collection)},
 * which must only be called after BankSystem setup is complete (ItemID names are numeric
 * placeholders before the registry is populated, plan §6.11).
 */
public final class NewsEventDefinition {

    // ── JSON schema keys (used for unknown-field detection) ─────────────

    private static final Set<String> KNOWN_EVENT_KEYS = Set.of(
            "id", "headline", "text", "category", "weight", "cooldownSeconds",
            "adminOnly", "announceDelayMs", "impact", "sequences", "markets", "picture",
            "requires", "records", "chains");
    private static final Set<String> KNOWN_IMPACT_KEYS = Set.of(
            "type", "peakFactor", "rampUpSeconds", "durationSeconds",
            "reversal", "reversalSeconds", "noise");
    private static final Set<String> KNOWN_SEQUENCE_KEYS = Set.of(
            "name", "weight", "steps");
    private static final Set<String> KNOWN_STEP_KEYS = Set.of(
            "name", "durationSeconds", "targetFactor", "curve", "noise",
            "markets", "permanent");
    private static final Set<String> KNOWN_MARKET_KEYS = Set.of(
            "item", "components", "weightFactor");
    private static final Set<String> KNOWN_DELAY_KEYS = Set.of("min", "max");
    private static final Set<String> KNOWN_CHAIN_KEYS = Set.of(
            "eventId", "on", "step", "chance", "delaySeconds", "sameMarkets");

    /** Category used when the JSON omits the optional {@code category} field. */
    public static final String DEFAULT_CATEGORY = "general";
    /** Weight used when the JSON omits the optional {@code weight} field. */
    public static final float DEFAULT_WEIGHT = 1.0f;
    /**
     * Sanity bound for the serialized size of {@code headline} + {@code text} combined.
     * Events above this emit a WARNING (a single record must stay far below the ~1 MiB
     * S2C custom-payload cap, and history responses carry many records).
     */
    public static final int TEXT_SIZE_WARN_BYTES = 32 * 1024;

    /**
     * The {@code announceDelayMs} range in milliseconds (plan §1): on each activation the
     * plugin samples one uniform random delay from {@code [minMs, maxMs]}. The delay is the
     * impact start relative to the headline publish — positive = headline first, price moves
     * later; negative = the impact already started before the news goes public; {@code {0,0}}
     * = simultaneous. Negative values are explicitly allowed; {@code minMs <= maxMs} is
     * enforced at parse time.
     *
     * @param minMs inclusive lower bound of the delay in milliseconds
     * @param maxMs inclusive upper bound of the delay in milliseconds
     */
    public record AnnounceDelayRange(long minMs, long maxMs) {
        /** The default range: publish and impact happen simultaneously. */
        public static final AnnounceDelayRange ZERO = new AnnounceDelayRange(0, 0);
    }

    /**
     * One portable market matcher from the event's {@code markets[]} array.
     * <p>
     * Supported forms (plan §1):
     * <ul>
     *   <li><b>Exact</b>: {@code "minecraft:diamond"} — matched against the registry name
     *       of each existing market ({@link ItemID#getName()}).</li>
     *   <li><b>Tag</b>: {@code "#c:ores"} — matched via the item tag of the market's
     *       template stack.</li>
     *   <li><b>Glob</b>: {@code "minecraft:*_ore"} — {@code *} wildcard on the registry name.</li>
     *   <li><b>Component</b>: exact registry id plus a {@code "components"} object for
     *       component markets (enchanted books, potions, ...), reusing the
     *       {@link MarketPreset} item-stack pattern.</li>
     * </ul>
     * The per-entry {@code weightFactor} scales the event's impact for the matched markets;
     * a <b>negative</b> value inverts the impact direction (plan §1). When several matcher
     * entries match the same market, the <b>first matching entry wins</b> — list specific
     * entries (exact ids) before broad ones (tags/globs).
     */
    public static final class MarketMatcher {

        /** The syntactic kind of a matcher, derived from its {@code item} string. */
        public enum Kind {
            /** Exact registry id match ({@code "minecraft:diamond"}). */
            EXACT,
            /** Item tag match ({@code "#c:ores"}). */
            TAG,
            /** Glob wildcard match on the registry name ({@code "minecraft:*_ore"}). */
            GLOB,
            /** Exact registry id + data components (component markets). */
            COMPONENT
        }

        private final Kind kind;
        /** Normalized pattern: full registry name, tag id (without '#'), or the raw glob. */
        private final String pattern;
        private final @Nullable JsonObject components;
        private final float weightFactor;
        private final @Nullable Pattern globRegex;
        private final @Nullable TagKey<Item> tagKey;

        private MarketMatcher(Kind kind, String pattern, @Nullable JsonObject components,
                              float weightFactor, @Nullable Pattern globRegex,
                              @Nullable TagKey<Item> tagKey) {
            this.kind = kind;
            this.pattern = pattern;
            this.components = components;
            this.weightFactor = weightFactor;
            this.globRegex = globRegex;
            this.tagKey = tagKey;
        }

        /** @return the syntactic kind of this matcher */
        public Kind getKind() { return kind; }

        /** @return the normalized pattern (registry name, tag id without '#', or glob) */
        public String getPattern() { return pattern; }

        /** @return the serialized component patch for {@link Kind#COMPONENT} matchers, else null */
        public @Nullable JsonObject getComponents() { return components; }

        /**
         * @return the impact scale for markets matched by this entry;
         *         negative values invert the impact direction
         */
        public float getWeightFactor() { return weightFactor; }

        /**
         * Core matching predicate against a market's registry name and template stack.
         * Pure for {@link Kind#EXACT} and {@link Kind#GLOB} (string matching only);
         * {@link Kind#TAG} additionally consults the stack's tag holders.
         * <p>
         * {@link Kind#COMPONENT} matchers always return false here — they need the
         * BankSystem {@link ItemIDManager} and are resolved separately in
         * {@link NewsEventDefinition#resolveMarkets(Collection)}.
         *
         * @param registryName the market item's registry name (e.g. {@code "minecraft:diamond"})
         * @param template     the market item's template stack (may be empty; only used for tags)
         * @return true if this matcher matches the market
         */
        public boolean matches(String registryName, ItemStack template) {
            switch (kind) {
                case EXACT:
                    return pattern.equals(registryName);
                case GLOB:
                    return globRegex != null && globRegex.matcher(registryName).matches();
                case TAG:
                    return tagKey != null && template != null && !template.isEmpty()
                            && template.is(tagKey);
                case COMPONENT:
                default:
                    return false; // resolved via ItemIDManager in resolveMarkets()
            }
        }

        /**
         * Resolves the target {@link ItemID} of a {@link Kind#COMPONENT} matcher via the
         * {@link MarketPreset} item-stack pattern. Must only be called after BankSystem
         * setup is complete (plan §6.11); never throws.
         *
         * @return the registered ItemID of the component item, or null if the item cannot
         *         be built or is not registered as a market item
         */
        public @Nullable ItemID resolveComponentTarget() {
            if (kind != Kind.COMPONENT) return null;
            try {
                JsonObject stackJson = new JsonObject();
                stackJson.addProperty("id", pattern);
                stackJson.add("components", components);
                ItemStack stack = MarketPreset.deserializeItemStack(stackJson);
                if (stack.isEmpty()) return null;
                ItemID id = ItemIDManager.getItemID(stack);
                return (id != null && id.isValid()) ? id : null;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Compiles a glob pattern ({@code *} = any sequence) into an anchored regex.
         * All other characters are matched literally.
         */
        private static Pattern compileGlob(String glob) {
            StringBuilder regex = new StringBuilder();
            StringBuilder literal = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (literal.length() > 0) {
                        regex.append(Pattern.quote(literal.toString()));
                        literal.setLength(0);
                    }
                    regex.append(".*");
                } else {
                    literal.append(c);
                }
            }
            if (literal.length() > 0) {
                regex.append(Pattern.quote(literal.toString()));
            }
            return Pattern.compile(regex.toString());
        }

        /**
         * Parses one {@code markets[]} entry. Problems are recorded in the report;
         * a syntactically unusable entry yields null (WARNING — the event stays loaded,
         * plan §1 "unresolvable items are warnings").
         *
         * @param json    the market entry object
         * @param file    source file name for report entries
         * @param eventId owning event id for report entries
         * @param report  collector for problems
         * @return the parsed matcher, or null if the entry is unusable
         */
        static @Nullable MarketMatcher parse(JsonObject json, String file,
                                             @Nullable String eventId, ValidationReport report) {
            warnUnknownKeys(json, KNOWN_MARKET_KEYS, "markets entry", file, eventId, report);

            String item = getString(json, "item");
            if (item == null || item.isBlank()) {
                report.addWarning(file, eventId, "markets entry has no 'item' string — entry ignored");
                return null;
            }
            item = item.trim();

            float weightFactor = 1.0f;
            if (json.has("weightFactor")) {
                Double wf = getFiniteNumber(json, "weightFactor");
                if (wf == null) {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': 'weightFactor' is not a finite number — using 1.0");
                } else {
                    weightFactor = wf.floatValue();
                }
            }

            JsonObject components = null;
            if (json.has("components")) {
                if (json.get("components").isJsonObject()) {
                    components = json.getAsJsonObject("components");
                } else {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': 'components' must be an object — ignored");
                }
            }

            // Tag matcher: "#namespace:path"
            if (item.startsWith("#")) {
                String tagId = item.substring(1);
                ResourceLocation loc = ResourceLocation.tryParse(tagId);
                if (loc == null) {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': invalid tag id — entry ignored");
                    return null;
                }
                if (components != null) {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': 'components' cannot be combined with a tag matcher — components ignored");
                }
                return new MarketMatcher(Kind.TAG, loc.toString(), null, weightFactor,
                        null, TagKey.create(Registries.ITEM, loc));
            }

            // Glob matcher: contains '*'
            if (item.indexOf('*') >= 0) {
                // Registry names always carry a namespace; default to "minecraft:" like
                // ResourceLocation does so "*_ore" behaves consistently with exact ids.
                String glob = item.indexOf(':') < 0 ? "minecraft:" + item : item;
                if (components != null) {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': 'components' cannot be combined with a glob matcher — components ignored");
                }
                return new MarketMatcher(Kind.GLOB, glob, null, weightFactor,
                        compileGlob(glob), null);
            }

            // Exact / component matcher
            ResourceLocation loc = ResourceLocation.tryParse(item);
            if (loc == null) {
                report.addWarning(file, eventId,
                        "markets entry '" + item + "': invalid registry id — entry ignored");
                return null;
            }
            // Existence check is best effort: unresolvable items are warnings, never errors
            // (the item may come from an optional mod that is absent on this server).
            try {
                if (!BuiltInRegistries.ITEM.containsKey(loc)) {
                    report.addWarning(file, eventId,
                            "markets entry '" + item + "': item is not in the registry (mod absent?) — matcher will never match on this server");
                }
            } catch (Exception ignored) {
                // Registry not bootstrapped (should not happen in-game) — skip the check.
            }
            if (components != null && components.size() > 0) {
                return new MarketMatcher(Kind.COMPONENT, loc.toString(), components,
                        weightFactor, null, null);
            }
            return new MarketMatcher(Kind.EXACT, loc.toString(), null, weightFactor, null, null);
        }
    }

    /**
     * One <b>unresolved</b> step of a {@link SequenceDefinition}, parsed from the JSON
     * {@code sequences[].steps[]} array (sequences plan §1.1). "Unresolved" means the
     * duration may still be a {@code {min, max}} range — the plugin rolls the concrete
     * duration once at activation time (T-095) and then builds the pure-math
     * {@link NewsSequence} via {@link SequenceDefinition#createSequence(long[])}.
     * <p>
     * The {@code targetFactor} is already resolved at parse time: for
     * {@link NewsSequence.Curve#HOLD} steps it is the previous step's target (step 0
     * holds 0) — targets are static, only durations roll.
     */
    public static final class StepDefinition {

        private final String name;
        private final long durationMinMs;
        private final long durationMaxMs;
        private final double targetFactor;
        private final NewsSequence.Curve curve;
        private final double noise;
        private final @Nullable List<MarketMatcher> markets;
        private final boolean permanent;

        private StepDefinition(String name, long durationMinMs, long durationMaxMs,
                               double targetFactor, NewsSequence.Curve curve, double noise,
                               @Nullable List<MarketMatcher> markets, boolean permanent) {
            this.name = name;
            this.durationMinMs = durationMinMs;
            this.durationMaxMs = durationMaxMs;
            this.targetFactor = targetFactor;
            this.curve = curve;
            this.noise = noise;
            this.markets = markets != null ? Collections.unmodifiableList(markets) : null;
            this.permanent = permanent;
        }

        /** @return the step name (unique within its sequence; UI display, chains' {@code onStep}) */
        public String getName() { return name; }

        /** @return the inclusive lower duration bound in milliseconds (== max for fixed durations) */
        public long getDurationMinMs() { return durationMinMs; }

        /** @return the inclusive upper duration bound in milliseconds (== min for fixed durations) */
        public long getDurationMaxMs() { return durationMaxMs; }

        /**
         * @return the signed influence level this step reaches at its end
         *         ({@code 0.3} = +30%; always finite and {@code > -1}). Already resolved
         *         for {@link NewsSequence.Curve#HOLD} steps (= previous step's target)
         */
        public double getTargetFactor() { return targetFactor; }

        /** @return the interpolation curve (never null, default {@link NewsSequence.Curve#LINEAR}) */
        public NewsSequence.Curve getCurve() { return curve; }

        /** @return the per-step jitter amplitude (0 = none) */
        public double getNoise() { return noise; }

        /**
         * @return the optional per-step market matchers (same grammar as the event-level
         *         {@code markets[]}, resolved lazily via {@link #resolveMatchers}), or
         *         null when absent — the step then inherits the event-level markets
         */
        public @Nullable List<MarketMatcher> getMarkets() { return markets; }

        /**
         * @return true if this (always last) step bakes its final value into the
         *         market's default price at sequence end instead of snapping off
         */
        public boolean isPermanent() { return permanent; }
    }

    /**
     * One <b>unresolved</b> sequence of an event, parsed from the JSON {@code sequences[]}
     * array (sequences plan §1.1) — or normalized from a legacy {@code impact} block via
     * {@link #fromLegacyImpact(NewsImpactEnvelope)} so that every loaded event exposes
     * its behavior uniformly as sequence definitions (single runtime code path).
     * <p>
     * At activation the plugin picks ONE sequence of the event by {@link #getWeight()},
     * rolls each step's concrete duration uniformly from its {@code [min, max]} range
     * and freezes everything into the active event (T-095). This class itself never
     * rolls — {@link #createSequence(long[])} takes the already-rolled durations.
     */
    public static final class SequenceDefinition {

        /** Weight used when the JSON omits the optional per-sequence {@code weight}. */
        public static final float DEFAULT_SEQUENCE_WEIGHT = 1.0f;

        private final String name;
        private final float weight;
        private final List<StepDefinition> steps;

        private SequenceDefinition(String name, float weight, List<StepDefinition> steps) {
            this.name = name;
            this.weight = weight;
            this.steps = Collections.unmodifiableList(steps);
        }

        /** @return the sequence name (shown in the UI, persisted into the active event) */
        public String getName() { return name; }

        /** @return the relative weight for the activation-time weighted pick ({@code > 0}) */
        public float getWeight() { return weight; }

        /** @return the unresolved steps in order (unmodifiable, never empty) */
        public List<StepDefinition> getSteps() { return steps; }

        /** @return the number of steps (always ≥ 1) */
        public int getStepCount() { return steps.size(); }

        /**
         * Builds the resolved pure-math {@link NewsSequence} from this definition and
         * the concrete step durations rolled by the plugin at activation time (T-095).
         *
         * @param rolledDurationsMs one concrete duration per step, in step order
         *                          (each normally rolled uniformly from
         *                          {@code [getDurationMinMs(), getDurationMaxMs()]})
         * @return the resolved sequence
         * @throws IllegalArgumentException if the array is null or its length does not
         *                                  match {@link #getStepCount()} (API misuse)
         */
        public NewsSequence createSequence(long[] rolledDurationsMs) {
            if (rolledDurationsMs == null || rolledDurationsMs.length != steps.size()) {
                throw new IllegalArgumentException("rolledDurationsMs must contain exactly "
                        + steps.size() + " duration(s) for sequence '" + name + "', got "
                        + (rolledDurationsMs == null ? "null" : rolledDurationsMs.length));
            }
            List<NewsSequence.StepSpec> specs = new ArrayList<>(steps.size());
            for (int i = 0; i < steps.size(); i++) {
                StepDefinition step = steps.get(i);
                specs.add(new NewsSequence.StepSpec(step.getName(), rolledDurationsMs[i],
                        step.getTargetFactor(), step.getCurve(), step.getNoise(),
                        step.isPermanent()));
            }
            return NewsSequence.create(specs);
        }

        /**
         * Normalizes a legacy {@code impact} envelope into the ONE implicit sequence
         * definition every legacy event exposes (sequences plan §1.1 legacy compat).
         * <p>
         * The step derivation is delegated to
         * {@link NewsSequence#fromLegacyEnvelope(NewsImpactEnvelope)} (single source of
         * truth for the ramp → hold → reversal mapping and its equivalence contract);
         * the resolved steps are mirrored back into fixed-duration ({@code min == max})
         * step definitions with no per-step markets (the event-level markets apply).
         * The sequence is named {@code "impact"} and has weight 1.
         *
         * @param impact the parsed legacy impact envelope (never null)
         * @return the equivalent implicit sequence definition
         */
        public static SequenceDefinition fromLegacyImpact(NewsImpactEnvelope impact) {
            NewsSequence resolved = NewsSequence.fromLegacyEnvelope(impact);
            List<StepDefinition> steps = new ArrayList<>(resolved.stepCount());
            for (NewsSequence.Step step : resolved.getSteps()) {
                steps.add(new StepDefinition(step.name(), step.durationMs(), step.durationMs(),
                        step.targetValue(), step.curve(), step.noise(), null, step.permanent()));
            }
            return new SequenceDefinition("impact", DEFAULT_SEQUENCE_WEIGHT, steps);
        }
    }

    // ── Chain definition (plan §4) ──────────────────────────────────────

    /**
     * The trigger moment at which a chain entry evaluates its chance roll and
     * enqueues a pending activation — one of the three lifecycle hooks of an
     * active news event.
     */
    public enum ChainTriggerMoment {
        /** Fires when the source event's NewsRecord is published. */
        PUBLISH("publish"),
        /** Fires when a named step <b>starts</b> (incl. steps fast-forwarded past by skipPhase). */
        STEP("step"),
        /** Fires when the source event retires normally (natural sequence completion; NOT on admin stop). */
        COMPLETION("completion");

        private final String jsonName;
        ChainTriggerMoment(String jsonName) { this.jsonName = jsonName; }
        /** @return the JSON key value for this trigger moment */
        public String jsonName() { return jsonName; }

        /**
         * @param name the JSON string
         * @return the matching trigger moment, or null if unknown
         */
        public static @Nullable ChainTriggerMoment fromString(@Nullable String name) {
            if (name == null) return null;
            for (ChainTriggerMoment m : values()) {
                if (m.jsonName.equalsIgnoreCase(name.trim())) return m;
            }
            return null;
        }
    }

    /**
     * One entry of the optional {@code "chains": [...]} array of an event definition
     * (sequences plan §4): when the trigger moment fires, the chance is rolled; on
     * success a pending activation with the given delay is enqueued in the NewsPlugin.
     * <p>
     * Chains are parsed per-event but <b>validated post-merge</b> in
     * {@link NewsEventLibrary#reload}: a target id that doesn't exist in the merged pool
     * is a WARNING (chain dropped, event kept); a target that is {@code adminOnly} is an
     * ERROR (chain dropped, event kept). A self-reference with chance 1.0 and no
     * {@code notFired} requirement is a WARNING "possible loop, bounded by depth".
     *
     * @param targetEventId the event id to activate when the chain fires
     * @param on            when the chance is rolled (required)
     * @param stepName      the step name for {@link ChainTriggerMoment#STEP} chains (null for others)
     * @param chance        the probability of the chain firing, clamped to [0, 1] (default 1.0)
     * @param delayMinMs    inclusive lower bound of the activation delay in milliseconds
     * @param delayMaxMs    inclusive upper bound of the activation delay in milliseconds
     * @param sameMarkets   when true, the chain-fired event inherits the source event's
     *                      resolved market subset instead of resolving its own matchers
     *                      (default false)
     */
    public record ChainDefinition(String targetEventId, ChainTriggerMoment on,
                                   @Nullable String stepName, double chance,
                                   long delayMinMs, long delayMaxMs, boolean sameMarkets) {
    }

    // ── Fields ───────────────────────────────────────────────────────────

    private final String id;
    private final Map<String, String> headline;
    private final Map<String, String> text;
    private final String category;
    private final float weight;
    private final long cooldownSeconds;
    private final boolean adminOnly;
    private final AnnounceDelayRange announceDelayMs;
    private final @Nullable NewsImpactEnvelope impact;
    private final List<SequenceDefinition> sequences;
    private final List<MarketMatcher> markets;
    private final @Nullable String pictureFileName;
    private final List<NewsRequirement> requirements;
    private final Map<String, String> records;
    private final List<ChainDefinition> chains;

    private NewsEventDefinition(String id, Map<String, String> headline, Map<String, String> text,
                                String category, float weight, long cooldownSeconds,
                                boolean adminOnly, AnnounceDelayRange announceDelayMs,
                                @Nullable NewsImpactEnvelope impact,
                                List<SequenceDefinition> sequences, List<MarketMatcher> markets,
                                @Nullable String pictureFileName,
                                List<NewsRequirement> requirements, Map<String, String> records,
                                List<ChainDefinition> chains) {
        this.id = id;
        this.headline = Collections.unmodifiableMap(headline);
        this.text = Collections.unmodifiableMap(text);
        this.category = category;
        this.weight = weight;
        this.cooldownSeconds = cooldownSeconds;
        this.adminOnly = adminOnly;
        this.announceDelayMs = announceDelayMs;
        this.impact = impact;
        this.sequences = Collections.unmodifiableList(sequences);
        this.markets = Collections.unmodifiableList(markets);
        this.pictureFileName = pictureFileName;
        this.requirements = Collections.unmodifiableList(requirements);
        this.records = Collections.unmodifiableMap(records);
        this.chains = Collections.unmodifiableList(chains);
    }

    // ── Accessors ────────────────────────────────────────────────────────

    /** @return the unique event id (cooldowns, manual trigger, persistence, history dedup) */
    public String getId() { return id; }

    /**
     * @return the headline as an insertion-ordered, unmodifiable language-code → text map.
     *         A plain JSON string was normalized to a single {@code "en_us"} entry;
     *         clients resolve exact language → {@code en_us} → first entry at render time
     */
    public Map<String, String> getHeadline() { return headline; }

    /**
     * @return the newspaper body text as an insertion-ordered, unmodifiable
     *         language-code → text map (same normalization as {@link #getHeadline()})
     */
    public Map<String, String> getText() { return text; }

    /** @return the event category (free-form grouping string, default {@value #DEFAULT_CATEGORY}) */
    public String getCategory() { return category; }

    /** @return the relative weight for the scheduler's weighted random pick (default 1) */
    public float getWeight() { return weight; }

    /** @return seconds this event stays on cooldown after firing (default 0) */
    public long getCooldownSeconds() { return cooldownSeconds; }

    /** @return true if this event only fires via admin command, never randomly */
    public boolean isAdminOnly() { return adminOnly; }

    /** @return the announce-delay sampling range (default {@link AnnounceDelayRange#ZERO}) */
    public AnnounceDelayRange getAnnounceDelayMs() { return announceDelayMs; }

    /**
     * @return the legacy price-impact envelope (pure math, see {@link NewsImpactEnvelope})
     *         when the event was authored with the simple {@code impact} block, or
     *         <b>null</b> for events authored with the advanced {@code sequences[]} form
     *         ({@code impact} and {@code sequences} are mutually exclusive). Runtime code
     *         should consume {@link #getSequences()} instead — legacy impacts are always
     *         also exposed as their normalized implicit sequence (sequences plan §1.1)
     */
    public @Nullable NewsImpactEnvelope getImpact() { return impact; }

    /**
     * The event's behavior as sequence definitions — the uniform runtime representation
     * (sequences plan §1.1). For {@code sequences[]}-authored events this is the parsed
     * list (1..n entries, activation picks one by weight); for legacy {@code impact}
     * events it is the ONE implicit normalized sequence
     * ({@link SequenceDefinition#fromLegacyImpact}). Never null, never empty for a
     * successfully parsed event.
     *
     * @return the unmodifiable sequence definitions in JSON order
     */
    public List<SequenceDefinition> getSequences() { return sequences; }

    /** @return the portable market matchers of this event (unmodifiable, in JSON order) */
    public List<MarketMatcher> getMarkets() { return markets; }

    /**
     * @return the bare file name of this event's picture inside
     *         {@code config/StockMarket/news/pictures/} (validated traversal-safe at
     *         parse time, see {@link NewsPictureLibrary#validatePictureFileName}),
     *         or null for a text-only event. Resolution against the
     *         {@link NewsPictureLibrary} happens at publish time — the referenced
     *         file may legitimately be missing (the event then publishes picture-less).
     */
    public @Nullable String getPictureFileName() { return pictureFileName; }

    /**
     * The event's trigger requirements from the optional {@code requires[]} array
     * (sequences plan §3): pure predicates over the {@link NewsWorldRegistry} that must
     * <b>ALL</b> hold for the event to be eligible. Consumed by the T-098 eligibility
     * filter via {@link NewsRequirement#allMet} (planning AND fire time, chains too)
     * and rendered to admins via {@link NewsRequirement#describe()} (T-099/T-100
     * unmet-requirements confirmation popup; the server-side manual TRIGGER itself
     * bypasses requirements, plan §10.1).
     *
     * @return the requirements in JSON order (unmodifiable, never null, empty when
     *         the event has no {@code requires} array)
     */
    public List<NewsRequirement> getRequirements() { return requirements; }

    /**
     * The event's registry writes from the optional {@code records{}} object
     * (sequences plan §3): custom string→string pairs applied to the
     * {@link NewsWorldRegistry} at publish time (last write wins) — the write itself
     * is wired in T-098 ({@code ServerNewsPublisher} → {@link NewsWorldRegistry#putValue}),
     * which also enforces the caps at write time; parse time only WARNs on cap breaches.
     *
     * @return the insertion-ordered pairs (unmodifiable, never null, empty when the
     *         event has no {@code records} object)
     */
    public Map<String, String> getRecords() { return records; }

    /**
     * The event's chain entries from the optional {@code chains[]} array
     * (sequences plan §4): each entry describes a conditional follow-up activation
     * triggered by a lifecycle moment of this event. Post-merge validation in
     * {@link NewsEventLibrary} checks that target ids exist and are not
     * {@code adminOnly}; runtime evaluation (chance roll, delay, depth/ancestry
     * guards) is in the NewsPlugin (T-098).
     *
     * @return the chain definitions in JSON order (unmodifiable, never null, empty
     *         when the event has no {@code chains} array)
     */
    public List<ChainDefinition> getChains() { return chains; }

    // ── Matcher resolution ───────────────────────────────────────────────

    /**
     * Resolves this event's portable matchers against the set of currently
     * existing/subscribed markets and returns the matched subset with the effective
     * per-market weight factor.
     * <p>
     * <b>Lazy by design (plan §6.11):</b> resolution must happen at scheduling/activation
     * time, after BankSystem setup is complete — never at parse time — because
     * {@link ItemID#getName()} returns a numeric placeholder before the ItemID registry
     * is populated, and the market set changes at runtime anyway.
     * <p>
     * When several matcher entries match the same market, the <b>first</b> entry in JSON
     * order wins (specific-before-broad, see {@link MarketMatcher}). This method never
     * throws; markets whose template stack cannot be resolved are skipped silently.
     *
     * @param candidateMarkets the ItemIDs of the currently existing/subscribed markets
     * @return an insertion-ordered map of matched market → effective weight factor
     *         (order follows {@code candidateMarkets} iteration order); empty if nothing matches
     */
    public Map<ItemID, Float> resolveMarkets(Collection<ItemID> candidateMarkets) {
        return resolveMatchers(markets, candidateMarkets);
    }

    /**
     * Resolves an arbitrary matcher list against a set of candidate markets — the shared
     * core of {@link #resolveMarkets(Collection)}, exposed so per-step matcher lists
     * ({@link StepDefinition#getMarkets()}) resolve with the exact same semantics
     * (first-match-wins, lazy, never throws — see {@link #resolveMarkets(Collection)}).
     *
     * @param matchers         the matcher list to resolve (event-level or per-step)
     * @param candidateMarkets the ItemIDs of the currently existing/subscribed markets
     * @return an insertion-ordered map of matched market → effective weight factor
     *         (order follows {@code candidateMarkets} iteration order); empty if nothing matches
     */
    public static Map<ItemID, Float> resolveMatchers(List<MarketMatcher> matchers,
                                                     Collection<ItemID> candidateMarkets) {
        Map<ItemID, Float> resolved = new LinkedHashMap<>();
        if (candidateMarkets == null || candidateMarkets.isEmpty()
                || matchers == null || matchers.isEmpty()) {
            return resolved;
        }

        // Component matchers resolve to a concrete ItemID once per resolution pass.
        // Index in the matcher list is preserved for first-match-wins semantics.
        List<@Nullable ItemID> componentTargets = new ArrayList<>(matchers.size());
        for (MarketMatcher matcher : matchers) {
            componentTargets.add(matcher.getKind() == MarketMatcher.Kind.COMPONENT
                    ? matcher.resolveComponentTarget() : null);
        }

        for (ItemID market : candidateMarkets) {
            if (market == null) continue;
            try {
                String registryName = market.getName();
                ItemStack template = market.getStackTemplate();
                for (int i = 0; i < matchers.size(); i++) {
                    MarketMatcher matcher = matchers.get(i);
                    boolean match;
                    if (matcher.getKind() == MarketMatcher.Kind.COMPONENT) {
                        ItemID target = componentTargets.get(i);
                        match = target != null && target.equals(market);
                    } else {
                        match = matcher.matches(registryName, template);
                    }
                    if (match) {
                        resolved.put(market, matcher.getWeightFactor());
                        break; // first matching entry wins
                    }
                }
            } catch (Exception ignored) {
                // Never let a single broken market/template break the whole resolution.
            }
        }
        return resolved;
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    /**
     * Parses one event object from a news JSON file.
     * <p>
     * Skip-and-continue contract: never throws. All problems are recorded in the report;
     * if any ERROR is recorded for this event, null is returned and the event is skipped
     * (WARNINGs keep it loaded). Duplicate-id detection across files happens in
     * {@link NewsEventLibrary}, not here.
     *
     * @param json   the event object from the {@code events[]} array
     * @param file   source file name (for report entries)
     * @param report collector for all problems found
     * @return the parsed definition, or null if the event had errors and was skipped
     */
    public static @Nullable NewsEventDefinition parse(JsonObject json, String file,
                                                      ValidationReport report) {
        try {
            return parseInternal(json, file, report);
        } catch (Exception e) {
            // Last-resort guard: parsing must never throw out of the library (plan §1).
            report.addError(file, getString(json, "id"),
                    "unexpected exception while parsing event: " + e);
            return null;
        }
    }

    private static @Nullable NewsEventDefinition parseInternal(JsonObject json, String file,
                                                               ValidationReport report) {
        int errorsBefore = report.errorCount();

        String id = getString(json, "id");
        if (id == null || id.isBlank()) {
            report.addError(file, null, "event has no 'id' string — event skipped");
            return null;
        }
        id = id.trim();

        warnUnknownKeys(json, KNOWN_EVENT_KEYS, "event", file, id, report);

        // headline / text: plain string OR inline translation map — normalized to a map
        Map<String, String> headline = parseTextMap(json.get("headline"), "headline", file, id, report);
        Map<String, String> text = parseTextMap(json.get("text"), "text", file, id, report);

        // Serialized-size sanity bound (WARNING only, plan §1)
        if (headline != null && text != null) {
            int bytes = serializedTextBytes(headline) + serializedTextBytes(text);
            if (bytes > TEXT_SIZE_WARN_BYTES) {
                report.addWarning(file, id, "serialized headline+text is " + bytes
                        + " bytes (> " + TEXT_SIZE_WARN_BYTES + " sanity bound) — consider trimming");
            }
        }

        String category = DEFAULT_CATEGORY;
        if (json.has("category")) {
            String cat = getString(json, "category");
            if (cat == null || cat.isBlank()) {
                report.addWarning(file, id, "'category' is not a non-empty string — using '"
                        + DEFAULT_CATEGORY + "'");
            } else {
                category = cat.trim();
            }
        }

        boolean adminOnly = false;
        if (json.has("adminOnly")) {
            JsonElement el = json.get("adminOnly");
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
                adminOnly = el.getAsBoolean();
            } else {
                report.addWarning(file, id, "'adminOnly' is not a boolean — using false");
            }
        }

        float weight = DEFAULT_WEIGHT;
        if (json.has("weight")) {
            Double w = getFiniteNumber(json, "weight");
            if (w == null) {
                report.addError(file, id, "'weight' is not a finite number — event skipped");
            } else if (w < 0) {
                report.addError(file, id, "'weight' must not be negative — event skipped");
            } else {
                weight = w.floatValue();
                // NOTE: the "weight 0 → can never fire" warning is intentionally NOT
                // emitted here. A weight-0 event can still be reachable as another
                // event's chain target, which is only knowable once the whole pool is
                // merged. The chain-aware version of this warning lives in the post-merge
                // pass NewsEventLibrary#validateMergedPool.
            }
        }

        long cooldownSeconds = 0;
        if (json.has("cooldownSeconds")) {
            Double cd = getFiniteNumber(json, "cooldownSeconds");
            if (cd == null || cd < 0) {
                report.addError(file, id,
                        "'cooldownSeconds' must be a non-negative number — event skipped");
            } else {
                cooldownSeconds = cd.longValue();
            }
        }

        AnnounceDelayRange announceDelay = parseAnnounceDelay(json.get("announceDelayMs"),
                file, id, report);

        // impact XOR sequences (sequences plan §1.1): the simple legacy form and the
        // advanced multi-step form are mutually exclusive. A legacy impact is normalized
        // into ONE implicit sequence so runtime code has a single representation.
        boolean hasImpact = json.has("impact");
        boolean hasSequences = json.has("sequences");
        NewsImpactEnvelope impact = null;
        List<SequenceDefinition> sequences = null;
        if (hasImpact && hasSequences) {
            report.addError(file, id,
                    "'impact' and 'sequences' are mutually exclusive — define only one — event skipped");
        } else if (hasSequences) {
            sequences = parseSequences(json.get("sequences"), file, id, report);
        } else if (hasImpact) {
            impact = parseImpact(json.get("impact"), file, id, report);
            if (impact != null) {
                sequences = List.of(SequenceDefinition.fromLegacyImpact(impact));
            }
        } else {
            report.addError(file, id,
                    "event needs either an 'impact' object or a 'sequences' array — event skipped");
        }

        // picture — optional bare file name inside config/StockMarket/news/pictures/.
        // Skip-and-continue with a twist (picture plan §1): a bad picture value is an
        // ERROR (the field is ignored) but must NEVER kill the event — the event still
        // loads picture-less. Its errors are therefore excluded from the skip decision
        // below via nonFatalErrors.
        int nonFatalErrors = 0;
        String pictureFileName = null;
        if (json.has("picture")) {
            JsonElement pictureEl = json.get("picture");
            if (!pictureEl.isJsonPrimitive() || !pictureEl.getAsJsonPrimitive().isString()) {
                report.addError(file, id, "'picture' must be a string file name — field ignored, event kept");
                nonFatalErrors++;
            } else {
                String name = pictureEl.getAsString().trim();
                // Path-traversal guard: only bare *.png file names are accepted.
                String problem = NewsPictureLibrary.validatePictureFileName(name);
                if (problem != null) {
                    report.addError(file, id, "'picture' \"" + name + "\": " + problem
                            + " — field ignored, event kept");
                    nonFatalErrors++;
                } else {
                    pictureFileName = name;
                }
            }
        }

        // requires[] — optional trigger requirements (sequences plan §3). Requirement
        // problems are FATAL errors (plan §10: an unknown/unenforceable requirement
        // must never silently pass — the whole event is skipped via the errorCount
        // comparison below). All entries are parsed before skipping so the admin gets
        // every problem in one reload pass.
        List<NewsRequirement> requirements = new ArrayList<>();
        if (json.has("requires")) {
            JsonElement requiresEl = json.get("requires");
            if (!requiresEl.isJsonArray()) {
                report.addError(file, id, "'requires' must be an array — event skipped");
            } else {
                JsonArray requiresArray = requiresEl.getAsJsonArray();
                for (int i = 0; i < requiresArray.size(); i++) {
                    JsonElement entry = requiresArray.get(i);
                    if (!entry.isJsonObject()) {
                        report.addError(file, id,
                                "requires entry " + i + " is not an object — event skipped");
                        continue;
                    }
                    NewsRequirement requirement = NewsRequirement.parse(entry.getAsJsonObject(),
                            file, id, report);
                    if (requirement != null) requirements.add(requirement);
                }
            }
        }

        // records{} — optional registry writes applied at publish (sequences plan §3).
        // Same non-fatal pattern as 'picture': a broken value is an ERROR that only
        // drops the entry — the event itself is kept. Cap breaches are WARNINGs here;
        // the actual write refusal happens at publish time (T-098 via putValue).
        Map<String, String> records = new LinkedHashMap<>();
        if (json.has("records")) {
            JsonElement recordsEl = json.get("records");
            if (!recordsEl.isJsonObject()) {
                report.addError(file, id,
                        "'records' must be an object of string values — field ignored, event kept");
                nonFatalErrors++;
            } else {
                for (Map.Entry<String, JsonElement> entry : recordsEl.getAsJsonObject().entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                        report.addError(file, id, "'records." + key
                                + "' must be a string (numeric strings allowed) — entry skipped, event kept");
                        nonFatalErrors++;
                        continue;
                    }
                    // Advisory cap checks (plan §3) — the registry refuses such writes
                    // at publish time; warn the author now instead of at 3 AM.
                    if (key.isBlank()) {
                        report.addWarning(file, id, "'records' has a blank key — the registry"
                                + " will refuse this write at publish time");
                    } else if (key.length() > NewsWorldRegistry.MAX_KEY_LENGTH) {
                        report.addWarning(file, id, "'records' key '"
                                + key.substring(0, 32) + "…' is longer than "
                                + NewsWorldRegistry.MAX_KEY_LENGTH + " chars — the registry"
                                + " will refuse this write at publish time");
                    }
                    if (value.getAsString().length() > NewsWorldRegistry.MAX_VALUE_LENGTH) {
                        report.addWarning(file, id, "'records." + key + "' value is longer than "
                                + NewsWorldRegistry.MAX_VALUE_LENGTH + " chars — the registry"
                                + " will refuse this write at publish time");
                    }
                    records.put(key, value.getAsString());
                }
                if (records.size() > NewsWorldRegistry.MAX_CUSTOM_KEYS) {
                    report.addWarning(file, id, "'records' defines " + records.size()
                            + " keys — the registry caps custom keys at "
                            + NewsWorldRegistry.MAX_CUSTOM_KEYS + ", excess writes will be refused");
                }
            }
        }

        // chains[] — optional event-chain entries (sequences plan §4, task T-098).
        // Malformed chain entries are ERRORs for the entry (the entry is skipped) but do
        // NOT kill the whole event — same non-fatal pattern as 'picture'/'records'.
        List<ChainDefinition> chains = new ArrayList<>();
        if (json.has("chains")) {
            JsonElement chainsEl = json.get("chains");
            if (!chainsEl.isJsonArray()) {
                report.addError(file, id,
                        "'chains' must be an array — field ignored, event kept");
                nonFatalErrors++;
            } else {
                JsonArray chainsArray = chainsEl.getAsJsonArray();
                for (int i = 0; i < chainsArray.size(); i++) {
                    JsonElement entry = chainsArray.get(i);
                    if (!entry.isJsonObject()) {
                        report.addError(file, id,
                                "chains entry " + i + " is not an object — entry skipped, event kept");
                        nonFatalErrors++;
                        continue;
                    }
                    ChainDefinition chain = parseChainEntry(entry.getAsJsonObject(), i,
                            file, id, report);
                    if (chain != null) {
                        chains.add(chain);
                    } else {
                        nonFatalErrors++;
                    }
                }
            }
        }

        // markets[] — matcher problems are warnings; a missing/empty list is a warning
        // too (the event loads but can never fire until markets exist that match).
        List<MarketMatcher> matchers = new ArrayList<>();
        JsonElement marketsEl = json.get("markets");
        if (marketsEl == null || !marketsEl.isJsonArray()) {
            report.addWarning(file, id, "'markets' array is missing — event can never fire");
        } else {
            for (JsonElement entry : marketsEl.getAsJsonArray()) {
                if (!entry.isJsonObject()) {
                    report.addWarning(file, id, "markets entry is not an object — entry ignored");
                    continue;
                }
                MarketMatcher matcher = MarketMatcher.parse(entry.getAsJsonObject(), file, id, report);
                if (matcher != null) matchers.add(matcher);
            }
            if (matchers.isEmpty()) {
                report.addWarning(file, id, "'markets' resolved to no usable matchers — event can never fire");
            }
        }

        // Cross-field check: a strongly negative announce delay that outlives the whole
        // impact means the event is fully over before publication — allowed, but flagged.
        if (impact != null && announceDelay != null && announceDelay.minMs() < 0
                && impact.getReversal() != NewsImpactEnvelope.ReversalMode.NONE
                && -announceDelay.minMs() >= impact.totalLengthMillis()) {
            report.addWarning(file, id, "announceDelayMs.min (" + announceDelay.minMs()
                    + " ms) outlives the whole impact (" + impact.totalLengthMillis()
                    + " ms) — the news may publish after the impact is already over");
        }

        // Any ERROR for this event ⇒ skip it (skip-and-continue) — except the
        // non-fatal 'picture' errors, which only drop the field (event kept).
        if (headline == null || text == null || announceDelay == null
                || sequences == null || sequences.isEmpty()
                || report.errorCount() > errorsBefore + nonFatalErrors) {
            return null;
        }

        return new NewsEventDefinition(id, headline, text, category, weight, cooldownSeconds,
                adminOnly, announceDelay, impact, sequences, matchers, pictureFileName,
                requirements, records, chains);
    }

    /**
     * Normalizes a {@code headline}/{@code text} value to an insertion-ordered language
     * map. A plain string becomes a single {@code "en_us"} entry so the client fallback
     * chain (exact → {@code en_us} → first entry) always finds it.
     *
     * @return the normalized map, or null on error (recorded in the report)
     */
    private static @Nullable Map<String, String> parseTextMap(@Nullable JsonElement element,
                                                              String fieldName, String file,
                                                              String eventId, ValidationReport report) {
        if (element == null) {
            report.addError(file, eventId, "'" + fieldName + "' is missing — event skipped");
            return null;
        }
        // Plain-string form (the typical single-language admin case)
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value.isBlank()) {
                report.addError(file, eventId, "'" + fieldName + "' is empty — event skipped");
                return null;
            }
            Map<String, String> map = new LinkedHashMap<>();
            map.put("en_us", value);
            return map;
        }
        // Translation-map form: { "en_us": "...", "de_de": "..." }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && !value.getAsString().isBlank()) {
                    map.put(entry.getKey(), value.getAsString());
                } else {
                    report.addWarning(file, eventId, "'" + fieldName + "." + entry.getKey()
                            + "' is not a non-empty string — language entry ignored");
                }
            }
            if (map.isEmpty()) {
                report.addError(file, eventId,
                        "'" + fieldName + "' map has no usable language entries — event skipped");
                return null;
            }
            return map;
        }
        report.addError(file, eventId, "'" + fieldName
                + "' must be a string or a language map — event skipped");
        return null;
    }

    /**
     * Parses the optional {@code announceDelayMs} object ({@code {min, max}} longs,
     * negative allowed, {@code min <= max} enforced).
     *
     * @return the parsed range (or {@link AnnounceDelayRange#ZERO} if absent),
     *         or null on error
     */
    private static @Nullable AnnounceDelayRange parseAnnounceDelay(@Nullable JsonElement element,
                                                                   String file, String eventId,
                                                                   ValidationReport report) {
        if (element == null) return AnnounceDelayRange.ZERO;
        if (!element.isJsonObject()) {
            report.addError(file, eventId,
                    "'announceDelayMs' must be an object {min, max} — event skipped");
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        warnUnknownKeys(obj, KNOWN_DELAY_KEYS, "announceDelayMs", file, eventId, report);

        Double min = getFiniteNumber(obj, "min");
        Double max = getFiniteNumber(obj, "max");
        if (min == null || max == null) {
            report.addError(file, eventId,
                    "'announceDelayMs' needs finite numeric 'min' and 'max' — event skipped");
            return null;
        }
        if (min > max) {
            report.addError(file, eventId, "'announceDelayMs' min (" + min.longValue()
                    + ") > max (" + max.longValue() + ") — event skipped");
            return null;
        }
        return new AnnounceDelayRange(min.longValue(), max.longValue());
    }

    /**
     * Parses the legacy {@code impact} object (mutually exclusive with {@code sequences}).
     * The {@code type} preset only provides defaults for the shape parameters; explicit
     * fields override them.
     *
     * @return the envelope, or null on error
     */
    private static @Nullable NewsImpactEnvelope parseImpact(@Nullable JsonElement element,
                                                            String file, String eventId,
                                                            ValidationReport report) {
        if (element == null || !element.isJsonObject()) {
            report.addError(file, eventId, "'impact' must be an object — event skipped");
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        warnUnknownKeys(obj, KNOWN_IMPACT_KEYS, "impact", file, eventId, report);

        NewsImpactEnvelope.ImpactType type = NewsImpactEnvelope.ImpactType.SHOCK;
        if (obj.has("type")) {
            String typeName = getString(obj, "type");
            NewsImpactEnvelope.ImpactType parsed = NewsImpactEnvelope.ImpactType.fromString(typeName);
            if (parsed == null) {
                report.addError(file, eventId, "impact 'type' \"" + typeName
                        + "\" is unknown (shock|trend|crash) — event skipped");
                return null;
            }
            type = parsed;
        }

        Double peakFactor = getFiniteNumber(obj, "peakFactor");
        if (peakFactor == null) {
            report.addError(file, eventId,
                    "impact 'peakFactor' must be a finite number — event skipped");
            return null;
        }
        if (peakFactor <= -1.0) {
            report.addError(file, eventId, "impact 'peakFactor' (" + peakFactor
                    + ") must be > -1 (a factor of -1 would zero the price) — event skipped");
            return null;
        }

        long rampUpSeconds = type.getDefaultRampUpSeconds();
        if (obj.has("rampUpSeconds")) {
            Double v = getFiniteNumber(obj, "rampUpSeconds");
            if (v == null || v < 0) {
                report.addError(file, eventId,
                        "impact 'rampUpSeconds' must be a non-negative number — event skipped");
                return null;
            }
            rampUpSeconds = v.longValue();
        }

        long durationSeconds = type.getDefaultDurationSeconds();
        if (obj.has("durationSeconds")) {
            Double v = getFiniteNumber(obj, "durationSeconds");
            if (v == null || v < 0) {
                report.addError(file, eventId,
                        "impact 'durationSeconds' must be a non-negative number — event skipped");
                return null;
            }
            durationSeconds = v.longValue();
        }

        NewsImpactEnvelope.ReversalMode reversal = type.getDefaultReversal();
        if (obj.has("reversal")) {
            String reversalName = getString(obj, "reversal");
            NewsImpactEnvelope.ReversalMode parsed = NewsImpactEnvelope.ReversalMode.fromString(reversalName);
            if (parsed == null) {
                report.addError(file, eventId, "impact 'reversal' \"" + reversalName
                        + "\" is unknown (ramp|exponential|none) — event skipped");
                return null;
            }
            reversal = parsed;
        }

        long reversalSeconds = type.getDefaultReversalSeconds();
        if (obj.has("reversalSeconds")) {
            Double v = getFiniteNumber(obj, "reversalSeconds");
            if (v == null || v < 0) {
                report.addError(file, eventId,
                        "impact 'reversalSeconds' must be a non-negative number — event skipped");
                return null;
            }
            reversalSeconds = v.longValue();
        }
        if (reversal != NewsImpactEnvelope.ReversalMode.NONE && reversalSeconds == 0
                && obj.has("reversalSeconds")) {
            report.addWarning(file, eventId,
                    "impact 'reversalSeconds' is 0 with a reversal mode — the impact ends abruptly");
        }

        double noise = 0.0;
        if (obj.has("noise")) {
            Double v = getFiniteNumber(obj, "noise");
            if (v == null) {
                report.addError(file, eventId,
                        "impact 'noise' must be a finite number — event skipped");
                return null;
            }
            if (v < 0) {
                report.addWarning(file, eventId, "impact 'noise' is negative — clamped to 0");
                v = 0.0;
            }
            noise = v;
        }

        return new NewsImpactEnvelope(type, peakFactor, rampUpSeconds, durationSeconds,
                reversal, reversalSeconds, noise);
    }

    // ── Sequence parsing (sequences plan §1.1 / §7) ──────────────────────

    /**
     * Parses the {@code sequences[]} array (mutually exclusive with {@code impact}).
     * Skip-and-continue: all problems of all entries are recorded before the event is
     * skipped, so the admin gets the complete picture in one reload pass.
     *
     * @return the parsed sequence definitions (possibly incomplete when entries had
     *         errors — the recorded ERRORs make {@code parseInternal} skip the event
     *         anyway), or null when the value is structurally unusable
     */
    private static @Nullable List<SequenceDefinition> parseSequences(@Nullable JsonElement element,
                                                                     String file, String eventId,
                                                                     ValidationReport report) {
        if (element == null || !element.isJsonArray()) {
            report.addError(file, eventId, "'sequences' must be an array — event skipped");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            report.addError(file, eventId, "'sequences' is empty — event skipped");
            return null;
        }
        List<SequenceDefinition> sequences = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonObject()) {
                report.addError(file, eventId,
                        "sequences entry " + i + " is not an object — event skipped");
                continue;
            }
            SequenceDefinition sequence = parseSequence(entry.getAsJsonObject(), i,
                    file, eventId, report);
            if (sequence != null) sequences.add(sequence);
        }
        return sequences;
    }

    /**
     * Parses one {@code sequences[]} entry: required non-empty {@code name}, optional
     * positive {@code weight} (default {@link SequenceDefinition#DEFAULT_SEQUENCE_WEIGHT})
     * and a non-empty {@code steps[]} array with unique step names.
     *
     * @return the parsed sequence, or null if it had errors (recorded in the report)
     */
    private static @Nullable SequenceDefinition parseSequence(JsonObject json, int index,
                                                              String file, String eventId,
                                                              ValidationReport report) {
        warnUnknownKeys(json, KNOWN_SEQUENCE_KEYS, "sequence", file, eventId, report);
        boolean valid = true;

        String name = getString(json, "name");
        if (name == null || name.isBlank()) {
            report.addError(file, eventId,
                    "sequences entry " + index + " has no 'name' string — event skipped");
            valid = false;
            name = "sequence" + index; // placeholder so follow-up messages stay readable
        } else {
            name = name.trim();
        }

        float weight = SequenceDefinition.DEFAULT_SEQUENCE_WEIGHT;
        if (json.has("weight")) {
            Double w = getFiniteNumber(json, "weight");
            if (w == null || w <= 0) {
                report.addError(file, eventId, "sequence '" + name
                        + "': 'weight' must be a positive finite number — event skipped");
                valid = false;
            } else {
                weight = w.floatValue();
            }
        }

        List<StepDefinition> steps = new ArrayList<>();
        JsonElement stepsEl = json.get("steps");
        if (stepsEl == null || !stepsEl.isJsonArray() || stepsEl.getAsJsonArray().isEmpty()) {
            report.addError(file, eventId, "sequence '" + name
                    + "' needs a non-empty 'steps' array — event skipped");
            valid = false;
        } else {
            JsonArray stepsArray = stepsEl.getAsJsonArray();
            Set<String> stepNames = new HashSet<>();
            // Step start values chain statically: each step starts at the previous
            // step's target (step 0 at 0) — needed to resolve hold-step targets here.
            double previousTarget = 0.0;
            for (int i = 0; i < stepsArray.size(); i++) {
                JsonElement entry = stepsArray.get(i);
                if (!entry.isJsonObject()) {
                    report.addError(file, eventId, "sequence '" + name + "' step " + i
                            + " is not an object — event skipped");
                    valid = false;
                    continue;
                }
                StepDefinition step = parseStep(entry.getAsJsonObject(), i, stepsArray.size(),
                        previousTarget, name, file, eventId, report);
                if (step == null) {
                    valid = false;
                    continue; // keep parsing to report ALL problems in one pass
                }
                if (!stepNames.add(step.getName())) {
                    report.addError(file, eventId, "sequence '" + name
                            + "': duplicate step name '" + step.getName() + "' — event skipped");
                    valid = false;
                }
                previousTarget = step.getTargetFactor();
                steps.add(step);
            }

            // A last step ending at a non-zero level without 'permanent' means the
            // influence snaps off at sequence end (plan §7 WARNING).
            if (valid && !steps.isEmpty()) {
                StepDefinition last = steps.get(steps.size() - 1);
                if (last.getTargetFactor() != 0.0 && !last.isPermanent()) {
                    report.addWarning(file, eventId, "sequence '" + name
                            + "' ends at non-zero influence (" + last.getTargetFactor()
                            + ") without 'permanent' — the influence snaps off at sequence end;"
                            + " add a final step back to 0 or set permanent:true");
                }
            }
        }

        return valid ? new SequenceDefinition(name, weight, steps) : null;
    }

    /**
     * Parses one {@code steps[]} entry of a sequence (see {@link StepDefinition} for
     * the field semantics and sequences plan §1.1 for the schema).
     *
     * @param stepIndex      the step position (0-based, for messages and the
     *                       last-step-only {@code permanent} check)
     * @param stepCount      total number of steps in the sequence
     * @param previousTarget the previous step's resolved target (0 for step 0) —
     *                       the value a {@code hold} step keeps
     * @return the parsed step, or null if it had errors (recorded in the report)
     */
    private static @Nullable StepDefinition parseStep(JsonObject json, int stepIndex, int stepCount,
                                                      double previousTarget, String sequenceName,
                                                      String file, String eventId,
                                                      ValidationReport report) {
        warnUnknownKeys(json, KNOWN_STEP_KEYS, "step", file, eventId, report);
        boolean valid = true;

        String name = getString(json, "name");
        if (name == null || name.isBlank()) {
            report.addError(file, eventId, "sequence '" + sequenceName + "' step " + stepIndex
                    + " has no 'name' string — event skipped");
            valid = false;
            name = "step" + stepIndex; // placeholder so follow-up messages stay readable
        } else {
            name = name.trim();
        }
        String label = "sequence '" + sequenceName + "' step '" + name + "'";

        // durationSeconds: plain number OR {min, max} range (fractional seconds allowed,
        // converted to milliseconds). The concrete duration is rolled at activation.
        long durationMinMs = 0;
        long durationMaxMs = 0;
        JsonElement durationEl = json.get("durationSeconds");
        if (durationEl == null) {
            report.addError(file, eventId, label
                    + " needs a 'durationSeconds' number or {min, max} object — event skipped");
            valid = false;
        } else if (durationEl.isJsonPrimitive() && durationEl.getAsJsonPrimitive().isNumber()) {
            Double v = getFiniteNumber(json, "durationSeconds");
            if (v == null || v < 0) {
                report.addError(file, eventId, label
                        + ": 'durationSeconds' must be a non-negative finite number — event skipped");
                valid = false;
            } else {
                durationMinMs = durationMaxMs = secondsToMillis(v);
            }
        } else if (durationEl.isJsonObject()) {
            JsonObject range = durationEl.getAsJsonObject();
            warnUnknownKeys(range, KNOWN_DELAY_KEYS, "durationSeconds", file, eventId, report);
            Double min = getFiniteNumber(range, "min");
            Double max = getFiniteNumber(range, "max");
            if (min == null || max == null) {
                report.addError(file, eventId, label
                        + ": 'durationSeconds' needs finite numeric 'min' and 'max' — event skipped");
                valid = false;
            } else if (min < 0 || max < 0) {
                report.addError(file, eventId, label
                        + ": 'durationSeconds' must not be negative — event skipped");
                valid = false;
            } else if (min > max) {
                report.addError(file, eventId, label + ": 'durationSeconds' min (" + min
                        + ") > max (" + max + ") — event skipped");
                valid = false;
            } else {
                durationMinMs = secondsToMillis(min);
                durationMaxMs = secondsToMillis(max);
            }
        } else {
            report.addError(file, eventId, label
                    + ": 'durationSeconds' must be a number or a {min, max} object — event skipped");
            valid = false;
        }

        NewsSequence.Curve curve = NewsSequence.Curve.LINEAR;
        if (json.has("curve")) {
            String curveName = getString(json, "curve");
            NewsSequence.Curve parsed = NewsSequence.Curve.fromString(curveName);
            if (parsed == null) {
                report.addError(file, eventId, label + ": 'curve' \"" + curveName
                        + "\" is unknown (linear|instant|exponential|hold) — event skipped");
                valid = false;
            } else {
                curve = parsed;
            }
        }

        // targetFactor: required for moving curves; hold keeps the previous level and
        // only warns when an explicit (different) target is given (plan §1.1).
        double targetFactor = previousTarget;
        if (curve == NewsSequence.Curve.HOLD) {
            if (json.has("targetFactor")) {
                Double v = getFiniteNumber(json, "targetFactor");
                if (v == null || v != previousTarget) {
                    report.addWarning(file, eventId, label + ": 'targetFactor' is ignored for"
                            + " curve 'hold' — the step keeps the previous value ("
                            + previousTarget + ")");
                }
            }
        } else {
            Double v = getFiniteNumber(json, "targetFactor");
            if (v == null) {
                report.addError(file, eventId, label
                        + " needs a finite 'targetFactor' number — event skipped");
                valid = false;
            } else if (v <= -1.0) {
                report.addError(file, eventId, label + ": 'targetFactor' (" + v
                        + ") must be > -1 (a factor of -1 would zero the price) — event skipped");
                valid = false;
            } else {
                targetFactor = v;
            }
        }

        // noise: same rules as the legacy impact block (non-finite = ERROR, negative = clamp)
        double noise = 0.0;
        if (json.has("noise")) {
            Double v = getFiniteNumber(json, "noise");
            if (v == null) {
                report.addError(file, eventId, label
                        + ": 'noise' must be a finite number — event skipped");
                valid = false;
            } else if (v < 0) {
                report.addWarning(file, eventId, label + ": 'noise' is negative — clamped to 0");
            } else {
                noise = v;
            }
        }

        // markets: optional per-step matcher list (same grammar, lazy resolution).
        // Absent or unusable → null = inherit the event-level markets (plan §1.1).
        List<MarketMatcher> stepMarkets = null;
        if (json.has("markets")) {
            JsonElement marketsEl = json.get("markets");
            if (!marketsEl.isJsonArray()) {
                report.addWarning(file, eventId, label
                        + ": 'markets' must be an array — step inherits the event-level markets");
            } else {
                List<MarketMatcher> parsedMatchers = new ArrayList<>();
                for (JsonElement entry : marketsEl.getAsJsonArray()) {
                    if (!entry.isJsonObject()) {
                        report.addWarning(file, eventId, label
                                + ": markets entry is not an object — entry ignored");
                        continue;
                    }
                    MarketMatcher matcher = MarketMatcher.parse(entry.getAsJsonObject(),
                            file, eventId, report);
                    if (matcher != null) parsedMatchers.add(matcher);
                }
                if (parsedMatchers.isEmpty()) {
                    report.addWarning(file, eventId, label + ": 'markets' resolved to no usable"
                            + " matchers — step inherits the event-level markets");
                } else {
                    stepMarkets = parsedMatchers;
                }
            }
        }

        // permanent: bool, last step only (plan §1.1 / §7)
        boolean permanent = false;
        if (json.has("permanent")) {
            JsonElement permanentEl = json.get("permanent");
            if (permanentEl.isJsonPrimitive() && permanentEl.getAsJsonPrimitive().isBoolean()) {
                permanent = permanentEl.getAsBoolean();
            } else {
                report.addWarning(file, eventId, label
                        + ": 'permanent' is not a boolean — using false");
            }
            if (permanent && stepIndex != stepCount - 1) {
                report.addError(file, eventId, label
                        + ": 'permanent' is only allowed on the last step — event skipped");
                valid = false;
            }
        }

        return valid ? new StepDefinition(name, durationMinMs, durationMaxMs, targetFactor,
                curve, noise, stepMarkets, permanent) : null;
    }

    /** Converts (possibly fractional) seconds to milliseconds, rounded to the nearest ms. */
    private static long secondsToMillis(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    // ── Chain parsing (sequences plan §4, task T-098) ────────────────────

    /**
     * Parses one {@code chains[]} entry. Non-fatal: a broken entry yields null and the
     * caller increments the non-fatal error counter (the event itself is kept).
     *
     * @param json    the chain entry object
     * @param index   the array position (for messages)
     * @param file    source file name for report entries
     * @param eventId owning event id for report entries
     * @param report  collector for problems
     * @return the parsed chain, or null if the entry had errors
     */
    private static @Nullable ChainDefinition parseChainEntry(JsonObject json, int index,
                                                              String file, String eventId,
                                                              ValidationReport report) {
        warnUnknownKeys(json, KNOWN_CHAIN_KEYS, "chains entry", file, eventId, report);
        String label = "chains entry " + index;

        // eventId — required
        String targetEventId = getString(json, "eventId");
        if (targetEventId == null || targetEventId.isBlank()) {
            report.addError(file, eventId, label
                    + " has no 'eventId' string — entry skipped, event kept");
            return null;
        }
        targetEventId = targetEventId.trim();

        // on — required trigger moment
        String onStr = getString(json, "on");
        if (onStr == null || onStr.isBlank()) {
            report.addError(file, eventId, label
                    + " has no 'on' string (publish|step|completion) — entry skipped, event kept");
            return null;
        }
        ChainTriggerMoment on = ChainTriggerMoment.fromString(onStr);
        if (on == null) {
            report.addError(file, eventId, label + ": 'on' \"" + onStr
                    + "\" is unknown (publish|step|completion) — entry skipped, event kept");
            return null;
        }

        // step — required when on=step, ignored otherwise
        String stepName = null;
        if (on == ChainTriggerMoment.STEP) {
            stepName = getString(json, "step");
            if (stepName == null || stepName.isBlank()) {
                report.addError(file, eventId, label
                        + ": 'on' is 'step' but no 'step' name given — entry skipped, event kept");
                return null;
            }
            stepName = stepName.trim();
        } else if (json.has("step")) {
            report.addWarning(file, eventId, label
                    + ": 'step' is ignored when 'on' is '" + on.jsonName() + "'");
        }

        // chance — optional, default 1.0, clamped to [0, 1]
        double chance = 1.0;
        if (json.has("chance")) {
            Double c = getFiniteNumber(json, "chance");
            if (c == null) {
                report.addError(file, eventId, label
                        + ": 'chance' must be a finite number — entry skipped, event kept");
                return null;
            }
            if (c < 0.0 || c > 1.0) {
                report.addWarning(file, eventId, label + ": 'chance' (" + c
                        + ") is outside [0, 1] — clamped");
                c = Math.max(0.0, Math.min(1.0, c));
            }
            chance = c;
        }

        // delaySeconds — optional, default {0, 0}, same min/max range form as step durations
        long delayMinMs = 0;
        long delayMaxMs = 0;
        if (json.has("delaySeconds")) {
            JsonElement delayEl = json.get("delaySeconds");
            if (delayEl.isJsonPrimitive() && delayEl.getAsJsonPrimitive().isNumber()) {
                Double v = getFiniteNumber(json, "delaySeconds");
                if (v == null || v < 0) {
                    report.addError(file, eventId, label
                            + ": 'delaySeconds' must be a non-negative finite number — entry skipped, event kept");
                    return null;
                }
                delayMinMs = delayMaxMs = secondsToMillis(v);
            } else if (delayEl.isJsonObject()) {
                JsonObject range = delayEl.getAsJsonObject();
                warnUnknownKeys(range, KNOWN_DELAY_KEYS, "delaySeconds", file, eventId, report);
                Double min = getFiniteNumber(range, "min");
                Double max = getFiniteNumber(range, "max");
                if (min == null || max == null) {
                    report.addError(file, eventId, label
                            + ": 'delaySeconds' needs finite numeric 'min' and 'max' — entry skipped, event kept");
                    return null;
                }
                if (min < 0 || max < 0) {
                    report.addError(file, eventId, label
                            + ": 'delaySeconds' must not be negative — entry skipped, event kept");
                    return null;
                }
                if (min > max) {
                    report.addError(file, eventId, label + ": 'delaySeconds' min (" + min
                            + ") > max (" + max + ") — entry skipped, event kept");
                    return null;
                }
                delayMinMs = secondsToMillis(min);
                delayMaxMs = secondsToMillis(max);
            } else {
                report.addError(file, eventId, label
                        + ": 'delaySeconds' must be a number or a {min, max} object — entry skipped, event kept");
                return null;
            }
        }

        // sameMarkets — optional boolean, default false
        boolean sameMarkets = false;
        if (json.has("sameMarkets")) {
            JsonElement smEl = json.get("sameMarkets");
            if (smEl.isJsonPrimitive() && smEl.getAsJsonPrimitive().isBoolean()) {
                sameMarkets = smEl.getAsBoolean();
            } else {
                report.addWarning(file, eventId, label
                        + ": 'sameMarkets' is not a boolean — using false");
            }
        }

        return new ChainDefinition(targetEventId, on, stepName, chance,
                delayMinMs, delayMaxMs, sameMarkets);
    }

    // ── Small JSON helpers (lenient, never throw) ────────────────────────
    // Package-private: shared with NewsRequirement (same lenient parse style).

    /** @return the string value of the member, or null if absent / not a string */
    static @Nullable String getString(@Nullable JsonObject json, String key) {
        if (json == null || !json.has(key)) return null;
        JsonElement el = json.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        return null;
    }

    /** @return the finite double value of the member, or null if absent / not a finite number */
    static @Nullable Double getFiniteNumber(JsonObject json, String key) {
        if (!json.has(key)) return null;
        JsonElement el = json.get(key);
        if (!el.isJsonPrimitive()) return null;
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (!prim.isNumber()) return null;
        try {
            double value = prim.getAsDouble();
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Records a WARNING for every JSON member key not in the known-key set. */
    static void warnUnknownKeys(JsonObject json, Set<String> knownKeys, String context,
                                String file, @Nullable String eventId,
                                ValidationReport report) {
        for (String key : json.keySet()) {
            if (!knownKeys.contains(key)) {
                report.addWarning(file, eventId, "unknown " + context + " field '" + key + "' — ignored");
            }
        }
    }

    /** @return the UTF-8 byte length of the map serialized as compact JSON */
    private static int serializedTextBytes(Map<String, String> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return JsonUtilities.toString(obj).getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public String toString() {
        return "NewsEventDefinition{id='" + id + "', category='" + category
                + "', weight=" + weight + ", adminOnly=" + adminOnly
                + ", matchers=" + markets.size() + ", sequences=" + sequences.size()
                + ", requires=" + requirements.size() + ", records=" + records.size()
                + ", chains=" + chains.size() + ", impact=" + impact + "}";
    }
}
