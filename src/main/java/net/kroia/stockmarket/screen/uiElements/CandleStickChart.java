package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class CandleStickChart extends GuiElement {

    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }

    private final int colorUp = TradeScreen.colorGreen;
    private final int colorDown = TradeScreen.colorRed;

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private PriceHistory priceHistory = null;

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
    protected void render() {
        if(priceHistory == null)
            return;


        int labelWidth = 0;
        int maxLabelWidth = 0;
        int yAxisLabelIncrement = 10;
        int labelXPos = 5;
        int chartWidth = 0;
        if(chartViewMaxPrice - chartViewMinPrice > 10)
        {
            yAxisLabelIncrement = (chartViewMaxPrice - chartViewMinPrice)/10;
        }
        // Draw yAxis
        for(int i=chartViewMaxPrice; i>chartViewMinPrice; i-=yAxisLabelIncrement)
        {
            int y = getChartYPos(i);

            // Draw text label
            String label = String.valueOf(i);
            labelWidth = getFont().width(label);
            maxLabelWidth = Math.max(maxLabelWidth, labelWidth);
            drawText(label, labelXPos, y - 4, 0xFFFFFFFF);
            chartWidth = getWidth()-maxLabelWidth-PADDING-labelXPos-5;
            drawRect(labelXPos+maxLabelWidth+5,  y, chartWidth, 1, 0xFF808080);

        }


        int candleWidth = 0;
        if(priceHistory != null)
        {
            candleWidth = chartWidth / priceHistory.size();
            candleWidth = candleWidth | 1; // Make sure it is odd
            if(candleWidth < 3)
                candleWidth = 3;
        }
        int x = getWidth()-PADDING-maxLabelWidth-candleWidth;
        for(int i=priceHistory.size()-1; i>=0; i--)
        {
            int low = priceHistory.getLowPrice(i);
            int high = priceHistory.getHighPrice(i);
            int close = priceHistory.getClosePrice(i);
            int open = priceHistory.getOpenPrice(i);
            renderCandle(x, candleWidth, maxLabelWidth, 0, open, close, high, low);
            x -= candleWidth;
            if(x < labelXPos+5)
                break;
        }
    }

    @Override
    protected void layoutChanged() {

    }


    public void renderCandle(int x,int candleWidth, int xOffset, int yOffset,
                                    int open, int close, int high, int low)
    {
        // int wickHighY = (int) (chartViewHeight - ((high - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);
        // int wickLowY = (int) (chartViewHeight - ((low - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);


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

    private int getChartYPos(int price)
    {
        return map(price, chartViewMinPrice, chartViewMaxPrice, getHeight()-PADDING, PADDING);
    }
}
