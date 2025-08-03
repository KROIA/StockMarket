package net.kroia.stockmarket.screen.uiElements.chart;

import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public class TradingVolumeHistoryChart extends StockMarketGuiElement {

    private int yPadding = 1;
    private int xPaddingRight = 0;
    private int candleWidth = 3;

    private PriceHistory priceHistory = null;

    public TradingVolumeHistoryChart() {
        super();
    }

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        if(priceHistory == null)
            return;



        int x = getWidth() - xPaddingRight;


        long maxVolume = priceHistory.getMaxVolume();
        int lastIndex = priceHistory.size()-1;
        int viewHeight= Math.max(getHeight() - yPadding * 2, 0);
        int yStartPos =  yPadding+viewHeight;

        //for(int i=lastIndex; i>=0; i--)
        for(int i=0; i<=lastIndex; i++)
        {
            x -= candleWidth;


            long volume = priceHistory.getVolume(i);
            int height = (int)map(volume, 0, maxVolume, 0, viewHeight);
            drawRect(x, yStartPos, candleWidth, -height, 0xFF91a9b8);

            //int low = priceHistory.getLowPrice(lastIndex-i);
            //int high = priceHistory.getHighPrice(lastIndex-i);
            //int close = priceHistory.getClosePrice(lastIndex-i);
            //int open = priceHistory.getOpenPrice(lastIndex-i);
            //renderCandle(x, candleWidth, 0, 0, open, close, high, low);

            if(x <= candleWidth)
                break;
        }
    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {

    }

    public void setYPadding(int yPadding) {
        this.yPadding = yPadding;
    }
    public void setXPaddingRight(int xPadding) {
        this.xPaddingRight = xPadding;
    }
    public void setPriceHistory(PriceHistory priceHistory) {
        this.priceHistory = priceHistory;
    }
    public void setCandleWidth(int candleWidth) {
        this.candleWidth = candleWidth;
    }
}
