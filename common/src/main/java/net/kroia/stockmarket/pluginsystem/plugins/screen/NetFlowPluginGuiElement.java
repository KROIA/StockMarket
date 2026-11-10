package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.NetFlowPlugin;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T-213: inline settings GUI for the {@link NetFlowPlugin}: one row per setting (toggles as check boxes,
 * numbers as label + text box), a read-only line with the active market's current anchor (streamed), plus
 * Apply / Apply to all / Defaults.
 */
public class NetFlowPluginGuiElement extends PluginGuiElement<NetFlowPlugin.Settings, NetFlowPlugin.RuntimeStreamData> {

    private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".net_flow_plugin.";

    /** @return the translated text of {@code PREFIX + key} */
    private static Component text(String key) {
        return Component.translatable(PREFIX + key);
    }

    private final CheckBox flowEnabled;
    private final TextBox flowSensitivity;
    private final TextBox minPriceMult;
    private final TextBox maxPriceMult;
    private final TextBox permanentImpact;
    private final TextBox capacityVolatilityScale;
    private final CheckBox thetaCapEnabled;
    private final CheckBox saturationEnabled;
    private final CheckBox anchorEnabled;
    private final TextBox anchorTimeDays;
    private final TextBox anchorMaxMovePerDay;
    private final TextBox anchorMinMult;
    private final TextBox anchorMaxMult;
    /** Read-only: the active market's current anchor as a multiple of the default price. */
    private final Label anchorValue;
    /** Last streamed anchors (null until the first packet). */
    private @Nullable NetFlowPlugin.RuntimeStreamData runtimeData;
    private final Button applyButton;
    private final Button applyAllButton;
    /** Fills the form with the plugin defaults (null = hidden). */
    private final @Nullable Button defaultsButton;
    /** Layout rows top to bottom: a check box alone or a label + text box pair. */
    private final List<GuiElement[]> rows = new ArrayList<>();
    private Map<ItemID, NetFlowPlugin.Settings> allSettings = new HashMap<>();

    public NetFlowPluginGuiElement() {
        flowEnabled = checkRow("flow_enabled");
        flowSensitivity = numberRow("flow_sensitivity", 10, 6);
        minPriceMult = numberRow("min_price_mult", 10, 6);
        maxPriceMult = numberRow("max_price_mult", 10, 6);
        permanentImpact = numberRow("permanent_impact", 2, 3);
        capacityVolatilityScale = numberRow("capacity_volatility_scale", 10, 6);
        thetaCapEnabled = checkRow("theta_cap_enabled");
        saturationEnabled = checkRow("saturation_enabled");
        anchorEnabled = checkRow("anchor_enabled");
        anchorTimeDays = numberRow("anchor_time_days", 6, 3);
        anchorMaxMovePerDay = numberRow("anchor_max_move_per_day", 6, 4);
        anchorMinMult = numberRow("anchor_min_mult", 6, 4);
        anchorMaxMult = numberRow("anchor_max_mult", 6, 4);
        anchorValue = new Label("");
        setSettingTooltip(text("anchor_value.tooltip"), anchorValue);
        addChild(anchorValue);
        rows.add(new GuiElement[]{anchorValue});
        updateAnchorLabel();

        applyButton = new Button(text("apply").getString(), this::onApply);
        applyAllButton = new Button(ApplyAllTexts.APPLY_ALL.getString(), () -> confirmApplyToAllMarkets(readSettings()));
        setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAllButton);
        defaultsButton = createDefaultsButton();
        addChild(applyButton);
        addChild(applyAllButton);
        if (defaultsButton != null) addChild(defaultsButton);

