package net.kroia.stockmarket.api.pluginmanager;

/**
 * The master's plugin manager, from {@link IPluginManager#getSync()}: synchronous ({@link ISyncServerPluginManager})
 * and asynchronous ({@link IAsyncPluginManager}) access to the same object.
 */
public interface IServerPluginManager extends ISyncServerPluginManager, IAsyncPluginManager {
}
