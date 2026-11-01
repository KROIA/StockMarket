package net.kroia.stockmarket.minecraft.command;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IStockMarketCommands;
import org.jetbrains.annotations.Nullable;

/**
 * Facade implementation for StockMarket command handlers.
 * Use createMaster() on master servers and createSlave() on slave servers.
 * On master: both async and sync handlers are available (ServerStockMarketCommandHandler implements both).
 * On slave: only async handler is available (AsyncStockMarketCommandHandler forwards via ARRS where needed).
 */
public class StockMarketCommandHandler implements IStockMarketCommands {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        AsyncStockMarketCommandHandler.setBackend(backend);
        ServerStockMarketCommandHandler.setBackend(backend);
    }

    private final IAsyncStockMarketCommandHandler asyncHandler;
    private final @Nullable IServerStockMarketCommandHandler serverHandler;

    private StockMarketCommandHandler(IAsyncStockMarketCommandHandler asyncHandler, @Nullable IServerStockMarketCommandHandler serverHandler) {
        this.asyncHandler = asyncHandler;
        this.serverHandler = serverHandler;
    }

    /**
     * Create a slave-side command handler.
     * Only the async handler is available; sync (master-only) commands will return null from getSync().
     */
    public static StockMarketCommandHandler createSlave() {
        return new StockMarketCommandHandler(new AsyncStockMarketCommandHandler(), null);
    }

    /**
     * Create a master-side command handler.
     * Both async and sync handlers are available.
     * The ServerStockMarketCommandHandler implements both interfaces.
     */
    public static StockMarketCommandHandler createMaster() {
        ServerStockMarketCommandHandler handler = new ServerStockMarketCommandHandler();
        return new StockMarketCommandHandler(handler, handler);
    }

    @Override
    public boolean hasSyncAccess() {
        return serverHandler != null;
    }

    @Override
    public boolean hasAsyncAccess() {
        return true;
    }

    @Override
    public IAsyncStockMarketCommandHandler getAsync() {
        return asyncHandler;
    }

    @Override
    public @Nullable IServerStockMarketCommandHandler getSync() {
        return serverHandler;
    }
}
