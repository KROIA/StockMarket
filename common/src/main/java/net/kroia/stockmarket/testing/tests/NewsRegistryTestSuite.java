package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.news.NewsWorldRegistry.FireInfo;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

/**
 * Tests for the news world-event registry (T-096, sequences plan §3, category
 * {@code sm_news_registry}): auto fire-record create/update semantics
 * ({@link NewsWorldRegistry#recordFire}), the capped custom key/value store
 * (key-count and key/value length caps, refusal semantics), the read API the
 * T-097 requirement predicates code against ({@code getFireInfo}/{@code fireCount}/
 * {@code hasFired}/{@code getValue}), the T-099 clear operations, unmodifiable
 * views and the NBT round-trip incl. empty/absent-tag loads. Pure in-memory —
 * no Minecraft context needed (the DataManager registry.nbt file wiring is
 * exercised in-game).
 */
public class NewsRegistryTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_REGISTRY;
    }

    @Override
    public void registerTests() {
        // Auto fire records
        addTest("record_fire_creates_entry", this::test_recordFire_createsEntry);
        addTest("record_fire_updates_entry", this::test_recordFire_updatesEntry);
        addTest("record_fire_blank_id_ignored", this::test_recordFire_blankIdIgnored);
        addTest("fire_queries_on_unknown_id", this::test_fireQueriesOnUnknownId);

        // Custom key/value store
        addTest("put_value_happy_path", this::test_putValue_happyPath);
        addTest("put_value_overwrite_last_write_wins", this::test_putValue_overwriteLastWriteWins);
        addTest("put_value_key_count_cap", this::test_putValue_keyCountCap);
        addTest("put_value_key_length_cap", this::test_putValue_keyLengthCap);
        addTest("put_value_value_length_cap", this::test_putValue_valueLengthCap);
        addTest("put_value_null_blank_refused", this::test_putValue_nullBlankRefused);
        addTest("remove_value", this::test_removeValue);

        // Clear operations (T-099)
        addTest("clear_event", this::test_clearEvent);
        addTest("clear_key", this::test_clearKey);
        addTest("clear_all_and_is_empty", this::test_clearAllAndIsEmpty);

        // Views
        addTest("views_are_unmodifiable", this::test_viewsAreUnmodifiable);

        // NBT persistence
        addTest("nbt_round_trip", this::test_nbtRoundTrip);
        addTest("nbt_empty_tag_loads_empty", this::test_nbtEmptyTagLoadsEmpty);
        addTest("nbt_load_replaces_existing_content", this::test_nbtLoadReplacesExistingContent);
    }

    // ========================================================================
    // Auto fire records
    // ========================================================================

    /** First recordFire creates the entry: count 1, first == last == now, game day stored. */
    private TestResult test_recordFire_createsEntry() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("gold_standard", 1_000L, 7L);

        TestResult r = assertTrue("hasFired must be true after the first fire",
                registry.hasFired("gold_standard"));
        if (!r.passed()) return r;
        r = assertEquals("fireCount must be 1", 1, registry.fireCount("gold_standard"));
        if (!r.passed()) return r;
        FireInfo info = registry.getFireInfo("gold_standard");
        r = assertNotNull("getFireInfo must return the created entry", info);
        if (!r.passed()) return r;
        r = assertEquals("firstFiredEpochMs must be the fire time", 1_000L, info.firstFiredEpochMs());
        if (!r.passed()) return r;
        r = assertEquals("lastFiredEpochMs must equal firstFiredEpochMs on the first fire",
                1_000L, info.lastFiredEpochMs());
        if (!r.passed()) return r;
        return assertEquals("lastFiredGameDay must be stored", 7L, info.lastFiredGameDay());
    }

    /** Repeated fires increment the count, keep the first timestamp, update last + game day. */
    private TestResult test_recordFire_updatesEntry() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 1_000L, 7L);
        registry.recordFire("e", 5_000L, 9L);
        registry.recordFire("e", 9_000L, 12L);

        FireInfo info = registry.getFireInfo("e");
        TestResult r = assertNotNull("entry must exist", info);
        if (!r.passed()) return r;
        r = assertEquals("count must have been incremented per fire", 3, info.fireCount());
        if (!r.passed()) return r;
        r = assertEquals("firstFiredEpochMs must never change", 1_000L, info.firstFiredEpochMs());
        if (!r.passed()) return r;
        r = assertEquals("lastFiredEpochMs must track the newest fire", 9_000L, info.lastFiredEpochMs());
        if (!r.passed()) return r;
        r = assertEquals("lastFiredGameDay must track the newest fire", 12L, info.lastFiredGameDay());
        if (!r.passed()) return r;
        return assertEquals("still exactly one entry per distinct event id",
                1, registry.fireInfos().size());
    }

    /** null/blank event ids are ignored (WARN), never stored, never throw. */
    private TestResult test_recordFire_blankIdIgnored() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire(null, 1_000L, 1L);
        registry.recordFire("", 1_000L, 1L);
        registry.recordFire("   ", 1_000L, 1L);
        return assertTrue("nothing must have been recorded", registry.isEmpty());
    }

    /** The T-097 read API on never-fired ids: null info, count 0, hasFired false. */
    private TestResult test_fireQueriesOnUnknownId() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("known", 1_000L, 1L);

        TestResult r = assertNull("getFireInfo of an unknown id must be null",
                registry.getFireInfo("unknown"));
        if (!r.passed()) return r;
        r = assertEquals("fireCount of an unknown id must be 0", 0, registry.fireCount("unknown"));
        if (!r.passed()) return r;
        r = assertFalse("hasFired of an unknown id must be false", registry.hasFired("unknown"));
        if (!r.passed()) return r;
        r = assertNull("getFireInfo(null) must be null-safe", registry.getFireInfo(null));
        if (!r.passed()) return r;
        return assertFalse("hasFired(null) must be null-safe", registry.hasFired(null));
    }

    // ========================================================================
    // Custom key/value store
    // ========================================================================

    /** putValue stores the pair; getValue returns it; unknown keys yield null. */
    private TestResult test_putValue_happyPath() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        TestResult r = assertTrue("putValue must accept a normal pair",
                registry.putValue("era", "gold_standard"));
        if (!r.passed()) return r;
        r = assertEquals("getValue must return the stored value",
                "gold_standard", registry.getValue("era"));
        if (!r.passed()) return r;
        r = assertNull("getValue of an unknown key must be null", registry.getValue("nope"));
        if (!r.passed()) return r;
        return assertNull("getValue(null) must be null-safe", registry.getValue(null));
    }

    /** Overwriting an existing key replaces the value (last write wins). */
    private TestResult test_putValue_overwriteLastWriteWins() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "gold_standard");
        TestResult r = assertTrue("overwrite must be accepted", registry.putValue("era", "fiat"));
        if (!r.passed()) return r;
        r = assertEquals("last write must win", "fiat", registry.getValue("era"));
        if (!r.passed()) return r;
        return assertEquals("overwrite must not add a second key",
                1, registry.customValues().size());
    }

    /** The 257th DISTINCT key is refused; overwriting at the cap stays allowed. */
    private TestResult test_putValue_keyCountCap() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        for (int i = 0; i < NewsWorldRegistry.MAX_CUSTOM_KEYS; i++) {
            if (!registry.putValue("key_" + i, "v" + i)) {
                return fail("key " + i + " within the cap must be accepted");
            }
        }
        TestResult r = assertEquals("store must be exactly at the cap",
                NewsWorldRegistry.MAX_CUSTOM_KEYS, registry.customValues().size());
        if (!r.passed()) return r;
        r = assertFalse("the 257th new key must be refused",
                registry.putValue("one_too_many", "v"));
        if (!r.passed()) return r;
        r = assertNull("the refused key must not be stored", registry.getValue("one_too_many"));
        if (!r.passed()) return r;
        r = assertTrue("overwriting an EXISTING key at the cap must stay allowed",
                registry.putValue("key_0", "updated"));
        if (!r.passed()) return r;
        r = assertEquals("the overwrite must have taken effect", "updated", registry.getValue("key_0"));
        if (!r.passed()) return r;
        return assertEquals("size must still be exactly the cap",
                NewsWorldRegistry.MAX_CUSTOM_KEYS, registry.customValues().size());
    }

    /** Keys longer than the cap are refused; a key of exactly the cap length passes. */
    private TestResult test_putValue_keyLengthCap() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        String maxKey = "k".repeat(NewsWorldRegistry.MAX_KEY_LENGTH);
        String tooLongKey = "k".repeat(NewsWorldRegistry.MAX_KEY_LENGTH + 1);

        TestResult r = assertTrue("a key of exactly the cap length must be accepted",
                registry.putValue(maxKey, "v"));
        if (!r.passed()) return r;
        r = assertFalse("an over-length key must be refused", registry.putValue(tooLongKey, "v"));
        if (!r.passed()) return r;
        return assertEquals("only the valid key must be stored", 1, registry.customValues().size());
    }

    /** Values longer than the cap are refused; a value of exactly the cap length passes. */
    private TestResult test_putValue_valueLengthCap() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        String maxValue = "v".repeat(NewsWorldRegistry.MAX_VALUE_LENGTH);
        String tooLongValue = "v".repeat(NewsWorldRegistry.MAX_VALUE_LENGTH + 1);

        TestResult r = assertTrue("a value of exactly the cap length must be accepted",
                registry.putValue("ok", maxValue));
        if (!r.passed()) return r;
        r = assertFalse("an over-length value must be refused",
                registry.putValue("bad", tooLongValue));
        if (!r.passed()) return r;
        r = assertNull("the refused pair must not be stored", registry.getValue("bad"));
        if (!r.passed()) return r;
        return assertEquals("the valid value must be stored intact",
                maxValue, registry.getValue("ok"));
    }

    /** null/blank keys and null values are refused cleanly (never throw). */
    private TestResult test_putValue_nullBlankRefused() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        TestResult r = assertFalse("null key must be refused", registry.putValue(null, "v"));
        if (!r.passed()) return r;
        r = assertFalse("blank key must be refused", registry.putValue("  ", "v"));
        if (!r.passed()) return r;
        r = assertFalse("null value must be refused", registry.putValue("k", null));
        if (!r.passed()) return r;
        return assertTrue("registry must stay empty after the refusals", registry.isEmpty());
    }

    /** removeValue deletes one pair; removing an unknown/null key reports false. */
    private TestResult test_removeValue() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "fiat");
        TestResult r = assertTrue("removing an existing key must return true",
                registry.removeValue("era"));
        if (!r.passed()) return r;
        r = assertNull("the removed key must be gone", registry.getValue("era"));
        if (!r.passed()) return r;
        r = assertFalse("removing an unknown key must return false", registry.removeValue("era"));
        if (!r.passed()) return r;
        return assertFalse("removeValue(null) must be null-safe", registry.removeValue(null));
    }

    // ========================================================================
    // Clear operations (T-099)
    // ========================================================================

    /** clearEvent drops exactly one fire record; the event counts as never fired again. */
    private TestResult test_clearEvent() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("a", 1_000L, 1L);
        registry.recordFire("b", 2_000L, 2L);

        TestResult r = assertTrue("clearing an existing event must return true",
                registry.clearEvent("a"));
        if (!r.passed()) return r;
        r = assertFalse("the cleared event must count as never fired", registry.hasFired("a"));
        if (!r.passed()) return r;
        r = assertTrue("other events must be untouched", registry.hasFired("b"));
        if (!r.passed()) return r;
        r = assertFalse("clearing an unknown event must return false", registry.clearEvent("a"));
        if (!r.passed()) return r;
        return assertFalse("clearEvent(null) must be null-safe", registry.clearEvent(null));
    }

    /** clearKey drops exactly one custom pair (alias of removeValue). */
    private TestResult test_clearKey() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "fiat");
        registry.putValue("other", "x");

        TestResult r = assertTrue("clearing an existing key must return true",
                registry.clearKey("era"));
        if (!r.passed()) return r;
        r = assertNull("the cleared key must be gone", registry.getValue("era"));
        if (!r.passed()) return r;
        r = assertEquals("other keys must be untouched", "x", registry.getValue("other"));
        if (!r.passed()) return r;
        return assertFalse("clearing an unknown key must return false", registry.clearKey("era"));
    }

    /** clearAll wipes both stores; isEmpty tracks both. */
    private TestResult test_clearAllAndIsEmpty() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        TestResult r = assertTrue("a fresh registry must be empty", registry.isEmpty());
        if (!r.passed()) return r;

        registry.recordFire("a", 1_000L, 1L);
        r = assertFalse("a fire record alone must make it non-empty", registry.isEmpty());
        if (!r.passed()) return r;
        registry.clearAll();

        registry.putValue("k", "v");
        r = assertFalse("a custom pair alone must make it non-empty", registry.isEmpty());
        if (!r.passed()) return r;

        registry.recordFire("a", 1_000L, 1L);
        registry.clearAll();
        r = assertTrue("clearAll must empty the registry", registry.isEmpty());
        if (!r.passed()) return r;
        r = assertEquals("no fire records must remain", 0, registry.fireInfos().size());
        if (!r.passed()) return r;
        return assertEquals("no custom pairs must remain", 0, registry.customValues().size());
    }

    // ========================================================================
    // Views
    // ========================================================================

    /** fireInfos()/customValues() reflect the state but must reject mutation. */
    private TestResult test_viewsAreUnmodifiable() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("a", 1_000L, 1L);
        registry.putValue("k", "v");

        TestResult r = assertEquals("fireInfos view must reflect the state",
                1, registry.fireInfos().size());
        if (!r.passed()) return r;
        r = assertEquals("customValues view must reflect the state",
                "v", registry.customValues().get("k"));
        if (!r.passed()) return r;

        try {
            registry.fireInfos().put("evil", new FireInfo(1, 0L, 0L, 0L));
            return fail("fireInfos() view must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // the contract T-099 renders against
        }
        try {
            registry.customValues().put("evil", "v");
            return fail("customValues() view must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // the contract T-099 renders against
        }
        try {
            registry.customValues().remove("k");
            return fail("customValues() view must reject removal too");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        return assertEquals("failed mutation attempts must not have changed anything",
                1, registry.customValues().size());
    }

    // ========================================================================
    // NBT persistence
    // ========================================================================

    /** save() + load() must reproduce fire records AND custom pairs exactly. */
    private TestResult test_nbtRoundTrip() {
        NewsWorldRegistry original = new NewsWorldRegistry();
        original.recordFire("gold_standard", 1_000L, 7L);
        original.recordFire("gold_standard", 5_000L, 9L);
        original.recordFire("crash", 3_000L, 8L);
        original.putValue("era", "gold_standard");
        original.putValue("counter", "42");

        CompoundTag tag = new CompoundTag();
        TestResult r = assertTrue("save must succeed", original.save(tag));
        if (!r.passed()) return r;

        NewsWorldRegistry loaded = new NewsWorldRegistry();
        r = assertTrue("load must succeed", loaded.load(tag));
        if (!r.passed()) return r;

        FireInfo gold = loaded.getFireInfo("gold_standard");
        r = assertNotNull("gold_standard record must survive", gold);
        if (!r.passed()) return r;
        r = assertEquals("gold_standard record must round-trip all four fields",
                new FireInfo(2, 1_000L, 5_000L, 9L), gold);
        if (!r.passed()) return r;
        r = assertEquals("crash record must round-trip",
                new FireInfo(1, 3_000L, 3_000L, 8L), loaded.getFireInfo("crash"));
        if (!r.passed()) return r;
        r = assertEquals("exactly the two fire records must be loaded",
                2, loaded.fireInfos().size());
        if (!r.passed()) return r;
        r = assertEquals("custom pair 'era' must survive",
                "gold_standard", loaded.getValue("era"));
        if (!r.passed()) return r;
        r = assertEquals("custom pair 'counter' must survive", "42", loaded.getValue("counter"));
        if (!r.passed()) return r;
        return assertEquals("exactly the two custom pairs must be loaded",
                2, loaded.customValues().size());
    }

    /** An empty tag (absent file / pre-registry world) loads as an empty registry. */
    private TestResult test_nbtEmptyTagLoadsEmpty() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        TestResult r = assertTrue("loading an empty tag must succeed (contains-guards)",
                registry.load(new CompoundTag()));
        if (!r.passed()) return r;
        return assertTrue("the registry must stay empty", registry.isEmpty());
    }

    /** load() replaces existing content — nothing pre-load may leak through. */
    private TestResult test_nbtLoadReplacesExistingContent() {
        NewsWorldRegistry saved = new NewsWorldRegistry();
        saved.recordFire("kept", 1_000L, 1L);
        saved.putValue("kept_key", "kept_value");
        CompoundTag tag = new CompoundTag();
        saved.save(tag);

        NewsWorldRegistry target = new NewsWorldRegistry();
        target.recordFire("stale", 9_000L, 9L);
        target.putValue("stale_key", "stale_value");
        TestResult r = assertTrue("load must succeed", target.load(tag));
        if (!r.passed()) return r;
        r = assertFalse("stale fire record must be gone after the load",
                target.hasFired("stale"));
        if (!r.passed()) return r;
        r = assertNull("stale custom pair must be gone after the load",
                target.getValue("stale_key"));
        if (!r.passed()) return r;
        r = assertTrue("loaded fire record must be present", target.hasFired("kept"));
        if (!r.passed()) return r;
        return assertEquals("loaded custom pair must be present",
                "kept_value", target.getValue("kept_key"));
    }
}
