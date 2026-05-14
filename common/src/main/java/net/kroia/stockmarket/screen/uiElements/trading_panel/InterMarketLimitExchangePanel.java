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

import java.util.function.BiConsumer;

/**
 * Limit Exchange sub-panel for inter-market trading.
 * Quantity is in "want" items (the traded item). The "have" item acts as
 * the currency.
 * - BUY LIMIT: buy want-items with a max rate limit
 * - SELL LIMIT: sell want-items with a min rate limit
 * Estimated cost shows: quantity * rateLimit (in have-items).
 *
 * Layout:
 * - Rate limit input with "Market" auto-fill button
 * - Quantity input with quick-add/subtract buttons
 * - Estimated cost at the limit rate
 * - BUY LIMIT and SELL LIMIT buttons side by side
 */
class InterMarketLimitExchangePanel extends StockMarketGuiElement {
    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".inter_market_limit_exchange.";
        private static final Component RATE_LIMIT = Component.translatable(PREFIX + "rate_limit");
        private static final Component QUANTITY = Component.translatable(PREFIX + "quantity");
        private static final Component BUY = Component.translatable(PREFIX + "buy");
        private static final Component SELL = Component.translatable(PREFIX + "sell");
        private static final Component MARKET_BUTTON = Component.translatable(PREFIX + "market_button");
        private static final Component ESTIMATED_COST_UNKNOWN = Component.translatable(PREFIX + "estimated_cost_unknown");

