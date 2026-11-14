package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.ExtraCodecUtils;
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
 * One-way S2C broadcast telling every connected client that a market changed on the (master)
 * server (SM-310): it was created, deleted, opened or closed (manual override or trading-hours
 * schedule), or entered or left maintenance. Replaces the former {@code MarketCreatedPacket}
 * (SM-304) and {@code MarketRemovedPacket}.
 * <p>
 * <b>Propagation design:</b>
 * <ul>
 *   <li>Every change is decided on the master's {@code ServerMarketManager}: creation in
 *       {@code createMarket}/{@code createMarketWithConfig}, deletion in {@code deleteMarket},
 *       and open/closed/maintenance by an edge detector at the end of its update tick, which
 *       catches every source (admin toggle, settings panel, BankSystem, schedule) in one place.</li>
 *   <li>{@link #broadcast(ItemID, Kind)} sends to all players on <i>this</i> server and relays to
 *       every slave, which re-sends it to its own players in {@link #handleOnSlave(ForwardPacketContext)}.</li>
 *   <li>On the client, {@link #handleOnClient(NetworkManager.PacketContext)} hands it to
 *       {@code ClientMarketManager.onMarketChanged}, which updates the cache and notifies the
 *       open StockMarket screen.</li>
 * </ul>
 * The open/closed/maintenance state of a market a screen displays also arrives through its
 * {@code MarketPriceStream} (~100 ms); this packet reaches every client for every market,
 * subscribed or not (e.g. the maintenance markers of the management market grid).
 */
public class MarketChangedPacket extends StockMarketNetworkPacket {

    /** What changed about the market. */
    public enum Kind {
        /** The market was created; the client adds it to its cache. */
        ADDED,
        /** The market was deleted; the client purges it. */
        REMOVED,
        /** The market opened for trading (schedule or manual override). */
        OPENED,
        /** The market closed for trading (schedule or manual override). */
        CLOSED,
        /** The market entered maintenance. */
        MAINTENANCE_ON,
        /** The market left maintenance. */
        MAINTENANCE_OFF
    }

    public static final Type<MarketChangedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "market_changed_packet")); // mc<=1.21.10
    //public static final Type<MarketChangedPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "market_changed_packet")); // mc>=1.21.11

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketChangedPacket> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.marketID,
            ExtraCodecUtils.enumStreamCodec(Kind.class), p -> p.kind,
            MarketChangedPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The market that changed. */
    private final ItemID marketID;
    /** What changed. */
    private final Kind kind;

    public MarketChangedPacket(ItemID marketID, Kind kind) {
        this.marketID = marketID;
        this.kind = kind;
    }

    /**
     * Broadcasts a market change to every connected client. Call on the server that made the
     * change (the master in a multi-server setup), after the change is applied. Sends to all
     * locally connected players and relays to all slave servers, which notify their own players.
     *
     * @param marketID the market that changed
     * @param kind     what changed
     */
    public static void broadcast(ItemID marketID, Kind kind) {
        MarketChangedPacket packet = new MarketChangedPacket(marketID, kind);
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

    /** Client-side handler (client main thread): applies the change to the client cache and open screen. */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES == null || BACKEND_CLIENT_INSTANCES.MARKET_MANAGER == null)
            return;
        BACKEND_CLIENT_INSTANCES.MARKET_MANAGER.onMarketChanged(marketID, kind);
    }

    /**
     * Master→slave relay handler (slave server main thread). The slave holds no market state
     * of its own; it only forwards the notification to its players.
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        sendToAllLocalClients();
    }
}
