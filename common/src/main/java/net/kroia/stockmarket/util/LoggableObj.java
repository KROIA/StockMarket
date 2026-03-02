package net.kroia.stockmarket.util;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.VirtualOrderbook;

public class LoggableObj {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        VirtualOrderbook.setBackend(backend);
    }




    public static void info(String message) {
        BACKEND_INSTANCES.LOGGER.info(message);
    }
    public void error(String message) {
        BACKEND_INSTANCES.LOGGER.error(message);
    }
    public void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error(message, throwable);
    }
    public void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn(message);
    }
    public void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug(message);
    }
}
