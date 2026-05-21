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
int getSubscriptionOrder()
```
Returns the subscription order used for sorting in the plugin list.

```java
void setSubscriptionOrder(int order)
```
Sets the subscription order used for sorting in the plugin list.

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
Called every tick for enabled plugins. Read market state and queue changes through the `MarketInterface` objects.

```java
abstract void finalize(List<MarketInterface> markets)
```
Called every tick after all caches have been applied. Use for post-processing.

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
double getPrice()
```
Returns the current market price.

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
void setTargetPrice(double targetPrice)
```
Sets the target price the market moves toward. Queued in the cache -- applied after all plugins update.

```java
void addToTargetPrice(double delta)
```
Adds a delta to the target price. Positive values increase, negative values decrease.

### Order Placement

```java
long placeOrder(double amount, double price)
```
Places a limit order. Positive `amount` = buy, negative `amount` = sell. Returns an internal order identifier. Queued in the cache.

```java
void placeOrder(double amount)
```
Places a market order at the best available price. Positive `amount` = buy, negative `amount` = sell. Queued in the cache.

### Player Metrics

```java
double getNetPlayerItemFlow()
```
Returns the cumulative net items sold/bought by players for this market. Positive = players net-sold into the market (supply exceeds demand), negative = players net-bought from the market (demand exceeds supply). Useful for supply/demand-driven pricing plugins.

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
long getRawVolume(long backendPrice)
```
Returns raw (unscaled) volume at a specific backend price.

```java
float getRawVirtualVolume(long backendPrice)
```
Returns raw virtual volume at a specific backend price.

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
Returns the combined real volume from all registered distribution calculators at the given price.

```java
float getDefaultRawVolume(double pickPrice)
```
Returns the combined raw volume from all registered distribution calculators at the given price.

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
protected void info(String msg)
protected void error(String msg)
protected void warn(String msg)
protected void debug(String msg)
```
Logging methods.

**Layout constants:** `padding` (4), `spacing` (4), `defaultElementHeight` (20).

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
