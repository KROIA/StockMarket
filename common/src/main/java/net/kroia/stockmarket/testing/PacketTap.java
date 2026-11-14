package net.kroia.stockmarket.testing;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kroia.stockmarket.mixin.ConnectionAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.kroia.modutilities.networking.client_server.arrs.GenericRequestPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test helper: records the packets the server sends to one online player, and the ModUtilities requests it
 * receives from that player, through a temporary handler placed right before the connection's packet handler
 * (outbound it sees {@link Packet} objects before the encoder, inbound after the decoder; in sp and mp alike).
 * Netty runs it on its own thread, so callers wait with {@code pumpUntil}. Always close it
 * (try-with-resources), which removes the handler.
 */
public final class PacketTap implements AutoCloseable {

    private final Channel channel;
    private final String name = "stockmarket_test_packet_tap_" + System.nanoTime();
    private final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
    /** Received requests as (request type id, copy of the encoded input); the packet's buffer is freed after handling. */
    private final List<java.util.Map.Entry<String, byte[]>> requests = new CopyOnWriteArrayList<>();

    private PacketTap(Channel channel) {
        this.channel = channel;
        ChannelDuplexHandler handler = new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof Packet<?> p)
                    packets.add(p);
                super.write(ctx, msg, promise);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof ServerboundCustomPayloadPacket sp && sp.payload() instanceof GenericRequestPacket rq)
                    requests.add(java.util.Map.entry(rq.getRequestTypeID(), ByteBufUtil.getBytes(rq.getData())));
                super.channelRead(ctx, msg);
            }
        };
        if (channel.pipeline().get("packet_handler") != null)
            channel.pipeline().addBefore("packet_handler", name, handler);
        else
            channel.pipeline().addLast(name, handler);
    }

    /**
     * Starts recording what the server sends to {@code player}.
     *
     * @param server the running server
     * @param player an online player with a network connection
     * @return the tap, or null if the player's connection was not found
     */
    public static @Nullable PacketTap open(MinecraftServer server, ServerPlayer player) {
        if (server.getConnection() == null)
            return null;
        List<Connection> conns = server.getConnection().getConnections();
        synchronized (conns) {
            for (Connection c : conns)
                if (c.getPacketListener() == player.connection)
                    return new PacketTap(((ConnectionAccessor) c).stockmarket$getChannel());
        }
        return null;
    }

    /** @return the system chat lines sent so far */
    public List<Component> systemMessages() {
        return packets.stream().filter(p -> p instanceof ClientboundSystemChatPacket)
                .map(p -> ((ClientboundSystemChatPacket) p).content()).toList();
    }

    /**
     * @param key a translation key
     * @return the first system chat line sent so far with that key, or null
     */
    public @Nullable TranslatableContents findTranslatable(String key) {
        for (Component c : systemMessages())
            if (c.getContents() instanceof TranslatableContents tc && key.equals(tc.getKey()))
                return tc;
        return null;
    }

    /**
     * @param payloadType a custom payload class, e.g. a ModUtilities network packet
     * @return true if a custom payload packet of that type was sent so far
     */
    public boolean sawPayload(Class<?> payloadType) {
        return packets.stream().anyMatch(p -> p instanceof ClientboundCustomPayloadPacket cp
                && payloadType.isInstance(cp.payload()));
    }

    /**
     * @param requestTypeID a ModUtilities request type id ({@code getRequestTypeID()} of the request)
     * @return the encoded inputs of the requests of that type received so far, oldest first
     */
    public List<byte[]> receivedRequests(String requestTypeID) {
        return requests.stream().filter(e -> e.getKey().equals(requestTypeID)).map(java.util.Map.Entry::getValue).toList();
    }

    /** Removes the handler; safe if the channel closed meanwhile. */
    @Override
    public void close() {
        try {
            channel.pipeline().remove(name);
        } catch (RuntimeException ignored) {
            // channel closed meanwhile: the handler went with it
        }
    }
}
