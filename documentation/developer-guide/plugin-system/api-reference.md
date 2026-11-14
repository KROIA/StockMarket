# API Reference

Complete reference for all plugin system classes, organized by class and method group.

---

## ServerPlugin\<TSettings, TRuntimeData\>

`net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin`

Base class for all server-side plugins. Extend this and implement the abstract methods.

- `TSettings` -- the custom settings type (use `Void` if no custom settings)
- `TRuntimeData` -- the runtime data type (use `Void` if no runtime data streaming)

### Properties

```java
void setName(String name)
```
Sets the plugin instance display name.

```java
String getName()
```
Returns the plugin instance display name.

```java
void setDescription(String description)
```
Sets the plugin instance description.

```java
String getDescription()
```
Returns the plugin instance description.

```java
boolean isEnabled()
```
Returns whether the plugin is currently enabled.

```java
void setEnabled(boolean enabled)
```
Enables or disables the plugin. Calls `onEnable()` or `onDisable()` on state change.

```java
UUID getInstanceID()
```
Returns the unique instance ID for this plugin instance.

```java
void setInstanceID(UUID id)
```
Restores the instance ID from saved data. Used when loading a plugin instance from persistence.

```java
String getPluginTypeID()
```
Returns the type ID string from the plugin's registry entry, or null if not registered.

```java
void setLoggerEnabled(boolean enabled)
```
Enables or disables log output for this plugin.

```java
boolean isLoggerEnabled()
```
Returns whether logging is enabled.

```java
GenericPluginData getGenericPluginData()
```
Returns the underlying metadata object containing name, description, enabled state, and instance ID.

```java
boolean getAutoSubscribeNewMarkets()
```
Returns whether this plugin automatically subscribes to newly created markets.

```java
void setAutoSubscribeNewMarkets(boolean autoSubscribe)
```
Sets whether this plugin automatically subscribes to newly created markets. Default: `true`.

```java
void setNetworkStreamPacketTickInterval(int tickInterval)
```
Sets the tick-based interval for runtime data stream packets.

```java
int getNetworkStreamPacketTickInterval()
```
Returns the tick-based interval for runtime data stream packets.

### Lifecycle (abstract -- must implement)

```java
abstract void init()
```
Called once when the plugin manager starts. Perform one-time setup.

```java
abstract void deInit()
```
Called once when the plugin manager stops. Perform cleanup.

