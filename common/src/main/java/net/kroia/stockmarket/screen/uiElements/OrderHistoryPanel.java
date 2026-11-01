package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.OrderHistoryRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scrollable panel displaying the player's completed/cancelled order records.
 * <p>
 * Data is loaded on-demand via {@link OrderHistoryRequest}.
 * Uses a dirty-flag pattern to defer the UI rebuild to the next render frame,
 * avoiding ConcurrentModificationException when updates arrive during iteration.
 * <p>
 * Records that share the same non-null {@code interMarketGroupID} are grouped
 * and displayed as a single "Traded X sellItem -> Y buyItem" entry.
 */
public class OrderHistoryPanel extends StockMarketGuiElement {

    private static final int MAX_RESULTS = 100;
    private static final long AUTO_REFRESH_INTERVAL_MS = 3000;
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final VerticalListView recordListView;
    private final Button clearButton;

    // Dirty-flag for deferred rebuild
    private boolean needsRebuild = false;
    private List<OrderRecordStruct> pendingRecords = new ArrayList<>();
    private long lastRefreshMs = 0;
    @org.jetbrains.annotations.Nullable
    private ItemID currentMarketID;
    // Client-side "clear" — hides records older than this timestamp without deleting from SQL
    private long clearedBeforeMs = 0;

    public OrderHistoryPanel() {
        super();
        setEnableBackground(true);

        recordListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        recordListView.setLayout(layout);
        addChild(recordListView);

        clearButton = new Button("Clear", this::onClearHistory);
        clearButton.setBackgroundColor(0xFFe8711c);
        clearButton.setHoverColor(0xFFe04c12);
        addChild(clearButton);

        // Restore persisted clear timestamp so previously cleared records stay hidden across sessions
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        clearedBeforeMs = prefs.getOrderHistoryClearedBeforeMs();
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
        int btnHeight = 18;
        int spacing = 2;
        clearButton.setBounds(getWidth() - 50 - spacing, spacing, 50, btnHeight);
        recordListView.setBounds(0, btnHeight + spacing * 2, getWidth(), getHeight() - btnHeight - spacing * 2);
    }

    /**
     * Sets the current market for filtering the clear operation.
     */
    public void setCurrentMarketID(@org.jetbrains.annotations.Nullable ItemID marketID) {
        this.currentMarketID = marketID;
    }

    /**
     * Hides all currently displayed records by setting a "cleared before" timestamp.
     * Does NOT delete from SQL — the global "Market Trades" tab is unaffected.
     * The timestamp is persisted via PlayerPreferences so it survives screen/session restarts.
     */
    private void onClearHistory() {
        clearedBeforeMs = System.currentTimeMillis();

        // Persist the clear timestamp to the server via PlayerPreferences
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        prefs.setOrderHistoryClearedBeforeMs(clearedBeforeMs);
        StockMarketGuiElement.updatePlayerPreferences(prefs);

        updateRecords(new ArrayList<>());
    }

    /**
     * Clears and rebuilds the record list view, applying market and clear-timestamp filters.
     * <p>
     * Records with a non-null {@code interMarketGroupID} are grouped into pairs and displayed
     * as combined inter-market trade entries. Standalone records (null groupID) are displayed
     * individually as before. All entries are sorted chronologically (most recent first).
     */
    private void rebuildRecordList(List<OrderRecordStruct> records) {
        recordListView.removeChilds();

        // Separate records into grouped inter-market trades and standalone records
        Map<UUID, List<OrderRecordStruct>> groupedByIMGroupID = new LinkedHashMap<>();
        List<OrderRecordStruct> standaloneRecords = new ArrayList<>();

        for (OrderRecordStruct record : records) {
            // Apply market filter: skip records that don't belong to the current market view.
            // For inter-market trades, we allow records from ANY market since the grouped entry
            // shows both legs (which belong to different markets).
            if (currentMarketID != null && record.interMarketGroupID() == null
                    && record.itemID() != currentMarketID.getShort())
                continue;
            // Filter out records hidden by the clear button
            if (clearedBeforeMs > 0 && record.time() <= clearedBeforeMs)
                continue;

            if (record.interMarketGroupID() != null) {
                groupedByIMGroupID.computeIfAbsent(record.interMarketGroupID(), k -> new ArrayList<>()).add(record);
            } else {
                standaloneRecords.add(record);
            }
        }

        // Build a combined list of displayable entries with timestamps for sorting.
        // Each entry is a widget paired with its effective timestamp.
        List<TimestampedEntry> allEntries = new ArrayList<>();

        // Add standalone records as HistoryEntryWidgets
        for (OrderRecordStruct record : standaloneRecords) {
            HistoryEntryWidget entry = new HistoryEntryWidget(record);
            allEntries.add(new TimestampedEntry(record.time(), entry));
        }

        // Add grouped inter-market trade entries
        for (Map.Entry<UUID, List<OrderRecordStruct>> group : groupedByIMGroupID.entrySet()) {
            List<OrderRecordStruct> legs = group.getValue();
            if (legs.size() == 2) {
                // Normal case: exactly 2 legs forming a complete inter-market trade.
                // Apply market filter for grouped entries: if a market filter is set,
                // at least one leg must involve that market.
                if (currentMarketID != null) {
                    boolean matchesMarket = false;
                    for (OrderRecordStruct leg : legs) {
                        if (leg.itemID() == currentMarketID.getShort()) {
                            matchesMarket = true;
                            break;
                        }
                    }
                    if (!matchesMarket) continue;
                }

                // Identify sell leg (negative amount) and buy leg (positive amount)
                OrderRecordStruct sellLeg = legs.get(0).amount() < 0 ? legs.get(0) : legs.get(1);
                OrderRecordStruct buyLeg = legs.get(0).amount() > 0 ? legs.get(0) : legs.get(1);

                // Use the earlier timestamp of the two legs for chronological sorting
                long effectiveTime = Math.min(sellLeg.time(), buyLeg.time());

                InterMarketHistoryEntryWidget entry = new InterMarketHistoryEntryWidget(sellLeg, buyLeg);
                allEntries.add(new TimestampedEntry(effectiveTime, entry));
            } else {
                // Unexpected group size (not exactly 2 legs) — display each record individually
                for (OrderRecordStruct record : legs) {
                    if (currentMarketID != null && record.itemID() != currentMarketID.getShort())
                        continue;
                    HistoryEntryWidget entry = new HistoryEntryWidget(record);
                    allEntries.add(new TimestampedEntry(record.time(), entry));
                }
            }
        }

        // Sort all entries by timestamp descending (most recent first)
        allEntries.sort(Comparator.comparingLong(TimestampedEntry::timestamp).reversed());

        // Add sorted entries to the list view
        for (TimestampedEntry entry : allEntries) {
            recordListView.addChild(entry.widget());
        }

        layoutChanged();
    }

