package net.kroia.stockmarket.api.command;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async command handler interface - works on both master and slave servers.
 * Commands that open GUI screens or perform actions that can be resolved locally
 * (or via existing async market manager APIs) implement this interface.
 * Backs the {@code /stockmarket} commands; not meant to be called by other mods.
 */
public interface IAsyncStockMarketCommandHandler {

    /**
     * {@code /stockmarket devTestScreen} (dev builds only): opens the development test screen for the executor.
     *
     * @param executor the player who ran the command
     * @return future resolving to {@code true} if the screen was opened, {@code false} if the player is offline
     */
    CompletableFuture<Boolean> stockmarket_devTestScreen_async(@NotNull UUID executor);
}
