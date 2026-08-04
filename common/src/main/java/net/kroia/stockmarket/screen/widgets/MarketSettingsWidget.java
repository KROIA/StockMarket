package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class MarketSettingsWidget extends StockMarketGuiElement {

    private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_widget.";
    private static final Component MARKET_OPEN_TEXT = Component.translatable(PREFIX + "market_open");
    private static final Component DEFAULT_PRICE_TEXT = Component.translatable(PREFIX + "default_price");
    private static final Component ABUNDANCE_TEXT = Component.translatable(PREFIX + "abundance");
    private static final Component VIRTUAL_ORDERBOOK_ENABLED_TEXT = Component.translatable(PREFIX + "virtual_orderbook_enabled");
    private static final Component VIRTUAL_ORDERBOOK_ENABLED_TOOLTIP = Component.translatable(PREFIX + "virtual_orderbook_enabled.tooltip");
    private static final Component RESET_VIRTUAL_ORDERBOOK_TEXT = Component.translatable(PREFIX + "reset_virtual_orderbook");
    private static final Component RESET_VIRTUAL_ORDERBOOK_TOOLTIP = Component.translatable(PREFIX + "reset_virtual_orderbook.tooltip");
    private static final Component CLEAR_VIRTUAL_ORDERBOOK_TEXT = Component.translatable(PREFIX + "clear_virtual_orderbook");
    private static final Component CLEAR_VIRTUAL_ORDERBOOK_TOOLTIP = Component.translatable(PREFIX + "clear_virtual_orderbook.tooltip");

    private static final int elementHeight = 20;

    private final ClientMarket market;
    private final MarketSettings settings;
    private boolean loading = false;

    private final CheckBox marketOpenCheckBox;
    private final CheckBox virtualOrderbookEnabledCheckBox;
    private final Label defaultPriceLabel;
    private final TextBox defaultPriceTextBox;
    private final Label abundanceLabel;
    private final TextBox abundanceTextBox;
    private final Label netFlowLabel;
    private final Label netFlowValueLabel;
    private final Button resetNetFlowButton;
    private final Button resetVirtualOrderbookButton;
    private final Button clearVirtualOrderbookButton;
    private final Button applyButton;

    public MarketSettingsWidget(ClientMarket market)
    {
        this.market = market;
        this.settings = new MarketSettings();

        marketOpenCheckBox = new CheckBox(MARKET_OPEN_TEXT.getString());
        marketOpenCheckBox.setChecked(settings.marketOpen);

        virtualOrderbookEnabledCheckBox = new CheckBox(VIRTUAL_ORDERBOOK_ENABLED_TEXT.getString(),
                this::onVirtualOrderbookEnabledChanged);
        // NOTE: initial setChecked is deferred to after clear/reset buttons are constructed,
        // because the listener dereferences them.
        virtualOrderbookEnabledCheckBox.setHoverTooltipSupplier(VIRTUAL_ORDERBOOK_ENABLED_TOOLTIP::getString);
        virtualOrderbookEnabledCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        defaultPriceLabel = new Label(DEFAULT_PRICE_TEXT.getString());
        defaultPriceLabel.setAlignment(Label.Alignment.RIGHT);

        defaultPriceTextBox = new TextBox();
        defaultPriceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false,
                (int)Math.log10((double)(Long.MAX_VALUE/getBankManager().getItemFractionScaleFactor())),
                (int)Math.log10(getBankManager().getItemFractionScaleFactor())));

        abundanceLabel = new Label(ABUNDANCE_TEXT.getString());
        abundanceLabel.setAlignment(Label.Alignment.RIGHT);

        abundanceTextBox = new TextBox();
        abundanceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));

        netFlowLabel = new Label("Net Flow:");
        netFlowLabel.setAlignment(Label.Alignment.RIGHT);

        netFlowValueLabel = new Label("0");
        netFlowValueLabel.setAlignment(Label.Alignment.LEFT);

        resetNetFlowButton = new Button("Reset", () -> {
            market.resetNetPlayerItemFlow().thenAccept(success -> {
                if (success) loadSettings();
            });
        });

        // Reset button for the virtual orderbook — fires an admin-gated request that
        // clears the bot-generated depth on the master. On success we reload settings
        // so any related fields the server may have adjusted are refreshed.
        resetVirtualOrderbookButton = new Button(RESET_VIRTUAL_ORDERBOOK_TEXT.getString(), () -> {
            market.resetVirtualOrderbook().thenAccept(success -> {
                if (success) loadSettings();
            });
        });
        resetVirtualOrderbookButton.setHoverTooltipSupplier(RESET_VIRTUAL_ORDERBOOK_TOOLTIP::getString);
        resetVirtualOrderbookButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        // Sticky-clear button — parallel to reset but goes through the distinct
        // ClearVirtualOrderbook FunctionType. Both live server-side in ServerMarket;
        // clear zeros + sets the sticky-clear flag; reset refills + drops it.
        clearVirtualOrderbookButton = new Button(CLEAR_VIRTUAL_ORDERBOOK_TEXT.getString(), () -> {
            market.clearVirtualOrderbook().thenAccept(success -> {
                if (success) loadSettings();
            });
        });
        clearVirtualOrderbookButton.setHoverTooltipSupplier(CLEAR_VIRTUAL_ORDERBOOK_TOOLTIP::getString);
        clearVirtualOrderbookButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        applyButton = new Button("Apply", this::saveSettings);

        // Now that clear/reset buttons exist, safe to fire the listener via setChecked.
        virtualOrderbookEnabledCheckBox.setChecked(settings.virtualOrderbookEnabled);

        addChild(marketOpenCheckBox);
        addChild(virtualOrderbookEnabledCheckBox);
        addChild(defaultPriceLabel);
        addChild(defaultPriceTextBox);
        addChild(abundanceLabel);
        addChild(abundanceTextBox);
        addChild(netFlowLabel);
        addChild(netFlowValueLabel);
        addChild(resetNetFlowButton);
        addChild(resetVirtualOrderbookButton);
        addChild(clearVirtualOrderbookButton);
        addChild(applyButton);

        // One extra row for the virtual-orderbook checkbox + the clear/reset row.
        setHeight(8 * elementHeight + 9 * padding);
        loadSettings();
    }

    @Override
    protected void render() {}

    @Override
    protected void layoutChanged() {
        int width = getWidth() - 2 * padding;
        int labelWidth = width / 3;
        int fieldWidth = width - labelWidth - spacing;

        marketOpenCheckBox.setBounds(padding, padding, width, elementHeight);

        virtualOrderbookEnabledCheckBox.setBounds(padding, marketOpenCheckBox.getBottom() + padding, width, elementHeight);

        defaultPriceLabel.setBounds(padding, virtualOrderbookEnabledCheckBox.getBottom() + padding, labelWidth, elementHeight);
        defaultPriceTextBox.setBounds(defaultPriceLabel.getRight() + spacing, defaultPriceLabel.getTop(), fieldWidth, elementHeight);

        abundanceLabel.setBounds(padding, defaultPriceLabel.getBottom() + padding, labelWidth, elementHeight);
        abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, abundanceLabel.getTop(), fieldWidth, elementHeight);

        // Net flow row: label | value label | reset button
        int resetButtonWidth = 40;
        int valueWidth = fieldWidth - resetButtonWidth - spacing;
        netFlowLabel.setBounds(padding, abundanceLabel.getBottom() + padding, labelWidth, elementHeight);
        netFlowValueLabel.setBounds(netFlowLabel.getRight() + spacing, netFlowLabel.getTop(), valueWidth, elementHeight);
        resetNetFlowButton.setBounds(netFlowValueLabel.getRight() + spacing, netFlowLabel.getTop(), resetButtonWidth, elementHeight);

        // Virtual-orderbook control group: the enable checkbox and the Clear/Reset
        // buttons sit together as a visually cohesive block so the "these buttons act
        // on the same subsystem the checkbox toggles" relationship reads at a glance.
        // Task 3 also gates Clear/Reset enabled state on the checkbox value.
        virtualOrderbookEnabledCheckBox.setBounds(padding, netFlowLabel.getBottom() + padding, width, elementHeight);

        // Clear and Reset side by side directly beneath the checkbox, split 50/50.
        int clearResetLeftW = (width - spacing) / 2;
        int clearResetRightW = width - clearResetLeftW - spacing;
        clearVirtualOrderbookButton.setBounds(padding, virtualOrderbookEnabledCheckBox.getBottom() + padding, clearResetLeftW, elementHeight);
        resetVirtualOrderbookButton.setBounds(clearVirtualOrderbookButton.getRight() + spacing, clearVirtualOrderbookButton.getTop(), clearResetRightW, elementHeight);

        applyButton.setBounds(padding, resetVirtualOrderbookButton.getBottom() + padding, width, elementHeight);
    }

    /**
     * Fires when the "Enable Virtual Orderbook" checkbox toggles. Grays out the Clear
     * and Reset buttons when unchecked — the server-side ops are no-ops for a disabled
     * VO (see ServerMarket.clearVirtualOrderbook/resetVirtualOrderbook), so preventing
     * the click is defense-in-depth.
     */
    private void onVirtualOrderbookEnabledChanged(Boolean enabled) {
        updateVirtualOrderbookButtonEnabled(Boolean.TRUE.equals(enabled));
    }

    private void updateVirtualOrderbookButtonEnabled(boolean enabled) {
        clearVirtualOrderbookButton.setEnabled(enabled);
        resetVirtualOrderbookButton.setEnabled(enabled);
    }

    public void loadSettings()
    {
        market.getSettings().thenAccept(this::setSettings);
    }

    public void saveSettings()
    {
        settings.marketOpen = marketOpenCheckBox.isChecked();
        settings.virtualOrderbookEnabled = virtualOrderbookEnabledCheckBox.isChecked();
        settings.defaultPrice = getBankManager().convertToRawAmount(defaultPriceTextBox.getDouble());
        float abundance = parseAbundance(abundanceTextBox.getText());
        if (abundance > 0)
            settings.naturalAbundance = abundance;
        market.setSettings(settings);
    }

    public void setSettings(MarketSettings marketSettings)
    {
        loading = true;
        settings.marketOpen = marketSettings.marketOpen;
        settings.defaultPrice = marketSettings.defaultPrice;
        settings.naturalAbundance = marketSettings.naturalAbundance;
        settings.netPlayerItemFlow = marketSettings.netPlayerItemFlow;
        settings.virtualOrderbookEnabled = marketSettings.virtualOrderbookEnabled;
        // Round-trip fields that this widget does not surface as controls but must
        // preserve when Apply writes settings back to the server. Otherwise the
        // client would blindly overwrite server-side state with widget defaults.
        settings.virtualOrderbookCleared = marketSettings.virtualOrderbookCleared;
        settings.ignorePluginAutosubscribe = marketSettings.ignorePluginAutosubscribe;
        marketOpenCheckBox.setChecked(settings.marketOpen);
        virtualOrderbookEnabledCheckBox.setChecked(settings.virtualOrderbookEnabled);
        // Sync the Clear/Reset button enabled state with the freshly-loaded checkbox
        // value. Without this, the buttons would remain in their construction-time state
        // (enabled) until the user toggles the checkbox for the first time.
        updateVirtualOrderbookButtonEnabled(settings.virtualOrderbookEnabled);
        defaultPriceTextBox.setText(getBankManager().convertToRealAmount(settings.defaultPrice));
        abundanceTextBox.setText(formatAbundance(settings.naturalAbundance));
        netFlowValueLabel.setText(String.valueOf(getBankManager().convertToRealAmount(settings.netPlayerItemFlow)));
        loading = false;
    }

    public MarketSettings getMarketSettings() {
        settings.marketOpen = marketOpenCheckBox.isChecked();
        settings.virtualOrderbookEnabled = virtualOrderbookEnabledCheckBox.isChecked();
        settings.defaultPrice = getBankManager().convertToRawAmount(defaultPriceTextBox.getDouble());
        float abundance = parseAbundance(abundanceTextBox.getText());
        if (abundance > 0)
            settings.naturalAbundance = abundance;
        return settings;
    }

    private static String formatAbundance(float value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static float parseAbundance(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return -1f;
        }
    }
}
