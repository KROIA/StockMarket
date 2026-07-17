# Advanced News Events: Sequences, Requirements, Registry & Chains

This page covers the advanced authoring features of the [News Event System](overview.md), on top of the base JSON schema described in [Configuring News Events](configuration.md):

- **[Multi-step sequences](#multi-step-sequences)** (`sequences`) -- storyline-shaped price curves instead of the simple three-phase `impact`.
- **[The world-event registry](#the-world-event-registry)** -- long-term world memory: what fired when, plus custom key/value records.
- **[Trigger requirements](#trigger-requirements)** (`requires`) -- events that only become eligible when the world's history allows it.
- **[Event chains](#event-chains)** (`chains`) -- events that trigger follow-up events.

The simple `impact` block remains the **recommended form** for normal events -- it is not deprecated, and internally both forms run on the exact same machinery. Reach for the advanced features when you want multi-act stories ("pump, peak, dump, recover"), one-time world-changing events, or connected narratives.

All four features are demonstrated by the shipped default events: `gold_rush_rumor` (sequences), and the **gold-standard pair** `gold_reserve_standard` / `end_of_gold_standard` (requirements, records, chains) -- used as the running example below. See [Getting the New Default Events](#getting-the-new-default-events) if your server already has an older `default_events.json`.

## Multi-Step Sequences

An event defines **exactly one** of `impact` or `sequences` -- both present is an error, neither is an error (the event is skipped).

`sequences` is an array of one or more named sequences; each sequence is a list of **steps** the price influence walks through in order. The shipped `gold_rush_rumor` event:

```json
{
  "id": "gold_rush_rumor",
  "headline": "Rumors of a massive gold strike sweep the trading floor!",
  "text": "Whispers of an enormous gold vein have traders piling into gold. ...",
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
}
```

Two out of three activations play the full pump-and-dump; one in three the rumor just fizzles. Players never know which story they are in.

### Sequence Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | string | **required** | Shown in the admin UI and persisted into the running event. |
| `weight` | number > 0 | `1.0` | Relative pick probability. At **activation** the plugin picks **one** sequence of the event by weight -- each activation is an independent roll. |
| `steps` | array | **required** | Non-empty list of steps, played in order. Step names must be unique within their sequence (they are referenced by [chains](#event-chains)). |

### Step Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | string | **required** | Step label -- shown in the Active tab and the details screen, and the reference key for `chains` with `"on": "step"`. |
| `durationSeconds` | number or `{min, max}` | **required** | Step length. A range is rolled **uniformly once at activation** and frozen into the running event -- a restart never re-rolls, but every activation plays with different timings. Non-negative; `min <= max`. |
| `targetFactor` | number > -1 | **required** (except `hold`) | The signed influence level the step reaches **at its end**: `0.4` = +40%, `-0.15` = -15% (same semantics as `peakFactor`). The value interpolates from the previous step's end level (the first step starts from 0). |
| `curve` | string | `"linear"` | How the value moves from the previous level to `targetFactor` -- see [Curves](#curves). |
| `noise` | number ≥ 0 | `0` | Per-tick jitter on the influence **while this step runs** (same math as the `impact` noise). Each step can have its own -- the shipped example is calm during the hype and twitchy at the peak. |
| `markets` | array | *(inherit)* | Optional per-step matcher list (exact same grammar as the event-level [markets\[\]](configuration.md#markets)). When present it **replaces** the event-level markets for this step; absent = the step inherits them. |
| `permanent` | boolean | `false` | **Last step only.** At sequence end the final value is baked into the market's default price instead of snapping off -- the sequence equivalent of `reversal: "none"` (same rules: `stop` cancels the pending bake, `skipphase` finalizes it early, the bake happens once and survives restarts). |

### Curves

| Curve | Behavior |
|-------|----------|
| `linear` | A straight line from the previous level to `targetFactor` over the step's duration. |
| `instant` | Jumps to `targetFactor` right at the step start and holds it for the whole step. Use it for flash crashes. |
| `exponential` | Fast at first, then leveling off toward `targetFactor` -- most of the move happens early in the step (time constant = duration / 6, matching the `impact` exponential reversal; the tiny ~0.25% residual snaps to the target at the step end). Reads as panic selling or a fading effect. |
| `hold` | Keeps the previous level for the whole step. `targetFactor` is not needed; if you give one that differs from the held level, a warning reminds you it is ignored. |

### End of a Sequence

When the last step ends, the event retires (once its headline is published). Two clean ways to end:

- **Back to normal:** author the last step to `targetFactor: 0.0` -- the influence glides out. A last step ending at a non-zero level *without* `permanent` triggers a validation warning: the influence would snap off abruptly at sequence end.
- **Permanent shift:** flag the last step `"permanent": true` -- its final level bakes into the market's default price, exactly like [`reversal: "none"`](configuration.md#impact).

### Per-Step Markets

A step with its own `markets` list hits a different market set than the rest of the event -- in the example, the `sell_off` panic spills over into raw gold while the hype only lifted gold ingots. Details:

- Per-step matchers resolve at **activation time** against the subscribed, news-enabled markets and are frozen into the running event.
- During a step, its market map fully **replaces** the event-level one -- a market entering or leaving the set at a step boundary jumps in or out of the influence at the boundary value. That jump is expected behavior; keep boundary levels small if you want it subtle.
- The **union** of all steps' markets counts as "the event's markets" for the activity caps, the newspaper's item icons and eligibility (the event is eligible if at least one market of any step resolves).

### Relationship to `impact`

Internally, every `impact` event is normalized into one implicit sequence with the step names **`ramp` → `hold` → `reversal`** (or a final zero-length **`permanent`** step for `reversal: "none"`). The runtime behavior is identical -- this only matters when you want to reference an `impact` event's phases from [chains](#event-chains) (`"on": "step", "step": "hold"` works on plain `impact` events, as the shipped gold-standard event demonstrates) and for `/stockmarket news skipphase`, which for sequence events jumps to the start of the **next step**.

### What Admins See

- **Active tab** (management window): each running sequence event shows its current **step name**, a **countdown to the next step** and "phase i of n" (omitted for single-step sequences).
- **Details screen** (click an event row): a per-sequence step breakdown -- one row per step with index, name, authored duration (or `min–max` range), target impact %, curve and a `[permanent]` marker, plus an indented market line for steps with their own markets. Events with several sequences show each sequence's **pick chance** and collapsible headers. The breakdown always shows the *authored* duration ranges; the concrete rolled durations of a running instance are not displayed.
- `/stockmarket news info <eventId>` prints the same breakdown in text form.

## The World-Event Registry

The registry is the news system's **long-term world memory**, stored in `world/data/StockMarket/News/registry.nbt` (master server, like the news history). It holds two kinds of state:

- **Fire records** -- written **automatically on every publish**: per event id, how many times it fired, the timestamps of the first and last fire, and the in-game day of the last fire.
- **Custom values** -- string key/value pairs written by events via `records` (or inspected/removed by admins). Values are strings; numeric strings (`"3"`, `"2.5"`) are the convention for the numeric [requirement predicates](#trigger-requirements).

### Writing Values: `records`

```json
"records": { "era": "gold_standard" }
```

The pairs are applied when the event **publishes** its headline -- last write wins. Limits (writes beyond them are refused with a server-log warning; the reload report warns you in advance): at most **256 custom keys**, key and value each at most **256 characters**.

### Time Basis: Wall Clock

Registry timestamps are **real-world wall-clock time**. Ages used by requirements like `firedBefore.minSecondsAgo` keep growing while the server is offline or paused -- deliberately different from event **cooldowns**, which tick on pause-safe server time and freeze with the server. "The gold standard was adopted three hours ago" means three real hours, whether the server ran or not.

### Registry Admin Commands

Same permission rules as the other [news admin commands](configuration.md#admin-commands) (they work from slave servers too):

| Command | Description |
|---------|-------------|
| `/stockmarket news registry list` | Show all fire records (count, first/last fire as UTC timestamp, relative age, in-game day) and all custom key/value pairs. |
| `/stockmarket news registry clear all` | Wipe the whole registry -- all fire records and all custom values. |
| `/stockmarket news registry clear <eventId>` | Delete one event's fire record. The event counts as **"never fired"** again for all requirement predicates. |
| `/stockmarket news registry clear key <key>` | Delete one custom key/value pair. |

Clearing an id or key that does not exist is a clean no-op. `clear` operations that actually delete something are **audited** -- broadcast to the other online StockMarket admins, like manual triggers (they can re-arm one-time events). `list` is a pure query and not audited.

## Trigger Requirements

`requires` is an array of predicates over the registry. **All entries must hold** for the event to be eligible -- checked when the scheduler plans and again at fire time, and also when a [chain](#event-chains) tries to fire the event.

```json
"requires": [
  { "type": "firedBefore", "eventId": "gold_reserve_standard", "minSecondsAgo": 10800 },
  { "type": "notFired",    "eventId": "end_of_gold_standard" },
  { "type": "keyEquals",   "key": "era", "value": "gold_standard" }
]
```

**Manual triggers bypass requirements:** `/stockmarket news trigger` fires the event regardless. The management GUI shows a confirmation dialog first, listing the unmet requirements ("Trigger despite unmet requirements?"); the command triggers without asking.

### Predicate Reference

| Type | Fields | Holds when |
|------|--------|------------|
| `firedBefore` | `eventId`, `minTimes` (default `1`), `minSecondsAgo`, `maxSecondsAgo` | The event fired at least `minTimes` times, and its **last** fire is at least `minSecondsAgo` / at most `maxSecondsAgo` seconds old (both optional, both boundary-inclusive). A **never-fired event always fails** -- even with `minTimes: 0`. |
| `notFired` | `eventId` | The event has **no fire record** -- never published, or its record was cleared via `registry clear`. |
| `notFiredWithin` | `eventId`, `seconds` | The event never fired, **or** its last fire is *strictly older* than `seconds` (a fire exactly `seconds` ago still counts as "within" and fails). |
| `countAtLeast` | `eventId`, `count` | The event fired at least `count` times (never fired = 0, so `count: 0` is trivially true). |
| `countAtMost` | `eventId`, `count` | The event fired at most `count` times. |
| `keyEquals` | `key`, `value` | The custom key **exists** and equals `value` exactly (case-sensitive). An absent key is **false**. |
| `keyNotEquals` | `key`, `value` | The custom key is **absent or** differs from `value`. An absent key is **true** -- combine with `keyExists` if the key must have been written. |
| `keyExists` | `key` | The custom key has been written (any value). |
| `keyAbsent` | `key` | The custom key has never been written (or was cleared). |
| `keyAtLeast` | `key`, `value` | The stored value, parsed as a number, is `>= value` (inclusive). An **absent key or a non-numeric stored value is false**. `value` may be a JSON number or a numeric string. |
| `keyAtMost` | `key`, `value` | The stored value, parsed as a number, is `<= value` (inclusive). Same absent/non-numeric rule. |

Notes:

- All `seconds` fields are non-negative wall-clock seconds (fractions are rounded to whole seconds); counts are non-negative whole numbers.
- A `firedBefore` window with `minSecondsAgo > maxSecondsAgo` loads with a warning -- it can never be satisfied.
- **An unknown `type` (or a malformed entry) is a load error that skips the whole event.** This is stricter than most other fields on purpose: a requirement the server cannot enforce must never silently pass.

### Example: The Gold-Standard Pair

The shipped defaults use requirements and records to tell a one-time, two-act story. Act one -- `gold_reserve_standard` fires **at most once naturally** and stamps the era:

```json
{
  "id": "gold_reserve_standard",
  "headline": "Trade council adopts the gold standard!",
  "weight": 2,
  "cooldownSeconds": 86400,
  "impact": { "type": "trend", "peakFactor": 0.25, "rampUpSeconds": 300, "durationSeconds": 600, "reversal": "none" },
  "markets": [ { "item": "minecraft:gold_ingot" } ],
  "requires": [ { "type": "notFired", "eventId": "gold_reserve_standard" } ],
  "records": { "era": "gold_standard" },
  "chains": [ { "eventId": "gold_rush_rumor", "on": "step", "step": "hold", "chance": 0.5, "delaySeconds": { "min": 300, "max": 900 } } ]
}
```

Act two -- `end_of_gold_standard` only becomes eligible once act one fired **at least 3 real hours ago** and the era is still `gold_standard`; it then permanently removes the premium act one baked in (`1.25 × 0.8 = 1.0`) and flips the era:

```json
{
  "id": "end_of_gold_standard",
  "headline": "Trade council abandons the gold standard!",
  "weight": 2,
  "impact": { "type": "crash", "peakFactor": -0.2, "rampUpSeconds": 60, "durationSeconds": 300, "reversal": "none" },
  "markets": [ { "item": "minecraft:gold_ingot" } ],
  "requires": [
    { "type": "firedBefore", "eventId": "gold_reserve_standard", "minSecondsAgo": 10800 },
    { "type": "notFired",    "eventId": "end_of_gold_standard" },
    { "type": "keyEquals",   "key": "era", "value": "gold_standard" }
  ],
  "records": { "era": "fiat" }
}
```

Each event runs **once per world** -- unless an admin re-arms the story with `/stockmarket news registry clear <eventId>`.

## Event Chains

`chains` lets an event schedule follow-up events -- the "gold standard adopted" story above has a 50% chance to spawn a gold-rush rumor 5 to 15 minutes into its hold phase:

```json
"chains": [
  { "eventId": "gold_rush_rumor", "on": "step", "step": "hold", "chance": 0.5, "delaySeconds": { "min": 300, "max": 900 } }
]
```

### Chain Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `eventId` | string | **required** | The event to fire. Must exist somewhere in the merged pool (checked after all files are loaded -- the target may live in another file) and must **not** be `adminOnly`. |
| `on` | `"publish"` \| `"step"` \| `"completion"` | **required** | The trigger moment -- see below. |
| `step` | string | *(required for `on: "step"`)* | The step name that triggers the chain. Also works on plain `impact` events via their implicit step names `ramp` / `hold` / `reversal` / `permanent`. **A step name that does not exist simply never triggers** -- no validation error, so double-check the spelling. |
| `chance` | number 0..1 | `1.0` | Probability, rolled once at the trigger moment. Values outside [0, 1] are clamped with a warning. |
| `delaySeconds` | number or `{min, max}` | `0` | Delay between the trigger moment and the follow-up activation, rolled uniformly. Ticks on **pause-safe server time** (like cooldowns -- not wall clock) and survives restarts. |
| `sameMarkets` | boolean | `false` | When true, the follow-up event runs on the **source event's resolved markets** (with their weight factors) instead of resolving its own matchers. |

### Trigger Moments

- `publish` -- when the source event's headline is published. Fires for **every** publish path, including manual admin triggers and chain-fired events.
- `step` -- when the named step **starts**. Steps that `/stockmarket news skipphase` fast-forwards *past* still fire their chains.
- `completion` -- when the source event retires **naturally** (its sequence played out). **Not** on `/stockmarket news stop`: stop means cancel -- and it also **discards the event's still-pending chain firings**.

### What a Chain Fire Checks -- and What It Bypasses

When the delay expires, the target is re-checked: it must still exist in the library, be enabled (not admin-disabled), not be `adminOnly`, not be already active, pass **its own `requires`** at that moment, and resolve at least one market. If any check fails, the firing lapses with a server-log entry -- it is not retried.

Chains deliberately **bypass**, as an explicit causal consequence rather than a random pick:

- both **activity caps** (global and per-market) -- a storyline is never cut off by the caps,
- the target's **cooldown** -- the chain fires even if the target is still cooling down (the fire re-arms the cooldown as usual),
- the scheduler's **weight roll and event spacing** -- chains do not consume scheduler slots.

### Loop Protection

- **Depth limit:** at most **4 chain hops** from the original scheduler/admin-fired event (A→B→C→D→E is blocked at E).
- **Ancestry:** a chain can never fire an event that is already in its own chain lineage (A→B→A is blocked).
- Blocked firings are logged, never errors. A self-referencing chain with `chance: 1.0` and no `notFired`-itself requirement gets a "possible loop" warning at load time (it still runs, bounded by the depth limit).

### Validation

A malformed chain entry (missing `eventId`/`on`, unknown `on` value, bad `chance`/`delaySeconds`) is an error that drops **only that entry** -- the event itself stays loaded. After all files are merged: an unknown target id is a **warning** (chain dropped), an `adminOnly` target is an **error** (chain dropped, event kept).

## Getting the New Default Events

The example file `default_events.json` is only generated when `config/StockMarket/news/` contains **no `.json` file at all** -- existing servers keep their old file and do **not** get the three showcase events (`gold_rush_rumor`, `end_of_gold_standard`, and the upgraded `gold_reserve_standard`) automatically. To get them:

- **If the defaults file is your only news file:** delete `default_events.json` and run `/stockmarket news reload` -- the current version is regenerated.
- **If you have your own files in the folder:** copy the events from this page (or from a freshly generated file) into any of your files. Note that the upgraded `gold_reserve_standard` adds `requires`/`records`/`chains` to the event you may already have.

The default **pictures** follow the same hands-off rule: they are only extracted while the `pictures/` folder contains no `.png` (see [Default Pictures](pictures.md#default-pictures)). On an existing server the new events publish picture-less until you add `gold_rush_rumor.png` / `end_of_gold_standard.png` yourself -- or empty the pictures folder and reload to regenerate all defaults.
