package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MarketPriceHistoryRequest extends StockMarketGenericRequest<MarketPriceHistoryRequest.InputData, PriceHistoryData>
{

    /**
     * Default hard cap for the number of candles the server is willing to serve
     * in a single response. When a client requests a range that would exceed
     * this after aggregation, older candles are dropped and
     * {@link PriceHistoryData#isTruncated()} is set. T-137 will move the
     * runtime value to a mod setting; this constant stays as the fallback.
     */
    public static final int MAX_CANDLES_PER_RESPONSE_DEFAULT = 4096;

    /**
     * Request payload for a paginated / aggregated price-history query.
     *
     * @param item            target market
     * @param candleDeltaMs   candle bucket size in milliseconds. {@code 0} means
     *                        "return raw candles" (legacy behavior). When
     *                        positive, the server aggregates raw candles into
     *                        buckets of this size using
     *                        {@code PriceHistoryData.createFromDifferentCandleDeltaTime}.
     * @param minTimestamp    inclusive lower time bound. {@code -1} means
     *                        open-ended (from server start / earliest record).
     * @param maxTimestamp    exclusive upper time bound. Also serves as the
     *                        anchor for the count-backwards / truncation mode:
     *                        when the aggregated list exceeds
     *                        {@link #maxCandles}, the newest-N candles (those
     *                        closest to {@code maxTimestamp}) are kept.
     * @param maxCandles      hard cap for this response. {@code 0} means "use
     *                        server default" ({@link #MAX_CANDLES_PER_RESPONSE_DEFAULT}).
     */
    public record InputData(ItemID item, long candleDeltaMs, long minTimestamp, long maxTimestamp, int maxCandles)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.item,
                ByteBufCodecs.VAR_LONG, p -> p.candleDeltaMs,
                ByteBufCodecs.VAR_LONG, p -> p.minTimestamp,
                ByteBufCodecs.VAR_LONG, p -> p.maxTimestamp,
                ByteBufCodecs.VAR_INT, p -> p.maxCandles,
                InputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return MarketPriceHistoryRequest.class.getName();
    }

    @Override
    protected PriceHistoryData getDefaultResponse() {
        return new PriceHistoryData(0, ItemID.INVALID_ID, 100);
    }
    @Override
    public CompletableFuture<PriceHistoryData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender)
    {
        if(needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender))
            return CompletableFuture.completedFuture(new PriceHistoryData(System.currentTimeMillis() , input.item, getItemFractionScaleFactor()));
        CompletableFuture<PriceHistoryData> future = new CompletableFuture<>();
        info("MarketPriceHistoryRequest started for item: " + input);

        // Resolve the lower bound: -1 (open-ended) becomes 0 for the SQL DateFilter.
        final long effectiveMin = (input.minTimestamp == -1L) ? 0L : input.minTimestamp;
        // Effective per-response cap: caller override wins, otherwise the server-authoritative
        // mod setting (T-137). The compile-time constant is used as a fallback for the
        // (early startup / test) case where SERVER_SETTINGS is not yet populated.
        int defaultCap = MAX_CANDLES_PER_RESPONSE_DEFAULT;
        try {
            if (BACKEND_INSTANCES != null && BACKEND_INSTANCES.SERVER_SETTINGS != null) {
                defaultCap = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getPriceHistoryMaxCandlesPerResponse();
            }
        } catch (Exception ignored) {
            // Fall back to the compile-time default if the setting is not yet available.
        }
        final int effectiveCap = (input.maxCandles > 0) ? input.maxCandles : defaultCap;
        final EqualityFilter marketFilter = new EqualityFilter(input.item.getShort());

        CompletableFuture<List<MarketPriceStruct>>  fut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getHistory(
                Optional.of(new DateFilter(effectiveMin, input.maxTimestamp)),
                Optional.of(marketFilter), -1);
        // Query the oldest-available timestamp in parallel so we can tell the client
        // when it has walked back to the beginning of the server's on-disk history.
        CompletableFuture<Long> oldestFut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getOldestTimestamp(
                Optional.of(marketFilter));

        fut.thenCombine(oldestFut, (list, oldestOnDisk) -> new Object[]{list, oldestOnDisk}).thenAccept(pair -> {
            @SuppressWarnings("unchecked")
            List<MarketPriceStruct> list = (List<MarketPriceStruct>) pair[0];
            long oldestOnDisk = (Long) pair[1];

            PriceHistoryData data = PriceHistoryData.fromSqlData(list, getCurrentMarketPrice(input.item), getItemFractionScaleFactor());
            if(data == null)
            {
                warn("MarketPriceHistoryRequest failed to fetch data for item: " + input.item);
                PriceHistoryData empty = new PriceHistoryData(System.currentTimeMillis(), input.item, getItemFractionScaleFactor());
                // When no rows exist for this market, tell the client there is no earlier data.
                empty.setOldestAvailableTimestamp(PriceHistoryData.NO_OLDER_DATA);
                empty.setTruncated(false);
                future.complete(empty);
                return;
            }

            // Aggregate into candleDeltaMs-sized buckets when requested. Reuse the
            // existing loadFrom / Candle.merge logic so behavior matches the client-side
            // resampler exactly (empty-gap-filling stays off here: pagination consumers
            // want holes preserved, not synthesized).
            if (input.candleDeltaMs > 0) {
                // Cap the aggregation's "now" reference at input.maxTimestamp so that
                // historical / count-back queries don't get an extra live candle
                // extending past the requested upper bound. When maxTimestamp is
                // effectively unbounded (large sentinel), this falls back to real now.
                long aggregationServerTime = Math.min(input.maxTimestamp, System.currentTimeMillis());
                data = data.createFromDifferentCandleDeltaTime(
                        aggregationServerTime,
                        input.candleDeltaMs,
                        data.getCurrentMarketPrice(),
                        false);
            }

            // Enforce the response cap by dropping from the OLDEST side, so the
            // newest-N candles (those anchored at maxTimestamp) survive.
            boolean truncated = false;
            List<PriceHistoryData.Candle> candles = data.getCandles();
            if (candles.size() > effectiveCap) {
                int drop = candles.size() - effectiveCap;
                // Rebuild the list keeping only the newest effectiveCap entries.
                List<PriceHistoryData.Candle> trimmed = new ArrayList<>(candles.subList(drop, candles.size()));
                data = new PriceHistoryData(
                        input.item,
                        getItemFractionScaleFactor(),
                        trimmed,
                        data.getCurrentMarketPrice(),
                        oldestOnDisk,
                        true);
                truncated = true;
            }

            IServerMarket market = getServerMarketManager().getMarket(input.item);
            if (market != null) {
                MarketPriceStruct currentCandleData = market.getCurrentMarketPriceStruct();
                if(input.maxTimestamp >= currentCandleData.time()) {
                    data.startNewCandle(System.currentTimeMillis());
                    data.setCurrentMarketPrice(currentCandleData.high());
                    data.setCurrentMarketPrice(currentCandleData.low());
                    data.setCurrentMarketPrice(market.getCurrentMarketPrice());
                }
            }

            // Populate pagination metadata. When no rows exist on disk the manager
            // returns Long.MAX_VALUE, which matches PriceHistoryData.NO_OLDER_DATA and
            // signals to the client "you have everything back to server-start".
            data.setOldestAvailableTimestamp(oldestOnDisk);
            if (truncated) {
                data.setTruncated(true);
            }

            future.complete(data);

        });
        return future;
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, PriceHistoryData output) {
        PriceHistoryData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public PriceHistoryData decodeOutput(RegistryFriendlyByteBuf buf) {
        return PriceHistoryData.STREAM_CODEC.decode(buf);
    }
}
