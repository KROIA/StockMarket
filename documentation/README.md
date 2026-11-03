# StockMarket Documentation

Index of all documentation pages. For an introduction to the mod, its blocks, commands and features, start with the [main README](../README.md).

## User Guide
For players, server admins and content creators.

- [Admin Setup Use Cases](user-guide/admin-setup.md) — standard bot-driven market vs. player-driven market
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

## Developer Guide
For mod developers extending StockMarket.

### Plugin System
- [Overview](developer-guide/plugin-system/overview.md) — plugin architecture
- [Getting Started](developer-guide/plugin-system/getting-started.md) — write your first plugin
- [API Reference](developer-guide/plugin-system/api-reference.md) — plugin API
- [Examples](developer-guide/plugin-system/examples.md) — example plugins

### API
- [Market Creation API](developer-guide/api/market-creation-api.md) — create, query and close markets from other mods
