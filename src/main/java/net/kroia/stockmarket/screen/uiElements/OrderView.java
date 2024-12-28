package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.network.chat.Component;

public class OrderView extends GuiElement {

    public static final int padding = 0;
    public static final int dirWidthRatio = 20;
    public static final int amountWidthRatio = 20;
    public static final int filledWidthRatio = 20;
    public static final int priceWidthRatio = 20;
    public static final int cancelWidthRatio = 20;
    public static final int summRatio = dirWidthRatio + amountWidthRatio + filledWidthRatio + priceWidthRatio + cancelWidthRatio;

    public static final GuiElement.LayoutType layoutType = GuiElement.LayoutType.CENTER;

    private final Label directionLabel;
    private final Label amountLabel;
    private final Label filledLabel;
    private final Label priceLabel;
    private final Button cancelButton;

    private final int cancelNormalColor = 0xFFeb8c34; // Orange
    private final int cancelHoverColor = 0xFFf2a45c; // Light Orange
    private final int cancelDownColor = 0xFFd6370b; // Dark Orange

    private final Order order;

    public OrderView(Order order) {
        super(0,0,0,20);
        this.order = order;
        directionLabel = new Label();
        amountLabel = new Label();
        filledLabel = new Label();
        priceLabel = new Label();
        cancelButton = new Button(TradeScreen.CANCEL.getString(), this::onCancelOrder);

        directionLabel.setLayoutType(layoutType);
        amountLabel.setLayoutType(layoutType);
        filledLabel.setLayoutType(layoutType);
        priceLabel.setLayoutType(layoutType);
        cancelButton.setLayoutType(layoutType);

        cancelButton.setIdleColor(cancelNormalColor);
        cancelButton.setHoverColor(cancelHoverColor);
        cancelButton.setPressedColor(cancelDownColor);

        addChild(directionLabel);
        addChild(amountLabel);
        addChild(filledLabel);
        addChild(priceLabel);
        addChild(cancelButton);

        if(order.isBuy())
        {
            directionLabel.setText(TradeScreen.BUY.getString());
            setBackgroundColor(TradeScreen.colorGreen);
        }
        else
        {
            directionLabel.setText(TradeScreen.SELL.getString());
            setBackgroundColor(TradeScreen.colorRed);
        }
        amountLabel.setText(String.valueOf(Math.abs(order.getAmount())));
        if(order instanceof LimitOrder limitOrder) {
            priceLabel.setText(String.valueOf(limitOrder.getPrice()));
        }
        else {
            priceLabel.setText("Market");
        }
    }

    public Order getOrder() {
        return order;
    }

    private void onCancelOrder()
    {
        ClientMarket.cancelOrder(order);
    }

    @Override
    protected void layoutChanged() {


        int width = getWidth()-padding*2;
        int height = getHeight();




        int _dirWidthRatio = dirWidthRatio * width / summRatio;
        int _amountWidthRatio = amountWidthRatio * width / summRatio;
        int _filledWidthRatio = filledWidthRatio * width / summRatio;
        int _priceWidthRatio = priceWidthRatio * width / summRatio;
        int _cancelWidthRatio = cancelWidthRatio * width / summRatio;

        directionLabel.setBounds(padding, 0, _dirWidthRatio, height);
        amountLabel.setBounds(directionLabel.getRight(), 0, _amountWidthRatio, height);
        filledLabel.setBounds(amountLabel.getRight(), 0, _filledWidthRatio, height);
        priceLabel.setBounds(filledLabel.getRight(), 0, _priceWidthRatio, height);
        cancelButton.setBounds(priceLabel.getRight()+3, 3, _cancelWidthRatio-6, height-6);
    }

    @Override
    protected void render() {
        filledLabel.setText(String.valueOf(order.getFilledAmount()));
    }

}
