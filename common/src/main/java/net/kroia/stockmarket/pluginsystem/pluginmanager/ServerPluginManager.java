package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginsystem.IServerPluginManager;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.minecraft.nbt.ListTag;

import java.util.Map;

/**
 * ServerPluginManager runs on the master server and holds the instances of the plugins.
 * It updates the plugins
 *
 */
public class ServerPluginManager implements ServerSaveableChunked, IServerPluginManager{

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        ServerMarket.setBackend(backend);
    }



    @Override
    public void update()
    {

    }



    @Override
    public boolean save(Map<String, ListTag> listTags) {
        return false;
    }

    @Override
    public boolean load(Map<String, ListTag> listTags) {
        return false;
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerPluginManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerPluginManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerPluginManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerPluginManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerPluginManager]: "+message);
    }
}
