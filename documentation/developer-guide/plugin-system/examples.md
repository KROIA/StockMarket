# Example Plugins

Three complete example plugins demonstrating different plugin system capabilities.

---

## Example 1: PriceFloorPlugin

Prevents market prices from dropping below a configurable minimum. When the current price falls below the floor, the plugin sets the target price to the minimum.

**Demonstrates:** target price manipulation, custom settings with StreamCodec, save/load, inline GUI element.

### ServerPlugin

```java
package com.example.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public class PriceFloorPlugin extends ServerPlugin<PriceFloorPlugin.Settings, Void> {

    // Settings record with StreamCodec -- defines the configurable minimum price
    public record Settings(float minPrice) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) { return new Settings(buf.readFloat()); }
            @Override
            public void encode(ByteBuf buf, Settings s) { buf.writeFloat(s.minPrice()); }
        };
    }

    private float minPrice = 10.0f;

    public PriceFloorPlugin() {
        super();
    }

    @Override
    public void init() {}

    @Override
    public void deInit() {}

    @Override
    public void update(List<MarketInterface> markets) {
        for (MarketInterface market : markets) {
            if (market.market.getPrice() < minPrice) {
                market.market.setTargetPrice(minPrice);
            }
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

    // --- Custom Settings (per-market, StreamCodec-based) ---

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideDefaultCustomSettings() {
        return new Settings(minPrice);
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, @NotNull Settings settings) {
        minPrice = settings.minPrice();
    }

    // --- Persistence ---

    @Override
    public boolean save(CompoundTag tag) {
        tag.putFloat("minPrice", minPrice);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("minPrice")) minPrice = tag.getFloat("minPrice");
        return true;
    }
}
```

### PluginGuiElement

Inline element with a Label, TextBox, and Apply button. Settings are received as typed `Settings` objects -- no manual byte decoding needed.

```java
package com.example.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.PriceFloorPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PriceFloorGuiElement extends PluginGuiElement<PriceFloorPlugin.Settings, Void> {

    private final Label minPriceLabel;
    private final TextBox minPriceTextBox;
    private final Button applyButton;

    public PriceFloorGuiElement() {
        minPriceLabel = new Label("Min Price:");
        minPriceTextBox = new TextBox();
        minPriceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
        applyButton = new Button("Apply", this::onApply);

        addChild(minPriceLabel);
        addChild(minPriceTextBox);
        addChild(applyButton);
    }

    // Return the same codec as the server plugin
    @Override
    protected StreamCodec<ByteBuf, PriceFloorPlugin.Settings> customSettingsCodec() {
        return PriceFloorPlugin.Settings.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data,
                                            @Nullable Map<ItemID, PriceFloorPlugin.Settings> customSettingsMap) {
        // Settings are per-market -- for simple plugins, pick any market's settings
        if (customSettingsMap != null && !customSettingsMap.isEmpty()) {
            PriceFloorPlugin.Settings settings = customSettingsMap.values().iterator().next();
            minPriceTextBox.setText(settings.minPrice());
        }
        // Set active market for sendCustomSettings
        List<ItemID> markets = data.getSubscribedMarkets();
        if (!markets.isEmpty()) {
            setActiveMarket(markets.get(0));
        }
    }

    private void onApply() {
        // Send per-market settings -- requires an active market
        ItemID market = getActiveMarket();
        if (market != null) {
            sendCustomSettings(market,
                    new PriceFloorPlugin.Settings((float) minPriceTextBox.getDouble()));
        }
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 3);
        int labelW = w / 3;

        minPriceLabel.setBounds(0, 0, labelW, eh);
        minPriceTextBox.setBounds(labelW + spacing, 0, w - labelW - spacing, eh);
        applyButton.setBounds(0, eh + spacing, w, eh);
    }

    @Override
    protected void render() {}
}
```

### Registration

