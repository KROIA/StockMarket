package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
 * <b>Existing installs:</b> the file is only generated when the news folder contains no
 * {@code .json} — servers that predate the {@code picture} field keep their old
 * {@code default_events.json}. Admins can delete it to regenerate the current version,
 * or add {@code "picture": "<eventId>.png"} lines manually (the default pictures are
 * still extracted into {@code pictures/} independently).
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
            "lumber_construction_boom");

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
    private static final String DEFAULT_EVENTS_JSON = """
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
                    "rampUpSeconds": 120,
                    "durationSeconds": 600,
                    "reversal": "exponential",
                    "reversalSeconds": 900,
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
                    "durationSeconds": 240,
                    "reversalSeconds": 420
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
                    "durationSeconds": 180,
                    "reversal": "exponential",
                    "reversalSeconds": 1200
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
                    "rampUpSeconds": 300,
                    "durationSeconds": 600,
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
                        { "name": "hype",     "durationSeconds": { "min": 90, "max": 240 }, "targetFactor": 0.4,   "curve": "linear",      "noise": 0.05 },
                        { "name": "peak",     "durationSeconds": { "min": 60, "max": 120 },                        "curve": "hold",        "noise": 0.08 },
                        { "name": "sell_off", "durationSeconds": { "min": 20, "max": 60 },  "targetFactor": -0.15, "curve": "exponential", "noise": 0.04,
                          "markets": [
                            { "item": "minecraft:gold_ingot", "weightFactor": 1.0 },
                            { "item": "minecraft:raw_gold",   "weightFactor": 0.6 }
                          ] },
                        { "name": "recover",  "durationSeconds": 300, "targetFactor": 0.0, "curve": "linear" }
                      ]
                    },
                    {
                      "name": "fizzle",
                      "weight": 1,
                      "steps": [
                        { "name": "stir",   "durationSeconds": { "min": 60, "max": 120 }, "targetFactor": 0.15, "curve": "linear", "noise": 0.04 },
                        { "name": "denial", "durationSeconds": { "min": 60, "max": 180 }, "targetFactor": 0.0,  "curve": "exponential" }
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
                    "rampUpSeconds": 60,
                    "durationSeconds": 300,
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
                    "reversalSeconds": 900
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
                    "durationSeconds": 300,
                    "reversalSeconds": 600
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
                    "rampUpSeconds": 180,
                    "durationSeconds": 480,
                    "reversal": "ramp",
                    "reversalSeconds": 600
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
                    "rampUpSeconds": 150,
                    "durationSeconds": 450,
                    "reversal": "ramp",
                    "reversalSeconds": 450,
                    "noise": 0.03
                  },
                  "markets": [
                    { "item": "#minecraft:logs",     "weightFactor": 1.0 },
                    { "item": "minecraft:*_planks",  "weightFactor": 0.6 },
                    { "item": "minecraft:stone",     "weightFactor": -0.15 }
                  ]
                }
              ]
            }
            """;
}