        /** Returns "Est. cost: ~{value} {itemName}" using the translation pattern. */
        static String estimatedCost(String formattedValue, String itemName) {
            return Component.translatable(PREFIX + "estimated_cost", formattedValue, itemName).getString();
        }
    }

    // Rate limit input
    private final Label rateLimitLabel;
    private final Label rateUnitLabel;
    private final Button marketRateButton;
    private final TextBox rateLimitTextBox;

    // Quantity input
    private final TextBox quantityTextBox;
    private final Label quantityLabel;
    // Shows the WANT item name (since quantity is in want-items)
    private final Label wantItemNameLabel;

    // Quick-add/subtract buttons
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

    private final Label estimatedCostLabel;

    private final Button buyButton;
    private final Button sellButton;

    private double quantity = 0;
    private double rateLimit = 0; // max "have" items per 1 "want" item
    private double currentRate = 0;
    private double haveBalance = 0;
    private String haveItemName = "";
    private String wantItemName = "";
    private boolean marketOpen = true;

    public InterMarketLimitExchangePanel(BiConsumer<Double, Double> onBuyLimit, BiConsumer<Double, Double> onSellLimit) {
        int darkerBuyColor1 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.8f);
        int darkerBuyColor2 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.7f);
        int darkerBuyColor3 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.6f);
        int darkerBuyColor4 = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.5f);

        int darkerSellColor1 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.8f);
        int darkerSellColor2 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.7f);
        int darkerSellColor3 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.6f);
        int darkerSellColor4 = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.5f);

        int addColor = ColorUtilities.setBrightness(UI_Colors.buyColorGreen_dark, 0.8f);
        int removeColor = ColorUtilities.setBrightness(UI_Colors.sellColorRed_dark, 0.8f);

        // Rate limit section
        rateLimitLabel = new Label(Texts.RATE_LIMIT.getString());
        rateLimitLabel.setTextFontScale(0.8f);

        rateUnitLabel = new Label();
        rateUnitLabel.setTextFontScale(0.8f);
        rateUnitLabel.setAlignment(Alignment.RIGHT);

        rateLimitTextBox = new TextBox();
        rateLimitTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 100, getBankManager().getItemFractionDigitsCount()));
        rateLimitTextBox.setAlignment(Alignment.RIGHT);
        rateLimitTextBox.setOnTextChanged((text) -> {
            double newRate = rateLimitTextBox.getDouble();
            rateLimit = Math.max(0, newRate);
            updateEstimatedCost();
            updateValidation();
        });

        // "Market" button auto-fills the current cross-rate
        marketRateButton = new Button(Texts.MARKET_BUTTON.getString(), () -> {
            setRateLimit(currentRate);
            updateEstimatedCost();
            updateValidation();
        });

        // Quantity section
        quantityTextBox = new TextBox();
        quantityTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 100, getBankManager().getItemFractionDigitsCount()));
        quantityTextBox.setAlignment(Alignment.RIGHT);
        quantityTextBox.setOnTextChanged((text) -> {
            double newQuantity = quantityTextBox.getDouble();
            quantity = Math.max(0, newQuantity);
            updateButtons();
            updateEstimatedCost();
            updateValidation();
        });

        quantityLabel = new Label(Texts.QUANTITY.getString());
        quantityLabel.setTextFontScale(0.8f);

        // Shows the WANT item name next to the quantity label
        wantItemNameLabel = new Label();
        wantItemNameLabel.setTextFontScale(0.8f);
        wantItemNameLabel.setAlignment(Alignment.RIGHT);

        // Quick-add/subtract buttons
        zeroButton = new Button("0", () -> { quantity = 0; quantityTextBox.setText("0.0"); updateButtons(); updateEstimatedCost(); updateValidation(); });
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

        // Estimated cost label
        estimatedCostLabel = new Label("");
        estimatedCostLabel.setTextFontScale(0.8f);
        estimatedCostLabel.setAlignment(Alignment.CENTER);

        // Buy limit button (green)
        buyButton = new Button(Texts.BUY.getString(), () -> onBuyLimit.accept(quantity, rateLimit));
        buyButton.setBackgroundColor(darkerBuyColor1);
        buyButton.setHoverColor(darkerBuyColor2);
        buyButton.setPressedColor(darkerBuyColor3);
        buyButton.setOutlineColor(darkerBuyColor4);

        // Sell limit button (red)
        sellButton = new Button(Texts.SELL.getString(), () -> onSellLimit.accept(quantity, rateLimit));
        sellButton.setBackgroundColor(darkerSellColor1);
        sellButton.setHoverColor(darkerSellColor2);
        sellButton.setPressedColor(darkerSellColor3);
        sellButton.setOutlineColor(darkerSellColor4);

        // Add all children
        addChild(rateLimitLabel);
        addChild(rateUnitLabel);
        addChild(marketRateButton);
        addChild(rateLimitTextBox);
        addChild(quantityTextBox);
        addChild(quantityLabel);
        addChild(wantItemNameLabel);
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
        addChild(estimatedCostLabel);
        addChild(buyButton);
        addChild(sellButton);

        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedCost();
        updateValidation();
    }

    /** Sets the display name of the "have" item (currency side). */
    public void setHaveItemName(String name) {
        this.haveItemName = name;
        updateRateUnitLabel();
        updateEstimatedCost();
    }

    /** Sets the display name of the "want" item (traded item, shown next to quantity). */
    public void setWantItemName(String name) {
        this.wantItemName = name;
        wantItemNameLabel.setText(name);
        updateRateUnitLabel();
        updateEstimatedCost();
    }

    public void setCurrentRate(double rate) {
        this.currentRate = rate;
        updateEstimatedCost();
        updateValidation();
    }

    public double getCurrentRate() {
        return currentRate;
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
        updateEstimatedCost();
        updateValidation();
    }

    public double getQuantity() {
        return quantity;
    }

    /** Sets the rate limit input and updates the internal state. */
    public void setRateLimit(double rate) {
        this.rateLimit = rate;
        String rateString = String.format("%.2f", rate);
        rateLimitTextBox.setText(rateString);
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

        // Rate limit label row
        rateLimitLabel.setBounds(padding, padding, width / 2, StockMarketGuiElement.defaultElementHeight / 2);
        rateUnitLabel.setBounds(rateLimitLabel.getRight(), rateLimitLabel.getTop(), width / 2, rateLimitLabel.getHeight());

        // "Market" button + rate limit text box
        int marketBtnWidth = 45;
        marketRateButton.setBounds(padding, rateLimitLabel.getBottom(), marketBtnWidth, StockMarketGuiElement.defaultElementHeight);
        rateLimitTextBox.setBounds(marketRateButton.getRight() + spacing, rateLimitLabel.getBottom(), width - marketBtnWidth - spacing, StockMarketGuiElement.defaultElementHeight);

        // Quantity label row
        quantityLabel.setBounds(padding, rateLimitTextBox.getBottom() + spacing * 2, width / 2, StockMarketGuiElement.defaultElementHeight / 2);
        wantItemNameLabel.setBounds(quantityLabel.getRight(), quantityLabel.getTop(), width / 2, quantityLabel.getHeight());

        // Quantity text box
        quantityTextBox.setBounds(padding, quantityLabel.getBottom(), width, StockMarketGuiElement.defaultElementHeight);

        // Quick-add/subtract button grid
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

        // Estimated cost label
        estimatedCostLabel.setBounds(padding, remove10Button.getBottom() + spacing, width, StockMarketGuiElement.defaultElementHeight / 2);

        // Buy and sell limit buttons side by side at the bottom
        int halfWidth = (width - spacing) / 2;
        buyButton.setBounds(padding, height - (StockMarketGuiElement.defaultElementHeight - padding), halfWidth, StockMarketGuiElement.defaultElementHeight);
        sellButton.setBounds(buyButton.getRight() + spacing, buyButton.getTop(), halfWidth, StockMarketGuiElement.defaultElementHeight);
    }

    private void addQuantity(double amount) {
        quantity += amount;
        if (quantity < 0)
            quantity = 0;
        quantityTextBox.setText(String.valueOf(quantity));
        updateButtons();
        updateEstimatedCost();
        updateValidation();
    }

    private void updateButtons() {
        remove1Button.setEnabled(quantity >= 1);
        remove10Button.setEnabled(quantity >= 10);
        remove32Button.setEnabled(quantity >= 32);
        remove64Button.setEnabled(quantity >= 64);
        remove128Button.setEnabled(quantity >= 128);
    }

    /** Updates the rate unit label to show "have per want" names. */
    private void updateRateUnitLabel() {
        if (!haveItemName.isEmpty() && !wantItemName.isEmpty()) {
            rateUnitLabel.setText(haveItemName + "/" + wantItemName);
        }
    }

    /**
     * Updates the estimated cost label based on quantity and rate limit.
     * Cost = quantity * rateLimit (how many "have" items at the limit rate).
     * Colors the cost red if the player doesn't have enough "have" items for a buy.
     */
    private void updateEstimatedCost() {
        if (rateLimit > 0 && quantity > 0) {
            double cost = quantity * rateLimit;
            estimatedCostLabel.setText(Texts.estimatedCost(String.format("%.2f", cost), haveItemName));
        } else {
            estimatedCostLabel.setText(Texts.ESTIMATED_COST_UNKNOWN.getString());
        }

        // Color red if player cannot afford a buy at the limit rate
        double cost = quantity * rateLimit;
        boolean insufficient = cost > haveBalance && haveBalance > 0;
        if (insufficient && quantity > 0 && rateLimit > 0)
            estimatedCostLabel.setTextColor(UI_Colors.sellColorRed);
        else
            estimatedCostLabel.setTextColor(GuiElement.DEFAULT_TEXT_COLOR);
    }

    /**
     * Enables or disables the buy/sell limit buttons based on balance, rate, and market state.
     * Buy requires enough have-items to cover the cost (quantity * rateLimit).
     * Sell does not require have-balance (the player sells want-items).
     */
    private void updateValidation() {
        double cost = quantity * rateLimit;
        buyButton.setEnabled(marketOpen && quantity > 0 && rateLimit > 0 && cost <= haveBalance);
        sellButton.setEnabled(marketOpen && quantity > 0 && rateLimit > 0);
    }
}
