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

    private static final int elementHeight = 20;

    private final ClientMarket market;
    private final MarketSettings settings;
    private boolean loading = false;

    private final CheckBox marketOpenCheckBox;
    private final Label defaultPriceLabel;
    private final TextBox defaultPriceTextBox;
    private final Label abundanceLabel;
    private final TextBox abundanceTextBox;
    private final Label netFlowLabel;
    private final Label netFlowValueLabel;
    private final Button resetNetFlowButton;
    private final Button applyButton;

    public MarketSettingsWidget(ClientMarket market)
    {
        this.market = market;
        this.settings = new MarketSettings();

        marketOpenCheckBox = new CheckBox(MARKET_OPEN_TEXT.getString());
        marketOpenCheckBox.setChecked(settings.marketOpen);

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

        applyButton = new Button("Apply", this::saveSettings);

        addChild(marketOpenCheckBox);
        addChild(defaultPriceLabel);
        addChild(defaultPriceTextBox);
        addChild(abundanceLabel);
        addChild(abundanceTextBox);
        addChild(netFlowLabel);
        addChild(netFlowValueLabel);
        addChild(resetNetFlowButton);
        addChild(applyButton);

        setHeight(5 * elementHeight + 6 * padding);
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

        defaultPriceLabel.setBounds(padding, marketOpenCheckBox.getBottom() + padding, labelWidth, elementHeight);
        defaultPriceTextBox.setBounds(defaultPriceLabel.getRight() + spacing, defaultPriceLabel.getTop(), fieldWidth, elementHeight);

        abundanceLabel.setBounds(padding, defaultPriceLabel.getBottom() + padding, labelWidth, elementHeight);
        abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, abundanceLabel.getTop(), fieldWidth, elementHeight);

        // Net flow row: label | value label | reset button
        int resetButtonWidth = 40;
        int valueWidth = fieldWidth - resetButtonWidth - spacing;
        netFlowLabel.setBounds(padding, abundanceLabel.getBottom() + padding, labelWidth, elementHeight);
        netFlowValueLabel.setBounds(netFlowLabel.getRight() + spacing, netFlowLabel.getTop(), valueWidth, elementHeight);
        resetNetFlowButton.setBounds(netFlowValueLabel.getRight() + spacing, netFlowLabel.getTop(), resetButtonWidth, elementHeight);

        applyButton.setBounds(padding, netFlowLabel.getBottom() + padding, width, elementHeight);
    }

    public void loadSettings()
    {
        market.getSettings().thenAccept(this::setSettings);
    }

    public void saveSettings()
    {
        settings.marketOpen = marketOpenCheckBox.isChecked();
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
        marketOpenCheckBox.setChecked(settings.marketOpen);
        defaultPriceTextBox.setText(getBankManager().convertToRealAmount(settings.defaultPrice));
        abundanceTextBox.setText(formatAbundance(settings.naturalAbundance));
        netFlowValueLabel.setText(String.valueOf(getBankManager().convertToRealAmount(settings.netPlayerItemFlow)));
        loading = false;
    }

    public MarketSettings getMarketSettings() {
        settings.marketOpen = marketOpenCheckBox.isChecked();
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
