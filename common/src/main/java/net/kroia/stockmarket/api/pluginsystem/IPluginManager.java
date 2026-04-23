package net.kroia.stockmarket.api.pluginsystem;

import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import org.jetbrains.annotations.Nullable;

public interface IPluginManager {
    /**
     * Checks if this instance has access to synchronized plugin manager.
     * If this server is a master, it will have sync access.
     * @apiNote
     * Do not call this from client side!
     * @return true if this is a master or multiserver capability is turned off.
     */
    boolean hasSyncAccess();


    /**
     * Checks if this instance can use async plugin manager interactions.
     * If this server is a master, it will have async access available.
     * @apiNote
     * Do not call this from client side!
     * @return  true if this is a master.
     *          true if this is a slave which is connected to a master
     *          false if this is a slave without connection to a master
     */
    boolean hasAsyncAccess();


    /**
     * @apiNote
     * Do not call this from client side!
     * @return the synchronized access interface.
     */
    @Nullable IServerPluginManager getSync();


    /**
     * @apiNote
     * Do not call this from client side!
     * @return the asynchronous access interface.
     */
    IAsyncPluginManager getAsync();


    boolean isSlave();

    boolean isMaster();
}
