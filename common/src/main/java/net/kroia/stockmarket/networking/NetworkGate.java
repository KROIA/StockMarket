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
 * <b>Player-facing reads stay open</b> (per T-123 DevNote): markets list,
 * prices, history, news, prefs read all keep working on an untrusted slave so
 * the client UI can still be browsed.
 * <p>
 * <b>Management-data reads ARE gated</b> (T-129): reads that expose
 * administrative data (full plugin list + custom settings, server mod-settings
 * JSON, preset catalog) must NOT reach an untrusted slave, so their handlers
 * additionally call {@link #isManagementReadAllowed}. Ordinary player reads are
 * unaffected.
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
        return isTrustedSlave(slaveID, requestType);
    }

    /**
     * Returns {@code true} iff a <b>management-data read</b> is allowed to reach
     * the caller on the master server.
     * <p>
     * This is the read-side counterpart to {@link #isMutatingCallAllowed}
     * (T-129). Some Requests do not mutate state but return
     * <b>administrative/management data</b> that must not be exposed to a
     * self-compiled client behind an untrusted slave — e.g. the full plugin
     * list (metadata + subscribed markets + every per-market custom setting),
     * the server mod-settings JSON, or the preset catalog. Ordinary player-facing
     * reads (markets, prices, history, news) stay open and must NOT use this gate.
     * <ul>
     *   <li>{@code slaveID == null} or empty — master-local call (client on the
     *       master, or the master itself). Always allowed.</li>
     *   <li>{@code slaveID} non-empty — forwarded by a slave. Only allowed if
     *       that slave is currently in BankSystem's trusted-slave set.</li>
     * </ul>
     * <p>
     * Fails <b>closed</b> on any error reaching BankSystem: an untrusted slave or
     * an exception yields {@code false} and a WARN log labeled with
     * {@code requestType}. Semantics are identical to
     * {@link #isMutatingCallAllowed}; the separate name documents intent at the
     * call site (guarding a read rather than a write).
     *
     * @param slaveID     the slave-forwarding identifier attached to the request
     *                    by the RPC layer; empty string for a master-local call
     * @param requestType a short human-readable label for the log line
     *                    (e.g. {@code "PluginListRequest"}) — never a wire value
     * @return {@code true} to serve the management data, {@code false} to return
     *         an empty/withheld response
     */
    public static boolean isManagementReadAllowed(String slaveID, String requestType) {
        return isTrustedSlave(slaveID, requestType);
    }

    /**
     * Shared trust core for both {@link #isMutatingCallAllowed} and
     * {@link #isManagementReadAllowed}.
     * <p>
     * Master-local calls (null/empty {@code slaveID}) are always allowed; a
     * slave-forwarded call is allowed only if BankSystem currently reports the
     * slave as trusted. Any exception while consulting BankSystem fails
     * <b>closed</b> (returns {@code false}) and logs a WARN so an admin can trace
     * the rejection.
     *
     * @param slaveID     the slave-forwarding identifier; empty for master-local
     * @param requestType short label used in the WARN log line
     * @return {@code true} if the call may proceed, {@code false} to reject
     */
    private static boolean isTrustedSlave(String slaveID, String requestType) {
        if (slaveID == null || slaveID.isEmpty()) {
            return true; // master-local call (client on master, or master itself)
        }
        try {
            IBankManager api = BankSystemMod.getAPI().getServerBankManager();
            if (api == null) {
                warn(requestType, "BankSystem API not available — refusing call from slave '" + slaveID + "'");
                return false;
            }
            ISyncServerBankManager sbm = api.getSync();
            if (sbm == null) {
                warn(requestType, "BankSystem sync manager not available — refusing call from slave '" + slaveID + "'");
                return false;
            }
            if (!sbm.isSlaveServerTrusted(slaveID)) {
                warn(requestType, "Rejected call from UNTRUSTED slave '" + slaveID
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
