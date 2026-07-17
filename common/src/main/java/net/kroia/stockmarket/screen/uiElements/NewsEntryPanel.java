package net.kroia.stockmarket.screen.uiElements;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One entry of the newspaper feed (task T-074, plan §4): renders a single published
 * {@link NewsRecord} in newspaper style —
 * <ul>
 *   <li><b>headline</b> — prominent (larger font), resolved from the record's inline
 *       translation map at every rebuild (see {@link NewsTranslations}),</li>
 *   <li><b>picture</b> (T-091, layout redesigned in T-113) — records carrying a
 *       loaded picture hash show a square {@link NewsPictureElement} (COVER mode,
 *       side capped at {@value #PICTURE_MAX_SIDE} px) at the <b>top-right corner</b>
 *       of the card; the headline and body wrap around the image (reduced-width
 *       region on the left for lines that would overlap the image, full-width
 *       region below the image bottom edge — real-newspaper column style). Text-
 *       only records and records whose picture is not (yet) available render at
 *       full width with no image slot at all — no placeholder, no ink outline
 *       (T-113 hide-when-absent contract),</li>
 *   <li><b>newspaper text</b> — word-wrapped body below the headline, flowing
 *       around the picture as described above,</li>
 *   <li><b>timestamp line</b> — client-local formatted publish date/time plus the
 *       immersive "Day N" game day, spanning the full meta row width.</li>
 * </ul>
 * <p>
 * <b>No market-impact info (task T-084):</b> the panel deliberately shows neither a
 * price-direction indicator nor the affected market items — players must not be able
 * to read an event's market impact off the newspaper. That information is only shown
 * to operators in the plugin management UI.
 * <p>
 * <b>Sizing contract:</b> the panel wraps its text for the width passed to the
 * constructor / {@link #rebuildForWidth(int)} and sets its own height accordingly.
 * The owning screen must therefore rebuild the feed (constructing panels with the
 * actual column width — half the list width in the two-column newspaper layout)
 * whenever that width changes — heights must be final before the panels are added
 * to the feed list, because the list layout reads child heights when it is applied.
 */
public class NewsEntryPanel extends StockMarketGuiElement {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".news_screen.";

        public static Component gameDay(long day) {
            return Component.translatable(PREFIX + "game_day", String.valueOf(day));
        }
    }

    // ── Newspaper palette (dark ink on paper) ────────────────────────────
    // COLOR_ENTRY_PAPER/COLOR_ENTRY_EDGE are package-visible (T-091): the shared
    // NewsPictureElement letterboxes/outlines with the same tones instead of
    // duplicating the palette values.
    static final int COLOR_ENTRY_PAPER = 0xFFF3EDDE;
    /** Card border: soft ink tone (matches COLOR_META_INK) so every entry reads
     *  as its own enclosed panel on the page (T-086 follow-up). */
    static final int COLOR_ENTRY_EDGE = 0xFF6E6754;
    private static final int COLOR_HEADLINE_INK = 0xFF1E1B16;
    private static final int COLOR_BODY_INK = 0xFF3A362E;
    private static final int COLOR_META_INK = 0xFF6E6754;

    // ── Font scales / metrics ────────────────────────────────────────────
    private static final float HEADLINE_SCALE = 1.25f;
    private static final float BODY_SCALE = 0.85f;
    private static final float META_SCALE = 0.8f;
    private static final int INNER_PAD = 4;
    private static final int SECTION_SPACING = 3;
    /**
     * Cap of the square picture block's side length in GUI pixels (T-091). A full
     * column-width square (~150+ px in a typical two-column layout) would dominate
     * the page and push the text below the fold — 100 px keeps the entry readable
     * while the picture stays clearly recognizable. Narrower columns simply use the
     * full wrap width as the side.
     */
    private static final int PICTURE_MAX_SIDE = 100;
    /**
     * Horizontal gutter between the picture (top-right, T-113) and the text on its
     * left. Kept intentionally slim so the reduced-width column above the picture
     * still fits a couple of body words per line.
     */
    private static final int PICTURE_TEXT_GUTTER = 4;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final NewsRecord record;

    /**
     * The record's picture box (COVER mode, hide-when-absent per T-113), or null
     * for text-only records that never had a hash to begin with (T-091). Created
     * once in the constructor — its hash never changes — and re-positioned/re-sized
     * by every {@link #rebuildForWidth(int)}. The picture cache's change listener
     * (wired by {@link net.kroia.stockmarket.screen.NewsScreen}) triggers a feed
     * rebuild once a picture lands, so the layout reflows around the newly-arrived
     * image without a manual repaint here. When the picture is not (yet) available,
     * the element is given zero bounds and paints nothing (T-113) — the text uses
     * the full card width instead of framing an empty paper-gray box.
     */
    private final @Nullable NewsPictureElement pictureElement;

    /** Wrapped headline text ("\n"-joined), re-resolved on every rebuild. */
    private String headlineText = "";
    /** Wrapped body text ("\n"-joined), re-resolved on every rebuild. */
    private String bodyText = "";
    /** Meta row text: local publish time + game day, drawn full-width left-aligned. */
    private String metaText = "";

    /** Y offsets of the content sections, computed by {@link #rebuildForWidth(int)}. */
    private int headlineY;
    private int bodyY;
    private int metaRowY;
    /**
     * Natural height of the wrapped content (incl. padding), computed by
     * {@link #rebuildForWidth(int)}. Kept separately from the element height so the
     * card can be stretched taller for row equalization ({@link #stretchToHeight})
     * without losing the content baseline.
     */
    private int contentHeight;

    /**
     * Builds the panel for one news record and sizes it for the given width.
     *
     * @param record     the published news record to display (never modified)
     * @param panelWidth the width (in GUI pixels) this panel will occupy inside the
     *                   feed list — text is wrapped for exactly this width
     */
    public NewsEntryPanel(NewsRecord record, int panelWidth) {
        super();
        this.record = record;
        // Panel-styled card (T-086 follow-up): every entry is visually enclosed —
        // a slightly lighter paper tone than the page plus a subtle ink border.
        // Both flags are set explicitly so the card look never depends on the
        // GuiElement defaults.
        setEnableBackground(true);
        setEnableOutline(true);
        setBackgroundColor(COLOR_ENTRY_PAPER);
        setOutlineColor(COLOR_ENTRY_EDGE);

        // T-091/T-113: records with a picture hash get a square COVER picture box
        // at the top-right of the card (hide-when-absent — see class Javadoc). The
        // child must exist before the first rebuildForWidth call positions it.
        // Text-only records (null hash) stay pixel-identical to the no-image path
        // (no child, no allocation).
        byte[] pictureHash = record.getPictureHash();
        if (pictureHash != null) {
            pictureElement = new NewsPictureElement(pictureHash, NewsPictureElement.FitMode.COVER);
            pictureElement.setHideWhenAbsent(true);
            addChild(pictureElement);
        } else {
            pictureElement = null;
        }

        rebuildForWidth(panelWidth);
    }

    /** @return the record displayed by this panel */
    public NewsRecord getRecord() {
        return record;
    }

    /**
     * Re-resolves the translation maps with the client's <b>current</b> language,
     * re-wraps all text for the given width and updates this panel's height.
     * Called from the constructor and by the owning screen when the feed width
     * changes (which implies a full feed rebuild — see class Javadoc).
     *
     * @param panelWidth the width this panel will occupy in the feed list
     */
    public void rebuildForWidth(int panelWidth) {
        setWidth(panelWidth);
        int wrapWidth = Math.max(40, panelWidth - 2 * INNER_PAD);

        // Resolve at rebuild time so a mid-session language switch is picked up
        // the next time the feed rebuilds (fallback chain in NewsTranslations).
        String language = Minecraft.getInstance().options.languageCode;
        String headline = NewsTranslations.resolve(record.getHeadline(), language);
        String body = NewsTranslations.resolve(record.getText(), language);

        // ── T-113: decide the top-right image slot ──
        // Probe the picture cache at layout time (hasLoadedPicture also enqueues
        // the fetch at HIGH priority, so a first-encounter record still starts
        // loading and the change listener rebuilds the feed on arrival). Records
        // without a hash (pictureElement == null) skip this entirely — the text
        // wraps at full width just like it did before T-091.
        int imageSide;
        int reducedWidth;
        boolean showImage = pictureElement != null && pictureElement.hasLoadedPicture();
        if (showImage) {
            imageSide = Math.min(wrapWidth, PICTURE_MAX_SIDE);
            // Reduced width must leave at least half the card for text or the
            // "wrap around" reads like a two-column table instead of a column
            // with a photo — clamp the picture down before it dominates the card.
            if (imageSide > wrapWidth - PICTURE_TEXT_GUTTER - 40) {
                imageSide = Math.max(0, wrapWidth - PICTURE_TEXT_GUTTER - 40);
                showImage = imageSide > 0;
            }
            reducedWidth = Math.max(20, wrapWidth - imageSide - PICTURE_TEXT_GUTTER);
        } else {
            imageSide = 0;
            reducedWidth = wrapWidth;
        }

        int lineH_headline = scaledLineHeight(HEADLINE_SCALE);
        int lineH_body = scaledLineHeight(BODY_SCALE);
        int imageBottomY = INNER_PAD + imageSide;

        // Headline: always sits at the top-left. Wrap width = reducedWidth for
        // the lines that fall inside the image's vertical extent, then full
        // wrapWidth for any headline overflow (rare but respected — a very long
        // multi-line headline still reads correctly under the image).
        headlineY = INNER_PAD;
        List<String> headlineLines = wrapAroundImage(headline, reducedWidth, wrapWidth,
                headlineY, imageBottomY, lineH_headline, HEADLINE_SCALE);

        // Body starts under the headline. Compute how many body lines still
        // overlap the image vertically, then flow the rest at full width.
        bodyY = headlineY + headlineLines.size() * lineH_headline + SECTION_SPACING;
        List<String> bodyLines = wrapAroundImage(body, reducedWidth, wrapWidth,
                bodyY, imageBottomY, lineH_body, BODY_SCALE);

        headlineText = String.join("\n", headlineLines);
        bodyText = String.join("\n", bodyLines);

        // Timestamp line: client-local date/time + immersive game day.
        metaText = TIME_FORMATTER.format(Instant.ofEpochMilli(record.getTimestampEpochMs()))
                + "  " + Texts.gameDay(record.getGameDay()).getString();

        // ── T-113: position the top-right image ──
        if (pictureElement != null) {
            if (showImage) {
                pictureElement.setBounds(INNER_PAD + wrapWidth - imageSide, INNER_PAD,
                        imageSide, imageSide);
            } else {
                // Zero bounds: render() skips (hideWhenAbsent) so no ghost box
                // survives while the picture is loading or has given up.
                pictureElement.setBounds(0, 0, 0, 0);
            }
        }

        // Meta row height = META_SCALE line height + top/bottom padding.
        int metaRowHeight = scaledLineHeight(META_SCALE) + 2;
        int textBottomY = bodyY + (bodyLines.isEmpty() ? 0 : bodyLines.size() * lineH_body);
        // Make sure the meta row never overlaps the image OR the body text.
        int naturalMetaRowY = textBottomY + (bodyLines.isEmpty() ? 0 : SECTION_SPACING);
        metaRowY = Math.max(naturalMetaRowY, imageBottomY + SECTION_SPACING);
        contentHeight = metaRowY + metaRowHeight + INNER_PAD;
        setHeight(contentHeight);
    }

    /** @return the natural content height computed by the last {@link #rebuildForWidth(int)} */
    public int getContentHeight() {
        return contentHeight;
    }

    /**
     * Stretches the card to the given height — used to equalize the two panels of a
     * two-column newspaper row to the taller one (T-086 follow-up). Only the element
     * height grows: the background and outline cover the full stretched card while
     * the text layout stays top-aligned and unchanged (the extra space is plain
     * card paper below the meta row). Values smaller than the natural content
     * height are ignored, so a single panel in an odd trailing row keeps its size.
     *
     * @param height the target card height in GUI pixels
     */
    public void stretchToHeight(int height) {
        setHeight(Math.max(contentHeight, height));
    }

    @Override
    protected void render() {
        // All text uses the Component overloads with dropShadow=false: the panel
        // is dark ink on light paper, and the vanilla drop shadow kills the
        // contrast. The explicit flag keeps the panel shadow-free regardless of
        // the hosting screen's ClientGraphics shadow setting.

        // Headline (prominent, dark ink).
        drawText(Component.literal(headlineText), INNER_PAD, headlineY, COLOR_HEADLINE_INK, false, HEADLINE_SCALE);

        // Newspaper body text.
        if (!bodyText.isEmpty())
            drawText(Component.literal(bodyText), INNER_PAD, bodyY, COLOR_BODY_INK, false, BODY_SCALE);

        // Meta row: timestamp spans the full row width. Drawn shadowless for a
        // print-consistent look.
        drawText(Component.literal(metaText), INNER_PAD, metaRowY, COLOR_META_INK, false, META_SCALE);
    }

    @Override
    protected void layoutChanged() {
        // All geometry is computed in rebuildForWidth(); the feed rebuilds
        // (constructing fresh panels) whenever the available width changes.
    }

    /**
     * T-113: greedy word-wrap that flows text around a top-right image slot.
     * Lines whose baseline still overlaps the image vertically use
     * {@code reducedWidth}; once the running y exceeds {@code imageBottomY}, the
     * remaining text wraps at the full {@code fullWidth}. When there is no
     * image slot ({@code imageBottomY <= startY}), the whole block wraps at
     * {@code fullWidth} — no reduction.
     *
     * @param text          the text to wrap
     * @param reducedWidth  wrap width for the lines beside the image
     * @param fullWidth     wrap width once the image is cleared vertically
     * @param startY        the y at which this block starts drawing
     * @param imageBottomY  the y (exclusive) below which the image no longer
     *                      constrains the wrap width
     * @param lineHeight    the block's line advance in GUI pixels
     * @param fontScale     the font scale the text will be drawn with
     * @return the wrapped lines, never null
     */
    private List<String> wrapAroundImage(String text, int reducedWidth, int fullWidth,
                                         int startY, int imageBottomY, int lineHeight,
                                         float fontScale) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank())
            return lines;

        // No image slot to wrap around → shortcut: single-width wrap.
        if (reducedWidth == fullWidth || imageBottomY <= startY) {
            return wrapText(text, fullWidth, fontScale);
        }

        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                // Recompute current wrap width per candidate word: as lines are
                // added, y advances and may cross imageBottomY, at which point the
                // wrap width jumps from reducedWidth to fullWidth for the very
                // next line (real newspaper reflow).
                int currentY = startY + lines.size() * lineHeight;
                int maxWidth = currentY + lineHeight <= imageBottomY ? reducedWidth : fullWidth;
                maxWidth = Math.max(10, maxWidth);
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, fontScale) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                    // The just-added line may have pushed us past imageBottomY —
                    // recompute maxWidth for the next word.
                    currentY = startY + lines.size() * lineHeight;
                    maxWidth = currentY + lineHeight <= imageBottomY ? reducedWidth : fullWidth;
                    maxWidth = Math.max(10, maxWidth);
                }
                // Hard-break a single word wider than the current line.
                while (textWidth(word, fontScale) > maxWidth && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && textWidth(word.substring(0, cut), fontScale) > maxWidth)
                        cut--;
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                    currentY = startY + lines.size() * lineHeight;
                    maxWidth = currentY + lineHeight <= imageBottomY ? reducedWidth : fullWidth;
                    maxWidth = Math.max(10, maxWidth);
                }
                line.append(word);
            }
            if (!line.isEmpty() || paragraph.isEmpty())
                lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Greedy word-wrap of {@code text} into lines no wider than {@code maxWidth}
     * GUI pixels at the given font scale. Explicit {@code "\n"}s in the source are
     * honored as paragraph breaks; single words wider than the line are hard-broken.
     *
     * @param text      the text to wrap (null/blank yields an empty list)
     * @param maxWidth  maximum line width in GUI pixels
     * @param fontScale the font scale the text will be drawn with
     * @return the wrapped lines, never null
     */
    private List<String> wrapText(String text, int maxWidth, float fontScale) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank())
            return lines;
        maxWidth = Math.max(10, maxWidth);

        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, fontScale) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                // Hard-break a single word that is wider than the whole line.
                while (textWidth(word, fontScale) > maxWidth && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && textWidth(word.substring(0, cut), fontScale) > maxWidth)
                        cut--;
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line.append(word);
            }
            if (!line.isEmpty() || paragraph.isEmpty())
                lines.add(line.toString());
        }
        return lines;
    }

    /** @return the width of {@code text} at {@code fontScale} in GUI pixels */
    private int textWidth(String text, float fontScale) {
        // getTextWidth applies the element's textFontScale (1.0 here), so the
        // requested draw scale is applied manually.
        return (int) (getTextWidth(text) * fontScale);
    }

    /** @return the font line height at the given scale in GUI pixels */
    private int scaledLineHeight(float fontScale) {
        // getTextHeight applies the element's textFontScale (1.0 here).
        return Math.round(getTextHeight() * fontScale);
    }
}
