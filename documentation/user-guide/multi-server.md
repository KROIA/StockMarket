# Multi-Server Setup

StockMarket can run on several connected servers (for example a survival and a creative server)
that share one stock market. It uses the master/slave link of BankSystem and ModUtilities: one
**master** server holds all markets, and every **slave** server forwards its players' requests to
it.

The connection itself (config file, shared secret, trust) is set up once for BankSystem and is
described in the BankSystem [Multi-Server Setup guide](https://github.com/KROIA/BankSystem)
(`documentation/user-guide/MultiserverSetup.md`). This page covers what StockMarket adds.

---

## Chapters
* [Requirements](#requirements)
* [Connecting the servers](#connecting-the-servers)
* [What runs where](#what-runs-where)
* [Trust](#trust)
* [Commands on a slave](#commands-on-a-slave)
* [When a slave loses the master](#when-a-slave-loses-the-master)

---

## Requirements

- **Same versions everywhere.** Master and all slaves must run the same StockMarket,
  BankSystem and ModUtilities version. In 2.2.0 the network format of market settings, presets,
  plugin settings and the price history changed, and the master rejects a slave with a
  different ModUtilities version (`MC_VERSION_MISMATCH`).
- **Clients** must also run the same StockMarket version as the servers.

---

## Connecting the servers

Edit `<ServerFolder>/config/modutilities/MultiServerConfig.json` on each server:

| Key | Master | Slave |
|---|---|---|
| `enable` | `true` | `true` |
| `isMaster` | `true` | `false` |
| `sharedSecret` | a secret string | the same string as the master |
| `slaveID` | ignored | a unique name per slave |
| `masterHost` | ignored | host name or IP of the master |
| `masterTcpPort` | port the master listens on (default `25575`) | the master's port |

A `config/MultiServerConfig.json` from an older version is moved to this location
automatically on first start. Restart the servers after editing the file.

---

## What runs where

| Part | Master | Slave |
|---|---|---|
| Markets, order book, matching engine | ✔ | forwards orders and reads to the master |
| Price, order and trade history database (`world/data/StockMarket/Database/`) | ✔ | — |
| [Order origin record](admin-setup.md#order-origin-record) (`stockmarket_diagnostics.db`): which server each player order came from | ✔ | — (the master records the slave's `slaveID`) |
| Market plugins, news events, bots | ✔ | — |
| Mod Settings (`world/data/StockMarket/settings.json`) | ✔ | not loaded; the master pushes derived data (villager prices) |
| Market presets | ✔ | — |
| Admin flag (`/stockmarket op`) | ✔ | — |
| Villager trading | ✔ | ✔ with the master's prices (pushed at every price refresh and when the Villager Trading settings change) |
| Placeholders | ✔ | ✔, values pulled from the master |
| Discord feed, profit alert | ✔ | — (trades on slaves are posted by the master) |
| Clock for trading hours and in-game candles | ✔ | players on slaves follow the master's clock |

Players on any server see the same markets, prices and their own orders. The Management GUI
(`/stockmarket manage`) works on slaves too; its requests go to the master. The **Mod Settings**
button is only shown on the master.

---

## Trust

Every slave is **untrusted** until you run, on the master:

<code>/banksystem trust &lt;slaveID&gt;</code>

From an untrusted slave the master serves public data (market list, prices, news) but refuses
placing and cancelling orders, every management read and write, and every per-player read (a
player's own orders, order history and preferences), because the slave names the player itself.
**Trust every slave on which players should trade.** See
[Access Control & Trust Model](../../README.md#access-control--trust-model).

---

## Commands on a slave

| Command | On a slave |
|---|---|
| `/stockmarket manage`, `/stockmarket news ...` | Work; requests are forwarded to the master |
| `/stockmarket <market> remove`, `/stockmarket open\|close <market>` | Forwarded to the master (trusted slave required) |
| `/stockmarket op\|deop`, `/stockmarket preset add`, `/stockmarket backup ...` | Master only; a slave answers with a "master only" message |
| `/stockmarket discord status` | Shows that the master sends the posts |

---

## When a slave loses the master

- Nothing is sent to the master while the link is down. Players on the slave cannot trade
  or open market data; their open orders stay on the master and keep running.
- Requests that were already sent but get no answer report *"no answer from the server"*. An
  order may then have been placed anyway: check **Pending Orders** / **My History** before
  placing it again. Nothing is resent automatically.
- The link reconnects on its own once the master is reachable again.