```java
package com.example.plugins;

import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;

public class ExamplePlugins {
    public static final PluginRegistryObject PRICE_FLOOR = PluginRegistry.registerPlugin(
            PriceFloorPlugin.class.getName(),
            "PriceFloorPlugin",
            "Prevents market prices from dropping below a configurable minimum.",
            PriceFloorPlugin::new
    );

    public static void serverSetup() {}

    public static void clientSetup() {
        PRICE_FLOOR.setGuiElementFactory(
                com.example.plugins.screen.PriceFloorGuiElement::new
        );
    }
}
```

**Key design choices:**
- The plugin only acts when the price is below the floor, leaving the market alone otherwise.
- `setTargetPrice()` queues the change -- it does not immediately modify the market. If another plugin also sets the target price in the same tick, the last write wins.
- The `Settings` record + `StreamCodec` pattern keeps the server and client in sync with zero manual byte encoding. Both sides reference the same `PriceFloorPlugin.Settings.CODEC`.

---

## Example 2: TradingHoursPlugin

Restricts trading to configurable hours. Outside trading hours, the plugin freezes the market by continuously setting the target price to the current price, preventing drift.

**Demonstrates:** time-based logic, custom settings with StreamCodec (two integers), runtime data streaming with StreamCodec (current hour + trading status), save/load.

### ServerPlugin

```java
package com.example.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.time.LocalTime;
import java.util.List;

public class TradingHoursPlugin extends ServerPlugin<TradingHoursPlugin.Settings, TradingHoursPlugin.Status> {

    // Settings record -- configurable start and end hours
    public record Settings(int startHour, int endHour) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Settings::startHour,
                ByteBufCodecs.INT, Settings::endHour,
                Settings::new
        );
    }

    // Runtime data record -- current hour and trading status streamed to GUI
    public record Status(int currentHour, boolean isTrading) {
        public static final StreamCodec<ByteBuf, Status> CODEC = new StreamCodec<>() {
            @Override
            public Status decode(ByteBuf buf) {
                return new Status(buf.readInt(), buf.readBoolean());
            }
            @Override
            public void encode(ByteBuf buf, Status s) {
                buf.writeInt(s.currentHour());
                buf.writeBoolean(s.isTrading());
            }
        };
    }

    private int startHour = 9;
    private int endHour = 17;

    public TradingHoursPlugin() {
        super();
    }

    @Override
    public void init() {}

    @Override
    public void deInit() {}

    @Override
    public void update(List<MarketInterface> markets) {
        int currentHour = LocalTime.now().getHour();
        boolean isTradingTime = isTradingHour(currentHour);

        if (!isTradingTime) {
            // Freeze the market by setting target price to current price
            for (MarketInterface market : markets) {
                market.market.setTargetPrice(market.market.getPrice());
            }
        }
        // During trading hours: do nothing, let other plugins drive the price
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

    // --- Runtime Data Streaming (StreamCodec-based) ---

    @Override
    protected StreamCodec<ByteBuf, Status> runtimeDataCodec() {
        return Status.CODEC;
    }

    @Override
    protected Status provideRuntimeData() {
        int currentHour = LocalTime.now().getHour();
        return new Status(currentHour, isTradingHour(currentHour));
    }

    @Override
    public long getRuntimeDataStreamInterval() {
        return 1000; // Update once per second
    }

    // --- Custom Settings (per-market, StreamCodec-based) ---

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideDefaultCustomSettings() {
        return new Settings(startHour, endHour);
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, @NotNull Settings settings) {
        // Validate and apply hours
        if (settings.startHour() >= 0 && settings.startHour() <= 23 &&
            settings.endHour() >= 0 && settings.endHour() <= 23) {
            startHour = settings.startHour();
            endHour = settings.endHour();
        }
    }

    // --- Persistence ---

    @Override
    public boolean save(CompoundTag tag) {
        tag.putInt("startHour", startHour);
        tag.putInt("endHour", endHour);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("startHour")) startHour = tag.getInt("startHour");
        if (tag.contains("endHour")) endHour = tag.getInt("endHour");
        return true;
    }

    // --- Internal ---

    private boolean isTradingHour(int hour) {
        if (startHour <= endHour) {
            return hour >= startHour && hour < endHour;
        } else {
            // Wraps around midnight (e.g., 22:00 to 06:00)
            return hour >= startHour || hour < endHour;
        }
    }
}
```

