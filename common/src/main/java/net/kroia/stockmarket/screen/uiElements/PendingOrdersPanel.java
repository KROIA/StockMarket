package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.networking.request.CancelOrderRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable panel displaying the player's active (pending) orders with cancel buttons.
 * <p>
 * Receives order data from the {@code ActiveOrdersStream} via {@link #updateOrders(List)}.
 * Uses a dirty-flag pattern to defer the UI rebuild to the next render frame,
 * avoiding ConcurrentModificationException when updates arrive during iteration.
 */
public class PendingOrdersPanel extends StockMarketGuiElement {

    private final VerticalListView orderListView;

    // Dirty-flag for deferred rebuild
    private boolean needsRebuild = false;
    private List<Order> pendingOrders = new ArrayList<>();

    /** Called when the user clicks an item icon to switch to that market. */
    private @Nullable Consumer<ItemID> onMarketSwitch;

    public PendingOrdersPanel() {
        super();
        setEnableBackground(true);

        orderListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        orderListView.setLayout(layout);
        addChild(orderListView);
    }

    /**
     * Updates the displayed orders. Called when the ActiveOrdersStream pushes new data.
     * Uses a dirty flag to defer the rebuild to the next render frame.
     *
     * @param orders the current list of active orders for this player
     */
    public void updateOrders(List<Order> orders) {
        needsRebuild = true;
        pendingOrders = orders != null ? new ArrayList<>(orders) : new ArrayList<>();
    }

    /**
     * Sets a callback that fires when the user clicks an item icon in a pending order row.
     * Allows switching the active market from the pending orders panel.
     *
     * @param callback receives the ItemID of the clicked order's market, or null to disable
     */
    public void setOnMarketSwitch(@Nullable Consumer<ItemID> callback) {
        this.onMarketSwitch = callback;
    }

    @Override
    protected void render() {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildOrderList(pendingOrders);
        }
    }

    @Override
    protected void layoutChanged() {
        orderListView.setBounds(0, 0, getWidth(), getHeight());
    }

    /**
     * Clears and rebuilds the order list view from the given orders.
     * Orders are sorted most recent first (by timestamp descending).
     */
    private void rebuildOrderList(List<Order> orders) {
        orderListView.removeChilds();

        // Sort by time descending (most recent first)
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingLong(Order::getTime).reversed());

        for (Order order : sorted) {
            OrderEntryWidget entry = new OrderEntryWidget(order);
            orderListView.addChild(entry);
        }

        layoutChanged();
    }

    /**
     * Sends a cancel request to the server for the given order.
     * The ActiveOrdersStream will push an updated order list after the cancel takes effect.
     */
    private void onCancelOrder(Order order) {
        CancelOrderRequest.InputData input = new CancelOrderRequest.InputData(
                order.getItemID(),
                order.getTime(),
                order.getType().ordinal(),
                order.getStartPrice(),
                order.getTargetVolume()
        );
        BACKEND_INSTANCES.NETWORKING.CANCEL_ORDER_REQUEST.sendRequestToServer(input);
    }

    // -------------------------------------------------------------------------
    //  OrderEntryWidget — single row representing one pending order
    // -------------------------------------------------------------------------

    /**
     * Displays a single pending order as a compact row:
     * {@code [ItemIcon 16x16] [BUY/SELL] [filled/total] [@ price] [Cancel btn]}
     * <p>
     * The item icon is clickable — clicking it fires the market switch callback
     * to navigate to that order's market. A hover overlay indicates clickability.
     */
    private class OrderEntryWidget extends StockMarketGuiElement {

        private final ItemView itemIcon;
        private final Label typeLabel;
        private final Label amountLabel;
        private final Label priceLabel;
        private final Button cancelButton;
        private final Order order;

        // Layout constants for icon hit-testing
        private static final int ICON_SIZE = 16;
        private static final int ICON_MARGIN = 2;

        OrderEntryWidget(Order order) {
            super();
            this.order = order;
            setEnableBackground(true);

            // Item icon from the order's ItemID
            itemIcon = new ItemView(order.getItemID().getStack());
            addChild(itemIcon);

            // BUY / SELL type label
            boolean isBuy = order.getTargetVolume() > 0;
            typeLabel = new Label(isBuy ? "BUY" : "SELL");
            typeLabel.setTextFontScale(0.8f);
            addChild(typeLabel);

            // Amount: filled / total (convert from backend to display values)
            double filled = Math.abs(MarketManager.convertToRealAmountStatic(order.getFilledVolume()));
            double total = Math.abs(MarketManager.convertToRealAmountStatic(order.getTargetVolume()));
            amountLabel = new Label(String.format("%.2f/%.2f", filled, total));
            amountLabel.setTextFontScale(0.8f);
            addChild(amountLabel);

            // Price display (convert backend price to human-readable)
            double realPrice = MarketManager.convertToRealAmountStatic(order.getStartPrice());
            String priceStr = order.getType() == Order.Type.LIMIT
                    ? "@ " + String.format("%.2f", realPrice)
                    : "@ MKT";
            priceLabel = new Label(priceStr);
            priceLabel.setTextFontScale(0.8f);
            addChild(priceLabel);

            // Cancel button
            cancelButton = new Button("x", () -> onCancelOrder(order));
            cancelButton.setBackgroundColor(0xFFe8711c);
            cancelButton.setHoverColor(0xFFe04c12);
            addChild(cancelButton);

            setHeight(24);
        }

        @Override
        protected void render() {
            // Draw hover overlay on the item icon when the mouse is over it
            if (isMouseOver() && isInIconArea(getMouseX(), getMouseY())) {
                int iconX = ICON_MARGIN;
                int iconY = (getHeight() - ICON_SIZE) / 2;
                drawRect(iconX, iconY, ICON_SIZE, ICON_SIZE, 0x60FFFFFF);
            }
        }

        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button == 0 && isInIconArea(getMouseX(), getMouseY())) {
                if (onMarketSwitch != null) {
                    onMarketSwitch.accept(order.getItemID());
                }
                return true;
            }
            return false;
        }

        /**
         * Checks if the given local coordinates fall within the item icon area.
         */
        private boolean isInIconArea(int mouseX, int mouseY) {
            int iconX = ICON_MARGIN;
            int iconY = (getHeight() - ICON_SIZE) / 2;
            return mouseX >= iconX && mouseX < iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY < iconY + ICON_SIZE;
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth();
            int h = getHeight();
            int btnSize = 16;
            int s = 2;
            int x = s;

            itemIcon.setBounds(x, (h - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
            x += ICON_SIZE + s;
            typeLabel.setBounds(x, 0, 30, h);
            x += 30 + s;
            int remaining = w - x - btnSize - s * 2;
            int amountWidth = remaining / 2;
            int priceWidth = remaining - amountWidth;
            amountLabel.setBounds(x, 0, amountWidth, h);
            x += amountWidth + s;
            priceLabel.setBounds(x, 0, priceWidth, h);
            cancelButton.setBounds(w - btnSize - s, (h - btnSize) / 2, btnSize, btnSize);
        }
    }
}
