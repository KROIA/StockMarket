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
 * trading-hours clocks (T-158), the candle timebase (T-218) and the initial chart window size (T-140).
 * Clients send it once per join, so it doubles as the join-time sync of master-owned client settings. Slaves route this request to the master
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
     * @param initialLoadCandles T-140: the master's {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES} (candles loaded
     *                        when a chart first opens); clients store it in {@code ClientSettings} on join.
     *                        Later admin changes reach clients through {@code ClientSettingsSyncPacket}.
     */
    public record OutputData(long serverTime, long clientTimeEcho, int realMinuteOfDay, int gameMinuteOfDay, boolean useRealTime,
                             boolean candleUseGameTime, int initialLoadCandles)
    {
        // Written by hand: StreamCodec.composite in 1.21.1 takes at most 6 fields.
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.of(
                (buf, d) -> {
                    ByteBufCodecs.VAR_LONG.encode(buf, d.serverTime());
                    ByteBufCodecs.VAR_LONG.encode(buf, d.clientTimeEcho());
                    ByteBufCodecs.VAR_INT.encode(buf, d.realMinuteOfDay());
                    ByteBufCodecs.VAR_INT.encode(buf, d.gameMinuteOfDay());
                    ByteBufCodecs.BOOL.encode(buf, d.useRealTime());
                    ByteBufCodecs.BOOL.encode(buf, d.candleUseGameTime());
                    ByteBufCodecs.VAR_INT.encode(buf, d.initialLoadCandles());
                },
                buf -> new OutputData(
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf))
        );
    }

    @Override
    public String getRequestTypeID() {
        return ServerTimeRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(0, 0, 0, 0, false, false, 0); // initialLoadCandles 0 = not synced, client falls back
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
                getServerMarketManager() instanceof ServerMarketManager smm && smm.isCandleUseGameTime(),
                // T-140: read live, so a join after an admin change gets the new value.
                BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getPriceHistoryInitialLoadCandles()));
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