### PluginGuiElement

Displays settings (start/end hour) and live runtime data (current hour, trading status). Both are received as typed objects -- no manual byte decoding.

```java
package com.example.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.TradingHoursPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class TradingHoursGuiElement extends PluginGuiElement<TradingHoursPlugin.Settings, TradingHoursPlugin.Status> {

    private final Label startLabel;
    private final TextBox startTextBox;
    private final Label endLabel;
    private final TextBox endTextBox;
    private final Button applyButton;
    private final Label statusLabel;

    public TradingHoursGuiElement() {
        startLabel = new Label("Start Hour:");
        startTextBox = new TextBox();
        startTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(false, false, 2, 0));

        endLabel = new Label("End Hour:");
        endTextBox = new TextBox();
        endTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(false, false, 2, 0));

        applyButton = new Button("Apply", this::onApply);
        statusLabel = new Label("Status: --");

        addChild(startLabel);
        addChild(startTextBox);
        addChild(endLabel);
        addChild(endTextBox);
        addChild(applyButton);
        addChild(statusLabel);
    }

    // Return codecs matching the server plugin
    @Override
    protected StreamCodec<ByteBuf, TradingHoursPlugin.Settings> customSettingsCodec() {
        return TradingHoursPlugin.Settings.CODEC;
    }

    @Override
    protected StreamCodec<ByteBuf, TradingHoursPlugin.Status> runtimeDataCodec() {
        return TradingHoursPlugin.Status.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data,
                                            @Nullable Map<ItemID, TradingHoursPlugin.Settings> customSettingsMap) {
        // Settings are per-market -- for simple plugins, pick any market's settings
        if (customSettingsMap != null && !customSettingsMap.isEmpty()) {
            TradingHoursPlugin.Settings settings = customSettingsMap.values().iterator().next();
            startTextBox.setText(settings.startHour());
            endTextBox.setText(settings.endHour());
        }
        // Set active market for sendCustomSettings
        List<ItemID> markets = data.getSubscribedMarkets();
        if (!markets.isEmpty()) {
            setActiveMarket(markets.get(0));
        }
        // Start streaming for live status updates
        startDataStream();
    }

    @Override
    protected void onRuntimeDataReceived(TradingHoursPlugin.Status data) {
        // Runtime data already decoded -- use typed fields
        statusLabel.setText("Hour: " + data.currentHour() + " | " +
                            (data.isTrading() ? "OPEN" : "CLOSED"));
    }

    private void onApply() {
        // Send per-market settings
        ItemID market = getActiveMarket();
        if (market != null) {
            sendCustomSettings(market, new TradingHoursPlugin.Settings(
                    (int) startTextBox.getDouble(),
                    (int) endTextBox.getDouble()
            ));
        }
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 6);
        int labelW = w / 3;

        int y = 0;
        startLabel.setBounds(0, y, labelW, eh);
        startTextBox.setBounds(labelW + spacing, y, w - labelW - spacing, eh);

        y += eh + spacing;
        endLabel.setBounds(0, y, labelW, eh);
        endTextBox.setBounds(labelW + spacing, y, w - labelW - spacing, eh);

        y += eh + spacing;
        applyButton.setBounds(0, y, w, eh);

        y += eh + spacing;
        statusLabel.setBounds(0, y, w, eh);
    }

    @Override
    protected void render() {}
}
```

### Registration

