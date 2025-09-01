package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.kroia.stockmarket.plugin.base.IPluginSettings;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

public class DefaultOrderbookVolumeDistributionPlugin extends MarketPlugin {



    public static class Settings implements IPluginSettings
    {
        public float volumeScale;
        public float nearMarketVolumeScale;
        public float volumeAccumulationRate ;
        public float volumeFastAccumulationRate;
        public float volumeDecumulationRate;

        public Settings()
        {
            volumeScale = 100.0f;
            nearMarketVolumeScale = 2f;
            volumeAccumulationRate = 0.005f;
            volumeFastAccumulationRate = 0.1f;
            volumeDecumulationRate = 0.0001f;
        }

        public Settings(VirtualOrderBook.Settings other) {
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


    private Settings settings = new Settings();
    private long lastMillis;
    private float currentMarketPrice = 0;
    @Override
    public void setup()
    {
        market.getOrderBook().registerDefaultVolumeDistributionFunction(this::getTargetAmount);
    }
    @Override
    public void update()
    {
        currentMarketPrice = market.getPrice();
        //info("Current market price: " + currentMarketPrice);
        updateVolume();
    }

    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {

    }



    public void updateVolume() {
        long currentMillis = System.currentTimeMillis();
        Tuple<@NotNull Integer,@NotNull  Integer> editableRange = market.getOrderBook().getEditableBackendPriceRange();
        double deltaT = Math.min((currentMillis - lastMillis) / 1000.0, 1.0);
        lastMillis = currentMillis;

        IMarketPluginInterface.OrderBookInterface orderBook = market.getOrderBook();
        float[] newVolume = new float[editableRange.getB() - editableRange.getA()+1];
        for(int i=editableRange.getA(); i<editableRange.getB()+1; i++)
        {
            /*float targetAmount = getTargetAmount(market.convertBackendPriceToRealPrice(i));
            float currentVal = orderBook.getVolume(i);
            float scale = settings.volumeAccumulationRate;
            if(Math.abs(currentVal) < Math.abs(targetAmount)*0.2f)
            {
                scale = settings.volumeFastAccumulationRate;
            }else if(Math.abs(currentVal) > Math.abs(targetAmount))
            {
                scale = settings.volumeDecumulationRate;
            }
            if(currentVal<targetAmount)
            {
                float deltaAmount = (targetAmount-currentVal) * (float) deltaT * scale;
                newVolume[i - editableRange.getA()] = Math.max(0,currentVal + deltaAmount);
            }else if(currentVal>targetAmount)
            {
                float deltaAmount = (targetAmount-currentVal) * (float) deltaT * scale;
                newVolume[i - editableRange.getA()] = Math.max(0,currentVal + deltaAmount);
            }*/

            float targetAmount = getTargetAmount(market.convertBackendPriceToRealPrice(i));
            float currentVal = orderBook.getVolume(i);
            if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
            {
                currentVal = 0;
                newVolume[i - editableRange.getA()] = 0;
                //virtualOrderVolumeDistribution.set(priceIndex, currentVal);
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
            newVolume[i - editableRange.getA()] = currentVal + deltaAmount;
        }
        orderBook.setVolume(editableRange.getA(), newVolume);
        /*Tuple<@NotNull Float,@NotNull  Float> editableRange = market.getOrderBook().getEditablePriceRange();
        double deltaT = Math.min((currentMillis - lastMillis) / 1000.0, 1.0);
        lastMillis = currentMillis;

        float updateCount = 100;
        float icrement = ((1+editableRange.getB() - editableRange.getA())/updateCount);
        IMarketPluginInterface.OrderBookInterface orderBook = market.getOrderBook();
        for(float i=editableRange.getA(); i<editableRange.getB(); i+=icrement)
        {
            float targetAmount = getTargetAmount(i);
            float currentVal = orderBook.getVolume(i, i+icrement);
            if((currentVal<targetAmount) || (currentVal>targetAmount)) {
                if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
                {
                    currentVal = 0;
                    orderBook.setVolume(i, i+icrement, 0);
                    //virtualOrderVolumeDistribution.set(priceIndex, currentVal);
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
                orderBook.addVolume(i, i+icrement, deltaAmount);
                //virtualOrderVolumeDistribution.add(priceIndex, deltaAmount);
            }
        }*/
    }
    private void setToDefaultDistribution()
    {
        Tuple<@NotNull Float,@NotNull  Float> editableRange = market.getOrderBook().getEditablePriceRange();

        float updateCount = 100;
        float icrement = ((1+editableRange.getB() - editableRange.getA())/updateCount);
        IMarketPluginInterface.OrderBookInterface orderBook = market.getOrderBook();
        for(float i=editableRange.getA(); i<editableRange.getB(); i+=icrement)
        {
            float targetAmount = getTargetAmount(i);
            orderBook.setVolume(i, i+icrement, targetAmount);
        }
    }

    float getTargetAmount(float price)
    {
        if(price < 0)
            return 0;
        // Calculate close price volume distribution
        float currentPriceFloat = currentMarketPrice;
        //float relativePrice = (currentPriceFloat - (float)price)/(currentPriceFloat+1);
        int relativeRawPrice = market.convertRealPriceToBackendPrice(currentPriceFloat - (float)price);

        final float constant1 = (float)(2.0/Math.E);

        float amount = 0;
        if(relativeRawPrice < 40 && relativeRawPrice > -40) {
            //amount += (float) Math.E * width * relativePrice * (float) Math.exp(-Math.abs(relativePrice * width));
            float sqrt = (float) Math.sqrt(Math.abs(relativeRawPrice)) * Math.signum(relativeRawPrice);
            amount += (float) constant1 * settings.nearMarketVolumeScale * sqrt * (float) Math.exp(-Math.abs(relativeRawPrice*relativeRawPrice*0.01));
        }


        if(relativeRawPrice > 0)
            amount += 0.1f;
        else if(relativeRawPrice < 0)
            amount -= 0.1f;
        if(price == 0)
            amount += 0.2f;


        if(relativeRawPrice > 0) {
            float lowPriceAccumulator = 1/(1+(float)market.convertRealPriceToBackendPrice(price));
            amount += lowPriceAccumulator * 5;
        }
        return amount*settings.volumeScale;
    }



    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        settings.encode(buf);
    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        settings.decode(buf);
    }

    @Override
    public boolean saveToFilesystem(CompoundTag tag) {
        CompoundTag settingsTag = new CompoundTag();
        settings.save(settingsTag);
        tag.put("settings", settingsTag);
        return true;
    }

    @Override
    public boolean loadFromFilesystem(CompoundTag tag) {
        if(tag.contains("settings"))
        {
            CompoundTag settingsTag = tag.getCompound("settings");
            settings.load(settingsTag);
        }
        return true;
    }
}
