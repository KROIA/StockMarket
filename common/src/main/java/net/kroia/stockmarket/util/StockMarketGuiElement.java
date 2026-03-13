package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientMarketManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class StockMarketGuiElement extends GuiElement {
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    public final static float hoverToolTipFontSize = 0.8f;
    public final static int padding = 5;
    public final static int spacing = 5;




    public StockMarketGuiElement() {
        super();
    }
    public StockMarketGuiElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    protected ClientMarketManager getMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER;
    }
    protected List<ItemID> getAvailableMarkets()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getAvailableMarkets();
    }

    protected @Nullable ClientMarket getMarket(ItemID itemID)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getMarket(itemID);
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
