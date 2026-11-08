package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.permission.LuckPermsBridge;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.AdminFlagSync;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@link AdminFlagSync} against a real LuckPerms (dev runs with {@code -Pwith_luckperms=true}); mirrors BankSystem's
 * AdminFlagSyncLuckPermsTests. Every test skips without LuckPerms or off the master. Uses fake StockMarket users
 * ({@code LpSyncTest_*}), temporary LuckPerms groups ({@code sm_test_*}) and, for the live listener test, the online
 * test player; all are restored in {@code finally} with the sync setting turned off first, so cleanup edits never
 * flow back into flags. Offline users are checked with {@link LuckPermsBridge#getEffective}, which runs after the
 * player's pending node writes, so no polling is needed.
 *
 * <p>Limit: the "still admin through a LuckPerms group" message goes to the executor's chat, which the server cannot
 * read back; the group-deop test asserts the restored flag only.
 */
public class AdminFlagSyncLuckPermsTests extends TestSuite {
    static final String GROUP_PREFIX = "sm_test_";
    private static final String NODE = StockMarketPermissions.ADMIN;
    private static final long TIMEOUT = 10_000;
    private static final UUID EXECUTOR = UUID.fromString("00000000-0000-0000-0000-00000000c182");

    private static StockMarketModBackend.ServerInstances backend;

    /** @param b the master's server instances (set by DevBootstrap on the master only; null elsewhere) */
    public static void setBackend(StockMarketModBackend.ServerInstances b) { backend = b; }

    private final List<UUID> fakeUsers = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private Boolean settingBefore;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.USER;
    }

    @Override
    public void registerTests() {
        addTest("lp_op_writes_node", lp(this::testOpWritesNode));
        addTest("lp_set_unset_flag_follows_live", lp(this::testFlagFollowsLive));
        addTest("lp_group_grant_deop_flag_stays", lp(this::testGroupGrantDeop));
        addTest("lp_user_node_only_deop_clears_flag", lp(this::testUserNodeDeop));
        addTest("lp_new_user_in_granted_group_becomes_admin", lp(this::testNewUserInGrantedGroup));
        addTest("lp_migration_runs_once", lp(this::testMigrationOnce));
        addTest("lp_setting_off_nothing_syncs", lp(this::testSettingOff));
    }

    /** Skips without LuckPerms or a master; afterwards deletes fake users/groups and restores the setting. */
    private Supplier<TestResult> lp(Supplier<TestResult> test) {
        return () -> {
            if (!LuckPermsBridge.isAvailable())
                return skip("LuckPerms not installed");
            if (getServer() == null || backend == null || backend.SERVER_SETTINGS == null || manager() == null)
                return skip("no master server");
            settingBefore = backend.SERVER_SETTINGS.UTILITIES.SYNC_ADMIN_FLAG_WITH_PERMISSION.get();
            try {
                return test.get();
            } finally {
                setSetting(false); // first: cleanup edits below must not flow back into flags
                for (UUID u : fakeUsers) {
                    await(Lp.deleteUser(u), TIMEOUT);
                    manager().removeUser(u);
                }
                for (String g : groups)
                    await(Lp.deleteGroup(g), TIMEOUT);
                fakeUsers.clear();
                names.clear();
                groups.clear();
                pump(500); // drain queued listener calls while the setting is off
                setSetting(Boolean.TRUE.equals(settingBefore));
            }
        };
    }

    // ------------------------------------------------------------------ tests

    /** {@code /stockmarket op} sets the flag and writes {@code stockmarket.admin=true}. */
    private TestResult testOpWritesNode() {
        setSetting(true);
        UUID u = fakeUser("Op", false);
        if (!handler().stockmarket_setStockmarketAdminMode_user(EXECUTOR, nameOf(u), true))
            return fail("op returned false");
        TestResult r = assertTrue("flag set by op", manager().isStockmarketAdmin(u));
        if (!r.passed())
            return r;
        return assertEquals("LuckPerms node after op", Optional.of(true), effective(u));
    }

    /** {@code lp user <p> permission set|unset stockmarket.admin}: the flag follows through the listener. */
    private TestResult testFlagFollowsLive() {
        ServerPlayer p = player();
        if (p == null)
            return skip("no online player (the listener only sees users LuckPerms has loaded)");
        UUID u = p.getUUID();
        if (!manager().userExists(u))
            return skip("online player is not a StockMarket user");
        boolean flagBefore = manager().isStockmarketAdmin(u);
        Optional<Boolean> nodeBefore = await(Lp.userNode(u), TIMEOUT);
        try {
            setSetting(false);
            await(Lp.unsetUserNode(u), TIMEOUT);
            pump(1000);
            manager().setStockmarketAdminMode(u, false);
            setSetting(true);
            await(Lp.setUserNode(u, true), TIMEOUT);
            if (!pumpUntil(() -> manager().isStockmarketAdmin(u), 5000))
                return fail("flag did not follow 'permission set " + NODE + " true'");
            await(Lp.unsetUserNode(u), TIMEOUT);
            if (!pumpUntil(() -> !manager().isStockmarketAdmin(u), 5000))
                return fail("flag did not follow 'permission unset " + NODE + "'");
            return pass("flag followed set and unset");
        } finally {
            setSetting(false);
            if (nodeBefore.isPresent())
                await(Lp.setUserNode(u, nodeBefore.get()), TIMEOUT);
            else
                await(Lp.unsetUserNode(u), TIMEOUT);
            pump(500);
            manager().setStockmarketAdminMode(u, flagBefore);
        }
    }

    /** Deop while a LuckPerms group grants the node: the group wins, the flag is set back. */
    private TestResult testGroupGrantDeop() {
        setSetting(true);
        UUID u = fakeUser("GroupDeop", true);
        await(Lp.addToGroup(u, group()), TIMEOUT);
        if (!Optional.of(true).equals(effective(u)))
            return fail("group grant not effective");
        pump(500);
        if (!handler().stockmarket_setStockmarketAdminMode_user(EXECUTOR, nameOf(u), false))
            return fail("deop returned false");
        // deop clears the flag at once, then the group-wins follow-up sets it back
        if (!pumpUntil(() -> manager().isStockmarketAdmin(u), 5000))
            return fail("flag not restored after deop while a LuckPerms group grants " + NODE);
        pump(1000);
        TestResult r = assertTrue("flag stays after the follow-up settled", manager().isStockmarketAdmin(u));
        if (!r.passed())
            return r;
        return pass("flag stays (executor message not observable server-side, see class docs)");
    }

    /** Op then deop without any group: deop removes the user node and the flag stays cleared. */
    private TestResult testUserNodeDeop() {
        setSetting(true);
        UUID u = fakeUser("NodeDeop", false);
        handler().stockmarket_setStockmarketAdminMode_user(EXECUTOR, nameOf(u), true);
        if (!Optional.of(true).equals(effective(u)))
            return fail("op did not write the node");
        if (!handler().stockmarket_setStockmarketAdminMode_user(EXECUTOR, nameOf(u), false))
            return fail("deop returned false");
        TestResult r = assertEquals("node after deop", Optional.of(false), effective(u));
        if (!r.passed())
            return r;
        pump(1500); // a wrong group-wins follow-up would set the flag back in this window
        return assertFalse("flag cleared after deop (user node only)", manager().isStockmarketAdmin(u));
    }

    /** A user created while a LuckPerms group already grants the node gets the flag. */
    private TestResult testNewUserInGrantedGroup() {
        setSetting(true);
        UUID u = UUID.randomUUID();
        fakeUsers.add(u);
        await(Lp.addToGroup(u, group()), TIMEOUT);
        pump(1000); // listener for a not-yet StockMarket user: must be harmless
        manager().onPlayerJoin(u, name("NewUser"));
        return assertTrue("new user in a granted group becomes admin",
                pumpUntil(() -> manager().isStockmarketAdmin(u), 5000));
    }

    /**
     * The migration pushes flagged admins once and writes the marker; a second run (marker present) pushes nothing.
     * Other flagged admins of the world are cleared for the test and restored afterwards, so nothing real reaches
     * LuckPerms.
     */
    private TestResult testMigrationOnce() {
        Path marker = backend.DATA_MANAGER.getAbsoluteSavePath(AdminFlagSync.MIGRATION_MARKER);
        List<UUID> realAdmins = new ArrayList<>(manager().getStockmarketAdminUUIDs());
        byte[] backup = null;
        try {
            for (UUID id : realAdmins)
                manager().setStockmarketAdminMode(id, false);
            if (Files.exists(marker)) {
                backup = Files.readAllBytes(marker);
                Files.delete(marker);
            }
            UUID first = fakeUser("Migrate1", true);
            setSetting(true);
            AdminFlagSync.migrateIfNeeded();
            if (!pumpUntil(() -> Files.exists(marker), TIMEOUT))
                return fail("migration wrote no marker");
            TestResult r = assertEquals("flagged admin pushed by the migration", Optional.of(true), effective(first));
            if (!r.passed())
                return r;
            UUID second = fakeUser("Migrate2", true);
            AdminFlagSync.migrateIfNeeded();
            pump(1500);
            return assertEquals("second run (marker present) pushes nothing", Optional.of(false), effective(second));
        } catch (Exception e) {
            return fail("marker IO: " + e);
        } finally {
            try {
                if (backup != null)
                    Files.write(marker, backup);
                else
                    Files.deleteIfExists(marker);
            } catch (Exception e) {
                // nothing else to do; the next run starts from whatever is left
            }
            for (UUID id : realAdmins)
                manager().setStockmarketAdminMode(id, true);
        }
    }

    /** Setting off: op writes no node, a LuckPerms grant leaves the flag, a new granted user stays non-admin. */
    private TestResult testSettingOff() {
        setSetting(false);
        UUID op = fakeUser("OffOp", false);
        handler().stockmarket_setStockmarketAdminMode_user(EXECUTOR, nameOf(op), true);
        pump(1500);
        TestResult r = assertEquals("op with setting off writes no node", Optional.of(false), effective(op));
        if (!r.passed())
            return r;
        UUID follow = fakeUser("OffFollow", false);
        await(LuckPermsBridge.setNode(follow, NODE, true), TIMEOUT);
        pump(1500);
        r = assertFalse("LuckPerms grant with setting off leaves the flag", manager().isStockmarketAdmin(follow));
        if (!r.passed())
            return r;
        UUID fresh = UUID.randomUUID();
        fakeUsers.add(fresh);
        await(Lp.addToGroup(fresh, group()), TIMEOUT);
        manager().onPlayerJoin(fresh, name("OffNew"));
        pump(1500);
        return assertFalse("new user in a granted group with setting off stays non-admin", manager().isStockmarketAdmin(fresh));
    }

    // ------------------------------------------------------------------ helpers

    private static ServerMarketManager manager() {
        return backend == null || backend.MARKET_MANAGER == null ? null : backend.MARKET_MANAGER.getServerMarketManager();
    }

    private static ServerStockMarketCommandHandler handler() {
        return new ServerStockMarketCommandHandler();
    }

    private static void setSetting(boolean on) {
        backend.SERVER_SETTINGS.UTILITIES.SYNC_ADMIN_FLAG_WITH_PERMISSION.set(on);
    }

    /** Effective node value; for offline users it waits for their pending node writes (ModUtilities bridge). */
    private Optional<Boolean> effective(UUID u) {
        return await(LuckPermsBridge.getEffective(u, NODE), TIMEOUT);
    }

    /** Creates a fake StockMarket user with the given flag; removed again after the test. */
    private UUID fakeUser(String tag, boolean admin) {
        UUID u = UUID.randomUUID();
        fakeUsers.add(u);
        names.put(u, name(tag));
        manager().onPlayerJoin(u, names.get(u));
        manager().setStockmarketAdminMode(u, admin);
        return u;
    }

    /** The fake user's StockMarket name (op/deop look users up by name). */
    private String nameOf(UUID u) {
        return names.get(u);
    }

    private static String name(String tag) {
        return "LpSyncTest_" + tag + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    /** Creates a temporary group granting {@link StockMarketPermissions#ADMIN}; deleted after the test. */
    private String group() {
        String g = GROUP_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        groups.add(g);
        await(Lp.createGroupWith(g, NODE), TIMEOUT);
        return g;
    }

    private @Nullable ServerPlayer player() {
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    /** Direct LuckPerms API calls the bridge does not offer. Only loaded when LuckPerms is available. */
    static final class Lp {
        static LuckPerms api() {
            return LuckPermsProvider.get();
        }

        /** The user's own global {@code stockmarket.admin} entry, if any. */
        static CompletableFuture<Optional<Boolean>> userNode(UUID u) {
            return api().getUserManager().loadUser(u).thenApply(user -> user.getNodes().stream()
                    .filter(n -> n.getKey().equalsIgnoreCase(NODE) && n.getContexts().isEmpty())
                    .findFirst().map(Node::getValue));
        }

        /** Like {@code lp user <u> permission set stockmarket.admin <value>}. */
        static CompletableFuture<Void> setUserNode(UUID u, boolean value) {
            return api().getUserManager().modifyUser(u, user -> {
                user.data().clear(n -> n.getKey().equalsIgnoreCase(NODE) && n.getContexts().isEmpty());
                user.data().add(Node.builder(NODE).value(value).build());
            });
        }

        /** Like {@code lp user <u> permission unset stockmarket.admin}. */
        static CompletableFuture<Void> unsetUserNode(UUID u) {
            return api().getUserManager().modifyUser(u, user ->
                    user.data().clear(n -> n.getKey().equalsIgnoreCase(NODE) && n.getContexts().isEmpty()));
        }

        static CompletableFuture<Void> createGroupWith(String group, String node) {
            GroupManager gm = api().getGroupManager();
            return gm.createAndLoadGroup(group).thenCompose(g -> {
                g.data().add(Node.builder(node).build());
                return gm.saveGroup(g);
            });
        }

        static CompletableFuture<Void> addToGroup(UUID u, String group) {
            return api().getUserManager().modifyUser(u, user -> user.data().add(InheritanceNode.builder(group).build()));
        }

        static CompletableFuture<Void> deleteGroup(String group) {
            GroupManager gm = api().getGroupManager();
            return gm.loadGroup(group).thenCompose(g -> g.map(gm::deleteGroup)
                    .orElseGet(() -> CompletableFuture.completedFuture(null)));
        }

        static CompletableFuture<Void> deleteUser(UUID u) {
            return api().getUserManager().deletePlayerData(u);
        }
    }
}
