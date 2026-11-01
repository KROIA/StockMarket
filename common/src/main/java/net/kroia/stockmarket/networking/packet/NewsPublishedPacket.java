package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

/**
 * One-way S2C broadcast informing every connected client that a news event has been
 * published on the (master) server (NewsEventSystem plan §3, task T-073).
 * <p>
 * <b>Why a Packet and not a Request/Stream:</b> per the project's networking
 * conventions a news publish is a server-initiated, fire-and-forget broadcast to all
 * clients — exactly the (rare) Packet use case. Missed publishes (offline players,
 * slave players who joined mid-event) are covered by the paginated
 * {@code NewsHistoryRequest} the newspaper screen fires on open.
 * <p>
 * <b>Propagation design (cloned from {@link MarketRemovedPacket}):</b>
 * <ul>
 *   <li>Publishing always happens on the master server: the NewsPlugin builds the
 *       {@link NewsRecord} and hands it to the {@code ServerNewsPublisher}, which
 *       appends it to the history (T-072) and then calls {@link #broadcast(NewsRecord)}.</li>
 *   <li>{@link #broadcast(NewsRecord)} sends the packet to all players connected to
 *       <i>this</i> server, and — when running as multi-server master — relays it to
 *       every slave server via {@code MultiServerManager}.</li>
 *   <li>Each slave receives it in {@link #handleOnSlave(ForwardPacketContext)} and
 *       re-sends it to the players connected to that slave, so slave-hosted clients
 *       are covered too.</li>
 *   <li>On the client, {@link #handleOnClient(NetworkManager.PacketContext)} (main
 *       thread) appends the record to the per-connection
 *       {@code ClientNewsCache} so the newspaper screen can show it instantly,
 *       background-prefetches the record's picture hash into the
 *       {@code ClientNewsPictureCache} (T-090), and
 *       shows the T-074 headline toast <b>only</b> for players who opted in via the
 *       newspaper screen checkbox (default off). <b>No chat message, no sound</b> —
 *       by design the opt-in toast is the only push notification.</li>
 * </ul>
 * <p>
 * Payload note: the record carries its full inline translation maps; records are
 * KB-scale, far below the 1 MiB S2C custom-payload cap — no chunking needed.
 */
public class NewsPublishedPacket extends StockMarketNetworkPacket {

    public static final Type<NewsPublishedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "news_published_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NewsPublishedPacket> STREAM_CODEC = StreamCodec.composite(
            NewsRecord.STREAM_CODEC, p -> p.record,
            NewsPublishedPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The news record that was published on the master server. */
    private final NewsRecord record;

    public NewsPublishedPacket(NewsRecord record) {
        this.record = record;
    }

    /**
     * Broadcasts a freshly published news record to every connected client.
     * Call on the server that executed the publish (the master in a multi-server
     * setup — the NewsPlugin only runs there). Sends to all locally connected players
     * and relays to all slave servers, which in turn notify their own players.
     *
     * @param record the record that was published (and already appended to the history)
     */
    public static void broadcast(NewsRecord record) {
        if (record == null)
            return;
        NewsPublishedPacket packet = new NewsPublishedPacket(record);
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
     * S2C handlers there). Appends the record to the per-connection
     * {@code ClientNewsCache}; the newspaper screen picks it up via the cache's
     * change listener. The only visible effect is the opt-in headline toast
     * (default off) — no chat message, no sound by design.
     */
    @Override
    protected void handleOnClient(NetworkManager.PacketContext context) {
        if (BACKEND_CLIENT_INSTANCES == null || BACKEND_CLIENT_INSTANCES.NEWS_CACHE == null)
            return;
        BACKEND_CLIENT_INSTANCES.NEWS_CACHE.add(record);
        // T-090: background-prefetch the record's newspaper picture so it is (very
        // likely) already loaded when the player opens the newspaper. prefetch() is
        // null-hash-safe, so text-only records need no guard of their own.
        if (BACKEND_CLIENT_INSTANCES.NEWS_PICTURE_CACHE != null)
            BACKEND_CLIENT_INSTANCES.NEWS_PICTURE_CACHE.prefetch(record.getPictureHash());
        // T-074 opt-in toast: shown only for players who enabled news popups in the
        // newspaper screen (PlayerPreferences.newsToastEnabled, default off). Everyone
        // else gets no notification at all — no chat message by design.
        StockMarketClientHooks.showNewsToastIfEnabled(record);
    }

    /**
     * Master→slave relay handler (runs on the slave server main thread).
     * The slave holds no news state of its own — it only forwards the
     * notification to the players connected to this slave server.
     */
    @Override
    protected void handleOnSlave(ForwardPacketContext context) {
        sendToAllLocalClients();
    }
}
