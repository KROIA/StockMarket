package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Line chart that displays the cross-rate between two markets over time.
 * Used in pair-mode trading (Item-to-Item) where the rate is computed as:
 *   rate = wantClosePrice / haveClosePrice
 *
 * For each matching candle timestamp the close prices from both markets
 * are divided to produce a single rate data point, then connected as a line.
 */
public class CrossRateChart extends StockMarketGuiElement {

    // ── Constants ──

    private static final int LINE_COLOR = ColorUtilities.getRGB(100, 180, 255);
    private static final int GRID_LINE_COLOR = UI_Colors.candlestickChart_horizontalLine;
    private static final int CURRENT_RATE_COLOR = UI_Colors.candlestickChart_currentPriceLine;
    private static final int AXIS_FRAME_COLOR = ColorUtilities.setAlpha(UI_Colors.candlestickChart_zeroLine, 255);

    private static final SimpleDateFormat dayFormat   = new SimpleDateFormat("dd.", Locale.getDefault());
    private static final SimpleDateFormat monthFormat  = new SimpleDateFormat(" MMMM ", Locale.getDefault());
    private static final SimpleDateFormat yearFormat   = new SimpleDateFormat("yyyy", Locale.getDefault());
    private static final SimpleDateFormat timeFormat   = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ── Fields ──

    private @Nullable ClientMarket haveMarket;
    private @Nullable ClientMarket wantMarket;
    private int currentCandleTimeIdx = 0;

    // Computed cross-rate data points
    private final List<RatePoint> ratePoints = new ArrayList<>();

    // Canvas area for the line chart (excludes axes labels)
    private final Rectangle canvasRect = new Rectangle(1, 1, 0, 0);
    private final Rectangle canvasScissorRect = new Rectangle(1, 1, 0, 0);

    // Auto-fit view bounds
    private double viewMinRate = 0;
    private double viewMaxRate = 1;

    // Space reserved for axis labels
    private int maxRateLabelWidth = 0;
    private int maxTimeLabelHeight = 0;

    // Time period selector buttons (same set as CandlestickChart)
    private final List<Button> candleTimeSelectButtons = new ArrayList<>();
    private final int defaultButtonBackgroundColor = ColorUtilities.setAlpha(DEFAULT_BACKGROUND_COLOR, 1.0f);

    /**
     * A single computed cross-rate data point derived from matching candle timestamps.
     */
    private record RatePoint(long timestamp, double rate) {}

    // ── Constructor ──

    public CrossRateChart() {
        this.setTextFontScale(0.8f);
        long[] candleTimes = ClientMarket.getAvailableCandleTimeDeltas();
        float buttonFontSizeScale = 1.0f;

        for (int i = 0; i < candleTimes.length; i++) {
            String timeStr = timeToShortString(candleTimes[i]);
            int finalI = i;
            Button button = new Button(timeStr, () -> {
                selectCandleTimeDeltaByIndex(finalI);
            });
            candleTimeSelectButtons.add(button);
            addChild(button);

            button.setTextFontScale(buttonFontSizeScale);
            int textWidth = getTextWidth(timeStr);
            int textHeight = button.getTextHeight();
            button.setWidth(textWidth + 2 * padding);
            button.setHeight(textHeight + 2 * padding);
            button.setBackgroundColor(ColorUtilities.setAlpha(button.getBackgroundColor(), 1.0f));
            button.setPressedColor(ColorUtilities.setAlpha(button.getPressedColor(), 1.0f));
            button.setHoverColor(ColorUtilities.setAlpha(button.getHoverColor(), 1.0f));
        }

        selectCandleTimeDeltaByIndex(currentCandleTimeIdx);
    }

    // ── Public API ──

    /**
     * Sets the two markets whose cross-rate will be displayed.
     * @param haveMarket the "have" side market (denominator)
     * @param wantMarket the "want" side market (numerator)
     */
    public void setMarkets(@Nullable ClientMarket haveMarket, @Nullable ClientMarket wantMarket) {
        this.haveMarket = haveMarket;
        this.wantMarket = wantMarket;
        recomputeRateData();
    }

    public @Nullable ClientMarket getHaveMarket() {
        return haveMarket;
    }

    public @Nullable ClientMarket getWantMarket() {
        return wantMarket;
    }

    /**
     * Selects the candle time period by index and recomputes the rate data.
     */
    public void selectCandleTimeDeltaByIndex(int index) {
        currentCandleTimeIdx = index;
        for (int i = 0; i < candleTimeSelectButtons.size(); i++) {
            Button button = candleTimeSelectButtons.get(i);
            int buttonColor = (i == index) ? button.getPressedColor() : defaultButtonBackgroundColor;
            candleTimeSelectButtons.get(i).setBackgroundColor(buttonColor);
        }
        recomputeRateData();
    }

