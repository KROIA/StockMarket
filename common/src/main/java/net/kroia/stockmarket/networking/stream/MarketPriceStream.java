package net.kroia.stockmarket.networking.stream;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

public class MarketPriceStream extends StockMarketGenericStream<ItemID, MarketPriceStream.ResponseData>
{
    /**
     * One live update of a market: price, current candle volume and the trading status.
     * T-218: plus the start of the master's live candle, which drives the client's candle
     * rotation in game-time mode.
     */
    public static class ResponseData
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ResponseData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.timestamp,
                ByteBufCodecs.VAR_LONG, p -> p.marketPrice,
                ByteBufCodecs.FLOAT, p -> p.tradedVolume,
                TradingStatus.STREAM_CODEC, p -> p.tradingStatus,
                ByteBufCodecs.VAR_LONG, p -> p.candleStartMs,
                ByteBufCodecs.VAR_LONG, p -> p.candleGameTime,
                ResponseData::new
        );

        public long timestamp;
        public long marketPrice;
        public float tradedVolume;
        /** Trading state + master clock at the time of this update (T-158). */
        public TradingStatus tradingStatus;
        /** T-218: wall-clock start (ms) of the master's live raw candle; changes when the candle rotates. */
        public long candleStartMs;
        /** T-218: in-game start (overworld day time) of that candle, or -1 if unknown. */
        public long candleGameTime;

        public ResponseData(long timestamp, long marketPrice, float tradedVolume, TradingStatus tradingStatus,
                            long candleStartMs, long candleGameTime)
        {
            this.timestamp = timestamp;
            this.marketPrice = marketPrice;
            this.tradedVolume = tradedVolume;
            this.tradingStatus = tradingStatus;
            this.candleStartMs = candleStartMs;
            this.candleGameTime = candleGameTime;
        }
    }

    /**
     * Trading status of a market as seen by the master server (T-158).
     *
     * @param state         {@link MarketSettings.TradingState} ordinal (MAINTENANCE wins over the trading window)
     * @param clockMinute   minute of the day (0..1439) of the globally active trading clock on the master
     * @param clockRealTime {@code true} if the active clock is the master's real time, {@code false} for in-game time
     * @param openMinute    opening minute of the day of this market's trading window (0..1440)
     * @param closeMinute   closing minute of the day of this market's trading window (0..1440);
     *                      {@code open == close} or 00:00-24:00 means always open, {@code open > close} wraps midnight
     * @param maxOrderVolume  T-179: this market's max volume of one player order (real items); 0 = unlimited.
     *                        Used by the trade screen to clamp its amount boxes.
     * @param tradeCooldownMs T-179: the master's global per-player trade cooldown ({@code TRADE_COOLDOWN_MS}); 0 = off.
     *                        Used by the trade screen to grey out buy/sell after an accepted order.
     */
    public record TradingStatus(byte state, short clockMinute, boolean clockRealTime, short openMinute, short closeMinute,
                                long maxOrderVolume, long tradeCooldownMs)
    {
        // Hand-rolled: StreamCodec.composite supports at most 6 fields. Field order = wire format.
        public static final StreamCodec<RegistryFriendlyByteBuf, TradingStatus> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, TradingStatus s) {
                ByteBufCodecs.BYTE.encode(buf, s.state);
                ByteBufCodecs.SHORT.encode(buf, s.clockMinute);
                ByteBufCodecs.BOOL.encode(buf, s.clockRealTime);
                ByteBufCodecs.SHORT.encode(buf, s.openMinute);
                ByteBufCodecs.SHORT.encode(buf, s.closeMinute);
                ByteBufCodecs.VAR_LONG.encode(buf, s.maxOrderVolume);
                ByteBufCodecs.VAR_LONG.encode(buf, s.tradeCooldownMs);
            }

            @Override
            public TradingStatus decode(RegistryFriendlyByteBuf buf) {
                return new TradingStatus(ByteBufCodecs.BYTE.decode(buf), ByteBufCodecs.SHORT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.SHORT.decode(buf), ByteBufCodecs.SHORT.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
            }
        };

        /**
         * @return the decoded trading state; unknown ordinals (version mismatch) map to OPEN
         */
        public MarketSettings.TradingState getTradingState()
        {
            MarketSettings.TradingState[] values = MarketSettings.TradingState.values();
            return state >= 0 && state < values.length ? values[state] : MarketSettings.TradingState.OPEN;
        }

        /**
         * @return {@code true} if the window covers the whole day (same rule as {@link MarketSettings#isAlwaysOpen()})
         */
        public boolean isAlwaysOpen()
        {
            return openMinute == closeMinute || (openMinute <= 0 && closeMinute >= 1440);
        }
    }

    private long updateInterval = 100;
    private long lastTimeMs = System.currentTimeMillis();
    private ItemID itemID;
    private final ResponseData lastPrice = new ResponseData(-1,-1, 0f, null, -1, -1);

    @Override
    public GenericStream<ItemID, ResponseData> copy() {
        return new MarketPriceStream();
    }

    @Override
    public String getStreamTypeID() {
        return MarketPriceStream.class.getName();
    }



    @Override
    public void onStartStreamSendingOnSever() {
        itemID = getContextData();
    }
    @Override
    public void onStopStreamSendingOnServer() {
    }

    @Override
    protected void updateOnServer() {
        long now = System.currentTimeMillis();
        if (now - lastTimeMs > updateInterval) {
            lastTimeMs = now;
            @Nullable IServerMarketManager manager = getMarketManager();
            if(manager == null) {
                stopStream();
                return;
            }
            IServerMarket serverMarket = manager.getMarket(itemID);
            if(serverMarket == null) {
                // The market was deleted while this stream was active. Clients
                // normally stop their subscription themselves when they receive the
                // MarketChangedPacket REMOVED — this is the server-side safety net so no
                // orphaned stream keeps polling a nonexistent market forever.
                info("Market " + itemID + " no longer exists — stopping price stream");
                stopStream();
                return;
            }
            long currentPrice = serverMarket.getCurrentMarketPrice();
            long currentTime = serverMarket.getCurrentTime();
            float currentVolume = serverMarket.getCurrentCandleTradedVolume();
            TradingStatus currentStatus = buildTradingStatus(manager, serverMarket);
            MarketPriceStruct candle = serverMarket.getCurrentMarketPriceStruct();
            if(currentPrice != lastPrice.marketPrice ||
               currentTime != lastPrice.timestamp ||
               currentVolume != lastPrice.tradedVolume ||
               candle.time() != lastPrice.candleStartMs ||
               !currentStatus.equals(lastPrice.tradingStatus)) {
                lastPrice.marketPrice = currentPrice;
                lastPrice.timestamp = currentTime;
                lastPrice.tradedVolume = currentVolume;
                lastPrice.tradingStatus = currentStatus;
                lastPrice.candleStartMs = candle.time();
                lastPrice.candleGameTime = candle.gameTime();
                sendPacket();
            }
        }
    }

    /**
     * Snapshots the market's trading state, the master's trading clock and (T-179) the order limits
     * (max order volume, trade cooldown). Changes are detected by record equality, so an admin edit
     * reaches subscribed clients within one update interval (~100 ms). The clock values are the
     * ones cached by {@link ServerMarketManager} at its last update tick, so the client sees the same
     * minute the open/closed decision was made with. This stream always runs on the master
     * (slaves route it there via {@code needsRoutingToMaster()}).
     */
    private static TradingStatus buildTradingStatus(IServerMarketManager manager, IServerMarket market)
    {
        int clockMinute = 0;
        boolean realTime = false;
        long tradeCooldownMs = 0;
        if (manager instanceof ServerMarketManager smm) {
            clockMinute = smm.getCurrentTradingMinute();
            realTime = smm.isTradingHoursUseRealTime();
            tradeCooldownMs = smm.getTradeCooldownMs();
        }
        MarketSettings settings = market.getSettings();
        return new TradingStatus((byte) market.getTradingState().ordinal(), (short) clockMinute, realTime,
                (short) settings.tradingOpenMinute, (short) settings.tradingCloseMinute,
                settings.maxOrderVolume, tradeCooldownMs);
    }

    @Override
    public ResponseData provideStreamPacketOnServer() {
        return lastPrice;
    }

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buffer, ItemID context) {
        ItemID.STREAM_CODEC.encode(buffer, context);
    }

    @Override
    public ItemID decodeContextData(RegistryFriendlyByteBuf buffer) {
        return ItemID.STREAM_CODEC.decode(buffer);
    }

    @Override
    public void encodeData(RegistryFriendlyByteBuf buffer, ResponseData aLong) {
        ResponseData.STREAM_CODEC.encode(buffer, aLong);
    }

    @Override
    public ResponseData decodeData(RegistryFriendlyByteBuf buffer) {
        return ResponseData.STREAM_CODEC.decode(buffer);
    }
}
