package net.kroia.stockmarket.api.command;

import org.jetbrains.annotations.NotNull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async command handler interface - works on both master and slave servers.
 * Commands that open GUI screens or perform actions that can be resolved locally
 * (or via existing async market manager APIs) implement this interface.
 */
public interface IAsyncStockMarketCommandHandler {
    CompletableFuture<Boolean> stockmarket_manage_async(@NotNull UUID executor);
    CompletableFuture<Boolean> stockmarket_devTestScreen_async(@NotNull UUID executor);
}
