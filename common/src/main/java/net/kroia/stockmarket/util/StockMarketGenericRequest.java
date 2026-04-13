package net.kroia.stockmarket.util;


import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.GenericRequest;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class StockMarketGenericRequest<IN, OUT> extends GenericRequest<IN, OUT> {
    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.playerIsAdmin(player);
    }

    protected IServerMarketManager getServerMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync();
    }
    protected IBankManager getServerBankManager() {return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager(); }
    protected int getItemFractionScaleFactor()
    {
        IServerBankManager manager = getServerBankManager().getSync();
        if(manager == null)
            return BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
        return manager.getItemFractionScaleFactor();
    }

    protected final long getCurrentMarketPrice(ItemID id)
    {
        IServerMarketManager serverMarketManager = BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        IServerMarket m =  serverMarketManager.getMarket(id);
        if(m == null)
            return 0L;
        return m.getCurrentMarketPrice();
    }



    public CompletableFuture<OUT> handleOnServer(IN input, ServerPlayer sender) {
        return handleOnMasterServer(input, "", sender.getUUID());
    }
    @Override
    public boolean needsRoutingToMaster()
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave();
    }


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
