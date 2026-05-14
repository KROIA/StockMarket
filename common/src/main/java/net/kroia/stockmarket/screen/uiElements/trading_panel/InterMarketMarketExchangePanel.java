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

/**
 * Market Exchange sub-panel for inter-market trading.
 * Allows the player to sell a quantity of "have" items at the current market
 * cross-rate and receive "want" items in return. The estimated yield is
 * displayed based on the live rate.
 *
 * Layout:
 * - Quantity input with quick-add/subtract buttons
 * - Estimated yield label (quantity / currentRate)
 * - Current rate display
 * - Single "EXCHANGE" button
 */
class InterMarketMarketExchangePanel extends StockMarketGuiElement {
    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".inter_market_market_exchange.";
        private static final Component QUANTITY = Component.translatable(PREFIX + "quantity");
        private static final Component EXCHANGE = Component.translatable(PREFIX + "exchange");
        private static final Component ESTIMATED_YIELD_UNKNOWN = Component.translatable(PREFIX + "estimated_yield_unknown");
        private static final Component RATE_UNKNOWN = Component.translatable(PREFIX + "rate_unknown");

        /** Returns "Est. yield: ~{value} {itemName}" using the translation pattern. */
        static String estimatedYield(String formattedValue, String itemName) {
            return Component.translatable(PREFIX + "estimated_yield", formattedValue, itemName).getString();
        }

