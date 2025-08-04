package net.kroia.stockmarket.util;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientStockMarketManager;

public abstract class StockMarketGuiElement extends GuiElement {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    protected ClientMarket selectedMarket;

    public final static float hoverToolTipFontSize = 0.8f;
    public final static int padding = 5;
    private final static int spacing = 5;


    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketGuiElement() {
        super();
    }
    public StockMarketGuiElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ClientStockMarketManager getMarketManager() {
        return BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER;
    }
    public void selectMarket(TradingPair tradingPair) {
        this.selectedMarket = getMarketManager().getClientMarket(tradingPair);
    }
    public ClientMarket getSelectedMarket() {
        return selectedMarket;
    }


    /*@Override
    public void addChild(GuiElement el)
    {
        if(el != null)
        {
            super.addChild(el);
            float hoverToolTipFontSize = 0.5f;
            el.setHoverTooltipFontScale(hoverToolTipFontSize);
        }
    }*/
}
