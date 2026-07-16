package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.CloseButton;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.screen.uiElements.NewsPictureElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
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
     * per-sequence step breakdown (T-100 — replaces the flat peak/ramp-up/hold/
     * reversal lines, which remain only as a fallback for sequence-less snapshots;
     * the running step is live-highlighted and multi-sequence headers are
     * collapsible), a live phase/remaining line (drawn from the owner's runtime
     * stream every frame), one row per impacted market, and — when non-empty — the
     * trigger-requirements (met/unmet) and chained-events sections (T-100).
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
         * One step row of the breakdown (T-100), split into three draw segments so
         * the impact percentage keeps its own up/down color:
         * {@code <i. name — duration — >}{@code <±impact%>}{@code < — curve [permanent]>}.
         *
         * @param seqIndex  index into {@code details.sequences()} (live-highlight key)
         * @param stepIndex 0-based step index within the sequence (live-highlight key)
         */
        private record StepLine(int seqIndex, int stepIndex, String left,
                                String impact, int impactColor, String right, int y) {}

        /**
         * One impacted market: resolved icon (child ItemView), the weight-factor
         * label (a child {@link Label} so it can carry the explanatory hover
         * tooltip, T-086) and the remaining texts (drawn in {@code render()}).
         */
        private record MarketRow(ItemView icon, String name, Label weightLabel,
                                 String peakText, int peakColor) {}

        private final List<Line> lines = new ArrayList<>();
        private final List<MarketRow> marketRows = new ArrayList<>();
        // T-100: sequence step breakdown (headers/steps drawn segment-wise in
        // render(); the indented per-step market-override lines are static).
        private final List<SeqHeader> seqHeaders = new ArrayList<>();
        private final List<StepLine> stepLines = new ArrayList<>();
        private final List<Line> stepMarketLines = new ArrayList<>();
        /** The snapshot this content renders (live-highlight sequence resolution). */
        private final NewsAdminRequest.EventDetails details;
        private final int builtWidth;
        /** Y of the dynamic live-status line (phase/remaining, render-time data). */
        private final int liveLineY;
        private final int marketsY;
        // Column widths of the left-clustered impact rows (max over all rows).
        private final int peakColumnW;
        private final int weightColumnW;

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

            // Live phase/remaining (drawn dynamically in render()).
            liveLineY = y;
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
         * Builds the per-sequence step breakdown (T-100, plan §6): one header per
         * sequence (with its weighted pick chance when the event has several — a
         * lone sequence always fires) and, unless collapsed, one row per step:
         * {@code i. name — duration — ±impact% — curve [permanent]}, plus an
         * indented market line for steps that override the event-level markets
         * (steps without one inherit them and show nothing).
         * <p>
         * Durations show the authored min–max range (a single value when fixed).
         * The activation-time rolled durations of a running instance are not part
         * of the wire snapshot, so they cannot be displayed here.
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

                for (int sti = 0; sti < sequence.steps().size(); sti++) {
                    NewsAdminRequest.EventDetails.StepInfo step = sequence.steps().get(sti);
                    String duration = step.durationMinMs() == step.durationMaxMs()
                            ? NewsUiFormatting.formatRemainingTime(step.durationMinMs())
                            : NewsUiFormatting.formatRemainingTime(step.durationMinMs())
                                    + "–" + NewsUiFormatting.formatRemainingTime(step.durationMaxMs());
                    double stepTerm = 1.0 + step.targetFactor();
                    int impactColor = stepTerm > 1.0 ? NewsPluginGuiElement.COLOR_UP_GREEN
                            : stepTerm < 1.0 ? NewsPluginGuiElement.COLOR_DOWN_RED
                            : NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
                    String right = " — " + step.curve() + (step.permanent()
                            ? " " + NewsPluginGuiElement.Texts.DETAILS_STEP_PERMANENT.getString()
                            : "");
                    stepLines.add(new StepLine(si, sti,
                            (sti + 1) + ". " + step.name() + " — " + duration + " — ",
                            NewsUiFormatting.formatFactorPercent(stepTerm),
                            impactColor, right, y));
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

            // Sequence breakdown (T-100): headers + step rows. The running step of
            // an active instance is live-highlighted (backdrop + white text); its
            // sequence's header is tinted green. Resolution/limitations: see
            // resolveRunningSequence.
            int runningSeq = resolveRunningSequence(live);
            int runningStep = runningSeq >= 0 && live != null ? live.stepIndex() : -1;
            int metaH = NewsUiText.lineHeight(this, META_SCALE);
            for (SeqHeader header : seqHeaders) {
                drawText(header.text(), INNER_PAD, header.y(),
                        header.seqIndex() == runningSeq
                                ? NewsPluginGuiElement.COLOR_UP_GREEN : 0xFFFFFFFF,
                        META_SCALE);
            }
            for (StepLine step : stepLines) {
                boolean highlighted = step.seqIndex() == runningSeq
                        && step.stepIndex() == runningStep;
                if (highlighted) {
                    drawRect(INNER_PAD, step.y() - 1,
                            builtWidth - 2 * INNER_PAD, metaH, STEP_HIGHLIGHT_BG);
                }
                // Segment-wise draw; defensive truncation — a truncated segment
                // swallows the ones after it (small windows / long step names).
                int x = INNER_PAD + STEP_INDENT;
                int limit = builtWidth - INNER_PAD;
                String left = NewsUiText.truncate(this, step.left(), limit - x, META_SCALE);
                drawText(left, x, step.y(), highlighted
                        ? 0xFFFFFFFF : NewsPluginGuiElement.COLOR_TEXT_SECONDARY, META_SCALE);
                if (!left.equals(step.left())) {
                    continue;
                }
                x += (int) (getTextWidth(left) * META_SCALE);
                String impact = NewsUiText.truncate(this, step.impact(), limit - x, META_SCALE);
                drawText(impact, x, step.y(), step.impactColor(), META_SCALE);
                if (!impact.equals(step.impact())) {
                    continue;
                }
                x += (int) (getTextWidth(impact) * META_SCALE);
                drawText(NewsUiText.truncate(this, step.right(), limit - x, META_SCALE),
                        x, step.y(), highlighted
                                ? 0xFFFFFFFF : NewsPluginGuiElement.COLOR_TEXT_SECONDARY,
                        META_SCALE);
            }
            String liveText;
            int liveColor;
            if (live != null) {
                liveText = NewsPluginGuiElement.Texts.detailsLive(
                        NewsPluginGuiElement.Texts.phase(live.phaseName()).getString(),
                        NewsUiFormatting.formatRemainingTime(live.remainingMs())).getString();
                liveColor = NewsPluginGuiElement.phaseColor(live.phaseName());
            } else {
                liveText = NewsPluginGuiElement.Texts.DETAILS_NOT_ACTIVE.getString();
                liveColor = NewsPluginGuiElement.COLOR_NEUTRAL_GRAY;
            }
            drawText(liveText, INNER_PAD, liveLineY, liveColor, META_SCALE);

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
    }
}