    // ── Layout ──

    @Override
    protected void layoutChanged() {
        int currentXPos = padding;
        int currentYPos = padding;
        for (Button button : candleTimeSelectButtons) {
            button.setPosition(currentXPos, currentYPos);
            currentXPos += button.getWidth() + spacing;
        }
    }

    // ── Data computation ──

    /**
     * Recomputes the cross-rate data points from both markets' candle histories.
     * For each candle index, the rate is: wantClose / haveClose.
     * The close price is approximated by the current market price for the latest candle,
     * and the open price for historical candles (matching how CandlestickChart treats close).
     */
    private void recomputeRateData() {
        ratePoints.clear();
        if (haveMarket == null || wantMarket == null) return;

        long deltaTime = ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx];
        PriceHistoryData haveData = haveMarket.getPriceHistoryData(deltaTime);
        PriceHistoryData wantData = wantMarket.getPriceHistoryData(deltaTime);
        if (haveData == null || wantData == null) return;

        List<PriceHistoryData.Candle> haveCandles = haveData.getCandles();
        List<PriceHistoryData.Candle> wantCandles = wantData.getCandles();
        if (haveCandles.isEmpty() || wantCandles.isEmpty()) return;

        // Walk both candle lists using a two-pointer merge on timestamps.
        // Only produce a rate point where both markets have a candle at the same time slot.
        int hi = 0;
        int wi = 0;
        while (hi < haveCandles.size() && wi < wantCandles.size()) {
            PriceHistoryData.Candle hc = haveCandles.get(hi);
            PriceHistoryData.Candle wc = wantCandles.get(wi);

            // Use the candle delta time as the tolerance window for matching timestamps
            long timeDiff = hc.openTimestamp - wc.openTimestamp;
            long tolerance = deltaTime / 2;

            if (Math.abs(timeDiff) <= tolerance) {
                // Matching candles -- compute close prices.
                // For the latest candle, use current market price; for older ones, use open of next candle
                // (same convention CandlestickChart uses). Simplified: use open as close approximation.
                double haveClose;
                double wantClose;

                if (hi == haveCandles.size() - 1) {
                    haveClose = haveData.getCurrentMarketRealPrice();
                } else {
                    haveClose = haveData.toRealPrice(haveCandles.get(hi + 1).open);
                }

                if (wi == wantCandles.size() - 1) {
                    wantClose = wantData.getCurrentMarketRealPrice();
                } else {
                    wantClose = wantData.toRealPrice(wantCandles.get(wi + 1).open);
                }

                if (haveClose > 0) {
                    double rate = wantClose / haveClose;
                    ratePoints.add(new RatePoint(hc.openTimestamp, rate));
                }
                hi++;
                wi++;
            } else if (timeDiff > 0) {
                // have is ahead -- advance want
                wi++;
            } else {
                // want is ahead -- advance have
                hi++;
            }
        }

