package net.kroia.stockmarket.pluginsystem.plugin.core;

import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;

public class MarketInterface {
    public final IPluginMarket market;
    public final IPluginOrderBook oderBook;

    public MarketInterface(IPluginMarket pluginMarket,
                            IPluginOrderBook pluginOrderBook)
    {
        this.market = pluginMarket;
        this.oderBook = pluginOrderBook;
    }
}
