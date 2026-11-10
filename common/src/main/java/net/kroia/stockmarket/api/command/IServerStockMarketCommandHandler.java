package net.kroia.stockmarket.api.command;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Sync command handler interface - master-only commands.
 * These commands require direct access to the master server's data
 * and cannot be forwarded asynchronously (e.g. op/deop admin mode).
 * Server thread only; not meant to be called by other mods.
 */
public interface IServerStockMarketCommandHandler {

    /**
     * {@code /stockmarket op|deop} without a name: sets or clears the executor's own StockMarket admin flag.
     * Same as {@link #stockmarket_setStockmarketAdminMode_user} with the executor's name.
     *
     * @param executor the player who ran the command
     * @param isAdmin  {@code true} for op, {@code false} for deop
     * @return true if the flag was set
     */
    boolean stockmarket_setStockmarketAdminMode(@NotNull UUID executor, boolean isAdmin);

    /**
     * {@code /stockmarket op|deop <username>}: sets or clears a user's StockMarket admin flag, tells the executor
     * and the user, and mirrors the change to LuckPerms when the admin sync setting is on.
     *
     * @param executor the player who ran the command; gets the result message
     * @param userName the stored name of the target user (also offline users who joined before)
     * @param isAdmin  {@code true} for op, {@code false} for deop
     * @return true if the user exists and the flag was set; false (with a "player not found" message) otherwise
     */
    boolean stockmarket_setStockmarketAdminMode_user(@NotNull UUID executor, String userName, boolean isAdmin);
}
