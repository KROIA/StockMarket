package net.kroia.stockmarket.market.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.util.DynamicIndexedArray;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;


/**
 * This class represents a virtual order book that is used to simulate the order book of a stock market.
 * Players can buy or sell into the virtual orders.
 */
public class VirtualOrderBook implements ServerSaveable {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public static class Settings implements ServerSaveable, INetworkPayloadConverter
    {
        public float volumeScale;
        public float nearMarketVolumeScale;
        public float volumeAccumulationRate ;
        public float volumeFastAccumulationRate;
        public float volumeDecumulationRate;

        public Settings()
        {
            if(BACKEND_INSTANCES.SERVER_SETTINGS != null)
            {
                volumeScale = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ORDER_BOOK_VOLUME_SCALE.get();
                nearMarketVolumeScale = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.NEAR_MARKET_VOLUME_SCALE.get();
                volumeAccumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_ACCUMULATION_RATE.get();
                volumeFastAccumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_FAST_ACCUMULATION_RATE.get();
                volumeDecumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_DECUMULATION_RATE.get();
            }
            else {
                volumeScale = 100.0f;
                nearMarketVolumeScale = 2f;
                volumeAccumulationRate = 0.001f;
                volumeFastAccumulationRate = 0.1f;
                volumeDecumulationRate = 0.0001f;
            }
        }

        public Settings(Settings other) {
            this.volumeScale = other.volumeScale;
            this.nearMarketVolumeScale = other.nearMarketVolumeScale;
            this.volumeAccumulationRate = other.volumeAccumulationRate;
            this.volumeFastAccumulationRate = other.volumeFastAccumulationRate;
            this.volumeDecumulationRate = other.volumeDecumulationRate;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat("volumeScale", volumeScale);
            tag.putFloat("nearMarketVolumeScale", nearMarketVolumeScale);
            tag.putFloat("volumeAccumulationRate", volumeAccumulationRate);
            tag.putFloat("volumeFastAccumulationRate", volumeFastAccumulationRate);
            tag.putFloat("volumeDecumulationRate", volumeDecumulationRate);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains("volumeScale"))
                volumeScale = tag.getFloat("volumeScale");
            if(tag.contains("nearMarketVolumeScale"))
                nearMarketVolumeScale = tag.getFloat("nearMarketVolumeScale");
            if(tag.contains("volumeAccumulationRate"))
                volumeAccumulationRate = tag.getFloat("volumeAccumulationRate");
            if(tag.contains("volumeFastAccumulationRate"))
                volumeFastAccumulationRate = tag.getFloat("volumeFastAccumulationRate");
            if(tag.contains("volumeDecumulationRate"))
                volumeDecumulationRate = tag.getFloat("volumeDecumulationRate");

            if(volumeAccumulationRate <= 0)
                this.volumeAccumulationRate = 0.00001f;
            if(volumeFastAccumulationRate <= 0)
                this.volumeFastAccumulationRate = 0.00001f;
            if(volumeDecumulationRate <= 0)
                this.volumeDecumulationRate = 0.00001f;
            return true;
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            volumeScale = buf.readFloat();
            nearMarketVolumeScale = buf.readFloat();
            volumeAccumulationRate = buf.readFloat();
            volumeFastAccumulationRate = buf.readFloat();
            volumeDecumulationRate = buf.readFloat();

