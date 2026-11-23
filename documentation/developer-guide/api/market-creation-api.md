# StockMarket — Market Creation API

Public in-process integration SPI for creating, querying, and closing StockMarket
markets from **another mod** (or scripting bridge / admin tooling). BankSystem is
the first consumer, but this API is fully mod-agnostic — nothing here is
BankSystem-specific.

- **Package:** `net.kroia.stockmarket.api`
- **StockMarket version:** 2.1.0+ (client-side integration, §9: 2.2.0+)
- **Minecraft:** 1.21.1 · **Java:** 21

---

## 1. Overview

The API lets an external mod open a market for a given item, check whether one
exists, and close it — with full control over the market's initial price,
abundance, virtual-orderbook state, and plugin auto-subscription.

**Dependency direction is one-way:** your mod depends on StockMarket. StockMarket
does **not** depend on you. Because of this, StockMarket should be treated as an
**optional / soft dependency**: the game must still run if StockMarket is not
installed. Never hard-reference StockMarket classes on a code path that runs
before you have confirmed the mod is present, and isolate all StockMarket calls
behind a presence check (see the guard pattern in §8).

Everything in this API is **in-process**: no packets, no networking, no thread
primitives. The integration works on the **master** (or a single server), where
the markets live. It does **not** forward to the master on a slave server: there,
`openMarket` returns `FAILED` ("StockMarket server not initialized"),
`marketExistsFor` and `isMarketOpen` return `false`, and `closeMarket` /
`setMarketOpen` do nothing. Run your integration code on the master.

---

## 2. Getting the integration handle

Two-step lookup:

1. `StockMarketMod.getAPI()` returns the `StockMarketAPI` facade (mod id, version,
   and the integration accessor).
2. `StockMarketAPI.getIntegration()` returns the `IStockMarketIntegration` SPI,
   or `null` when no server is running in this JVM.

```java
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.api.integration.IStockMarketIntegration;

StockMarketAPI api = StockMarketMod.getAPI();          // never null once class is loaded
IStockMarketIntegration integration = api.getIntegration();
if (integration == null) {
    // No server in this JVM (pure client, or server not started yet). Abort.
    return;
}
```

**Availability & threading:**

