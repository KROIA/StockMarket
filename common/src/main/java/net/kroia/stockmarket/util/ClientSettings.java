package net.kroia.stockmarket.util;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client-side settings snapshot synced from the server at player join
 * (see {@code PlayerJoinSyncPacket}).
 * <p>
 * The server fills this object in {@code StockMarketModSettings.getClientSettings()}
 * and sends it once per join; the client stores it in
 * {@code StockMarketModBackend.ClientInstances.SETTINGS} where GUI code can query it
 * (e.g. via {@code StockMarketGuiScreen.isMasterServer()}).
 */
public class ClientSettings {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.dummy,
            ByteBufCodecs.BOOL, p -> p.isMasterServer,
            ByteBufCodecs.BOOL, p -> p.slaveTrusted,
            ClientSettings::new
    );

    boolean dummy;

    /**
     * True when the server the player is connected to is the MASTER server of a
     * master/slave multi-server setup (or a regular single server, which acts as
     * its own master). Slave servers send {@code false}.
     * <p>
     * Used client-side to gate master-only UI such as the "Mod Settings" button in
     * the ManagementScreen: only the master loads and owns {@code settings.json},
     * so editing settings from a slave is not possible.
     */
    boolean isMasterServer = false;

    /**
     * True when the master trusts this slave server (T-123 / T-125 / T-126).
     * Delivered via a two-phase-sync design because BankSystem's
     * {@code AsyncBankManager.isSlaveServerTrustedAsync(slaveID)} short-circuits
     * synchronously to {@code false} whenever the slave&rarr;master handshake
     * has not yet completed — so a blocking read at player-join time returns
     * inverted for a genuinely trusted slave (T-126 diagnostic).
     * <ul>
     *   <li>on the master: always {@code true} — a master implicitly trusts itself.</li>
     *   <li>on a slave: <b>phase 1</b> — at player join,
     *       {@code StockMarketModSettings.getClientSettings()} reads the current
     *       value of {@link net.kroia.stockmarket.util.SlaveTrustCache} (fail-closed
     *       default {@code false} while the handshake is still in progress). This
     *       value ships in the initial {@code PlayerJoinSyncPacket}. <b>Phase 2</b>
     *       — as soon as the slave&rarr;master handshake completes, BankSystem's
     *       {@code IBankSystemEvents#getSlaveConnectionAcceptedSignal()} fires;
     *       a listener in {@code StockMarketModBackend} issues a real
     *       (non-blocking) {@code isSlaveServerTrustedAsync} round-trip and, when
     *       it completes, populates the cache and broadcasts a
     *       {@code SlaveTrustSyncPacket} to every already-connected player. That
     *       packet arrives on the client main thread and calls
     *       {@link #setSlaveTrusted(boolean)}, transitioning a mis-defaulted
     *       {@code false} to the correct {@code true} within a tick.</li>
     * </ul>
     * <p>
     * Used client-side to gray out and label every mutating StockMarket UI action
     * (buy/sell, order cancel/move, plugin management, preset save, market create,
     * mod settings edit) when the client is connected to an untrusted slave. The
     * server independently enforces the same rule via
     * {@code NetworkGate.isMutatingCallAllowed(...)} — this flag is a UX
     * convenience only, not a security boundary.
     * <p>
     * <b>Live master-side trust toggle caveat (T-123, still deferred):</b> if an
     * admin runs {@code /banksystem trust <slaveID>} while a player is connected,
     * BankSystem exposes no trust-changed event so the flag does not refresh
     * mid-session. A reconnect (or the slave&rarr;master link cycling) picks it up
     * via the same SLAVE_CONNECTION_ACCEPTED path used at first connect.
     */
    boolean slaveTrusted = true;

    boolean fillMissingCandlesticks = true;

    public ClientSettings() {
    }
    public ClientSettings(boolean dummy) {
        this.dummy = dummy;
    }
    public ClientSettings(boolean dummy, boolean isMasterServer) {
        this.dummy = dummy;
        this.isMasterServer = isMasterServer;
    }
    public ClientSettings(boolean dummy, boolean isMasterServer, boolean slaveTrusted) {
        this.dummy = dummy;
        this.isMasterServer = isMasterServer;
        this.slaveTrusted = slaveTrusted;
    }

    /**
     * Copies all server-synced values from the received settings object into this
     * (client-held) instance. Called from the {@code PlayerJoinSyncPacket} handler.
     *
     * @param settings the settings object decoded from the join-sync packet
     */
    public void loadFrom(ClientSettings settings)
    {
        this.isMasterServer = settings.isMasterServer;
        this.slaveTrusted = settings.slaveTrusted;
    }

    /**
     * @return true if the server this client is connected to is the master server
     *         (see {@link #isMasterServer})
     */
    public boolean isMasterServer() {
        return isMasterServer;
    }

    /**
     * Server-side setter used when building the join-sync payload.
     *
     * @param isMasterServer whether this server is the master server
     */
    public void setMasterServer(boolean isMasterServer) {
        this.isMasterServer = isMasterServer;
    }

    /**
     * @return true if the master trusts this slave (see {@link #slaveTrusted});
     *         always true when the connected server IS the master
     */
    public boolean isSlaveTrusted() {
        return slaveTrusted;
    }

    /**
     * Server-side setter used when building the join-sync payload (T-123).
     *
     * @param slaveTrusted whether the master trusts this slave server
     */
    public void setSlaveTrusted(boolean slaveTrusted) {
        this.slaveTrusted = slaveTrusted;
    }

    public void setFillMissingCandlesticks(boolean fillMissingCandlesticks) {
        this.fillMissingCandlesticks = fillMissingCandlesticks;
    }
    public boolean isFillMissingCandlesticks() {
        return fillMissingCandlesticks;
    }

}
