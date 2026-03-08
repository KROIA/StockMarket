package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MarketManager
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        Market.setBackend(backend);
    }


    private final Map<ItemID, Market> markets = new HashMap<>();


    public MarketManager()
    {

    }

    public Market createMarket(@NotNull ItemID itemID)
    {
        Market m = markets.get(itemID);
        if (m == null)
        {
            m = new Market(itemID);
            markets.put(itemID, m);
        }
        return m;
    }
    public @Nullable Market getMarket(@NotNull ItemID itemID)
    {
        return markets.get(itemID);
    }
}
