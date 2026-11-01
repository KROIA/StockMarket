package net.kroia.stockmarket.news;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves the inline translation maps carried by {@link NewsRecord} (and news event
 * definitions) to a single display string (NewsEventSystem plan §1/§4, task T-074).
 * <p>
 * <b>Fallback chain (in this order):</b>
 * <ol>
 *   <li><b>Exact client language</b> — the entry whose key equals the requested
 *       language code (e.g. {@code "de_de"}). Matching is exact and case-sensitive;
 *       Minecraft language codes are lowercase by convention.</li>
 *   <li><b>{@code en_us}</b> — the canonical default language of the shipped events.</li>
 *   <li><b>First map entry</b> — the first entry in the map's insertion order.
 *       The whole persistence/network pipeline ({@code NewsRecord} NBT ListTag pairs,
 *       LinkedHashMap stream codecs) deliberately preserves this order so that
 *       single-language admin events (a one-entry map) always resolve.</li>
 * </ol>
 * <p>
 * <b>Call this at render/refresh time, not once at receive time:</b> the client's
 * language can change mid-session (Options → Language) without a relog, and history
 * entries must re-render in the newly selected language. All UI call sites
 * (newspaper entries, the news toast) therefore resolve on every rebuild/render pass
 * with the current {@code Minecraft.getInstance().options.languageCode}.
 * <p>
 * This class is intentionally free of client-only imports so it can be unit-tested
 * in the {@code sm_news} in-game test suite on both server types.
 */
public final class NewsTranslations {

    /** Language code of the canonical fallback language ({@value}). */
    public static final String FALLBACK_LANGUAGE = "en_us";

    private NewsTranslations() {
    }

    /**
     * Resolves a translation map to display text using the fallback chain
     * <i>exact language → {@code en_us} → first map entry</i> (see class Javadoc).
     *
     * @param translations the language-code → text map (insertion-ordered);
     *                     null or empty maps resolve to the empty string
     * @param languageCode the client's current language code (e.g. {@code "de_de"});
     *                     null skips the exact-match step
     * @return the resolved text, never null (empty string when nothing is available)
     */
    public static @NotNull String resolve(@Nullable Map<String, String> translations,
                                          @Nullable String languageCode) {
        if (translations == null || translations.isEmpty())
            return "";

        // 1) Exact client language.
        if (languageCode != null) {
            String exact = translations.get(languageCode);
            if (exact != null)
                return exact;
        }

        // 2) Canonical en_us fallback.
        String fallback = translations.get(FALLBACK_LANGUAGE);
        if (fallback != null)
            return fallback;

        // 3) First entry in insertion order (single-language admin events).
        return translations.values().iterator().next();
    }
}
