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
 * Inline settings GUI for the VolatilityPlugin: the volatility scale plus Apply / Apply to all / Defaults.
 * T-213: the flow settings moved to {@link NetFlowPluginGuiElement}.
 */
public class VolatilityPluginGuiElement extends PluginGuiElement<VolatilityPlugin.Settings, Void> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".volatility_plugin.";
        public static final Component SCALE_LABEL = Component.translatable(PREFIX + "scale");
        public static final Component APPLY = Component.translatable(PREFIX + "apply");
        public static final Component SCALE_TOOLTIP = Component.translatable(PREFIX + "scale.tooltip");
    }

    private final Label scaleLabel;
    private final TextBox scaleTextBox;
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

        applyButton = new Button(Texts.APPLY.getString(), this::onApply);
        applyAllButton = new Button(ApplyAllTexts.APPLY_ALL.getString(), () -> confirmApplyToAllMarkets(readSettings()));
        setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAllButton);
        defaultsButton = createDefaultsButton();

        setSettingTooltip(Texts.SCALE_TOOLTIP, scaleLabel, scaleTextBox);

        addChild(scaleLabel);
        addChild(scaleTextBox);
        addChild(applyButton);
        addChild(applyAllButton);
        if (defaultsButton != null) addChild(defaultsButton);

        // 2 rows of controls (scale, apply + apply-to-all + defaults)
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
    }

    /** Builds the settings from the current input values. */
    private VolatilityPlugin.Settings readSettings() {
        return new VolatilityPlugin.Settings((float) scaleTextBox.getDouble());
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

        // Row 2: apply + apply-to-all (+ defaults) buttons side by side
        int buttons = defaultsButton != null ? 3 : 2;
        int buttonW = (w - 2 * padding - (buttons - 1) * spacing) / buttons;
        applyButton.setBounds(padding, scaleTextBox.getBottom() + spacing, buttonW, eh);
        applyAllButton.setBounds(applyButton.getRight() + spacing, applyButton.getTop(), buttonW, eh);
        if (defaultsButton != null) defaultsButton.setBounds(applyAllButton.getRight() + spacing, applyButton.getTop(), buttonW, eh);
    }

    @Override
    protected void render() {}
}
