package net.kroia.stockmarket.screen.widgets;

import com.mojang.math.Axis;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.market.core.DepthMemory;
import net.kroia.stockmarket.stockmarket.market.core.VirtualOrderbook;
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
    /** T-209: line colour per {@code VirtualOrderbook.BORDER_*} index: magenta = log grid edge, orange = log slot at the window, cyan = linear window, blue = re-centre borders. */
    private static final int[] BORDER_COLORS = {
            0xFFFF55FF, 0xFFFF9933, 0xFF33CCFF, 0xFF3355FF,
            0xFF3355FF, 0xFF33CCFF, 0xFFFF9933, 0xFFFF55FF
    };
    /** T-209: hover distance in pixels for a border tooltip. */
    private static final int BORDER_HOVER_PX = 2;
    /** T-209: thickness in pixels of the linear array lines (edges, re-centre borders, connecting vertical). */
    private static final int LINEAR_LINE_PX = 2;
    /** T-209: minimum pixel spacing of the log slot ticks; closer ticks are skipped (zoomed far out). */
    private static final int LOG_TICK_MIN_SPACING_PX = 3;
    /** T-209: hard cap of log slot iterations per frame. */
    private static final int LOG_TICK_MAX_ITERATIONS = 2000;
    /** T-209: log slot ticks drawn this frame as {slot k, raw start price, canvas y}, for the tooltip. */
    private final List<long[]> logTicks = new ArrayList<>();
    /** T-209: raw units per real price unit, derived from the log floor border (raw price 1). */
    private long logRawScale = 1;

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
            renderLogSlotTicks();
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

    /** T-209: @return true for the re-centre borders, drawn half width (from the middle to the right edge) */
    private static boolean isRecenterBorder(int i)
    {
        return i == VirtualOrderbook.BORDER_RECENTER_LO || i == VirtualOrderbook.BORDER_RECENTER_HI;
    }

    /** T-209: @return true for the borders of the linear array (edges and re-centre borders), drawn {@link #LINEAR_LINE_PX} thick */
    private static boolean isLinearBorder(int i)
    {
        return i >= VirtualOrderbook.BORDER_LINEAR_LO && i <= VirtualOrderbook.BORDER_LINEAR_HI;
    }

    /** T-209: @return the canvas X where a border line starts (half width for the re-centre borders) */
    private int borderStartX(int i)
    {
        return isRecenterBorder(i) ? scissorRect.x + scissorRect.width / 2 : scissorRect.x;
    }

    /**
     * T-209: draws the visible borders (off-screen ones are skipped). The linear array is drawn as
     * 2 px lines: full-width cyan edges joined by a vertical at 3/4 of the width (clipped to the
     * visible price range), and blue half-width re-centre borders crossing that vertical.
     * The log grid and ask ceiling markers stay 1 px full-width lines.
     */
    private void renderArrayBorders()
    {
        if (arrayBorders.isEmpty()) return;
        // Same height renderHistogram() uses; set here too so the lines show while the book is empty.
        scissorRect.height = parentChart.toCanvasSpaceY(parentChart.getMinVisiblePrice()) - padding;
        int n = Math.min(arrayBorders.size(), BORDER_COLORS.length);
        if (n > VirtualOrderbook.BORDER_LINEAR_HI)
        {
            double lo = arrayBorders.get(VirtualOrderbook.BORDER_LINEAR_LO);
            double hi = arrayBorders.get(VirtualOrderbook.BORDER_LINEAR_HI);
            double minVis = parentChart.getMinVisiblePrice(), maxVis = parentChart.getMaxVisiblePrice();
            if (!Double.isNaN(lo) && !Double.isNaN(hi) && hi >= minVis && lo <= maxVis)
            {
                int yTop = parentChart.toCanvasSpaceY(Math.min(hi, maxVis));
                int yBottom = parentChart.toCanvasSpaceY(Math.max(lo, minVis));
                int x = scissorRect.x + scissorRect.width * 3 / 4 - LINEAR_LINE_PX / 2;
                drawRect(x, Math.min(yTop, yBottom), LINEAR_LINE_PX, Math.abs(yBottom - yTop), BORDER_COLORS[VirtualOrderbook.BORDER_LINEAR_LO]);
            }
        }
        for (int i = 0; i < n; i++)
        {
            int y = borderY(i);
            if (y == Integer.MIN_VALUE) continue;
            int x = borderStartX(i);
            if (isLinearBorder(i))
                drawRect(x, y - LINEAR_LINE_PX / 2, scissorRect.x + scissorRect.width - x, LINEAR_LINE_PX, BORDER_COLORS[i]);
            else
                drawRect(x, y, scissorRect.width, 1, BORDER_COLORS[i]);
        }
    }

    /** T-209: tooltip naming every border within {@link #BORDER_HOVER_PX} of the mouse (and over its line), e.g. "linear ↑ 172.00". */
    private void renderArrayBorderTooltip()
    {
        if (arrayBorders.isEmpty() || !isMouseOver()) return;
        int my = getMouseY();
        int mx = getMouseX();
        StringBuilder sb = new StringBuilder();
        int n = Math.min(arrayBorders.size(), BORDER_KEYS.length);
        for (int i = 0; i < n; i++)
        {
            int y = borderY(i);
            if (y == Integer.MIN_VALUE || Math.abs(y - my) > BORDER_HOVER_PX || mx < borderStartX(i)) continue;
            if (!sb.isEmpty()) sb.append('\n');
            String price = BigDecimal.valueOf(arrayBorders.get(i)).stripTrailingZeros().toPlainString();
            sb.append(Component.translatable("gui.stockmarket.orderbook_volume_histogram.border." + BORDER_KEYS[i], price).getString());
        }
        // Log slot ticks (right half only), at most one: they are at least LOG_TICK_MIN_SPACING_PX apart
        if (mx >= scissorRect.x + scissorRect.width / 2)
        {
            for (long[] tick : logTicks)
            {
                if (Math.abs(tick[2] - my) > 1) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(Component.translatable("gui.stockmarket.orderbook_volume_histogram.border.log_slot", tick[0],
                        realPriceText(tick[1]), realPriceText(DepthMemory.slotEnd((int) tick[0]))).getString());
                break;
            }
        }
        if (!sb.isEmpty())
            drawTooltip(sb.toString(), getMousePos());
    }

    /** T-209: @return a raw price as real price text, using the scale from the log floor (raw 1) */
    private String realPriceText(long raw)
    {
        return BigDecimal.valueOf(raw).divide(BigDecimal.valueOf(logRawScale)).stripTrailingZeros().toPlainString();
    }

    /**
     * T-209: draws a half-width orange tick at the start of every log grid slot ({@link DepthMemory})
     * in the visible range outside the linear window, from the log floor up to the ask ceiling.
     * The grid is fixed and global, so it is derived here from the borders the response already sends
     * (the log floor is raw price 1, which also gives the raw/real scale). Ticks closer than
     * {@link #LOG_TICK_MIN_SPACING_PX} to the last drawn one are skipped (thinning when zoomed out).
     * Fills {@link #logTicks} for the tooltip.
     */
    private void renderLogSlotTicks()
    {
        logTicks.clear();
        if (arrayBorders.size() < VirtualOrderbook.BORDER_COUNT) return;
        double floor = arrayBorders.get(VirtualOrderbook.BORDER_LOG_FLOOR);
        if (Double.isNaN(floor) || !(floor > 0)) return; // depth memory off: no log grid
        logRawScale = Math.max(1, Math.round(1 / floor));
        double linLo = arrayBorders.get(VirtualOrderbook.BORDER_LINEAR_LO);
        double linHi = arrayBorders.get(VirtualOrderbook.BORDER_LINEAR_HI);
        double ceiling = arrayBorders.get(VirtualOrderbook.BORDER_ASK_CEILING);
        double minVis = Math.max(parentChart.getMinVisiblePrice(), floor);
        double maxVis = parentChart.getMaxVisiblePrice();
        if (!Double.isNaN(ceiling)) maxVis = Math.min(maxVis, ceiling);
        if (!(maxVis >= minVis) || maxVis * logRawScale >= Long.MAX_VALUE / 2) return;
        long rawLo = Math.max(1, (long) Math.ceil(minVis * logRawScale));
        long rawHi = (long) Math.floor(maxVis * logRawScale);
        if (rawLo > rawHi) return;
        int kLo = DepthMemory.slotOf(rawLo), kHi = DepthMemory.slotOf(rawHi);
        long linLoRaw = Math.round(linLo * logRawScale), linHiRaw = Math.round(linHi * logRawScale);
        int x = scissorRect.x + scissorRect.width / 2;
        int w = scissorRect.width - scissorRect.width / 2;
        int lastY = Integer.MIN_VALUE;
        // Stride keeps a frame under LOG_TICK_MAX_ITERATIONS even across the whole grid.
        int stride = Math.max(1, (kHi - kLo + 1 + LOG_TICK_MAX_ITERATIONS - 1) / LOG_TICK_MAX_ITERATIONS);
        for (int k = kLo; k <= kHi; k += stride)
        {
            long start = DepthMemory.slotStart(k);
            if (start < rawLo || start > rawHi) continue;
            if (DepthMemory.slotStart(k + 1) <= start) continue;        // empty slot (low raw prices)
            if (start >= linLoRaw && start <= linHiRaw) continue;      // inside the linear window
            int y = parentChart.toCanvasSpaceY(start / (double) logRawScale);
            if (lastY != Integer.MIN_VALUE && Math.abs(y - lastY) < LOG_TICK_MIN_SPACING_PX) continue;
            drawRect(x, y, w, 1, BORDER_COLORS[VirtualOrderbook.BORDER_LOG_SLOT_BELOW]);
            logTicks.add(new long[]{k, start, y});
            lastY = y;
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