```java
public static final PluginRegistryObject TRADING_HOURS = PluginRegistry.registerPlugin(
        TradingHoursPlugin.class.getName(),
        "TradingHoursPlugin",
        "Restricts trading to configurable hours.",
        TradingHoursPlugin::new
);

public static void clientSetup() {
    TRADING_HOURS.setGuiElementFactory(
            com.example.plugins.screen.TradingHoursGuiElement::new
    );
}
```

**Key design choices:**
- The plugin does not cancel existing orders. It only prevents price movement by anchoring the target price. Other plugins that set target prices in the same tick may override this.
- `isTradingHour()` handles midnight wraparound (e.g., 22:00 to 06:00) for markets that need overnight trading windows.
- Runtime data updates at 1 second intervals -- frequent enough to show status changes, infrequent enough to avoid unnecessary network traffic.
- Input validation in `onCustomSettingsApplied()` silently rejects invalid hours (outside 0--23).
- Both `Settings` and `Status` are inner records with `StreamCodec` fields. The GUI element references them via `TradingHoursPlugin.Settings` and `TradingHoursPlugin.Status`.

---

## Example 3: LiquidityProviderPlugin

Places standing limit buy and sell orders at a configurable spread around the current market price. Maintains per-market state to track placed orders and adjusts positions as the price moves.

**Demonstrates:** limit order placement via `placeOrder(amount, price)`, per-market runtime data with StreamCodec collections, orderbook reading, custom settings with StreamCodec, save/load.

### ServerPlugin

