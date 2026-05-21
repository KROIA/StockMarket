# Plugin System Overview

The StockMarket plugin system is a framework for creating configurable market behavior plugins. Plugins subscribe to markets and manipulate prices, orders, and orderbook volume through a defined API.

## Architecture

```
ServerPlugin (your code)
    | subscribes to markets
    v
MarketInterface (per subscribed market)
    |-- IPluginMarket  (market.market)  -- price queries, target price, order placement
    '-- IPluginOrderBook (market.oderBook) -- orderbook volume, virtual volume, order reads
    | changes queued in caches
    v
Market Engine -- receives all changes atomically at finalize
```

Plugins interact with markets exclusively through `MarketInterface` objects. Each `MarketInterface` wraps two API surfaces:

- **`IPluginMarket`** -- market-level operations: read prices, set target prices, place orders.
- **`IPluginOrderBook`** -- orderbook-level operations: read orders, query volume, manipulate virtual volume, register volume distribution calculators.

All mutations go through an internal cache layer. The market engine never sees partial changes.

## Plugin Lifecycle

```
init()  -->  update(markets)  -->  finalize(markets)  -->  deInit()
               ^                        |
               '--- repeats each tick --'
```

| Phase | Called When | Purpose |
|---|---|---|
| `init()` | Plugin manager starts | One-time setup |
| `update(List<MarketInterface>)` | Every tick, for enabled plugins | Read market state, queue changes via caches |
| `finalize(List<MarketInterface>)` | Every tick, after all plugins update | Post-processing after caches apply |
| `deInit()` | Plugin manager stops | Cleanup |

Additional event callbacks:

| Event | Trigger |
|---|---|
| `onMarketSubscribed(ItemID)` | Plugin subscribes to a new market |
| `onMarketUnsubscribed(ItemID)` | Plugin unsubscribes from a market |
| `onEnable()` | Plugin is enabled |
| `onDisable()` | Plugin is disabled |

## Batched Cache Pattern

Plugins never modify markets directly. When you call `market.market.setTargetPrice(...)` or `market.oderBook.setRawVolume(...)`, the change is queued in a per-market cache. After **all** enabled plugins have finished their `update()` calls, the framework applies every queued change atomically. This prevents mid-update inconsistencies between plugins.

The sequence each tick:

1. **Update phase** -- all enabled plugins call `update()`. Each plugin reads the current market state and queues changes through the `MarketInterface`.
2. **Apply phase** -- the framework applies all queued changes from all plugins to the market engine.
3. **Finalize phase** -- all enabled plugins call `finalize()`. The market state now reflects all changes. Plugins can do post-processing here.

## Capabilities

| Capability | API |
|---|---|
| Set/adjust target prices | `IPluginMarket.setTargetPrice()`, `addToTargetPrice()` |
| Place market orders | `IPluginMarket.placeOrder(amount)` |
| Place limit orders | `IPluginMarket.placeOrder(amount, price)` |
| Read current/default price | `IPluginMarket.getPrice()`, `getDefaultRealPrice()` |
| Read player supply/demand | `IPluginMarket.getNetPlayerItemFlow()` |
| Manipulate virtual orderbook volume | `IPluginOrderBook.setRawVolume()`, `addRawVolume()`, `resetVirtualVolume()` |
| Register volume distribution calculators | `IPluginOrderBook.registerDefaultVolumeDistributionCalculator()` |
| Stream type-safe runtime data to client GUI | `ServerPlugin.runtimeDataCodec()`, `provideRuntimeData()`, `getRuntimeDataStreamInterval()` |
| Define per-market type-safe custom settings | `ServerPlugin.customSettingsCodec()`, `provideDefaultCustomSettings()`, `onCustomSettingsApplied()` |
| Persist state across server restarts | `ServerPlugin.save(CompoundTag)`, `load(CompoundTag)` |
| Custom inline or full-screen GUI | `PluginGuiElement` subclass, registered via `PluginRegistryObject.setGuiElementFactory()` |
| Draw overlays on candlestick chart | Override `PluginGuiElement.setCandlestickChart()`, register `CandlestickChart.Overlay` |
| Draw overlays on orderbook histogram | Override `PluginGuiElement.setOrderbookVolumeHistogram()`, register `OrderbookVolumeHistogram.Overlay` |

Both `ServerPlugin` and `PluginGuiElement` are generic classes parameterized by `<TSettings, TRuntimeData>`. Custom settings and runtime data are defined as inner record types with `StreamCodec<ByteBuf, T>` fields. The framework handles all byte-level encoding/decoding internally -- plugin developers work exclusively with typed Java objects.

## Registration

Plugins are registered through `PluginRegistry` at class load time:

```java
public static final PluginRegistryObject MY_PLUGIN = PluginRegistry.registerPlugin(
        MyPlugin.class.getName(),   // typeID (unique identifier)
        "MyPlugin",                 // display name
        "What the plugin does.",    // description
        MyPlugin::new               // server-side factory
);
```

GUI element factories are registered separately in `clientSetup()` to avoid loading client-only classes on dedicated servers:

```java
public static void clientSetup() {
    MY_PLUGIN.setGuiElementFactory(MyPluginGuiElement::new);
}
```

## File Organization

All plugin system code lives under `net.kroia.stockmarket.pluginsystem`:

```
pluginsystem/
    plugin/
        ServerPlugin.java          -- base class you extend
        core/
            GenericPluginData.java  -- metadata (name, description, enabled, instanceID)
            PluginSyncData.java    -- network snapshot for client sync
            cache/                 -- internal cache layer (not part of plugin API)
    interaction/
        MarketInterface.java       -- wrapper holding IPluginMarket + IPluginOrderBook
        PluginMarket.java          -- IPluginMarket implementation
        PluginOrderBook.java       -- IPluginOrderBook implementation
    registry/
        PluginRegistry.java        -- static registry for plugin types
        PluginRegistryObject.java  -- registration data for a plugin type
    screen/
        PluginGuiElement.java      -- base GUI element for custom UI
    pluginmanager/
        ServerPluginManager.java   -- manages server-side plugin instances
        ClientPluginManager.java   -- manages client-side plugin state
    plugins/                       -- built-in plugin implementations
    Plugins.java                   -- built-in plugin registration
```

## Next Steps

- [Getting Started](getting-started.md) -- step-by-step guide to creating your first plugin
- [API Reference](api-reference.md) -- complete method reference for all plugin API classes
- [Examples](examples.md) -- three complete example plugins