- `getIntegration()` returns non-null only on the **server side** (dedicated or
  integrated), and only **after the server has started**. On a pure-client JVM it
  returns `null`. For client-side helpers (open the trade screen, embed a price chart)
  see [§9](#9-client-side-integration-220).
- **Every method on `IStockMarketIntegration` is server-thread only.** If you are
  on another thread, marshal onto the server thread yourself before calling.
- The instance is a lazily-cached singleton that survives server stop/start
  cycles within a JVM, so it is safe to re-fetch on each use.

---

## 3. Creating a market

```java
@NotNull MarketOpenResult.Result openMarket(@NotNull ItemID subject,
                                            @NotNull MarketConfig cfg);
```

`openMarket` intentionally **bypasses the preset system** so callers get
deterministic behaviour regardless of any preset that happens to match the
subject. All price/abundance/flag values come from `cfg`.

- `subject` — the `net.kroia.banksystem.util.ItemID` the market is for. (The
  subject type is BankSystem's `ItemID`; see §8 for construction.)
- `cfg` — a `MarketConfig` describing the market's initial state.

### `MarketConfig` fields

`net.kroia.stockmarket.api.integration.MarketConfig` is a `record`:

| Field | Type | Meaning | Default (`defaults()`) | Units |
|---|---|---|---|---|
| `virtualOrderbookEnabled` | `boolean` | Whether the market's synthetic (virtual) orderbook is active. When `false`, the market behaves as **empty** in the matching engine — only real player orders provide liquidity. | `true` | — |
| `defaultPrice` | `float` | Initial market price. | `1.0` | **display units** (same units as `MarketPreset.defaultPrice`); converted to raw amounts internally. |
| `naturalAbundance` | `float` | Natural abundance used by the default orderbook volume-distribution plugin. Since 2.2.0 it also sets the market's default **max player order size** (see below). | `100.0` | abundance units |
| `ignorePluginAutosubscribe` | `boolean` | When `true`, the new market is **skipped** by the create-time plugin auto-subscribe pass. Admins can still subscribe plugins manually afterwards. | `false` | — |

> The record may gain **additive** fields in future versions. Always build
> instances via the canonical constructor or `MarketConfig.defaults()` — never
> assume a fixed field count.

### `MarketConfig.defaults()`

Returns a commodity-style config: virtual orderbook enabled, price `1.0`,
abundance `100.0`, plugin auto-subscribe active. Equivalent to
`new MarketConfig(true, 1.0f, 100.0f, false)`. (Before 2.2.0 the abundance was `10.0`.)

### Max order size (2.2.0)

A new market's max player order size (`MarketSettings.maxOrderVolume`, real items) is
derived from `naturalAbundance`:

```
maxOrderVolume = max(f, round(f x naturalAbundance))     f = Mod Setting MAX_ORDER_VOLUME_FACTOR (default 64)
```

So `MarketConfig.defaults()` gives 6,400 items per order, and an abundance of `0` gives
`f` items (64 with the default setting). Bot orders are not limited. To change the
limit after creation, edit the market's settings (`ISyncServerMarket.getSettings()` /
`setSettings(MarketSettings)` on the master, or the Market Settings screen); `0` means
unlimited. Source: `MarketSettings.defaultMaxOrderVolume(float, long)`.

---

## 4. Recipes

### a) Standard commodity-style market (defaults)

Virtual orderbook liquidity + plugins behave exactly like a normally created
market.

```java
IStockMarketIntegration integration = StockMarketMod.getAPI().getIntegration();
if (integration == null) return;

MarketOpenResult.Result result =
        integration.openMarket(subject, MarketConfig.defaults());
```

### b) Pure user-interaction market (shares / companies)

A market whose price moves **only** from real player buy/sell orders — no
synthetic liquidity, no bots. This requires **two independent flags**, and
**both** must be set:

```java
IStockMarketIntegration integration = StockMarketMod.getAPI().getIntegration();
if (integration == null) return;

MarketConfig shareConfig = new MarketConfig(
        /* virtualOrderbookEnabled   */ false,   // no synthetic orderbook liquidity
        /* defaultPrice              */ 100.0f,  // starting share price (display units)
        /* naturalAbundance          */ 0.0f,    // no book depth; max order size = 64 items (see "Max order size")
        /* ignorePluginAutosubscribe */ true     // no default/custom plugins auto-subscribe
);

MarketOpenResult.Result result = integration.openMarket(subject, shareConfig);
```

**What each flag suppresses — they are independent:**

- `virtualOrderbookEnabled = false` — disables the market's synthetic orderbook.
  The underlying virtual orderbook is flagged disabled at creation, so the market
  immediately behaves as **empty** in the matching engine (no bot-provided
  liquidity). Player orders are the only liquidity.
- `ignorePluginAutosubscribe = true` — the create-time auto-subscribe pass skips
  this market, so **no** default or custom plugin (volatility bot, target-price
  bot, volume-distribution plugin, etc.) is attached on creation. This mirrors the
  behaviour of preset markets flagged to ignore auto-subscribe.

> **Warning:** setting only one flag leaves the other behavior active. If you set
> `virtualOrderbookEnabled = false` but leave `ignorePluginAutosubscribe = false`,
> plugins will still auto-subscribe and manipulate the market. If you set
> `ignorePluginAutosubscribe = true` but leave `virtualOrderbookEnabled = true`,
> the synthetic orderbook still provides liquidity. For a true pure-user-interaction
> market you must set **both**.

---

## 5. Handling the result

`openMarket` returns a non-null `MarketOpenResult.Result` record:

```java
public record Result(@NotNull Status status, @Nullable String reason) {
    public boolean isSuccess();   // status == SUCCESS
}
```

Preconditions are checked in order: already-exists → blacklisted → create.

| `Status` | Meaning |
|---|---|
| `SUCCESS` | Market created. `reason()` is `null`. Since 2.2.0 connected clients get the new market pushed and see it without reopening their screen. |
| `ALREADY_EXISTS` | A market for `subject` already exists — **no-op**. Makes `openMarket` naturally **idempotent**: safe to call again without side effects. |
| `ITEM_BLACKLISTED` | The subject item is blacklisted in the banking system and cannot be traded. `reason()` may explain. |
| `FAILED` | Creation failed for another reason (e.g. the underlying manager returned null). Inspect `reason()` for a short human-readable explanation. |

```java
switch (result.status()) {
    case SUCCESS         -> { /* market is live */ }
    case ALREADY_EXISTS  -> { /* nothing to do */ }
    case ITEM_BLACKLISTED -> log.warn("Blacklisted: {}", result.reason());
    case FAILED          -> log.error("Open failed: {}", result.reason());
}
```

---

## 6. Checking existence / closing

```java
boolean marketExistsFor(@NotNull ItemID subject);
void    closeMarket(@NotNull ItemID subject);
```

- `marketExistsFor(subject)` — `true` iff a market is currently registered for
  `subject`. (Server-thread only.)
- `closeMarket(subject)` — deletes the market: cancels every open player order
  (refunding locked balances via the banking system), unsubscribes it from all
  plugins, and broadcasts the removal to clients. If no market exists for
  `subject`, it is a **no-op**. (Server-thread only.)

> **Note on `ignorePluginAutosubscribe`:** it only blocks the **create-time**
> auto-subscribe pass. It does **not** prevent an admin from manually subscribing
> a plugin to the market later via the plugin management screen. If you need a
> market to stay plugin-free permanently, that is an operational policy, not a
> guarantee of this flag.

---

## 7. Maintenance mode

Distinct from creating/deleting a market, you can put an existing market into
**maintenance mode** and take it out again. These methods map to the market's internal
**maintenance** flag (`open == !maintenance`); the names are kept for compatibility
(BankSystem calls them reflectively by name). Maintenance blocks players only; bots keep
trading. The maintenance flag is **persisted**
and survives server restarts.

```java
void    setMarketOpen(@NotNull ItemID subject, boolean open);
boolean isMarketOpen(@NotNull ItemID subject);
```

- `setMarketOpen(subject, true)` — ends maintenance; players can trade again, starting from
  an order book without player orders.
- `setMarketOpen(subject, false)` — puts the market into maintenance mode.
- `isMarketOpen(subject)` — `true` iff a market exists for `subject` **and** it is
  currently not under maintenance. Returns `false` if the market is under maintenance **or** if no market
  exists for `subject`.

If no market exists for `subject`, `setMarketOpen` is a **no-op**. All methods are
**server-thread only**.

> **⚠️ WARNING — maintenance is DESTRUCTIVE.** `setMarketOpen(subject, false)`:
> - **cancels every open _player_ order**, refunding any locked balances via the
>   banking system (bot orders are left intact);
> - **rejects new player orders** — the market stays listed on the client trade screen,
>   which shows an "Under Maintenance" banner; only the pair list of
>   `GetAvailablePairsRequest` leaves it out while under maintenance;
> - **cancels inter-market orders** that reference the market.
>
> Bots keep trading on a market under maintenance.
>
> Price history is **preserved** and the order book is emptied of player orders.
> Reopening with `setMarketOpen(subject, true)` lets players trade again on an order
> book **without player orders** — cancelled orders are **not** restored.

```java
IStockMarketIntegration integration = StockMarketMod.getAPI().getIntegration();
if (integration == null) return;

// Enter maintenance (cancels + refunds all open player orders, rejects new ones).
integration.setMarketOpen(subject, false);

// Query current state.
boolean open = integration.isMarketOpen(subject);   // false while under maintenance

// End maintenance (players can trade again; no player orders in the book).
integration.setMarketOpen(subject, true);
```

### `setMarketOpen(subject, false)` vs `closeMarket(subject)`

Two different operations — do not confuse them:

- **`setMarketOpen(subject, false)`** — **reversible maintenance mode**. The market keeps
  existing (price history intact); it only rejects player orders, and its open player
  orders are cancelled+refunded. Reopen it any time with `setMarketOpen(subject, true)`.
- **`closeMarket(subject)`** — **permanent delete** (see §6). The market is
  removed entirely (unsubscribed from all plugins, deleted, removal broadcast to
  clients). There is no "reopen"; you must `openMarket` again to recreate it.

### Maintenance vs. trading hours

Maintenance is independent of a market's **trading hours** (see
[Trading Hours](../../user-guide/trading-hours.md)). `setMarketOpen` /
`isMarketOpen` only control and report the **maintenance** flag: `isMarketOpen` can be
`true` while the market is outside its trading window and refuses orders. Opening
or closing by schedule never cancels orders; maintenance does.

