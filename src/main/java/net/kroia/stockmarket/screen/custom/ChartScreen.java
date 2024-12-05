// ChartScreen.java
package net.kroia.stockmarket.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.CandleStickChart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ChartScreen extends Screen {

    private static final Component TITLE = Component.translatable("gui."+ StockMarketMod.MODID+"stock_market_block_screen");
    private static final Component SELL_BUTTON = Component.translatable("gui."+ StockMarketMod.MODID+"stock_market_block_screen.sell_button");
    private static final CandleStickChart candleStickChart = new CandleStickChart();

    //private final ArrayList<CandleStickChart.CandleData> chartData;
    private final int chartWidth = 200;
    private final int chartHeight = 100;
    private final int padding = 10;

    private Button sellButton;

    public ChartScreen(ArrayList<CandleStickChart.CandleData> chartData) {
        super(TITLE);
        //this.chartData = chartData;
        candleStickChart.setChartView(0, 100, chartWidth, chartHeight);
        candleStickChart.setCandleData(chartData);
    }

    @Override
    protected void init() {
        super.init();

        sellButton = addRenderableWidget(Button.builder(SELL_BUTTON, this::onSellButtonPressed).build());

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //TutorialMod.LOGGER.info("Render Chart Screen");
        // Background is typically rendered first
        this.renderBackground(graphics);

        // Render things here before widgets (background textures)
        // You can draw additional things like text, images, etc.

        // Render the widgets (buttons, labels, etc.)
        super.render(graphics, mouseX, mouseY, partialTick);

        candleStickChart.render(graphics);


        /*
        // Coordinates for the line
        int x1 = 50; // Starting x-coordinate
        int y1 = 50; // Starting y-coordinate
        int x2 = 200; // Ending x-coordinate
        int y2 = 150; // Ending y-coordinate

        // Line color (ARGB format)
        int color = 0xFF00FF00; // Fully opaque green

        drawLine(graphics,10,10,150,150, color, 4);*/
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int width) {
        graphics.fill(x1, y1-width/2, x2, y1+width/2, color);
        graphics.fill(x2-width/2, y2, x2+width/2, y1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private void onSellButtonPressed(Button button)
    {

    }
}
