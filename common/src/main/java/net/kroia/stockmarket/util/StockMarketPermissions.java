package net.kroia.stockmarket.util;

import net.kroia.modutilities.PermissionCompat;

/**
 * Permission nodes for StockMarket admin functions (Task T-168, mirrors BankSystem's {@code BankSystemPermissions}).
 * Permission mods such as LuckPerms can grant or deny them; without one every node falls back to vanilla
 * op level {@link #FALLBACK_LEVEL}. Always pass {@link #FALLBACK_LEVEL} at the call site too: on NeoForge
 * the level given at registration wins.
 */
public final class StockMarketPermissions {
    private StockMarketPermissions() {}

    /** Vanilla op level required for every node when no permission mod decides. */
    public static final int FALLBACK_LEVEL = 2;

    /** {@code /stockmarket op} and {@code /stockmarket deop} (sets the per-user StockMarket admin flag). */
    public static final String COMMAND_OP = "stockmarket.admin.command.op";
    /** {@code /stockmarket manage} (opening the GUI additionally requires the StockMarket admin flag). */
    public static final String COMMAND_MANAGE = "stockmarket.admin.command.manage";
    /** {@code /stockmarket preset ...} */
    public static final String COMMAND_PRESET = "stockmarket.admin.command.preset";
    /** All {@code /stockmarket news ...} subcommands. */
    public static final String COMMAND_NEWS = "stockmarket.admin.command.news";
    /** {@code /stockmarket backup ...} */
    public static final String COMMAND_BACKUP = "stockmarket.admin.command.backup";
    /** {@code /stockmarket open|close <market>} and {@code /stockmarket <market> remove}. */
    public static final String COMMAND_MARKET = "stockmarket.admin.command.market";
    /** {@code /stockmarket exportrecipes} (dev builds only). */
    public static final String COMMAND_EXPORTRECIPES = "stockmarket.admin.command.exportrecipes";
    /** {@code /stockmarket devTestScreen} (dev builds only). */
    public static final String COMMAND_DEVTEST = "stockmarket.admin.command.devtest";
    /** Receiving admin chat notifications about settings changes made by other admins. */
    public static final String NOTIFY = "stockmarket.admin.notify";

    /** Every node, used by {@link #register()} and the registration test. */
    public static final String[] ALL_NODES = {COMMAND_OP, COMMAND_MANAGE, COMMAND_PRESET, COMMAND_NEWS,
            COMMAND_BACKUP, COMMAND_MARKET, COMMAND_EXPORTRECIPES, COMMAND_DEVTEST, NOTIFY};

    /** Registers all nodes with ModUtilities. Must run during mod init, before the server starts (NeoForge). */
    public static void register() {
        for (String node : ALL_NODES) {
            PermissionCompat.registerNode(node, FALLBACK_LEVEL);
        }
    }
}
