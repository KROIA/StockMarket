package net.kroia.stockmarket.networking.interserver.child;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.interserver.payload.BroadcastPayload;
import net.kroia.stockmarket.networking.interserver.payload.PacketForwardPayload;
import net.kroia.stockmarket.networking.interserver.payload.HubPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Runs on child servers — handles packets sent FROM the hub TO this child.
 *
 * When the hub routes a {@link BroadcastPayload} here, we display it
 * to all players currently on this child server.
 */
public class ChildInboundHandler extends SimpleChannelInboundHandler<HubPayload> {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }


    /** We need the MC server reference to broadcast messages to players. */
    private static MinecraftServer mcServer;

    private final HubConnector connector;

    public ChildInboundHandler(HubConnector connector) {
        this.connector = connector;
    }

    /**
     * Must be called once the MC server is available so we can push messages to players.
     * Call this from your SERVER_STARTED event after HubConnector.init().
     */
    public static void setMcServer(MinecraftServer server) {
        mcServer = server;
    }

    // ── Inbound packets from hub ──────────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HubPayload payload) {
        switch (payload) {

            // Hub routed a string message to this server — display it to players
            case BroadcastPayload bc -> {
                info("[HubMod] Received from hub: ["+bc.fromServer()+"] "+bc.senderName()+": "+bc.message());

                if (mcServer != null) {
                    // mcServer.execute() ensures we run on the MC main thread
                    mcServer.execute(() -> {
                        Component chat = Component.literal(
                                "§7[§b" + bc.fromServer() + "§7→§aThis Server§7] §f" +
                                        bc.senderName() + "§7: §e" + bc.message()
                        );
                        mcServer.getPlayerList().broadcastSystemMessage(chat, false);
                    });
                }
            }
            case PacketForwardPayload bb -> {
                info("[HubMod] bytes received from: "+bb.senderServerID()+" bytes: "+bb.data());
            }

            default ->
                    warn("[HubMod] Unhandled payload from hub: "+payload.getClass().getSimpleName());
        }
    }

    // ── Connection events ─────────────────────────────────────────────────────

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        warn("[HubMod] Lost connection to hub — scheduling reconnect...");
        connector.scheduleReconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        error("[HubMod] Exception in child inbound handler", cause);
        ctx.close();
    }



    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("HubMod/ChildInbound"+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/ChildInbound"+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("HubMod/ChildInbound"+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("HubMod/ChildInbound"+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("HubMod/ChildInbound"+message);
    }
}
