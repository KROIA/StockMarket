package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class PlayerPreferencesTestSuite extends TestSuite {

    // Dummy item IDs for testing (short-based, no Minecraft context needed)
    private static final ItemID ITEM_A = new ItemID((short) 100);
    private static final ItemID ITEM_B = new ItemID((short) 200);
    private static final ItemID ITEM_C = new ItemID((short) 300);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PLAYER_PREFERENCES;
    }

    @Override
    public void registerTests() {
        addTest("add_favorite_basic", this::test_addFavorite_basic);
        addTest("add_favorite_ignores_duplicate", this::test_addFavorite_ignoresDuplicate);
        addTest("remove_favorite", this::test_removeFavorite);
        addTest("remove_favorite_nonexistent", this::test_removeFavorite_nonexistent);
        addTest("is_favorite_empty_list", this::test_isFavorite_emptyList);
        addTest("last_market_id_default_null", this::test_lastMarketID_defaultNull);
        addTest("last_market_id_set_get", this::test_lastMarketID_setGet);
        addTest("last_market_id_set_null", this::test_lastMarketID_setNull);
        addTest("nbt_save_load_empty", this::test_nbt_saveLoadEmpty);
        addTest("nbt_save_load_with_data", this::test_nbt_saveLoadWithData);
        addTest("nbt_save_load_favorites_order", this::test_nbt_saveLoadFavoritesOrder);
        addTest("nbt_backward_compat", this::test_nbt_backwardCompat);
        addTest("news_toast_default_off", this::test_newsToast_defaultOff);
        addTest("news_toast_nbt_round_trip", this::test_newsToast_nbtRoundTrip);
        addTest("news_toast_missing_key_stays_off", this::test_newsToast_missingKeyStaysOff);
    }

    // -----------------------------------------------------------------------
    // Favorite management tests
    // -----------------------------------------------------------------------

    /**
     * Adding a market to favorites makes it appear in the list and isFavorite returns true.
     */
    private TestResult test_addFavorite_basic() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.addFavorite(ITEM_A);

        TestResult r = assertTrue("isFavorite should return true after adding", prefs.isFavorite(ITEM_A));
        if (!r.passed()) return r;
        r = assertEquals("Favorites list should have size 1", 1, prefs.getFavoriteMarketIDs().size());
        if (!r.passed()) return r;
        r = assertEquals("First favorite should be ITEM_A",
                ITEM_A.getShort(), prefs.getFavoriteMarketIDs().get(0).getShort());
        if (!r.passed()) return r;
        return pass("addFavorite adds market to list and isFavorite returns true");
    }

    /**
     * Adding a market that is already a favorite is a no-op (static ordering).
     * Adding A, B, C results in [A, B, C]. Re-adding A leaves it as [A, B, C].
     */
    private TestResult test_addFavorite_ignoresDuplicate() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.addFavorite(ITEM_A); // [A]
        prefs.addFavorite(ITEM_B); // [A, B]
        prefs.addFavorite(ITEM_C); // [A, B, C]

        // Re-add A — should be no-op since it's already present
        prefs.addFavorite(ITEM_A); // still [A, B, C]

        List<ItemID> favs = prefs.getFavoriteMarketIDs();
        TestResult r = assertEquals("List should still have 3 elements", 3, favs.size());
        if (!r.passed()) return r;
        r = assertEquals("First element should still be A",
                ITEM_A.getShort(), favs.get(0).getShort());
        if (!r.passed()) return r;
        r = assertEquals("Second element should still be B",
                ITEM_B.getShort(), favs.get(1).getShort());
        if (!r.passed()) return r;
        r = assertEquals("Third element should still be C",
                ITEM_C.getShort(), favs.get(2).getShort());
        if (!r.passed()) return r;
        return pass("Re-adding an existing favorite does not change the list");
    }

    /**
     * Removing a favorite makes isFavorite return false and shrinks the list.
     */
    private TestResult test_removeFavorite() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.addFavorite(ITEM_A);
        prefs.removeFavorite(ITEM_A);

        TestResult r = assertFalse("isFavorite should return false after removal", prefs.isFavorite(ITEM_A));
        if (!r.passed()) return r;
        r = assertTrue("Favorites list should be empty", prefs.getFavoriteMarketIDs().isEmpty());
        if (!r.passed()) return r;
        return pass("removeFavorite removes market and isFavorite returns false");
    }

    /**
     * Removing a market that isn't in favorites causes no error and leaves the list unchanged.
     */
    private TestResult test_removeFavorite_nonexistent() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.addFavorite(ITEM_A);

        // Remove ITEM_B which was never added
        prefs.removeFavorite(ITEM_B);

        TestResult r = assertEquals("List should still have 1 element", 1, prefs.getFavoriteMarketIDs().size());
        if (!r.passed()) return r;
        r = assertTrue("ITEM_A should still be a favorite", prefs.isFavorite(ITEM_A));
        if (!r.passed()) return r;
        return pass("Removing a nonexistent favorite causes no error and leaves list unchanged");
    }

    /**
     * isFavorite returns false on an empty favorites list.
     */
    private TestResult test_isFavorite_emptyList() {
        PlayerPreferences prefs = new PlayerPreferences();
        TestResult r = assertFalse("isFavorite should return false on empty list", prefs.isFavorite(ITEM_A));
        if (!r.passed()) return r;
        return pass("isFavorite returns false on empty list");
    }

    // -----------------------------------------------------------------------
    // lastMarketID tests
    // -----------------------------------------------------------------------

    /**
     * New PlayerPreferences has null lastMarketID by default.
     */
    private TestResult test_lastMarketID_defaultNull() {
        PlayerPreferences prefs = new PlayerPreferences();
        TestResult r = assertNull("Default lastMarketID should be null", prefs.getLastMarketID());
        if (!r.passed()) return r;
        return pass("New PlayerPreferences has null lastMarketID");
    }

    /**
     * Setting a lastMarketID and reading it back returns the same value.
     */
    private TestResult test_lastMarketID_setGet() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setLastMarketID(ITEM_A);

        TestResult r = assertNotNull("lastMarketID should not be null after setting", prefs.getLastMarketID());
        if (!r.passed()) return r;
        r = assertEquals("lastMarketID should match what was set",
                ITEM_A.getShort(), prefs.getLastMarketID().getShort());
        if (!r.passed()) return r;
        return pass("setLastMarketID/getLastMarketID round-trip works");
    }

    /**
     * Setting lastMarketID to null clears it.
     */
    private TestResult test_lastMarketID_setNull() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setLastMarketID(ITEM_A);
        prefs.setLastMarketID(null);

        TestResult r = assertNull("lastMarketID should be null after setting to null", prefs.getLastMarketID());
        if (!r.passed()) return r;
        return pass("Setting lastMarketID to null clears it");
    }

    // -----------------------------------------------------------------------
    // NBT persistence tests
    // -----------------------------------------------------------------------

    /**
     * Saving and loading empty preferences preserves the empty state.
     */
    private TestResult test_nbt_saveLoadEmpty() {
        PlayerPreferences original = new PlayerPreferences();

        CompoundTag tag = new CompoundTag();
        boolean saved = original.save(tag);
        TestResult r = assertTrue("save() should return true", saved);
        if (!r.passed()) return r;

        PlayerPreferences loaded = new PlayerPreferences();
        boolean loadResult = loaded.load(tag);
        r = assertTrue("load() should return true", loadResult);
        if (!r.passed()) return r;
        r = assertNull("lastMarketID should be null after loading empty prefs", loaded.getLastMarketID());
        if (!r.passed()) return r;
        r = assertTrue("Favorites should be empty after loading empty prefs",
                loaded.getFavoriteMarketIDs().isEmpty());
        if (!r.passed()) return r;
        return pass("Save/load of empty preferences preserves empty state");
    }

    /**
     * Saving preferences with data and loading them back preserves all fields.
     */
    private TestResult test_nbt_saveLoadWithData() {
        PlayerPreferences original = new PlayerPreferences();
        original.setLastMarketID(ITEM_A);
        original.addFavorite(ITEM_B);
        original.addFavorite(ITEM_C);

        CompoundTag tag = new CompoundTag();
        boolean saved = original.save(tag);
        TestResult r = assertTrue("save() should return true", saved);
        if (!r.passed()) return r;

        PlayerPreferences loaded = new PlayerPreferences();
        boolean loadResult = loaded.load(tag);
        r = assertTrue("load() should return true", loadResult);
        if (!r.passed()) return r;

        // Verify lastMarketID
        r = assertNotNull("lastMarketID should not be null after loading", loaded.getLastMarketID());
        if (!r.passed()) return r;
        r = assertEquals("lastMarketID should match",
                ITEM_A.getShort(), loaded.getLastMarketID().getShort());
        if (!r.passed()) return r;

        // Verify favorites
        List<ItemID> favs = loaded.getFavoriteMarketIDs();
        r = assertEquals("Favorites should have 2 entries", 2, favs.size());
        if (!r.passed()) return r;
        r = assertEquals("First favorite should be B (added first)",
                ITEM_B.getShort(), favs.get(0).getShort());
        if (!r.passed()) return r;
        r = assertEquals("Second favorite should be C (added second)",
                ITEM_C.getShort(), favs.get(1).getShort());
        if (!r.passed()) return r;
        return pass("Save/load round-trip preserves lastMarketID and favorites");
    }

    /**
     * Favorites order is preserved through save/load.
     * Adding A, B, C in that order results in [A, B, C] (append semantics).
     */
    private TestResult test_nbt_saveLoadFavoritesOrder() {
        PlayerPreferences original = new PlayerPreferences();
        original.addFavorite(ITEM_A); // [A]
        original.addFavorite(ITEM_B); // [A, B]
        original.addFavorite(ITEM_C); // [A, B, C]

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        PlayerPreferences loaded = new PlayerPreferences();
        loaded.load(tag);

        List<ItemID> favs = loaded.getFavoriteMarketIDs();
        TestResult r = assertEquals("Favorites should have 3 entries", 3, favs.size());
        if (!r.passed()) return r;
        r = assertEquals("First should be A (first added)",
                ITEM_A.getShort(), favs.get(0).getShort());
        if (!r.passed()) return r;
        r = assertEquals("Second should be B",
                ITEM_B.getShort(), favs.get(1).getShort());
        if (!r.passed()) return r;
        r = assertEquals("Third should be C (last added)",
                ITEM_C.getShort(), favs.get(2).getShort());
        if (!r.passed()) return r;
        return pass("Save/load preserves favorites order [A, B, C]");
    }

    /**
     * Loading from a CompoundTag with no "favorites" and no "lastMarket" keys
     * should produce empty preferences without errors (backward compatibility).
     */
    private TestResult test_nbt_backwardCompat() {
        CompoundTag emptyTag = new CompoundTag();
        // Tag has no "hasLastMarket", "lastMarket", or "favorites" keys

        PlayerPreferences loaded = new PlayerPreferences();
        boolean loadResult = loaded.load(emptyTag);
        TestResult r = assertTrue("load() should return true even with empty tag", loadResult);
        if (!r.passed()) return r;
        r = assertNull("lastMarketID should be null from empty tag", loaded.getLastMarketID());
        if (!r.passed()) return r;
        r = assertTrue("Favorites should be empty from empty tag",
                loaded.getFavoriteMarketIDs().isEmpty());
        if (!r.passed()) return r;
        return pass("Backward-compatible load from empty tag produces empty preferences");
    }

    // -----------------------------------------------------------------------
    // News toast opt-in flag tests (T-074)
    // -----------------------------------------------------------------------

    /**
     * The news toast opt-in must default to OFF (user decision: players who never
     * touched the newspaper checkbox get no notification at all).
     */
    private TestResult test_newsToast_defaultOff() {
        PlayerPreferences prefs = new PlayerPreferences();
        TestResult r = assertFalse("newsToastEnabled must default to false", prefs.isNewsToastEnabled());
        if (!r.passed()) return r;
        return pass("News toast opt-in defaults to off");
    }

    /**
     * The enabled flag survives an NBT save/load round-trip (checkbox state must
     * persist across relogs).
     */
    private TestResult test_newsToast_nbtRoundTrip() {
        PlayerPreferences original = new PlayerPreferences();
        original.setNewsToastEnabled(true);

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        PlayerPreferences loaded = new PlayerPreferences();
        loaded.load(tag);
        TestResult r = assertTrue("enabled flag must survive NBT round-trip", loaded.isNewsToastEnabled());
        if (!r.passed()) return r;
        return pass("News toast opt-in survives NBT save/load");
    }

    /**
     * Loading a pre-T-074 tag (no "newsToastEnabled" key) must yield OFF — even if
     * the in-memory instance had the flag set before load().
     */
    private TestResult test_newsToast_missingKeyStaysOff() {
        PlayerPreferences loaded = new PlayerPreferences();
        loaded.setNewsToastEnabled(true); // must be overwritten by the (missing) tag value
        loaded.load(new CompoundTag());
        TestResult r = assertFalse("missing key must load as false (default off)",
                loaded.isNewsToastEnabled());
        if (!r.passed()) return r;
        return pass("Pre-T-074 tags load with the news toast off");
    }
}
