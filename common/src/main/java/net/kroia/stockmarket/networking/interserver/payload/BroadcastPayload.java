package net.kroia.stockmarket.networking.interserver.payload;

/**
 * Sent FROM the hub TO all child servers — e.g. to display a received message.
 *
 * @param senderName  Original sender's player name
 * @param fromServer  Server the message came from
 * @param message     The string content
 */
public record BroadcastPayload(
        String senderName,
        String fromServer,
        String message
) implements HubPayload {
    @Override
    public int packetId() { return PacketIds.BROADCAST; }
}
