package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.TransactionHistoryRequest;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable panel displaying all players' completed trades for a specific market.
 * <p>
 * Data is loaded on-demand via {@link TransactionHistoryRequest}.
 * Uses a dirty-flag pattern to defer the UI rebuild to the next render frame.
 */
public class TransactionHistoryPanel extends StockMarketGuiElement {

    private static final int MAX_RESULTS = 100;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final VerticalListView recordListView;

    // Dirty-flag for deferred rebuild
    private boolean needsRebuild = false;
    private List<OrderRecordStruct> pendingRecords = new ArrayList<>();

    public TransactionHistoryPanel() {
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
     * Sends a {@link TransactionHistoryRequest} to the server for the given market
     * and updates the panel when the response arrives.
     *
     * @param marketID the market item ID to fetch transaction history for
     */
    public void refresh(ItemID marketID) {
        if (marketID == null) return;
        BACKEND_INSTANCES.NETWORKING.TRANSACTION_HISTORY_REQUEST.sendRequestToServer(
                new TransactionHistoryRequest.InputData(marketID, MAX_RESULTS)
        ).thenAccept(response -> updateRecords(response.records()));
    }

    /**
     * Replaces the displayed records. Thread-safe via dirty flag.
     *
     * @param records the new list of transaction history records
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
            TradeEntryWidget entry = new TradeEntryWidget(record);
            recordListView.addChild(entry);
        }

        layoutChanged();
    }

    // -------------------------------------------------------------------------
    //  TradeEntryWidget — single row representing one market transaction
    // -------------------------------------------------------------------------

    /**
     * Displays a single market transaction as a compact row:
     * {@code [BUY/SELL] [amount] [@ price] [timestamp]}
     * <p>
     * Player identification is omitted for simplicity — this panel focuses on
     * market-wide price/volume transparency.
     */
    private class TradeEntryWidget extends StockMarketGuiElement {

        private final Label typeLabel;
        private final Label amountLabel;
        private final Label priceLabel;
        private final Label timeLabel;

        TradeEntryWidget(OrderRecordStruct record) {
            super();
            setEnableBackground(true);

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
            int s = 2;
            int x = s;

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
