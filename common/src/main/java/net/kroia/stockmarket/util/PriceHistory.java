package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public class PriceHistory implements ServerSaveable {

    private final int maxHistorySize = StockMarketModSettings.UI.PRICE_HISTORY_SIZE;
    private int[] lowPrice = new int[maxHistorySize];
    private int[] highPrice = new int[maxHistorySize];
    private int[] closePrice = new int[maxHistorySize];
    private long[] volume = new long[maxHistorySize];
    private int oldestClosePrice = 0;
    private Timestamp[] timeStamps = new Timestamp[maxHistorySize];

    private String itemID;

    public PriceHistory(String itemID) {
        this.itemID = itemID;
        for(int i = 0; i < maxHistorySize; i++)
        {
            timeStamps[i] = new Timestamp();
        }
        clear();
    }
    public PriceHistory(String itemID, int initialPrice) {
        this.itemID = itemID;
        oldestClosePrice = initialPrice;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = initialPrice;
            highPrice[i] = initialPrice;
            closePrice[i] = initialPrice;
            volume[i] = 0;
            timeStamps[i] = new Timestamp();
        }
    }
    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public void clear()
    {
        oldestClosePrice = 0;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = 0;
            highPrice[i] = 0;
            closePrice[i] = 0;
            volume[i] = 0;
        }
    }

    public int size()
    {
        return lowPrice.length;
    }

    public String getItemID()
    {
        return itemID;
    }
    public void addPrice(int low, int high, int close, Timestamp timestamp)
    {
        // Shift the prices to the left
        oldestClosePrice = closePrice[0];
        for (int i = 0; i < maxHistorySize-1; i++) {
            lowPrice[i] = lowPrice[i+1];
            highPrice[i] = highPrice[i+1];
            closePrice[i] = closePrice[i+1];
            volume[i] = volume[i+1];
            timeStamps[i] = timeStamps[i+1];
        }
        lowPrice[maxHistorySize-1] = low;
        highPrice[maxHistorySize-1] = high;
        closePrice[maxHistorySize-1] = close;
        volume[maxHistorySize-1] = 0;
        timeStamps[maxHistorySize-1] = timestamp;
    }



    public void setCurrentPrice(int close)
    {
        closePrice[maxHistorySize-1] = close;
        lowPrice[maxHistorySize-1] = Math.min(lowPrice[maxHistorySize-1], close);
        highPrice[maxHistorySize-1] = Math.max(highPrice[maxHistorySize-1], close);
    }
    public void setCurrentVolume(int volume)
    {
        this.volume[maxHistorySize-1] = volume;
    }
    public void addVolume(int volume)
    {
        this.volume[maxHistorySize-1] += volume;
    }
    public int getCurrentPrice()
    {
        return closePrice[maxHistorySize-1];
    }
    public long getCurrentVolume()
    {
        return volume[maxHistorySize-1];
    }

    public int getLowPrice(int index)
    {
        return lowPrice[index];
    }
    public int getLowPrice()
    {
        return lowPrice[maxHistorySize-1];
    }

    public int getHighPrice(int index)
    {
        return highPrice[index];
    }
    public int getHighPrice()
    {
        return highPrice[maxHistorySize-1];
    }

    public int getClosePrice(int index)
    {
        return closePrice[index];
    }
    public int getClosePrice()
    {
        return closePrice[maxHistorySize-1];
    }

    public long getVolume(int index)
    {
        return volume[index];
    }
    public long getMaxVolume()
    {
        long max = Long.MIN_VALUE;
        for (int i = 0; i < maxHistorySize; i++) {
            max = Math.max(max, volume[i]);
        }
        return max;
    }

    public int getOpenPrice(int index)
    {
        if(index == 0)
        {
            return oldestClosePrice;
        }
        return closePrice[index-1];
    }

    public int getLowestPrice()
    {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < maxHistorySize; i++) {
            min = Math.min(min, lowPrice[i]);
        }
        return min;
    }
    public int getHighestPrice()
    {
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < maxHistorySize; i++) {
            max = Math.max(max, highPrice[i]);
        }
        return max;
    }

    public Timestamp getTimeStamp(int index)
    {
        return timeStamps[index];
    }


    // Interface to send the timestamp over the network
    public PriceHistory(FriendlyByteBuf buf) {
        itemID = buf.readUtf();
        oldestClosePrice = buf.readInt();
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = buf.readInt();
            highPrice[i] = buf.readInt();
            closePrice[i] = buf.readInt();
            volume[i] = buf.readLong();
            timeStamps[i] = new Timestamp(buf);
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
        buf.writeInt(oldestClosePrice);
        for (int i = 0; i < maxHistorySize; i++) {
            buf.writeInt(lowPrice[i]);
            buf.writeInt(highPrice[i]);
            buf.writeInt(closePrice[i]);
            buf.writeLong(volume[i]);
            timeStamps[i].toBytes(buf);
        }
    }


    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        tag.putIntArray("lowPrice", lowPrice);
        tag.putIntArray("highPrice", highPrice);
        tag.putIntArray("closePrice", closePrice);
        tag.putLongArray("volume", volume);
        tag.putInt("oldestClosePrice", oldestClosePrice);

        ListTag times = new ListTag();
        for (int i = 0; i < maxHistorySize; i++) {
            CompoundTag timeTag = new CompoundTag();
            success &= timeStamps[i].save(timeTag);
            times.add(timeTag);
        }
        tag.put("timeStamps", times);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;

        if(!tag.contains("lowPrice") ||
           !tag.contains("highPrice") ||
           !tag.contains("closePrice") ||
           !tag.contains("oldestClosePrice") ||
           !tag.contains("timeStamps"))
            return false;
        boolean success = true;

        int[] tmpLowPrice = tag.getIntArray("lowPrice");
        int[] tmpHighPrice = tag.getIntArray("highPrice");
        int[] tmpClosePrice = tag.getIntArray("closePrice");
        boolean hasVolume = tag.contains("volume");
        long[] tmpVolume = tag.getLongArray("volume");
        oldestClosePrice = tag.getInt("oldestClosePrice");
        Timestamp[] tmpTimeStamps = new Timestamp[maxHistorySize];
        ListTag times = tag.getList("timeStamps", 10);
        for (int i = 0; i < tmpLowPrice.length; i++) {
            CompoundTag timeTag = times.getCompound(i);
            tmpTimeStamps[i] = Timestamp.loadFromTag(timeTag);
            if(tmpTimeStamps[i] == null)
                success = false;
        }

        if(tmpLowPrice.length != maxHistorySize)
        {
            if(tmpLowPrice.length < maxHistorySize)
            {
                for(int i=0; i<maxHistorySize-tmpLowPrice.length; i++)
                {
                    lowPrice[i] = tmpLowPrice[0];
                    highPrice[i] = tmpHighPrice[0];
                    closePrice[i] = tmpClosePrice[0];
                    if(hasVolume)
                        volume[i] = tmpVolume[0];
                    timeStamps[i] = tmpTimeStamps[0];

                }
                for(int i=maxHistorySize-tmpLowPrice.length; i<maxHistorySize; i++)
                {
                    lowPrice[i] = tmpLowPrice[i-maxHistorySize+tmpLowPrice.length];
                    highPrice[i] = tmpHighPrice[i-maxHistorySize+tmpLowPrice.length];
                    closePrice[i] = tmpClosePrice[i-maxHistorySize+tmpLowPrice.length];
                    if(hasVolume)
                        volume[i] = tmpVolume[i-maxHistorySize+tmpLowPrice.length];
                    timeStamps[i] = tmpTimeStamps[i-maxHistorySize+tmpLowPrice.length];
                }
            }
            else {
                for(int i=0; i<maxHistorySize; i++)
                {
                    lowPrice[i] = tmpLowPrice[tmpLowPrice.length-maxHistorySize+i];
                    highPrice[i] = tmpHighPrice[tmpLowPrice.length-maxHistorySize+i];
                    closePrice[i] = tmpClosePrice[tmpLowPrice.length-maxHistorySize+i];
                    if(hasVolume)
                        volume[i] = tmpVolume[tmpLowPrice.length-maxHistorySize+i];
                    timeStamps[i] = tmpTimeStamps[tmpLowPrice.length-maxHistorySize+i];
                }
            }
        }
        else {
            lowPrice = tmpLowPrice;
            highPrice = tmpHighPrice;
            closePrice = tmpClosePrice;
            if(hasVolume)
                volume = tmpVolume;
        }
        return success;
    }
}