Lower-level market interfaces expose both states:

| Interface | Methods |
|---|---|
| `ISyncServerMarket` | `isUnderMaintenance()`, `setMaintenance(boolean)` (renamed from `isMarketOpen`/`setMarketOpen`, inverted meaning), `isTradingOpen()`, `getTradingState()` (`MarketSettings.TradingState`: `OPEN`, `CLOSED`, `MAINTENANCE`; `MAINTENANCE` wins), `setTradingHours(int openMinute, int closeMinute)`, `setTradingOverride(boolean open)`, `updateTradingHours(int minuteOfDay)` (called by the market manager each tick, master only) |
| `IAsyncMarket` | `isUnderMaintenanceAsync()`, `setMaintenanceAsync(boolean)`, `setTradingHoursAsync(int, int)` (admin-gated for players), `setTradingOverrideAsync(boolean)` (server-side only, not callable from clients) |

- Minutes are minutes of the day, `0..1440`. Values are clamped; `open == close` or
  `0/1440` means always open; `open > close` wraps over midnight. The window is
  half-open `[open, close)`.
- `setTradingHours` and `setTradingOverride` only store the value; the open state is
  re-evaluated at the end of the next market-manager tick.
- An override lasts until the scheduled state next flips (window edits and
  clock-source switches count), then it is cleared. It is persisted.
