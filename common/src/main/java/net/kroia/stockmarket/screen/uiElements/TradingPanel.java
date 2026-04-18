package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.api.bank.IAsyncBank;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.util.PrettyPrinter;

import java.util.function.Consumer;

public class TradingPanel extends TabElement {
    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_panel.";
        //private static final Component TITLE = Component.translatable(PREFIX +"title");
        private static final Component QUANTITY = Component.translatable(PREFIX +"quantity");
        private static final Component MARKET_ORDER_TAB_TITLE = Component.translatable(PREFIX +"market_order_tab_title");
        private static final Component LIMIT_ORDER_TAB_TITLE = Component.translatable(PREFIX +"limit_order_tab_title");
        private static final Component BUY = Component.translatable(PREFIX +"buy");
        private static final Component SELL = Component.translatable(PREFIX +"sell");
    }

    private static class MarketOrderPanel extends StockMarketGuiElement
    {

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

        //private final HorizontalSlider quantitySlider;

        private final Button buyButton;
        private final Button sellButton;

        //private IAsyncBank itemBank;
       // private IAsyncBank moneyBank;
        private double quantity = 0;

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
            quantityTextBox.setAllowLetters(false);
            quantityTextBox.setMaxDecimalChar(2);
            quantityTextBox.setAllowNegativeNumbers(false);
            quantityTextBox.setAlignment(Alignment.RIGHT);
            quantityTextBox.setOnTextChanged((text)->{
                double newQuantity = quantityTextBox.getDouble();
                quantity = Math.max(0, newQuantity);
                updateButtons();
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

            //quantitySlider = new HorizontalSlider();
            //quantitySlider.setSliderValue(0);

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
            addChild(buyButton);
            addChild(sellButton);

            quantityTextBox.setText(String.valueOf(quantity));
            updateButtons();
        }
        public void setItemName(String itemName)
        {
            itemNameLabel.setText(itemName);
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

            buyButton.setBounds(padding, height-(StockMarketGuiElement.defaultElementHeight-padding), (width-spacing)/2, StockMarketGuiElement.defaultElementHeight);
            sellButton.setBounds(buyButton.getRight()+spacing/2, buyButton.getTop(), buyButton.getWidth(), buyButton.getHeight());


        }
        private void addQuantity(double amount)
        {
            quantity += amount;
            if(quantity < 0)
                quantity = 0;
            quantityTextBox.setText(String.valueOf(quantity));
            updateButtons();
        }
        private void updateButtons()
        {
            remove10Button.setEnabled(quantity >= 10);
            remove32Button.setEnabled(quantity >= 32);
            remove64Button.setEnabled(quantity >= 64);
            remove128Button.setEnabled(quantity >= 128);
        }
    }


    //private final TabElement tabElement;
    private final MarketOrderPanel marketOrderPanel;


    public TradingPanel(Consumer<Double> onBuyMarket, Consumer<Double> onSellMarket)
    {
        //tabElement = new TabElement();
        marketOrderPanel = new MarketOrderPanel(onBuyMarket, onSellMarket);
        addTab(Texts.MARKET_ORDER_TAB_TITLE.getString(), marketOrderPanel);

        //addChild(tabElement);
    }

    public void setItemName(String itemName)
    {
        marketOrderPanel.setItemName(itemName);
    }

/*
    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = StockMarketGuiElement.padding;
        int spacing =  StockMarketGuiElement.spacing;
        int width = getWidth() - padding*2;
        int height = getHeight() - padding*2;
        tabElement.setBounds(padding, padding, width, height);
    }*/
}
