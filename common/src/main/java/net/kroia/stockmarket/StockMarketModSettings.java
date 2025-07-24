package net.kroia.stockmarket;

import com.google.gson.reflect.TypeToken;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.stockmarket.market.server.DefaultMarketBotSettings;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.util.*;

public class StockMarketModSettings {
    public static void init()
    {
        Market.init();


    }

    public static boolean saveSettings()
    {
        HashMap<String, Object> settings = new HashMap<>();
        settings.put("UI", UI.saveSettings());
        settings.put("Market", Market.saveSettings());
        settings.put("MarketBot", MarketBot.saveSettings());
        settings.put("Utilities", Utilities.saveSettings());

        boolean success = StockMarketDataHandler.saveAsJson(settings, "settings.json");
        if(success)
            StockMarketMod.LOGGER.info("StockMarketModSettings saved to JSON file.");
        else
            StockMarketMod.LOGGER.error("Failed to save StockMarketModSettings to JSON file.");
        return success;
    }

    public static boolean loadSettings()
    {
        Type type = new TypeToken<HashMap<String, Object>>() {}.getType();
        // Check if file exists
        if(!StockMarketDataHandler.fileExists("settings.json"))
        {
            StockMarketMod.LOGGER.warn("StockMarketModSettings JSON file does not exist.");
            return false;
        }
        HashMap<String, Object> settings = StockMarketDataHandler.loadFromJson("settings.json", type);
        if(settings == null)
            return false;

        if(settings.containsKey("UI"))
        {
            try {
                UI.readSettings((HashMap<String, Object>) settings.get("UI"));
            } catch (Exception e) {
                StockMarketMod.LOGGER.error("Failed to load UI settings: " + e.getMessage());
            }
        }
        if(settings.containsKey("Market"))
        {
            try {
                Market.readSettings((HashMap<String, Object>) settings.get("Market"));
            } catch (Exception e) {
                StockMarketMod.LOGGER.error("Failed to load Market settings: " + e.getMessage());
            }
        }
        if(settings.containsKey("MarketBot"))
        {
            try {
                MarketBot.readSettings((HashMap<String, Object>) settings.get("MarketBot"));
            } catch (Exception e) {
                StockMarketMod.LOGGER.error("Failed to load MarketBot settings: " + e.getMessage());
            }
        }
        if(settings.containsKey("Utilities"))
        {
            try {
                Utilities.readSettings((HashMap<String, Object>) settings.get("Utilities"));
            } catch (Exception e) {
                StockMarketMod.LOGGER.error("Failed to load Utilities settings: " + e.getMessage());
            }
        }

        StockMarketMod.LOGGER.info("StockMarketModSettings loaded from JSON file.");
        return true;
    }
    public static final class Utilities
    {
        public static long SAVE_INTERVAL_MINUTES = 5; // 5 minutes

        public static boolean LOGGING_ENABLE_INFO = true;
        public static boolean LOGGING_ENABLE_WARNING = true;
        public static boolean LOGGING_ENABLE_ERROR = true;
        public static boolean LOGGING_ENABLE_DEBUG = false;

        /**
         * For better performance when there are many trade items
         * The items are processed in chunks
         * The TRADE_ITEM_CHUNK_SIZE defines how many items are processed in one chunk
         * Downside: The update rate is not every tick but every n't ticks depending on how many chunks there are
         */
        public static int TRADE_ITEM_CHUNK_SIZE = 100;

        public static HashMap<String, Object> saveSettings()
        {
            HashMap<String, Object> settings = new HashMap<>();
            settings.put("SAVE_INTERVAL_MINUTES", SAVE_INTERVAL_MINUTES);
            settings.put("LOGGING_ENABLE_INFO", LOGGING_ENABLE_INFO);
            settings.put("LOGGING_ENABLE_WARNING", LOGGING_ENABLE_WARNING);
            settings.put("LOGGING_ENABLE_ERROR", LOGGING_ENABLE_ERROR);
            settings.put("LOGGING_ENABLE_DEBUG", LOGGING_ENABLE_DEBUG);
            return settings;
        }