```java
abstract void update(List<MarketInterface> markets)
```
Called every tick for enabled plugins. Read market state and queue changes through the `MarketInterface` objects. Since 2.2.0 `markets` holds only the subscribed markets that are inside their trading hours; markets under maintenance are included (bots keep trading there). `getMarketInterfaces()` still returns all subscribed markets. See [`isMarketOpen()`](#market-state).

```java
abstract void finalize(List<MarketInterface> markets)
```
Called every tick after all enabled plugins ran `update`, with the same market list. The queued changes are applied to the markets only after all `finalize` calls, so reads such as `getTargetPrice()` return this tick's queued values. Use it for modifiers that must see every plugin's `update` result, for example the NewsPlugin, which multiplies the target price here. Do not call `setTargetPrice` in `finalize`: it would overwrite such modifiers.

### Events (abstract -- must implement)

```java
abstract void onMarketSubscribed(ItemID marketID)
```
Called when this plugin subscribes to a market. Initialize per-market state here.

```java
abstract void onMarketUnsubscribed(ItemID marketID)
```
Called when this plugin unsubscribes from a market. Clean up per-market state here.

```java
abstract void onEnable()
```
Called when the plugin transitions from disabled to enabled.

```java
abstract void onDisable()
```
Called when the plugin transitions from enabled to disabled.

### Market Management

```java
void subscribeToMarket(ItemID itemID)
```
Subscribes this plugin to the specified market. Must not be called from inside the update loop.

```java
void unsubscribeFromMarket(ItemID itemID)
```
Unsubscribes this plugin from the specified market. Must not be called from inside the update loop.

```java
List<ItemID> getSubscribedMarkets()
```
Returns the list of market IDs this plugin is currently subscribed to.

```java
List<MarketInterface> getMarketInterfaces()
```
Returns the list of `MarketInterface` objects for all subscribed markets.

```java
@Nullable MarketInterface getMarketInterface(ItemID marketID)
```
Returns the `MarketInterface` for the given market, or null if not subscribed.

### Runtime Data Streaming

```java
protected @Nullable StreamCodec<ByteBuf, TRuntimeData> runtimeDataCodec()
```
Override to return the codec for this plugin's runtime data type. Return null if this plugin does not stream runtime data.

```java
protected @Nullable TRuntimeData provideRuntimeData()
```
Override to provide the current runtime data snapshot for streaming to the client. Only called if `runtimeDataCodec()` returns non-null. Return null if no data is available at this moment. The framework only sends the packet when the payload changes.

```java
long getRuntimeDataStreamInterval()
```
Override to control the update interval in milliseconds. Default: 500.

```java
final byte[] encodeRuntimeData()
```
Framework method: encodes runtime data to bytes using the plugin's codec. Called by the streaming infrastructure -- plugin developers should not call this directly.

### Custom Settings

Custom settings are stored **per market** using an internal `Map<ItemID, TSettings>`. The framework manages this map automatically. When a market is first subscribed, the framework calls `provideDefaultCustomSettings()` to get initial settings, stores them in the per-market map, and then calls `onCustomSettingsApplied(marketID, settings)`.

```java
protected @Nullable StreamCodec<ByteBuf, TSettings> customSettingsCodec()
```
Override to return the codec for this plugin's custom settings type. Return null if this plugin has no custom settings.

```java
protected @Nullable TSettings provideDefaultCustomSettings()
```
Override to provide default settings for newly subscribed markets. Called when a market is first subscribed. Return null if this plugin has no custom settings.

```java
protected void onCustomSettingsApplied(ItemID marketID, @NotNull TSettings settings)
```
Override to react when settings are applied to a specific market. Called after decoding. Use this to update internal plugin state from the received settings.

```java
final @Nullable TSettings getCustomSettings(ItemID marketID)
```
Returns the current settings for the given market, or null if no settings exist for that market.

```java
public final void replaceCustomSettings(ItemID marketID, @NotNull TSettings settings)
```
Replaces the settings of one market from inside the plugin, for example to persist a value the plugin derived at runtime. Stores them in the per-market map (what gets saved and synced to the GUI) and calls `onCustomSettingsApplied(marketID, settings)`. *(2.2.0)*

```java
final byte[] encodeCustomSettings(ItemID marketID)
```
Framework method: encodes settings for a single market to bytes using the plugin's codec. Called by the networking infrastructure -- plugin developers should not call this directly.

```java
final Map<ItemID, byte[]> encodeAllCustomSettings()
```
Framework method: encodes settings for all subscribed markets. Called by the networking infrastructure -- plugin developers should not call this directly.

```java
final boolean decodeAndApplyCustomSettings(ItemID marketID, byte[] data)
```
Framework method: decodes and applies per-market settings from bytes. Called by the networking infrastructure -- plugin developers should not call this directly.

```java
final boolean decodeAndApplyCustomSettingsLegacy(byte[] data)
```
Framework method: backward-compatible decode that applies the same settings to all subscribed markets. Used for migration from the old global settings model.

### Data Persistence

```java
boolean save(CompoundTag tag)
```
Override to save plugin state to NBT. Return true on success.

```java
boolean load(CompoundTag tag)
```
Override to load plugin state from NBT. Return true on success.

### Logging

```java
protected void info(String msg)
```
Logs an info message (only if logger is enabled).

```java
protected void error(String msg)
```
Logs an error message (only if logger is enabled).

```java
protected void error(String msg, Throwable e)
```
Logs an error message with exception (only if logger is enabled).

```java
protected void warn(String msg)
```
Logs a warning message (only if logger is enabled).

```java
protected void debug(String msg)
```
Logs a debug message (only if logger is enabled).

---

## MarketInterface

`net.kroia.stockmarket.pluginsystem.interaction.MarketInterface`

Wrapper that provides access to market and orderbook APIs for a single subscribed market.

```java
public final IPluginMarket market
```
Market-level operations: price queries, target price, order placement.

```java
public final IPluginOrderBook oderBook
```
Orderbook-level operations: order access, volume queries, virtual volume management.

> Note: The field name is `oderBook` (not `orderBook`) -- this is intentional in the codebase.

---

## IPluginMarket

`net.kroia.stockmarket.api.plugin.interaction.IPluginMarket`

Accessed via `market.market` on a `MarketInterface`. All prices use real (scaled) values unless stated otherwise.

### Market Information

```java
@NotNull ItemID getMarketID()
```
Returns the item ID for this market.

```java
double getDefaultRealPrice()
```
Returns the default (initial) price of the market.

```java
void setDefaultRealPrice(double defaultRealPrice)
```
Sets the market's default price (real units, must be > 0). **Not** buffered: applied to the market immediately and saved with its settings. Meant for rare, permanent level shifts (the NewsPlugin uses it to bake in a news impact that does not revert). For continuous steering use `setTargetPrice` / `addToTargetPrice`.

```java
double getPrice()
```
Returns the current market price.

```java
double getMaxRealOrderVolume()
```
Returns the market's max order size: the most items a single player order may buy or sell. `0` = unlimited. Bot orders are not limited by it. *(2.2.0)*

### Market Metadata

```java
float getNaturalAbundance()
```
Returns the natural abundance factor for this market. Used by volume distribution plugins to scale orderbook depth.

### Price Conversion

```java
double convertBackendPriceToRealPrice(long backendPrice)
```
Converts an internal (unscaled) backend price to a real price.

```java
long convertRealPriceToBackendPrice(double realPrice)
```
Converts a real price to the internal backend representation.

### Target Price

```java
double getPreviousTargetPrice()
```
Returns the target price used in the last market update.

```java
double getTargetPrice()
```
Returns the current target price that the market is moving towards. This is the value set by plugins during the current tick. If no plugin has set a target price yet this tick, returns the previous target price.

```java
void setTargetPrice(double targetPrice)
```
Sets the target price the market moves toward. Queued in the cache -- applied after all plugins update.

```java
void addToTargetPrice(double delta)
```
Adds a delta to the target price. Positive values increase, negative values decrease.

### Equilibrium Price *(2.2.0)*

A channel between plugins for the price the market settles at without noise. The NetFlowPlugin publishes it; the VolatilityPlugin adds its random walk on top, and the virtual order book is centred on it. All three are `default` methods, so existing implementations keep compiling.

```java
default void setEquilibriumPrice(double equilibriumPrice)
```
Publishes this tick's equilibrium price (real units). Rolled over once per tick like the target price. Default: no-op.

```java
default double getEquilibriumPrice()
```
Returns the value set this tick, else last tick's, else the default price (for example when no NetFlowPlugin runs on the market). Default: `getDefaultRealPrice()`.

```java
default boolean isTargetPriceSetThisTick()
```
Whether a plugin already wrote the target price this tick (`setTargetPrice` or `addToTargetPrice`). Lets a plugin set a fallback target only when nobody else did. Default: `false`.

### Order Placement

```java
long placeOrder(double amount, double price)
```
Places a limit order. Positive `amount` = buy, negative `amount` = sell. Returns an internal order identifier. Queued in the cache.

```java
void placeOrder(double amount)
```
Places a market order at the best available price. Positive `amount` = buy, negative `amount` = sell. Queued in the cache.

### Market State

```java
boolean isMarketOpen()
```
Returns whether this market is currently open for trading: **not under maintenance AND inside its trading hours** (`!maintenance && tradingOpen`).

- **Under maintenance** (Maintenance mode in the management screen): player orders are rejected, bot/plugin orders are still processed.
- **Outside trading hours** (schedule or `/stockmarket open|close` override, see [Trading Hours](../../user-guide/trading-hours.md)): all orders are rejected and the market is **not passed to `update`/`finalize` at all** (`getMarketInterfaces()` still contains it). Cached bot orders for a closed market are dropped; pending virtual-orderbook changes are kept and the target price is not rolled, so plugins resume where they stopped when the market reopens.

```java
float getCurrentCandleTradedVolume()
```
Returns the accumulated traded volume for the current price candle. Resets when a new candle starts. Useful for tracking market activity and building volume-based indicators.

```java
boolean isVirtualOrderbookEnabled()
```
Whether the market uses the virtual order book. When `false`, plugins that read or write virtual volume should skip the market.

```java
void clearVirtualOrderbook()
```
Sets all virtual volume to zero and keeps it at zero through later price moves ("sticky" clear), until `resetVirtualOrderbook()` is called.

```java
void resetVirtualOrderbook()
```
Refills the virtual order book from the default volume distribution and ends a sticky clear.

### Player Metrics

```java
double getNetPlayerItemFlow()
```
Returns the cumulative net items sold/bought by players for this market. Positive = players net-sold into the market (supply exceeds demand), negative = players net-bought from the market (demand exceeds supply). Useful for supply/demand-driven pricing plugins.

```java
default void setNetPlayerItemFlow(double netPlayerItemFlow)
```
Overwrites the net flow counter (real units, same sign). Applied immediately, not buffered. Default: no-op. *(2.2.0)*

```java
default double getFadedNetPlayerItemFlow()
```
The same counter, but it decays toward 0 with the half-life of the Mod Setting `NET_FLOW_HALF_LIFE_MINUTES` (default 360 min). A burst of trades fades out; steady demand or supply keeps it up. **Price plugins should use this value.** Default: `getNetPlayerItemFlow()` (no fading). *(2.2.0)*

```java
default void setFadedNetPlayerItemFlow(double fadedNetPlayerItemFlow)
```
Overwrites the faded net flow. Applied immediately. Default: no-op. *(2.2.0)*

---

## IPluginOrderBook

`net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook`

Accessed via `market.oderBook` on a `MarketInterface`.

### Order Access

```java
@NotNull List<Order> getBuyOrders()
```
Returns all current buy orders in the orderbook.

```java
@NotNull List<Order> getSellOrders()
```
Returns all current sell orders in the orderbook.

```java
@NotNull List<Order> getBuyOrders(long startPrice, long endPrice)
```
Returns buy orders within the given backend price range (inclusive).

```java
@NotNull List<Order> getSellOrders(long startPrice, long endPrice)
```
Returns sell orders within the given backend price range (inclusive).

```java
@NotNull List<Order> getNewOrders()
```
Returns orders created since the last update.

### Volume Queries

```java
float getRealVolume(double minPrice, double maxPrice)
```
Returns total volume between `minPrice` and `maxPrice` (inclusive, real prices).

```java
float getRealVolume(long backendPrice)
```
Returns real volume at a specific backend price.

```java
float getRealVirtualVolume(long backendPrice)
```
Returns real virtual volume at a specific backend price.

```java
default float getRealVirtualVolume(double minPrice, double maxPrice)
```
Like `getRealVolume(minPrice, maxPrice)`, but only the virtual depth; resting limit orders (for example player orders) are not counted. Positive = buy side, negative = sell side. Default: `0` (no depth information). *(2.1.2)*

```java
default float getRealVirtualVolumeToPrice(double targetRealPrice, double capReal)
```
The virtual volume a market order must consume to move the price from the current price to `targetRealPrice`, read level by level the way the matching engine walks the book (including the remembered depth outside the order book window). Only the side the order eats is counted. Resting limit orders are not counted, so an order of this size ends at or before the target. Use it to size orders that must not overshoot.
- `capReal`: stop counting at this many items (your order limit); `Double.POSITIVE_INFINITY` = no cap.
- Returns a signed real volume: negative = ask volume a buy consumes (target above the price), positive = bid volume a sell consumes (target below), `0` if the target is the current price.
- Default: `0`. *(2.2.0)*

```java
long getRawVolume(long backendPrice)
```
Returns raw (unscaled) volume at a specific backend price.

```java
float getRawVirtualVolume(long backendPrice)
```
Returns raw virtual volume at a specific backend price.

### Capital Queries

```java
float getCapital(long startPrice, long endPrice)
```
Returns the total capital (volume x price) in the given backend price range. Useful for measuring monetary depth in a price region.

### Virtual Volume Management

```java
@NotNull Tuple<@NotNull Double, @NotNull Double> getEditablePriceRange()
```
Returns the real price range where virtual volume can be set.

```java
@NotNull Tuple<@NotNull Long, @NotNull Long> getEditableBackendPriceRange()
```
Returns the backend price range where virtual volume can be set.

```java
void setRawVolume(double minPrice, double maxPrice, float volume)
```
Sets uniform volume distribution across the given real price range. Volume is always positive; the backend assigns the correct sign per side.

```java
void setRawVolume(long backendStartPrice, float[] volume)
```
Sets volume using an array starting at `backendStartPrice`. Each element maps to one price level.

```java
void addRawVolume(double minPrice, double maxPrice, float volume)
```
Adds uniform volume distribution across the given real price range.

```java
void addRawVolume(long backendStartPrice, float[] volume)
```
Adds volume using an array starting at `backendStartPrice`.

```java
void resetVirtualVolume()
```
Resets all virtual volume to zero.

```java
default void setVirtualDepthSideFactors(float bidFactor, float askFactor)
```
Sets how fast the remembered virtual depth outside the editable range recovers, per side. A factor f > 1 refills that side's missing volume f times faster and removes excess f times slower; f < 1 the other way round; 1 = the Mod Setting `VIRTUAL_DEPTH_RECOVERY_HALF_LIFE_MINUTES`. Applied with the other cached changes; the values stay until set again. Default: no-op. *(2.2.0)*

### Volume Distribution

```java
void registerDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator calculator)
```
Registers a calculator that provides virtual volume outside the editable price range.

```java
void unregisterDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator calculator)
```
Removes a previously registered volume distribution calculator.

```java
float getDefaultRealVolume(double pickPrice)
```
Returns the combined real volume from all registered distribution calculators at the given price. Since 2.2.0 the calculators are centred on the market's fair price (`IPluginMarket.getEquilibriumPrice()`) instead of the current price, so the default depth does not follow a dumped or pumped price; the bid/ask split stays at the current price. Positive = buy side (below the current price), negative = sell side.

```java
default float getDefaultRealVolumeAround(double centerPrice, double pickPrice)
```
Like `getDefaultRealVolume`, but centred and split at `centerPrice`, for example to read the default book around the default price whatever the price is now. Default: falls back to `getDefaultRealVolume(pickPrice)`. *(2.2.0)*

```java
float getDefaultRawVolume(double pickPrice)
```
Returns the combined raw volume from all registered distribution calculators at the given price.

```java
float[] getDefaultRawVolume(long backendStartPrice, int count)
```
Batch version: the default raw volume of `count` consecutive backend price levels starting at `backendStartPrice` (index `i` = price `backendStartPrice + i`). Much cheaper than one call per level.

---

## IVolumeDistributionCalculator

`net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator`

Functional interface for defining virtual orderbook volume at prices outside the editable range.

```java
float getVolume(double marketPrice, double volumePickPrice)
```
Returns the virtual volume at `volumePickPrice`, given the current `marketPrice`. The returned value can be positive or negative. Volume below the market price counts as buy volume; above counts as sell volume.

---

## PluginGuiElement\<TSettings, TRuntimeData\>

`net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement`

Extends `StockMarketGuiElement`. Base class for plugin-specific client-side UI. Override methods to provide custom settings editing and live data visualization.

- `TSettings` -- the custom settings type (must match the server plugin's `TSettings`; use `Void` if no settings)
- `TRuntimeData` -- the runtime data type (must match the server plugin's `TRuntimeData`; use `Void` if no runtime data)

### Screen Mode

```java
boolean needsCustomScreen()
```
Override and return `true` to use a dedicated full-screen layout instead of inline embedding. Default: `false`.

### Codecs

```java
protected @Nullable StreamCodec<ByteBuf, TSettings> customSettingsCodec()
```
Override to return the codec for this plugin's custom settings type. Must match the server-side plugin's codec. Return null if this plugin has no custom settings.

```java
protected @Nullable StreamCodec<ByteBuf, TRuntimeData> runtimeDataCodec()
```
Override to return the codec for this plugin's runtime data type. Must match the server-side plugin's codec. Return null if this plugin does not stream runtime data.

### Data Reception

```java
protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, TSettings> customSettingsMap)
```
Override to initialize the element when sync data arrives from the server. The framework automatically decodes the per-market custom settings bytes using `customSettingsCodec()` and passes the typed result as a map from market ID to settings. Access subscribed markets via `data.getSubscribedMarkets()`.

```java
PluginSyncData getPluginSyncData()
```
Returns the stored sync data, or null if not yet received.

```java
UUID getPluginInstanceID()
```
Returns the plugin instance UUID, or null if sync data not yet received.

### Runtime Streaming

```java
void startDataStream()
```
Starts receiving runtime data from the server. Call when the element becomes visible.

```java
void stopDataStream()
```
Stops the runtime data stream. Call when the element is hidden or screen closes.

```java
protected void onRuntimeDataReceived(TRuntimeData data)
```
Override to process incoming runtime data. The framework decodes the raw bytes using `runtimeDataCodec()` and passes the typed result.

### Custom Settings

```java
protected final void sendCustomSettings(ItemID marketID, TSettings settings)
```
Sends typed custom settings for a specific market to the server. The framework encodes the object to bytes using `customSettingsCodec()`. The response arrives via `onCustomSettingsResponse`.

```java
protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable TSettings confirmedSettings)
```
Override to handle the server's response to a settings update. `success` is true if the server applied the settings. `marketID` identifies which market the response is for. `confirmedSettings` contains the server's confirmed decoded settings object.

### Apply to All Markets and Defaults *(2.2.0)*

Helpers for the "Apply to all" and "Defaults" buttons the built-in plugin GUIs show. Use them to give your settings form the same behaviour.

```java
protected final void sendCustomSettingsToAllMarkets(TSettings settings)
```
Sends `settings` to every market currently subscribed to this plugin, one `sendCustomSettings` call per market, so each confirmation arrives separately in `onCustomSettingsResponse`. Does nothing before sync data arrived.

```java
protected final void confirmApplyToAllMarkets(TSettings settings)
```
Opens a confirmation popup that warns that the settings of all subscribed markets will be overwritten. On confirm it calls `sendCustomSettingsToAllMarkets(settings)`; on cancel nothing is sent and the previous screen is shown again. Pass the values read from your form at click time.

```java
protected @Nullable TSettings defaultSettings()
```
Override to return the settings a newly subscribed market gets (usually the same as the server plugin's `provideDefaultCustomSettings()`). Default: `null` = no "Defaults" button.

```java
protected void showSettings(TSettings settings)
```
Override to fill your settings form with `settings` without sending anything. Called by the "Defaults" button; the user then applies them with Apply (one market) or Apply to all. Default: no-op.

```java
protected final @Nullable Button createDefaultsButton()
```
Creates the "Defaults" button (fills the form with `defaultSettings()` through `showSettings`, sends nothing). Returns `null` if `defaultSettings()` returns `null`. Add the button to your layout yourself.

```java
public static void setSettingTooltip(Component tooltip, GuiElement... elements)
```
Attaches a hover tooltip to setting controls (typically a label and its input). The tooltip opens to the left, because plugin settings sit at the right side of their screens.

`PluginGuiElement.ApplyAllTexts` (protected) holds the shared translated texts: `APPLY_ALL`, `APPLY_ALL_TOOLTIP`, `CONFIRM_TITLE`, `DEFAULTS`, `DEFAULTS_TOOLTIP`.

```java
// In your PluginGuiElement subclass
Button applyAll = new Button(ApplyAllTexts.APPLY_ALL.getString(),
        () -> confirmApplyToAllMarkets(readSettingsFromForm()));
setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAll);
addChild(applyAll);

Button defaults = createDefaultsButton();   // null if defaultSettings() returns null
if (defaults != null) addChild(defaults);
```

### Active Market

```java
public final void setActiveMarket(@Nullable ItemID marketID)
```
Sets the active market for settings editing. Triggers `onActiveMarketChanged`. Typically called from `onPluginSyncDataReceived` after receiving the list of subscribed markets.

```java
public @Nullable ItemID getActiveMarket()
```
Returns the currently active market ID, or null if no market is active.

```java
protected void onActiveMarketChanged(@Nullable ItemID marketID)
```
Override to react when the active market changes. Use this to update text fields and UI elements with the new market's settings.

### Rendering

```java
protected void render()
```
Override for custom rendering. Called each frame.

```java
protected void layoutChanged()
```
Override to reposition child elements when the element bounds change.

### Chart Overlay Hooks

```java
public void setCandlestickChart(@Nullable CandlestickChart chart)
```
Called by the screen to provide a reference to the shared candlestick chart. Override to register overlays via `chart.addOverlay()`. Pass `null` to unregister. Default: no-op.

```java
public void setOrderbookVolumeHistogram(@Nullable OrderbookVolumeHistogram histogram)
```
Called by the screen to provide a reference to the shared orderbook volume histogram. Override to register overlays via `histogram.addOverlay()`. Pass `null` to unregister. Default: no-op.

Both hooks are called automatically by the Plugin Management Screen after sync data is set. Plugin developers override these to draw custom visualizations on the shared charts. When the screen rebuilds its plugin list, it calls these with `null` first to remove stale overlays.

### Inherited Utilities (from StockMarketGuiElement)

```java
protected @Nullable ClientMarket getMarket(ItemID marketID)
```
Looks up a client-side market by item ID.

```java
protected IClientPluginManager getPluginManager()
```
Returns the client-side plugin manager.

```java
public static boolean isMarketVisible(@Nullable ItemID marketID)
```
Whether a market may be shown on this client. On a slave server, markets whose item the slave cannot resolve (the item's mod exists only on the master) are hidden; the master shows every market. Filter market lists in your GUI with it. *(2.2.0)*

```java
protected void info(String msg)
protected void error(String msg)
protected void warn(String msg)
protected void debug(String msg)
```
Logging methods.

**Layout constants:** `padding` (4), `spacing` (4), `defaultElementHeight` (20), `hoverToolTipFontSize` (0.8).

---

## PluginSyncData

`net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData`

Network-transferable snapshot of a plugin's state, received by `PluginGuiElement`.

```java
UUID getInstanceID()
```
Plugin instance UUID.

```java
String getName()
```
Plugin display name.

```java
String getDescription()
```
Plugin description.

```java
String getPluginTypeID()
```
Plugin type ID (matches registration).

```java
boolean isEnabled()
```
Whether the plugin is enabled.

```java
boolean isLoggerEnabled()
```
Whether logging is enabled.

```java
List<ItemID> getSubscribedMarkets()
```
List of markets the plugin is subscribed to.

```java
@Nullable Map<ItemID, byte[]> getCustomSettings()
```
Per-market encoded custom settings map from `ServerPlugin.encodeAllCustomSettings()`, or null. Each entry maps a market ID to the encoded settings bytes for that market. Plugin developers typically do not need to call this directly -- the `PluginGuiElement` framework decodes the bytes using `customSettingsCodec()` and passes the typed result to `onPluginSyncDataReceived(data, customSettingsMap)`.

```java
GenericPluginData getGenericData()
```
The underlying generic metadata object.

---

## PluginRegistry

`net.kroia.stockmarket.pluginsystem.registry.PluginRegistry`

Static registry for plugin types.

```java
static PluginRegistryObject registerPlugin(String pluginTypeID,
                                           String pluginName,
                                           String pluginDescription,
                                           Supplier<ServerPlugin> serverPluginFactory)
```
Registers a plugin type. Returns the existing object if `pluginTypeID` is already registered.

```java
static @Nullable PluginRegistryObject findPlugin(String pluginTypeID)
```
Finds a registered plugin by type ID.

```java
static @Nullable PluginGuiElement createGuiElement(String pluginTypeID)
```
Creates a `PluginGuiElement` for the given type. Returns a default element if no custom factory was set.

```java
static @NotNull Map<String, PluginRegistryObject> getRegistryObjects()
```
Returns all registered plugin types.

---

## PluginRegistryObject

`net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject`

Holds registration data for a single plugin type.

```java
String getPluginTypeID()
```
Returns the unique type ID.

```java
String getPluginName()
```
Returns the display name.

```java
String getPluginDescription()
```
Returns the description.

```java
void setGuiElementFactory(@Nullable Supplier<PluginGuiElement> factory)
```
Sets the GUI element factory. Must be called from `clientSetup()` only.

```java
@Nullable Supplier<PluginGuiElement> getGuiElementFactory()
```
Returns the GUI element factory, or null if none was registered.

```java
ServerPlugin instantiateServerPlugin()
```
Creates a new server plugin instance from the factory.

```java
@Nullable PluginGuiElement createGuiElement()
```
Creates a `PluginGuiElement` from the factory, or a default element if no factory was set.

---

## CandlestickChart.Overlay

`net.kroia.stockmarket.screen.widgets.CandlestickChart.Overlay`

Functional interface for rendering on top of the candlestick chart. Overlays are scissor-clipped to the chart canvas area.

```java
@FunctionalInterface
public interface Overlay {
    void render(CandlestickChart chart);
}
```

### Registration

```java
chart.addOverlay(Overlay overlay)
```
Registers an overlay callback.

```java
chart.removeOverlay(Overlay overlay)
```
Removes a previously registered overlay.

### Coordinate Conversion

```java
Rectangle getCanvasBounds()
```
Returns the drawable canvas area in local coordinates.

```java
int toCanvasSpaceX(long time)
```
Converts a time index to an X pixel coordinate.

```java
int toCanvasSpaceY(double price)
```
Converts a price to a Y pixel coordinate.

```java
@Nullable ClientMarket getMarket()
```
Returns the market currently displayed in the chart, or null.

### Drawing

Overlays use the chart's public drawing methods inherited from `GuiElement`:

- `chart.drawRect(x, y, width, height, color)` -- filled rectangle
- `chart.drawLine(x1, y1, x2, y2, thickness, color)` -- line
- `chart.drawText(text, x, y)` -- text label
- `chart.getTextHeight()` -- text metrics

All coordinates are in the chart's local space. The framework applies scissor clipping to the canvas area before calling overlays.

### Example: Target Price Line

```java
private void renderChartOverlay(CandlestickChart chart) {
    ClientMarket market = chart.getMarket();
    if (market == null) return;

    double targetPrice = getTargetForMarket(market);
    Rectangle bounds = chart.getCanvasBounds();
    int lineY = chart.toCanvasSpaceY(targetPrice);

    if (lineY >= bounds.y && lineY <= bounds.y + bounds.height) {
        chart.drawRect(bounds.x, lineY - 1, bounds.width, 2, 0xFFFF6600);
        chart.drawText("Target", bounds.x + 4, lineY - chart.getTextHeight() - 2);
    }
}
```

---

## OrderbookVolumeHistogram.Overlay

`net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram.Overlay`

Functional interface for rendering on top of the orderbook volume histogram. Overlays are scissor-clipped to the histogram canvas area.

```java
@FunctionalInterface
public interface Overlay {
    void render(OrderbookVolumeHistogram histogram);
}
```

### Registration

```java
histogram.addOverlay(Overlay overlay)
```
Registers an overlay callback.

```java
histogram.removeOverlay(Overlay overlay)
```
Removes a previously registered overlay.

### Coordinate Conversion

```java
Rectangle getCanvasBounds()
```
Returns the drawable canvas area in local coordinates.

```java
int toCanvasSpaceX(float absVolume)
```
Converts an absolute volume value to an X pixel coordinate (bars are right-aligned).

```java
int toCanvasSpaceY(double price)
```
Converts a price to a Y pixel coordinate (delegates to the parent chart).

```java
float getMaxAbsVolume()
```
Returns the maximum absolute volume value in the current display (used for normalization).

```java
double getStartPrice()
double getEndPrice()
```
Returns the visible price range of the histogram data.

```java
@Nullable ClientMarket getMarket()
```
Returns the market currently displayed in the histogram, or null.

### Drawing

Same public `GuiElement` methods as `CandlestickChart.Overlay`: `drawRect`, `drawLine`, `drawText`, `getTextHeight`.

### Example: Volume Target Curve

```java
private void renderVolumeOverlay(OrderbookVolumeHistogram histogram) {
    ClientMarket market = histogram.getMarket();
    if (market == null) return;

    float maxAbsVolume = histogram.getMaxAbsVolume();
    if (maxAbsVolume <= 0) return;

    // Compute target values and find max for self-normalization
    float[] targets = computeTargetsForRange(
            histogram.getStartPrice(), histogram.getEndPrice(), 50);
    float maxTarget = findMax(targets);
    if (maxTarget <= 0) return;

    // Draw curve scaled to fill histogram width
    int lastX = -1, lastY = -1;
    for (int i = 0; i < targets.length; i++) {
        float normalized = targets[i] / maxTarget * maxAbsVolume;
        int x = histogram.toCanvasSpaceX(normalized);
        int y = histogram.toCanvasSpaceY(priceAt(i));
        if (lastX >= 0) {
            histogram.drawLine(lastX, lastY, x, y, 1.5f, 0xFFFFAA00);
        }
        lastX = x;
        lastY = y;
    }
}
```

---

## Built-in Plugins

`net.kroia.stockmarket.pluginsystem.Plugins` registers the built-in plugins. Each `PluginRegistryObject` uses the plugin's class name as its type ID. Their settings records and `DEFAULT_*` constants are public, so an addon can read the defaults or build settings, for example for `ServerPlugin.replaceCustomSettings`. What each setting does in game is described in the [user guide](../../user-guide/plugin-system/overview.md); this section covers what an addon plugin needs to know.

| Registry object | Plugin class (`pluginsystem.plugins`) | Settings type |
|---|---|---|
| `NET_FLOW_PLUGIN` *(2.2.0)* | `NetFlowPlugin` | `NetFlowPlugin.Settings` |
| `VOLATILITY_PLUGIN` | `VolatilityPlugin` | `VolatilityPlugin.Settings` |
| `TARGET_PRICE_BOT_PLUGIN` | `TargetPriceBot` | `TargetPriceBot.Settings` |
| `DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN` | `DefaultOrderbookVolumeDistributionPlugin` | `DefaultOrderbookVolumeDistributionPlugin.Settings` |
| `NEWS_PLUGIN` | `NewsPlugin` | `NewsPlugin.Settings` |

**Execution order.** Plugins run in the order of the plugin list (admins move them up and down on the Plugin Management screen). A fresh world creates NetFlowPlugin, VolatilityPlugin, DefaultOrderbookVolumeDistributionPlugin, TargetPriceBot, NewsPlugin in this order. A plugin that writes the target price should check `isTargetPriceSetThisTick()` if it must not overwrite an earlier plugin.

**How the price is formed (2.2.0).** The NetFlowPlugin turns the players' net flow into a fair price and publishes it with `setEquilibriumPrice`. The VolatilityPlugin reads it and sets the target price to `equilibrium x (1 + noise x volatilityScale)`. The TargetPriceBot trades against the virtual order book to move the price towards that target. Without a NetFlowPlugin on the market, `getEquilibriumPrice()` is the default price.

### NetFlowPlugin *(2.2.0)*

Moves the equilibrium price with the players' net item flow: selling lowers it, buying raises it. Each tick, for every market with a virtual order book, it publishes `equilibrium = anchor x e^f` through `setEquilibriumPrice`, where `f = clamp(-flow / capacity, ln minPriceMult, ln maxPriceMult)` and `capacity = flowSensitivity x naturalAbundance / (1 + capacityVolatilityScale)` (with the θ cap: at least the default book's depth / `permanentImpact`). It also sets the target price to the equilibrium, but only if no plugin did this tick. It only reads the market's flow counters, never writes them.

`NetFlowPlugin.Settings` is a record with these fields; the defaults are public constants of `NetFlowPlugin`:

| Field | Default constant | Default | Valid range (else the default) |
|---|---|---|---|
| `flowEnabled` | `DEFAULT_FLOW_ENABLED` | `true` | false = the equilibrium stays at the anchor |
| `flowSensitivity` | `DEFAULT_FLOW_SENSITIVITY` | `2000` | items per unit of abundance for a factor-e move; <= 0 turns the flow off |
| `minPriceMult` | `DEFAULT_MIN_PRICE_MULT` | `0.05` | (0, 1), lowest equilibrium as a fraction of the anchor |
| `maxPriceMult` | `DEFAULT_MAX_PRICE_MULT` | `3.0` | > 1, highest equilibrium as a multiple of the anchor |
| `permanentImpact` (θ) | `DEFAULT_PERMANENT_IMPACT` | `0.5` | (0, 1] |
| `capacityVolatilityScale` | `DEFAULT_CAPACITY_VOLATILITY_SCALE` | `0.3` | |
| `thetaCapEnabled` | `DEFAULT_THETA_CAP_ENABLED` | `true` | |
| `saturationEnabled` | `DEFAULT_SATURATION_ENABLED` | `true` | |
| `anchorEnabled` | `DEFAULT_ANCHOR_ENABLED` | **`true`** | long-term anchor on or off |
| `anchorTimeDays` | `DEFAULT_ANCHOR_TIME_DAYS` | `2` | > 0 |
| `anchorMaxMovePerDay` | `DEFAULT_ANCHOR_MAX_MOVE_PER_DAY` | `0.1` | >= 0, in ln units per day |
| `anchorMinMult` | `DEFAULT_ANCHOR_MIN_MULT` | `0.25` | (0, 1), lowest anchor as a fraction of the default price |
| `anchorMaxMult` | `DEFAULT_ANCHOR_MAX_MULT` | `4` | > 1, highest anchor as a multiple of the default price |

- The **long-term anchor is on by default**. It applies to new markets and to saved settings that have no anchor field; a saved `anchorEnabled` is kept. With the anchor off, the anchor is the default price.
- Build settings with `Settings.createDefault()` and the `with...` copy methods (`withPermanentImpact`, `withCapacityVolatilityScale`, `withToggles`, `withAnchor`, `withAnchorEnabled`). The compact constructor replaces invalid θ and anchor values with the defaults.
- Pure static helpers: `computeEquilibriumPrice(...)`, `flowExponent(...)`, `flowBand(...)`, `capacity(...)`, `stepAnchor(...)`, `bookEFoldDepth(...)`.
- Per-market read-outs of a running instance: `getEquilibrium(ItemID)` (NaN until the first update), `getAnchorMultiplier(ItemID)`, `getSaturatedFlow(ItemID)`, `getMarketSettings(ItemID)`, `getBookEFold(ItemID)`.
- `IAsyncMarket.resetNetPlayerItemFlowAsync()` (the **Reset** button next to Net Flow) also resets the anchor to the default price.

### TargetPriceBot

Trades against the virtual order book to move the market price towards the target price (`getPreviousTargetPrice()`, the value the price plugins set last tick). It acts every 5th tick and skips markets without a virtual order book. A PID controller sets how hard it pushes; the order size is the virtual volume between the price and its target (`IPluginOrderBook.getRealVirtualVolumeToPrice`), so it never trades past its target. One order is at most `maxOrderVolumeFactor` times the market's max player order size.

`TargetPriceBot.Settings`: `pidP`, `pidI`, `pidD`, `pidRate`, `maxOrderVolumeFactor`, `maxTargetMovePerSecond`, `reversionTimeSeconds`, `oscillationPeriodSeconds`, `oscillationDamping`, `oscillationAmplitude`. Defaults: `DEFAULT_PID_P` 0.5, `DEFAULT_PID_I` 0.1, `DEFAULT_PID_D` 0, `DEFAULT_PID_RATE` 0.1, `DEFAULT_MAX_ORDER_VOLUME_FACTOR` 10, `DEFAULT_MAX_TARGET_MOVE_PER_SECOND` 0.005 (0.5 % per second), `DEFAULT_REVERSION_TIME_SECONDS` 600, `DEFAULT_OSCILLATION_PERIOD_SECONDS` 60, `DEFAULT_OSCILLATION_DAMPING` 0.3, `DEFAULT_OSCILLATION_AMPLITUDE` 0.15. `Settings.createDefault()` and `withReference(...)` build them.

**Reference after a player trade (2.2.0).** The bot does not snap the price back after a player trade. A player fill (a change of the market's net flow counter) anchors the bot's **reference**, its setpoint, at the price the player left. The reference then returns to the target over `reversionTimeSeconds`, with a small damped swing. It never goes past the target or beyond the anchor.

- **Band:** the anchor price is clamped to `[E x BOT_MIN_PRICE_MULT, E x NetFlowPlugin.DEFAULT_MAX_PRICE_MULT]`, where E is the market's `getEquilibriumPrice()` (the default price without a NetFlowPlugin). `BOT_MIN_PRICE_MULT` is `0.25`, so a dump below 25 % of E only anchors the reference at 25 % of E: the bot leans the price back into the band instead of following the dump down. The upper edge is 3 times E. The band uses these fixed constants, not the market's own NetFlowPlugin settings. `TargetPriceBot.referenceAnchorPrice(price, equilibrium)` returns the clamped price.
- **Continued trades in the same direction** (for example a player who keeps selling) only move the anchor to the new price. They keep the reference and its return in progress, so the return does not restart and steady selling or buying is still pulled back towards the target. A fill on the other side of the target starts a new return.
- `reversionTimeSeconds <= 0` turns the reference off: the bot then follows the target only through the `maxTargetMovePerSecond` rate limit.
- **Gap guard:** for up to `GAP_GUARD_MAX_BLOCK_SECONDS` (60) after a player fill, the bot does not trade through an almost empty stretch of the book (`crossesEmptyGap`, window `GAP_WINDOW_FRACTION` = 1 % of the price).
- Pure static helpers: `anchorReference(...)`, `stepReference(...)`, `referenceAnchorPrice(...)`, `limitTargetMove(...)`, `botOrderVolumeLimit(...)`, `crossesEmptyGap(...)`.

### VolatilityPlugin

Sets the target price to `equilibrium x (1 + noise x volatilityScale)`, a random walk around `getEquilibriumPrice()`. Since 2.2.0 it no longer contains the flow model (that moved to the NetFlowPlugin); `VolatilityPlugin.Settings` has only `volatilityScale` (default `DEFAULT_VOLATILITY_SCALE` = 0.3).

### DefaultOrderbookVolumeDistributionPlugin

Fills the virtual order book and registers the default volume distribution. `Settings` fields: `volumeScale`, `speed`, `accumulationRate`, `decumulationRate`, `resetVolume`, and since 2.2.0 the trend accumulation fields `trendPeriodMinutes`, `trendGain`, `trendMaxFactor` (defaults `DEFAULT_TREND_PERIOD_MINUTES` 10, `DEFAULT_TREND_GAIN` 10, `DEFAULT_TREND_MAX_FACTOR` 4) and the round-price cluster fields `roundLevelScale`, `roundLevelWidth`, `roundLevelRange` (defaults `DEFAULT_ROUND_LEVEL_SCALE` 1, `DEFAULT_ROUND_LEVEL_WIDTH` 0.03, `DEFAULT_ROUND_LEVEL_RANGE` 1.1). Copy helpers: `withTrend(...)`, `withRoundLevels(...)`.

### NewsPlugin

Publishes news events and multiplies the target price while they run; a permanent event changes the market's default price (`setDefaultRealPrice`). `NewsPlugin.Settings(newsEnabled, sensitivity)`, defaults `DEFAULT_NEWS_ENABLED` (`true`) and `DEFAULT_SENSITIVITY` (1.0). The combined news factor is clamped to `[MIN_COMBINED_FACTOR, MAX_COMBINED_FACTOR]` = [0.1, 10].

---

## Data Encoding Convention

Custom settings and runtime data use typed Java records with `StreamCodec<ByteBuf, T>` for binary encoding. The framework handles all byte-level encoding and decoding internally -- plugin developers work with typed objects.

### Defining a record with a StreamCodec

Each settings or runtime data type is a Java record with a `public static final CODEC` field:

```java
public record Settings(float myFloat, int myInt, boolean myBool, double myDouble) {
    public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
        @Override
        public Settings decode(ByteBuf buf) {
            return new Settings(buf.readFloat(), buf.readInt(), buf.readBoolean(), buf.readDouble());
        }
        @Override
        public void encode(ByteBuf buf, Settings s) {
            buf.writeFloat(s.myFloat());
            buf.writeInt(s.myInt());
            buf.writeBoolean(s.myBool());
            buf.writeDouble(s.myDouble());
        }
    };
}
```

### Using StreamCodec.composite for simple records

For records with only primitive fields, use the `StreamCodec.composite` builder with `ByteBufCodecs` constants:

```java
public record Settings(float pidP, float pidI, float pidD, float pidRate) {
    public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Settings::pidP,
            ByteBufCodecs.FLOAT, Settings::pidI,
            ByteBufCodecs.FLOAT, Settings::pidD,
            ByteBufCodecs.FLOAT, Settings::pidRate,
            Settings::new
    );
}
```

### Collections

For collections, write the count first, then each element using `ByteBuf` read/write methods:

```java
public record MarketData(List<Entry> entries) {
    public record Entry(short itemId, double value) {}

    public static final StreamCodec<ByteBuf, MarketData> CODEC = new StreamCodec<>() {
        @Override
        public MarketData decode(ByteBuf buf) {
            int count = buf.readInt();
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buf.readShort(), buf.readDouble()));
            }
            return new MarketData(entries);
        }
        @Override
        public void encode(ByteBuf buf, MarketData data) {
            buf.writeInt(data.entries().size());
            for (Entry e : data.entries()) {
                buf.writeShort(e.itemId());
                buf.writeDouble(e.value());
            }
        }
    };
}
```

Supported `ByteBuf` methods: `readFloat`/`writeFloat`, `readDouble`/`writeDouble`, `readInt`/`writeInt`, `readShort`/`writeShort`, `readByte`/`writeByte`, `readBoolean`/`writeBoolean`, `readLong`/`writeLong`.

### How it works

Both the server plugin and client GUI element override `customSettingsCodec()` and/or `runtimeDataCodec()` to return the same codec. The framework uses these codecs to encode/decode the data transparently:

- **Server to client**: The framework encodes per-market settings using `encodeAllCustomSettings()`, which calls `customSettingsCodec().encode()` for each subscribed market. On the client, the framework decodes each entry and passes the typed result as a `Map<ItemID, TSettings>` to `onPluginSyncDataReceived(data, customSettingsMap)`.
- **Client to server**: `sendCustomSettings(marketID, settings)` accepts a market ID and a typed `TSettings` object. The framework encodes it, sends it over the network, and calls `decodeAndApplyCustomSettings(marketID, data)` on the server which decodes and delegates to `onCustomSettingsApplied(marketID, settings)`.
- **Runtime data**: Same pattern -- `provideRuntimeData()` returns a typed `TRuntimeData` object, and `onRuntimeDataReceived(data)` receives the decoded typed object.
