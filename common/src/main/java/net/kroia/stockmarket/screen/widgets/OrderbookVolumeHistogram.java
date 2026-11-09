package net.kroia.stockmarket.screen.widgets;

import com.mojang.math.Axis;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** T-209: when true the widget fetches and draws the virtual depth layout borders (plugin management only). */
    private boolean showArrayBorders = false;
    /** T-209: real border prices from the last response, indexed by {@code VirtualOrderbook.BORDER_*}; NaN = unused. */
    private List<Double> arrayBorders = List.of();

    /** T-209: lang key suffix per {@code VirtualOrderbook.BORDER_*} index. */
    private static final String[] BORDER_KEYS = {
            "log_floor", "log_slot_below", "linear_lo", "recenter_lo",
            "recenter_hi", "linear_hi", "log_slot_above", "ask_ceiling"
    };
    /** T-209: line colour per {@code VirtualOrderbook.BORDER_*} index: magenta = log grid edge, orange = log slot at the window, cyan = linear window, yellow = re-centre band. */
    private static final int[] BORDER_COLORS = {
            0xFFFF55FF, 0xFFFF9933, 0xFF33CCFF, 0xFFFFFF55,
            0xFFFFFF55, 0xFF33CCFF, 0xFFFF9933, 0xFFFF55FF
    };
    /** T-209: hover distance in pixels for a border tooltip. */
    private static final int BORDER_HOVER_PX = 2;

    public OrderbookVolumeHistogram(CandlestickChart parentChart)
    {
        this.parentChart = parentChart;
    }

    /**
     * T-209: shows the virtual depth layout borders (linear window, re-centre band, log grid edges, ask ceiling)
     * as coloured lines with a hover tooltip. Used by the plugin management screen to check the array layout.
     *
     * @param show true to fetch and draw the borders
     */
    public void setShowArrayBorders(boolean show)
    {
        showArrayBorders = show;
        if (!show) arrayBorders = List.of();
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
            renderArrayBorders();
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
            market.requestOrderbookVolume(startPrice, endPrice, 50, showArrayBorders).thenAccept(response -> {
                this.startPrice = response.startPrice();
                this.endPrice = response.endPrice();
                //this.startPrice = startPrice;
                //this.endPrice = endPrice;
                this.volumeChunks = response.volumes();
                if (showArrayBorders) this.arrayBorders = response.arrayBorders();
            });
        }
        renderArrayBorderTooltip();
    }

    /** T-209: @return the canvas Y of border {@code i}, or {@code Integer.MIN_VALUE} if unused or off screen */
    private int borderY(int i)
    {
        double price = arrayBorders.get(i);
        if (Double.isNaN(price) || price < parentChart.getMinVisiblePrice() || price > parentChart.getMaxVisiblePrice())
            return Integer.MIN_VALUE;
        return parentChart.toCanvasSpaceY(price);
    }

    /** T-209: draws one full-width line per visible border (off-screen borders are skipped). */
    private void renderArrayBorders()
    {
        if (arrayBorders.isEmpty()) return;
        // Same height renderHistogram() uses; set here too so the lines show while the book is empty.
        scissorRect.height = parentChart.toCanvasSpaceY(parentChart.getMinVisiblePrice()) - padding;
        int n = Math.min(arrayBorders.size(), BORDER_COLORS.length);
        for (int i = 0; i < n; i++)
        {
            int y = borderY(i);
            if (y != Integer.MIN_VALUE)
                drawRect(scissorRect.x, y, scissorRect.width, 1, BORDER_COLORS[i]);
        }
    }

    /** T-209: tooltip naming every border within {@link #BORDER_HOVER_PX} of the mouse, e.g. "linear ↑ 172.00". */
    private void renderArrayBorderTooltip()
    {
        if (arrayBorders.isEmpty() || !isMouseOver()) return;
        int my = getMouseY();
        StringBuilder sb = new StringBuilder();
        int n = Math.min(arrayBorders.size(), BORDER_KEYS.length);
        for (int i = 0; i < n; i++)
        {
            int y = borderY(i);
            if (y == Integer.MIN_VALUE || Math.abs(y - my) > BORDER_HOVER_PX) continue;
            if (!sb.isEmpty()) sb.append('\n');
            String price = BigDecimal.valueOf(arrayBorders.get(i)).stripTrailingZeros().toPlainString();
            sb.append(Component.translatable("gui.stockmarket.orderbook_volume_histogram.border." + BORDER_KEYS[i], price).getString());
        }
        if (!sb.isEmpty())
            drawTooltip(sb.toString(), getMousePos());
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
        if      (vol >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", vol / 1_000_000);
        else if (vol >= 1_000)     return String.format(Locale.ROOT, "%.1fk", vol / 1_000);
        else if (vol >= 1)         return String.format(Locale.ROOT, "%.0f", vol);
        else                       return String.format(Locale.ROOT, "%.2f", vol);
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
