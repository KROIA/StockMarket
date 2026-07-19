package net.kroia.stockmarket.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
 * {@code reversal: "none"} (permanent shift), inline {@code en_us}/{@code de_de}
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
 * <b>Additive self-healing (existing installs):</b> on every reload
 * {@link NewsEventLibrary} tops up an existing {@code default_events.json} — any shipped
 * default whose id is present in <b>no</b> loaded {@code .json} is parsed into the live
 * pool and appended to the file by {@link #appendMissingToFile}. Existing entries
 * (including admin-customized defaults) and the {@code scheduler} block are never modified
 * or overwritten, so a server that predates newly shipped events receives them without
 * losing its edits. The full file is written from scratch only on the first run (empty
 * folder, see {@link #writeDefaultFile}); a folder that has no {@code default_events.json}
 * is left untouched. The default pictures are healed independently and per id into
 * {@code pictures/} (see {@link DefaultNewsPictures#extractMissingDefaults}).
 */
public final class DefaultNewsEvents {

    /** File name of the generated defaults file. */
    public static final String DEFAULT_FILE_NAME = "default_events.json";

    /**
     * The ids of all shipped default events, in file order. Each id also names its
     * default picture ({@code <eventId>.png}) extracted by {@link DefaultNewsPictures}.
     */
    public static final List<String> DEFAULT_EVENT_IDS = List.of(
            "diamond_rush",
            "iron_supply_disruption",
            "ore_market_crash",
            "gold_reserve_standard",
            "gold_rush_rumor",
            "end_of_gold_standard",
            "emerald_counterfeit_scandal",
            "netherite_insider_leak",
            "redstone_breakthrough",
            "lumber_construction_boom",
            // Broader-theme defaults: agriculture, mining, natural disasters, politics,
            // cultural/seasonal, warfare. Adds two more chains, two more requires demos,
            // one more adminOnly event, plus two additional sequences-authored events.
            "wheat_harvest_festival",
            "crop_pest_outbreak",
            "honey_bumper_season",
            "copper_seam_discovery",
            "coal_miners_strike",
            "volcanic_eruption",
            "great_forest_fire",
            "great_forest_fire_recovery",
            "import_tariff_treaty",
            "winter_solstice_gifting",
            "post_solstice_slump",
            "redstone_arms_race",
            // 50 new events (2026-07-19): mining, agriculture, livestock/fishing, nether/end, trade/politics
            "nether_quartz_glut",
            "amethyst_geode_discovery",
            "lapis_enchanting_craze",
            "glowstone_farm_boom",
            "deepslate_quarry_expansion",
            "prismarine_monument_raid",
            "quartz_cartel_squeeze",
            "dripstone_cavern_rush",
            "tuff_facade_fashion",
            "amethyst_speculation",
            "potato_blight_return",
            "pumpkin_autumn_festival",
            "melon_summer_glut",
            "sugarcane_plantation_boom",
            "cocoa_trade_route_opens",
            "mushroom_foraging_craze",
            "netherwart_brewing_shortage",
            "beetroot_harvest_surplus",
            "great_drought",
            "grain_relief_convoy",
            "spring_shearing_glut",
            "leather_tannery_boom",
            "cattle_plague_outbreak",
            "salmon_spawning_run",
            "cod_overfishing_crisis",
            "tropical_fish_aquarium_fad",
            "henhouse_fire_egg_shortage",
            "fletcher_feather_demand",
            "ink_dye_market_crash",
            "rabbit_stew_winter_craze",
            "blaze_rod_brewing_rush",
            "ender_pearl_expedition_boom",
            "ghast_tear_apothecary_craze",
            "shulker_shell_shortage",
            "chorus_fruit_bumper_harvest",
            "magma_cream_alchemy_demand",
            "phantom_membrane_repair_run",
            "end_expedition_returns",
            "crying_obsidian_anchor_fad",
            "wither_skull_bounty",
            "mint_recoinage_reform",
            "dockworkers_strike",
            "metals_export_embargo",
            "treasury_stimulus_windfall",
            "counterfeit_coin_scandal",
            "gemstone_heist",
            "royal_purple_fashion",
            "luxury_goods_tax",
            "speculation_mania",
            "margin_call_crash");

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
     * Parses the shipped defaults JSON constant into its root object
     * ({@code scheduler} + {@code events}). Never throws.
     *
     * @return the parsed root object, or null if the shipped constant somehow fails
     *         to parse (never happens in practice — the test suite validates it)
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
     *         (empty only if the shipped constant somehow fails to parse)
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

    // The shipped defaults exceed the 65535-byte JVM limit for a single String
    // constant, so the JSON is stored as four text-block chunks and joined at
    // RUNTIME (String.join, not '+') to avoid javac re-folding them into one
    // oversized constant. Each chunk breaks only between two complete event
    // objects inside the "events" array; concatenated they reproduce the exact
    // original JSON. Part boundaries (last event id per part):
    //   part 1: diamond_rush .. great_forest_fire_recovery (18 events, incl. scheduler)
    //   part 2: import_tariff_treaty .. sugarcane_plantation_boom (18 events)
    //   part 3: cocoa_trade_route_opens .. ender_pearl_expedition_boom (18 events)
    //   part 4: ghast_tear_apothecary_craze .. margin_call_crash (18 events)
    private static final String DEFAULT_EVENTS_JSON_1 = """
            {
              "scheduler": {
                "minSecondsBetweenEvents": 900,
                "maxSecondsBetweenEvents": 3600,
                "maxActiveEventsGlobal": 3,
                "maxActiveEventsPerMarket": 1,
                "historyMaxEntries": 500
              },
              "events": [
                {
                  "id": "diamond_rush",
                  "picture": "diamond_rush.png",
                  "headline": {
                    "en_us": "Diamond rush in the northern mountains!",
                    "de_de": "Diamantenrausch in den n\\u00f6rdlichen Bergen!"
                  },
                  "text": {
                    "en_us": "Prospectors report huge diamond veins in the northern mountains. Experts expect supply to surge within days, putting pressure on prices across the ore market. Emerald traders are already rerouting caravans towards the new claims.",
                    "de_de": "Sch\\u00fcrfer melden riesige Diamantvorkommen in den n\\u00f6rdlichen Bergen. Experten erwarten, dass das Angebot innerhalb weniger Tage stark steigt und die Preise am Erzmarkt unter Druck geraten. Smaragdh\\u00e4ndler leiten ihre Karawanen bereits zu den neuen Claims um."
                  },
                  "category": "commodities",
                  "weight": 10,
                  "cooldownSeconds": 7200,
                  "adminOnly": false,
                  "announceDelayMs": { "min": 0, "max": 60000 },
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.35,
                    "rampUpSeconds": 600,
                    "durationSeconds": 3000,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.05
                  },
                  "markets": [
                    { "item": "minecraft:diamond",       "weightFactor": 1.0 },
                    { "item": "minecraft:diamond_block", "weightFactor": 1.0 },
                    { "item": "#c:ores",                 "weightFactor": 0.3 },
                    { "item": "minecraft:emerald",       "weightFactor": -0.4 }
                  ]
                },
                {
                  "id": "iron_supply_disruption",
                  "picture": "iron_supply_disruption.png",
                  "headline": {
                    "en_us": "Mine collapse halts iron production!",
                    "de_de": "Minenungl\\u00fcck stoppt die Eisenproduktion!"
                  },
                  "text": {
                    "en_us": "A major mine collapse has cut off several iron shafts. Smelteries warn of delivery delays and traders are stockpiling ingots while repairs are underway.",
                    "de_de": "Ein schwerer Mineneinsturz hat mehrere Eisensch\\u00e4chte abgeschnitten. H\\u00fctten warnen vor Lieferverz\\u00f6gerungen und H\\u00e4ndler horten Barren, w\\u00e4hrend die Reparaturen laufen."
                  },
                  "category": "commodities",
                  "weight": 12,
                  "cooldownSeconds": 5400,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.45,
                    "durationSeconds": 1200,
                    "reversalSeconds": 2100
                  },
                  "markets": [
                    { "item": "minecraft:iron_ingot", "weightFactor": 1.0 },
                    { "item": "minecraft:iron_block", "weightFactor": 1.0 },
                    { "item": "minecraft:raw_iron",   "weightFactor": 0.8 }
                  ]
                },
                {
                  "id": "ore_market_crash",
                  "picture": "ore_market_crash.png",
                  "headline": {
                    "en_us": "Ore bubble bursts - mining stocks in free fall!",
                    "de_de": "Erzblase geplatzt - Bergbauwerte im freien Fall!"
                  },
                  "text": {
                    "en_us": "After months of speculation the ore bubble has finally burst. Panic selling grips every ore market as overleveraged traders liquidate their positions. Analysts expect a slow recovery.",
                    "de_de": "Nach Monaten der Spekulation ist die Erzblase endlich geplatzt. Panikverk\\u00e4ufe erfassen alle Erzm\\u00e4rkte, w\\u00e4hrend \\u00fcberhebelte H\\u00e4ndler ihre Positionen aufl\\u00f6sen. Analysten erwarten eine langsame Erholung."
                  },
                  "category": "markets",
                  "weight": 4,
                  "cooldownSeconds": 21600,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.5,
                    "durationSeconds": 900,
                    "reversal": "exponential",
                    "reversalSeconds": 6000
                  },
                  "markets": [
                    { "item": "minecraft:*_ore", "weightFactor": 1.0 },
                    { "item": "minecraft:raw_*", "weightFactor": 0.7 }
                  ]
                },
                {
                  "id": "gold_reserve_standard",
                  "picture": "gold_reserve_standard.png",
                  "headline": {
                    "en_us": "Trade council adopts the gold standard!",
                    "de_de": "Handelsrat f\\u00fchrt den Goldstandard ein!"
                  },
                  "text": {
                    "en_us": "The trade council has voted to back all inter-village trade with gold reserves. Demand for gold is expected to remain permanently elevated - a structural shift, not a passing rally.",
                    "de_de": "Der Handelsrat hat beschlossen, den gesamten Handel zwischen den D\\u00f6rfern mit Goldreserven zu decken. Die Goldnachfrage d\\u00fcrfte dauerhaft erh\\u00f6ht bleiben - ein struktureller Wandel, keine kurzfristige Rally."
                  },
                  "category": "politics",
                  "weight": 2,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.25,
                    "rampUpSeconds": 1500,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    { "item": "minecraft:gold_ingot", "weightFactor": 1.0 }
                  ],
                  "requires": [
                    { "type": "notFired", "eventId": "gold_reserve_standard" }
                  ],
                  "records": { "era": "gold_standard" },
                  "chains": [
                    { "eventId": "gold_rush_rumor", "on": "step", "step": "hold", "chance": 0.5, "delaySeconds": { "min": 300, "max": 900 } }
                  ]
                },
                {
                  "id": "gold_rush_rumor",
                  "picture": "gold_rush_rumor.png",
                  "headline": {
                    "en_us": "Rumors of a massive gold strike sweep the trading floor!",
                    "de_de": "Ger\\u00fcchte \\u00fcber riesigen Goldfund fegen \\u00fcber das Parkett!"
                  },
                  "text": {
                    "en_us": "Whispers of an enormous gold vein have traders piling into gold. Nobody has actually seen the nugget yet - seasoned brokers warn that rumors like this often collapse as fast as they spread, punishing whoever buys the top.",
                    "de_de": "Gefl\\u00fcster \\u00fcber eine gewaltige Goldader treibt die H\\u00e4ndler in den Goldmarkt. Gesehen hat den Fund bisher niemand - erfahrene Makler warnen, dass solche Ger\\u00fcchte oft so schnell zusammenbrechen, wie sie sich verbreiten, und den Letzten bei\\u00dfen die Hunde."
                  },
                  "category": "rumors",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "sequences": [
                    {
                      "name": "pump_and_dump",
                      "weight": 2,
                      "steps": [
                        { "name": "hype",     "durationSeconds": { "min": 450, "max": 1200 }, "targetFactor": 0.4,   "curve": "linear",      "noise": 0.05 },
                        { "name": "peak",     "durationSeconds": { "min": 300, "max": 600 },                        "curve": "hold",        "noise": 0.08 },
                        { "name": "sell_off", "durationSeconds": { "min": 100, "max": 300 },  "targetFactor": -0.15, "curve": "exponential", "noise": 0.04,
                          "markets": [
                            { "item": "minecraft:gold_ingot", "weightFactor": 1.0 },
                            { "item": "minecraft:raw_gold",   "weightFactor": 0.6 }
                          ] },
                        { "name": "recover",  "durationSeconds": 1500, "targetFactor": 0.0, "curve": "linear" }
                      ]
                    },
                    {
                      "name": "fizzle",
                      "weight": 1,
                      "steps": [
                        { "name": "stir",   "durationSeconds": { "min": 300, "max": 600 }, "targetFactor": 0.15, "curve": "linear", "noise": 0.04 },
                        { "name": "denial", "durationSeconds": { "min": 300, "max": 900 }, "targetFactor": 0.0,  "curve": "exponential" }
                      ]
                    }
                  ],
                  "markets": [
                    { "item": "minecraft:gold_ingot", "weightFactor": 1.0 }
                  ]
                },
                {
                  "id": "end_of_gold_standard",
                  "picture": "end_of_gold_standard.png",
                  "headline": {
                    "en_us": "Trade council abandons the gold standard!",
                    "de_de": "Handelsrat schafft den Goldstandard ab!"
                  },
                  "text": {
                    "en_us": "After years of gold-backed trade the council has voted to untie inter-village commerce from its gold reserves. The structural demand that propped up the gold price is gone, and analysts expect the old premium to unwind for good.",
                    "de_de": "Nach Jahren goldgedeckten Handels hat der Rat beschlossen, den Handel zwischen den D\\u00f6rfern von den Goldreserven zu l\\u00f6sen. Die strukturelle Nachfrage, die den Goldpreis st\\u00fctzte, ist damit Geschichte, und Analysten erwarten, dass sich der alte Aufschlag dauerhaft abbaut."
                  },
                  "category": "politics",
                  "weight": 2,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.2,
                    "rampUpSeconds": 300,
                    "durationSeconds": 1500,
                    "reversal": "none"
                  },
                  "markets": [
                    { "item": "minecraft:gold_ingot", "weightFactor": 1.0 }
                  ],
                  "requires": [
                    { "type": "firedBefore", "eventId": "gold_reserve_standard", "minSecondsAgo": 10800 },
                    { "type": "notFired",    "eventId": "end_of_gold_standard" },
                    { "type": "keyEquals",   "key": "era", "value": "gold_standard" }
                  ],
                  "records": { "era": "fiat" }
                },
                {
                  "id": "emerald_counterfeit_scandal",
                  "picture": "emerald_counterfeit_scandal.png",
                  "headline": {
                    "en_us": "Counterfeit emeralds flood the market!",
                    "de_de": "Gef\\u00e4lschte Smaragde \\u00fcberschwemmen den Markt!"
                  },
                  "text": {
                    "en_us": "Inspectors have uncovered a large-scale counterfeiting ring. Confidence in emerald certificates has collapsed and villagers are refusing payment in gems until audits conclude.",
                    "de_de": "Inspektoren haben einen gro\\u00df angelegten F\\u00e4lscherring aufgedeckt. Das Vertrauen in Smaragdzertifikate ist eingebrochen, und Dorfbewohner verweigern Edelstein-Zahlungen, bis die Pr\\u00fcfungen abgeschlossen sind."
                  },
                  "category": "crime",
                  "weight": 5,
                  "cooldownSeconds": 14400,
                  "adminOnly": true,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.4,
                    "reversalSeconds": 4500
                  },
                  "markets": [
                    { "item": "minecraft:emerald",       "weightFactor": 1.0 },
                    { "item": "minecraft:emerald_block", "weightFactor": 1.0 }
                  ]
                },
                {
                  "id": "netherite_insider_leak",
                  "picture": "netherite_insider_leak.png",
                  "headline": {
                    "en_us": "Leaked: expedition secures massive ancient debris hoard",
                    "de_de": "Geleakt: Expedition sichert riesigen Fund an antikem Schrott"
                  },
                  "text": {
                    "en_us": "Documents leaked tonight reveal that a private expedition already secured a massive hoard of ancient debris days ago - and quietly sold ahead of the announcement. The market moved before the public ever knew.",
                    "de_de": "Heute Nacht geleakte Dokumente zeigen, dass eine private Expedition bereits vor Tagen einen riesigen Fund an antikem Schrott gesichert hat - und noch vor der Bekanntgabe still verkauft hat. Der Markt bewegte sich, bevor die \\u00d6ffentlichkeit davon erfuhr."
                  },
                  "category": "commodities",
                  "weight": 3,
                  "cooldownSeconds": 28800,
                  "announceDelayMs": { "min": -45000, "max": -15000 },
                  "impact": {
                    "type": "shock",
                    "peakFactor": -0.3,
                    "durationSeconds": 1500,
                    "reversalSeconds": 3000
                  },
                  "markets": [
                    { "item": "minecraft:netherite_ingot", "weightFactor": 1.0 },
                    { "item": "minecraft:netherite_scrap", "weightFactor": 0.8 },
                    { "item": "minecraft:ancient_debris",  "weightFactor": 0.8 }
                  ]
                },
                {
                  "id": "redstone_breakthrough",
                  "picture": "redstone_breakthrough.png",
                  "headline": {
                    "en_us": "Engineering breakthrough doubles redstone efficiency!",
                    "de_de": "Technischer Durchbruch verdoppelt Redstone-Effizienz!"
                  },
                  "text": {
                    "en_us": "A guild of engineers has published a compact circuit design that halves the redstone needed for common contraptions. Workshops across the land are ordering dust in bulk to retool.",
                    "de_de": "Eine Ingenieursgilde hat ein kompaktes Schaltungsdesign ver\\u00f6ffentlicht, das den Redstone-Bedarf g\\u00e4ngiger Konstruktionen halbiert. Werkst\\u00e4tten im ganzen Land bestellen Staub in gro\\u00dfen Mengen f\\u00fcr die Umr\\u00fcstung."
                  },
                  "category": "technology",
                  "weight": 8,
                  "cooldownSeconds": 10800,
                  "announceDelayMs": { "min": 15000, "max": 90000 },
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 3000
                  },
                  "markets": [
                    { "item": "minecraft:redstone", "weightFactor": 1.0 }
                  ]
                },
                {
                  "id": "lumber_construction_boom",
                  "picture": "lumber_construction_boom.png",
                  "headline": {
                    "en_us": "Construction boom drives lumber demand to record highs",
                    "de_de": "Bauboom treibt Holznachfrage auf Rekordh\\u00f6hen"
                  },
                  "text": {
                    "en_us": "Villages everywhere are expanding and carpenters cannot keep up. Sawmills report record order books, lifting prices across every kind of log while stone suppliers see orders quietly slip away.",
                    "de_de": "\\u00dcberall wachsen die D\\u00f6rfer, und die Zimmerleute kommen nicht hinterher. S\\u00e4gewerke melden Rekordauftr\\u00e4ge, was die Preise f\\u00fcr alle Holzarten steigen l\\u00e4sst, w\\u00e4hrend Steinlieferanten stillschweigend Auftr\\u00e4ge verlieren."
                  },
                  "category": "economy",
                  "weight": 9,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.35,
                    "rampUpSeconds": 750,
                    "durationSeconds": 2250,
                    "reversal": "ramp",
                    "reversalSeconds": 2250,
                    "noise": 0.03
                  },
                  "markets": [
                    { "item": "#minecraft:logs",     "weightFactor": 1.0 },
                    { "item": "minecraft:*_planks",  "weightFactor": 0.6 },
                    { "item": "minecraft:stone",     "weightFactor": -0.15 }
                  ]
                },
                {
                  "id": "wheat_harvest_festival",
                  "picture": "wheat_harvest_festival.png",
                  "headline": {
                    "en_us": "Harvest festival drives up grain demand!",
                    "de_de": "Erntefest treibt die Getreidenachfrage in die H\\u00f6he!"
                  },
                  "text": {
                    "en_us": "Villages across the land are preparing for the annual harvest festival. Bakeries stockpile flour, brewers order barley in bulk, and prices for wheat and bread climb ahead of the celebrations.",
                    "de_de": "\\u00dcberall bereiten sich die D\\u00f6rfer auf das j\\u00e4hrliche Erntefest vor. B\\u00e4ckereien horten Mehl, Brauer bestellen Gerste in gro\\u00dfen Mengen, und die Preise f\\u00fcr Weizen und Brot steigen vor den Feierlichkeiten."
                  },
                  "category": "commodities",
                  "weight": 5,
                  "cooldownSeconds": 5400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.22,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    { "item": "minecraft:wheat", "weightFactor": 1.0 },
                    { "item": "minecraft:bread", "weightFactor": 0.6 }
                  ]
                },
                {
                  "id": "crop_pest_outbreak",
                  "picture": "crop_pest_outbreak.png",
                  "headline": {
                    "en_us": "Locust swarm ravages farmland - staple crops in short supply!",
                    "de_de": "Heuschreckenschwarm verw\\u00fcstet Felder - Grundnahrungsmittel werden knapp!"
                  },
                  "text": {
                    "en_us": "A massive locust swarm has descended on the central farmlands, devouring rows of wheat, potatoes, and carrots. With the fall harvest threatened, prices for staple crops are spiking as households and merchants scramble to secure supplies.",
                    "de_de": "Ein gewaltiger Heuschreckenschwarm hat sich \\u00fcber die zentralen Felder hergemacht und Weizen, Kartoffeln und Karotten kahlgefressen. Die Herbsternte steht auf der Kippe, und die Preise f\\u00fcr Grundnahrungsmittel schie\\u00dfen nach oben, w\\u00e4hrend Haushalte und H\\u00e4ndler ihre Vorr\\u00e4te sichern."
                  },
                  "category": "disaster",
                  "weight": 4,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.38,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.05
                  },
                  "markets": [
                    { "item": "minecraft:wheat",  "weightFactor": 1.0 },
                    { "item": "minecraft:potato", "weightFactor": 0.9 },
                    { "item": "minecraft:carrot", "weightFactor": 0.9 },
                    { "item": "minecraft:bread",  "weightFactor": 0.5 }
                  ]
                },
                {
                  "id": "honey_bumper_season",
                  "picture": "honey_bumper_season.png",
                  "headline": {
                    "en_us": "Bee colonies boom - honey floods the market",
                    "de_de": "Bienenv\\u00f6lker im H\\u00f6henflug - Honig \\u00fcberschwemmt den Markt"
                  },
                  "text": {
                    "en_us": "Beekeepers report an extraordinary season - hives are overflowing and orchards buzzing with pollinators. With honey, honeycombs and sweet berries piling up in warehouses, prices are drifting down as sellers compete for buyers.",
                    "de_de": "Imker melden eine au\\u00dfergew\\u00f6hnliche Saison - die St\\u00f6cke laufen \\u00fcber, die Obstg\\u00e4rten summen vor Best\\u00e4ubern. Da sich Honig, Waben und S\\u00fc\\u00dfbeeren in den Lagern stapeln, geben die Preise nach, w\\u00e4hrend Verk\\u00e4ufer um K\\u00e4ufer buhlen."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 5400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.22,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.03
                  },
                  "markets": [
                    { "item": "minecraft:honey_bottle",  "weightFactor": 1.0 },
                    { "item": "minecraft:honeycomb",     "weightFactor": 0.8 },
                    { "item": "minecraft:sweet_berries", "weightFactor": 0.5 }
                  ]
                },
                {
                  "id": "copper_seam_discovery",
                  "picture": "copper_seam_discovery.png",
                  "headline": {
                    "en_us": "Massive copper seam opened in coastal cliffs!",
                    "de_de": "Gewaltige Kupferader in den K\\u00fcstenklippen erschlossen!"
                  },
                  "text": {
                    "en_us": "Surveyors have opened up a huge new copper seam running along the coastal cliffs. Foundries are queuing wagons for the fresh raw ore, and traders warn that ingots, blocks, and raw copper will all cheapen permanently as the new seam ramps up.",
                    "de_de": "Vermesser haben eine gewaltige neue Kupferader entlang der K\\u00fcstenklippen erschlossen. Gie\\u00dfereien schicken Wagen um Wagen zu den frischen Rohvorkommen, und H\\u00e4ndler warnen, dass Barren, Bl\\u00f6cke und Rohkupfer dauerhaft billiger werden, sobald die Ader in Produktion geht."
                  },
                  "category": "commodities",
                  "weight": 8,
                  "cooldownSeconds": 5400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.25,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    { "item": "minecraft:copper_ingot", "weightFactor": 1.0 },
                    { "item": "minecraft:copper_block", "weightFactor": 1.0 },
                    { "item": "minecraft:raw_copper",   "weightFactor": 0.8 }
                  ]
                },
                {
                  "id": "coal_miners_strike",
                  "picture": "coal_miners_strike.png",
                  "headline": {
                    "en_us": "Coal miners walk out - furnaces run cold!",
                    "de_de": "Kohlekumpel legen die Arbeit nieder - \\u00d6fen bleiben kalt!"
                  },
                  "text": {
                    "en_us": "Coal miners across the region have walked off the job in protest over unsafe shafts. Furnaces are running cold, smiths are hoarding what fuel they have, and the price of coal and charcoal is climbing by the hour.",
                    "de_de": "Kohlekumpel in der ganzen Region haben aus Protest gegen unsichere Sch\\u00e4chte die Arbeit niedergelegt. Die \\u00d6fen bleiben kalt, Schmiede horten ihren letzten Brennstoff, und die Preise f\\u00fcr Kohle und Holzkohle klettern st\\u00fcndlich."
                  },
                  "category": "labor",
                  "weight": 6,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.36,
                    "durationSeconds": 1800,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.04
                  },
                  "markets": [
                    { "item": "#minecraft:coals", "weightFactor": 1.0 }
                  ]
                },
                {
                  "id": "volcanic_eruption",
                  "picture": "volcanic_eruption.png",
                  "headline": {
                    "en_us": "Volcano erupts - villages fortify against ashfall!",
                    "de_de": "Vulkan bricht aus - D\\u00f6rfer verschanzen sich gegen den Ascheregen!"
                  },
                  "text": {
                    "en_us": "A dormant volcano roared back to life overnight, blanketing the eastern reaches in ash. Villages are ordering obsidian, basalt and blackstone in bulk to reinforce shelters and dampen embers - defensive-stone traders have never seen a rush like this.",
                    "de_de": "Ein schlafender Vulkan ist \\u00fcber Nacht wieder erwacht und hat den Osten unter einer Ascheschicht begraben. D\\u00f6rfer bestellen Obsidian, Basalt und Blackstone in gro\\u00dfen Mengen, um Unterst\\u00e4nde zu verst\\u00e4rken und Glutnester zu ersticken - H\\u00e4ndler mit Verteidigungsgestein haben so einen Ansturm noch nie erlebt."
                  },
                  "category": "disaster",
                  "weight": 2,
                  "cooldownSeconds": 21600,
                  "adminOnly": true,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.48,
                    "durationSeconds": 2100,
                    "reversal": "exponential",
                    "reversalSeconds": 6000,
                    "noise": 0.06
                  },
                  "markets": [
                    { "item": "minecraft:obsidian",   "weightFactor": 1.0 },
                    { "item": "minecraft:basalt",     "weightFactor": 0.8 },
                    { "item": "minecraft:blackstone", "weightFactor": 0.7 }
                  ]
                },
                {
                  "id": "great_forest_fire",
                  "picture": "great_forest_fire.png",
                  "headline": {
                    "en_us": "Wildfires sweep the taiga - lumber prices spike!",
                    "de_de": "Waldbr\\u00e4nde fegen durch die Taiga - Holzpreise schnellen hoch!"
                  },
                  "text": {
                    "en_us": "Wildfires touched off by a dry lightning storm are racing through the northern taiga, consuming acres of timber a day. Sawmills warn of severe log shortages while the flames burn and orders are already being redirected to distant forests.",
                    "de_de": "Waldbr\\u00e4nde, ausgel\\u00f6st durch ein trockenes Gewitter, rasen durch die n\\u00f6rdliche Taiga und fressen t\\u00e4glich hektarweise Bauholz. S\\u00e4gewerke warnen vor massiven Engp\\u00e4ssen bei Baumst\\u00e4mmen, w\\u00e4hrend die Flammen lodern, und Auftr\\u00e4ge werden bereits in entfernte W\\u00e4lder umgeleitet."
                  },
                  "category": "disaster",
                  "weight": 3,
                  "cooldownSeconds": 14400,
                  "sequences": [
                    {
                      "name": "wildfire",
                      "weight": 1,
                      "steps": [
                        { "name": "spark",     "durationSeconds": { "min": 300, "max": 600 },  "targetFactor": 0.15, "curve": "linear",      "noise": 0.03 },
                        { "name": "spread",    "durationSeconds": { "min": 600, "max": 1200 }, "targetFactor": 0.40, "curve": "linear",      "noise": 0.05 },
                        { "name": "inferno",   "durationSeconds": { "min": 450,  "max": 900 },                       "curve": "hold",        "noise": 0.07 },
                        { "name": "contained", "durationSeconds": { "min": 600, "max": 1200 }, "targetFactor": 0.20, "curve": "exponential", "noise": 0.04 },
                        { "name": "settle",    "durationSeconds": 1500, "targetFactor": 0.0, "curve": "linear" }
                      ]
                    }
                  ],
                  "markets": [
                    { "item": "#minecraft:logs",    "weightFactor": 1.0 },
                    { "item": "minecraft:*_planks", "weightFactor": 0.6 }
                  ],
                  "chains": [
                    { "eventId": "great_forest_fire_recovery", "on": "completion", "chance": 0.7, "delaySeconds": { "min": 600, "max": 1800 } }
                  ]
                },
                {
                  "id": "great_forest_fire_recovery",
                  "picture": "great_forest_fire_recovery.png",
                  "headline": {
                    "en_us": "Disaster relief planks flood the lumber market",
                    "de_de": "Katastrophenhilfe schwemmt den Holzmarkt"
                  },
                  "text": {
                    "en_us": "Emergency shipments of planks and salvaged logs from unaffected regions are pouring into the burned lands. Sawmills are running around the clock and the shortage-driven premium on lumber is unwinding rapidly.",
                    "de_de": "Notlieferungen von Brettern und geborgenen St\\u00e4mmen aus unversehrten Regionen str\\u00f6men in die Brandgebiete. S\\u00e4gewerke laufen rund um die Uhr, und der knappheitsbedingte Aufschlag auf Bauholz baut sich rasch ab."
                  },
                  "category": "recovery",
                  "weight": 4,
                  "cooldownSeconds": 21600,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.18,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    { "item": "#minecraft:logs",    "weightFactor": 1.0 },
                    { "item": "minecraft:*_planks", "weightFactor": 0.6 }
                  ],
                  "requires": [
                    { "type": "firedBefore", "eventId": "great_forest_fire", "minSecondsAgo": 60 }
                  ]
                },
            """;

    private static final String DEFAULT_EVENTS_JSON_2 = """
                {
                  "id": "import_tariff_treaty",
                  "picture": "import_tariff_treaty.png",
                  "headline": {
                    "en_us": "Trade council signs sweeping import tariff treaty",
                    "de_de": "Handelsrat unterzeichnet umfassenden Einfuhrzoll-Vertrag"
                  },
                  "text": {
                    "en_us": "After months of negotiation the trade council has signed a sweeping import tariff treaty on foreign metals. Cross-border shipments of iron and gold will carry a permanent surcharge - analysts expect the premium to become the new normal price.",
                    "de_de": "Nach monatelangen Verhandlungen hat der Handelsrat einen umfassenden Einfuhrzoll-Vertrag auf ausl\\u00e4ndische Metalle unterzeichnet. Grenz\\u00fcberschreitende Lieferungen von Eisen und Gold tragen k\\u00fcnftig einen dauerhaften Aufschlag - Analysten erwarten, dass der Zuschlag zum neuen Normalpreis wird."
                  },
                  "category": "politics",
                  "weight": 1,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.18,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 2400,
                    "reversal": "none"
                  },
                  "markets": [
                    { "item": "minecraft:iron_ingot", "weightFactor": 1.0 },
                    { "item": "minecraft:gold_ingot", "weightFactor": 0.8 }
                  ],
                  "requires": [
                    { "type": "notFired", "eventId": "import_tariff_treaty" }
                  ]
                },
                {
                  "id": "winter_solstice_gifting",
                  "picture": "winter_solstice_gifting.png",
                  "headline": {
                    "en_us": "Winter solstice gifting season lifts sweets and textiles",
                    "de_de": "Wintersonnenwende-Schenkzeit befl\\u00fcgelt S\\u00fc\\u00dfwaren und Textilien"
                  },
                  "text": {
                    "en_us": "The winter solstice gifting season is in full swing. Bakeries can barely keep cakes and cookies on the shelves, and dyed wool orders are stacking up in every village hall as households prepare gifts and decorations.",
                    "de_de": "Die Schenkzeit zur Wintersonnenwende ist in vollem Gang. B\\u00e4ckereien bekommen kaum genug Kuchen und Kekse ins Regal, und Bestellungen f\\u00fcr gef\\u00e4rbte Wolle stapeln sich in jeder Dorfhalle, w\\u00e4hrend Haushalte Geschenke und Schmuck vorbereiten."
                  },
                  "category": "cultural",
                  "weight": 4,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 1050,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.04
                  },
                  "markets": [
                    { "item": "minecraft:cake",   "weightFactor": 1.0 },
                    { "item": "minecraft:cookie", "weightFactor": 0.8 },
                    { "item": "#minecraft:wool",  "weightFactor": 0.5 }
                  ],
                  "chains": [
                    { "eventId": "post_solstice_slump", "on": "completion", "chance": 0.6, "delaySeconds": { "min": 600, "max": 1800 }, "sameMarkets": true }
                  ]
                },
                {
                  "id": "post_solstice_slump",
                  "picture": "post_solstice_slump.png",
                  "headline": {
                    "en_us": "After the solstice: gift-market demand collapses",
                    "de_de": "Nach der Sonnenwende: Nachfrage im Geschenkmarkt bricht ein"
                  },
                  "text": {
                    "en_us": "With the gifting season over, households are done buying and warehouses are still stuffed. Cakes go stale on the shelves, cookie tins gather dust, and dyed wool is discounted aggressively as merchants offload year-end stock.",
                    "de_de": "Mit dem Ende der Schenkzeit sind die Haushalte durch, und die Lager sind noch immer voll. Kuchen werden im Regal alt, Keksdosen setzen Staub an, und gef\\u00e4rbte Wolle wird aggressiv verramscht, w\\u00e4hrend die H\\u00e4ndler ihre Jahresendbest\\u00e4nde loswerden."
                  },
                  "category": "cultural",
                  "weight": 3,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.28,
                    "durationSeconds": 1200,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.04
                  },
                  "markets": [
                    { "item": "minecraft:cake",   "weightFactor": 1.0 },
                    { "item": "minecraft:cookie", "weightFactor": 0.8 },
                    { "item": "#minecraft:wool",  "weightFactor": 0.5 }
                  ]
                },
                {
                  "id": "redstone_arms_race",
                  "picture": "redstone_arms_race.png",
                  "headline": {
                    "en_us": "Kingdoms race to build redstone defenses",
                    "de_de": "K\\u00f6nigreiche wetteifern um Redstone-Verteidigungsanlagen"
                  },
                  "text": {
                    "en_us": "Rival kingdoms are pouring resources into automated defenses, and their engineers are buying up every scrap of redstone and iron they can find. Speculators are jumping in behind them - traders warn the rally can just as easily fizzle out if a ceasefire is announced.",
                    "de_de": "Rivalisierende K\\u00f6nigreiche stecken massiv Mittel in automatisierte Verteidigungsanlagen, und ihre Ingenieure kaufen jeden erreichbaren Krumen Redstone und Eisen auf. Spekulanten springen hinterher - H\\u00e4ndler warnen, dass die Rally ebenso schnell verpuffen kann, sobald ein Waffenstillstand verk\\u00fcndet wird."
                  },
                  "category": "conflict",
                  "weight": 3,
                  "cooldownSeconds": 10800,
                  "sequences": [
                    {
                      "name": "escalation",
                      "weight": 2,
                      "steps": [
                        { "name": "arms_buildup", "durationSeconds": { "min": 900, "max": 1800 }, "targetFactor": 0.30, "curve": "linear",      "noise": 0.04 },
                        { "name": "peak_tension", "durationSeconds": { "min": 600, "max": 1200 },                        "curve": "hold",        "noise": 0.05 },
                        { "name": "ceasefire",    "durationSeconds": { "min": 900, "max": 1500 }, "targetFactor": 0.0,  "curve": "exponential", "noise": 0.03 }
                      ]
                    },
                    {
                      "name": "brief_scare",
                      "weight": 1,
                      "steps": [
                        { "name": "alarm",  "durationSeconds": { "min": 300, "max": 600 }, "targetFactor": 0.20, "curve": "linear", "noise": 0.04 },
                        { "name": "denial", "durationSeconds": { "min": 300, "max": 600 }, "targetFactor": 0.0,  "curve": "exponential" }
                      ]
                    }
                  ],
                  "markets": [
                    { "item": "minecraft:redstone",   "weightFactor": 1.0 },
                    { "item": "minecraft:iron_ingot", "weightFactor": 0.5 }
                  ]
                },
                {
                  "id": "nether_quartz_glut",
                  "picture": "nether_quartz_glut.png",
                  "headline": {
                    "en_us": "Nether quartz glut buries the market!",
                    "de_de": "Netherquarz-Schwemme erdr\\u00fcckt den Markt!"
                  },
                  "text": {
                    "en_us": "Fresh prospecting camps in the crimson wastes have flooded the caravans with nether quartz. Smelteries report warehouses filled to the rafters, and traders warn that the price of quartz and cut blocks will keep sliding for as long as the digging holds. Overmined and oversupplied, the shine has gone off the market.",
                    "de_de": "Neue Sch\\u00fcrfcamps in den karmesinroten \\u00d6dl\\u00e4ndern haben die Karawanen mit Netherquarz \\u00fcberschwemmt. H\\u00fctten melden bis unters Dach gef\\u00fcllte Lagerhallen, und H\\u00e4ndler warnen, dass die Preise f\\u00fcr Quarz und geschnittene Bl\\u00f6cke weiter fallen, solange der Abbau anh\\u00e4lt. \\u00dcberf\\u00f6rdert und \\u00fcberversorgt, ist der Glanz vom Markt verschwunden."
                  },
                  "category": "commodities",
                  "weight": 8,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.28,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:quartz",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:quartz_block",
                      "weightFactor": 0.8
                    }
                  ]
                },
                {
                  "id": "amethyst_geode_discovery",
                  "picture": "amethyst_geode_discovery.png",
                  "headline": {
                    "en_us": "Colossal geode cracked open - amethyst everywhere!",
                    "de_de": "Gewaltige Geode aufgebrochen - \\u00fcberall Amethyst!"
                  },
                  "text": {
                    "en_us": "Miners in the deep caverns have broken into a geode the size of a village hall, its walls solid with amethyst clusters. So much crystal is now reaching the caravans that jewelers expect the glut to be permanent - the days of scarce amethyst are simply over, and the price will settle at a new, lower level for good.",
                    "de_de": "Bergleute in den tiefen H\\u00f6hlen sind in eine Geode so gro\\u00df wie eine Dorfhalle vorgedrungen, deren W\\u00e4nde dicht mit Amethystdrusen besetzt sind. So viel Kristall erreicht nun die Karawanen, dass Juweliere von einem dauerhaften \\u00dcberangebot ausgehen - die Zeiten des knappen Amethysts sind schlicht vorbei, und der Preis pendelt sich auf einem neuen, tieferen Niveau ein."
                  },
                  "category": "commodities",
                  "weight": 4,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.3,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:amethyst_shard",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "lapis_enchanting_craze",
                  "picture": "lapis_enchanting_craze.png",
                  "headline": {
                    "en_us": "Enchanting craze sends lapis demand soaring!",
                    "de_de": "Verzauberungs-Manie treibt die Lapis-Nachfrage in die H\\u00f6he!"
                  },
                  "text": {
                    "en_us": "A new fashion for enchanted gear has swept the guild halls, and every apprentice wants a stocked enchanting table. Lapis lazuli is suddenly the ingredient nobody can keep on the shelf, and traders who read the newspaper early may position themselves before the buying frenzy fully hits the market.",
                    "de_de": "Eine neue Mode f\\u00fcr verzauberte Ausr\\u00fcstung hat die Gildenhallen erfasst, und jeder Lehrling will einen gut best\\u00fcckten Zaubertisch. Lapislazuli ist pl\\u00f6tzlich die Zutat, die niemand mehr im Regal halten kann, und wer die Zeitung fr\\u00fch liest, kann sich wom\\u00f6glich in Stellung bringen, bevor der Kaufrausch den Markt voll erfasst."
                  },
                  "category": "technology",
                  "weight": 7,
                  "cooldownSeconds": 10800,
                  "announceDelayMs": {
                    "min": 15000,
                    "max": 90000
                  },
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 3000,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:lapis_lazuli",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:lapis_block",
                      "weightFactor": 0.8
                    }
                  ]
                },
                {
                  "id": "glowstone_farm_boom",
                  "picture": "glowstone_farm_boom.png",
                  "headline": {
                    "en_us": "Glowstone farms flood the market with light!",
                    "de_de": "Glowstone-Farmen \\u00fcberschwemmen den Markt mit Licht!"
                  },
                  "text": {
                    "en_us": "Enterprising builders have set up sprawling glowstone harvesting operations across the nether ceilings, and the dust is now pouring back through the portals by the cartload. With supply outrunning even the busiest lamp-makers, the price of glowstone dust and blocks is drifting steadily downward.",
                    "de_de": "Findige Baumeister haben unter den Netherdecken weitl\\u00e4ufige Glowstone-Ernteanlagen errichtet, und der Staub str\\u00f6mt nun karrenweise durch die Portale zur\\u00fcck. Da das Angebot selbst die flei\\u00dfigsten Lampenbauer \\u00fcbersteigt, geben die Preise f\\u00fcr Glowstone-Staub und -Bl\\u00f6cke stetig nach."
                  },
                  "category": "commodities",
                  "weight": 8,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.25,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:glowstone_dust",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:glowstone",
                      "weightFactor": 0.8
                    }
                  ]
                },
                {
                  "id": "deepslate_quarry_expansion",
                  "picture": "deepslate_quarry_expansion.png",
                  "headline": {
                    "en_us": "Deep quarry opens - deepslate cheap for good",
                    "de_de": "Tiefer Steinbruch erschlossen - Tiefengestein dauerhaft billig"
                  },
                  "text": {
                    "en_us": "The trade council has bankrolled a vast quarry sunk to the deep stone layers, and cobbled deepslate now arrives in quantities the market has never seen. Analysts call it a structural shift, not a passing surplus - with the quarry running for years to come, cheap deepslate is simply the new normal.",
                    "de_de": "Der Handelsrat hat einen gewaltigen Steinbruch bis in die tiefen Gesteinsschichten finanziert, und Bruch-Tiefengestein trifft nun in nie gekannten Mengen ein. Analysten sprechen von einem strukturellen Wandel, nicht von einem vor\\u00fcbergehenden \\u00dcberschuss - da der Steinbruch noch jahrelang l\\u00e4uft, ist billiges Tiefengestein schlicht der neue Normalzustand."
                  },
                  "category": "economy",
                  "weight": 5,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.2,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:cobbled_deepslate",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:deepslate",
                      "weightFactor": 0.8
                    }
                  ]
                },
                {
                  "id": "prismarine_monument_raid",
                  "picture": "prismarine_monument_raid.png",
                  "headline": {
                    "en_us": "Ocean monument stripped bare - prismarine floods in!",
                    "de_de": "Ozeanmonument leerger\\u00e4umt - Prismarin \\u00fcberflutet den Markt!"
                  },
                  "text": {
                    "en_us": "A guild of divers has stormed a great ocean monument and hauled its walls to the surface overnight. Prismarine shards and crystals hit the caravans all at once, and the sudden wall of supply has sent prices plunging before sellers can even set a fair rate.",
                    "de_de": "Eine Taucherzunft hat ein gro\\u00dfes Ozeanmonument gest\\u00fcrmt und seine W\\u00e4nde \\u00fcber Nacht an die Oberfl\\u00e4che geschafft. Prismarinscherben und -kristalle treffen mit einem Schlag auf die Karawanen, und die pl\\u00f6tzliche Angebotswand l\\u00e4sst die Preise abst\\u00fcrzen, noch ehe die Verk\\u00e4ufer einen fairen Kurs festlegen k\\u00f6nnen."
                  },
                  "category": "commodities",
                  "weight": 5,
                  "cooldownSeconds": 14400,
                  "impact": {
                    "type": "shock",
                    "peakFactor": -0.35,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:prismarine_shard",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:prismarine_crystals",
                      "weightFactor": 0.9
                    },
                    {
                      "item": "minecraft:prismarine",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "quartz_cartel_squeeze",
                  "picture": "quartz_cartel_squeeze.png",
                  "headline": {
                    "en_us": "Cartel corners quartz supply - prices squeezed!",
                    "de_de": "Kartell verknappt den Quarz - Preise unter Druck!"
                  },
                  "text": {
                    "en_us": "Word from the back rooms is that a ring of merchants has quietly bought up every quartz shipment leaving the nether portals. With the supply locked in their vaults and buyers left scrambling, the price has been squeezed sharply upward overnight - a manipulation the trade council is said to be investigating.",
                    "de_de": "Aus den Hinterzimmern hei\\u00dft es, ein Ring von H\\u00e4ndlern habe still und heimlich jede Quarzlieferung aufgekauft, die die Netherportale verl\\u00e4sst. Da der Nachschub in ihren Gew\\u00f6lben liegt und die K\\u00e4ufer im Regen stehen, wurde der Preis \\u00fcber Nacht scharf nach oben getrieben - eine Manipulation, gegen die der Handelsrat angeblich bereits ermittelt."
                  },
                  "category": "crime",
                  "weight": 3,
                  "cooldownSeconds": 28800,
                  "adminOnly": true,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.45,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3000,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:quartz",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "dripstone_cavern_rush",
                  "picture": "dripstone_cavern_rush.png",
                  "headline": {
                    "en_us": "Dripstone the new fashion in grand halls!",
                    "de_de": "Tropfstein wird zur neuen Mode in den Prunkhallen!"
                  },
                  "text": {
                    "en_us": "Master builders have taken to lining grand halls with dripstone columns, and every noble house wants the dramatic cavern look for its own. Demand for dripstone blocks and pointed spikes is climbing steadily as masons compete for the fashionable stone.",
                    "de_de": "Baumeister haben begonnen, Prunkhallen mit Tropfsteins\\u00e4ulen auszukleiden, und jedes Adelshaus will den dramatischen H\\u00f6hlenlook f\\u00fcr sich. Die Nachfrage nach Tropfsteinbl\\u00f6cken und spitzem Tropfstein steigt stetig, w\\u00e4hrend Steinmetze um das modische Gestein wetteifern."
                  },
                  "category": "commodities",
                  "weight": 7,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:dripstone_block",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:pointed_dripstone",
                      "weightFactor": 0.7
                    }
                  ]
                },
                {
                  "id": "tuff_facade_fashion",
                  "picture": "tuff_facade_fashion.png",
                  "headline": {
                    "en_us": "Tuff-and-calcite facades sweep the townhouses",
                    "de_de": "Tuff-und-Kalzit-Fassaden erobern die Stadth\\u00e4user"
                  },
                  "text": {
                    "en_us": "A wave of taste for pale tuff-and-calcite facades has swept the townhouses, and masons cannot cut the mottled stone fast enough. As tuff and calcite climb, plain cobblestone is quietly falling out of favor - the humble grey walls now read as old-fashioned and their price drifts lower.",
                    "de_de": "Eine Modewelle f\\u00fcr helle Tuff-und-Kalzit-Fassaden hat die Stadth\\u00e4user erfasst, und die Steinmetze kommen mit dem Zuschneiden des gesprenkelten Gesteins kaum nach. W\\u00e4hrend Tuff und Kalzit steigen, ger\\u00e4t schlichtes Kopfsteinpflaster still aus der Mode - die biederen grauen Mauern gelten nun als altbacken, und ihr Preis gibt nach."
                  },
                  "category": "cultural",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:tuff",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:calcite",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:cobblestone",
                      "weightFactor": -0.15
                    }
                  ]
                },
                {
                  "id": "amethyst_speculation",
                  "picture": "amethyst_speculation.png",
                  "headline": {
                    "en_us": "Speculators pile into amethyst on wild rumors!",
                    "de_de": "Spekulanten st\\u00fcrzen sich auf Amethyst - wilde Ger\\u00fcchte!"
                  },
                  "text": {
                    "en_us": "Whispers of a secret royal amethyst commission have traders bidding the crystal up hard, though nobody can name a source. Seasoned brokers caution that speculation like this often peaks in a frenzy and collapses just as fast - and sometimes the story never gets off the ground at all.",
                    "de_de": "Ger\\u00fcchte \\u00fcber einen geheimen k\\u00f6niglichen Amethyst-Auftrag treiben die H\\u00e4ndler dazu, den Kristall kr\\u00e4ftig hochzubieten, auch wenn niemand eine Quelle nennen kann. Erfahrene Makler warnen, dass solche Spekulationen oft in einem Rausch gipfeln und ebenso schnell zusammenbrechen - und manchmal kommt die Geschichte gar nicht erst in Fahrt."
                  },
                  "category": "rumors",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "sequences": [
                    {
                      "name": "pump_and_dump",
                      "weight": 2,
                      "steps": [
                        {
                          "name": "hype",
                          "durationSeconds": {
                            "min": 450,
                            "max": 1200
                          },
                          "targetFactor": 0.4,
                          "curve": "linear",
                          "noise": 0.05
                        },
                        {
                          "name": "peak",
                          "durationSeconds": {
                            "min": 300,
                            "max": 600
                          },
                          "curve": "hold",
                          "noise": 0.08
                        },
                        {
                          "name": "sell_off",
                          "durationSeconds": {
                            "min": 100,
                            "max": 300
                          },
                          "targetFactor": -0.15,
                          "curve": "exponential",
                          "noise": 0.04
                        },
                        {
                          "name": "recover",
                          "durationSeconds": 1500,
                          "targetFactor": 0.0,
                          "curve": "linear"
                        }
                      ]
                    },
                    {
                      "name": "fizzle",
                      "weight": 1,
                      "steps": [
                        {
                          "name": "stir",
                          "durationSeconds": {
                            "min": 300,
                            "max": 600
                          },
                          "targetFactor": 0.15,
                          "curve": "linear",
                          "noise": 0.04
                        },
                        {
                          "name": "denial",
                          "durationSeconds": {
                            "min": 300,
                            "max": 900
                          },
                          "targetFactor": 0.0,
                          "curve": "exponential"
                        }
                      ]
                    }
                  ],
                  "markets": [
                    {
                      "item": "minecraft:amethyst_shard",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "potato_blight_return",
                  "picture": "potato_blight_return.png",
                  "headline": {
                    "en_us": "Blight returns to the potato fields!",
                    "de_de": "Krautf\\u00e4ule kehrt auf die Kartoffelfelder zur\\u00fcck!"
                  },
                  "text": {
                    "en_us": "The dreaded blight has crept back into the potato fields overnight, blackening entire rows before the farmers could react. With the storehouses barely half full, prices for potatoes and baked potatoes are shooting up as households rush to secure what little remains.",
                    "de_de": "\\u00dcber Nacht hat sich die gef\\u00fcrchtete Krautf\\u00e4ule zur\\u00fcck auf die Kartoffelfelder geschlichen und ganze Reihen geschw\\u00e4rzt, ehe die Bauern reagieren konnten. Da die Vorratsh\\u00e4user kaum halb voll sind, schie\\u00dfen die Preise f\\u00fcr Kartoffeln und Ofenkartoffeln in die H\\u00f6he, w\\u00e4hrend die Haushalte sich um die letzten Reste rei\\u00dfen."
                  },
                  "category": "disaster",
                  "weight": 4,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:potato",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:baked_potato",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "pumpkin_autumn_festival",
                  "picture": "pumpkin_autumn_festival.png",
                  "headline": {
                    "en_us": "Autumn festival lifts the pumpkin trade!",
                    "de_de": "Herbstfest belebt den K\\u00fcrbishandel!"
                  },
                  "text": {
                    "en_us": "The annual autumn festival is upon us and every village square is being decked out with lanterns and pies. Bakers cannot bake pumpkin pie fast enough, carvers are besieged for jack-o'-lanterns, and prices for pumpkins climb steadily through the season while the ordinary cake stalls stand quiet.",
                    "de_de": "Das allj\\u00e4hrliche Herbstfest steht vor der T\\u00fcr, und auf jedem Dorfplatz werden Laternen und Kuchen ausgestellt. Die B\\u00e4cker kommen mit dem K\\u00fcrbiskuchen nicht hinterher, die Schnitzer werden nach K\\u00fcrbislaternen belagert, und die K\\u00fcrbispreise klettern die Saison \\u00fcber stetig, w\\u00e4hrend die gew\\u00f6hnlichen Kuchenst\\u00e4nde leer bleiben."
                  },
                  "category": "cultural",
                  "weight": 5,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.25,
                    "rampUpSeconds": 1050,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:pumpkin",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:pumpkin_pie",
                      "weightFactor": 0.7
                    },
                    {
                      "item": "minecraft:carved_pumpkin",
                      "weightFactor": 0.5
                    },
                    {
                      "item": "minecraft:cake",
                      "weightFactor": -0.15
                    }
                  ]
                },
                {
                  "id": "melon_summer_glut",
                  "picture": "melon_summer_glut.png",
                  "headline": {
                    "en_us": "Bumper summer floods the market with melons",
                    "de_de": "Rekordsommer \\u00fcberschwemmt den Markt mit Melonen"
                  },
                  "text": {
                    "en_us": "A long, warm summer has left the melon patches groaning under the weight of the harvest. Carts of fruit arrive at market faster than the stalls can sell them, and with warehouses stuffed to the rafters the price of melons and melon slices is sliding lower by the day.",
                    "de_de": "Ein langer, warmer Sommer hat die Melonenfelder unter der Last der Ernte \\u00e4chzen lassen. Wagen um Wagen voller Fr\\u00fcchte erreicht den Markt schneller, als die St\\u00e4nde sie verkaufen k\\u00f6nnen, und da die Lager bis unter das Dach gef\\u00fcllt sind, rutscht der Preis f\\u00fcr Melonen und Melonenscheiben von Tag zu Tag tiefer."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 21600,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.22,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:melon_slice",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:melon",
                      "weightFactor": 0.7
                    }
                  ]
                },
                {
                  "id": "sugarcane_plantation_boom",
                  "picture": "sugarcane_plantation_boom.png",
                  "headline": {
                    "en_us": "New river plantations turn sugar cane cheap for good",
                    "de_de": "Neue Flussplantagen machen Zuckerrohr dauerhaft billig"
                  },
                  "text": {
                    "en_us": "Vast new sugar-cane plantations have been established along the great river deltas, and the first barges are already arriving. The trade council expects the flood of cane to hold indefinitely, pressing the prices of sugar cane, refined sugar, and paper down to a permanently lower level.",
                    "de_de": "Entlang der gro\\u00dfen Flussdeltas sind weite neue Zuckerrohrplantagen entstanden, und die ersten K\\u00e4hne treffen bereits ein. Der Handelsrat rechnet damit, dass die Rohrschwemme auf Dauer anh\\u00e4lt, und dr\\u00fcckt die Preise f\\u00fcr Zuckerrohr, raffinierten Zucker und Papier auf ein dauerhaft niedrigeres Niveau."
                  },
                  "category": "economy",
                  "weight": 5,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.25,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:sugar_cane",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:sugar",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:paper",
                      "weightFactor": 0.4
                    }
                  ]
                },
            """;

    private static final String DEFAULT_EVENTS_JSON_3 = """
                {
                  "id": "cocoa_trade_route_opens",
                  "picture": "cocoa_trade_route_opens.png",
                  "headline": {
                    "en_us": "Southern trade route opens the cocoa floodgates",
                    "de_de": "S\\u00fcdliche Handelsroute \\u00f6ffnet die Kakaoschleusen"
                  },
                  "text": {
                    "en_us": "A new caravan route through the southern jungles has finally been secured, and the first shipments of cocoa beans are pouring into the northern markets. With supply suddenly abundant, cocoa prices are drifting lower as chocolatiers stock up while the going is cheap.",
                    "de_de": "Eine neue Karawanenroute durch die s\\u00fcdlichen Dschungel ist endlich gesichert, und die ersten Ladungen Kakaobohnen str\\u00f6men in die n\\u00f6rdlichen M\\u00e4rkte. Da das Angebot pl\\u00f6tzlich reichlich ist, geben die Kakaopreise nach, w\\u00e4hrend sich die Chocolatiers eindecken, solange es g\\u00fcnstig ist."
                  },
                  "category": "commodities",
                  "weight": 7,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.28,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:cocoa_beans",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "mushroom_foraging_craze",
                  "picture": "mushroom_foraging_craze.png",
                  "headline": {
                    "en_us": "Foraging craze sends mushroom prices soaring",
                    "de_de": "Sammelfieber treibt die Pilzpreise nach oben"
                  },
                  "text": {
                    "en_us": "A craze for wild mushroom stew has gripped the villages, and every innkeeper wants red and brown caps by the basketful. Prices for both mushrooms and ready-made stew are climbing fast, while the bakers grumble that villagers off foraging in the woods are buying far less bread.",
                    "de_de": "Ein Fieber nach Waldpilzeintopf hat die D\\u00f6rfer erfasst, und jeder Wirt will rote und braune Kappen k\\u00f6rbeweise. Die Preise f\\u00fcr beide Pilzsorten und f\\u00fcr fertigen Eintopf steigen rasch, w\\u00e4hrend die B\\u00e4cker murren, dass die Dorfbewohner beim Pilzesammeln im Wald weit weniger Brot kaufen."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.24,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:red_mushroom",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:brown_mushroom",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:mushroom_stew",
                      "weightFactor": 0.6
                    },
                    {
                      "item": "minecraft:bread",
                      "weightFactor": -0.2
                    }
                  ]
                },
                {
                  "id": "netherwart_brewing_shortage",
                  "picture": "netherwart_brewing_shortage.png",
                  "headline": {
                    "en_us": "Brewers empty the nether wart stocks!",
                    "de_de": "Brauer r\\u00e4umen die Netherwarzen-Lager leer!"
                  },
                  "text": {
                    "en_us": "A surge in potion demand has seen the brewers' guild buy up every last crate of nether wart, and the fortress caravans cannot resupply fast enough. Nether wart prices have spiked overnight, while sugar and glowstone dust sag as half-finished brews sit idle without their base ingredient.",
                    "de_de": "Ein sprunghaft gestiegener Trankbedarf hat die Brauergilde jede letzte Kiste Netherwarzen aufkaufen lassen, und die Festungskarawanen kommen mit dem Nachschub nicht hinterher. Die Netherwarzenpreise sind \\u00fcber Nacht in die H\\u00f6he geschnellt, w\\u00e4hrend Zucker und Leuchtsteinstaub nachgeben, weil halbfertige Gebr\\u00e4ue ohne ihre Grundzutat brachliegen."
                  },
                  "category": "commodities",
                  "weight": 5,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.42,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:nether_wart",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:sugar",
                      "weightFactor": -0.2
                    },
                    {
                      "item": "minecraft:glowstone_dust",
                      "weightFactor": -0.15
                    }
                  ]
                },
                {
                  "id": "beetroot_harvest_surplus",
                  "picture": "beetroot_harvest_surplus.png",
                  "headline": {
                    "en_us": "Record beetroot harvest weighs on prices",
                    "de_de": "Rekord-R\\u00fcbenernte dr\\u00fcckt die Preise"
                  },
                  "text": {
                    "en_us": "Growers report a record beetroot harvest this year, with cellars overflowing and soup kitchens spoiled for choice. As the surplus works its way through the market, prices for beetroot and beetroot soup are easing steadily lower.",
                    "de_de": "Die Anbauer melden in diesem Jahr eine Rekordernte an Roten R\\u00fcben, die Keller quellen \\u00fcber und die Suppenk\\u00fcchen haben die Qual der Wahl. W\\u00e4hrend sich der \\u00dcberschuss durch den Markt arbeitet, geben die Preise f\\u00fcr Rote R\\u00fcben und R\\u00fcbensuppe stetig nach."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.2,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:beetroot",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:beetroot_soup",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "great_drought",
                  "picture": "great_drought.png",
                  "headline": {
                    "en_us": "Great drought scorches the croplands!",
                    "de_de": "Gro\\u00dfe D\\u00fcrre verdorrt das Ackerland!"
                  },
                  "text": {
                    "en_us": "Weeks without rain have turned the croplands to dust, and the wells are running dangerously low. Wheat wilts in the fields, root crops shrivel underground, and prices for every staple are surging as farmers and merchants brace for a hungry season.",
                    "de_de": "Wochen ohne Regen haben das Ackerland zu Staub werden lassen, und die Brunnen versiegen bedenklich. Der Weizen verwelkt auf den Feldern, die Wurzelfr\\u00fcchte verk\\u00fcmmern im Boden, und die Preise f\\u00fcr alle Grundnahrungsmittel schnellen empor, w\\u00e4hrend sich Bauern und H\\u00e4ndler auf eine hungrige Zeit einstellen."
                  },
                  "category": "disaster",
                  "weight": 3,
                  "cooldownSeconds": 21600,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1800,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:wheat",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:potato",
                      "weightFactor": 0.9
                    },
                    {
                      "item": "minecraft:carrot",
                      "weightFactor": 0.9
                    },
                    {
                      "item": "minecraft:beetroot",
                      "weightFactor": 0.8
                    }
                  ],
                  "records": {
                    "climate": "drought"
                  },
                  "chains": [
                    {
                      "eventId": "grain_relief_convoy",
                      "on": "completion",
                      "chance": 0.7,
                      "delaySeconds": {
                        "min": 600,
                        "max": 1800
                      }
                    }
                  ]
                },
                {
                  "id": "grain_relief_convoy",
                  "picture": "grain_relief_convoy.png",
                  "headline": {
                    "en_us": "Relief caravans pour grain into the stricken markets",
                    "de_de": "Hilfskarawanen sch\\u00fctten Getreide in die notleidenden M\\u00e4rkte"
                  },
                  "text": {
                    "en_us": "Long relief caravans from the untouched river provinces are rolling into the drought-stricken markets, their wagons heavy with grain and root crops. As the sacks pile up on the market squares, the shortage premium on staples is unwinding and prices are settling back toward normal.",
                    "de_de": "Lange Hilfskarawanen aus den verschonten Flussprovinzen rollen in die von der D\\u00fcrre gezeichneten M\\u00e4rkte, die Wagen schwer beladen mit Getreide und Wurzelfr\\u00fcchten. W\\u00e4hrend sich die S\\u00e4cke auf den Marktpl\\u00e4tzen stapeln, baut sich der knappheitsbedingte Aufschlag auf Grundnahrungsmittel ab, und die Preise pendeln sich wieder auf normalem Niveau ein."
                  },
                  "category": "recovery",
                  "weight": 0,
                  "cooldownSeconds": 21600,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.2,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:wheat",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:potato",
                      "weightFactor": 0.9
                    },
                    {
                      "item": "minecraft:carrot",
                      "weightFactor": 0.9
                    },
                    {
                      "item": "minecraft:beetroot",
                      "weightFactor": 0.8
                    }
                  ],
                  "requires": [
                    {
                      "type": "firedBefore",
                      "eventId": "great_drought",
                      "minSecondsAgo": 60
                    }
                  ]
                },
                {
                  "id": "spring_shearing_glut",
                  "picture": "spring_shearing_glut.png",
                  "headline": {
                    "en_us": "Spring shearing floods the wool market",
                    "de_de": "Fr\\u00fchjahrsschur \\u00fcberschwemmt den Wollmarkt"
                  },
                  "text": {
                    "en_us": "The first warm days brought every shepherd's flock to the shears at once, and bales of fresh fleece are piling up faster than the weavers can spin them. Wool and raw thread are drifting cheaper by the day as sellers undercut one another at the gate. Tanners, meanwhile, note that buyers are quietly turning back to leather for their finer garments.",
                    "de_de": "Die ersten warmen Tage haben die Herden aller Sch\\u00e4fer zugleich unter die Schere gebracht, und Ballen frischer Wolle stapeln sich schneller, als die Weber sie verspinnen k\\u00f6nnen. Wolle und Rohgarn werden von Tag zu Tag billiger, w\\u00e4hrend die Verk\\u00e4ufer sich am Tor gegenseitig unterbieten. Gerber wiederum bemerken, dass sich die K\\u00e4ufer f\\u00fcr ihre feineren Gew\\u00e4nder still wieder dem Leder zuwenden."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.24,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:string",
                      "weightFactor": 0.5
                    },
                    {
                      "item": "#minecraft:wool",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:leather",
                      "weightFactor": -0.3
                    }
                  ]
                },
                {
                  "id": "leather_tannery_boom",
                  "picture": "leather_tannery_boom.png",
                  "headline": {
                    "en_us": "New tanneries drive a run on leather",
                    "de_de": "Neue Gerbereien l\\u00f6sen einen Ansturm auf Leder aus"
                  },
                  "text": {
                    "en_us": "A cluster of new tanneries has opened along the river, and their appetite for hides is reshaping the whole leather trade. Saddlers, bookbinders and armourers are all bidding against one another, and prices are climbing steadily. Wool merchants grumble that the fashion for tooled leather is pulling custom away from their looms.",
                    "de_de": "Entlang des Flusses hat eine Reihe neuer Gerbereien er\\u00f6ffnet, und ihr Hunger nach H\\u00e4uten krempelt den gesamten Lederhandel um. Sattler, Buchbinder und Waffenschmiede \\u00fcberbieten sich gegenseitig, und die Preise klettern stetig. Wollh\\u00e4ndler murren, dass die Mode f\\u00fcr gepunztes Leder ihnen die Kundschaft von den Webst\\u00fchlen zieht."
                  },
                  "category": "economy",
                  "weight": 7,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 750,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:leather",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "#minecraft:wool",
                      "weightFactor": -0.25
                    }
                  ]
                },
                {
                  "id": "cattle_plague_outbreak",
                  "picture": "cattle_plague_outbreak.png",
                  "headline": {
                    "en_us": "Cattle plague fells the herds - meat and hides grow scarce!",
                    "de_de": "Rinderseuche rafft die Herden dahin - Fleisch und H\\u00e4ute werden knapp!"
                  },
                  "text": {
                    "en_us": "A murrain sweeping the pasturelands has felled entire herds within days, and drovers are burning carcasses to halt its spread. With beef, milk and hides all vanishing from the stalls at once, prices have lurched upward and butchers are turning buyers away. Herders warn the shortage will bite hardest before the surviving stock can recover.",
                    "de_de": "Eine Viehseuche fegt \\u00fcber die Weidegr\\u00fcnde und hat binnen Tagen ganze Herden dahingerafft; Treiber verbrennen die Kadaver, um ihre Ausbreitung zu stoppen. Da Rindfleisch, Milch und H\\u00e4ute zugleich aus den St\\u00e4nden verschwinden, sind die Preise nach oben gesprungen, und Metzger weisen ihre Kundschaft ab. Hirten warnen, dass die Knappheit am h\\u00e4rtesten zuschl\\u00e4gt, bevor sich der \\u00fcberlebende Bestand erholen kann."
                  },
                  "category": "disaster",
                  "weight": 3,
                  "cooldownSeconds": 21600,
                  "adminOnly": true,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.42,
                    "durationSeconds": 1800,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:beef",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:milk_bucket",
                      "weightFactor": 0.7
                    },
                    {
                      "item": "minecraft:leather",
                      "weightFactor": 0.5
                    }
                  ]
                },
                {
                  "id": "salmon_spawning_run",
                  "picture": "salmon_spawning_run.png",
                  "headline": {
                    "en_us": "Great salmon run fills the nets",
                    "de_de": "Gro\\u00dfer Lachszug f\\u00fcllt die Netze"
                  },
                  "text": {
                    "en_us": "The rivers are thick with the seasonal salmon run, and the fishing fleets are hauling in more than the smokehouses can cure. Barrels of fresh and cooked salmon are stacking up on every quay, and prices are sliding as the catch outpaces demand. Old hands know the glut lasts only as long as the fish keep running.",
                    "de_de": "Die Fl\\u00fcsse sind dicht vom jahreszeitlichen Lachszug, und die Fischerflotten holen mehr ein, als die R\\u00e4uchereien p\\u00f6keln k\\u00f6nnen. F\\u00e4sser mit frischem und gebratenem Lachs stapeln sich an jedem Kai, und die Preise rutschen, da der Fang die Nachfrage \\u00fcbertrifft. Alte Hasen wissen, dass die Schwemme nur so lange anh\\u00e4lt, wie die Fische ziehen."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 5400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.25,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:salmon",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:cooked_salmon",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "cod_overfishing_crisis",
                  "picture": "cod_overfishing_crisis.png",
                  "headline": {
                    "en_us": "Overfishing collapses the cod grounds for good",
                    "de_de": "\\u00dcberfischung l\\u00e4sst die Dorschgr\\u00fcnde f\\u00fcr immer zusammenbrechen"
                  },
                  "text": {
                    "en_us": "Years of dragging the shoals dry have finally emptied the great cod grounds, and this season the fleets returned with holds barely half full. Fishmongers say the collapse is no passing lean spell but a lasting one - the breeding stock is simply gone. Prices for cod, fresh and cooked, have settled onto a permanently higher shelf.",
                    "de_de": "Jahrelanges Leerfischen der Schw\\u00e4rme hat die gro\\u00dfen Dorschgr\\u00fcnde endg\\u00fcltig ausgezehrt, und diese Saison kehrten die Flotten mit kaum halb gef\\u00fcllten Lader\\u00e4umen zur\\u00fcck. Fischh\\u00e4ndler sagen, der Zusammenbruch sei keine vor\\u00fcbergehende Flaute, sondern von Dauer - der Laichbestand ist schlicht dahin. Die Preise f\\u00fcr Dorsch, frisch wie gebraten, haben sich auf einem dauerhaft h\\u00f6heren Niveau eingependelt."
                  },
                  "category": "disaster",
                  "weight": 3,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:cod",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:cooked_cod",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "tropical_fish_aquarium_fad",
                  "picture": "tropical_fish_aquarium_fad.png",
                  "headline": {
                    "en_us": "Aquarium fad grips the wealthy",
                    "de_de": "Aquarienmode packt die Wohlhabenden"
                  },
                  "text": {
                    "en_us": "A craze for glass tanks of glittering tropical fish has swept the manor houses, and no gentry parlour is complete without one. Collectors are paying dizzying sums for the brightest specimens, and divers cannot bring them ashore fast enough. Prices for tropical fish are climbing as fast as the fashion spreads.",
                    "de_de": "Eine Begeisterung f\\u00fcr gl\\u00e4serne Becken mit schillernden tropischen Fischen hat die Herrenh\\u00e4user erfasst, und kein Salon der feinen Leute kommt mehr ohne eines aus. Sammler zahlen schwindelerregende Summen f\\u00fcr die pr\\u00e4chtigsten Exemplare, und die Taucher bringen sie kaum schnell genug an Land. Die Preise f\\u00fcr tropische Fische klettern so rasch, wie sich die Mode verbreitet."
                  },
                  "category": "cultural",
                  "weight": 5,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.32,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:tropical_fish",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "henhouse_fire_egg_shortage",
                  "picture": "henhouse_fire_egg_shortage.png",
                  "headline": {
                    "en_us": "Henhouse fire wipes out laying flocks - eggs vanish!",
                    "de_de": "H\\u00fchnerstallbrand vernichtet die Legescharen - Eier verschwinden!"
                  },
                  "text": {
                    "en_us": "A fire that tore through the district's henhouses overnight has left the laying flocks in ashes, and baskets stood empty at market before word of the blaze had even spread. Bakers scrambling for eggs found prices already leaping, as if the shortage had been felt before it was announced. It will be weeks before new pullets come into lay.",
                    "de_de": "Ein Feuer, das \\u00fcber Nacht durch die H\\u00fchnerst\\u00e4lle des Viertels raste, hat die Legescharen in Asche gelegt, und die K\\u00f6rbe standen schon leer am Markt, ehe sich die Kunde vom Brand \\u00fcberhaupt verbreitet hatte. B\\u00e4cker, die nach Eiern jagten, fanden die Preise bereits im Sprung vor, als h\\u00e4tte man die Knappheit gesp\\u00fcrt, bevor sie verk\\u00fcndet wurde. Es wird Wochen dauern, bis neue Junghennen zu legen beginnen."
                  },
                  "category": "disaster",
                  "weight": 4,
                  "cooldownSeconds": 7200,
                  "announceDelayMs": {
                    "min": -45000,
                    "max": -15000
                  },
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:egg",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "fletcher_feather_demand",
                  "picture": "fletcher_feather_demand.png",
                  "headline": {
                    "en_us": "Archery muster empties the fletchers' feather stores",
                    "de_de": "Bogensch\\u00fctzen-Aufgebot leert die Federlager der Pfeilmacher"
                  },
                  "text": {
                    "en_us": "With the muster called and every able archer ordered to the butts, the fletchers are working through the night and their bins of goose feathers are running dry. Buyers are offering above the going rate for any bundle of quills that comes to market. Until the flocks moult again, feathers will stay dear.",
                    "de_de": "Da das Aufgebot einberufen und jeder taugliche Bogensch\\u00fctze an die Schie\\u00dfst\\u00e4nde beordert ist, arbeiten die Pfeilmacher die N\\u00e4chte durch, und ihre K\\u00e4sten mit G\\u00e4nsefedern laufen leer. K\\u00e4ufer bieten \\u00fcber dem \\u00fcblichen Preis f\\u00fcr jedes B\\u00fcndel Federkiele, das auf den Markt kommt. Bis die Schw\\u00e4rme wieder mausern, bleiben Federn teuer."
                  },
                  "category": "conflict",
                  "weight": 5,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 750,
                    "durationSeconds": 2250,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:feather",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "ink_dye_market_crash",
                  "picture": "ink_dye_market_crash.png",
                  "headline": {
                    "en_us": "Black-dye trade collapses as fashion turns pale",
                    "de_de": "Handel mit Schwarzf\\u00e4rbemitteln bricht ein, als die Mode ins Helle kippt"
                  },
                  "text": {
                    "en_us": "The season's taste has swung hard toward pale and undyed cloth, and the once-coveted black dye has fallen out of favour overnight. Warehouses full of ink sacs and finished black dye have lost their buyers, and dyers are dumping stock at any price. Pale and white dyes, by contrast, are suddenly the colour everyone wants.",
                    "de_de": "Der Geschmack der Saison ist hart zu blassem und ungef\\u00e4rbtem Tuch umgeschwenkt, und das einst begehrte Schwarz ist \\u00fcber Nacht aus der Mode gefallen. Lagerh\\u00e4user voller Tintenbeutel und fertigem Schwarzf\\u00e4rbemittel haben ihre K\\u00e4ufer verloren, und die F\\u00e4rber verramschen ihre Best\\u00e4nde zu jedem Preis. Blasse und wei\\u00dfe Farben hingegen sind mit einem Mal das, was alle wollen."
                  },
                  "category": "markets",
                  "weight": 4,
                  "cooldownSeconds": 14400,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.4,
                    "durationSeconds": 900,
                    "reversal": "exponential",
                    "reversalSeconds": 6000,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:ink_sac",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:black_dye",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:white_dye",
                      "weightFactor": -0.3
                    }
                  ]
                },
                {
                  "id": "rabbit_stew_winter_craze",
                  "picture": "rabbit_stew_winter_craze.png",
                  "headline": {
                    "en_us": "Winter craze for rabbit stew empties the warrens",
                    "de_de": "Winterrausch um Kanincheneintopf leert die Bauten"
                  },
                  "text": {
                    "en_us": "A hard frost and a fashion for hot rabbit stew have set every inn and household clamouring for coneys, and the warrens can scarcely keep up. Rabbit, cooked meat and even hides for winter linings are all fetching more by the week. Mutton sellers complain that the stewpots have stolen their custom for the season.",
                    "de_de": "Ein strenger Frost und die Mode f\\u00fcr hei\\u00dfen Kanincheneintopf haben jedes Wirtshaus und jeden Haushalt nach Karnickeln l\\u00e4rmen lassen, und die Bauten kommen kaum nach. Kaninchen, gebratenes Fleisch und selbst Felle f\\u00fcr Winterf\\u00fctterungen erzielen Woche f\\u00fcr Woche mehr. Hammelverk\\u00e4ufer beklagen, dass ihnen die Eintopft\\u00f6pfe f\\u00fcr diese Saison die Kundschaft gestohlen haben."
                  },
                  "category": "cultural",
                  "weight": 4,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 1050,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:rabbit",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:cooked_rabbit",
                      "weightFactor": 0.7
                    },
                    {
                      "item": "minecraft:rabbit_hide",
                      "weightFactor": 0.4
                    },
                    {
                      "item": "minecraft:mutton",
                      "weightFactor": -0.25
                    }
                  ]
                },
                {
                  "id": "blaze_rod_brewing_rush",
                  "picture": "blaze_rod_brewing_rush.png",
                  "headline": {
                    "en_us": "Alchemists' guild empties the blaze-rod stalls!",
                    "de_de": "Alchemistengilde leert die Lohenruten-St\\u00e4nde!"
                  },
                  "text": {
                    "en_us": "The alchemists' guild has placed a standing order for every blaze rod the delvers can drag back from the Nether. Brewing stands are firing day and night, and rod and powder alike are climbing as the guild's buyers outbid one another.",
                    "de_de": "Die Alchemistengilde hat eine Dauerbestellung auf jede Lohenrute aufgegeben, die die Delver aus dem Nether schleppen k\\u00f6nnen. Die Braust\\u00e4nde laufen Tag und Nacht, und sowohl Ruten als auch Staub steigen, w\\u00e4hrend sich die Eink\\u00e4ufer der Gilde gegenseitig \\u00fcberbieten."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.32,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:blaze_rod",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:blaze_powder",
                      "weightFactor": 0.7
                    }
                  ]
                },
                {
                  "id": "ender_pearl_expedition_boom",
                  "picture": "ender_pearl_expedition_boom.png",
                  "headline": {
                    "en_us": "Expedition fever drives ender-pearl demand sky-high",
                    "de_de": "Expeditionsfieber treibt die Nachfrage nach Enderperlen in die H\\u00f6he"
                  },
                  "text": {
                    "en_us": "A wave of frontier expeditions is setting out for the far reaches, and every party wants pearls for the leap home. With ender eyes needed to chart the way, brokers report pearls and eyes alike snapped up faster than the delvers can gather them.",
                    "de_de": "Eine Welle von Grenzland-Expeditionen bricht in die fernen Weiten auf, und jede Gruppe will Perlen f\\u00fcr den Sprung nach Hause. Da auch Enderaugen zum Kartieren des Weges gebraucht werden, melden Makler, dass Perlen und Augen schneller vergriffen sind, als die Delver sie sammeln k\\u00f6nnen."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2100,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:ender_pearl",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:ender_eye",
                      "weightFactor": 0.6
                    }
                  ]
                },
            """;

    private static final String DEFAULT_EVENTS_JSON_4 = """
                {
                  "id": "ghast_tear_apothecary_craze",
                  "picture": "ghast_tear_apothecary_craze.png",
                  "headline": {
                    "en_us": "Apothecaries bid up ghast tears for healing tonics!",
                    "de_de": "Apotheker treiben die Preise f\\u00fcr Ghast-Tr\\u00e4nen f\\u00fcr Heiltr\\u00e4nke hoch!"
                  },
                  "text": {
                    "en_us": "A sudden craze for regeneration tonics has the apothecaries' guild bidding furiously on every ghast tear that reaches the market. Brave delvers who face the wailing beasts of the Nether are being paid a king's ransom, and prices have spiked overnight.",
                    "de_de": "Ein pl\\u00f6tzlicher Rausch um Regenerationstr\\u00e4nke l\\u00e4sst die Apothekergilde erbittert um jede Ghast-Tr\\u00e4ne bieten, die den Markt erreicht. Mutige Delver, die sich den heulenden Bestien des Nethers stellen, werden f\\u00fcrstlich entlohnt, und die Preise sind \\u00fcber Nacht in die H\\u00f6he geschossen."
                  },
                  "category": "commodities",
                  "weight": 4,
                  "cooldownSeconds": 14400,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:ghast_tear",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "shulker_shell_shortage",
                  "picture": "shulker_shell_shortage.png",
                  "headline": {
                    "en_us": "End raids stall - shulker shells scarce for good",
                    "de_de": "End-Raubz\\u00fcge stocken - Shulker-Schalen dauerhaft knapp"
                  },
                  "text": {
                    "en_us": "The raiding parties that once stripped the End cities have stalled, and the flow of shulker shells has all but dried up. With no new supply in sight, traders warn that the shortage is structural - the elevated price is here to stay.",
                    "de_de": "Die Raubz\\u00fcge, die einst die End-St\\u00e4dte pl\\u00fcnderten, sind ins Stocken geraten, und der Nachschub an Shulker-Schalen ist so gut wie versiegt. Da kein neues Angebot in Sicht ist, warnen H\\u00e4ndler, dass die Knappheit struktureller Natur ist - der erh\\u00f6hte Preis bleibt."
                  },
                  "category": "commodities",
                  "weight": 3,
                  "cooldownSeconds": 43200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:shulker_shell",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "chorus_fruit_bumper_harvest",
                  "picture": "chorus_fruit_bumper_harvest.png",
                  "headline": {
                    "en_us": "Outer islands yield a chorus-fruit glut",
                    "de_de": "\\u00c4u\\u00dfere Inseln bringen eine Chorusfrucht-Schwemme"
                  },
                  "text": {
                    "en_us": "The chorus plants of the outer End islands have grown wild this season, and returning delvers are hauling back more fruit than the markets can absorb. With popped chorus fruit piling up beside it in the warehouses, prices are drifting steadily lower.",
                    "de_de": "Die Choruspflanzen der \\u00e4u\\u00dferen End-Inseln sind in dieser Saison wild gewuchert, und heimkehrende Delver bringen mehr Fr\\u00fcchte zur\\u00fcck, als die M\\u00e4rkte aufnehmen k\\u00f6nnen. Da sich geplatzte Chorusfr\\u00fcchte daneben in den Lagern stapeln, geben die Preise stetig nach."
                  },
                  "category": "commodities",
                  "weight": 5,
                  "cooldownSeconds": 7200,
                  "impact": {
                    "type": "trend",
                    "peakFactor": -0.25,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2700,
                    "reversal": "ramp",
                    "reversalSeconds": 2400,
                    "noise": 0.03
                  },
                  "markets": [
                    {
                      "item": "minecraft:chorus_fruit",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:popped_chorus_fruit",
                      "weightFactor": 0.7
                    }
                  ]
                },
                {
                  "id": "magma_cream_alchemy_demand",
                  "picture": "magma_cream_alchemy_demand.png",
                  "headline": {
                    "en_us": "Fire-resistance brewing drives magma-cream demand",
                    "de_de": "Feuerresistenz-Brauerei treibt die Magmacreme-Nachfrage"
                  },
                  "text": {
                    "en_us": "With expeditions pushing ever deeper into the lava seas, brewers cannot mix fire-resistance potions fast enough. Magma cream is the key ingredient, and the alchemists' guild is buying every batch the slime-and-blaze hunters can render down.",
                    "de_de": "Da Expeditionen immer tiefer in die Lavameere vordringen, kommen die Brauer mit dem Mischen von Feuerresistenz-Tr\\u00e4nken nicht hinterher. Magmacreme ist die Schl\\u00fcsselzutat, und die Alchemistengilde kauft jede Charge auf, die die Schleim- und Lohenj\\u00e4ger auslassen k\\u00f6nnen."
                  },
                  "category": "commodities",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.26,
                    "rampUpSeconds": 750,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:magma_cream",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "phantom_membrane_repair_run",
                  "picture": "phantom_membrane_repair_run.png",
                  "headline": {
                    "en_us": "Rush to repair elytra empties membrane stocks",
                    "de_de": "Ansturm auf Elytren-Reparaturen leert die Membranvorr\\u00e4te"
                  },
                  "text": {
                    "en_us": "After a season of hard flying, every glider-pilot in the realm is scrambling to patch worn elytra - and only phantom membrane will do the job. Night-hunters cannot down the phantoms fast enough, and membrane prices are climbing with every torn wing.",
                    "de_de": "Nach einer Saison harten Fliegens versucht jeder Gleiter-Pilot des Reiches, abgenutzte Elytren zu flicken - und nur Phantommembranen taugen daf\\u00fcr. Die Nachtj\\u00e4ger k\\u00f6nnen die Phantome nicht schnell genug erlegen, und die Membranpreise klettern mit jedem zerrissenen Fl\\u00fcgel."
                  },
                  "category": "commodities",
                  "weight": 5,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2100,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:phantom_membrane",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "end_expedition_returns",
                  "picture": "end_expedition_returns.png",
                  "headline": {
                    "en_us": "Triumphant End expedition floods the market with relics!",
                    "de_de": "Triumphale End-Expedition \\u00fcberschwemmt den Markt mit Relikten!"
                  },
                  "text": {
                    "en_us": "A great expedition has returned from the End in triumph, its ships laden with elytra and vials of dragon's breath. The sudden flood of once-priceless relics has sent prices crashing as buyers realize the scarcity is broken - at least for now.",
                    "de_de": "Eine gro\\u00dfe Expedition ist im Triumph aus dem End zur\\u00fcckgekehrt, ihre Schiffe beladen mit Elytren und Fl\\u00e4schchen voll Drachenatem. Die pl\\u00f6tzliche Schwemme einst unbezahlbarer Relikte hat die Preise abst\\u00fcrzen lassen, als den K\\u00e4ufern klar wurde, dass die Knappheit gebrochen ist - zumindest vorerst."
                  },
                  "category": "commodities",
                  "weight": 2,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.4,
                    "rampUpSeconds": 300,
                    "durationSeconds": 1200,
                    "reversal": "exponential",
                    "reversalSeconds": 6000,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:elytra",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:dragon_breath",
                      "weightFactor": 0.6
                    }
                  ],
                  "requires": [
                    {
                      "type": "notFired",
                      "eventId": "end_expedition_returns"
                    }
                  ],
                  "records": {
                    "frontier": "end_breached"
                  }
                },
                {
                  "id": "crying_obsidian_anchor_fad",
                  "picture": "crying_obsidian_anchor_fad.png",
                  "headline": {
                    "en_us": "Respawn anchors become the must-have - crying obsidian soars",
                    "de_de": "Seelenanker werden zum Muss - Weinender Obsidian schie\\u00dft hoch"
                  },
                  "text": {
                    "en_us": "No frontier camp is complete without a respawn anchor this season, and the craze has sent crying obsidian to dizzy heights. With everyone abandoning their old beds for the glowing anchors, bed-makers are watching demand quietly slip away.",
                    "de_de": "Kein Grenzlager gilt in dieser Saison als vollst\\u00e4ndig ohne Seelenanker, und die Mode hat Weinenden Obsidian in schwindelerregende H\\u00f6hen getrieben. Da alle ihre alten Betten f\\u00fcr die leuchtenden Anker aufgeben, sehen die Bettmacher zu, wie ihnen die Nachfrage stillschweigend entgleitet."
                  },
                  "category": "cultural",
                  "weight": 5,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:crying_obsidian",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:*_bed",
                      "weightFactor": -0.15
                    }
                  ]
                },
                {
                  "id": "wither_skull_bounty",
                  "picture": "wither_skull_bounty.png",
                  "headline": {
                    "en_us": "Warlord posts a bounty on wither skulls!",
                    "de_de": "Kriegsherr setzt ein Kopfgeld auf Witherskelett-Sch\\u00e4del aus!"
                  },
                  "text": {
                    "en_us": "A warlord massing for war has posted a lavish bounty on every wither-skeleton skull that can be brought to his fortress. Soul sand is being bought up alongside them to raise the beast itself, and both have shot up as mercenaries pour into the Nether fortresses.",
                    "de_de": "Ein Kriegsherr, der zum Krieg r\\u00fcstet, hat ein \\u00fcppiges Kopfgeld auf jeden Witherskelett-Sch\\u00e4del ausgesetzt, der zu seiner Festung gebracht wird. Seelensand wird gleich mitgekauft, um die Bestie selbst zu beschw\\u00f6ren, und beide sind in die H\\u00f6he geschossen, w\\u00e4hrend S\\u00f6ldner in die Netherfestungen str\\u00f6men."
                  },
                  "category": "conflict",
                  "weight": 3,
                  "cooldownSeconds": 21600,
                  "adminOnly": true,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.45,
                    "durationSeconds": 1800,
                    "reversal": "exponential",
                    "reversalSeconds": 4500,
                    "noise": 0.06
                  },
                  "markets": [
                    {
                      "item": "minecraft:wither_skeleton_skull",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:soul_sand",
                      "weightFactor": 0.4
                    }
                  ]
                },
                {
                  "id": "mint_recoinage_reform",
                  "picture": "mint_recoinage_reform.png",
                  "headline": {
                    "en_us": "The mint recoins the realm's currency!",
                    "de_de": "Die M\\u00fcnze pr\\u00e4gt das Reichsgeld neu!"
                  },
                  "text": {
                    "en_us": "By decree of the trade council the royal mint has begun recoining every coin in circulation to a heavier gold-and-iron standard. Every old piece must be melted and struck anew, and the mint's insatiable demand for metal is expected to keep prices structurally elevated for good.",
                    "de_de": "Auf Erlass des Handelsrats hat die k\\u00f6nigliche M\\u00fcnze begonnen, jede umlaufende M\\u00fcnze auf einen schwereren Gold-und-Eisen-Standard umzupr\\u00e4gen. Jedes alte St\\u00fcck muss eingeschmolzen und neu geschlagen werden, und der unstillbare Metallhunger der M\\u00fcnze d\\u00fcrfte die Preise dauerhaft strukturell erh\\u00f6ht halten."
                  },
                  "category": "politics",
                  "weight": 2,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.28,
                    "rampUpSeconds": 1500,
                    "durationSeconds": 3000,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:iron_ingot",
                      "weightFactor": 0.6
                    }
                  ],
                  "requires": [
                    {
                      "type": "notFired",
                      "eventId": "mint_recoinage_reform"
                    }
                  ],
                  "records": {
                    "currency": "reformed"
                  }
                },
                {
                  "id": "dockworkers_strike",
                  "picture": "dockworkers_strike.png",
                  "headline": {
                    "en_us": "Dockworkers walk out - harbor cranes fall silent!",
                    "de_de": "Hafenarbeiter legen die Arbeit nieder - die Krane stehen still!"
                  },
                  "text": {
                    "en_us": "The dockworkers' guild has downed tools over withheld wages, and not a single crate now moves across the wharves. Metal and timber shipments are stranded on the quays, and merchants are already bidding up whatever iron, copper and planks remain inside the city walls.",
                    "de_de": "Die Zunft der Hafenarbeiter hat wegen einbehaltener L\\u00f6hne die Arbeit niedergelegt, und keine einzige Kiste bewegt sich mehr \\u00fcber die Kais. Metall- und Holzlieferungen stranden an den Anlegern, und H\\u00e4ndler treiben bereits die Preise f\\u00fcr alles Eisen, Kupfer und Bauholz in die H\\u00f6he, das noch innerhalb der Stadtmauern liegt."
                  },
                  "category": "labor",
                  "weight": 6,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1800,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:iron_ingot",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:copper_ingot",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "#minecraft:planks",
                      "weightFactor": 0.4
                    }
                  ]
                },
                {
                  "id": "metals_export_embargo",
                  "picture": "metals_export_embargo.png",
                  "headline": {
                    "en_us": "Trade council embargoes all foreign metals!",
                    "de_de": "Handelsrat verh\\u00e4ngt Embargo auf alle ausl\\u00e4ndischen Metalle!"
                  },
                  "text": {
                    "en_us": "In a bid to protect the home foundries the trade council has slammed an embargo on every foreign metal crossing the border. With cheap imports cut off, domestic iron, copper and gold lose their foreign competition - analysts expect home prices to settle permanently higher.",
                    "de_de": "Um die heimischen Gie\\u00dfereien zu sch\\u00fctzen, hat der Handelsrat ein Embargo auf jedes ausl\\u00e4ndische Metall verh\\u00e4ngt, das die Grenze \\u00fcberquert. Da die billigen Einfuhren wegfallen, verlieren heimisches Eisen, Kupfer und Gold ihre ausl\\u00e4ndische Konkurrenz - Analysten erwarten dauerhaft h\\u00f6here Inlandspreise."
                  },
                  "category": "politics",
                  "weight": 2,
                  "cooldownSeconds": 86400,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.24,
                    "rampUpSeconds": 1200,
                    "durationSeconds": 2700,
                    "reversal": "none"
                  },
                  "markets": [
                    {
                      "item": "minecraft:iron_ingot",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:copper_ingot",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "treasury_stimulus_windfall",
                  "picture": "treasury_stimulus_windfall.png",
                  "headline": {
                    "en_us": "Royal treasury floods the market with fresh coin",
                    "de_de": "K\\u00f6nigliche Schatzkammer \\u00fcberschwemmt den Markt mit frischem Geld"
                  },
                  "text": {
                    "en_us": "The royal treasury has opened its vaults, pouring newly minted coin into the markets to spur trade after a long lean season. With coin suddenly cheap and plentiful, merchants are rushing into hard assets - gems and gold above all - to preserve their wealth before the money loses its shine.",
                    "de_de": "Die k\\u00f6nigliche Schatzkammer hat ihre Gew\\u00f6lbe ge\\u00f6ffnet und sch\\u00fcttet frisch gepr\\u00e4gtes Geld in die M\\u00e4rkte, um den Handel nach einer langen mageren Zeit anzukurbeln. Da M\\u00fcnzen pl\\u00f6tzlich billig und reichlich vorhanden sind, fl\\u00fcchten H\\u00e4ndler in Sachwerte - vor allem Edelsteine und Gold -, um ihr Verm\\u00f6gen zu sichern, bevor das Geld seinen Glanz verliert."
                  },
                  "category": "economy",
                  "weight": 6,
                  "cooldownSeconds": 14400,
                  "announceDelayMs": {
                    "min": 30000,
                    "max": 90000
                  },
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 3000,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:diamond",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:emerald",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:iron_ingot",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "counterfeit_coin_scandal",
                  "picture": "counterfeit_coin_scandal.png",
                  "headline": {
                    "en_us": "Counterfeit gold coin floods the market - trust collapses!",
                    "de_de": "Gef\\u00e4lschte Goldm\\u00fcnzen \\u00fcberschwemmen den Markt - das Vertrauen bricht ein!"
                  },
                  "text": {
                    "en_us": "A smuggling ring has flooded the realm with expertly forged gold coin, and no one can tell true metal from lead-cored fakes. Assayers are refusing gold at the counter until every hoard is tested, and the panic to dump suspect coin has sent the gold market into free fall.",
                    "de_de": "Ein Schmugglerring hat das Reich mit meisterhaft gef\\u00e4lschten Goldm\\u00fcnzen \\u00fcberschwemmt, und niemand kann echtes Metall von bleigef\\u00fcllten F\\u00e4lschungen unterscheiden. Pr\\u00fcfmeister verweigern Gold am Tresen, bis jeder Hort gepr\\u00fcft ist, und die Panik, verd\\u00e4chtige M\\u00fcnzen abzusto\\u00dfen, hat den Goldmarkt in den freien Fall gest\\u00fcrzt."
                  },
                  "category": "crime",
                  "weight": 3,
                  "cooldownSeconds": 21600,
                  "adminOnly": true,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.42,
                    "durationSeconds": 900,
                    "reversal": "exponential",
                    "reversalSeconds": 5000,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 1.0
                    }
                  ]
                },
                {
                  "id": "gemstone_heist",
                  "picture": "gemstone_heist.png",
                  "headline": {
                    "en_us": "Daring vault heist empties the gem exchange!",
                    "de_de": "K\\u00fchner Tresorraub leert die Edelsteinb\\u00f6rse!"
                  },
                  "text": {
                    "en_us": "Thieves cracked the gem exchange's deepest vault overnight and made off with its entire diamond and emerald reserve. By the time the theft was announced, prices had already lurched upward - insiders, it seems, knew the stones were gone before the watch ever raised the alarm.",
                    "de_de": "Diebe knackten \\u00fcber Nacht das tiefste Gew\\u00f6lbe der Edelsteinb\\u00f6rse und machten sich mit ihrer gesamten Diamant- und Smaragdreserve davon. Als der Diebstahl bekannt gegeben wurde, waren die Preise bereits nach oben gesprungen - Eingeweihte wussten offenbar vom Verschwinden der Steine, bevor die Wache \\u00fcberhaupt Alarm schlug."
                  },
                  "category": "crime",
                  "weight": 4,
                  "cooldownSeconds": 14400,
                  "announceDelayMs": {
                    "min": -45000,
                    "max": -15000
                  },
                  "impact": {
                    "type": "shock",
                    "peakFactor": 0.4,
                    "durationSeconds": 1500,
                    "reversal": "exponential",
                    "reversalSeconds": 3600,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:diamond",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:emerald",
                      "weightFactor": 0.8
                    }
                  ]
                },
                {
                  "id": "royal_purple_fashion",
                  "picture": "royal_purple_fashion.png",
                  "headline": {
                    "en_us": "Court adopts royal purple - dye prices soar!",
                    "de_de": "Hof erhebt K\\u00f6nigspurpur zur Mode - Farbpreise schnellen empor!"
                  },
                  "text": {
                    "en_us": "The queen has declared royal purple the colour of the season, and every noble house is scrambling to re-dye its wardrobe before the next gala. Dyers cannot keep up with the orders, and prices for purple, magenta and pink dyes are climbing by the day as the fashion sweeps the court.",
                    "de_de": "Die K\\u00f6nigin hat K\\u00f6nigspurpur zur Farbe der Saison erkl\\u00e4rt, und jedes Adelshaus wetteifert darum, seine Garderobe vor der n\\u00e4chsten Gala umzuf\\u00e4rben. Die F\\u00e4rber kommen mit den Auftr\\u00e4gen nicht nach, und die Preise f\\u00fcr Purpur-, Magenta- und Rosafarbstoffe steigen von Tag zu Tag, w\\u00e4hrend die Mode \\u00fcber den Hof fegt."
                  },
                  "category": "cultural",
                  "weight": 5,
                  "cooldownSeconds": 10800,
                  "impact": {
                    "type": "trend",
                    "peakFactor": 0.3,
                    "rampUpSeconds": 900,
                    "durationSeconds": 2400,
                    "reversal": "ramp",
                    "reversalSeconds": 2700,
                    "noise": 0.04
                  },
                  "markets": [
                    {
                      "item": "minecraft:purple_dye",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:magenta_dye",
                      "weightFactor": 0.7
                    },
                    {
                      "item": "minecraft:pink_dye",
                      "weightFactor": 0.5
                    }
                  ]
                },
                {
                  "id": "luxury_goods_tax",
                  "picture": "luxury_goods_tax.png",
                  "headline": {
                    "en_us": "Council levies steep luxury tax on gems and gold",
                    "de_de": "Rat erhebt saftige Luxussteuer auf Edelsteine und Gold"
                  },
                  "text": {
                    "en_us": "To fill the war chest the trade council has slapped a punishing luxury tax on every sale of diamonds, emeralds and gold. Buyers are balking at the new surcharge, demand for finery is cooling sharply, and analysts expect the chill on luxury prices to become a lasting fixture of the market.",
                    "de_de": "Um die Kriegskasse zu f\\u00fcllen, hat der Handelsrat eine dr\\u00fcckende Luxussteuer auf jeden Verkauf von Diamanten, Smaragden und Gold verh\\u00e4ngt. K\\u00e4ufer schrecken vor dem neuen Aufschlag zur\\u00fcck, die Nachfrage nach Prunk k\\u00fchlt merklich ab, und Analysten erwarten, dass die Preisd\\u00e4mpfung im Luxussegment zu einer dauerhaften Erscheinung am Markt wird."
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
                    {
                      "item": "minecraft:diamond",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:emerald",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 0.6
                    }
                  ]
                },
                {
                  "id": "speculation_mania",
                  "picture": "speculation_mania.png",
                  "headline": {
                    "en_us": "Speculative mania grips the trading floor!",
                    "de_de": "Spekulationsrausch erfasst das Parkett!"
                  },
                  "text": {
                    "en_us": "A frenzy has seized the trading floor - clerks and courtiers alike are borrowing against their homes to pile into diamonds, emeralds and gold. Seasoned brokers mutter that manias like this always end the same way, but for now the only word on every tongue is 'buy'.",
                    "de_de": "Ein Rausch hat das Parkett gepackt - Schreiber wie H\\u00f6flinge beleihen ihre H\\u00e4user, um sich in Diamanten, Smaragde und Gold zu st\\u00fcrzen. Erfahrene Makler murmeln, dass solche Manien immer gleich enden, doch vorerst kennt jeder Mund nur ein Wort: 'kaufen'."
                  },
                  "category": "rumors",
                  "weight": 4,
                  "cooldownSeconds": 14400,
                  "sequences": [
                    {
                      "name": "euphoria",
                      "weight": 2,
                      "steps": [
                        {
                          "name": "buildup",
                          "durationSeconds": {
                            "min": 600,
                            "max": 1500
                          },
                          "targetFactor": 0.35,
                          "curve": "linear",
                          "noise": 0.05
                        },
                        {
                          "name": "blowoff",
                          "durationSeconds": {
                            "min": 300,
                            "max": 750
                          },
                          "curve": "hold",
                          "noise": 0.08
                        },
                        {
                          "name": "crash",
                          "durationSeconds": {
                            "min": 150,
                            "max": 450
                          },
                          "targetFactor": -0.25,
                          "curve": "exponential",
                          "noise": 0.06
                        },
                        {
                          "name": "settle",
                          "durationSeconds": 1500,
                          "targetFactor": 0.0,
                          "curve": "linear"
                        }
                      ]
                    },
                    {
                      "name": "cooler_heads",
                      "weight": 1,
                      "steps": [
                        {
                          "name": "rally",
                          "durationSeconds": {
                            "min": 450,
                            "max": 900
                          },
                          "targetFactor": 0.18,
                          "curve": "linear",
                          "noise": 0.04
                        },
                        {
                          "name": "fade",
                          "durationSeconds": {
                            "min": 600,
                            "max": 1200
                          },
                          "targetFactor": 0.0,
                          "curve": "exponential"
                        }
                      ]
                    }
                  ],
                  "markets": [
                    {
                      "item": "minecraft:diamond",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:emerald",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 0.6
                    }
                  ],
                  "chains": [
                    {
                      "eventId": "margin_call_crash",
                      "on": "completion",
                      "chance": 0.6,
                      "delaySeconds": {
                        "min": 300,
                        "max": 900
                      }
                    }
                  ]
                },
                {
                  "id": "margin_call_crash",
                  "picture": "margin_call_crash.png",
                  "headline": {
                    "en_us": "Margin calls wipe out overleveraged traders in a cascade!",
                    "de_de": "Nachschussforderungen radieren \\u00fcberhebelte H\\u00e4ndler in einer Kettenreaktion aus!"
                  },
                  "text": {
                    "en_us": "The lenders have called in their loans all at once, and traders who bought the mania on borrowed coin cannot pay. Forced liquidations are feeding on themselves - every sale drives the price lower, triggering the next margin call - and diamonds, emeralds and gold are cascading downward with no floor in sight.",
                    "de_de": "Die Geldverleiher haben ihre Kredite alle auf einmal f\\u00e4llig gestellt, und H\\u00e4ndler, die den Rausch auf Pump gekauft haben, k\\u00f6nnen nicht zahlen. Zwangsverk\\u00e4ufe n\\u00e4hren sich selbst - jeder Verkauf dr\\u00fcckt den Preis tiefer und l\\u00f6st die n\\u00e4chste Nachschussforderung aus -, und Diamanten, Smaragde und Gold st\\u00fcrzen kaskadenartig ins Bodenlose."
                  },
                  "category": "markets",
                  "weight": 0,
                  "cooldownSeconds": 14400,
                  "impact": {
                    "type": "crash",
                    "peakFactor": -0.45,
                    "durationSeconds": 900,
                    "reversal": "exponential",
                    "reversalSeconds": 6000,
                    "noise": 0.05
                  },
                  "markets": [
                    {
                      "item": "minecraft:diamond",
                      "weightFactor": 1.0
                    },
                    {
                      "item": "minecraft:emerald",
                      "weightFactor": 0.8
                    },
                    {
                      "item": "minecraft:gold_ingot",
                      "weightFactor": 0.6
                    }
                  ],
                  "requires": [
                    {
                      "type": "firedBefore",
                      "eventId": "speculation_mania",
                      "minSecondsAgo": 60
                    }
                  ]
                }
              ]
            }
            """;

    /**
     * The shipped defaults. Schema reference (see NewsEventSystem plan §1):
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
     *   <li>{@code chains[]} — follow-up activations triggered on
     *       {@code publish|step|completion} with a chance and a delay range.</li>
     * </ul>
     */
    private static final String DEFAULT_EVENTS_JSON =
            String.join("", DEFAULT_EVENTS_JSON_1, DEFAULT_EVENTS_JSON_2,
                    DEFAULT_EVENTS_JSON_3, DEFAULT_EVENTS_JSON_4);
}
