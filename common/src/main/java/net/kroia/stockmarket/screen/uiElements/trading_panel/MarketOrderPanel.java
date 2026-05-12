package net.kroia.stockmarket.screen.uiElements.trading_panel;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

class MarketOrderPanel extends StockMarketGuiElement
{
    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".market_order_panel.";
        private static final Component QUANTITY = Component.translatable(PREFIX +"quantity");
        private static final Component BUY = Component.translatable(PREFIX +"buy");
        private static final Component SELL = Component.translatable(PREFIX +"sell");
    }

    private final TextBox quantityTextBox;
    private final Label quantityLabel;
    private final Label itemNameLabel;

    private final Button add10Button;
    private final Button add32Button;
    private final Button add64Button;
    private final Button add128Button;
    private final Button remove10Button;
    private final Button remove32Button;
    private final Button remove64Button;
    private final Button remove128Button;

    private final Label estimatedCostLabel;

    //private final HorizontalSlider quantitySlider;

    private final Button buyButton;
    private final Button sellButton;

    //private IAsyncBank itemBank;
    // private IAsyncBank moneyBank;
    private double quantity = 0;
    private double currentMarketPrice = 0;
    private double moneyBalance = 0;
    private double itemBalance = 0;
    private String currencyName = "";

    public MarketOrderPanel(Consumer<Double> onBuy, Consumer<Double> onSell)
    {
        //this.itemBank = itemBank;
        //this.moneyBank = moneyBank;
        int darkerBuyColor1 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.8f);
        int darkerBuyColor2 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.7f);
        int darkerBuyColor3 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.6f);
        int darkerBuyColor4 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.5f);

        int darkerSellColor1 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.8f);
        int darkerSellColor2 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.7f);
        int darkerSellColor3 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.6f);
        int darkerSellColor4 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.5f);

        quantityTextBox =  new TextBox();
        quantityTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 100,getBankManager().getItemFractionDigitsCount()));
        quantityTextBox.setAlignment(Alignment.RIGHT);
        quantityTextBox.setOnTextChanged((text)->{
            double newQuantity = quantityTextBox.getDouble();
            quantity = Math.max(0, newQuantity);
            updateButtons();
            updateEstimatedCost();
            updateValidation();
        });

        quantityLabel =  new Label(Texts.QUANTITY.getString());
        quantityLabel.setTextFontScale(0.8f);

        itemNameLabel = new Label();
        itemNameLabel.setTextFontScale(0.8f);
        itemNameLabel.setAlignment(Alignment.RIGHT);

        add10Button = new Button("+10", () -> addQuantity(10.0));
        add32Button = new Button("+32", () -> addQuantity(32.0));
        add64Button = new Button("+64", () -> addQuantity(64.0));
        add128Button = new Button("+128", () -> addQuantity(128.0));
        add10Button.setOutlineColor(darkerBuyColor1);
        add32Button.setOutlineColor(darkerBuyColor1);
        add64Button.setOutlineColor(darkerBuyColor1);
        add128Button.setOutlineColor(darkerBuyColor1);
        add10Button.setTextFontScale(1.3f);
        add32Button.setTextFontScale(1.3f);
        add64Button.setTextFontScale(1.3f);
        add128Button.setTextFontScale(1.3f);
        //add10Button.setIdleColor(ColorUtilities.setBrightness(DEFAULT_BACKGROUND_COLOR, 0.7f));
        //add32Button.setIdleColor(ColorUtilities.setBrightness(DEFAULT_BACKGROUND_COLOR, 0.7f));
        // add64Button.setIdleColor(ColorUtilities.setBrightness(DEFAULT_BACKGROUND_COLOR, 0.7f));
        // add128Button.setIdleColor(ColorUtilities.setBrightness(DEFAULT_BACKGROUND_COLOR, 0.7f));

        remove10Button = new Button("-10", () -> addQuantity(-10.0));
        remove32Button = new Button("-32", () -> addQuantity(-32.0));
        remove64Button = new Button("-64", () -> addQuantity(-64.0));
        remove128Button = new Button("-128", () -> addQuantity(-128.0));
        remove10Button.setOutlineColor(darkerSellColor1);
        remove32Button.setOutlineColor(darkerSellColor1);
        remove64Button.setOutlineColor(darkerSellColor1);
        remove128Button.setOutlineColor(darkerSellColor1);
        remove10Button.setTextFontScale(1.3f);
        remove32Button.setTextFontScale(1.3f);
        remove64Button.setTextFontScale(1.3f);
        remove128Button.setTextFontScale(1.3f);

        //quantitySlider = new HorizontalSlider();
        //quantitySlider.setSliderValue(0);

        // Estimated cost label displayed between quantity buttons and buy/sell buttons
        estimatedCostLabel = new Label("");
        estimatedCostLabel.setTextFontScale(0.8f);
        estimatedCostLabel.setAlignment(Alignment.CENTER);

        buyButton = new Button(Texts.BUY.getString(), () -> onBuy.accept(quantity));
        buyButton.setBackgroundColor(darkerBuyColor1);
        buyButton.setHoverColor(darkerBuyColor2);
        buyButton.setPressedColor(darkerBuyColor3);
        buyButton.setOutlineColor(darkerBuyColor4);
        sellButton = new Button(Texts.SELL.getString(), () -> onSell.accept(quantity));
        sellButton.setBackgroundColor(darkerSellColor1);
        sellButton.setHoverColor(darkerSellColor2);
        sellButton.setPressedColor(darkerSellColor3);
        sellButton.setOutlineColor(darkerSellColor4);

        addChild(quantityTextBox);
        addChild(quantityLabel);
        addChild(itemNameLabel);
        addChild(add10Button);
        addChild(add32Button);
        addChild(add64Button);
        addChild(add128Button);
        addChild(remove10Button);
        addChild(remove32Button);
        addChild(remove64Button);
        addChild(remove128Button);
        //addChild(quantitySlider);
        addChild(estimatedCostLabel);
        addChild(buyButton);
        addChild(sellButton);

        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedCost();
        updateValidation();
    }
    public void setItemName(String itemName)
    {
        itemNameLabel.setText(itemName);
    }
    public void setQuantity(double quantity)
    {
        quantityTextBox.setText(String.valueOf(quantity));
        this.quantity =  quantity;
        updateButtons();
    }
    public double getQuantity()
    {
        return quantity;
    }
    public void setCurrentMarketPrice(double price)
    {
        this.currentMarketPrice = price;
        updateEstimatedCost();
        updateValidation();
    }
    public void setMoneyBalance(double balance)
    {
        this.moneyBalance = balance;
        updateEstimatedCost();
        updateValidation();
    }
    public void setItemBalance(double balance)
    {
        this.itemBalance = balance;
        updateEstimatedCost();
        updateValidation();
    }
    public void setCurrencyName(String name)
    {
        this.currencyName = name;
        updateEstimatedCost();
    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = StockMarketGuiElement.padding;
        int spacing =  StockMarketGuiElement.spacing;
        int width = getWidth() - padding*2;
        int height = getHeight() - padding*2;


        quantityLabel.setBounds(padding, padding, width/2, StockMarketGuiElement.defaultElementHeight/2);
        itemNameLabel.setBounds(quantityLabel.getRight(), quantityLabel.getTop(), width/2, quantityLabel.getHeight());
        quantityTextBox.setBounds(padding, quantityLabel.getBottom(), width, StockMarketGuiElement.defaultElementHeight);

        int buttonWidth = (width-3*spacing)/4;
        add10Button.setBounds(padding, quantityTextBox.getBottom()+spacing, buttonWidth, defaultElementHeight);
        add32Button.setBounds(add10Button.getRight()+spacing, add10Button.getTop(), buttonWidth, add10Button.getHeight());
        add64Button.setBounds(add32Button.getRight()+spacing, add32Button.getTop(), buttonWidth, add32Button.getHeight());
        add128Button.setBounds(add64Button.getRight()+spacing, add64Button.getTop(), buttonWidth, add64Button.getHeight());

        remove10Button.setBounds(add10Button.getLeft(), add10Button.getBottom()+spacing, buttonWidth, defaultElementHeight);
        remove32Button.setBounds(remove10Button.getRight()+spacing, remove10Button.getTop(), buttonWidth, remove10Button.getHeight());
        remove64Button.setBounds(remove32Button.getRight()+spacing, remove32Button.getTop(), buttonWidth, remove32Button.getHeight());
        remove128Button.setBounds(remove64Button.getRight()+spacing, remove64Button.getTop(), buttonWidth, remove64Button.getHeight());

        // Estimated cost label between remove buttons and buy/sell buttons
        estimatedCostLabel.setBounds(padding, remove10Button.getBottom()+spacing, width, StockMarketGuiElement.defaultElementHeight/2);

        buyButton.setBounds(padding, height-(StockMarketGuiElement.defaultElementHeight-padding), (width-spacing)/2, StockMarketGuiElement.defaultElementHeight);
        sellButton.setBounds(buyButton.getRight()+spacing, buyButton.getTop(), buyButton.getWidth(), buyButton.getHeight());


    }
    private void addQuantity(double amount)
    {
        quantity += amount;
        if(quantity < 0)
            quantity = 0;
        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedCost();
        updateValidation();
    }
    private void updateButtons()
    {
        remove10Button.setEnabled(quantity >= 10);
        remove32Button.setEnabled(quantity >= 32);
        remove64Button.setEnabled(quantity >= 64);
        remove128Button.setEnabled(quantity >= 128);
    }

    /**
     * Updates the estimated cost label based on current quantity and market price.
     * Note: for market orders this is approximate since actual execution price may differ.
     * Colors the text red if the player cannot afford the trade.
     */
    private void updateEstimatedCost()
    {
        double cost = quantity * currentMarketPrice;
        String costText = String.format("Est: %.2f %s", cost, currencyName);
        estimatedCostLabel.setText(costText);

        // Color red only when the displayed money cost exceeds the player's balance
        // (sell-side validation is handled separately by updateValidation disabling the sell button)
        boolean buyInsufficient = cost > moneyBalance && moneyBalance > 0;
        if(buyInsufficient && quantity > 0 && currentMarketPrice > 0)
            estimatedCostLabel.setTextColor(UI_Colors.sellColorRed);
        else
            estimatedCostLabel.setTextColor(GuiElement.DEFAULT_TEXT_COLOR);
    }

    /**
     * Enables or disables the buy/sell buttons based on balance validation.
     */
    private void updateValidation()
    {
        double cost = quantity * currentMarketPrice;
        buyButton.setEnabled(quantity > 0 && cost <= moneyBalance);
        sellButton.setEnabled(quantity > 0 && quantity <= itemBalance);
    }
}
