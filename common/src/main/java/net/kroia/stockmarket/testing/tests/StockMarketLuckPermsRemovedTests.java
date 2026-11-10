package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.permission.LuckPermsBridge;
import net.kroia.modutilities.testing.LuckPermsPhase;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.modutilities.testing.autotest.AutoTest;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.AdminFlagSync;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "LuckPerms removed from a save" (tools/autotest {@code --luckperms removed}, see {@link LuckPermsPhase}); mirrors
 * BankSystem's BankSystemLuckPermsRemovedTests.
 * Phase 1 (LuckPerms present, master, autotest runs only, it leaves the sync setting on): turns the sync setting on,
 * runs the migration (marker), flags a fake admin, saves and records all of it. Phase 2 (same world, LuckPerms gone):
 * checks it survived and that op/deop, admin checks and {@link AdminFlagSync} work without LuckPerms. Phase-2 tests
 * skip outside phase 2.
 */
public class StockMarketLuckPermsRemovedTests extends TestSuite {
    private static final String MOD = "stockmarket";
    private static final UUID EXECUTOR = UUID.fromString("00000000-0000-0000-0000-00000000c183");

    private static StockMarketModBackend.ServerInstances backend;

    /** @param b the master's server instances (set by DevBootstrap on the master only; null elsewhere) */
    public static void setBackend(StockMarketModBackend.ServerInstances b) { backend = b; }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.USER;
    }

    @Override
    public void registerTests() {
        addTest("lprm_phase1_record_state", this::recordPhase1);
        addTest("lprm_clean_start", phase2(this::testCleanStart));
        addTest("lprm_setting_and_marker_kept", phase2(this::testSettingAndMarkerKept));
        addTest("lprm_admin_flag_kept", phase2(this::testAdminFlagKept));
        addTest("lprm_op_deop_toggle_flag", phase2(this::testOpDeop));
        addTest("lprm_admin_checks_fall_back", phase2(this::testFallback));
        addTest("lprm_admin_flag_sync_noop", phase2(this::testSyncNoop));
    }

    /** Runs the test only in phase 2 on the master; skips otherwise. */
    private Supplier<TestResult> phase2(Supplier<TestResult> test) {
        return () -> {
            MinecraftServer s = getServer();
            if (s == null || manager() == null)
                return skip("no master server");
            if (!LuckPermsPhase.isPhase2(s))
                return skip(LuckPermsPhase.phase1Exists(s) ? "LuckPerms is installed" : "world has no LuckPerms phase-1 file");
            return test.get();
        };
    }

    // ------------------------------------------------------------------ phase 1

    private TestResult recordPhase1() {
        MinecraftServer s = getServer();
        if (!LuckPermsBridge.isAvailable())
            return skip("LuckPerms not installed");
        if (s == null || manager() == null)
            return skip("no master server");
        if (!AutoTest.isEnabled())
            return skip("autotest runs only: phase 1 leaves the sync setting on");
        backend.SERVER_SETTINGS.INTEGRATIONS.SYNC_ADMIN_FLAG_WITH_PERMISSION.set(true);
        UUID admin = UUID.randomUUID();
        manager().onPlayerJoin(admin, "LpRemovedAdmin");
        manager().setStockmarketAdminMode(admin, true);
        Path marker = marker();
        AdminFlagSync.migrateIfNeeded();
        if (!pumpUntil(() -> Files.exists(marker), 10_000))
            return fail("migration wrote no marker");
        if (!backend.DATA_MANAGER.save(s))
            return fail("save failed");
        LuckPermsPhase.record(s, MOD, "syncSetting", "on");
        LuckPermsPhase.record(s, MOD, "migrationMarker", "1");
        LuckPermsPhase.record(s, MOD, "admin." + admin, "true");
        return assertEquals("phase file readable", "on", LuckPermsPhase.expected(s, MOD, "syncSetting"));
    }

    // ------------------------------------------------------------------ phase 2

    /** Reaching this at all means the server started; check the backend came up as master, without LuckPerms. */
    private TestResult testCleanStart() {
        TestResult r = assertFalse("LuckPermsBridge.isAvailable()", LuckPermsBridge.isAvailable());
        if (!r.passed())
            return r;
        r = assertTrue("settings and data manager loaded", backend.SERVER_SETTINGS != null && backend.DATA_MANAGER != null);
        if (!r.passed())
            return r;
        return assertFalse("phase 1 recorded StockMarket state", LuckPermsPhase.expectations(getServer(), MOD).isEmpty());
    }

    private TestResult testSettingAndMarkerKept() {
        TestResult r = assertEquals("phase 1 recorded the setting", "on", LuckPermsPhase.expected(getServer(), MOD, "syncSetting"));
        if (!r.passed())
            return r;
        r = assertTrue("SYNC_ADMIN_FLAG_WITH_PERMISSION still on", AdminFlagSync.isEnabled());
        if (!r.passed())
            return r;
        return assertTrue("migration marker still present", Files.exists(marker()));
    }

    private TestResult testAdminFlagKept() {
        int n = 0;
        for (Map.Entry<String, String> e : LuckPermsPhase.expectations(getServer(), MOD).entrySet()) {
            if (!e.getKey().startsWith("admin."))
                continue;
            UUID u = UUID.fromString(e.getKey().substring("admin.".length()));
            TestResult r = assertEquals("admin flag of " + u, Boolean.parseBoolean(e.getValue()), manager().isStockmarketAdmin(u));
            if (!r.passed())
                return r;
            n++;
        }
        return n == 0 ? fail("phase 1 recorded no admin") : pass(n + " admin flag(s) kept");
    }

    /** {@code /stockmarket op|deop} still toggles the flag without LuckPerms (setting still on). */
    private TestResult testOpDeop() {
        UUID u = UUID.randomUUID();
        String name = "LpRemovedOpTest";
        manager().onPlayerJoin(u, name);
        try {
            ServerStockMarketCommandHandler h = new ServerStockMarketCommandHandler();
            if (!h.stockmarket_setStockmarketAdminMode_user(EXECUTOR, name, true))
                return fail("op returned false");
            pump(500);
            TestResult r = assertTrue("op sets the flag", manager().isStockmarketAdmin(u));
            if (!r.passed())
                return r;
            if (!h.stockmarket_setStockmarketAdminMode_user(EXECUTOR, name, false))
                return fail("deop returned false");
            pump(500);
            return assertFalse("deop clears the flag", manager().isStockmarketAdmin(u));
        } catch (Exception e) {
            return fail("op/deop threw " + e);
        } finally {
            manager().removeUser(u);
        }
    }

    /** The kept flag still reads as admin; node checks of an online player equal the op-level fallback. */
    private TestResult testFallback() {
        for (Map.Entry<String, String> e : LuckPermsPhase.expectations(getServer(), MOD).entrySet()) {
            if (!e.getKey().startsWith("admin."))
                continue;
            UUID u = UUID.fromString(e.getKey().substring("admin.".length()));
            TestResult r = assertTrue("isStockmarketAdmin falls back to the flag for " + u, manager().isStockmarketAdmin(u));
            if (!r.passed())
                return r;
        }
        ServerPlayer p = player();
        if (p == null)
            return pass("flag fallback ok (no online player, node checks not compared)");
        for (String node : StockMarketPermissions.ALL_NODES) {
            boolean expected = PermissionCompat.hasPermission(p, StockMarketPermissions.FALLBACK_LEVEL);
            if (expected != PermissionCompat.hasPermission(p, node, StockMarketPermissions.FALLBACK_LEVEL))
                return TestResult.fail("", node + " equals the op-level check", String.valueOf(expected), String.valueOf(!expected));
            if (expected != PermissionCompat.hasPermission(p.createCommandSourceStack(), node, StockMarketPermissions.FALLBACK_LEVEL))
                return TestResult.fail("", node + " (source) equals the op-level check", String.valueOf(expected), String.valueOf(!expected));
        }
        return pass("flag and op-level fallback ok");
    }

    /** Every AdminFlagSync entry point is a harmless no-op without LuckPerms: marker and flags unchanged. */
    private TestResult testSyncNoop() {
        Path marker = marker();
        try {
            String markerBefore = Files.readString(marker);
            Map<String, String> exp = LuckPermsPhase.expectations(getServer(), MOD);
            AdminFlagSync.init();
            AdminFlagSync.migrateIfNeeded();
            AdminFlagSync.onUserAdded(UUID.randomUUID());
            for (String key : exp.keySet()) {
                if (!key.startsWith("admin."))
                    continue;
                UUID u = UUID.fromString(key.substring("admin.".length()));
                AdminFlagSync.pushToLuckPerms(EXECUTOR, u, "LpRemovedAdmin", false);
            }
            pump(1000);
            TestResult r = assertEquals("marker unchanged", markerBefore, Files.readString(marker));
            if (!r.passed())
                return r;
            return testAdminFlagKept();
        } catch (Exception e) {
            return fail("AdminFlagSync threw " + e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ServerMarketManager manager() {
        return backend == null || backend.MARKET_MANAGER == null ? null : backend.MARKET_MANAGER.getServerMarketManager();
    }

    private static Path marker() {
        return backend.DATA_MANAGER.getAbsoluteSavePath(AdminFlagSync.MIGRATION_MARKER);
    }

    private ServerPlayer player() {
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }
}
