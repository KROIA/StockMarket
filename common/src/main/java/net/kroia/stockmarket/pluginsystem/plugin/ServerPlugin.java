package net.kroia.stockmarket.pluginsystem.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.plugin.IServerPlugin;

public class ServerPlugin implements IServerPlugin {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }










    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerPlugin]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlugin]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlugin]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerPlugin]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerPlugin]: "+message);
    }

}
