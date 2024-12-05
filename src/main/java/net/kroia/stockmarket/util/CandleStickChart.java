package net.kroia.stockmarket.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class CandleStickChart {

    public static class CandleData
    {
        // Timestamp
        public long timestamp;
        // Open price
        public int open;
        // Close price
        public int close;
        // High price
        public int high;
        // Low price
        public int low;

        public CandleData(long timestamp, int open, int close, int high, int low)
        {
            this.timestamp = timestamp;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
        }
        public void render(GuiGraphics graphics, int x,int candleWidth, int chartViewMinPrice, int chartViewMaxPrice, int chartViewWidth, int chartViewHeight)
        {
            // Calculate candlestick position
            int candleX = x;
            int candleY = (int) (chartViewHeight - ((close - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);
            int candleHeight = (int) ((open - close) / (float) (chartViewMaxPrice - chartViewMinPrice) * chartViewHeight);
            if (candleHeight == 0)
            {
                candleHeight = 1;
            }
            // Draw candlestick
            graphics.fill(candleX, candleY, candleWidth, candleHeight, open > close ? 0x00FF00 : 0xFF0000);
            // Draw wick
            int wickY1 = (int) (chartViewHeight - ((high - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);

            int wickY2 = (int) (chartViewHeight - ((low - chartViewMinPrice) / (float) (chartViewMaxPrice - chartViewMinPrice)) * chartViewHeight);

            graphics.fill(candleX + candleWidth / 2, wickY1, candleX + candleWidth / 2, wickY2, open > close ? 0x00FF00 : 0xFF0000);
        }
    }

    private int chartViewMinPrice;
    private int chartViewMaxPrice;
    private int chartViewWidth;
    private int chartViewHeight;
    private int candleWidth;

    private ArrayList<CandleData> candleData = new ArrayList<>();

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


    public void setCandleData(ArrayList<CandleData> candleData)
    {
        this.candleData = candleData;
        if(!candleData.isEmpty())
            candleWidth = chartViewWidth / candleData.size();
    }

    public void render(GuiGraphics graphics)
    {
        int x = 0;
        for (CandleData data : candleData)
        {
            data.render(graphics, x, candleWidth, chartViewMinPrice, chartViewMaxPrice, chartViewWidth, chartViewHeight);
            x += candleWidth;
        }
    }
}
