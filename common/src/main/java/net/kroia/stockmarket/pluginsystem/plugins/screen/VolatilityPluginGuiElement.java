package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.banksystem.util.ItemID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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
    private Map<ItemID, VolatilityPlugin.Settings> allSettings = new HashMap<>();

    public VolatilityPluginGuiElement() {
        scaleLabel = new Label(Texts.SCALE_LABEL.getString());
        scaleLabel.setAlignment(Alignment.RIGHT);
        scaleTextBox = new TextBox();
        scaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        addChild(scaleLabel);
        addChild(scaleTextBox);
        addChild(applyButton);

        // 2 rows of controls
        setHeight(2 * (defaultElementHeight + spacing) + padding);
    }

    @Override
    protected StreamCodec<ByteBuf, VolatilityPlugin.Settings> customSettingsCodec() {
        return VolatilityPlugin.Settings.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, VolatilityPlugin.Settings> customSettingsMap) {
        if (customSettingsMap != null) {
            allSettings = new HashMap<>(customSettingsMap);
        }
        // If a market is already selected, populate from its settings
        ItemID active = getActiveMarket();
        if (active != null && allSettings.containsKey(active)) {
            scaleTextBox.setText(allSettings.get(active).volatilityScale());
        }
    }

    @Override
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        if (marketID != null && allSettings.containsKey(marketID)) {
            scaleTextBox.setText(allSettings.get(marketID).volatilityScale());
        }
    }

    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable VolatilityPlugin.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) {
            allSettings.put(marketID, confirmedSettings);
        }
    }

    private void onApply() {
        ItemID market = getActiveMarket();
        if (market == null) return;
        sendCustomSettings(market, new VolatilityPlugin.Settings((float) scaleTextBox.getDouble()));
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int eh = defaultElementHeight;
        int labelW = w / 3;
        int padding = PluginGuiElement.padding;

        // Row 1: label + textbox side by side
        scaleLabel.setBounds(padding, padding, labelW, eh);
        scaleTextBox.setBounds(labelW + spacing, scaleLabel.getTop(), w - labelW - spacing, eh);

        // Row 2: apply button
        applyButton.setBounds(padding, scaleTextBox.getBottom() + spacing, w-2*padding, eh);
    }

    @Override
    protected void render() {}
}
