package net.kroia.stockmarket.screen.uiElements.chart;

import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.market.clientdata.OrderBookVolumeData;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.util.function.Function;

public class OrderbookVolumeChartWidget extends StockMarketGuiElement {

    private OrderBookVolumeData orderBookVolume;

    private final int colorSell;
    private final int colorBuy;

    private int yPadding = 0;
    private int xPadding = 5;
    float chartViewMinPrice = 0;
    float chartViewMaxPrice = 1000;
    private Function<Float, Integer> priceToYPosFunc;
    int minYPos = 0;
    int maxYPos = 0;
    float lastMaxVolume = 0;
    int chartViewWidth = 0;
    int chartViewHeight = 0;


    public OrderbookVolumeChartWidget(Function<Float, Integer> priceToYPosFunc, int colorBuy, int colorSell) {
        super();
        this.priceToYPosFunc = priceToYPosFunc;
        this.colorBuy = colorBuy;
        this.colorSell = colorSell;
    }
    public void setOrderBookVolume(OrderBookVolumeData orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    public void setYPadding(int yPadding) {
        this.yPadding = yPadding;
    }
    public void setXPadding(int xPadding) {
        this.xPadding = xPadding;
    }
    public void setMinMaxPrice(float minPrice, float maxPrice)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;
    }
    @Override
    protected void renderBackground() {
        super.renderBackground();
        if(orderBookVolume == null)
            return;
        int x = xPadding;

        minYPos = priceToYPosFunc.apply(chartViewMinPrice);
        maxYPos = priceToYPosFunc.apply(chartViewMaxPrice);

        chartViewHeight = minYPos - maxYPos;
        chartViewWidth = getWidth() - xPadding*2;

        float barHeight = (float)chartViewHeight;
        if(orderBookVolume.volume.length > 1)
        {
            barHeight = (float)chartViewHeight / (orderBookVolume.volume.length - 1);
        }

        float[] volume = orderBookVolume.volume;
        // Get max volume of volume
        lastMaxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int y = minYPos;
        int lastY = y;
        float currentVolume = 0;
        for (float vol : volume) {
            lastY = y;
            y = Math.max((int)(maxYPos + chartViewHeight+barHeight/2 - barHeight*i), maxYPos);
           // long absVol = Math.abs(vol);

            if(y == lastY)
            {
                // skip this bar since it is not getting displayed because the height is 0.
                i++;
                /*if(vol < 0 && currentVolume > 0 || vol > 0 && currentVolume < 0)
                {
                    currentVolume = vol;
                    continue;
                }
                if(vol > 0) {
                    currentVolume = Math.max(vol, currentVolume); // Simple way to remove aliasing effect
                }
                else
                {
                    currentVolume = Math.min(vol, currentVolume); // Simple way to remove aliasing effect
                }*/
                if(currentVolume < 0 && vol > 0 || currentVolume > 0 && vol < 0)
                    currentVolume = vol;
                else
                    currentVolume += vol;
                continue;
            }
            currentVolume = vol;
            float absVol = Math.abs(currentVolume);
            if (absVol > 0) {
                int color = currentVolume > 0 ? colorBuy : colorSell;
                int barWidth = getVolumeBarWidth(absVol);//(int)map(Math.min(absVol, lastMaxVolume), 0L, lastMaxVolume, 0L, (long)chartViewWidth);
                int xPos = x + chartViewWidth - barWidth;
                int height = lastY-y;
                drawRect(xPos, y, barWidth, height, color);
                currentVolume = 0L; // Reset current volume after drawing the bar
            }
            i++;
        }
    }

    @Override
    protected void render() {
        // No foreground rendering needed for this widget
    }

    @Override
    protected void layoutChanged() {

    }


    public Rectangle getGlobalChartBounds()
    {
        Point globalPos = getGlobalPositon();
        return new Rectangle(globalPos.x+xPadding, maxYPos+globalPos.y, getWidth() - xPadding*2, chartViewHeight);
    }
    public int getGlobalYPosForPrice(float price)
    {
        Point globalPos = getGlobalPositon();
        return globalPos.y + priceToYPosFunc.apply(price);
    }
    public int getVolumeBarWidth(float volume)
    {
        return (int)map(Math.min(volume, lastMaxVolume), 0L, lastMaxVolume, 0L, (long)chartViewWidth);
    }

}
