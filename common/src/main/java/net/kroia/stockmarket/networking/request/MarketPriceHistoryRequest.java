package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.StockMarketMod;
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
     *                        server default" ({@link #MAX_CANDLES_PER_RESPONSE_DEFAULT}
     *                        / the mod setting). Larger values are clamped to the server
     *                        cap. Also bounds how many raw rows the server reads: only
     *                        about {@code maxCandles} non-empty buckets ending at
     *                        {@code maxTimestamp} are queried (count-anchored, so
     *                        {@code minTimestamp = -1} means "the page before maxTimestamp").
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
        // [HistoryPerf] timing: t0 = request accepted, DB = until both queries finished
        // (includes waiting in the single-thread DB executor queue).
        final long perfT0 = System.nanoTime();

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
        // A client may ask for fewer candles than the server cap, never for more.
        final int effectiveCap = (input.maxCandles > 0) ? Math.min(input.maxCandles, defaultCap) : defaultCap;
        final EqualityFilter marketFilter = new EqualityFilter(input.item.getShort());

        // Never scan more raw rows than the response can hold. Raw rows are written once
        // per CANDLE_TIME, so effectiveCap candles of candleDeltaMs need about
        // effectiveCap * (candleDeltaMs / CANDLE_TIME) rows. Look up the timestamp of that
        // many-th newest row <= maxTimestamp (index-only walk) and start the query at the
        // beginning of its bucket, so the oldest returned candle is complete.
        // Counting rows instead of time keeps the window full across downtime gaps and
        // still jumps to where the data lives when the world was loaded long after the
        // last save. Without this an open-ended (minTimestamp = -1) request read the
        // market's ENTIRE history (100k+ rows) only to trim it to the newest candles.
        final long[] queriedMin = {effectiveMin};
        CompletableFuture<Long> windowStartFut = CompletableFuture.completedFuture(Long.MIN_VALUE);
        if (input.candleDeltaMs > 0) {
            long rawIntervalMs = 60_000L;
            try {
                rawIntervalMs = Math.max(1L, BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CANDLE_TIME.get());
            } catch (Exception ignored) {
                // Settings not available (early startup / test) -> assume the 1-minute default.
            }
            long rows = (long) effectiveCap * Math.max(1L, input.candleDeltaMs / rawIntervalMs);
            windowStartFut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getNthNewestTimestamp(
                    marketFilter, input.maxTimestamp, (int) Math.min(Integer.MAX_VALUE, rows - 1));
        }
        CompletableFuture<List<MarketPriceStruct>> fut = windowStartFut.thenCompose(windowStartRow -> {
            // MIN_VALUE: fewer rows than needed exist -> read everything (reaches the start).
            if (windowStartRow != Long.MIN_VALUE)
                queriedMin[0] = Math.max(effectiveMin, PriceHistoryData.floorTime(windowStartRow, input.candleDeltaMs));
            return BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getHistory(
                    Optional.of(new DateFilter(queriedMin[0], input.maxTimestamp)),
                    Optional.of(marketFilter), -1);
        });
        // Query the oldest-available timestamp in parallel so we can tell the client
        // when it has walked back to the beginning of the server's on-disk history.
        CompletableFuture<Long> oldestFut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getOldestTimestamp(
                Optional.of(marketFilter));

        fut.thenCombine(oldestFut, (list, oldestOnDisk) -> new Object[]{list, oldestOnDisk}).thenAccept(pair -> {
            @SuppressWarnings("unchecked")
            List<MarketPriceStruct> list = (List<MarketPriceStruct>) pair[0];
            long oldestOnDisk = (Long) pair[1];
            final long perfTDb = System.nanoTime();

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

            final long perfTAgg = System.nanoTime();
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
            // The window clamp above skipped rows that exist on disk -> more history is available.
            if (queriedMin[0] > effectiveMin && oldestOnDisk < queriedMin[0])
                truncated = true;
            if (truncated) {
                data.setTruncated(true);
            }

            StockMarketMod.LOGGER.debug("[HistoryPerf] server market={} delta={} min={} max={} queriedMin={} cap={} rows={} candles={} dbMs={} aggMs={} totalMs={}",
                    input.item.getShort(), input.candleDeltaMs, input.minTimestamp, input.maxTimestamp, queriedMin[0], effectiveCap,
                    list.size(), data.getCandles().size(),
                    (perfTDb - perfT0) / 1_000_000, (perfTAgg - perfTDb) / 1_000_000, (System.nanoTime() - perfT0) / 1_000_000);
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
