package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Returns the master server's wall clock (for client/server time offset sync) plus the
 * trading-hours clocks (T-158). Slaves route this request to the master
 * ({@code needsRoutingToMaster()}), so all values always describe the master.
 */
public class ServerTimeRequest extends StockMarketGenericRequest<Long, ServerTimeRequest.OutputData> {

    /**
     * @param serverTime      master wall-clock time in ms
     * @param clientTimeEcho  the client's send time, echoed back for round-trip compensation
     * @param realMinuteOfDay master's local real time as minute of the day (0..1439)
     * @param gameMinuteOfDay master overworld in-game time as minute of the day (0..1439)
     * @param useRealTime     {@code true} if trading hours currently follow real time, {@code false} for in-game time
     * @param candleUseGameTime T-218: the master's candle timebase ({@code CANDLE_USE_GAME_TIME} as cached at
     *                        startup); clients pick their chart deltas from it on join
     */
    public record OutputData(long serverTime, long clientTimeEcho, int realMinuteOfDay, int gameMinuteOfDay, boolean useRealTime,
                             boolean candleUseGameTime)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, OutputData::serverTime,
                ByteBufCodecs.VAR_LONG, OutputData::clientTimeEcho,
                ByteBufCodecs.VAR_INT, OutputData::realMinuteOfDay,
                ByteBufCodecs.VAR_INT, OutputData::gameMinuteOfDay,
                ByteBufCodecs.BOOL, OutputData::useRealTime,
                ByteBufCodecs.BOOL, OutputData::candleUseGameTime,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return ServerTimeRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(0, 0, 0, 0, false, false);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(Long input, String slaveID, @Nullable UUID playerSender) {
        // Runs on the master. The live setting is read directly (the manager's cached copy may lag one tick).
        boolean useRealTime = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.TRADING_HOURS_USE_REAL_TIME.get();
        return CompletableFuture.completedFuture(new OutputData(System.currentTimeMillis(), input,
                ServerMarketManager.realMinuteOfDay(),
                ServerMarketManager.gameMinuteOfDay(UtilitiesPlatform.getServer()),
                useRealTime,
                // T-218: the cached (restart-required) value, matching how the master rotates candles.
                getServerMarketManager() instanceof ServerMarketManager smm && smm.isCandleUseGameTime()));
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Long input) {
        ByteBufCodecs.VAR_LONG.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public Long decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.VAR_LONG.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
