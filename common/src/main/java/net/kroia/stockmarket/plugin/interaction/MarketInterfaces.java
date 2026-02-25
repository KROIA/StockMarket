package net.kroia.stockmarket.plugin.interaction;

import net.kroia.stockmarket.plugin.base.interaction.IPluginMarket;
import net.kroia.stockmarket.plugin.base.interaction.IPluginOrderBook;

public class MarketInterfaces {

    public final IPluginMarket market;
    public final IPluginOrderBook oderBook;

    public MarketInterfaces(IPluginMarket pluginMarket,
                            IPluginOrderBook pluginOrderBook)
    {
        this.market = pluginMarket;
        this.oderBook = pluginOrderBook;
    }


}
