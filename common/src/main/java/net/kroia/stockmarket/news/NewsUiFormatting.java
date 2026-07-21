package net.kroia.stockmarket.news;

import java.util.Locale;

/**
 * Pure string-formatting helpers for news UI displays (task T-075).
 * <p>
 * Lives in the {@code news/} package (not in the client-only GUI packages) so the
 * logic is unit-testable in the {@code sm_news} in-game suite and reusable by
 * non-GUI consumers (e.g. the T-076 {@code /stockmarket news list} command output).
 * No Minecraft or client classes are referenced.
 */
public final class NewsUiFormatting {

    /** Placeholder shown when a factor value is not displayable (NaN/infinite). */
    public static final String INVALID_FACTOR_TEXT = "--";

    private NewsUiFormatting() {
    }

    /**
     * Formats a remaining-time span as {@code mm:ss}, or {@code h:mm:ss} once the
     * span reaches one hour. Negative inputs clamp to {@code 00:00}; partial seconds
     * are truncated (a news envelope updates twice per second, display precision is
     * whole seconds).
     * <p>
     * Examples: {@code 0 → "00:00"}, {@code 83_000 → "01:23"},
     * {@code 3_723_000 → "1:02:03"}.
     *
     * @param remainingMs the remaining time in milliseconds
     * @return the formatted time span, never null
     */
    public static String formatRemainingTime(long remainingMs) {
        long totalSeconds = Math.max(0, remainingMs) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    /**
     * Formats a multiplicative factor term as a signed percentage with one decimal:
     * {@code 1.04 → "+4.0%"}, {@code 0.919 → "-8.1%"}, {@code 1.0 → "+0.0%"}.
     * Values that would round to (negative) zero are normalized to {@code "+0.0%"};
     * non-finite inputs yield {@link #INVALID_FACTOR_TEXT}.
     *
     * @param factorTerm the multiplicative factor (1.0 = no influence)
     * @return the signed percentage string, never null
     */
    public static String formatFactorPercent(double factorTerm) {
        if (!Double.isFinite(factorTerm)) return INVALID_FACTOR_TEXT;
        double percent = (factorTerm - 1.0) * 100.0;
        if (Math.abs(percent) < 0.05) percent = 0.0; // avoid "-0.0%"
        return String.format(Locale.ROOT, "%+.1f%%", percent);
    }
}
