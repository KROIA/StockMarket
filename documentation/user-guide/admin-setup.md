# Admin Setup Use Cases

This guide covers two common ways to set up the stock market on a server, with concrete configuration steps and the trade-offs of each approach. Both use cases can coexist on the same server — for example, a bot-driven market for common items and a fully player-driven market for high-value goods.

## Chapters

* [Use Case 1: Standard Market with Virtual Orderbook (default)](#use-case-1-standard-market-with-virtual-orderbook-default)
* [Use Case 2: Player-Driven Market (virtual orderbook disabled)](#use-case-2-player-driven-market-virtual-orderbook-disabled)
* [Recommendation](#recommendation)
* [Permissions (LuckPerms)](#permissions-luckperms)
* [Discord feed](#discord-feed)
* [Profit alert](#profit-alert)
* [Frozen bank accounts](#frozen-bank-accounts)
* [Backups](#backups)
* [Order origin record](#order-origin-record)

---

## Use Case 1: Standard Market with Virtual Orderbook (default)

### What it is

The out-of-the-box setup. The **virtual orderbook** is enabled and populated by the built-in plugins — `DefaultOrderbookVolumeDistributionPlugin`, `TargetPriceBot`, `NetFlowPlugin` and `VolatilityPlugin`. Players always find liquidity at reasonable prices, and prices move naturally with player activity plus simulated market-maker behavior.

### Setup

This is the default configuration. No admin action is required beyond creating the markets themselves. See the main [README — For Admins / Single Player](../../README.md#for-admins--single-player) section for how to open the Management GUI and create markets, and the [Plugin System overview](plugin-system/overview.md) for the default plugins.

New markets created from the default preset come up with the virtual orderbook enabled and are auto-subscribed to the default plugins.

### When to use it

* Public survival servers.
* Small player populations where organic player-to-player liquidity would be too thin.
* Any setup where players are expected to receive near-instant fills.
* Admins who want a "living market" feel without needing a large active trader base.

### Trade-offs

* Prices are partly synthetic — the `TargetPriceBot` and `DefaultOrderbookVolumeDistributionPlugin` inject liquidity and drive price movement independently of real player supply and demand.
* The market does not reflect pure player-driven supply and demand as strongly as a fully player-driven exchange.

---

## Use Case 2: Player-Driven Market (virtual orderbook disabled)

### What it is

A market where **liquidity comes only from player limit orders**. No synthetic depth is added to the orderbook. Prices move only when players actually trade with each other. This is the closest analog to a real-world exchange within the mod.

### Setup

You can configure this at the preset level (so all new markets from that preset are player-driven automatically) or on an existing market.

**Option A — Preset-level (recommended for new markets):**

1. Open the Management GUI with `/stockmarket manage`.
2. Go to the **Presets** tab.
3. Create or edit a preset.
4. **Uncheck** the "Virtual Orderbook" option.
5. **Check** the "No Plugin Autosubscribe" option.
6. Save the preset.
7. New markets created from that preset will come up player-driven with no plugin subscriptions.

**Option B — Existing market:**

1. Open the Management GUI with `/stockmarket manage`.
2. Select the market on the **Overview** tab.
3. Click **Market Settings**.
4. **Uncheck** "Enable Virtual Orderbook".
5. Click **Apply**.
6. (Optional) Open the **Plugin Management** screen and unsubscribe any active plugin subscriptions from that market if you want to remove all bot influence.

Note: the **Clear** and **Reset Virtual Orderbook** buttons in the market settings only apply while the virtual orderbook is enabled. With it disabled they have no effect.

### When to use it

* **Roleplay / economy servers** where realism matters and prices should reflect actual player supply and demand.
* **PvP / faction servers** where scarcity is a deliberate game mechanic.
* **Small controlled trading circles** where every trade should be a real exchange between two players.
* Admins who want to experiment with true market dynamics without bot interference on selected items.

### How it differs from a market with the virtual orderbook enabled

* **No synthetic liquidity.** The orderbook is empty until a player places a limit order on it.
* **Prices only move on real trades.** Long periods without player activity leave the price frozen.
* **Orderbook plugins skip disabled markets.** `DefaultOrderbookVolumeDistributionPlugin`, `TargetPriceBot`, `NetFlowPlugin` and `VolatilityPlugin` automatically ignore markets that have the virtual orderbook disabled, even if the plugin is still subscribed.
* **Market orders may partially fill or cancel.** If not enough resting player orders exist on the opposite side, a market order fills what it can and cancels the rest. Reserved funds and items for the unfilled portion are automatically returned to the player.

### Problems and caveats admins should know about

* **Wide bid-ask spreads.** Low liquidity leads to large gaps between the best bid and the best ask. Market orders can slip significantly or fail entirely.
* **Dormant new markets.** A newly created market can sit with no price discovery until the first player places a limit order on each side.
* **Requires an active player base placing limit orders on both sides.** A market with only sellers or only buyers is effectively one-directional.
* **Failed / partial market orders auto-refund**, but the failed-fill user experience may confuse players unfamiliar with real exchanges. Communicate the setup to your player base — a pinned message or server MOTD helps.
* **Natural price drift.** The natural-price mechanism (based on `defaultPrice` and abundance) can still influence some server systems (news events, villager trade repricing, and volatility if it is enabled elsewhere), so the market can appear to drift without any player activity. Flag this behavior for players.

---

## Recommendation

For new servers, start with **Use Case 1** across the board. Once you have a stable and active player base, consider migrating specific high-value items to **Use Case 2** where realistic player-driven pricing matters more than guaranteed liquidity. The two setups can coexist — the choice is per market.

---

## Permissions (LuckPerms)

StockMarket admin commands and functions check these permission nodes, so a permission mod such as LuckPerms can grant or deny them per player or group. Without a permission mod, or when the permission mod leaves a node unset, each node requires operator level 2 (same as before).

| Node | Grants |
|------|--------|
| `stockmarket.admin.command.op` | `/stockmarket op` and `/stockmarket deop` (StockMarket admin mode of a player) |
| `stockmarket.admin.command.manage` | `/stockmarket manage` (the player must also have StockMarket admin mode) |
| `stockmarket.admin.command.preset` | `/stockmarket preset ...` |
| `stockmarket.admin.command.news` | `/stockmarket news ...` |
| `stockmarket.admin.command.backup` | `/stockmarket backup ...` |
| `stockmarket.admin.command.market` | `/stockmarket open <market>`, `/stockmarket close <market>` and `/stockmarket <market> remove` |
| `stockmarket.admin.command.exportrecipes` | `/stockmarket exportrecipes` (dev builds only) |
| `stockmarket.admin.command.devtest` | `/stockmarket devTestScreen` (dev builds only) |
| `modutilities.command.discord` | `/stockmarket discord status` and `/stockmarket discord reload` (a ModUtilities node, also used by `/modutilities discord` and `/banksystem discord`; without a permission mod it requires operator level 3) |
| `stockmarket.admin.notify` | Receiving chat notifications when another admin changes settings, and [profit alerts](#profit-alert) |
| `stockmarket.admin` | Source of the StockMarket admin flag while `SYNC_ADMIN_FLAG_WITH_PERMISSION` is on (see below) |

Example: give the `admin` group every StockMarket admin right:

```
/lp group admin permission set stockmarket.admin.* true
```

The wildcard is resolved by LuckPerms itself (its `apply-wildcards` option, on by default), so it works on Fabric, Quilt and NeoForge. Other permission mods may not support wildcards; set the nodes individually there.

Notes:

* The management GUI and the other in-game admin functions are gated by **StockMarket admin mode** (`/stockmarket op`), not by a node. The node `stockmarket.admin.command.op` controls who may switch admin mode on or off. Admin mode is stored on the master server, so it also works for players on slave servers.
* Nodes are only checked for players online on the server that runs the check. The console and command blocks always use operator level 2.
* Command nodes are checked when a player's command list is sent. After changing a node for an online player, they may need to rejoin before the command shows up or disappears.

### Syncing StockMarket admin mode with LuckPerms

The Mod Setting `SYNC_ADMIN_FLAG_WITH_PERMISSION` (Mod Settings → Integrations, "Sync admin flag with LuckPerms", default off, ⟳ Restart required) keeps StockMarket admin mode and the LuckPerms node `stockmarket.admin` in step, in both directions:

- `/stockmarket op <player>` sets admin mode and gives the player the node `stockmarket.admin=true` in LuckPerms (this works for offline players too). `/stockmarket deop <player>` clears admin mode and removes the player's own `stockmarket.admin` node.
- Changing the node in LuckPerms (commands, web editor, a group) updates admin mode straight away, without a rejoin.
- A LuckPerms group grant wins: if a group still grants `stockmarket.admin`, the player stays a StockMarket admin right after `deop`, and the command tells you so. Remove the group grant to deop the player.
- A player who joins for the first time while a LuckPerms group already grants `stockmarket.admin` becomes a StockMarket admin straight away. Note that `stockmarket.admin.*` does not include `stockmarket.admin` itself; grant it on its own.
- When the setting is first turned on (at server start or in the Mod Settings screen), every player who already has admin mode gets `stockmarket.admin=true` in LuckPerms, once. This never removes anything. A marker file `AdminFlagLuckPermsMigrated.marker` in the world's `data/StockMarket` folder records that it ran; delete it to run the migration again.
- With multiple servers: set the option on the master (slaves never load `settings.json`). A slave passes LuckPerms changes of its players on to the master, but only if the master trusts that slave (`/banksystem trust`); the master applies them only while its option is on. Untrusted slaves never change admin mode.
- Turning the setting on needs a restart of the master: LuckPerms changes only reach admin mode after it. `/stockmarket op`/`deop` and the one-time migration apply right away. Turning it off takes effect immediately.
- Worlds from older versions keep their value: the setting moved from the Utilities group to Integrations and is carried over on the first load.
- Without LuckPerms the setting does nothing: admin mode only changes through `/stockmarket op` and `deop`, and the node `stockmarket.admin` falls back to operator level 2.

## Discord feed

StockMarket can post player trades and trading alerts to Discord channels through webhooks (provided by ModUtilities). The feed is off by default, and only the master server posts.

1. Turn on **Mod Settings → Integrations → Discord feed (trades and alerts)** (`Integrations.DISCORD_ENABLED`, default off). It takes effect immediately, no restart needed.
2. Add your webhooks to `config/modutilities/discord/webhooks.json` on the master: `{"webhooks": {"trades": "<webhook URL>"}}`. Only Discord webhook URLs are accepted.
3. Enable and route the types in `config/modutilities/discord/stockmarket.json`. The file is created at the first master start, with every type disabled. Example: `"market-buy": {"enabled": true, "webhooks": ["trades"], "minAmount": 0}`.
4. Run `/stockmarket discord reload`. `/stockmarket discord status` shows the routing. Both commands cover the Discord setup of every mod, so `/modutilities discord` and `/banksystem discord` show the same overview. On a slave server, status only says that the master sends the posts.

| Type | Posted when | Fields |
|---|---|---|
| `market-buy` | A player's buy order against money is finished: fully filled, or canceled after some fills. | Player, Item, Item ID, Amount, Avg. price, Time |
| `market-sell` | The same for sell orders. | Same as `market-buy` |
| `market-exchange` | A player's inter-market order (item for item) is finished: fully filled, or canceled after some fills. One message per order, never one per leg. A partial fill shows what happened, e.g. sold 64, bought 0. | Player, Leftover, Currency, Sold item, Sold amount, Sold avg. price, Bought item, Bought amount, Bought avg. price, Sold item ID, Bought item ID, Inter-market group, Time |
| `market-alert` | A trading alert is raised, e.g. the profit alert. | Alert text, Time |

* Bot orders and orders without any fill are never posted.
* `minAmount` filters trades by item quantity. For `market-buy` and `market-sell` this is the traded quantity (`Amount`). For `market-exchange` it is the quantity the player entered in the order: the amount to sell when the player gave only that, otherwise the amount to buy. For a limit order that gave the amount to sell, this is the buy amount at the limit rate.
* `Leftover` is the money from the sale that was not spent on the purchase. It goes back to the player's account.
* The embed language follows `language` in `stockmarket.json` (`en_us`, `de_de`, `de_ch`, `zh_cn`). A type can override `title`, `color` and `fields`.
* Webhook URLs contain a secret token. They are never logged or shown in game; keep `webhooks.json` private.
* Alert texts name the player and the player's UUID. Route `market-alert` to a channel that only admins can read.

### Showing account numbers and UUIDs

To protect players' privacy, the default trade messages leave out the bank account number and the player UUID. They are still provided as values. To show them, give the type its own `fields` list in `stockmarket.json`. The list replaces the default fields completely, so it must contain every field you want to keep:

```json
"market-buy": {
  "enabled": true, "webhooks": ["trades"], "minAmount": 0,
  "fields": [
    {"name": "Player", "value": "{player}", "inline": true},
    {"name": "UUID", "value": "{player_uuid}", "inline": true},
    {"name": "Account", "value": "{account}", "inline": true},
    {"name": "Item", "value": "{item}", "inline": true},
    {"name": "Amount", "value": "{amount}", "inline": true},
    {"name": "Avg. price", "value": "{price}", "inline": true},
    {"name": "Time", "value": "{time}", "inline": false}
  ]
}
```

Do the same for the other trade types, then run `/stockmarket discord reload`. A field whose value is empty is left out. All numbers are plain numbers with a `.` as the decimal point, e.g. `2.5`.

Available values:

* `market-buy` and `market-sell`: `{player}`, `{player_uuid}`, `{account}` (`#<number>`), `{item}`, `{item_id}`, `{amount}`, `{price}` (average), `{time}`.
* `market-exchange`: `{player}`, `{player_uuid}`, `{account}`, `{sold_item}`, `{sold_item_id}`, `{sold_amount}`, `{sold_price}` (average), `{bought_item}`, `{bought_item_id}`, `{bought_amount}`, `{bought_price}` (average), `{leftover}`, `{currency}` (the currency's name), `{amount}` (the quantity the player entered, see `minAmount` above), `{interMarketGroup}`, `{time}`.

Your `fields` list is kept when StockMarket updates its default templates.

## Profit alert

The master server watches how much money each player makes from trading and warns the admins when it is unusually much, for example when a player has found a way to exploit the market.

* **Profit alert threshold** (`ServerMarket.PROFIT_ALERT_THRESHOLD`, default `1,000,000`, `0` = off) and **Profit alert window (min)** (`ServerMarket.PROFIT_ALERT_WINDOW_MINUTES`, default `60`, range 1 - 10080) are in the Market group of the [Mod Settings](mod-settings.md). Both take effect immediately.
* Counted is the **realized profit**: sell proceeds minus buy costs of the player's finished orders, summed over all markets. It is cash based, so selling items bought long ago also counts. Bot orders are not counted.
* Each player has fixed windows: a window starts with the player's first finished order and lasts the set number of minutes. When the profit within a window first goes above the threshold, an alert is raised once for that window. The windows are kept in memory only; a restart starts new ones.
* The alert goes to:
  * every online player with the permission `stockmarket.admin.notify` (operator level 2 without a permission mod), as a chat message: *"Alert: &lt;player&gt; realized &lt;amount&gt; trading profit within &lt;minutes&gt; min."*
  * the server log, as a warning starting with `[StockMarket Alert]`, with the player's name and UUID
  * the Discord type `market-alert`, if the [Discord feed](#discord-feed) is on and the type is enabled
* Only the master checks profits, so trades made on slave servers are covered too.

## Frozen bank accounts

When a BankSystem admin freezes a bank account, StockMarket cancels all of its open orders on every market (including inter-market orders), which releases the reserved money and items, and rejects new orders for it with *"This bank account is frozen by an admin and cannot trade"*. Unfreezing only allows trading again; cancelled orders are not restored. The master decides, so orders placed through slave servers are covered too.

## Backups

StockMarket keeps price candles, order history and other trade data in an SQLite database on the master: `world/data/StockMarket/Database/stockdata.db`. A copy made while the server writes to it can be broken. Pause the writes while you copy it:

| Command | Effect |
|---|---|
| `/stockmarket backup pause` | Finishes all queued writes, then stops writing. Wait for the console line `[StockMarket] db-worker paused for backup` before you start copying. |
| `/stockmarket backup resume` | Starts writing again. Writes that came in during the pause are queued and done now. |
| `/stockmarket backup status` | Shows whether the writes are paused and for how many seconds. |

* The commands need the permission `stockmarket.admin.command.backup` (operator level 2 without a permission mod) and only work on the master server. They work from the console, so a backup script can use them (for example through RCON).
* A pause ends by itself after **120 seconds**, with a warning in the log, so a forgotten `resume` cannot stop the market data for good. Copy the database within that time.
* Trading goes on during the pause. Saving to the database, and loading chart and order history (which also goes through the database queue), wait until it ends.
* The [order origin record](#order-origin-record) `stockmarket_diagnostics.db` is not paused by these commands, and a restore does not need it.

## Order origin record

On a multi-server network it helps to know on which server an order was placed, for example when you investigate suspicious trades. The master writes this to its own file, next to `stockdata.db`: `world/data/StockMarket/Database/stockmarket_diagnostics.db` (SQLite, open it with any SQLite browser).

The table `OrderOrigin` has one row per player order the master accepted:

| Column | Content |
|---|---|
| `ref` | The order's reference, the same as in the BankSystem transaction ledger: `sm:order:<id>` for a regular order, `sm:im:<uuid>` for an inter-market order (both legs share the row) |
| `origin_server_uid` | The permanent ID of the server the order was placed on, from ModUtilities (`world/data/ModUtilities/server_identity.json` on that server); empty if it is not known |
| `origin_name` | The slave's `slaveID`, or empty for an order placed on the master itself |

* Bot orders are not recorded. The account, item, amounts and time of an order are not repeated here; look them up in the order history or the BankSystem transaction ledger under the same reference.
* Only the master writes the file; it is created with the first player order.
* Gameplay never reads it. You can delete it while the server is stopped; it is created again with the next player order.
