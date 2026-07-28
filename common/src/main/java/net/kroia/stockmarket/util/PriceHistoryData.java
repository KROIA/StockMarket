package net.kroia.stockmarket.util;

import net.kroia.banksystem.banking.bankmanager.BankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;

public class PriceHistoryData
{
    public static class Candle
    {
        public final static SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss:SSS", Locale.getDefault());
        public static final StreamCodec<RegistryFriendlyByteBuf, Candle> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.openTimestamp,
                ByteBufCodecs.VAR_LONG, p -> p.open,
                ByteBufCodecs.VAR_LONG, p -> p.high,
                ByteBufCodecs.VAR_LONG, p -> p.low,
                ByteBufCodecs.FLOAT, p -> p.tradedVolume,
                Candle::new
        );

        public final long openTimestamp;
        public final long open;
        public long high;
        public long low;
        // Real-scaled total traded volume for this candle period
        public float tradedVolume;

        public Candle(long open, long high, long low)
        {
            this(System.currentTimeMillis(), open, high, low, 0f);
        }
        public Candle(long openTimestamp, long open, long high, long low)
        {
            this(openTimestamp, open, high, low, 0f);
        }
        public Candle(long openTimestamp, long open, long high, long low, float tradedVolume)
        {
            this.openTimestamp = openTimestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.tradedVolume = tradedVolume;
        }
        public Candle(MarketPriceStruct  sqlData)
        {
            openTimestamp = sqlData.time();
            open = sqlData.open();
            high = sqlData.high();
            low = sqlData.low();
            tradedVolume = sqlData.tradedVolume();
        }
        public static Candle merge(List<Candle> candles, long timestamp, int begin, int count)
        {
            long open = 0;
            long high = -Long.MAX_VALUE;
            long low  = Long.MAX_VALUE;
            float totalVolume = 0;
            if(candles.size() > begin)
            {
                Candle candle = candles.get(begin);
                open = candle.open;
            }
            int end = begin+count;
            for(int i=begin; i<end; i++)
            {
                Candle candle = candles.get(i);
                high = Math.max(high, candle.high);
                low = Math.min(low, candle.low);
                totalVolume += candle.tradedVolume;
            }
            return new Candle(timestamp, open, high, low, totalVolume);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Time: ").append(dateTimeFormat.format(openTimestamp)).append("\n");
            sb.append("Open: ").append(open).append("\n");
            sb.append("High: ").append(high).append("\n");
            sb.append("Low: ").append(low);
            return sb.toString();
        }
    }

    /**
     * Sentinel value for {@link #oldestAvailableTimestamp} meaning "server has no
     * data older than the returned candles" (i.e. the client already holds
     * everything back to server-start for this market).
     */
    public static final long NO_OLDER_DATA = Long.MAX_VALUE;

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceHistoryData> STREAM_CODEC = StreamCodec.composite(
            ItemID.STREAM_CODEC, p -> p.itemID,
            ByteBufCodecs.INT, p -> p.itemScaleFactor,
            ExtraCodecUtils.listStreamCodec(Candle.STREAM_CODEC), p -> p.candles,
            ByteBufCodecs.VAR_LONG, p -> p.currentMarketPrice,
            ByteBufCodecs.VAR_LONG, p -> p.oldestAvailableTimestamp,
            ByteBufCodecs.BOOL, p -> p.truncated,
            PriceHistoryData::new
    );

    private final ItemID itemID;
    private final int itemScaleFactor;
    private final List<Candle> candles;
    private long currentMarketPrice;
    /**
     * Oldest timestamp the server has on disk for this market. Used by the client
     * pagination cache (T-135) to know when it has walked back to the beginning
     * of the server's recorded history. {@link #NO_OLDER_DATA} means "nothing
     * older exists".
     */
    private long oldestAvailableTimestamp;
    /**
     * {@code true} when the server truncated the response to fit its per-response
     * candle cap (see {@code MarketPriceHistoryRequest.MAX_CANDLES_PER_RESPONSE_DEFAULT}).
     * The returned list is the newest-N candles; older ones were dropped.
     */
    private boolean truncated;


    public PriceHistoryData(long currentServerTime, ItemID itemID, int itemScaleFactor)
    {
        this.itemID = itemID;
        this.itemScaleFactor = itemScaleFactor;
        candles = new ArrayList<>();
        currentMarketPrice = 0;
        this.oldestAvailableTimestamp = NO_OLDER_DATA;
        this.truncated = false;
        startNewCandle(currentServerTime);
    }
    /**
     * Legacy 4-arg constructor. Kept for backward compatibility with existing
     * callers (tests, {@code CrossRateMarket}, {@code ClientMarket}) that don't
     * need the pagination metadata. Defaults {@code oldestAvailableTimestamp} to
     * {@link #NO_OLDER_DATA} and {@code truncated} to {@code false}.
     */
    public PriceHistoryData(ItemID itemID, int itemScaleFactor, List<Candle> candles, long currentMarketPrice)
    {
        this(itemID, itemScaleFactor, candles, currentMarketPrice, NO_OLDER_DATA, false);
    }
    /**
     * Full constructor including pagination scalars.
     *
     * @param itemID                    the market this history belongs to
     * @param itemScaleFactor           price fraction scaling factor
     * @param candles                   candle list (chronological, oldest first)
     * @param currentMarketPrice        current market price in raw units
     * @param oldestAvailableTimestamp  oldest timestamp available on the server
     *                                  for this market, or {@link #NO_OLDER_DATA}
     *                                  when no earlier data exists
     * @param truncated                 {@code true} if the server dropped older
     *                                  candles to fit its per-response cap
     */
    public PriceHistoryData(ItemID itemID, int itemScaleFactor, List<Candle> candles, long currentMarketPrice,
                            long oldestAvailableTimestamp, boolean truncated)
    {
        this.itemID = itemID;
        this.itemScaleFactor = itemScaleFactor;
        this.candles = candles;
        this.currentMarketPrice = currentMarketPrice;
        this.oldestAvailableTimestamp = oldestAvailableTimestamp;
        this.truncated = truncated;
    }
    public PriceHistoryData createFromDifferentCandleDeltaTime(long currentServerTime, long candleDeltaTimeMs, long currentMarketPrice, boolean createEmptyCandleForTimeGaps)
    {
        PriceHistoryData newData = new PriceHistoryData(itemID, itemScaleFactor, new ArrayList<>(), 0);
        newData.loadFrom(this, candleDeltaTimeMs, currentServerTime, currentMarketPrice, createEmptyCandleForTimeGaps);
        return newData;
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
    public void clear(long currentServerTime)
    {
        candles.clear();
        currentMarketPrice = 0;
        startNewCandle(currentServerTime);
    }
    public void loadFrom(PriceHistoryData other)
    {
        candles.clear();
        candles.addAll(other.candles);
        currentMarketPrice = other.currentMarketPrice;
    }
    public void loadFrom(PriceHistoryData other, long candleDeltaTimeMs, long currentServerTime, long currentMarketPrice, boolean createEmptyCandleForTimeGaps)
    {
        candles.clear();

        if(other.candles.isEmpty())
            return;

        // Floor the start time by a whole "candleDeltaTimeMs" number
        long startTime = floorTime(other.candles.getFirst().openTimestamp, candleDeltaTimeMs);

        long nextTime = startTime + candleDeltaTimeMs;
        int startCandleIndex = 0;
        int newCandleCount = 0;

        for(int i=0; i<other.candles.size(); i++)
        {
            Candle candle = other.candles.get(i);
            if(candle.openTimestamp > nextTime)
            {
                Candle newCandle = Candle.merge(other.candles, startTime, startCandleIndex, newCandleCount);
                candles.add(newCandle);
                newCandleCount = 1;
                startCandleIndex = i;
                startTime = nextTime;
                nextTime += candleDeltaTimeMs;
                if(createEmptyCandleForTimeGaps) {
                    if(candle.openTimestamp > nextTime) {
                        long nextMarketPrice = candle.open;
                        /*for (int j = i + 1; j < other.candles.size(); j++) {
                            Candle jCandle = other.candles.get(j);
                            if (jCandle.openTimestamp < nextTime) {
                                nextMarketPrice = jCandle.open;
                                break;
                            }
                        }*/
                        while (candle.openTimestamp > nextTime) {



                            this.currentMarketPrice = nextMarketPrice;
                            startNewCandle(nextTime);
                            nextTime += candleDeltaTimeMs;
                            //nextTime = floorTime(candle.openTimestamp + candleDeltaTimeMs, candleDeltaTimeMs);
                        }
                    }
                }
                else
                {
                    if(candle.openTimestamp + candleDeltaTimeMs > nextTime)
                        nextTime = floorTime(candle.openTimestamp + candleDeltaTimeMs, candleDeltaTimeMs);
                }
            }
            else {
                newCandleCount++;
            }
        }
        Candle lastCandle = other.candles.getLast();
        if(lastCandle != null && newCandleCount > 0) {
            Candle lastCandleToAdd = Candle.merge(other.candles, startTime, startCandleIndex, newCandleCount);
            candles.add(lastCandleToAdd);
        }
        this.currentMarketPrice = other.currentMarketPrice;
        if(createEmptyCandleForTimeGaps) {
            while (currentServerTime > nextTime) {
                startNewCandle(nextTime);
                nextTime += candleDeltaTimeMs;
            }
        }
        else
        {
            Candle thisLastCandle = candles.getLast();
            if (thisLastCandle != null) {
                long currentServerTimeFloored = PriceHistoryData.floorTime(currentServerTime, candleDeltaTimeMs);
                if (currentServerTimeFloored > thisLastCandle.openTimestamp)
                    startNewCandle(currentServerTime);
            }
            else
            {
                startNewCandle(currentServerTime);
            }
        }

    }

    public void setCurrentMarketPrice(long currentMarketPrice)
    {
        setCurrentMarketPrice(currentMarketPrice, 0);
    }
    public void setCurrentMarketPrice(long currentMarketPrice, float tradedVolume)
    {
        this.currentMarketPrice = currentMarketPrice;
        if(!candles.isEmpty())
        {
            // Update newest candle
            Candle  candle = candles.getLast();
            candle.high = Math.max(this.currentMarketPrice, candle.high);
            candle.low = Math.min(this.currentMarketPrice, candle.low);
            candle.tradedVolume = tradedVolume;
        }
    }
    public void startNewCandle(long currentServerTime)
    {
        candles.add(new Candle(currentServerTime, currentMarketPrice,currentMarketPrice,currentMarketPrice));
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
        if(candles.isEmpty())
            return 0;
        long min = Long.MAX_VALUE;
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
        if(candles.isEmpty())
            return 0;
        long max = Long.MIN_VALUE;
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

    /**
     * @return the oldest timestamp the server has recorded for this market, or
     *         {@link #NO_OLDER_DATA} when there is no data older than the
     *         returned candles.
     */
    public long getOldestAvailableTimestamp()
    {
        return oldestAvailableTimestamp;
    }

    /**
     * @return {@code true} if the server truncated the response (dropped older
     *         candles) to fit its per-response cap. The returned candle list is
     *         the newest-N; caller may need to page further back.
     */
    public boolean isTruncated()
    {
        return truncated;
    }

    /**
     * Set the oldest-available-timestamp scalar. Called by the server-side
     * request handler after querying the database.
     */
    public void setOldestAvailableTimestamp(long oldestAvailableTimestamp)
    {
        this.oldestAvailableTimestamp = oldestAvailableTimestamp;
    }

    /**
     * Set the truncated flag. Called by the server-side request handler when it
     * drops older candles to fit the per-response cap.
     */
    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }
    public double toRealPrice(long rawPrice)
    {
        return BankManager.convertToRealAmountStatic(rawPrice, itemScaleFactor);
    }
    public long toRawPrice(double realPrice)
    {
        return BankManager.convertToRawAmountStatic(realPrice, itemScaleFactor);
    }
    public static long floorTime(long inputTime, long roundingTimeBase)
    {
        return (inputTime / roundingTimeBase) * roundingTimeBase;
    }


}
