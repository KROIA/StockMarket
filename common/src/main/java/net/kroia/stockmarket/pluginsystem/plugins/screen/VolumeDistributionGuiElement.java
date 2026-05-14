package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.geometry.Rectangle;
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
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
    }

    private final Label volumeScaleLabel;
    private final TextBox volumeScaleTextBox;
    private final Label speedLabel;
    private final TextBox speedTextBox;
    private final Button applyButton;
    private @Nullable OrderbookVolumeHistogram histogram;
    private @Nullable OrderbookVolumeHistogram.Overlay volumeOverlay;
    private float currentVolumeScale = 1.0f;
    private final Map<Short, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData.MarketDistribution> marketDistributions = new HashMap<>();

    public VolumeDistributionGuiElement() {
        volumeScaleLabel = new Label(Texts.VOLUME_SCALE.getString());
        volumeScaleTextBox = new TextBox();
        volumeScaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        speedLabel = new Label(Texts.SPEED.getString());
        speedTextBox = new TextBox();
        speedTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        addChild(volumeScaleLabel);
        addChild(volumeScaleTextBox);
        addChild(speedLabel);
        addChild(speedTextBox);
        addChild(applyButton);
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
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable DefaultOrderbookVolumeDistributionPlugin.Settings customSettings) {
        if (customSettings != null) {
            volumeScaleTextBox.setText(customSettings.volumeScale());
            speedTextBox.setText(customSettings.speed());
            currentVolumeScale = customSettings.volumeScale();
        }
        startDataStream();
    }

    private void onApply() {
        sendCustomSettings(new DefaultOrderbookVolumeDistributionPlugin.Settings(
                (float) volumeScaleTextBox.getDouble(),
                (float) speedTextBox.getDouble()
        ));
        currentVolumeScale = (float) volumeScaleTextBox.getDouble();
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 4);
        int labelW = w / 3;

        // Row 1: volume scale label + textbox
        volumeScaleLabel.setBounds(0, 0, labelW, eh);
        volumeScaleTextBox.setBounds(labelW + spacing, 0, w - labelW - spacing, eh);

        // Row 2: speed label + textbox
        int row2Y = eh + spacing;
        speedLabel.setBounds(0, row2Y, labelW, eh);
        speedTextBox.setBounds(labelW + spacing, row2Y, w - labelW - spacing, eh);

        // Row 3: apply button
        applyButton.setBounds(0, row2Y + eh + spacing, w, eh);
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

        Rectangle bounds = histogram.getCanvasBounds();
        if (bounds.width <= 0) return;

        int chunkCount = 50;
        double chunkPriceDelta = (endPrice - startPrice) / chunkCount;

        // First pass: interpolate from streamed data and find max for self-normalization
        float[] targets = new float[chunkCount + 1];
        float maxTarget = 0;
        for (int i = 0; i <= chunkCount; i++) {
            double price = startPrice + chunkPriceDelta * i;
            targets[i] = Math.abs(interpolateVolume(distribution, price));
            maxTarget = Math.max(maxTarget, targets[i]);
        }
        if (maxTarget <= 0) return;

        // Second pass: draw the curve, scaled so peak target fills the histogram width
        int lastX = -1;
        int lastY = -1;
        int lineColor = 0xFFFFAA00;

        for (int i = 0; i <= chunkCount; i++) {
            float normalized = targets[i] / maxTarget * maxAbsVolume;
            int x = histogram.toCanvasSpaceX(normalized);
            int y = histogram.toCanvasSpaceY(startPrice + chunkPriceDelta * i);

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
