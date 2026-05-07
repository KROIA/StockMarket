package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.marketmanager.User;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class UserTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.USER;
    }

    @Override
    public void registerTests() {
        addTest("save_load_round_trip", this::test_save_load_roundTrip);
        addTest("load_missing_uuid", this::test_load_missingUUID);
        addTest("load_missing_username", this::test_load_missingUserName);
        addTest("load_missing_admin_flag", this::test_load_missingAdminFlag);
        addTest("create_from_tag_valid", this::test_createFromTag_validTag);
        addTest("create_from_tag_invalid", this::test_createFromTag_invalidTag);
        addTest("create_with_changed_name", this::test_createWithChangedName);
        addTest("to_json_round_trip", this::test_toJson_roundTrip);
    }

    /**
     * UUID, name, admin status preserved through save/load.
     */
    private TestResult test_save_load_roundTrip() {
        UUID uuid = UUID.randomUUID();
        User original = new User(uuid, "TestPlayer");
        original.setStockMarketAdmin(true);

        CompoundTag tag = new CompoundTag();
        boolean saved = original.save(tag);
        TestResult r = assertTrue("save() should return true", saved);
        if (!r.passed()) return r;

        User loaded = User.createFromTag(tag);
        r = assertNotNull("createFromTag should not return null", loaded);
        if (!r.passed()) return r;
        r = assertEquals("UUID should match", uuid, loaded.getUUID());
        if (!r.passed()) return r;
        r = assertEquals("Name should match", "TestPlayer", loaded.getName());
        if (!r.passed()) return r;
        r = assertTrue("Admin status should be true", loaded.isStockMarketAdmin());
        if (!r.passed()) return r;

        return pass("Save/load round-trip preserves UUID, name, and admin status");
    }

    /**
     * Returns false when UUID is missing.
     */
    private TestResult test_load_missingUUID() {
        CompoundTag tag = new CompoundTag();
        tag.putString("userName", "TestPlayer");
        tag.putBoolean("isStockMarketAdmin", false);
        // No userUUID

        User user = User.createFromTag(tag);
        return assertNull("Should return null when UUID is missing", user);
    }

    /**
     * Returns false when userName is missing.
     */
    private TestResult test_load_missingUserName() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("userUUID", UUID.randomUUID());
        tag.putBoolean("isStockMarketAdmin", false);
        // No userName

        User user = User.createFromTag(tag);
        return assertNull("Should return null when userName is missing", user);
    }

    /**
     * Missing admin flag defaults to false.
     */
    private TestResult test_load_missingAdminFlag() {
        UUID uuid = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("userUUID", uuid);
        tag.putString("userName", "TestPlayer");
        // No isStockMarketAdmin

        User user = User.createFromTag(tag);
        TestResult r = assertNotNull("Should load successfully without admin flag", user);
        if (!r.passed()) return r;
        r = assertFalse("Admin should default to false", user.isStockMarketAdmin());
        if (!r.passed()) return r;
        return pass("Missing admin flag defaults to false");
    }

    /**
     * Returns User with correct fields from valid tag.
     */
    private TestResult test_createFromTag_validTag() {
        UUID uuid = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("userUUID", uuid);
        tag.putString("userName", "ValidUser");
        tag.putBoolean("isStockMarketAdmin", true);

        User user = User.createFromTag(tag);
        TestResult r = assertNotNull("Should create user from valid tag", user);
        if (!r.passed()) return r;
        r = assertEquals("UUID should match", uuid, user.getUUID());
        if (!r.passed()) return r;
        r = assertEquals("Name should match", "ValidUser", user.getName());
        if (!r.passed()) return r;
        r = assertTrue("Admin should be true", user.isStockMarketAdmin());
        if (!r.passed()) return r;
        return pass("createFromTag produces correct User from valid tag");
    }

    /**
     * Returns null from invalid tag.
     */
    private TestResult test_createFromTag_invalidTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("garbage", "data");

        User user = User.createFromTag(tag);
        return assertNull("Should return null for invalid tag", user);
    }

    /**
     * Preserves UUID and admin, changes name.
     */
    private TestResult test_createWithChangedName() {
        UUID uuid = UUID.randomUUID();
        User original = new User(uuid, "OriginalName");
        original.setStockMarketAdmin(true);

        User changed = User.createWithChangedName(original, "NewName");
        TestResult r = assertEquals("UUID should be preserved", uuid, changed.getUUID());
        if (!r.passed()) return r;
        r = assertEquals("Name should be changed", "NewName", changed.getName());
        if (!r.passed()) return r;
        r = assertTrue("Admin status should be preserved", changed.isStockMarketAdmin());
        if (!r.passed()) return r;
        return pass("createWithChangedName preserves UUID and admin, changes name");
    }

    /**
     * JSON serialization produces expected structure.
     */
    private TestResult test_toJson_roundTrip() {
        UUID uuid = UUID.randomUUID();
        User user = new User(uuid, "JsonUser");
        user.setStockMarketAdmin(true);

        JsonElement json = user.toJson();
        TestResult r = assertTrue("toJson should return a JsonObject", json.isJsonObject());
        if (!r.passed()) return r;

        JsonObject obj = json.getAsJsonObject();
        r = assertTrue("JSON should contain userUUID", obj.has("userUUID"));
        if (!r.passed()) return r;
        r = assertEquals("UUID should match in JSON", uuid.toString(), obj.get("userUUID").getAsString());
        if (!r.passed()) return r;
        r = assertTrue("JSON should contain userName", obj.has("userName"));
        if (!r.passed()) return r;
        r = assertEquals("Name should match in JSON", "JsonUser", obj.get("userName").getAsString());
        if (!r.passed()) return r;
        r = assertTrue("JSON should contain isStockMarketAdmin", obj.has("isStockMarketAdmin"));
        if (!r.passed()) return r;
        r = assertTrue("Admin should be true in JSON", obj.get("isStockMarketAdmin").getAsBoolean());
        if (!r.passed()) return r;
        return pass("toJson produces expected JSON structure");
    }
}