            if(volumeAccumulationRate <= 0)
                this.volumeAccumulationRate = 0.00001f;
            if(volumeFastAccumulationRate <= 0)
                this.volumeFastAccumulationRate = 0.00001f;
            if(volumeDecumulationRate <= 0)
                this.volumeDecumulationRate = 0.00001f;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(volumeScale);
            buf.writeFloat(nearMarketVolumeScale);
            buf.writeFloat(volumeAccumulationRate);
            buf.writeFloat(volumeFastAccumulationRate);
            buf.writeFloat(volumeDecumulationRate);
        }
        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("volumeScale", volumeScale);
            jsonObject.addProperty("nearMarketVolumeScale", nearMarketVolumeScale);
            jsonObject.addProperty("volumeAccumulationRate", volumeAccumulationRate);
            jsonObject.addProperty("volumeFastAccumulationRate", volumeFastAccumulationRate);
            jsonObject.addProperty("volumeDecumulationRate", volumeDecumulationRate);
            return jsonObject;
        }

        public boolean fromJson(JsonElement json) {
            if(json.isJsonObject()) {
                JsonObject jsonObject = json.getAsJsonObject();
                if(jsonObject.has("volumeScale"))
                    volumeScale = jsonObject.get("volumeScale").getAsFloat();
                if(jsonObject.has("nearMarketVolumeScale"))
                    nearMarketVolumeScale = jsonObject.get("nearMarketVolumeScale").getAsFloat();
                if(jsonObject.has("volumeAccumulationRate"))
                    volumeAccumulationRate = jsonObject.get("volumeAccumulationRate").getAsFloat();
                if(jsonObject.has("volumeFastAccumulationRate"))
                    volumeFastAccumulationRate = jsonObject.get("volumeFastAccumulationRate").getAsFloat();
                if(jsonObject.has("volumeDecumulationRate"))
                    volumeDecumulationRate = jsonObject.get("volumeDecumulationRate").getAsFloat();

                return true;
            }
            return false;
        }

    }
    private int currentMarketPrice = 0;
    private final DynamicIndexedArray virtualOrderVolumeDistribution;
    private long lastMillis;
    private Settings settings = new Settings();
    private int priceScaleFactor = 1;
    //private int itemFractionScaleFactor = 1; // Scale factor for item fractions, used to handle fractional item amounts.


    //private boolean dbgSavedToFile = false;
    private Function<Float, Float> defaultVolumeDistributionFunction = null;

    public VirtualOrderBook(int realVolumeBookSize, int initialPrice) {
        virtualOrderVolumeDistribution = new DynamicIndexedArray(realVolumeBookSize, this::getTargetAmount);
        lastMillis = System.currentTimeMillis()-1000;
        setCurrentPrice(initialPrice);
        //updateVolume(initialPrice);
        virtualOrderVolumeDistribution.resetToDefaultValues();
    }

    public void cleanup() {
        virtualOrderVolumeDistribution.clear();
    }

    public void setPriceScaleFactor(int priceScaleFactor) {
        if(priceScaleFactor <= 0)
            priceScaleFactor = 1;
        this.priceScaleFactor = priceScaleFactor;
    }
    public void setDefaultVolumeDistributionFunction(Function<Float, Float> defaultVolumeDistributionFunction) {
        this.defaultVolumeDistributionFunction = defaultVolumeDistributionFunction;
        //virtualOrderVolumeDistribution.resetToDefaultValues();
    }

    public void updateVolume(int currentPrice) {
        /*
        long currentMillis = System.currentTimeMillis();
        double deltaT = Math.min((currentMillis - lastMillis) / 1000.0, 1.0);
        lastMillis = currentMillis;

        int startOffset = 0;
        if(virtualOrderVolumeDistribution.getIndexOffset() < 0)
        {
            startOffset -= virtualOrderVolumeDistribution.getIndexOffset();
        }
        for(int i=startOffset; i<virtualOrderVolumeDistribution.getSize(); i++)
        {
            int priceIndex = virtualOrderVolumeDistribution.getVirtualIndex(i);
            if(priceIndex < 0)
                continue;
            float targetAmount = getTargetAmount(priceIndex);
            float currentVal = virtualOrderVolumeDistribution.get(priceIndex);
            if((currentVal<targetAmount) || (currentVal>targetAmount)) {
                if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
                {
                    currentVal = 0;
                    virtualOrderVolumeDistribution.set(priceIndex, currentVal);
                }

                float scale = settings.volumeAccumulationRate;

                if(Math.abs(currentVal) < Math.abs(targetAmount)*0.2f)
                {
                    scale = settings.volumeFastAccumulationRate;
                }else if(Math.abs(currentVal) > Math.abs(targetAmount))
                {
                    scale = settings.volumeDecumulationRate;
                }
                float deltaAmount = (targetAmount - currentVal) * (float) deltaT * scale;
                if(deltaAmount < 0 && currentVal > 0 && -deltaAmount > currentVal)
                {
                    deltaAmount = -currentVal;
                }
                else if(deltaAmount > 0 && currentVal < 0 && deltaAmount > -currentVal)
                {
                    deltaAmount = -currentVal;
                }
                virtualOrderVolumeDistribution.add(priceIndex, deltaAmount);

            }
        }
        */
    }

    public void setCurrentPrice(int currentMarketPrice) {
        this.currentMarketPrice = currentMarketPrice;
        int currentIndexOffset = virtualOrderVolumeDistribution.getIndexOffset();
        int sizeForth = virtualOrderVolumeDistribution.getSize()/4;
        if(currentMarketPrice > currentIndexOffset + sizeForth*3)
        {
            virtualOrderVolumeDistribution.setOffset(currentMarketPrice-virtualOrderVolumeDistribution.getSize()/2);
        }
        else if(currentMarketPrice < currentIndexOffset + sizeForth)
        {
            virtualOrderVolumeDistribution.setOffset(currentMarketPrice-virtualOrderVolumeDistribution.getSize()/2);
        }
    }


    public void setSettings(Settings settings)
    {
        this.settings = settings;
    }
    public void resetVolumeDistribution()
    {
        virtualOrderVolumeDistribution.resetToDefaultValues();
    }
    public Settings getSettings() {
        return settings;
    }

    public int getMinEditablePrice()
    {
        return virtualOrderVolumeDistribution.getVirtualIndex(0);
    }
    public int getMaxEditablePrice()
    {
        return virtualOrderVolumeDistribution.getVirtualIndex(virtualOrderVolumeDistribution.getSize()-1);
    }

    public void setVolume(int minPrice, int maxPrice, float volume) {
        if(minPrice > maxPrice)
            return;
        float volumePerPricerange = Math.max(0, volume) / (maxPrice - minPrice + 1);
        for(int i=minPrice; i<=maxPrice; i++)
        {
            if(virtualOrderVolumeDistribution.isInRange(i)) {
                // Change sign if target price is > currentMarketPrice
                if(i > currentMarketPrice)
                    virtualOrderVolumeDistribution.set(i, -volumePerPricerange);
                else if(i < currentMarketPrice)
                    virtualOrderVolumeDistribution.set(i, volumePerPricerange);
            }
        }
    }
    public void addVolume(int minPrice, int maxPrice, float volume) {
        if(minPrice > maxPrice)
            return;
        float volumePerPricerange = Math.max(0, volume) / (maxPrice - minPrice + 1);
        for(int i=minPrice; i<=maxPrice; i++)
        {
            if(virtualOrderVolumeDistribution.isInRange(i)) {
                // Change sign if target price is > currentMarketPrice
                if(i > currentMarketPrice)
                    virtualOrderVolumeDistribution.add(i, -volumePerPricerange);
                else if(i < currentMarketPrice)
                    virtualOrderVolumeDistribution.add(i, volumePerPricerange);
            }
        }
    }
    public void setVolume(int startPrice, float[] volume, float scaleMultiplier) {
        virtualOrderVolumeDistribution.set(startPrice, volume, currentMarketPrice, scaleMultiplier);
    }
    public void addVolume(int startPrice, float[] volume, float scaleMultiplier) {
        virtualOrderVolumeDistribution.add(startPrice, volume, currentMarketPrice, scaleMultiplier);
    }

    public void setVolumeScale(float volumeScale) {
        this.settings.volumeScale = volumeScale;
    }
    public float getVolumeScale() {
        return settings.volumeScale;
    }

    public void setNearMarketVolumeScale(float nearMarketVolumeScale) {
        this.settings.nearMarketVolumeScale = nearMarketVolumeScale;
    }
    public float getNearMarketVolumeScale() {
        return settings.nearMarketVolumeScale;
    }
    public void setVolumeAccumulationRate(float volumeAccumulationRate) {
        this.settings.volumeAccumulationRate = volumeAccumulationRate;
        if(volumeAccumulationRate <= 0)
            this.settings.volumeAccumulationRate = 0.00001f;
    }
    public float getVolumeAccumulationRate() {
        return settings.volumeAccumulationRate;
    }
    public void setVolumeFastAccumulationRate(float volumeFastAccumulationRate) {
        this.settings.volumeFastAccumulationRate = volumeFastAccumulationRate;
        if(volumeFastAccumulationRate <= 0)
            this.settings.volumeFastAccumulationRate = 0.00001f;
    }
    public float getVolumeFastAccumulationRate() {
        return settings.volumeFastAccumulationRate;
    }
    public void setVolumeDecumulationRate(float volumeDecumulationRate) {
        this.settings.volumeDecumulationRate = volumeDecumulationRate;
        if(volumeDecumulationRate <= 0)
            this.settings.volumeDecumulationRate = 0.00001f;
    }
    public float getVolumeDecumulationRate() {
        return settings.volumeDecumulationRate;
    }



    /**
     * Get the amount of items to buy or sell based on the price difference
     * @param rawPrice on which the amount should be based
     * @return the amount of items to buy or sell. Negative values indicate selling
     */
    public float getAmount(int rawPrice)
    {
        if(virtualOrderVolumeDistribution.isInRange(rawPrice))
        {
            return virtualOrderVolumeDistribution.get(rawPrice);
        }
        return getTargetAmount(rawPrice);
    }
    public void removeAmount(int rawPrice, long amount)
    {
        if(virtualOrderVolumeDistribution.isInRange(rawPrice))
        {
            if(virtualOrderVolumeDistribution.get(rawPrice) > 0)
            {
                virtualOrderVolumeDistribution.add(rawPrice, -Math.abs(amount));
            }
            else if(virtualOrderVolumeDistribution.get(rawPrice) < 0)
            {
                virtualOrderVolumeDistribution.add(rawPrice, Math.abs(amount));
            }
        }
    }

    private float getTargetAmount(int price)
    {
        if(defaultVolumeDistributionFunction != null)
        {
            float realPrice = price / (float)priceScaleFactor;
            float volume = Math.abs(defaultVolumeDistributionFunction.apply(realPrice));
            if(currentMarketPrice > price)
                return volume;
            else if(currentMarketPrice < price)
                return -volume;
            else
                return volume;
        }
        return 0;


        /*
        if(price < 0)
            return 0;
        // Calculate close price volume distribution
        float currentPriceFloat = (float)currentMarketPrice;
        //float relativePrice = (currentPriceFloat - (float)price)/(currentPriceFloat+1);
        float relativePrice = (currentPriceFloat - (float)price);

        final float constant1 = (float)(2.0/Math.E);

        float amount = 0;
        if(relativePrice < 40 && relativePrice > -40) {
            //amount += (float) Math.E * width * relativePrice * (float) Math.exp(-Math.abs(relativePrice * width));
            float sqrt = (float) Math.sqrt(Math.abs(relativePrice)) * Math.signum(relativePrice);
            amount += (float) constant1 * settings.nearMarketVolumeScale * sqrt * (float) Math.exp(-Math.abs(relativePrice*relativePrice*0.01));
        }


        if(relativePrice > 0)
            amount += 0.1f;
        else if(relativePrice <= 0)
            amount += -0.1f;
        if(price == 0)
            amount += 0.2f;

        float lowPriceAccumulator = 1/(1+(float)price);
        if(relativePrice > 0)
            amount += lowPriceAccumulator*5;

        return (amount*settings.volumeScale);*/
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag.contains("virtualOrderVolumeDistribution"))
            virtualOrderVolumeDistribution.load(tag.getCompound("virtualOrderVolumeDistribution"));
        settings.load(tag);
        return true;
    }
    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag virtualOrderVolumeDistributionTag = new CompoundTag();
        virtualOrderVolumeDistribution.save(virtualOrderVolumeDistributionTag);
        tag.put("virtualOrderVolumeDistribution", virtualOrderVolumeDistributionTag);
        settings.save(tag);
        return true;
    }

    public JsonElement toJson()
    {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("settings", settings.toJson());
        jsonObject.addProperty("currentMarketPrice", currentMarketPrice);
        jsonObject.addProperty("lastMillis", lastMillis);
        jsonObject.addProperty("virtualOrderVolumeDistributionSize", virtualOrderVolumeDistribution.getSize());
        return jsonObject;
    }
    public String toJsonString() {
        return JsonUtilities.toPrettyString(toJson());
    }
    @Override
    public String toString() {
        return toJsonString();
    }
}
