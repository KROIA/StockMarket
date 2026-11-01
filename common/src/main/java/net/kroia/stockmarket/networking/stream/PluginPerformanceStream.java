package net.kroia.stockmarket.networking.stream;

import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.UUID;

/**
 * Server-to-client stream that delivers the master server's per-tick plugin
 * timing snapshot (one payload per broadcast, covering every plugin's update
 * and finalize pass — see {@link PluginPerformanceSnapshot}) to trusted admin
 * clients.
 * <p>
 * <b>Context data (IN):</b> {@link Byte} — dummy, because the stream is
 * manager-wide and the requesting player is identified via
 * {@link #getRequestorPlayerUUID()}.
 * <p>
 * <b>Stream data (OUT):</b> {@link PluginPerformanceSnapshot} — the full
 * per-plugin timing snapshot serialised as one payload.
 * <p>
 * <b>Cadence.</b> The server tick loop feeds fresh timings into
 * {@code ServerPluginManager}'s tracker every tick (20&nbsp;Hz), but this
 * stream only serialises + sends the snapshot every
 * {@value #UPDATE_INTERVAL_MS}&nbsp;ms — matching the runtime-data stream's
 * 500&nbsp;ms cadence. The plugin timing bar in the management UI does not
 * need per-tick refresh; the smoothed rolling average already comes from a
 * 1&nbsp;s window on the server side.
 * <p>
 * <b>Trust + admin gating.</b> Mirrors {@link PluginRuntimeDataStream}: the
 * feed is management data and must never reach an untrusted slave or a
 * non-admin subscriber. Both checks are re-queried live on every tick so a
 * runtime {@code /banksystem untrust <slaveID>} or an admin flag change
 * tears the feed down within one interval.
 * <p>
 * <b>Slave behaviour.</b> Plugin timing is collected on the master (where the
 * plugin update loop actually runs). This stream inherits the standard
 * master-routing behaviour from {@link StockMarketGenericStream}
 * (via {@link #needsRoutingToMaster()} on the base class) so a subscription
 * made from a slave-connected client is routed to the master, then the
 * per-tick trust check gates delivery.
 */
public class PluginPerformanceStream extends StockMarketGenericStream<Byte, PluginPerformanceSnapshot> {

    /** Broadcast cadence in milliseconds — mirrors {@link PluginRuntimeDataStream}. */
    private static final long UPDATE_INTERVAL_MS = 500L;

    private long lastTimeMs = System.currentTimeMillis();
    private PluginPerformanceSnapshot lastSnapshot = PluginPerformanceSnapshot.empty();

    @Override
    public GenericStream<Byte, PluginPerformanceSnapshot> copy() {
        return new PluginPerformanceStream();
    }

    @Override
    public String getStreamTypeID() {
        return PluginPerformanceStream.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        lastTimeMs = 0L; // force an immediate first broadcast on next tick
    }

    @Override
    public void onStopStreamSendingOnServer() {
        info("PluginPerformanceStream stopped");
    }

    @Override
    protected void updateOnServer() {
        // Trust gate — mirrors PluginRuntimeDataStream: untrusted slaves never
        // receive a payload; a mid-session "/banksystem untrust <slaveID>" tears
        // the feed down within one tick because trust is re-queried live.
        if (!isStreamTrustAllowed("PluginPerformanceStream")) {
            stopStream();
            return;
        }

        // Admin gate — plugin timings are management data. Fail closed on a
        // missing/unresolvable requestor UUID, and drop the feed for a
        // non-admin subscriber.
        UUID req = getRequestorPlayerUUID();
        if (req == null || !playerIsAdmin(req)) {
            stopStream();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTimeMs < UPDATE_INTERVAL_MS) return;
        lastTimeMs = now;

        IServerPluginManager mgr = getPluginManager();
        if (!(mgr instanceof ServerPluginManager serverMgr)) {
            stopStream();
            return;
        }

        lastSnapshot = serverMgr.buildPerformanceSnapshot();
        sendPacket();
    }

    @Override
    public PluginPerformanceSnapshot provideStreamPacketOnServer() {
        return lastSnapshot;
    }

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buf, Byte contextData) {
        ByteBufCodecs.BYTE.encode(buf, contextData);
    }

    @Override
    public Byte decodeContextData(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BYTE.decode(buf);
    }

    @Override
    public void encodeData(RegistryFriendlyByteBuf buf, PluginPerformanceSnapshot data) {
        PluginPerformanceSnapshot.STREAM_CODEC.encode(buf, data);
    }

    @Override
    public PluginPerformanceSnapshot decodeData(RegistryFriendlyByteBuf buf) {
        return PluginPerformanceSnapshot.STREAM_CODEC.decode(buf);
    }
}
