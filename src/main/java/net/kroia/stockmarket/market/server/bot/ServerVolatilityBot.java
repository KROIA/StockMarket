package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.BotMoneyBank;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

import java.util.Random;
import java.util.UUID;

public class ServerVolatilityBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {
        public double volatility = 100;
        public double lastError = 0;
        public double integradedError = 0;
        public double randomWalkDifferencePercentage = 0;
        public int targetPrice = 0;
        public long targetItemBalance = 0;
        public long timerMillis = 10000;
        public long minTimerMillis = 1000;
        public long maxTimerMillis = 10000;
        public int imbalancePriceRange = 100;

        public double pid_p = 0.1;
        public double pid_d = 0.1;
        public double pid_i = 0.1;

        public Settings()
        {
            super();
            this.updateTimerIntervallMS = 100;
        }
        @Override
        public boolean save(CompoundTag tag) {
            boolean success = super.save(tag);
            tag.putDouble("volatility", volatility);
            tag.putDouble("lastError", lastError);
            tag.putDouble("integradedError", integradedError);
            tag.putDouble("randomWalkDifferencePercentage", randomWalkDifferencePercentage);
            tag.putInt("targetPrice", targetPrice);
            tag.putLong("targetItemBalance", targetItemBalance);
            tag.putLong("timerMillis", timerMillis);
            tag.putLong("minTimerMillis", minTimerMillis);
            tag.putLong("maxTimerMillis", maxTimerMillis);
            tag.putDouble("pid_p", pid_p);
            tag.putDouble("pid_d", pid_d);
            tag.putDouble("pid_i", pid_i);


            return success;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            boolean success = super.load(tag);
            if(!tag.contains("volatility") ||
               !tag.contains("lastError") ||
               !tag.contains("integradedError") ||
               !tag.contains("randomWalkDifferencePercentage") ||
               !tag.contains("targetPrice") ||
               !tag.contains("targetItemBalance") ||
               !tag.contains("timerMillis") ||
               !tag.contains("minTimerMillis") ||
               !tag.contains("maxTimerMillis")||
               !tag.contains("pid_p") ||
               !tag.contains("pid_d") ||
               !tag.contains("pid_i"))
                return false;
            volatility = tag.getDouble("volatility");
            lastError = tag.getDouble("lastError");
            integradedError = tag.getDouble("integradedError");
            randomWalkDifferencePercentage = tag.getDouble("randomWalkTargetPrice");
            targetPrice = tag.getInt("targetPrice");
            targetItemBalance = tag.getLong("targetItemBalance");
            timerMillis = tag.getLong("timerMillis");
            minTimerMillis = tag.getLong("minTimerMillis");
            maxTimerMillis = tag.getLong("maxTimerMillis");
            pid_p = tag.getDouble("pid_p");
            pid_d = tag.getDouble("pid_d");
            pid_i = tag.getDouble("pid_i");

            return success;
        }
    }
    private MeanRevertingRandomWalk randomWalk;
    private static Random random = new Random();
    //private double speed = 0;
    public long lastMillis = 0;
    public long lastTimerMillis = 0;

    Settings settings;

    public ServerVolatilityBot() {
        super();
        setSettings(new Settings());
        randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);

        lastMillis = System.currentTimeMillis();



    }

    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {

        /*int orderVolume = (int)((randomWalk.nextValue())*((Settings)this.settings).volatility);
        if(orderVolume == 0)
            return;
        marketTrade(orderVolume);*/
        BankUser user = ServerBankManager.getBotUser();
        Bank moneyBank = user.getMoneyBank();
        String itemID = parent.getItemID();
        Bank itemBank = user.getBank(itemID);
        UUID botUUID = getUUID();

        long currentItemBalance = itemBank.getTotalBalance();


        long currentMillis = System.currentTimeMillis();
        if(currentMillis - lastTimerMillis > settings.timerMillis)
        {
            lastTimerMillis = currentMillis;
            settings.timerMillis = settings.minTimerMillis + random.nextLong(settings.maxTimerMillis-settings.minTimerMillis);
            //targetPrice = 100 + random.nextInt(100)-50;
            double randomWalkValue = randomWalk.nextValue();
            double mapped = settings.volatility * randomWalkValue/100;
            settings.randomWalkDifferencePercentage = mapped;
           // stockDifference;


        }

        if(settings.targetItemBalance <= 1)
        {
            settings.targetPrice = 1;
        }
        else {
            int itemImbalance = (int) (((settings.targetItemBalance - currentItemBalance) * settings.imbalancePriceRange / (double) settings.targetItemBalance) / 2 + settings.imbalancePriceRange);
            settings.targetPrice = (int) ((settings.randomWalkDifferencePercentage + 1) * itemImbalance);
        }


        long deltaTMillis = currentMillis - lastMillis;
        lastMillis = currentMillis;
        double deltaT = Math.max(0.0001,Math.min(1.0,(double)deltaTMillis/1000.0));


        int currentPrice = getCurrentPrice();
        double error = settings.targetPrice - currentPrice;

        settings.integradedError += error*deltaT*settings.pid_i;
        settings.integradedError = Math.min(1, Math.max(-1, settings.integradedError));

        double proportionalError = error * settings.pid_p;
        double derivativeError = (error - settings.lastError) / deltaT * settings.pid_d;
        settings.lastError = error;
        //derivativeError = Math.min(100, Math.max(-5, derivativeError));

        double speed = proportionalError + derivativeError + settings.integradedError;
        int randomScale = Math.abs((int)speed)+1;
        int volume = (int)(speed)+random.nextInt(randomScale)*2-randomScale;

        marketTrade(volume);
       // StockMarketMod.LOGGER.info("VolatilityBot: targetPrice: "+settings.targetPrice+" speed: "+speed+" volume: "+volume+" error: "+error+ " P: "+proportionalError+" D: "+derivativeError+" I: "+settings.integradedError);

        clearOrders();
        currentPrice = getCurrentPrice();
        int priceIncerement = 1;
        double imbalanceFactor = 0;
        if(settings.targetItemBalance > 0)
        {
            imbalanceFactor = (settings.targetItemBalance - currentItemBalance)/(double)settings.targetItemBalance/4;
        }
        for(int i=1; i<=this.settings.maxOrderCount/2; i++)
        {
            int sellPrice = currentPrice + i*priceIncerement;
            int buyPrice = currentPrice - i*priceIncerement;


            int buyVolume = (int)(getAvailableVolume(buyPrice) * (1 + imbalanceFactor))+1;
            if(buyVolume > 0) {

                if(moneyBank.getBalance()>(buyVolume*2)*buyPrice) {
                    limitTrade(buyVolume, buyPrice);
                }
            }

            int sellVolume = (int)(getAvailableVolume(sellPrice) * (1-imbalanceFactor))+1;
            if(sellVolume < 0) {
                if(itemBank.getBalance() > -sellVolume) {
                    sellLimit(-sellVolume, sellPrice);
                }
            }
        }
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    @Override
    public int getVolumeDistribution(int x)
    {
        double fX = (double)Math.abs(x);
        double exp = Math.exp(-fX*1.f/this.settings.volumeSpread);
        double random = Math.random()+1;

        double volume = (super.settings.volumeScale*random) * (1 - exp) * exp;

        if(x < 0)
            return (int)-volume;
        return (int)volume;

        //return -x*;
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
    public double getIntegradedError() {
        return settings.integradedError;
    }






}
