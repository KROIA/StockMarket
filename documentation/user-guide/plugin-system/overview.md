# Plugin System Overview

## What Are Plugins?

Plugins are configurable modules that automate market behavior on the server. Each plugin performs a specific job -- setting prices, placing orders, or managing orderbook depth. Without plugins, markets would be static: no price movement, no bot activity, and no orderbook depth for players to trade against.

Plugins run entirely on the server. Players do not interact with plugins directly; they experience the effects through changing prices and available orders.

## Default Plugins

The StockMarket mod ships with five built-in plugins. Together, they create a functioning market with realistic price behavior. Each has its own page with every setting, the math behind it, diagrams and tuning advice.

| Plugin | What it does | Details |
|--------|--------------|---------|
| **NetFlowPlugin** | Computes each market's equilibrium price from net player buying and selling (bounded by Min/Max Price Multipliers), with an optional long-term anchor that keeps part of lasting trends. | [NetFlowPlugin](plugins/net-flow-plugin.md) |
| **VolatilityPlugin** | Sets each market's target price: a random walk around the NetFlowPlugin's equilibrium (or the default price without it). | [VolatilityPlugin](plugins/volatility-plugin.md) |
| **TargetPriceBot** | Places market orders that push the real price toward the target price, steered by a PID controller. | [TargetPriceBot](plugins/target-price-bot.md) |
| **DefaultOrderbookVolumeDistributionPlugin** | Fills the order book with virtual buy and sell volume around the current price and refills it over time. | [Order Book Volume Distribution](plugins/orderbook-volume-distribution.md) |
| **NewsPlugin** | Publishes JSON-defined news events and multiplies the target price of the affected markets while they run. | [NewsPlugin](plugins/news-plugin.md), [News Event System](../news-system/overview.md) |

Every plugin also has the same **generic settings** in its entry on the [Plugin Management Screen](management.md): *Enabled*, *Auto-subscribe* (subscribe newly created markets automatically), *Logger* (write the plugin's messages to the server log), its market subscriptions and its position in the list.

## How Plugins Work Together

Plugins execute in order from top to bottom in the plugin list. The default order is:

1. **NetFlowPlugin** -- Computes the equilibrium price from net player flow and publishes it.
2. **VolatilityPlugin** -- Sets a target price by applying random fluctuations around that equilibrium.
3. **DefaultOrderbookVolumeDistributionPlugin** -- Fills the orderbook with virtual volume around the current market price.
4. **TargetPriceBot** -- Reads the target price and places buy or sell orders to push the market toward it.

This order matters because each plugin can depend on the output of the plugins that ran before it. The VolatilityPlugin reads the equilibrium the NetFlowPlugin just published. The TargetPriceBot reads the target price that the VolatilityPlugin just set. If you reversed their order, the TargetPriceBot would act on the previous tick's target price before the VolatilityPlugin updates it.

The **NewsPlugin** is the exception: it multiplies the target price in a separate finalize stage that runs after all plugins have updated, so its position in the list does not matter.

### General Rule

Plugins that **set** target prices should run before plugins that **read** target prices. Plugins that shape the orderbook should generally run last, after the price-driving plugins have finished.

### Changing the Order

You can reorder plugins in the Plugin Management Screen using the up/down buttons. If you move a plugin, its position in the execution sequence changes immediately. See the [Managing Plugins](management.md) guide for instructions.
