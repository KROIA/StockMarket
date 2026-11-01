# Placeholders

StockMarket provides text placeholders for scoreboards, tab lists, chat formats and similar displays: live market prices, 24h change and volume per trading pair, plus each player's open orders and portfolio value.

## Chapters

* [Requirements](#requirements)
* [Syntax](#syntax)
* [Market placeholders](#market-placeholders)
* [Player placeholders](#player-placeholders)
* [Pair names](#pair-names)
* [Formatting](#formatting)
* [Multi-server (slave) behavior](#multi-server-slave-behavior)

---

## Requirements

A placeholder mod must be installed on the server:

| Placeholder mod | Loaders |
|---|---|
| [TAB](https://github.com/NEZNAMY/TAB) | Fabric, NeoForge |
| [Patbox Text Placeholder API](https://github.com/Patbox/TextPlaceholderAPI) | Fabric |

Without one of them the placeholders are simply not available. No configuration is needed; StockMarket registers its placeholders automatically.

---

## Syntax

Every placeholder lives in the `stockmarket` namespace. The syntax depends on the placeholder mod:

| Placeholder mod | Syntax | Example |
|---|---|---|
| TAB | `%stockmarket_<name>%` | `%stockmarket_price_diamond%` |
| Placeholder API | `%stockmarket:<name>%` | `%stockmarket:price_diamond%` |

Every placeholder has a formatted variant and a `_raw` variant. The `_raw` variant is a plain number (dot as decimal separator, 2 decimals, no thousands separators, no currency symbol), meant for conditions and calculations.

Values refresh every 2 seconds.

---

## Market placeholders

One set per trading pair (market). `<pair>` is the pair name, see [Pair names](#pair-names).

| Name | TAB | Placeholder API | Example | Description |
|---|---|---|---|---|
| `price_<pair>` | `%stockmarket_price_diamond%` | `%stockmarket:price_diamond%` | `$1'234.56` | Current market price |
| `price_<pair>_raw` | `%stockmarket_price_diamond_raw%` | `%stockmarket:price_diamond_raw%` | `1234.56` | |
| `change_<pair>` | `%stockmarket_change_diamond%` | `%stockmarket:change_diamond%` | `+3.21%` | Price change over the last 24 hours |
| `change_<pair>_raw` | `%stockmarket_change_diamond_raw%` | `%stockmarket:change_diamond_raw%` | `3.21` | |
| `volume_<pair>` | `%stockmarket_volume_diamond%` | `%stockmarket:volume_diamond%` | `12'500.00` | Items traded in the last 24 hours |
| `volume_<pair>_raw` | `%stockmarket_volume_diamond_raw%` | `%stockmarket:volume_diamond_raw%` | `12500.00` | |

- **Change** compares the current price with the opening price of the oldest price candle of the last 24 hours. Without price history it is `0`.
- **Volume** is the traded item quantity of the last 24 hours, including the currently running candle.
- The trading currency has no market and therefore no placeholders.

---

## Player placeholders

Evaluated for the player viewing the text.

| Name | TAB | Placeholder API | Example | Description |
|---|---|---|---|---|
| `open_orders` | `%stockmarket_open_orders%` | `%stockmarket:open_orders%` | `3` | Number of the player's open limit orders across all markets, including pending item/item (inter-market) orders |
| `open_orders_raw` | `%stockmarket_open_orders_raw%` | `%stockmarket:open_orders_raw%` | `3` | |
| `portfolio` | `%stockmarket_portfolio%` | `%stockmarket:portfolio%` | `$8'420.00` | Market value of the items in the player's personal bank account |
| `portfolio_raw` | `%stockmarket_portfolio_raw%` | `%stockmarket:portfolio_raw%` | `8420.00` | |

The **portfolio** counts items only (available and locked balance), valued at the current market price. Money is not included; items without a market count as 0.

---

## Pair names

Pair names are derived from the item's id, so they are the same on every server and never depend on world save data:

| Item | Pair name | Example placeholder (TAB) |
|---|---|---|
| `minecraft:diamond` | `diamond` | `%stockmarket_price_diamond%` |
| `create:brass_ingot` | `create_brass_ingot` | `%stockmarket_price_create_brass_ingot%` |
| Enchanted book (Mending I) | `enchanted_book_` + 6 hex chars, e.g. `enchanted_book_3fa91c` | `%stockmarket_price_enchanted_book_3fa91c%` |

Rules:

1. The `minecraft:` namespace is dropped; other namespaces are kept as `namespace_path`.
2. The name is lowercased and every character other than `a-z`, `0-9` and `_` becomes `_`.
3. Items with extra data (enchanted books, potions, ...) get a `_` plus a 6-character hash of that data appended, so different variants of the same item get different names.

> **Note:** the hash suffix is only stable within one Minecraft version. After a Minecraft update, names of items with extra data may change and the placeholders using them must be updated. Plain items (`diamond`, `create_brass_ingot`) are not affected.

If two different items end up with the same pair name, the item whose full id sorts first keeps it and the other one gets no placeholders; a warning is written to the server log.

---

## Formatting

Money values (`price`, `portfolio`) use BankSystem's money format setting (default `$%s`, e.g. `$1'234.56`), so they look the same as BankSystem's own placeholders. `volume` uses the same number style without the currency symbol. `change` is a signed percentage with 2 decimals.

---

## Multi-server (slave) behavior

All values are computed on the master server. A slave server pulls them from the master every 2 seconds:

- **Market placeholders** work on every connected slave.
- **Player placeholders** are only provided to slaves the master trusts (`/banksystem trust <slaveID>`). On an untrusted slave they are empty.
- While a slave is not connected to its master, the placeholders show the last received values (empty after a slave restart).
- Newly created markets appear on a slave within a few seconds.
