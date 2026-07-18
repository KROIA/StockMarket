package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
import net.kroia.stockmarket.StockMarketMod;

/**
 * Server-side network permission gate for <b>mutating</b> StockMarket RPCs
 * (T-123).
 * <p>
 * The bug this class closes: on a multi-server (master + slaves) setup, a
 * slave that is <b>not</b> in the master's trusted-slaves list (BankSystem's
 * {@code ServerBankManager.trustedSlaveServers}) was still able to forward
 * client actions — orders, cancellations, plugin management — to the master
 * and have them executed. The slave-untrust flag was signalling only.
 * <p>
 * BankSystem's {@code AsyncForwardingRequest} already gates its dispatch-table
 * calls via {@code isRequestAllowed(...)}, but StockMarket's own <b>direct
 * Requests</b> (each concrete {@code GenericRequest} subclass in the
 * {@code request/} package) bypassed that path. This helper is the missing
 * choke-point — call it at the very top of each mutating handler and reject
 * the call if it originates from an untrusted slave.
 * <p>
 * <b>Read-only fetches are intentionally NOT gated</b> (per T-123 DevNote):
 * markets list, prices, history, news, prefs read all keep working on an
 * untrusted slave so the client UI can still be browsed — the untrusted flag
 * blocks writes only.
 * <p>
 * <b>Trust storage — do NOT reimplement.</b> The trusted-slave set lives in
 * BankSystem's {@code ServerBankManager} and is toggled by the
 * {@code /banksystem trust <slaveID>} / {@code untrust} admin commands
 * (op level 2, master-only). This class only <b>reads</b> that state via
 * BankSystem's public {@link ISyncServerBankManager#isSlaveServerTrusted}.
 */
public final class NetworkGate {

    private NetworkGate() {}

    /**
     * Returns {@code true} iff a mutating call is allowed to proceed on the
     * master server.
     * <ul>
     *   <li>{@code slaveID == null} or empty — the call originated from a
     *       client directly connected to the master (or from the master
     *       itself). Always allowed; the master implicitly trusts itself.</li>
     *   <li>{@code slaveID} non-empty — the call was forwarded by a slave
     *       server. Only allowed if that slave is currently in BankSystem's
     *       trusted-slave set.</li>
     * </ul>
     * <p>
     * Any exception while reaching BankSystem (partially initialized backend,
     * BankSystem not present) fails <b>closed</b> — the mutating call is
     * rejected. Rejections are logged at WARN so an admin can trace them.
     *
     * @param slaveID     the slave-forwarding identifier attached to the request
     *                    by the RPC layer (see
     *                    {@code GenericRequestPacket.handleOnMaster}); empty
     *                    string for a master-local call
     * @param requestType a short human-readable label for the log line
     *                    (e.g. {@code "CreateOrderRequest"}) — never a wire value
     * @return {@code true} to let the handler proceed, {@code false} to
     *         short-circuit with a default/failure response
     */
    public static boolean isMutatingCallAllowed(String slaveID, String requestType) {
        if (slaveID == null || slaveID.isEmpty()) {
            return true; // master-local call (client on master, or master itself)
        }
        try {
            IBankManager api = BankSystemMod.getAPI().getServerBankManager();
            if (api == null) {
                warn(requestType, "BankSystem API not available — refusing mutating call from slave '" + slaveID + "'");
                return false;
            }
            ISyncServerBankManager sbm = api.getSync();
            if (sbm == null) {
                warn(requestType, "BankSystem sync manager not available — refusing mutating call from slave '" + slaveID + "'");
                return false;
            }
            if (!sbm.isSlaveServerTrusted(slaveID)) {
                warn(requestType, "Rejected mutating call from UNTRUSTED slave '" + slaveID
                        + "' — ask an admin to run /banksystem trust " + slaveID);
                return false;
            }
            return true;
        } catch (Exception e) {
            warn(requestType, "Failed to consult BankSystem trust state (slave='" + slaveID + "'): " + e);
            return false;
        }
    }

    private static void warn(String requestType, String msg) {
        StockMarketMod.LOGGER.warn("[NetworkGate/" + requestType + "] " + msg);
    }
}
