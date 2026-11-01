# Admin Setup Use Cases

This guide covers two common ways to set up the stock market on a server, with concrete configuration steps and the trade-offs of each approach. Both use cases can coexist on the same server — for example, a bot-driven market for common items and a fully player-driven market for high-value goods.

## Chapters

* [Use Case 1: Standard Market with Virtual Orderbook (default)](#use-case-1-standard-market-with-virtual-orderbook-default)
* [Use Case 2: Player-Driven Market (virtual orderbook disabled)](#use-case-2-player-driven-market-virtual-orderbook-disabled)
* [Recommendation](#recommendation)

---

## Use Case 1: Standard Market with Virtual Orderbook (default)

### What it is

The out-of-the-box setup. The **virtual orderbook** is enabled and populated by the built-in plugins — `DefaultOrderbookVolumeDistributionPlugin`, `TargetPriceBot`, and `VolatilityPlugin`. Players always find liquidity at reasonable prices, and prices move naturally with player activity plus simulated market-maker behavior.

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
* **Orderbook plugins skip disabled markets.** `DefaultOrderbookVolumeDistributionPlugin`, `TargetPriceBot`, and `VolatilityPlugin` automatically ignore markets that have the virtual orderbook disabled, even if the plugin is still subscribed.
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
