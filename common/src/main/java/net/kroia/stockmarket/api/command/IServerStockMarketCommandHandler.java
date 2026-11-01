package net.kroia.stockmarket.api.command;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Sync command handler interface - master-only commands.
 * These commands require direct access to the master server's data
 * and cannot be forwarded asynchronously (e.g. op/deop admin mode).
 */
public interface IServerStockMarketCommandHandler {
    boolean stockmarket_setStockmarketAdminMode(@NotNull UUID executor, boolean isAdmin);
    boolean stockmarket_setStockmarketAdminMode_user(@NotNull UUID executor, String userName, boolean isAdmin);

    // Give the player a one-time starter kit of items for testing the stock market
    boolean stockmarket_starterKit(@NotNull UUID executor);
}
