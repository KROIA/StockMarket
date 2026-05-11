# Managing Plugins

## Prerequisites

- Operator permission level 2 or higher (the same level required for `/stockmarket manage`)
- Access to the in-game management screen

## Opening the Plugin Management Screen

1. Run `/stockmarket manage` to open the Management Screen.
2. On the right side, find the **Plugins** widget. It shows the plugin count in the format "X / Y enabled" (where X is the number of active plugins and Y is the total).
3. Click **Manage Plugins** to open the Plugin Management Screen.

## Plugin List Overview

The Plugin Management Screen has two main areas:

**Left side — Chart Display:**
- A **candlestick chart** showing price history for the selected market.
- An **orderbook volume histogram** next to the chart, showing buy/sell volume distribution.
- Plugin overlays appear on these charts automatically (e.g., the TargetPriceBot's target price line, the VolumeDistribution plugin's target curve).

**Right side — Plugin List:**
A scrollable list of all plugin instances. Each entry shows:

- **Name** -- The display name of the plugin. Hover over it to see the internal registry ID (useful for troubleshooting).
- **Description** -- A short summary of what the plugin does.
- **Enabled checkbox** -- Turns the plugin on or off.
- **Up/Down buttons** -- Small arrow buttons to change the plugin's position in the execution order.
- **Market icons** -- Item icons showing which markets this plugin is subscribed to. Click any icon to select that market for the chart display — all icons for the same market across all plugins highlight with a green overlay. Each icon has a small close button in the top-right corner for unsubscribing (press and release on the button to confirm). A "+" button at the end lets you subscribe to additional markets.
- **Settings area** -- Either inline settings fields or an "Open Plugin" button, depending on the plugin type.

## Enabling and Disabling Plugins

To enable or disable a plugin:

1. Find the plugin in the list.
2. Check or uncheck the **Enabled** checkbox.
3. The change takes effect immediately on the server. No restart is needed.

The "X / Y enabled" counter on the Management Screen updates to reflect the new state.

## Reordering Plugins

Execution order determines the sequence in which plugins run each update cycle. To change the order:

1. Click the **up arrow** button to move a plugin up (it will execute earlier).
2. Click the **down arrow** button to move a plugin down (it will execute later).

The list updates immediately to show the new order. See the [Plugin System Overview](overview.md) for why execution order matters.

## Subscribing Markets to Plugins

Each plugin can be subscribed to one or more markets. A plugin only affects the markets it is subscribed to.

To subscribe a market:

1. Click the **"+"** button in the market icons row of the plugin entry.
2. A popup opens showing all available markets as item icons. Markets already subscribed to this plugin are excluded from the list.
3. Click an item to subscribe that market to the plugin.
4. The plugin list refreshes and the new market icon appears in the entry.

## Unsubscribing Markets from Plugins

To remove a market from a plugin:

1. Find the market's item icon in the plugin entry.
2. Click and hold the small **close button** in the top-right corner of the icon. The button turns bright red when active.
3. Release the mouse while still over the close button to confirm removal. If you change your mind, move the mouse away before releasing — the removal is cancelled.
4. The plugin stops affecting that market immediately.

## Filtering Plugins by Market

When you have many plugins and markets, filtering helps you focus on a specific market:

1. Click the filter button at the top of the screen. It shows "All Markets" by default.
2. A popup opens with item icons for all markets that have at least one plugin subscribed.
3. Click a market item to filter the list -- only plugins subscribed to that market will be shown.
4. Click **Show All** to reset the filter and display all plugins again.

The filter button text and icon update to show which market is currently selected.

## Editing Plugin Settings

Plugins have two types of settings interfaces: inline settings and custom screen settings.

### Inline Settings

Used by **VolatilityPlugin** and **DefaultOrderbookVolumeDistributionPlugin**.

1. The settings fields appear directly inside the plugin entry in the list.
2. Change the values in the text fields.
3. Click **Apply** to send the new settings to the server.

**VolatilityPlugin fields:**
- Volatility Scale

**DefaultOrderbookVolumeDistributionPlugin fields:**
- Volume Scale
- Speed

### Custom Screen Settings

Used by **TargetPriceBot**.

1. Click the **Open Plugin** button in the plugin entry.
2. A dedicated settings screen opens with:
   - **Top left:** A candlestick chart with an adjacent orderbook volume histogram showing price history and volume distribution. An orange horizontal line shows the live target price that the bot is steering toward.
   - **Bottom left:** A market selector. Click an item to switch the chart to that market's data.
   - **Right side:** PID gain settings (P Gain, I Gain, D Gain, Rate) as text input fields.
3. Edit the PID values and click **Apply** to send changes to the server.
4. Press **Escape** to return to the plugin list.

The candlestick chart updates in real time while the screen is open, showing both the price history and the current target price.

## Persistence

All plugin configuration is saved automatically when the server saves world data. This includes:

- Which plugins are enabled or disabled
- The execution order
- Which markets each plugin is subscribed to
- All plugin-specific settings (volatility scale, PID gains, volume scale, speed)

Settings survive server restarts. A fresh world starts with all three default plugins registered and subscribed to the first available market.
