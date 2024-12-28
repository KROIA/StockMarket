package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.minecraft.world.item.ItemStack;

import static net.kroia.stockmarket.screen.custom.TradeScreen.*;

public class TradePanel extends GuiElement {






    private final int buyButtonNormalColor = 0xFF008800;
    private final int buyButtonHoverColor = 0xFF00BB00;
    private final int buyButtonPressedColor = 0xFF00FF00;


    private final int sellButtonNormalColor = 0xFF880000;
    private final int sellButtonHoverColor = 0xFFBB0000;
    private final int sellButtonPressedColor = 0xFFFF0000;


    private final Button changeItemButton;

    private final Label yourItemBalanceLabel;
    private final ItemView currentItemView;
    private final Label currentItemBalanceLabel;
    private final ItemView moneyItemView;
    private final Label currentMoneyBalanceLabel;
    private final Label currentPriceTextLabel;
    private final Label currentPriceLabel;

    private final Label amountLabel;
    private final TextBox amountTextBox;

    private final Label marketOrderLabel;
    private final Button marketBuyButton;
    private final Button marketSellButton;

    private final Label limitOrderLabel;
    private final Label limitPriceLabel;
    private final TextBox limitPriceTextBox;
    private final Button limitBuyButton;
    private final Button limitSellButton;


    private int amount = 0;
    private int limitPrice = 0;


    public TradePanel(Runnable onItemChangeButtonClicked,
                      Runnable onMarketBuyButtonClicked,
                      Runnable onMarketSellButtonClicked,
                      Runnable onLimitBuyButtonClicked,
                      Runnable onLimitSellButtonClicked) {
        super();
        changeItemButton = new Button(CHANGE_ITEM_BUTTON.getString());
        changeItemButton.setOnFallingEdge(onItemChangeButtonClicked);
        yourItemBalanceLabel = new Label(YOUR_BALANCE_LABEL.getString());
        yourItemBalanceLabel.setAlignment(Alignment.CENTER);
        currentItemView = new ItemView();
        currentItemBalanceLabel = new Label();
        currentItemBalanceLabel.setAlignment(Alignment.LEFT);
        moneyItemView = new ItemView(new ItemStack(BankSystemItems.MONEY.get()));
        moneyItemView.setShowTooltip(false);
        currentMoneyBalanceLabel = new Label();
        currentMoneyBalanceLabel.setAlignment(Alignment.LEFT);
        currentPriceTextLabel = new Label(PRICE_LABEL.getString());
        currentPriceTextLabel.setAlignment(Alignment.RIGHT);
        currentPriceLabel = new Label();
        currentPriceLabel.setAlignment(Alignment.LEFT);
        amountLabel = new Label(AMOUNT_LABEL.getString()+":");
        amountLabel.setAlignment(Alignment.RIGHT);
        amountTextBox = new TextBox(0,0,0);
        amountTextBox.setText(""+amount);
        amountTextBox.setAllowLetters(false);
        amountTextBox.setOnTextChanged(() -> {
            int am = amountTextBox.getInt();
            amount = Math.max(am, 0);
            amountTextBox.setText(""+amount);
        });

        marketOrderLabel = new Label(MARKET_ORDER_LABEL.getString());
        marketOrderLabel.setAlignment(Alignment.CENTER);
        marketBuyButton = new Button(BUY.getString(), onMarketBuyButtonClicked);
        marketBuyButton.setHoverColor(buyButtonHoverColor);
        marketBuyButton.setIdleColor(buyButtonNormalColor);
        marketBuyButton.setPressedColor(buyButtonPressedColor);
        marketSellButton = new Button(SELL.getString(), onMarketSellButtonClicked);
        marketSellButton.setHoverColor(sellButtonHoverColor);
        marketSellButton.setIdleColor(sellButtonNormalColor);
        marketSellButton.setPressedColor(sellButtonPressedColor);

        limitOrderLabel = new Label(LIMIT_ORDER_LABEL.getString());
        limitOrderLabel.setAlignment(Alignment.CENTER);
        limitPriceLabel = new Label(LIMIT_PRICE_LABEL.getString()+":");
        limitPriceLabel.setAlignment(Alignment.RIGHT);
        limitPriceTextBox = new TextBox(0,0,0);
        limitPriceTextBox.setText(""+limitPrice);
        limitPriceTextBox.setOnTextChanged(() -> {
            int am = limitPriceTextBox.getInt();
            if(am < 0)
                limitPrice = 0;
            else
                limitPrice = am;
            limitPriceTextBox.setText(""+limitPrice);
        });

        limitBuyButton = new Button(BUY.getString(), onLimitBuyButtonClicked);
        limitBuyButton.setHoverColor(buyButtonHoverColor);
        limitBuyButton.setIdleColor(buyButtonNormalColor);
        limitBuyButton.setPressedColor(buyButtonPressedColor);
        limitSellButton = new Button(SELL.getString(), onLimitSellButtonClicked);
        limitSellButton.setHoverColor(sellButtonHoverColor);
        limitSellButton.setIdleColor(sellButtonNormalColor);
        limitSellButton.setPressedColor(sellButtonPressedColor);

        addChild(yourItemBalanceLabel);
        addChild(currentItemView);
        addChild(currentItemBalanceLabel);
        addChild(moneyItemView);
        addChild(currentPriceTextLabel);
        addChild(currentPriceLabel);
        addChild(currentMoneyBalanceLabel);
        addChild(changeItemButton);
        addChild(amountLabel);
        addChild(amountTextBox);
        addChild(marketOrderLabel);
        addChild(marketBuyButton);
        addChild(marketSellButton);
        addChild(limitOrderLabel);
        addChild(limitPriceLabel);
        addChild(limitPriceTextBox);
        addChild(limitBuyButton);
        addChild(limitSellButton);
    }

