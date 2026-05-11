package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.geometry.RectangleF;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CandlestickChart extends StockMarketGuiElement {

    // ── Constants ──

    public static final int colorGreen          = UI_Colors.buyColorGreen;
    public static final int colorRed            = UI_Colors.sellColorRed;
    public static final int colorHorizontalLine = UI_Colors.candlestickChart_horizontalLine;
    public static final int colorZeroLine       = UI_Colors.candlestickChart_zeroLine;
    public static final SimpleDateFormat dayFormat   = new SimpleDateFormat("dd.", Locale.getDefault());
    public static final SimpleDateFormat monthFormat = new SimpleDateFormat(" MMMM ", Locale.getDefault());
    public static final SimpleDateFormat yearFormat  = new SimpleDateFormat("yyyy", Locale.getDefault());
    public static final SimpleDateFormat timeFormat  = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ── Overlay interface ──

    @FunctionalInterface
    public interface Overlay {
        void render(CandlestickChart chart);
    }

    // ── Fields ──

    private final List<Overlay> overlays = new ArrayList<>();

    private @Nullable ClientMarket market;
    private @Nullable PriceHistoryData data;
    private int currentCandleTimeIdx = 0;

    int candleWidth = 12;
    private final Rectangle canvasRect = new Rectangle(1, 1, 0, 0);
    private final Rectangle canvasScissorRect = new Rectangle(1, 1, 0, 0);
    private double zoomLevel = 200;
    private final RectangleF chartviewRect = new RectangleF(200, 0, 200, 500);
    private final Point lastDragMousePos = new Point();
    private int maxPriceLabelTextWidth = 0;
    private int firstVisibleCandleIndex = 0;
    private int lastVisibleCandleIndex = 0;
    private int maxTimeDateLabelWidth = 0;
    private boolean firstDraw = false;
    private boolean dragging = false;

    private final List<Button> candleTimeSelectButtons = new ArrayList<>();
    private final int defaultButtonBackgroundColor = ColorUtilities.setAlpha(DEFAULT_BACKGROUND_COLOR, 1.0f);

    // ── Constructor ──

    public CandlestickChart() {
        this.setTextFontScale(0.8f);
        long[] candleTimes = ClientMarket.getAvailableCandleTimeDeltas();
        float buttonFontSizeScale = 1.0f;

        for (int i = 0; i < candleTimes.length; i++) {
            String timeStr = timeToShortString(candleTimes[i]);
            int finalI = i;
            Button button = new Button(timeStr, () -> {
                selectCandleTimeDeltaByIndex(finalI);
            });

            button.setTextFontScale(buttonFontSizeScale);
            int textWidth = getTextWidth(timeStr);
            int textHeight = button.getTextHeight();
            button.setWidth(textWidth + 2 * padding);
            button.setHeight(textHeight + 2 * padding);
            button.setBackgroundColor(ColorUtilities.setAlpha(button.getBackgroundColor(), 1.0f));
            button.setPressedColor(ColorUtilities.setAlpha(button.getPressedColor(), 1.0f));
            button.setHoverColor(ColorUtilities.setAlpha(button.getHoverColor(), 1.0f));

            candleTimeSelectButtons.add(button);
            addChild(button);
        }

        selectCandleTimeDeltaByIndex(currentCandleTimeIdx);
    }

    // ── Public API ──

    public void addOverlay(Overlay overlay) { overlays.add(overlay); }

    public void removeOverlay(Overlay overlay) { overlays.remove(overlay); }

    public void setMarket(@Nullable ClientMarket market) {
        this.market = market;
        this.data = null;
        if (market != null) {
            selectCandleTimeDeltaByIndex(currentCandleTimeIdx);
            firstDraw = true;
        }
    }

    public @Nullable ClientMarket getMarket() {
        return market;
    }

    public double getMinVisiblePrice() {
        return fromCanvasSpaceY(canvasRect.y + canvasRect.height);
    }

    public double getMaxVisiblePrice() {
        return fromCanvasSpaceY(canvasRect.y + 1);
    }

    /**
     * Returns a copy of the canvas drawing area bounds.
     * Plugin developers can use this together with toCanvasSpaceX/Y
     * to draw custom overlays on top of the chart.
     */
    public Rectangle getCanvasBounds() {
        return new Rectangle(canvasRect.x, canvasRect.y, canvasRect.width, canvasRect.height);
    }

    public void selectCandleTimeDelta(int timeDeltaMs) {
        if (market != null) {
            PriceHistoryData newData = market.getPriceHistoryData(timeDeltaMs);
            if (newData != null)
                this.data = newData;
        }
    }

    public void selectCandleTimeDeltaByIndex(int index) {
        currentCandleTimeIdx = index;
        if (market != null) {
            long deltaTime = ClientMarket.getAvailableCandleTimeDeltas()[index];
            this.data = market.getPriceHistoryData(deltaTime);
            autoCenterView();
        }
        for (int i = 0; i < candleTimeSelectButtons.size(); i++) {
            Button button = candleTimeSelectButtons.get(i);
            int buttonColor = (i == index) ? button.getPressedColor() : defaultButtonBackgroundColor;
            candleTimeSelectButtons.get(i).setBackgroundColor(buttonColor);
        }
    }

    // ── Layout ──

    @Override
    protected void layoutChanged() {
        int padding = StockMarketGuiElement.padding;
        int selectTimingButtonSpacing = spacing;

        int currentXPos = padding;
        int currentYPos = padding;
        for (Button candleTimeSelectButton : candleTimeSelectButtons) {
            candleTimeSelectButton.setPosition(currentXPos, currentYPos);
            currentXPos += candleTimeSelectButton.getWidth() + selectTimingButtonSpacing;
        }
    }

    // ── Rendering ──

    @Override
    protected void renderBackground() {
        super.renderBackground();
        if (data == null || data.getCandles().isEmpty())
            return;
        lastVisibleCandleIndex = Math.min(data.getCandles().size() - 1, lastVisibleCandleIndex);
        firstVisibleCandleIndex = Math.min(data.getCandles().size() - 1, firstVisibleCandleIndex);

        enableScissor(canvasScissorRect);
        drawRect(canvasRect.x, toCanvasSpaceY(0) - 2, canvasRect.width, 2, colorZeroLine);

        renderChartHorizontalBackground();
        disableScissor();
        renderChartVerticalBackground();

        renderCandles();
        drawFrame(canvasRect, ColorUtilities.setAlpha(colorZeroLine, 1.0f), 1);

        // Render plugin overlays on top of chart
        enableScissor(canvasScissorRect);
        for (Overlay overlay : overlays) {
            overlay.render(this);
        }
        disableScissor();
    }

    @Override
    protected void render() {
    }

    private void renderCandles() {
        if (data.getCandles().isEmpty())
            return;
        enableScissor(canvasScissorRect);
        candleWidth = (Math.abs(canvasRect.width / (int) chartviewRect.width));

        lastVisibleCandleIndex = -1;
        firstVisibleCandleIndex = 0;
        List<PriceHistoryData.Candle> candles = data.getCandles();

        if (!candles.isEmpty()) {
            double closePrice = data.getCurrentMarketRealPrice();
            int candleCount = candles.size();
            int lastCandleXPos = toCanvasSpaceX(0) - candleWidth * 2;

            boolean startedVisible = false;
            for (int i = candleCount - 1; i >= 0; i--) {
                PriceHistoryData.Candle candle = candles.get(i);
                int newCandleXPos = toCanvasSpaceX(candleCount - i - 1) - candleWidth;
                boolean isVisible = renderCandlestick(newCandleXPos, lastCandleXPos,
                        data.toRealPrice(candle.open),
                        data.toRealPrice(candle.high),
                        data.toRealPrice(candle.low),
                        closePrice);
                lastCandleXPos = newCandleXPos;
                if (isVisible && !startedVisible) {
                    lastVisibleCandleIndex = i;
                    startedVisible = true;
                }
                if (startedVisible && !isVisible) {
                    firstVisibleCandleIndex = i + 1;
                    break;
                }
                closePrice = data.toRealPrice(candle.open);
            }
        }
        disableScissor();
        if (firstDraw) {
            firstDraw = false;
            autoCenterView();
        }
    }

    private void renderChartHorizontalBackground() {
        int targetLineCount = 8;

        double visiblePriceRange = chartviewRect.height;
        double rawStep = visiblePriceRange / targetLineCount;

        // Round to a "nice" number: nearest 1, 2, or 5 times a power of 10
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;

        double niceStep;
        if      (normalized < 1.5) niceStep = 1  * magnitude;
        else if (normalized < 3.5) niceStep = 2  * magnitude;
        else if (normalized < 7.5) niceStep = 5  * magnitude;
        else                       niceStep = 10 * magnitude;

        // Find the first price level just below the visible bottom
        double firstLine = Math.floor(chartviewRect.y / niceStep) * niceStep;

        // Draw all lines within the visible range
        double topPrice = chartviewRect.y + chartviewRect.height;
        maxPriceLabelTextWidth = 0;
        scissorPause();
        for (double price = firstLine; price <= topPrice; price += niceStep) {
            renderHorizontalValueLabel(price);
        }
        scissorResume();
        maxPriceLabelTextWidth += 10;
        canvasRect.width = Math.max(2, ((getWidth() - maxPriceLabelTextWidth) / 2) * 2);
        canvasRect.height = Math.max(1, (getHeight() - maxTimeDateLabelWidth));
        canvasScissorRect.width = canvasRect.width - 1;
        canvasScissorRect.x = canvasRect.x + 1;
        canvasScissorRect.y = canvasRect.y + 1;
        canvasScissorRect.height = canvasRect.height - 1;
        for (double price = firstLine; price <= topPrice; price += niceStep) {
            renderHorizontalValueLine(price);
        }
    }

    private void renderChartVerticalBackground() {
        int targetLineCount = 10;
        int visibleTimeStamps = (int) chartviewRect.width;

        double rawStep = (double) visibleTimeStamps / targetLineCount;

        // Round to a "nice" number: nearest 1, 2, or 5 times a power of 10
        double magnitude = Math.min(1, Math.pow(10, Math.floor(Math.log10(rawStep))));
        double normalized = rawStep / magnitude;

        int niceStep;
        if      (normalized < 1.5) niceStep = (int) (1  * magnitude);
        else if (normalized < 3.5) niceStep = (int) (2  * magnitude);
        else if (normalized < 7.5) niceStep = (int) (5  * magnitude);
        else                       niceStep = (int) (10 * magnitude);

        maxPriceLabelTextWidth = 0;
        List<PriceHistoryData.Candle> candles = data.getCandles();
        int candleCount = candles.size();
        int offset = Math.max(1, (lastVisibleCandleIndex - firstVisibleCandleIndex) / targetLineCount);

        Date lastTime = new Date(0);
        maxTimeDateLabelWidth = 0;
        int canvasY = canvasRect.y + canvasRect.height;
        int i = firstVisibleCandleIndex;

        for (; i <= lastVisibleCandleIndex; i += offset) {
            if (i >= lastVisibleCandleIndex - offset && offset > 1) {
                i = lastVisibleCandleIndex;
            }

            PriceHistoryData.Candle candle = candles.get(i);
            int canvasX = toCanvasSpaceX(candleCount - i - 1);

            Date currentTime = new Date(candle.openTimestamp);
            String[] timeDateStr = timestampToTimeDate(currentTime, lastTime);
            lastTime = currentTime;

            int textHeight = getTextHeight();
            int lineOffset = Math.max(canvasY, canvasY + 1);

            for (int j = 0; j < timeDateStr.length; j++) {
                int yPos = lineOffset + (j * textHeight);
                int textWidth = getTextWidth(timeDateStr[j]);
                maxTimeDateLabelWidth = Math.max(maxTimeDateLabelWidth, textWidth);
                drawText(timeDateStr[j], Math.max(canvasRect.x, canvasX - (candleWidth + textWidth) / 2), yPos);
            }
        }
        maxTimeDateLabelWidth += 10;
    }

    private void renderHorizontalValueLabel(double price) {
        if (price < 0)
            return;
        int textHeight = getTextHeight();
        int yPos = toCanvasSpaceY(price);
        if (yPos > canvasRect.y + canvasRect.height || yPos - textHeight / 2 < canvasRect.y)
            return;
        String priceLabel = formatPrice(price);
        int textWidth = getTextWidth(priceLabel);

        drawText(priceLabel, canvasRect.x + canvasRect.width + 5, yPos - textHeight / 2);
        maxPriceLabelTextWidth = Math.max(maxPriceLabelTextWidth, textWidth);
    }

    private void renderHorizontalValueLine(double price) {
        if (price <= 0)
            return;
        int yPos = toCanvasSpaceY(price);
        drawRect(canvasRect.x, yPos - 1, canvasRect.width, 2, colorHorizontalLine);
    }

    private boolean renderCandlestick(int newCandleXPos, int lastCandleXPos, double openPrice, double highPrice, double lowPrice, double closePrice) {
        int xOffset = newCandleXPos;
        int localCandleWidth = Math.abs(newCandleXPos - lastCandleXPos);

        if (xOffset < 0 || (xOffset + localCandleWidth - 1) > (canvasRect.x + canvasRect.width))
            return false;

        int color = openPrice > closePrice ? colorRed : colorGreen;

        int wickYMin = toCanvasSpaceY(lowPrice);
        int wickYMax = toCanvasSpaceY(highPrice);

        int bodyYMin = toCanvasSpaceY(Math.min(openPrice, closePrice));
        int bodyYMax = toCanvasSpaceY(Math.max(openPrice, closePrice));

        if (bodyYMin == bodyYMax) {
            bodyYMin++;
            bodyYMax--;
        }

        int wickWidth = localCandleWidth / 4;
        if (wickWidth < 1) {
            wickWidth = 1;
        } else if (wickWidth > 11) {
            wickWidth = 11;
        }
        wickWidth |= 1; // Make sure it is odd

        int wickOffset = (localCandleWidth - wickWidth) / 2;
        if (wickOffset <= 0) {
            wickOffset = 0;
        }

        if ((bodyYMax) - wickYMax > 0) {
            drawRect(xOffset + wickOffset, bodyYMax,
                    wickWidth, wickYMax - bodyYMax, color);
        }

        if (wickYMin - (bodyYMin) > 0) {
            drawRect(xOffset + wickOffset, bodyYMin,
                    wickWidth, wickYMin - bodyYMin, color);
        }

        drawRect(xOffset, bodyYMin,
                localCandleWidth, bodyYMax - bodyYMin, color);

        return true;
    }

    // ── Input handling ──

    @Override
    protected boolean mouseScrolledOverElement(double delta) {
        if (data == null)
            return false;

        double zoomFactor = (delta > 0) ? 0.9 : 1.1;
        double mouseWorldX = fromCanvasSpaceX(getMouseX());
        double mouseWorldY = fromCanvasSpaceY(getMouseY());

        boolean consumed = false;

        // Horizontal zoom (disabled when holding Ctrl for vertical-only zoom)
        if (!isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL)) {
            double minScrollValue = Math.min(canvasRect.width / 3.f, data.getCandles().size());
            zoomLevel = Math.max(Math.min(zoomLevel * zoomFactor, minScrollValue), 5.0f);

            if (zoomLevel < minScrollValue && zoomLevel > 5) {
                int newCandleWidth = (Math.abs(canvasRect.width / (int) zoomLevel) / 2) * 2;
                if (newCandleWidth > 0)
                    chartviewRect.width = (double) (((long) zoomLevel * newCandleWidth) / newCandleWidth);
                else
                    chartviewRect.width = (int) zoomLevel;

                // Shift the rect so the point under the mouse stays fixed
                double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
                chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
                clampViewToNewestCandle();
            } else {
                zoomLevel = Math.max(Math.min(zoomLevel, minScrollValue), 5.0f);
                chartviewRect.width = (int) zoomLevel;
                clampViewToNewestCandle();
            }

            clampViewToOldestCandle();
            consumed = true;
        }

        // Vertical zoom (disabled when holding Shift for horizontal-only zoom)
        if (!isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            double newHeight = chartviewRect.height * zoomFactor;
            if (newHeight < Long.MAX_VALUE && newHeight > 0.1) {
                chartviewRect.height = newHeight;

                // Shift the rect so the point under the mouse stays fixed
                double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);
                chartviewRect.y = Math.max(0, mouseWorldY - mouseNormY * chartviewRect.height);
            } else {
                chartviewRect.height = Math.max(Math.min(newHeight, Long.MAX_VALUE), 0.1);
            }
            consumed = true;
        }

        return consumed;
    }

    @Override
    protected boolean mouseClickedOverElement(int button) {
        lastDragMousePos.x = getMouseX();
        lastDragMousePos.y = getMouseY();
        dragging = true;
        return true;
    }

    @Override
    protected void mouseReleased(int button) {
        dragging = false;
    }

    @Override
    protected boolean mouseDragged(int button, double deltaX, double deltaY) {
        if (!dragging || data == null)
            return false;
        int mouseX = getMouseX();
        int mouseY = getMouseY();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (mouseX != lastDragMousePos.x || mouseY != lastDragMousePos.y)) {
            int dx = lastDragMousePos.x - mouseX;
            int dy = lastDragMousePos.y - mouseY;
            lastDragMousePos.x = mouseX;
            lastDragMousePos.y = mouseY;

            // Convert pixel delta to world space delta
            double worldDeltaX = dx * chartviewRect.width / canvasRect.width;
            double worldDeltaY = dy * chartviewRect.height / canvasRect.height;

            // Subtract because dragging right should move the view left
            chartviewRect.x -= worldDeltaX;
            chartviewRect.y = Math.max(0, chartviewRect.y - worldDeltaY);

            clampViewToNewestCandle();
            clampViewToOldestCandle();

            return true;
        }
        return false;
    }

    @Override
    protected boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlKeyDown = isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL);
        if (keyCode == GLFW.GLFW_KEY_SPACE && !ctrlKeyDown) {
            if (data != null) {
                // Recenter view based on the horizontal time
                double maxPrice = data.toRealPrice(data.getMaxPrice(firstVisibleCandleIndex, lastVisibleCandleIndex));
                double minPrice = data.toRealPrice(data.getMinPrice(firstVisibleCandleIndex, lastVisibleCandleIndex));

                double priceDifference = maxPrice - minPrice;
                chartviewRect.y = Math.max(0, minPrice - priceDifference * 0.1);
                chartviewRect.height = (maxPrice + priceDifference * 0.1) - chartviewRect.y;
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (data != null) {
                autoCenterView();
                return true;
            }
        }
        return false;
    }

    // ── View management ──

    public void autoCenterView() {
        if (data != null) {
            int lastIndex = data.getCandles().size();
            int firstIndex = lastIndex - (lastVisibleCandleIndex - firstVisibleCandleIndex);

            double maxPrice = data.toRealPrice(data.getMaxPrice(firstIndex, lastIndex));
            double minPrice = data.toRealPrice(data.getMinPrice(firstIndex, lastIndex));

            double priceDifference = maxPrice - minPrice;

            chartviewRect.y = Math.max(0, minPrice - priceDifference * 0.1);
            chartviewRect.height = Math.max((maxPrice + priceDifference * 0.1) - chartviewRect.y, 0.1);
            if (chartviewRect.width > lastIndex) {
                chartviewRect.width = lastIndex;
            }
            if (chartviewRect.width < 1)
                chartviewRect.width = 1;
            zoomLevel = chartviewRect.width;
            chartviewRect.x = chartviewRect.width; // Move to the newest candle
        }
    }

    /**
     * Prevents the view from scrolling past the newest candle (lower X bound).
     */
    private void clampViewToNewestCandle() {
        if (chartviewRect.x < chartviewRect.width)
            chartviewRect.x = chartviewRect.width;
    }

    /**
     * Prevents the view from scrolling past the oldest candle (upper X bound).
     */
    private void clampViewToOldestCandle() {
        if (data != null && chartviewRect.x > data.getCandles().size() + chartviewRect.width - 1) {
            chartviewRect.x = data.getCandles().size() + chartviewRect.width - 1;
        }
    }

    // ── Coordinate conversion ──

    public int toCanvasSpaceX(long time) {
        long in2 = ((long) chartviewRect.x - (long) chartviewRect.width);
        long in1 = (long) (chartviewRect.x);
        return (int) map(time, in1, in2, canvasRect.x + 1, canvasRect.x + canvasRect.width - 1);
    }

    public int toCanvasSpaceY(double price) {
        return (canvasRect.y + 1) - (int) (((price - (chartviewRect.y + chartviewRect.height)) * (canvasRect.height - 2)) / (chartviewRect.height));
    }

    public double fromCanvasSpaceX(int time) {
        return chartviewRect.x - (double) (time - (canvasRect.x + 1)) * (chartviewRect.width) / (canvasRect.width - 1);
    }

    public double fromCanvasSpaceY(int yPos) {
        return chartviewRect.y + chartviewRect.height - (double) (yPos - (canvasRect.y + 1)) * (chartviewRect.height) / (canvasRect.height - 2);
    }

    long map(long x, long in_min, long in_max, long out_min, long out_max) {
        if (in_min == in_max)
            return Long.MAX_VALUE;
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    // ── Formatting utilities ──

    @Override
    public int getTextWidth(String text) {
        String[] el = text.split("\n");
        int longestIndex = 0;
        int longestWidth = 0;
        for (int i = 0; i < el.length; i++) {
            if (el[i].length() > longestWidth) {
                longestWidth = el[i].length();
                longestIndex = i;
            }
        }
        return (int) ((float) getFont().width(el[longestIndex]) * getTextFontScale());
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

    private String formatPrice(double price) {
        if      (price >= 1_000_000_000) return formatCompact(price / 1_000_000_000, "B");
        else if (price >= 1_000_000)     return formatCompact(price / 1_000_000,     "M");
        else if (price >= 1_000)         return formatCompact(price / 1_000,         "k");
        else                             return String.format("%6.2f", price);
    }

    private String formatCompact(double value, String suffix) {
        if      (value >= 100) return String.format("%3.0f%s", value, suffix);
        else if (value >= 10)  return String.format("%4.1f%s", value, suffix);
        else                   return String.format("%4.2f%s", value, suffix);
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
