package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.modutilities.setting.SettingsStore;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tests for the "Mod Settings" admin-screen backend logic
 * ({@link ModSettingsRequest} + {@link StockMarketModSettings}):
 * <ul>
 *   <li>the SettingsStore JSON round-trip used as the request's wire format
 *       (including the ItemStack currency via its custom parser),</li>
 *   <li>the {@link ModSettingsRequest#sanitize} validation bounds, and</li>
 *   <li>graceful handling of partial payloads (missing groups keep their values).</li>
 * </ul>
 * Pure logic tests on fresh settings instances — no running server market required.
 */
public class ModSettingsTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MOD_SETTINGS;
    }

    @Override
    public void registerTests() {
        addTest("json_round_trip", this::test_jsonRoundTrip);
        addTest("sanitize_clamps_lower_bounds", this::test_sanitizeClampsLowerBounds);
        addTest("sanitize_clamps_upper_bounds", this::test_sanitizeClampsUpperBounds);
        addTest("sanitize_restores_empty_currency", this::test_sanitizeRestoresEmptyCurrency);
        addTest("partial_payload_keeps_other_values", this::test_partialPayloadKeepsOtherValues);
    }

    /**
     * Serializing the editable groups to a JSON string (the ModSettingsRequest wire
     * format) and loading it into a second settings instance must reproduce every
     * value, including the ItemStack currency handled by its custom parser.
     */
    private TestResult test_jsonRoundTrip() {
        StockMarketModSettings source = new StockMarketModSettings();
        source.UTILITIES.SAVE_INTERVAL_MINUTES.set(7L);
        source.UTILITIES.LOGGING_ENABLE_DEBUG.set(true);
        source.UTILITIES.LOGGING_ENABLE_INFO.set(false);
        source.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.set(555);
        source.MARKET.CANDLE_TIME.set(120_000L);
        source.MARKET.CURRENCY.set(new ItemStack(Items.DIAMOND));
        source.VILLAGER_TRADING.ENABLED.set(false);
        source.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.set(42L);
        source.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.set(0.5f);
        source.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.set(1.75f);

        SettingsStore store = new SettingsStore();
        String json = store.toJsonString(source.getEditableGroups());

        StockMarketModSettings target = new StockMarketModSettings();
        store.fromJson(target.getEditableGroups(), JsonParser.parseString(json));

        TestResult r = assertEquals("SAVE_INTERVAL_MINUTES survives round-trip", 7L, (long) target.UTILITIES.SAVE_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertTrue("LOGGING_ENABLE_DEBUG survives round-trip", target.UTILITIES.LOGGING_ENABLE_DEBUG.get());
        if (!r.passed()) return r;
        r = assertFalse("LOGGING_ENABLE_INFO survives round-trip", target.UTILITIES.LOGGING_ENABLE_INFO.get());
        if (!r.passed()) return r;
        r = assertEquals("VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE survives round-trip", 555, (int) target.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get());
        if (!r.passed()) return r;
        r = assertEquals("CANDLE_TIME survives round-trip", 120_000L, (long) target.MARKET.CANDLE_TIME.get());
        if (!r.passed()) return r;
        r = assertTrue("CURRENCY item survives round-trip",
                ItemStack.isSameItemSameComponents(target.MARKET.CURRENCY.get(), new ItemStack(Items.DIAMOND)));
        if (!r.passed()) return r;
        r = assertFalse("VillagerTrading.ENABLED survives round-trip", target.VILLAGER_TRADING.ENABLED.get());
        if (!r.passed()) return r;
        r = assertEquals("PRICE_REFRESH_INTERVAL_MINUTES survives round-trip", 42L, (long) target.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertEquals("VILLAGER_BUY_MARGIN survives round-trip", 0.5f, (float) target.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.get());
        if (!r.passed()) return r;
        r = assertEquals("VILLAGER_SELL_MARGIN survives round-trip", 1.75f, (float) target.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.get());
        if (!r.passed()) return r;
        return pass("SettingsStore JSON round-trip preserves all editable settings");
    }

    /**
     * Values below the documented minimums must be clamped up (and NaN margins fall
     * back to the setting's default).
     */
    private TestResult test_sanitizeClampsLowerBounds() {
        StockMarketModSettings settings = new StockMarketModSettings();
        ItemStack previousCurrency = settings.MARKET.CURRENCY.get();
        settings.UTILITIES.SAVE_INTERVAL_MINUTES.set(0L);
        settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.set(5);
        settings.MARKET.CANDLE_TIME.set(10L);
        settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.set(0L);
        settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.set(-1f);
        settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.set(Float.NaN);

        ModSettingsRequest.sanitize(settings, previousCurrency);

        TestResult r = assertEquals("save interval clamped to minimum",
                ModSettingsRequest.MIN_SAVE_INTERVAL_MINUTES, (long) settings.UTILITIES.SAVE_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertEquals("orderbook array size clamped to minimum",
                ModSettingsRequest.MIN_ORDERBOOK_ARRAY_SIZE, (int) settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get());
        if (!r.passed()) return r;
        r = assertEquals("candle time clamped to minimum",
                ModSettingsRequest.MIN_CANDLE_TIME_MS, (long) settings.MARKET.CANDLE_TIME.get());
        if (!r.passed()) return r;
        r = assertEquals("refresh interval clamped to minimum",
                ModSettingsRequest.MIN_PRICE_REFRESH_INTERVAL_MINUTES, (long) settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertEquals("negative buy margin clamped to minimum",
                ModSettingsRequest.MIN_VILLAGER_MARGIN, (float) settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.get());
        if (!r.passed()) return r;
        r = assertEquals("NaN sell margin falls back to default",
                settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.getDefaultValue(),
                (float) settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.get());
        if (!r.passed()) return r;
        return pass("sanitize() clamps all lower bounds");
    }

    /**
     * Values above the documented maximums must be clamped down.
     */
    private TestResult test_sanitizeClampsUpperBounds() {
        StockMarketModSettings settings = new StockMarketModSettings();
        ItemStack previousCurrency = settings.MARKET.CURRENCY.get();
        settings.UTILITIES.SAVE_INTERVAL_MINUTES.set(Long.MAX_VALUE);
        settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.set(Integer.MAX_VALUE);
        settings.MARKET.CANDLE_TIME.set(Long.MAX_VALUE);
        settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.set(Long.MAX_VALUE);
        settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.set(9999f);

        ModSettingsRequest.sanitize(settings, previousCurrency);

        TestResult r = assertEquals("save interval clamped to maximum",
                ModSettingsRequest.MAX_SAVE_INTERVAL_MINUTES, (long) settings.UTILITIES.SAVE_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertEquals("orderbook array size clamped to maximum",
                ModSettingsRequest.MAX_ORDERBOOK_ARRAY_SIZE, (int) settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get());
        if (!r.passed()) return r;
        r = assertEquals("candle time clamped to maximum",
                ModSettingsRequest.MAX_CANDLE_TIME_MS, (long) settings.MARKET.CANDLE_TIME.get());
        if (!r.passed()) return r;
        r = assertEquals("refresh interval clamped to maximum",
                ModSettingsRequest.MAX_PRICE_REFRESH_INTERVAL_MINUTES, (long) settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertEquals("buy margin clamped to maximum",
                ModSettingsRequest.MAX_VILLAGER_MARGIN, (float) settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.get());
        if (!r.passed()) return r;
        return pass("sanitize() clamps all upper bounds");
    }

    /**
     * An empty/cleared currency must never survive sanitize(): the previous
     * currency value is restored.
     */
    private TestResult test_sanitizeRestoresEmptyCurrency() {
        StockMarketModSettings settings = new StockMarketModSettings();
        ItemStack previousCurrency = new ItemStack(Items.EMERALD);
        settings.MARKET.CURRENCY.set(ItemStack.EMPTY);

        ModSettingsRequest.sanitize(settings, previousCurrency);

        TestResult r = assertFalse("currency must not stay empty", settings.MARKET.CURRENCY.get().isEmpty());
        if (!r.passed()) return r;
        r = assertTrue("previous currency restored",
                ItemStack.isSameItemSameComponents(settings.MARKET.CURRENCY.get(), previousCurrency));
        if (!r.passed()) return r;

        // Without a previous value the setting's default must be used.
        StockMarketModSettings settings2 = new StockMarketModSettings();
        settings2.MARKET.CURRENCY.set(ItemStack.EMPTY);
        ModSettingsRequest.sanitize(settings2, null);
        r = assertFalse("currency falls back to default when no previous value exists",
                settings2.MARKET.CURRENCY.get().isEmpty());
        if (!r.passed()) return r;
        return pass("sanitize() never leaves an empty trading currency");
    }

    /**
     * A payload that only contains one group (as SettingsStore.fromJson allows)
     * must not reset the settings of the other groups.
     */
    private TestResult test_partialPayloadKeepsOtherValues() {
        StockMarketModSettings settings = new StockMarketModSettings();
        settings.UTILITIES.SAVE_INTERVAL_MINUTES.set(99L);
        settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.set(0.33f);

        // Payload containing ONLY the VillagerTrading group
        JsonObject villagerGroup = new JsonObject();
        villagerGroup.addProperty("ENABLED", false);
        JsonObject root = new JsonObject();
        root.add("VillagerTrading", villagerGroup);

        new SettingsStore().fromJson(settings.getEditableGroups(), root);

        TestResult r = assertEquals("untouched Utilities value preserved", 99L, (long) settings.UTILITIES.SAVE_INTERVAL_MINUTES.get());
        if (!r.passed()) return r;
        r = assertFalse("VillagerTrading.ENABLED applied from partial payload", settings.VILLAGER_TRADING.ENABLED.get());
        if (!r.passed()) return r;
        r = assertEquals("VillagerTrading setting missing from payload preserved", 0.33f, (float) settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.get());
        if (!r.passed()) return r;
        return pass("Partial payloads only change the settings they contain");
    }
}
