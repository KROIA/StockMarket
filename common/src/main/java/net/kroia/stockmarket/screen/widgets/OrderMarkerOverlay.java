package net.kroia.stockmarket.screen.widgets;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Interactive overlay for the CandlestickChart that draws horizontal marker lines
 * for the player's pending limit orders. Each marker has a drag handle to move the
 * order to a new price and an "X" button to cancel the order.
 */
public class OrderMarkerOverlay implements CandlestickChart.InteractiveOverlay {

    // ── Constants ──

    private static final int HANDLE_HEIGHT = 14;
    private static final int CANCEL_BUTTON_SIZE = 14;
    private static final int LINE_THICKNESS = 2;
    private static final int MARGIN = 4;
    private static final float LABEL_FONT_SCALE = 0.7f;
    private static final float LINE_ALPHA = 0.5f;
    private static final float HANDLE_ALPHA = 0.9f;

    // ── Data ──

    private List<Order> orders = new ArrayList<>();
    private List<InterMarketOrder> interMarketOrders = new ArrayList<>();
    private @Nullable ItemID currentMarketItemID;
    private boolean pairMode = false;

    // ── Drag state ──

    private @Nullable Order draggingOrder = null;
    private int dragMouseY = 0;

    // ── Hit-test regions rebuilt each frame ──

    private final List<MarkerHitRegion> hitRegions = new ArrayList<>();
    private final List<InterMarketMarkerHitRegion> interMarketHitRegions = new ArrayList<>();

    // ── Callbacks ──

    /** Called when the user drags an order to a new price: (order, newRealPrice). */
    private @Nullable BiConsumer<Order, Double> onMoveOrder;

    /** Called when the user clicks the cancel button on an order. */
    private @Nullable Consumer<Order> onCancelOrder;

    /** Called when the user clicks the cancel button on an inter-market order. */
    private @Nullable Consumer<InterMarketOrder> onCancelInterMarketOrder;

    // ── Inner class for hit testing ──

    private static class MarkerHitRegion {
        Order order;
        Rectangle dragHandleRect;
        Rectangle cancelButtonRect;
        int lineY;

        MarkerHitRegion(Order order, Rectangle dragHandleRect, Rectangle cancelButtonRect, int lineY) {
            this.order = order;
            this.dragHandleRect = dragHandleRect;
            this.cancelButtonRect = cancelButtonRect;
            this.lineY = lineY;
        }
    }

    // Hit region for inter-market order markers (no drag support)
    private static class InterMarketMarkerHitRegion {
        InterMarketOrder order;
        Rectangle cancelButtonRect;
        int lineY;

        InterMarketMarkerHitRegion(InterMarketOrder order, Rectangle cancelButtonRect, int lineY) {
            this.order = order;
            this.cancelButtonRect = cancelButtonRect;
            this.lineY = lineY;
        }
    }

    // ── Public API ──

    /**
     * Updates the displayed orders. Called when the ActiveOrdersStream pushes new data.
     *
     * @param orders the current list of all active orders for this player
     */
    public void updateOrders(List<Order> orders) {
        this.orders = orders != null ? new ArrayList<>(orders) : new ArrayList<>();
    }

    /**
     * Sets the currently displayed market. Only orders matching this market are drawn.
     *
     * @param itemID the ItemID of the current market, or null to hide all markers
     */
    public void setCurrentMarket(@Nullable ItemID itemID) {
        this.currentMarketItemID = itemID;
    }

    public void setOnMoveOrder(@Nullable BiConsumer<Order, Double> callback) {
        this.onMoveOrder = callback;
    }

    public void setOnCancelOrder(@Nullable Consumer<Order> callback) {
        this.onCancelOrder = callback;
    }

    /**
     * Updates the displayed inter-market orders. Called when the ActiveOrdersStream pushes new data.
     *
     * @param orders the current list of inter-market orders for this player
     */
    public void updateInterMarketOrders(List<InterMarketOrder> orders) {
        this.interMarketOrders = orders != null ? new ArrayList<>(orders) : new ArrayList<>();
    }

    /**
     * Enables or disables pair mode. When pair mode is active, inter-market limit orders
     * are drawn at their cross-rate limit price on the chart.
     */
    public void setPairMode(boolean pairMode) {
        this.pairMode = pairMode;
    }

    public void setOnCancelInterMarketOrder(@Nullable Consumer<InterMarketOrder> callback) {
        this.onCancelInterMarketOrder = callback;
    }

    // ── Rendering ──

