package net.kroia.stockmarket.util;

import net.kroia.modutilities.networking.arrs.GenericRequest;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.MarketManager;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketGenericRequest<IN, OUT> extends GenericRequest<IN, OUT> {
    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.playerIsAdmin(player);
    }

    protected MarketManager getServerMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER;
    }
}