    public void setItemStack(ItemStack item)
    {
        currentItemView.setItemStack(item);
    }
    public void setCurrentItemBalance(long balance)
    {
        currentItemBalanceLabel.setText(""+balance);
    }
    public void setCurrentMoneyBalance(long balance)
    {
        currentMoneyBalanceLabel.setText(""+balance);
    }
    public void setCurrentPrice(int price)
    {
        currentPriceLabel.setText(""+price);
    }

    public void setAmount(int amount)
    {
        this.amount = amount;
        amountTextBox.setText(""+amount);
    }
    public int getAmount() {
        return amount;
    }

    public void setLimitPrice(int price)
    {
        this.limitPrice = price;
        limitPriceTextBox.setText(""+price);
    }
    public int getLimitPrice()
    {
        return limitPrice;
    }

    @Override
    protected void layoutChanged() {
        int padding = 4;
        int spacing = 4;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;
        int labelHeight = 14;
        int buttonHeight = 16;


        int y = padding;
        int x = padding;

        changeItemButton.setBounds(x, y, width, buttonHeight);


        yourItemBalanceLabel.setBounds(x, changeItemButton.getBottom()+spacing, width, labelHeight);
        currentItemView.setPosition(x, yourItemBalanceLabel.getBottom()+spacing);
        currentItemBalanceLabel.setBounds(currentItemView.getRight(),currentItemView.getTop(), width/2-spacing/2-currentItemView.getWidth(), currentItemView.getHeight());
        moneyItemView.setPosition(currentItemBalanceLabel.getRight()+spacing, currentItemBalanceLabel.getTop());
        currentMoneyBalanceLabel.setBounds(moneyItemView.getRight(),moneyItemView.getTop(), currentItemBalanceLabel.getWidth(), moneyItemView.getHeight());
        currentPriceTextLabel.setBounds(x, currentItemView.getBottom()+spacing, (width-spacing)/2, labelHeight);
        currentPriceLabel.setBounds(currentPriceTextLabel.getRight()+spacing, currentPriceTextLabel.getTop(), currentPriceTextLabel.getWidth(), currentPriceTextLabel.getHeight());



        amountLabel.setBounds(x, currentPriceLabel.getBottom()+spacing*6, (width-spacing)/2, labelHeight);
        amountTextBox.setBounds(amountLabel.getRight()+spacing, amountLabel.getTop(), amountLabel.getWidth(), amountLabel.getHeight());

        marketOrderLabel.setBounds(x, amountLabel.getBottom()+spacing*2, width, labelHeight);
        marketBuyButton.setBounds(x, marketOrderLabel.getBottom()+spacing, (width-spacing)/2, buttonHeight);
        marketSellButton.setBounds(marketBuyButton.getRight()+spacing, marketBuyButton.getTop(), marketBuyButton.getWidth(), marketBuyButton.getHeight());

        limitOrderLabel.setBounds(x, marketBuyButton.getBottom()+spacing*4, width, labelHeight);
        limitPriceLabel.setBounds(x, limitOrderLabel.getBottom()+spacing, (width-spacing)/2, labelHeight);
        limitPriceTextBox.setBounds(limitPriceLabel.getRight()+spacing, limitPriceLabel.getTop(), limitPriceLabel.getWidth(), limitPriceLabel.getHeight());
        limitBuyButton.setBounds(x, limitPriceLabel.getBottom()+spacing, (width-spacing)/2, buttonHeight);
        limitSellButton.setBounds(limitBuyButton.getRight()+spacing, limitBuyButton.getTop(), limitBuyButton.getWidth(), limitBuyButton.getHeight());
    }
    @Override
    protected void render() {

    }


}
