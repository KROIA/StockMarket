package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.banking.bank.Bank;
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
        public float volumeSteeringFactor = 0.1f;


        public boolean enableRandomWalk = true;
        public float volatility; // 0-1 or higher

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
    public ServerVolatilityBot() {
        super();
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
        long currentItemBalance = getMatchingEngine().getRealVolumeImbalance();


        int marketOrderAmount = 0;
        int targetPrice = settings.defaultPrice;
        float volumeScale = settings.volumeScale;
        if(settings.enableVolumeTracking)
        {

            // plot((((-x+abs(-x))/2)^2+(-x+abs(-x))/2+exp((-x-abs(-x))/2)))
            float x = (float)currentItemBalance * settings.volumeSteeringFactor;
            float scale;
            if(currentItemBalance < 0)
            {
                scale = (x*x-x)+1;
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
            int amount = getVolume(getCurrentPrice()+2);
            if(-amount < marketOrderAmount)
                marketOrderAmount = -amount;
        }
        else if(marketOrderAmount < 0)
        {
            int amount = getVolume(getCurrentPrice()-2);
            if(amount < -marketOrderAmount)
                marketOrderAmount = -amount;
        }
        marketTrade(marketOrderAmount);




        /*long currentMillis = System.currentTimeMillis();
        if(currentMillis - lastTimerMillis > settings.timerMillis)
        {
            lastTimerMillis = currentMillis;
            settings.timerMillis = settings.minTimerMillis + random.nextLong(settings.maxTimerMillis-settings.minTimerMillis);
            double randomWalkValue = randomWalk1.nextValue() + randomWalk2.getCurrentValue() * 5;
            settings.randomWalkDifferencePercentage = settings.volatility * randomWalkValue/100;
            timerCounter++;
            if(timerCounter >= 10)
            {
                timerCounter = 0;
                randomWalk2.nextValue();
            }
        }

        int averageTargetPrice =  (settings.imbalancePriceRange/2);

        double normalizedDifference = (settings.targetItemBalance - currentItemBalance);///(double)settings.targetItemBalance;
        // Linear part
        double imbalancePriceOffset = (normalizedDifference * settings.imbalancePriceChangeFactor);
        if(normalizedDifference > 0)
        {
            // Quadratic part only active for positive differences
            double quadraticPart = normalizedDifference*normalizedDifference  * settings.imbalancePriceChangeQuadFactor;
            imbalancePriceOffset += quadraticPart;
        }

        int targetPrice = averageTargetPrice + (int)(imbalancePriceOffset * settings.imbalancePriceRange);
        settings.targetPrice = (int) ((settings.randomWalkDifferencePercentage + 1) * targetPrice);

        if(settings.targetPrice < 0)
            settings.targetPrice = 0;



        long deltaTMillis = currentMillis - lastMillis;
        lastMillis = currentMillis;
        double deltaT = Math.max(0.0001,Math.min(1.0,(double)deltaTMillis/1000.0));


        int currentPrice = getCurrentPrice();
        double error = settings.targetPrice - currentPrice;

        settings.integratedError += error*deltaT*settings.pid_i;
        settings.integratedError = Math.min(settings.pid_iBound, Math.max(-settings.pid_iBound, settings.integratedError));

        double proportionalError = error * settings.pid_p;
        double derivativeError = (error - settings.lastError) / deltaT * settings.pid_d;
        settings.lastError = error;
        double speed = proportionalError + derivativeError + settings.integratedError;

        int randomScale = Math.abs((int)speed)+1;
        int randomVolume = random.nextInt((int)(-settings.orderRandomness),(int)(settings.orderRandomness)+1);
        int volume = (int)(Math.round(speed)) + randomVolume;

        if(currentPrice != 0 || volume > 0)
            marketTrade(volume);*/
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

    /*public void setVolatility(double volatility) {
        settings.volatility = volatility;
    }
    public double getVolatility() {
        return settings.volatility;
    }
    public void setOrderRandomness(double orderRandomness) {
        settings.orderRandomness = orderRandomness;
    }
    public double getOrderRandomness() {
        return settings.orderRandomness;
    }
    public int getTargetPrice() {
        return settings.targetPrice;
    }
    public double getLastError() {
        return settings.lastError;
    }
    public double getRandomWalkDifferencePercentage() {
        return settings.randomWalkDifferencePercentage;
    }
    public void setTargetItemBalance(long targetItemBalance) {
        settings.targetItemBalance = targetItemBalance;
    }
    public long getTargetItemBalance() {
        return settings.targetItemBalance;
    }
    public void setTimerMillis(long timerMillis) {
        settings.timerMillis = timerMillis;
    }
    public long getTimerMillis() {
        return settings.timerMillis;
    }
    public void setMinTimerMillis(long minTimerMillis) {
        settings.minTimerMillis = minTimerMillis;
    }
    public long getMinTimerMillis() {
        return settings.minTimerMillis;
    }
    public void setMaxTimerMillis(long maxTimerMillis) {
        settings.maxTimerMillis = maxTimerMillis;
    }
    public long getMaxTimerMillis() {
        return settings.maxTimerMillis;
    }
    public void setImbalancePriceRange(int imbalancePriceRange) {
        settings.imbalancePriceRange = imbalancePriceRange;
    }
    public int getImbalancePriceRange() {
        return settings.imbalancePriceRange;
    }
    public void setImbalancePriceChangeFactor(double imbalancePriceChangeFactor) {
        settings.imbalancePriceChangeFactor = imbalancePriceChangeFactor;
    }
    public double getImbalancePriceChangeFactor() {
        return settings.imbalancePriceChangeFactor;
    }
    public void setImbalancePriceChangeQuadFactor(double imbalancePriceChangeQuadFactor) {
        settings.imbalancePriceChangeQuadFactor = imbalancePriceChangeQuadFactor;
    }
    public double getImbalancePriceChangeQuadFactor() {
        return settings.imbalancePriceChangeQuadFactor;
    }
    public void setPidP(double pid_p) {
        settings.pid_p = pid_p;
    }
    public double getPidP() {
        return settings.pid_p;
    }
    public void setPidD(double pid_d) {
        settings.pid_d = pid_d;
    }
    public double getPidD() {
        return settings.pid_d;
    }
    public void setPidI(double pid_i) {
        settings.pid_i = pid_i;
    }
    public double getPidI() {
        return settings.pid_i;
    }
    public void setPidIBound(double pid_iBound) {
        settings.pid_iBound = pid_iBound;
    }
    public double getPidIBound() {
        return settings.pid_iBound;
    }
    public double getintegratedError() {
        return settings.integratedError;
    }
    public void setintegratedError(double integratedError) {
        settings.integratedError = integratedError;
    }
    */

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
