# StockMarket — Market Creation API

Public in-process integration SPI for creating, querying, and closing StockMarket
markets from **another mod** (or scripting bridge / admin tooling). BankSystem is
the first consumer, but this API is fully mod-agnostic — nothing here is
BankSystem-specific.

- **Package:** `net.kroia.stockmarket.api`
- **StockMarket version:** 2.0.8+
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
primitives. Slave servers may call the same API — StockMarket internally
dispatches to the master via its existing request path.

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
  returns `null`.
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
| `naturalAbundance` | `float` | Natural abundance used by the default orderbook volume-distribution plugin. | `10.0` | abundance units |
| `ignorePluginAutosubscribe` | `boolean` | When `true`, the new market is **skipped** by the create-time plugin auto-subscribe pass. Admins can still subscribe plugins manually afterwards. | `false` | — |

> The record may gain **additive** fields in future versions. Always build
> instances via the canonical constructor or `MarketConfig.defaults()` — never
> assume a fixed field count.

### `MarketConfig.defaults()`

Returns a commodity-style config: virtual orderbook enabled, price `1.0`,
abundance `10.0`, plugin auto-subscribe active. Equivalent to
`new MarketConfig(true, 1.0f, 10.0f, false)`.

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
        /* naturalAbundance          */ 0.0f,    // unused when no volume-distribution plugin
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
| `SUCCESS` | Market created. `reason()` is `null`. |
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

## 7. Pausing / resuming a market

Distinct from creating/deleting a market, you can **pause** (close) and **resume**
(open) trading on an existing market by toggling its `marketOpen` flag:

```java
void    setMarketOpen(@NotNull ItemID subject, boolean open);
boolean isMarketOpen(@NotNull ItemID subject);
```

- `setMarketOpen(subject, true)` — reopens the market for trading. It reappears on
  the client trade screen with an empty order book.
- `setMarketOpen(subject, false)` — closes (pauses) the market for trading.
- `isMarketOpen(subject)` — `true` iff a market exists for `subject` **and** it is
  currently open. Returns `false` if the market is closed **or** if no market
  exists for `subject`.

If no market exists for `subject`, `setMarketOpen` is a **no-op**. All methods are
**server-thread only**.

> **⚠️ WARNING — closing is DESTRUCTIVE.** `setMarketOpen(subject, false)`:
> - **cancels every open _player_ order**, refunding any locked balances via the
>   banking system (bot orders are left intact);
> - **hides the market from the client trade screen** — it is filtered out of the
>   available trading pairs (`GetAvailablePairsRequest`) while closed.
>
> Price history is **preserved** and the order book is emptied of player orders.
> Reopening with `setMarketOpen(subject, true)` makes the market reappear with an
> **empty** order book — cancelled orders are **not** restored.

```java
IStockMarketIntegration integration = StockMarketMod.getAPI().getIntegration();
if (integration == null) return;

// Pause trading (cancels + refunds all open player orders, hides from trade screen).
integration.setMarketOpen(subject, false);

// Query current state.
boolean open = integration.isMarketOpen(subject);   // false while paused

// Resume trading (market reappears with an empty order book).
integration.setMarketOpen(subject, true);
```

### `setMarketOpen(subject, false)` vs `closeMarket(subject)`

Two different operations — do not confuse them:

- **`setMarketOpen(subject, false)`** — **reversible pause**. The market keeps
  existing (price history intact); it is only hidden and its player orders are
  cancelled+refunded. Reopen it any time with `setMarketOpen(subject, true)`.
- **`closeMarket(subject)`** — **permanent delete** (see §8). The market is
  removed entirely (unsubscribed from all plugins, deleted, removal broadcast to
  clients). There is no "reopen"; you must `openMarket` again to recreate it.

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
