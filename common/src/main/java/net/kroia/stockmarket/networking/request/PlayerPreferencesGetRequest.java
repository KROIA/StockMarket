package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Request to fetch the calling player's trading preferences from the master server.
 * Input is a dummy byte (server identifies the player from the sender UUID).
 * Output is the player's {@link PlayerPreferences}; defaults for a call from an untrusted slave,
 * which sets the sender's UUID itself (T-239).
 */
public class PlayerPreferencesGetRequest extends StockMarketGenericRequest<Byte, PlayerPreferences> {

    @Override
    public String getRequestTypeID() {
        return PlayerPreferencesGetRequest.class.getName();
    }

    @Override
    protected PlayerPreferences getDefaultResponse() {
        return new PlayerPreferences();
    }

    @Override
    public CompletableFuture<PlayerPreferences> handleOnMasterServer(Byte input, String slaveID, @Nullable UUID playerSender) {
        // T-239: an untrusted slave sets the player UUID itself, so it could read any player's preferences
        if (playerSender == null || !NetworkGate.isManagementReadAllowed(slaveID, "PlayerPreferencesGetRequest"))
            return CompletableFuture.completedFuture(getDefaultResponse());
        PlayerPreferences prefs = getServerMarketManager().getPlayerPreferences(playerSender);
        return CompletableFuture.completedFuture(prefs);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Byte input) {
        ByteBufCodecs.BYTE.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, PlayerPreferences output) {
        PlayerPreferences.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public Byte decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BYTE.decode(buf);
    }

    @Override
    public PlayerPreferences decodeOutput(RegistryFriendlyByteBuf buf) {
        return PlayerPreferences.STREAM_CODEC.decode(buf);
    }
}
