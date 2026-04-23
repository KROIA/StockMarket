package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginmanager.IAsyncPluginManager;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.stockmarket.api.pluginmanager.IPluginManager;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginManager implements IPluginManager {
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        ServerPluginManager.setBackend(backend);
        AsyncPluginManager.setBackend(backend);
    }


    private final @NotNull IAsyncPluginManager asyncServerPluginManager;

    private final @Nullable IServerPluginManager serverPluginManager;
    private final @Nullable ServerSaveableChunked serverPluginManagerPersistenceInterface;


    private PluginManager(@NotNull IAsyncPluginManager asyncPluginManager, @Nullable IServerPluginManager syncManager, @Nullable ServerSaveableChunked serverPluginManagerPersistenceInterface)
    {
        asyncServerPluginManager = asyncPluginManager;
        serverPluginManager = syncManager;
        this.serverPluginManagerPersistenceInterface = serverPluginManagerPersistenceInterface;
    }


    public static PluginManager createMaster()
    {
        ServerPluginManager syncManager = new ServerPluginManager();
        return new PluginManager(syncManager, syncManager, syncManager);
    }
    public static PluginManager createSlave()
    {
        AsyncPluginManager asyncPluginManager = AsyncPluginManager.createSlaveServerManager();
        return new PluginManager(asyncPluginManager, null, null);
    }
    public static IClientPluginManager createClient()
    {
        return new ClientPluginManager();
    }



    @Override
    public boolean hasSyncAccess() {
        return serverPluginManager != null;
    }

    @Override
    public boolean hasAsyncAccess() {
        return MultiServerUtils.canInteractWithStockMarket();
    }

    @Override
    public @Nullable IServerPluginManager getSync() {
        return serverPluginManager;
    }

    @Override
    public IAsyncPluginManager getAsync() {
        return asyncServerPluginManager;
    }

    @Override
    public boolean isSlave() {
        return serverPluginManager == null;
    }

    @Override
    public boolean isMaster() {
        return serverPluginManager != null;
    }

    public @Nullable ServerSaveableChunked  getServerPluginManagerPersistenceInterface() {
        return serverPluginManagerPersistenceInterface;
    }
}
