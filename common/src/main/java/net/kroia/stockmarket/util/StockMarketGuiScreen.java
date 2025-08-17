package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.IClientBankManager;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IClientMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarketManager;
import net.minecraft.network.chat.Component;

public abstract class StockMarketGuiScreen extends GuiScreen {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    protected IClientMarket selectedMarket;
    public static final float guiScale = 0.8f;

    protected StockMarketGuiScreen(Component pTitle) {
        super(pTitle);
        setGuiScale(guiScale);
    }

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected ClientMarketManager getMarketManager() {
        return BACKEND_INSTANCES.CLIENT_MARKET_MANAGER;
    }
    protected IClientBankManager getBankManager() {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager();
    }
    protected void selectMarket(TradingPair tradingPair) {
        this.selectedMarket = getMarketManager().getClientMarket(tradingPair);
    }
    protected IClientMarket getSelectedMarket() {
        return selectedMarket;
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[StockMarketGuiScreen] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiScreen] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiScreen] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[StockMarketGuiScreen] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[StockMarketGuiScreen] " + msg);
    }
}
