package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

/**
 * One-way S2C update of the {@code ClientSettings.slaveTrusted} flag used by
 * StockMarket's client-side UI gating (T-123 / T-125 / T-126).
 * <p>
 * <b>Why a Packet:</b> per the project's networking conventions this is a
 * server-initiated fire-and-forget broadcast (rare packet use case). There is
 * no client-side question involved (Request) and no continuous data (Stream).
 * <p>
 * <b>When it fires:</b> The trust flag lives on the master in BankSystem's
 * {@code ServerBankManager.trustedSlaveServers}. On a slave-server JVM the
 * value is fetched via
 * {@link net.kroia.banksystem.api.bankmanager.IAsyncBankManager#isSlaveServerTrustedAsync(String)}
 * once the slave&rarr;master handshake completes (BankSystem's
 * {@code IBankSystemEvents#getSlaveConnectionAcceptedSignal()}), cached on the
 * slave in {@link net.kroia.stockmarket.util.SlaveTrustCache}, and then
 * broadcast via this packet to every player currently connected to the slave.
 * <p>
 * The initial {@code PlayerJoinSyncPacket} carries the current cache value at
 * join time; a client that joined <b>before</b> the handshake completed
 * receives the fail-closed default ({@code slaveTrusted=false}) and is
 * corrected to the true value by this follow-up packet within a tick.
 * <p>
 * On the master, this packet is never sent — the master's join-sync unconditionally
 * carries {@code slaveTrusted=true} and there is no cache to fill.
 * <p>
 * <b>Not registered with MultiServerPacketRegistry:</b> the packet is purely
 * slave&rarr;client on this JVM. No master&rarr;slave relay is needed — the
 * master does not participate in a slave's trust broadcast.
 */
public class SlaveTrustSyncPacket extends StockMarketNetworkPacket {

    public static final Type<SlaveTrustSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "slave_trust_sync_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlaveTrustSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.trusted,
            SlaveTrustSyncPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The master's authoritative "is this slave trusted?" answer, as cached on the slave. */
    private final boolean trusted;

    public SlaveTrustSyncPacket(boolean trusted) {
        this.trusted = trusted;
    }

    /**
     * Sends this packet to every player currently connected to this
     * (slave) server. Called from
     * {@code StockMarketModBackend.onSlaveConnectionAccepted} after the trust
     * cache has been refreshed with the master's answer.
     * <p>
     * No-op when no server is available (e.g. during unit tests) or when the
     * server has no online players (broadcast lands nowhere — subsequent joins
     * pick up the value from the cache via {@code PlayerJoinSyncPacket}).
     *
     * @param trusted the master's authoritative trust flag for this slave
     */
    public static void broadcast(boolean trusted) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null)
            return;
        SlaveTrustSyncPacket packet = new SlaveTrustSyncPacket(trusted);
        packet.sendToClients(server.getPlayerList().getPlayers());
    }

    /**
     * Client-side handler (runs on the client main thread — Architectury
     * dispatches S2C handlers there). Updates {@code ClientSettings.slaveTrusted}
     * via the existing setter so every UI gate consulting
     * {@code isSlaveTrusted()} re-reads the new value on its next frame.
     * <p>
     * Guarded against a very-early arrival: if the client just joined and the
     * {@code ClientInstances} object has not been created yet by
     * {@code onPlayerJoinClientSide}, the packet is dropped — the initial
     * {@code PlayerJoinSyncPacket} that eventually populates it carries the
     * same cached value we would have written here, so no state is lost.
     */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES == null || BACKEND_CLIENT_INSTANCES.SETTINGS == null)
            return;
        BACKEND_CLIENT_INSTANCES.SETTINGS.setSlaveTrusted(trusted);
    }
}
