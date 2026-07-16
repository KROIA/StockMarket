package net.kroia.stockmarket.util;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client-side settings snapshot synced from the server at player join
 * (see {@code PlayerJoinSyncPacket}).
 * <p>
 * The server fills this object in {@code StockMarketModSettings.getClientSettings()}
 * and sends it once per join; the client stores it in
 * {@code StockMarketModBackend.ClientInstances.SETTINGS} where GUI code can query it
 * (e.g. via {@code StockMarketGuiScreen.isMasterServer()}).
 */
public class ClientSettings {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.dummy,
            ByteBufCodecs.BOOL, p -> p.isMasterServer,
            ClientSettings::new
    );

    boolean dummy;

    /**
     * True when the server the player is connected to is the MASTER server of a
     * master/slave multi-server setup (or a regular single server, which acts as
     * its own master). Slave servers send {@code false}.
     * <p>
     * Used client-side to gate master-only UI such as the "Mod Settings" button in
     * the ManagementScreen: only the master loads and owns {@code settings.json},
     * so editing settings from a slave is not possible.
     */
    boolean isMasterServer = false;

    boolean fillMissingCandlesticks = true;

    public ClientSettings() {
    }
    public ClientSettings(boolean dummy) {
        this.dummy = dummy;
    }
    public ClientSettings(boolean dummy, boolean isMasterServer) {
        this.dummy = dummy;
        this.isMasterServer = isMasterServer;
    }

    /**
     * Copies all server-synced values from the received settings object into this
     * (client-held) instance. Called from the {@code PlayerJoinSyncPacket} handler.
     *
     * @param settings the settings object decoded from the join-sync packet
     */
    public void loadFrom(ClientSettings settings)
    {
        this.isMasterServer = settings.isMasterServer;
    }

    /**
     * @return true if the server this client is connected to is the master server
     *         (see {@link #isMasterServer})
     */
    public boolean isMasterServer() {
        return isMasterServer;
    }

    /**
     * Server-side setter used when building the join-sync payload.
     *
     * @param isMasterServer whether this server is the master server
     */
    public void setMasterServer(boolean isMasterServer) {
        this.isMasterServer = isMasterServer;
    }

    public void setFillMissingCandlesticks(boolean fillMissingCandlesticks) {
        this.fillMissingCandlesticks = fillMissingCandlesticks;
    }
    public boolean isFillMissingCandlesticks() {
        return fillMissingCandlesticks;
    }

}
