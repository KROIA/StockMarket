package net.kroia.stockmarket.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;

public class OrderbookVolumeChart {

    private ArrayList<Integer> orderBookVolume;

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

    public void setOrderBookVolume(ArrayList<Integer> orderBookVolume)
    {
        this.orderBookVolume = orderBookVolume;
    }

    public void render(GuiGraphics graphics)
    {
        if(orderBookVolume == null)
            return;
        int x = chartPositionX;
        int y = chartPositionY + chartViewHeight;
        int barHeight = chartViewHeight / orderBookVolume.size();
        int maxVolume = orderBookVolume.stream().max(Integer::compareTo).orElse(0);
        for(int volume : orderBookVolume)
        {
            if(volume > 0) {
                int barWidth = map(volume, 0, maxVolume, 0, chartViewWidth);
                int xPos = x + chartViewWidth - barWidth;
                graphics.fill(xPos, y, xPos + barWidth,y+ barHeight, 0x8800FFFF);
            }
            y -= barHeight;
        }

    }
}
