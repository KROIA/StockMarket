package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.IClientBankManager;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class StockMarketGuiScreen extends GuiScreen {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        StockMarketGuiElement.setBackend(backend);
    }


    public static final float guiScale = 0.7f;

    protected StockMarketGuiScreen(Component pTitle) {
        super(pTitle);
        setGuiScale(guiScale);
    }
    protected StockMarketGuiScreen(Component pTitle, Screen parent) {
        super(pTitle, parent);
        setGuiScale(guiScale);
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
