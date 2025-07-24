package net.kroia.stockmarket;

import com.google.gson.reflect.TypeToken;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.setting.ModSettings;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.setting.SettingsGroup;
import net.kroia.stockmarket.market.server.DefaultMarketBotSettings;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class StockMarketModSettings extends ModSettings {

    public final Utilities UTILITIES = createGroup(new Utilities());
    public final UISettings UI = createGroup(new UISettings());
    public final Market MARKET = createGroup(new Market());
    public final MarketBot MARKET_BOT = createGroup(new MarketBot());


    public StockMarketModSettings() {
        super("StockMarketModSettings", "settings.json");
    }





    public static final class Utilities extends SettingsGroup
    {
        public final Setting<Long> SAVE_INTERVAL_MINUTES = registerSetting("SAVE_INTERVAL_MINUTES",5L, Long.class); // 5 minutes
        public final Setting<Boolean> LOGGING_ENABLE_INFO = registerSetting("LOGGING_ENABLE_INFO",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_WARNING = registerSetting("LOGGING_ENABLE_WARNING",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_ERROR = registerSetting("LOGGING_ENABLE_ERROR",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_DEBUG = registerSetting("LOGGING_ENABLE_DEBUG",false, Boolean.class);


        /**
         * For better performance when there are many trade items
         * The items are processed in chunks
         * The TRADE_ITEM_CHUNK_SIZE defines how many items are processed in one chunk
         * Downside: The update rate is not every tick but every n't ticks depending on how many chunks there are
         */
        public final Setting<Integer> TRADE_ITEM_CHUNK_SIZE = registerSetting("TRADE_ITEM_CHUNK_SIZE", 100, Integer.class);

        public Utilities() { super("Utilities"); }
    }
    public static final class UISettings extends SettingsGroup
    {

        /**
         * Size of the price history for the candle stick chart
         * Needs restart to take effect
         */
        public final Setting<Integer> PRICE_HISTORY_SIZE = registerSetting("PRICE_HISTORY_SIZE", 100, Integer.class);

        /**
         * Maximum number of tiles used to visualize the order book for the price chart
         */
        public final Setting<Integer> MAX_ORDERBOOK_TILES = registerSetting("MAX_ORDERBOOK_TILES", 100, Integer.class);

        public UISettings() { super("UISettings"); }
    }

    public static final class Market extends SettingsGroup
    {
        /**
         * Defines the time for one candle stick in milliseconds
         */
        public final Setting<Long> SHIFT_PRICE_CANDLE_INTERVAL_MS = registerSetting("SHIFT_PRICE_CANDLE_INTERVAL_MS", 60000L, Long.class); // 1 minute

        /**
         * If true, the market will be open directly after creation
         * If false, the market will be closed and needs to be opened manually
         */
        public final Setting<Boolean> MARKET_OPEN_AT_CREATION = registerSetting("MARKET_OPEN_AT_CREATION", false, Boolean.class);

        /**
         * The item used as currency in the market
         * Default is the MoneyItem from BankSystemMod
         */
        private final Setting<ItemStack> CURRENCY_ITEM = registerSetting("CURRENCY_ITEM", BankSystemItems.MONEY.get().getDefaultInstance(), ItemStack.class);

        /**
         * List of items that are tradable without creating them manually
         * It contains a key-value pair where the key is the ItemID and the value is the initial price
         */
        public final Setting<HashMap<ItemID, Integer>> INITIAL_TRADABLE_ITEMS = registerSetting(
                "INITIAL_TRADABLE_ITEMS",
                new HashMap<ItemID, Integer>() {{ }},
                new TypeToken<HashMap<ItemID, Integer>>(){}.getType()
        );



        public Market() { super("Market"); }
        public ItemStack getCurrencyItem()
        {
            if(CURRENCY_ITEM.get() == null)
            {
                CURRENCY_ITEM.set(BankSystemItems.MONEY.get().getDefaultInstance());
            }
            return CURRENCY_ITEM.get();
        }
        private void setCurrencyItem(ItemStack item)
        {
            if(item != null)
            {
                CURRENCY_ITEM.set(item);
            }
        }


        public ArrayList<ItemID> getNotTradableItems()
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

    public static final class MarketBot extends SettingsGroup
    {
        public final Setting<Boolean> ENABLED = registerSetting("MARKET_BOT_ENABLED", true, Boolean.class);

        public final Setting<Long> UPDATE_TIMER_INTERVAL_MS = registerSetting("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS", 500L, Long.class); // 1 second
        public final Setting<Float> ORDER_BOOK_VOLUME_SCALE = registerSetting("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE", 100f, Float.class); // Scale for the order book volume visualization
        public final Setting<Float> NEAR_MARKET_VOLUME_SCALE = registerSetting("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE", 2f, Float.class); // Scale for the near market volume visualization
        public final Setting<Float> VOLUME_ACCUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_ACCUMULATION_RATE", 0.01f, Float.class); // Rate at which the volume is accumulated
        public final Setting<Float> VOLUME_FAST_ACCUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE", 0.5f, Float.class); // Rate at which the volume is accumulated when the bot is active
        public final Setting<Float> VOLUME_DECUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_DECUMULATION_RATE", 0.005f, Float.class); // Rate at which the volume is decumulated


        public MarketBot() { super("MarketBot"); }


        //private static HashMap<ItemID, ServerTradingBotFactory.BotBuilderContainer> botBuilder;
        public static class BotBuilder
        {
            private HashMap<String,HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> botPresets = new HashMap<>();

            public BotBuilder()
            {

            }
            public void loadFromFilesystem()
            {
                botPresets = StockMarketMod.SERVER_DATA_HANDLER.loadDefaultBotSettings();
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
                StockMarketMod.logInfo("If you see a exception after here, in the case you have updated the mod to a newer version, you can ignore the exception.");
                botBuilder.loadFromFilesystem();
            } catch (Exception e) {
                recreatePresets = true;
                StockMarketMod.logError("Failed to load default bot settings, new settings will be generated: "+e.getMessage());
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


    /**
     * ---------------------------------------------------------------------------------------
     *                Utilities for creating and managing settings groups
     * ---------------------------------------------------------------------------------------
     */

    @Override
    public String getSettingsFilePath() {
        return StockMarketMod.SERVER_DATA_HANDLER.getSaveFolder().getPath();
    }


    @Override
    public boolean saveSettings() {
        boolean success = super.saveSettings();
        if (success) {
            StockMarketMod.SERVER_EVENTS.STOCKMARKET_DATA_SAVED_TO_FILE.notify();
        }
        return success;
    }

    @Override
    public boolean loadSettings() {
        boolean success = super.loadSettings();
        if (success) {
            StockMarketMod.SERVER_EVENTS.STOCKMARKET_DATA_LOADED_FROM_FILE.notify();
        }
        return success;
    }
}
