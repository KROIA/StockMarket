# NetFlowPlugin

The NetFlowPlugin decides **where a market's price settles** when players keep selling or buying an item. Every server tick it computes each subscribed market's *equilibrium price* from the net amount of items players traded, and hands it to the [VolatilityPlugin](volatility-plugin.md), which adds its random walk on top. It does not trade by itself -- the [TargetPriceBot](target-price-bot.md) places the orders that move the real price toward the target.

```
equilibrium = anchor x e^(-net flow / capacity)        (limited by Min/Max Price Multiplier)
anchor      = default price                             (long-term anchor off)
              default price x slowly learned factor     (long-term anchor on)
```

**When to use it:** on every market whose price should react to player supply and demand. Without it the VolatilityPlugin wanders around the static default price.

**Interaction with other plugins:**

- It runs **first** in the default plugin order. It publishes the equilibrium and also writes it as the target price; the [VolatilityPlugin](volatility-plugin.md) (next in the list) replaces that target with the equilibrium plus its random walk. Without a VolatilityPlugin the target is the equilibrium itself.
- The [TargetPriceBot](target-price-bot.md) steers the real price toward the target. Subscribe it to the same markets.
- The [NewsPlugin](news-plugin.md) multiplies the target during news events. A permanent news event changes the market's *default price*, which moves the equilibrium, its limits and the anchor with it.
- The capacity uses the default order book depth of the [volume distribution plugin](orderbook-volume-distribution.md) (see *Permanent Impact and Book Depth Cap*). In return, that plugin centres the default order book on the equilibrium price, not on the last traded price, so a dumped or pumped price does not drag the book along.
- The plugin only works on markets whose virtual order book is enabled. Other markets are skipped.

> **Upgrading from an older version:** the flow settings used to be part of the VolatilityPlugin. When an existing world is loaded for the first time, a NetFlowPlugin is created in front of each VolatilityPlugin, with the same subscriptions, auto-subscribe and enabled state, and every market's Player Flow Influence, Flow Sensitivity, Min/Max Price Multiplier and Permanent Impact are copied over (the old Volatility Scale becomes the Capacity Volatility Scale). Prices continue exactly where they were. Going back to an older mod version afterwards resets the VolatilityPlugin flow settings to their defaults and logs an *unknown plugin* warning for the NetFlowPlugin.

## Settings Overview

All settings are **per market**. Select a market icon in the plugin entry of the [Plugin Management Screen](../management.md), edit the fields, then click **Apply** (this market only) or **Apply to all** (every subscribed market, after a confirmation dialog). Hover over a field to see its tooltip.

| Setting (UI name) | Default | Allowed values | What it controls |
|---|---|---|---|
| Player Flow Influence | on | on / off | Whether player trading moves the equilibrium at all |
| Flow Sensitivity | `2000` | number; <= 0 turns flow influence off | How many items it takes to move the equilibrium |
| Min Price Multiplier | `0.05` | strictly between 0 and 1 | Lowest equilibrium, as a fraction of the anchor |
| Max Price Multiplier | `3` | greater than 1 | Highest equilibrium, as a multiple of the anchor |
| Permanent Impact | `0.5` | greater than 0, at most 1 | Largest lasting move, relative to the move through the default order book |
| Capacity Volatility Scale | `0.3` | number >= 0 | Makes the capacity smaller for volatile markets |
| Book Depth Cap | on | on / off | Whether the default order book limits the capacity |
| Flow Saturation | on | on / off | Whether flow beyond the price limits is ignored |
| Long-term Anchor | off | on / off | Whether lasting trends move the anchor |
| Anchor Time (days) | `2` | greater than 0 | How slowly the anchor follows the flow |
| Anchor Max Move / Day | `0.1` | number >= 0 | Largest anchor move per day |
| Anchor Min Multiplier | `0.25` | strictly between 0 and 1 | Lowest anchor, as a fraction of the default price |
| Anchor Max Multiplier | `4` | greater than 1 | Highest anchor, as a multiple of the default price |

Below the settings a read-only line shows the selected market's **current anchor** as a multiple of the default price. Invalid values are replaced by the default.

## How the Equilibrium Price Is Calculated

