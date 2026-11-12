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
        addTest("slave_admin_command_non_op_with_flag_allowed", () -> test_slaveAdminCommand_gate(true));
        addTest("slave_admin_command_without_flag_refused", () -> test_slaveAdminCommand_gate(false));
        addTest("op_deop_backup_need_level_2", this::test_opDeopBackup_needLevel2);
        addTest("manage_node_visible_to_level_0_player", this::test_manageNode_visibleToLevel0Player);
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

    // === SM-287: flag-gated admin commands for a non-op player on a slave ===

    private static final long SLAVE_WAIT_MS = 10_000;

    /**
     * SM-287, slave only: the master holds the admin flag of the online player. A level-0 source of that player
     * passes the {@code requires} check, and {@link StockMarketCommands#runAsAdmin} runs the action if and only if
     * the master reports the flag. Without it the player gets {@code message.stockmarket.command_admin_only}, or on an
     * untrusted slave {@code message.stockmarket.command_slave_untrusted} (SM-312).
     * <p>
     * The master answers admin-flag reads of an untrusted slave empty (T-239, {@code NetworkGate}), so there no
     * player ever passes: {@code flag=true} needs a trusted slave (autotest cells {@code slave-trusted} /
     * {@code spslave-trusted}, where BankSystem's DevBootstrap trusts the slave from startup) and skips otherwise;
     * {@code flag=false} runs on every slave. Only on a trusted slave the flag is set through the master (an
     * untrusted slave's write would be refused) and restored afterwards.
     *
     * @param flag the admin flag the master holds for the player during the test
     */
    private TestResult test_slaveAdminCommand_gate(boolean flag) {
        if (backend == null || backend.MARKET_MANAGER == null || !backend.MARKET_MANAGER.isSlave())
            return skip("slave only");
        if (!net.kroia.stockmarket.util.MultiServerUtils.canInteractWithStockMarket())
            return skip("not connected to the master");
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty())
            return skip("no online player");
        // Empty id: asks the master whether the slave this request comes through is trusted.
        boolean trusted = Boolean.TRUE.equals(await(
                backend.BANK_SYSTEM_API.getServerBankManager().getAsync().isSlaveServerTrustedAsync(""), SLAVE_WAIT_MS));
        if (flag && !trusted)
            return skip("untrusted slave: the master answers admin-flag reads empty (T-239); runs in the slave-trusted / spslave-trusted cells");
        ServerPlayer player = players.get(0);
        UUID uuid = player.getUUID();
        net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager master = backend.MARKET_MANAGER.getAsync();
        boolean before = trusted && Boolean.TRUE.equals(await(master.isStockmarketAdminAsync(uuid), SLAVE_WAIT_MS));
        try {
            if (trusted) {
                await(master.setStockmarketAdminModeAsync(uuid, flag), SLAVE_WAIT_MS);
                TestResult set = assertEquals("master applied the admin flag of " + player.getName().getString(), flag,
                        Boolean.TRUE.equals(await(master.isStockmarketAdminAsync(uuid), SLAVE_WAIT_MS)));
                if (!set.passed()) return set;
            }

            java.util.List<net.minecraft.network.chat.Component> messages = new java.util.ArrayList<>();
            CommandSourceStack source = player.createCommandSourceStack().withPermission(0).withSource(capture(messages));
            TestResult r = assertTrue("level-0 player passes the requires check",
                    StockMarketPermissions.canUseFlagGatedCommand(source, StockMarketPermissions.COMMAND_MANAGE));
            if (!r.passed()) return r;
            // SM-312: the slave's trust answer may still be in flight (or retrying) after a reconnect;
            // on timeout the assert below fails with the SlaveTrustCache diagnostic.
            pumpUntil(() -> net.kroia.stockmarket.util.SlaveTrustCache.get() != null, SLAVE_WAIT_MS);
            AtomicBoolean ran = new AtomicBoolean();
            CompletableFuture<Boolean> result = StockMarketCommands.runAsAdmin(source, () -> ran.set(true));
            if (!pumpUntil(result::isDone, SLAVE_WAIT_MS))
                return fail("admin check via the master did not complete");
            r = assertEquals("action ran (flag=" + flag + ")", flag, ran.get());
            if (!r.passed()) return r;
            r = assertEquals("result matches", flag, result.join());
            if (!r.passed()) return r;
            // SM-312: an untrusted slave names the missing trust instead of "admins only".
            String refusalKey = trusted ? "message.stockmarket.command_admin_only" : "message.stockmarket.command_slave_untrusted";
            boolean refused = messages.stream().anyMatch(c -> hasTranslationKey(c, refusalKey));
            List<String> sentKeys = new java.util.ArrayList<>();
            messages.forEach(c -> collectTranslationKeys(c, sentKeys));
            return assertEquals(refusalKey + " sent (flag=" + flag + ", trusted=" + trusted
                    + ", SlaveTrustCache=" + net.kroia.stockmarket.util.SlaveTrustCache.get()
                    + ", sent keys=" + sentKeys + ")", !flag, refused);
        } finally {
            if (trusted) try {
                await(master.setStockmarketAdminModeAsync(uuid, before), SLAVE_WAIT_MS);
            } catch (RuntimeException e) {
                backend.LOGGER.error("[UserTestSuite] SM-287: could not restore the admin flag of " + player.getName().getString(), e);
            }
        }
    }

    /**
     * SM-287: {@code /stockmarket op|deop|backup} stay on their node (op level 2): a level-0 source of the online
     * player cannot use them, a level-2 source can. Skips if a permission mod grants the player the node anyway
     * (then the source level does not decide). Same dispatcher on master and slave, so it runs on both.
     */
    private TestResult test_opDeopBackup_needLevel2() {
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> root = stockmarketNode();
        if (root == null) return skip("/stockmarket not registered");
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return skip("no online player");
        CommandSourceStack level0 = players.get(0).createCommandSourceStack().withPermission(0);
        for (String literal : new String[]{"op", "deop", "backup"}) {
            com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node = root.getChild(literal);
            TestResult r = assertNotNull("/stockmarket " + literal + " registered", node);
            if (!r.passed()) return r;
            boolean at0 = node.canUse(level0), at2 = node.canUse(level0.withPermission(2));
            if (at0 && at2)
                return skip("a permission mod grants the player /stockmarket " + literal);
            r = assertFalse("level-0 player cannot use /stockmarket " + literal, at0);
            if (!r.passed()) return r;
            r = assertTrue("level-2 player can use /stockmarket " + literal, at2);
            if (!r.passed()) return r;
        }
        return pass("op, deop and backup need level 2");
    }

    /** SM-287: the {@code /stockmarket manage} node is usable by a level-0 player, so it is sent to non-op clients. */
    private TestResult test_manageNode_visibleToLevel0Player() {
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> root = stockmarketNode();
        if (root == null) return skip("/stockmarket not registered");
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return skip("no online player");
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> manage = root.getChild("manage");
        TestResult r = assertNotNull("/stockmarket manage registered", manage);
        if (!r.passed()) return r;
        return assertTrue("level-0 player can use /stockmarket manage",
                manage.canUse(players.get(0).createCommandSourceStack().withPermission(0)));
    }

    private com.mojang.brigadier.tree.CommandNode<CommandSourceStack> stockmarketNode() {
        return getServer().getCommands().getDispatcher().getRoot().getChild("stockmarket");
    }

    /** @return a command output that records every message (success and failure) into {@code messages} */
    private static net.minecraft.commands.CommandSource capture(List<net.minecraft.network.chat.Component> messages) {
        return new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component component) { messages.add(component); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
    }

    /** @return true if {@code c} or one of its siblings is a translatable component with {@code key} */
    /** SM-312 diagnostics: every translation key in {@code c} and its siblings, in order. */
    private static void collectTranslationKeys(net.minecraft.network.chat.Component c, List<String> out) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)
            out.add(t.getKey());
        c.getSiblings().forEach(s -> collectTranslationKeys(s, out));
    }

    private static boolean hasTranslationKey(net.minecraft.network.chat.Component c, String key) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key))
            return true;
        return c.getSiblings().stream().anyMatch(s -> hasTranslationKey(s, key));
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
