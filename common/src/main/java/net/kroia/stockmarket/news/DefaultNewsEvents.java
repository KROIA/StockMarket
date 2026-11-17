package net.kroia.stockmarket.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ships the default news events that {@link NewsEventLibrary} writes into
 * {@code config/StockMarket/news/DefaultEvents/} (one file per event) when no news file exists yet.
 * <p>
 * The files double as <b>admin documentation</b>: they demonstrate every schema feature —
 * shock/trend/crash presets, single- and multi-market events, exact/tag/glob matchers,
 * negative {@code weightFactor} (inverted impact), an {@code adminOnly} event, an
 * {@code announceDelayMs} range (including a negative "insiders knew first" range),
 * {@code reversal: "none"} (permanent shift), inline {@code en_us}/{@code zh_cn}/{@code de_de}
 * translation maps, and a {@code picture} reference per event (bare file name inside
 * the {@code pictures/} subfolder, see {@link DefaultNewsPictures}). Since T-101 it
 * also showcases the advanced features (sequences plan §1–§4): a multi-step
 * {@code sequences[]} event with two weighted sequence variants, duration ranges,
 * mixed curves, per-step noise and a per-step {@code markets} override
 * ({@code gold_rush_rumor}), and the gold-standard pair — {@code gold_reserve_standard}
 * fires at most once naturally ({@code requires} notFired-itself), writes
 * {@code records: {era: gold_standard}} into the world-event registry and {@code chains}
 * the rumor event off its hold step; {@code end_of_gold_standard} becomes eligible only
 * hours after the adoption fired ({@code firedBefore.minSecondsAgo} + {@code keyEquals})
 * and flips the era back to {@code fiat}. Admins add their
 * own news by dropping additional {@code .json} files into the folder (drop-in loading;
 * filenames are irrelevant, all files merge).
 * <p>
 * <b>2026-07-19:</b> 50 additional events were appended (mining/stone, agriculture,
 * livestock/fishing/textiles, nether/end, and trade/politics/finance themes), bringing
 * the shipped defaults to 72 events.
 * <p>
 * <b>Source (SM-342):</b> the defaults live in {@code common/src/main/resources/news/}:
 * {@code events/<eventId>.json} (one event object per file), {@code scheduler.json} and
 * {@code index.json} (ordered id list; a new default event needs an entry there).
 * <p>
 * <b>Config layout (SM-343):</b> the defaults are written as one file per event into
 * {@code config/StockMarket/news/DefaultEvents/<eventId>.json} (same text as the resource
 * file) plus {@code config/StockMarket/news/scheduler.json} ({@code {"scheduler": {...}}}),
 * see {@link #writeDefaultFiles}. A pre-2.2 single {@code default_events.json} is split into
 * that layout once and renamed to {@code default_events.json.migrated}
 * ({@link #migrateLegacyFile}).
 * <p>
 * <b>Additive self-healing (existing installs):</b> on every reload {@link NewsEventLibrary}
 * writes {@code DefaultEvents/<id>.json} for any shipped default whose id is defined in
 * <b>no</b> loaded file ({@link #writeMissingEventFiles}); existing files (admin edits) are
 * never overwritten (exceptions: the one-time SM-338 scheduler and SM-341 luxury tax
 * upgrades, {@link #migrateOldSchedulerDefaults}, {@link #migrateOldLuxuryTax}). The default
 * pictures are healed independently and per id into {@code pictures/}
 * (see {@link DefaultNewsPictures#extractMissingDefaults}).
 */
public final class DefaultNewsEvents {

    /**
     * Pre-2.2 single defaults file ({@code scheduler} + {@code events} array). Only read by
     * the one-time split in {@link #migrateLegacyFile}, then renamed with {@link #MIGRATED_SUFFIX}.
     */
    public static final String LEGACY_FILE_NAME = "default_events.json";
    /** Suffix of the renamed legacy file (no longer {@code .json}, so it is never loaded again). */
    public static final String MIGRATED_SUFFIX = ".migrated";
    /** Subfolder of the news config dir holding one {@code <eventId>.json} per shipped default. */
    public static final String DEFAULT_EVENTS_DIR = "DefaultEvents";
    /** File in the news config dir holding the scheduler block ({@code {"scheduler": {...}}}). */
    public static final String SCHEDULER_FILE_NAME = "scheduler.json";

    /**
     * Classpath folder of the shipped defaults ({@code common/src/main/resources/news/}).
     * A jar cannot be listed reliably through the classloader, so {@link #INDEX_RESOURCE}
     * defines the set and order of the default events.
     */
    public static final String RESOURCE_DIR = "/news/";
    /** JSON array of the default event ids, in file order (inside {@link #RESOURCE_DIR}). */
    public static final String INDEX_RESOURCE = "index.json";
    /** The shipped {@code scheduler} block (inside {@link #RESOURCE_DIR}). */
    public static final String SCHEDULER_RESOURCE = "scheduler.json";
    /** Subfolder of {@link #RESOURCE_DIR} holding one {@code <eventId>.json} event object per file. */
    public static final String EVENTS_RESOURCE_DIR = "events/";

    /**
     * The shipped defaults, assembled once from the resources in {@link #RESOURCE_DIR}
     * (empty string if they are missing or unparsable, see {@link #loadResource}).
     * Declared before {@link #DEFAULT_EVENT_IDS}, which is derived from it during static init. Schema reference (see NewsEventSystem plan §1):
     * <ul>
     *   <li>{@code scheduler} — optional; global scheduler tuning, last-loaded file wins.</li>
     *   <li>{@code headline}/{@code text} — plain string or {@code {lang → text}} map.</li>
     *   <li>{@code impact.type} — {@code shock|trend|crash} preset; explicit fields override.</li>
     *   <li>{@code impact.reversal} — {@code ramp|exponential|none} ({@code none} = permanent).</li>
     *   <li>{@code markets[].item} — exact id, {@code #tag}, or {@code *}-glob; never the
     *       short ItemID (not portable across servers).</li>
     *   <li>{@code announceDelayMs} — {@code {min,max}} ms range sampled per activation;
     *       negative = impact starts before the news goes public.</li>
     *   <li>{@code picture} — optional bare file name of a PNG inside the
     *       {@code pictures/} subfolder (≤ 128 KiB, 16..512 px per side); a missing
     *       file only warns and the event publishes picture-less.</li>
     *   <li>{@code sequences[]} — advanced multi-step alternative to {@code impact}
     *       (exactly one of the two per event): weighted sequence variants, per-step
     *       {@code durationSeconds} ranges (rolled once at activation),
     *       {@code linear|instant|exponential|hold} curves, per-step {@code noise}
     *       and per-step {@code markets} overrides.</li>
     *   <li>{@code requires[]} — trigger requirements against the world-event
     *       registry; ALL must hold for the event to become eligible.</li>
     *   <li>{@code records{}} — string→string registry writes applied at publish
     *       (last write wins).</li>
     *   <li>{@code undoes} — optional event id; when this event's permanent shift bakes it
     *       adds back, per market, the default-price change that event's bakes left there
     *       (e.g. {@code luxury_tax_repealed} undoes {@code luxury_goods_tax}).</li>
     *   <li>{@code chains[]} — follow-up activations triggered on
     *       {@code publish|step|completion} with a chance and a delay range.</li>
     * </ul>
     */
    private static final String DEFAULT_EVENTS_JSON;
    /** Raw resource text of each shipped default event, by id in index order (filled by {@link #loadResource}). */
    private static final Map<String, String> EVENT_TEXTS = new LinkedHashMap<>();
    /** Raw resource text of the shipped scheduler block (filled by {@link #loadResource}). */
    private static String schedulerText = "";

    static {
        DEFAULT_EVENTS_JSON = loadResource();
    }

    /**
     * The ids of all shipped default events, in file order. Each id also names its
     * default picture ({@code <eventId>.png}) extracted by {@link DefaultNewsPictures}.
     */
    public static final List<String> DEFAULT_EVENT_IDS =
            List.copyOf(getDefaultEventObjectsById().keySet());

    private DefaultNewsEvents() {
    }

    /**
     * Fresh install: writes every shipped default as {@code DefaultEvents/<id>.json} (same
     * text as the resource file) and {@link #SCHEDULER_FILE_NAME} if it is missing.
     * Never throws — failures are logged and reported via the return value.
     *
     * @param newsDir the news config directory (must already exist)
     * @return true if the files were written successfully
     */
    public static boolean writeDefaultFiles(Path newsDir) {
        if (DEFAULT_EVENT_IDS.isEmpty()) {
            // Resource missing or unparsable (already logged): never write an empty/broken set.
            StockMarketMod.LOGGER.error("[NewsEventLibrary] No shipped default news events available -"
                    + " {} not generated", DEFAULT_EVENTS_DIR);
            return false;
        }
        try {
            Path scheduler = newsDir.resolve(SCHEDULER_FILE_NAME);
            if (!Files.exists(scheduler)) {
                Files.writeString(scheduler, schedulerFileText(), StandardCharsets.UTF_8);
            }
            writeMissingEventFiles(newsDir, EVENT_TEXTS.keySet());
            StockMarketMod.LOGGER.info("[NewsEventLibrary] Generated default news events in {}",
                    newsDir.resolve(DEFAULT_EVENTS_DIR));
            return true;
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Failed to write default news events", e);
            return false;
        }
    }

    /**
     * Writes {@code DefaultEvents/<id>.json} with the shipped resource text for each given id
     * whose file does not exist yet. An existing file (admin edit, even a broken one) is never
     * touched, so the caller must only pass ids defined nowhere in the news folder tree.
     *
     * @param newsDir the news config directory
     * @param ids     shipped default ids to write
     * @return the number of files written
     * @throws IOException if the folder or a file cannot be written
     */
    public static int writeMissingEventFiles(Path newsDir, Collection<String> ids) throws IOException {
        Path dir = newsDir.resolve(DEFAULT_EVENTS_DIR);
        Files.createDirectories(dir);
        int written = 0;
        for (String id : ids) {
            String text = EVENT_TEXTS.get(id);
            Path file = dir.resolve(id + ".json");
            if (text == null || Files.exists(file)) continue;
            Files.writeString(file, text, StandardCharsets.UTF_8);
            written++;
        }
        return written;
    }

    /** @return the shipped scheduler block wrapped as {@code {"scheduler": {...}}} (content of {@link #SCHEDULER_FILE_NAME}) */
    public static String schedulerFileText() {
        return "{\n  \"scheduler\": " + indent(schedulerText, "  ").substring(2) + "\n}\n";
    }

    /** @return the raw resource text of the shipped default event {@code id}, or null */
    public static @Nullable String getDefaultEventText(String id) {
        return EVENT_TEXTS.get(id);
    }

    /** @return the raw JSON text of the shipped defaults (used by tests to validate it parses clean) */
    public static String getDefaultEventsJson() {
        return DEFAULT_EVENTS_JSON;
    }

    /**
     * Parses the shipped defaults resource into its root object
     * ({@code scheduler} + {@code events}). Never throws.
     *
     * @return the parsed root object, or null if the resource is missing or fails
     *         to parse (logged; the test suite validates the shipped file)
     */
    private static @Nullable JsonObject parseDefaultRoot() {
        try {
            JsonElement root = JsonUtilities.fromString(DEFAULT_EVENTS_JSON);
            if (root != null && root.isJsonObject()) {
                return root.getAsJsonObject();
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsEventLibrary] Failed to parse the shipped default news events JSON", e);
        }
        return null;
    }

    /**
     * Extracts the shipped default event objects keyed by their {@code id}, in file order.
     * Used by {@link NewsEventLibrary}'s additive self-heal to (a) parse a missing default
     * into the live pool through the normal parse path and (b) run the legacy luxury tax
     * migration. Never throws.
     *
     * @return an insertion-ordered id → event-object map of the shipped defaults
     *         (empty if the resource is missing or fails to parse)
     */
    public static Map<String, JsonObject> getDefaultEventObjectsById() {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        JsonObject root = parseDefaultRoot();
        if (root == null) return map;
        JsonElement eventsEl = root.get("events");
        if (eventsEl == null || !eventsEl.isJsonArray()) return map;
        for (JsonElement el : eventsEl.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            JsonElement idEl = obj.get("id");
            if (idEl != null && idEl.isJsonPrimitive()) {
                map.put(idEl.getAsString(), obj);
            }
        }
        return map;
    }

    /**
     * SM-343 one-time split of a pre-2.2 {@link #LEGACY_FILE_NAME}. If it exists:
     * <ul>
     *   <li>{@link #DEFAULT_EVENTS_DIR} already exists: the folder wins and the old file is
     *       only renamed (logged with the reason);</li>
     *   <li>otherwise the SM-338/SM-341 content migrations run on it first, then every event
     *       object in it (shipped or admin-added, edits kept) becomes
     *       {@code DefaultEvents/<id>.json} and its scheduler block becomes
     *       {@link #SCHEDULER_FILE_NAME} (unless that file already exists: then the block is
     *       only kept in the backup, logged).</li>
     * </ul>
     * Afterwards the file is renamed to {@code default_events.json.migrated} and never read
     * again. A file that is not a parsable JSON object is left in place, so it keeps loading
     * (and reporting its errors) as a normal news file until the admin fixes it. Never throws.
     *
     * @param newsDir the news config directory
     */
    public static void migrateLegacyFile(Path newsDir) {
        Path legacy = newsDir.resolve(LEGACY_FILE_NAME);
        if (!Files.isRegularFile(legacy)) return;
        Path eventsDir = newsDir.resolve(DEFAULT_EVENTS_DIR);
        Path backup = newsDir.resolve(LEGACY_FILE_NAME + MIGRATED_SUFFIX);
        try {
            if (Files.exists(eventsDir)) {
                Files.move(legacy, backup, StandardCopyOption.REPLACE_EXISTING);
                StockMarketMod.LOGGER.warn("[NewsEventLibrary] {} and the {}/ folder both exist - the folder wins;"
                        + " the old file was renamed to {} without splitting", LEGACY_FILE_NAME,
                        DEFAULT_EVENTS_DIR, backup.getFileName());
                return;
            }
            migrateOldSchedulerDefaults(legacy);
            migrateOldLuxuryTax(legacy);

            JsonElement rootEl;
            try {
                rootEl = JsonUtilities.fromString(Files.readString(legacy, StandardCharsets.UTF_8));
            } catch (Exception e) {
                rootEl = null;
            }
            if (rootEl == null || !rootEl.isJsonObject()) {
                StockMarketMod.LOGGER.warn("[NewsEventLibrary] {} is not a valid JSON object - not split"
                        + " into {}/, left in place", LEGACY_FILE_NAME, DEFAULT_EVENTS_DIR);
                return;
            }
            JsonObject root = rootEl.getAsJsonObject();

            JsonElement schedulerEl = root.get("scheduler");
            if (schedulerEl != null) {
                Path schedulerFile = newsDir.resolve(SCHEDULER_FILE_NAME);
                if (Files.exists(schedulerFile)) {
                    StockMarketMod.LOGGER.warn("[NewsEventLibrary] {} already exists - the scheduler block of {}"
                            + " was not copied (still in {})", SCHEDULER_FILE_NAME, LEGACY_FILE_NAME,
                            backup.getFileName());
                } else {
                    JsonObject wrapped = new JsonObject();
                    wrapped.add("scheduler", schedulerEl);
                    Files.writeString(schedulerFile, JsonUtilities.toPrettyString(wrapped), StandardCharsets.UTF_8);
                }
            }

            Files.createDirectories(eventsDir);
            int written = 0;
            JsonElement eventsEl = root.get("events");
            if (eventsEl != null && eventsEl.isJsonArray()) {
                Set<String> used = new HashSet<>();
                JsonArray events = eventsEl.getAsJsonArray();
                for (int i = 0; i < events.size(); i++) {
                    JsonElement el = events.get(i);
                    if (!el.isJsonObject()) {
                        StockMarketMod.LOGGER.warn("[NewsEventLibrary] {} events entry {} is not an object -"
                                + " not split (still in {})", LEGACY_FILE_NAME, i, backup.getFileName());
                        continue;
                    }
                    String name = uniqueFileName(idOf(el.getAsJsonObject()), i, used);
                    Files.writeString(eventsDir.resolve(name),
                            JsonUtilities.toPrettyString(el), StandardCharsets.UTF_8);
                    written++;
                }
            }
            Files.move(legacy, backup, StandardCopyOption.REPLACE_EXISTING);
            StockMarketMod.LOGGER.info("[NewsEventLibrary] Split {} into {} event file(s) in {}/ and {};"
                    + " the old file is kept as {}", LEGACY_FILE_NAME, written, DEFAULT_EVENTS_DIR,
                    SCHEDULER_FILE_NAME, backup.getFileName());
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Failed to split " + LEGACY_FILE_NAME
                    + " into " + DEFAULT_EVENTS_DIR + "/", e);
        }
    }

    /**
     * File name {@code <id>.json} for a split event: characters outside {@code [A-Za-z0-9_-]}
     * become {@code _} (also keeps the name from starting with a dot), a missing id gives
     * {@code event_<n>}, and a name already used (case-insensitive, for Windows) gets
     * {@code _2}, {@code _3}, ...
     */
    private static String uniqueFileName(@Nullable String id, int index, Set<String> used) {
        String base = id == null ? "" : id.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.isEmpty()) base = "event_" + (index + 1);
        String name = base;
        for (int n = 2; !used.add(name.toLowerCase(Locale.ROOT)); n++) {
            name = base + "_" + n;
        }
        return name + ".json";
    }

    /**
     * SM-338 upgrade of a file holding a {@code scheduler} block: {@link #SCHEDULER_FILE_NAME}
     * in the per-file layout, or the legacy {@code default_events.json} right before its split
     * (SM-343). Files written before 2.2 have no {@code impactDurationMultiplier} in their {@code scheduler} block. Such a file
     * gets the shipped multipliers ({@link NewsEventLibrary.SchedulerConfig#SHIPPED_IMPACT_DURATION_MULTIPLIER},
     * {@link NewsEventLibrary.SchedulerConfig#SHIPPED_IMPACT_STRENGTH_MULTIPLIER}) for each
     * one it lacks, and the new event interval only if both interval fields are still at the
     * old defaults (900 / 3600 s): an admin-tuned interval is kept. The added multiplier key
     * marks the file as migrated, so the step runs once and later admin edits (even back to
     * 900 / 3600) are never touched again. A missing file or scheduler block is left alone.
     * Never throws; a file that cannot be parsed is left unchanged.
     *
     * @param file the file to upgrade
     * @return true if the file was rewritten
     */
    public static boolean migrateOldSchedulerDefaults(Path file) {
        if (!Files.exists(file)) return false;
        try {
            JsonElement rootEl = JsonUtilities.fromString(Files.readString(file, StandardCharsets.UTF_8));
            if (rootEl == null || !rootEl.isJsonObject()) return false;
            JsonElement schedulerEl = rootEl.getAsJsonObject().get("scheduler");
            if (schedulerEl == null || !schedulerEl.isJsonObject()) return false;
            JsonObject scheduler = schedulerEl.getAsJsonObject();
            if (scheduler.has("impactDurationMultiplier")) return false; // already migrated
            scheduler.addProperty("impactDurationMultiplier",
                    NewsEventLibrary.SchedulerConfig.SHIPPED_IMPACT_DURATION_MULTIPLIER);
            if (!scheduler.has("impactStrengthMultiplier")) {
                scheduler.addProperty("impactStrengthMultiplier",
                        NewsEventLibrary.SchedulerConfig.SHIPPED_IMPACT_STRENGTH_MULTIPLIER);
            }
            if (isNumber(scheduler, "minSecondsBetweenEvents",
                    NewsEventLibrary.SchedulerConfig.OLD_DEFAULT_MIN_SECONDS_BETWEEN_EVENTS)
                    && isNumber(scheduler, "maxSecondsBetweenEvents",
                    NewsEventLibrary.SchedulerConfig.OLD_DEFAULT_MAX_SECONDS_BETWEEN_EVENTS)) {
                scheduler.addProperty("minSecondsBetweenEvents",
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MIN_SECONDS_BETWEEN_EVENTS);
                scheduler.addProperty("maxSecondsBetweenEvents",
                        NewsEventLibrary.SchedulerConfig.DEFAULT_MAX_SECONDS_BETWEEN_EVENTS);
            }
            Files.writeString(file, JsonUtilities.toPrettyString(rootEl), StandardCharsets.UTF_8);
            StockMarketMod.LOGGER.info("[NewsEventLibrary] Moved the news scheduler in {} to the 2.2 defaults"
                    + " (rarer, longer, weaker events)", file);
            return true;
        } catch (Exception e) {
            StockMarketMod.LOGGER.warn("[NewsEventLibrary] Could not migrate the scheduler block of {}"
                    + " - file left unchanged", file, e);
            return false;
        }
    }

    /**
     * SM-341 upgrade of the pre-2.2 {@code luxury_goods_tax} event, which has no
     * {@code requires}/{@code records} and could stack its permanent cut. Works on both
     * layouts: an {@code events}-array file (the legacy {@code default_events.json} right
     * before its split) or the single-event file {@code DefaultEvents/luxury_goods_tax.json}.
     * If the entry is still exactly an old shipped version (with or without the zh_cn
     * translations), it is replaced by the shipped one; an admin-edited entry is never
     * touched. Runs once: skipped as soon as {@code luxury_tax_repealed} exists (in the same
     * array file, or as {@code luxury_tax_repealed.json} next to the single-event file), which
     * the library's self-heal adds later in the same reload. Never throws; a file that cannot
     * be parsed is left unchanged.
     *
     * @param file the array file or the single-event {@code luxury_goods_tax.json}
     * @return true if the file was rewritten
     */
    public static boolean migrateOldLuxuryTax(Path file) {
        if (!Files.exists(file)) return false;
        try {
            JsonElement rootEl = JsonUtilities.fromString(Files.readString(file, StandardCharsets.UTF_8));
            if (rootEl == null || !rootEl.isJsonObject()) return false;
            JsonObject oldWithZh = JsonUtilities.fromString(OLD_LUXURY_GOODS_TAX_JSON).getAsJsonObject();
            JsonObject oldWithoutZh = oldWithZh.deepCopy();
            oldWithoutZh.getAsJsonObject("headline").remove("zh_cn");
            oldWithoutZh.getAsJsonObject("text").remove("zh_cn");
            JsonElement eventsEl = rootEl.getAsJsonObject().get("events");
            if (eventsEl == null) {
                // Per-file layout: the file is the event itself.
                if (Files.exists(file.resolveSibling("luxury_tax_repealed.json"))) return false;
                String shipped = EVENT_TEXTS.get("luxury_goods_tax");
                if (shipped == null || !(rootEl.equals(oldWithZh) || rootEl.equals(oldWithoutZh))) return false;
                Files.writeString(file, shipped, StandardCharsets.UTF_8);
                logLuxuryTaxMigrated(file);
                return true;
            }
            if (!eventsEl.isJsonArray()) return false;
            JsonArray events = eventsEl.getAsJsonArray();
            for (JsonElement el : events) {
                if (el.isJsonObject() && "luxury_tax_repealed".equals(idOf(el.getAsJsonObject()))) return false;
            }
            for (int i = 0; i < events.size(); i++) {
                JsonElement el = events.get(i);
                if (el.equals(oldWithZh) || el.equals(oldWithoutZh)) {
                    events.set(i, getDefaultEventObjectsById().get("luxury_goods_tax"));
                    Files.writeString(file, JsonUtilities.toPrettyString(rootEl), StandardCharsets.UTF_8);
                    logLuxuryTaxMigrated(file);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            StockMarketMod.LOGGER.warn("[NewsEventLibrary] Could not migrate luxury_goods_tax in {}"
                    + " - file left unchanged", file, e);
            return false;
        }
    }

    private static void logLuxuryTaxMigrated(Path file) {
        StockMarketMod.LOGGER.info("[NewsEventLibrary] Updated the unedited luxury_goods_tax"
                + " event in {} (fires once until repealed)", file);
    }

    /** @return the string {@code id} of an event object, or null */
    private static @Nullable String idOf(JsonObject event) {
        JsonElement idEl = event.get("id");
        return idEl != null && idEl.isJsonPrimitive() ? idEl.getAsString() : null;
    }

    /**
     * The {@code luxury_goods_tax} entry as shipped before SM-341 (2.0.4 .. 2.1.x without
     * zh_cn, 2.2 dev builds with it). Only {@link #migrateOldLuxuryTax} and its test use it.
     */
    public static final String OLD_LUXURY_GOODS_TAX_JSON = """
            {
              "id": "luxury_goods_tax",
              "picture": "luxury_goods_tax.png",
              "headline": {
                "en_us": "Council levies steep luxury tax on gems and gold",
                "zh_cn": "议会对宝石与黄金课以重税",
                "de_de": "Rat erhebt saftige Luxussteuer auf Edelsteine und Gold"
              },
              "text": {
                "en_us": "To fill the war chest the trade council has slapped a punishing luxury tax on every sale of diamonds, emeralds and gold. Buyers are balking at the new surcharge, demand for finery is cooling sharply, and analysts expect the chill on luxury prices to become a lasting fixture of the market.",
                "zh_cn": "为充实战备金库，贸易议会对钻石、绿宝石与黄金的每笔交易课以惩罚性奢侈税。买家面对新增费用望而却步，奢侈品需求急剧降温，分析师预计这轮寒潮将成为市场的长期特征。",
                "de_de": "Um die Kriegskasse zu füllen, hat der Handelsrat eine drückende Luxussteuer auf jeden Verkauf von Diamanten, Smaragden und Gold verhängt. Käufer schrecken vor dem neuen Aufschlag zurück, die Nachfrage nach Prunk kühlt merklich ab, und Analysten erwarten, dass die Preisdämpfung im Luxussegment zu einer dauerhaften Erscheinung am Markt wird."
              },
              "category": "politics",
              "weight": 2,
              "cooldownSeconds": 86400,
              "impact": {
                "type": "trend",
                "peakFactor": -0.2,
                "rampUpSeconds": 1200,
                "durationSeconds": 2700,
                "reversal": "none"
              },
              "markets": [
                { "item": "minecraft:diamond",    "weightFactor": 1.0 },
                { "item": "minecraft:emerald",    "weightFactor": 0.8 },
                { "item": "minecraft:gold_ingot", "weightFactor": 0.6 }
              ]
            }
            """;

    /** @return true if {@code json.key} is a number equal to {@code expected} */
    private static boolean isNumber(JsonObject json, String key, long expected) {
        JsonElement el = json.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()
                && el.getAsDouble() == expected;
    }

    /**
     * Fills {@link #EVENT_TEXTS} / {@link #schedulerText} (the per-file writes use them) and
     * assembles the shipped defaults as one {@code scheduler} + {@code events} JSON text
     * (the legacy {@code default_events.json} shape; used by tests, by
     * {@link #getDefaultEventObjectsById} and by the legacy luxury tax migration) from the jar resources under
     * {@link #RESOURCE_DIR}: {@link #SCHEDULER_RESOURCE} becomes the {@code scheduler} block
     * and each id of {@link #INDEX_RESOURCE} (in index order) pulls in
     * {@code events/<id>.json}, re-indented by four spaces into the {@code events} array.
     * The output layout is fixed and byte-identical to the pre-SM-342 default_events.json
     * text blocks. A missing or unparsable
     * event file is logged and skipped. Never throws.
     *
     * @return the assembled JSON text, or an empty string (logged) if the index or the
     *         scheduler resource is missing/unparsable or no event could be loaded
     */
    private static String loadResource() {
        try {
            String indexText = readResource(RESOURCE_DIR + INDEX_RESOURCE);
            String scheduler = readResource(RESOURCE_DIR + SCHEDULER_RESOURCE);
            if (indexText == null || scheduler == null) return "";
            JsonParser.parseString(scheduler).getAsJsonObject(); // validate before shipping it
            StringBuilder events = new StringBuilder();
            for (JsonElement idEl : JsonParser.parseString(indexText).getAsJsonArray()) {
                String path = RESOURCE_DIR + EVENTS_RESOURCE_DIR + idEl.getAsString() + ".json";
                String eventText = readResource(path);
                if (eventText == null) continue;
                try {
                    JsonParser.parseString(eventText).getAsJsonObject();
                } catch (Exception e) {
                    StockMarketMod.LOGGER.error("[NewsEventLibrary] Skipping unparsable default news event "
                            + path, e);
                    continue;
                }
                EVENT_TEXTS.put(idEl.getAsString(), eventText);
                if (!events.isEmpty()) events.append(",\n");
                events.append(indent(eventText, "    "));
            }
            if (events.isEmpty()) {
                StockMarketMod.LOGGER.error("[NewsEventLibrary] No default news event could be loaded from {}"
                        + " - running without default news events", RESOURCE_DIR);
                return "";
            }
            schedulerText = scheduler;
            return "{\n  \"scheduler\": " + indent(scheduler, "  ").substring(2)
                    + ",\n  \"events\": [\n" + events + "\n  ]\n}\n";
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Could not parse the default news resources in "
                    + RESOURCE_DIR + " - running without default news events", e);
            return "";
        }
    }

    /**
     * Reads a jar resource through the classloader (works on every loader, like
     * {@link DefaultNewsPictures}). CRLF is folded to LF so a Windows checkout with
     * autocrlf still yields the exact shipped bytes. Never throws.
     *
     * @param path absolute classpath path, e.g. {@code /news/index.json}
     * @return the resource text, or null (logged as an error) if it is missing or unreadable
     */
    public static @Nullable String readResource(String path) {
        try (InputStream in = DefaultNewsEvents.class.getResourceAsStream(path)) {
            if (in == null) {
                StockMarketMod.LOGGER.error("[NewsEventLibrary] Missing default news resource {}", path);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Could not read default news resource " + path, e);
            return null;
        }
    }

    /** @return {@code text} without trailing newlines, every line prefixed with {@code prefix} */
    private static String indent(String text, String prefix) {
        return prefix + text.stripTrailing().replace("\n", "\n" + prefix);
    }

}
