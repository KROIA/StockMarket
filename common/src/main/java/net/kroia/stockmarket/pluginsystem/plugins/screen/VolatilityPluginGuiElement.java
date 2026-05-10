package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Inline settings GUI for the VolatilityPlugin.
 * Displays a volatility scale input and an Apply button.
 */
public class VolatilityPluginGuiElement extends PluginGuiElement<VolatilityPlugin.Settings, Void> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volatility_plugin.";
        public static final Component SCALE_LABEL = Component.translatable(PREFIX + "scale");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
    }

    private final Label scaleLabel;
    private final TextBox scaleTextBox;
    private final Button applyButton;

    public VolatilityPluginGuiElement() {
        scaleLabel = new Label(Texts.SCALE_LABEL.getString());
        scaleTextBox = new TextBox();
        scaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        addChild(scaleLabel);
        addChild(scaleTextBox);
        addChild(applyButton);
    }

    @Override
    protected StreamCodec<ByteBuf, VolatilityPlugin.Settings> customSettingsCodec() {
        return VolatilityPlugin.Settings.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable VolatilityPlugin.Settings customSettings) {
        if (customSettings != null) {
            scaleTextBox.setText(customSettings.volatilityScale());
        }
    }

    private void onApply() {
        sendCustomSettings(new VolatilityPlugin.Settings((float) scaleTextBox.getDouble()));
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int eh = Math.min(defaultElementHeight, h / 3);
        int labelW = w / 3;

        // Row 1: label + textbox side by side
        scaleLabel.setBounds(0, 0, labelW, eh);
        scaleTextBox.setBounds(labelW + spacing, 0, w - labelW - spacing, eh);

        // Row 2: apply button
        applyButton.setBounds(0, eh + spacing, w, eh);
    }

    @Override
    protected void render() {}
}