        /** Returns "Rate: {value} {haveItem}/{wantItem}" using the translation pattern. */
        static String rate(String formattedValue, String haveItem, String wantItem) {
            return Component.translatable(PREFIX + "rate", formattedValue, haveItem, wantItem).getString();
        }
    }

    private final TextBox quantityTextBox;
    private final Label quantityLabel;
    private final Label haveItemNameLabel;

    // Quick-add/subtract buttons (same pattern as MarketOrderPanel)
    private final Button zeroButton;
    private final Button add1Button;
    private final Button remove1Button;
    private final Button add10Button;
    private final Button add32Button;
    private final Button add64Button;
    private final Button add128Button;
    private final Button remove10Button;
    private final Button remove32Button;
    private final Button remove64Button;
    private final Button remove128Button;

    private final Label estimatedYieldLabel;
    private final Label currentRateLabel;

    private final Button exchangeButton;

    private double quantity = 0;
    private double currentRate = 0; // "have" items per 1 "want" item
    private double haveBalance = 0;
    private String haveItemName = "";
    private String wantItemName = "";
    private boolean marketOpen = true;

    public InterMarketMarketExchangePanel(Consumer<Double> onExchange) {
        int exchangeColor1 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.8f);
        int exchangeColor2 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.7f);
        int exchangeColor3 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.6f);
        int exchangeColor4 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.5f);

        int addColor = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.8f);
        int removeColor = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.8f);

        // Quantity input
        quantityTextBox = new TextBox();
        quantityTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 100, getBankManager().getItemFractionDigitsCount()));
        quantityTextBox.setAlignment(Alignment.RIGHT);
        quantityTextBox.setOnTextChanged((text) -> {
            double newQuantity = quantityTextBox.getDouble();
            quantity = Math.max(0, newQuantity);
            updateButtons();
            updateEstimatedYield();
            updateValidation();
        });

        quantityLabel = new Label(Texts.QUANTITY.getString());
        quantityLabel.setTextFontScale(0.8f);

        haveItemNameLabel = new Label();
        haveItemNameLabel.setTextFontScale(0.8f);
        haveItemNameLabel.setAlignment(Alignment.RIGHT);

        // Quick-add/subtract buttons
        zeroButton = new Button("0", () -> { quantity = 0; quantityTextBox.setText("0.0"); updateButtons(); updateEstimatedYield(); updateValidation(); });
        zeroButton.setTextFontScale(1.3f);

        add1Button = new Button("+1", () -> addQuantity(1.0));
        add1Button.setOutlineColor(addColor);
        add1Button.setTextFontScale(1.3f);

        remove1Button = new Button("-1", () -> addQuantity(-1.0));
        remove1Button.setOutlineColor(removeColor);
        remove1Button.setTextFontScale(1.3f);

        add10Button = new Button("+10", () -> addQuantity(10.0));
        add32Button = new Button("+32", () -> addQuantity(32.0));
        add64Button = new Button("+64", () -> addQuantity(64.0));
        add128Button = new Button("+128", () -> addQuantity(128.0));
        add10Button.setOutlineColor(addColor);
        add32Button.setOutlineColor(addColor);
        add64Button.setOutlineColor(addColor);
        add128Button.setOutlineColor(addColor);
        add10Button.setTextFontScale(1.3f);
        add32Button.setTextFontScale(1.3f);
        add64Button.setTextFontScale(1.3f);
        add128Button.setTextFontScale(1.3f);

        remove10Button = new Button("-10", () -> addQuantity(-10.0));
        remove32Button = new Button("-32", () -> addQuantity(-32.0));
        remove64Button = new Button("-64", () -> addQuantity(-64.0));
        remove128Button = new Button("-128", () -> addQuantity(-128.0));
        remove10Button.setOutlineColor(removeColor);
        remove32Button.setOutlineColor(removeColor);
        remove64Button.setOutlineColor(removeColor);
        remove128Button.setOutlineColor(removeColor);
        remove10Button.setTextFontScale(1.3f);
        remove32Button.setTextFontScale(1.3f);
        remove64Button.setTextFontScale(1.3f);
        remove128Button.setTextFontScale(1.3f);

        // Estimated yield and rate info
        estimatedYieldLabel = new Label("");
        estimatedYieldLabel.setTextFontScale(0.8f);
        estimatedYieldLabel.setAlignment(Alignment.CENTER);

        currentRateLabel = new Label("");
        currentRateLabel.setTextFontScale(0.8f);
        currentRateLabel.setAlignment(Alignment.CENTER);

        // Exchange button (green, single action)
        exchangeButton = new Button(Texts.EXCHANGE.getString(), () -> onExchange.accept(quantity));
        exchangeButton.setBackgroundColor(exchangeColor1);
        exchangeButton.setHoverColor(exchangeColor2);
        exchangeButton.setPressedColor(exchangeColor3);
        exchangeButton.setOutlineColor(exchangeColor4);

        // Add all children
        addChild(quantityTextBox);
        addChild(quantityLabel);
        addChild(haveItemNameLabel);
        addChild(zeroButton);
        addChild(add1Button);
        addChild(remove1Button);
        addChild(add10Button);
        addChild(add32Button);
        addChild(add64Button);
        addChild(add128Button);
        addChild(remove10Button);
        addChild(remove32Button);
        addChild(remove64Button);
        addChild(remove128Button);
        addChild(estimatedYieldLabel);
        addChild(currentRateLabel);
        addChild(exchangeButton);

        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedYield();
        updateValidation();
    }

    public void setHaveItemName(String name) {
        this.haveItemName = name;
        haveItemNameLabel.setText(name);
        updateEstimatedYield();
    }

    public void setWantItemName(String name) {
        this.wantItemName = name;
        updateEstimatedYield();
    }

    public void setCurrentRate(double rate) {
        this.currentRate = rate;
        updateEstimatedYield();
        updateValidation();
    }

    public void setHaveBalance(double balance) {
        this.haveBalance = balance;
        updateValidation();
    }

    public void setMarketOpen(boolean open) {
        this.marketOpen = open;
        updateValidation();
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedYield();
        updateValidation();
    }

    public double getQuantity() {
        return quantity;
    }

    @Override
    protected void render() {
        // No custom rendering needed
    }

    @Override
    protected void layoutChanged() {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        // Quantity label row
        quantityLabel.setBounds(padding, padding, width / 2, StockMarketGuiElement.defaultElementHeight / 2);
        haveItemNameLabel.setBounds(quantityLabel.getRight(), quantityLabel.getTop(), width / 2, quantityLabel.getHeight());

        // Quantity text box
        quantityTextBox.setBounds(padding, quantityLabel.getBottom(), width, StockMarketGuiElement.defaultElementHeight);

        // Quick-add/subtract button grid (same layout as MarketOrderPanel)
        int rowHeight = defaultElementHeight;
        int twoRowHeight = rowHeight * 2 + spacing;
        int zeroWidth = (width - 5 * spacing) / 6;
        int smallBtnWidth = zeroWidth;
        int buttonWidth = (width - zeroWidth - smallBtnWidth - 5 * spacing) / 4;
        int rightStart = padding + zeroWidth + spacing + smallBtnWidth + spacing;
        int topY = quantityTextBox.getBottom() + spacing;

        zeroButton.setBounds(padding, topY, zeroWidth, twoRowHeight);
        add1Button.setBounds(zeroButton.getRight() + spacing, topY, smallBtnWidth, rowHeight);
        remove1Button.setBounds(zeroButton.getRight() + spacing, add1Button.getBottom() + spacing, smallBtnWidth, rowHeight);

        add10Button.setBounds(rightStart, topY, buttonWidth, rowHeight);
        add32Button.setBounds(add10Button.getRight() + spacing, topY, buttonWidth, rowHeight);
        add64Button.setBounds(add32Button.getRight() + spacing, topY, buttonWidth, rowHeight);
        add128Button.setBounds(add64Button.getRight() + spacing, topY, buttonWidth, rowHeight);

        remove10Button.setBounds(rightStart, add10Button.getBottom() + spacing, buttonWidth, rowHeight);
        remove32Button.setBounds(remove10Button.getRight() + spacing, remove10Button.getTop(), buttonWidth, rowHeight);
        remove64Button.setBounds(remove32Button.getRight() + spacing, remove32Button.getTop(), buttonWidth, rowHeight);
        remove128Button.setBounds(remove64Button.getRight() + spacing, remove64Button.getTop(), buttonWidth, rowHeight);

        // Estimated yield and rate labels
        estimatedYieldLabel.setBounds(padding, remove10Button.getBottom() + spacing, width, StockMarketGuiElement.defaultElementHeight / 2);
        currentRateLabel.setBounds(padding, estimatedYieldLabel.getBottom() + spacing, width, StockMarketGuiElement.defaultElementHeight / 2);

        // Exchange button at the bottom
        exchangeButton.setBounds(padding, height - (StockMarketGuiElement.defaultElementHeight - padding), width, StockMarketGuiElement.defaultElementHeight);
    }

    private void addQuantity(double amount) {
        quantity += amount;
        if (quantity < 0)
            quantity = 0;
        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedYield();
        updateValidation();
    }

    private void updateButtons() {
        remove1Button.setEnabled(quantity >= 1);
        remove10Button.setEnabled(quantity >= 10);
        remove32Button.setEnabled(quantity >= 32);
        remove64Button.setEnabled(quantity >= 64);
        remove128Button.setEnabled(quantity >= 128);
    }

    /**
     * Updates the estimated yield and current rate labels.
     * Yield = quantity / currentRate (how many "want" items the player receives).
     * Colors the yield red if the player doesn't have enough "have" items.
     */
    private void updateEstimatedYield() {
        if (currentRate > 0 && quantity > 0) {
            double yield = quantity / currentRate;
            estimatedYieldLabel.setText(Texts.estimatedYield(String.format("%.2f", yield), wantItemName));
        } else {
            estimatedYieldLabel.setText(Texts.ESTIMATED_YIELD_UNKNOWN.getString());
        }

        if (currentRate > 0) {
            currentRateLabel.setText(Texts.rate(String.format("%.2f", currentRate), haveItemName, wantItemName));
        } else {
            currentRateLabel.setText(Texts.RATE_UNKNOWN.getString());
        }

        // Color red if player cannot afford
        boolean insufficient = quantity > haveBalance && haveBalance > 0;
        if (insufficient && quantity > 0)
            estimatedYieldLabel.setTextColor(UI_Colors.sellColorRed);
        else
            estimatedYieldLabel.setTextColor(GuiElement.DEFAULT_TEXT_COLOR);
    }

    /**
     * Enables or disables the exchange button based on balance and market state.
     */
    private void updateValidation() {
        exchangeButton.setEnabled(marketOpen && quantity > 0 && currentRate > 0 && quantity <= haveBalance);
    }
}