        // one row per setting plus the button row
        setHeight((rows.size() + 1) * (defaultElementHeight + spacing) + padding);
    }

    /** Adds a check box row with label and tooltip {@code key} / {@code key.tooltip}. */
    private CheckBox checkRow(String key) {
        CheckBox box = new CheckBox(text(key).getString());
        setSettingTooltip(text(key + ".tooltip"), box);
        addChild(box);
        rows.add(new GuiElement[]{box});
        return box;
    }

    /** Adds a label + numeric text box row ({@code digits} before, {@code decimals} after the point). */
    private TextBox numberRow(String key, int digits, int decimals) {
        Label label = new Label(text(key).getString());
        label.setAlignment(Alignment.RIGHT);
        TextBox box = new TextBox();
        box.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, digits, decimals));
        setSettingTooltip(text(key + ".tooltip"), label, box);
        addChild(label);
        addChild(box);
        rows.add(new GuiElement[]{label, box});
        return box;
    }

    @Override
    protected StreamCodec<ByteBuf, NetFlowPlugin.Settings> customSettingsCodec() {
        return NetFlowPlugin.Settings.CODEC;
    }

    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, NetFlowPlugin.Settings> customSettingsMap) {
        if (customSettingsMap != null) allSettings = new HashMap<>(customSettingsMap);
        ItemID active = getActiveMarket();
        if (active != null && allSettings.containsKey(active)) populateFrom(allSettings.get(active));
        startDataStream();
    }

    @Override
    protected StreamCodec<ByteBuf, NetFlowPlugin.RuntimeStreamData> runtimeDataCodec() {
        return NetFlowPlugin.RuntimeStreamData.CODEC;
    }

    @Override
    protected void onRuntimeDataReceived(NetFlowPlugin.RuntimeStreamData data) {
        runtimeData = data;
        updateAnchorLabel();
    }

    /** Shows the active market's streamed anchor ("-" while unknown). */
    private void updateAnchorLabel() {
        ItemID active = getActiveMarket();
        double mult = runtimeData != null && active != null ? runtimeData.anchorOf(active.getShort()) : Double.NaN;
        anchorValue.setText(Component.translatable(PREFIX + "anchor_value",
                Double.isNaN(mult) ? "-" : String.format(java.util.Locale.ROOT, "%.4f", mult)).getString());
    }

    @Override
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        if (marketID != null && allSettings.containsKey(marketID)) populateFrom(allSettings.get(marketID));
        updateAnchorLabel();
    }

    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable NetFlowPlugin.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) allSettings.put(marketID, confirmedSettings);
    }

    @Override
    protected NetFlowPlugin.Settings defaultSettings() {
        return NetFlowPlugin.Settings.createDefault();
    }

    @Override
    protected void showSettings(NetFlowPlugin.Settings settings) {
        populateFrom(settings);
    }

    /** Fills all input controls from the given settings. */
    private void populateFrom(NetFlowPlugin.Settings s) {
        flowEnabled.setChecked(s.flowEnabled());
        flowSensitivity.setText(s.flowSensitivity(), 6);
        minPriceMult.setText(s.minPriceMult(), 6);
        maxPriceMult.setText(s.maxPriceMult(), 6);
        permanentImpact.setText(s.permanentImpact(), 3);
        capacityVolatilityScale.setText(s.capacityVolatilityScale(), 6);
        thetaCapEnabled.setChecked(s.thetaCapEnabled());
        saturationEnabled.setChecked(s.saturationEnabled());
        anchorEnabled.setChecked(s.anchorEnabled());
        anchorTimeDays.setText(s.anchorTimeDays(), 3);
        anchorMaxMovePerDay.setText(s.anchorMaxMovePerDay(), 4);
        anchorMinMult.setText(s.anchorMinMult(), 4);
        anchorMaxMult.setText(s.anchorMaxMult(), 4);
    }

    /** Builds the settings from the current input values. */
    private NetFlowPlugin.Settings readSettings() {
        return new NetFlowPlugin.Settings(
                flowEnabled.isChecked(),
                (float) flowSensitivity.getDouble(),
                (float) minPriceMult.getDouble(),
                (float) maxPriceMult.getDouble(),
                (float) permanentImpact.getDouble(),
                (float) capacityVolatilityScale.getDouble(),
                thetaCapEnabled.isChecked(),
                saturationEnabled.isChecked(),
                anchorEnabled.isChecked(),
                (float) anchorTimeDays.getDouble(),
                (float) anchorMaxMovePerDay.getDouble(),
                (float) anchorMinMult.getDouble(),
                (float) anchorMaxMult.getDouble());
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
        int padding = PluginGuiElement.padding;
        int labelW = w / 3;
        int textBoxX = labelW + spacing;
        int textBoxW = w - labelW - spacing;
        int y = padding;
        for (GuiElement[] row : rows) {
            if (row.length == 1) row[0].setBounds(padding, y, w - 2 * padding, eh);
            else {
                row[0].setBounds(padding, y, labelW, eh);
                row[1].setBounds(textBoxX, y, textBoxW, eh);
            }
            y += eh + spacing;
        }
        int buttons = defaultsButton != null ? 3 : 2;
        int buttonW = (w - 2 * padding - (buttons - 1) * spacing) / buttons;
        applyButton.setBounds(padding, y, buttonW, eh);
        applyAllButton.setBounds(applyButton.getRight() + spacing, y, buttonW, eh);
        if (defaultsButton != null) defaultsButton.setBounds(applyAllButton.getRight() + spacing, y, buttonW, eh);
    }

    @Override
    protected void render() {}
}
