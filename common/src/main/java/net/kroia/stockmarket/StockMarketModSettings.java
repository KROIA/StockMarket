package net.kroia.stockmarket;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
        public static HashMap<ItemID, Integer> TRADABLE_ITEMS;
        //public static ArrayList<ItemID> NOT_TRADABLE_ITEMS;

        public static void init()
        {
            TRADABLE_ITEMS = new HashMap<>();
            //TRADABLE_ITEMS.put("minecraft:diamond", 160);
            //TRADABLE_ITEMS.put("minecraft:iron_ingot", 15);
            //TRADABLE_ITEMS.put("minecraft:gold_ingot", 40);
            //TRADABLE_ITEMS.put("minecraft:emerald", 100);
            //TRADABLE_ITEMS.put("minecraft:coal", 8);
            //TRADABLE_ITEMS.put("minecraft:oak_log", 10);
            //TRADABLE_ITEMS.put("minecraft:netherite_scrap", 500);

            //NOT_TRADABLE_ITEMS = new ArrayList<>();
            //NOT_TRADABLE_ITEMS.add(BankSystemMod.MOD_ID+":"+MoneyItem.NAME);
        }

        public static ArrayList<ItemID> getNotTradableItems()
        {
            ArrayList<ItemID> items = new ArrayList<>();
            items.add(new ItemID(BankSystemMod.MOD_ID+":"+MoneyItem.NAME));
            return items;
        }
    }

    public static final class MarketBot
    {
        public static final boolean ENABLED = true;

        public static final String USER_NAME = "StockMarketBot";
        public static final long STARTING_BALANCE = 1000_000_000L*1000L; // Money balance

        public static final int MAX_ORDERS = 200;

        public static final double VOLUME_SCALE = 10;
        public static final double VOLUME_SPREAD = MAX_ORDERS/2.0;
        public static final double VOLUME_RANDOMNESS = 2;
        public static final long UPDATE_TIMER_INTERVAL_MS = 500;

        private static HashMap<String, ServerTradingBotFactory.BotBuilderContainer> botBuilder;
        public static HashMap<String, ServerTradingBotFactory.BotBuilderContainer> getBotBuilder()
        {
            botBuilder = new HashMap<>();

            float priceScale = 1f;
            long updateMS = 500;
            float volatility = 0.2f;
            botBuilder = new HashMap<>();

            Map<String, ServerTradingBotFactory.DefaultBotSettings> allSettigns = StockMarketDataHandler.loadDefaultBotSettings();
            boolean relcreatePresets = false;
            for(Map.Entry<String, ServerTradingBotFactory.DefaultBotSettings> entry : allSettigns.entrySet())
            {
                if(entry.getValue().getSettings() == null)
                {
                    relcreatePresets = true;
                    allSettigns.clear();
                    break;
                }
            }
            if(allSettigns.isEmpty() || relcreatePresets)
            {
                // Create defaults:
                Map<String, ServerTradingBotFactory.DefaultBotSettings> ores = new HashMap<>();
                ores.put("minecraft:coal", new ServerTradingBotFactory.DefaultBotSettings((int)(8*priceScale),0.05f,volatility,updateMS));
                ores.put("minecraft:copper_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.1f,volatility,updateMS));
                ores.put("minecraft:iron_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(15*priceScale),0.1f,volatility,updateMS));
                ores.put("minecraft:gold_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(40*priceScale),0.3f,volatility,updateMS));
                ores.put("minecraft:diamond", new ServerTradingBotFactory.DefaultBotSettings((int)(160*priceScale),0.4f,volatility,updateMS));
                ores.put("minecraft:lapis_lazuli", new ServerTradingBotFactory.DefaultBotSettings((int)(8*priceScale),0.25f,volatility,updateMS));
                ores.put("minecraft:emerald", new ServerTradingBotFactory.DefaultBotSettings((int)(100*priceScale),0.75f,volatility,updateMS));
                ores.put("minecraft:quartz", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.2f,volatility,updateMS));
                ores.put("minecraft:redstone", new ServerTradingBotFactory.DefaultBotSettings((int)(4*priceScale),0.2f,volatility,updateMS));
                ores.put("minecraft:glowstone_dust", new ServerTradingBotFactory.DefaultBotSettings((int)(4*priceScale),0.3f,volatility,updateMS));
                ores.put("minecraft:netherite_scrap", new ServerTradingBotFactory.DefaultBotSettings((int)(500*priceScale),0.9f,volatility,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(ores, "Ores.json");

                // Building blocks
                Map<String, ServerTradingBotFactory.DefaultBotSettings> buildingBlocks = new HashMap<>();
                buildingBlocks.put("minecraft:oak_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:spruce_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:birch_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:jungle_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:acacia_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:dark_oak_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:sand", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:dirt", new ServerTradingBotFactory.DefaultBotSettings((int)(3*priceScale),0.01f,0.05f,updateMS));
                buildingBlocks.put("minecraft:gravel", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:clay_ball", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:stone", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:obsidian", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01f,0.1f,updateMS));
                buildingBlocks.put("minecraft:glass", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,0.1f,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "BuildingBlocks.json");

                // Foods
                Map<String, ServerTradingBotFactory.DefaultBotSettings> food = new HashMap<>();
                food.put("minecraft:apple", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cooked_beef", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cooked_porkchop", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cooked_chicken", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cooked_mutton", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cooked_rabbit", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:bread", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:carrot", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:potato", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:beetroot", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:melon_slice", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:pumpkin_pie", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cookie", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:sweet_berries", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:cake", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01f,volatility,updateMS));
                food.put("minecraft:chorus_fruit", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01f,volatility,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(food, "Food.json");

                // misc
                Map<String, ServerTradingBotFactory.DefaultBotSettings> misc = new HashMap<>();
                misc.put("minecraft:bone", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                misc.put("minecraft:gunpowder", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01f,volatility,updateMS));
                misc.put("minecraft:ender_pearl", new ServerTradingBotFactory.DefaultBotSettings((int)(50*priceScale),0.01f,volatility,updateMS));
                misc.put("minecraft:blaze_rod", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01f,volatility,updateMS));
                misc.put("minecraft:ghast_tear", new ServerTradingBotFactory.DefaultBotSettings((int)(100*priceScale),0.01f,volatility,updateMS));
                misc.put("minecraft:elytra", new ServerTradingBotFactory.DefaultBotSettings((int)(5000*priceScale),0.01f,volatility,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(misc, "Misc.json");

                allSettigns = StockMarketDataHandler.loadDefaultBotSettings();
            }
            for(Map.Entry<String, ServerTradingBotFactory.DefaultBotSettings> entry : allSettigns.entrySet())
            {
                ServerTradingBotFactory.botTableBuilder(botBuilder, entry.getKey(), entry.getValue());
            }
            return botBuilder;
        }
        public static ServerTradingBotFactory.BotBuilderContainer getBotBuilder(String item)
        {
            return getBotBuilder().get(item);
        }
    }

}
