package net.kroia.stockmarket.pluginsystem.plugin.core.cache;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginCache {
    private final Map<ItemID, MarketCache> marketCaches = new HashMap<>();
    private final List<MarketInterface> interfaces = new ArrayList<>();



    @Nullable
    public MarketCache getMarketCache(ItemID marketID)
    {
        return marketCaches.get(marketID);
    }
    public Map<ItemID, MarketCache> getMarketCaches()
    {
        return marketCaches;
    }
    public List<MarketInterface> getInterfaces()
    {
        return interfaces;
    }

    /**
     * Interfaces of all markets that are currently inside their trading hours. Plugins only
     * update/finalize these, so closed markets keep a static price. Markets under maintenance are
     * still included (bots keep trading during maintenance).
     * @return a new list of the open markets' interfaces
     */
    public List<MarketInterface> getOpenInterfaces()
    {
        List<MarketInterface> open = new ArrayList<>(interfaces.size());
        for (MarketInterface interf : interfaces)
        {
            MarketCache cache = marketCaches.get(interf.market.getMarketID());
            if (cache == null || cache.isTradingOpen())
                open.add(interf);
        }
        return open;
    }


    public boolean putCache(ItemID marketID, MarketCache cache)
    {
        if(marketCaches.containsKey(marketID))
        {
            return false;
        }
        MarketInterface interf = new MarketInterface(cache.getPluginMarket(), cache.getPluginOrderBook());
        interfaces.add(interf);
        marketCaches.put(marketID, cache);
        return true;
    }
    public boolean removeMarketCache(ItemID marketID)
    {
        if(!marketCaches.containsKey(marketID))
            return false;
        marketCaches.remove(marketID);
        // Find the interface
        interfaces.removeIf(el -> el.market.getMarketID().equals(marketID));
        return true;
    }
}
