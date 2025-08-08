package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.banking.bank.MoneyBank;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import static net.kroia.stockmarket.screen.custom.TradeScreen.*;

public class TradePanel extends StockMarketGuiElement {
    private final int buyButtonNormalColor = 0xFF008800;
    private final int buyButtonHoverColor = 0xFF00BB00;
    private final int buyButtonPressedColor = 0xFF00FF00;


    private final int sellButtonNormalColor = 0xFF880000;
    private final int sellButtonHoverColor = 0xFFBB0000;
    private final int sellButtonPressedColor = 0xFFFF0000;


    private final TradingPairView previousTradingPairView;
    private final TradingPairView nextTradingPairView;
    private final Button changeItemButton;


    private final Frame balanceFrame;
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

    private final Label marketClosedLabel;

    private int amount = 0;
    private int limitPrice = 0;
    private int currentMarketPrice = 0;


    public TradePanel(Runnable onItemChangeButtonClicked,
                      Runnable onMarketBuyButtonClicked,
                      Runnable onMarketSellButtonClicked,
                      Runnable onLimitBuyButtonClicked,
                      Runnable onLimitSellButtonClicked,
                      Runnable onSelectNextMarketButtonClicked,
                      Runnable onSelectPreviousMarketButtonClicked) {
        super();

        previousTradingPairView = new TradingPairView();
        previousTradingPairView.setOnFallingEdge(onSelectPreviousMarketButtonClicked);
        previousTradingPairView.setHoverTooltipSupplier(PREVIOUS_PAIR_TOOLTIP::getString);
        previousTradingPairView.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
        nextTradingPairView = new TradingPairView();
        nextTradingPairView.setOnFallingEdge(onSelectNextMarketButtonClicked);
        nextTradingPairView.setHoverTooltipSupplier(NEXT_PAIR_TOOLTIP::getString);
        nextTradingPairView.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);

        changeItemButton = new Button(CHANGE_ITEM_BUTTON.getString());
        changeItemButton.setOnFallingEdge(onItemChangeButtonClicked);
        setPreviousTradingPair(null);
        setNextTradingPair(null);

        balanceFrame = new Frame();
        balanceFrame.setEnableBackground(false);
        yourItemBalanceLabel = new Label(YOUR_BALANCE_LABEL.getString());
        yourItemBalanceLabel.setAlignment(Alignment.CENTER);
        currentItemView = new ItemView();
        currentItemBalanceLabel = new Label();
        currentItemBalanceLabel.setAlignment(Alignment.RIGHT);
        moneyItemView = new ItemView();
        currentMoneyBalanceLabel = new Label();
        currentMoneyBalanceLabel.setAlignment(Alignment.LEFT);
        balanceFrame.addChild(yourItemBalanceLabel);
        balanceFrame.addChild(currentItemView);
        balanceFrame.addChild(currentItemBalanceLabel);
        balanceFrame.addChild(moneyItemView);
        balanceFrame.addChild(currentMoneyBalanceLabel);



        currentPriceTextLabel = new Label(PRICE_LABEL.getString());
        currentPriceTextLabel.setAlignment(Alignment.RIGHT);
        currentPriceLabel = new Label();
        currentPriceLabel.setAlignment(Alignment.LEFT);


        amountLabel = new Label(AMOUNT_LABEL.getString()+":");
        amountLabel.setAlignment(Alignment.RIGHT);
        amountTextBox = new TextBox(0,0,0);
        amountTextBox.setText(""+amount);
        amountTextBox.setAllowLetters(false);
        amountTextBox.setOnTextChanged((text) -> {
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


        //marketBuyButton.ho
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
        limitPriceTextBox.setOnTextChanged((text) -> {
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


        // Set tooltip mouse position alignment
        // (TOP_RIGHT means the tooltip will be positioned so that the mouse is at the top right corner of the tooltip)
        currentItemView.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        moneyItemView.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        currentPriceLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        currentPriceTextLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        amountLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        marketOrderLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        marketBuyButton.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);
        marketSellButton.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);
        limitOrderLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        limitPriceLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
        limitBuyButton.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);
        limitSellButton.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);



