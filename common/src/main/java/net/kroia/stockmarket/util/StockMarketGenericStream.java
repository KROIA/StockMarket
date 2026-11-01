package net.kroia.stockmarket.util;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class StockMarketGenericStream<IN, OUT> extends GenericStream<IN, OUT> {
    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(player.getUUID());
    }
    protected boolean playerIsAdmin(UUID playerUUID)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(playerUUID);
    }

    /**
     * Only call this function on the master server!
     */
    protected final @Nullable IServerMarketManager getMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync();
    }

    /**
     * Only call this function on the master server!
     */
    protected final IServerPluginManager getPluginManager() { return BACKEND_INSTANCES.PLUGIN_MANAGER.getSync(); }
    protected IBankManager getServerBankManager() {return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager(); }

    /**
     * Only call this function on the master server!
     */
    protected int getItemFractionScaleFactor()
    {
        IServerBankManager manager = getServerBankManager().getSync();
        if(manager == null)
            return BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
        return manager.getItemFractionScaleFactor();
    }

    /**
     * Only call this function on the master server!
     */
    protected final long getCurrentMarketPrice(ItemID id)
    {
        IServerMarketManager serverMarketManager = BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        IServerMarket m =  serverMarketManager.getMarket(id);
        if(m == null)
            return 0L;
        return m.getCurrentMarketPrice();
    }


    @Override
    public boolean needsRoutingToMaster()
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave();
    }

    /**
     * Trust gate for a server-to-client stream, mirroring the fail-closed core
     * used by the mutating/management request gates (T-129).
     * <p>
     * Delegates to {@link NetworkGate#isManagementReadAllowed(String, String)}
     * with this stream's originating slave id ({@link #getSlaveServerID()}):
     * <ul>
     *   <li><b>Master-local / direct-to-master client stream</b>
     *       ({@code getSlaveServerID()} is {@code null} or empty) &rarr; always
     *       {@code true}; the master implicitly trusts itself.</li>
     *   <li><b>Trusted slave</b> &rarr; {@code true}; the live feed keeps
     *       flowing.</li>
     *   <li><b>Untrusted slave</b> (or any error reaching BankSystem) &rarr;
     *       {@code false}; the stream must be withheld/torn down.</li>
     * </ul>
     * <p>
     * Intended to be called at the very top of a subclass's per-tick
     * {@code updateOnServer} (before any payload work or send): an untrusted
     * subscriber then never receives a payload, and a mid-session
     * {@code /banksystem untrust <slaveID>} tears the feed down within one
     * stream interval because the trust state is re-queried live on every tick.
     * The trust decision itself lives entirely in StockMarket's
     * {@link NetworkGate}; no BankSystem/trust logic leaks into ModUtilities.
     *
     * @param label short human-readable label for the WARN log line emitted on
     *              rejection (e.g. the stream class name) — never a wire value
     * @return {@code true} if this stream may keep sending to its target,
     *         {@code false} when it is bound for an untrusted slave
     */
    protected boolean isStreamTrustAllowed(String label) {
        return NetworkGate.isManagementReadAllowed(getSlaveServerID(), label);
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("["+getStreamTypeID()+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("["+getStreamTypeID()+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("["+getStreamTypeID()+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("["+getStreamTypeID()+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("["+getStreamTypeID()+"]: "+message);
    }
}
