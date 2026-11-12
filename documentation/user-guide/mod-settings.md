# Mod Settings Reference

All server-wide StockMarket settings, as shown in the **Mod Settings** screen.

- **File:** `world/data/StockMarket/settings.json` (master server only)
- **Screen:** `/stockmarket manage` → Overview tab → **Mod Settings**

---

## Chapters
* [Using the screen](#using-the-screen)
* [Utilities](#utilities)
* [Market](#market)
* [Villager Trading](#villager-trading)
* [Integrations](#integrations)
* [Multi-server notes](#multi-server-notes)

---

## Using the screen

- The **Mod Settings** button is only shown when you are connected to the **master server**.
  The server checks op level 2 and master status for every request.
- **Apply** validates and clamps the values on the server, saves them to `settings.json` and
  shows the confirmed values again. The button turns orange while there are unsaved changes.
- **Reload** fetches the current values from the server.
- **Defaults** fills the fields with the default values. Nothing is saved until you press Apply.
- Fields with the orange **⟳ Restart required** label are read once at server start. The new
  value is saved right away but only used after a restart. All other settings take effect
  immediately.
- If the server does not answer, the screen shows *"No answer from the server - the change may
  have been applied. Reopen to check."* Nothing is resent automatically; reopen the screen to
  see the saved values.
- Online admins get a chat note when someone changes the settings.

Use this screen instead of editing `settings.json` by hand.

---

## Utilities

| Setting | Key | Default | Range | Notes |
|---|---|---|---|---|
| Autosave interval (minutes) | `SAVE_INTERVAL_MINUTES` | 5 | 1 - 1440 | How often all StockMarket data is saved to disk |
| Log info messages | `LOGGING_ENABLE_INFO` | on | | |
| Log warnings | `LOGGING_ENABLE_WARNING` | on | | |
| Log errors | `LOGGING_ENABLE_ERROR` | on | | |
| Log debug messages | `LOGGING_ENABLE_DEBUG` | off | | Very verbose; for troubleshooting |
| Alert chat on by default | `ALERTS_DEFAULT_ON` | on | | Whether admins get trading alerts in chat until they use `/stockmarket alerts on\|off`. See [Trading alerts](admin-setup.md#turning-chat-alerts-off) |

---

## Market

Group `ServerMarket` in `settings.json`.

| Setting | Key | Default | Range | Notes |
|---|---|---|---|---|
| Orderbook array size | `VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE` | 1000 | 100 - 1000000 | Size of the virtual order book price array. Only new markets use a changed value. **Restart required** |
| Trading currency | `CURRENCY` | BankSystem money | | The item all trades settle in. **Restart required** |
| Candle time (ms) | `CANDLE_TIME` | 60000 | 1000 - 86400000 | Length of one price candle. Only used while *Candles: use in-game time* is off |
| Candles: use in-game time | `CANDLE_USE_GAME_TIME` | off | | One candle per in-game hour of the master's overworld; charts offer 1h / 4h / 1D / 1W. **Restart required**. See [Candle Timebase](candle-timebase.md) |
| Price history: max candles / response | `PRICE_HISTORY_MAX_CANDLES_PER_RESPONSE` | 4096 | 16 - 65536 | Server cap per price-history answer; the client pages for more |
| Price history: initial load candles | `PRICE_HISTORY_INITIAL_LOAD_CANDLES` | 512 | 16 - 8192 | Candles a chart requests when it opens (and per older page). Sent to every player at join, also on slave servers; changes reach online players right away |
| Trading hours: use real time | `TRADING_HOURS_USE_REAL_TIME` | off | | Trading hours follow the master's local clock instead of the in-game time. See [Trading Hours](trading-hours.md) |
| Trade cooldown (ms) | `TRADE_COOLDOWN_MS` | 500 | 0 - 60000 | Minimum time between two accepted orders of the same player, across all markets. 0 = off. Rejected orders do not restart it; bots are not affected |
| Max order size factor | `MAX_ORDER_VOLUME_FACTOR` | 64 | ≥ 1 | Default max order size of a market = `max(factor, factor x abundance)`. Used for new markets, presets and saves without a value |
| Net flow half-life (min) | `NET_FLOW_HALF_LIFE_MINUTES` | 360 | 0 - 10080 | Half-life of the faded net flow the [NetFlowPlugin](plugin-system/plugins/net-flow-plugin.md) uses. 0 = no fading. The real net flow is not affected |
| Virtual depth recovery half-life (min) | `VIRTUAL_DEPTH_RECOVERY_HALF_LIFE_MINUTES` | 30 | 0 - 10080 | Virtual volume consumed outside the visible order book window recovers toward the default; the missing part halves after this time. 0 = off |
| Virtual depth price ceiling (x price) | `VIRTUAL_DEPTH_MAX_PRICE_MULTIPLIER` | 100 | 2 - 10000 | No virtual sell volume above this multiple of the current price. A buy that walks the book stops there and its remainder is cancelled |
| Profit alert threshold | `PROFIT_ALERT_THRESHOLD` | 1,000,000 | | Realized profit one player may make within the window before admins are alerted. 0 = off. See [Profit alert](admin-setup.md#profit-alert) |
| Profit alert window (min) | `PROFIT_ALERT_WINDOW_MINUTES` | 60 | 1 - 10080 | Length of the profit alert window |
| Pump-and-dump detection | `PUMP_DUMP_ENABLED` | on | | Flags pump-and-dump patterns as admin alerts; detection only. See [Pump-and-dump detector](admin-setup.md#pump-and-dump-detector) |
| Pump price move (%) | `PUMP_DUMP_PRICE_MOVE_PERCENT` | 20 | | A player market buy moving the price more than this, with the player's own sell resting above, is flagged. 0 = off |
| Buy-sell cycle window (min) | `PUMP_DUMP_CYCLE_MINUTES` | 30 | 1 - 10080 | Buys and sells of the same item within this time form a cycle |
| Buy-sell cycle profit threshold | `PUMP_DUMP_CYCLE_PROFIT` | 100,000 | | Profit over the average buy price of a cycle above which the sell is flagged. 0 = off |

**Apply to all markets** (next to *Max order size factor*) recomputes the max order size of
**every** market from its abundance and the **saved** factor, after a confirmation. Custom
per-market limits, including 0 = unlimited, are overwritten. Press Apply first if you changed
the factor.

---

## Villager Trading

Group `VillagerTrading`. See [Villager Trading](../../README.md#villager-trading) for how the
prices are built.

| Setting | Key | Default | Range | Notes |
|---|---|---|---|---|
| Villager trading enabled | `ENABLED` | on | | Master switch for market-driven villager prices |
| Price refresh interval (minutes) | `PRICE_REFRESH_INTERVAL_MINUTES` | 20 | 1 - 10080 | Real-time minutes between price refreshes |
| Villager buy margin | `VILLAGER_BUY_MARGIN` | 0.8 | 0.01 - 100 | Multiplier on the market price when a villager buys from the player |
| Villager sell margin | `VILLAGER_SELL_MARGIN` | 1.2 | 0.01 - 100 | Multiplier on the market price when a villager sells to the player |
| Replace emeralds for unlisted items | `REPLACE_EMERALDS_FOR_UNLISTED` | off | | Emerald trades of items without a market are paid in money at the emerald market price (rounded to 2 significant digits, no margin). Needs an emerald market |
| Use preset prices for villagers | `USE_PRESET_PRICES_FOR_VILLAGERS` | off | | Only active while the emerald replacement is on (greyed out otherwise). Converted trades use the item's preset default price instead of the emerald price, even without a market. Items without a preset price use the emerald price and get no villager discount |

Villager discounts (reputation, Hero of the Village, cured zombies) on money trades are capped at 20% and count whole cost items, so a small money cost (fewer than five notes or coins) shows no discount.

Changes to this group are sent to all connected slave servers right away.

---

## Integrations

Group `Integrations`. Each integration has its own switchable section.

| Setting | Key | Default | Notes |
|---|---|---|---|
| Placeholders (TAB / Placeholder API) | `PLACEHOLDERS_ENABLED` | on | **Restart required**. See [Placeholders](placeholders.md) |
| Sync admin flag with LuckPerms | `SYNC_ADMIN_FLAG_WITH_PERMISSION` | off | **Restart required**. See [Syncing admin mode with LuckPerms](admin-setup.md#syncing-stockmarket-admin-mode-with-luckperms) |
| Discord feed (trades and alerts) | `DISCORD_ENABLED` | off | Master only. See [Discord feed](admin-setup.md#discord-feed) |

---

## Multi-server notes

- Slave servers never load `settings.json`. The master runs the markets and pushes derived data
  (for example villager prices) to the slaves. See [Multi-Server Setup](multi-server.md).
- Per-market values (default price, abundance, max order size, trading hours, virtual order
  book) are not Mod Settings; they are set in the **Market Settings** screen of each market.
