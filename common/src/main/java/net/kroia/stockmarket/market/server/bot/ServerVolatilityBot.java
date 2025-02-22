package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.banking.BankUser;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

import java.io.FileWriter;
import java.util.Random;

public class ServerVolatilityBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {
        public enum Type
        {
            //ServerTradingBot settings
            ENABLED,
            MAX_ORDER_COUNT,
            VOLUME_SCALE,
            VOLUME_SPREAD,
            VOLUME_RANDOMNESS,
            UPDATE_INTERVAL,



            //ServerVolatilityBot settings
            VOLATILITY,
            ORDER_RANDOMNESS,
            INTEGRATED_ERROR,
            TARGET_ITEM_BALANCE,
            TIMER_VOLATILITY_MILLIS,
            MIN_VOLATILITY_TIMER_MILLIS,
            MAX_VOLATILITY_TIMER_MILLIS,
            IMBALANCE_PRICE_RANGE,
            IMBALANCE_PRICE_CHANGE_FACTOR,
            IMBALANCE_PRICE_CHANGE_QUAD_FACTOR,
            PID_P,
            PID_D,
            PID_I,
            PID_I_BOUNDS
        }
        public double volatility = 100;
        public double orderRandomness = 1;
        public double lastError = 0;
        public double integratedError = 0;
        public double randomWalkDifferencePercentage = 0;
        public int targetPrice = 0;
        public long targetItemBalance = 0;
        public long timerMillis = 10000;
        public long minTimerMillis = 10000;
        public long maxTimerMillis = 120000;
        public int imbalancePriceRange = 100;
        public double imbalancePriceChangeFactor = 0.1;
        public double imbalancePriceChangeQuadFactor = 10;

        public double pid_p = 0.1;
        public double pid_d = 0.1;
        public double pid_i = 0.0001;
        public double pid_iBound = 10;

        public Settings()
        {
            super();
            //this.updateTimerIntervallMS = 100;
        }
        public Settings(double volatility,
                        long targetItemBalance,
                        long minTimerMillis,
                        long maxTimerMillis,
                        int imbalancePriceRange,
                        double imbalancePriceChangeFactor,
                        double imbalancePriceChangeQuadFactor,
                        double pid_p,
                        double pid_d,
                        double pid_i,
                        double pid_iBound)
        {
            this();
            this.volatility = volatility;
            this.targetItemBalance = targetItemBalance;
            this.minTimerMillis = minTimerMillis;
            this.maxTimerMillis = maxTimerMillis;
            this.imbalancePriceRange = imbalancePriceRange;
            this.imbalancePriceChangeFactor = imbalancePriceChangeFactor;
            this.imbalancePriceChangeQuadFactor = imbalancePriceChangeQuadFactor;
            this.pid_p = pid_p;
            this.pid_d = pid_d;
            this.pid_i = pid_i;
            this.pid_iBound = pid_iBound;
        }
        public Settings(int price, double rarity, double volatility, long udateTimerIntervallMS)
        {
            this();
            this.pid_p = 0.1;
            this.pid_d = -0.01;
            this.pid_i = 0.001;
            this.pid_iBound = 1;
            setFromData(price, rarity, volatility, udateTimerIntervallMS);
        }
        @Override
        public boolean save(CompoundTag tag) {
            boolean success = super.save(tag);
            tag.putDouble("volatility", volatility);
            tag.putDouble("orderRandomness", orderRandomness);
            tag.putDouble("lastError", lastError);
            tag.putDouble("integratedError", integratedError);
            tag.putDouble("randomWalkDifferencePercentage", randomWalkDifferencePercentage);
            tag.putInt("targetPrice", targetPrice);
            tag.putLong("targetItemBalance", targetItemBalance);
            tag.putLong("timerMillis", timerMillis);
            tag.putLong("minTimerMillis", minTimerMillis);
            tag.putLong("maxTimerMillis", maxTimerMillis);
            tag.putInt("imbalancePriceRange", imbalancePriceRange);
            tag.putDouble("imbalancePriceChangeFactor", imbalancePriceChangeFactor);
            tag.putDouble("imbalancePriceChangeQuadFactor", imbalancePriceChangeQuadFactor);
            tag.putDouble("pid_p", pid_p);
            tag.putDouble("pid_d", pid_d);
            tag.putDouble("pid_i", pid_i);
            tag.putDouble("pid_iBound", pid_iBound);





            return success;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            boolean success = super.load(tag);
            if(!tag.contains("volatility") ||
               !tag.contains("orderRandomness") ||
               !tag.contains("lastError") ||
               !tag.contains("integratedError") ||
               !tag.contains("randomWalkDifferencePercentage") ||
               !tag.contains("targetPrice") ||
               !tag.contains("targetItemBalance") ||
               !tag.contains("timerMillis") ||
               !tag.contains("minTimerMillis") ||
               !tag.contains("maxTimerMillis")||
               !tag.contains("imbalancePriceRange") ||
               !tag.contains("imbalancePriceChangeFactor") ||
               !tag.contains("imbalancePriceChangeQuadFactor") ||
               !tag.contains("pid_p") ||
               !tag.contains("pid_d") ||
               !tag.contains("pid_i") ||
               !tag.contains("pid_iBound"))
                return false;
            volatility = tag.getDouble("volatility");
            orderRandomness = tag.getDouble("orderRandomness");
            lastError = tag.getDouble("lastError");
            integratedError = tag.getDouble("integratedError");
            randomWalkDifferencePercentage = tag.getDouble("randomWalkTargetPrice");
            targetPrice = tag.getInt("targetPrice");
            targetItemBalance = tag.getLong("targetItemBalance");
            timerMillis = tag.getLong("timerMillis");
            minTimerMillis = tag.getLong("minTimerMillis");
            maxTimerMillis = tag.getLong("maxTimerMillis");
            imbalancePriceRange = tag.getInt("imbalancePriceRange");
            imbalancePriceChangeFactor = tag.getDouble("imbalancePriceChangeFactor");
            imbalancePriceChangeQuadFactor = tag.getDouble("imbalancePriceChangeQuadFactor");
            pid_p = tag.getDouble("pid_p");
            pid_d = tag.getDouble("pid_d");
            pid_i = tag.getDouble("pid_i");
            pid_iBound = tag.getDouble("pid_iBound");

            return success;
        }

