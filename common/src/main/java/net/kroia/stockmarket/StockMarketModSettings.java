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
import net.minecraft.world.item.*;

import java.lang.reflect.Type;
import java.util.*;

import static java.lang.Integer.valueOf;

public class StockMarketModSettings {
    public static void init()
    {
        Market.init();


    }

    public static void tryLoadFromFile()
    {
        if(!loadFromJson())
        {
            saveToJson();
        }
    }
    public static final class UI
    {
        public static int PRICE_HISTORY_SIZE = 100;
        public static int MAX_ORDERBOOK_TILES = 100;
    }

    public static final class Market
    {
        public static long SHIFT_PRICE_CANDLE_INTERVAL_MS = 60000;
        public static HashMap<ItemID, Integer> TRADABLE_ITEMS;

        public static boolean MARKET_OPEN_AT_CREATION = false;
        //public static ArrayList<ItemID> NOT_TRADABLE_ITEMS;

        private static ItemStack CURRENCY_ITEM;
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
            TRADABLE_ITEMS = new HashMap<>();
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


    public static boolean saveToJson()
    {
        HashMap<String, Object> settings = new HashMap<>();
        settings.put("UI_PRICE_HISTORY_SIZE", UI.PRICE_HISTORY_SIZE);
        settings.put("UI_MAX_ORDERBOOK_TILES", UI.MAX_ORDERBOOK_TILES);

        settings.put("MARKET_SHIFT_PRICE_CANDLE_INTERVAL_MS", Market.SHIFT_PRICE_CANDLE_INTERVAL_MS);
        settings.put("MARKET_OPEN_AT_CREATION", Market.MARKET_OPEN_AT_CREATION);
        settings.put("MARKET_CURRENCY_ITEM", ItemUtilities.getItemID(Market.getCurrencyItem().getItem()));

        settings.put("MARKET_BOT_ENABLED", MarketBot.ENABLED);
        settings.put("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS", MarketBot.UPDATE_TIMER_INTERVAL_MS);
        settings.put("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE", MarketBot.ORDER_BOOK_VOLUME_SCALE);
        settings.put("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE", MarketBot.NEAR_MARKET_VOLUME_SCALE);
        settings.put("MARKET_BOT_VOLUME_ACCUMULATION_RATE", MarketBot.VOLUME_ACCUMULATION_RATE);
        settings.put("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE", MarketBot.VOLUME_FAST_ACCUMULATION_RATE);
        settings.put("MARKET_BOT_VOLUME_DECUMULATION_RATE", MarketBot.VOLUME_DECUMULATION_RATE);



        boolean success = StockMarketDataHandler.saveAsJson(settings, "stockmarket_initial_settings.json");
        if(success)
        {
            StockMarketMod.LOGGER.info("StockMarketModSettings saved to JSON file.");
        }
        else
        {
            StockMarketMod.LOGGER.error("Failed to save StockMarketModSettings to JSON file.");
        }
        return success;
    }

    public static boolean loadFromJson()
    {
        Type type = new TypeToken<HashMap<String, Object>>() {}.getType();
        // Check if file exists
        if(!StockMarketDataHandler.fileExists("stockmarket_initial_settings.json"))
        {
            StockMarketMod.LOGGER.warn("StockMarketModSettings JSON file does not exist.");
            return false;
        }
        HashMap<String, Object> settings = StockMarketDataHandler.loadFromJson("stockmarket_initial_settings.json", type);
        if(settings == null)
            return false;

        if(settings.containsKey("UI_PRICE_HISTORY_SIZE"))
        {
            UI.PRICE_HISTORY_SIZE = ((Double) settings.get("UI_PRICE_HISTORY_SIZE")).intValue();
        }
        if(settings.containsKey("UI_MAX_ORDERBOOK_TILES"))
        {
            UI.MAX_ORDERBOOK_TILES = ((Double) settings.get("UI_MAX_ORDERBOOK_TILES")).intValue();
        }

        if(settings.containsKey("MARKET_SHIFT_PRICE_CANDLE_INTERVAL_MS"))
        {
            Market.SHIFT_PRICE_CANDLE_INTERVAL_MS = ((Double) settings.get("MARKET_SHIFT_PRICE_CANDLE_INTERVAL_MS")).longValue();
        }
        if(settings.containsKey("MARKET_OPEN_AT_CREATION"))
        {
            Market.MARKET_OPEN_AT_CREATION = (boolean) settings.get("MARKET_OPEN_AT_CREATION");
        }
        if(settings.containsKey("MARKET_CURRENCY_ITEM"))
        {
            String itemID = (String) settings.get("MARKET_CURRENCY_ITEM");
            ItemStack itemStack = ItemUtilities.createItemStackFromId(itemID, 1);
            if(itemStack != null && !itemStack.isEmpty())
            {
                Market.setCurrencyItem(itemStack);
            }
            else
            {
                StockMarketMod.LOGGER.error("Failed to load currency item from settings: "+itemID);
                return false;
            }
        }

        if(settings.containsKey("MARKET_BOT_ENABLED"))
        {
            MarketBot.ENABLED = (boolean) settings.get("MARKET_BOT_ENABLED");
        }
        if(settings.containsKey("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS"))
        {
            MarketBot.UPDATE_TIMER_INTERVAL_MS = ((Double) settings.get("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS")).longValue();
        }
        if(settings.containsKey("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE"))
        {
            MarketBot.ORDER_BOOK_VOLUME_SCALE = ((Double) settings.get("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE")).floatValue();
        }
        if(settings.containsKey("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE"))
        {
            MarketBot.NEAR_MARKET_VOLUME_SCALE = ((Double) settings.get("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE")).floatValue();
        }
        if(settings.containsKey("MARKET_BOT_VOLUME_ACCUMULATION_RATE"))
        {
            MarketBot.VOLUME_ACCUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_ACCUMULATION_RATE")).floatValue();
        }
        if(settings.containsKey("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE"))
        {
            MarketBot.VOLUME_FAST_ACCUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE")).floatValue();
        }
        if(settings.containsKey("MARKET_BOT_VOLUME_DECUMULATION_RATE"))
        {
            MarketBot.VOLUME_DECUMULATION_RATE = ((Double) settings.get("MARKET_BOT_VOLUME_DECUMULATION_RATE")).floatValue();
        }
        StockMarketMod.LOGGER.info("StockMarketModSettings loaded from JSON file.");
        return true;
    }
}
