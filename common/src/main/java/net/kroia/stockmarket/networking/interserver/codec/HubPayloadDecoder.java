package net.kroia.stockmarket.networking.interserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import net.kroia.stockmarket.networking.interserver.payload.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Converts raw bytes received over TCP back into {@link HubPayload} objects.
 *
 * This decoder runs AFTER {@code LengthFieldBasedFrameDecoder}, which means
 * by the time this runs the {@code ByteBuf} already contains exactly one
 * complete frame — no need to worry about partial reads.
 */
public class HubPayloadDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) return;

        int packetId = in.readByte() & 0xFF;

        HubPayload payload = switch (packetId) {
            case PacketIds.HANDSHAKE -> new HandshakePayload(
                    readString(in),  // serverId
                    readString(in)   // token
            );
            case PacketIds.STRING_MESSAGE -> {
                String senderName  = readString(in);
                String fromServer  = readString(in);
                String target      = readString(in);
                String message     = readString(in);
                yield new StringMessagePayload(
                        senderName,
                        fromServer,
                        target.isBlank() ? null : target,
                        message
                );
            }
            case PacketIds.BROADCAST -> new BroadcastPayload(
                    readString(in),  // senderName
                    readString(in),  // fromServer
                    readString(in)   // message
            );
            case PacketIds.BYTE_BUFFER -> new PacketForwardPayload(
                    UUIDUtil.STREAM_CODEC.decode(in),
                    ByteBufCodecs.STRING_UTF8.decode(in),
                    ByteBufCodecs.STRING_UTF8.decode(in),
                    ResourceLocation.STREAM_CODEC.decode(in),
                    ByteBufCodecs.BYTE_ARRAY.decode(in)
            );
            default -> throw new DecoderException(
                    "Unknown hub packet ID: 0x" + Integer.toHexString(packetId));
        };

        out.add(payload); // passes decoded object to the next pipeline handler
    }

    /**
     * Reads a UTF-8 string written by {@link HubPayloadEncoder#writeString}.
     */
    private String readString(ByteBuf buf) {
        int length = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}