```java
package com.example.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiquidityProviderPlugin extends ServerPlugin<LiquidityProviderPlugin.Settings, LiquidityProviderPlugin.Levels> {

    // Settings record -- spread and order size
    public record Settings(float spread, float orderSize) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Settings::spread,
                ByteBufCodecs.FLOAT, Settings::orderSize,
                Settings::new
        );
    }

    // Runtime data record -- buy/sell levels for all subscribed markets
    public record Levels(List<MarketLevel> entries) {
        public record MarketLevel(short itemId, double buyPrice, double sellPrice) {}

        public static final StreamCodec<ByteBuf, Levels> CODEC = new StreamCodec<>() {
            @Override
            public Levels decode(ByteBuf buf) {
                int count = buf.readInt();
                List<MarketLevel> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    entries.add(new MarketLevel(buf.readShort(), buf.readDouble(), buf.readDouble()));
                }
                return new Levels(entries);
            }
            @Override
            public void encode(ByteBuf buf, Levels data) {
                buf.writeInt(data.entries().size());
                for (MarketLevel e : data.entries()) {
                    buf.writeShort(e.itemId());
                    buf.writeDouble(e.buyPrice());
                    buf.writeDouble(e.sellPrice());
                }
            }
        };
    }

    static class MarketState {
        double lastBuyPrice = 0;
        double lastSellPrice = 0;
        int tickCounter = 0;
    }

    private final Map<ItemID, MarketState> marketStates = new HashMap<>();

    // Configurable parameters
    private float spread = 0.05f;     // 5% spread from current price
    private float orderSize = 1.0f;   // Size of each limit order

    public LiquidityProviderPlugin() {
        super();
    }

    @Override
    public void init() {}

    @Override
    public void deInit() {}

    @Override
    public void update(List<MarketInterface> markets) {
        for (MarketInterface market : markets) {
            MarketState state = marketStates.get(market.market.getMarketID());
            if (state == null) continue;
            updateForMarket(market, state);
        }
    }

    private void updateForMarket(MarketInterface market, MarketState state) {
        // Only update every 100 ticks (5 seconds at 20 TPS)
        state.tickCounter++;
        if (state.tickCounter < 100) return;
        state.tickCounter = 0;

        double currentPrice = market.market.getPrice();
        if (currentPrice <= 0) return;

        double halfSpread = currentPrice * spread;
        double buyPrice = currentPrice - halfSpread;
        double sellPrice = currentPrice + halfSpread;

        // Only place new orders if price has moved significantly
        if (Math.abs(buyPrice - state.lastBuyPrice) > halfSpread * 0.5 ||
            Math.abs(sellPrice - state.lastSellPrice) > halfSpread * 0.5) {

            // Check existing volume at these levels
            float buyVolume = market.oderBook.getRealVolume(buyPrice, currentPrice);
            float sellVolume = market.oderBook.getRealVolume(currentPrice, sellPrice);

            // Place buy order if insufficient buy-side liquidity
            if (buyVolume < orderSize * 2) {
                market.market.placeOrder(orderSize, buyPrice);
                info("Placed buy order: " + orderSize + " @ " + buyPrice);
            }

            // Place sell order if insufficient sell-side liquidity
            if (Math.abs(sellVolume) < orderSize * 2) {
                market.market.placeOrder(-orderSize, sellPrice);
                info("Placed sell order: " + (-orderSize) + " @ " + sellPrice);
            }

            state.lastBuyPrice = buyPrice;
            state.lastSellPrice = sellPrice;
        }
    }

    @Override
    public void finalize(List<MarketInterface> markets) {}

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        marketStates.put(marketID, new MarketState());
    }

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {
        marketStates.remove(marketID);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    // --- Runtime Data Streaming (StreamCodec-based) ---

    @Override
    protected StreamCodec<ByteBuf, Levels> runtimeDataCodec() {
        return Levels.CODEC;
    }

    @Override
    protected Levels provideRuntimeData() {
        if (marketStates.isEmpty()) return null;

        List<Levels.MarketLevel> entries = new ArrayList<>();
        for (Map.Entry<ItemID, MarketState> entry : marketStates.entrySet()) {
            entries.add(new Levels.MarketLevel(
                    entry.getKey().getShort(),
                    entry.getValue().lastBuyPrice,
                    entry.getValue().lastSellPrice
            ));
        }
        return new Levels(entries);
    }

    @Override
    public long getRuntimeDataStreamInterval() {
        return 1000;
    }

    // --- Custom Settings (per-market, StreamCodec-based) ---

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideDefaultCustomSettings() {
        return new Settings(spread, orderSize);
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, @NotNull Settings settings) {
        // Validate and apply
        if (settings.spread() > 0 && settings.spread() <= 1.0f && settings.orderSize() > 0) {
            spread = settings.spread();
            orderSize = settings.orderSize();
        }
    }

    // --- Persistence ---

    @Override
    public boolean save(CompoundTag tag) {
        tag.putFloat("spread", spread);
        tag.putFloat("orderSize", orderSize);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("spread")) spread = tag.getFloat("spread");
        if (tag.contains("orderSize")) orderSize = tag.getFloat("orderSize");
        return true;
    }
}
```

### PluginGuiElement

Inline settings for spread and order size, plus live display of buy/sell levels. Both settings and runtime data are received as typed objects.

