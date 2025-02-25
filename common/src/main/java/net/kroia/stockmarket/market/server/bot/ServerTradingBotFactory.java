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
            this.settings.copyFrom(settings);
        }
        public void loadDefaultSettings(ServerVolatilityBot.Settings settings)
        {
            settings.copyFrom(this.settings);
        }
        public ServerVolatilityBot.Settings getSettings()
        {
            return settings;
        }
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

        return null;
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
