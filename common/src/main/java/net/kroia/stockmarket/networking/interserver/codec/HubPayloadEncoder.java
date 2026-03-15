package net.kroia.stockmarket.networking.interserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.kroia.stockmarket.networking.interserver.payload.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * Converts a {@link HubPayload} Java object into raw bytes to send over TCP.
 *
 * Wire format per message (after LengthFieldPrepender adds the frame length):
 * ┌──────────┬──────────────────────────────────────────┐
 * │  ID (1B) │  Fields (variable)                       │
 * └──────────┴──────────────────────────────────────────┘
 *
 * Strings are encoded as:
 * ┌──────────────┬───────────────┐
 * │ length (2B)  │ UTF-8 bytes   │
 * └──────────────┴───────────────┘
 */
public class HubPayloadEncoder extends MessageToByteEncoder<HubPayload> {

    @Override
    protected void encode(ChannelHandlerContext ctx, HubPayload payload, ByteBuf out) {
        // Always write the packet ID first
        out.writeByte(payload.packetId());

        switch (payload) {
            case HandshakePayload hs -> {
                writeString(out, hs.serverId());
                writeString(out, hs.token());
            }
            case StringMessagePayload msg -> {
                writeString(out, msg.senderName());
                writeString(out, msg.fromServer());
                writeString(out, msg.targetServer() != null ? msg.targetServer() : "");
                writeString(out, msg.message());
            }
            case BroadcastPayload bc -> {
                writeString(out, bc.senderName());
                writeString(out, bc.fromServer());
                writeString(out, bc.message());
            }
            case PacketForwardPayload bb -> {
                UUIDUtil.STREAM_CODEC.encode(out, bb.senderId());
                ByteBufCodecs.STRING_UTF8.encode(out, bb.senderServerID());
                ByteBufCodecs.STRING_UTF8.encode(out, bb.targetServerID());
                ResourceLocation.STREAM_CODEC.encode(out, bb.packetType());
                ByteBufCodecs.BYTE_ARRAY.encode(out, bb.data());
            }
        }
    }

    /**
     * Writes a UTF-8 string as [2-byte unsigned length][bytes].
     * Max string length: 65535 bytes.
     */
    private void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}