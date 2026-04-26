package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public class MarketSettingsWidget extends StockMarketGuiElement {

    private static final int elementHeight = 20;

    private final ClientMarket market;
    private final MarketSettings settings;

    private final CheckBox marketOpenCheckBox;
    private final TextBox defaultPriceTextBox;


    public MarketSettingsWidget(ClientMarket market)
    {
        this.market = market;
        this.settings = new MarketSettings();

        marketOpenCheckBox = new CheckBox("Market Open");
        marketOpenCheckBox.setChecked(settings.marketOpen);

        defaultPriceTextBox =  new TextBox();
        defaultPriceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false,
                (int)Math.log10((double)(Long.MAX_VALUE/getBankManager().getItemFractionScaleFactor())),
                (int)Math.log10(getBankManager().getItemFractionScaleFactor())));
        //defaultPriceTextBox.setText(getBankManager().convertToRealAmount(settings.defaultPrice));

        addChild(defaultPriceTextBox);
        addChild(marketOpenCheckBox);

        setHeight(elementHeight*getChilds().size()+2*padding);
        loadSettings();
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int width = getWidth()-2*padding;


        marketOpenCheckBox.setBounds(padding, padding, width, elementHeight);
        defaultPriceTextBox.setBounds(padding, marketOpenCheckBox.getBottom(), width, elementHeight);
    }

    public void loadSettings()
    {
        market.getSettings().thenAccept(this::setSettings);
    }
    public void saveSettings()
    {
        market.setSettings(settings);
    }
    public void setSettings(MarketSettings marketSettings)
    {
        settings.marketOpen = marketSettings.marketOpen;
        settings.defaultPrice = marketSettings.defaultPrice;
        marketOpenCheckBox.setChecked(settings.marketOpen);
        defaultPriceTextBox.setText(getBankManager().convertToRealAmount(settings.defaultPrice));
    }
    public MarketSettings getMarketSettings() {
        settings.marketOpen = marketOpenCheckBox.isChecked();
        settings.defaultPrice = getBankManager().convertToRawAmount(defaultPriceTextBox.getDouble());

        return settings;
    }
}
