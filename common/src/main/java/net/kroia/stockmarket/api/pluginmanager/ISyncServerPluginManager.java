package net.kroia.stockmarket.api.pluginmanager;

import net.kroia.banksystem.util.ItemID;

public interface ISyncServerPluginManager {
    void update();

    /**
     * Auto-subscribes a newly created market to all plugins that opt in,
     * in plugin list (execution) order.
     *
     * @param marketID the ID of the newly created market
     */
    void autoSubscribeNewMarket(ItemID marketID);

    /**
     * Unsubscribes a market from all plugins and removes its cache.
     * Counterpart to {@link #autoSubscribeNewMarket(ItemID)}.
     *
     * @param marketID the ID of the market to remove
     */
    void removeCache(ItemID marketID);
}
