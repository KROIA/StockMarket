package net.kroia.stockmarket.testing.tests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.market.preset.DefaultPresets;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

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
    public void registerTests() {
        addTest("preset_record_fields", this::test_presetRecordFields);
        addTest("category_find_existing", this::test_categoryFindExisting);
        addTest("category_find_missing", this::test_categoryFindMissing);
        addTest("default_presets_not_empty", this::test_defaultPresetsNotEmpty);
        addTest("default_presets_five_categories", this::test_defaultPresetsFiveCategories);
        addTest("default_presets_no_duplicate_items_within_category", this::test_noDuplicatesWithinCategory);
        addTest("default_presets_all_prices_positive", this::test_allPricesPositive);
        addTest("default_presets_all_abundances_positive", this::test_allAbundancesPositive);
        addTest("default_presets_all_item_ids_valid_format", this::test_allItemIdsValidFormat);
        addTest("json_round_trip", this::test_jsonRoundTrip);
        addTest("json_round_trip_preserves_values", this::test_jsonRoundTripPreservesValues);
        addTest("category_names_unique", this::test_categoryNamesUnique);
        addTest("preset_replacement_in_category", this::test_presetReplacementInCategory);
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
        List<MarketPresetCategory> categories = DefaultPresets.generate();
        TestResult r = assertFalse("Default presets should not be empty", categories.isEmpty());
        if (!r.passed()) return r;
        for (MarketPresetCategory cat : categories) {
            r = assertFalse("Category '" + cat.getCategory() + "' should not be empty",
                    cat.getPresets().isEmpty());
            if (!r.passed()) return r;
        }
        return pass("All default categories have presets");
    }

    private TestResult test_defaultPresetsFiveCategories() {
        List<MarketPresetCategory> categories = DefaultPresets.generate();
        return assertEquals("Should have 5 default categories", 5, categories.size());
    }

    private TestResult test_noDuplicatesWithinCategory() {
        List<MarketPresetCategory> categories = DefaultPresets.generate();
        for (MarketPresetCategory cat : categories) {
            Set<String> seen = new HashSet<>();
            for (MarketPreset preset : cat.getPresets()) {
                if (!seen.add(preset.getItemId())) {
                    return fail("Duplicate item '" + preset.getItemId() + "' in category '" + cat.getCategory() + "'");
                }
            }
        }
        return pass("No duplicate items within any category");
    }

    private TestResult test_allPricesPositive() {
        List<MarketPresetCategory> categories = DefaultPresets.generate();
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
        List<MarketPresetCategory> categories = DefaultPresets.generate();
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
        List<MarketPresetCategory> categories = DefaultPresets.generate();
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
        List<MarketPresetCategory> categories = DefaultPresets.generate();
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
        List<MarketPresetCategory> categories = DefaultPresets.generate();
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
}
