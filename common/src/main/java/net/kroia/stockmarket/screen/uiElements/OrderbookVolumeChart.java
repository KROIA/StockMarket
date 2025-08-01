package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.clientdata.OrderBookVolumeData;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public class OrderbookVolumeChart extends StockMarketGuiElement {

    private OrderBookVolumeData orderBookVolume;

    private final int colorSell = TradeScreen.colorRed;
    private final int colorBuy = TradeScreen.colorGreen;

    private static final int PADDING = 10;


    public OrderbookVolumeChart(int x, int y, int width, int height) {
        super(x, y, width, height);
    }
    public OrderbookVolumeChart() {
        super(0,0,0,1);
    }

    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }
    public void setOrderBookVolume(OrderBookVolumeData orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    @Override
    protected void render() {
        if(orderBookVolume == null)
            return;
        int x = PADDING/2;

        int chartViewHeight = getHeight() - 2*PADDING - getHeight()/10;
        int chartViewWidth = getWidth() - PADDING;

        float barHeight = (float)chartViewHeight/ orderBookVolume.tiles;

        long[] volume = orderBookVolume.volume;
        // Get max volume of volume
        long maxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int y = (int)(PADDING + chartViewHeight+barHeight/2);
        int lastY = y;
        for (long vol : volume) {
            lastY = y;
            y = (int)(PADDING + chartViewHeight+barHeight/2 - barHeight*i);
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
    protected void layoutChanged() {

    }

}