        public static void readSettings(HashMap<String, Object> settings)
        {
            if(settings.containsKey("SAVE_INTERVAL_MINUTES"))
            {
                SAVE_INTERVAL_MINUTES = ((Double) settings.get("SAVE_INTERVAL_MINUTES")).longValue();
                if(SAVE_INTERVAL_MINUTES < 0)
                {
                    SAVE_INTERVAL_MINUTES = 5; // Default value
                    StockMarketMod.LOGGER.warn("Invalid SAVE_INTERVAL_MINUTES value, resetting to default: " + SAVE_INTERVAL_MINUTES);
                }
            }
            if(settings.containsKey("LOGGING_ENABLE_INFO"))
                LOGGING_ENABLE_INFO = (boolean) settings.get("LOGGING_ENABLE_INFO");
            if(settings.containsKey("LOGGING_ENABLE_WARNING"))
                LOGGING_ENABLE_WARNING = (boolean) settings.get("LOGGING_ENABLE_WARNING");
            if(settings.containsKey("LOGGING_ENABLE_ERROR"))
                LOGGING_ENABLE_ERROR = (boolean) settings.get("LOGGING_ENABLE_ERROR");
            if(settings.containsKey("LOGGING_ENABLE_DEBUG"))
                LOGGING_ENABLE_DEBUG = (boolean) settings.get("LOGGING_ENABLE_DEBUG");
        }

    }
    public static final class UI
    {

        /**
         * Size of the price history for the candle stick chart
         * Needs restart to take effect
         */
        public static int PRICE_HISTORY_SIZE = 100;

        /**
         * Maximum number of tiles used to visualize the order book for the price chart
         */
        public static int MAX_ORDERBOOK_TILES = 100;

        public static HashMap<String, Object> saveSettings()
        {
            HashMap<String, Object> settings = new HashMap<>();
            settings.put("PRICE_HISTORY_SIZE", PRICE_HISTORY_SIZE);
            settings.put("MAX_ORDERBOOK_TILES", MAX_ORDERBOOK_TILES);
            return settings;
        }

        public static void readSettings(HashMap<String, Object> settings)
        {
            if(settings.containsKey("PRICE_HISTORY_SIZE"))
            {
                PRICE_HISTORY_SIZE = ((Double) settings.get("PRICE_HISTORY_SIZE")).intValue();
                if(PRICE_HISTORY_SIZE < 0)
                {
                    PRICE_HISTORY_SIZE = 100; // Default value
                    StockMarketMod.LOGGER.warn("Invalid PRICE_HISTORY_SIZE value, resetting to default: " + PRICE_HISTORY_SIZE);
                }
            }
            if(settings.containsKey("MAX_ORDERBOOK_TILES"))
            {
                MAX_ORDERBOOK_TILES = ((Double) settings.get("MAX_ORDERBOOK_TILES")).intValue();
                if(MAX_ORDERBOOK_TILES < 0)
                {
                    MAX_ORDERBOOK_TILES = 100; // Default value
                    StockMarketMod.LOGGER.warn("Invalid MAX_ORDERBOOK_TILES value, resetting to default: " + MAX_ORDERBOOK_TILES);
                }
            }
        }
    }

    public static final class Market
    {
        /**
         * Defines the time for one candle stick in milliseconds
         */
        public static long SHIFT_PRICE_CANDLE_INTERVAL_MS = 60000;

        /**
         * If true, the market will be open directly after creation
         * If false, the market will be closed and needs to be opened manually
         */
        public static boolean MARKET_OPEN_AT_CREATION = false;

        /**
         * The item used as currency in the market
         * Default is the MoneyItem from BankSystemMod
         */
        private static ItemStack CURRENCY_ITEM;

        /**
         * List of items that are tradable without creating them manually
         * It contains a key-value pair where the key is the ItemID and the value is the initial price
         */
        public static HashMap<ItemID, Integer> INITIAL_TRADABLE_ITEMS;



