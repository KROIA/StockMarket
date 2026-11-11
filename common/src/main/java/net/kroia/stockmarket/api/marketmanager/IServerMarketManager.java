package net.kroia.stockmarket.api.marketmanager;

/**
 * The master's market manager, from {@link IMarketManager#getSync()}: synchronous ({@link ISyncServerMarketManager},
 * server thread only) and asynchronous ({@link IAsyncMarketManager}, futures already complete) access to the same object.
 */
public interface IServerMarketManager extends ISyncServerMarketManager, IAsyncMarketManager{

}
