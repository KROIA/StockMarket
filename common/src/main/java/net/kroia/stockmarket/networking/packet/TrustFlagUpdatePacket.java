package net.kroia.stockmarket.networking.packet;

import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.SlaveTrustCache;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * One-way <b>master&rarr;slave</b> (S2S) packet delivering the current
 * "is this slave trusted?" flag to the affected slave whenever the master's
 * trust set changes at runtime (T-128).
 * <p>
 * <b>Why a Packet and not a Request/Stream:</b> per the project's networking
 * conventions this is a master-initiated, fire-and-forget push — exactly the
 * Packet use case. The payload carries the authoritative post-mutation value
 * directly, so the slave never needs to round-trip back to the master to
 * confirm.
 * <p>
 * <b>When it is sent:</b> {@code StockMarketModBackend}'s master branch
 * subscribes to BankSystem's {@code IBankSystemEvents#getTrustChangedSignal()};
 * whenever that event fires, the master dispatches this packet to the slave
 * named in the event payload via {@code MultiServerManager.sendToSlave}. Slaves
 * that are not currently connected simply miss the push and pick up the correct
 * trust value at their next handshake via T-126's
 * {@code SLAVE_CONNECTION_ACCEPTED} wiring.
 * <p>
 * <b>Slave-side effect:</b> the handler calls {@link SlaveTrustCache#set(boolean)}
 * and — if the value actually changed — re-broadcasts {@link SlaveTrustSyncPacket}
 * to every currently-connected client, reusing T-126's existing client-broadcast
 * plumbing. Clients update their {@code ClientSettings.slaveTrusted} within a
 * tick and their gated UI (trade / management / plugin / create-market screens)
 * re-renders in the correct state without a reconnect.
 */
public class TrustFlagUpdatePacket extends StockMarketNetworkPacket {

    public static final Type<TrustFlagUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "trust_flag_update_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrustFlagUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.trusted,
            TrustFlagUpdatePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The master's authoritative post-mutation trust value for the target slave. */
    private final boolean trusted;

    public TrustFlagUpdatePacket(boolean trusted) {
        this.trusted = trusted;
    }

    /**
     * (Master only) Sends this packet to the slave with the given ID via
     * {@link net.kroia.modutilities.networking.multi_server.MultiServerManager#sendToSlave(String, CustomPacketPayload)}.
     * Silently no-ops if this JVM is not the master, if the slave is not
     * currently connected, or if the multi-server layer is not running — in all
     * of those cases the next {@code SLAVE_CONNECTION_ACCEPTED} handshake
     * populates the slave's cache with the current authoritative value anyway
     * (T-126 wiring).
     *
     * @param slaveID the ID of the slave to notify
     * @param trusted the post-mutation trust value from BankSystem
     */
    public static void sendTo(String slaveID, boolean trusted) {
        new TrustFlagUpdatePacket(trusted).sendToSlave(slaveID);
    }

    /**
     * Slave-side handler: refresh the local trust cache and, if the value
     * actually changed, re-broadcast the client-side {@link SlaveTrustSyncPacket}
     * so every connected player's UI transitions to the correct state within a
     * tick. Runs on the Netty event-loop thread — both {@link SlaveTrustCache#set(boolean)}
     * (single volatile write) and {@code SlaveTrustSyncPacket.broadcast} (queues
     * via Architectury's {@code NetworkManager}) are safe from arbitrary threads.
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        boolean changed = SlaveTrustCache.set(trusted);
        if (changed) {
            SlaveTrustSyncPacket.broadcast(trusted);
        }
    }
}
