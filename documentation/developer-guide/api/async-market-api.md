# StockMarket — Market Access API (sync, async and client)

Reference for the market, market-manager, preset and command interfaces in
`net.kroia.stockmarket.api`, and for the **request-failure contract** that all
asynchronous calls follow since 2.2.0.

- **Package:** `net.kroia.stockmarket.api` (sub-packages `market`, `marketmanager`,
  `preset`, `pluginmanager`, `command`)
- **StockMarket version:** 2.2.0+
- **Minecraft:** 1.21.1 · **Java:** 21 · **Requires:** ModUtilities and BankSystem 2.2.0

---

## Chapters
* [1. Which API to use](#1-which-api-to-use)
* [2. Sync and async access](#2-sync-and-async-access)
* [3. Request failures (2.2.0)](#3-request-failures-220)
* [4. Market manager interfaces](#4-market-manager-interfaces)
* [5. Market interfaces](#5-market-interfaces)
* [6. Client-side API](#6-client-side-api)
* [7. Preset, plugin manager and command interfaces](#7-preset-plugin-manager-and-command-interfaces)
* [8. Prices and volumes: raw vs. real](#8-prices-and-volumes-raw-vs-real)

---

## 1. Which API to use

| You want to ... | Use | Where it runs |
|---|---|---|
| Create, query, close a market or switch maintenance from another mod | `IStockMarketIntegration` via `StockMarketMod.getAPI().getIntegration()` — see the [Market Creation API](market-creation-api.md) | Master server (or single server), server thread |
| Change prices, place bot orders, shape the order book | A market plugin — see the [Plugin System guide](../plugin-system/overview.md) and the [plugin API reference](../plugin-system/api-reference.md) | Master server, plugin update loop |
| Read markets, prices and orders, or place orders for the local player in a client-side addon (HUD, screen) | The client managers from `StockMarketModBackend.getClientInstances()` (§6) | Client |
| Understand how StockMarket itself talks between client, slave and master | The sync/async interfaces in this page (§2-§5) | Master, slave and client |

The market manager of a running server (`IMarketManager`) is **not** handed out to other
mods: `StockMarketAPI` only exposes `getModID()`, `getModVersion()` and `getIntegration()`.
Server-side integrations go through `IStockMarketIntegration`; the interfaces below
describe the contracts StockMarket uses internally and that the client managers expose.

---

## 2. Sync and async access

Every manager comes in a **sync** and an **async** flavour, picked through a small facade:

```java
public interface IMarketManager {
    boolean hasSyncAccess();                 // true on the master (or a single server)
    boolean hasAsyncAccess();                // master, or a slave connected to its master
    @Nullable IServerMarketManager getSync(); // null on a slave
    IAsyncMarketManager getAsync();          // always available on a server
    boolean isSlave();
    boolean isMaster();
}
```

`IPluginManager` has the same shape for plugins. Do not call either facade on the client.

| Side | `getSync()` | `getAsync()` behaviour |
|---|---|---|
| Master / single server | the real `ServerMarketManager` | the same object; async methods run locally and their futures complete at once |
| Slave connected to the master | `null` | every call is sent to the master over ModUtilities ARRS; the future completes when the answer arrives |
| Slave **not** connected | `null` | nothing is sent; the future completes at once with a fallback value (see §3.3) |
| Client | — | the client managers (§6) send ARRS requests to the server they are connected to, which forwards them to the master if it is a slave |

Sync methods (`ISyncServerMarketManager`, `ISyncServerMarket`) are **server-thread only** and
only exist where the data lives: on the master.

---

## 3. Request failures (2.2.0)

Before 2.2.0 an async request that got no answer left its future pending forever. Since
2.2.0 the futures of `IAsyncMarket`, `IAsyncMarketManager`, `IAsyncPresetManager`,
`IClientMarketManager.requestMarkets()` / `requestPendingOrders()`,
`IClientPluginManager.requestPluginList()` / `requestReorderPlugin()` and
`ClientMarket.createOrder()` (and its `createLimit*` / `createMarketOrder` shortcuts)
**complete exceptionally** when the request fails.

### 3.1 The two failure types

Both exceptions come from ModUtilities (`net.kroia.modutilities.networking.client_server.arrs`):

| Exception | Meaning | What the caller may do |
|---|---|---|
| `RequestOutcomeUnknownException` (extends `TimeoutException`) | The request **was sent**, but no answer arrived in time, or the connection was lost. The receiver may or may not have run it. | **Never undo, refund or resend automatically.** Tell the user the change *may* have been applied and let them check (e.g. reload the order list). Re-read the state before doing anything else. |
| `RequestNotSentException` (extends `IllegalStateException`) | The request was **never sent** (master/slave link not connected). Nothing happened on the receiver. | Safe to report as plain "failed"; a refund of something you reserved locally is safe. |

Treat **any other exception** (for example an answer that cannot be decoded) like an
unknown outcome.

Client-to-server requests are never checked before sending, so they fail only with
`RequestOutcomeUnknownException`. A client request that could not even be encoded (not in a
world) completes with `null` instead of failing; `ClientMarket.createOrder()` documents this.

### 3.2 Timeouts

| Request | Timeout |
|---|---|
| Price history (`MarketPriceHistoryRequest`), order history, transaction history | 120 s — the master reads them from its single-threaded database queue |
| Everything else | 30 s (ARRS default) |

The failures are active because BankSystem switches ModUtilities' request failures on at
start; with StockMarket 2.2.0 this is always the case.

### 3.3 Slave without a master connection

A slave that is not connected to its master does not send anything, and the async
methods complete **normally** with a fallback value instead of failing:

| Return type | Fallback |
|---|---|
| `Boolean` (writes, `marketExistsAsync`, `isStockmarketAdminAsync`) | `false` |
| `isUnderMaintenanceAsync()` | `true` (fail safe: treat the market as closed) |
| `List<...>` | empty list |
| `Long` / `Float` prices and volumes | `0` |
| `ItemID` (`getTradingCurrencyIDAsync`) | `ItemID.INVALID_ID` |
| `IAsyncMarket` (`getMarketAsync`, `createMarketAsync`) | `null` |
| `MarketSettings`, `PlayerPreferences`, `MarketPriceStruct` | a new default object |

Check `IMarketManager.hasAsyncAccess()` first if you need to tell "no master" from "no data".

### 3.4 Handling pattern

`CompletableFuture` callbacks may wrap the cause in a `CompletionException`; unwrap it:

```java
import java.util.concurrent.CompletionException;
import net.kroia.modutilities.networking.client_server.arrs.RequestNotSentException;
import net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException;

market.setMaintenanceAsync(true).whenComplete((ok, error) -> {
    if (error == null) {
        if (!ok) showMessage("Refused by the server");   // denied, untrusted slave, ...
        return;
    }
    Throwable cause = (error instanceof CompletionException && error.getCause() != null)
            ? error.getCause() : error;
    if (cause instanceof RequestNotSentException) {
        showMessage("Not sent: no connection to the master. Nothing changed.");
    } else {
        // RequestOutcomeUnknownException or anything else: the master may have applied it.
        showMessage("No answer from the server - the change may have been applied. Reload to check.");
        reloadStateFromServer();   // re-read; never undo or resend
    }
});
```

Rules of thumb:

- **Reads:** on failure, keep the old value or show "unavailable", and allow a later retry
  (a read has no side effect).
- **Writes and orders:** never retry automatically, never roll back a local reservation on
  `RequestOutcomeUnknownException`. StockMarket's own screens follow this: an order without
  an answer tells the player that it may have been placed and to check their orders.
- Futures complete on the game thread of the side that sent the request (the server thread
  on a slave, the client thread on a client), so callbacks may touch game state.

---

## 4. Market manager interfaces

### `IAsyncMarketManager` (`api.marketmanager`)

Available on master, slave and (internally) client. All methods return `CompletableFuture`s
that follow §3.

| Method | Returns | Notes |
|---|---|---|
| `getTradingCurrencyIDAsync()` | `ItemID` | The currency item markets trade against (`ServerMarket.CURRENCY`) |
| `getAvailableMarketIDsAsync()` | `List<ItemID>` | All existing markets |
| `marketExistsAsync(ItemID)` | `Boolean` | |
| `createMarketAsync(ItemID)` | `@Nullable IAsyncMarket` | Creates the market from its preset; `null` if it could not be created |
| `deleteMarketAsync(ItemID)` | `Boolean` | Open player orders on the market are cancelled |
| `getMarketAsync(ItemID)` | `@Nullable IAsyncMarket` | `null` if the market does not exist |
| `setStockmarketAdminModeAsync(UUID, boolean)` | `Boolean` | Admin flag of a player (see the [LuckPerms sync](../../user-guide/admin-setup.md#syncing-stockmarket-admin-mode-with-luckperms)) |
| `isStockmarketAdminAsync(UUID)` | `Boolean` | |
| `getPlayerPreferencesAsync(UUID)` | `PlayerPreferences` | Per-player trading screen preferences |
| `updatePlayerPreferencesAsync(UUID, PlayerPreferences)` | `Boolean` | |
| `onPlayerJoinAsync(UUID, String)` | `void` | Fire and forget; called by StockMarket on join |

Writes forwarded from an **untrusted** slave are refused (the future completes with
`false`/`null`); public reads keep working. See
[Access Control & Trust Model](../../../README.md#access-control--trust-model).

### `ISyncServerMarketManager` / `IServerMarketManager`

The master's manager implements `IServerMarketManager`, which is
`ISyncServerMarketManager` + `IAsyncMarketManager`. Server-thread only.

| Method | Notes |
|---|---|
| `getTradingCurrencyID()`, `getAvailableMarketIDs()`, `marketExists(ItemID)` | |
| `createMarket(ItemID)` | From the matching preset; `null` if it exists or is refused |
| `createMarketWithConfig(ItemID, MarketConfig)` | Bypasses presets; what `IStockMarketIntegration.openMarket` uses |
| `deleteMarket(ItemID)` | `false` if no market existed |
| `getMarket(ItemID)` | `@Nullable IServerMarket` |
| `getPlayerUUID(String)`, `setStockmarketAdminMode(UUID, boolean)`, `isStockmarketAdmin(UUID)` | |
| `getPlayerPreferences(UUID)`, `updatePlayerPreferences(UUID, PlayerPreferences)` | |
| `onPlayerJoin(UUID, String)` | |
| `getCurrentMarketPricesAndStartNewCandle()`, `update()` | Called by StockMarket's tick; do not call yourself |

---

## 5. Market interfaces

`IServerMarket` (the master's market object) is `ISyncServerMarket` + `IAsyncMarket`.
On slaves and clients, `IAsyncMarket` is a thin proxy that forwards each call to the master.

### `IAsyncMarket` (`api.market`)

Prices and volumes are **raw** backend values unless the method name says `Real` (see §8).

| Method | Returns | Notes |
|---|---|---|
| `getItemIDAsync()` | `ItemID` | Not a future: the market's item |
| `getDefaultPriceAsync()`, `getCurrentMarketPriceAsync()` | `Long` | Raw prices |
| `getCurrentTimeAsync()` | `Long` | Master time in ms |
| `getRawVolumeAsync(long)`, `getRawVolumeAsync(long, long)` | `Long` | Order book volume at a price / in a range |
| `getRealVolumeAsync(double)`, `getRealVolumeAsync(double, double)` | `Float` | Same in real units |
| `putOrderAsync(Order)` | `Boolean` | Places a player order. Sent from a slave, the master runs the same checks as for a player order (see below); `false` on any refusal. On the master it goes straight to the market without these checks |
| `getLimitOrdersAsync()` | `List<Order>` | Resting limit orders |
| `isUnderMaintenanceAsync()` | `Boolean` | Fallback `true` when the master is unreachable |
| `setMaintenanceAsync(boolean)` | `Boolean` | Entering maintenance cancels and refunds player orders and inter-market orders on the market; bots keep trading |
| `setTradingHoursAsync(int openMinute, int closeMinute)` | `Boolean` | Minutes of the day `0..1440`; admin-gated for players |
| `setTradingOverrideAsync(boolean open)` | `Boolean` | Server-side only (not callable by clients); see [Trading Hours](../../user-guide/trading-hours.md) |
| `getCurrentMarketPriceStructAsync()`, `getCurrentMarketPriceStructAndResetAsync()` | `MarketPriceStruct` | Current candle |
| `getSettingsAsync()`, `setSettingsAsync(MarketSettings)` | `MarketSettings` / `Boolean` | Market Settings screen data |
| `resetNetPlayerItemFlowAsync()` | `Boolean` | Resets the real and faded net flow (and the NetFlowPlugin anchor) |
| `resetVirtualOrderbookAsync()`, `clearVirtualOrderbookAsync()` | `Boolean` | Admin-gated; `false` when denied |

Writes are refused when they come from an untrusted slave; admin-gated writes are refused for
players without StockMarket admin mode. A refusal completes the future with `false`; it is
not an exception.

**`putOrderAsync` from a slave (2.2.0).** The master uses only the order's market, player,
bank account, type, volume and price, and places it like a player order from the trading
screen:

- the market must exist and be open, the volume must not be 0 and the price not negative;
- the max order size and the player's trade cooldown apply;
- the player must be a member of the bank account with the WITHDRAW and DEPOSIT permissions
  (or its personal owner) and have enough money (buy) or items (sell);
- the money or items are locked for the order, and released again if the market refuses it.

Bot orders (no player), inter-market orders and orders for a different market than the one
the call was sent to are refused. The answer is only `true`/`false`; use
`ClientMarket.createOrder()` when you need the reason (§5, *Order rejection codes*).

### `ISyncServerMarket` (`api.market`)

The master-side market, server-thread only. Highlights (see the Javadoc for the rest):

| Method | Notes |
|---|---|
| `getMaxRealOrderVolume()` | Max items per player order, `0` = unlimited (2.2.0) |
| `putOrder(Order)`, `cancelOrder(UUID, long, Order.Type, long, long)`, `getLimitOrders()` | |
| `isUnderMaintenance()`, `setMaintenance(boolean)` | Renamed from `isMarketOpen()`/`setMarketOpen()` in 2.2.0, inverted meaning |
| `isTradingOpen()`, `getTradingState()` | `MarketSettings.TradingState`: `OPEN`, `CLOSED`, `MAINTENANCE` (maintenance wins) |
| `setTradingHours(int, int)`, `setTradingOverride(boolean)` | Stored now, applied at the end of the next market-manager tick |
| `getNetPlayerItemFlow()`, `setNetPlayerItemFlow(long)`, `resetNetPlayerItemFlow()` | Real net flow in raw units, positive = players net-sold |
| `getFadedNetPlayerItemFlow()`, `setFadedNetPlayerItemFlow(double)` | Faded copy, half-life `NET_FLOW_HALF_LIFE_MINUTES` (2.2.0) |
| `resetVirtualOrderbook()`, `clearVirtualOrderbook()` | |
| `getSettings()`, `setSettings(MarketSettings)` | |

Methods prefixed `test_` are internal hooks; do not call them from addon code.

### Order rejection codes

`CreateOrderRequest.Status` is the answer of a player order (`ClientMarket.createOrder()`).
2.2.0 appended three values; the ordinal is the wire format, so the old values keep their
numbers:

| Status | Meaning | `OutputData.detail` |
|---|---|---|
| `MARKET_CLOSED` | Market under maintenance or outside its trading hours. Checked before any funds are locked | `0` |
| `ORDER_TOO_LARGE` | The amount is above the market's max order size | the limit in items |
| `TRADING_TOO_FAST` | The player's trade cooldown has not run out | remaining ms |

---

## 6. Client-side API

```java
StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
if (client == null) return;   // not in a world (or already disconnected)
```

`ClientInstances` (class `net.kroia.stockmarket.StockMarketModBackend`) holds, per
connection:

| Field | Type |
|---|---|
| `MARKET_MANAGER` | `IClientMarketManager` |
| `PLUGIN_MANAGER` | `IClientPluginManager` (admin data; the server refuses non-admins) |
| `PRESET_MANAGER` | `IAsyncPresetManager` (admin data) |

The holder is created when the player joins a world and set to `null` when they leave, so
fetch it again instead of caching it.

### `IClientMarketManager`

| Method | Notes |
|---|---|
| `requestMarkets()` | `CompletableFuture<List<ItemID>>`; also refreshes the local market cache and drops markets the server no longer has. Fails per §3, and fails with `IllegalStateException` if the request could not be sent (never read as "no markets") |
| `getAvailableMarkets()` | The cached list from the last `requestMarkets()` |
| `getMarket(ItemID)` | `@Nullable ClientMarket` from the cache |
| `requestPendingOrders(itemFilter, bankAccountNr, playerFilter, timeBegin, timeEnd)` | `CompletableFuture<ActiveOrdersRequest.OutputData>`; fails per §3 |
| `getTradingCurrencyIDAsync()` | |
| `requestCreateMarket(ItemID)` | Admin only. `CompletableFuture<Boolean>`: `true` once the server created the market, even if loading it into the local cache got no answer (the next `requestMarkets()` loads it); `false` otherwise |
| `requestDeleteMarket(ItemID)` | Admin only |
| `getCrossRateMarket(have, want)` | `IPriceDataProvider` for a pair chart (`want price / have price`) |

### `ClientMarket` — placing an order for the local player

```java
ClientMarket market = client.MARKET_MANAGER.getMarket(itemID);
if (market == null) return;

// Market buy of 16 items from bank account 'accountNr' (negative volume = sell).
market.createMarketOrder(accountNr, 16).whenComplete((answer, error) -> {
    if (error != null) {
        // RequestOutcomeUnknownException: the order may have been placed. Do NOT resend;
        // ask the player to check their open orders.
        return;
    }
    if (answer == null) return;   // could not be sent (not in a world)
    switch (answer.status) {
        case CREATED          -> { /* ok */ }
        case ORDER_TOO_LARGE  -> showMessage("Max order size: " + answer.detail);
        case TRADING_TOO_FAST -> showMessage("Wait " + answer.detail + " ms");
        case MARKET_CLOSED    -> showMessage("Market closed");
        default               -> showMessage("Rejected: " + answer.status);
    }
});
```

Other order helpers: `createLimitOrder(accountNr, volume, price)`,
`createLimitBuyOrder(accountNr, volume, price)`, `createLimitSellOrder(accountNr, volume, price)`
and the general `createOrder(accountNr, Order.Type, volume, price)`. Volumes and prices are
real units.

`ClientMarket` also implements `IPriceDataProvider` (candles, current price, paging of older
history) for charts.

---

## 7. Preset, plugin manager and command interfaces

### `IAsyncPresetManager` (`api.preset`)

Admin-only preset catalog, used by the Presets tab. Futures follow §3.

| Method | Returns |
|---|---|
| `getCategoriesAsync()` | `List<MarketPresetCategory>` |
| `updatePresetsAsync(List<MarketPreset>)` | `Boolean` |
| `saveCategoryAsync(MarketPresetCategory)` | `Boolean` |
| `deleteCategoryAsync(String)` | `Boolean` |
| `renameCategoryAsync(String oldName, String newName)` | `Boolean` |

### `IClientPluginManager` (`api.pluginmanager`)

Client view of the plugin list (admin data). `requestPluginList()` and
`requestReorderPlugin(UUID, int)` return the request future and fail per §3. Other requests:
`requestUpdateSettings`, `requestUpdateCustomSettings`, `requestUpdateSubscription`,
`requestCreatePlugin`, `requestDeletePlugin`. `getPluginDataList()`, `getPluginCount()` and
`getEnabledPluginCount()` read the last synced data.

Server side, `ISyncServerPluginManager` offers `autoSubscribeNewMarket(ItemID)` and
`removeCache(ItemID)`, which StockMarket calls when markets are created and deleted.
`IAsyncPluginManager` has no methods yet.

### Command interfaces (`api.command`)

`IStockMarketCommands` mirrors `IMarketManager` for the `/stockmarket` command handlers:

| Interface | Methods | Runs on |
|---|---|---|
| `IAsyncStockMarketCommandHandler` | `stockmarket_manage_async(UUID executor)`, `stockmarket_devTestScreen_async(UUID executor)` (dev builds) | Master and slave |
| `IServerStockMarketCommandHandler` | `stockmarket_setStockmarketAdminMode(UUID executor, boolean)`, `stockmarket_setStockmarketAdminMode_user(UUID executor, String userName, boolean)` | Master only |

These back the commands in the [README](../../../README.md#commands); they are not meant to
be called by other mods.

---

## 8. Prices and volumes: raw vs. real

StockMarket stores prices and volumes as integers (**raw** / backend values). The **real**
value is the raw value divided by the fraction scale factor of the BankSystem item (money
and items can have decimal places). Methods without `Real` in their name (`getDefaultPriceAsync`, `getRawVolumeAsync`,
`getNetPlayerItemFlow` on `ISyncServerMarket`, ...) use raw values. Plugins get conversion
helpers on `IPluginMarket` (`convertBackendPriceToRealPrice`, `convertRealPriceToBackendPrice`).
