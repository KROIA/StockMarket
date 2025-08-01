package net.kroia.stockmarket.util;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientStockMarketManager;

public abstract class StockMarketGuiElement extends GuiElement {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    protected ClientMarket selectedMarket;

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketGuiElement() {
        super();
    }
    public StockMarketGuiElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    protected ClientStockMarketManager getMarketManager() {
        return BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER;
    }
    protected void selectMarket(TradingPair tradingPair) {
        this.selectedMarket = getMarketManager().getClientMarket(tradingPair);
    }
    protected ClientMarket getSelectedMarket() {
        return selectedMarket;
    }
}