        public static HashMap<String, Object> saveSettings()
        {
            HashMap<String, Object> settings = new HashMap<>();
            settings.put("SHIFT_PRICE_CANDLE_INTERVAL_MS", SHIFT_PRICE_CANDLE_INTERVAL_MS);
            settings.put("MARKET_OPEN_AT_CREATION", MARKET_OPEN_AT_CREATION);
            settings.put("MARKET_CURRENCY_ITEM", ItemUtilities.getItemID(getCurrencyItem().getItem()));
            return settings;
        }

        public static void readSettings(HashMap<String, Object> settings)
        {
            if(settings.containsKey("SHIFT_PRICE_CANDLE_INTERVAL_MS"))
            {
                SHIFT_PRICE_CANDLE_INTERVAL_MS = ((Double) settings.get("SHIFT_PRICE_CANDLE_INTERVAL_MS")).longValue();
                if(SHIFT_PRICE_CANDLE_INTERVAL_MS < 0)
                {
                    SHIFT_PRICE_CANDLE_INTERVAL_MS = 60000; // Default value
                    StockMarketMod.LOGGER.warn("Invalid SHIFT_PRICE_CANDLE_INTERVAL_MS value, resetting to default: " + SHIFT_PRICE_CANDLE_INTERVAL_MS);
                }
            }
            if(settings.containsKey("MARKET_OPEN_AT_CREATION"))
            {
                MARKET_OPEN_AT_CREATION = (boolean) settings.get("MARKET_OPEN_AT_CREATION");
            }
            if(settings.containsKey("MARKET_CURRENCY_ITEM"))
            {
                String itemID = (String) settings.get("MARKET_CURRENCY_ITEM");
                ItemStack itemStack = ItemUtilities.createItemStackFromId(itemID, 1);
                if(itemStack != null && !itemStack.isEmpty())
                {
                    setCurrencyItem(itemStack);
                }
                else
                {
                    StockMarketMod.LOGGER.error("Failed to load currency item from settings: "+itemID);
                }
            }
        }
        public static ItemStack getCurrencyItem()
        {
            if(CURRENCY_ITEM == null)
            {
                CURRENCY_ITEM = BankSystemItems.MONEY.get().getDefaultInstance();
            }
            return CURRENCY_ITEM;
        }
        private static void setCurrencyItem(ItemStack item)
        {
            if(item != null)
            {
                CURRENCY_ITEM = item;
            }
        }

        public static void init()
        {
            INITIAL_TRADABLE_ITEMS = new HashMap<>();


            //CURRENCY_ITEM = BankSystemItems.MONEY.get().getDefaultInstance();
            //CURRENCY_ITEM = ItemUtilities.createItemStackFromId("minecraft:emerald", 1);
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

            if(getCurrencyItem() != null)
            {
                items.add(new ItemID(ItemUtilities.getItemID(getCurrencyItem().getItem())));
            }
            items.addAll(BankSystemMod.SERVER_BANK_MANAGER.getBlacklistedItemIDs());
            return items;
        }
    }

    public static final class MarketBot
    {
        public static boolean ENABLED = true;

        public static long UPDATE_TIMER_INTERVAL_MS = 500;
        public static float ORDER_BOOK_VOLUME_SCALE = 100f;
        public static float NEAR_MARKET_VOLUME_SCALE = 2f;
        public static float VOLUME_ACCUMULATION_RATE = 0.01f;
        public static float VOLUME_FAST_ACCUMULATION_RATE = 0.5f;
        public static float VOLUME_DECUMULATION_RATE = 0.005f;

        public static HashMap<String, Object> saveSettings()
        {
            HashMap<String, Object> settings = new HashMap<>();
            settings.put("MARKET_BOT_ENABLED", ENABLED);
            settings.put("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS", UPDATE_TIMER_INTERVAL_MS);
            settings.put("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE", ORDER_BOOK_VOLUME_SCALE);
            settings.put("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE", NEAR_MARKET_VOLUME_SCALE);
            settings.put("MARKET_BOT_VOLUME_ACCUMULATION_RATE", VOLUME_ACCUMULATION_RATE);
            settings.put("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE", VOLUME_FAST_ACCUMULATION_RATE);
            settings.put("MARKET_BOT_VOLUME_DECUMULATION_RATE", VOLUME_DECUMULATION_RATE);
            return settings;
        }

