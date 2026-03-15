package net.kroia.stockmarket.networking.interserver.payload;

/**
 * Central registry of all packet IDs used in the hub TCP protocol.
 * Both encoder and decoder must agree on these values.
 */
public final class PacketIds {
    public static final int HANDSHAKE      = 0x00; // Child → Hub: register server
    public static final int STRING_MESSAGE = 0x01; // Child → Hub → Child: send a string
    public static final int BROADCAST      = 0x02; // Hub  → All: hub-initiated broadcast

    private PacketIds() {}
}
