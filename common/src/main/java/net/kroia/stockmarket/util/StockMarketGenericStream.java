package net.kroia.stockmarket.util;

import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.MarketManager;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketGenericStream<IN, OUT> extends GenericStream<IN, OUT> {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return player.hasPermissions(BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.ADMIN_PERMISSION_LEVEL.get());
    }

    protected final MarketManager getMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER;
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("["+getStreamTypeID()+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("["+getStreamTypeID()+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("["+getStreamTypeID()+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("["+getStreamTypeID()+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("["+getStreamTypeID()+"]: "+message);
    }
}
