package net.kroia.stockmarket.util;

import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.permission.LuckPermsBridge;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opt-in two-way sync between the per-user StockMarket admin flag and the LuckPerms node
 * {@link StockMarketPermissions#ADMIN} (setting {@code Utilities.SYNC_ADMIN_FLAG_WITH_PERMISSION}).
 * Mirrors BankSystem's {@code AdminFlagSync}. Without the setting or without LuckPerms nothing here does anything.
 * <ul>
 *   <li>{@code /stockmarket op|deop} (master only): sets the flag, then writes the node ({@link #pushToLuckPerms}).
 *       Deop only removes the user's own node; if a LuckPerms group still grants it, the group wins: the flag is
 *       set back at once and the executor is told.</li>
 *   <li>New StockMarket user on the master ({@link #onUserAdded}): an effective grant sets the flag.</li>
 *   <li>LuckPerms node change ({@link #onNodeChanged}, server thread): the master writes the flag directly;
 *       a trusted slave forwards {@code SyncAdminFlagFromPermission} to the master, which applies it only from a
 *       trusted slave while its own setting is on (AsyncMarketManager); an untrusted slave ignores it.
 *       The apply path never calls setNode, so the sync cannot loop.</li>
 *   <li>One-time migration on the master ({@link #migrateIfNeeded}): pushes node=true for every flagged admin, then
 *       writes {@link #MIGRATION_MARKER} into the world's StockMarket data folder. Never pushes false.</li>
 * </ul>
 * Slaves keep compile-time default settings (only the master loads settings.json), so unlike BankSystem a slave
 * cannot read the setting itself: it forwards whenever it is trusted and the master's setting decides.
 */
public final class AdminFlagSync {
    private AdminFlagSync() {}

    /** What a LuckPerms node change does on this server. */
    public enum ListenerAction { WRITE_LOCAL, FORWARD, IGNORE }

    /** What follows a deop once the node was removed. */
    public enum DeopFollowUp { RESTORE_AND_NOTE, NONE }

    /** Marker file in the world's {@code data/StockMarket} folder; its presence means the migration already ran. */
    public static final String MIGRATION_MARKER = "AdminFlagLuckPermsMigrated.marker";

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    /** @param backend the server instances of the current server session (null-safe for every method here) */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) { BACKEND_INSTANCES = backend; }

    private static volatile boolean migrating = false;

    // ---- pure decisions ----

    /**
     * @param settingOn   this server's SYNC_ADMIN_FLAG_WITH_PERMISSION
     * @param lpAvailable LuckPerms is installed and up
     * @return true if op/deop must mirror the flag into LuckPerms
     */
    public static boolean shouldPushToLuckPerms(boolean settingOn, boolean lpAvailable) {
        return settingOn && lpAvailable;
    }

    /**
     * @param settingOn      the deciding setting (on a slave: true, the master gates with its own value)
     * @param isMaster       this server owns the user data
     * @param isTrustedSlave this slave is trusted by the master
     * @return what a LuckPerms node change does on this server
     */
    public static ListenerAction listenerAction(boolean settingOn, boolean isMaster, boolean isTrustedSlave) {
        if (!settingOn)
            return ListenerAction.IGNORE;
        if (isMaster)
            return ListenerAction.WRITE_LOCAL;
        return isTrustedSlave ? ListenerAction.FORWARD : ListenerAction.IGNORE;
    }

    /**
     * @param effective the effective node value after deop removed the user's own node
     * @return RESTORE_AND_NOTE if a LuckPerms group still grants the node (group wins)
     */
    public static DeopFollowUp deopFollowUp(Optional<Boolean> effective) {
        return effective.orElse(false) ? DeopFollowUp.RESTORE_AND_NOTE : DeopFollowUp.NONE;
    }

    /**
     * @param effective the effective node value of a brand-new user (whose flag is false)
     * @return true if the flag must be set; false or unknown leaves it as it is
     */
    public static boolean newUserApply(Optional<Boolean> effective) {
        return effective.orElse(false);
    }

    /**
     * @param settingOn   the master's setting
     * @param lpAvailable LuckPerms is installed and up
     * @param migrated    the migration marker exists
     * @return true if the one-time admin push must run now
     */
    public static boolean shouldMigrate(boolean settingOn, boolean lpAvailable, boolean migrated) {
        return settingOn && lpAvailable && !migrated;
    }

    /**
     * Master-side gate for a sync write forwarded by a slave.
     *
     * @param fromSlave       the request came from a slave (non-empty slave ID)
     * @param fromPlayer      the request carries a player sender
     * @param slaveTrusted    the master trusts that slave
     * @param masterSettingOn the master's own SYNC_ADMIN_FLAG_WITH_PERMISSION
     * @return true if the master applies the write
     */
    public static boolean acceptSlaveSync(boolean fromSlave, boolean fromPlayer, boolean slaveTrusted, boolean masterSettingOn) {
        return fromSlave && !fromPlayer && slaveTrusted && masterSettingOn;
    }

    // ---- runtime ----

    /** @return true if this server's own {@code SYNC_ADMIN_FLAG_WITH_PERMISSION} setting is on (always false on slaves) */
    public static boolean isEnabled() {
        StockMarketModBackend.ServerInstances b = BACKEND_INSTANCES;
        return b != null && b.SERVER_SETTINGS != null
                && Boolean.TRUE.equals(b.SERVER_SETTINGS.UTILITIES.SYNC_ADMIN_FLAG_WITH_PERMISSION.get());
    }

    /** @return the master's user store, or null on a slave / before setup */
    private static ServerMarketManager masterManager() {
        StockMarketModBackend.ServerInstances b = BACKEND_INSTANCES;
        return b == null || b.MARKET_MANAGER == null ? null : b.MARKET_MANAGER.getServerMarketManager();
    }

    /**
     * Registers the LuckPerms listener once at mod init. No-op without LuckPerms. The setting is checked inside
     * the callback, so turning it on or off at runtime needs no re-registration.
     */
    public static void init() {
        LuckPermsBridge.addNodeChangeListener(StockMarketPermissions.ADMIN, AdminFlagSync::onNodeChanged);
    }

    /** LuckPerms listener (server thread): applies the effective node value to the flag. */
    private static void onNodeChanged(UUID uuid, boolean admin) {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null)
            return;
        applyFlag(uuid, admin, "from LuckPerms");
    }

    /**
     * Writes the flag without touching LuckPerms (loop-safe). Master: directly, if its setting is on. Slave: forwarded
     * to the master if the master trusts this slave; the master checks trust and its own setting again.
     * Call on the server thread.
     */
    private static void applyFlag(UUID uuid, boolean admin, String reason) {
        ServerMarketManager manager = masterManager();
        if (manager != null) {
            if (listenerAction(isEnabled(), true, false) == ListenerAction.WRITE_LOCAL)
                writeLocal(manager, uuid, admin, reason);
            return;
        }
        if (listenerAction(true, false, SlaveTrustCache.getOrFalse()) != ListenerAction.FORWARD) {
            BACKEND_INSTANCES.LOGGER.debug("[AdminFlagSync] Ignored StockMarket admin flag change for " + uuid
                    + " (" + reason + "): this slave is not trusted by the master");
            return;
        }
        if (!(BACKEND_INSTANCES.MARKET_MANAGER.getAsync() instanceof AsyncMarketManager async))
            return;
        async.syncAdminFlagFromPermissionAsync(uuid, admin).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok))
                BACKEND_INSTANCES.LOGGER.info("[AdminFlagSync] Forwarded StockMarket admin flag of "
                        + ServerStockMarketCommandHandler.tryGetPlayerName(uuid) + " = " + admin + " (" + reason + ") to the master");
            else
                BACKEND_INSTANCES.LOGGER.debug("[AdminFlagSync] Master did not apply admin flag sync for " + uuid
                        + " (setting off on the master, untrusted, or unknown user)");
        });
    }

    /**
     * Master only: sets the flag if it differs (the echo of our own setNode is already equal, so loop-safe).
     *
     * @return true if the flag now has the requested value (false: unknown user)
     */
    public static boolean writeLocal(IServerMarketManager manager, UUID uuid, boolean admin, String reason) {
        if (manager.isStockmarketAdmin(uuid) == admin)
            return true;
        boolean ok = manager.setStockmarketAdminMode(uuid, admin);
        if (ok && BACKEND_INSTANCES != null)
            BACKEND_INSTANCES.LOGGER.info("[AdminFlagSync] StockMarket admin flag of "
                    + ServerStockMarketCommandHandler.tryGetPlayerName(uuid) + " set to " + admin + " (" + reason + ")");
        return ok;
    }

    /**
     * After {@code /stockmarket op|deop} changed the flag: mirror it into LuckPerms (setting on + LuckPerms only)
     * and report a failure to the executor. Deop removes only the user's own node; if a LuckPerms group still
     * grants it ("group wins"), the flag is set back at once and the executor is told.
     *
     * @param executor   the command sender
     * @param player     the user whose flag changed
     * @param playerName display name for messages
     * @param admin      the new flag value
     */
    public static void pushToLuckPerms(UUID executor, UUID player, String playerName, boolean admin) {
        if (!shouldPushToLuckPerms(isEnabled(), LuckPermsBridge.isAvailable()))
            return;
        CompletableFuture<Boolean> write = LuckPermsBridge.setNode(player, StockMarketPermissions.ADMIN, admin);
        write.thenAccept(ok -> {
            if (!ok)
                onServerThread(() -> ServerStockMarketCommandHandler.sendMessage(executor, Component.literal(
                        "Failed to write the LuckPerms node '" + StockMarketPermissions.ADMIN + "' for " + playerName
                        + " (see the server log). The StockMarket flag was changed, but the next LuckPerms change of that node overwrites it.")));
        });
        if (admin)
            return;
        // setNode invalidates the user's cache before completing, so this lookup sees the post-write state.
        write.thenCompose(ok -> ok ? LuckPermsBridge.getEffective(player, StockMarketPermissions.ADMIN)
                        : CompletableFuture.completedFuture(Optional.<Boolean>empty()))
                .thenAccept(effective -> onServerThread(() -> {
                    if (deopFollowUp(effective) != DeopFollowUp.RESTORE_AND_NOTE)
                        return;
                    applyFlag(player, true, "still granted by a LuckPerms group after deop");
                    ServerStockMarketCommandHandler.sendMessage(executor,
                            Component.translatable("message.stockmarket.still_admin_via_luckperms_group", playerName));
                }));
    }

    /**
     * Master only, called when StockMarket creates a user: a player who already has the node in LuckPerms (e.g. via
     * a group) gets the flag. Closes the gap where the listener fired before the user existed.
     *
     * @param uuid the new user
     */
    public static void onUserAdded(UUID uuid) {
        if (masterManager() == null || !isEnabled() || !LuckPermsBridge.isAvailable())
            return;
        LuckPermsBridge.getEffective(uuid, StockMarketPermissions.ADMIN).thenAccept(effective -> onServerThread(() -> {
            if (newUserApply(effective))
                applyFlag(uuid, true, "LuckPerms grant of new user");
        }));
    }

    /** Master only: one-time push of every flagged admin to LuckPerms. Safe to call repeatedly. */
    public static void migrateIfNeeded() {
        ServerMarketManager manager = masterManager();
        if (manager == null || migrating || BACKEND_INSTANCES.DATA_MANAGER == null)
            return;
        Path marker = BACKEND_INSTANCES.DATA_MANAGER.getAbsoluteSavePath(MIGRATION_MARKER);
        if (!shouldMigrate(isEnabled(), LuckPermsBridge.isAvailable(), Files.exists(marker)))
            return;
        migrating = true;
        StockMarketLogger logger = BACKEND_INSTANCES.LOGGER;
        List<CompletableFuture<Boolean>> pushes = new ArrayList<>();
        for (UUID uuid : manager.getStockmarketAdminUUIDs())
            pushes.add(LuckPermsBridge.setNode(uuid, StockMarketPermissions.ADMIN, true));
        CompletableFuture.allOf(pushes.toArray(new CompletableFuture[0])).whenComplete((v, e) -> {
            migrating = false;
            if (e != null || !pushes.stream().allMatch(CompletableFuture::join)) {
                logger.warn("[AdminFlagSync] Migration of StockMarket admins to LuckPerms node '"
                        + StockMarketPermissions.ADMIN + "' failed for some players; retried at the next server start");
                return;
            }
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "StockMarket admins pushed to LuckPerms node " + StockMarketPermissions.ADMIN
                        + ". Delete this file to run the migration again.\n");
                logger.info("[AdminFlagSync] Migrated " + pushes.size()
                        + " StockMarket admin(s) to LuckPerms node '" + StockMarketPermissions.ADMIN + "'");
            } catch (Exception ex) {
                logger.error("[AdminFlagSync] Could not write migration marker " + marker, ex);
            }
        });
    }

    private static void onServerThread(Runnable r) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server != null)
            server.execute(r);
    }
}