        // Auto-fit the view to the computed data
        autoFitView();
    }

    /**
     * Sets the view bounds to encompass all rate data with a 10% margin.
     */
    private void autoFitView() {
        if (ratePoints.isEmpty()) {
            viewMinRate = 0;
            viewMaxRate = 1;
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (RatePoint rp : ratePoints) {
            min = Math.min(min, rp.rate);
            max = Math.max(max, rp.rate);
        }

        double range = max - min;
        if (range < 1e-9) range = max * 0.1; // Avoid zero-range for flat data
        if (range < 1e-9) range = 1.0;

        viewMinRate = Math.max(0, min - range * 0.1);
        viewMaxRate = max + range * 0.1;
    }

    // ── Rendering ──

    @Override
    protected void renderBackground() {
        super.renderBackground();

        // Recompute rate data each frame to pick up live price updates
        recomputeRateData();

        if (ratePoints.size() < 2) return;

        // Compute canvas dimensions (reserve space for labels)
        computeCanvasBounds();

        // Draw grid and axes
        renderHorizontalGrid();
        renderVerticalGrid();

        // Draw the rate line
        renderRateLine();

        // Draw current rate marker
        renderCurrentRateMarker();

        // Draw frame around canvas
        drawFrame(canvasRect, AXIS_FRAME_COLOR, 1);
    }

    @Override
    protected void render() {
        // No foreground rendering needed
    }

    /**
     * Computes the canvas rectangle, reserving space for rate labels on the right
     * and time labels at the bottom.
     */
    private void computeCanvasBounds() {
        // Estimate rate label width from the view bounds
        maxRateLabelWidth = Math.max(getTextWidth(formatRate(viewMinRate)), getTextWidth(formatRate(viewMaxRate))) + 10;
        maxTimeLabelHeight = getTextHeight() + 10;

        int totalWidth = Math.max(2, getWidth() - maxRateLabelWidth);
        int totalHeight = Math.max(2, getHeight() - maxTimeLabelHeight);

        canvasRect.x = 1;
        canvasRect.y = 1;
        canvasRect.width = totalWidth;
        canvasRect.height = totalHeight;

        canvasScissorRect.x = canvasRect.x + 1;
        canvasScissorRect.y = canvasRect.y + 1;
        canvasScissorRect.width = canvasRect.width - 1;
        canvasScissorRect.height = canvasRect.height - 1;
    }

    /**
     * Draws horizontal grid lines with rate labels on the right axis.
     */
    private void renderHorizontalGrid() {
        int targetLineCount = 8;
        double visibleRange = viewMaxRate - viewMinRate;
        double rawStep = visibleRange / targetLineCount;

        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;

        double niceStep;
        if      (normalized < 1.5) niceStep = 1  * magnitude;
        else if (normalized < 3.5) niceStep = 2  * magnitude;
        else if (normalized < 7.5) niceStep = 5  * magnitude;
        else                       niceStep = 10 * magnitude;

        double firstLine = Math.floor(viewMinRate / niceStep) * niceStep;

        for (double rate = firstLine; rate <= viewMaxRate; rate += niceStep) {
            int yPos = rateToCanvasY(rate);
            if (yPos < canvasRect.y || yPos > canvasRect.y + canvasRect.height)
                continue;

            // Grid line
            enableScissor(canvasScissorRect);
            drawRect(canvasRect.x, yPos, canvasRect.width, 1, GRID_LINE_COLOR);
            disableScissor();

            // Rate label to the right
            String label = formatRate(rate);
            int textHeight = getTextHeight();
            drawText(label, canvasRect.x + canvasRect.width + 5, yPos - textHeight / 2);
        }
    }

    /**
     * Draws vertical grid lines with time labels at the bottom.
     */
    private void renderVerticalGrid() {
        if (ratePoints.size() < 2) return;

        int targetLineCount = Math.min(10, ratePoints.size());
        int step = Math.max(1, ratePoints.size() / targetLineCount);

        int canvasBottom = canvasRect.y + canvasRect.height;
        Date lastTime = new Date(0);

        for (int i = 0; i < ratePoints.size(); i += step) {
            // Snap the last step to the final point
            if (i >= ratePoints.size() - step && step > 1) {
                i = ratePoints.size() - 1;
            }

            RatePoint rp = ratePoints.get(i);
            int xPos = indexToCanvasX(i);
            if (xPos < canvasRect.x || xPos > canvasRect.x + canvasRect.width)
                continue;

            // Vertical grid line
            enableScissor(canvasScissorRect);
            int dashHeight = 4;
            int gapHeight = 4;
            for (int y = canvasRect.y; y < canvasBottom; y += dashHeight + gapHeight) {
                int h = Math.min(dashHeight, canvasBottom - y);
                drawRect(xPos, y, 1, h, GRID_LINE_COLOR);
            }
            disableScissor();

            // Time label below the canvas
            Date currentTime = new Date(rp.timestamp);
            String[] timeDateStr = timestampToTimeDate(currentTime, lastTime);
            lastTime = currentTime;

            int textHeight = getTextHeight();
            for (int j = 0; j < timeDateStr.length; j++) {
                int yPos = canvasBottom + 2 + (j * textHeight);
                int textWidth = getTextWidth(timeDateStr[j]);
                drawText(timeDateStr[j], Math.max(canvasRect.x, xPos - textWidth / 2), yPos);
            }
        }
    }

    /**
     * Draws the rate line by connecting consecutive data points.
     */
    private void renderRateLine() {
        if (ratePoints.size() < 2) return;

        enableScissor(canvasScissorRect);

        for (int i = 1; i < ratePoints.size(); i++) {
            int x1 = indexToCanvasX(i - 1);
            int y1 = rateToCanvasY(ratePoints.get(i - 1).rate);
            int x2 = indexToCanvasX(i);
            int y2 = rateToCanvasY(ratePoints.get(i).rate);

            drawLine(x1, y1, x2, y2, 1.5f, LINE_COLOR);
        }

        disableScissor();
    }

    /**
     * Draws a dashed horizontal line at the current rate with a label.
     */
    private void renderCurrentRateMarker() {
        if (ratePoints.isEmpty()) return;
        if (haveMarket == null || wantMarket == null) return;

        double havePrice = haveMarket.getCurrentMarketRealPrice();
        double wantPrice = wantMarket.getCurrentMarketRealPrice();
        if (havePrice <= 0) return;

        double currentRate = wantPrice / havePrice;
        int yPos = rateToCanvasY(currentRate);
        if (yPos < canvasRect.y || yPos > canvasRect.y + canvasRect.height)
            return;

        // Dashed horizontal line
        enableScissor(canvasScissorRect);
        int dashWidth = 4;
        int gapWidth = 4;
        for (int x = canvasRect.x; x < canvasRect.x + canvasRect.width; x += dashWidth + gapWidth) {
            int w = Math.min(dashWidth, canvasRect.x + canvasRect.width - x);
            drawRect(x, yPos, w, 1, CURRENT_RATE_COLOR);
        }
        disableScissor();

        // Rate label to the right
        String label = formatRate(currentRate);
        int textWidth = getTextWidth(label);
        int textHeight = getTextHeight();
        int labelX = canvasRect.x + canvasRect.width + 2;
        int labelY = yPos - textHeight / 2;
        drawRect(labelX, labelY, textWidth + 6, textHeight, CURRENT_RATE_COLOR);
        drawText(label, labelX + 3, labelY, 0xFFFFFFFF, getTextFontScale());
    }

    // ── Coordinate conversion ──

    /**
     * Converts a data point index to canvas X coordinate.
     * Index 0 is leftmost, last index is rightmost.
     */
    private int indexToCanvasX(int index) {
        int count = ratePoints.size();
        if (count <= 1) return canvasRect.x + canvasRect.width / 2;
        return canvasRect.x + (int)((long)(canvasRect.width - 2) * index / (count - 1)) + 1;
    }

    /**
     * Converts a rate value to canvas Y coordinate.
     * Higher rates are drawn higher (lower Y pixel value).
     */
    private int rateToCanvasY(double rate) {
        double range = viewMaxRate - viewMinRate;
        if (range <= 0) return canvasRect.y + canvasRect.height / 2;
        double normalized = (rate - viewMinRate) / range; // 0 = bottom, 1 = top
        return canvasRect.y + canvasRect.height - 1 - (int)(normalized * (canvasRect.height - 2));
    }

    // ── Formatting utilities ──

    private String formatRate(double rate) {
        if      (rate >= 1_000_000) return String.format("%.1fM", rate / 1_000_000);
        else if (rate >= 1_000)     return String.format("%.1fk", rate / 1_000);
        else if (rate >= 100)       return String.format("%.1f",  rate);
        else if (rate >= 10)        return String.format("%.2f",  rate);
        else if (rate >= 1)         return String.format("%.3f",  rate);
        else if (rate >= 0.01)      return String.format("%.4f",  rate);
        else                        return String.format("%.6f",  rate);
    }

    private String[] timestampToTimeDate(Date currentTime, Date lastTime) {
        String currentTimeStr  = timeFormat.format(currentTime);
        String currentDayStr   = dayFormat.format(currentTime);
        String currentMonthStr = monthFormat.format(currentTime);
        String currentYearStr  = yearFormat.format(currentTime);

        String lastDayStr   = dayFormat.format(lastTime);
        String lastMonthStr = monthFormat.format(lastTime);
        String lastYearStr  = yearFormat.format(lastTime);

        String dateStr = "";
        if (!lastYearStr.equals(currentYearStr))
            dateStr += currentYearStr + "\n";
        if (!lastMonthStr.equals(currentMonthStr))
            dateStr += currentMonthStr + "\n";
        if (!lastDayStr.equals(currentDayStr))
            dateStr += currentDayStr + "\n";

        if (dateStr.isEmpty())
            return new String[]{currentTimeStr};

        return (dateStr + currentTimeStr).split("\n");
    }

    private String timeToShortString(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long minutes      = totalSeconds / 60;
        long hours        = minutes / 60;
        long days         = hours / 24;
        long weeks        = days / 7;
        long months       = days / 30;
        long years        = days / 365;

        long remMonths  = months % 12;
        long remWeeks   = weeks % 4;
        long remDays    = days % 7;
        long remHours   = hours % 24;
        long remMinutes = minutes % 60;
        long remSeconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (years     > 0) sb.append(years).append("Y ");
        if (remMonths > 0) sb.append(remMonths).append("M ");
        if (remWeeks  > 0) sb.append(remWeeks).append("W ");
        if (remDays   > 0) sb.append(remDays).append("D ");
        if (remHours  > 0) sb.append(remHours).append("h ");
        if (remMinutes > 0) sb.append(remMinutes).append("m ");
        if (remSeconds > 0) sb.append(remSeconds).append("s");

        if (sb.isEmpty()) return "0s";

        return sb.toString().trim();
    }
}
