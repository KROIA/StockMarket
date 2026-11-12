package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

/**
 * One-way S2C broadcast informing every connected client that a market has been
 * created on the (master) server (SM-304). Counterpart of {@link MarketRemovedPacket}
 * and cloned from it in shape.
 * <p>
 * Without it a client only learned about new markets through
 * {@code ClientMarketManager.requestMarkets()} (join, screen open), so an already open
 * market screen never showed a market created by someone else.
 * <p>
 * <b>Propagation design:</b>
 * <ul>
 *   <li>Creation always executes on the master's {@code ServerMarketManager.createMarket}
 *       / {@code createMarketWithConfig} (slaves reach them through the ARRS CreateMarket
 *       request; BankSystem through {@code StockMarketIntegrationImpl.openMarket}). Both
 *       call {@link #broadcast(ItemID)} after the market is in the market map, so a
 *       client {@code requestMarkets()} racing the packet already sees it.</li>
 *   <li>{@link #broadcast(ItemID)} sends to all players on <i>this</i> server and relays
 *       to every slave, which re-sends it to its own players in
 *       {@link #handleOnSlave(ForwardPacketContext)}.</li>
 *   <li>On the client, {@link #handleOnClient(NetworkManager.PacketContext)} hands the ID to
 *       {@code ClientMarketManager.onMarketAdded}, which creates the ClientMarket and
 *       notifies the open StockMarket screen.</li>
 * </ul>
 */
public class MarketCreatedPacket extends StockMarketNetworkPacket {

    public static final Type<MarketCreatedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "market_created_packet")); // mc<=1.21.10
    //public static final Type<MarketCreatedPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "market_created_packet")); // mc>=1.21.11

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketCreatedPacket> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.marketID,
            MarketCreatedPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The ItemID of the market that was created on the master server. */
    private final ItemID marketID;

    public MarketCreatedPacket(ItemID marketID) {
        this.marketID = marketID;
    }

    /**
     * Broadcasts the creation of a market to every connected client.
     * Call on the server that created it (the master in a multi-server setup), after
     * the market exists. Sends to all locally connected players and relays to all slave
     * servers, which in turn notify their own players.
     *
     * @param marketID the market that was created
     */
    public static void broadcast(ItemID marketID) {
        MarketCreatedPacket packet = new MarketCreatedPacket(marketID);
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
     * Client-side handler (client main thread). Adds the market to the client cache;
     * the manager notifies the open screen once the ClientMarket exists.
     */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES == null || BACKEND_CLIENT_INSTANCES.MARKET_MANAGER == null)
            return;
        BACKEND_CLIENT_INSTANCES.MARKET_MANAGER.onMarketAdded(marketID);
    }

    /**
     * Master→slave relay handler (slave server main thread). The slave holds no market
     * state of its own; it only forwards the notification to its players.
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        sendToAllLocalClients();
    }
}
