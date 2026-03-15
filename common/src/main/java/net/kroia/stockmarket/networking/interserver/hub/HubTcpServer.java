package net.kroia.stockmarket.networking.interserver.hub;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.interserver.codec.HubPayloadDecoder;
import net.kroia.stockmarket.networking.interserver.codec.HubPayloadEncoder;
import net.kroia.stockmarket.networking.interserver.payload.HubPayload;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Netty TCP server that runs INSIDE the hub Minecraft server (as a mod).
 *
 * Lifecycle:
 *  - {@link #start(int, MinecraftServer)} called on SERVER_STARTED event
 *  - {@link #stop()}                      called on SERVER_STOPPING event
 *
 * Child servers connect here and register with a handshake.
 * Packets are then routed between children by {@link HubChildHandler}.
 */
public class HubTcpServer {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    /** All currently connected child servers, keyed by their serverId. */
    static final Map<String, Channel> CHILD_SERVERS = new ConcurrentHashMap<>();

    private static NioEventLoopGroup bossGroup;
    private static NioEventLoopGroup workerGroup;
    private static Channel serverChannel;

    // ── Start / Stop ─────────────────────────────────────────────────────────

    /**
     * Starts the TCP listener on {@code port}.
     * Safe to call from the MC server thread — Netty runs on its own threads.
     */
    public static void start(int port, MinecraftServer mcServer) {
        bossGroup  = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // ① Split the TCP stream into frames using a 3-byte length prefix
                                .addLast(new LengthFieldBasedFrameDecoder(1 << 21, 0, 3, 0, 3))
                                // ② Prepend a 3-byte length to every outgoing frame
                                .addLast(new LengthFieldPrepender(3))
                                // ③ bytes → HubPayload
                                .addLast(new HubPayloadDecoder())
                                // ④ HubPayload → bytes
                                .addLast(new HubPayloadEncoder())
                                // ⑤ Your routing logic
                                .addLast(new HubChildHandler(mcServer));
                    }
                });

        bootstrap.bind(port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                serverChannel = future.channel();
                info("[HubMod] Hub TCP listener started on port "+ port);
            } else {
                error("[HubMod] Failed to bind hub TCP port "+ port,
                        future.cause());
            }
        });
    }

    public static void stop() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup  != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        info("[HubMod] Hub TCP listener stopped.");
    }

    // ── Routing helpers ───────────────────────────────────────────────────────

    /** Send a payload to one specific child server. */
    public static void sendToChild(String serverId, HubPayload payload) {
        Channel ch = CHILD_SERVERS.get(serverId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(payload);
        } else {
            warn("[HubMod] sendToChild: server '"+serverId+"' not connected");
        }
    }

    /** Broadcast a payload to all connected child servers. */
    public static void broadcastToChildren(HubPayload payload) {
        broadcastToChildren(payload, null);
    }

    /** Broadcast to all children EXCEPT the one with {@code excludeServerId}. */
    public static void broadcastToChildren(HubPayload payload, String excludeServerId) {
        CHILD_SERVERS.forEach((id, ch) -> {
            if (!id.equals(excludeServerId) && ch.isActive()) {
                ch.writeAndFlush(payload);
            }
        });
    }


    protected static void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("HubMod/HubTcpServer"+message);
    }
    protected static void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubTcpServer"+message);
    }
    protected static void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubTcpServer"+message, throwable);
    }
    protected static void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("HubMod/HubTcpServer"+message);
    }
    protected static void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("HubMod/HubTcpServer"+message);
    }
}