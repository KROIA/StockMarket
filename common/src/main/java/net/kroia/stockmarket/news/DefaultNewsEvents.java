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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ships the default {@code default_events.json} that {@link NewsEventLibrary} writes into
 * {@code config/StockMarket/news/} when the folder is empty on first load.
 * <p>
 * The file doubles as <b>admin documentation</b>: it demonstrates every schema feature —
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
 * {@code index.json} (ordered id list; a new default event needs an entry there). They are
 * assembled at runtime into the single config file, see {@link #loadResource}.
 * <p>
 * <b>Additive self-healing (existing installs):</b> on every reload
 * {@link NewsEventLibrary} tops up an existing {@code default_events.json} — any shipped
 * default whose id is present in <b>no</b> loaded {@code .json} is parsed into the live
 * pool and appended to the file by {@link #appendMissingToFile}. Existing entries
 * (including admin-customized defaults) and the {@code scheduler} block are never modified
 * or overwritten (exceptions: the one-time SM-338 scheduler upgrade and the SM-341 luxury tax upgrade,
 * {@link #migrateOldSchedulerDefaults}, {@link #migrateOldLuxuryTax}), so a server that predates newly shipped events receives them without
 * losing its edits. The full file is written from scratch only on the first run (empty
 * folder, see {@link #writeDefaultFile}); a folder that has no {@code default_events.json}
 * is left untouched. The default pictures are healed independently and per id into
 * {@code pictures/} (see {@link DefaultNewsPictures#extractMissingDefaults}).
 */
public final class DefaultNewsEvents {

    /** File name of the generated defaults file. */
    public static final String DEFAULT_FILE_NAME = "default_events.json";

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
    private static final String DEFAULT_EVENTS_JSON = loadResource();

    /**
     * The ids of all shipped default events, in file order. Each id also names its
     * default picture ({@code <eventId>.png}) extracted by {@link DefaultNewsPictures}.
     */
    public static final List<String> DEFAULT_EVENT_IDS =
            List.copyOf(getDefaultEventObjectsById().keySet());

    private DefaultNewsEvents() {
    }

    /**
     * Writes {@code default_events.json} into the given news config directory.
     * Never throws — failures are logged and reported via the return value.
     *
     * @param newsDir the news config directory (must already exist)
     * @return true if the file was written successfully
     */
    public static boolean writeDefaultFile(Path newsDir) {
        if (DEFAULT_EVENT_IDS.isEmpty()) {
            // Resource missing or unparsable (already logged): never write an empty/broken file.
            StockMarketMod.LOGGER.error("[NewsEventLibrary] No shipped default news events available -"
                    + " {} not generated", DEFAULT_FILE_NAME);
            return false;
        }
        try {
            Path file = newsDir.resolve(DEFAULT_FILE_NAME);
            Files.writeString(file, DEFAULT_EVENTS_JSON, StandardCharsets.UTF_8);
            StockMarketMod.LOGGER.info("[NewsEventLibrary] Generated default news events file: {}", file);
            return true;
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Failed to write default news events file", e);
            return false;
        }
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
     * into the live pool through the normal parse path and (b) append its raw JSON object
     * to {@code default_events.json}. Never throws.
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
     * Additively persists the given shipped-default event ids into {@code default_events.json}
     * — merging, never overwriting (self-healing, complements {@link #writeDefaultFile}):
     * <ul>
     *   <li>if the file exists it is parsed and the missing default event objects are
     *       <b>appended</b> to its {@code events} array; the existing {@code scheduler}
     *       block and every existing event entry are preserved untouched (an admin-modified
     *       default is never overwritten);</li>
     *   <li>if the file does not exist it is created with the shipped {@code scheduler}
     *       block plus the missing events;</li>
     *   <li>an id already present in the file's {@code events} array is skipped, so the
     *       merge can never introduce a duplicate-id entry.</li>
     * </ul>
     * Never throws — on any I/O or parse failure it logs and returns false; the caller keeps
     * its in-memory additions regardless (never-crash contract). A file that exists but
     * cannot be parsed is left <b>unchanged</b> so an admin's broken edit is never clobbered.
     *
     * @param newsDir    the news config directory (must already exist)
     * @param missingIds the shipped-default ids to append (verified absent from the whole pool)
     * @return true if the file was (re)written with at least one appended event
     */
    public static boolean appendMissingToFile(Path newsDir, Collection<String> missingIds) {
        Path file = newsDir.resolve(DEFAULT_FILE_NAME);
        try {
            Map<String, JsonObject> shipped = getDefaultEventObjectsById();
            JsonObject root;
            if (Files.exists(file)) {
                JsonElement existing;
                try {
                    existing = JsonUtilities.fromString(
                            Files.readString(file, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    StockMarketMod.LOGGER.warn("[NewsEventLibrary] Could not parse {} to append"
                            + " missing default event(s) — file left unchanged, in-memory heal kept",
                            DEFAULT_FILE_NAME, e);
                    return false;
                }
                if (existing == null || !existing.isJsonObject()) {
                    StockMarketMod.LOGGER.warn("[NewsEventLibrary] {} is not a JSON object —"
                            + " file left unchanged, in-memory heal kept", DEFAULT_FILE_NAME);
                    return false;
                }
                root = existing.getAsJsonObject();
            } else {
                // Fresh file: seed it with the shipped scheduler block, then the missing events.
                root = new JsonObject();
                JsonObject shippedRoot = parseDefaultRoot();
                if (shippedRoot != null && shippedRoot.has("scheduler")) {
                    root.add("scheduler", shippedRoot.get("scheduler"));
                }
            }

            JsonArray events;
            JsonElement eventsEl = root.get("events");
            if (eventsEl != null && eventsEl.isJsonArray()) {
                events = eventsEl.getAsJsonArray();
            } else {
                events = new JsonArray();
                root.add("events", events);
            }

            // Never duplicate: skip any id already present in the file's events array.
            Set<String> presentIds = new HashSet<>();
            for (JsonElement el : events) {
                if (el.isJsonObject()) {
                    JsonElement idEl = el.getAsJsonObject().get("id");
                    if (idEl != null && idEl.isJsonPrimitive()) presentIds.add(idEl.getAsString());
                }
            }

            int appended = 0;
            for (String id : missingIds) {
                if (presentIds.contains(id)) continue;
                JsonObject obj = shipped.get(id);
                if (obj == null) continue;
                events.add(obj);
                appended++;
            }
            if (appended == 0) return false;

            Files.writeString(file, JsonUtilities.toPrettyString(root), StandardCharsets.UTF_8);
            StockMarketMod.LOGGER.info(
                    "[NewsEventLibrary] Appended {} missing default news event(s) to {}",
                    appended, file);
            return true;
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsEventLibrary] Failed to append missing default"
                    + " news events to " + DEFAULT_FILE_NAME + " — in-memory heal kept", e);
            return false;
        }
    }

    /**
     * SM-338 upgrade of an existing {@code default_events.json}: files written before 2.2
     * have no {@code impactDurationMultiplier} in their {@code scheduler} block. Such a file
     * gets the shipped multipliers ({@link NewsEventLibrary.SchedulerConfig#SHIPPED_IMPACT_DURATION_MULTIPLIER},
     * {@link NewsEventLibrary.SchedulerConfig#SHIPPED_IMPACT_STRENGTH_MULTIPLIER}) for each
     * one it lacks, and the new event interval only if both interval fields are still at the
     * old defaults (900 / 3600 s): an admin-tuned interval is kept. The added multiplier key
     * marks the file as migrated, so the step runs once and later admin edits (even back to
     * 900 / 3600) are never touched again. A missing file or scheduler block is left alone.
     * Never throws; a file that cannot be parsed is left unchanged.
     *
     * @param newsDir the news config directory
     * @return true if the file was rewritten
     */
    public static boolean migrateOldSchedulerDefaults(Path newsDir) {
        Path file = newsDir.resolve(DEFAULT_FILE_NAME);
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
                    + " - file left unchanged", DEFAULT_FILE_NAME, e);
            return false;
        }
    }

    /**
     * SM-341 upgrade of an existing {@code default_events.json}: the pre-2.2
     * {@code luxury_goods_tax} entry has no {@code requires}/{@code records}, so it could
     * stack its permanent cut. If the file's entry is still exactly an old shipped version
     * (with or without the zh_cn translations), it is replaced by the shipped one; an
     * admin-edited entry is never touched. Runs once: the step is skipped as soon as the
     * file holds {@code luxury_tax_repealed}, which the library's self-heal appends later
     * in the same reload. Never throws; a file that cannot be parsed is left unchanged.
     *
     * @param newsDir the news config directory
     * @return true if the file was rewritten
     */
    public static boolean migrateOldLuxuryTax(Path newsDir) {
        Path file = newsDir.resolve(DEFAULT_FILE_NAME);
        if (!Files.exists(file)) return false;
        try {
            JsonElement rootEl = JsonUtilities.fromString(Files.readString(file, StandardCharsets.UTF_8));
            if (rootEl == null || !rootEl.isJsonObject()) return false;
            JsonElement eventsEl = rootEl.getAsJsonObject().get("events");
            if (eventsEl == null || !eventsEl.isJsonArray()) return false;
            JsonArray events = eventsEl.getAsJsonArray();
            for (JsonElement el : events) {
                if (el.isJsonObject() && "luxury_tax_repealed".equals(idOf(el.getAsJsonObject()))) return false;
            }
            JsonObject oldWithZh = JsonUtilities.fromString(OLD_LUXURY_GOODS_TAX_JSON).getAsJsonObject();
            JsonObject oldWithoutZh = oldWithZh.deepCopy();
            oldWithoutZh.getAsJsonObject("headline").remove("zh_cn");
            oldWithoutZh.getAsJsonObject("text").remove("zh_cn");
            for (int i = 0; i < events.size(); i++) {
                JsonElement el = events.get(i);
                if (el.equals(oldWithZh) || el.equals(oldWithoutZh)) {
                    events.set(i, getDefaultEventObjectsById().get("luxury_goods_tax"));
                    Files.writeString(file, JsonUtilities.toPrettyString(rootEl), StandardCharsets.UTF_8);
                    StockMarketMod.LOGGER.info("[NewsEventLibrary] Updated the unedited luxury_goods_tax"
                            + " event in {} (fires once until repealed)", file);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            StockMarketMod.LOGGER.warn("[NewsEventLibrary] Could not migrate luxury_goods_tax in {}"
                    + " - file left unchanged", DEFAULT_FILE_NAME, e);
            return false;
        }
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
     * Assembles the shipped {@code default_events.json} text from the jar resources under
     * {@link #RESOURCE_DIR}: {@link #SCHEDULER_RESOURCE} becomes the {@code scheduler} block
     * and each id of {@link #INDEX_RESOURCE} (in index order) pulls in
     * {@code events/<id>.json}, re-indented by four spaces into the {@code events} array.
     * The output layout is fixed, so the written file stays byte-identical to the pre-SM-342
     * text blocks (the migrations and "unedited" checks rely on it). A missing or unparsable
     * event file is logged and skipped. Never throws.
     *
     * @return the assembled JSON text, or an empty string (logged) if the index or the
     *         scheduler resource is missing/unparsable or no event could be loaded
     */
    private static String loadResource() {
        try {
            String indexText = readResource(RESOURCE_DIR + INDEX_RESOURCE);
            String schedulerText = readResource(RESOURCE_DIR + SCHEDULER_RESOURCE);
            if (indexText == null || schedulerText == null) return "";
            JsonParser.parseString(schedulerText).getAsJsonObject(); // validate before shipping it
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
                if (!events.isEmpty()) events.append(",\n");
                events.append(indent(eventText, "    "));
            }
            if (events.isEmpty()) {
                StockMarketMod.LOGGER.error("[NewsEventLibrary] No default news event could be loaded from {}"
                        + " - running without default news events", RESOURCE_DIR);
                return "";
            }
            return "{\n  \"scheduler\": " + indent(schedulerText, "  ").substring(2)
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
