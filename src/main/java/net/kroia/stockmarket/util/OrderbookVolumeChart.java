package net.kroia.stockmarket.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class OrderbookVolumeChart {

    private OrderbookVolume orderBookVolume;

    private int chartPositionX;
    private int chartPositionY;
    private int chartViewWidth;
    private int chartViewHeight;




    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }



    public void setChartView(int posX, int posY, int width, int height)
    {
        this.chartPositionX = posX;
        this.chartPositionY = posY;
        this.chartViewWidth = width;
        this.chartViewHeight = height;
    }

    public void setOrderBookVolume(OrderbookVolume orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    public void render(GuiGraphics graphics)
    {
        if(orderBookVolume == null)
            return;
        int x = chartPositionX;
        int y = chartPositionY + chartViewHeight;
        int barHeight = chartViewHeight / orderBookVolume.getTiles();
        int[] volume = orderBookVolume.getVolume();
        // Get max volume of volume
        int maxVolume = orderBookVolume.getMaxVolume();
        for (int vol : volume) {
            if (vol > 0) {
                int barWidth = map(vol, 0, maxVolume, 0, chartViewWidth);
                int xPos = x + chartViewWidth - barWidth;
                graphics.fill(xPos, y, xPos + barWidth, y + barHeight, 0x8800FFFF);
            }
            y -= barHeight;
        }

    }
}
