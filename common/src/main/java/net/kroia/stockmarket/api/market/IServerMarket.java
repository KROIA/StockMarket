package net.kroia.stockmarket.api.market;

/**
 * A market on the master: synchronous ({@link ISyncServerMarket}, server thread only) and asynchronous
 * ({@link IAsyncMarket}, futures already complete) access to the same object.
 */
public interface IServerMarket extends IAsyncMarket, ISyncServerMarket{
}
