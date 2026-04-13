package net.kroia.stockmarket.util;

import net.kroia.banksystem.banking.bankmanager.BankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
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
        public Candle(MarketPriceStruct  sqlData)
        {
            openTimestamp = sqlData.time();
            open = sqlData.open();
            high = sqlData.high();
            low = sqlData.low();
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceHistoryData> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.itemID,
            ByteBufCodecs.INT, p -> p.itemScaleFactor,
            ExtraCodecUtils.listStreamCodec(Candle.STREAM_CODEC), p -> p.candles,
            ByteBufCodecs.VAR_LONG, p -> p.currentMarketPrice,
            PriceHistoryData::new
    );

    private final ItemID itemID;
    private final int itemScaleFactor;
    private final List<Candle> candles;
    private long currentMarketPrice;


    public PriceHistoryData(ItemID itemID, int itemScaleFactor)
    {
        this.itemID = itemID;
        this.itemScaleFactor = itemScaleFactor;
        candles = new ArrayList<>();
        currentMarketPrice = 0;
        startNewCandle();
    }
    public PriceHistoryData(ItemID itemID, int itemScaleFactor, List<Candle> candles, long currentMarketPrice)
    {
        this.itemID = itemID;
        this.itemScaleFactor = itemScaleFactor;
        this.candles = candles;
        this.currentMarketPrice = currentMarketPrice;
    }

    public static @Nullable PriceHistoryData fromSqlData(List<MarketPriceStruct> sqlData, long currentMarketPrice, int itemScaleFactor)
    {
        if(sqlData.isEmpty())
            return null;
        ItemID itemID = new ItemID(sqlData.getFirst().id());
        List<Candle> candles = new ArrayList<>();
        for(MarketPriceStruct candle : sqlData)
        {
            candles.add(new Candle(candle));
        }
        return new PriceHistoryData(itemID, itemScaleFactor, candles, currentMarketPrice);
    }

    public void insert(PriceHistoryData other)
    {
        candles.addAll(other.candles);
        candles.sort(Comparator.comparingLong(a -> a.openTimestamp));
    }
    public void clear()
    {
        candles.clear();
        currentMarketPrice = 0;
        startNewCandle();
    }
    public void loadFrom(PriceHistoryData other)
    {
        candles.clear();
        candles.addAll(other.candles);
        currentMarketPrice = other.currentMarketPrice;
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
    public @Nullable Candle getCurrentCandle()
    {
        if(candles.isEmpty())
            return null;
        return candles.getLast();
    }
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
    public double getCurrentMarketRealPrice()
    {
        return toRealPrice(currentMarketPrice);
    }

    public long getMinPrice(int startIndex, int endIndex)
    {
        long min = Integer.MAX_VALUE;
        if(startIndex > endIndex)
        {
            int tmp = endIndex;
            endIndex = startIndex;
            startIndex = tmp;
        }
        startIndex = Math.max(0, Math.min(startIndex, candles.size()-1));
        endIndex = Math.max(0, Math.min(endIndex, candles.size()-1));
        for(int i = startIndex; i <= endIndex; i++)
        {
            min = Math.min(min, candles.get(i).low);
        }
        return min;
    }
    public long getMaxPrice(int startIndex, int endIndex)
    {
        long max = Integer.MIN_VALUE;
        if(startIndex > endIndex)
        {
            int tmp = endIndex;
            endIndex = startIndex;
            startIndex = tmp;
        }
        startIndex = Math.max(0, Math.min(startIndex, candles.size()-1));
        endIndex = Math.max(0, Math.min(endIndex, candles.size()-1));
        for(int i = startIndex; i <= endIndex; i++)
        {
            max = Math.max(max, candles.get(i).high);
        }
        return max;
    }

    public int getItemScaleFactor()
    {
        return itemScaleFactor;
    }
    public double toRealPrice(long rawPrice)
    {
        return BankManager.convertToRealAmountStatic(rawPrice, itemScaleFactor);
    }
    public long toRawPrice(double realPrice)
    {
        return BankManager.convertToRawAmountStatic(realPrice, itemScaleFactor);
    }
}
