package net.kroia.stockmarket.testing.tests;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.stockmarket.market.preset.DefaultPresets;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * In-game checks of translated texts the user would otherwise read in each language by hand. Each test switches the
 * game language to the mod's shipped lang file and resolves the text through the same key / code path the screen uses:
 * <ul>
 *   <li>SM-372: the volatility plugin's Scale tooltip states the current random-walk model (checked against a
 *       simulation of {@link NormalizedRandomPriceGenerator} at the default scale).</li>
 *   <li>SM-373: preset category buttons ({@link MarketPresetCategory#displayName}) show the translated name.</li>
 *   <li>SM-374: the German "account frozen" order message says "eingefroren", like BankSystem's frozen messages.</li>
 * </ul>
 */
public class LangTextInGameTestSuite extends TestSuite {

    private static final List<String> LANGUAGES = List.of("en_us", "de_de", "de_ch", "zh_cn");
    private static final String SCALE_TOOLTIP = "gui.stockmarket.volatility_plugin.scale.tooltip";
    private static final String FROZEN_MESSAGE = "message.stockmarket.order_status.account_frozen";

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.GUI;
    }

    @Override
    public void registerTests() {
        addTest("sm372_scale_tooltip_matches_random_walk", this::test_scaleTooltip);
        addTest("sm373_preset_category_buttons_translated", this::test_presetCategoryNames);
        addTest("sm374_german_frozen_order_message", this::test_frozenOrderMessage);
    }

    /**
     * SM-372: the tooltip claims, at the default scale 0.3: usually about +-10%, 90% within +-23%, practically never
     * beyond +-42%, about +-2% per hour. The simulated walk must bear that out, and every language must say it.
     */
    private TestResult test_scaleTooltip() {
        float scale = VolatilityPlugin.Settings.createDefault().volatilityScale();
        TestResult r = assertEquals("default scale the tooltip describes", 0.3f, scale);
        if (!r.passed()) return r;

        // 32 seeded walks, 2 years of hourly samples each after a 15-day warm-up (the slowest trend has tau 60 h).
        int walks = 32, hours = 2 * 365 * 24;
        double[] deviations = new double[walks * hours];
        double[] hourlyChanges = new double[walks * hours];
        Random seeds = new Random(372L);
        int n = 0;
        for (int walk = 0; walk < walks; walk++) {
            NormalizedRandomPriceGenerator generator = new NormalizedRandomPriceGenerator(new Random(seeds.nextLong()));
            for (int hour = 0; hour < 15 * 24; hour++) generator.step(3_600_000L);
            double previous = generator.getCurrentValue() * scale;
            for (int hour = 0; hour < hours; hour++, n++) {
                double deviation = generator.step(3_600_000L) * scale;
                deviations[n] = Math.abs(deviation);
                hourlyChanges[n] = Math.abs(deviation - previous);
                previous = deviation;
            }
        }
        Arrays.sort(deviations);
        Arrays.sort(hourlyChanges);
        double median = 100 * deviations[n / 2];
        double p90 = 100 * deviations[n * 9 / 10];
        double beyond42 = 100.0 * Arrays.stream(deviations).filter(d -> d > 0.42).count() / n;
        double hourly = 100 * hourlyChanges[n / 2];
        String stats = String.format(Locale.ROOT, "median %.1f%%, p90 %.1f%%, beyond 42%%: %.2f%% of the time, hourly %.1f%%",
                median, p90, beyond42, hourly);
        r = assertTrue("simulated walk fits 'about +-10%, 90% within +-23%, practically never beyond +-42%, about +-2% per hour': "
                + stats, median >= 8.5 && median <= 11.5 && p90 >= 21 && p90 <= 25 && beyond42 < 1 && hourly >= 1.5 && hourly <= 3);
        if (!r.passed()) return r;

        for (String lang : LANGUAGES) {
            String tooltip = withLanguage(lang, () -> Component.translatable(SCALE_TOOLTIP).getString());
            for (String number : List.of("+-10%", "+-23%", "+-42%", "+-2%", "0.3")) {
                r = assertTrue(lang + " Scale tooltip says " + number + ": " + tooltip, tooltip.contains(number));
                if (!r.passed()) return r;
            }
            for (String old : List.of("+-4%", "+-12%", "+-26%")) {
                r = assertFalse(lang + " Scale tooltip still has the old " + old + ": " + tooltip, tooltip.contains(old));
                if (!r.passed()) return r;
            }
        }
        return pass("Scale tooltip matches the walk in " + LANGUAGES.size() + " languages (" + stats + ")");
    }

    /**
     * SM-373: under each language every shipped preset category shows the lang file's name on its button
     * (PresetEditorTab and CreateMarketTab label the buttons with {@link MarketPresetCategory#displayName}).
     */
    private TestResult test_presetCategoryNames() {
        List<String> categories = DefaultPresets.generate(null).stream().map(MarketPresetCategory::getCategory).toList();
        // The running master's preset files carry the same category ids (a slave gets them from the master).
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (backend != null && backend.PRESET_MANAGER != null && !backend.PRESET_MANAGER.getCategories().isEmpty()) {
            Set<String> live = backend.PRESET_MANAGER.getCategories().stream()
                    .map(MarketPresetCategory::getCategory).collect(Collectors.toSet());
            for (String category : categories) {
                TestResult r = assertTrue("the server's presets have the shipped category " + category, live.contains(category));
                if (!r.passed()) return r;
            }
        }
        for (String lang : LANGUAGES) {
            Map<String, String> texts = langTexts(lang);
            List<String> wrong = withLanguage(lang, () -> categories.stream()
                    .filter(c -> !MarketPresetCategory.displayName(c).equals(
                            texts.get("gui.stockmarket.preset_category." + c.toLowerCase(Locale.ROOT))))
                    .toList());
            TestResult r = assertTrue(lang + " category buttons not showing their translation: " + wrong, wrong.isEmpty());
            if (!r.passed()) return r;
        }
        TestResult r = assertEquals("German button of category Ore", "Erz",
                withLanguage("de_de", () -> MarketPresetCategory.displayName("Ore")));
        if (!r.passed()) return r;
        List<String> untranslated = withLanguage("zh_cn", () -> categories.stream()
                .filter(c -> MarketPresetCategory.displayName(c).equals(c)).toList());
        r = assertTrue("zh_cn buttons still showing the English id: " + untranslated, untranslated.isEmpty());
        if (!r.passed()) return r;
        return pass(categories.size() + " category buttons translated in " + LANGUAGES.size() + " languages");
    }

    /** SM-374: the order rejected on a frozen account reads "eingefroren" (de_de) / "igfrore" (de_ch), as in BankSystem. */
    private TestResult test_frozenOrderMessage() {
        String german = withLanguage("de_de", () -> Component.translatable(FROZEN_MESSAGE).getString());
        TestResult r = assertTrue("de_de message says 'eingefroren', not 'gesperrt': " + german,
                german.contains("eingefroren") && !german.contains("gesperrt"));
        if (!r.passed()) return r;
        String swiss = withLanguage("de_ch", () -> Component.translatable(FROZEN_MESSAGE).getString());
        r = assertTrue("de_ch message says 'igfrore', not 'gsperrt': " + swiss,
                swiss.contains("igfrore") && !swiss.contains("gsperrt"));
        if (!r.passed()) return r;
        return pass("de_de: " + german + " / de_ch: " + swiss);
    }

    /**
     * Runs {@code check} with {@code lang} as the active game language, built like the client builds it after a
     * language switch: the mod's en_us texts first, the chosen language on top. Keys outside the mod fall back to the
     * previous language, so other text stays readable meanwhile (in single player the client shares this instance).
     * Restores the previous language afterwards.
     *
     * @param lang  a shipped lang file name, e.g. {@code de_de}
     * @param check the code that resolves the texts
     * @return what {@code check} returned
     */
    private static <T> T withLanguage(String lang, Supplier<T> check) {
        Map<String, String> texts = langTexts(lang);
        Language previous = Language.getInstance();
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                String text = texts.get(key);
                return text != null ? text : previous.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return texts.containsKey(key) || previous.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return previous.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return previous.getVisualOrder(text);
            }
        });
        try {
            return check.get();
        } finally {
            Language.inject(previous);
        }
    }

    /**
     * @param lang a shipped lang file name
     * @return the mod's en_us texts overlaid with {@code lang}'s, read from the mod jar with the game's own JSON loader
     */
    private static Map<String, String> langTexts(String lang) {
        Map<String, String> texts = new HashMap<>();
        for (String file : lang.equals("en_us") ? List.of("en_us") : List.of("en_us", lang)) {
            try (InputStream in = LangTextInGameTestSuite.class.getResourceAsStream("/assets/stockmarket/lang/" + file + ".json")) {
                if (in == null) throw new IllegalStateException("missing lang file " + file + ".json");
                Language.loadFromJson(in, texts::put);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return texts;
    }
}
