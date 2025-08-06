package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class LimitOrderInChartDisplay extends StockMarketGuiElement {

    private static final int buyColor = ColorUtilities.getRGB(ColorUtilities.setBrightness(TradeScreen.colorGreen, 0.4f), 255);
    private static final int sellColor = ColorUtilities.getRGB(ColorUtilities.setBrightness(TradeScreen.colorRed, 0.4f), 255);
    private int color;
    private OrderReadData order;

    private final Button moveButton;
    private int globalMouseYStart;
    private int clickPosYOffset;
    private boolean isDragging = false;
    private final TradingPair pair;
    private final Function<Integer, Integer> yPosToPriceFunc;
    private final BiConsumer<OrderReadData, Integer> onOrderReplacedToNewPrice;

    public LimitOrderInChartDisplay(Function<Integer, Integer> yPosToPriceFunc,
                                    OrderReadData order,
                                    TradingPair pair,
                                    BiConsumer<OrderReadData, Integer> onOrderReplacedToNewPrice) {
        super();
        this.yPosToPriceFunc = yPosToPriceFunc;
        this.onOrderReplacedToNewPrice = onOrderReplacedToNewPrice;
        this.pair = pair;
        moveButton = new Button("");
        moveButton.setOnDown(this::onButtonDown);
        moveButton.setOnRisingEdge(this::onButtonRising);
        moveButton.setOnFallingEdge(this::onButtonFalling);
        moveButton.setHoverTooltipMousePositionAlignment(Alignment.TOP);
        moveButton.setHoverTooltipSupplier(this::getButtonTooltip);
        moveButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        setOrder(order);

        setOutlineColor(0);
        setBackgroundColor(0);
        addChild(moveButton);
        setSize(15, 5);
    }

    public OrderReadData getOrder()
    {
        return order;
    }
    public void setOrder(OrderReadData order) {
        this.order = order;
        if(order.isBuy())
            color = buyColor;
        else
            color = sellColor;
        moveButton.setOutlineColor(color);
        moveButton.setIdleColor(color);
        moveButton.setHoverColor(ColorUtilities.setBrightness(color, 0.9f));
        moveButton.setPressedColor(ColorUtilities.setBrightness(color, 0.8f));
    }


    @Override
    protected void render() {
        int xPos = moveButton.getRight();
        drawRect(xPos, getHeight()/2, getWidth()-xPos, 1, color);

        if(isDragging)
        {
            moveButton.drawTooltip(getButtonTooltip(), getMouseX(), getMouseY(),
                    moveButton.getTooltipBackgroundColor(), moveButton.getTooltipBackgroundPadding(), Alignment.RIGHT);
        }
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
    public int getCurrentPrice()
    {
        return yPosToPriceFunc.apply(getY() + getHeight()/2);
    }


    private void onButtonFalling()
    {
        globalMouseYStart = getParent().getMouseY();
        clickPosYOffset = getMouseY();
        isDragging = true;
        moveButton.setHoverTooltipSupplier(null);
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
        int newPrice = getCurrentPrice();
        if(onOrderReplacedToNewPrice != null)
            onOrderReplacedToNewPrice.accept(order, newPrice);
        moveButton.setHoverTooltipSupplier(this::getButtonTooltip);

    }

    private String getButtonTooltip()
    {
        String displayName = ClientPlayerUtilities.getItemDisplayText(pair.getCurrency().getStack());
        if(isDragging)
        {
            return StockMarketTextMessages.getLimitOrderInChartDisplayMoveButtonMoving(getCurrentPrice(), displayName);
        }
        return StockMarketTextMessages.getLimitOrderInChartDisplayMoveButton();
    }

}