Every market counts its **net player flow**: items players sold into the market minus items they bought from it. Only real player fills count, not bots or the virtual order book. The counter fades over time (the market's *net flow half-life*), so old trades slowly lose their effect.

```
capacity    = max(Flow Sensitivity x natural abundance / (1 + Capacity Volatility Scale),
                  default book depth / Permanent Impact)
equilibrium = anchor x e^(-net flow / capacity)
              ...limited to [Min Price Multiplier x anchor, Max Price Multiplier x anchor]
```

- **Natural abundance** is the market's *Abundance* value (market settings or preset). Common items need far more trading to move.
- **Capacity** is the number of items players must net-sell to drop the equilibrium to about **37 %** (a factor of e = 2.72). Net-buying the same amount raises it **2.72x**. Small flows have proportional effects: net-selling 10 % of the capacity lowers the equilibrium by about 10 %.
- Example: a diamond (default price 160, abundance 50) has a sensitivity capacity of 2000 x 50 / 1.3 = **76,923** items. Net-selling 7,700 diamonds lowers the equilibrium to about 160 x e^(-0.1) = **145**.

### Player Flow Influence

Turns the whole flow model on or off. When off, the equilibrium is always exactly the anchor, and player trading only moves the price temporarily (the TargetPriceBot pulls it back). Turn it off for markets that should always return to a fixed price.

### Flow Sensitivity

How "heavy" the market is. Doubling it doubles the items needed for the same move. For a -10 % move you need about `0.105 x capacity` items, for -50 % about `0.69 x capacity`. A value of `0` or below disables the flow influence. Small servers usually want a lower value, large servers a higher one.

### Min Price Multiplier and Max Price Multiplier

The floor and ceiling of the **equilibrium**, relative to the anchor. With the defaults and the anchor at the default price, a diamond's equilibrium stays between 8 and 480. They bound the equilibrium, not the final price: the random walk and news events still act on top.

### Permanent Impact and Book Depth Cap

A player who sells a large stack in one go walks the price down through the virtual order book. The Permanent Impact limit makes sure the equilibrium never moves more than this fraction of that one-shot move, so dumping and buying back cannot shift the price further than the trade itself did. The plugin measures the densest depth of the default order book and raises the capacity to `book depth / Permanent Impact` when that is larger. Lower values make the market harder to move with big trades. Unchecking **Book Depth Cap** uses the sensitivity capacity only. Markets without a volume distribution plugin have no default book; the limit is then inactive and a warning is logged once.

### Capacity Volatility Scale

The capacity is divided by `1 + Capacity Volatility Scale`, so a higher value lets the market react a bit faster. Migrated worlds get their old Volatility Scale here; it is independent of the VolatilityPlugin's scale.

### Flow Saturation

When the equilibrium is at its floor or ceiling, extra flow is not stored. If players net-sell far beyond the floor, the moment they buy back the equilibrium starts rising again -- there is no dead zone to trade back first. Unchecking **Flow Saturation** uses the market's flow counter directly (the floor and ceiling still apply). To reset a market to its default price, press **Reset** next to **Net Flow** in the market's settings on the Management Screen; this also resets the anchor.

## Long-term Anchor

Without the anchor the net flow fades away and the price always returns to the default price, even after weeks of one-sided trading. The anchor keeps a small part of a **lasting** trend: it slowly moves the price the flow is measured from.

The anchor is **off by default**. Check **Long-term Anchor** for a market and click **Apply** (or **Apply to all**) to use it.

```
every tick:  anchor move = flow exponent x elapsed time / Anchor Time    (at most Anchor Max Move / Day per day)
             anchor      = limited to [Anchor Min Multiplier, Anchor Max Multiplier] x default price
```

- The *flow exponent* is the current `-net flow / capacity` (within the price limits). While players sell, the anchor slowly drops; while they buy, it rises.
- Without flow the anchor stays where it is -- it does not return to the default price by itself. An idle market keeps its price.
- With the default 2 days a short trade leaves only about 18 % of its effect in the anchor once the flow has faded. Quick dump-and-buy-back trades therefore cannot profit from it.
- **Reset** next to **Net Flow** on the Management Screen sets the anchor back to the default price.

**Tuning advice:**

- Higher **Anchor Time** = slower, smaller lasting part. Keep it at 1 day or above: shorter times let a fast dump-and-buy-back trade profit.
- **Anchor Max Move / Day** caps very large flows: with `0.1` the anchor moves at most about 10 % per day, 0.7 in log units (about 2x) per saturated week.
- **Anchor Min/Max Multiplier** limit how far a long trend can carry the price away from the default price. The equilibrium can reach `Anchor x Min/Max Price Multiplier`, so with all defaults a diamond ranges from 160 x 0.25 x 0.05 = 2 to 160 x 4 x 3 = 1,920.
- With **Long-term Anchor** off (the default) every market always returns to its default price, as in older versions.

## Generic Settings

| Setting | Effect for this plugin |
|---|---|
| Enabled | When disabled, no equilibrium is published; the VolatilityPlugin then wanders around the default price. Settings and subscriptions are kept. |
| Auto-subscribe | Newly created markets are subscribed automatically (markets created with *Ignore Plugin Autosubscribe* are skipped). |
| Logger | Writes the plugin's log messages to the server log. |
| Market subscriptions | Only subscribed markets get a flow-driven equilibrium. |
| Position in the list | Run this plugin **before** the VolatilityPlugin and the TargetPriceBot (default order). See [How Plugins Work Together](../overview.md#how-plugins-work-together). |
