package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IAsyncPlugin;

public class AsyncPlugin implements IAsyncPlugin {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final boolean isClientSide;

    private AsyncPlugin(boolean isClientSide)
    {
        this.isClientSide = isClientSide;
    }
    public static AsyncPlugin createClientPlugin()
    {
        return new AsyncPlugin(true);
    }
    public static AsyncPlugin createSlaveServerMarket()
    {
        return new AsyncPlugin(false);
    }
    public static AsyncPlugin createMarket(boolean isClientSide)
    {
        return new AsyncPlugin(isClientSide);
    }
















    private static void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[AsyncPlugin] " + msg);
    }
    private static void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[AsyncPlugin] " + msg);
    }
    private static void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[AsyncPlugin] " + msg, e);
    }
    private static void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[AsyncPlugin] " + msg);
    }
    private static void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[AsyncPlugin] " + msg);
    }
}
