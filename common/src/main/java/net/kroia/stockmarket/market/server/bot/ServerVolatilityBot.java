package net.kroia.stockmarket.market.server.bot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Random;

public class ServerVolatilityBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {
        public float volumeScale = 2f;

        public boolean enableTargetPrice = true;
        public float targetPriceSteeringFactor = 0.1f;


        public boolean enableVolumeTracking = true;
        public float volumeSteeringFactor = 1.0E-7f;


        public boolean enableRandomWalk = true;
        public float volatility = 0.2f; // 0-1 or higher

        public Settings()
        {
            super();
        }

        @Override
        public boolean save(CompoundTag tag) {
            boolean success = super.save(tag);

            tag.putFloat("volumeScale", volumeScale);
            tag.putBoolean("enableTargetPrice", enableTargetPrice);
            tag.putFloat("targetPriceSteeringFactor", targetPriceSteeringFactor);
            tag.putBoolean("enableVolumeTracking", enableVolumeTracking);
            tag.putFloat("volumeSteeringFactor", volumeSteeringFactor);
            tag.putBoolean("enableRandomWalk", enableRandomWalk);
            tag.putFloat("volatility", volatility);

            return success;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            boolean success = super.load(tag);

            if(tag.contains("volumeScale"))
                volumeScale = tag.getFloat("volumeScale");
            if(tag.contains("enableTargetPrice"))
                enableTargetPrice = tag.getBoolean("enableTargetPrice");
            if(tag.contains("targetPriceSteeringFactor"))
                targetPriceSteeringFactor = tag.getFloat("targetPriceSteeringFactor");
            if(tag.contains("enableVolumeTracking"))
                enableVolumeTracking = tag.getBoolean("enableVolumeTracking");
            if(tag.contains("volumeSteeringFactor"))
                volumeSteeringFactor = tag.getFloat("volumeSteeringFactor");
            if(tag.contains("enableRandomWalk"))
                enableRandomWalk = tag.getBoolean("enableRandomWalk");
            if(tag.contains("volatility"))
                volatility = tag.getFloat("volatility");


            return success;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            super.encode(buf);
            buf.writeFloat(volumeScale);
            buf.writeBoolean(enableTargetPrice);
            buf.writeFloat(targetPriceSteeringFactor);
            buf.writeBoolean(enableVolumeTracking);
            buf.writeFloat(volumeSteeringFactor);
            buf.writeBoolean(enableRandomWalk);
            buf.writeFloat(volatility);
        }
        @Override
        public void decode(FriendlyByteBuf buf) {
            super.decode(buf);
            this.volumeScale = buf.readFloat();
            this.enableTargetPrice = buf.readBoolean();
            this.targetPriceSteeringFactor = buf.readFloat();
            this.enableVolumeTracking = buf.readBoolean();
            this.volumeSteeringFactor = buf.readFloat();
            this.enableRandomWalk = buf.readBoolean();
            this.volatility = buf.readFloat();
        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = super.toJson().getAsJsonObject();
            jsonObject.addProperty("volumeScale", volumeScale);
            jsonObject.addProperty("enableTargetPrice", enableTargetPrice);
            jsonObject.addProperty("targetPriceSteeringFactor", targetPriceSteeringFactor);
            jsonObject.addProperty("enableVolumeTracking", enableVolumeTracking);
            jsonObject.addProperty("volumeSteeringFactor", volumeSteeringFactor);
            jsonObject.addProperty("enableRandomWalk", enableRandomWalk);
            jsonObject.addProperty("volatility", volatility);
            return jsonObject;
        }

        public boolean fromJson(JsonElement json) {
            if(!json.isJsonObject())
            {
                return false;
            }
            JsonObject jsonObject = json.getAsJsonObject();
            boolean success = super.fromJson(jsonObject);
            if(!success)
                return false;

            JsonElement element = jsonObject.get("volumeScale");
            
            if(element != null && element.isJsonPrimitive()) {
                this.volumeScale = element.getAsFloat();
                if(this.volumeScale < 0)
                    this.volumeScale = 0;
            }

            element = jsonObject.get("enableTargetPrice");
            if(element != null && element.isJsonPrimitive())
                this.enableTargetPrice = element.getAsBoolean();

            element = jsonObject.get("targetPriceSteeringFactor");
            if(element != null && element.isJsonPrimitive()) {
                this.targetPriceSteeringFactor = element.getAsFloat();
                if(this.targetPriceSteeringFactor < 0)
                    this.targetPriceSteeringFactor = 0;
            }

            element = jsonObject.get("enableVolumeTracking");
            if(element != null && element.isJsonPrimitive())
                this.enableVolumeTracking = element.getAsBoolean();

            element = jsonObject.get("volumeSteeringFactor");
            if(element != null && element.isJsonPrimitive()) {
                this.volumeSteeringFactor = element.getAsFloat();
                if(this.volumeSteeringFactor < 0)
                    this.volumeSteeringFactor = 0;
            }

            element = jsonObject.get("enableRandomWalk");
            if(element != null && element.isJsonPrimitive())
                this.enableRandomWalk = element.getAsBoolean();

            element = jsonObject.get("volatility");
            if(element != null && element.isJsonPrimitive()) {
                this.volatility = element.getAsFloat();
                if(this.volatility < 0)
                    this.volatility = 0;
            }

            return true;

        }


