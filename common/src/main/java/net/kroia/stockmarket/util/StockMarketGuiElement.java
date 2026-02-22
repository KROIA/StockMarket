package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.IClientBankManager;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IClientMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarketManager;

public abstract class StockMarketGuiElement extends GuiElement {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    protected IClientMarket selectedMarket;

    public final static float hoverToolTipFontSize = 0.8f;
    public final static int padding = 5;
    public final static int spacing = 5;


    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketGuiElement() {
        super();
    }
    public StockMarketGuiElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ClientMarketManager getMarketManager() {
        return BACKEND_INSTANCES.CLIENT_MARKET_MANAGER;
    }
    public IClientBankManager getBankManager() {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager();
    }
    public void selectMarket(TradingPair tradingPair) {
        this.selectedMarket = getMarketManager().getClientMarket(tradingPair);
    }
    public IClientMarket getSelectedMarket() {
        return selectedMarket;
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[StockMarketGuiElement] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiElement] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiElement] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[StockMarketGuiElement] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[StockMarketGuiElement] " + msg);
    }
}
