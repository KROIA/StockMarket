package net.kroia.stockmarket.networking.interserver.payload;

import com.mojang.datafixers.types.Type;
import net.kroia.modutilities.networking.NetworkPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PacketForwardPayload(
        UUID senderId,
        String senderServerID,
        String targetServerID,
        ResourceLocation packetType,
        byte[] data
) implements HubPayload {
    @Override
    public int packetId() { return PacketIds.BYTE_BUFFER; }

    /** Convenience: true when this is a broadcast (no specific target). */
    public boolean isBroadcast()
    {
        return targetServerID == null || targetServerID.isBlank();
    }
}