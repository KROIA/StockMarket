package net.kroia.stockmarket.screen.widgets;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.stream.PluginPerformanceSnapshot;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ClientPluginManager;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom widget that renders a horizontal, stacked timing bar for the plugin
 * system's per-tick performance snapshot (T-137).
 * <p>
 * The bar's full width represents one server tick
 * ({@link PluginPerformanceSnapshot#TICK_BUDGET_NS} = 50&nbsp;ms). For each plugin
 * in the latest snapshot delivered by {@link ClientPluginManager}, up to two
 * segments are drawn side-by-side:
 * <ul>
 *     <li>an <em>update-pass</em> segment in the plugin's stable base colour
 *         (derived from its instance UUID),</li>
 *     <li>a <em>finalisation-pass</em> segment in a darker shade of the same
 *         base colour.</li>
 * </ul>
 * Segments are stacked in execution order. If their combined width would exceed
 * the bar, only the visible portion is drawn and a red overflow marker is shown
 * at the right edge. A separate warning line is rendered below the bar when the
 * plugin loop as a whole exceeds the tick budget.
 * <p>
 * Hovering a segment displays a per-segment tooltip with the plugin name, pass
 * label, latest/average/peak timings, budget percentage and market count.
 * <p>
 * All text used by this widget goes through translation keys; see
 * {@code assets/stockmarket/lang/*.json}.
 *
 * <p><b>Threading:</b> {@link ClientPluginManager#getLatestTimingSnapshot()}
 * returns a volatile reference; {@code null} is expected until the first
 * snapshot arrives (the widget renders an empty placeholder in that case).
 */
public class PluginPerformanceBar extends StockMarketGuiElement {

    // ------------------------------------------------------------------------
    // Lang keys
    // ------------------------------------------------------------------------

    private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_performance_bar.";
    private static final String KEY_OVERLOAD        = PREFIX + "overload";
    /**
     * Dynamic label showing the current effective plugin budget vs. the 50 ms
     * tick hard cap (T-137). Two {@code %s} placeholders:
     * <ol>
     *     <li>the effective budget in whole milliseconds,</li>
     *     <li>the tick budget in whole milliseconds (always 50 today, but
     *         passed as an argument so it never needs to be hardcoded into
     *         the translated string).</li>
     * </ol>
     */
    private static final String KEY_LABEL_DYNAMIC   = PREFIX + "label_dynamic";
    private static final String KEY_PASS_UPDATE     = PREFIX + "pass_update";
    private static final String KEY_PASS_FINALIZE   = PREFIX + "pass_finalize";
    private static final String KEY_TOOLTIP_TIME    = PREFIX + "tooltip_time";
    private static final String KEY_TOOLTIP_AVG     = PREFIX + "tooltip_avg";
    private static final String KEY_TOOLTIP_PEAK    = PREFIX + "tooltip_peak";
    private static final String KEY_TOOLTIP_BUDGET  = PREFIX + "tooltip_budget";
    private static final String KEY_TOOLTIP_MARKETS = PREFIX + "tooltip_markets";

    // ------------------------------------------------------------------------
    // Layout / colour constants
    // ------------------------------------------------------------------------

    /** Height of the bar strip itself (px). */
    public static final int BAR_HEIGHT = 14;
    /**
     * Vertical space reserved for the overflow-warning line (px). Sized to fit
     * the warning text at {@link #WARNING_FONT_SCALE} = 0.7 (≈ ceil(9 * 0.7) = 7
     * px on the vanilla font). Always reserved even when the warning isn't
     * being drawn so the surrounding layout doesn't jitter when the plugin loop
     * crosses the tick budget.
     */
    public static final int WARNING_HEIGHT = 7;
    /**
     * Spacing between the bar and the warning line (px). Doubles as the widget's
     * trailing gap: since the warning line sits flush with the widget's lower
     * edge, this gap is the only vertical whitespace between the bar row and
     * the widget bottom.
     */
    private static final int BAR_WARNING_GAP = 2;
    /**
     * Total widget height including bar + separator + warning line. Screens
     * should reserve this much vertical space (matches the {@code bannerReserve}
     * pattern used elsewhere in {@code PluginManagementScreen}).
     */
    public static final int TOTAL_HEIGHT = BAR_HEIGHT + BAR_WARNING_GAP + WARNING_HEIGHT;

    private static final int EMPTY_BACKGROUND_COLOR = 0xFF1C1C1C;
    private static final int EMPTY_OUTLINE_COLOR    = 0xFF555555;
    private static final int BAR_OUTLINE_COLOR      = 0xFF000000;
    private static final int OVERFLOW_MARKER_COLOR  = 0xFFEE3333;
    private static final int OVERFLOW_TEXT_COLOR    = 0xFFEE4444;
    private static final int LABEL_TEXT_COLOR       = 0xFFDDDDDD;

    /** Font scale used to render the "overload" warning line. */
    private static final float WARNING_FONT_SCALE = 0.7f;
    /**
     * Font scale used to render the leftmost widget label ("Plugin tick time
     * budget"). Slightly smaller than the default so the label comfortably fits
     * inside {@link #LABEL_WIDTH} at all supported locales.
     */
    private static final float LABEL_FONT_SCALE = 0.85f;
    /**
     * Horizontal space reserved on the left for the widget label (px). The
     * actual timing bar is drawn starting at {@code LABEL_WIDTH + LABEL_GAP}.
     */
    private static final int LABEL_WIDTH = 140;
    /** Gap between the label and the timing bar (px). */
    private static final int LABEL_GAP = 4;

    // ------------------------------------------------------------------------
    // Hover hit-test cache
    // ------------------------------------------------------------------------

    /**
     * Rectangle covering one drawn segment, plus enough metadata to build the
     * per-segment tooltip on hover. Populated during every {@link #render()}
     * and consumed by the same {@code render()} call to hit-test the mouse.
     */
    private static final class SegmentRect {
        final int x;
        final int y;
        final int width;
        final int height;
        final PluginPerformanceSnapshot.Entry entry;
        final boolean isUpdatePass;

        SegmentRect(int x, int y, int width, int height,
                    PluginPerformanceSnapshot.Entry entry, boolean isUpdatePass) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.entry = entry;
            this.isUpdatePass = isUpdatePass;
        }
    }

    /** Reused each frame — never leaks across frames. */
    private final List<SegmentRect> segments = new ArrayList<>();

    /**
     * Constructs a plugin-performance bar widget. Background/outline are
     * disabled at the {@link StockMarketGuiElement} level because the widget
     * paints its own bar background inside {@link #render()}.
     */
    public PluginPerformanceBar() {
        super();
        setEnableBackground(false);
        setEnableOutline(false);
        setHeight(TOTAL_HEIGHT);
    }

    // ------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------

    @Override
    protected void render() {
        segments.clear();

        int width = getWidth();
        if (width <= 0) return;

        int barY = 0;
        int barH = BAR_HEIGHT;

        // Reserve the leftmost strip for the widget label; the timing bar starts
        // just to the right of it. Guard against tiny widget widths — if the
        // caller hands us less width than the label needs, fall back to a
        // zero-width bar so we at least still show the label + placeholder.
        int barLeft = Math.min(LABEL_WIDTH + LABEL_GAP, width);
        int barPixelBudget = Math.max(0, width - barLeft);

        // Fetch snapshot up front — the label depends on the effective budget,
        // which we get from the snapshot. Null (pre-first-packet) is handled
        // by falling back to the tick budget hard cap so the label still reads
        // sensibly ("50 / 50 ms") before the master has answered.
        PluginPerformanceSnapshot snapshot = fetchSnapshot();
        long tickBudgetNs = (snapshot != null && snapshot.tickBudgetNs > 0L)
                ? snapshot.tickBudgetNs
                : PluginPerformanceSnapshot.TICK_BUDGET_NS;
        long effectiveBudgetNs = snapshot != null
                ? snapshot.effectiveBudgetNs
                : tickBudgetNs;
        // Clamp defensively — a malformed payload could theoretically arrive
        // with an out-of-range effective budget; keep it inside [0, tickBudget].
        effectiveBudgetNs = Math.max(0L, Math.min(tickBudgetNs, effectiveBudgetNs));

        // Draw the left-hand label vertically centered against the bar strip.
        // Uses the translated KEY_LABEL_DYNAMIC string with the effective +
        // hard-cap millisecond values interpolated in Java (so translators
        // don't need to hardcode "50").
        long effectiveMs = Math.round(effectiveBudgetNs / 1_000_000.0);
        long tickBudgetMs = Math.round(tickBudgetNs / 1_000_000.0);
        String labelText = Component.translatable(KEY_LABEL_DYNAMIC,
                Long.toString(effectiveMs), Long.toString(tickBudgetMs)).getString();
        int labelTextH = getTextHeight();
        int labelY = barY + (barH - labelTextH) / 2;
        drawText(labelText, 0, labelY, LABEL_TEXT_COLOR, LABEL_FONT_SCALE);

        if (barPixelBudget <= 0) {
            // Not enough room for the bar at all — label is drawn, nothing else.
            return;
        }

        // Zero-effective-budget special case (T-137): non-plugin server work
        // has already consumed the whole tick, so plugins have literally no
        // headroom. Paint the entire bar red as a 100 % overload indicator,
        // unconditionally show the warning text below, and skip segment
        // rendering (dividing anything by zero would blow up).
        if (effectiveBudgetNs <= 0L) {
            drawRect(barLeft, barY, barPixelBudget, barH, OVERFLOW_MARKER_COLOR);
            drawFrame(barLeft, barY, barPixelBudget, barH, BAR_OUTLINE_COLOR, 1);
            String warning = Component.translatable(KEY_OVERLOAD).getString();
            int warningY = barY + barH + BAR_WARNING_GAP;
            drawText(warning, barLeft, warningY, OVERFLOW_TEXT_COLOR, WARNING_FONT_SCALE);
            return;
        }

        // Paint bar background + subtle outline so the empty state stays visible.
        drawRect(barLeft, barY, barPixelBudget, barH, EMPTY_BACKGROUND_COLOR);
        drawFrame(barLeft, barY, barPixelBudget, barH, EMPTY_OUTLINE_COLOR, 1);

        if (snapshot == null || snapshot.entries.isEmpty()) {
            // Null (before first packet) or zero-plugin case — leave the placeholder.
            return;
        }

        // From here on the bar scales against the EFFECTIVE budget: 100 % width
        // = the CPU budget plugins actually have available after the server's
        // rolling non-plugin work is subtracted, not the fixed 50 ms tick cap.
        long budgetNs = effectiveBudgetNs;

        // Compute effective total (skip disabled plugins in the overload check —
        // they contribute 0 anyway when not running, but this makes intent
        // explicit and defensive against future backend behaviour changes).
        long effectiveTotalAvgNs = 0L;
        for (PluginPerformanceSnapshot.Entry e : snapshot.entries) {
            if (!e.enabled) continue;
            effectiveTotalAvgNs += e.totalAvgNs();
        }
        boolean overloaded = effectiveTotalAvgNs > budgetNs;

        // Stack segments left-to-right in execution order. cursorX tracks the
        // *unclamped* logical offset — segments that spill past the bar's
        // right edge are clipped when drawn but the cursor keeps advancing so
        // subsequent segments still respect execution order.
        int cursorX = 0;
        for (PluginPerformanceSnapshot.Entry e : snapshot.entries) {
            int updateColor   = colorForEntry(e, /*updatePass=*/true);
            int finalizeColor = colorForEntry(e, /*updatePass=*/false);
            if (!e.enabled) {
                updateColor   = dimColor(updateColor);
                finalizeColor = dimColor(finalizeColor);
            }
            cursorX = drawSegment(cursorX, barLeft, barY, barH, barPixelBudget,
                    e.updateAvgNs, budgetNs, updateColor, e, true);
            cursorX = drawSegment(cursorX, barLeft, barY, barH, barPixelBudget,
                    e.finalizeAvgNs, budgetNs, finalizeColor, e, false);
        }

        // Overflow marker: a red vertical strip at the very right edge whenever
        // the drawn stack would have spilled off-bar. Uses cursorX (unclamped)
        // as the truth source so it fires even when segment rounding hides the
        // last pixels of overflow.
        if (cursorX > barPixelBudget || overloaded) {
            int markerW = Math.max(2, barPixelBudget / 80);
            drawRect(barLeft + barPixelBudget - markerW, barY, markerW, barH, OVERFLOW_MARKER_COLOR);
        }

        // Redraw outline on top so segments never bleed into the border.
        drawFrame(barLeft, barY, barPixelBudget, barH, BAR_OUTLINE_COLOR, 1);

        // Warning text below the bar, aligned with the bar's left edge so it
        // reads as annotating the bar (not the label).
        if (overloaded) {
            String warning = Component.translatable(KEY_OVERLOAD).getString();
            int warningY = barY + barH + BAR_WARNING_GAP;
            drawText(warning, barLeft, warningY, OVERFLOW_TEXT_COLOR, WARNING_FONT_SCALE);
        }

        // Per-segment hover tooltip. Iterate in reverse so the last-drawn (top)
        // segment wins if two ever overlap; today they don't, but this makes
        // the behaviour deterministic.
        if (isMouseOver()) {
            int mx = getMouseX();
            int my = getMouseY();
            for (int i = segments.size() - 1; i >= 0; i--) {
                SegmentRect s = segments.get(i);
                if (mx >= s.x && mx < s.x + s.width && my >= s.y && my < s.y + s.height) {
                    drawSegmentTooltip(s, mx, my, budgetNs);
                    break;
                }
            }
        }
    }

    /**
     * Draws a single {@link SegmentRect} into the bar and appends it to the
     * hit-test cache. Advances {@code cursorX} by the segment's <em>full</em>
     * width so subsequent segments keep their execution-order offset even when
     * this one is clipped by the bar's right edge.
     *
     * @param cursorX        current unclamped x offset relative to the bar's
     *                       left edge (0 == bar-left)
     * @param barLeft        widget-local x coordinate of the bar's left edge
     *                       (shifts every draw/hit-test to the right of the
     *                       label strip)
     * @param barY           widget-local y of the bar's top edge
     * @param barH           bar height in px
     * @param barPixelBudget bar width in px; the segment is clipped to
     *                       {@code [barLeft, barLeft + barPixelBudget]}
     * @param timeNs         plugin time for this segment (ns)
     * @param budgetNs       tick budget in ns (bar's full width represents this)
     * @param color          fill colour for this segment
     * @param entry          snapshot entry, cached for the hover tooltip
     * @param isUpdatePass   {@code true} for the update pass; {@code false} for
     *                       the finalisation pass
     * @return the new cursor X position after this segment (still relative to
     *         the bar's left edge, unclamped)
     */
    private int drawSegment(int cursorX, int barLeft, int barY, int barH, int barPixelBudget,
                            long timeNs, long budgetNs, int color,
                            PluginPerformanceSnapshot.Entry entry, boolean isUpdatePass) {
        if (timeNs <= 0L || budgetNs <= 0L) {
            return cursorX;
        }
        // Full unclamped segment width in pixels.
        int fullW = (int) Math.round((double) timeNs / (double) budgetNs * (double) barPixelBudget);
        if (fullW <= 0) {
            // Still tick the cursor forward — using 0 keeps ordering stable
            // even for sub-pixel segments.
            return cursorX;
        }

        // Clamp the drawn rect to the bar's visible x range (bar-local coords).
        int drawX = Math.max(0, cursorX);
        int drawRight = Math.min(barPixelBudget, cursorX + fullW);
        int drawW = drawRight - drawX;

        if (drawW > 0) {
            // Translate bar-local x into widget-local x by adding barLeft.
            int widgetX = barLeft + drawX;
            drawRect(widgetX, barY, drawW, barH, color);
            segments.add(new SegmentRect(widgetX, barY, drawW, barH, entry, isUpdatePass));
        }
        return cursorX + fullW;
    }

    /**
     * Builds and queues the per-segment tooltip. Uses translated strings for
     * every user-visible line so the widget stays locale-friendly.
     */
    private void drawSegmentTooltip(SegmentRect s, int mx, int my, long budgetNs) {
        long latestNs = s.isUpdatePass ? s.entry.updateLatestNs   : s.entry.finalizeLatestNs;
        long avgNs    = s.isUpdatePass ? s.entry.updateAvgNs      : s.entry.finalizeAvgNs;
        long peakNs   = s.isUpdatePass ? s.entry.updatePeakNs     : s.entry.finalizePeakNs;

        String passLabel = Component.translatable(s.isUpdatePass ? KEY_PASS_UPDATE : KEY_PASS_FINALIZE).getString();
        String timeLine    = Component.translatable(KEY_TOOLTIP_TIME,    formatMs(latestNs)).getString();
        String avgLine     = Component.translatable(KEY_TOOLTIP_AVG,     formatMs(avgNs)).getString();
        String peakLine    = Component.translatable(KEY_TOOLTIP_PEAK,    formatMs(peakNs)).getString();
        String pctLine     = Component.translatable(KEY_TOOLTIP_BUDGET,  formatPercent(avgNs, budgetNs)).getString();
        String marketsLine = Component.translatable(KEY_TOOLTIP_MARKETS, s.entry.subscribedMarketCount).getString();

        StringBuilder sb = new StringBuilder();
        sb.append(s.entry.displayName).append('\n')
          .append(passLabel).append('\n')
          .append(timeLine).append('\n')
          .append(avgLine).append('\n')
          .append(peakLine).append('\n')
          .append(pctLine).append('\n')
          .append(marketsLine);

        drawTooltip(sb.toString(), mx, my, hoverToolTipFontSize);
    }

    @Override
    protected void layoutChanged() {
        // No child elements to arrange; render() reads getWidth() directly.
    }

    // ------------------------------------------------------------------------
    // Snapshot access
    // ------------------------------------------------------------------------

    /**
     * Reads the latest snapshot from the client-side plugin manager. Returns
     * {@code null} if the manager is not yet available (e.g. very early during
     * client init) or if no snapshot has arrived yet (stream not started, or
     * client is on an untrusted slave / non-admin).
     */
    private static @Nullable PluginPerformanceSnapshot fetchSnapshot() {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.PLUGIN_MANAGER == null) {
            return null;
        }
        // The client-side plugin manager is always a ClientPluginManager on the
        // client; the interface field intentionally does not expose the
        // client-only timing API, so a cast is required here.
        if (BACKEND_INSTANCES.PLUGIN_MANAGER instanceof ClientPluginManager clientMgr) {
            return clientMgr.getLatestTimingSnapshot();
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------------------

    /** Formats a nanosecond duration as milliseconds, two decimal places. */
    private static String formatMs(long ns) {
        double ms = ns / 1_000_000.0;
        return String.format(Locale.ROOT, "%.2f ms", ms);
    }

    /** Formats {@code avgNs / budgetNs} as a percentage, one decimal place. */
    private static String formatPercent(long avgNs, long budgetNs) {
        if (budgetNs <= 0L) return "0.0%";
        double pct = (avgNs * 100.0) / (double) budgetNs;
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    // ------------------------------------------------------------------------
    // Colour derivation
    // ------------------------------------------------------------------------

    /**
     * Deterministic per-plugin colour derived from its instance UUID, so the
     * same plugin always shows the same colour across sessions and clients.
     * The finalisation pass uses the same hue at a lower value (V) so the two
     * passes read as related but distinguishable.
     *
     * @param entry      the timing entry to colour
     * @param updatePass {@code true} for the normal-update pass; {@code false}
     *                   for the finalisation pass
     * @return a packed ARGB colour with full alpha
     */
    private static int colorForEntry(PluginPerformanceSnapshot.Entry entry, boolean updatePass) {
        long hash = entry.instanceID.getMostSignificantBits() ^ entry.instanceID.getLeastSignificantBits();
        // Fold to an unsigned 32-bit slice for stable hue distribution.
        long hue1000 = ((hash ^ (hash >>> 32)) & 0xFFFFFFFFL) % 997L;
        float hue = hue1000 / 997.0f;
        float sat = 0.65f;
        float val = updatePass ? 0.85f : 0.55f;
        return 0xFF000000 | hsvToRgb(hue, sat, val);
    }

    /**
     * Fades a colour toward mid-grey to indicate a disabled plugin — keeps the
     * segment visible and hue-associated without demanding attention.
     */
    private static int dimColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        // Blend 50/50 toward mid-grey — reduces saturation without going flat.
        r = (r + 96) / 2;
        g = (g + 96) / 2;
        b = (b + 96) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Standard HSV → RGB conversion. All inputs are floats in [0, 1]; output is
     * a 24-bit RGB triple (no alpha) packed as {@code (R<<16)|(G<<8)|B}.
     */
    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        float r, g, b;
        switch (Math.floorMod(i, 6)) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return (ri << 16) | (gi << 8) | bi;
    }
}
