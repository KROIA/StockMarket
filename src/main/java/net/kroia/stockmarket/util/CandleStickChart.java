package net.kroia.stockmarket.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class CandleStickChart {

    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private int chartViewWidth;
    private int chartViewHeight;
    private int candleWidth;

    private int plotXOffset = 100;
    private int plotYOffset = 100;

    private PriceHistory priceHistory = null;
    public CandleStickChart()
    {

    }

    public void setChartView(int minPrice, int maxPrice, int width, int height)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;
        this.chartViewWidth = width;
        this.chartViewHeight = height;
    }


    public void setPriceHistory(PriceHistory priceHistory)
    {
        this.priceHistory = priceHistory;
        if(priceHistory != null)
        {
            candleWidth = chartViewWidth / priceHistory.size();
        }
    }

    public void render(GuiGraphics graphics)
    {
        if(priceHistory == null)
            return;
        int x = 0;

        for(int i=0; i<priceHistory.size(); i++)
        {
            int low = priceHistory.getLowPrice(i);
            int high = priceHistory.getHighPrice(i);
            int close = priceHistory.getClosePrice(i);
            int open = priceHistory.getOpenPrice(i);
            renderCandle(graphics, x, candleWidth, chartViewMinPrice, chartViewMaxPrice, chartViewWidth, chartViewHeight, plotXOffset, plotYOffset, open, close, high, low);
            x += candleWidth;
        }
    }


    public static void renderCandle(GuiGraphics graphics, int x,int candleWidth, int chartViewMinPrice, int chartViewMaxPrice, int chartViewWidth, int chartViewHeight, int xOffset, int yOffset,
                                    int open, int close, int high, int low)
    {
        // int wickHighY = (int) (chartViewHeight - ((high - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);
        // int wickLowY = (int) (chartViewHeight - ((low - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);


        int color = open > close ? 0xFFFF0000 : 0xFF00FF00;

        // Draw wick
        int wickYMin = map(low, chartViewMinPrice, chartViewMaxPrice, chartViewHeight, 0);
        int wickYMax = map(high, chartViewMinPrice, chartViewMaxPrice, chartViewHeight, 0);

        graphics.fill(xOffset + x + candleWidth / 2 - 1, yOffset + wickYMin,
                xOffset + x + candleWidth / 2 + 1, yOffset + wickYMax, color);

        color &= 0xFFFEFEFE;

        // Draw body
        int bodyYMin = map(Math.min(open, close), chartViewMinPrice, chartViewMaxPrice, chartViewHeight, 0);
        int bodyYMax = map(Math.max(open, close), chartViewMinPrice, chartViewMaxPrice, chartViewHeight, 0);

        if(bodyYMax == bodyYMin)
        {
            bodyYMin -= 1;
            bodyYMax += 1;
        }

        graphics.fill(xOffset + x, yOffset + bodyYMin,
                xOffset + x + candleWidth, yOffset + bodyYMax, color);
    }
}
