package net.kroia.stockmarket.networking.interserver.payload;

/**
 * Base interface for all packets sent over the TCP hub connection.
 * Each implementing record defines its own fields and packet ID.
 */
public sealed interface HubPayload permits
        HandshakePayload,
        StringMessagePayload,
        BroadcastPayload,
        PacketForwardPayload {

    /** Unique byte ID used to identify this packet type on the wire. */
    int packetId();
}
