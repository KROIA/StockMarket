package net.kroia.stockmarket.api.command;

/**
 * Facade interface for accessing StockMarket command handlers.
 * Provides access to both async (master+slave) and sync (master-only) handlers.
 */
public interface IStockMarketCommands {

    /**
     * @return true on the master, where {@link #getSync()} is available
     */
    boolean hasSyncAccess();

    /**
     * @return always true: the async handler exists on master and slave
     */
    boolean hasAsyncAccess();

    /**
     * @return the handler for commands that work on master and slave
     */
    IAsyncStockMarketCommandHandler getAsync();

    /**
     * @return the handler for master-only commands, or null on a slave
     */
    IServerStockMarketCommandHandler getSync();
}
