package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModSettings;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;

public class ServerTradingBotFactory {

    public static class DefaultBotSettings
    {
        private final ServerVolatilityBot.Settings settings;

        public DefaultBotSettings(int price, float rarity, float volatility, long udateTimerIntervallMS)
        {
            this(new ServerVolatilityBot.Settings(price, rarity, volatility, udateTimerIntervallMS, true, true, true));
        }
        public DefaultBotSettings(ServerVolatilityBot.Settings settings)
        {
            this.settings = new ServerVolatilityBot.Settings();
            //maxOrderCount = settings.maxOrderCount;
            //volumeScale = settings.volumeScale;
            //volumeSpread = settings.volumeSpread;
            //volumeRandomness = settings.volumeRandomness;
            this.settings.copyFrom(settings);
            //orderRandomness = settings.orderRandomness;
            //targetItemBalance = settings.targetItemBalance;
            //minTimerMillis = settings.minTimerMillis;
            //maxTimerMillis = settings.maxTimerMillis;
            //imbalancePriceRange = settings.imbalancePriceRange;
            //imbalancePriceChangeFactor = settings.imbalancePriceChangeFactor;
            //imbalancePriceChangeQuadFactor = settings.imbalancePriceChangeQuadFactor;
            //pid_p = settings.pid_p;
            //pid_d = settings.pid_d;
            //pid_i = settings.pid_i;
            //pid_iBound = settings.pid_iBound;
        }
        public void loadDefaultSettings(ServerVolatilityBot.Settings settings)
        {
            //settings.maxOrderCount = maxOrderCount;
            //settings.volumeScale = volumeScale;
            //settings.volumeSpread = volumeSpread;
            //settings.volumeRandomness = volumeRandomness;
            //settings.updateTimerIntervallMS = this.settings.updateTimerIntervallMS;
            //settings.volatility = this.settings.volatility;
            //settings.defaultPrice = this.settings.defaultPrice;

            settings.copyFrom(this.settings);
            //settings.orderRandomness = orderRandomness;
            //settings.targetItemBalance = targetItemBalance;
            //settings.minTimerMillis = minTimerMillis;
            //settings.maxTimerMillis = maxTimerMillis;
            //settings.imbalancePriceRange = imbalancePriceRange;
            //settings.imbalancePriceChangeFactor = imbalancePriceChangeFactor;
            //settings.imbalancePriceChangeQuadFactor = imbalancePriceChangeQuadFactor;
            //settings.pid_p = pid_p;
            //settings.pid_d = pid_d;
            //settings.pid_i = pid_i;
            //settings.pid_iBound = pid_iBound;
        }
        public ServerVolatilityBot.Settings getSettings()
        {
            return settings;
        }
        /*public DefaultBotSettings setMaxOrderCount(int maxOrderCount)
        {
            this.maxOrderCount = maxOrderCount;
            return this;
        }
        public DefaultBotSettings setVolumeScale(double volumeScale)
        {
            this.volumeScale = volumeScale;
            return this;
        }
        public DefaultBotSettings setVolumeSpread(double volumeSpread)
        {
            this.volumeSpread = volumeSpread;
            return this;
        }
        public DefaultBotSettings setVolumeRandomness(double volumeRandomness)
        {
            this.volumeRandomness = volumeRandomness;
            return this;
        }*/
        public DefaultBotSettings setUpdateTimerIntervallMS(long updateTimerIntervallMS)
        {
            this.settings.updateTimerIntervallMS = updateTimerIntervallMS;
            return this;
        }
        public DefaultBotSettings setVolatility(float volatility)
        {
            this.settings.volatility = volatility;
            return this;
        }
        public DefaultBotSettings setDefaultPrice(int defaultPrice)
        {
            this.settings.defaultPrice = defaultPrice;
            return this;
        }
        /*public DefaultBotSettings setOrderRandomness(double orderRandomness)
        {
            this.orderRandomness = orderRandomness;
            return this;
        }
        public DefaultBotSettings setTargetItemBalance(long targetItemBalance)
        {
            this.targetItemBalance = targetItemBalance;
            return this;
        }
        public DefaultBotSettings setMinTimerMillis(long minTimerMillis)
        {
            this.minTimerMillis = minTimerMillis;
            return this;
        }
        public DefaultBotSettings setMaxTimerMillis(long maxTimerMillis)
        {
            this.maxTimerMillis = maxTimerMillis;
            return this;
        }
        public DefaultBotSettings setImbalancePriceRange(int imbalancePriceRange)
        {
            this.imbalancePriceRange = imbalancePriceRange;
            return this;
        }
        public DefaultBotSettings setImbalancePriceChangeFactor(double imbalancePriceChangeFactor)
        {
            this.imbalancePriceChangeFactor = imbalancePriceChangeFactor;
            return this;
        }
        public DefaultBotSettings setImbalancePriceChangeQuadFactor(double imbalancePriceChangeQuadFactor)
        {
            this.imbalancePriceChangeQuadFactor = imbalancePriceChangeQuadFactor;
            return this;
        }
        public DefaultBotSettings setPid_p(double pid_p)
        {
            this.pid_p = pid_p;
            return this;
        }
        public DefaultBotSettings setPid_d(double pid_d)
        {
            this.pid_d = pid_d;
            return this;
        }
        public DefaultBotSettings setPid_i(double pid_i)
        {
            this.pid_i = pid_i;
            return this;
        }
        public DefaultBotSettings setPid_iBound(double pid_iBound)
        {
            this.pid_iBound = pid_iBound;
            return this;
        }*/
    }

    public static ServerTradingBot loadFromTag(CompoundTag tag)
    {
        if(tag == null)
            return null;

        if(!tag.contains("class"))
            return null;

        String className = tag.getString("class");

        if(className.compareTo(ServerTradingBot.class.getSimpleName()) == 0)
        {
            ServerTradingBot bot = new ServerTradingBot();
            if(bot.load(tag))
                return bot;
        }
        else if(className.compareTo(ServerVolatilityBot.class.getSimpleName()) == 0)
        {
            ServerVolatilityBot bot = new ServerVolatilityBot();
            if(bot.load(tag))
                return bot;
        }
        //else if(className.compareTo(ServerMarketMakerBot.class.getSimpleName()) == 0)
        //{
        //    ServerMarketMakerBot bot = new ServerMarketMakerBot();
        //    if(bot.load(tag))
        //        return bot;
        //}

        throw new RuntimeException("Unknown bot class: " + className);
    }

    public static class BotBuilderContainer
    {
        public String itemID;
        public DefaultBotSettings defaultSettings;
    }
    public static void botTableBuilder(
            HashMap<String, BotBuilderContainer> table,
            String itemID,
            DefaultBotSettings settings)
    {
        BotBuilderContainer container = new BotBuilderContainer();
        container.defaultSettings = settings;
        container.itemID = itemID;
        table.put(itemID, container);
    }
}
