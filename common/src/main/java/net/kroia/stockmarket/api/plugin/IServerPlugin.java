package net.kroia.stockmarket.api.plugin;

/**
 * A plugin instance on the master, the type every market plugin ({@code ServerPlugin}) implements. Combines the
 * marker interfaces {@link IAsyncPlugin} and {@link ISyncServerPlugin}.
 */
public interface IServerPlugin extends IAsyncPlugin, ISyncServerPlugin {
}
