package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.geometry.RectangleF;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import net.kroia.modutilities.gui.InputConstants;



import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CandlestickChart extends StockMarketGuiElement {

    // ── Constants ──

    public static final int colorGreen          = UI_Colors.buyColorGreen;
    public static final int colorRed            = UI_Colors.sellColorRed;
    public static final int colorHorizontalLine = UI_Colors.candlestickChart_horizontalLine;
    public static final int colorZeroLine       = UI_Colors.candlestickChart_zeroLine;
    public static final int colorCurrentPrice   = UI_Colors.candlestickChart_currentPriceLine;
    public static final SimpleDateFormat dayFormat   = new SimpleDateFormat("dd.", Locale.getDefault());
    public static final SimpleDateFormat monthFormat = new SimpleDateFormat(" MMMM ", Locale.getDefault());
    public static final SimpleDateFormat yearFormat  = new SimpleDateFormat("yyyy", Locale.getDefault());
    public static final SimpleDateFormat timeFormat  = new SimpleDateFormat("HH:mm", Locale.getDefault());
    // Number of candles shown by default when a market is opened for the first time
    private static final int DEFAULT_VISIBLE_CANDLES = 50;

    // ── Overlay interfaces ──

    @FunctionalInterface
    public interface Overlay {
        void render(CandlestickChart chart);
    }

    /**
     * Extended overlay that can intercept mouse events on the chart canvas.
     * If any interactive overlay consumes an event, the chart's own input handling
     * (panning / dragging) is skipped for that event.
     */
    public interface InteractiveOverlay extends Overlay {
        boolean mouseClicked(CandlestickChart chart, int mouseX, int mouseY, int button);
        boolean mouseDragged(CandlestickChart chart, int mouseX, int mouseY, int button, double deltaX, double deltaY);
        boolean mouseReleased(CandlestickChart chart, int mouseX, int mouseY, int button);
    }

    // ── Fields ──

    // Saved viewport state per market item, so switching between markets preserves the user's view
    private record ViewportState(double viewX, double viewY, double viewWidth, double viewHeight,
                                 double zoomLevel, int candleTimeIdx) {}

    private static final Map<String, ViewportState> savedViewports = new HashMap<>();

    private final List<Overlay> overlays = new ArrayList<>();

    // The data source for candle/price information (ClientMarket, CrossRateMarket, etc.)
    private @Nullable IPriceDataProvider priceDataProvider;
    // Cached downcast for backward compat with OrderbookVolumeHistogram and other code
    // that still needs a ClientMarket reference; null when the provider is not a ClientMarket.
    private @Nullable ClientMarket clientMarket;
    private @Nullable PriceHistoryData data;
    private int currentCandleTimeIdx = 0;

    int candleWidth = 12;
    private final Rectangle canvasRect = new Rectangle(1, 1, 0, 0);
    private final Rectangle canvasScissorRect = new Rectangle(1, 1, 0, 0);
    private final Rectangle volumeRect = new Rectangle(1, 1, 0, 0);
    private final Rectangle volumeScissorRect = new Rectangle(1, 1, 0, 0);
    private double zoomLevel = 200;
    private final RectangleF chartviewRect = new RectangleF(200, 0, 200, 500);
    private final Point lastDragMousePos = new Point();
    private int maxPriceLabelTextWidth = 0;
    private double currentPriceStep = 0;
    private int firstVisibleCandleIndex = 0;
    private int lastVisibleCandleIndex = 0;
    private int maxTimeDateLabelWidth = 0;
    private boolean firstDraw = false;
    private boolean dragging = false;
    private boolean skipAutoCenterOnce = false;

    private final List<Button> candleTimeSelectButtons = new ArrayList<>();
    private final int defaultButtonBackgroundColor = ColorUtilities.setAlpha(DEFAULT_BACKGROUND_COLOR, 1.0f);

    // Lang key for the "loading older data" indicator shown at the left edge
    // of the chart while a paginated fetch of older candles is in flight.
    private static final String KEY_LOADING_OLDER = "stockmarket.candlestick.loadingOlder";

    // True while a paginated "older data" fetch is in flight for the current
    // candle-delta. Toggled true when we fire the request and reset via
    // whenComplete on the returned future — see maybeRequestOlderData.
    private final AtomicBoolean loadingOlder = new AtomicBoolean(false);

    // Candle-time deltas (in ms) for which a one-time auto-fit is pending.
    // Populated in selectCandleTimeDeltaByIndex when we fire an initial
    // paginated fetch for an empty delta (i.e. the user switched to a delta
    // that has no cached candles yet). Consumed in renderBackground once
    // the response has landed and PriceHistoryData is populated: at that
    // point we set firstDraw=true so the existing "fit DEFAULT_VISIBLE_CANDLES
    // + autoCenterView" code path in renderCandles runs exactly like it does
    // on initial chart open. Cleared on market switch (see setPriceDataProvider)
    // so pending flags never leak across (market, delta) boundaries.
    // Explicitly does NOT get populated by T-135 older-chunk pagination —
    // that path never enters selectCandleTimeDeltaByIndex, so the view is
    // preserved during pan-left.
    private final Set<Long> pendingAutoFitDeltas = new HashSet<>();

    /**
     * Candle-time deltas (in ms) that have already had a first-time vertical
     * fit applied for the CURRENT market. Once a delta lands here it is never
     * auto-fitted again for this market — subsequent user selections of the
     * same delta (cache-hit switch or reclick of the currently active delta)
     * leave the viewport untouched.
     *
     * <p>Populated at every point where the viewport is made valid for a delta:
     * <ul>
     *   <li>{@link #renderBackground()} when it consumes a
     *       {@link #pendingAutoFitDeltas} entry (data-arrival path).</li>
     *   <li>{@link #setPriceDataProvider(IPriceDataProvider)} inside the
     *       cache-warm restore-failed branch after the manual firstDraw
     *       setup, and via {@link #restoreViewportState(String)} on the
     *       viewport-restore path.</li>
     *   <li>{@link #deserializeViewport(CompoundTag)} when a persisted
     *       viewport is applied.</li>
     * </ul>
     *
     * <p>Cleared alongside {@link #pendingAutoFitDeltas} in
     * {@link #setPriceDataProvider(IPriceDataProvider)} so per-market
     * isolation is preserved. The spacebar handler does NOT touch this set —
     * spacebar is an explicit manual override that always fits regardless.
     */
    private final Set<Long> deltasEverFitted = new HashSet<>();

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

    public void addOverlay(Overlay overlay) { overlays.add(overlay); }

    public void removeOverlay(Overlay overlay) { overlays.remove(overlay); }

    /**
     * Sets the price data provider for the chart.
     * Works with any IPriceDataProvider implementation — both single-market (ClientMarket)
     * and cross-rate (CrossRateMarket). The chart is a pure display widget that delegates
     * all price/candle data retrieval to the provider.
     *
     * @param provider the data source, or null to clear the chart
     */
    public void setPriceDataProvider(@Nullable IPriceDataProvider provider) {
        saveViewportState();
        this.priceDataProvider = provider;
        this.clientMarket = (provider instanceof ClientMarket cm) ? cm : null;
        this.data = null;
        // Drop any pending auto-fit flags from the previous market so they
        // don't spuriously refit the view when the new market happens to have
        // matching cached deltas. Per-(market, delta) tracking is enforced by
        // clearing on every switch. deltasEverFitted is cleared too so that
        // first-time auto-fit gating is fresh per market.
        pendingAutoFitDeltas.clear();
        deltasEverFitted.clear();

        if (provider != null) {
            if (!restoreViewportState(provider.getViewportKey())) {
                skipAutoCenterOnce = true;
                selectCandleTimeDeltaByIndex(currentCandleTimeIdx);
                if (data != null && !data.getCandles().isEmpty()) {
                    int targetCandles = Math.min(DEFAULT_VISIBLE_CANDLES, data.getCandles().size());
                    chartviewRect.width = Math.max(targetCandles, 1);
                    zoomLevel = chartviewRect.width;
                    chartviewRect.x = chartviewRect.width;
                    // Viewport is now valid for this delta — record so future
                    // reselects of the same delta don't refit.
                    long initialDelta = ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx];
                    deltasEverFitted.add(initialDelta);
                }
                firstDraw = true;
            }
        }
    }

    /**
     * Convenience method for single-market mode. Delegates to {@link #setPriceDataProvider}.
     *
     * @param market the ClientMarket to display, or null to clear the chart
     */
    public void setMarket(@Nullable ClientMarket market) {
        setPriceDataProvider(market);
    }

    /**
     * Returns the underlying ClientMarket if the current provider is a ClientMarket, or null otherwise.
     * Used by OrderbookVolumeHistogram for orderbook depth requests.
     */
    public @Nullable ClientMarket getMarket() {
        return clientMarket;
    }

    /**
     * Returns the current price data provider (ClientMarket, CrossRateMarket, etc.).
     *
     * @return the active provider, or null if no provider is set
     */
    public @Nullable IPriceDataProvider getPriceDataProvider() {
        return priceDataProvider;
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

    /**
     * Selects the candle time resolution by exact delta value (in milliseconds).
     * Fetches new candle data from the current provider if available.
     *
     * @param timeDeltaMs the candle period in milliseconds
     */
    public void selectCandleTimeDelta(int timeDeltaMs) {
        if (priceDataProvider != null) {
            PriceHistoryData newData = priceDataProvider.getPriceHistoryData(timeDeltaMs);
            if (newData != null)
                this.data = newData;
        }
    }

    /**
     * Selects the candle time resolution by index into the available time deltas array.
     * Fetches new data from the provider and updates the toolbar button highlighting.
     *
     * @param index the index into {@link ClientMarket#getAvailableCandleTimeDeltas()}
     */
    public void selectCandleTimeDeltaByIndex(int index) {
        // Same-delta reclick guard: when the user clicks the button for the
        // already-active delta AND its data is already loaded, do NOTHING —
        // no data refetch, no autoCenterView, no button-color repaint (colors
        // are already correct by definition). Skipping the whole method here
        // prevents the spurious vertical recenter the user was seeing on
        // reclicks. We deliberately still fall through when data is null or
        // empty so the initial "fire the fetch" path (below) can run on the
        // constructor's bootstrap call and on any post-market-swap situation
        // where the same index needs re-initialising.
        if (index == currentCandleTimeIdx
                && priceDataProvider != null
                && data != null && !data.getCandles().isEmpty()) {
            return;
        }

        currentCandleTimeIdx = index;
        if (priceDataProvider != null) {
            long deltaTime = ClientMarket.getAvailableCandleTimeDeltas()[index];
            this.data = priceDataProvider.getPriceHistoryData(deltaTime);

            // Has this delta ever had its viewport fitted for the current
            // market? If yes, the user has seen a valid view of it before
            // and we must NOT auto-refit on subsequent selections.
            boolean everFitted = deltasEverFitted.contains(deltaTime);

            // If the newly-selected delta has no cached candles yet, fire an
            // initial paginated fetch so the chart populates on first view.
            // We use requestOlderData(delta, Long.MAX_VALUE) rather than the
            // ClientMarket-specific requestInitialWindow so the widget stays
            // provider-agnostic (works for any IPriceDataProvider — the
            // interface default is a no-op for providers without pagination).
            if ((data == null || data.getCandles().isEmpty())
                    && priceDataProvider.hasMoreOlderData(deltaTime)) {
                // Only queue the one-shot auto-fit for deltas that have NEVER
                // been fitted for the current market. If the user has already
                // seen this delta once, we intentionally leave the viewport
                // alone even after a fresh async fetch — matches spec: fit
                // fires only "for the very first time per market".
                if (!everFitted) {
                    pendingAutoFitDeltas.add(deltaTime);
                }
                loadingOlder.set(true);
                priceDataProvider.requestOlderData(deltaTime, Long.MAX_VALUE)
                        .whenComplete((r, e) -> loadingOlder.set(false));
            }

            if (skipAutoCenterOnce) {
                skipAutoCenterOnce = false;
            } else if (!everFitted && data != null && !data.getCandles().isEmpty()) {
                // First-ever selection of this delta with data already cached:
                // fit the viewport once and remember that we did. Cache-hit
                // switches on subsequent selections skip this branch and leave
                // the viewport exactly as the user last configured it.
                autoCenterView();
                deltasEverFitted.add(deltaTime);
            }
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

        // Refresh data from provider each frame to pick up live price updates
        // and new candles (CrossRateMarket rebuilds PriceHistoryData on recompute)
        if (priceDataProvider != null) {
            long deltaTime = ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx];
            PriceHistoryData freshData = priceDataProvider.getPriceHistoryData(deltaTime);
            if (freshData != null)
                this.data = freshData;

            // First-time delta-switch auto-fit: when the initial paginated fetch
            // triggered by selectCandleTimeDeltaByIndex lands and populates the
            // cache, refit the view exactly once. Set-based check-and-remove
            // guarantees a single fit per (market, delta) — subsequent T-135
            // older-chunk loads never re-enter this branch because that path
            // does not populate pendingAutoFitDeltas. Setting firstDraw=true
            // reuses the same "fit DEFAULT_VISIBLE_CANDLES + autoCenterView"
            // code path in renderCandles that initial chart open uses.
            //
            // Recording the delta in deltasEverFitted at consumption time is
            // what closes the loop: any later reselection of this same delta
            // (cache-hit switch, or reclick of the currently active button)
            // will observe everFitted==true and skip the auto-fit entirely.
            if (data != null && !data.getCandles().isEmpty()
                    && pendingAutoFitDeltas.remove(deltaTime)) {
                firstDraw = true;
                deltasEverFitted.add(deltaTime);
            }
        }

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
        renderVolumeBars();
        renderCurrentPriceMarker();
        int frameColor = ColorUtilities.setAlpha(colorZeroLine, 1.0f);
        drawFrame(canvasRect, frameColor, 1);
        drawFrame(volumeRect, frameColor, 1);

        // Render plugin overlays on top of chart
        enableScissor(canvasScissorRect);
        for (Overlay overlay : overlays) {
            overlay.render(this);
        }
        disableScissor();

        renderLoadingOlderIndicator();
    }

    /**
     * Renders a small text label at the left edge of the plot area (inside
     * the chart's border) while a paginated fetch of older candle data is
     * in flight. Text-only — no textures.
     */
    private void renderLoadingOlderIndicator() {
        if (!loadingOlder.get()) return;
        String label = Component.translatable(KEY_LOADING_OLDER).getString();
        int textX = canvasRect.x + 4;
        int textY = canvasRect.y + 4;
        drawText(label, textX, textY);
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
            int candleCount = data.getCandles().size();
            int targetCandles = Math.min(DEFAULT_VISIBLE_CANDLES, candleCount);
            chartviewRect.width = Math.max(targetCandles, 1);
            zoomLevel = chartviewRect.width;
            chartviewRect.x = chartviewRect.width; // Snap the view to the newest candle
            // Vertical fit: replicate the spacebar reset action so the chart
            // ends up in the same state as if the user pressed spacebar right
            // after the data arrived. We can't rely on firstVisibleCandleIndex /
            // lastVisibleCandleIndex here because the loop above computed them
            // using the OLD chartviewRect.width (from the previous delta), so
            // their span does not match the new DEFAULT_VISIBLE_CANDLES window.
            // Compute the will-be-visible index range explicitly instead.
            int lastIdx = candleCount - 1;
            int firstIdx = Math.max(0, candleCount - targetCandles);
            applyVerticalFitToRange(firstIdx, lastIdx);
        }
    }

    private void renderVolumeBars() {
        if (data == null || data.getCandles().isEmpty())
            return;
        List<PriceHistoryData.Candle> candles = data.getCandles();
        int candleCount = candles.size();

        float maxVolume = 0;
        for (int i = firstVisibleCandleIndex; i <= lastVisibleCandleIndex; i++) {
            if (i >= 0 && i < candleCount)
                maxVolume = Math.max(maxVolume, candles.get(i).tradedVolume);
        }
        if (maxVolume <= 0)
            return;

        int volumeBase = volumeRect.y + volumeRect.height;
        float norm = (float) volumeRect.height / maxVolume;

        enableScissor(volumeScissorRect);

        // Same pattern as renderCandles: track lastXPos to eliminate gaps between bars
        double closePrice = data.getCurrentMarketRealPrice();
        int lastXPos = toCanvasSpaceX(0) - candleWidth * 2;
        for (int i = candleCount - 1; i >= 0; i--) {
            int xPos = toCanvasSpaceX(candleCount - i - 1) - candleWidth;
            int barWidth = Math.abs(xPos - lastXPos);
            lastXPos = xPos;

            if (xPos < 0 || (xPos + barWidth - 1) > (volumeRect.x + volumeRect.width))
                continue;

            PriceHistoryData.Candle candle = candles.get(i);
            int barHeight = (int) (candle.tradedVolume * norm);
            if (barHeight < 1 && candle.tradedVolume > 0) barHeight = 1;

            boolean bullish = closePrice >= data.toRealPrice(candle.open);
            drawRect(xPos, volumeBase - barHeight, barWidth, barHeight, bullish ? colorGreen : colorRed);
            closePrice = data.toRealPrice(candle.open);
        }

        disableScissor();
    }

    private void renderCurrentPriceMarker() {
        if (data == null)
            return;
        double currentPrice = data.getCurrentMarketRealPrice();
        int yPos = toCanvasSpaceY(currentPrice);
        if (yPos < canvasRect.y || yPos > canvasRect.y + canvasRect.height)
            return;

        // Dashed horizontal line across the chart canvas
        enableScissor(canvasScissorRect);
        int dashWidth = 4;
        int gapWidth = 4;
        for (int x = canvasRect.x; x < canvasRect.x + canvasRect.width; x += dashWidth + gapWidth) {
            int w = Math.min(dashWidth, canvasRect.x + canvasRect.width - x);
            drawRect(x, yPos, w, 1, colorCurrentPrice);
        }
        disableScissor();

        // Price label to the right of the chart (same position as grid labels)
        String priceLabel = formatPrice(currentPrice);
        int textWidth = getTextWidth(priceLabel);
        int textHeight = getTextHeight();
        int labelX = canvasRect.x + canvasRect.width + 2;
        int labelY = yPos - textHeight / 2;
        drawRect(labelX, labelY, textWidth + 6, textHeight, colorCurrentPrice);
        drawText(priceLabel, labelX + 3, labelY, 0xFFFFFFFF, getTextFontScale());
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
        currentPriceStep = niceStep;
        maxPriceLabelTextWidth = 0;
        scissorPause();
        for (double price = firstLine; price <= topPrice; price += niceStep) {
            renderHorizontalValueLabel(price);
        }
        scissorResume();
        maxPriceLabelTextWidth += 10;
        int totalWidth = Math.max(2, ((getWidth() - maxPriceLabelTextWidth) / 2) * 2);
        int totalHeight = Math.max(1, (getHeight() - maxTimeDateLabelWidth));
        int volumeHeight = totalHeight / 8;
        int candleHeight = totalHeight - volumeHeight;

        canvasRect.width = totalWidth;
        canvasRect.height = candleHeight;
        canvasScissorRect.x = canvasRect.x + 1;
        canvasScissorRect.y = canvasRect.y + 1;
        canvasScissorRect.width = canvasRect.width - 1;
        canvasScissorRect.height = canvasRect.height - 1;

        volumeRect.x = canvasRect.x;
        volumeRect.y = canvasRect.y + candleHeight;
        volumeRect.width = totalWidth;
        volumeRect.height = volumeHeight;
        volumeScissorRect.x = volumeRect.x + 1;
        volumeScissorRect.y = volumeRect.y + 1;
        volumeScissorRect.width = volumeRect.width - 1;
        volumeScissorRect.height = volumeRect.height - 1;
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
        int canvasY = volumeRect.y + volumeRect.height;
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
        if (!isKeyPressed(InputConstants.KEY_LEFT_CONTROL)) {
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
            maybeRequestOlderData();
            consumed = true;
        }

        // Vertical zoom (disabled when holding Shift for horizontal-only zoom)
        if (!isKeyPressed(InputConstants.KEY_LEFT_SHIFT)) {
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
        // Forward to interactive overlays first — if consumed, skip chart panning
        int mx = getMouseX();
        int my = getMouseY();
        for (Overlay overlay : overlays) {
            if (overlay instanceof InteractiveOverlay interactive) {
                if (interactive.mouseClicked(this, mx, my, button)) {
                    return true;
                }
            }
        }

        lastDragMousePos.x = mx;
        lastDragMousePos.y = my;
        dragging = true;
        return true;
    }

    @Override
    protected void mouseReleased(int button) {
        // Forward to interactive overlays first
        int mx = getMouseX();
        int my = getMouseY();
        for (Overlay overlay : overlays) {
            if (overlay instanceof InteractiveOverlay interactive) {
                if (interactive.mouseReleased(this, mx, my, button)) {
                    return;
                }
            }
        }

        dragging = false;
    }

    @Override
    protected boolean mouseDragged(int button, double deltaX, double deltaY) {
        // Forward to interactive overlays first — if consumed, skip chart panning
        int mx = getMouseX();
        int my = getMouseY();
        for (Overlay overlay : overlays) {
            if (overlay instanceof InteractiveOverlay interactive) {
                if (interactive.mouseDragged(this, mx, my, button, deltaX, deltaY)) {
                    return true;
                }
            }
        }

        if (!dragging || data == null)
            return false;

        if (button == InputConstants.MOUSE_BUTTON_LEFT && (mx != lastDragMousePos.x || my != lastDragMousePos.y)) {
            int dx = lastDragMousePos.x - mx;
            int dy = lastDragMousePos.y - my;
            lastDragMousePos.x = mx;
            lastDragMousePos.y = my;

            // Convert pixel delta to world space delta
            double worldDeltaX = dx * chartviewRect.width / canvasRect.width;
            double worldDeltaY = dy * chartviewRect.height / canvasRect.height;

            // Subtract because dragging right should move the view left
            chartviewRect.x -= worldDeltaX;
            chartviewRect.y = Math.max(0, chartviewRect.y - worldDeltaY);

            clampViewToNewestCandle();
            clampViewToOldestCandle();
            maybeRequestOlderData();

            return true;
        }
        return false;
    }

    @Override
    protected boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlKeyDown = isKeyPressed(InputConstants.KEY_LEFT_CONTROL);
        if (keyCode == 32 && !ctrlKeyDown) {
            if (data != null) {
                // Recenter vertical range over currently visible candles
                applyVerticalFitToRange(firstVisibleCandleIndex, lastVisibleCandleIndex);
                return true;
            }
        } else if (keyCode == 32) {
            if (data != null) {
                autoCenterView();
                return true;
            }
        }
        return false;
    }

    // ── Viewport persistence ──

    /**
     * Serializes the current viewport state into a CompoundTag for NBT storage.
     */
    protected CompoundTag serializeViewport() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("viewX", chartviewRect.x);
        tag.putDouble("viewY", chartviewRect.y);
        tag.putDouble("viewW", chartviewRect.width);
        tag.putDouble("viewH", chartviewRect.height);
        tag.putDouble("zoom", zoomLevel);
        tag.putInt("candleTimeIdx", currentCandleTimeIdx);
        return tag;
    }

    /**
     * Restores viewport state from a CompoundTag previously created by
     * {@link #serializeViewport()}. Skips auto-centering after applying.
     */
    protected void deserializeViewport(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        chartviewRect.x = tag.getDouble("viewX");
        chartviewRect.y = tag.getDouble("viewY");
        chartviewRect.width = tag.getDouble("viewW");
        chartviewRect.height = tag.getDouble("viewH");
        zoomLevel = tag.getDouble("zoom");
        skipAutoCenterOnce = true;
        selectCandleTimeDeltaByIndex(tag.getInt("candleTimeIdx"));
        // The deserialised viewport is an authoritative placement — mark this
        // delta as fitted so future reselects don't clobber it with a fresh
        // auto-fit.
        deltasEverFitted.add(ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx]);
        firstDraw = false;
    }

    /**
     * Looks up a previously saved viewport state by key and returns it as a
     * CompoundTag, or null if no state is saved for that key. Allows subclasses
     * to read another chart's viewport (e.g. copying the TradeScreen's viewport
     * as initial state for a display chart).
     */
    protected static CompoundTag lookupSavedViewport(String viewportKey) {
        ViewportState state = savedViewports.get(viewportKey);
        if (state == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putDouble("viewX", state.viewX);
        tag.putDouble("viewY", state.viewY);
        tag.putDouble("viewW", state.viewWidth);
        tag.putDouble("viewH", state.viewHeight);
        tag.putDouble("zoom", state.zoomLevel);
        tag.putInt("candleTimeIdx", state.candleTimeIdx);
        return tag;
    }

    /**
     * Saves the current viewport state (position, zoom, candle time index)
     * into the static map, keyed by the current provider's viewport key.
     */
    private void saveViewportState() {
        if (this.priceDataProvider != null) {
            savedViewports.put(this.priceDataProvider.getViewportKey(), new ViewportState(
                    chartviewRect.x, chartviewRect.y,
                    chartviewRect.width, chartviewRect.height,
                    zoomLevel, currentCandleTimeIdx));
        }
    }

    /**
     * Restores a previously saved viewport state for the given key.
     * @return true if a saved state existed and was restored
     */
    private boolean restoreViewportState(String viewportKey) {
        ViewportState state = savedViewports.get(viewportKey);
        if (state == null)
            return false;

        chartviewRect.x = state.viewX;
        chartviewRect.y = state.viewY;
        chartviewRect.width = state.viewWidth;
        chartviewRect.height = state.viewHeight;
        zoomLevel = state.zoomLevel;
        currentCandleTimeIdx = state.candleTimeIdx;

        // Load the correct candle data and update button colors without auto-centering
        skipAutoCenterOnce = true;
        selectCandleTimeDeltaByIndex(currentCandleTimeIdx);
        // The restored viewport is a user-authored placement — mark this delta
        // as fitted so any future reselection leaves it alone rather than
        // re-fitting on top of the user's carefully saved view.
        deltasEverFitted.add(ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx]);
        firstDraw = false;
        return true;
    }

    // ── View management ──

    /**
     * Fits the vertical viewport range to the price min/max of the given
     * candle index range, adding 10% padding on both sides. This is the
     * same computation the spacebar reset action performs; it is extracted
     * so the initial "firstDraw" auto-fit (after a delta switch's data lands)
     * can reuse it with an explicit index range — the
     * {@code firstVisibleCandleIndex} / {@code lastVisibleCandleIndex} fields
     * are only valid for the previous frame's viewport width and so cannot
     * be used at the moment we resize the window.
     *
     * <p>The passed indices are clamped internally by
     * {@link PriceHistoryData#getMinPrice(int, int)} and
     * {@link PriceHistoryData#getMaxPrice(int, int)}, so out-of-range or
     * inverted arguments are safe.
     *
     * @param firstIndexInclusive index of the first candle to include (0-based)
     * @param lastIndexInclusive  index of the last candle to include (0-based)
     */
    private void applyVerticalFitToRange(int firstIndexInclusive, int lastIndexInclusive) {
        if (data == null) return;
        double maxPrice = data.toRealPrice(data.getMaxPrice(firstIndexInclusive, lastIndexInclusive));
        double minPrice = data.toRealPrice(data.getMinPrice(firstIndexInclusive, lastIndexInclusive));
        double priceDifference = maxPrice - minPrice;
        chartviewRect.y = Math.max(0, minPrice - priceDifference * 0.1);
        chartviewRect.height = (maxPrice + priceDifference * 0.1) - chartviewRect.y;
    }

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
     * <p>
     * When the current provider still has older data available on the server
     * (per {@link IPriceDataProvider#hasMoreOlderData(long)}), this method
     * intentionally does NOT clamp — instead, the panning/scroll input path
     * calls {@link #maybeRequestOlderData()} to page in older candles, and
     * the view is allowed to sit at the edge until the response arrives and
     * the loaded range naturally expands.
     * <p>
     * Only when the provider reports no more older data (server-start floor
     * reached, or no provider) does this method clamp exactly as before.
     */
    private void clampViewToOldestCandle() {
        if (data == null) return;
        int candleCount = data.getCandles().size();
        long deltaMs = ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx];
        boolean canPaginate = priceDataProvider != null
                && priceDataProvider.hasMoreOlderData(deltaMs);
        if (canPaginate) {
            return;
        }
        if (chartviewRect.x > candleCount + chartviewRect.width - 1) {
            chartviewRect.x = candleCount + chartviewRect.width - 1;
        }
    }

    /**
     * Trigger a paginated fetch of older candles if the visible left edge has
     * approached the oldest loaded candle and the provider still has older
     * data available.
     * <p>
     * Fire-and-forget: {@link ClientMarket} debounces and coalesces concurrent
     * calls per candle delta, so the widget can safely invoke this on every
     * drag/scroll event without extra guarding. The returned future is
     * observed only to reset the {@link #loadingOlder} indicator flag.
     * <p>
     * The safety margin (in candle units) means we start fetching a few
     * candles BEFORE the user actually hits the oldest-visible edge, so the
     * response typically arrives before the user drags past it.
     */
    private void maybeRequestOlderData() {
        if (priceDataProvider == null || data == null) return;

        long deltaMs = ClientMarket.getAvailableCandleTimeDeltas()[currentCandleTimeIdx];
        if (!priceDataProvider.hasMoreOlderData(deltaMs)) return;

        List<PriceHistoryData.Candle> candles = data.getCandles();

        if (candles.isEmpty()) {
            // No candles rendered — the initial window came back empty even
            // though the server does have older data (world loaded days after
            // last save, initial 8.5 h window missed the actual data range).
            // Without a loaded candle to anchor on, the "am I near the left
            // edge" check below can never trigger, so pan-left alone would
            // stay stuck forever. Use the Long.MAX_VALUE bootstrap sentinel
            // — ClientMarket.requestOlderWindow interprets it as "fetch the
            // server's newest N candles regardless of time range" and jumps
            // straight to where the data lives.
            loadingOlder.set(true);
            priceDataProvider.requestOlderData(deltaMs, Long.MAX_VALUE)
                    .whenComplete((r, e) -> loadingOlder.set(false));
            return;
        }

        int candleCount = candles.size();
        // Safety margin: whichever is larger — 5 candles or 10% of the
        // visible view width. Small enough not to spam the server on every
        // wiggle, large enough to hide most round-trip latency.
        double safetyMarginCandles = Math.max(5.0, chartviewRect.width * 0.1);
        double leftEdgeCandleIndex = chartviewRect.x - chartviewRect.width;
        if (leftEdgeCandleIndex >= (candleCount - 1) - safetyMarginCandles) {
            long oldestTimestamp = candles.get(0).openTimestamp;
            loadingOlder.set(true);
            priceDataProvider.requestOlderData(deltaMs, oldestTimestamp)
                    .whenComplete((r, e) -> loadingOlder.set(false));
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
        int maxWidth = 0;
        for (String line : el) {
            int w = super.getTextWidth(line);
            if (w > maxWidth)
                maxWidth = w;
        }
        return maxWidth;
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
        if      (price >= 1_000_000_000) return formatCompact(price / 1_000_000_000, 1_000_000_000, "B");
        else if (price >= 1_000_000)     return formatCompact(price / 1_000_000,     1_000_000,     "M");
        else if (price >= 1_000)         return formatCompact(price / 1_000,         1_000,         "k");
        else                             return formatCompact(price, 1, "");
    }

    private String formatCompact(double value, double divisor, String suffix) {
        int decimals = 0;
        if (currentPriceStep > 0) {
            double stepInUnits = currentPriceStep / divisor;
            if (stepInUnits > 0 && stepInUnits < 1) {
                decimals = Math.max(0, (int) Math.ceil(-Math.log10(stepInUnits)));
            }
        }
        if (value >= 100)     decimals = Math.max(decimals, 0);
        else if (value >= 10) decimals = Math.max(decimals, 1);
        else                  decimals = Math.max(decimals, 2);
        String formatted = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (decimals > 3) {
            int dot = formatted.indexOf('.');
            if (dot < 0) return formatted + suffix;
            String intPart = formatted.substring(0, dot + 1);
            String decPart = formatted.substring(dot + 1);
            StringBuilder sb = new StringBuilder(intPart);
            for (int i = 0; i < decPart.length(); i++) {
                if (i > 0 && i % 3 == 0) sb.append('\'');
                sb.append(decPart.charAt(i));
            }
            formatted = sb.toString();
        }
        return formatted + suffix;
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
