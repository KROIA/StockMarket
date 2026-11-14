package net.kroia.stockmarket.util;

import net.kroia.modutilities.PermissionCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Permission nodes for StockMarket admin functions (Task T-168, mirrors BankSystem's {@code BankSystemPermissions}).
 * Permission mods such as LuckPerms can grant or deny them. Without one, a node falls back to the vanilla op level
 * returned by {@link #fallbackLevel(String)}: {@link #FALLBACK_LEVEL} for most nodes, {@link #PLAYER_LEVEL} for the
 * {@link #FLAG_GATED_NODES}. Always pass {@link #fallbackLevel(String)} at the call site too: on NeoForge the level
 * given at registration wins.
 * <p>
 * <b>Flag-gated commands (SM-266):</b> {@code manage}, {@code preset}, {@code news} admin subcommands,
 * {@code open|close <market>} and {@code <market> remove} are guarded by the per-user StockMarket admin flag
 * ({@code /stockmarket op}), not by server op. Their nodes default to "everyone" for players, so a permission mod can
 * only DENY them (hide the command even from flagged admins). A player additionally needs the admin flag at execution
 * time. Non-player sources (console, command blocks) have no flag and keep the op-level check
 * ({@link #FALLBACK_LEVEL}). See {@link #canUseFlagGatedCommand(CommandSourceStack, String)}.
 */
public final class StockMarketPermissions {
    private StockMarketPermissions() {}

    /** Vanilla op level required for every non flag-gated node when no permission mod decides. */
    public static final int FALLBACK_LEVEL = 2;
    /** Fallback level of the {@link #FLAG_GATED_NODES}: every player passes the node, the admin flag decides. */
    public static final int PLAYER_LEVEL = 0;

    /** {@code /stockmarket op} and {@code /stockmarket deop} (sets the per-user StockMarket admin flag). Op level 2. */
    public static final String COMMAND_OP = "stockmarket.admin.command.op";
    /** {@code /stockmarket manage}. Flag-gated: every player by default, deny hides it; the admin flag is required. */
    public static final String COMMAND_MANAGE = "stockmarket.admin.command.manage";
    /** {@code /stockmarket preset ...}. Flag-gated: every player by default, deny hides it; the admin flag is required. */
    public static final String COMMAND_PRESET = "stockmarket.admin.command.preset";
    /** All {@code /stockmarket news ...} admin subcommands. Flag-gated like {@link #COMMAND_PRESET}. */
    public static final String COMMAND_NEWS = "stockmarket.admin.command.news";
    /** {@code /stockmarket backup ...}. Op level 2. */
    public static final String COMMAND_BACKUP = "stockmarket.admin.command.backup";
    /** {@code /stockmarket open|close <market>} and {@code /stockmarket <market> remove}. Flag-gated like {@link #COMMAND_PRESET}. */
    public static final String COMMAND_MARKET = "stockmarket.admin.command.market";
    /** {@code /stockmarket exportrecipes} (dev builds only). Op level 2. */
    public static final String COMMAND_EXPORTRECIPES = "stockmarket.admin.command.exportrecipes";
    /** {@code /stockmarket devTestScreen} (dev builds only). Op level 2. */
    public static final String COMMAND_DEVTEST = "stockmarket.admin.command.devtest";
    /** Receiving admin chat notifications about settings changes made by other admins. Op level 2. */
    public static final String NOTIFY = "stockmarket.admin.notify";

    /**
     * Source of the per-user StockMarket admin flag while {@code Integrations.SYNC_ADMIN_FLAG_WITH_PERMISSION} is on
     * (see {@link AdminFlagSync}). Not covered by {@code stockmarket.admin.*}; grant it on its own.
     */
    public static final String ADMIN = "stockmarket.admin";

    /** Every node, used by {@link #register()} and the registration test. */
    public static final String[] ALL_NODES = {COMMAND_OP, COMMAND_MANAGE, COMMAND_PRESET, COMMAND_NEWS,
            COMMAND_BACKUP, COMMAND_MARKET, COMMAND_EXPORTRECIPES, COMMAND_DEVTEST, NOTIFY, ADMIN};

    /** Nodes of the commands guarded by the StockMarket admin flag instead of server op (SM-266). */
    public static final Set<String> FLAG_GATED_NODES = Set.of(COMMAND_MANAGE, COMMAND_PRESET, COMMAND_NEWS, COMMAND_MARKET);

    /**
     * @param node a node from {@link #ALL_NODES}
     * @return the vanilla op level the node falls back to without a permission mod
     */
    public static int fallbackLevel(String node) {
        return FLAG_GATED_NODES.contains(node) ? PLAYER_LEVEL : FALLBACK_LEVEL;
    }

    /**
     * Brigadier {@code requires} check of a flag-gated command (SM-266). Players pass unless a permission mod denies
     * the node; the admin flag is checked when the command runs. Non-player sources need op level
     * {@link #FALLBACK_LEVEL}, as before.
     *
     * @param source the command source
     * @param node   one of {@link #FLAG_GATED_NODES}
     * @return true if the source may see and run the command
     */
    public static boolean canUseFlagGatedCommand(CommandSourceStack source, String node) {
        if (source.getEntity() instanceof ServerPlayer)
            return PermissionCompat.hasPermission(source, node, PLAYER_LEVEL);
        return PermissionCompat.hasPermission(source, FALLBACK_LEVEL);
    }

    /** Registers all nodes with ModUtilities. Must run during mod init, before the server starts (NeoForge). */
    public static void register() {
        for (String node : ALL_NODES) {
            PermissionCompat.registerNode(node, fallbackLevel(node));
        }
    }
}
