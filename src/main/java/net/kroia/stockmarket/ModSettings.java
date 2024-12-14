package net.kroia.stockmarket;

import net.kroia.stockmarket.market.server.bot.ServerMarketMakerBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ModSettings {
    public static final class Player
    {
        public static final long STARTING_BALANCE = 0;
    }

    public static final class Bank
    {
        public static final boolean NOTIFICATION_USE_SHORT_ITEM_ID = true;
    }

    public static final class Market
    {
        public static final long SHIFT_PRICE_CANCLE_INTERVAL_MS = 5000;
        public static final HashMap<String, Integer> TRADABLE_ITEMS;
        static{
            TRADABLE_ITEMS = new HashMap<>();
            TRADABLE_ITEMS.put("minecraft:diamond", 50);
            TRADABLE_ITEMS.put("minecraft:iron_ingot", 10);
            TRADABLE_ITEMS.put("minecraft:gold_ingot", 25);
            TRADABLE_ITEMS.put("minecraft:emerald", 100);
            TRADABLE_ITEMS.put("minecraft:coal", 5);
            //TRADABLE_ITEMS.put("minecraft:quartz", 5);
            //TRADABLE_ITEMS.put("minecraft:obsidian", 10);
            //TRADABLE_ITEMS.put("minecraft:glowstone", 10);
            //TRADABLE_ITEMS.put("minecraft:blaze_rod", 20);
            //TRADABLE_ITEMS.put("minecraft:ender_pearl", 50);
            //TRADABLE_ITEMS.put("minecraft:ghast_tear", 100);
            //TRADABLE_ITEMS.put("minecraft:shulker_shell", 200);
            //TRADABLE_ITEMS.put("minecraft:netherite_ingot", 500);
            //TRADABLE_ITEMS.put("minecraft:ancient_debris", 1000);
            //TRADABLE_ITEMS.put("minecraft:elytra", 5000);
            //TRADABLE_ITEMS.put("minecraft:dragon_egg", 10000);
            //TRADABLE_ITEMS.put("minecraft:enchanted_golden_apple", 1000);
            //TRADABLE_ITEMS.put("minecraft:totem_of_undying", 1000);
        }
    }

    public static final class MarketBot
    {
        public static final boolean ENABLED = true;

        public static final String USER_NAME = "StockMarketBot";
        public static final long STARTING_BALANCE = 1000000; // Money balance

        public static final HashMap<String, Long> STARTING_STOCKS;
        static{
            STARTING_STOCKS = new HashMap<>();
            STARTING_STOCKS.put("minecraft:iron_ingot", 1000L);
            STARTING_STOCKS.put("minecraft:gold_ingot", 1000L);
            STARTING_STOCKS.put("minecraft:diamond", 1000L);
            STARTING_STOCKS.put("minecraft:emerald", 1000L);
        }
        public static final int MAX_ORDERS = 50;

        public static final double VOLUME_SCALE = 50;
        public static final double VOLUME_SPREAD = 10;
        public static final long UPDATE_TIMER_INTERVAL_MS = 100;

        public static final class VolatilityBot
        {
            public static final int VOLATILITY = 1;
        }

        private static HashMap<String, ServerTradingBotFactory.BotBuilderContainer> bots;
        public static HashMap<String, ServerTradingBotFactory.BotBuilderContainer> createBots()
        {
            if(bots != null)
                return bots;
            bots = new HashMap<>();

            //ServerTradingBotFactory.botTableBuilder(bots, "minecraft:diamond", new ServerTradingBot(), new ServerTradingBot.Settings(), 10000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:diamond", new ServerVolatilityBot(), new ServerVolatilityBot.Settings(),10000);
            //ServerTradingBotFactory.botTableBuilder(bots, "minecraft:iron_ingot", new ServerVolatilityBot(), new ServerVolatilityBot.Settings(), 1000);
            //ServerTradingBotFactory.botTableBuilder(bots, "minecraft:emerald", new ServerTradingBot(), new ServerTradingBot.Settings(), 100);
            //ServerTradingBotFactory.botTableBuilder(bots, "minecraft:gold_ingot", new ServerTradingBot(), new ServerTradingBot.Settings(), 100);
            //ServerTradingBotFactory.botTableBuilder(bots, "minecraft:gold_ingot", new ServerMarketMakerBot(), new ServerMarketMakerBot.Settings(), 1000);

            return bots;
        }
    }

}
