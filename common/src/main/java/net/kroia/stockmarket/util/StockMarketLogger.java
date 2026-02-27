package net.kroia.stockmarket.util;

import com.mojang.logging.LogUtils;
import net.kroia.banksystem.BankSystemModBackend;
import net.kroia.stockmarket.StockMarketModBackend;
import org.slf4j.Logger;

public class StockMarketLogger {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static StockMarketModBackend.Instances INSTANCES;

    public StockMarketLogger(StockMarketModBackend.Instances instances) {
        INSTANCES = instances;
    }

    public void info(String message) {
        boolean enabled = true;
        if(INSTANCES.SERVER_SETTINGS != null)
            enabled = INSTANCES.SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_INFO.get();
        if(enabled)
            LOGGER.info(message);
    }
    public void error(String message) {
        boolean enabled = true;
        if(INSTANCES.SERVER_SETTINGS != null)
            enabled = INSTANCES.SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_ERROR.get();
        if(enabled)
            LOGGER.error(message);
    }
    public void error(String message, Throwable throwable) {
        boolean enabled = true;
        if(INSTANCES.SERVER_SETTINGS != null)
            enabled = INSTANCES.SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_ERROR.get();
        if(enabled)
            LOGGER.error(message, throwable);
    }
    public void warn(String message) {
        boolean enabled = true;
        if(INSTANCES.SERVER_SETTINGS != null)
            enabled = INSTANCES.SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_WARNING.get();
        if(enabled)
            LOGGER.warn(message);
    }
    public void debug(String message) {
        boolean enabled = true;
        if(INSTANCES.SERVER_SETTINGS != null)
            enabled = INSTANCES.SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_DEBUG.get();
        if(enabled)
            LOGGER.debug(message);
    }
}
