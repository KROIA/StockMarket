package net.kroia.stockmarket.api.command;

/**
 * Facade interface for accessing StockMarket command handlers.
 * Provides access to both async (master+slave) and sync (master-only) handlers.
 */
public interface IStockMarketCommands {
    boolean hasSyncAccess();
    boolean hasAsyncAccess();
    IAsyncStockMarketCommandHandler getAsync();
    IServerStockMarketCommandHandler getSync();
}
