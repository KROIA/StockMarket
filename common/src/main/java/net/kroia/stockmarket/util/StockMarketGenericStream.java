package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.Market;
import net.kroia.stockmarket.market.server.MarketManager;
import net.minecraft.server.level.ServerPlayer;

public abstract class StockMarketGenericStream<IN, OUT> extends GenericStream<IN, OUT> {
    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
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
    protected final long getCurrentMarketPrice(ItemID id)
    {
        MarketManager marketManager = BACKEND_INSTANCES.MARKET_MANAGER;
        Market m =  marketManager.getMarket(id);
        if(m == null)
            return 0L;
        return m.getCurrentMarketPrice();
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
