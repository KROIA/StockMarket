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
 * Displays the volatility scale, the flow-equilibrium toggle and its parameters
 * (flow sensitivity, min/max price multipliers) plus an Apply button.
 */
public class VolatilityPluginGuiElement extends PluginGuiElement<VolatilityPlugin.Settings, Void> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volatility_plugin.";
        public static final Component SCALE_LABEL = Component.translatable(PREFIX + "scale");
        public static final Component FLOW_ENABLED_LABEL = Component.translatable(PREFIX + "flow_enabled");
        public static final Component FLOW_SENSITIVITY_LABEL = Component.translatable(PREFIX + "flow_sensitivity");
        public static final Component MIN_PRICE_MULT_LABEL = Component.translatable(PREFIX + "min_price_mult");
        public static final Component MAX_PRICE_MULT_LABEL = Component.translatable(PREFIX + "max_price_mult");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
    }

    private final Label scaleLabel;
    private final TextBox scaleTextBox;
    private final CheckBox flowEnabledCheckBox;
    private final Label flowSensitivityLabel;
    private final TextBox flowSensitivityTextBox;
    private final Label minPriceMultLabel;
    private final TextBox minPriceMultTextBox;
    private final Label maxPriceMultLabel;
    private final TextBox maxPriceMultTextBox;
    private final Button applyButton;
    private Map<ItemID, VolatilityPlugin.Settings> allSettings = new HashMap<>();

    public VolatilityPluginGuiElement() {
        scaleLabel = new Label(Texts.SCALE_LABEL.getString());
        scaleLabel.setAlignment(Alignment.RIGHT);
        scaleTextBox = new TextBox();
        scaleTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        flowEnabledCheckBox = new CheckBox(Texts.FLOW_ENABLED_LABEL.getString());
        flowEnabledCheckBox.setChecked(VolatilityPlugin.DEFAULT_FLOW_INFLUENCE_ENABLED);

        flowSensitivityLabel = new Label(Texts.FLOW_SENSITIVITY_LABEL.getString());
        flowSensitivityLabel.setAlignment(Alignment.RIGHT);
        flowSensitivityTextBox = new TextBox();
        flowSensitivityTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        minPriceMultLabel = new Label(Texts.MIN_PRICE_MULT_LABEL.getString());
        minPriceMultLabel.setAlignment(Alignment.RIGHT);
        minPriceMultTextBox = new TextBox();
        minPriceMultTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        maxPriceMultLabel = new Label(Texts.MAX_PRICE_MULT_LABEL.getString());
        maxPriceMultLabel.setAlignment(Alignment.RIGHT);
        maxPriceMultTextBox = new TextBox();
        maxPriceMultTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);

        addChild(scaleLabel);
        addChild(scaleTextBox);
        addChild(flowEnabledCheckBox);
        addChild(flowSensitivityLabel);
        addChild(flowSensitivityTextBox);
        addChild(minPriceMultLabel);
        addChild(minPriceMultTextBox);
        addChild(maxPriceMultLabel);
        addChild(maxPriceMultTextBox);
        addChild(applyButton);

        // 6 rows of controls (scale, flow toggle, sensitivity, min mult, max mult, apply)
        setHeight(6 * (defaultElementHeight + spacing) + padding);
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
            populateFrom(allSettings.get(active));
        }
    }

    @Override
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        if (marketID != null && allSettings.containsKey(marketID)) {
            populateFrom(allSettings.get(marketID));
        }
    }

    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable VolatilityPlugin.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) {
            allSettings.put(marketID, confirmedSettings);
        }
    }

    /**
     * Fills all input controls from the given settings.
     * @param settings the settings to display
     */
    private void populateFrom(VolatilityPlugin.Settings settings) {
        scaleTextBox.setText(settings.volatilityScale());
        flowEnabledCheckBox.setChecked(settings.flowInfluenceEnabled());
        flowSensitivityTextBox.setText(settings.flowSensitivity());
        minPriceMultTextBox.setText(settings.minPriceMult());
        maxPriceMultTextBox.setText(settings.maxPriceMult());
    }

    private void onApply() {
        ItemID market = getActiveMarket();
        if (market == null) return;
        sendCustomSettings(market, new VolatilityPlugin.Settings(
                (float) scaleTextBox.getDouble(),
                flowEnabledCheckBox.isChecked(),
                (float) flowSensitivityTextBox.getDouble(),
                (float) minPriceMultTextBox.getDouble(),
                (float) maxPriceMultTextBox.getDouble()));
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int eh = defaultElementHeight;
        int labelW = w / 3;
        int padding = PluginGuiElement.padding;
        int textBoxX = labelW + spacing;
        int textBoxW = w - labelW - spacing;

        // Row 1: volatility scale label + textbox
        scaleLabel.setBounds(padding, padding, labelW, eh);
        scaleTextBox.setBounds(textBoxX, scaleLabel.getTop(), textBoxW, eh);

        // Row 2: flow influence toggle (full width)
        flowEnabledCheckBox.setBounds(padding, scaleTextBox.getBottom() + spacing, w - 2 * padding, eh);

        // Row 3: flow sensitivity label + textbox
        flowSensitivityLabel.setBounds(padding, flowEnabledCheckBox.getBottom() + spacing, labelW, eh);
        flowSensitivityTextBox.setBounds(textBoxX, flowSensitivityLabel.getTop(), textBoxW, eh);

        // Row 4: min price multiplier label + textbox
        minPriceMultLabel.setBounds(padding, flowSensitivityTextBox.getBottom() + spacing, labelW, eh);
        minPriceMultTextBox.setBounds(textBoxX, minPriceMultLabel.getTop(), textBoxW, eh);

        // Row 5: max price multiplier label + textbox
        maxPriceMultLabel.setBounds(padding, minPriceMultTextBox.getBottom() + spacing, labelW, eh);
        maxPriceMultTextBox.setBounds(textBoxX, maxPriceMultLabel.getTop(), textBoxW, eh);

        // Row 6: apply button
        applyButton.setBounds(padding, maxPriceMultTextBox.getBottom() + spacing, w - 2 * padding, eh);
    }

    @Override
    protected void render() {}
}
