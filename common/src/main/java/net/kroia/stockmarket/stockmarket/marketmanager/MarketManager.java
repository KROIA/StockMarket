package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.modutilities.persistence.ServerSaveableChunked;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.api.marketmanager.IMarketManager;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarketManager implements IMarketManager {
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        ServerMarketManager.setBackend(backend);
        AsyncMarketManager.setBackend(backend);
    }

    private final @NotNull IAsyncMarketManager asyncServerMarketManager;

    private final @Nullable IServerMarketManager serverMarketManager;
    private final @Nullable ServerSaveableChunked serverMarketManagerPersistenceInterface;

    public static MarketManager createMaster()
    {
        ServerMarketManager syncManager = new ServerMarketManager();
        return new MarketManager(syncManager, syncManager, syncManager);
    }
    public static MarketManager createSlave()
    {
        AsyncMarketManager asyncMarketManager = AsyncMarketManager.createSlaveServerManager();
        return new MarketManager(asyncMarketManager, null, null);
    }
    public static IClientMarketManager createClient()
    {
        return new ClientMarketManager();
    }

    private MarketManager(@NotNull IAsyncMarketManager asyncMarketManager, @Nullable IServerMarketManager syncManager, @Nullable ServerSaveableChunked serverMarketManagerPersistenceInterface)
    {
        asyncServerMarketManager = asyncMarketManager;
        serverMarketManager = syncManager;
        this.serverMarketManagerPersistenceInterface = serverMarketManagerPersistenceInterface;
    }

    @Override
    public boolean hasSyncAccess() {
        return serverMarketManager != null;
    }

    @Override
    public boolean hasAsyncAccess() {
        return MultiServerUtils.canInteractWithStockMarket();
    }

    @Override
    public @Nullable IServerMarketManager getSync() {
        return serverMarketManager;
    }

    @Override
    public IAsyncMarketManager getAsync() {
        return asyncServerMarketManager;
    }

    @Override
    public boolean isSlave() {
        return serverMarketManager == null;
    }

    @Override
    public boolean isMaster() {
        return serverMarketManager != null;
    }

    public @Nullable ServerSaveableChunked  getServerMarketManagerPersistenceInterface() {
        return serverMarketManagerPersistenceInterface;
    }
}
