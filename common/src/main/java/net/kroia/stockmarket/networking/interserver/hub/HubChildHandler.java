package net.kroia.stockmarket.networking.interserver.hub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.interserver.config.ModConfig;
import net.kroia.stockmarket.networking.interserver.payload.*;
import net.kroia.stockmarket.networking.packet.TestPacket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Handles incoming TCP connections from child servers on the hub.
 *
 * One instance of this handler is created per connected child.
 * Routing logic:
 *  - {@link HandshakePayload}     → authenticate + register the child
 *  - {@link StringMessagePayload} → route to target or broadcast, also show on hub
 */
public class HubChildHandler extends SimpleChannelInboundHandler<HubPayload> {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }
    private final MinecraftServer mcServer;

    /** Set after a successful handshake. Null until then. */
    private String serverId;

    public HubChildHandler(MinecraftServer mcServer) {
        this.mcServer = mcServer;
    }

    // ── Channel events ────────────────────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HubPayload payload) {
        switch (payload) {

            // ── Handshake: child server identifies itself ─────────────────────
            case HandshakePayload hs -> {
                if (!hs.token().equals(ModConfig.get().sharedSecret)) {
                    warn("[HubMod] Rejected connection from '"+hs.serverId()+"' — bad token");
                    ctx.close();
                    return;
                }
                serverId = hs.serverId();
                HubTcpServer.CHILD_SERVERS.put(serverId, ctx.channel());
                info("[HubMod] Child server '"+serverId+"' connected and authenticated.");
            }

            // ── String message: route between servers ─────────────────────────
            case StringMessagePayload msg -> {
                if (serverId == null) {
                    warn("[HubMod] Received StringMessage before handshake — closing.");
                    ctx.close();
                    return;
                }

                info("[HubMod] Message from '"+ msg.fromServer()+"' (player: "+ msg.senderName()+
                                ") → target='"+(msg.isBroadcast() ? "ALL" : msg.targetServer())+"': "+msg.message());

                // Show the message to players on the hub server itself
                mcServer.execute(() -> {
                    Component chat = Component.literal(
                            "§7[§b" + msg.fromServer() + "§7] §f" +
                                    msg.senderName() + "§7: §e" + msg.message()
                    );
                    mcServer.getPlayerList().broadcastSystemMessage(chat, false);
                });

                // Route: to a specific child, or broadcast to all children
                if (msg.isBroadcast()) {
                    // Wrap as BroadcastPayload so receiving children know the source
                    BroadcastPayload bc = new BroadcastPayload(
                            msg.senderName(), msg.fromServer(), msg.message());
                    HubTcpServer.broadcastToChildren(bc, serverId); // skip sender
                } else {
                    // Direct route to the named child
                    BroadcastPayload bc = new BroadcastPayload(
                            msg.senderName(), msg.fromServer(), msg.message());
                    HubTcpServer.sendToChild(msg.targetServer(), bc);
                }
            }
            case PacketForwardPayload bb -> {
                if (serverId == null) {
                    warn("[HubMod] Received StringMessage before handshake — closing.");
                    ctx.close();
                    return;
                }
                info("Received PacketForwardPayload from child server: "+bb.senderServerID());

                ResourceLocation testPacketResouceeLoc = TestPacket.TYPE.id();
                ResourceLocation packetResouceLoc = bb.packetType();
                if(testPacketResouceeLoc.equals(packetResouceLoc))
                {
                    RegistryAccess reg = mcServer.registryAccess();
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bb.data()), reg);

                    TestPacket dummy = TestPacket.STREAM_CODEC.decode(buf);
                    TestPacket.HANDLER.handleServerRedirection(dummy, ctx);
                }

            }

            default ->
                    warn("[HubMod] Unhandled payload type: "+ payload.getClass().getSimpleName());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (serverId != null) {
            HubTcpServer.CHILD_SERVERS.remove(serverId);
            info("[HubMod] Child server '"+serverId+"' disconnected.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        error("[HubMod] Exception in child handler for '"+ serverId+"'", cause);
        ctx.close();
    }

    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("HubMod/HubChildHandler"+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubChildHandler"+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/HubChildHandler"+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("HubMod/HubChildHandler"+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("HubMod/HubChildHandler"+message);
    }
}