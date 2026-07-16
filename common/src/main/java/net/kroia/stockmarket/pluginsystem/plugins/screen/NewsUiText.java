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
}
