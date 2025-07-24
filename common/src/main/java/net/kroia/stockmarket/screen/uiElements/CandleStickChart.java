package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.PriceHistory;

import java.util.ArrayList;
import java.util.HashMap;

public class CandleStickChart extends GuiElement {

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

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private PriceHistory priceHistory = null;
    int chartWidth = 0;
    int maxLabelWidth = 0;
    //int labelXPos = 5;


    private HashMap<Long,LimitOrderInChartDisplay> limitOrderDisplays = new HashMap<>();

    private static final int PADDING = 10;

    public CandleStickChart(int x, int y, int width, int height) {
        super(x, y, width, height);
    }
    public CandleStickChart() {
        super(0,0,0,1);
    }

    public void setMinMaxPrice(int minPrice, int maxPrice)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setY(getChartYPos(display.getOrder().getPrice()));
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

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        if(priceHistory == null)
            return;
        int yAxisLabelIncrement = 1;
        int labelWidth = 0;


        int candleWidth = 0;
        if(priceHistory != null)
        {
            candleWidth = chartWidth / priceHistory.size();
            candleWidth = candleWidth | 1; // Make sure it is odd
            if(candleWidth < 3)
                candleWidth = 3;
        }
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
            chartWidth = getWidth()-maxLabelWidth-labelXPos-5;
            drawRect(1,  y, x, 1, 0xFF808080);
        }


        long maxVolume = priceHistory.getMaxVolume();
        for(int i=priceHistory.size()-1; i>=0; i--)
        {
            x -= candleWidth;
            long volume = priceHistory.getVolume(i);
            drawRect(x, getHeight()-1, candleWidth, (int)-map(volume, 0, maxVolume, 0, getHeight()/11+PADDING-2), 0xFF91a9b8);

            int low = priceHistory.getLowPrice(i);
            int high = priceHistory.getHighPrice(i);
            int close = priceHistory.getClosePrice(i);
            int open = priceHistory.getOpenPrice(i);
            renderCandle(x, candleWidth, 0, 0, open, close, high, low);

            if(x <= candleWidth)
                break;
        }
    }
    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setWidth(getWidth()/2-5);
            display.setPosition(getWidth()-display.getWidth(), getChartYPos(display.getOrder().getPrice()));
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

        if((bodyYMax) - wickYMax > 0) {
            // Wick up
            drawRect(xOffset + x + candleWidth / 2, yOffset + bodyYMax,
                    1, wickYMax-bodyYMax, color);
        }

        if(wickYMin - (bodyYMin) > 0)
        {
            // Wick down
            drawRect(xOffset + x + candleWidth / 2, yOffset + bodyYMin,
                          1, wickYMin-bodyYMin, color);
        }

        drawRect(xOffset + x, yOffset + bodyYMin,
                candleWidth, bodyYMax-bodyYMin, color);
    }


    public void updateOrderDisplay()
    {
        ArrayList<Order> orders = ClientMarket.getOrders(TradeScreen.getItemID());
        HashMap<Long,Integer> stillActiveOrderIds = new HashMap<>();
        ArrayList<Long> forRemoval = new ArrayList<>();

        for(int j=0; j<orders.size(); ++j)
        {
            long orderID = orders.get(j).getOrderID();
            stillActiveOrderIds.put(orderID, 1);
        }

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            if(!stillActiveOrderIds.containsKey(display.getOrder().getOrderID()))
            {
                forRemoval.add(display.getOrder().getOrderID());
            }
            else
            {
                stillActiveOrderIds.put(display.getOrder().getOrderID(), 2);
            }
        }
        for(Long orderID : forRemoval)
        {
            removeChild(limitOrderDisplays.get(orderID));
            limitOrderDisplays.remove(orderID);
        }
        for(int j=0; j<orders.size(); ++j)
        {
            Order order = orders.get(j);
            if(order instanceof LimitOrder limitOrder)
            {
                long orderID = limitOrder.getOrderID();
                if(stillActiveOrderIds.get(orderID) == 1)
                {
                    LimitOrderInChartDisplay orderView = new LimitOrderInChartDisplay(this::getPriceFromYPos, limitOrder);
                    orderView.setWidth(getWidth()/2-5);
                    orderView.setPosition(getWidth()-orderView.getWidth(), getChartYPos(limitOrder.getPrice()));
                    limitOrderDisplays.put(orderID, orderView);
                    addChild(orderView);
                }
            }
        }
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
