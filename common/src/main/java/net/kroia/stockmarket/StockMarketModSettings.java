package net.kroia.stockmarket;

import net.kroia.modutilities.setting.ModSettings;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.setting.SettingsGroup;

public class StockMarketModSettings extends ModSettings {

    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public final Utilities UTILITIES = createGroup(new Utilities());

    public StockMarketModSettings() {
        super("StockMarketModSettings");
    }

    public static void init()
    {
      //  Market.init();
    }

    public static final class Utilities extends SettingsGroup
    {
        public final Setting<Long> SAVE_INTERVAL_MINUTES = registerSetting("SAVE_INTERVAL_MINUTES",5L, Long.class); // 5 minutes
        public final Setting<Boolean> LOGGING_ENABLE_INFO = registerSetting("LOGGING_ENABLE_INFO",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_WARNING = registerSetting("LOGGING_ENABLE_WARNING",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_ERROR = registerSetting("LOGGING_ENABLE_ERROR",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_DEBUG = registerSetting("LOGGING_ENABLE_DEBUG",false, Boolean.class);
        public final Setting<Integer> ADMIN_PERMISSION_LEVEL = registerSetting("ADMIN_PERMISSION_LEVEL",2, Integer.class);

        public Utilities() { super("Utilities"); }
    }
    public static final class Player extends SettingsGroup
    {
        public final Setting<Float> STARTING_BALANCE = registerSetting("STARTING_BALANCE", 0.f, Float.class); // Starting balance for new players

        public Player() { super("Player"); }
    }



    /**
     * ---------------------------------------------------------------------------------------
     *                Utilities for creating and managing settings groups
     * ---------------------------------------------------------------------------------------
     */

    @Override
    public boolean saveSettings(String filePath) {
        boolean success = super.saveSettings(filePath);
        if (success) {
            // todo: replace the below
            //BACKEND_INSTANCES.SERVER_EVENTS.SETTINGS_SAVED_TO_FILE.notifyListeners();
        }
        return success;
    }

    @Override
    public boolean loadSettings(String filePaht) {
        boolean success = super.loadSettings(filePaht);
        if (success) {
            // todo: replace the below
            //BACKEND_INSTANCES.SERVER_EVENTS.SETTINGS_LOADED_FROM_FILE.notifyListeners();
        }
        return success;
    }



   /* public static final class UI
    {
        public static final int PRICE_HISTORY_SIZE = 100;
        public static final int MAX_ORDERBOOK_TILES = 100;
    }

    public static final class Market
    {
        public static final long SHIFT_PRICE_CANDLE_INTERVAL_MS = 60000;
        public static HashMap<String, Integer> TRADABLE_ITEMS;
        public static ArrayList<String> NOT_TRADABLE_ITEMS;

        public static void init()
        {
            TRADABLE_ITEMS = new HashMap<>();
            TRADABLE_ITEMS.put("minecraft:diamond", 160);
            TRADABLE_ITEMS.put("minecraft:iron_ingot", 15);
            TRADABLE_ITEMS.put("minecraft:gold_ingot", 40);
            TRADABLE_ITEMS.put("minecraft:emerald", 100);
            TRADABLE_ITEMS.put("minecraft:coal", 8);
            TRADABLE_ITEMS.put("minecraft:oak_log", 10);
            TRADABLE_ITEMS.put("minecraft:netherite_scrap", 500);

            NOT_TRADABLE_ITEMS = new ArrayList<>();
            NOT_TRADABLE_ITEMS.add(BankSystemMod.MOD_ID+":"+MoneyItem.NAME);
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

            double priceScale = 1;
            long updateMS = 500;
            double volatility = 0.2;
            botBuilder = new HashMap<>();

            Map<String, ServerTradingBotFactory.DefaultBotSettings> allSettigns = StockMarketDataHandler.loadDefaultBotSettings();
            if(allSettigns.isEmpty())
            {
                // Create defaults:
                Map<String, ServerTradingBotFactory.DefaultBotSettings> ores = new HashMap<>();
                ores.put("minecraft:coal", new ServerTradingBotFactory.DefaultBotSettings((int)(8*priceScale),0.05,volatility,updateMS));
                ores.put("minecraft:copper_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.1,volatility,updateMS));
                ores.put("minecraft:iron_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(15*priceScale),0.1,volatility,updateMS));
                ores.put("minecraft:gold_ingot", new ServerTradingBotFactory.DefaultBotSettings((int)(40*priceScale),0.3,volatility,updateMS));
                ores.put("minecraft:diamond", new ServerTradingBotFactory.DefaultBotSettings((int)(160*priceScale),0.4,volatility,updateMS));
                ores.put("minecraft:lapis_lazuli", new ServerTradingBotFactory.DefaultBotSettings((int)(8*priceScale),0.25,volatility,updateMS));
                ores.put("minecraft:emerald", new ServerTradingBotFactory.DefaultBotSettings((int)(100*priceScale),0.75,volatility,updateMS));
                ores.put("minecraft:quartz", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.2,volatility,updateMS));
                ores.put("minecraft:redstone", new ServerTradingBotFactory.DefaultBotSettings((int)(4*priceScale),0.2,volatility,updateMS));
                ores.put("minecraft:glowstone_dust", new ServerTradingBotFactory.DefaultBotSettings((int)(4*priceScale),0.3,volatility,updateMS));
                ores.put("minecraft:netherite_scrap", new ServerTradingBotFactory.DefaultBotSettings((int)(500*priceScale),0.9,volatility,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(ores, "Ores.json");

                // Building blocks
                Map<String, ServerTradingBotFactory.DefaultBotSettings> buildingBlocks = new HashMap<>();
                buildingBlocks.put("minecraft:oak_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:spruce_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:birch_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:jungle_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:acacia_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:dark_oak_log", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:sand", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:dirt", new ServerTradingBotFactory.DefaultBotSettings((int)(3*priceScale),0.01,0.05,updateMS));
                buildingBlocks.put("minecraft:gravel", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:clay_ball", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:stone", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:obsidian", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01,0.1,updateMS));
                buildingBlocks.put("minecraft:glass", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,0.1,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "BuildingBlocks.json");

                // Foods
                Map<String, ServerTradingBotFactory.DefaultBotSettings> food = new HashMap<>();
                food.put("minecraft:apple", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cooked_beef", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cooked_porkchop", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cooked_chicken", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cooked_mutton", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cooked_rabbit", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:bread", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:carrot", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:potato", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:beetroot", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:melon_slice", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:pumpkin_pie", new ServerTradingBotFactory.DefaultBotSettings((int)(10*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cookie", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:sweet_berries", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:cake", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01,volatility,updateMS));
                food.put("minecraft:chorus_fruit", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01,volatility,updateMS));
                StockMarketDataHandler.saveDefaultBotSettings(food, "Food.json");

                // misc
                Map<String, ServerTradingBotFactory.DefaultBotSettings> misc = new HashMap<>();
                misc.put("minecraft:bone", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                misc.put("minecraft:gunpowder", new ServerTradingBotFactory.DefaultBotSettings((int)(5*priceScale),0.01,volatility,updateMS));
                misc.put("minecraft:ender_pearl", new ServerTradingBotFactory.DefaultBotSettings((int)(50*priceScale),0.01,volatility,updateMS));
                misc.put("minecraft:blaze_rod", new ServerTradingBotFactory.DefaultBotSettings((int)(20*priceScale),0.01,volatility,updateMS));
                misc.put("minecraft:ghast_tear", new ServerTradingBotFactory.DefaultBotSettings((int)(100*priceScale),0.01,volatility,updateMS));
                misc.put("minecraft:elytra", new ServerTradingBotFactory.DefaultBotSettings((int)(5000*priceScale),0.01,volatility,updateMS).setVolumeSpread(100).setPid_iBound(10));
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
    }*/

}
