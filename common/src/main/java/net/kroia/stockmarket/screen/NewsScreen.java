package net.kroia.stockmarket.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Frame;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.request.NewsHistoryRequest;
import net.kroia.stockmarket.news.ClientNewsCache;
import net.kroia.stockmarket.news.ClientNewsPictureCache;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.screen.uiElements.NewsEntryPanel;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The newspaper screen (task T-074, plan §4): a newspaper-styled, newest-first feed
 * of all published news events.
 * <p>
 * <b>Data flow — merging live cache and paginated history:</b>
 * <ul>
 *   <li><b>Instant content:</b> on open, the per-connection {@link ClientNewsCache}
 *       (filled live by {@code NewsPublishedPacket}) is copied into the feed, so
 *       recent news shows without any round-trip.</li>
 *   <li><b>History pages:</b> a {@link NewsHistoryRequest} for the first page
 *       ({@code beforeUid = 0}) is fired on open; the "load more" button fetches the
 *       next page using the uid of the oldest displayed record as the cursor. An
 *       empty response marks the end of the history.</li>
 *   <li><b>De-duplication:</b> every record is keyed by its unique {@code newsUid};
 *       a uid already displayed is skipped, so cache entries and page entries merge
 *       cleanly no matter which source delivered them first.</li>
 *   <li><b>Live updates:</b> while the screen is open it registers itself as the
 *       cache's change listener — a news publish immediately inserts the new record
 *       at the top of the feed. The listener is removed when the screen goes away
 *       ({@link #removed()}, which vanilla calls on every screen switch including
 *       {@link #onClose()}).</li>
 * </ul>
 * All feed mutations run on the client main thread (request callbacks hop via
 * {@code Minecraft.execute}); the visual rebuild is deferred to the next
 * {@link #tick()} via a dirty flag, matching the panel patterns used elsewhere.
 * <p>
 * <b>Toast opt-in:</b> the "enable news popups" checkbox (user decision: lives in
 * this screen, default off) reads and writes the {@code newsToastEnabled} flag of
 * the per-player {@link PlayerPreferences}, synced via the existing
 * {@code PlayerPreferencesUpdateRequest} flow.
 */
public class NewsScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".news_screen.";
        private static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component MASTHEAD = Component.translatable(PREFIX + "masthead");
        public static final Component TOAST_CHECKBOX = Component.translatable(PREFIX + "toast_checkbox");
        public static final Component TOAST_CHECKBOX_TOOLTIP = Component.translatable(PREFIX + "toast_checkbox.tooltip");
        public static final Component CLEAR_BUTTON = Component.translatable(PREFIX + "clear_button");
        public static final Component CLEAR_BUTTON_TOOLTIP = Component.translatable(PREFIX + "clear_button.tooltip");
        public static final Component LOAD_MORE = Component.translatable(PREFIX + "load_more");
        public static final Component NO_MORE = Component.translatable(PREFIX + "no_more");
        public static final Component LOADING = Component.translatable(PREFIX + "loading");
        public static final Component EMPTY = Component.translatable(PREFIX + "empty");
    }

    /** Page size requested from the server (clamped server-side to [1, 100]). */
    private static final int PAGE_SIZE = 20;

    // ── Newspaper palette ────────────────────────────────────────────────
    private static final int COLOR_PAPER = 0xFFE9E2CE;
    private static final int COLOR_PAPER_EDGE = 0xFF8F8468;
    private static final int COLOR_INK = 0xFF1E1B16;
    private static final int COLOR_META_INK = 0xFF6E6754;
    private static final int COLOR_RULE = 0xFF4A443A;

    // ── UI elements ──────────────────────────────────────────────────────
    private final Frame paperFrame;
    private final Label mastheadLabel;
    private final Frame mastheadRule;
    private final CheckBox toastCheckBox;
    private final Button clearButton;
    private final VerticalListView feedListView;
    private final Button loadMoreButton;
    private final Label emptyLabel;

    // ── Feed state (client main thread only) ─────────────────────────────
    /** Displayed records, kept sorted newest-first (descending {@code newsUid}). */
    private final List<NewsRecord> entries = new ArrayList<>();
    /** Uids of all displayed records (cache/page merge de-duplication). */
    private final Set<Long> knownUids = new HashSet<>();
    /** True once a history page came back empty — no older records exist. */
    private boolean endReached = false;
    /** True while a history page request is in flight (blocks double-fetches). */
    private boolean fetchInFlight = false;
    /** Deferred-rebuild flag: the feed list is rebuilt on the next tick. */
    private boolean feedDirty = false;
    /** Guards the checkbox listener while its state is set programmatically. */
    private boolean loadingCheckBox = false;
    /** The feed width the current panels were wrapped for (rebuild on change). */
    private int lastFeedWidth = -1;

    /** Opens the newspaper without a parent screen (newspaper item entry point). */
    public NewsScreen() {
        this(null);
    }

    /**
     * Opens the newspaper returning to {@code parent} on close (button entry points
     * on TradeScreen / ManagementScreen).
     *
     * @param parent the screen to return to, or null for none
     */
    public NewsScreen(@Nullable Screen parent) {
        super(Texts.TITLE, parent);

        // Newspaper style is dark ink on light paper — the vanilla text drop
        // shadow kills the contrast and makes the ink hard to read, so disable
        // it for this screen. Widgets like Label/CheckBox expose no shadow flag
        // and follow this ClientGraphics setting instead. The instance is owned
        // by this screen (each GuiScreen creates its own in the constructor),
        // so this cannot leak shadow state into other screens or widgets.
        getClientGraphics().setEnableShadow(false);

        paperFrame = new Frame();
        paperFrame.setBackgroundColor(COLOR_PAPER);
        paperFrame.setOutlineColor(COLOR_PAPER_EDGE);

        mastheadLabel = new Label(Texts.MASTHEAD.getString());
        mastheadLabel.setAlignment(Label.Alignment.CENTER);
        mastheadLabel.setTextColor(COLOR_INK);
        mastheadLabel.setTextFontScale(2.0f);

        // Thin horizontal rule below the masthead, like a printed newspaper.
        mastheadRule = new Frame();
        mastheadRule.setBackgroundColor(COLOR_RULE);
        mastheadRule.setOutlineColor(COLOR_RULE);

        toastCheckBox = new CheckBox(Texts.TOAST_CHECKBOX.getString(), this::onToastCheckBoxChanged);
        toastCheckBox.setTextColor(COLOR_META_INK);
        toastCheckBox.setTextFontScale(0.8f);
        toastCheckBox.setHoverTooltipSupplier(Texts.TOAST_CHECKBOX_TOOLTIP::getString);
        toastCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        loadingCheckBox = true;
        toastCheckBox.setChecked(StockMarketGuiElement.getPlayerPreferences().isNewsToastEnabled());
        loadingCheckBox = false;

        // T-109: per-player soft-clear button. Sits on the right side of the settings
        // row opposite the toast checkbox; no admin gating — every player clears their
        // own newspaper. Persisted server-side as PlayerPreferences.newsClearedBeforeMs
        // (the underlying NewsRecords are never touched — admins still see everything).
        clearButton = new Button(Texts.CLEAR_BUTTON.getString(), this::onClearClicked);
        clearButton.setHoverTooltipSupplier(Texts.CLEAR_BUTTON_TOOLTIP::getString);
        clearButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        // The scrollbar handle (an EmptyButton) has no public accessor on ListView —
        // style it via an anonymous subclass (protected member access) so the whole
        // scrollbar matches the paper look instead of the default gray widget tones.
        feedListView = new VerticalListView() {
            {
                scrollbarButton.setBackgroundColor(COLOR_PAPER_EDGE);
                scrollbarButton.setHoverColor(COLOR_META_INK);
                scrollbarButton.setPressedColor(COLOR_RULE);
                scrollbarButton.setOutlineColor(COLOR_RULE);
            }
        };
        LayoutVertical feedLayout = new LayoutVertical(2, 3, true, false);
        feedListView.setLayout(feedLayout);
        // Paper-styled list container: paper background, no dark widget outline,
        // and a paper-colored scrollbar track (the ListView always paints the track
        // rect — default 0xFF444444 — which broke the newspaper background).
        feedListView.setBackgroundColor(COLOR_PAPER);
        feedListView.setEnableBackground(true);
        feedListView.setEnableOutline(false);
        feedListView.setScrollbarBackgroundColor(COLOR_PAPER);

        loadMoreButton = new Button(Texts.LOAD_MORE.getString(), this::onLoadMoreClicked);

        emptyLabel = new Label(Texts.EMPTY.getString());
        emptyLabel.setAlignment(Label.Alignment.CENTER);
        emptyLabel.setTextColor(COLOR_META_INK);
        emptyLabel.setEnabled(false);

        addElement(paperFrame);
        addElement(mastheadLabel);
        addElement(mastheadRule);
        addElement(toastCheckBox);
        addElement(clearButton);
        addElement(feedListView);
        addElement(loadMoreButton);
        addElement(emptyLabel);

        // Instant content from the live cache, then fetch the first history page.
        ClientNewsCache cache = getNewsCache();
        if (cache != null) {
            cache.setChangeListener(this::onNewsCacheChanged);
            mergeRecords(cache.getRecords());
        }
        // T-091: a landed picture texture repaints the feed the same deferred way a
        // new record does — panels poll the cache per frame anyway, but the rebuild
        // also re-runs the prefetch bookkeeping. Composed with the news-cache
        // listener above (each cache supports exactly one listener); both are
        // cleared together in removed().
        ClientNewsPictureCache pictureCache = getPictureCache();
        if (pictureCache != null) {
            pictureCache.setChangeListener(() -> feedDirty = true);
        }
        requestNextPage(0);
    }

    /** Opens the newspaper screen (newspaper item / command entry point). */
    public static void openScreen() {
        setScreen(new NewsScreen());
    }

    /** @return the per-connection news cache, or null while not connected */
    private @Nullable ClientNewsCache getNewsCache() {
        return BACKEND_INSTANCES != null ? BACKEND_INSTANCES.NEWS_CACHE : null;
    }

    /** @return the per-connection news picture cache, or null while not connected (T-091) */
    private @Nullable ClientNewsPictureCache getPictureCache() {
        return BACKEND_INSTANCES != null ? BACKEND_INSTANCES.NEWS_PICTURE_CACHE : null;
    }

    // ── Data: merging cache + history pages ──────────────────────────────

    /**
     * Cache change listener: a new record was published while the screen is open.
     * Runs on the client main thread (Architectury dispatches the packet there).
     */
    private void onNewsCacheChanged() {
        ClientNewsCache cache = getNewsCache();
        if (cache != null)
            mergeRecords(cache.getRecords());
    }

    /**
     * Merges records into the feed, skipping already-known uids, keeps the feed
     * sorted newest-first and schedules a visual rebuild.
     *
     * @param records records from either the live cache or a history page
     */
    private void mergeRecords(List<NewsRecord> records) {
        boolean changed = false;
        for (NewsRecord record : records) {
            if (record == null || knownUids.contains(record.getNewsUid()))
                continue;
            knownUids.add(record.getNewsUid());
            entries.add(record);
            changed = true;
        }
        if (changed) {
            entries.sort(Comparator.comparingLong(NewsRecord::getNewsUid).reversed());
            feedDirty = true;
        }
    }

    /**
     * Fetches one history page from the master server.
     *
     * @param beforeUid exclusive uid cursor; 0 for the first (newest) page,
     *                  otherwise the uid of the oldest displayed record
     */
    private void requestNextPage(long beforeUid) {
        if (fetchInFlight || endReached)
            return;
        fetchInFlight = true;
        updateLoadMoreButton();
        BACKEND_INSTANCES.NETWORKING.NEWS_HISTORY_REQUEST
                .sendRequestToServer(new NewsHistoryRequest.InputData(beforeUid, PAGE_SIZE))
                .thenAccept(response -> Minecraft.getInstance().execute(() -> {
                    fetchInFlight = false;
                    List<NewsRecord> page = response != null ? response.records() : List.of();
                    if (page.isEmpty()) {
                        // Empty page = the end of the history was reached.
                        endReached = true;
                    } else {
                        mergeRecords(page);
                        if (page.size() < PAGE_SIZE)
                            endReached = true;
                    }
                    feedDirty = true;
                }));
    }

    /** "Load more" button: fetch the page older than the oldest displayed record. */
    private void onLoadMoreClicked() {
        if (entries.isEmpty()) {
            requestNextPage(0);
        } else {
            requestNextPage(entries.get(entries.size() - 1).getNewsUid());
        }
    }

    // ── Toast opt-in checkbox ────────────────────────────────────────────

    /**
     * Persists the toast opt-in flag in the player's preferences on the master
     * server (default off; survives relogs — see {@code PlayerPreferences}).
     *
     * @param checked the new checkbox state
     */
    private void onToastCheckBoxChanged(Boolean checked) {
        if (loadingCheckBox)
            return;
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        prefs.setNewsToastEnabled(checked);
        StockMarketGuiElement.updatePlayerPreferences(prefs);
    }

    // ── Soft-clear button (T-109) ────────────────────────────────────────

    /**
     * Soft-clears the newspaper for this player (T-109): sets
     * {@code newsClearedBeforeMs = System.currentTimeMillis()} in the player's
     * preferences, both optimistically on the client (so the feed re-renders
     * immediately without waiting for the round-trip) and via
     * {@link StockMarketGuiElement#updatePlayerPreferences} on the master server
     * (persisted, survives relogs). The underlying {@link NewsRecord}s are never
     * modified — admins still see everything, and new events published after the
     * clear appear in this player's feed as usual.
     */
    private void onClearClicked() {
        long nowMs = System.currentTimeMillis();
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        prefs.setNewsClearedBeforeMs(nowMs);
        StockMarketGuiElement.updatePlayerPreferences(prefs);
        // Optimistic local re-render: the local mirror is mutated in-place above, so
        // the next rebuildFeed already sees the new cutoff. Marking feedDirty defers
        // the actual rebuild to the next tick (matches the rest of this screen).
        feedDirty = true;
    }

    // ── Lifecycle / rendering ────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (feedDirty) {
            feedDirty = false;
            rebuildFeed();
        }
    }

    /**
     * {@inheritDoc}
     * Called by vanilla whenever this screen is replaced (including via
     * {@link #onClose()}); unregisters the cache change listener so a closed
     * newspaper no longer receives publish notifications.
     */
    @Override
    public void removed() {
        ClientNewsCache cache = getNewsCache();
        if (cache != null)
            cache.setChangeListener(null);
        // T-091: release the picture-cache listener slot too (set in the constructor).
        ClientNewsPictureCache pictureCache = getPictureCache();
        if (pictureCache != null)
            pictureCache.setChangeListener(null);
        super.removed();
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int eh = StockMarketGuiElement.defaultElementHeight;
        int width = getWidth();
        int height = getHeight();

        // Newspaper page margins: keep a paper sheet centered with slim margins.
        int sheetX = Math.max(p, width / 12);
        int sheetW = width - 2 * sheetX;
        paperFrame.setBounds(sheetX, p, sheetW, height - 2 * p);

        int innerX = sheetX + p;
        int innerW = sheetW - 2 * p;

        mastheadLabel.setBounds(innerX, p + s, innerW, 24);
        mastheadRule.setBounds(innerX, mastheadLabel.getBottom() + 2, innerW, 1);

        // Settings row below the masthead: the toast opt-in checkbox. T-086: the
        // checkbox used to span half the page — far wider than its visible label,
        // leaving a huge empty click/hover strip. Shrink it to about a third of
        // that, but never below what box + label text actually need.
        int cbTextW = (int) (Minecraft.getInstance().font.width(Texts.TOAST_CHECKBOX.getString()) * 0.8f);
        int cbWidth = Math.min(innerW / 2, Math.max(innerW / 6, cbTextW + eh));
        int settingsRowY = mastheadRule.getBottom() + s;
        toastCheckBox.setBounds(innerX, settingsRowY, cbWidth, eh - 4);

        // T-109: Clear button on the right side of the settings row (opposite the
        // toast checkbox) — sized to its label with a small padding, right-aligned
        // to the paper's inner edge.
        int clearTextW = Minecraft.getInstance().font.width(Texts.CLEAR_BUTTON.getString());
        int clearWidth = Math.max(eh * 2, clearTextW + eh);
        clearButton.setBounds(innerX + innerW - clearWidth, settingsRowY, clearWidth, eh - 4);

        // Footer: the load-more button.
        int footerY = height - p - eh - s;
        loadMoreButton.setBounds(innerX + innerW / 4, footerY, innerW / 2, eh);

        // Feed list fills the space between settings row and footer.
        int feedY = toastCheckBox.getBottom() + s;
        feedListView.setBounds(innerX, feedY, innerW, footerY - s - feedY);
        emptyLabel.setBounds(innerX, feedY + eh, innerW, eh);

        // Text wraps for the actual list width: rebuild the feed when it changes
        // (initial layout, window resize, GUI scale change).
        int feedWidth = feedListView.getContainerWidth();
        if (feedWidth > 0 && feedWidth != lastFeedWidth) {
            lastFeedWidth = feedWidth;
            rebuildFeed();
        }
    }

    /**
     * Rebuilds the feed list from {@link #entries} as a two-column newspaper page:
     * consecutive records pair up into rows (newest-first, left column then right
     * column), each row being an invisible {@link Frame} holding up to two
     * half-width {@link NewsEntryPanel}s. Row-pairing (instead of balanced column
     * flow) keeps the newest-first reading order left-to-right, top-to-bottom and
     * plugs straight into the existing {@link VerticalListView} stacking/scrolling.
     * <p>
     * Panels wrap their text for the column width and size their own height; the
     * row height is the taller of the pair (panels keep their natural height, so
     * a short article next to a long one leaves paper background below it — like
     * a real newspaper page). An odd trailing record simply fills the left column
     * of its row. All heights are final before the rows are added, so the list
     * layout stacks them correctly.
     * <p>
     * <b>T-109 soft-clear filter:</b> records with
     * {@code timestampEpochMs <= newsClearedBeforeMs} are excluded from the visible
     * feed. Filtered-out records remain in {@link #entries} (soft filter: the local
     * cache is not modified), so the "load more" cursor still walks past them via
     * the oldest-in-{@code entries} uid — a page that filters to zero visible rows
     * still advances the pagination correctly.
     */
    private void rebuildFeed() {
        int feedWidth = feedListView.getContainerWidth();
        if (feedWidth <= 0)
            return;
        // LayoutVertical(padding=2) stretches the row frames to containerWidth - 2*padding.
        int rowWidth = feedWidth - 4;
        // Two columns with a small gutter; the right column absorbs odd-pixel rounding.
        int gutter = StockMarketGuiElement.spacing;
        int leftWidth = (rowWidth - gutter) / 2;
        int rightWidth = rowWidth - gutter - leftWidth;

        // T-109: apply the per-player soft-clear filter. entries stays untouched so
        // pagination cursor logic (oldest displayed uid) still advances correctly
        // over pages whose records were all filtered out.
        long clearedBeforeMs = StockMarketGuiElement.getPlayerPreferences().getNewsClearedBeforeMs();
        List<NewsRecord> visible = new ArrayList<>(entries.size());
        for (NewsRecord record : entries) {
            if (record.getTimestampEpochMs() > clearedBeforeMs)
                visible.add(record);
        }

        feedListView.removeChilds();
        for (int i = 0; i < visible.size(); i += 2) {
            NewsEntryPanel left = new NewsEntryPanel(visible.get(i), leftWidth);
            int rowHeight = left.getHeight();

            // Invisible grouping frame; the panels keep manual bounds inside it.
            // Background/outline must be disabled EXPLICITLY: GuiElement defaults
            // render a translucent gray background + dark outline, which tiled the
            // whole feed with gray boxes over the paper (T-086 follow-up fix).
            Frame row = new Frame(0, 0, rowWidth, rowHeight);
            row.setEnableBackground(false);
            row.setEnableOutline(false);
            row.addChild(left);

            if (i + 1 < visible.size()) {
                NewsEntryPanel right = new NewsEntryPanel(visible.get(i + 1), rightWidth);
                right.setX(leftWidth + gutter);
                // T-086 follow-up: both cards of a row share the height of the
                // taller one (the shorter card stretches — content stays
                // top-aligned, the rest is plain card paper), so every vertical
                // gap between rows is constant. An odd trailing row (single
                // panel, this branch not taken) keeps its natural height.
                rowHeight = Math.max(rowHeight, right.getHeight());
                left.stretchToHeight(rowHeight);
                right.stretchToHeight(rowHeight);
                row.setHeight(rowHeight);
                row.addChild(right);
            }
            feedListView.addChild(row);
        }

        // T-109: "empty" label is a visible-empty check now — after Clear, entries
        // may still hold records but nothing is visible; the placeholder should
        // still show once the history is exhausted (endReached).
        emptyLabel.setEnabled(visible.isEmpty() && endReached);
        updateLoadMoreButton();

        // T-091: background-prefetch every picture in the feed (plan §12.4). Visible
        // panels fetch first automatically — their per-frame getTexture polls enqueue
        // at HIGH priority, this batch fills the BACKGROUND queue so scrolling never
        // waits. prefetchAll dedups against every known state, so re-running it on
        // every rebuild is cheap. Only pre-fetch pictures for records the player will
        // actually see — filtered-out records don't need their pictures.
        ClientNewsPictureCache pictureCache = getPictureCache();
        if (pictureCache != null) {
            List<byte[]> hashes = new ArrayList<>(visible.size());
            for (NewsRecord record : visible) {
                if (record.getPictureHash() != null)
                    hashes.add(record.getPictureHash());
            }
            pictureCache.prefetchAll(hashes);
        }
    }

    /** Updates the footer button text/clickability for the current fetch state. */
    private void updateLoadMoreButton() {
        if (fetchInFlight) {
            loadMoreButton.setText(Texts.LOADING.getString());
            loadMoreButton.setClickable(false);
        } else if (endReached) {
            loadMoreButton.setText(Texts.NO_MORE.getString());
            loadMoreButton.setClickable(false);
        } else {
            loadMoreButton.setText(Texts.LOAD_MORE.getString());
            loadMoreButton.setClickable(true);
        }
    }
}