    @Override
    public void render(CandlestickChart chart) {
        hitRegions.clear();
        interMarketHitRegions.clear();

        if (currentMarketItemID == null) return;

        Rectangle bounds = chart.getCanvasBounds();
        int canvasTop = bounds.y;
        int canvasBottom = bounds.y + bounds.height;

        // Marker spans half the chart width
        int markerWidth = bounds.width / 2;

        for (Order order : orders) {
            // Only show LIMIT orders for the currently displayed market
            if (!order.isLimitOrder()) continue;
            if (!order.getItemID().equals(currentMarketItemID)) continue;

            double realPrice = MarketManager.convertToRealAmountStatic(order.getStartPrice());
            int lineY;

            // If this is the order being dragged, use the drag mouse position instead
            boolean isDragging = (draggingOrder != null && isSameOrder(draggingOrder, order));
            if (isDragging) {
                lineY = dragMouseY;
            } else {
                lineY = chart.toCanvasSpaceY(realPrice);
            }

            // Skip if outside canvas bounds (with margin for the buttons)
            int halfHeight = HANDLE_HEIGHT / 2 + MARGIN;
            if (lineY + halfHeight < canvasTop || lineY - halfHeight > canvasBottom) continue;

            // Choose color based on order direction
            boolean isBuy = order.isBuyOrder();
            int solidColor = isBuy ? UI_Colors.buyColorGreen : UI_Colors.sellColorRed;
            int lineColor = ColorUtilities.setAlpha(solidColor, LINE_ALPHA);
            int handleColor = ColorUtilities.setAlpha(solidColor, HANDLE_ALPHA);
            int cancelBgColor = ColorUtilities.setAlpha(
                    ColorUtilities.setBrightness(solidColor, 0.6f), HANDLE_ALPHA);

            // Build label text: just the volume (color already indicates buy/sell)
            double remaining = Math.abs(MarketManager.convertToRealAmountStatic(order.getRemainingVolume()));
            String label = formatAmount(remaining);

            int textWidth = chart.getTextWidth(label);
            int textHeight = chart.getTextHeight();

            // Drag handle dimensions — right-aligned within the chart
            int handleWidth = textWidth + 8;
            int markerStartX = bounds.x + bounds.width - markerWidth;
            int handleX = markerStartX;
            int handleY = lineY - HANDLE_HEIGHT / 2;

            // Cancel button right after the drag handle
            int cancelX = handleX + handleWidth + 1;
            int cancelY = handleY;

            // Draw horizontal line from cancel button right edge to chart right edge
            int lineStartX = cancelX + CANCEL_BUTTON_SIZE;
            int lineEndX = bounds.x + bounds.width;
            chart.drawRect(lineStartX, lineY - LINE_THICKNESS / 2, lineEndX - lineStartX, LINE_THICKNESS, lineColor);

            // Draw drag handle background
            chart.drawRect(handleX, handleY, handleWidth, HANDLE_HEIGHT, handleColor);
            // Draw label text centered in handle
            chart.drawText(label, handleX + 4, handleY + (HANDLE_HEIGHT - textHeight) / 2, 0xFFFFFFFF, LABEL_FONT_SCALE);

            // Draw cancel [X] button with diagonal lines instead of text
            chart.drawRect(cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE, cancelBgColor);
            int xPad = 3;
            StockMarketGuiElement.drawXMark(chart, cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE, xPad, 1.0f, 0xFFFFFFFF);

            // If dragging, draw a price label showing the new target price (left of drag handle)
            if (isDragging) {
                double newPrice = chart.fromCanvasSpaceY(dragMouseY);
                if (newPrice < 0) newPrice = 0;
                String priceLabel = String.format("%.2f", newPrice);
                int priceLabelWidth = chart.getTextWidth(priceLabel);
                int priceLabelX = handleX - priceLabelWidth - 6;
                chart.drawRect(priceLabelX - 2, handleY, priceLabelWidth + 4, HANDLE_HEIGHT, 0xCC333333);
                chart.drawText(priceLabel, priceLabelX, handleY + (HANDLE_HEIGHT - textHeight) / 2,
                        0xFFFFFFFF, LABEL_FONT_SCALE);
            }

            // Store hit regions for mouse event handling
            hitRegions.add(new MarkerHitRegion(
                    order,
                    new Rectangle(handleX, handleY, handleWidth, HANDLE_HEIGHT),
                    new Rectangle(cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE),
                    lineY
            ));
        }

        // ── Inter-market limit order markers (pair mode only) ──
        if (pairMode) {
            renderInterMarketOrders(chart, bounds, canvasTop, canvasBottom);
        }
    }

