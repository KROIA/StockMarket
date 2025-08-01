package net.kroia.stockmarket.util;

import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientStockMarketManager;
import net.minecraft.network.chat.Component;

public abstract class StockMarketGuiScreen extends GuiScreen {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    protected ClientMarket selectedMarket;

    protected StockMarketGuiScreen(Component pTitle) {
        super(pTitle);
    }

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
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
