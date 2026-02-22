package net.kroia.stockmarket.util;

import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketGenericStream<CONTEXT_DATA, DATA> extends GenericStream<CONTEXT_DATA, DATA>{
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.playerIsAdmin(player);
    }
}
