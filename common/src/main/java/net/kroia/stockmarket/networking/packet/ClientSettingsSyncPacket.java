package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * T-140: one-way S2C broadcast of the master-owned settings a client reads, sent when an admin
 * changes one of them through {@code ModSettingsRequest} (SET).
 * <p>
 * <b>Why a Packet:</b> a master-initiated, fire-and-forget broadcast. The join-time value comes
 * from {@code ServerTimeRequest} (which every client sends once per join and which is routed to the
 * master); this packet only covers players that are already online when the value changes.
 * <p>
 * <b>Propagation</b> (same shape as {@link MarketRemovedPacket}): {@link #broadcast(int)} sends to
 * every player of this (master) server and relays to every slave, whose
 * {@link #handleOnSlave(ForwardPacketContext)} re-sends it to the slave's own players. Slaves never
 * read the value themselves.
 * <p>
 * Currently carries only {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES}; add a field here (and in
 * {@code ServerTimeRequest.OutputData}) when another master-owned setting must reach clients.
 */
public class ClientSettingsSyncPacket extends StockMarketNetworkPacket {

    public static final Type<ClientSettingsSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "client_settings_sync_packet")); // mc<=1.21.10
            //Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "client_settings_sync_packet")); // mc>=1.21.11

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettingsSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, p -> p.initialLoadCandles,
            ClientSettingsSyncPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The master's {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES}. */
    private final int initialLoadCandles;

    /**
     * @param initialLoadCandles the master's {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES}
     */
    public ClientSettingsSyncPacket(int initialLoadCandles) {
        this.initialLoadCandles = initialLoadCandles;
    }

    /**
     * Master side, after a SET: broadcasts the new value only if it changed.
     *
     * @param before the value before the SET was applied
     * @param after  the value after apply + sanitize
     * @return {@code true} if a broadcast was sent
     */
    public static boolean broadcastIfChanged(int before, int after) {
        if (before == after)
            return false;
        broadcast(after);
        return true;
    }

    /**
     * Sends the value to every player of this server and relays it to every slave
     * (no-op unless this server is a multi-server master).
     *
     * @param initialLoadCandles the master's {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES}
     */
    public static void broadcast(int initialLoadCandles) {
        ClientSettingsSyncPacket packet = new ClientSettingsSyncPacket(initialLoadCandles);
        packet.sendToAllLocalClients();
        packet.broadcastToSlaves();
    }

    /** Sends this packet to all players of this server; no-op without a server. */
    private void sendToAllLocalClients() {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null)
            return;
        sendToClients(server.getPlayerList().getPlayers());
    }

    /**
     * Writes the carried values into the given client settings.
     *
     * @param settings the client's settings; ignored if {@code null}
     */
    public void applyTo(@Nullable ClientSettings settings) {
        if (settings != null)
            settings.setInitialLoadCandles(initialLoadCandles);
    }

    /**
     * Client handler (main thread). Dropped if the client instances are not built yet; the
     * join-time {@code ServerTimeRequest} then delivers the current value anyway.
     */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES != null)
            applyTo(BACKEND_CLIENT_INSTANCES.SETTINGS);
    }

    /** Master &rarr; slave relay: the slave forwards the packet to its own players. */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        sendToAllLocalClients();
    }
}
