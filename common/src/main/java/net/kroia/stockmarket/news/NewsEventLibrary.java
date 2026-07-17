package net.kroia.stockmarket.news;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads, merges and validates all news event definitions from the drop-in folder
 * {@code config/StockMarket/news/} (NewsEventSystem plan §1).
 * <p>
 * <b>Drop-in loading:</b> every {@code *.json} file in the folder is loaded; each file may
 * contain any number of events; filenames are irrelevant — an admin adds news to the server
 * by simply dropping a file into the folder. All files merge into one event pool. Files are
 * processed in <b>alphabetical filename order</b> so conflicts resolve deterministically:
 * <ul>
 *   <li><b>Duplicate event ids</b> across files: reported as an ERROR, the later-processed
 *       definition wins.</li>
 *   <li><b>{@code scheduler} blocks</b>: each reload starts from the built-in defaults,
 *       then every file's block overrides the fields it specifies — last-loaded file wins
 *       per field.</li>
 * </ul>
 * <p>
 * <b>Never-crash contract:</b> a malformed file never throws out of this class and never
 * crashes the server. All problems are collected into the {@link ValidationReport} returned
 * by {@link #reload()} (skip-and-continue, like {@code MarketPresetManager}). If a reload
 * fails hard (I/O error, or every event errored out while a previous pool exists), the
 * <b>previously loaded definitions are kept</b> so a typo can never leave the plugin empty.
 * <p>
 * <b>First run:</b> if the folder is missing or contains no {@code .json} files,
 * {@link DefaultNewsEvents} writes a self-documenting {@code default_events.json} which is
 * then loaded normally. Analogously, {@link DefaultNewsPictures} populates the
 * {@code pictures/} subfolder when it contains no {@code .png} files.
 * <p>
 * <b>Pictures:</b> the library also owns the {@link NewsPictureLibrary} for the
 * {@code pictures/} subfolder; it is rescanned on every reload and its validation
 * problems merge into the same report (picture plan §2).
 * <p>
 * Matcher resolution is intentionally <b>not</b> done here — definitions store portable
 * matchers and are resolved lazily against the live market set via
 * {@link NewsEventDefinition#resolveMarkets} (ItemID name-placeholder race, plan §6.11).
 * <p>
 * Not thread-safe: call {@link #reload()} and the getters from the server thread only.
 */
public class NewsEventLibrary {

    /** News config directory, relative to the platform config folder (like the preset dir). */
    public static final String NEWS_CONFIG_DIR = "StockMarket/news";

    private static final Set<String> KNOWN_ROOT_KEYS = Set.of("scheduler", "events");
    private static final Set<String> KNOWN_SCHEDULER_KEYS = Set.of(
            "minSecondsBetweenEvents", "maxSecondsBetweenEvents",
            "maxActiveEventsGlobal", "maxActiveEventsPerMarket", "historyMaxEntries");

    /** @return the absolute news config directory ({@code config/StockMarket/news}) */
    public static Path getNewsConfigPath() {
        return Platform.getConfigFolder().resolve(NEWS_CONFIG_DIR);
    }

    /**
     * Global scheduler tuning parsed from the optional {@code scheduler} block of any news
     * file. Every reload starts from the defaults below; each file's block then overrides
     * the fields it specifies (files in alphabetical order → last-loaded wins per field).
     * Invalid values are reported and the previous value for that field is kept.
     */
    public static final class SchedulerConfig {

        /** Default minimum seconds between two random event activations. */
        public static final long DEFAULT_MIN_SECONDS_BETWEEN_EVENTS = 900;
        /** Default maximum seconds between two random event activations. */
        public static final long DEFAULT_MAX_SECONDS_BETWEEN_EVENTS = 3600;
        /** Default cap of simultaneously active events across all markets. */
        public static final int DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL = 3;
        /** Default cap of simultaneously active events per market. */
        public static final int DEFAULT_MAX_ACTIVE_EVENTS_PER_MARKET = 1;
        /** Default cap of retained news history records. */
        public static final int DEFAULT_HISTORY_MAX_ENTRIES = 500;

        private long minSecondsBetweenEvents = DEFAULT_MIN_SECONDS_BETWEEN_EVENTS;
        private long maxSecondsBetweenEvents = DEFAULT_MAX_SECONDS_BETWEEN_EVENTS;
        private int maxActiveEventsGlobal = DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL;
        private int maxActiveEventsPerMarket = DEFAULT_MAX_ACTIVE_EVENTS_PER_MARKET;
        private int historyMaxEntries = DEFAULT_HISTORY_MAX_ENTRIES;

        /** @return minimum seconds between two random event activations */
        public long getMinSecondsBetweenEvents() { return minSecondsBetweenEvents; }
        /** @return maximum seconds between two random event activations */
        public long getMaxSecondsBetweenEvents() { return maxSecondsBetweenEvents; }
        /** @return cap of simultaneously active events across all markets */
        public int getMaxActiveEventsGlobal() { return maxActiveEventsGlobal; }
        /** @return cap of simultaneously active events per market */
        public int getMaxActiveEventsPerMarket() { return maxActiveEventsPerMarket; }
        /** @return cap of retained news history records */
        public int getHistoryMaxEntries() { return historyMaxEntries; }

        /**
         * Applies one file's {@code scheduler} block on top of the current values.
         * Unknown fields and invalid values are reported; only valid fields override.
         *
         * @param json   the scheduler block
         * @param file   source file name for report entries
         * @param report collector for problems
         */
        void applyFrom(JsonObject json, String file, ValidationReport report) {
            for (String key : json.keySet()) {
                if (!KNOWN_SCHEDULER_KEYS.contains(key)) {
                    report.addWarning(file, null, "unknown scheduler field '" + key + "' — ignored");
                }
            }
            Long minBetween = readNonNegativeLong(json, "minSecondsBetweenEvents", file, report);
            if (minBetween != null) minSecondsBetweenEvents = minBetween;
            Long maxBetween = readNonNegativeLong(json, "maxSecondsBetweenEvents", file, report);
            if (maxBetween != null) maxSecondsBetweenEvents = maxBetween;
            Long maxGlobal = readNonNegativeLong(json, "maxActiveEventsGlobal", file, report);
            if (maxGlobal != null) maxActiveEventsGlobal = (int) Math.min(Integer.MAX_VALUE, maxGlobal);
            Long maxPerMarket = readNonNegativeLong(json, "maxActiveEventsPerMarket", file, report);
            if (maxPerMarket != null) maxActiveEventsPerMarket = (int) Math.min(Integer.MAX_VALUE, maxPerMarket);
            Long historyMax = readNonNegativeLong(json, "historyMaxEntries", file, report);
            if (historyMax != null) {
                if (historyMax == 0) {
                    report.addWarning(file, null, "'historyMaxEntries' is 0 — no news history will be kept");
                }
                historyMaxEntries = (int) Math.min(Integer.MAX_VALUE, historyMax);
            }

            if (minSecondsBetweenEvents > maxSecondsBetweenEvents) {
                report.addError(file, null, "scheduler minSecondsBetweenEvents ("
                        + minSecondsBetweenEvents + ") > maxSecondsBetweenEvents ("
                        + maxSecondsBetweenEvents + ") — both reset to defaults");
                minSecondsBetweenEvents = DEFAULT_MIN_SECONDS_BETWEEN_EVENTS;
                maxSecondsBetweenEvents = DEFAULT_MAX_SECONDS_BETWEEN_EVENTS;
            }
        }

        /** Reads a non-negative integer field; invalid values are reported and yield null. */
        private static @Nullable Long readNonNegativeLong(JsonObject json, String key,
                                                          String file, ValidationReport report) {
            if (!json.has(key)) return null;
            JsonElement el = json.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                try {
                    double value = el.getAsDouble();
                    if (Double.isFinite(value) && value >= 0) {
                        return (long) value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            report.addError(file, null, "scheduler '" + key
                    + "' must be a non-negative number — value ignored");
            return null;
        }
    }

    // ── State ────────────────────────────────────────────────────────────

    /** Loaded event pool (insertion = file order); replaced atomically on successful reload. */
    private Map<String, NewsEventDefinition> definitions = new LinkedHashMap<>();
    private SchedulerConfig schedulerConfig = new SchedulerConfig();
    private ValidationReport lastReport = new ValidationReport();
    /** Config-layer picture pool ({@code pictures/} subfolder), rescanned on every reload. */
    private final NewsPictureLibrary pictureLibrary = new NewsPictureLibrary();

    // ── Accessors ────────────────────────────────────────────────────────

    /** @return the loaded event pool as an unmodifiable id → definition map (file order) */
    public Map<String, NewsEventDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * @param id the event id
     * @return the definition with the given id, or null if not loaded
     */
    public @Nullable NewsEventDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    /** @return the number of loaded event definitions */
    public int getDefinitionCount() {
        return definitions.size();
    }

    /** @return the merged scheduler configuration of the last successful reload */
    public SchedulerConfig getSchedulerConfig() {
        return schedulerConfig;
    }

    /** @return the report of the most recent {@link #reload()} call */
    public ValidationReport getLastReport() {
        return lastReport;
    }

    /**
     * @return the config-layer picture library (fileName → hash/bytes/dimensions),
     *         rescanned on every {@link #reload()} pass. Consumed at publish time to
     *         snapshot a referenced picture into the published store (picture plan §2/§8)
     */
    public NewsPictureLibrary getPictureLibrary() {
        return pictureLibrary;
    }

    // ── Loading ──────────────────────────────────────────────────────────

    /**
     * Reloads all definitions from the default folder ({@link #getNewsConfigPath()}).
     *
     * @return the validation report of this pass (also stored as {@link #getLastReport()})
     */
    public ValidationReport reload() {
        return reload(getNewsConfigPath());
    }

    /**
     * Reloads all definitions from the given folder. See the class Javadoc for the
     * merge, conflict and never-crash contract. Also generates the defaults file when
     * the folder is empty.
     *
     * @param newsDir the news config directory (created if missing)
     * @return the validation report of this pass (also stored as {@link #getLastReport()})
     */
    public ValidationReport reload(Path newsDir) {
        ValidationReport report = new ValidationReport();
        try {
            reloadInternal(newsDir, report);
        } catch (Exception e) {
            // Last-resort guard: reload must never throw (plan §1). Keep the old pool.
            report.addError("", null, "unexpected exception during reload — keeping "
                    + definitions.size() + " previously loaded definition(s): " + e);
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Unexpected exception during reload", e);
        }
        lastReport = report;
        logReport(report);
        return report;
    }

    private void reloadInternal(Path newsDir, ValidationReport report) {
        // 1. Ensure the folder exists; generate defaults on first run (empty folder).
        List<Path> files;
        try {
            if (!Files.exists(newsDir)) {
                Files.createDirectories(newsDir);
            }
            files = listJsonFiles(newsDir);
            if (files.isEmpty()) {
                DefaultNewsEvents.writeDefaultFile(newsDir);
                files = listJsonFiles(newsDir);
            }
        } catch (IOException e) {
            report.addError("", null, "failed to access news config directory '" + newsDir
                    + "' — keeping " + definitions.size() + " previously loaded definition(s): " + e);
            return;
        }

        // 1b. Pictures (picture plan §2/§7): extract the default pictures when the
        //     pictures/ subfolder is empty (mirrors the defaults-json step above),
        //     then rescan the folder into the picture library. Picture problems merge
        //     into the same report so `/stockmarket news reload` shows them too.
        Path picturesDir = newsDir.resolve(NewsPictureLibrary.PICTURES_DIR_NAME);
        DefaultNewsPictures.extractDefaultsIfEmpty(picturesDir);
        pictureLibrary.rescan(picturesDir, report);

        // 2. Parse all files into a fresh pool (alphabetical order → deterministic
        //    later-wins for duplicate ids and scheduler fields).
        Map<String, NewsEventDefinition> newDefinitions = new LinkedHashMap<>();
        SchedulerConfig newScheduler = new SchedulerConfig();
        for (Path file : files) {
            parseFile(file, newDefinitions, newScheduler, report);
        }

        // 2b. Referenced-but-missing pictures: WARNING only — the event keeps loading
        //     and simply publishes picture-less (picture plan §1).
        for (NewsEventDefinition definition : newDefinitions.values()) {
            String picture = definition.getPictureFileName();
            if (picture != null && pictureLibrary.get(picture) == null) {
                report.addWarning("", definition.getId(), "picture '" + picture
                        + "' not found in " + picturesDir + " — event loads without picture");
            }
        }

        // 2c. Post-merge validation across the whole merged pool (checks that need to
        //     see every file's events at once, e.g. chain targets living in other files).
        validateMergedPool(newDefinitions, report);

        // 3. Keep the previous pool if this pass produced nothing but had errors —
        //    a typo must never leave the plugin empty (plan §5).
        if (newDefinitions.isEmpty() && !definitions.isEmpty() && report.hasErrors()) {
            report.addError("", null, "reload produced no valid events — keeping "
                    + definitions.size() + " previously loaded definition(s)");
            return;
        }

        definitions = newDefinitions;
        schedulerConfig = newScheduler;
        StockMarketMod.LOGGER.info("[NewsEventLibrary] Loaded {} news event definition(s) from {} file(s) in {}",
                newDefinitions.size(), files.size(), newsDir);
    }

    /**
     * Post-merge validation hook, called once per reload after all files have been
     * parsed and merged into the new pool (but before it replaces the current one).
     * This is the place for checks that must see the <b>complete</b> event pool —
     * per-event/per-file validation belongs into {@link NewsEventDefinition#parse}.
     * <p>
     * T-098: event-chain target validation (sequences plan §4) — chain target ids may
     * live in other files and can therefore only be resolved post-merge:
     * <ul>
     *   <li>Unknown target id (not in the merged pool) = <b>WARNING</b>, chain dropped,
     *       event kept (plan §4/§7).</li>
     *   <li>Target is {@code adminOnly} = <b>ERROR</b>, chain dropped, event kept
     *       (plan §10.3 decision: chains may NOT fire adminOnly targets).</li>
     *   <li>Self-reference with chance 1.0 and no {@code notFired} requirement =
     *       <b>WARNING</b> "possible loop, bounded by depth" (advisory only).</li>
     *   <li>T-104 (Issue #70): a {@code step}-moment chain whose referenced step name
     *       appears in no sequence of the source event = <b>WARNING</b>. The chain
     *       stays in the definition and simply remains inert at runtime — the
     *       warning is an authoring aid that catches typos at load time instead of
     *       leaving them silently dead.</li>
     * </ul>
     * Because the definition's chains list is unmodifiable, invalid chains cannot be
     * removed from it here. Instead the method returns (via the report's supplemental
     * data) the set of chain indices to skip at runtime. The NewsPlugin must query
     * {@link #isChainValid(String, int)} before enqueuing a pending activation.
     *
     * @param mergedDefinitions the freshly merged event pool of this reload pass
     * @param report            collector for problems found across the pool
     */
    private void validateMergedPool(Map<String, NewsEventDefinition> mergedDefinitions,
                                    ValidationReport report) {
        // Reset the invalid-chain index set for this reload pass.
        invalidChains.clear();

        for (Map.Entry<String, NewsEventDefinition> defEntry : mergedDefinitions.entrySet()) {
            NewsEventDefinition definition = defEntry.getValue();
            List<NewsEventDefinition.ChainDefinition> chains = definition.getChains();
            if (chains.isEmpty()) continue;

            for (int i = 0; i < chains.size(); i++) {
                NewsEventDefinition.ChainDefinition chain = chains.get(i);
                NewsEventDefinition target = mergedDefinitions.get(chain.targetEventId());

                if (target == null) {
                    // Plan §4/§7: unknown target = WARNING, chain dropped.
                    report.addWarning("", definition.getId(),
                            "chains entry " + i + ": target event '" + chain.targetEventId()
                                    + "' does not exist in the merged pool — chain dropped");
                    markChainInvalid(definition.getId(), i);
                    continue;
                }

                if (target.isAdminOnly()) {
                    // Plan §10.3: chains may NOT fire adminOnly targets = ERROR, chain dropped.
                    report.addError("", definition.getId(),
                            "chains entry " + i + ": target event '" + chain.targetEventId()
                                    + "' is adminOnly — chains may not fire adminOnly events — chain dropped, event kept");
                    markChainInvalid(definition.getId(), i);
                    continue;
                }

                // T-104 (Issue #70): step-moment chain must reference a step name that
                // exists in at least one sequence of the source event; otherwise the
                // chain remains inert at runtime with no visible signal — WARN so authors
                // catch typos at load time. Legacy `impact` events fall out of the same
                // enumeration since `getSequences()` returns their normalized implicit
                // sequence with the ramp/hold/reversal (or `permanent`) step names
                // (see NewsSequence.fromLegacyEnvelope).
                if (chain.on() == NewsEventDefinition.ChainTriggerMoment.STEP) {
                    Set<String> stepNames = collectStepNames(definition);
                    if (!stepNames.contains(chain.stepName())) {
                        report.addWarning("", definition.getId(),
                                "chains entry " + i + ": references step '" + chain.stepName()
                                        + "' which exists in no sequence of this event"
                                        + " — chain will never fire (available: " + stepNames + ")");
                    }
                }

                // Self-reference loop advisory (plan §4).
                if (chain.targetEventId().equals(definition.getId()) && chain.chance() >= 1.0) {
                    boolean hasNotFiredSelf = false;
                    for (NewsRequirement req : definition.getRequirements()) {
                        if (req instanceof NewsRequirement.NotFired nf
                                && nf.eventId().equals(definition.getId())) {
                            hasNotFiredSelf = true;
                            break;
                        }
                    }
                    if (!hasNotFiredSelf) {
                        report.addWarning("", definition.getId(),
                                "chains entry " + i + ": self-referencing chain with chance 1.0"
                                        + " and no 'notFired' requirement — possible loop, bounded by depth");
                    }
                }
            }
        }
    }

    /**
     * Collects the step-name universe of a source event across all its sequences —
     * used by T-104 chain step-name validation (Issue #70) to detect step-moment
     * chains whose {@code step} field matches nothing (typo → inert chain at runtime).
     * <p>
     * Legacy {@code impact} events do not need a special branch: their implicit
     * ramp/hold/reversal (or {@code permanent} for {@code reversal: none}) step names
     * are already exposed through {@link NewsEventDefinition#getSequences()} — the
     * legacy envelope is normalized into one implicit {@code SequenceDefinition} at
     * parse time (see {@link NewsSequence#fromLegacyEnvelope}).
     *
     * @param source the source event
     * @return the set of step names across all sequences, in insertion order
     */
    private static Set<String> collectStepNames(NewsEventDefinition source) {
        Set<String> names = new LinkedHashSet<>();
        for (NewsEventDefinition.SequenceDefinition seq : source.getSequences()) {
            for (NewsEventDefinition.StepDefinition step : seq.getSteps()) {
                names.add(step.getName());
            }
        }
        return names;
    }

    /**
     * Per-event-id, per-chain-index set of chains that failed post-merge validation.
     * The NewsPlugin checks via {@link #isChainValid} before enqueuing a pending
     * activation. Cleared and rebuilt on every reload.
     */
    private final Map<String, Set<Integer>> invalidChains = new LinkedHashMap<>();

    /** Marks one chain entry as invalid (post-merge validation failure). */
    private void markChainInvalid(String eventId, int chainIndex) {
        invalidChains.computeIfAbsent(eventId, k -> new LinkedHashSet<>()).add(chainIndex);
    }

    /**
     * Whether a chain entry passed the post-merge validation of the most recent reload.
     * The NewsPlugin must call this before enqueuing a pending chain activation —
     * chains that failed validation (unknown target, adminOnly target) are silently
     * skipped at runtime.
     *
     * @param eventId    the source event's definition id
     * @param chainIndex the index into the definition's {@link NewsEventDefinition#getChains()} list
     * @return true if the chain is valid (or the event has no invalid chains recorded)
     */
    public boolean isChainValid(String eventId, int chainIndex) {
        Set<Integer> invalid = invalidChains.get(eventId);
        return invalid == null || !invalid.contains(chainIndex);
    }

    /** Lists all {@code *.json} files of the folder, sorted by filename (case-insensitive). */
    private static List<Path> listJsonFiles(Path newsDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(newsDir, "*.json")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) files.add(file);
            }
        }
        files.sort((a, b) -> a.getFileName().toString()
                .compareToIgnoreCase(b.getFileName().toString()));
        return files;
    }

    /**
     * Parses one news file into the target pool. Skip-and-continue: a broken file only
     * adds report entries; other files still load.
     */
    private static void parseFile(Path file, Map<String, NewsEventDefinition> target,
                                  SchedulerConfig scheduler, ValidationReport report) {
        String fileName = file.getFileName().toString();

        JsonElement rootElement;
        try {
            rootElement = JsonUtilities.fromString(Files.readString(file));
        } catch (IOException e) {
            report.addError(fileName, null, "failed to read file: " + e);
            return;
        } catch (Exception e) {
            report.addError(fileName, null, "invalid JSON syntax: " + e.getMessage());
            return;
        }
        if (rootElement == null || !rootElement.isJsonObject()) {
            report.addError(fileName, null, "root element must be an object with an 'events' array");
            return;
        }
        JsonObject root = rootElement.getAsJsonObject();

        for (String key : root.keySet()) {
            if (!KNOWN_ROOT_KEYS.contains(key)) {
                report.addWarning(fileName, null, "unknown top-level field '" + key + "' — ignored");
            }
        }

        // Optional scheduler block (per-field override, last-loaded file wins)
        if (root.has("scheduler")) {
            if (root.get("scheduler").isJsonObject()) {
                scheduler.applyFrom(root.getAsJsonObject("scheduler"), fileName, report);
            } else {
                report.addError(fileName, null, "'scheduler' must be an object — block ignored");
            }
        }

        // Events array
        JsonElement eventsEl = root.get("events");
        if (eventsEl == null) {
            if (!root.has("scheduler")) {
                report.addWarning(fileName, null, "file contains no 'events' array");
            }
            return;
        }
        if (!eventsEl.isJsonArray()) {
            report.addError(fileName, null, "'events' must be an array — file skipped");
            return;
        }
        for (JsonElement el : eventsEl.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                report.addError(fileName, null, "events entry is not an object — entry skipped");
                continue;
            }
            NewsEventDefinition definition = NewsEventDefinition.parse(el.getAsJsonObject(), fileName, report);
            if (definition == null) continue;

            // Duplicate ids across files: ERROR, later-processed definition wins (plan §1).
            if (target.containsKey(definition.getId())) {
                report.addError(fileName, definition.getId(),
                        "duplicate event id — this later definition wins over the earlier one");
            }
            target.put(definition.getId(), definition);
        }
    }

    /** Logs the report entries via the mod logger (MarketPreset skip-and-continue style). */
    private static void logReport(ValidationReport report) {
        for (ValidationReport.Entry entry : report.getEntries()) {
            if (entry.severity() == ValidationReport.Severity.ERROR) {
                StockMarketMod.LOGGER.error("[NewsEventLibrary] {}", entry);
            } else {
                StockMarketMod.LOGGER.warn("[NewsEventLibrary] {}", entry);
            }
        }
        if (report.hasErrors() || report.hasWarnings()) {
            StockMarketMod.LOGGER.info("[NewsEventLibrary] Reload finished with {}", report.summary());
        }
    }
}
