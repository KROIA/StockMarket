package net.kroia.stockmarket.util;

/**
 * Slave-side cache of the master-authoritative "is this slave trusted?" flag
 * used to gate the client UI (T-123 / T-125 / T-126).
 * <p>
 * <b>Why a cache instead of an inline query:</b>
 * {@code StockMarketModSettings.getClientSettings()} is called synchronously from
 * {@code PlayerJoinSyncPacket.send} at player-join time. BankSystem's
 * {@code AsyncBankManager.isSlaveServerTrustedAsync(slaveID)} short-circuits
 * synchronously to {@code false} whenever
 * {@code MultiServerUtils.canInteractWithBankSystem()} is not yet true — which
 * is the normal state at slave startup (the slave&rarr;master TCP handshake has
 * not completed at server-start time, and often not at first-player-join time
 * either). A blocking {@code .get(timeout)} does <b>not</b> help because the
 * future is already completed with {@code false}. See T-126 root-cause
 * paragraph in {@code PENDING_TASKS.md} for the diagnostic trail.
 * <p>
 * <b>Two-phase-sync design:</b>
 * <ol>
 *   <li>At player join, {@link #getOrFalse()} returns the current cached value
 *       or the fail-closed default ({@code false}) when the cache is still
 *       {@code null}. This value flows into the initial
 *       {@code PlayerJoinSyncPacket}.</li>
 *   <li>When the slave&rarr;master handshake completes, BankSystem's
 *       {@code IBankSystemEvents#getSlaveConnectionAcceptedSignal()} fires. Our
 *       listener issues a real
 *       {@link net.kroia.banksystem.api.bankmanager.IAsyncBankManager#isSlaveServerTrustedAsync(String)}
 *       round-trip (no blocking) and, when it completes, calls {@link #set(boolean)}
 *       and broadcasts a {@code SlaveTrustSyncPacket} to every online player so a
 *       client that received the fail-closed default in step 1 transitions to the
 *       correct state within a tick.</li>
 * </ol>
 * When the slave connection drops (BankSystem's
 * {@code getSlaveConnectionLostSignal()}), {@link #clear()} resets the cache to
 * {@code null} so the fail-closed default applies until the next handshake.
 * <p>
 * The field is {@code volatile} because it is written from a Netty event-loop
 * thread (via the BankSystem signal callback) and read from the server main
 * thread (via {@code getClientSettings()}). Reads and writes are single-value
 * atomic — no lock needed. On the master, this cache is not consulted at all
 * (the master always sends {@code slaveTrusted=true}), so leaving it {@code null}
 * on the master is intentional and harmless.
 */
public final class SlaveTrustCache {

    /**
     * Master-authoritative trust flag for this slave: {@code null} = not yet
     * fetched (fail-closed at read time), {@code Boolean.TRUE}/{@code Boolean.FALSE}
     * = the last value the master returned via
     * {@code AsyncBankManager.isSlaveServerTrustedAsync}. Written by the
     * SLAVE_CONNECTION_ACCEPTED signal handler in
     * {@code StockMarketModBackend.onSlaveConnectionAccepted}; cleared by
     * SLAVE_CONNECTION_LOST. Read from the server thread by
     * {@code StockMarketModSettings.getClientSettings()}.
     */
    private static volatile Boolean cached = null;

    private SlaveTrustCache() {}

    /**
     * @return the cached trust flag, or {@code null} if the slave&rarr;master
     *         handshake has not yet completed since this JVM started (or
     *         since the last disconnect).
     */
    public static Boolean get() {
        return cached;
    }

    /**
     * Convenience read for the join-sync packet builder: returns the cached
     * value if known, or {@code false} (fail-closed default) if the cache is
     * still {@code null}. Clients that receive the fail-closed default are
     * corrected by a subsequent {@code SlaveTrustSyncPacket} once the cache
     * fills.
     *
     * @return the current trust flag with a fail-closed default of {@code false}
     */
    public static boolean getOrFalse() {
        Boolean v = cached;
        return v != null && v;
    }

    /**
     * Stores the master's authoritative answer. Returns {@code true} when the
     * value actually changed (or transitioned from {@code null}) so callers
     * can decide whether to fire a client broadcast.
     *
     * @param trusted the master's answer to
     *                {@code isSlaveServerTrustedAsync(getSlaveID())}
     * @return {@code true} if the cache differs from what it held before this
     *         call (including {@code null} &rarr; {@code Boolean.value}); useful
     *         to skip redundant broadcasts on reconnect churn.
     */
    public static boolean set(boolean trusted) {
        Boolean prev = cached;
        cached = trusted;
        return prev == null || prev != trusted;
    }

    /**
     * Invalidates the cache. Called when the slave loses its connection to the
     * master (BankSystem's {@code getSlaveConnectionLostSignal()}). The next
     * {@link #getOrFalse()} returns the fail-closed default until a fresh
     * handshake re-fetches the value.
     */
    public static void clear() {
        cached = null;
    }
}
