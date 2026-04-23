package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IClientPlugin;

public class ClientPlugin implements IClientPlugin {
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }









    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientPlugin]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPlugin]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPlugin]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientPlugin]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientPlugin]: "+message);
    }
}