        // Set hover tooltip texts
        currentPriceLabel.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipCurrentPrice(currentMarketPrice, getItemName(), getCurrencyName()));
        currentPriceTextLabel.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipCurrentPrice(currentMarketPrice, getItemName(), getCurrencyName()));
        amountLabel.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipAmount(getItemName()));
        marketOrderLabel.setHoverTooltipSupplier(StockMarketTextMessages::getTradePanelTooltipMarketOrder);
        marketBuyButton.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipMarketBuy(amount, getItemName(), limitPrice, getCurrencyName()));
        marketSellButton.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipMarketSell(amount, getItemName(), limitPrice, getCurrencyName()));
        limitOrderLabel.setHoverTooltipSupplier(StockMarketTextMessages::getTradePanelTooltipLimitOrder);
        limitPriceLabel.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipLimitPrice(getItemName()));
        limitBuyButton.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipLimitBuy(amount, getItemName(), limitPrice, getCurrencyName()));
        limitSellButton.setHoverTooltipSupplier(() -> StockMarketTextMessages.getTradePanelTooltipLimitSell(amount, getItemName(), limitPrice, getCurrencyName()));



        marketClosedLabel = new Label(MARKET_CLOSED.getString());
        marketClosedLabel.setAlignment(Alignment.CENTER);

        //addChild(yourItemBalanceLabel);
        //addChild(currentItemView);
        //addChild(currentItemBalanceLabel);
        //addChild(moneyItemView);
        addChild(previousTradingPairView);
        addChild(nextTradingPairView);
        addChild(balanceFrame);
        addChild(currentPriceTextLabel);
        addChild(currentPriceLabel);
        //addChild(currentMoneyBalanceLabel);
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

        addChild(marketClosedLabel);

        for(GuiElement child : getChilds()) {
            child.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        }
    }

    public void setTradingPair(TradingPair pair)
    {
        currentItemView.setItemStack(pair.getItem().getStack());
        moneyItemView.setItemStack(pair.getCurrency().getStack());
    }
    public void setCurrentItemBalance(long balance, long centScaleFactor)
    {
        currentItemBalanceLabel.setText(MoneyBank.getNormalizedAmount(balance, centScaleFactor));
    }
    public void setCurrentMoneyBalance(long balance, long centScaleFactor)
    {
        currentMoneyBalanceLabel.setText(MoneyBank.getNormalizedAmount(balance, centScaleFactor));
    }
    public void setCurrentPrice(int price)
    {
        currentMarketPrice = price;
        currentPriceLabel.setText(String.valueOf(price));
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
        int padding = 5;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;
        int labelHeight = 15;
        int buttonHeight = 15;


        int y = padding;
        int x = padding;

        previousTradingPairView.setBounds(x, y, width/2-(spacing+1)/2, 20);
        nextTradingPairView.setBounds(previousTradingPairView.getRight()+spacing, y, width/2-(spacing+1)/2, 20);
        changeItemButton.setBounds(x, previousTradingPairView.getBottom()+spacing, width, buttonHeight);


        yourItemBalanceLabel.setBounds(0, spacing, width, labelHeight);
        currentItemBalanceLabel.setBounds(0, yourItemBalanceLabel.getBottom(), width/2-(spacing+1)/2-currentItemView.getWidth(), currentItemView.getHeight());
        currentItemView.setPosition(currentItemBalanceLabel.getRight(), currentItemBalanceLabel.getTop());
        moneyItemView.setPosition(currentItemView.getRight()+spacing, currentItemBalanceLabel.getTop());
        currentMoneyBalanceLabel.setBounds(moneyItemView.getRight(),moneyItemView.getTop(), currentItemBalanceLabel.getWidth(), moneyItemView.getHeight());
        balanceFrame.setBounds(x, changeItemButton.getBottom()+spacing,
                width, currentMoneyBalanceLabel.getBottom() - yourItemBalanceLabel.getTop()+ spacing*2);


        currentPriceTextLabel.setBounds(x, balanceFrame.getBottom()+spacing, (width-spacing)/2, labelHeight);
        currentPriceLabel.setBounds(currentPriceTextLabel.getRight()+spacing, currentPriceTextLabel.getTop(), currentPriceTextLabel.getWidth(), currentPriceTextLabel.getHeight());


        marketClosedLabel.setBounds(x, currentPriceLabel.getBottom()+spacing*6, width, labelHeight);

        int tradingStartY = height - (buttonHeight*2+ 4*labelHeight+3*spacing);
        amountLabel.setBounds(x, tradingStartY, (width-spacing)/2, labelHeight);
        amountTextBox.setBounds(amountLabel.getRight()+spacing, amountLabel.getTop(), amountLabel.getWidth(), amountLabel.getHeight());

        marketOrderLabel.setBounds(x, amountLabel.getBottom()+spacing*2, width, labelHeight);
        marketBuyButton.setBounds(x, marketOrderLabel.getBottom(), (width-spacing)/2, buttonHeight);
        marketSellButton.setBounds(marketBuyButton.getRight()+spacing, marketBuyButton.getTop(), marketBuyButton.getWidth(), marketBuyButton.getHeight());

        limitOrderLabel.setBounds(x, marketBuyButton.getBottom()+spacing*2, width, labelHeight);
        limitPriceLabel.setBounds(x, limitOrderLabel.getBottom(), (width-spacing)/2, labelHeight);
        limitPriceTextBox.setBounds(limitPriceLabel.getRight()+spacing, limitPriceLabel.getTop(), limitPriceLabel.getWidth(), limitPriceLabel.getHeight());
        limitBuyButton.setBounds(x, limitPriceLabel.getBottom(), (width-spacing)/2, buttonHeight);
        limitSellButton.setBounds(limitBuyButton.getRight()+spacing, limitBuyButton.getTop(), limitBuyButton.getWidth(), limitBuyButton.getHeight());

    }
    @Override
    protected void render() {

    }

    public void onMarketClosed()
    {
        amountLabel.setEnabled(false);
        amountTextBox.setEnabled(false);
        limitOrderLabel.setEnabled(false);
        marketOrderLabel.setEnabled(false);
        limitPriceLabel.setEnabled(false);
        limitPriceTextBox.setEnabled(false);
        marketBuyButton.setEnabled(false);
        marketSellButton.setEnabled(false);
        limitBuyButton.setEnabled(false);
        limitSellButton.setEnabled(false);

        marketClosedLabel.setEnabled(true);
    }
    public void onMarketOpened()
    {
        amountLabel.setEnabled(true);
        amountTextBox.setEnabled(true);
        limitOrderLabel.setEnabled(true);
        limitPriceLabel.setEnabled(true);
        limitPriceTextBox.setEnabled(true);
        marketOrderLabel.setEnabled(true);
        marketBuyButton.setEnabled(true);
        marketSellButton.setEnabled(true);
        limitBuyButton.setEnabled(true);
        limitSellButton.setEnabled(true);

        marketClosedLabel.setEnabled(false);
    }

    public void setMarketOpen(boolean open)
    {
        if(open)
            onMarketOpened();
        else
            onMarketClosed();
    }

    private String getCurrencyName()
    {
        String currencyName = "";
        if(moneyItemView.getItemStack()!=null)
            currencyName = moneyItemView.getItemStack().getHoverName().getString();
        return currencyName;
    }
    public String getItemName()
    {
        String itemName = "";
        if(currentItemView.getItemStack()!=null)
            itemName = currentItemView.getItemStack().getHoverName().getString();
        return itemName;
    }
    public void setPreviousTradingPair(TradingPair pair) {
        previousTradingPairView.setTradingPair(pair);
        previousTradingPairView.setEnabled(pair != null);
    }
    public TradingPair getPreviousTradingPair() {
        return previousTradingPairView.getTradingPair();
    }
    public void setNextTradingPair(TradingPair pair) {
        nextTradingPairView.setTradingPair(pair);
        nextTradingPairView.setEnabled(pair != null);
    }
    public TradingPair getNextTradingPair() {
        return nextTradingPairView.getTradingPair();
    }

}
