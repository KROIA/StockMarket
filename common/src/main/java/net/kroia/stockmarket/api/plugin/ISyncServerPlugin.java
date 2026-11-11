package net.kroia.stockmarket.api.plugin;

/**
 * Marker for synchronous access to a plugin on the master. It has no methods yet; a plugin reaches the markets
 * through {@link net.kroia.stockmarket.api.plugin.interaction.IPluginMarket}.
 */
public interface ISyncServerPlugin {
}
