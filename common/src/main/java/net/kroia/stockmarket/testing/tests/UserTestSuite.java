package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.NbtCompat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.stockmarket.marketmanager.User;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserTestSuite extends TestSuite {
    private static StockMarketModBackend.ServerInstances backend;
    public static void setBackend(StockMarketModBackend.ServerInstances b) { backend = b; }

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
        addTest("save_load_preserves_preferences", this::test_save_load_preservesPreferences);
        addTest("create_with_changed_name_preserves_preferences", this::test_createWithChangedName_preservesPreferences);
        addTest("permission_nodes_registered_with_level_2", this::test_permissionNodes_registeredWithLevel2);
        addTest("admin_command_non_player_uses_op_level", this::test_adminCommand_nonPlayerUsesOpLevel);
        addTest("admin_command_non_op_with_flag_allowed", () -> test_adminCommand_gate(true, 0, true));
        addTest("admin_command_op_without_flag_refused", () -> test_adminCommand_gate(false, 4, false));
        addTest("duplicate_name_self_op_flags_executor", this::test_duplicateName_selfOpFlagsExecutor);
        addTest("duplicate_name_lookup_refuses_ambiguous", this::test_duplicateName_lookupRefusesAmbiguous);
        addTest("duplicate_name_join_marks_old_stale", this::test_duplicateName_joinMarksOldStale);
        addTest("name_lookup_prefers_online_player", this::test_nameLookup_prefersOnlinePlayer);
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
        NbtCompat.putUUID(tag, "userUUID", UUID.randomUUID());
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
        NbtCompat.putUUID(tag, "userUUID", uuid);
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
        NbtCompat.putUUID(tag, "userUUID", uuid);
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

    /**
     * User save/load preserves preferences (empty default state).
     */
    private TestResult test_save_load_preservesPreferences() {
        UUID uuid = UUID.randomUUID();
        User original = new User(uuid, "TestPlayer");

        CompoundTag tag = new CompoundTag();
        original.save(tag);

        User loaded = User.createFromTag(tag);
        TestResult r = assertNotNull("loaded user should not be null", loaded);
        if (!r.passed()) return r;
        r = assertNotNull("preferences should not be null", loaded.getPreferences());
        if (!r.passed()) return r;
        r = assertNull("default lastMarketID should be null", loaded.getPreferences().getLastMarketID());
        if (!r.passed()) return r;
        r = assertTrue("default favorites should be empty", loaded.getPreferences().getFavoriteMarketIDs().isEmpty());
        if (!r.passed()) return r;
        return pass("User save/load preserves preferences (empty default)");
    }

    /**
     * createWithChangedName preserves the original user's preferences object.
     */
    private TestResult test_createWithChangedName_preservesPreferences() {
        UUID uuid = UUID.randomUUID();
        User original = new User(uuid, "OriginalName");
        original.setStockMarketAdmin(true);

        // Modify the original's preferences
        ItemID testItem = new ItemID((short) 42);
        original.getPreferences().setLastMarketID(testItem);
        original.getPreferences().addFavorite(testItem);

        User changed = User.createWithChangedName(original, "NewName");
        TestResult r = assertNotNull("changed user preferences should not be null", changed.getPreferences());
        if (!r.passed()) return r;
        r = assertNotNull("lastMarketID should be preserved", changed.getPreferences().getLastMarketID());
        if (!r.passed()) return r;
        r = assertEquals("lastMarketID should match original",
                testItem.getShort(), changed.getPreferences().getLastMarketID().getShort());
        if (!r.passed()) return r;
        r = assertFalse("favorites should not be empty", changed.getPreferences().getFavoriteMarketIDs().isEmpty());
        if (!r.passed()) return r;
        return pass("createWithChangedName preserves preferences");
    }

    /**
     * Every admin permission node (T-168) is registered with its fallback level: op level 2, except the flag-gated
     * command nodes (SM-266), which every player passes (level 0). Registration runs in StockMarketMod.init().
     */
    private TestResult test_permissionNodes_registeredWithLevel2() {
        java.util.Map<String, Integer> registered = net.kroia.modutilities.PermissionCompat.getRegisteredNodes();
        for (String node : StockMarketPermissions.ALL_NODES) {
            int expected = StockMarketPermissions.FLAG_GATED_NODES.contains(node) ? 0 : 2;
            TestResult r = assertEquals("fallback level of " + node, expected, registered.get(node));
            if (!r.passed()) return r;
        }
        return pass("All " + StockMarketPermissions.ALL_NODES.length + " permission nodes registered with their fallback level");
    }

    /** SM-266: console and other non-player sources keep the op-level check on the flag-gated commands. */
    private TestResult test_adminCommand_nonPlayerUsesOpLevel() {
        CommandSourceStack console = getServer().createCommandSourceStack();
        TestResult r = assertTrue("console (level 4) passes",
                StockMarketPermissions.canUseFlagGatedCommand(console, StockMarketPermissions.COMMAND_MARKET));
        if (!r.passed()) return r;
        return assertFalse("level-0 non-player source is refused",
                StockMarketPermissions.canUseFlagGatedCommand(console.withPermission(0), StockMarketPermissions.COMMAND_MARKET));
    }

    /**
     * SM-266: a player's admin command runs if and only if they have the StockMarket admin flag, regardless of op.
     *
     * @param flag        the admin flag to give the online player during the test
     * @param opLevel     the permission level of the player's command source
     * @param expectedRan whether the guarded action must run
     */
    private TestResult test_adminCommand_gate(boolean flag, int opLevel, boolean expectedRan) {
        ServerMarketManager manager = backend == null || backend.MARKET_MANAGER == null ? null : backend.MARKET_MANAGER.getServerMarketManager();
        if (manager == null)
            return skip("no master server");
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty())
            return skip("no online player");
        ServerPlayer player = players.get(0);
        UUID uuid = player.getUUID();
        boolean before = manager.isStockmarketAdmin(uuid);
        try {
            manager.setStockmarketAdminMode(uuid, flag);
            CommandSourceStack source = player.createCommandSourceStack().withPermission(opLevel);
            TestResult r = assertTrue("player passes the requires check",
                    StockMarketPermissions.canUseFlagGatedCommand(source, StockMarketPermissions.COMMAND_MARKET));
            if (!r.passed()) return r;
            AtomicBoolean ran = new AtomicBoolean();
            CompletableFuture<Boolean> result = StockMarketCommands.runAsAdmin(source, () -> ran.set(true));
            if (!pumpUntil(result::isDone, 5_000))
                return fail("admin check did not complete");
            r = assertEquals("action ran (flag=" + flag + ", level=" + opLevel + ")", expectedRan, ran.get());
            if (!r.passed()) return r;
            return assertEquals("result matches", expectedRan, result.join());
        } finally {
            manager.setStockmarketAdminMode(uuid, before);
        }
    }

    // === SM-264: two stored users with the same name (e.g. after switching online-mode) ===

    private static final String DUP_NAME = "SmDupNameTest";

    private static ServerMarketManager manager() {
        return backend == null || backend.MARKET_MANAGER == null ? null : backend.MARKET_MANAGER.getServerMarketManager();
    }

    /**
     * Inserts a user directly, without {@code onPlayerJoin}, to reproduce stored data from before SM-264 in which
     * two users hold the same name.
     */
    @SuppressWarnings("unchecked")
    private static void putLegacyUser(ServerMarketManager m, UUID uuid, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = ServerMarketManager.class.getDeclaredField("userMap");
        f.setAccessible(true);
        ((java.util.Map<UUID, User>) f.get(m)).put(uuid, new User(uuid, name));
    }

    /** Self-op sets the flag on the executor's own UUID, not on another user with the same name. */
    private TestResult test_duplicateName_selfOpFlagsExecutor() {
        ServerMarketManager m = manager();
        if (m == null) return skip("no master server");
        UUID old = UUID.randomUUID(), current = UUID.randomUUID();
        try {
            putLegacyUser(m, old, DUP_NAME);
            putLegacyUser(m, current, DUP_NAME);
            TestResult r = assertTrue("self-op succeeds", new ServerStockMarketCommandHandler().stockmarket_setStockmarketAdminMode(current, true));
            if (!r.passed()) return r;
            r = assertTrue("executor is admin", m.isStockmarketAdmin(current));
            if (!r.passed()) return r;
            return assertFalse("old record untouched", m.isStockmarketAdmin(old));
        } catch (ReflectiveOperationException e) {
            return fail("reflection failed: " + e);
        } finally {
            m.removeUser(old);
            m.removeUser(current);
        }
    }

    /** A name held by several stored users resolves to nobody, and op by that name changes nobody. */
    private TestResult test_duplicateName_lookupRefusesAmbiguous() {
        ServerMarketManager m = manager();
        if (m == null) return skip("no master server");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try {
            putLegacyUser(m, a, DUP_NAME);
            putLegacyUser(m, b, DUP_NAME);
            TestResult r = assertNull("ambiguous name resolves to null", m.getPlayerUUID(DUP_NAME));
            if (!r.passed()) return r;
            r = assertTrue("reported as ambiguous", m.isUserNameAmbiguous(DUP_NAME));
            if (!r.passed()) return r;
            r = assertFalse("op <name> refused", new ServerStockMarketCommandHandler().stockmarket_setStockmarketAdminMode_user(a, DUP_NAME, true));
            if (!r.passed()) return r;
            return assertFalse("nobody flagged", m.isStockmarketAdmin(a) || m.isStockmarketAdmin(b));
        } catch (ReflectiveOperationException e) {
            return fail("reflection failed: " + e);
        } finally {
            m.removeUser(a);
            m.removeUser(b);
        }
    }

    /** A join with a name held by another UUID renames the old record; its flag stays, the name resolves to the new one. */
    private TestResult test_duplicateName_joinMarksOldStale() {
        ServerMarketManager m = manager();
        if (m == null) return skip("no master server");
        UUID old = UUID.randomUUID(), current = UUID.randomUUID();
        try {
            m.onPlayerJoin(old, DUP_NAME);
            m.setStockmarketAdminMode(old, true);
            m.onPlayerJoin(current, DUP_NAME);
            TestResult r = assertEquals("name resolves to the joining UUID", current, m.getPlayerUUID(DUP_NAME));
            if (!r.passed()) return r;
            r = assertEquals("old record renamed", ServerMarketManager.staleName(DUP_NAME, old), m.getUserName(old));
            if (!r.passed()) return r;
            r = assertTrue("old record keeps its admin flag", m.isStockmarketAdmin(old));
            if (!r.passed()) return r;
            return assertFalse("new record not admin", m.isStockmarketAdmin(current));
        } finally {
            m.removeUser(old);
            m.removeUser(current);
        }
    }

    /** An online player wins the name lookup over a stored user with the same name. */
    private TestResult test_nameLookup_prefersOnlinePlayer() {
        ServerMarketManager m = manager();
        if (m == null) return skip("no master server");
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return skip("no online player");
        ServerPlayer player = players.get(0);
        if (!m.userExists(player.getUUID())) return skip("online player has no StockMarket user");
        UUID fake = UUID.randomUUID();
        try {
            putLegacyUser(m, fake, player.getName().getString());
            return assertEquals("online player resolved", player.getUUID(), m.getPlayerUUID(player.getName().getString()));
        } catch (ReflectiveOperationException e) {
            return fail("reflection failed: " + e);
        } finally {
            m.removeUser(fake);
        }
    }
}
