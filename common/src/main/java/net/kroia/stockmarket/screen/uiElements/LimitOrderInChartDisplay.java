package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.screen.custom.TradeScreen;

import java.util.function.Function;

public class LimitOrderInChartDisplay extends GuiElement {


    private static final int buyColor = ColorUtilities.getRGB(ColorUtilities.setBrightness(TradeScreen.colorGreen, 0.4f), 255);
    private static final int sellColor = ColorUtilities.getRGB(ColorUtilities.setBrightness(TradeScreen.colorRed, 0.4f), 255);
    private final int color;
    private final LimitOrder order;

    private final Button moveButton;
    private int globalMouseYStart;
    private int clickPosYOffset;
    private boolean isDragging = false;
    private final Function<Integer, Integer> yPosToPriceFunc;

    public LimitOrderInChartDisplay(Function<Integer, Integer> yPosToPriceFunc, LimitOrder order) {
        super();
        this.yPosToPriceFunc = yPosToPriceFunc;
        this.order = order;
        if(order.isBuy())
            color = buyColor;
        else
            color = sellColor;
        moveButton = new Button("");
        moveButton.setOutlineColor(color);
        moveButton.setIdleColor(color);
        moveButton.setHoverColor(ColorUtilities.setBrightness(color, 0.9f));
        moveButton.setPressedColor(ColorUtilities.setBrightness(color, 0.8f));
        moveButton.setOnDown(this::onButtonDown);
        moveButton.setOnRisingEdge(this::onButtonRising);
        moveButton.setOnFallingEdge(this::onButtonFalling);

        setOutlineColor(0);
        setBackgroundColor(0);
        addChild(moveButton);
        setSize(15, 5);
    }

    public LimitOrder getOrder()
    {
        return order;
    }


    @Override
    protected void render() {
        int xPos = moveButton.getRight();
        drawRect(xPos, getHeight()/2, getWidth()-xPos, 1, color);

        /*if(isDragging)
        {
            drawText()
        }*/
    }

    @Override
    protected void layoutChanged() {
        int height = getHeight();
        moveButton.setBounds(0, 0,15, height);
    }

    @Override
    public void setY(int y) {
        if(isDragging)
            return;
        super.setY(y-getHeight()/2);
    }
    @Override
    public void setPosition(int x, int y) {
        if(isDragging)
        {
            super.setPosition(x, getY());
            return;
        }
        super.setPosition(x, y-getHeight()/2);
    }
    @Override
    public void setBounds(int x, int y, int width, int height) {
        if(isDragging)
        {
            super.setBounds(x, getY(), width, height);
            return;
        }
        super.setBounds(x, y-height/2, width, height);
    }

    public boolean isDragging()
    {
        return isDragging;
    }


    private void onButtonFalling()
    {
        globalMouseYStart = getParent().getMouseY();
        clickPosYOffset = getMouseY();
        isDragging = true;
    }
    private void onButtonDown()
    {
        // Move to the mouse position
        int y = getParent().getMouseY();
        int dy = y-globalMouseYStart;
        super.setY(y-clickPosYOffset);
    }
    private void onButtonRising()
    {
        isDragging = false;
        int newPrice = yPosToPriceFunc.apply(getY()+getHeight()/2);
        ClientMarket.changeOrderPrice(order.getItemID(), order.getOrderID(), newPrice);
    }

}
