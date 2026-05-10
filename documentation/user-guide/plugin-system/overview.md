# Plugin System Overview

## What Are Plugins?

Plugins are configurable modules that automate market behavior on the server. Each plugin performs a specific job -- setting prices, placing orders, or managing orderbook depth. Without plugins, markets would be static: no price movement, no bot activity, and no orderbook depth for players to trade against.

Plugins run entirely on the server. Players do not interact with plugins directly; they experience the effects through changing prices and available orders.

## Default Plugins

The StockMarket mod ships with three built-in plugins. Together, they create a functioning market with realistic price behavior.

### 1. VolatilityPlugin

Adds natural price movement to markets. Without this plugin, prices remain flat at their default value.

The plugin uses a random walk algorithm to generate price fluctuations around a market's base price. The result is organic-looking price movement that rises and falls over time, similar to real markets.

**Settings:**

| Setting | Effect |
|---------|--------|
| Volatility Scale | Controls how large the price swings are. Higher values produce more dramatic price movement. A value of `1.0` is the default. Setting it to `0.5` halves the swings; setting it to `2.0` doubles them. |

### 2. TargetPriceBot

Automatically places orders to push the market price toward a target price. When the current price is below the target, the bot places buy orders to push the price up. When the price is above the target, it places sell orders to push the price down.

The bot uses a PID controller -- a feedback mechanism that adjusts its behavior based on how far the price is from the target and how fast it is changing. This prevents the bot from overshooting or oscillating wildly.

**Settings:**

| Setting | Effect |
|---------|--------|
| P Gain | Reaction strength. Higher values make the bot respond more aggressively to price differences. Default: `0.5`. |
| I Gain | Accumulated error correction. Addresses persistent price drift that the P gain alone cannot fix. Default: `0.1`. |
| D Gain | Dampening. Reduces overshoot by reacting to how quickly the error is changing. Default: `0.0`. |
| Rate | Update speed factor for the PID controller. Default: `0.1`. |

### 3. DefaultOrderbookVolumeDistributionPlugin

Fills the orderbook with virtual buy and sell orders at different price levels. This creates realistic market depth so that players see meaningful bid/ask spreads when they open the trading screen.

Without this plugin, the orderbook would be empty except for player orders and bot orders, making the market feel thin and unrealistic.

**Settings:**

| Setting | Effect |
|---------|--------|
| Volume Scale | Controls how much depth appears in the orderbook. Higher values produce more volume at each price level. Default: `1.0`. |
| Speed | How quickly the orderbook depth rebuilds after trades consume existing volume. Higher values mean faster recovery. Default: `0.05`. |

## How Plugins Work Together

Plugins execute in order from top to bottom in the plugin list. The default order is:

1. **VolatilityPlugin** -- Sets a target price by applying random fluctuations to each market's base price.
2. **TargetPriceBot** -- Reads the target price and places buy or sell orders to push the market toward it.
3. **DefaultOrderbookVolumeDistributionPlugin** -- Fills the orderbook with virtual volume around the current market price.

This order matters because each plugin can depend on the output of the plugins that ran before it. The TargetPriceBot reads the target price that the VolatilityPlugin just set. If you reversed their order, the TargetPriceBot would act on the previous tick's target price before the VolatilityPlugin updates it.

### General Rule

Plugins that **set** target prices should run before plugins that **read** target prices. Plugins that shape the orderbook should generally run last, after the price-driving plugins have finished.

### Changing the Order

You can reorder plugins in the Plugin Management Screen using the up/down buttons. If you move a plugin, its position in the execution sequence changes immediately. See the [Managing Plugins](management.md) guide for instructions.
