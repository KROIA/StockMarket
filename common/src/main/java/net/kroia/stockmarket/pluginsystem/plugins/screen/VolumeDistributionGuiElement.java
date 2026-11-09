package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Inline settings GUI for the DefaultOrderbookVolumeDistributionPlugin.
 * Displays volume scale and convergence speed inputs with an Apply button.
 */
public class VolumeDistributionGuiElement extends PluginGuiElement<DefaultOrderbookVolumeDistributionPlugin.Settings, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volume_distribution.";
        public static final Component VOLUME_SCALE = Component.translatable(PREFIX + "volume_scale");
        public static final Component SPEED = Component.translatable(PREFIX + "speed");
        public static final Component ACCUMULATION_RATE = Component.translatable(PREFIX + "accumulation_rate");
        public static final Component DECUMULATION_RATE = Component.translatable(PREFIX + "decumulation_rate");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
        public static final Component RESET_VOLUME = Component.translatable(PREFIX + "reset_volume");
        public static final Component VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "volume_scale.tooltip");
        public static final Component SPEED_TOOLTIP = Component.translatable(PREFIX + "speed.tooltip");
        public static final Component ACCUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "accumulation_rate.tooltip");
        public static final Component DECUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "decumulation_rate.tooltip");
    }

    private final Label volumeScaleLabel;
    private final TextBox volumeScaleTextBox;
    private final Label speedLabel;
    private final TextBox speedTextBox;
    private final Label accumulationRateLabel;
    private final TextBox accumulationRateTextBox;
    private final Label decumulationRateLabel;
    private final TextBox decumulationRateTextBox;
    private final Button applyButton;
    private final Button applyAllButton;
    private final Button resetVolumeButton;
    /** Fills the form with the plugin defaults (null = hidden); own row below the buttons. */
    private final @Nullable Button defaultsButton;
    private @Nullable OrderbookVolumeHistogram histogram;
    private @Nullable OrderbookVolumeHistogram.Overlay volumeOverlay;
    private float currentVolumeScale = 1.0f;
    private final Map<Short, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData.MarketDistribution> marketDistributions = new HashMap<>();
    private Map<ItemID, DefaultOrderbookVolumeDistributionPlugin.Settings> allSettings = new HashMap<>();

    public VolumeDistributionGuiElement() {
        volumeScaleLabel = new Label(Texts.VOLUME_SCALE.getString());
        volumeScaleLabel.setAlignment(Alignment.RIGHT);
        volumeScaleTextBox = new TextBox();
        volumeScaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        speedLabel = new Label(Texts.SPEED.getString());
        speedLabel.setAlignment(Alignment.RIGHT);
        speedTextBox = new TextBox();
        speedTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        accumulationRateLabel = new Label(Texts.ACCUMULATION_RATE.getString());
        accumulationRateLabel.setAlignment(Alignment.RIGHT);
        accumulationRateTextBox = new TextBox();
        accumulationRateTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        decumulationRateLabel = new Label(Texts.DECUMULATION_RATE.getString());
        decumulationRateLabel.setAlignment(Alignment.RIGHT);
        decumulationRateTextBox = new TextBox();
        decumulationRateTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);
        resetVolumeButton = new Button(Texts.RESET_VOLUME.getString(), this::onResetVolume);
        // Apply-to-all never resets the volume (resetVolume = false)
        applyAllButton = new Button(ApplyAllTexts.APPLY_ALL.getString(), () -> confirmApplyToAllMarkets(readSettings(false)));
        setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAllButton);
        defaultsButton = createDefaultsButton();

        setSettingTooltip(Texts.VOLUME_SCALE_TOOLTIP, volumeScaleLabel, volumeScaleTextBox);
        setSettingTooltip(Texts.SPEED_TOOLTIP, speedLabel, speedTextBox);
        setSettingTooltip(Texts.ACCUMULATION_RATE_TOOLTIP, accumulationRateLabel, accumulationRateTextBox);
        setSettingTooltip(Texts.DECUMULATION_RATE_TOOLTIP, decumulationRateLabel, decumulationRateTextBox);

        addChild(volumeScaleLabel);
        addChild(volumeScaleTextBox);
        addChild(speedLabel);
        addChild(speedTextBox);
        addChild(accumulationRateLabel);
        addChild(accumulationRateTextBox);
        addChild(decumulationRateLabel);
        addChild(decumulationRateTextBox);
        addChild(applyButton);
        addChild(applyAllButton);
        addChild(resetVolumeButton);
        if (defaultsButton != null) addChild(defaultsButton);

        // 4 label+field rows, 1 button row with Apply + Apply-to-all + Reset side by side,
        // and the Defaults button in its own row
        setHeight((defaultsButton != null ? 6 : 5) * (defaultElementHeight + spacing) + padding);
    }

    @Override
    public void setOrderbookVolumeHistogram(@Nullable OrderbookVolumeHistogram histogram) {
        if (this.histogram != null && volumeOverlay != null) {
            this.histogram.removeOverlay(volumeOverlay);
        }
        this.histogram = histogram;
        if (histogram != null) {
            volumeOverlay = this::renderVolumeOverlay;
            histogram.addOverlay(volumeOverlay);
        } else {
            volumeOverlay = null;
        }
    }

    @Override
    protected StreamCodec<ByteBuf, DefaultOrderbookVolumeDistributionPlugin.Settings> customSettingsCodec() {
        return DefaultOrderbookVolumeDistributionPlugin.Settings.CODEC;
    }

    @Override
    protected StreamCodec<ByteBuf, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData> runtimeDataCodec() {
        return DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData.CODEC;
    }

    @Override
    protected void onRuntimeDataReceived(DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData data) {
        marketDistributions.clear();
        for (var entry : data.entries()) {
            marketDistributions.put(entry.itemId(), entry);
        }
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, DefaultOrderbookVolumeDistributionPlugin.Settings> customSettingsMap) {
        if (customSettingsMap != null) {
            allSettings = new HashMap<>(customSettingsMap);
        }
        // Populate textboxes if a market is already selected
        ItemID active = getActiveMarket();
        if (active != null && allSettings.containsKey(active)) {
            populateSettingsFromMarket(active);
        }
        startDataStream();
    }

    private void populateSettingsFromMarket(ItemID marketID) {
        DefaultOrderbookVolumeDistributionPlugin.Settings s = allSettings.get(marketID);
        if (s != null) {
            volumeScaleTextBox.setText(s.volumeScale(), 6);
            speedTextBox.setText(s.speed(), 6);
            accumulationRateTextBox.setText(s.accumulationRate(), 6);
            decumulationRateTextBox.setText(s.decumulationRate(), 6);
            currentVolumeScale = s.volumeScale();
        }
    }

    @Override
    protected DefaultOrderbookVolumeDistributionPlugin.Settings defaultSettings() {
        return DefaultOrderbookVolumeDistributionPlugin.Settings.createDefault();
    }

    @Override
    protected void showSettings(DefaultOrderbookVolumeDistributionPlugin.Settings s) {
        // only the form; currentVolumeScale (overlay) follows on Apply
        volumeScaleTextBox.setText(s.volumeScale(), 6);
        speedTextBox.setText(s.speed(), 6);
        accumulationRateTextBox.setText(s.accumulationRate(), 6);
        decumulationRateTextBox.setText(s.decumulationRate(), 6);
    }

    @Override
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        if (marketID != null && allSettings.containsKey(marketID)) {
            populateSettingsFromMarket(marketID);
        }
    }

    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable DefaultOrderbookVolumeDistributionPlugin.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) {
            allSettings.put(marketID, confirmedSettings);
        }
    }

    /**
     * Builds the settings from the current input values.
     *
     * @param resetVolume true to also wipe the market's current virtual volume
     */
    private DefaultOrderbookVolumeDistributionPlugin.Settings readSettings(boolean resetVolume) {
        return new DefaultOrderbookVolumeDistributionPlugin.Settings(
                (float) volumeScaleTextBox.getDouble(),
                (float) speedTextBox.getDouble(),
                (float) accumulationRateTextBox.getDouble(),
                (float) decumulationRateTextBox.getDouble(),
                resetVolume
        );
    }

    private void onApply() {
        ItemID market = getActiveMarket();
        if (market == null) return;
        sendCustomSettings(market, readSettings(false));
        currentVolumeScale = (float) volumeScaleTextBox.getDouble();
    }

    private void onResetVolume() {
        ItemID market = getActiveMarket();
        if (market == null) return;
        sendCustomSettings(market, readSettings(true));
        currentVolumeScale = (float) volumeScaleTextBox.getDouble();
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int eh = defaultElementHeight;
        int labelW = w / 3;
        int fieldW = w - labelW - spacing - padding * 2;
        int padding = PluginGuiElement.padding;

        int y = padding;
        volumeScaleLabel.setBounds(padding, y, labelW, eh);
        volumeScaleTextBox.setBounds(volumeScaleLabel.getRight() + spacing, y, fieldW, eh);

        y = volumeScaleTextBox.getBottom() + spacing;
        speedLabel.setBounds(padding, y, labelW, eh);
        speedTextBox.setBounds(speedLabel.getRight() + spacing, y, fieldW, eh);

        y = speedTextBox.getBottom() + spacing;
        accumulationRateLabel.setBounds(padding, y, labelW, eh);
        accumulationRateTextBox.setBounds(accumulationRateLabel.getRight() + spacing, y, fieldW, eh);

        y = accumulationRateTextBox.getBottom() + spacing;
        decumulationRateLabel.setBounds(padding, y, labelW, eh);
        decumulationRateTextBox.setBounds(decumulationRateLabel.getRight() + spacing, y, fieldW, eh);

        y = decumulationRateTextBox.getBottom() + spacing;
        int buttonW = (w - 2 * padding - 2 * spacing) / 3;
        applyButton.setBounds(padding, y, buttonW, eh);
        applyAllButton.setBounds(applyButton.getRight() + spacing, y, buttonW, eh);
        resetVolumeButton.setBounds(applyAllButton.getRight() + spacing, y, buttonW, eh);
        if (defaultsButton != null) defaultsButton.setBounds(padding, applyButton.getBottom() + spacing, buttonW, eh);
    }

    @Override
    protected void render() {}

    private void renderVolumeOverlay(OrderbookVolumeHistogram histogram) {
        ClientMarket market = histogram.getMarket();
        if (market == null) return;

        var distribution = marketDistributions.get(market.getItemID().getShort());
        if (distribution == null) return;

        double startPrice = histogram.getStartPrice();
        double endPrice = histogram.getEndPrice();
        if (startPrice >= endPrice) return;

        float maxAbsVolume = histogram.getMaxAbsVolume();
        if (maxAbsVolume <= 0) return;

        // Streamed data is in raw per-backend-price-point units (same as orderbook).
        // Histogram shows summed raw volume per chunk. Multiply by backend prices per chunk.
        float scaleFactor = market.getItemFractionScaleFactor();
        int histogramChunks = histogram.getChunkCount();
        if (histogramChunks <= 0) histogramChunks = 50;
        float backendPricesPerChunk = (float)((endPrice - startPrice) * scaleFactor / histogramChunks);

        int chunkCount = 50;
        double chunkPriceDelta = (endPrice - startPrice) / chunkCount;

        int lastX = -1;
        int lastY = -1;
        int lineColor = 0xFFFFAA00;

        for (int i = 0; i <= chunkCount; i++) {
            double price = startPrice + chunkPriceDelta * i;
            float targetVolume = Math.abs(interpolateVolume(distribution, price)) * backendPricesPerChunk / scaleFactor;
            int x = histogram.toCanvasSpaceX(targetVolume);
            int y = histogram.toCanvasSpaceY(price);

            if (lastX >= 0) {
                histogram.drawLine(lastX, lastY, x, y, 1.5f, lineColor);
            }
            lastX = x;
            lastY = y;
        }
    }

    private static float interpolateVolume(
            DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData.MarketDistribution dist,
            double price) {
        double fractionalIndex = (price - dist.startPrice()) / dist.priceStep();
        int low = (int) Math.floor(fractionalIndex);
        int high = low + 1;
        if (low < 0 || high >= dist.volumes().length) return 0;
        double t = fractionalIndex - low;
        return (float) (dist.volumes()[low] * (1 - t) + dist.volumes()[high] * t);
    }
}
