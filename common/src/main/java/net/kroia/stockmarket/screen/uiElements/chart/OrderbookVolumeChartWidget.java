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
    int chartViewMinPrice = 0;
    int chartViewMaxPrice = 1000;
    private Function<Integer, Integer> priceToYPosFunc;


    public OrderbookVolumeChartWidget(Function<Integer, Integer> priceToYPosFunc, int colorBuy, int colorSell) {
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
    public void setMinMaxPrice(int minPrice, int maxPrice)
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

        float barHeight = (float)chartViewHeight/ orderBookVolume.tiles;

        long[] volume = orderBookVolume.volume;
        // Get max volume of volume
        long maxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int y = (int)(maxy + chartViewHeight+barHeight/2);
        int lastY = y;
        for (long vol : volume) {
            lastY = y;
            y = (int)(maxy + chartViewHeight+barHeight/2 - barHeight*i);
            long absVol = Math.abs(vol);
            if (absVol > 0) {
                int color = vol > 0 ? colorBuy : colorSell;
                int barWidth = (int)map(absVol, 0L, maxVolume, 0L, (long)chartViewWidth);
                int xPos = x + chartViewWidth - barWidth;
                int height = lastY-y;
                drawRect(xPos, y, barWidth, height, color);
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