    /**
     * Renders inter-market limit orders as non-draggable horizontal lines with a cancel button.
     * Only shown in pair mode, where the chart Y axis represents the cross-rate.
     */
    private void renderInterMarketOrders(CandlestickChart chart, Rectangle bounds,
                                          int canvasTop, int canvasBottom) {
        // Cyan/blue color for inter-market pair orders (bidirectional)
        int solidColor = 0xFF44BBEE;
        int lineColor = ColorUtilities.setAlpha(solidColor, LINE_ALPHA);
        int handleColor = ColorUtilities.setAlpha(solidColor, HANDLE_ALPHA);
        int cancelBgColor = ColorUtilities.setAlpha(
                ColorUtilities.setBrightness(solidColor, 0.6f), HANDLE_ALPHA);

        int markerWidth = bounds.width / 2;

        for (InterMarketOrder order : interMarketOrders) {
            if (!order.isLimitOrder()) continue;

            // Convert raw cross-rate limit to real rate
            double ratePrice = (double) order.getCrossRateLimit() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
            int lineY = chart.toCanvasSpaceY(ratePrice);

            // Skip if outside canvas bounds (with margin for the buttons)
            int halfHeight = HANDLE_HEIGHT / 2 + MARGIN;
            if (lineY + halfHeight < canvasTop || lineY - halfHeight > canvasBottom) continue;

            // Build label: show the rate limit value (e.g., "≤10.50")
            String label = "≤" + formatAmount(ratePrice);

            int textWidth = chart.getTextWidth(label);
            int textHeight = chart.getTextHeight();

            // Handle dimensions — right-aligned within the chart (same layout as regular orders)
            int handleWidth = textWidth + 8;
            int markerStartX = bounds.x + bounds.width - markerWidth;
            int handleX = markerStartX;
            int handleY = lineY - HANDLE_HEIGHT / 2;

            // Cancel button right after the handle
            int cancelX = handleX + handleWidth + 1;
            int cancelY = handleY;

            // Draw horizontal line from cancel button right edge to chart right edge
            int lineStartX = cancelX + CANCEL_BUTTON_SIZE;
            int lineEndX = bounds.x + bounds.width;
            chart.drawRect(lineStartX, lineY - LINE_THICKNESS / 2, lineEndX - lineStartX, LINE_THICKNESS, lineColor);

            // Draw handle background with label
            chart.drawRect(handleX, handleY, handleWidth, HANDLE_HEIGHT, handleColor);
            chart.drawText(label, handleX + 4, handleY + (HANDLE_HEIGHT - textHeight) / 2, 0xFFFFFFFF, LABEL_FONT_SCALE);

            // Draw cancel [X] button
            chart.drawRect(cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE, cancelBgColor);
            int xPad = 3;
            StockMarketGuiElement.drawXMark(chart, cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE, xPad, 1.0f, 0xFFFFFFFF);

            // Store hit region for cancel click handling (no drag support)
            interMarketHitRegions.add(new InterMarketMarkerHitRegion(
                    order,
                    new Rectangle(cancelX, cancelY, CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE),
                    lineY
            ));
        }
    }

    // ── Mouse event handling ──

    @Override
    public boolean mouseClicked(CandlestickChart chart, int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // Check cancel buttons first (higher priority than drag handles)
        for (MarkerHitRegion region : hitRegions) {
            if (isInsideRect(mouseX, mouseY, region.cancelButtonRect)) {
                if (onCancelOrder != null) {
                    onCancelOrder.accept(region.order);
                }
                return true;
            }
        }

        // Check inter-market order cancel buttons
        for (InterMarketMarkerHitRegion region : interMarketHitRegions) {
            if (isInsideRect(mouseX, mouseY, region.cancelButtonRect)) {
                if (onCancelInterMarketOrder != null) {
                    onCancelInterMarketOrder.accept(region.order);
                }
                return true;
            }
        }

        // Check drag handles (regular orders only; inter-market orders are not draggable)
        for (MarkerHitRegion region : hitRegions) {
            if (isInsideRect(mouseX, mouseY, region.dragHandleRect)) {
                draggingOrder = region.order;
                dragMouseY = mouseY;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(CandlestickChart chart, int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        if (draggingOrder != null) {
            dragMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(CandlestickChart chart, int mouseX, int mouseY, int button) {
        if (draggingOrder != null) {
            double newPrice = chart.fromCanvasSpaceY(mouseY);
            if (newPrice < 0) newPrice = 0;

            double originalPrice = MarketManager.convertToRealAmountStatic(draggingOrder.getStartPrice());

            // Only trigger move if the price actually changed meaningfully
            if (Math.abs(newPrice - originalPrice) > 0.001) {
                if (onMoveOrder != null) {
                    onMoveOrder.accept(draggingOrder, newPrice);
                }
            }

            draggingOrder = null;
            return true;
        }
        return false;
    }

    // ── Utility methods ──

    /**
     * Checks if two Order references identify the same pending order
     * by comparing their identifying fields.
     */
    private boolean isSameOrder(Order a, Order b) {
        return a.getTime() == b.getTime()
                && a.getItemID().equals(b.getItemID())
                && a.getStartPrice() == b.getStartPrice()
                && a.getTargetVolume() == b.getTargetVolume();
    }

    private static boolean isInsideRect(int x, int y, Rectangle rect) {
        return x >= rect.x && x < rect.x + rect.width
                && y >= rect.y && y < rect.y + rect.height;
    }

    /**
     * Formats an amount value compactly for the drag handle label.
     */
    private static String formatAmount(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format("%.1fk", value / 1_000);
        if (value == Math.floor(value) && value < 10_000) return String.format("%.0f", value);
        return String.format("%.2f", value);
    }
}
