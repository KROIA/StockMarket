package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.api.bankmanager.ISyncServerBankManager;
import net.kroia.stockmarket.StockMarketMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <b>Public reads stay open</b> (per T-123 DevNote): market list, prices,
 * price history and news keep working on an untrusted slave so the client UI
 * can still be browsed.
 * <p>
 * <b>Management-data reads ARE gated</b> (T-129): reads that expose
 * administrative data (full plugin list + custom settings, server mod-settings
 * JSON, preset catalog) must NOT reach an untrusted slave, so their handlers
 * additionally call {@link #isManagementReadAllowed}.
 * <p>
 * <b>Per-player reads ARE gated too</b> (T-239, user decision 2026-11-10):
 * a player's own active orders, order history, active-orders stream,
 * preferences and admin flag. The slave sets the forwarded player UUID itself,
 * so an untrusted slave could otherwise read any player's data by naming them.
 * These handlers also call {@link #isManagementReadAllowed}.
 * <p>
 * <b>Trust storage — do NOT reimplement.</b> The trusted-slave set lives in
 * BankSystem's {@code ServerBankManager} and is toggled by the
 * {@code /banksystem trust <slaveID>} / {@code untrust} admin commands
 * (op level 2, master-only). This class only <b>reads</b> that state via
 * BankSystem's public {@link ISyncServerBankManager#isSlaveServerTrusted}.
 */
public final class NetworkGate {

    private NetworkGate() {}

    /** MU-160: at most one "untrusted slave" warning per (slave, request type) in this interval. */
    private static final long UNTRUSTED_LOG_INTERVAL_MS = 5 * 60_000L;
    /** MU-160: key "slaveID/requestType" -> {time of the last logged warning, rejections suppressed since}. */
    private static final Map<String, long[]> UNTRUSTED_LOG = new ConcurrentHashMap<>();

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
     * the server mod-settings JSON, or the preset catalog. Per-player reads (own
     * orders, order history, order stream, preferences, admin flag) use it as well
     * (T-239), since the slave sets the player UUID. Public reads (market list,
     * prices, price history, news) stay open and must NOT use this gate.
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
                long suppressed = untrustedRejectionToLog(slaveID + "/" + requestType, System.currentTimeMillis());
                if (suppressed >= 0)
                    warn(requestType, "Rejected call from UNTRUSTED slave '" + slaveID
                            + "' — ask an admin to run /banksystem trust " + slaveID
                            + (suppressed > 0 ? " (" + suppressed + " more suppressed)" : ""));
                return false;
            }
            return true;
        } catch (Exception e) {
            warn(requestType, "Failed to consult BankSystem trust state (slave='" + slaveID + "'): " + e);
            return false;
        }
    }

    /**
     * MU-160: throttles the "untrusted slave" warning. The first rejection of a key is logged, then at most one per
     * {@link #UNTRUSTED_LOG_INTERVAL_MS}; the rejections in between are only counted.
     *
     * @param key   "slaveID/requestType"
     * @param nowMs current time in ms
     * @return -1 to stay quiet, otherwise the number of rejections suppressed since the last logged one
     */
    private static long untrustedRejectionToLog(String key, long nowMs) {
        long[] result = {-1};
        UNTRUSTED_LOG.compute(key, (k, state) -> {
            if (state != null && nowMs - state[0] < UNTRUSTED_LOG_INTERVAL_MS) {
                state[1]++;
                return state;
            }
            result[0] = state == null ? 0 : state[1];
            return new long[]{nowMs, 0};
        });
        return result[0];
    }

    private static void warn(String requestType, String msg) {
        StockMarketMod.LOGGER.warn("[NetworkGate/" + requestType + "] " + msg);
    }
}
