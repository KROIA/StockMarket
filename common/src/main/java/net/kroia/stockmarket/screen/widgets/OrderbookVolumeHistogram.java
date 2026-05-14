package net.kroia.stockmarket.screen.widgets;

import com.mojang.math.Axis;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OrderbookVolumeHistogram extends StockMarketGuiElement {

    @FunctionalInterface
    public interface Overlay {
        void render(OrderbookVolumeHistogram histogram);
    }

    private final List<Overlay> overlays = new ArrayList<>();

    private final CandlestickChart parentChart;

    private int updateRequestTickCounter = 0;
    private int updateRequestTickCounts = 20;
    private double startPrice;
    private double endPrice;
    private float[] volumeChunks;

    private final int greenColor = UI_Colors.buyColorGreen_dark;
    private final int redColor = UI_Colors.sellColorRed;
    private final Rectangle scissorRect = new Rectangle(0,0,0,0);
    private float maxAbsVolumeCache = 0;

    public OrderbookVolumeHistogram(CandlestickChart parentChart)
    {
        this.parentChart = parentChart;
    }

    public void addOverlay(Overlay overlay) { overlays.add(overlay); }
    public void removeOverlay(Overlay overlay) { overlays.remove(overlay); }

    public Rectangle getCanvasBounds() {
        return new Rectangle(scissorRect.x, scissorRect.y, scissorRect.width, scissorRect.height);
    }

    // Convert absolute volume to X pixel position (bars are right-aligned)
    public int toCanvasSpaceX(float absVolume) {
        if (maxAbsVolumeCache <= 0) return scissorRect.x + scissorRect.width;
        int barWidth = (int)(absVolume * scissorRect.width / maxAbsVolumeCache);
        return scissorRect.x + scissorRect.width - barWidth;
    }

    public int toCanvasSpaceY(double price) {
        return parentChart.toCanvasSpaceY(price);
    }

    public float getMaxAbsVolume() { return maxAbsVolumeCache; }
    public double getStartPrice() { return startPrice; }
    public double getEndPrice() { return endPrice; }
    public int getChunkCount() { return volumeChunks != null ? volumeChunks.length : 0; }

    public @Nullable ClientMarket getMarket() {
        return parentChart.getMarket();
    }

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        @Nullable ClientMarket market = parentChart.getMarket();
        if (market == null)
            return;
        renderHistogram();

        // Render overlays on top of histogram
        if (market != null) {
            enableScissor(scissorRect);
            for (Overlay overlay : overlays) {
                overlay.render(this);
            }
            disableScissor();
        }
    }
    @Override
    protected void render() {
        @Nullable ClientMarket market = parentChart.getMarket();
        if (market == null)
            return;

        updateRequestTickCounter++;
        if(updateRequestTickCounter > updateRequestTickCounts)
        {
            updateRequestTickCounter = 0;
            double startPrice = parentChart.getMinVisiblePrice();
            double endPrice = parentChart.getMaxVisiblePrice();
            market.requestOrderbookVolume(startPrice, endPrice, 50).thenAccept(response -> {
                this.startPrice = response.startPrice();
                this.endPrice = response.endPrice();
                //this.startPrice = startPrice;
                //this.endPrice = endPrice;
                this.volumeChunks = response.volumes();
            });
        }
    }
    @Override
    protected void layoutChanged() {
        setHeight(parentChart.getHeight());

        scissorRect.x=padding;
        scissorRect.y=padding;
        scissorRect.width=getWidth()-2*padding;
        //scissorRect.height=getHeight()-2*padding;
    }

    private static final int guideLineColor = UI_Colors.candlestickChart_horizontalLine;

    private final List<double[]> guideLineCache = new ArrayList<>();

    private void renderVolumeGuideLines(int width, float norm) {
        guideLineCache.clear();
        double rawStep = maxAbsVolumeCache / 2.0;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;

        double niceStep;
        if      (normalized < 1.5) niceStep = 1  * magnitude;
        else if (normalized < 3.5) niceStep = 2  * magnitude;
        else if (normalized < 7.5) niceStep = 5  * magnitude;
        else                       niceStep = 10 * magnitude;

        int canvasBottom = scissorRect.y + scissorRect.height;

        for (double vol = niceStep; vol < maxAbsVolumeCache; vol += niceStep) {
            int barWidth = (int)(vol * width * norm);
            int lineX = scissorRect.x + width - barWidth;

            // Vertical dashed guide line (clipped by scissor)
            int dashH = 4;
            int gapH = 4;
            for (int y = scissorRect.y; y < canvasBottom; y += dashH + gapH) {
                int h = Math.min(dashH, canvasBottom - y);
                drawRect(lineX, y, 1, h, guideLineColor);
            }

            guideLineCache.add(new double[]{lineX, vol});
        }
    }

    private void renderVolumeGuideLabels() {
        if (guideLineCache.isEmpty()) return;
        int anchorY = scissorRect.y + scissorRect.height;
        for (double[] entry : guideLineCache) {
            int lineX = (int) entry[0];
            double vol = entry[1];
            String label = formatVolume(vol);
            int textW = getTextWidth(label);
            int textH = getTextHeight();
            var graphics = getGraphics();
            graphics.pushPose();
            graphics.rotateAround(Axis.ZP.rotationDegrees(-90), lineX, anchorY, 0);
            // Pre-rotation x offset: negative textW so the text ends at the pivot (right-aligned against chart edge)
            drawText(label, lineX - textW, anchorY - textH / 2, DEFAULT_TEXT_COLOR, getTextFontScale());
            graphics.popPose();
        }
    }

    private static String formatVolume(double vol) {
        if      (vol >= 1_000_000) return String.format("%.1fM", vol / 1_000_000);
        else if (vol >= 1_000)     return String.format("%.1fk", vol / 1_000);
        else if (vol >= 1)         return String.format("%.0f", vol);
        else                       return String.format("%.2f", vol);
    }

    protected void renderHistogram()
    {
        if(volumeChunks == null)
            return;

        int chunkCount = volumeChunks.length;
        if(chunkCount == 0)
            return;

        //double startPrice = parentChart.getMinVisiblePrice();
        //double endPrice = parentChart.getMaxVisiblePrice();
        double priceDelta = (endPrice - startPrice) / (double)(chunkCount);


        maxAbsVolumeCache = 0;
        for(int i = 0; i < chunkCount; i++)
        {
            maxAbsVolumeCache =  Math.max(maxAbsVolumeCache, Math.abs(volumeChunks[i]));
        }
        if(maxAbsVolumeCache <= 0)
            return;


        float norm = 1/maxAbsVolumeCache;
        int width = scissorRect.width;
        float widthF = width;

        scissorRect.height = parentChart.toCanvasSpaceY(parentChart.getMinVisiblePrice())-padding;

        enableScissor(scissorRect);

        int currentY = parentChart.toCanvasSpaceY(startPrice);
        for(int i = 0; i < chunkCount; i++)
        {
            float volume = volumeChunks[i];
            float absVolume = Math.abs(volume);
            int chunkWidth = (int)(absVolume * widthF * norm);
            double price = startPrice + priceDelta*(i+1);
            int endY = parentChart.toCanvasSpaceY(price);

            drawRect(scissorRect.x + width - chunkWidth, currentY, chunkWidth, endY-currentY, (volume>0?greenColor:redColor));

            currentY = endY;
        }

        renderVolumeGuideLines(width, norm);

        disableScissor();

        renderVolumeGuideLabels();
    }
}
