package net.kroia.stockmarket.util;

import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class CandleStickChart {

    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }

    private final int colorUp = 0x7F00FF00;
    private final int colorDown = 0x7FFF0000;

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private int chartViewWidth;
    private int chartViewHeight;
    //private int candleWidth;

    private int plotXOffset = 100;
    private int plotYOffset = 100;

    private PriceHistory priceHistory = null;
    private final TradeScreen parent;
    public CandleStickChart(TradeScreen parent)
    {
        this.parent = parent;
    }

    public void setChartView(int xPos, int yPos, int width, int height)
    {
        this.chartViewWidth = width;
        this.chartViewHeight = height;
        this.plotXOffset = xPos;
        this.plotYOffset = yPos;
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
    public int getChartViewWidth()
    {
        return chartViewWidth;
    }
    public int getChartViewHeight()
    {
        return chartViewHeight;
    }
    public int getChartPositionX()
    {
        return plotXOffset;
    }
    public int getChartPositionY()
    {
        return plotYOffset;
    }

    public void setPriceHistory(PriceHistory priceHistory)
    {
        this.priceHistory = priceHistory;

    }

    public void render(GuiGraphics graphics)
    {
        if(priceHistory == null)
            return;


        int labelWidth = 0;
        // Draw yAxis
        for(int i=chartViewMinPrice; i<=chartViewMaxPrice; i+=10)
        {
            int y = getChartYPos(i);

            // Draw text label
            String label = String.valueOf(i);
            Minecraft minecraft = getMinecraft();
            labelWidth = minecraft.font.width(label);
            graphics.drawString(minecraft.font, label, plotXOffset, plotYOffset + y - 4, 0xFFFFFFFF, false);
            graphics.fill(plotXOffset+labelWidth, plotYOffset + y, plotXOffset + chartViewWidth, plotYOffset + y + 1, 0xFF808080);

        }

        int x = 0;
        int candleWidth = 0;
        if(priceHistory != null)
        {
            candleWidth = (chartViewWidth-labelWidth) / priceHistory.size();
        }
        for(int i=0; i<priceHistory.size(); i++)
        {
            int low = priceHistory.getLowPrice(i);
            int high = priceHistory.getHighPrice(i);
            int close = priceHistory.getClosePrice(i);
            int open = priceHistory.getOpenPrice(i);
            renderCandle(graphics, x, candleWidth, plotXOffset+labelWidth, plotYOffset, open, close, high, low);
            x += candleWidth;
        }
    }


    public void renderCandle(GuiGraphics graphics, int x,int candleWidth, int xOffset, int yOffset,
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
            graphics.fill(xOffset + x + candleWidth / 2 - 1, yOffset + bodyYMax,
                          xOffset + x + candleWidth / 2 + 1, yOffset + wickYMax, color);
        }

        if(wickYMin - (bodyYMin) > 0)
        {
            // Wick down
            graphics.fill(xOffset + x + candleWidth / 2 - 1, yOffset + bodyYMin,
                          xOffset + x + candleWidth / 2 + 1, yOffset + wickYMin, color);
        }

        graphics.fill(xOffset + x, yOffset + bodyYMin,
                xOffset + x + candleWidth, yOffset + bodyYMax, color);
    }

    private int getChartYPos(int price)
    {
        return map(price, chartViewMinPrice, chartViewMaxPrice, chartViewHeight, 0);
    }

    private Minecraft getMinecraft()
    {
        return parent.getMinecraft();
    }
}
