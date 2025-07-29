package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;

import java.io.FileWriter;
import java.util.Random;

public class ServerVolatilityBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {

        public float volumeScale = 2f;

        public boolean enableTargetPrice = true;
        public float targetPriceSteeringFactor = 0.1f;

        public int targetPrice = 0; //Just for visualisation on the bot settings menu


        public boolean enableVolumeTracking = true;
        public float volumeSteeringFactor = 1.0E-7f;


        public boolean enableRandomWalk = true;
        public float volatility = 0.2f; // 0-1 or higher

        public Settings()
        {
            super();

        }

        public Settings(int price, float rarity, float volatility, long udateTimerIntervallMS, boolean enableTargetPrice, boolean enableVolumeTracking, boolean enableRandomWalk)
        {
            this();
            setFromData(price, rarity, volatility, udateTimerIntervallMS, enableTargetPrice, enableVolumeTracking, enableRandomWalk);
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
            tag.putFloat("targetPrice", targetPrice);



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
            if(tag.contains("targetPrice"))
                targetPrice = tag.getInt("targetPrice");


            return success;
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
                this.targetPrice = st.targetPrice;

            }
        }
        public void setFromData(int price, float rarity, float volatility, long udateTimerIntervallMS,
                                boolean enableTargetPrice, boolean enableVolumeTracking, boolean enableRandomWalk)
        {
            //this.targetItemBalance = (long)(((1-rarity) * (1-rarity)) * 100000)+5000;
            this.defaultPrice = price;
            this.targetPrice = price;
            this.updateTimerIntervallMS = udateTimerIntervallMS;

            this.enableTargetPrice = enableTargetPrice;
            this.targetPriceSteeringFactor = Math.max(rarity*0.1f,0.00001f);

            this.enableVolumeTracking = enableVolumeTracking;
            this.volumeSteeringFactor = Math.max(0.0000001f/(1.2f-rarity),0.0000001f);

            this.enableRandomWalk = enableRandomWalk;
            this.volatility = Math.abs(volatility);

            this.orderBookVolumeScale = 100f/(0.01f+Math.abs(rarity));
            this.volumeScale = this.orderBookVolumeScale * this.volatility;
        }
    }
    private MeanRevertingRandomWalk randomWalk1;
    private MeanRevertingRandomWalk randomWalk2;
    private MeanRevertingRandomWalk randomWalk3;
    private static Random random = new Random();
    //private double speed = 0;
    //public long lastMillis = 0;
    private long lastTimerMillis = 0;
    private long targetTimerMillis = 1000;
    private long timerCounter = 0;
    private final PID pid = new PID(0.1f, 0.01f, 0.1f, 1);

    Settings settings;
    public ServerVolatilityBot(ServerMarket market) {
        super(market);
        setSettings(new Settings());
        randomWalk1 = new MeanRevertingRandomWalk(0.1, 0.05);
        randomWalk2 = new MeanRevertingRandomWalk(0.1, 0.05);
        randomWalk3 = new MeanRevertingRandomWalk(0.1, 0.05);
    }

    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {
        long currentItemBalance = getItemImbalance();


        long marketOrderAmount = 0;
        int targetPrice = settings.defaultPrice;
        float volumeScale = settings.volumeScale;
        if(settings.enableVolumeTracking)
        {

            // plot((((-x+abs(-x))/2)^2+(-x+abs(-x))/2+exp((-x-abs(-x))/2)))
            float x = (float)currentItemBalance * settings.volumeSteeringFactor;
            float scale;
            if(currentItemBalance < 0)
            {
                scale = -x+1;
                //scale = (x*x-x)+1;
            }
            else
            {
                scale = (float)Math.exp(-x);
            }
            targetPrice = Math.max((int)(targetPrice * scale), 0);
        }

        if(settings.enableRandomWalk)
        {
            long currentMillis = System.currentTimeMillis();
            if(currentMillis - lastTimerMillis > targetTimerMillis)
            {
                lastTimerMillis = currentMillis;
                targetTimerMillis = 1000 + random.nextLong(100000);
                randomWalk1.nextValue();
                timerCounter++;
                if(timerCounter >= 10)
                {
                    timerCounter = 0;
                    randomWalk2.nextValue();
                }
            }
            targetPrice += (int)((randomWalk1.getCurrentValue() + randomWalk2.getCurrentValue())*settings.volatility*settings.defaultPrice);

            marketOrderAmount += (int)(randomWalk3.nextValue()*volumeScale);
        }

        if(settings.enableTargetPrice)
        {
            if(targetPrice < 0)
                targetPrice = 0;

            int currentPrice = getCurrentPrice();
            float output = pid.update(targetPrice - currentPrice);
            int normalized = (int)(Math.min(Math.max(-10, output),10)*volumeScale);
            marketOrderAmount += normalized;
        }
        settings.targetPrice = targetPrice;

        if(marketOrderAmount > 0)
        {
            long amount = getOrderBookVolume(getCurrentPrice()+2);
            if(-amount < marketOrderAmount)
                marketOrderAmount = -amount;
        }
        else if(marketOrderAmount < 0)
        {
            long amount = getOrderBookVolume(getCurrentPrice()-2);
            if(amount < -marketOrderAmount)
                marketOrderAmount = -amount;
        }
        marketTrade(marketOrderAmount);

    }



    private void createLimitOrders(Bank itemBank, Bank moneyBank)
    {

    }


    @Override
    public void setSettings(ServerTradingBot.Settings settings) {
        if(settings instanceof Settings) {
            super.setSettings(settings);
            this.settings = (Settings)settings;
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
        CompoundTag meanRevertingRandomWalkTag = new CompoundTag();
        randomWalk1.save(meanRevertingRandomWalkTag);
        tag.put("randomWalk1", meanRevertingRandomWalkTag);
        CompoundTag meanRevertingRandomWalk2Tag = new CompoundTag();
        randomWalk2.save(meanRevertingRandomWalk2Tag);
        tag.put("randomWalk2", meanRevertingRandomWalk2Tag);


        return success;
    }
    @Override
    public boolean load(CompoundTag tag) {
        boolean success = super.load(tag);

        if(tag.contains("randomWalk1"))
        {
            CompoundTag meanRevertingRandomWalkTag = tag.getCompound("randomWalk1");
            randomWalk1.load(meanRevertingRandomWalkTag);
        }
        if(tag.contains("randomWalk2"))
        {
            CompoundTag meanRevertingRandomWalk2Tag = tag.getCompound("randomWalk2");
            randomWalk2.load(meanRevertingRandomWalk2Tag);
        }

        return success;
    }


    private void debugPlotRandomWalk()
    {
        try (FileWriter writer = new FileWriter("randomWalk.csv")) {
            writer.write("Index,randomWalk1,randomWalk2\n"); // CSV header
            for(int i=0; i<10000; i++)
            {
                writer.write(i + ";" + randomWalk1.nextValue() + ";"+ randomWalk2.getCurrentValue() + "\n");
                if(i%10 == 0)
                    randomWalk2.nextValue();
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
