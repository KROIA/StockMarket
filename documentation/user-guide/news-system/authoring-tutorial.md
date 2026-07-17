# Authoring News Events: A Step-by-Step Tutorial

This tutorial walks a server admin or content creator through **writing their own news events** from scratch: JSON files that appear in the in-game newspaper, move real market prices, and can tell branching stories over multiple in-game days.

You do not need to touch Java, edit any built-in file, or restart the server -- everything lives in one folder and the mod hot-reloads it.

> Reference material this tutorial refers back to:
> - [News Event System Overview](overview.md) -- what players see, the plugin model.
> - [Configuring News Events](configuration.md) -- exhaustive JSON schema reference.
> - [Advanced News Events](advanced-events.md) -- sequences, requirements, chains.
> - [News Event Pictures](pictures.md) -- picture files, sizes, dither.

Where the reference docs are dense field-by-field tables, this page is a **narrative walkthrough** -- start at the top and add features section by section as you need them.

## Table of Contents

1. [What You'll Build](#1-what-youll-build)
2. [Prerequisites](#2-prerequisites)
3. [Your First Event: A Minimal Impact Event](#3-your-first-event-a-minimal-impact-event)
4. [Adding Item Matchers](#4-adding-item-matchers)
5. [Adding Translations](#5-adding-translations)
6. [Adding a Picture](#6-adding-a-picture)
7. [Announce Delay: Front-Running and Insider Windows](#7-announce-delay-front-running-and-insider-windows)
8. [Upgrading to Sequences](#8-upgrading-to-sequences)
9. [Adding Requirements](#9-adding-requirements)
10. [Adding Chains](#10-adding-chains)
11. [The Scheduler](#11-the-scheduler)
12. [Debugging Your Events](#12-debugging-your-events)
13. [Testing Safely](#13-testing-safely)
14. [Shipping Your Events](#14-shipping-your-events)

---

## 1. What You'll Build

By the end of this tutorial you will have authored a `.json` file with several events that:

- Appear in the in-game newspaper when a player clicks the **News** button (or right-clicks the newspaper item).
- Move the target price of specific markets in configurable ways -- shocks, trends, permanent shifts, and multi-act storylines.
- Support multiple languages inline.
- Show a custom picture printed with the headline.
- Optionally chain into follow-up events, forming little economic stories.

The final example builds a small "harvest cycle" storyline that fires once per in-game season, changes the wheat market and then unwinds itself two acts later -- a compact demonstration of every schema feature.

---

## 2. Prerequisites

You need:

- A running StockMarket-enabled server (single-player counts). See the [README](../../../README.md) for setup.
- StockMarket admin status on that server (`/stockmarket op`). Reload and trigger commands require it.
- A text editor -- anything from Notepad to VS Code works. UTF-8 with JSON syntax highlighting recommended.

Where files live:

```
<server-folder>/config/StockMarket/news/
    default_events.json          # example file, generated on first run
    pictures/                    # optional PNG files (see pictures.md)
```

Rules of the folder:

- **Any `*.json` file** in this folder is loaded. Filenames are irrelevant.
- **All files merge** into one event pool -- you can split your work across as many files as you like.
- Files are processed in **alphabetical order** (case-insensitive). Duplicate event ids are an error, later definition wins.
- The `default_events.json` is only regenerated when the folder contains **no** `.json` file at all -- it will not overwrite your own files.

To iterate on your file, edit it and run:

```
/stockmarket news reload
```

The reload prints a validation report to your chat and to the server log. Nothing about your existing world (active events, cooldowns, history) is disturbed.

---

## 3. Your First Event: A Minimal Impact Event

Let's write the simplest possible event: a headline that moves one market's price for a few minutes and comes back down. Create `config/StockMarket/news/my_events.json`:

```json
{
  "events": [
    {
      "id": "wheat_shortage",
      "headline": "Wheat blight strikes the eastern plains!",
      "text": "Farmers report entire fields lost to a fast-spreading blight. Bakers are already paying premium prices for the remaining stock.",
      "category": "agriculture",
      "weight": 5,
      "cooldownSeconds": 3600,
      "impact": {
        "type": "shock",
        "peakFactor": 0.5,
        "durationSeconds": 300,
        "reversalSeconds": 600
      },
      "markets": [
        { "item": "minecraft:wheat", "weightFactor": 1.0 }
      ]
    }
  ]
}
```

What each field does:

| Field | Meaning |
|-------|---------|
| `id` | Unique key across every JSON file on the server. Used by `/stockmarket news trigger`, cooldown persistence, and the history. Duplicates are an error. |
| `headline` | The bold line at the top of the newspaper entry (and the toast). |
| `text` | The article body. |
| `category` | A free-form label -- shown in the definition but not enforced against a list. Useful when you want to group your own events. |
| `weight` | Random-pick weight. Higher = more likely to be picked when the scheduler rolls. `0` = never fires randomly. |
| `cooldownSeconds` | Seconds the event stays ineligible after firing (pause-safe server time -- freezes with the server). |
| `impact.type` | Preset that supplies default shape values. `shock` = fast onset, exponential decay. Other presets: `trend` (slow build, linear return), `crash` (instant move, slow recovery). |
| `impact.peakFactor` | Multiplicative influence at peak. `0.5` = +50%, `-0.4` = -40%. Must be **greater than -1** (a factor of -1 would zero the price). |
| `impact.durationSeconds` | Time the influence stays at peak. |
| `impact.reversalSeconds` | For the `shock` preset this is the exponential decay time constant. |
| `markets[]` | Which markets are affected -- more in the next section. |

Save the file, then in-game (with admin status):

```
/stockmarket news reload
/stockmarket news trigger wheat_shortage
```

Right-click a newspaper (or open the news screen with `/stockmarket news`) and you should see your headline. The wheat market's target price is now being pushed up by 50%; the [TargetPriceBot](../../../README.md#targetpricebot) starts trading toward the shifted target, and you should see the price rise on the trading screen over the next minute or two.

To inspect the running event or the cooldown state at any time:

```
/stockmarket news list
```

Congratulations -- you have a working event. Everything else in this tutorial adds features on top of this base.

---

## 4. Adding Item Matchers

The `markets[]` array supports three forms of item selector. Combine them freely:

**Exact registry id** -- targets one specific market:

```json
{ "item": "minecraft:diamond", "weightFactor": 1.0 }
```

**Item tag** -- a `#` prefix matches every market whose item is in the tag:

```json
{ "item": "#c:ores", "weightFactor": 0.3 }
```

**Glob** -- `*` matches any character sequence, so a category of similar items can be caught in one entry:

```json
{ "item": "minecraft:*_ore", "weightFactor": 0.5 }
```

A glob without a namespace defaults to `minecraft:*` -- `*_ore` and `minecraft:*_ore` are equivalent.

### The `weightFactor` Field

`weightFactor` scales the event's `peakFactor` for the matched market(s):

- `1.0` -- full impact (the default).
- `0.3` -- 30% of the impact (the ore market barely notices).
- `-0.4` -- **inverts the direction**. When diamonds crash, emeralds might rally.

Negative weight factors are the key to authoring "one market moves up, another moves down" events with a single JSON block.

### Precedence: Order Matters

When several entries match the same market, **the first entry in JSON order wins**. Always list specific ids before broader tags/globs:

```json
"markets": [
  { "item": "minecraft:diamond",       "weightFactor": 1.0 },
  { "item": "minecraft:diamond_block", "weightFactor": 1.0 },
  { "item": "#c:ores",                 "weightFactor": 0.3 },
  { "item": "minecraft:emerald",       "weightFactor": -0.4 }
]
```

Here diamonds and diamond blocks get the full impact, everything in `#c:ores` gets 30%, and emeralds get -40%. If the tag entry came first, diamonds would be caught by it and never see the full impact.

Full matcher reference: [markets\[\]](configuration.md#markets).

---

## 5. Adding Translations

Both `headline` and `text` accept **either** a plain string (treated as `en_us`) **or** an inline map of Minecraft language codes:

```json
"headline": {
  "en_us": "Wheat blight strikes the eastern plains!",
  "de_de": "Getreidebrand wütet in den östlichen Ebenen!",
  "fr_fr": "Une rouille du blé frappe les plaines orientales!"
},
"text": {
  "en_us": "Farmers report entire fields lost...",
  "de_de": "Bauern berichten von ganzen Feldern..."
}
```

The map is stored in the news history and sent to every client -- each client resolves its own language at render time using this fallback chain:

1. Exact client language (e.g. `de_de`).
2. English (`en_us`).
3. The first entry the event defines.

**A single event covers every language.** No duplicate events, no client-side language files. Players can switch language mid-session and the whole news history re-renders in the new language on the next open.

For servers with only one language, the plain-string form stays the simplest choice.

---

## 6. Adding a Picture

Picture files live one folder deeper:

```
config/StockMarket/news/pictures/
```

Drop a `wheat_shortage.png` in there (any color -- it's auto-converted to newsprint on the client) and reference it by **bare file name**:

```json
{
  "id": "wheat_shortage",
  "picture": "wheat_shortage.png",
  "headline": "...",
  ...
}
```

Requirements:

- **PNG format** only. Any color type / bit depth (color images are fine).
- **Size**: at most 128 KiB per file.
- **Dimensions**: 16 to 512 px per side.
- **Shape**: square (1:1) recommended -- all display surfaces are square.

You can author in full color; the client automatically converts each image to newsprint grayscale with the ink-on-paper look. If you want precise control, author in grayscale and preview in-game.

A missing file is only a **warning** (event publishes picture-less); a malformed file name is an error that drops the field but keeps the event. See [News Event Pictures](pictures.md) for the full picture pipeline (snapshotting, sync, defaults).

---

## 7. Announce Delay: Front-Running and Insider Windows

By default, the headline is published at the same instant the price starts moving. `announceDelayMs` shifts the impact relative to the publish:

```json
"announceDelayMs": { "min": 30000, "max": 90000 }
```

The plugin samples one uniform delay from `[min, max]` (milliseconds) per activation. Players never learn a fixed timing.

**Positive delays** -- the headline publishes first, the price moves later:

- Creates a **front-running window** for attentive readers. Someone who checks the newspaper immediately can position before the move.
- Great for slow-brewing events ("Rumors of a new mine..." followed by the actual price rise 60-90 seconds later).

**Negative delays** -- the price moves first, the headline follows:

- The "insiders knew" flavor. The market moves suspiciously *before* the news breaks.
- Combined with an already-visible price move, it teaches players to watch the [candlestick chart](../../../README.md#candle-stick-chart) too, not just the newspaper.

```json
"announceDelayMs": { "min": -45000, "max": -15000 }
```

Both `min` and `max` may be negative. `min <= max` is enforced. A delay so negative that the headline would appear after the event is over is legal but flagged with a warning.

Full reference: [announceDelayMs](configuration.md#announcedelayms).

---

## 8. Upgrading to Sequences

The `impact` block covers 90% of what most servers need. Reach for `sequences` when you want **a story** -- pump-and-dump, flash-crash-and-rebound, multi-day arcs.

An event defines **exactly one** of `impact` or `sequences`. Both is an error; neither is an error (the event is skipped).

`sequences` is an array of one or more **weighted alternatives**. At activation the plugin picks one sequence by weight -- so a single event id can have several story variants that players cannot predict. Each sequence is a list of **steps** the price influence walks through in order.

Here's a pump-and-dump vs. fizzle-out variant of our wheat event:

```json
{
  "id": "wheat_speculation",
  "headline": "Speculators pile into wheat futures!",
  "text": "Whispers of a supply squeeze have traders bidding wheat up hard. Skeptics warn the story may not hold.",
  "category": "rumors",
  "weight": 6,
  "cooldownSeconds": 10800,
  "sequences": [
    {
      "name": "pump_and_dump",
      "weight": 2,
      "steps": [
        { "name": "hype",     "durationSeconds": { "min": 90, "max": 240 }, "targetFactor":  0.4,  "curve": "linear",      "noise": 0.05 },
        { "name": "peak",     "durationSeconds": { "min": 60, "max": 120 },                        "curve": "hold",        "noise": 0.08 },
        { "name": "sell_off", "durationSeconds": { "min": 20, "max":  60 }, "targetFactor": -0.15, "curve": "exponential", "noise": 0.04 },
        { "name": "recover",  "durationSeconds": 300,                       "targetFactor":  0.0,  "curve": "linear" }
      ]
    },
    {
      "name": "fizzle",
      "weight": 1,
      "steps": [
        { "name": "stir",   "durationSeconds": { "min": 60, "max": 120 }, "targetFactor": 0.15, "curve": "linear",      "noise": 0.04 },
        { "name": "denial", "durationSeconds": { "min": 60, "max": 180 }, "targetFactor": 0.0,  "curve": "exponential" }
      ]
    }
  ],
  "markets": [
    { "item": "minecraft:wheat", "weightFactor": 1.0 }
  ]
}
```

Two thirds of activations play the full pump-and-dump; one third the speculation just fizzles. Players never know which story they are in.

### Step Fields at a Glance

| Field | Meaning |
|-------|---------|
| `name` | Step label -- shown in the admin UI; also the reference key for `chains` with `"on": "step"`. Must be unique within its sequence. |
| `durationSeconds` | Number or `{min, max}`. A range is rolled uniformly **once at activation** and frozen -- every activation plays with different but consistent timings. |
| `targetFactor` | The signed influence level the step **reaches at its end**. `0.4` = +40%, `-0.15` = -15%. Interpolates from the previous step's end level (the first step starts from 0). Required except for `hold` curves. |
| `curve` | How to move to `targetFactor`: `linear` (straight line), `instant` (jump-and-hold), `exponential` (fast-then-leveling), `hold` (keep previous level unchanged). |
| `noise` | Per-tick jitter on the influence while this step runs. Author the calm/twitchy character step-by-step. |
| `markets` | *Optional.* When present, **replaces** the event-level markets for this step -- author panic that spills into related markets, or dilution that spreads out. |
| `permanent` | *Last step only.* Bakes the final influence into the market's default price at sequence end -- the multi-step equivalent of `reversal: "none"`. |

### Ending a Sequence Cleanly

Two clean endings:

- **Back to normal:** last step ends at `targetFactor: 0.0` -- the influence glides out. Ending at a non-zero level without `permanent` triggers a validation warning (the influence would snap off abruptly).
- **Permanent shift:** flag the last step `"permanent": true` -- the final level bakes into the market's default price, and the shift survives restarts forever.

Full reference (all fields, all curves, per-step market rules, `impact` vs. `sequences` relationship): [Multi-Step Sequences](advanced-events.md#multi-step-sequences).

---

## 9. Adding Requirements

`requires` is an array of **predicates** against the [world-event registry](advanced-events.md#the-world-event-registry). All entries must hold for the event to become eligible.

The registry remembers, per event id: how many times it fired, timestamps of first and last fire, and the in-game day. Events can also write custom **key/value records** with `records: {"era": "gold_standard"}` at publish time (last write wins).

Requirements are checked when the scheduler picks the next event, and again at fire time. Manual `/stockmarket news trigger` bypasses them (management GUI asks for confirmation first).

Simplest example -- an "aftermath" event that only fires **at least three hours** after the primary event and never fires twice:

```json
"requires": [
  { "type": "firedBefore", "eventId": "wheat_shortage", "minSecondsAgo": 10800 },
  { "type": "notFired",    "eventId": "wheat_recovery" }
]
```

Combined with `records` and multiple events, you can build one-time storylines that unfold across multiple game days.

### The 11 Predicates

| `type` | Fields | Holds when |
|--------|--------|------------|
| `firedBefore` | `eventId`, `minTimes` (default `1`), `minSecondsAgo`, `maxSecondsAgo` | Event fired at least `minTimes` times, and its last fire is within the given age window (both bounds optional, both inclusive). **Never-fired always fails**. |
| `notFired` | `eventId` | Event has never fired (or the record was cleared). |
| `notFiredWithin` | `eventId`, `seconds` | Event never fired, OR its last fire is *strictly older* than `seconds`. |
| `countAtLeast` | `eventId`, `count` | Event fired at least `count` times (`count: 0` is trivially true). |
| `countAtMost` | `eventId`, `count` | Event fired at most `count` times. |
| `keyEquals` | `key`, `value` | The registry key exists and equals `value` exactly (case-sensitive). |
| `keyNotEquals` | `key`, `value` | Key is absent OR differs from `value`. |
| `keyExists` | `key` | Key has been written (any value). |
| `keyAbsent` | `key` | Key has never been written (or was cleared). |
| `keyAtLeast` | `key`, `value` | Stored value, parsed as a number, is `>= value`. Absent/non-numeric = false. |
| `keyAtMost` | `key`, `value` | Stored value, parsed as a number, is `<= value`. Absent/non-numeric = false. |

Registry timestamps are **real-world wall-clock** (ages tick while the server is offline) -- deliberately different from event cooldowns, which are pause-safe. See [Time Basis: Wall Clock](advanced-events.md#time-basis-wall-clock).

**Important:** an unknown `type` (or a malformed entry) is a **load error** that skips the whole event -- stricter than most other fields. A requirement the server cannot enforce must never silently pass.

Full reference: [Trigger Requirements](advanced-events.md#trigger-requirements).

---

## 10. Adding Chains

`chains` lets one event **schedule follow-up events**. Perfect for storylines with a beginning-middle-end structure.

```json
"chains": [
  { "eventId": "wheat_recovery", "on": "completion", "chance": 0.7, "delaySeconds": { "min": 1800, "max": 3600 } }
]
```

This says: 70% of the time, 30-60 minutes after `wheat_shortage` completes naturally, fire `wheat_recovery`.

### Chain Fields

| Field | Meaning |
|-------|---------|
| `eventId` | The follow-up event id. Must exist in the merged pool (checked across all files) and must not be `adminOnly`. |
| `on` | The trigger moment: `publish` (when the source event's headline goes out), `step` (when a named step starts -- also works with `impact` events via the implicit step names `ramp`/`hold`/`reversal`), or `completion` (when the source event retires **naturally**). |
| `step` | *(Required for `on: "step"`.)* The step name that triggers this chain. A misspelled step name simply never triggers -- no validation error. |
| `chance` | Probability, rolled once at the trigger moment. `0.0`-`1.0`. |
| `delaySeconds` | Delay from the trigger moment to the follow-up activation. Ticks on **pause-safe** server time (like cooldowns, unlike registry ages). |
| `sameMarkets` | When `true`, the follow-up event runs on the **source event's resolved markets** instead of resolving its own -- useful when the story targets the same items. |

### A 2-Event Storyline

Let's tie our two events together:

```json
{
  "events": [
    {
      "id": "wheat_shortage",
      "headline": "Wheat blight strikes the eastern plains!",
      "text": "Farmers report entire fields lost to a fast-spreading blight...",
      "weight": 5,
      "cooldownSeconds": 3600,
      "impact": {
        "type": "shock",
        "peakFactor": 0.5,
        "durationSeconds": 300,
        "reversalSeconds": 600
      },
      "markets": [ { "item": "minecraft:wheat", "weightFactor": 1.0 } ],
      "chains": [
        { "eventId": "wheat_recovery", "on": "completion", "chance": 0.7, "delaySeconds": { "min": 1800, "max": 3600 } }
      ]
    },
    {
      "id": "wheat_recovery",
      "headline": "Wheat harvest recovers as new fields come online!",
      "text": "Growers report record yields from resistant strains planted after the blight. Prices are settling back down.",
      "weight": 0,
      "impact": {
        "type": "trend",
        "peakFactor": -0.3,
        "rampUpSeconds": 60,
        "durationSeconds": 300,
        "reversal": "ramp",
        "reversalSeconds": 300
      },
      "markets": [ { "item": "minecraft:wheat", "weightFactor": 1.0 } ]
    }
  ]
}
```

Note `wheat_recovery` has `weight: 0` -- it cannot be picked randomly; it only appears **as a chain from `wheat_shortage`**. This is the standard pattern for storyline-follow-up events. Set `weight: 0` and let chains do the driving.

### Loop Protection

Chains have two guards:

- **Depth limit:** at most **4 hops** from the original scheduler/admin-fired event (A -> B -> C -> D -> E is blocked at E).
- **Ancestry:** a chain can never fire an event already in its lineage (A -> B -> A is blocked at the second A).

A self-referencing chain with `chance: 1.0` gets a "possible loop" warning at load time; it still runs (bounded by the depth limit).

### What Chains Bypass

Chain firings deliberately bypass the scheduler's activity caps, the target's cooldown, and the weight roll -- a storyline is never cut off by the caps. They **do** re-check the target's `requires` and market eligibility at fire time; failing checks let the firing lapse quietly.

Full reference: [Event Chains](advanced-events.md#event-chains).

---

## 11. The Scheduler

The `scheduler` block tunes the random-event pacing. It is **optional** and may appear in any file; every reload starts from the built-in defaults, then each file's block overrides the fields it specifies -- files in alphabetical order, **last-loaded file wins per field**.

```json
{
  "scheduler": {
    "minSecondsBetweenEvents": 900,
    "maxSecondsBetweenEvents": 3600,
    "maxActiveEventsGlobal": 3,
    "maxActiveEventsPerMarket": 1,
    "historyMaxEntries": 1000
  },
  "events": [ ... ]
}
```

| Field | Default | Effect |
|-------|---------|--------|
| `minSecondsBetweenEvents` | `900` | Lower bound of the wait between two random activations. |
| `maxSecondsBetweenEvents` | `3600` | Upper bound. Each interval is uniformly sampled from `[min, max]`. If an attempt fires nothing (caps reached, everything on cooldown), a full new interval waits. |
| `maxActiveEventsGlobal` | `3` | Cap of simultaneously active events across all markets. |
| `maxActiveEventsPerMarket` | `1` | Cap per market. A market at its cap is skipped by new events. |
| `historyMaxEntries` | `1000` | How many published records the server keeps. **Prefer a multiple of 100** for exact retention -- see [History Persistence and Chunk Layout](configuration.md#history-persistence-and-chunk-layout). |

Because scheduler fields merge per-field, you can drop a small `pacing.json` file with just a scheduler override:

```json
{ "scheduler": { "maxActiveEventsGlobal": 5 } }
```

...and leave every other file to focus on its events. The mod also lets an admin override scheduler values live from the **NewsPlugin management window** without editing files.

Full reference: [The Scheduler Block](configuration.md#the-scheduler-block).

---

## 12. Debugging Your Events

Every reload prints a **validation report** to your chat and to the server log, grouped per file. Two severities:

- **Errors** -- bad JSON syntax, missing required fields (`id`, `headline`, `text`), both `impact` and `sequences`, non-finite numbers, `peakFactor <= -1`, `min > max`, duplicate ids, unknown requirement types. **Errors skip the affected event** (or the whole file for file-level problems). Everything else still loads. A few field-local errors drop only the field: `picture`, single `records` entries, single `chains` entries.
- **Warnings** -- unknown fields, unresolvable items, empty `markets`, oversized texts (>32 KB per event), odd-but-legal combinations. Warnings keep the event loaded.

**Safety nets built into the reload:**

- A **bad file never crashes the server** -- loading is strictly skip-and-continue.
- If a reload produces **no valid events at all** while a previous pool exists, the old pool is **kept**. A typo can never silently leave the server without news.
- **Already-active events are never affected by a reload** -- they run on a snapshot of the values they started with.

### Common Mistakes

| Symptom | Cause | Fix |
|---------|-------|-----|
| Event loads but never fires | `weight: 0` + not chained + not manually triggered | Set `weight > 0`, or trigger with `/stockmarket news trigger <id>`. |
| "unknown impact type" warning | Typo in `impact.type` (e.g. `"trends"`) | Use exactly `shock`, `trend`, or `crash`. |
| Event skipped by validator | Both `impact` and `sequences` present, or neither | Choose exactly one. |
| Chain never fires | Wrong step name in `"on": "step"` | Step name typos are silent -- double-check against the source event's actual step names, or `ramp`/`hold`/`reversal`/`permanent` for `impact` events. |
| "peakFactor must be > -1" error | Value like `-1.0` or `-1.5` | Cap at `-0.99` for near-total crashes. `-1` would zero the price. |
| Warning about no matched markets | Item id not on the server (mod missing), or item is filtered out by tag/glob | Add the item as a market via `/stockmarket manage`, or fix the id. |
| Requirement predicate ignored | Unknown `type` in `requires` | Unknown types are strict errors -- check spelling against the [11 predicates](#the-11-predicates). |
| Chain "possible loop" warning | Self-chain with `chance: 1.0` | Add a `notFired` on the same id, or reduce chance -- the depth guard still blocks it after 4 hops. |

### Where to Look

- **In-game chat** -- the reload report prints there first.
- **`logs/latest.log`** -- the same report plus context and stack traces on unexpected exceptions.
- **`/stockmarket news list`** -- shows all loaded definitions with cooldown / active markers and all active events with their live impact factors.
- **`/stockmarket news info <eventId>`** -- prints the full breakdown of one event (step-by-step for sequences).

### Anti-Patterns to Avoid

- **Chain loops without termination.** A self-referencing chain with high probability will fire until the depth guard stops it (up to 4 hops). Add a `notFired`-itself requirement, or lower the chance.
- **Impossibly tight cooldowns.** `cooldownSeconds: 0` combined with a small pool means the same event may fire repeatedly. Give each event breathing room.
- **Overlapping markets between concurrent events.** The combined factor across all events on one market is clamped to `[0.1, 10]`, so stacked events cannot floor or moon a price -- but a stacked pileup is confusing to players. Use `maxActiveEventsPerMarket: 1` (the default) unless you specifically want it.
- **`peakFactor` near `-1`.** Values like `-0.95` produce near-zero prices where the [TargetPriceBot](../../../README.md#targetpricebot) can't behave well. Stay above `-0.8`.
- **`target` instead of `targetFactor`.** Common typo in sequence steps -- the field is `targetFactor`. Unknown fields warn but don't kill the load, so the step ends up with the previous level as its target (unexpected behavior).

---

## 13. Testing Safely

While you're iterating, you don't want half-broken events firing randomly on a live server. Two tactics:

### Mark Work-in-Progress Events `adminOnly`

```json
"adminOnly": true
```

`adminOnly: true` prevents the event from ever being picked by the scheduler; only `/stockmarket news trigger <id>` fires it. Flip it back to `false` (or delete the line -- `false` is the default) when the event is ready to ship.

Chained follow-ups **cannot** point at `adminOnly` targets -- attempting it is a load error, chain dropped, source event kept. This is deliberate: chains are causal, and an `adminOnly` event is explicitly off the random-pool.

### Use a Dev World

Testing sequences and chains often needs the same event to fire many times in a row. Set up a small creative-mode test world:

```
/stockmarket op
/stockmarket news trigger my_event
/stockmarket news skipphase my_event   # fast-forward through a phase
/stockmarket news stop my_event        # hard-stop and re-arm cooldown
```

Combine with `/stockmarket news registry clear <eventId>` to re-arm one-time events and re-test them.

### Fast-Forwarding During Tests

- `/stockmarket news skipphase <eventId>` -- advances an active event to the start of its next phase. For sequences, jumps to the next step. Skipping the last step ends the event normally; for `reversal: "none"`, skipping the hold **bakes the permanent shift** like a natural completion.
- `/stockmarket news stop <eventId>` -- hard-stops in any phase. Full cooldown restarts. A pending `reversal: "none"` bake is **cancelled** (nothing is baked).
- `/stockmarket news trigger <eventId> [market]` -- bypasses cooldown, weight, `adminOnly` and requirements. The optional `[market]` restricts the impact to one market for testing (in quotes, e.g. `"minecraft:wheat"`).

Full command reference: [Admin Commands](configuration.md#admin-commands).

---

## 14. Shipping Your Events

Your file is one self-contained JSON. To share it:

- **Give it to another server owner** -- they drop it into their own `config/StockMarket/news/` folder, run `/stockmarket news reload`, and it merges into their pool. No conflicts to worry about unless your event ids collide with existing ids (they get an error listing the duplicates -- rename yours to fix).
- **Ship it with a modpack** -- put the file into the pack's `config/StockMarket/news/` folder and it loads on first launch.
- **Pictures too?** Ship the referenced PNG files inside `config/StockMarket/news/pictures/` (same folder name and rules on the target server).

Recommended conventions to make your file play well with others:

- **Namespace your event ids.** Prefix them with a short server or pack name -- e.g. `myserver_wheat_shortage` instead of `wheat_shortage`. Reduces collision risk with defaults and other authors.
- **Comment via `category`.** The `category` field is free-form -- use it to group your events (`"category": "myserver:harvest"`). It appears in `/stockmarket news list` and `/stockmarket news info` output.
- **Split by theme, not by file.** All files merge into one pool, so `harvest.json` + `mining.json` + `trade.json` is clearer than one 800-line file. Filenames are irrelevant to loading order except alphabetically for scheduler conflict resolution.

For the exhaustive schema (every field, every default, every validation rule): **[Configuring News Events](configuration.md)**.
For sequences, requirements, and chains in full: **[Advanced News Events](advanced-events.md)**.
For the picture pipeline: **[News Event Pictures](pictures.md)**.
For what players see: **[News Event System Overview](overview.md)**.

Happy authoring. Your headlines are going to move real prices in real games -- have fun with it.
