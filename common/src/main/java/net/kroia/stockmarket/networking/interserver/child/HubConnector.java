package net.kroia.stockmarket.networking.interserver.child;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.interserver.codec.HubPayloadDecoder;
import net.kroia.stockmarket.networking.interserver.codec.HubPayloadEncoder;
import net.kroia.stockmarket.networking.interserver.payload.PacketForwardPayload;
import net.kroia.stockmarket.networking.interserver.payload.HandshakePayload;
import net.kroia.stockmarket.networking.interserver.payload.HubPayload;
import net.kroia.stockmarket.networking.interserver.payload.StringMessagePayload;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages the outbound TCP connection from a child server to the hub.
 *
 * Lifecycle:
 *  - {@link #connect()} called on SERVER_STARTED
 *  - {@link #disconnect()} called on SERVER_STOPPING
 *  - Auto-reconnects if the hub goes down (5 second backoff)
 */
public class HubConnector {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    private static HubConnector INSTANCE;

    private final String hubHost;
    private final int    hubPort;
    private final String serverId;
    private final String sharedSecret;

    private NioEventLoopGroup group;
    private Channel channel;
    private volatile boolean shuttingDown = false;

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static void init(String hubHost, int hubPort, String serverId, String secret) {
        INSTANCE = new HubConnector(hubHost, hubPort, serverId, secret);
        INSTANCE.group = new NioEventLoopGroup();
        INSTANCE.connect();
    }

    public static HubConnector get() { return INSTANCE; }

    private HubConnector(String hubHost, int hubPort, String serverId, String secret) {
        this.hubHost      = hubHost;
        this.hubPort      = hubPort;
        this.serverId     = serverId;
        this.sharedSecret = secret;
    }

    // ── Connect / Disconnect ──────────────────────────────────────────────────

    void connect() {
        if (shuttingDown) return;

        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // ① Frame splitter
                                .addLast(new LengthFieldBasedFrameDecoder(1 << 21, 0, 3, 0, 3))
                                // ② Frame length prepender
                                .addLast(new LengthFieldPrepender(3))
                                // ③ bytes → HubPayload
                                .addLast(new HubPayloadDecoder())
                                // ④ HubPayload → bytes
                                .addLast(new HubPayloadEncoder())
                                // ⑤ Handle packets received FROM the hub
                                .addLast(new ChildInboundHandler(HubConnector.this));
                    }
                });

        bootstrap.connect(hubHost, hubPort).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                channel = future.channel();
                info("[HubMod] Connected to hub at "+hubHost+":" + hubPort);
                // Immediately authenticate with the hub
                sendToHub(new HandshakePayload(serverId, sharedSecret));
            } else {
                warn("[HubMod] Could not connect to hub — retrying in 5s...");
                scheduleReconnect();
            }
        });
    }

    public void scheduleReconnect() {
        if (!shuttingDown) {
            group.schedule(this::connect, 5, TimeUnit.SECONDS);
        }
    }

    public void disconnect() {
        shuttingDown = true;
        if (channel != null) channel.close();
        group.shutdownGracefully();
        info("[HubMod] Disconnected from hub.");
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Send any {@link HubPayload} to the hub.
     * Thread-safe — Netty queues the write internally.
     */
    public void sendToHub(HubPayload payload) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(payload).addListener(f -> {
                if (!f.isSuccess()) {
                    error("[HubMod] Failed to send "+payload.getClass().getSimpleName()+" to hub", f.cause());
                }
            });
        } else {
            warn("[HubMod] Cannot send — not connected to hub.");
        }
    }

    /**
     * Convenience: send a string message from a player to a specific target server.
     * Use {@code targetServer = null} to broadcast to all servers.
     */
    public void sendString(String playerName, String message, String targetServer) {
        sendToHub(new StringMessagePayload(
                playerName,
                serverId,
                targetServer != null ? targetServer : "",
                message
        ));
    }

    /*public void forwardPacket(UUID playerID, String targetServer, NetworkPacket packet)
    {
        sendToHub(new PacketForwardPayload(
                playerID,
                serverId,
                targetServer != null ? targetServer : "",
                packet
        ));
    }*/

    public String getServerId() { return serverId; }


    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("HubMod/HubConnector"+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubConnector"+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubConnector"+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("HubMod/HubConnector"+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("HubMod/HubConnector"+message);
    }
}