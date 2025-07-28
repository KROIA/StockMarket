package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.banking.bank.MoneyBank;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.function.Consumer;

public class OrderView extends GuiElement {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public static final int padding = 0;
    public static final int dirWidthRatio = 20;
    public static final int amountWidthRatio = 20;
    public static final int filledWidthRatio = 20;
    public static final int priceWidthRatio = 20;
    public static final int cancelWidthRatio = 20;
    public static final int sumRatio = dirWidthRatio + amountWidthRatio + filledWidthRatio + priceWidthRatio + cancelWidthRatio;

    public static final Alignment alignment = Alignment.CENTER;

    private final Label directionLabel;
    private final Label amountLabel;
    private final Label filledLabel;
    private final Label priceLabel;
    private final Button cancelButton;

    private final int cancelNormalColor = 0xFFeb8c34; // Orange
    private final int cancelHoverColor = 0xFFf2a45c; // Light Orange
    private final int cancelDownColor = 0xFFd6370b; // Dark Orange

    private OrderReadData order;

    public OrderView(OrderReadData order, Consumer<OrderReadData> onCancelOrder) {
        super(0,0,0,20);
        this.order = order;
        directionLabel = new Label();
        amountLabel = new Label();
        filledLabel = new Label();
        priceLabel = new Label();
        cancelButton = new Button(TradeScreen.CANCEL.getString(), () -> {
            if(onCancelOrder != null) {
                onCancelOrder.accept(order);
            }
        });

        directionLabel.setAlignment(alignment);
        amountLabel.setAlignment(alignment);
        filledLabel.setAlignment(alignment);
        priceLabel.setAlignment(alignment);
        cancelButton.setLayoutType(alignment);

        cancelButton.setIdleColor(cancelNormalColor);
        cancelButton.setHoverColor(cancelHoverColor);
        cancelButton.setPressedColor(cancelDownColor);

        addChild(directionLabel);
        addChild(amountLabel);
        addChild(filledLabel);
        addChild(priceLabel);
        addChild(cancelButton);

        setOrder(order);
    }


    public void setOrder(OrderReadData order) {
        this.order = order;

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
        amountLabel.setText(MoneyBank.getNormalizedAmount(Math.abs(order.amount)));
        if(order.type == Order.Type.LIMIT) {
            priceLabel.setText(String.valueOf(order.limitPrice));
        }
        else {
            priceLabel.setText("Market");
        }
    }
    public OrderReadData getOrder() {
        return order;
    }

    @Override
    protected void layoutChanged() {
        int width = getWidth()-padding*2;
        int height = getHeight();

        int _dirWidthRatio = dirWidthRatio * width / sumRatio;
        int _amountWidthRatio = amountWidthRatio * width / sumRatio;
        int _filledWidthRatio = filledWidthRatio * width / sumRatio;
        int _priceWidthRatio = priceWidthRatio * width / sumRatio;
        int _cancelWidthRatio = cancelWidthRatio * width / sumRatio;

        directionLabel.setBounds(padding, 0, _dirWidthRatio, height);
        amountLabel.setBounds(directionLabel.getRight(), 0, _amountWidthRatio, height);
        filledLabel.setBounds(amountLabel.getRight(), 0, _filledWidthRatio, height);
        priceLabel.setBounds(filledLabel.getRight(), 0, _priceWidthRatio, height);
        cancelButton.setBounds(priceLabel.getRight()+3, 3, _cancelWidthRatio-6, height-6);
    }

    @Override
    protected void render() {
        filledLabel.setText(MoneyBank.getNormalizedAmount(Math.abs(order.filledAmount)));
    }

}
