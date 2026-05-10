# Getting Started

Step-by-step guide to creating a StockMarket plugin from scratch.

## Step 1: Create a ServerPlugin Subclass

Extend `ServerPlugin<TSettings, TRuntimeData>` and implement all abstract methods. The type parameters define the types used for custom settings and runtime data streaming. Use `Void` for either parameter if the plugin does not use that feature.

At minimum, you need the lifecycle methods (`init`, `deInit`, `update`, `finalize`) and the event callbacks.

```java
package com.example.myplugin;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.minecraft.nbt.CompoundTag;
import java.util.List;

// Void, Void = no custom settings, no runtime data streaming
public class MyPlugin extends ServerPlugin<Void, Void> {

    public MyPlugin() {
        super();
    }

    @Override
    public void init() {}

    @Override
    public void deInit() {}

    @Override
    public void update(List<MarketInterface> markets) {
        for (MarketInterface market : markets) {
            double price = market.market.getPrice();
            // Your logic here
        }
    }

    @Override
    public void finalize(List<MarketInterface> markets) {}

    @Override
    public void onMarketSubscribed(ItemID marketID) {}

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {}

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public boolean save(CompoundTag tag) { return true; }

    @Override
    public boolean load(CompoundTag tag) { return true; }
}
```

The `update()` method runs every tick for enabled plugins. The `markets` list contains one `MarketInterface` per subscribed market. Access market data via `market.market` (prices, target price, orders) and `market.oderBook` (orderbook volume, virtual volume).

## Step 2: Register the Plugin

Create a registration class that registers your plugin type with `PluginRegistry`. The registration happens at class load time and must be safe for dedicated servers (no client class references).

```java
package com.example.myplugin;

import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;

public class MyPlugins {
    public static final PluginRegistryObject MY_PLUGIN = PluginRegistry.registerPlugin(
            MyPlugin.class.getName(),
            "MyPlugin",
            "Description of what the plugin does.",
            MyPlugin::new
    );

    public static void serverSetup() {
        // Called during server init -- force class loading to trigger registration
    }

    public static void clientSetup() {
        // Register GUI element factory (only runs on client)
        MY_PLUGIN.setGuiElementFactory(MyPluginGuiElement::new);
    }
}
```

The `typeID` (first argument) must be unique across all registered plugins. Using the fully qualified class name (`MyPlugin.class.getName()`) is the convention.

Call `MyPlugins.serverSetup()` from your mod's server initialization to ensure the class loads and registration executes.

## Step 3: Add a PluginGuiElement (Optional)

If your plugin has configurable settings, create a `PluginGuiElement<TSettings, TRuntimeData>` subclass. The type parameters must match the corresponding `ServerPlugin`. This renders inline within the plugin management screen.

The pattern: Label + TextBox for each setting, plus an Apply button that sends a typed settings object to the server. Override `customSettingsCodec()` to return the same codec used by the server plugin so the framework can decode incoming settings automatically.

```java
package com.example.myplugin;

import io.netty.buffer.ByteBuf;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

public class MyPluginGuiElement extends PluginGuiElement<MyPlugin.Settings, Void> {

    private final Label settingLabel;
    private final TextBox settingTextBox;
    private final Button applyButton;

    public MyPluginGuiElement() {
        settingLabel = new Label("My Setting:");
        settingTextBox = new TextBox();
        settingTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
        applyButton = new Button("Apply", this::onApply);

        addChild(settingLabel);
        addChild(settingTextBox);
        addChild(applyButton);
    }

    // Return the same codec as the server plugin so settings are decoded automatically
    @Override
    protected StreamCodec<ByteBuf, MyPlugin.Settings> customSettingsCodec() {
        return MyPlugin.Settings.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable MyPlugin.Settings settings) {
        // Settings are already decoded by the framework -- just use the typed object
        if (settings != null) {
            settingTextBox.setText(settings.mySetting());
        }
    }

    private void onApply() {
        // Send a typed settings object -- the framework encodes it via the codec
        sendCustomSettings(new MyPlugin.Settings((float) settingTextBox.getDouble()));
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 3);
        int labelW = w / 3;

        settingLabel.setBounds(0, 0, labelW, eh);
        settingTextBox.setBounds(labelW + spacing, 0, w - labelW - spacing, eh);
        applyButton.setBounds(0, eh + spacing, w, eh);
    }

    @Override
    protected void render() {}
}
```

For plugins that need more space (charts, visualizations), override `needsCustomScreen()` to return `true`. The framework will display your element in a dedicated full-screen layout instead of inline.

```java
@Override
public boolean needsCustomScreen() {
    return true;
}
```

## Step 4: Add Custom Settings

Custom settings allow the management UI to read and write plugin-specific configuration. Define an inner record type with a `StreamCodec`, then override three methods on your `ServerPlugin`:

