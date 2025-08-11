package net.kroia.stockmarket.util;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.market.server.ServerMarketManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;

public class PriceHistory implements ServerSaveable, INetworkPayloadConverter {


    private int maxHistorySize;
    private int[] lowPrice;
    private int[] highPrice;
    private int[] closePrice;
    private long[] volume;
    private int oldestClosePrice = 0;
    private Timestamp[] timeStamps;// = new Timestamp[maxHistorySize];
    private int priceScaleFactor = 1;
    private int currencyItemFractionScaleFactor = 1; // Default scale factor for currency items
    public PriceHistory(int maxHistorySize) {
        if(maxHistorySize<1)
            maxHistorySize = 1;
        this.maxHistorySize = maxHistorySize;
        lowPrice = new int[maxHistorySize];
        highPrice = new int[maxHistorySize];
        closePrice = new int[maxHistorySize];
        volume = new long[maxHistorySize];
        timeStamps = new Timestamp[maxHistorySize];
        for(int i = 0; i < maxHistorySize; i++)
        {
            timeStamps[i] = new Timestamp();
        }
        clear();
    }
    public PriceHistory(int initialPrice, int maxHistorySize) {
        if(maxHistorySize<1)
            maxHistorySize = 1;
        this.maxHistorySize = maxHistorySize;
        lowPrice = new int[maxHistorySize];
        highPrice = new int[maxHistorySize];
        closePrice = new int[maxHistorySize];
        volume = new long[maxHistorySize];
        timeStamps = new Timestamp[maxHistorySize];
        oldestClosePrice = initialPrice;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = initialPrice;
            highPrice[i] = initialPrice;
            closePrice[i] = initialPrice;
            volume[i] = 0;
            timeStamps[i] = new Timestamp();
        }
    }

    public void setScaleFactors(int priceScaleFactor, int currencyItemFractionScaleFactor)
    {
        this.priceScaleFactor = priceScaleFactor;
        this.currencyItemFractionScaleFactor = currencyItemFractionScaleFactor;
    }
    public int getPriceScaleFactor()
    {
        return priceScaleFactor;
    }
    public int getCurrencyItemFractionScaleFactor()
    {
        return currencyItemFractionScaleFactor;
    }



    public static PriceHistory copy(PriceHistory org, int maxHistorySize)
    {
        if(org == null)
            return null;
        int oldestPrice = org.oldestClosePrice;

        if(org.maxHistorySize < maxHistorySize || maxHistorySize < 1) {
            maxHistorySize = org.maxHistorySize;
        }
        else {
            oldestPrice = org.getOpenRawPrice(maxHistorySize - 1);
        }

        PriceHistory copy = new PriceHistory(maxHistorySize);
        copy.oldestClosePrice = oldestPrice;
        copy.priceScaleFactor = org.priceScaleFactor;
        copy.currencyItemFractionScaleFactor = org.currencyItemFractionScaleFactor;

        for (int i = 0; i < maxHistorySize; i++) {
            copy.lowPrice[i] = org.lowPrice[i];
            copy.highPrice[i] = org.highPrice[i];
            copy.closePrice[i] = org.closePrice[i];
            copy.volume[i] = org.volume[i];
            copy.timeStamps[i] = org.timeStamps[i].copy();
        }
        return copy;
    }

    public void clear()
    {
        clear(0);
    }
    public void clear(int defaultValue)
    {
        oldestClosePrice = defaultValue;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = defaultValue;
            highPrice[i] = defaultValue;
            closePrice[i] = defaultValue;
            volume[i] = 0;
        }
    }

    public int size()
    {
        return lowPrice.length;
    }

    public void addPrice(int low, int high, int close, Timestamp timestamp)
    {
        // Shift the prices to the left
        /*oldestClosePrice = closePrice[0];
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
        timeStamps[maxHistorySize-1] = timestamp;*/

        oldestClosePrice = closePrice[maxHistorySize-1];
        for (int i = maxHistorySize-1; i > 0; i--) {
            lowPrice[i] = lowPrice[i-1];
            highPrice[i] = highPrice[i-1];
            closePrice[i] = closePrice[i-1];
            volume[i] = volume[i-1];
            timeStamps[i] = timeStamps[i-1];
        }
        lowPrice[0] = low;
        highPrice[0] = high;
        closePrice[0] = close;
        volume[0] = 0;
        timeStamps[0] = timestamp;
    }




    public void setCurrentRawPrice(int close)
    {
        closePrice[0] = close;
        lowPrice[0] = Math.min(lowPrice[0], close);
        highPrice[0] = Math.max(highPrice[0], close);
    }
    public void setCurrentVolume(long volume)
    {
        this.volume[0] = volume;
    }
    public void addVolume(long volume)
    {
        this.volume[0] += volume;
    }
    public int getCurrentRawPrice()
    {
        return closePrice[0];
    }
    public long getCurrentVolume()
    {
        return volume[0];
    }

    public int getLowRawPrice(int index)
    {
        return lowPrice[index];
    }
    public int getLowRawPrice()
    {
        return lowPrice[0];
    }

    public int getHighRawPrice(int index)
    {
        return highPrice[index];
    }
    public int getHighRawPrice()
    {
        return highPrice[0];
    }

    public int getCloseRawPrice(int index)
    {
        return closePrice[index];
    }
    public int getCloseRawPrice()
    {
        return closePrice[0];
    }

    public long getVolume(int index)
    {
        return volume[index];
    }

    public long getMaxVolume()
    {
        return getMaxVolume(-1);
    }
    public long getMaxVolume(int lastIndex)
    {
        if(lastIndex <= 0 || lastIndex > maxHistorySize)
            lastIndex = maxHistorySize;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < lastIndex; i++) {
            max = Math.max(max, volume[i]);
        }
        return max;
    }


    public int getOpenRawPrice(int index)
    {
        if(index == maxHistorySize-1)
        {
            return oldestClosePrice;
        }
        return closePrice[index+1];
    }

    public int getLowestRawPrice()
    {
        return getLowestRawPrice(-1);
    }
    public int getLowestRawPrice(int lastIndex)
    {
        if(lastIndex <= 0 || lastIndex > maxHistorySize)
            lastIndex = maxHistorySize;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < lastIndex; i++) {
            min = Math.min(min, lowPrice[i]);
        }
        return min;
    }

    public int getHighestRawPrice()
    {
        return getHighestRawPrice(-1);
    }
    public int getHighestRawPrice(int lastIndex)
    {
        if(lastIndex <= 0 || lastIndex > maxHistorySize)
            lastIndex = maxHistorySize;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < lastIndex; i++) {
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
        decode(buf);
    }


    public float getOpenRealPrice(int index) {
        return getRealPrice(getOpenRawPrice(index));
    }
    public float getOpenRealPrice() {
        return getRealPrice(getOpenRawPrice(0));
    }
    public float getLowRealPrice(int index) {
        return getRealPrice(getLowRawPrice(index));
    }
    public float getLowRealPrice() {
        return getRealPrice(getLowRawPrice(0));
    }
    public float getHighRealPrice(int index) {
        return getRealPrice(getHighRawPrice(index));
    }
    public float getHighRealPrice() {
        return getRealPrice(getHighRawPrice(0));
    }
    public float getCloseRealPrice(int index) {
        return getRealPrice(getCloseRawPrice(index));
    }
    public float getCloseRealPrice() {
        return getRealPrice(getCloseRawPrice(0));
    }
    public float getLowestRealPrice() {
        return getRealPrice(getLowestRawPrice());
    }
    public float getLowestRealPrice(int lastIndex) {
        return getRealPrice(getLowestRawPrice(lastIndex));
    }
    public float getHighestRealPrice() {
        return getRealPrice(getHighestRawPrice());
    }
    public float getHighestRealPrice(int lastIndex) {
        return getRealPrice(getHighestRawPrice(lastIndex));
    }
    public float getCurrentRealPrice() {
        return getRealPrice(getCurrentRawPrice());
    }


    private float getRealPrice(int price) {
        return ServerMarketManager.rawToRealPrice(price, priceScaleFactor);
    }
    public String getRealPriceString(int rawPrice)
    {
        return Bank.getFormattedAmount(getRealPrice(rawPrice), currencyItemFractionScaleFactor);
    }
    public String getRealPriceString(float realPrice)
    {
        return Bank.getFormattedAmount(realPrice, currencyItemFractionScaleFactor);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(oldestClosePrice);
        buf.writeInt(priceScaleFactor);
        buf.writeInt(currencyItemFractionScaleFactor); // Write the currency item fraction scale factor
        buf.writeInt(maxHistorySize);
        for (int i = 0; i < maxHistorySize; i++) {
            buf.writeInt(lowPrice[i]);
            buf.writeInt(highPrice[i]);
            buf.writeInt(closePrice[i]);
            buf.writeLong(volume[i]);
            timeStamps[i].toBytes(buf);
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        oldestClosePrice = buf.readInt();
        priceScaleFactor = buf.readInt();
        currencyItemFractionScaleFactor = buf.readInt(); // Read the currency item fraction scale factor
        maxHistorySize = buf.readInt();
        lowPrice = new int[maxHistorySize];
        highPrice = new int[maxHistorySize];
        closePrice = new int[maxHistorySize];
        volume = new long[maxHistorySize];
        timeStamps = new Timestamp[maxHistorySize];
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = buf.readInt();
            highPrice[i] = buf.readInt();
            closePrice[i] = buf.readInt();
            volume[i] = buf.readLong();
            timeStamps[i] = new Timestamp(buf);
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
        tag.putInt("priceScaleFactor", priceScaleFactor);
        tag.putInt("currencyItemFractionScaleFactor", currencyItemFractionScaleFactor);

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
           !tag.contains("timeStamps") ||
           !tag.contains("volume") ||
           !tag.contains("priceScaleFactor") ||
           !tag.contains("currencyItemFractionScaleFactor"))
            return false;
        boolean success = true;

        int[] tmpLowPrice = tag.getIntArray("lowPrice");
        int[] tmpHighPrice = tag.getIntArray("highPrice");
        int[] tmpClosePrice = tag.getIntArray("closePrice");
        long[] tmpVolume = tag.getLongArray("volume");
        oldestClosePrice = tag.getInt("oldestClosePrice");
        Timestamp[] tmpTimeStamps = new Timestamp[tmpLowPrice.length];
        ListTag times = tag.getList("timeStamps", 10);
        for (int i = 0; i < tmpLowPrice.length; i++) {
            CompoundTag timeTag = times.getCompound(i);
            tmpTimeStamps[i] = Timestamp.loadFromTag(timeTag);
            if(tmpTimeStamps[i] == null)
                success = false;
        }

        this.maxHistorySize = tmpLowPrice.length;
        lowPrice = new int[maxHistorySize];
        highPrice = new int[maxHistorySize];
        closePrice = new int[maxHistorySize];
        volume = new long[maxHistorySize];
        timeStamps = new Timestamp[maxHistorySize];
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = tmpLowPrice[i];
            highPrice[i] = tmpHighPrice[i];
            closePrice[i] = tmpClosePrice[i];
            volume[i] = tmpVolume[i];
            timeStamps[i] = tmpTimeStamps[i].copy();
        }

        if(tag.contains("priceScaleFactor"))
        {
            priceScaleFactor = tag.getInt("priceScaleFactor");
        }
        else
        {
            priceScaleFactor = 1; // Default scale factor
        }
        if(tag.contains("currencyItemFractionScaleFactor"))
        {
            currencyItemFractionScaleFactor = tag.getInt("currencyItemFractionScaleFactor");
        }
        else
        {
            currencyItemFractionScaleFactor = 1; // Default scale factor for currency items
        }



        /*int minSize = Math.min(tmpLowPrice.length, maxHistorySize);
        for(int i=0; i<minSize; i++)
        {
            lowPrice[i] = tmpLowPrice[i];
            highPrice[i] = tmpHighPrice[i];
            closePrice[i] = tmpClosePrice[i];
            volume[i] = tmpVolume[i];
            timeStamps[i] = tmpTimeStamps[i];
        }

        if(maxHistorySize > tmpLowPrice.length)
        {
            Timestamp dummy;
            if(tmpTimeStamps.length > 0)
            {
                dummy = tmpTimeStamps[minSize-1].copy();
            }
            else {
                dummy = new Timestamp();
            }
            for(int i=minSize; i<maxHistorySize; i++)
            {
                lowPrice[i] = 0;
                highPrice[i] = 0;
                closePrice[i] = 0;
                volume[i] = 0;
                timeStamps[i] = dummy.copy();
            }
        }*/
        return success;
    }
}
