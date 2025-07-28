package net.kroia.stockmarket.util;

import net.kroia.modutilities.networking.arrs.GenericRequest;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketGenericRequest<IN, OUT> extends GenericRequest<IN, OUT> {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.playerIsAdmin(player);
    }
}
