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
 * Request to update the calling player's trading preferences on the master server.
 * Input is the updated {@link PlayerPreferences}.
 * Output is a boolean indicating success (true) or failure (false, e.g. unknown player).
 */
public class PlayerPreferencesUpdateRequest extends StockMarketGenericRequest<PlayerPreferences, Boolean> {

    @Override
    public String getRequestTypeID() {
        return PlayerPreferencesUpdateRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(PlayerPreferences input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null)
            return CompletableFuture.completedFuture(false);
        // T-123 (untrusted slave gate): player preferences are per-player server
        // state — keep them gated with the mutating group even though they
        // don't touch orderbook state. If a player wants to keep changing
        // preferences while their slave is untrusted, an admin has to trust
        // the slave first.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PlayerPreferencesUpdateRequest"))
            return CompletableFuture.completedFuture(false);
        boolean success = getServerMarketManager().updatePlayerPreferences(playerSender, input);
        return CompletableFuture.completedFuture(success);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, PlayerPreferences input) {
        PlayerPreferences.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public PlayerPreferences decodeInput(RegistryFriendlyByteBuf buf) {
        return PlayerPreferences.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