**1. Define a Settings record with a CODEC:**

```java
public class MyPlugin extends ServerPlugin<MyPlugin.Settings, Void> {

    // Inner record type holding the plugin's configurable parameters
    public record Settings(float mySetting, int myOtherSetting) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                return new Settings(buf.readFloat(), buf.readInt());
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.mySetting());
                buf.writeInt(s.myOtherSetting());
            }
        };
    }

    private float mySetting = 1.0f;
    private int myOtherSetting = 10;
    // ... rest of plugin
```

For records with simple primitive fields, you can also use the `StreamCodec.composite` builder:

```java
public record Settings(float mySetting, int myOtherSetting) {
    public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Settings::mySetting,
            ByteBufCodecs.INT, Settings::myOtherSetting,
            Settings::new
    );
}
```

**2. Override the codec and data methods:**

```java
@Override
protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
    return Settings.CODEC;
}

@Override
protected Settings provideCustomSettings() {
    return new Settings(mySetting, myOtherSetting);
}

@Override
protected boolean applyCustomSettings(Settings settings) {
    mySetting = settings.mySetting();
    myOtherSetting = settings.myOtherSetting();
    return true;
}
```

The framework handles all byte-level encoding and decoding. The `PluginGuiElement` on the client side uses the same codec and works with the same `Settings` type directly. Return `null` from `customSettingsCodec()` if the plugin has no custom settings. Return `false` from `applyCustomSettings()` to reject invalid settings.

## Step 5: Add Persistence

Override `save()` and `load()` to persist plugin state across server restarts. The framework provides a `CompoundTag` (Minecraft NBT) for storage.

```java
@Override
public boolean save(CompoundTag tag) {
    tag.putFloat("mySetting", mySetting);
    tag.putInt("myOtherSetting", myOtherSetting);
    return true;
}

@Override
public boolean load(CompoundTag tag) {
    if (tag.contains("mySetting")) mySetting = tag.getFloat("mySetting");
    if (tag.contains("myOtherSetting")) myOtherSetting = tag.getInt("myOtherSetting");
    return true;
}
```

Always check `tag.contains()` before reading to handle missing keys gracefully (e.g., when loading data saved by an older plugin version). Return `true` on success, `false` on failure.

## Step 6: Add Runtime Data Streaming

Runtime data streaming sends live typed data from the server plugin to the client GUI element at a configurable interval. Like custom settings, runtime data uses a record type with a `StreamCodec`.

**1. Define a RuntimeData record with a CODEC:**

```java
public class MyPlugin extends ServerPlugin<MyPlugin.Settings, MyPlugin.Status> {

    // Runtime data record streamed to the client
    public record Status(double currentValue, boolean isActive) {
        public static final StreamCodec<ByteBuf, Status> CODEC = new StreamCodec<>() {
            @Override
            public Status decode(ByteBuf buf) {
                return new Status(buf.readDouble(), buf.readBoolean());
            }
            @Override
            public void encode(ByteBuf buf, Status s) {
                buf.writeDouble(s.currentValue());
                buf.writeBoolean(s.isActive());
            }
        };
    }
    // ... rest of plugin
```

**2. Server side** -- override on your `ServerPlugin`:

```java
@Override
protected StreamCodec<ByteBuf, Status> runtimeDataCodec() {
    return Status.CODEC;
}

@Override
protected Status provideRuntimeData() {
    return new Status(currentValue, isActive);
}

@Override
public long getRuntimeDataStreamInterval() {
    return 500; // milliseconds between updates (default is 500)
}
```

Return `null` from `runtimeDataCodec()` if the plugin does not stream runtime data. Return `null` from `provideRuntimeData()` if there is no data to send at this moment. The framework skips sending when the payload has not changed since the last transmission.

**3. Client side** -- override on your `PluginGuiElement`:

```java
public class MyGuiElement extends PluginGuiElement<MyPlugin.Settings, MyPlugin.Status> {

    @Override
    protected StreamCodec<ByteBuf, MyPlugin.Status> runtimeDataCodec() {
        return MyPlugin.Status.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable MyPlugin.Settings settings) {
        // Start streaming when sync data arrives
        startDataStream();
    }

    @Override
    protected void onRuntimeDataReceived(MyPlugin.Status data) {
        // Data is already decoded -- use the typed fields directly
        currentValue = data.currentValue();
        isActive = data.isActive();
    }
}
```

Call `startDataStream()` when the GUI element needs live data (typically in `onPluginSyncDataReceived`). Call `stopDataStream()` when the element is no longer visible.

## Next Steps

- [API Reference](api-reference.md) -- complete method signatures for all plugin API classes
- [Examples](examples.md) -- three complete example plugins demonstrating different capabilities
