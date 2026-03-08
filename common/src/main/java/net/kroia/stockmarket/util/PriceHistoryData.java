package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public class PriceHistoryData
{
    public static class Candle
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Candle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.openTimestamp,
                ByteBufCodecs.VAR_LONG, p -> p.open,
                ByteBufCodecs.VAR_LONG, p -> p.high,
                ByteBufCodecs.VAR_LONG, p -> p.low,
                Candle::new
        );

        public final long openTimestamp;
        public final long open;
        public long high;
        public long low;

        public Candle(long open, long high, long low)
        {
            this.openTimestamp = System.currentTimeMillis();
            this.open = open;
            this.high = high;
            this.low = low;
        }
        public Candle(long openTimestamp, long open, long high, long low)
        {
            this.openTimestamp = openTimestamp;
            this.open = open;
            this.high = high;
            this.low = low;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceHistoryData> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.itemID,
            ExtraCodecUtils.listStreamCodec(Candle.STREAM_CODEC), p -> p.candles,
            ByteBufCodecs.VAR_LONG, p -> p.currentMarketPrice,
            PriceHistoryData::new
    );

    private final ItemID itemID;
    private final List<Candle> candles;
    private long currentMarketPrice;


    public PriceHistoryData(ItemID itemID)
    {
        this.itemID = itemID;
        candles = new ArrayList<>();
        currentMarketPrice = 0;
        startNewCandle();
    }
    public PriceHistoryData(ItemID itemID, List<Candle> candles, long currentMarketPrice)
    {
        this.itemID = itemID;
        this.candles = candles;
        this.currentMarketPrice = currentMarketPrice;
    }

    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        if(!candles.isEmpty())
        {
            // Update newest candle
            Candle  candle = candles.getLast();
            candle.high = Math.max(this.currentMarketPrice, candle.high);
            candle.low = Math.min(this.currentMarketPrice, candle.low);
        }
    }
    public void startNewCandle()
    {
        candles.add(new Candle(currentMarketPrice,currentMarketPrice,currentMarketPrice));
    }

    public List<Candle> getCandles()
    {
        return candles;
    }
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
}
