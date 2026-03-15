package net.kroia.stockmarket.networking.interserver.payload;

/**
 * Carries a plain string from one server to another (or broadcast to all).
 *
 * @param senderName    Player name who sent the message
 * @param fromServer    Server ID the message originated on
 * @param targetServer  Server ID to route to, or "" to broadcast to all
 * @param message       The string content
 */
public record StringMessagePayload(
        String senderName,
        String fromServer,
        String targetServer,
        String message
) implements HubPayload {
    @Override
    public int packetId() { return PacketIds.STRING_MESSAGE; }

    /** Convenience: true when this is a broadcast (no specific target). */
    public boolean isBroadcast() { return targetServer == null || targetServer.isBlank(); }
}