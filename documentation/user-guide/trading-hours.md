# Trading Hours

Every market can have a daily trading window, like a real stock exchange. Outside that window the market is **closed**: nobody can place new orders and the price stands still until the market reopens. Admins can also open or close a market by command, independent of the schedule.

By default every market is open around the clock (00:00–24:00), so nothing changes until you set hours.

## Chapters

* [Clock source](#clock-source)
* [Setting the trading window](#setting-the-trading-window)
* [What "closed" means](#what-closed-means)
* [What players see](#what-players-see)
* [Commands](#commands)
* [Closed vs. Maintenance](#closed-vs-maintenance)
* [Multi-server (master/slave) behavior](#multi-server-masterslave-behavior)

---

## Clock source

Which clock the trading hours follow is a **global** setting that applies to all markets:

**Mod Settings → Market → "Trading hours: use real time"** (`TRADING_HOURS_USE_REAL_TIME`, default: off)

| Setting | Clock | Notes |
|---|---|---|
| Off (default) | In-game time of the master server's overworld | Day-time tick 0 = 06:00, tick 6000 = 12:00, tick 18000 = 00:00. `/time set` moves the clock. With `doDaylightCycle false` the clock stands still, so the markets stay in whatever state they are in. |
| On | Real local time of the master server | Uses the time zone of the machine the master server runs on. |

The setting takes effect immediately, no restart needed. If switching the clock moves a market in or out of its window, that counts as a scheduled transition (see [Commands](#commands)).

Price candles have their own, independent clock setting, see [Candle Timebase](candle-timebase.md).

---

## Setting the trading window

Open **Stock Market Management**, select a market and open its **Market Settings**. The **Trading hours** row has two time fields: open and close.

- Format `HH:MM` (also `H`, `HH` or `H:MM`). Hours 0–24, minutes 0–59. `24` is only valid as `24:00`.
- The window includes the open time and ends at the close time: `08:00`–`18:00` is open from 08:00 up to 17:59.
- **Overnight windows** are allowed: if open is later than close, the window wraps over midnight. `22:00`–`06:00` is open in the night and closed during the day.
- `00:00`–`24:00` means **always open** (the default). Any other window where open equals close is invalid.
- An invalid time turns the field red, shows *"Invalid time (HH:MM, 00:00-24:00)"* and blocks **Apply** and **Apply to all**.

The new window is used from the next server tick on.

### Apply to all

**Apply to all** copies the entered window to **every** market. A confirmation dialog asks first; cancelling changes nothing. Only the trading window is copied, no other market setting. Afterwards the widget reports how many markets were updated.

### Server time display

Below the trading hours row the widget shows both clocks of the server, refreshed every second, and which one is active:

`Server time 14:32 · In-game 07:15 (active: in-game)`

Use it to check what the markets are actually going by, especially when the server runs in a different time zone than you.

---

## What "closed" means

While a market is closed:

- **No new orders.** Orders from players, bots, plugins and inter-market (pair) trades are all refused. Players get the message *"Market is closed - outside trading hours"*.
- **Pending orders are kept.** Limit orders already in the book stay there and can still be cancelled. They can fill again once the market reopens.
- **Moving an order on the chart is refused.** A move is a cancel plus a new order, and the new order would be rejected, so the marker snaps back and the closed message is shown. Cancel the order instead, or wait for the reopen.
- **Inter-market orders are queued.** Pending pair orders whose have- or want-market is closed are skipped and retried; they fill after both markets are open again. New pair orders on a closed market are refused.
- **The price is frozen.** No matching takes place, so the market price does not change. The chart keeps drawing candles, but they are flat with zero volume.
- **Plugins skip closed markets.** Bots and other plugins do not trade on a closed market. Their state is kept, so they continue where they stopped when the market reopens.
- **News still applies.** News events can still move a closed market's reference (default) price. When the market reopens the price can therefore **gap** to the new level, like a real stock after overnight news.

---

## What players see

The trading panel of the terminal always shows the trading hours and the current clock:

- `Trading hours 08:00-18:00 · 14:32 (in-game)` — scheduled window, active clock.
- `24h · 14:32 (server)` — market is always open.

When trading is not possible a red banner covers the trading panel and the buy/sell buttons are disabled:

| Banner | Meaning |
|---|---|
| **Market Closed** | Outside the trading hours, or closed by command. |
| **Under Maintenance** | An admin put the market into maintenance mode (see [Closed vs. Maintenance](#closed-vs-maintenance)). Maintenance wins if both apply. |

The open-orders list stays usable, so pending orders can still be cancelled. In pair mode both markets must be open; the panel shows the more restrictive of the two.

---

## Commands

StockMarket admin mode (`/stockmarket op`) required; operator level is not needed. From the console: operator level 2.

| Command | Effect |
|---|---|
| `/stockmarket open <market>` | Forces the market open until its next scheduled transition. |
| `/stockmarket close <market>` | Forces the market closed until its next scheduled transition. |

**`<market>`** is the market's registry name in quotes, e.g. `/stockmarket close "minecraft:diamond"`, or its numeric ID. When several markets share one registry name (item variants with components, e.g. different enchanted books), use the numeric ID; the tab-completion tooltip shows the item's full name (e.g. *"Enchanted Book – Sharpness V"*). An ambiguous name lists the matching IDs.

How long the override lasts:

- It holds until the schedule itself flips (the next open or close time). Example: window 08:00–18:00, `/stockmarket open` at 20:00 keeps the market open until 08:00; from then on it follows the schedule again.
- On an **always-open** market there is no scheduled transition, so `close` holds until you run `open`.
- Editing the window or switching the clock source also counts as a transition if it changes whether the market is scheduled to be open. That ends the override.
- The override is saved and survives server restarts.
- The commands do not end maintenance. A market under maintenance stays under maintenance.

---

## Closed vs. Maintenance

Both stop player trading, but they serve different purposes.

| | Closed (trading hours / command) | Maintenance (Management screen / API) |
|---|---|---|
| Purpose | Schedule, realism | Admin action, exploit prevention, e.g. while changing market settings |
| Set by | Trading window, `/stockmarket open\|close` | "Maintenance mode" checkbox in Market Settings, **Maintenance all / End maintenance all** in Stock Market Management, other mods via the API |
| Player orders | New orders refused, pending orders **kept** | All pending player orders **cancelled and refunded** |
| Inter-market orders | Queued until reopen | **Cancelled** |
| Bots / plugins | Paused | **Keep trading** |
| Price | Frozen (flat candles) | Keeps moving (bots trade) |
| Pair trading list | Market stays listed | Market hidden |
| Banner | Market Closed | Under Maintenance |
| Saved over restarts | Yes (window and command override) | Yes |

---

## Multi-server (master/slave) behavior

- The schedule is evaluated only on the **master** server, using the master's clock (in-game overworld time or the master's local real time).
- Slaves and their players see the master's state and clock, in the trading panel as well as in the Market Settings server-time row.
- Trading-hour edits and the open/close commands work from slave servers too; they are forwarded to the master. Untrusted slaves are refused.