        @Override
        public void copyFrom(ServerTradingBot.Settings other)
        {
            if(other != null && other instanceof ServerVolatilityBot.Settings)
            {
                super.copyFrom(other);
                ServerVolatilityBot.Settings st = (ServerVolatilityBot.Settings)other;
                this.volumeScale = st.volumeScale;
                this.enableRandomWalk = st.enableRandomWalk;
                this.enableTargetPrice = st.enableTargetPrice;
                this.enableVolumeTracking = st.enableVolumeTracking;
                this.targetPriceSteeringFactor = st.targetPriceSteeringFactor;
                this.volumeSteeringFactor = st.volumeSteeringFactor;
                this.volatility = st.volatility;
            }
        }
    }

    private final NormalizedRandomPriceGenerator priceGenerator;
    private MeanRevertingRandomWalk randomWalk;
    private static Random random = new Random();
    TimerMillis randomWalkTimer = new TimerMillis(false);
    private final PID pid = new PID(0.1f, 0.1f, 0, 1);

    private float targetPriceF;
    Settings settings;
    public ServerVolatilityBot(ServerMarket market) {
        super(market);
        setSettings(new Settings());
        priceGenerator = new NormalizedRandomPriceGenerator(5);
        randomWalkTimer.start(random.nextInt(10000));
        randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);
    }

    public int getTargetPrice() {
        return Math.round(targetPriceF);
    }
    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {
        long currentItemBalance = getItemImbalance();


        float marketOrderAmountF = 0;
        targetPriceF = (float)settings.defaultPrice;

        float randomWalkDeltaTargetPriceF = 0;
        if(settings.enableVolumeTracking)
        {
            float x = (float)currentItemBalance * settings.volumeSteeringFactor;
            float scale;
            if(currentItemBalance < 0)
            {
                scale = -x+1;
            }
            else
            {
                scale = (float)Math.exp(-x);
            }
            targetPriceF = Math.max((targetPriceF * scale), 0);
        }

        if(settings.enableRandomWalk)
        {
            if(randomWalkTimer.check())
            {
                randomWalkTimer.start(100+random.nextInt(900));
                priceGenerator.getNextValue();
            }
            double randomWalkValue = (priceGenerator.getCurrentValue() * (double)settings.volatility * (double)settings.defaultPrice);
            randomWalkDeltaTargetPriceF = (int)Math.round(randomWalkValue);
            targetPriceF += randomWalkDeltaTargetPriceF;
            marketOrderAmountF +=  (float)randomWalk.nextValue() * settings.volatility * settings.volumeScale;
        }

        if(targetPriceF < 0)
            targetPriceF = 0;

        if(settings.enableTargetPrice)
        {
            float currentPriceF = getCurrentPrice();
            float output = pid.update(targetPriceF - currentPriceF);
            float normalized = (Math.min(Math.max(-10, output*5),10) * settings.volumeScale);
            marketOrderAmountF += normalized;
        }

        if(marketOrderAmountF > 0)
        {
            long amount = getOrderBookVolume(getCurrentPrice()+2);
            if(-amount < marketOrderAmountF)
                marketOrderAmountF = -amount;
        }
        else if(marketOrderAmountF < 0)
        {
            long amount = getOrderBookVolume(getCurrentPrice()-2);
            if(amount < -marketOrderAmountF)
                marketOrderAmountF = -amount;
        }
        long marketOrderAmount = Math.round(marketOrderAmountF);
        marketTrade(marketOrderAmount);

    }


    @Override
    public void setSettings(ServerTradingBot.Settings settings) {
        if(settings instanceof Settings) {
            super.setSettings(settings);
            this.settings = (Settings)settings;
            pid.setCurrentMillis();
            pid.setKP(this.settings.targetPriceSteeringFactor);
        }
        else
            throw new IllegalArgumentException("Settings must be of type ServerVolatilityBot.Settings");
    }

    private double map(double value, double inMin, double inMax, double outMin, double outMax)
    {
        return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
    }



    @Override
    public boolean save(CompoundTag tag) {
        boolean success = super.save(tag);
        CompoundTag priceGeneratorTag = new CompoundTag();
        priceGenerator.save(priceGeneratorTag);
        tag.put("priceGenerator", priceGeneratorTag);
        CompoundTag randomWalkTag = new CompoundTag();
        randomWalk.save(randomWalkTag);
        tag.put("randomWalk", randomWalkTag);
        return success;
    }
    @Override
    public boolean load(CompoundTag tag) {
        boolean success = super.load(tag);
        if(tag.contains("priceGenerator"))
        {
            CompoundTag priceGeneratorTag = tag.getCompound("priceGenerator");
            priceGenerator.load(priceGeneratorTag);
        }
        if(tag.contains("randomWalk"))
        {
            CompoundTag randomWalkTag = tag.getCompound("randomWalk");
            randomWalk.load(randomWalkTag);
        }

        return success;
    }
}