```java
package com.example.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.LiquidityProviderPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class LiquidityProviderGuiElement extends PluginGuiElement<LiquidityProviderPlugin.Settings, LiquidityProviderPlugin.Levels> {

    private final Label spreadLabel;
    private final TextBox spreadTextBox;
    private final Label orderSizeLabel;
    private final TextBox orderSizeTextBox;
    private final Button applyButton;
    private final Label statusLabel;

    public LiquidityProviderGuiElement() {
        spreadLabel = new Label("Spread:");
        spreadTextBox = new TextBox();
        spreadTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 5, 4));

        orderSizeLabel = new Label("Order Size:");
        orderSizeTextBox = new TextBox();
        orderSizeTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 4));

        applyButton = new Button("Apply", this::onApply);
        statusLabel = new Label("Levels: --");

        addChild(spreadLabel);
        addChild(spreadTextBox);
        addChild(orderSizeLabel);
        addChild(orderSizeTextBox);
        addChild(applyButton);
        addChild(statusLabel);
    }

    // Return codecs matching the server plugin
    @Override
    protected StreamCodec<ByteBuf, LiquidityProviderPlugin.Settings> customSettingsCodec() {
        return LiquidityProviderPlugin.Settings.CODEC;
    }

    @Override
    protected StreamCodec<ByteBuf, LiquidityProviderPlugin.Levels> runtimeDataCodec() {
        return LiquidityProviderPlugin.Levels.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data,
                                            @Nullable Map<ItemID, LiquidityProviderPlugin.Settings> customSettingsMap) {
        // Settings are per-market -- for simple plugins, pick any market's settings
        if (customSettingsMap != null && !customSettingsMap.isEmpty()) {
            LiquidityProviderPlugin.Settings settings = customSettingsMap.values().iterator().next();
            spreadTextBox.setText(settings.spread());
            orderSizeTextBox.setText(settings.orderSize());
        }
        // Set active market for sendCustomSettings
        List<ItemID> markets = data.getSubscribedMarkets();
        if (!markets.isEmpty()) {
            setActiveMarket(markets.get(0));
        }
        startDataStream();
    }

    @Override
    protected void onRuntimeDataReceived(LiquidityProviderPlugin.Levels data) {
        // Runtime data already decoded -- use typed fields
        if (!data.entries().isEmpty()) {
            LiquidityProviderPlugin.Levels.MarketLevel first = data.entries().get(0);
            statusLabel.setText(String.format("Buy: %.2f | Sell: %.2f",
                    first.buyPrice(), first.sellPrice()));
        }
    }

    private void onApply() {
        // Send per-market settings
        ItemID market = getActiveMarket();
        if (market != null) {
            sendCustomSettings(market, new LiquidityProviderPlugin.Settings(
                    (float) spreadTextBox.getDouble(),
                    (float) orderSizeTextBox.getDouble()
            ));
        }
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 6);
        int labelW = w / 3;

        int y = 0;
        spreadLabel.setBounds(0, y, labelW, eh);
        spreadTextBox.setBounds(labelW + spacing, y, w - labelW - spacing, eh);

        y += eh + spacing;
        orderSizeLabel.setBounds(0, y, labelW, eh);
        orderSizeTextBox.setBounds(labelW + spacing, y, w - labelW - spacing, eh);

        y += eh + spacing;
        applyButton.setBounds(0, y, w, eh);

        y += eh + spacing;
        statusLabel.setBounds(0, y, w, eh);
    }

    @Override
    protected void render() {}
}
```

### Registration

```java
public static final PluginRegistryObject LIQUIDITY_PROVIDER = PluginRegistry.registerPlugin(
        LiquidityProviderPlugin.class.getName(),
        "LiquidityProviderPlugin",
        "Places standing limit orders at a spread around the current price.",
        LiquidityProviderPlugin::new
);

public static void clientSetup() {
    LIQUIDITY_PROVIDER.setGuiElementFactory(
            com.example.plugins.screen.LiquidityProviderGuiElement::new
    );
}
```

**Key design choices:**
- Per-market state via `HashMap<ItemID, MarketState>` -- initialized in `onMarketSubscribed`, cleaned up in `onMarketUnsubscribed`. This is the standard pattern for plugins that track state per market.
- Orders are only placed every 100 ticks (5 seconds) and only when the price has moved enough to warrant repositioning. This prevents flooding the orderbook.
- The plugin reads existing volume with `getRealVolume()` before placing orders, avoiding redundant liquidity.
- `placeOrder(amount, price)` creates limit orders: positive amount = buy, negative amount = sell. Orders are queued in the cache and applied atomically after all plugins finish their `update()`.
- The `Levels` record uses a nested `MarketLevel` record and a custom `StreamCodec` to encode a variable-length list of per-market data. The GUI element receives the decoded `Levels` object and accesses entries via `data.entries()`.
- `Settings` uses the simpler `StreamCodec.composite` builder since it only has two float fields.
- Input validation rejects spreads outside (0, 1] and non-positive order sizes.
