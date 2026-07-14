package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

/**
 * One-way S2C broadcast informing every connected client that a market has been
 * deleted on the (master) server.
 * <p>
 * <b>Why a Packet and not a Request/Stream:</b> per the project's networking
 * conventions a market deletion is a server-initiated, fire-and-forget broadcast
 * to all clients — exactly the Packet use case. There is no continuous data
 * (Stream) and no client-initiated question (Request) involved.
 * <p>
 * <b>Propagation design:</b>
 * <ul>
 *   <li>The deletion itself always executes on the master server's
 *       {@code ServerMarketManager.deleteMarket} (slave servers and clients reach it
 *       through the existing ARRS delete request). That method calls
 *       {@link #broadcast(ItemID)} after removing the market.</li>
 *   <li>{@link #broadcast(ItemID)} sends the packet to all players connected to
 *       <i>this</i> server, and — when running as multi-server master — relays it to
 *       every slave server via {@code MultiServerManager}.</li>
 *   <li>Each slave receives it in {@link #handleOnSlave(ForwardPacketContext)} and
 *       re-sends it to the players connected to that slave, so slave-hosted clients
 *       are covered too.</li>
 *   <li>On the client, {@link #handleOnClient(NetworkManager.PacketContext)} (main
 *       thread) purges all cached state for the market
 *       ({@code ClientMarketManager.onMarketRemoved}) and notifies the currently
 *       open StockMarket screen so it can drop/deselect the dead market.</li>
 * </ul>
 */
public class MarketRemovedPacket extends StockMarketNetworkPacket {

    public static final Type<MarketRemovedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "market_removed_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketRemovedPacket> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.marketID,
            MarketRemovedPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The ItemID of the market that was deleted on the master server. */
    private final ItemID marketID;

    public MarketRemovedPacket(ItemID marketID) {
        this.marketID = marketID;
    }

    /**
     * Broadcasts the deletion of a market to every connected client.
     * Call on the server that executed the deletion (the master in a multi-server
     * setup). Sends to all locally connected players and relays to all slave
     * servers, which in turn notify their own players.
     *
     * @param marketID the market that was deleted
     */
    public static void broadcast(ItemID marketID) {
        MarketRemovedPacket packet = new MarketRemovedPacket(marketID);
        packet.sendToAllLocalClients();
        packet.broadcastToSlaves(); // no-op unless running as multi-server master
    }

    /**
     * Sends this packet to all players connected to the current server instance.
     * No-op when no server is available (e.g. during unit tests).
     */
    private void sendToAllLocalClients() {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null)
            return;
        sendToClients(server.getPlayerList().getPlayers());
    }

    /**
     * Client-side handler (runs on the client main thread — Architectury dispatches
     * S2C handlers there). Purges all client caches for the deleted market and lets
     * the currently open StockMarket screen react (deselect / refresh).
     */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES == null || BACKEND_CLIENT_INSTANCES.MARKET_MANAGER == null)
            return;
        // 1) Remove the ClientMarket, stop its price stream, evict cross-rate caches.
        BACKEND_CLIENT_INSTANCES.MARKET_MANAGER.onMarketRemoved(marketID);
        // 2) Notify the open GUI screen (TradeScreen / ManagementScreen) so a
        //    currently selected/dead market gets deselected gracefully.
        StockMarketClientHooks.notifyScreenMarketRemoved(marketID);
    }

    /**
     * Master→slave relay handler (runs on the slave server main thread).
     * The slave holds no market state of its own — it only forwards the
     * notification to the players connected to this slave server.
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        sendToAllLocalClients();
    }
}
