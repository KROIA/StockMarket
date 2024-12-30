package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class OrderbookVolumeChart extends GuiElement {

    private OrderbookVolume orderBookVolume;

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
    public void setOrderBookVolume(OrderbookVolume orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    @Override
    protected void render() {
        if(orderBookVolume == null)
            return;
        int x = PADDING/2;

        int chartViewHeight = getHeight() - 2*PADDING;
        int chartViewWidth = getWidth() - PADDING;

        float barHeight = (float)chartViewHeight/ orderBookVolume.getTiles();

        int[] volume = orderBookVolume.getVolume();
        // Get max volume of volume
        int maxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int y = (int)(PADDING + chartViewHeight+barHeight/2);
        int lastY = y;
        for (int vol : volume) {
            lastY = y;
            y = (int)(PADDING + chartViewHeight+barHeight/2 - barHeight*i);
            int absVol = Math.abs(vol);
            if (absVol > 0) {
                int color = vol > 0 ? colorBuy : colorSell;
                int barWidth = map(absVol, 0, maxVolume, 0, chartViewWidth);
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