        public static void readSettings(HashMap<String, Object> settings)
        {
            if(settings.containsKey("MARKET_BOT_ENABLED"))
            {
                ENABLED = (boolean) settings.get("MARKET_BOT_ENABLED");
            }
            if(settings.containsKey("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS"))
            {
                UPDATE_TIMER_INTERVAL_MS = ((Double) settings.get("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS")).longValue();
            }
            if(settings.containsKey("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE"))
            {
                ORDER_BOOK_VOLUME_SCALE = ((Double) settings.get("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE")).floatValue();
            }
            if(settings.containsKey("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE"))
            {
                NEAR_MARKET_VOLUME_SCALE = ((Double) settings.get("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE")).floatValue();
            }
            if(settings.containsKey("MARKET_BOT_VOLUME_ACCUMULATION_RATE"))
            {
                VOLUME_ACCUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_ACCUMULATION_RATE")).floatValue();
            }
            if(settings.containsKey("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE"))
            {
                VOLUME_FAST_ACCUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE")).floatValue();
            }
            if(settings.containsKey("MARKET_BOT_VOLUME_DECUMULATION_RATE"))
            {
                VOLUME_DECUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_DECUMULATION_RATE")).floatValue();
            }
        }

        //private static HashMap<ItemID, ServerTradingBotFactory.BotBuilderContainer> botBuilder;
        public static class BotBuilder
        {
            private HashMap<String,HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> botPresets = new HashMap<>();

            public BotBuilder()
            {

            }
            public void loadFromFilesystem()
            {
                botPresets = StockMarketDataHandler.loadDefaultBotSettings();
            }
            public HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> getBots(String presetName)
            {
                return botPresets.get(presetName);
            }
            public HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> getBots()
            {
                HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> all = new HashMap<>();
                for(Map.Entry<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> entry : botPresets.entrySet())
                {
                    all.putAll(entry.getValue());
                }
                return all;
            }
            public ServerTradingBotFactory.DefaultBotSettings get(ItemID itemID)
            {
                for(Map.Entry<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> entry : botPresets.entrySet())
                {
                    if(entry.getValue().containsKey(itemID))
                    {
                        return entry.getValue().get(itemID);
                    }
                }
                return null;
            }
            public void clear()
            {
                botPresets.clear();
            }
            public boolean isEmpty()
            {
                return botPresets.isEmpty();
            }
            public Set<ItemID> keySet()
            {
                Set<ItemID> keys = new HashSet<>();
                for(Map.Entry<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> entry : botPresets.entrySet())
                {
                    keys.addAll(entry.getValue().keySet());
                }
                return keys;
            }
        }
        public static BotBuilder getBotBuilder()
        {
            BotBuilder botBuilder = new BotBuilder();



            boolean recreatePresets = false;
            try {
                StockMarketMod.LOGGER.info("If you see a exception after here, in the case you have updated the mod to a newer version, you can ignore the exception.");
                botBuilder.loadFromFilesystem();
            } catch (Exception e) {
                recreatePresets = true;
                StockMarketMod.LOGGER.error("Failed to load default bot settings, new settings will be generated: "+e.getMessage());
            }

            if(!recreatePresets) {
                for (Map.Entry<ItemID, ServerTradingBotFactory.DefaultBotSettings> entry : botBuilder.getBots().entrySet()) {
                    if (entry == null ||
                            entry.getKey() == null ||
                            entry.getValue() == null ||
                            entry.getValue().getSettings() == null) {
                        recreatePresets = true;
                        botBuilder.clear();
                        break;
                    }
                }
            }



            if(botBuilder.isEmpty() || recreatePresets)
            {
                DefaultMarketBotSettings.createDefaultMarketBotSettings();
                botBuilder.loadFromFilesystem();
            }
            return botBuilder;
        }


        public static ServerTradingBotFactory.DefaultBotSettings getBotBuilder(ItemID itemID)
        {
            return getBotBuilder().get(itemID);
        }
    }



}
