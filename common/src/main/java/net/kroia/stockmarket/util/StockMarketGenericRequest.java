package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.IServerBankManager;
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
    protected IServerBankManager getServerBankManager() {return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager(); }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("["+getRequestTypeID()+"] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("["+getRequestTypeID()+"] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("["+getRequestTypeID()+"] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("["+getRequestTypeID()+"] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("["+getRequestTypeID()+"] " + msg);
    }
}
