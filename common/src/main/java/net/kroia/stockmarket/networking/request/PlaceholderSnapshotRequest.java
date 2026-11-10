package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.minecraft.compat.StockMarketPlaceholders;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Slave → master pull of the cached placeholder values (see {@link StockMarketPlaceholders}).
 * <p>
 * Input: UUIDs of the players currently online on the requesting slave. <br>
 * Output: all market placeholder values plus the player placeholder values for the requested
 * UUIDs. The master handler only reads the placeholder caches and records the UUIDs as
 * "remote interest" so the next master tick computes their values; it never touches market
 * or bank state itself. Player values carry per-market portfolio rows, so the slave can sum the portfolio again
 * without markets it cannot resolve (T-240, {@link StockMarketPlaceholders#withoutUnresolvableMarkets}).
 * <p>
 * Market values are always returned; player values only to trusted slaves
 * ({@link NetworkGate#isManagementReadAllowed}). Not callable by clients.
 */
public class PlaceholderSnapshotRequest extends StockMarketGenericRequest<List<UUID>, PlaceholderSnapshotRequest.OutputData> {

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, String>> STRING_MAP_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<UUID>> UUID_LIST_CODEC =
            UUIDUtil.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list());

    /**
     * @param market  placeholder name -> value for every market placeholder
     * @param players player UUID -> (placeholder name -> value)
     */
    public record OutputData(Map<String, String> market, Map<UUID, Map<String, String>> players) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                STRING_MAP_CODEC, OutputData::market,
                ByteBufCodecs.<RegistryFriendlyByteBuf, UUID, Map<String, String>, Map<UUID, Map<String, String>>>map(
                        HashMap::new, UUIDUtil.STREAM_CODEC.cast(), STRING_MAP_CODEC), OutputData::players,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PlaceholderSnapshotRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(Map.of(), Map.of());
    }

    /** Clients must not call this request; it exposes other players' values. */
    @Override
    public CompletableFuture<OutputData> handleOnServer(List<UUID> input, ServerPlayer sender) {
        return CompletableFuture.completedFuture(getDefaultResponse());
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(List<UUID> input, String slaveID, @Nullable UUID playerSender) {
        // Only slave servers themselves may pull snapshots (no player-originated or master-local calls).
        if (playerSender != null || slaveID == null || slaveID.isEmpty() || getServerMarketManager() == null)
            return CompletableFuture.completedFuture(getDefaultResponse());

        Map<UUID, Map<String, String>> players = Map.of();
        if (!input.isEmpty() && NetworkGate.isManagementReadAllowed(slaveID, "PlaceholderSnapshotRequest"))
            players = StockMarketPlaceholders.snapshotForRemote(input);
        return CompletableFuture.completedFuture(new OutputData(StockMarketPlaceholders.getServerValues(), players));
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, List<UUID> input) {
        UUID_LIST_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public List<UUID> decodeInput(RegistryFriendlyByteBuf buf) {
        return UUID_LIST_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
