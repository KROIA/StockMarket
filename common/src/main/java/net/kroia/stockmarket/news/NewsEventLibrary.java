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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads, merges and validates all news event definitions from the drop-in folder
 * {@code config/StockMarket/news/} (NewsEventSystem plan §1).
 * <p>
 * <b>Drop-in loading (SM-343):</b> every {@code *.json} file directly in the folder and in
 * each subfolder <b>exactly one level deep</b> is loaded (deeper folders are ignored, as are
 * dot-files/dot-folders and anything not ending in {@code .json}, e.g. the
 * {@code default_events.json.migrated} backup). A file holds either <b>one event object</b>
 * (the shipped {@code DefaultEvents/<id>.json} format) or the older
 * {@code {"scheduler": {...}, "events": [...]}} shape; filenames are irrelevant. All files
 * merge into one event pool. Order: {@code DefaultEvents/} first, then the root files, then
 * every other subfolder by name, files by name within each (all case-insensitive), so
 * conflicts resolve deterministically and an admin's redefinition of a shipped default
 * always beats the {@code DefaultEvents/} copy:
 * <ul>
 *   <li><b>Duplicate event ids</b> across files: reported as an ERROR naming both files,
 *       the later-processed definition wins.</li>
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
 * <b>First run:</b> if the folder tree contains no {@code .json} files,
 * {@link DefaultNewsEvents#writeDefaultFiles} writes {@code DefaultEvents/<id>.json} (one per
 * shipped event) and {@code scheduler.json}, which are then loaded normally. A pre-2.2
 * {@code default_events.json} is split into that layout once
 * ({@link DefaultNewsEvents#migrateLegacyFile}).
 * <p>
 * <b>Additive self-healing:</b> on every reload the shipped defaults are topped up without
 * overwriting anything. While the {@code DefaultEvents/} folder exists, any shipped default
 * event id defined in no loaded file is parsed into the pool and written as
 * {@code DefaultEvents/<id>.json} ({@link DefaultNewsEvents#writeMissingEventFiles}), so a
 * deleted default comes back (switch it off with the admin enable/disable toggle instead);
 * any shipped default picture missing from
 * {@code pictures/} is written per id ({@link DefaultNewsPictures#extractMissingDefaults}).
 * Admin-customized events, admin art and admin-modified defaults are always preserved — the
 * heal only adds what is missing, so existing installs receive newly shipped content while
 * keeping their edits.
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
            "maxActiveEventsGlobal", "maxActiveEventsPerMarket", "historyMaxEntries",
            "impactDurationMultiplier", "impactStrengthMultiplier");

    /** @return the absolute news config directory ({@code config/StockMarket/news}) */
    public static Path getNewsConfigPath() {
        return Platform.getConfigFolder().resolve(NEWS_CONFIG_DIR);
    }

    /**
     * Global scheduler tuning parsed from the optional {@code scheduler} block of any news
     * file. Every reload starts from the defaults below; each file's block then overrides
     * the fields it specifies (files in load order, see listJsonFiles → last-loaded wins per field).
     * Invalid values are reported and the previous value for that field is kept.
     */
    public static final class SchedulerConfig {

        /**
         * Default minimum seconds between two random event activations (2 h). SM-344: together
         * with the max this gives about 6 news per real day across all markets (measured with
         * the shipped events, see the {@code default_files_give_4_to_8_news_per_day} test).
         * Was 900 up to 2.1.x and 10800 in the SM-338 development state.
         */
        public static final long DEFAULT_MIN_SECONDS_BETWEEN_EVENTS = 7200;
        /** Default maximum seconds between two random event activations (4.5 h; SM-344). */
        public static final long DEFAULT_MAX_SECONDS_BETWEEN_EVENTS = 16200;
        /** SM-344: min interval of the SM-338 scheduler.json (about 4 news a day), migrated to the new default. */
        public static final long SM338_DEFAULT_MIN_SECONDS_BETWEEN_EVENTS = 10800;
        /** SM-344: max interval of the SM-338 scheduler.json, migrated to the new default. */
        public static final long SM338_DEFAULT_MAX_SECONDS_BETWEEN_EVENTS = 32400;
        /** SM-338: min interval written into {@code default_events.json} by releases up to 2.1.x. */
        public static final long OLD_DEFAULT_MIN_SECONDS_BETWEEN_EVENTS = 900;
        /** SM-338: max interval written into {@code default_events.json} by releases up to 2.1.x. */
        public static final long OLD_DEFAULT_MAX_SECONDS_BETWEEN_EVENTS = 3600;
        /**
         * Built-in impact duration multiplier when no file sets one: 1 = events play exactly
         * as authored. The shipped {@code scheduler.json} sets
         * {@link #SHIPPED_IMPACT_DURATION_MULTIPLIER} (SM-338).
         */
        public static final double DEFAULT_IMPACT_DURATION_MULTIPLIER = 1.0;
        /** Built-in impact strength multiplier when no file sets one (1 = as authored). */
        public static final double DEFAULT_IMPACT_STRENGTH_MULTIPLIER = 1.0;
        /** SM-338: duration multiplier of the shipped scheduler file: every step lasts 3x as long. */
        public static final double SHIPPED_IMPACT_DURATION_MULTIPLIER = 3.0;
        /** SM-338: strength multiplier of the shipped scheduler file: every step target is halved. */
        public static final double SHIPPED_IMPACT_STRENGTH_MULTIPLIER = 0.5;
        /** Default cap of simultaneously active events across all markets. */
        public static final int DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL = 3;
        /** Default cap of simultaneously active events per market. */
        public static final int DEFAULT_MAX_ACTIVE_EVENTS_PER_MARKET = 1;
        /**
         * Default cap of retained news history records.
         * <p>
         * Raised from 500 → 1000 by T-110 (chunked disk layout, ≈10 chunks of
         * {@value net.kroia.stockmarket.news.NewsHistoryChunkStore#CHUNK_SIZE}
         * records each). The JSON-authored {@code historyMaxEntries} value is
         * still respected on top — this default only kicks in when the field is
         * missing from every scheduler block. See {@code configuration.md} for
         * the cap-alignment caveat ("cap should be a multiple of the chunk size
         * for exact retention").
         */
        public static final int DEFAULT_HISTORY_MAX_ENTRIES = 1000;

        private long minSecondsBetweenEvents = DEFAULT_MIN_SECONDS_BETWEEN_EVENTS;
        private long maxSecondsBetweenEvents = DEFAULT_MAX_SECONDS_BETWEEN_EVENTS;
        private int maxActiveEventsGlobal = DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL;
        private int maxActiveEventsPerMarket = DEFAULT_MAX_ACTIVE_EVENTS_PER_MARKET;
        private int historyMaxEntries = DEFAULT_HISTORY_MAX_ENTRIES;
        private double impactDurationMultiplier = DEFAULT_IMPACT_DURATION_MULTIPLIER;
        private double impactStrengthMultiplier = DEFAULT_IMPACT_STRENGTH_MULTIPLIER;

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
         * SM-338: factor applied to every rolled step duration at activation (all events,
         * also admin triggers and chains). 3 = each event's impact lasts three times as long.
         *
         * @return the impact duration multiplier (&gt; 0)
         */
        public double getImpactDurationMultiplier() { return impactDurationMultiplier; }
        /**
         * SM-338: factor applied to every step target at activation, so also to permanent
         * shifts baked into the default price. 0.5 = each event moves the price half as far.
         *
         * @return the impact strength multiplier (&gt; 0)
         */
        public double getImpactStrengthMultiplier() { return impactStrengthMultiplier; }

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
            Double durationMult = readPositiveDouble(json, "impactDurationMultiplier", file, report);
            if (durationMult != null) impactDurationMultiplier = durationMult;
            Double strengthMult = readPositiveDouble(json, "impactStrengthMultiplier", file, report);
            if (strengthMult != null) impactStrengthMultiplier = strengthMult;

            if (minSecondsBetweenEvents > maxSecondsBetweenEvents) {
                report.addError(file, null, "scheduler minSecondsBetweenEvents ("
                        + minSecondsBetweenEvents + ") > maxSecondsBetweenEvents ("
                        + maxSecondsBetweenEvents + ") — both reset to defaults");
                minSecondsBetweenEvents = DEFAULT_MIN_SECONDS_BETWEEN_EVENTS;
                maxSecondsBetweenEvents = DEFAULT_MAX_SECONDS_BETWEEN_EVENTS;
            }
        }

        /** Reads a positive finite number field; invalid values are reported and yield null. */
        private static @Nullable Double readPositiveDouble(JsonObject json, String key,
                                                           String file, ValidationReport report) {
            if (!json.has(key)) return null;
            JsonElement el = json.get(key);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                double value = el.getAsDouble();
                if (Double.isFinite(value) && value > 0) return value;
            }
            report.addError(file, null, "scheduler '" + key
                    + "' must be a positive number — value ignored");
            return null;
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
            // SM-343: split a pre-2.2 default_events.json (after its content migrations)
            // into DefaultEvents/<id>.json + scheduler.json, rename it to *.migrated.
            DefaultNewsEvents.migrateLegacyFile(newsDir);
            files = listJsonFiles(newsDir);
            if (files.isEmpty()) {
                DefaultNewsEvents.writeDefaultFiles(newsDir);
                files = listJsonFiles(newsDir);
            } else {
                // SM-338: move an untouched pre-2.2 scheduler block to the new defaults.
                DefaultNewsEvents.migrateOldSchedulerDefaults(
                        newsDir.resolve(DefaultNewsEvents.SCHEDULER_FILE_NAME));
                // SM-341: give an unedited old luxury_goods_tax its "once until repealed" gate.
                DefaultNewsEvents.migrateOldLuxuryTax(newsDir.resolve(DefaultNewsEvents.DEFAULT_EVENTS_DIR)
                        .resolve("luxury_goods_tax.json"));
            }
        } catch (IOException e) {
            report.addError("", null, "failed to access news config directory '" + newsDir
                    + "' — keeping " + definitions.size() + " previously loaded definition(s): " + e);
            return;
        }

        // 1b. Pictures (picture plan §2/§7): additively self-heal the default pictures —
        //     each shipped default <eventId>.png is written only if it is missing, so new
        //     defaults reach existing installs while admin art is never overwritten — then
        //     rescan the folder into the picture library. Picture problems merge into the
        //     same report so `/stockmarket news reload` shows them too.
        Path picturesDir = newsDir.resolve(NewsPictureLibrary.PICTURES_DIR_NAME);
        DefaultNewsPictures.extractMissingDefaults(picturesDir);
        pictureLibrary.rescan(picturesDir, report);

        // 2. Parse all files into a fresh pool (deterministic order, see listJsonFiles →
        //    deterministic later-wins for duplicate ids and scheduler fields).
        Map<String, NewsEventDefinition> newDefinitions = new LinkedHashMap<>();
        Map<String, String> sourceFiles = new LinkedHashMap<>();
        SchedulerConfig newScheduler = new SchedulerConfig();
        for (Path file : files) {
            parseFile(file, displayName(newsDir, file), newDefinitions, sourceFiles, newScheduler, report);
        }

        // 2a. Additive self-heal of the shipped default EVENTS. Only tops up an existing
        //     DefaultEvents/ folder — the folder every install has: any shipped default
        //     whose id is present in NO loaded file is parsed into the pool here (active this
        //     reload) and written as DefaultEvents/<id>.json. A tree without DefaultEvents/
        //     is left alone — an admin who manages news without the shipped defaults.
        //     Skipped when this pass is a hard failure that will keep the previous pool
        //     (guard in step 3), so a broken folder can never smuggle defaults in over a
        //     good pool.
        boolean keepPrevious = newDefinitions.isEmpty() && !definitions.isEmpty() && report.hasErrors();
        if (!keepPrevious && Files.isDirectory(newsDir.resolve(DefaultNewsEvents.DEFAULT_EVENTS_DIR))) {
            healMissingDefaultEvents(newsDir, newDefinitions, report);
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
        //    a typo must never leave the plugin empty (plan §5). Computed before the
        //    event self-heal (step 2a) so healing can never defeat this safety net.
        if (keepPrevious) {
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
     * Additive self-heal of the shipped default events (existing installs receive newly
     * shipped events). For every id in {@link DefaultNewsEvents#DEFAULT_EVENT_IDS} that is
     * present in <b>no</b> loaded file anywhere in the news folder tree (so an admin who
     * redefined a default id in their own file is respected and not duplicated), the shipped
     * event object is:
     * <ol>
     *   <li>parsed through the normal {@link NewsEventDefinition#parse} path and added to
     *       the in-memory pool of this reload — the event is active immediately, without a
     *       second full reload;</li>
     *   <li>persisted by {@link DefaultNewsEvents#writeMissingEventFiles} as
     *       {@code DefaultEvents/<id>.json}; an existing file is never rewritten.</li>
     * </ol>
     * Existing entries are never overwritten (admin edits are respected) and the heal can
     * never introduce a duplicate-id error. Added ids are logged at INFO — when nothing is
     * missing (e.g. the pristine shipped defaults) the method is a no-op and stays silent,
     * preserving the zero-warning defaults contract. Never throws: on a persist failure the
     * in-memory additions are still kept.
     *
     * @param newsDir the news config directory (must already exist)
     * @param pool    the freshly parsed event pool of this reload, extended in place
     * @param report  collector passed to the per-event parse (shipped defaults parse clean)
     */
    private static void healMissingDefaultEvents(Path newsDir,
                                                 Map<String, NewsEventDefinition> pool,
                                                 ValidationReport report) {
        List<String> missing = new ArrayList<>();
        for (String id : DefaultNewsEvents.DEFAULT_EVENT_IDS) {
            if (!pool.containsKey(id)) missing.add(id);
        }
        if (missing.isEmpty()) return;

        Map<String, JsonObject> shipped = DefaultNewsEvents.getDefaultEventObjectsById();
        List<String> added = new ArrayList<>(missing.size());
        for (String id : missing) {
            JsonObject obj = shipped.get(id);
            if (obj == null) continue; // unreachable: DEFAULT_EVENT_IDS are derived from the shipped JSON
            NewsEventDefinition def = NewsEventDefinition.parse(
                    obj, DefaultNewsEvents.DEFAULT_EVENTS_DIR + "/" + id + ".json", report);
            if (def == null) continue; // shipped defaults parse clean; guard defensively anyway
            pool.put(def.getId(), def);
            added.add(def.getId());
        }
        if (added.isEmpty()) return;

        // Persist the additions (best-effort). On failure the in-memory heal is kept.
        try {
            DefaultNewsEvents.writeMissingEventFiles(newsDir, added);
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Failed to write healed default news event file(s)"
                    + " - in-memory heal kept", e);
        }

        StockMarketMod.LOGGER.info(
                "[NewsEventLibrary] Self-healed {} shipped default news event(s) missing from the news folder: {}",
                added.size(), added);
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

        // Collect every event id that is referenced as a chain target anywhere in the
        // merged pool. A weight-0 event named here is legitimately reachable via a chain
        // (fired on another event's completion/step), so it is exempt from the
        // "can never fire" warning below — analogous to the adminOnly exemption.
        Set<String> chainTargets = new LinkedHashSet<>();
        for (NewsEventDefinition definition : mergedDefinitions.values()) {
            for (NewsEventDefinition.ChainDefinition chain : definition.getChains()) {
                chainTargets.add(chain.targetEventId());
            }
        }

        // Chain-aware "weight 0 → can never fire" warning (moved out of
        // NewsEventDefinition#parse, which lacked cross-event context). Only a weight-0
        // event that is neither adminOnly (manually fired) nor a chain target (fired via
        // another event) is genuinely orphaned and unreachable.
        for (NewsEventDefinition definition : mergedDefinitions.values()) {
            if (definition.getWeight() == 0 && !definition.isAdminOnly()
                    && !chainTargets.contains(definition.getId())) {
                report.addWarning("", definition.getId(),
                        "'weight' is 0 and the event is not adminOnly — it can never fire");
            }
        }

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

    /**
     * Lists the news files in load order (SM-343): first the files of
     * {@link DefaultNewsEvents#DEFAULT_EVENTS_DIR}, then the {@code *.json} files directly in
     * the folder, then every other subfolder (one level deep only) by name. Loading the
     * shipped defaults first means an admin's redefinition of a default id anywhere else is
     * processed later and therefore wins (duplicate-id rule). Folders and files are sorted by
     * name (case-insensitive); names starting with a dot are skipped, as is anything not
     * ending in {@code .json} (e.g. {@code *.migrated}, {@code *.bak}).
     */
    private static List<Path> listJsonFiles(Path newsDir) throws IOException {
        List<Path> rootFiles = new ArrayList<>();
        List<Path> subDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(newsDir)) {
            for (Path p : stream) {
                if (p.getFileName().toString().startsWith(".")) continue;
                if (Files.isDirectory(p)) subDirs.add(p);
                else if (isJsonFile(p)) rootFiles.add(p);
            }
        }
        rootFiles.sort(NewsEventLibrary::compareByName);
        subDirs.sort(NewsEventLibrary::compareByName);
        List<Path> files = new ArrayList<>();
        for (Path dir : subDirs) {
            if (isDefaultEventsDir(dir)) files.addAll(listFolder(dir));
        }
        files.addAll(rootFiles);
        for (Path dir : subDirs) {
            if (!isDefaultEventsDir(dir)) files.addAll(listFolder(dir));
        }
        return files;
    }

    private static boolean isDefaultEventsDir(Path dir) {
        return dir.getFileName().toString().equalsIgnoreCase(DefaultNewsEvents.DEFAULT_EVENTS_DIR);
    }

    /** @return the {@code *.json} files directly in {@code dir} (no dot-files), sorted by name */
    private static List<Path> listFolder(Path dir) throws IOException {
        List<Path> inDir = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (!p.getFileName().toString().startsWith(".") && isJsonFile(p)) inDir.add(p);
            }
        }
        inDir.sort(NewsEventLibrary::compareByName);
        return inDir;
    }

    private static boolean isJsonFile(Path p) {
        return Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static int compareByName(Path a, Path b) {
        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
    }

    /** @return the file path relative to the news folder with {@code /} separators, e.g. {@code DefaultEvents/x.json} */
    private static String displayName(Path newsDir, Path file) {
        return newsDir.relativize(file).toString().replace('\\', '/');
    }

    /**
     * Parses one news file into the target pool. Skip-and-continue: a broken file only
     * adds report entries; other files still load. The file is either one event object
     * (has an {@code id} and no {@code events}/{@code scheduler}) or the
     * {@code {"scheduler": {...}, "events": [...]}} shape.
     *
     * @param sourceFiles event id → display name of the file that defined it (for the duplicate message)
     */
    private static void parseFile(Path file, String fileName, Map<String, NewsEventDefinition> target,
                                  Map<String, String> sourceFiles,
                                  SchedulerConfig scheduler, ValidationReport report) {

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

        // SM-343: one event object per file (the DefaultEvents/<id>.json format).
        if (root.has("id") && !root.has("events") && !root.has("scheduler")) {
            addDefinition(NewsEventDefinition.parse(root, fileName, report), fileName, target, sourceFiles, report);
            return;
        }

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
            addDefinition(NewsEventDefinition.parse(el.getAsJsonObject(), fileName, report),
                    fileName, target, sourceFiles, report);
        }
    }

    /**
     * Adds a parsed definition to the pool. Duplicate ids: ERROR naming both files, the
     * later-processed definition wins (plan §1).
     */
    private static void addDefinition(@Nullable NewsEventDefinition definition, String fileName,
                                      Map<String, NewsEventDefinition> target,
                                      Map<String, String> sourceFiles, ValidationReport report) {
        if (definition == null) return;
        String earlier = sourceFiles.put(definition.getId(), fileName);
        if (earlier != null) {
            report.addError(fileName, definition.getId(), "duplicate event id — also defined in '"
                    + earlier + "'; this later definition in '" + fileName + "' wins");
        }
        target.put(definition.getId(), definition);
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