        public void load(Settings other)
        {
            CompoundTag tag = new CompoundTag();
            other.save(tag);
            load(tag);
        }
        public void setFromData(int price, double rarity, double volatility, long udateTimerIntervallMS)
        {
            //this.targetItemBalance = (long)(((1-rarity) * (1-rarity)) * 100000)+5000;
            this.volatility = volatility*100;
            this.imbalancePriceRange = price * 2;
            this.updateTimerIntervallMS = udateTimerIntervallMS;

            if(volatility > 0.25)
            {
                this.imbalancePriceChangeQuadFactor = (volatility-0.25) * 8;
            }else {
                this.imbalancePriceChangeQuadFactor = 0;
            }
            this.imbalancePriceChangeFactor = volatility*0.1;
            this.volumeRandomness = volatility*2;
            this.volumeScale = (1-rarity) * 100;
            this.orderRandomness = volatility * (1-rarity) * 5+1;
        }
    }
    private MeanRevertingRandomWalk randomWalk1;
    private MeanRevertingRandomWalk randomWalk2;
    private static Random random = new Random();
    //private double speed = 0;
    public long lastMillis = 0;
    public long lastTimerMillis = 0;
    public long timerCounter = 0;

    Settings settings;

    public ServerVolatilityBot() {
        super();
        setSettings(new Settings());
        randomWalk1 = new MeanRevertingRandomWalk(0.1, 0.05);
        randomWalk2 = new MeanRevertingRandomWalk(0.1, 0.05);

        lastMillis = System.currentTimeMillis();


        //debugPlotRandomWalk();
    }

    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {
        //BankUser user = ServerMarket.getBotUser();
        //Bank moneyBank = user.getMoneyBank();
        ItemID itemID = parent.getItemID();
        //Bank itemBank = user.getBank(itemID);
        //if(itemBank == null)
        //    return;

        // Create Limit orders
        //clearOrders();
        //createLimitOrders(itemBank, moneyBank);

        //long currentItemBalance = itemBank.getTotalBalance();
        long currentItemBalance = getMatchingEngine().getRealVolumeImbalance();


        long currentMillis = System.currentTimeMillis();
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

        /*if(settings.targetItemBalance <= 1)
        {
            settings.targetPrice = 1;
        }
        else
        {*/
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
        //}


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

        //if(volume < 0 && itemBank.getBalance()/2 < -volume)
        //    volume = (int)-itemBank.getBalance()/2;
        if(currentPrice != 0 || volume > 0)
            marketTrade(volume);
    }



    private void createLimitOrders(Bank itemBank, Bank moneyBank)
    {
        int currentPrice = getCurrentPrice();
        long currentItemBalance = itemBank.getTotalBalance();
        int priceIncerement = 1;
        double imbalanceFactor = 0;
        if(settings.targetItemBalance > 0)
        {
            imbalanceFactor = Math.tanh((settings.targetItemBalance - currentItemBalance)/(double)settings.targetItemBalance/4)*0.5;
        }
        int startBuyPrice = currentPrice;
        int startSellPrice = currentPrice+1;
        if(currentPrice == 0)
            startSellPrice = currentPrice+1;

        for(int i=0; i<this.settings.maxOrderCount/2; i++)
        {
            int sellPrice = startSellPrice + i*priceIncerement;
            int buyPrice = startBuyPrice - i*priceIncerement;
            int buyVolume = (int)(getAvailableVolume(buyPrice) * (1 + imbalanceFactor))+1;
            if(buyVolume > 0 && buyPrice >= 0) {

                if(moneyBank.getBalance()>(buyVolume*2)*buyPrice) {
                    limitTrade(buyVolume, buyPrice);
                }
            }

            if(itemBank.getBalance() > 10) {
                int sellVolume = (int) (getAvailableVolume(sellPrice) * (1 - imbalanceFactor)) -1;
                if (sellVolume < 0 && sellPrice > 0) {
                    if (itemBank.getBalance()/2 < -sellVolume)
                        sellVolume = (int) -itemBank.getBalance()/2;

                    sellLimit(-sellVolume, sellPrice);

                }
            }
        }
    }


    @Override
    public void setSettings(ServerTradingBot.Settings settings) {
        if(settings instanceof Settings) {
            super.setSettings(settings);
            this.settings = (Settings)settings;
        }
        else
            throw new IllegalArgumentException("Settings must be of type ServerVolatilityBot.Settings");
    }

    private double map(double value, double inMin, double inMax, double outMin, double outMax)
    {
        return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin;
    }

    public void setVolatility(double volatility) {
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
