package net.kroia.stockmarket.plugin.base.cache;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.interaction.MarketInterfaces;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketBehaviorPluginCache {


    private final Map<TradingPair, MarketCache> marketCaches = new HashMap<>();
    private final List<MarketInterfaces> interfaces = new ArrayList<>();


    public MarketBehaviorPluginCache()
    {

    }

    @Nullable
    public MarketCache getMarketCache(TradingPair tradingPair)
    {
        return marketCaches.get(tradingPair);
    }
    public Map<TradingPair, MarketCache> getMarketCaches()
    {
        return marketCaches;
    }
    public List<MarketInterfaces> getInterfaces()
    {
        return interfaces;
    }


    public boolean putCache(TradingPair tradingPair, MarketCache cache)
    {
        if(marketCaches.containsKey(tradingPair))
        {
            return false;
        }
        MarketInterfaces interf = new MarketInterfaces(cache.getPluginMarket(), cache.getPluginOrderBook());
        interfaces.add(interf);
        marketCaches.put(tradingPair, cache);
        return true;
    }
    public boolean removeMarketCache(TradingPair tradingPair)
    {
        if(!marketCaches.containsKey(tradingPair))
            return false;
        marketCaches.remove(tradingPair);
        // Find the interface
        interfaces.removeIf(el -> el.market.getTradingPair().equals(tradingPair));
        return true;
    }

    /*public void apply(IServerMarketManager manager)
    {
        for(MarketCache marketCache : marketCaches.values())
        {
            marketCache.apply();
        }
    }*/
}
