package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.OrderHistoryRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable panel displaying the player's completed/cancelled order records.
 * <p>
 * Data is loaded on-demand via {@link OrderHistoryRequest}.
 * Uses a dirty-flag pattern to defer the UI rebuild to the next render frame,
 * avoiding ConcurrentModificationException when updates arrive during iteration.
 */
public class OrderHistoryPanel extends StockMarketGuiElement {

    private static final int MAX_RESULTS = 100;
    private static final long AUTO_REFRESH_INTERVAL_MS = 3000;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final VerticalListView recordListView;

    // Dirty-flag for deferred rebuild
    private boolean needsRebuild = false;
    private List<OrderRecordStruct> pendingRecords = new ArrayList<>();
    private long lastRefreshMs = 0;

    public OrderHistoryPanel() {
        super();
        setEnableBackground(true);

        recordListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        recordListView.setLayout(layout);
        addChild(recordListView);
    }

    /**
     * Sends an {@link OrderHistoryRequest} to the server and updates the panel
     * when the response arrives.
     */
    public void refresh() {
        BACKEND_INSTANCES.NETWORKING.ORDER_HISTORY_REQUEST.sendRequestToServer(
                new OrderHistoryRequest.InputData(MAX_RESULTS)
        ).thenAccept(response -> updateRecords(response.records()));
    }

    /**
     * Replaces the displayed records. Thread-safe via dirty flag.
     *
     * @param records the new list of order history records
     */
    public void updateRecords(List<OrderRecordStruct> records) {
        needsRebuild = true;
        pendingRecords = records != null ? new ArrayList<>(records) : new ArrayList<>();
    }

    @Override
    protected void render() {
        if (needsRebuild) {
            needsRebuild = false;
            rebuildRecordList(pendingRecords);
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs > AUTO_REFRESH_INTERVAL_MS) {
            lastRefreshMs = now;
            refresh();
        }
    }

    @Override
    protected void layoutChanged() {
        recordListView.setBounds(0, 0, getWidth(), getHeight());
    }

    /**
     * Clears and rebuilds the record list view. Records are already sorted by time descending
     * from the server response.
     */
    private void rebuildRecordList(List<OrderRecordStruct> records) {
        recordListView.removeChilds();

        for (OrderRecordStruct record : records) {
            HistoryEntryWidget entry = new HistoryEntryWidget(record);
            recordListView.addChild(entry);
        }

        layoutChanged();
    }

    // -------------------------------------------------------------------------
    //  HistoryEntryWidget — single row representing one completed order record
    // -------------------------------------------------------------------------

    /**
     * Displays a single order history record as a compact row:
     * {@code [ItemIcon 16x16] [BUY/SELL] [amount] [@ price] [timestamp]}
     */
    private class HistoryEntryWidget extends StockMarketGuiElement {

        private final ItemView itemIcon;
        private final Label typeLabel;
        private final Label amountLabel;
        private final Label priceLabel;
        private final Label timeLabel;

        HistoryEntryWidget(OrderRecordStruct record) {
            super();
            setEnableBackground(true);

            // Item icon from the record's itemID
            ItemID itemID = new ItemID(record.itemID());
            itemIcon = new ItemView(itemID.getStack());
            addChild(itemIcon);

            // BUY / SELL — positive amount = buy, negative = sell
            boolean isBuy = record.amount() > 0;
            typeLabel = new Label(isBuy ? "BUY" : "SELL");
            typeLabel.setTextFontScale(0.8f);
            addChild(typeLabel);

            // Amount (convert from backend to display values)
            double realAmount = Math.abs(MarketManager.convertToRealAmountStatic(record.amount()));
            amountLabel = new Label(String.format("%.2f", realAmount));
            amountLabel.setTextFontScale(0.8f);
            addChild(amountLabel);

            // Price (convert from backend to display values)
            double realPrice = MarketManager.convertToRealAmountStatic(record.price());
            priceLabel = new Label("@ " + String.format("%.2f", realPrice));
            priceLabel.setTextFontScale(0.8f);
            addChild(priceLabel);

            // Timestamp
            String timeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(record.time()));
            timeLabel = new Label(timeStr);
            timeLabel.setTextFontScale(0.8f);
            addChild(timeLabel);

            setHeight(24);
        }

        @Override
        protected void render() {
            // No dynamic rendering needed
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth();
            int h = getHeight();
            int iconSize = 16;
            int s = 2;
            int x = s;

            itemIcon.setBounds(x, (h - iconSize) / 2, iconSize, iconSize);
            x += iconSize + s;
            typeLabel.setBounds(x, 0, 30, h);
            x += 30 + s;
            amountLabel.setBounds(x, 0, 50, h);
            x += 50 + s;
            priceLabel.setBounds(x, 0, 60, h);
            x += 60 + s;
            timeLabel.setBounds(x, 0, w - x - s, h);
        }
    }
}
