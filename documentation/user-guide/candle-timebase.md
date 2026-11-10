# Candle Timebase

Price candles can follow real time (the default) or the in-game clock. This is a **global** setting for all markets and all charts:

**Mod Settings → Market → "Candles: use in-game time"** (`ServerMarket.CANDLE_USE_GAME_TIME`, default: off, ⟳ Restart required)

| Setting | One stored candle covers | Chart timeframes | Time axis labels |
|---|---|---|---|
| Off (default) | **Candle time (ms)** of real time (`ServerMarket.CANDLE_TIME`, default 1 min) | 1m, 5m, 15m, 1h, 4h, 1d | Real date and time |
| On | One in-game hour (1000 ticks) of the master server's overworld | 1h, 4h, 1D, 1W (in-game) | `Day N` and `hh:00` |

- The value is read once at server start. Change it in the Mod Settings screen, then restart the master server.
- **Candle time (ms)** only applies while the setting is off.
- It is independent of the [trading hours clock](trading-hours.md#clock-source) (`TRADING_HOURS_USE_REAL_TIME`). For example, trading hours can follow real time while candles follow in-game hours.
- Only the master server needs it. Players on slave servers get the master's candles and clock automatically.

---

## In-game candles

- A candle closes when the in-game hour of the master's overworld changes (07:00, 08:00, ...). Tick 0 is 06:00 of `Day 0`.
- Days count from `Day 0` and roll over at 00:00. The time axis shows `hh:00`, plus `Day N` where a new day starts.
- 4h candles start at 00:00, 04:00, 08:00, 12:00, 16:00 and 20:00. 1D candles run from 00:00 to 00:00, 1W candles cover seven days (`Day 0`–`Day 6`, `Day 7`–`Day 13`, ...).
- **Daylight cycle frozen** (`/gamerule doDaylightCycle false`): the clock stands still, so a candle closes every 1000 ticks instead (about 50 seconds at 20 TPS). These candles all show the same `hh:00` label.
- **`/time set`** to another hour closes the running candle and opens a new one. `/time set` sets an absolute time, so the day number can jump back (`/time set day` = `Day 0`, 07:00).
- **No gap filling:** hours that are skipped, for example by sleeping through the night or with `/time add`, have no candles, and none are added later. While the server is stopped the in-game clock stands still, so a restart leaves no gap.
- [Placeholders](placeholders.md) (24h change and volume) still use the last 24 real hours.

---

## Switching and existing history

- Every candle stores both its real start time and its in-game start time, whichever mode is active. Switching the setting back and forth loses no history.
- Candles saved before this update have no in-game time. In in-game mode they keep their real date and time labels.
- Downgrading to an older mod version keeps the price history.

---

## Known limitations

- **Mods that change how fast the in-game clock runs:** if one in-game hour takes longer than 1000 server ticks, the 1000-tick fallback for a frozen daylight cycle closes the candle before the hour is over, so candles can be shorter than one in-game hour.
- **Frozen daylight cycle on 4h / 1D / 1W:** every stored candle has the same in-game hour, so a 4h / 1D / 1W candle closes after 4 / 24 / 168 fallback rotations (1000 ticks each) instead of at an in-game hour boundary. Right after a chart opens, its running candle can run over that count once.
