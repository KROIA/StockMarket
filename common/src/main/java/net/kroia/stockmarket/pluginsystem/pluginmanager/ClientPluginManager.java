package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginsystem.IAsyncPluginManager;
import net.kroia.stockmarket.api.pluginsystem.IClientPluginManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;


public class ClientPluginManager implements IClientPluginManager {

    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        ClientMarket.setBackend(backend);
    }
    private final IAsyncPluginManager asyncPluginManager;


    public ClientPluginManager()
    {
        asyncPluginManager = AsyncPluginManager.createClientManager();
    }






    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientPluginManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientPluginManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientPluginManager]: "+message);
    }
}
