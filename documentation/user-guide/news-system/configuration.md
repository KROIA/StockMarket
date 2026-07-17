# Configuring News Events

This page covers the server-admin side of the [News Event System](overview.md): the JSON definition files, the full schema reference, validation behavior, and the admin commands. The advanced authoring features -- multi-step sequences, trigger requirements, the world-event registry and event chains -- have their own page: [Advanced News Events](advanced-events.md).

## The News Folder

All news events are defined in JSON files in:

```
config/StockMarket/news/
```

The folder uses **drop-in loading**:

- Every `*.json` file in the folder is loaded. Filenames do not matter.
- Each file may contain any number of events. All files merge into one event pool.
- To add news to a server, simply drop a file into the folder and run `/stockmarket news reload` (or restart).
- On first run (folder missing or empty) the mod generates `default_events.json` with a set of example events that demonstrate every schema feature -- use it as a template.
- Event pictures live in the `pictures/` subfolder -- see [News Event Pictures](pictures.md).

Files are processed in **alphabetical filename order** (case-insensitive), so conflicts resolve deterministically:

- **Duplicate event ids** across files are reported as an error; the later-processed definition wins.
- **`scheduler` blocks** merge per field; the last-loaded file wins per field (see [The Scheduler Block](#the-scheduler-block)).

## File Format

One file = an optional `scheduler` block + an `events` array:

```json
{
  "scheduler": {
    "minSecondsBetweenEvents": 900,
    "maxSecondsBetweenEvents": 3600,
    "maxActiveEventsGlobal": 3,
    "maxActiveEventsPerMarket": 1,
    "historyMaxEntries": 1000
  },
  "events": [
    {
      "id": "diamond_rush",
      "headline": {
        "en_us": "Diamond rush in the northern mountains!",
        "de_de": "Diamantenrausch in den nördlichen Bergen!"
      },
      "text": {
        "en_us": "Prospectors report huge diamond veins in the northern mountains. Experts expect supply to surge within days, putting pressure on prices across the ore market.",
        "de_de": "Schürfer melden riesige Diamantvorkommen in den nördlichen Bergen. Experten erwarten, dass das Angebot innerhalb weniger Tage stark steigt und die Preise am Erzmarkt unter Druck geraten."
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
    }
  ]
}
```

## Event Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | **required** | Unique key across all files. Used for cooldowns, `/stockmarket news trigger`, persistence and history. Duplicates are an error (later definition wins). |
| `headline` | string or language map | **required** | The headline shown in the newspaper and the toast. See [Headline and Text](#headline-and-text). |
| `text` | string or language map | **required** | The article body shown in the newspaper. Same format as `headline`. |
| `category` | string | `"general"` | Free-form grouping label (shown in the definition, not enforced against any list). |
| `weight` | number ≥ 0 | `1.0` | Relative probability in the random scheduler pick. `0` means the event never fires randomly (only via trigger). |
| `cooldownSeconds` | number ≥ 0 | `0` | How long the event stays ineligible after firing. The cooldown starts at activation and ticks on pause-safe server time. |
| `adminOnly` | boolean | `false` | If true, the event never fires randomly -- only `/stockmarket news trigger` can start it. |
| `announceDelayMs` | object `{min, max}` | `{0, 0}` | Random delay between headline and price impact. See [announceDelayMs](#announcedelayms). |
| `impact` | object | **one of the two required** | The simple three-phase price impact model -- the recommended form for normal events. See [impact](#impact). Exactly **one** of `impact` or `sequences` must be present (both or neither is an error). |
| `sequences` | array | **one of the two required** | The advanced multi-step alternative to `impact`: named sequences of steps with duration ranges, curves, per-step noise and per-step markets; one sequence is picked by weight at activation. See [Multi-Step Sequences](advanced-events.md#multi-step-sequences). |
| `markets` | array | *(warning if missing)* | Which markets the event affects. See [markets\[\]](#markets). An event without usable matchers loads but can never fire. |
| `picture` | string | -- | Bare file name of a PNG in `config/StockMarket/news/pictures/`, printed with the headline. A bad value is an error that only drops the field, a missing file only a warning -- the event loads either way. See [News Event Pictures](pictures.md). |
| `requires` | array | -- | Trigger requirements against the world-event registry (e.g. `notFired`, `firedBefore`, `keyEquals`). All entries must hold for the event to become eligible; an unknown requirement type skips the whole event. See [Trigger Requirements](advanced-events.md#trigger-requirements). |
| `records` | object | -- | String→string pairs written into the world-event registry when the event publishes (last write wins). See [The World-Event Registry](advanced-events.md#the-world-event-registry). |
| `chains` | array | -- | Follow-up events fired by this event -- on publish, on a named step, or on natural completion, each with a chance and a delay range. See [Event Chains](advanced-events.md#event-chains). |

Unknown fields anywhere in the schema are reported as warnings and ignored.

### Headline and Text

Both fields accept two forms:

- A **plain string** -- the typical single-language case. It is treated as the `en_us` text.

  ```json
  "headline": "Wheat harvest fails across the plains!"
  ```

- An **inline translation map** keyed by Minecraft language codes:

  ```json
  "headline": {
    "en_us": "Wheat harvest fails across the plains!",
    "de_de": "Weizenernte in den Ebenen ausgefallen!"
  }
  ```

The full map is stored in the news history and sent to every client; each client resolves its own language at render time with the fallback chain *exact client language → `en_us` → first entry*. This means multilingual servers need only one event definition, and players can switch languages mid-session without losing old news.

A validation warning is emitted if the serialized headline + text of a single event exceeds 32 KB.

### announceDelayMs

On each activation the plugin samples **one uniform random delay** from `[min, max]` (milliseconds). The delay is the impact start relative to the headline publish:

- **Positive** -- the headline publishes first, the price moves `delay` ms later. This creates a front-running window for attentive readers.
- **Negative** -- the price impact starts first, the headline goes public `|delay|` ms later ("the insiders knew").
- **`{0, 0}`** (the default) -- headline and impact are simultaneous.

`min <= max` is enforced; both may be negative. Because the delay is a random range, players can never learn a fixed headline-to-impact timing. A strongly negative delay that outlives the entire impact is allowed but flagged with a warning (the news would publish after the move is already over).

```json
"announceDelayMs": { "min": -45000, "max": -15000 }
```

### impact

The simple price model -- and the recommended one: it covers the typical "rises, holds, comes back" news shape with three numbers. For multi-act storylines (pump-and-dump, flash-crash-and-rebound) use the advanced [`sequences`](advanced-events.md#multi-step-sequences) form instead -- an event defines exactly one of the two. Internally both run on the same machinery: an `impact` is just a three-step sequence named `ramp` / `hold` / `reversal` (which is what the step-based admin tools and [event chains](advanced-events.md#event-chains) see).

`type` selects a preset that only supplies **defaults** for the shape parameters -- every explicit field overrides its preset default.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | `"shock"` \| `"trend"` \| `"crash"` | `"shock"` | Preset envelope shape (see table below). |
| `peakFactor` | number > -1 | **required** | Peak multiplicative influence: `0.55` = +55% at peak, `-0.4` = -40%. Multiplicative, so it works on any price scale. Values ≤ -1 are rejected (a factor of -1 would zero the price). |
| `rampUpSeconds` | number ≥ 0 | from `type` | Onset time from zero to peak (linear). `0` = instant. |
| `durationSeconds` | number ≥ 0 | from `type` | How long the impact holds at peak. |
| `reversal` | `"ramp"` \| `"exponential"` \| `"none"` | from `type` | How the influence returns to normal after the hold phase. |
| `reversalSeconds` | number ≥ 0 | from `type` | For `ramp`: the linear return time. For `exponential`: the decay time constant (the event fully expires after 6 time constants). Ignored for `none`. |
| `noise` | number ≥ 0 | `0` | Extra per-tick jitter on the impact factor (uniform in ±`noise`), adding volatility while the event runs. Negative values are clamped to 0 with a warning. |

Preset defaults supplied by `type`:

| Preset | `rampUpSeconds` | `durationSeconds` | `reversal` | `reversalSeconds` | Character |
|--------|-----------------|-------------------|------------|-------------------|-----------|
| `shock` | 5 | 300 | `exponential` | 300 | Near-instant jump, then decay back. |
| `trend` | 120 | 600 | `ramp` | 600 | Slow build-up, long hold, linear return. |
| `crash` | 10 | 120 | `exponential` | 900 | Instant move (typically negative `peakFactor`), slow recovery. |

**`reversal: "none"` is permanent.** The influence never decays -- when the hold phase ends, the shift is baked into the market's **default price** and the event retires. The market's whole price range (equilibrium, random walk) moves with it and the change survives restarts. Stopping such an event via `/stockmarket news stop` **cancels** it: the pending shift is reverted, nothing bakes, and the price returns to normal. To finalize the shift **early** instead, fast-forward the event with `/stockmarket news skipphase` -- skipping its hold phase bakes the full shift like a natural completion. Use it for structural events ("the gold standard is adopted"), and use it sparingly.

### markets

An array of portable market matchers. Each entry:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `item` | string | **required** | What to match -- see the matcher forms below. |
| `components` | object | -- | Optional data-component patch for component markets (enchanted books, potions, ...). Only valid together with an exact registry id. |
| `weightFactor` | number | `1.0` | Scales the event's impact for the matched markets. **Negative values invert the impact direction.** |

Matcher forms for `item`:

- **Exact registry id** -- `"minecraft:diamond"`. Matched against the registry name of each existing market. If the item is not in the registry (mod absent), a warning is reported and the matcher never matches.
- **Item tag** -- `"#c:ores"` (leading `#`). Matches every market whose item is in the tag.
- **Glob** -- `"minecraft:*_ore"` (`*` = any character sequence). A glob without a namespace (e.g. `"*_ore"`) defaults to the `minecraft:` namespace, consistent with how ids work elsewhere.
- **Exact id + `components`** -- targets a specific component market, e.g. one particular enchanted book:

  ```json
  { "item": "minecraft:enchanted_book", "components": { "minecraft:stored_enchantments": { "levels": { "minecraft:mending": 1 } } }, "weightFactor": 1.0 }
  ```

  `components` cannot be combined with tag or glob matchers (warning, components ignored).

**Precedence:** when several entries match the same market, the **first matching entry in JSON order wins**. List specific entries (exact ids) before broad ones (tags/globs) -- in the example file, `minecraft:diamond` gets the full impact while `#c:ores` gives the rest of the ore markets 30%.

**Portability:** matchers always store item names, never the internal numeric market id -- definitions can be copied between servers and resolve against whatever markets exist there. Matchers are re-resolved at activation time, so markets created after loading are picked up automatically.

## The Scheduler Block

Global scheduler tuning. The block is optional and may appear in **any** file; every reload starts from the defaults, then each file's block overrides the fields it specifies -- files in alphabetical order, **last-loaded file wins per field**.

| Field | Default | Description |
|-------|---------|-------------|
| `minSecondsBetweenEvents` | `900` | Minimum seconds between two random event activations. |
| `maxSecondsBetweenEvents` | `3600` | Maximum seconds between two random activations. Each interval is sampled uniformly from `[min, max]`. If an attempt fires nothing (caps reached, everything on cooldown), the scheduler waits a full new interval. |
| `maxActiveEventsGlobal` | `3` | Cap of simultaneously active events across all markets. |
| `maxActiveEventsPerMarket` | `1` | Cap of simultaneously active events per market. A market at its cap is skipped by new events. |
| `historyMaxEntries` | `1000` | How many published news records the server keeps (and serves to the newspaper). `0` disables history (warning). See [History Persistence and Chunk Layout](#history-persistence-and-chunk-layout) for the retention semantics -- **a multiple of 100 gives exact retention**; other values fluctuate slightly. |

All fields must be non-negative numbers. `min > max` resets both to their defaults with an error.

When the scheduler interval elapses, the plugin picks one event with a **weighted random roll** among all eligible events -- eligible means: not `adminOnly`, `weight > 0`, off cooldown, not already active, all its [trigger requirements](advanced-events.md#trigger-requirements) hold, and at least one matched market is subscribed, news-enabled and below the per-market cap. The impact then applies exactly to that matched-and-eligible subset. ([Chain-fired](advanced-events.md#event-chains) events follow their own rules -- they bypass the caps and the cooldown.)

## Validation and Reloading

Definitions are validated on every load (`/stockmarket news reload` or server start):

- **Errors** (bad JSON syntax, missing `id`/`headline`/`text`, neither or both of `impact`/`sequences`, non-finite numbers, `peakFactor <= -1`, `min > max`, duplicate ids, unknown [requirement](advanced-events.md#trigger-requirements) types, ...) skip the affected event -- or the affected file for file-level problems. Everything else still loads. (A few field-local errors only drop the field, not the event: `picture`, single `records` entries and single `chains` entries.)
- **Warnings** (unknown fields, unresolvable items, empty `markets`, oversized texts, odd-but-legal combinations) keep the event loaded.
- A **bad file never crashes the server** -- loading is strictly skip-and-continue, and all problems are collected into a report that `/stockmarket news reload` prints to the command sender (grouped per file, errors before warnings) and that is also written to the server log.
- If a reload produces **no valid events at all** while a previous pool exists, the previously loaded definitions are **kept** -- a typo can never leave the server without news.
- Already-active events are never affected by a reload: they run on a snapshot of the values they started with.

## Admin Commands

All news commands live under `/stockmarket news`. The subcommands require operator permission level 2 **and** StockMarket admin status (`/stockmarket op`), same as the other management commands. They work identically on master and slave servers -- slave-issued commands are routed to the master automatically (tab completion of event ids is only available on the master; `stop` still suggests `all` on slaves).

| Command | Description | Admin only |
|---------|-------------|------------|
| `/stockmarket news` | Open the newspaper screen. | No -- every player. |
| `/stockmarket news reload` | Reload all JSON files from `config/StockMarket/news/` and print the validation report. A reload that yields no valid events keeps the old definitions. | Yes |
| `/stockmarket news trigger <eventId> [market]` | Fire an event **now**, bypassing cooldown, weight, `adminOnly`, the activity caps and the event's [trigger requirements](advanced-events.md#trigger-requirements) (the management GUI asks for confirmation when requirements are unmet; the command triggers without asking). The optional `[market]` (registry name in quotes, e.g. `"minecraft:diamond"`, or the numeric market id) restricts the impact to that single market for testing. The event must still match the market, and the market must be subscribed and news-enabled. | Yes |
| `/stockmarket news list` | Show all loaded definitions (with `adminOnly` / `active` / cooldown markers) and all active events with phase, remaining time, publication state and the current price factor per market. | Yes |
| `/stockmarket news stop <eventId\|all>` | **Hard-stop** one active event (or all of them): the event ends immediately in **any** phase -- including the reverting phase -- its price influence is removed, and its **full cooldown restarts** from the stop. A `reversal: "none"` event is cancelled without baking its permanent shift. | Yes |
| `/stockmarket news skipphase <eventId>` | Fast-forward an active event to the start of its **next phase**: pending → ramping → holding → reverting → expired (for [sequence events](advanced-events.md#multi-step-sequences): to the start of the next step). Skipping the last phase ends the event normally (the activation cooldown keeps ticking, no restart); for `reversal: "none"` skipping the hold bakes the full permanent shift like a natural completion. | Yes |
| `/stockmarket news info <eventId>` | Print the full definition of one event to the command sender (definition parameters, matcher list, phases or sequences, requirements, chains). Read-only companion to the details screen. | Yes |
| `/stockmarket news enable <eventId>` | Enable a disabled event so it is eligible again (random scheduling and manual trigger). Enabling does not by itself start the event. | Yes |
| `/stockmarket news disable <eventId>` | Disable an event so it can never fire -- not randomly, not via `trigger`. Disabling does not stop an already active run; use `stop` for that. | Yes |
| `/stockmarket news resetcooldown <eventId>` | Clear one event's remaining cooldown on the server. The event becomes immediately eligible again (same as the per-row **Reset CD** button in the plugin's [All events tab](overview.md#all-events-tab)). | Yes |
| `/stockmarket news scheduler show` | Print the four **effective** scheduler values (`minSecondsBetweenEvents`, `maxSecondsBetweenEvents`, `maxActiveEventsGlobal`, `maxActiveEventsPerMarket`) with an `(overridden)` marker on any admin override. | Yes |
| `/stockmarket news scheduler set <key> <value>` | Override one scheduler value at runtime without reloading the JSON. `<key>` is one of `minSecondsBetweenEvents`, `maxSecondsBetweenEvents`, `maxActiveEventsGlobal`, `maxActiveEventsPerMarket`; `<value>` is a positive integer (brigadier rejects zero and negatives). The override persists across reloads until explicitly reset. | Yes |
| `/stockmarket news scheduler reset [key]` | Clear all scheduler overrides (no argument) or one specific key so the JSON scheduler block applies again. | Yes |
| `/stockmarket news registry list` | Show the [world-event registry](advanced-events.md#the-world-event-registry): per-event fire records (count, first/last fire, in-game day) and all custom key/value pairs. | Yes |
| `/stockmarket news registry clear <all\|eventId>` | Wipe the whole registry (`all`) or delete one event's fire record -- the event counts as "never fired" again for the requirement predicates. | Yes |
| `/stockmarket news registry clear key <key>` | Delete one custom key/value pair from the registry. | Yes |

Notes:

- **Audit trail:** every manual `trigger`, `stop` and `skipphase` (and every `reload`, and every `registry clear` that actually deletes something) is broadcast to the other online StockMarket admins (those connected to the master server) -- manual triggers are insider information, and the broadcast is the paper trail.
- **Stop semantics:** `stop` means **cancel**. The event's price influence is removed immediately in whatever phase it was in, and the market returns to its pre-event level on its own (the price simulation re-derives its target every tick -- nothing is yanked). A `reversal: "none"` event whose shift has not baked yet is cancelled **without** baking -- to finalize the shift early on purpose, use `skipphase` instead. Stopping also discards the event's still-pending [chain](advanced-events.md#event-chains) firings and never fires its completion chains. If a stopped event had not published its headline yet (announce delay), the publication is suppressed: players never get news about an event an admin cancelled.
- Every admin command above has an equivalent control in the NewsPlugin's [management window](overview.md#the-news-plugin-management-window) -- see the [Active tab](overview.md#active-tab) (per-event Skip phase / Stop, Reload, Stop all), the [All events tab](overview.md#all-events-tab) (per-event Enable / Disable, Reset CD, Trigger with confirmation dialog) and the [Scheduler tab](overview.md#scheduler-tab) (live scheduler override editor and upcoming timeline).

## History Persistence and Chunk Layout

The server's news history -- the records the newspaper's *Load more* button walks back through -- is written under the world folder:

```
world/data/StockMarket/News/
    history/
        000.nbt              # oldest surviving chunk
        000.hashes.nbt       # sidecar 0
        001.nbt
        001.hashes.nbt
        ...
        NNN.nbt              # newest (only mutable) chunk
        NNN.hashes.nbt
    pictures/                # snapshotted picture bytes (see pictures.md)
    registry.nbt             # world-event registry (see advanced-events.md)
```

Each `NNN.nbt` chunk holds **exactly 100 records** (except the newest, which fills as records are published). The paired `NNN.hashes.nbt` sidecar lists the [picture](pictures.md) hashes referenced by that chunk plus its record count and uid bounds -- small enough that all sidecars stay resident, while older chunk data is lazy-loaded through a small LRU as the newspaper paginates backwards. Chunk indices are monotonic and are never reused after a chunk drop.

### Retention Semantics

The scheduler's `historyMaxEntries` (default **`1000`**, raised from `500` in v2.0.4) caps how many records the server keeps. After each publish, while the total record count is over the cap **and at least two chunks exist**, the entire oldest chunk file is dropped atomically -- there is no partial-chunk rewrite.

**Cap caveat:** pick `historyMaxEntries` as a **multiple of 100** (`500`, `1000`, `2000`, ...) for exact retention. With a non-multiple cap the retained count fluctuates between `cap` and `cap - 100` between drops (the drop happens only when the excess reaches a full chunk). Non-multiples still work -- the retention window is simply slightly larger than the cap you set. A cap below `100` is legal but pinned to the newest chunk (the newest chunk is never dropped), so it effectively behaves as "up to 100 records".

Cap changes from a `/stockmarket news reload` apply **lazily**: shrinking the cap does not immediately prune existing chunks -- the excess drops on the next publish. Loading the world never prunes either.

### Migration From Pre-v2.0.4 (Single File)

Servers upgrading from earlier versions have a single `world/data/StockMarket/News/history.nbt` file instead of the `history/` directory. On the **first server launch** after the upgrade the file is:

1. Split into 100-record chunks in chronological order (oldest 100 records go into `history/000.nbt`, the next 100 into `001.nbt`, and so on).
2. Verified on disk with rebuilt sidecars.
3. **Deleted** -- only after every chunk exists on disk. A `WARN` line in the server log records the migration.

If both the legacy single file **and** an already-populated `history/` directory exist (interrupted migration or manual tampering), the safe branch runs: the old file is left in place, an `ERROR` is logged, and the news system continues with the on-disk chunks. Investigate the folder before manually deleting the legacy file.

### Picture Garbage Collection

Each chunk's sidecar enumerates the picture hashes referenced by that chunk. On startup and after each history mutation, the [picture snapshot store](pictures.md#publishing-history-and-syncing) computes the **union** of every sidecar's hash list and retains only those pictures -- when a chunk is dropped, its pictures become unreferenced and are cleaned up on the next GC pass. The union is computed without loading any chunk record data; a missing or corrupt sidecar is auto-recovered by loading the chunk once and rebuilding the sidecar (`WARN` log line).

## Example: Adding Your Own News File

Create `config/StockMarket/news/my_server_events.json`:

```json
{
  "events": [
    {
      "id": "wheat_blight",
      "headline": "Blight wipes out the wheat harvest!",
      "text": "Farmers report entire fields lost to a fast-spreading blight. Bakers are already paying premium prices for the remaining stock.",
      "category": "agriculture",
      "weight": 8,
      "cooldownSeconds": 10800,
      "impact": {
        "type": "shock",
        "peakFactor": 0.5,
        "durationSeconds": 300,
        "reversalSeconds": 600
      },
      "markets": [
        { "item": "minecraft:wheat", "weightFactor": 1.0 },
        { "item": "minecraft:bread", "weightFactor": 0.6 }
      ]
    }
  ]
}
```

Then run `/stockmarket news reload` -- the file merges into the pool, and `/stockmarket news trigger wheat_blight` lets you watch the impact immediately. The generated `default_events.json` contains further examples for every schema feature (translation maps, tags, globs, negative weight factors, `adminOnly`, negative announce delays, [picture](pictures.md) references, and a permanent `reversal: "none"` event) -- including the [advanced features](advanced-events.md): a multi-step `sequences` event with two weighted story variants (`gold_rush_rumor`) and the gold-standard pair demonstrating `requires`, `records` and `chains`.