- Both async setters are refused when forwarded from an untrusted slave.

`CreateOrderRequest.Status.MARKET_CLOSED` (appended to the enum) is returned when an
order is placed on a market that is under maintenance or outside its trading hours. The check
runs before any funds are locked.

---

## 8. Threading & safety notes · FAQ / gotchas

- **Server-thread only.** All `IStockMarketIntegration` methods must run on
  the server thread. From another thread, marshal onto it first.
- **Server-only.** `getIntegration()` is `null` on a pure client and before the
  server starts. Always null-check.

**Soft-dependency guard pattern** — isolate StockMarket access so your mod loads
without StockMarket installed. Put the actual calls in a separate class that is
only touched when the mod is present, so its StockMarket imports are never
class-loaded otherwise:

```java
// Check presence (loader-specific). Fabric example:
if (FabricLoader.getInstance().isModLoaded("stockmarket")) {
    StockMarketBridge.openMarket(subject);   // separate class, isolates SM imports
}
// NeoForge: ModList.get().isLoaded("stockmarket")
```

```java
// StockMarketBridge.java — only referenced when stockmarket is loaded
final class StockMarketBridge {
    static void openMarket(ItemID subject) {
        IStockMarketIntegration integration = StockMarketMod.getAPI().getIntegration();
        if (integration == null) return;                 // server not up
        integration.openMarket(subject, MarketConfig.defaults());
    }
}
```

**ItemID construction.** The `subject` is BankSystem's
`net.kroia.banksystem.util.ItemID`. Obtain it the same way the banking system
does for the item you want a market for (e.g. `new ItemID(shortId)` where the id
comes from BankSystem's item registry). Do not fabricate arbitrary ids — the id
must correspond to a bank-known item, or the market will not be tradable and may
be rejected as blacklisted.

**Idempotency.** Re-calling `openMarket` for an existing subject returns
`ALREADY_EXISTS` and does nothing — safe for retry/setup-on-startup flows.

---

## 9. Client-side integration (2.2.0)

`net.kroia.stockmarket.api.integration.StockMarketClientIntegration` is a static helper for
another mod's **client** code, for example a screen that shows a company share. BankSystem
calls it by reflection, so its class name, method names and signatures do not change.

Unlike `IStockMarketIntegration` (server thread, master only), it works on any client, also
on a slave server: it uses the player's own server and the client market cache. All methods
are **client thread only** (the render thread, e.g. a button handler or `init()`).

```java
static boolean openTradeScreen(@NotNull ItemID market)
```

Opens the StockMarket trade screen with `market` selected, without the StockMarket block.
The server still checks every order.

- Returns `false` and sends nothing if the client market cache has no market for `market`:
  no market exists, it was deleted, a slave cannot resolve its item, the master was down when
  the player joined, or the player is not in a world.
- Otherwise sends a request to the player's server and returns `true`; the screen opens when
  the server answers. A slave without a master connection refuses and the player gets a
  "not connected" chat message instead. A closed market or one under maintenance opens
  normally and shows its banner.

```java
static @NotNull GuiElement createPriceChart(@NotNull ItemID market)
```

Creates a live candlestick chart of `market` to embed in your own screen (a ModUtilities
`GuiElement`; the object is a `DisplayCandlestickChart` and implements `AutoCloseable`).

- It subscribes to the market's live prices if the market is in the client cache. Otherwise it
  stays empty and retries on every render, so it fills in once the market appears.
- It unsubscribes on `close()` and when it is removed from its GUI, and reconnects on the next
  render if it is shown again.
- Its zoom and position are stored apart from the trade screen's; the first time it starts
  from the player's trade screen view of that market.
- **Call `((AutoCloseable) chart).close()` in your screen's `removed()`**, otherwise the price
  subscription stays open for the rest of the session. Calling it twice is harmless.

```java
// In a ModUtilities GuiScreen of your mod (only reached when StockMarket is loaded, see §8)
GuiElement chart = StockMarketClientIntegration.createPriceChart(shareItem);
addElement(chart);
// ...
@Override
public void removed() {
    try { ((AutoCloseable) chart).close(); } catch (Exception ignored) {}
    super.removed();
}
```
