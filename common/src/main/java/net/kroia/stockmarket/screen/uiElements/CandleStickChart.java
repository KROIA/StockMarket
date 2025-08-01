package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.OrderReadListData;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;

public class CandleStickChart extends StockMarketGuiElement {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }
    private static float mapF(float value, float start1, float stop1, float start2, float stop2)
    {
        return start2 + (float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1));
    }

    private final int colorUp = TradeScreen.colorGreen;
    private final int colorDown = TradeScreen.colorRed;
    private static final int minCandleWidth = 3; // Minimum width of a candle in pixels

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private PriceHistory priceHistory = null;
    int chartWidth = 0;
    int maxLabelWidth = 0;
    private double scrollValue = 1;
    //int labelXPos = 5;

    private final Rectangle volumeDisplayRect = new Rectangle(0, 0, 0, 0);
    private boolean tooltipTimerStarted = false;
    private final TimerMillis tooltipTimer = new TimerMillis(false);


    private HashMap<Long,LimitOrderInChartDisplay> limitOrderDisplays = new HashMap<>();
    private BiConsumer<OrderReadData, Integer> priceChangeCallback;

    private static final int PADDING = 10;

    public CandleStickChart(BiConsumer<OrderReadData, Integer> priceChangeCallback, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.priceChangeCallback = priceChangeCallback;
    }
    public CandleStickChart(BiConsumer<OrderReadData, Integer> priceChangeCallback) {
        super(0,0,0,1);
        this.priceChangeCallback = priceChangeCallback;
    }

    public void setMinMaxPrice(int minPrice, int maxPrice)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setY(getChartYPos(display.getOrder().limitPrice));
        }
    }

    public int getChartViewMinPrice()
    {
        return chartViewMinPrice;
    }
    public int getChartViewMaxPrice()
    {
        return chartViewMaxPrice;
    }

    public void setPriceHistory(PriceHistory priceHistory)
    {
        this.priceHistory = priceHistory;
    }

    public int getMaxCandleCount()
    {
        return Math.max((int)(chartWidth*scrollValue*2) / minCandleWidth,10);
    }

    @Override
    protected void mouseScrolled(double delta) {
        if(!isMouseOver())
            return;
        scrollValue -= delta*0.01;

        if(scrollValue < 0)
            scrollValue = 0;
        else if(scrollValue > 1)
            scrollValue = 1;
    }
    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        if(priceHistory == null)
            return;
        int yAxisLabelIncrement = 1;
        int labelWidth = 0;


        int candleWidth = 0;
        candleWidth = (int)Math.ceil((double)getWidth() / (double)priceHistory.size());
        candleWidth = candleWidth | 1; // Make sure it is odd
        if(candleWidth < minCandleWidth)
            candleWidth = minCandleWidth;

        maxLabelWidth = getFont().width(String.valueOf(chartViewMaxPrice)) + 5;
        int labelXPos = getWidth() - maxLabelWidth;



        if(chartViewMaxPrice - chartViewMinPrice > 10)
        {
            yAxisLabelIncrement = (chartViewMaxPrice - chartViewMinPrice)/10;
        }

        int x = labelXPos-5;
        // Draw yAxis
        for(int i=chartViewMaxPrice; i>=chartViewMinPrice; i-=yAxisLabelIncrement)
        {
            int y = getChartYPos(i);
            String label = String.valueOf(i);
            drawText(label, labelXPos, y - 4, 0xFFFFFFFF);

            drawRect(1,  y, x, 1, 0xFF808080);
        }


        long maxVolume = priceHistory.getMaxVolume();
        int lastIndex = priceHistory.size()-1;
        volumeDisplayRect.x = 0;
        volumeDisplayRect.y = getHeight() - PADDING - getHeight()/10;
        volumeDisplayRect.width = chartWidth;
        volumeDisplayRect.height = getHeight() / 11 + PADDING - 2;
        for(int i=lastIndex; i>=0; i--)
        {
            x -= candleWidth;
            long volume = priceHistory.getVolume(lastIndex-i);
            drawRect(x, getHeight()-1, candleWidth, (int)-map(volume, 0, maxVolume, 0, (float) getHeight() /11+PADDING-2), 0xFF91a9b8);

            int low = priceHistory.getLowPrice(lastIndex-i);
            int high = priceHistory.getHighPrice(lastIndex-i);
            int close = priceHistory.getClosePrice(lastIndex-i);
            int open = priceHistory.getOpenPrice(lastIndex-i);
            renderCandle(x, candleWidth, 0, 0, open, close, high, low);

            if(x <= candleWidth)
                break;
        }
    }
    @Override
    protected void render() {
        int mouseX = getMouseX();
        int mouseY = getMouseY();
        if(volumeDisplayRect.contains(mouseX, mouseY))
        {
            if(!tooltipTimerStarted)
            {
                tooltipTimerStarted = true;
                tooltipTimer.start(getHoverTooltipDelay());
            }
            else if(tooltipTimer.isFinished())
            {
                drawTooltip(StockMarketTextMessages.getCandlestickChartTooltipTradeVolume(), mouseX, mouseY,
                        getTooltipBackgroundColor(), getTooltipBackgroundPadding(), Alignment.TOP);
            }
        }
        else {
            tooltipTimerStarted = false;
        }
    }

    @Override
    protected void layoutChanged() {
        chartWidth = getWidth()-maxLabelWidth-5;
        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setWidth(getWidth()/2-5);
            display.setPosition(getWidth()-display.getWidth(), getChartYPos(display.getOrder().limitPrice));
        }
    }


    public void renderCandle(int x,int candleWidth, int xOffset, int yOffset,
                                    int open, int close, int high, int low)
    {
        int color = open > close ? colorDown : colorUp;

        // Draw wick
        int wickYMin = getChartYPos(low);
        int wickYMax = getChartYPos(high);

        // Draw body
        int bodyYMin = getChartYPos(Math.min(open, close));
        int bodyYMax = getChartYPos(Math.max(open, close));

        if(bodyYMin == bodyYMax)
        {
            bodyYMin++;
            bodyYMax--;
        }

        int wickWidth = candleWidth / 4;
        if(wickWidth < 1)
        {
            wickWidth = 1;
        }
        else if(wickWidth > 11)
        {
            wickWidth = 11;
        }
        wickWidth |= 1; // Make sure it is odd

        int wickOffset = (candleWidth - wickWidth) / 2;
        if(wickOffset <= 0)
        {
            wickOffset = 0;
        }



        if((bodyYMax) - wickYMax > 0) {
            // Wick up
            drawRect(xOffset + x + wickOffset, yOffset + bodyYMax,
                    wickWidth, wickYMax-bodyYMax, color);
        }

        if(wickYMin - (bodyYMin) > 0)
        {
            // Wick down
            drawRect(xOffset + x + wickOffset, yOffset + bodyYMin,
                    wickWidth, wickYMin-bodyYMin, color);
        }

        drawRect(xOffset + x, yOffset + bodyYMin,
                candleWidth, bodyYMax-bodyYMin, color);
    }


    public void updateOrderDisplay(OrderReadListData orderList)
    {
        List<OrderReadData> orders = orderList.orders;
        //ArrayList<Order> orders = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getOrders(TradeScreen.getItemID());
        HashMap<Long,Integer> stillActiveOrderIds = new HashMap<>();
        ArrayList<Long> forRemoval = new ArrayList<>();

        for(int j=0; j<orders.size(); ++j)
        {
            long orderID = orders.get(j).orderID;
            stillActiveOrderIds.put(orderID, 1);
        }

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            if(!stillActiveOrderIds.containsKey(display.getOrder().orderID))
            {
                forRemoval.add(display.getOrder().orderID);
            }
            else
            {
                stillActiveOrderIds.put(display.getOrder().orderID, 2);
            }
        }
        for(Long orderID : forRemoval)
        {
            removeChild(limitOrderDisplays.get(orderID));
            limitOrderDisplays.remove(orderID);
        }
        for(int j=0; j<orders.size(); ++j)
        {
            OrderReadData order = orders.get(j);
            if(order.type == Order.Type.LIMIT)
            {
                long orderID = order.orderID;
                int case_ = stillActiveOrderIds.get(orderID);
                if(case_ == 1)
                {
                    LimitOrderInChartDisplay orderView = new LimitOrderInChartDisplay(this::getPriceFromYPos, order, this::onOrderReplacedToNewPrice);
                    orderView.setWidth(getWidth()/2-5);
                    orderView.setPosition(getWidth()-orderView.getWidth(), getChartYPos(order.limitPrice));
                    limitOrderDisplays.put(orderID, orderView);
                    addChild(orderView);
                }
                else if(case_ == 2)
                {
                    LimitOrderInChartDisplay orderView = limitOrderDisplays.get(orderID);
                    if(orderView != null)
                    {
                        orderView.setOrder(order);
                        orderView.setY(getChartYPos(order.limitPrice));
                    }
                }
            }
        }
    }

    private void onOrderReplacedToNewPrice(OrderReadData order, Integer newPrice)
    {
        if(priceChangeCallback != null)
        {
            priceChangeCallback.accept(order, newPrice);
        }
        //BACKEND_INSTANCES.LOGGER.warn("NOT_IMPLEMENTED: onOrderReplacedToNewPrice called for order: " + order.orderID + " with new price: " + newPrice);
       // BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.changeOrderPrice(order.getItemID(), order.getOrderID(), newPrice);
    }

    public int getChartYPos(int price)
    {
        return map(price, chartViewMinPrice, chartViewMaxPrice, getHeight()-PADDING - getHeight()/10, PADDING);
    }
    public int getPriceFromYPos(int y)
    {
        return Math.round(mapF(y, getHeight()-PADDING-getHeight()/10.f, PADDING, chartViewMinPrice, chartViewMaxPrice));
    }
}
