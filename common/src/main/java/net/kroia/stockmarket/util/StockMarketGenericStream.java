package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
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

    protected final IServerMarketManager getMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync();
    }
    protected final long getCurrentMarketPrice(ItemID id)
    {
        IServerMarketManager serverMarketManager = BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        IServerMarket m =  serverMarketManager.getMarket(id);
        if(m == null)
            return 0L;
        return m.getCurrentMarketPrice();
    }

    @Override
    public boolean needsRoutingToMaster()
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave();
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
