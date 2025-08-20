package net.kroia.stockmarket.screen.uiElements.chart;

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

        int miny = priceToYPosFunc.apply(chartViewMinPrice);
        int maxy = priceToYPosFunc.apply(chartViewMaxPrice);

        int chartViewHeight = miny - maxy;
        int chartViewWidth = getWidth() - xPadding*2;

        float barHeight = (float)chartViewHeight;
        if(orderBookVolume.volume.length > 1)
        {
            barHeight = (float)chartViewHeight / (orderBookVolume.volume.length - 1);
        }

        long[] volume = orderBookVolume.volume;
        // Get max volume of volume
        long maxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int y = miny;
        int lastY = y;
        long currentVolume = 0L;
        for (long vol : volume) {
            lastY = y;
            y = Math.max((int)(maxy + chartViewHeight+barHeight/2 - barHeight*i), maxy);
           // long absVol = Math.abs(vol);
            currentVolume += vol;
            if(y == lastY)
            {
                // skip this bar since it is not getting displayed because the height is 0.
                i++;
                continue;
            }
            long absVol = Math.abs(currentVolume);
            if (absVol > 0) {
                int color = currentVolume > 0 ? colorBuy : colorSell;
                int barWidth = (int)map(Math.min(absVol, maxVolume), 0L, maxVolume, 0L, (long)chartViewWidth);
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

}
