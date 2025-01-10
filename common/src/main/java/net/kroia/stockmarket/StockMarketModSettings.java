package net.kroia.stockmarket;

import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;

import java.util.HashMap;

public class StockMarketModSettings {
    public static void init()
    {
        Market.init();
    }
    public static final class UI
    {
        public static final int PRICE_HISTORY_SIZE = 100;
        public static final int MAX_ORDERBOOK_TILES = 100;
    }

    public static final class Market
    {
        public static final long SHIFT_PRICE_CANDLE_INTERVAL_MS = 60000;
        public static HashMap<String, Integer> TRADABLE_ITEMS;

        public static void init()
        {
            if(TRADABLE_ITEMS != null)
                return;
            TRADABLE_ITEMS = new HashMap<>();
            TRADABLE_ITEMS.put("minecraft:diamond", 100);
            TRADABLE_ITEMS.put("minecraft:iron_ingot", 30);
            TRADABLE_ITEMS.put("minecraft:gold_ingot", 50);
            TRADABLE_ITEMS.put("minecraft:emerald", 50);
            TRADABLE_ITEMS.put("minecraft:coal", 10);
            TRADABLE_ITEMS.put("minecraft:oak_log", 10);
            TRADABLE_ITEMS.put("minecraft:netherite_scrap", 500);

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
        public static final long STARTING_BALANCE = 1000_000_000; // Money balance

        public static final int MAX_ORDERS = 200;

        public static final double VOLUME_SCALE = 10;
        public static final double VOLUME_SPREAD = 10;
        public static final double VOLUME_RANDOMNESS = 2;
        public static final long UPDATE_TIMER_INTERVAL_MS = 500;
        public static HashMap<String, ServerTradingBotFactory.BotBuilderContainer> createBots()
        {
            HashMap<String, ServerTradingBotFactory.BotBuilderContainer> bots = new HashMap<>();
            bots = new HashMap<>();

            double pidP = 0.1;
            double pidI = 0.001;
            double pidD = -0.01;
            double pidIBound = 10;

            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:diamond", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,1000,0.1,10,pidP,pidD,pidI,pidIBound),1000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:iron_ingot", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,10000,10000,300000,300,0.1,10,pidP,pidD,pidI,pidIBound),10000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:gold_ingot", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,200,0.1,10,pidP,pidD,pidI,pidIBound),1000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:coal", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,50,0.1,10,pidP,pidD,pidI,pidIBound),1000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:oak_log", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,30,0.1,10,pidP,pidD,pidI,pidIBound),1000);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:netherite_scrap", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,2000,0.1,10,pidP,pidD,pidI,pidIBound),500);
            ServerTradingBotFactory.botTableBuilder(bots, "minecraft:emerald", new ServerVolatilityBot(),
                    new ServerVolatilityBot.Settings(100,1000,10000,300000,100,0.1,10,pidP,pidD,pidI,pidIBound),100);


            return bots;
        }
    }

}
