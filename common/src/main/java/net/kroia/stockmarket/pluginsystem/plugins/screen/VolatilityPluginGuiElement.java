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
 * (flow sensitivity, min/max price multipliers, permanent impact θ)
 * plus an Apply button.
 */
public class VolatilityPluginGuiElement extends PluginGuiElement<VolatilityPlugin.Settings, Void> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volatility_plugin.";
        public static final Component SCALE_LABEL = Component.translatable(PREFIX + "scale");
        public static final Component FLOW_ENABLED_LABEL = Component.translatable(PREFIX + "flow_enabled");
        public static final Component FLOW_SENSITIVITY_LABEL = Component.translatable(PREFIX + "flow_sensitivity");
        public static final Component MIN_PRICE_MULT_LABEL = Component.translatable(PREFIX + "min_price_mult");
        public static final Component MAX_PRICE_MULT_LABEL = Component.translatable(PREFIX + "max_price_mult");
        public static final Component PERMANENT_IMPACT_LABEL = Component.translatable(PREFIX + "permanent_impact");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
        public static final Component SCALE_TOOLTIP = Component.translatable(PREFIX + "scale.tooltip");
        public static final Component FLOW_ENABLED_TOOLTIP = Component.translatable(PREFIX + "flow_enabled.tooltip");
        public static final Component FLOW_SENSITIVITY_TOOLTIP = Component.translatable(PREFIX + "flow_sensitivity.tooltip");
        public static final Component MIN_PRICE_MULT_TOOLTIP = Component.translatable(PREFIX + "min_price_mult.tooltip");
        public static final Component MAX_PRICE_MULT_TOOLTIP = Component.translatable(PREFIX + "max_price_mult.tooltip");
        public static final Component PERMANENT_IMPACT_TOOLTIP = Component.translatable(PREFIX + "permanent_impact.tooltip");
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
    /** T-208: permanent impact θ in (0, 1]. */
    private final Label permanentImpactLabel;
    private final TextBox permanentImpactTextBox;
    private final Button applyButton;
    private final Button applyAllButton;
    /** Fills the form with the plugin defaults (null = hidden). */
    private final @Nullable Button defaultsButton;
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

        permanentImpactLabel = new Label(Texts.PERMANENT_IMPACT_LABEL.getString());
        permanentImpactLabel.setAlignment(Alignment.RIGHT);
        permanentImpactTextBox = new TextBox();
        permanentImpactTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 2, 3));

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);
        applyAllButton = new Button(ApplyAllTexts.APPLY_ALL.getString(), () -> confirmApplyToAllMarkets(readSettings()));
        setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAllButton);
        defaultsButton = createDefaultsButton();

        setSettingTooltip(Texts.SCALE_TOOLTIP, scaleLabel, scaleTextBox);
        setSettingTooltip(Texts.FLOW_ENABLED_TOOLTIP, flowEnabledCheckBox);
        setSettingTooltip(Texts.FLOW_SENSITIVITY_TOOLTIP, flowSensitivityLabel, flowSensitivityTextBox);
        setSettingTooltip(Texts.MIN_PRICE_MULT_TOOLTIP, minPriceMultLabel, minPriceMultTextBox);
        setSettingTooltip(Texts.MAX_PRICE_MULT_TOOLTIP, maxPriceMultLabel, maxPriceMultTextBox);
        setSettingTooltip(Texts.PERMANENT_IMPACT_TOOLTIP, permanentImpactLabel, permanentImpactTextBox);

        addChild(scaleLabel);
        addChild(scaleTextBox);
        addChild(flowEnabledCheckBox);
        addChild(flowSensitivityLabel);
        addChild(flowSensitivityTextBox);
        addChild(minPriceMultLabel);
        addChild(minPriceMultTextBox);
        addChild(maxPriceMultLabel);
        addChild(maxPriceMultTextBox);
        addChild(permanentImpactLabel);
        addChild(permanentImpactTextBox);
        addChild(applyButton);
        addChild(applyAllButton);
        if (defaultsButton != null) addChild(defaultsButton);

        // 7 rows of controls (scale, flow toggle, sensitivity, min mult, max mult,
        // permanent impact, apply + apply-to-all + defaults)
        setHeight(7 * (defaultElementHeight + spacing) + padding);
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

    @Override
    protected VolatilityPlugin.Settings defaultSettings() {
        return VolatilityPlugin.Settings.createDefault();
    }

    @Override
    protected void showSettings(VolatilityPlugin.Settings settings) {
        populateFrom(settings);
    }

    /**
     * Fills all input controls from the given settings.
     * @param settings the settings to display
     */
    private void populateFrom(VolatilityPlugin.Settings settings) {
        scaleTextBox.setText(settings.volatilityScale(), 6);
        flowEnabledCheckBox.setChecked(settings.flowInfluenceEnabled());
        flowSensitivityTextBox.setText(settings.flowSensitivity(), 6);
        minPriceMultTextBox.setText(settings.minPriceMult(), 6);
        maxPriceMultTextBox.setText(settings.maxPriceMult(), 6);
        permanentImpactTextBox.setText(settings.permanentImpact(), 3);
    }

    /** Builds the settings from the current input values. */
    private VolatilityPlugin.Settings readSettings() {
        return new VolatilityPlugin.Settings(
                (float) scaleTextBox.getDouble(),
                flowEnabledCheckBox.isChecked(),
                (float) flowSensitivityTextBox.getDouble(),
                (float) minPriceMultTextBox.getDouble(),
                (float) maxPriceMultTextBox.getDouble(),
                (float) permanentImpactTextBox.getDouble());
    }

    private void onApply() {
        ItemID market = getActiveMarket();
        if (market == null) return;
        sendCustomSettings(market, readSettings());
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

        // Row 6: permanent impact θ (T-208)
        permanentImpactLabel.setBounds(padding, maxPriceMultTextBox.getBottom() + spacing, labelW, eh);
        permanentImpactTextBox.setBounds(textBoxX, permanentImpactLabel.getTop(), textBoxW, eh);

        // Row 7: apply + apply-to-all (+ defaults) buttons side by side
        int buttons = defaultsButton != null ? 3 : 2;
        int buttonW = (w - 2 * padding - (buttons - 1) * spacing) / buttons;
        applyButton.setBounds(padding, permanentImpactTextBox.getBottom() + spacing, buttonW, eh);
        applyAllButton.setBounds(applyButton.getRight() + spacing, applyButton.getTop(), buttonW, eh);
        if (defaultsButton != null) defaultsButton.setBounds(applyAllButton.getRight() + spacing, applyButton.getTop(), buttonW, eh);
    }

    @Override
    protected void render() {}
}
