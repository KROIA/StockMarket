package net.kroia.stockmarket.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;

public class OrderbookVolumeChart {

    private OrderbookVolume orderBookVolume;

    private int chartPositionX;
    private int chartPositionY;
    private int chartViewWidth;
    private int chartViewHeight;

    private final int colorSell = 0x7FFF0000;
    private final int colorBuy = 0x7F00FF00;



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

    public int getChartPositionX()
    {
        return chartPositionX;
    }
    public int getChartPositionY()
    {
        return chartPositionY;
    }
    public int getChartViewWidth()
    {
        return chartViewWidth;
    }
    public int getChartViewHeight()
    {
        return chartViewHeight;
    }
    public void setOrderBookVolume(OrderbookVolume orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    public void render(PoseStack graphics)
    {
        if(orderBookVolume == null)
            return;
        int x = chartPositionX;

        float barHeight = (float)chartViewHeight / orderBookVolume.getTiles();
        int y = (int)(chartPositionY + chartViewHeight+barHeight/2);
        int[] volume = orderBookVolume.getVolume();
        // Get max volume of volume
        int maxVolume = orderBookVolume.getMaxVolume();
        int i = 1;
        int lastY = y;
        for (int vol : volume) {
            lastY = y;
            y = (int)(chartPositionY + chartViewHeight+barHeight/2 - barHeight*i);
            int absVol = Math.abs(vol);
            if (absVol > 0) {
                int color = vol > 0 ? colorBuy : colorSell;
                int barWidth = map(absVol, 0, maxVolume, 0, chartViewWidth);
                int xPos = x + chartViewWidth - barWidth;
                GuiComponent.fill(graphics, xPos, lastY, xPos + barWidth, y, color);
            }

            i++;
        }

    }
}
