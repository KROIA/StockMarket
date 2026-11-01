package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import org.jetbrains.annotations.Nullable;

public interface IMarketManager {

    /**
     * Checks if this instance has access to synchronized stockmarket.
     * If this server is a master, it will have sync access.
     * @apiNote
     * Do not call this from client side!
     * @return true if this is a master or multiserver capability is turned off.
     */
    boolean hasSyncAccess();


    /**
     * Checks if this instance can use async stockmarket interactions.
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
    @Nullable IServerMarketManager getSync();


    /**
     * @apiNote
     * Do not call this from client side!
     * @return the asynchronous access interface.
     */
    IAsyncMarketManager getAsync();


    boolean isSlave();

    boolean isMaster();
}
