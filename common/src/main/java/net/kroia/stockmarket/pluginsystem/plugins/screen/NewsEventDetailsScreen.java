package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.CloseButton;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.screen.uiElements.NewsPictureElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full details of one news event definition as a dedicated child {@link Screen}
 * (T-085; replaces the former in-window overlay popup). Opened from the news plugin
 * window's All-events rows and the upcoming-timeline entries.
 * <p>
 * <b>Navigation:</b> uses the parent-screen mechanism (ModSettingsScreen precedent) —
 * ESC/close returns to the <b>same parent screen instance</b>, so the news plugin
 * window keeps its complete state (selected tab, scroll positions, market selection).
 * <p>
 * <b>Data:</b> the screen renders the owning {@link NewsPluginGuiElement}'s
 * server-confirmed {@link NewsAdminRequest.EventDetails} snapshot. The owner keeps
 * receiving admin responses and the 500 ms runtime stream while this screen is open
 * (streams are only stopped when the plugin window itself closes), and notifies this
 * screen via {@link #onSnapshotRefreshed()} — the content then rebuilds from the fresh
 * snapshot, or the screen closes itself when the definition vanished (e.g. a reload
 * removed its file). The live phase/remaining line is drawn every frame from the
 * owner's latest runtime data.
 */
public class NewsEventDetailsScreen extends StockMarketGuiScreen {

    /**
     * T-105: local text keys owned by the details screen (kept out of
     * {@link NewsPluginGuiElement#Texts} because that file is touched by parallel
     * agents — copied here on purpose; the PM reconciles at merge time).
     */
    private static final class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".news_plugin.";
        static final Component PHASE_REMAINING =
                Component.translatable(PREFIX + "details_phase_remaining");
        static final Component EVENT_REMAINING =
                Component.translatable(PREFIX + "details_event_remaining");
        /** Value shown for a timer whose event is PENDING (announced but not published yet). */
        static final Component TIME_PENDING =
                Component.translatable(PREFIX + "details_time_pending");
        /** Value shown for a timer whose event has ended (PERMANENT/EXPIRED/terminal). */
        static final Component TIME_TERMINAL =
                Component.translatable(PREFIX + "details_time_terminal");
        /** Value shown when the event has no live instance running at all. */
        static final Component TIME_DASH =
                Component.translatable(PREFIX + "details_time_dash");
        // Step table column headers (T-105).
        static final Component TABLE_STEP_NO =
                Component.translatable(PREFIX + "details_table_step_no");
        static final Component TABLE_NAME =
                Component.translatable(PREFIX + "details_table_name");
        static final Component TABLE_DURATION =
                Component.translatable(PREFIX + "details_table_duration");
        /** Column header for the per-row live countdown cell (T-111). */
        static final Component TABLE_COUNTDOWN =
                Component.translatable(PREFIX + "details_table_countdown");
        static final Component TABLE_TARGET =
                Component.translatable(PREFIX + "details_table_target");
        static final Component TABLE_CURVE =
                Component.translatable(PREFIX + "details_table_curve");
        static final Component TABLE_PERMANENT =
                Component.translatable(PREFIX + "details_table_permanent");
        /** Cell text of a step that is permanent (checkmark). */
        static final Component TABLE_PERMANENT_MARK =
                Component.translatable(PREFIX + "details_table_permanent_mark");

        /**
         * Localizes a step curve name for display (T-105): {@code curve.linear} /
         * {@code curve.instant} / {@code curve.exponential} / {@code curve.hold}.
         * Unknown / author-defined curves fall through to the raw name.
         */
        static String curveName(String curve) {
            String key = PREFIX + "curve." + curve.toLowerCase(Locale.ROOT);
            return I18n.exists(key) ? I18n.get(key) : curve;
        }

        private Texts() {
        }
    }

    private final NewsPluginGuiElement owner;
    private final String eventId;
    private final CloseButton closeButton;
    private final VerticalListView contentList = new VerticalListView();
    private NewsAdminRequest.EventDetails details;
    private @Nullable DetailsContent content;
    /**
     * Indexes of collapsed sequences in the step breakdown (T-100) — screen-level so
     * the collapse state survives the content rebuilds triggered by snapshot
     * refreshes. Indexes may drift when a reload reorders the sequences; that is
     * harmless (worst case a different sequence starts out collapsed).
     */
    private final Set<Integer> collapsedSequences = new HashSet<>();

    /**
     * Creates the details screen for one definition snapshot.
     *
     * @param parent  the screen to return to on close (the plugin window's screen)
     * @param owner   the news plugin GUI element (snapshot + live-data source)
     * @param details the server-confirmed definition snapshot to show
     */
    public NewsEventDetailsScreen(Screen parent, NewsPluginGuiElement owner,
                                  NewsAdminRequest.EventDetails details) {
        super(NewsPluginGuiElement.Texts.DETAILS_SCREEN_TITLE, parent);
        this.owner = owner;
        this.eventId = details.id();
        this.details = details;

        closeButton = new CloseButton(this::onClose);
        contentList.setLayout(new LayoutVertical(0, 0, true, false));
        addElement(contentList);
        addElement(closeButton);
    }

    /** @return the shown definition id (used by the owner to route snapshot refreshes) */
    public String getEventId() {
        return eventId;
    }

    /**
     * Called by the owning element whenever a fresh server-confirmed snapshot arrived:
     * rebuilds the content (scroll preserved), or closes the screen when the shown
     * definition no longer exists (its file was removed by a reload).
     */
    public void onSnapshotRefreshed() {
        NewsAdminRequest.EventDetails fresh = owner.detailsFor(eventId);
        if (fresh == null) {
            onClose();
            return;
        }
        details = fresh;
        rebuildPreservingScroll();
    }

    /**
     * Rebuilds the content for the current snapshot, keeping the scroll position.
     * Also used by the sequence collapse/expand toggle (T-100).
     */
    private void rebuildPreservingScroll() {
        int savedScroll = contentList.getScrollOffset();
        rebuildContent();
        contentList.setScrollOffset(savedScroll);
    }

    /** @return the wrap width available to the content (list width minus scrollbar) */
    private int contentWidth() {
        return Math.max(60, contentList.getWidth() - contentList.getScrollbarThickness() - 2);
    }

    /** Rebuilds the content element for the current snapshot and wrap width. */
    private void rebuildContent() {
        contentList.removeChilds();
        content = new DetailsContent(details, contentWidth());
        contentList.addChild(content);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int w = getWidth();
        int h = getHeight();

        closeButton.setBounds(w - p - 20, p, 20, 20);
        contentList.setBounds(p, closeButton.getBottom() + s,
                w - 2 * p, h - closeButton.getBottom() - s - p);
        // (Re-)wrap the text when the available width changed (first init / resize).
        if (content == null || content.getBuiltWidth() != contentWidth()) {
            rebuildContent();
        }
    }

    /**
     * The scrollable body: locale-resolved headline, the event picture (T-091, a
     * square centered {@link NewsPictureElement} in COVER mode when the snapshot
     * carries a picture hash), the news text (word-wrapped), the definition
     * parameters (enabled, adminOnly, weight, cooldown, announce-delay range), the
     * per-sequence step breakdown as an aligned table (T-105 — replaces the free-
     * form text rows of T-100; column x positions are shared across every
     * sequence, the running step is live-highlighted, multi-sequence headers are
     * collapsible; T-111 tightens the Name column and adds a per-row live
     * countdown cell right after Duration), two split live timers — Phase
     * remaining (current step) and
     * Event remaining (best-effort sum of the remaining authored step durations)
     * — drawn from the owner's runtime stream every frame, one row per impacted
     * market, and — when non-empty — the trigger-requirements (met/unmet) and
     * chained-events sections (T-100).
     * <p>
     * <b>Impact-row layout (T-085):</b> everything is left-clustered — item icon, then
     * the signed peak percentage and the matcher weight factor in aligned columns
     * directly next to the icon, then the market name. (Previously the numbers were
     * right-aligned at the far edge, detached from the icon.)
     * <p>
     * All static lines are pre-wrapped for a fixed width at construction time — the
     * screen rebuilds this element when the width changes or a fresh snapshot arrives.
     */
    private class DetailsContent extends StockMarketGuiElement {

        private static final float TITLE_SCALE = 1.0f;
        private static final float META_SCALE = NewsPluginGuiElement.META_SCALE;
        private static final int INNER_PAD = 3;
        private static final int ICON_SIZE = 16;
        private static final int MARKET_ROW_H = ICON_SIZE + 2;
        /** Cap of the square event picture's side length in GUI pixels (T-091, plan §6). */
        private static final int PICTURE_MAX_SIDE = 140;
        /** Indent of a step row under its sequence header (T-100). */
        private static final int STEP_INDENT = 6;
        /** Translucent backdrop behind the live-highlighted running step (T-100). */
        private static final int STEP_HIGHLIGHT_BG = 0x28FFFFFF;
        /** Horizontal gap between table cells in GUI pixels (T-105). */
        private static final int TABLE_CELL_GAP = 6;
        /**
         * Extra breathing room appended to every table column's natural width
         * (T-105) so headers and values do not touch neighbouring columns. Value in
         * GUI pixels at the meta font scale.
         */
        private static final int TABLE_CELL_PADDING = 2;
        /** Font color of the step table's header row (T-105). */
        private static final int TABLE_HEADER_COLOR = 0xFFFFFFFF;
        /**
         * Compact fixed max width for the Name column in GUI pixels at the meta font
         * scale (T-111). Long step names truncate via
         * {@link NewsUiText#truncate}. The former flex-stretch behaviour (T-105) pushed
         * every subsequent column to the popup's right edge — this cap keeps every
         * column hugging the left edge so leftover row width sits as empty space on
         * the right.
         */
        private static final int NAME_MAX_WIDTH = 120;

        /** One pre-computed static text line. */
        private record Line(String text, int color, float scale, int y) {}

        /**
         * One sequence header of the step breakdown (T-100). Clickable headers
         * (multi-sequence events only) toggle their sequence's collapse state.
         *
         * @param seqIndex  index into {@code details.sequences()}
         * @param text      the pre-truncated header text (incl. ▼/▶ marker when clickable)
         * @param y         the line's y position
         * @param h         the click hit-box height (one meta line)
         * @param clickable true when clicking toggles collapse (>1 sequences)
         */
        private record SeqHeader(int seqIndex, String text, int y, int h, boolean clickable) {}

        /**
         * One table header row of the step breakdown (T-105). Emitted once per
         * expanded sequence (right below the sequence header, above the first
         * step row) so users can identify the columns of every sequence's block
         * independently — collapsing a sequence hides its header row along with
         * the step rows.
         *
         * @param y the row's y position
         */
        private record TableHeader(int y) {
        }

        /**
         * One step table row of the breakdown (T-105 — replaces the free-form
         * text row of T-100). Each cell text is pre-computed; the impact cell
         * keeps its own up/down color so the render stage can tint that one
         * column independently. Column x positions are shared across all
         * sequences (computed once at build time) so every step in every
         * sequence lines up.
         *
         * @param seqIndex  index into {@code details.sequences()} (live-highlight key)
         * @param stepIndex 0-based step index within the sequence (live-highlight key)
         * @param stepNo    the pre-formatted "1", "2", ... cell text
         * @param name      the step name cell text (pre-truncated)
         * @param duration  the authored min/max duration cell text (e.g. "01:30–04:00")
         * @param target    the signed target percentage cell text (e.g. "+30.0%")
         * @param targetColor color used for the target cell (up/down/neutral)
         * @param curve     the (translated when possible) curve cell text
         * @param permanent the permanent-flag cell text ("✓" or empty)
         * @param y         the row's y position
         */
        private record StepRow(int seqIndex, int stepIndex, String stepNo, String name,
                               String duration, String target, int targetColor,
                               String curve, String permanent, int y) {
        }

        /**
         * One impacted market: resolved icon (child ItemView), the weight-factor
         * label (a child {@link Label} so it can carry the explanatory hover
         * tooltip, T-086) and the remaining texts (drawn in {@code render()}).
         */
        private record MarketRow(ItemView icon, String name, Label weightLabel,
                                 String peakText, int peakColor) {}

        private final List<Line> lines = new ArrayList<>();
        private final List<MarketRow> marketRows = new ArrayList<>();
        // T-100/T-105: sequence step breakdown (headers/tables drawn segment-wise
        // in render(); the indented per-step market-override lines are static).
        private final List<SeqHeader> seqHeaders = new ArrayList<>();
        private final List<TableHeader> tableHeaders = new ArrayList<>();
        private final List<StepRow> stepRows = new ArrayList<>();
        private final List<Line> stepMarketLines = new ArrayList<>();
        /** The snapshot this content renders (live-highlight sequence resolution). */
        private final NewsAdminRequest.EventDetails details;
        private final int builtWidth;
        /**
         * T-105: y positions of the two split timers ({@code Phase remaining} and
         * {@code Event remaining}). Both are drawn dynamically in {@code render()}
         * from the runtime stream — phase from {@code stepRemainingMs}, event
         * best-effort from the picked sequence's remaining authored durations.
         */
        private final int phaseLineY;
        private final int eventLineY;
        private final int marketsY;
        // Column widths of the left-clustered impact rows (max over all rows).
        private final int peakColumnW;
        private final int weightColumnW;
        // T-105: shared step-table column x positions (computed once so every
        // sequence's block lines up). All values are content-relative — add
        // INNER_PAD + STEP_INDENT when drawing. Non-final because they are
        // computed after the first pass of the sequence walk.
        private int tableStepNoX;
        private int tableNameX;
        private int tableDurationX;
        /** T-111: x position of the live per-row countdown column. */
        private int tableCountdownX;
        private int tableTargetX;
        private int tableCurveX;
        private int tablePermanentX;
        private int tableRightEdgeX;
        /** Precomputed table header row cell texts (constant across sequences). */
        private String tableHeaderStepNo = "";
        private String tableHeaderName = "";
        private String tableHeaderDuration = "";
        /** T-111: countdown column header text. */
        private String tableHeaderCountdown = "";
        private String tableHeaderTarget = "";
        private String tableHeaderCurve = "";
        private String tableHeaderPermanent = "";

        /**
         * Builds the content for one definition snapshot.
         *
         * @param details the server-confirmed definition snapshot
         * @param width   the fixed wrap width in GUI pixels
         */
        DetailsContent(NewsAdminRequest.EventDetails details, int width) {
            setEnableBackground(false);
            setEnableOutline(false);
            this.details = details;
            this.builtWidth = width;

            int textW = width - 2 * INNER_PAD;
            int titleH = NewsUiText.lineHeight(this, TITLE_SCALE);
            int metaH = NewsUiText.lineHeight(this, META_SCALE);
            int y = INNER_PAD;

            // Headline (title scale, wrapped).
            for (String line : NewsUiText.wrapText(this,
                    NewsPluginGuiElement.resolveHeadline(details), textW, TITLE_SCALE)) {
                lines.add(new Line(line, 0xFFFFFFFF, TITLE_SCALE, y));
                y += titleH;
            }
            y += metaH / 2;

            // Event picture between headline and body (T-091): square COVER box,
            // side capped, centered on the content axis. A child element that polls
            // the picture cache itself — no callback wiring needed here (the owner's
            // snapshot refreshes rebuild this content anyway).
            if (details.pictureHash() != null) {
                int side = Math.min(textW, PICTURE_MAX_SIDE);
                NewsPictureElement picture = new NewsPictureElement(
                        details.pictureHash(), NewsPictureElement.FitMode.COVER);
                picture.setBounds(INNER_PAD + (textW - side) / 2, y, side, side);
                addChild(picture);
                y += side + metaH / 2;
            }

            // News text (wrapped, secondary color).
            String body = NewsTranslations.resolve(details.text(),
                    NewsPluginGuiElement.clientLanguage());
            if (!body.isEmpty()) {
                for (String line : NewsUiText.wrapText(this, body, textW, META_SCALE)) {
                    lines.add(new Line(line, NewsPluginGuiElement.COLOR_TEXT_SECONDARY, META_SCALE, y));
                    y += metaH;
                }
                y += metaH / 2;
            }

            // Definition parameters.
            y = addMetaLine(NewsPluginGuiElement.Texts.eventId(details.id()).getString(),
                    NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
            y = addMetaLine(NewsPluginGuiElement.Texts.detailsEnabled(
                            NewsPluginGuiElement.Texts.yesNo(details.enabled())).getString(),
                    details.enabled() ? NewsPluginGuiElement.COLOR_UP_GREEN
                            : NewsPluginGuiElement.COLOR_DOWN_RED, y, metaH);
            y = addMetaLine(NewsPluginGuiElement.Texts.detailsAdminOnly(
                            NewsPluginGuiElement.Texts.yesNo(details.adminOnly())).getString(),
                    details.adminOnly() ? NewsPluginGuiElement.COLOR_PENDING_ORANGE
                            : NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
            y = addMetaLine(NewsPluginGuiElement.Texts.detailsWeight(
                            String.valueOf(details.weight())).getString(),
                    NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
            String cooldown = details.cooldownRemainingMs() > 0
                    ? NewsPluginGuiElement.Texts.detailsCooldownRemaining(NewsUiFormatting
                            .formatRemainingTime(details.cooldownRemainingMs())).getString()
                    : NewsPluginGuiElement.Texts.DETAILS_COOLDOWN_READY.getString();
            y = addMetaLine(cooldown, NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
            y = addMetaLine(NewsPluginGuiElement.Texts.detailsAnnounceDelay(
                            String.valueOf(details.announceMinMs()),
                            String.valueOf(details.announceMaxMs())).getString(),
                    NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);

            // Impact description (T-100): since T-099 every wire snapshot carries a
            // sequences() block — legacy impact events yield their ONE implicit
            // normalized "impact" sequence — so the per-step breakdown below IS the
            // impact rendering (the server fills the flat legacy descriptor fields
            // with placeholder analogues for sequence-authored events and documents
            // that clients must render from sequences() instead). The flat lines are
            // kept only as a fallback for sequence-less snapshots (pre-T-099
            // convenience constructors, e.g. test fixtures — never on the wire).
            if (details.sequences().isEmpty()) {
                double peakTerm = 1.0 + details.peakFactor();
                int peakColor = details.peakFactor() > 0 ? NewsPluginGuiElement.COLOR_UP_GREEN
                        : details.peakFactor() < 0 ? NewsPluginGuiElement.COLOR_DOWN_RED
                        : NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                y = addMetaLine(NewsPluginGuiElement.Texts.detailsPeak(
                                NewsUiFormatting.formatFactorPercent(peakTerm),
                                String.valueOf(details.peakFactor())).getString(), peakColor, y, metaH);
                y = addMetaLine(NewsPluginGuiElement.Texts.detailsRampUp(
                                String.valueOf(details.rampUpSeconds())).getString(),
                        NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
                y = addMetaLine(NewsPluginGuiElement.Texts.detailsHold(
                                String.valueOf(details.durationSeconds())).getString(),
                        NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
                String reversal = "none".equals(details.reversal())
                        ? NewsPluginGuiElement.Texts.DETAILS_REVERSAL_PERMANENT.getString()
                        : NewsPluginGuiElement.Texts.detailsReversal(details.reversal(),
                                String.valueOf(details.reversalSeconds())).getString();
                y = addMetaLine(reversal, NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, y, metaH);
            } else {
                y = addSequenceBlock(textW, y, metaH);
            }

            // T-105: two split timers (drawn dynamically in render()) — phase
            // remaining (of the currently running step) and event remaining
            // (best-effort sum of the picked sequence's remaining step
            // durations). Stacked because the labels + mm:ss values would push
            // side-by-side layout past the wrap width on narrow windows.
            phaseLineY = y;
            y += metaH;
            eventLineY = y;
            y += metaH;
            y += metaH / 2;

            // Impacted markets.
            List<NewsAdminRequest.EventDetails.MarketImpact> markets = details.markets();
            y = addMetaLine(markets.isEmpty()
                            ? NewsPluginGuiElement.Texts.DETAILS_NO_MARKETS.getString()
                            : NewsPluginGuiElement.Texts.detailsMarketsTitle(markets.size()).getString(),
                    0xFFFFFFFF, y, metaH);
            marketsY = y;
            int maxPeakW = 0;
            int maxWeightW = 0;
            for (NewsAdminRequest.EventDetails.MarketImpact market : markets) {
                ItemStack stack = owner.resolveMarketStack(market.marketId(), market.displayName());
                ItemView icon = new ItemView(stack);
                icon.setShowTooltip(false); // the name is drawn next to the icon
                addChild(icon);
                // Signed peak percent this market would see at the event's peak
                // (weightFactor applied, sensitivity/noise excluded — same formula
                // as the server's INFO rendering).
                double marketPeak = NewsPlugin.eventFactorTerm(
                        1.0, details.peakFactor(), market.weightFactor(), 1.0, 0);
                int color = marketPeak > 1.0 ? NewsPluginGuiElement.COLOR_UP_GREEN
                        : marketPeak < 1.0 ? NewsPluginGuiElement.COLOR_DOWN_RED
                        : NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                String peakText = NewsUiFormatting.formatFactorPercent(marketPeak);
                String weightText = NewsPluginGuiElement.Texts.detailsWeightFactor(
                        String.valueOf(market.weightFactor())).getString();
                maxPeakW = Math.max(maxPeakW, (int) (getTextWidth(peakText) * META_SCALE));
                maxWeightW = Math.max(maxWeightW, (int) (getTextWidth(weightText) * META_SCALE));

                // T-086: the "×w" multiplier is a child Label so it can explain
                // itself on hover (weightFactor scales the event's peak impact for
                // this market; negative inverts). The label sits near the LEFT
                // window edge — anchor the tooltip's top-LEFT corner at the mouse
                // so it expands rightwards and never clips outside the window.
                Label weightLabel = new Label(weightText);
                weightLabel.setPadding(0);
                weightLabel.setTextFontScale(META_SCALE);
                weightLabel.setTextColor(NewsPluginGuiElement.COLOR_NEUTRAL_GRAY);
                weightLabel.setHoverTooltipSupplier(
                        NewsPluginGuiElement.Texts.WEIGHT_FACTOR_TOOLTIP::getString);
                weightLabel.setHoverTooltipFontScale(hoverToolTipFontSize);
                weightLabel.setHoverTooltipMousePositionAlignment(Alignment.TOP_LEFT);
                addChild(weightLabel);

                marketRows.add(new MarketRow(icon, stack.getHoverName().getString(),
                        weightLabel, peakText, color));
            }
            peakColumnW = maxPeakW;
            weightColumnW = maxWeightW;
            y += marketRows.size() * MARKET_ROW_H;

            // Trigger requirements (T-100, plan §10.1): every entry with its live
            // met/unmet state — unmet entries stand out in red (they feed the
            // "Trigger anyway?" confirmation). Section only when non-empty.
            if (!details.requirements().isEmpty()) {
                y += metaH / 2;
                y = addMetaLine(NewsPluginGuiElement.Texts.DETAILS_REQUIREMENTS_TITLE.getString(),
                        0xFFFFFFFF, y, metaH);
                for (NewsAdminRequest.EventDetails.RequirementStatus requirement
                        : details.requirements()) {
                    String prefixed = (requirement.met()
                            ? NewsPluginGuiElement.REQ_MET_PREFIX
                            : NewsPluginGuiElement.REQ_UNMET_PREFIX) + requirement.description();
                    int color = requirement.met() ? NewsPluginGuiElement.COLOR_UP_GREEN
                            : NewsPluginGuiElement.COLOR_DOWN_RED;
                    for (String line : NewsUiText.wrapText(this, prefixed, textW, META_SCALE)) {
                        y = addMetaLine(line, color, y, metaH);
                    }
                }
            }

            // Chained events (T-100): the server-rendered chain display lines.
            // Section only when non-empty.
            if (!details.chains().isEmpty()) {
                y += metaH / 2;
                y = addMetaLine(NewsPluginGuiElement.Texts.DETAILS_CHAINS_TITLE.getString(),
                        0xFFFFFFFF, y, metaH);
                for (String chainLine : details.chains()) {
                    for (String line : NewsUiText.wrapText(this, chainLine, textW, META_SCALE)) {
                        y = addMetaLine(line, NewsPluginGuiElement.COLOR_TEXT_SECONDARY, y, metaH);
                    }
                }
            }

            setSize(width, y + INNER_PAD);
        }

        /** @return the fixed width this content was wrapped for */
        int getBuiltWidth() {
            return builtWidth;
        }

        /** Adds one meta-scale line and returns the advanced y position. */
        private int addMetaLine(String text, int color, int y, int metaH) {
            lines.add(new Line(text, color, META_SCALE, y));
            return y + metaH;
        }

        /**
         * Builds the per-sequence step breakdown (T-105 — replaces the free-form
         * text row layout of T-100 with a proper aligned table): one clickable
         * header per sequence (with its weighted pick chance when the event has
         * several — a lone sequence always fires) and, unless collapsed, a
         * single {@code # | Name | Duration | Countdown | Target | Curve |
         * Permanent} table whose column x positions are shared across every
         * sequence's block so every step lines up. Per-step market overrides
         * render as an indented text line right under the step row (empty
         * per-step lists inherit the event-level markets and render nothing,
         * matching the wire contract).
         * <p>
         * Durations show the authored min–max range (a single value when fixed).
         * The activation-time rolled durations of a running instance are not part
         * of the wire snapshot, so they cannot be displayed here — this is
         * documented in {@code NewsAdminRequest.EventDetails.StepInfo}. The
         * Countdown cell (T-111) shows a live per-step countdown for the row of
         * the picked sequence's currently running step; every other row (past,
         * future, unpicked candidate sequences, PENDING/terminal sentinel) shows
         * the em-dash placeholder.
         *
         * @param textW the wrap/truncation width
         * @param y     the current y position
         * @param metaH the meta line advance
         * @return the advanced y position
         */
        private int addSequenceBlock(int textW, int y, int metaH) {
            List<NewsAdminRequest.EventDetails.SequenceInfo> sequences = details.sequences();
            boolean multiple = sequences.size() > 1;
            float totalWeight = 0;
            for (NewsAdminRequest.EventDetails.SequenceInfo sequence : sequences) {
                totalWeight += Math.max(0, sequence.weight());
            }

            // First pass: compute cell texts for every step + widen column
            // widths (natural max across headers and every cell of every
            // sequence — this is what makes rows line up across sequences).
            List<List<StepCells>> perSequenceCells = new ArrayList<>(sequences.size());
            tableHeaderStepNo = Texts.TABLE_STEP_NO.getString();
            tableHeaderName = Texts.TABLE_NAME.getString();
            tableHeaderDuration = Texts.TABLE_DURATION.getString();
            tableHeaderCountdown = Texts.TABLE_COUNTDOWN.getString();
            tableHeaderTarget = Texts.TABLE_TARGET.getString();
            tableHeaderCurve = Texts.TABLE_CURVE.getString();
            tableHeaderPermanent = Texts.TABLE_PERMANENT.getString();
            int stepNoW = measure(tableHeaderStepNo);
            int nameW = measure(tableHeaderName);
            int durationW = measure(tableHeaderDuration);
            // T-111: countdown column width — pre-measure the widest value the cell
            // will ever draw so the column doesn't jitter frame-to-frame as the
            // runtime ticks down. Runtime countdown is bounded above by the step's
            // rolled duration, itself bounded by durationMaxMs.
            int countdownW = Math.max(measure(tableHeaderCountdown),
                    measure(Texts.TIME_DASH.getString()));
            int targetW = measure(tableHeaderTarget);
            int curveW = measure(tableHeaderCurve);
            int permanentW = measure(tableHeaderPermanent);

            for (NewsAdminRequest.EventDetails.SequenceInfo sequence : sequences) {
                List<StepCells> cells = new ArrayList<>(sequence.steps().size());
                for (int sti = 0; sti < sequence.steps().size(); sti++) {
                    NewsAdminRequest.EventDetails.StepInfo step = sequence.steps().get(sti);
                    String stepNo = String.valueOf(sti + 1);
                    String duration = step.durationMinMs() == step.durationMaxMs()
                            ? NewsUiFormatting.formatRemainingTime(step.durationMinMs())
                            : NewsUiFormatting.formatRemainingTime(step.durationMinMs())
                                    + "–" + NewsUiFormatting.formatRemainingTime(step.durationMaxMs());
                    double stepTerm = 1.0 + step.targetFactor();
                    int targetColor = stepTerm > 1.0 ? NewsPluginGuiElement.COLOR_UP_GREEN
                            : stepTerm < 1.0 ? NewsPluginGuiElement.COLOR_DOWN_RED
                            : NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                    String target = NewsUiFormatting.formatFactorPercent(stepTerm);
                    // Localize the classic curve names; unknown / author-defined
                    // curves fall through to the raw JSON name (T-105).
                    String curve = Texts.curveName(step.curve());
                    String permanent = step.permanent()
                            ? Texts.TABLE_PERMANENT_MARK.getString() : "";
                    // T-094 validates that only the last step may be permanent —
                    // but even if a future wire snapshot violated that, this row
                    // still renders correctly (the marker is per-row anyway).
                    cells.add(new StepCells(stepNo, step.name(), duration, target,
                            targetColor, curve, permanent));
                    stepNoW = Math.max(stepNoW, measure(stepNo));
                    nameW = Math.max(nameW, measure(step.name()));
                    durationW = Math.max(durationW, measure(duration));
                    // T-111: reserve enough space for the widest possible runtime
                    // countdown for this step (bounded above by durationMaxMs).
                    countdownW = Math.max(countdownW,
                            measure(NewsUiFormatting.formatRemainingTime(step.durationMaxMs())));
                    targetW = Math.max(targetW, measure(target));
                    curveW = Math.max(curveW, measure(curve));
                    permanentW = Math.max(permanentW, measure(permanent));
                }
                perSequenceCells.add(cells);
            }

            // Column x positions (all content-relative — draw with the parent's
            // INNER_PAD + STEP_INDENT offset). Every column gets an extra
            // TABLE_CELL_PADDING; T-111: the Name column is capped at a compact
            // fixed max width so ALL columns hug the left edge — long step names
            // truncate defensively (see below). Any leftover row width sits as
            // empty space on the RIGHT of the last column. Column order:
            // # | Name | Duration | Countdown | Target | Curve | Perm.
            // Countdown is placed right of Duration so the authored range and the
            // live runtime value sit side-by-side.
            int rowInnerX = STEP_INDENT;
            int nameSlot = Math.min(NAME_MAX_WIDTH, nameW + TABLE_CELL_PADDING);

            tableStepNoX = rowInnerX;
            tableNameX = tableStepNoX + stepNoW + TABLE_CELL_PADDING + TABLE_CELL_GAP;
            tableDurationX = tableNameX + nameSlot + TABLE_CELL_GAP;
            tableCountdownX = tableDurationX + durationW + TABLE_CELL_PADDING + TABLE_CELL_GAP;
            tableTargetX = tableCountdownX + countdownW + TABLE_CELL_PADDING + TABLE_CELL_GAP;
            tableCurveX = tableTargetX + targetW + TABLE_CELL_PADDING + TABLE_CELL_GAP;
            tablePermanentX = tableCurveX + curveW + TABLE_CELL_PADDING + TABLE_CELL_GAP;
            tableRightEdgeX = tablePermanentX + permanentW + TABLE_CELL_PADDING;

            // Second pass: emit the sequence headers, table header rows and
            // step rows at concrete y positions.
            for (int si = 0; si < sequences.size(); si++) {
                NewsAdminRequest.EventDetails.SequenceInfo sequence = sequences.get(si);
                boolean collapsed = multiple && collapsedSequences.contains(si);

                String header;
                if (multiple) {
                    String chance = totalWeight > 0
                            ? String.format(Locale.ROOT, "%.0f%%",
                                    100f * Math.max(0, sequence.weight()) / totalWeight)
                            : NewsUiFormatting.INVALID_FACTOR_TEXT;
                    header = (collapsed ? "▶ " : "▼ ")
                            + NewsPluginGuiElement.Texts.detailsSequenceHeaderChance(
                                    sequence.name(), sequence.steps().size(), chance).getString();
                } else {
                    header = NewsPluginGuiElement.Texts.detailsSequenceHeader(
                            sequence.name(), sequence.steps().size()).getString();
                }
                seqHeaders.add(new SeqHeader(si,
                        NewsUiText.truncate(this, header, textW, META_SCALE), y, metaH, multiple));
                y += metaH;
                if (collapsed) {
                    continue;
                }

                // Table header row (only when this sequence actually has steps).
                if (!sequence.steps().isEmpty()) {
                    tableHeaders.add(new TableHeader(y));
                    y += metaH;
                }

                List<StepCells> cells = perSequenceCells.get(si);
                for (int sti = 0; sti < cells.size(); sti++) {
                    NewsAdminRequest.EventDetails.StepInfo step = sequence.steps().get(sti);
                    StepCells c = cells.get(sti);
                    // Defensive truncation for the name column — every other
                    // column is measured to fit its widest natural value, so
                    // only the flexible name column can overflow on narrow
                    // windows.
                    String truncatedName = NewsUiText.truncate(this, c.name(), nameSlot,
                            META_SCALE);
                    stepRows.add(new StepRow(si, sti, c.stepNo(), truncatedName,
                            c.duration(), c.target(), c.targetColor(), c.curve(),
                            c.permanent(), y));
                    y += metaH;

                    // Per-step market override (an empty list = inherits the event
                    // markets and shows nothing, matching the wire contract).
                    if (!step.markets().isEmpty()) {
                        StringBuilder joined = new StringBuilder();
                        for (NewsAdminRequest.EventDetails.MarketImpact market : step.markets()) {
                            if (!joined.isEmpty()) {
                                joined.append(", ");
                            }
                            joined.append(owner.resolveMarketStack(market.marketId(),
                                            market.displayName()).getHoverName().getString())
                                    .append(" ")
                                    .append(NewsPluginGuiElement.Texts.detailsWeightFactor(
                                            String.valueOf(market.weightFactor())).getString());
                        }
                        String subLine = NewsUiText.truncate(this,
                                NewsPluginGuiElement.Texts.detailsStepMarkets(
                                        joined.toString()).getString(),
                                textW - 2 * STEP_INDENT, META_SCALE);
                        stepMarketLines.add(new Line(subLine,
                                NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, META_SCALE, y));
                        y += metaH;
                    }
                }
            }
            return y;
        }

        /** Measures a string at the meta font scale (T-105 — column widening). */
        private int measure(String text) {
            return (int) Math.ceil(getTextWidth(text) * META_SCALE);
        }

        /** Temporary per-step cell texts used during the two-pass table build (T-105). */
        private record StepCells(String stepNo, String name, String duration,
                                 String target, int targetColor, String curve,
                                 String permanent) {
        }

        /**
         * Resolves which sequence the currently running instance picked (T-100).
         * The runtime stream does not carry the picked sequence's name — the step
         * identity (stepCount + the current step's name at stepIndex) identifies it
         * instead. For multi-sequence events whose sequences share both the step
         * count and the running step's name this is ambiguous; the FIRST matching
         * sequence wins (accepted limitation — the highlight may then sit in the
         * wrong, structurally identical sequence).
         *
         * @param live the streamed live info of this event, or null while inactive
         * @return the picked sequence's index, or -1 while not running / no match
         */
        private int resolveRunningSequence(
                NewsPlugin.RuntimeStreamData.@Nullable ActiveEventInfo live) {
            if (live == null || live.stepRemainingMs() < 0) {
                return -1; // PENDING or terminal — nothing runs, nothing highlights
            }
            List<NewsAdminRequest.EventDetails.SequenceInfo> sequences = details.sequences();
            for (int i = 0; i < sequences.size(); i++) {
                List<NewsAdminRequest.EventDetails.StepInfo> steps = sequences.get(i).steps();
                if (steps.size() == live.stepCount()
                        && live.stepIndex() >= 0 && live.stepIndex() < steps.size()
                        && steps.get(live.stepIndex()).name().equals(live.stepName())) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Collapses/expands a sequence when its header line is clicked (T-100 —
         * headers are only clickable for multi-sequence events). Child elements
         * (market icons, weight labels) consume their clicks first, so they never
         * reach this handler.
         */
        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button != 0) {
                return false;
            }
            int mouseY = getMouseY();
            for (SeqHeader header : seqHeaders) {
                if (header.clickable() && mouseY >= header.y() && mouseY < header.y() + header.h()) {
                    if (!collapsedSequences.remove(header.seqIndex())) {
                        collapsedSequences.add(header.seqIndex());
                    }
                    rebuildPreservingScroll();
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void layoutChanged() {
            // Only the market icons and the weight labels (T-086: real child
            // elements so they carry a hover tooltip) depend on geometry; the
            // remaining texts are drawn in render().
            int y = marketsY;
            int textH = Math.round(getTextHeight() * META_SCALE);
            int textOffsetY = (ICON_SIZE - textH) / 2;
            int weightX = INNER_PAD + ICON_SIZE + spacing + peakColumnW + 2 * spacing;
            for (MarketRow row : marketRows) {
                row.icon().setBounds(INNER_PAD, y, ICON_SIZE, ICON_SIZE);
                row.weightLabel().setBounds(weightX, y + textOffsetY, weightColumnW, textH);
                y += MARKET_ROW_H;
            }
        }

        @Override
        protected void render() {
            for (Line line : lines) {
                drawText(line.text(), INNER_PAD, line.y(), line.color(), line.scale());
            }
            // Indented per-step market-override lines (T-100, static).
            for (Line line : stepMarketLines) {
                drawText(line.text(), INNER_PAD + 2 * STEP_INDENT, line.y(),
                        line.color(), line.scale());
            }

            // Live status: phase + remaining while an instance runs (stream data).
            // Fetched once per frame — also drives the running-step highlight below.
            NewsPlugin.RuntimeStreamData.ActiveEventInfo live = owner.liveInfoFor(eventId);

            // Sequence breakdown (T-105 table): sequence header, table header row,
            // step rows. The running step of an active instance is live-highlighted
            // (backdrop + white text); its sequence's header is tinted green.
            // Resolution/limitations: see resolveRunningSequence.
            int runningSeq = resolveRunningSequence(live);
            int runningStep = runningSeq >= 0 && live != null ? live.stepIndex() : -1;
            int metaH = NewsUiText.lineHeight(this, META_SCALE);
            for (SeqHeader header : seqHeaders) {
                drawText(header.text(), INNER_PAD, header.y(),
                        header.seqIndex() == runningSeq
                                ? NewsPluginGuiElement.COLOR_UP_GREEN : 0xFFFFFFFF,
                        META_SCALE);
            }
            int tableBaseX = INNER_PAD + STEP_INDENT;
            for (TableHeader th : tableHeaders) {
                drawText(tableHeaderStepNo, tableBaseX + tableStepNoX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderName, tableBaseX + tableNameX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderDuration, tableBaseX + tableDurationX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderCountdown, tableBaseX + tableCountdownX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderTarget, tableBaseX + tableTargetX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderCurve, tableBaseX + tableCurveX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
                drawText(tableHeaderPermanent, tableBaseX + tablePermanentX, th.y(),
                        TABLE_HEADER_COLOR, META_SCALE);
            }
            // T-111: precompute the running-step's countdown text once — every row
            // of every OTHER step (past/future/unpicked sequence) shows the em-dash
            // placeholder. PENDING/terminal sentinel (stepRemainingMs < 0 or no
            // live info) => all rows show the em-dash.
            String dashText = Texts.TIME_DASH.getString();
            String runningCountdownText = null;
            if (live != null && live.stepRemainingMs() >= 0 && runningSeq >= 0) {
                runningCountdownText = NewsUiFormatting.formatRemainingTime(
                        Math.max(0, live.stepRemainingMs()));
            }
            for (StepRow step : stepRows) {
                boolean highlighted = step.seqIndex() == runningSeq
                        && step.stepIndex() == runningStep;
                if (highlighted) {
                    // Backdrop spans the full content row (matches T-100).
                    drawRect(INNER_PAD, step.y() - 1,
                            builtWidth - 2 * INNER_PAD, metaH, STEP_HIGHLIGHT_BG);
                }
                int textColor = highlighted
                        ? 0xFFFFFFFF : NewsPluginGuiElement.COLOR_TEXT_SECONDARY;
                drawText(step.stepNo(), tableBaseX + tableStepNoX, step.y(),
                        textColor, META_SCALE);
                drawText(step.name(), tableBaseX + tableNameX, step.y(),
                        textColor, META_SCALE);
                drawText(step.duration(), tableBaseX + tableDurationX, step.y(),
                        textColor, META_SCALE);
                // T-111: per-row live countdown cell. Only the row of the picked
                // sequence's currently running step shows a live value; every other
                // row (past, future, unpicked candidate sequences, PENDING/terminal
                // sentinel) shows the em-dash placeholder.
                String countdownCell = highlighted && runningCountdownText != null
                        ? runningCountdownText : dashText;
                drawText(countdownCell, tableBaseX + tableCountdownX, step.y(),
                        textColor, META_SCALE);
                // Target column keeps its up/down/neutral color even when the row
                // is highlighted — the sign is more important than uniform contrast.
                drawText(step.target(), tableBaseX + tableTargetX, step.y(),
                        step.targetColor(), META_SCALE);
                drawText(step.curve(), tableBaseX + tableCurveX, step.y(),
                        textColor, META_SCALE);
                drawText(step.permanent(), tableBaseX + tablePermanentX, step.y(),
                        textColor, META_SCALE);
            }
            // T-105: split timers — Phase remaining (current step) and Event
            // remaining (best-effort sum of remaining authored step durations
            // for the picked sequence). See computeEventRemainingMs for the
            // authored-min conservative estimate rationale.
            drawSplitTimers(live);

            // Impact rows, left-clustered: icon | ±peak % | ×weight | name (T-085).
            // The ×weight column is rendered by the child Labels (tooltip, T-086).
            int y = marketsY;
            int textOffsetY = (ICON_SIZE - Math.round(getTextHeight() * META_SCALE)) / 2;
            for (MarketRow row : marketRows) {
                int peakX = INNER_PAD + ICON_SIZE + spacing;
                int nameX = peakX + peakColumnW + 2 * spacing + weightColumnW + 2 * spacing;
                drawText(row.peakText(), peakX, y + textOffsetY, row.peakColor(), META_SCALE);
                drawText(NewsUiText.truncate(this, row.name(),
                                getWidth() - nameX - INNER_PAD, META_SCALE),
                        nameX, y + textOffsetY, NewsPluginGuiElement.COLOR_TEXT_SECONDARY, META_SCALE);
                y += MARKET_ROW_H;
            }
        }

        /**
         * Draws the two split live timers (T-105) — the older single "Live: …"
         * label lied by combining the streamed {@code stepRemainingMs} label
         * with the sequence-total {@code remainingMs}, so operators couldn't
         * tell how long the current step still had left. This split shows:
         * <ul>
         *   <li><b>Phase remaining</b> — driven purely by
         *       {@code stepRemainingMs} (the −1 sentinel means the event is
         *       either PENDING or terminal; both states render text values).</li>
         *   <li><b>Event remaining</b> — best-effort sum of the remaining
         *       authored step durations for the sequence identified by the
         *       running step (see {@link #computeEventRemainingMs}); "—" when
         *       the event is not currently running.</li>
         * </ul>
         *
         * @param live the current runtime info of this event, or null while
         *             inactive
         */
        private void drawSplitTimers(NewsPlugin.RuntimeStreamData.@Nullable ActiveEventInfo live) {
            String phaseRemainingText;
            int phaseRemainingColor;
            String eventRemainingText;
            int eventRemainingColor;
            if (live == null) {
                // No running instance at all — both timers show the neutral
                // dash placeholder.
                phaseRemainingText = Texts.TIME_DASH.getString();
                phaseRemainingColor = NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                eventRemainingText = Texts.TIME_DASH.getString();
                eventRemainingColor = NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
            } else if (live.stepRemainingMs() < 0) {
                // Sentinel: PENDING (announced but not published yet) or
                // terminal (PERMANENT/EXPIRED). The phase timer distinguishes
                // the two states via text; the event timer stays "—" in both
                // (the event either hasn't started or has already ended).
                boolean pending = "PENDING".equals(live.phaseName());
                phaseRemainingText = pending ? Texts.TIME_PENDING.getString()
                        : Texts.TIME_TERMINAL.getString();
                phaseRemainingColor = NewsPluginGuiElement.phaseColor(live.phaseName());
                eventRemainingText = Texts.TIME_DASH.getString();
                eventRemainingColor = NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
            } else {
                // Instance is running — real countdowns for both timers.
                long phaseMs = Math.max(0, live.stepRemainingMs());
                phaseRemainingText = NewsUiFormatting.formatRemainingTime(phaseMs);
                phaseRemainingColor = NewsPluginGuiElement.phaseColor(live.phaseName());
                long eventMs = computeEventRemainingMs(live);
                if (eventMs < 0) {
                    // Sequence could not be resolved (structurally identical
                    // sequences, name mismatch, etc.) — fall back to "—" rather
                    // than pretend a number.
                    eventRemainingText = Texts.TIME_DASH.getString();
                    eventRemainingColor = NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                } else {
                    eventRemainingText = NewsUiFormatting.formatRemainingTime(eventMs);
                    eventRemainingColor = NewsPluginGuiElement.phaseColor(live.phaseName());
                }
            }
            // Label + value on one line each. The labels share the neutral gray
            // used by every other left-column meta label; the values take the
            // phase color so users can spot at a glance which phase is running.
            drawTimerLine(Texts.PHASE_REMAINING.getString(), phaseRemainingText,
                    phaseRemainingColor, phaseLineY);
            drawTimerLine(Texts.EVENT_REMAINING.getString(), eventRemainingText,
                    eventRemainingColor, eventLineY);
        }

        /**
         * Draws one timer row — the label at the neutral meta color, the value
         * appended right after it in the given phase-tinted color. Kept simple
         * (no fixed column between label and value) because the labels are
         * short and the values are always mm:ss / short sentinels.
         */
        private void drawTimerLine(String label, String value, int valueColor, int y) {
            drawText(label, INNER_PAD, y,
                    NewsPluginGuiElement.COLOR_NEUTRAL_GRAY, META_SCALE);
            int labelW = (int) Math.ceil(getTextWidth(label) * META_SCALE);
            drawText(value, INNER_PAD + labelW + spacing, y, valueColor, META_SCALE);
        }

        /**
         * Best-effort estimate of the whole-event time remaining (T-105) — the
         * running step's actual remaining ms (rolled at activation, part of
         * the stream) plus the authored <b>minimum</b> duration of every
         * subsequent non-permanent step. The permanent last step of a
         * sequence (if any) contributes 0 ms — it fires instantly at the end
         * of the previous step and then retires the event. The activation-
         * time rolled durations of the subsequent steps are <b>not</b> on
         * the wire (T-100 completion notes flag this), so we deliberately
         * pick the minimum bound for a conservative (never-overshooting)
         * estimate.
         *
         * @param live the current runtime info (already known non-null with
         *             {@code stepRemainingMs >= 0})
         * @return the estimated remaining ms, or −1 if the picked sequence
         *         could not be resolved
         */
        private long computeEventRemainingMs(NewsPlugin.RuntimeStreamData.ActiveEventInfo live) {
            int seqIndex = resolveRunningSequence(live);
            if (seqIndex < 0) {
                return -1;
            }
            List<NewsAdminRequest.EventDetails.StepInfo> steps =
                    details.sequences().get(seqIndex).steps();
            long remaining = Math.max(0, live.stepRemainingMs());
            for (int i = live.stepIndex() + 1; i < steps.size(); i++) {
                NewsAdminRequest.EventDetails.StepInfo step = steps.get(i);
                if (step.permanent()) {
                    // Permanent last step: 0 ms, event retires immediately.
                    continue;
                }
                remaining += Math.max(0, step.durationMinMs());
            }
            return remaining;
        }
    }
}
