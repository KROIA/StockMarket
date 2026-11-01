package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonParser;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.minecraft.compat.StockMarketPlaceholders;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the pure pair-naming helpers of {@link StockMarketPlaceholders}: id stripping,
 * sanitizing, component-hash suffix and collision handling.
 */
public class PlaceholderNamingTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PLACEHOLDERS;
    }

    @Override
    public void registerTests() {
        addTest("minecraft_namespace_stripped", this::test_minecraftStripped);
        addTest("modded_namespace_kept", this::test_moddedNamespace);
        addTest("sanitize_invalid_chars", this::test_sanitize);
        addTest("hash_suffix_present_and_stable", this::test_hashSuffix);
        addTest("canonical_json_sorts_keys", this::test_canonicalJson);
        addTest("collision_first_by_full_id_wins", this::test_collision);
    }

    private TestResult test_minecraftStripped() {
        TestResult r = assertEquals("diamond", StockMarketPlaceholders.pairName("minecraft:diamond", null));
        if (!r.passed()) return r;
        r = assertEquals("empty component json adds no suffix", "iron_ingot", StockMarketPlaceholders.pairName("minecraft:iron_ingot", ""));
        if (!r.passed()) return r;
        return pass("minecraft: prefix is stripped");
    }

    private TestResult test_moddedNamespace() {
        TestResult r = assertEquals("create_brass_ingot", StockMarketPlaceholders.pairName("create:brass_ingot", null));
        if (!r.passed()) return r;
        return pass("other namespaces become ns_path");
    }

    private TestResult test_sanitize() {
        TestResult r = assertEquals("my_mod_ores_tin_ore", StockMarketPlaceholders.pairName("My-Mod:ores/Tin.Ore", null));
        if (!r.passed()) return r;
        r = assertTrue("result must match [a-z0-9_]+",
                StockMarketPlaceholders.pairName("Ä-mod:x y", null).matches("[a-z0-9_]+"));
        if (!r.passed()) return r;
        return pass("names are lowercased and sanitized");
    }

    private TestResult test_hashSuffix() {
        String json = "{\"minecraft:stored_enchantments\":{\"levels\":{\"minecraft:mending\":1}}}";
        String a = StockMarketPlaceholders.pairName("minecraft:enchanted_book", json);
        String b = StockMarketPlaceholders.pairName("minecraft:enchanted_book", json);
        TestResult r = assertTrue("suffix of 6 hex chars expected, got " + a, a.matches("enchanted_book_[0-9a-f]{6}"));
        if (!r.passed()) return r;
        r = assertEquals("same input must give the same name", a, b);
        if (!r.passed()) return r;
        String other = StockMarketPlaceholders.pairName("minecraft:enchanted_book", json.replace("mending", "unbreaking"));
        r = assertFalse("different components should give a different suffix", a.equals(other));
        if (!r.passed()) return r;
        return pass("component hash suffix is present and stable");
    }

    private TestResult test_canonicalJson() {
        String a = StockMarketPlaceholders.canonicalJson(JsonParser.parseString("{\"b\":1,\"a\":{\"d\":[{\"z\":1,\"y\":2}],\"c\":2}}")).toString();
        String b = StockMarketPlaceholders.canonicalJson(JsonParser.parseString("{\"a\":{\"c\":2,\"d\":[{\"y\":2,\"z\":1}]},\"b\":1}")).toString();
        TestResult r = assertEquals("{\"a\":{\"c\":2,\"d\":[{\"y\":2,\"z\":1}]},\"b\":1}", a);
        if (!r.passed()) return r;
        r = assertEquals("key order must not matter", a, b);
        if (!r.passed()) return r;
        return pass("canonical JSON is key-sorted");
    }

    private TestResult test_collision() {
        Map<String, String> candidates = new HashMap<>();
        candidates.put("a_x:y", StockMarketPlaceholders.pairName("a_x:y", null));
        candidates.put("a:x_y", StockMarketPlaceholders.pairName("a:x_y", null));
        candidates.put("minecraft:stone", StockMarketPlaceholders.pairName("minecraft:stone", null));
        List<String> warnings = new ArrayList<>();
        Map<String, String> winners = StockMarketPlaceholders.resolveCollisions(candidates, warnings::add);
        TestResult r = assertEquals("both colliding ids sanitize to a_x_y", candidates.get("a_x:y"), candidates.get("a:x_y"));
        if (!r.passed()) return r;
        r = assertEquals("two names expected", 2, winners.size());
        if (!r.passed()) return r;
        r = assertEquals("first by full id wins", "a:x_y", winners.get("a_x_y"));
        if (!r.passed()) return r;
        r = assertEquals("stone unaffected", "minecraft:stone", winners.get("stone"));
        if (!r.passed()) return r;
        r = assertEquals("one warning expected", 1, warnings.size());
        if (!r.passed()) return r;
        return pass("collisions resolved by sorted full id with a warning");
    }
}
