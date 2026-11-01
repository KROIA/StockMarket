package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.modutilities.gui.elements.base.GuiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared text measuring/wrapping helpers for the news admin UI (T-083/T-085).
 * <p>
 * All methods take the measuring {@link GuiElement} explicitly ({@code getTextWidth} /
 * {@code getTextHeight} are per-element because of the element font scale), so the
 * same logic serves {@link NewsPluginGuiElement}, {@link NewsEventDetailsScreen} and
 * {@link NewsDialogScreen} without duplicating the wrap algorithm.
 */
final class NewsUiText {

    private NewsUiText() {
    }

    /**
     * Truncates {@code text} with an ellipsis so it fits {@code maxWidth} GUI pixels
     * at the given font scale.
     *
     * @param element   the element used for text measurement
     * @param text      the text to truncate
     * @param maxWidth  the available width in GUI pixels
     * @param fontScale the font scale the text will be drawn with
     * @return the (possibly truncated) text, never null
     */
    static String truncate(GuiElement element, String text, int maxWidth, float fontScale) {
        if (maxWidth <= 0) return "";
        if (element.getTextWidth(text) * fontScale <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && element.getTextWidth(text.substring(0, end) + "…") * fontScale > maxWidth) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /**
     * Greedy word-wraps {@code text} into lines that fit {@code maxWidth} GUI pixels
     * at the given font scale. Paragraph breaks ({@code \n}) are preserved; overlong
     * single words are hard-broken.
     *
     * @param element   the element used for text measurement
     * @param text      the text to wrap
     * @param maxWidth  the available line width in GUI pixels
     * @param fontScale the font scale the text will be drawn with
     * @return the wrapped lines (empty list for empty input)
     */
    static List<String> wrapText(GuiElement element, String text, int maxWidth, float fontScale) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        if (maxWidth <= 0) {
            out.add(text);
            return out;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (element.getTextWidth(candidate) * fontScale <= maxWidth) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString());
                }
                // Hard-break words that alone exceed the line width.
                String rest = word;
                while (element.getTextWidth(rest) * fontScale > maxWidth && rest.length() > 1) {
                    int end = rest.length();
                    while (end > 1 && element.getTextWidth(rest.substring(0, end)) * fontScale > maxWidth) {
                        end--;
                    }
                    out.add(rest.substring(0, end));
                    rest = rest.substring(end);
                }
                line = new StringBuilder(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    /**
     * @param element   the element used for text measurement
     * @param fontScale the font scale the text will be drawn with
     * @return the line advance (text height + leading) for the given font scale
     */
    static int lineHeight(GuiElement element, float fontScale) {
        return Math.round(element.getTextHeight() * fontScale) + 2;
    }

    /**
     * T-113: greedy word-wrap that flows text around a top-right image slot in
     * newspaper column style. Lines whose baseline still overlaps the image
     * vertically use {@code reducedWidth}; once the running y exceeds
     * {@code imageBottomY}, the remaining text wraps at {@code fullWidth}.
     * <p>
     * When there is no image slot ({@code reducedWidth == fullWidth} or
     * {@code imageBottomY <= startY}), the whole block wraps at
     * {@code fullWidth} — same result as {@link #wrapText}.
     *
     * @param element       the element used for text measurement
     * @param text          the text to wrap
     * @param reducedWidth  wrap width for the lines beside the image
     * @param fullWidth     wrap width once the image is cleared vertically
     * @param startY        the y at which this block starts drawing
     * @param imageBottomY  the y (exclusive) below which the image no longer
     *                      constrains the wrap width
     * @param lineHeight    the block's line advance in GUI pixels
     * @param fontScale     the font scale the text will be drawn with
     * @return the wrapped lines (empty list for empty input)
     */
    static List<String> wrapAroundImage(GuiElement element, String text,
                                        int reducedWidth, int fullWidth,
                                        int startY, int imageBottomY,
                                        int lineHeight, float fontScale) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        // No image slot to wrap around → shortcut: single-width wrap.
        if (reducedWidth == fullWidth || imageBottomY <= startY) {
            return wrapText(element, text, fullWidth, fontScale);
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                // Recompute current wrap width per candidate word: as lines are
                // added, y advances and may cross imageBottomY, at which point
                // the wrap width jumps from reducedWidth to fullWidth for the
                // very next line (real newspaper reflow).
                int currentY = startY + out.size() * lineHeight;
                int maxWidth = currentY + lineHeight <= imageBottomY
                        ? reducedWidth : fullWidth;
                if (maxWidth <= 0) maxWidth = fullWidth;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (element.getTextWidth(candidate) * fontScale <= maxWidth) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    currentY = startY + out.size() * lineHeight;
                    maxWidth = currentY + lineHeight <= imageBottomY
                            ? reducedWidth : fullWidth;
                    if (maxWidth <= 0) maxWidth = fullWidth;
                }
                // Hard-break words that alone exceed the current line width.
                String rest = word;
                while (element.getTextWidth(rest) * fontScale > maxWidth && rest.length() > 1) {
                    int end = rest.length();
                    while (end > 1 && element.getTextWidth(rest.substring(0, end)) * fontScale > maxWidth) {
                        end--;
                    }
                    out.add(rest.substring(0, end));
                    rest = rest.substring(end);
                    currentY = startY + out.size() * lineHeight;
                    maxWidth = currentY + lineHeight <= imageBottomY
                            ? reducedWidth : fullWidth;
                    if (maxWidth <= 0) maxWidth = fullWidth;
                }
                line = new StringBuilder(rest);
            }
            out.add(line.toString());
        }
        return out;
    }
}