    /**
     * Helper record pairing a display widget with its effective timestamp for chronological sorting.
     */
    private record TimestampedEntry(long timestamp, StockMarketGuiElement widget) {}

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
            amountLabel = new Label(String.format(Locale.ROOT, "%.2f", realAmount));
            amountLabel.setTextFontScale(0.8f);
            addChild(amountLabel);

            // Price (convert from backend to display values)
            double realPrice = MarketManager.convertToRealAmountStatic(record.price());
            priceLabel = new Label("@ " + String.format(Locale.ROOT, "%.2f", realPrice));
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
            int remaining = w - x - s;
            int colWidth = remaining / 3;
            amountLabel.setBounds(x, 0, colWidth, h);
            x += colWidth + s;
            priceLabel.setBounds(x, 0, colWidth, h);
            x += colWidth + s;
            timeLabel.setBounds(x, 0, w - x - s, h);
        }
    }

    // -------------------------------------------------------------------------
    //  InterMarketHistoryEntryWidget — combined row for an inter-market trade
    // -------------------------------------------------------------------------

    /**
     * Displays a grouped inter-market trade as a compact row showing both legs:
     * {@code [sellIcon 16x16] -> [buyIcon 16x16]  Traded X.XX -> Y.YY  [timestamp]}
     * <p>
     * The sell leg (negative amount) represents what was given, and the buy leg
     * (positive amount) represents what was received.
     */
    private class InterMarketHistoryEntryWidget extends StockMarketGuiElement {

        private final ItemView sellIcon;
        private final Label arrowLabel;
        private final ItemView buyIcon;
        private final Label tradeLabel;
        private final Label timeLabel;

        InterMarketHistoryEntryWidget(OrderRecordStruct sellLeg, OrderRecordStruct buyLeg) {
            super();
            setEnableBackground(true);

            // Sell leg icon (what was given)
            ItemID sellItemID = new ItemID(sellLeg.itemID());
            sellIcon = new ItemView(sellItemID.getStack());
            addChild(sellIcon);

            // Arrow between icons
            arrowLabel = new Label("->");
            arrowLabel.setTextFontScale(0.8f);
            addChild(arrowLabel);

            // Buy leg icon (what was received)
            ItemID buyItemID = new ItemID(buyLeg.itemID());
            buyIcon = new ItemView(buyItemID.getStack());
            addChild(buyIcon);

            // "Traded X.XX -> Y.YY" text
            double sellAmount = Math.abs(MarketManager.convertToRealAmountStatic(sellLeg.amount()));
            double buyAmount = Math.abs(MarketManager.convertToRealAmountStatic(buyLeg.amount()));
            tradeLabel = new Label(String.format("Traded %.2f -> %.2f", sellAmount, buyAmount));
            tradeLabel.setTextFontScale(0.8f);
            addChild(tradeLabel);

            // Timestamp (use the earlier of the two legs)
            long effectiveTime = Math.min(sellLeg.time(), buyLeg.time());
            String timeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(effectiveTime));
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

            // [sellIcon] -> [buyIcon]  Traded X.XX -> Y.YY  [timestamp]
            sellIcon.setBounds(x, (h - iconSize) / 2, iconSize, iconSize);
            x += iconSize + s;

            arrowLabel.setBounds(x, 0, 14, h);
            x += 14 + s;

            buyIcon.setBounds(x, (h - iconSize) / 2, iconSize, iconSize);
            x += iconSize + s;

            // Split the remaining space between trade label and timestamp
            int remaining = w - x - s;
            int timeLabelWidth = 70; // enough for "MM-dd HH:mm"
            int tradeLabelWidth = remaining - timeLabelWidth - s;

            tradeLabel.setBounds(x, 0, tradeLabelWidth, h);
            x += tradeLabelWidth + s;
            timeLabel.setBounds(x, 0, timeLabelWidth, h);
        }
    }
}
