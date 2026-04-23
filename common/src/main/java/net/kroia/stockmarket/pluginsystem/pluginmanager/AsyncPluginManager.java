package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginmanager.IAsyncPluginManager;
import net.kroia.stockmarket.pluginsystem.plugin.AsyncPlugin;

public class AsyncPluginManager implements IAsyncPluginManager {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        AsyncPlugin.setBackend(backend);
    }

    private final boolean isClientSide;
    private AsyncPluginManager(boolean clientSide) {
        this.isClientSide = clientSide;
    }


    public static AsyncPluginManager createClientManager()
    {
        return new AsyncPluginManager(true);
    }
    public static AsyncPluginManager createSlaveServerManager()
    {
        return new AsyncPluginManager(false);
    }
}
