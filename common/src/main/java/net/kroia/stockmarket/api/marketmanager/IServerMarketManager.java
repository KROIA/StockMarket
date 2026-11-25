package net.kroia.stockmarket.api.marketmanager;

/**
 * The master's market manager, from {@link IMarketManager#getSync()}: synchronous ({@link ISyncServerMarketManager},
 * server thread only) and asynchronous ({@link IAsyncMarketManager}, any thread; runs on the server thread) access to
 * the same object.
 */
public interface IServerMarketManager extends ISyncServerMarketManager, IAsyncMarketManager{

}
