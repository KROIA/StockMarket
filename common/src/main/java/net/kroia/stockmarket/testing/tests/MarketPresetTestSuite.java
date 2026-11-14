package net.kroia.stockmarket.testing.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.preset.DefaultPresets;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MarketPresetTestSuite extends TestSuite {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_PRESET;
    }

    @Override
    public boolean needsGame() {
        return false; // passes offline in gradlew :common:test
    }

    @Override
    public void registerTests() {
        addTest("preset_record_fields", this::test_presetRecordFields);
        addTest("category_find_existing", this::test_categoryFindExisting);
        addTest("category_find_missing", this::test_categoryFindMissing);
        addTest("default_presets_not_empty", this::test_defaultPresetsNotEmpty);
        addTest("default_presets_many_categories", this::test_defaultPresetsManyCategories);
        addTest("default_presets_no_duplicate_items_within_category", this::test_noDuplicatesWithinCategory);
        addTest("default_presets_all_prices_positive", this::test_allPricesPositive);
        addTest("default_presets_all_abundances_positive", this::test_allAbundancesPositive);
        addTest("default_presets_all_item_ids_valid_format", this::test_allItemIdsValidFormat);
        addTest("json_round_trip", this::test_jsonRoundTrip);
        addTest("json_round_trip_preserves_values", this::test_jsonRoundTripPreservesValues);
        addTest("category_names_unique", this::test_categoryNamesUnique);
        addTest("preset_replacement_in_category", this::test_presetReplacementInCategory);
        // T-166 max order volume
        addTest("max_order_volume_derived_default", this::test_maxOrderVolume_derivedDefault);
        addTest("max_order_volume_forward_merge", this::test_maxOrderVolume_forwardMerge);
        // SM-279: a market created from a preset (in game, master only)
        addTest("market_from_preset_gets_abundance_and_max_volume", this::test_marketFromPreset);
    }

    /** Presets (incl. all default presets) derive maxOrderVolume from their abundance. */
    private TestResult test_maxOrderVolume_derivedDefault() {
        TestResult r = assertEquals("abundance 5 -> 320", 320L, new MarketPreset("minecraft:diamond", 100f, 5f).getMaxOrderVolume());
        if (!r.passed()) return r;
        for (MarketPresetCategory cat : DefaultPresets.generate(null)) {
            for (MarketPreset p : cat.getPresets()) {
                long expected = net.kroia.stockmarket.stockmarket.market.MarketSettings.defaultMaxOrderVolume(p.getNaturalAbundance());
                r = assertEquals("default preset " + p.getItemId(), expected, p.getMaxOrderVolume());
                if (!r.passed()) return r;
            }
        }
        return pass("Preset maxOrderVolume derived from abundance");
    }

    /** Forward-merge fills a missing maxOrderVolume from the entry's abundance and keeps existing values. */
    private TestResult test_maxOrderVolume_forwardMerge() {
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                "{\"category\":\"Test\",\"presets\":["
                        + "{\"itemId\":\"minecraft:iron_ingot\",\"defaultPrice\":15.0,\"naturalAbundance\":30.0},"
                        + "{\"itemId\":\"minecraft:diamond\",\"defaultPrice\":100.0,\"naturalAbundance\":5.0,\"maxOrderVolume\":0}"
                        + "]}").getAsJsonObject();
        boolean changed = net.kroia.stockmarket.stockmarket.market.preset.MarketPresetManager
                .forwardMergeCategoryJson(root, new java.util.ArrayList<>());
        TestResult r = assertTrue("merge reports a change", changed);
        if (!r.passed()) return r;
        MarketPresetCategory cat = GSON.fromJson(root, MarketPresetCategory.class);
        r = assertEquals("missing field -> 64 x 30", 1920L, cat.findPreset("minecraft:iron_ingot").getMaxOrderVolume());
        if (!r.passed()) return r;
        r = assertEquals("existing 0 kept", 0L, cat.findPreset("minecraft:diamond").getMaxOrderVolume());
        if (!r.passed()) return r;
        return pass("Forward-merge fills maxOrderVolume from abundance");
    }

    private TestResult test_presetRecordFields() {
        MarketPreset preset = new MarketPreset("minecraft:iron_ingot", 15.0f, 30.0f);
        TestResult r = assertEquals("itemId", "minecraft:iron_ingot", preset.getItemId());
        if (!r.passed()) return r;
        r = assertEquals("defaultPrice", 15.0f, preset.getDefaultPrice());
        if (!r.passed()) return r;
        r = assertEquals("naturalAbundance", 30.0f, preset.getNaturalAbundance());
        if (!r.passed()) return r;
        return pass("MarketPreset record fields correct");
    }

    private TestResult test_categoryFindExisting() {
        MarketPresetCategory cat = new MarketPresetCategory("Test", List.of(
                new MarketPreset("minecraft:stone", 1.0f, 150.0f),
                new MarketPreset("minecraft:dirt", 0.3f, 200.0f)
        ));
        MarketPreset found = cat.findPreset("minecraft:dirt");
        TestResult r = assertNotNull("findPreset should return dirt", found);
        if (!r.passed()) return r;
        r = assertEquals("found price", 0.3f, found.getDefaultPrice());
        if (!r.passed()) return r;
        return pass("Category findPreset finds existing item");
    }

    private TestResult test_categoryFindMissing() {
        MarketPresetCategory cat = new MarketPresetCategory("Test", List.of(
                new MarketPreset("minecraft:stone", 1.0f, 150.0f)
        ));
        MarketPreset found = cat.findPreset("minecraft:diamond");
        return assertNull("findPreset should return null for missing item", found);
    }

    private TestResult test_defaultPresetsNotEmpty() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        TestResult r = assertFalse("Default presets should not be empty", categories.isEmpty());
        if (!r.passed()) return r;
        for (MarketPresetCategory cat : categories) {
            r = assertFalse("Category '" + cat.getCategory() + "' should not be empty",
                    cat.getPresets().isEmpty());
            if (!r.passed()) return r;
        }
        return pass("All default categories have presets");
    }

    private TestResult test_defaultPresetsManyCategories() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        if (categories.size() < 27) {
            return fail("Expected at least 27 default categories, got " + categories.size());
        }
        return pass("Default presets have " + categories.size() + " categories");
    }

    private TestResult test_noDuplicatesWithinCategory() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        for (MarketPresetCategory cat : categories) {
            Set<String> seen = new HashSet<>();
            for (MarketPreset preset : cat.getPresets()) {
                String key = preset.getUniqueKey();
                if (!seen.add(key)) {
                    return fail("Duplicate preset '" + key + "' in category '" + cat.getCategory() + "'");
                }
            }
        }
        return pass("No duplicate presets within any category");
    }

    private TestResult test_allPricesPositive() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        for (MarketPresetCategory cat : categories) {
            for (MarketPreset preset : cat.getPresets()) {
                if (preset.getDefaultPrice() <= 0) {
                    return fail("Non-positive price for '" + preset.getItemId() + "' in '" + cat.getCategory() + "': " + preset.getDefaultPrice());
                }
            }
        }
        return pass("All default prices are positive");
    }

    private TestResult test_allAbundancesPositive() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        for (MarketPresetCategory cat : categories) {
            for (MarketPreset preset : cat.getPresets()) {
                if (preset.getNaturalAbundance() <= 0) {
                    return fail("Non-positive abundance for '" + preset.getItemId() + "' in '" + cat.getCategory() + "': " + preset.getNaturalAbundance());
                }
            }
        }
        return pass("All default abundances are positive");
    }

    private TestResult test_allItemIdsValidFormat() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        for (MarketPresetCategory cat : categories) {
            for (MarketPreset preset : cat.getPresets()) {
                String id = preset.getItemId();
                if (id == null || !id.contains(":")) {
                    return fail("Invalid itemId format in '" + cat.getCategory() + "': " + id);
                }
            }
        }
        return pass("All item IDs have valid namespace:path format");
    }

    private TestResult test_jsonRoundTrip() {
        MarketPresetCategory original = new MarketPresetCategory("TestCat", List.of(
                new MarketPreset("minecraft:iron_ingot", 15.0f, 30.0f),
                new MarketPreset("minecraft:diamond", 160.0f, 5.0f)
        ));
        String json = GSON.toJson(original);
        MarketPresetCategory decoded = GSON.fromJson(json, MarketPresetCategory.class);

        TestResult r = assertNotNull("Decoded category should not be null", decoded);
        if (!r.passed()) return r;
        r = assertEquals("Category name preserved", "TestCat", decoded.getCategory());
        if (!r.passed()) return r;
        r = assertEquals("Preset count preserved", 2, decoded.getPresets().size());
        if (!r.passed()) return r;
        return pass("JSON round-trip preserves category structure");
    }

    private TestResult test_jsonRoundTripPreservesValues() {
        MarketPreset original = new MarketPreset("minecraft:gold_ingot", 40.0f, 15.0f);
        MarketPresetCategory cat = new MarketPresetCategory("Test", List.of(original));

        String json = GSON.toJson(cat);
        MarketPresetCategory decoded = GSON.fromJson(json, MarketPresetCategory.class);
        MarketPreset found = decoded.findPreset("minecraft:gold_ingot");

        TestResult r = assertNotNull("Should find gold_ingot after round-trip", found);
        if (!r.passed()) return r;
        r = assertEquals("defaultPrice preserved", 40.0f, found.getDefaultPrice());
        if (!r.passed()) return r;
        r = assertEquals("naturalAbundance preserved", 15.0f, found.getNaturalAbundance());
        if (!r.passed()) return r;
        return pass("JSON round-trip preserves preset values exactly");
    }

    private TestResult test_categoryNamesUnique() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        Set<String> names = new HashSet<>();
        for (MarketPresetCategory cat : categories) {
            if (!names.add(cat.getCategory())) {
                return fail("Duplicate category name: " + cat.getCategory());
            }
        }
        return pass("All default category names are unique");
    }

    /**
     * Verifies that the list returned by MarketPresetCategory.getPresets() is mutable,
     * allowing in-place replacement of presets (used by PresetUpdateRequest).
     */
    private TestResult test_presetReplacementInCategory() {
        List<MarketPresetCategory> categories = DefaultPresets.generate(null);
        MarketPresetCategory cat = categories.get(0);
        List<MarketPreset> presets = cat.getPresets();

        TestResult r = assertFalse("Category should have presets", presets.isEmpty());
        if (!r.passed()) return r;

        MarketPreset original = presets.get(0);
        float newPrice = original.getDefaultPrice() + 100f;
        MarketPreset replacement = new MarketPreset(original.getItemId(), newPrice, original.getNaturalAbundance());
        presets.set(0, replacement);

        // Read back through the category to confirm mutation is visible
        MarketPreset updated = cat.getPresets().get(0);
        r = assertEquals("Price should be updated", newPrice, updated.getDefaultPrice());
        if (!r.passed()) return r;
        r = assertEquals("ItemId should be unchanged", original.getItemId(), updated.getItemId());
        if (!r.passed()) return r;
        r = assertEquals("NaturalAbundance should be unchanged",
                original.getNaturalAbundance(), updated.getNaturalAbundance());
        if (!r.passed()) return r;
        return pass("Preset replacement in category works via list.set()");
    }

    /**
     * SM-279 (T-185): a market created on the master for an item with a preset gets that preset's natural abundance
     * and max order volume (the x10 preset abundances end up in the market, and with them the derived default max
     * order volume). Uses the first preset whose item has a registered ItemID and no market yet; the market is
     * deleted afterwards and the item filters are restored (createMarket allow-lists the item). In game on a master
     * only; skips offline and on a slave.
     */
    private TestResult test_marketFromPreset() {
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (backend == null || backend.MARKET_MANAGER == null || backend.MARKET_MANAGER.isSlave() || backend.PRESET_MANAGER == null)
            return skip("master only (in game)");
        ISyncServerMarketManager manager = backend.MARKET_MANAGER.getSync();
        MarketPreset preset = null;
        ItemID itemID = null;
        for (MarketPresetCategory cat : backend.PRESET_MANAGER.getCategories()) {
            for (MarketPreset p : cat.getPresets()) {
                if (!p.hasRegisteredItemID())
                    continue;
                ItemID id = new ItemID(p.getRegisteredItemIDShort());
                if (!manager.marketExists(id) && backend.PRESET_MANAGER.getPreset(id) == p) {
                    preset = p;
                    itemID = id;
                    break;
                }
            }
            if (preset != null)
                break;
        }
        if (preset == null)
            return skip("every preset item already has a market (or none is registered)");

        TestFixture fixture = new TestFixture("PresetMarket");
        fixture.setBackend(backend); // snapshots the item filters createMarket changes
        try {
            IServerMarket market = manager.createMarket(itemID);
            TestResult r = assertNotNull("market created for preset " + preset.getItemId(), market);
            if (!r.passed()) return r;
            MarketSettings settings = market.getSettings();
            r = assertEquals("abundance of preset " + preset.getItemId(), preset.getNaturalAbundance(), settings.naturalAbundance);
            if (!r.passed()) return r;
            return assertEquals("max order volume of preset " + preset.getItemId(), preset.getMaxOrderVolume(), settings.maxOrderVolume);
        } finally {
            manager.deleteMarket(itemID);
            fixture.cleanup();
        }
    }
}
