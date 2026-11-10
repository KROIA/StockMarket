# StockMarket Documentation

Index of all documentation pages. For an introduction to the mod, its blocks, commands and features, start with the [main README](../README.md).

## User Guide
For players, server admins and content creators.

- [Admin Setup Use Cases](user-guide/admin-setup.md) — standard bot-driven market vs. player-driven market, LuckPerms permissions, Discord feed, profit alert, database backups
- [Mod Settings Reference](user-guide/mod-settings.md) — every server-wide setting with default, range and restart note
- [Multi-Server Setup](user-guide/multi-server.md) — master/slave servers: what runs where, trust, commands on a slave
- [Trading Hours](user-guide/trading-hours.md) — daily open/close windows, `/stockmarket open|close`, closed markets vs. maintenance mode
- [Candle Timebase](user-guide/candle-timebase.md) — price candles on real time or in-game hours, chart timeframes and `Day N` labels
- [Placeholders](user-guide/placeholders.md) — text placeholders (prices, 24h change, volume, player orders and portfolio) for scoreboards, tab lists and chat

### News System
- [Overview](user-guide/news-system/overview.md) — how news events publish headlines and move prices
- [Configuration](user-guide/news-system/configuration.md) — JSON reference and admin commands
- [Authoring Tutorial](user-guide/news-system/authoring-tutorial.md) — write your own news events step by step
- [Advanced Events](user-guide/news-system/advanced-events.md) — sequences, requirements, registry and chains
- [Pictures](user-guide/news-system/pictures.md) — add pictures to news events
- [Default Events Catalog](user-guide/news-system/default-events-catalog.md) — the built-in news events

### Plugin System
- [Overview](user-guide/plugin-system/overview.md) — what market plugins do
- [Managing Plugins](user-guide/plugin-system/management.md) — add, configure and subscribe plugins in-game
- [NetFlowPlugin](user-guide/plugin-system/plugins/net-flow-plugin.md) — flow-driven equilibrium, Min/Max Price Multipliers, long-term anchor
- [VolatilityPlugin](user-guide/plugin-system/plugins/volatility-plugin.md) — random walk around the equilibrium
- [TargetPriceBot](user-guide/plugin-system/plugins/target-price-bot.md) — PID gains and how fast the price follows its target
- [Order Book Volume Distribution](user-guide/plugin-system/plugins/orderbook-volume-distribution.md) — order book depth and refill speed
- [NewsPlugin](user-guide/plugin-system/plugins/news-plugin.md) — per-market news sensitivity and scheduler values

## Developer Guide
For mod developers extending StockMarket.

### Plugin System
- [Overview](developer-guide/plugin-system/overview.md) — plugin architecture
- [Getting Started](developer-guide/plugin-system/getting-started.md) — write your first plugin
- [API Reference](developer-guide/plugin-system/api-reference.md) — plugin API
- [Examples](developer-guide/plugin-system/examples.md) — example plugins

### API
- [Market Creation API](developer-guide/api/market-creation-api.md) — create, query and close markets from other mods
- [Market Access API](developer-guide/api/async-market-api.md) — sync, async and client market interfaces, order status codes and the request-failure contract
