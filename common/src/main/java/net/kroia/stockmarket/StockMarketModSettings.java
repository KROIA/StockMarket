package net.kroia.stockmarket;

import com.google.gson.reflect.TypeToken;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.setting.ModSettings;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.setting.SettingsGroup;
import net.kroia.modutilities.setting.parser.ItemStackJsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StockMarketModSettings extends ModSettings {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public final Utilities UTILITIES = createGroup(new Utilities());
    public final UISettings UI = createGroup(new UISettings());
    public final Market MARKET = createGroup(new Market());
    public final MarketBot MARKET_BOT = createGroup(new MarketBot());

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public StockMarketModSettings() {
        super("StockMarketModSettings");
    }





    public static final class Utilities extends SettingsGroup
    {
        public final Setting<Long> SAVE_INTERVAL_MINUTES = registerSetting("SAVE_INTERVAL_MINUTES",5L, Long.class); // 5 minutes
        public final Setting<Boolean> LOGGING_ENABLE_INFO = registerSetting("LOGGING_ENABLE_INFO",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_WARNING = registerSetting("LOGGING_ENABLE_WARNING",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_ERROR = registerSetting("LOGGING_ENABLE_ERROR",true, Boolean.class);
        public final Setting<Boolean> LOGGING_ENABLE_DEBUG = registerSetting("LOGGING_ENABLE_DEBUG",false, Boolean.class);
        public final Setting<Integer> ADMIN_PERMISSION_LEVEL = registerSetting("ADMIN_PERMISSION_LEVEL",2, Integer.class);


        /**
         * For better performance when there are many trade items
         * The items are processed in chunks
         * The TRADE_ITEM_CHUNK_SIZE defines how many items are processed in one chunk
         * Downside: The update rate is not every tick but every n't ticks depending on how many chunks there are
         */
        public final Setting<Integer> TRADE_ITEM_CHUNK_SIZE = registerSetting("TRADE_ITEM_CHUNK_SIZE", 100, Integer.class);

        public Utilities() { super("Utilities"); }


        public boolean playerIsAdmin(ServerPlayer player)
        {
            return player.hasPermissions(ADMIN_PERMISSION_LEVEL.get());
        }
    }
    public static final class UISettings extends SettingsGroup
    {

        /**
         * Size of the price history for the candle stick chart
         * Needs restart to take effect
         */
        public final Setting<Integer> PRICE_HISTORY_SIZE = registerSetting("PRICE_HISTORY_SIZE", 200, Integer.class);

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
        //public final Setting<Long> NOTIFY_SUBSCRIBER_INTERVAL_MS = registerSetting("NOTIFY_SUBSCRIBER_INTERVAL_MS", 100L, Long.class); // 1 minute

        /**
         * If true, the market will be open directly after creation
         * If false, the market will be closed and needs to be opened manually
         */
        public final Setting<Boolean> MARKET_OPEN_AT_CREATION = registerSetting("MARKET_OPEN_AT_CREATION", false, Boolean.class);

        /**
         * The item used as currency in the market
         * Default is the MoneyItem from BankSystemMod
         */
        private final Setting<ItemStack> CURRENCY_ITEM = registerSetting("CURRENCY_ITEM", BankSystemItems.MONEY.get().getDefaultInstance(), ItemStack.class, new ItemStackJsonParser());

        /**
         * List of items that are tradable without creating them manually
         * It contains a key-value pair where the key is the ItemID and the value is the initial price
         */
        public final Setting<HashMap<ItemID, Integer>> INITIAL_TRADABLE_ITEMS = registerSetting(
                "INITIAL_TRADABLE_ITEMS",
                new HashMap<ItemID, Integer>() {{ }},
                new TypeToken<HashMap<ItemID, Integer>>(){}.getType()
        );

        /**
         * Defines the size of the virtual order book array which is used as virtual order liquidity
         */
        public final Setting<Integer> VIRTUAL_ORDERBOOK_ARRAY_SIZE = registerSetting("VIRTUAL_ORDERBOOK_ARRAY_SIZE", 100, Integer.class);


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


        public Map<ItemID, Boolean> getNotTradableItems()
        {
            Map<ItemID, Boolean> items = new HashMap<>();
            ArrayList<ItemStack> moneyItems = BankSystemItems.getMoneyItems();
            for(ItemStack moneyItem : moneyItems)
            {
                if(moneyItem != null && !moneyItem.getItem().equals(BankSystemItems.MONEY.get()))
                {
                    items.put(new ItemID(moneyItem), true);
                }
            }

            /*if(getCurrencyItem() != null)
            {
                items.add(new ItemID(getCurrencyItem().getItem().getDefaultInstance()));
            }*/
            ArrayList<ItemID> blacklisted = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBlacklistedItemIDs();
            for(ItemID id : blacklisted)
                items.put(id, true);
            return items;
        }
    }

    public static final class MarketBot extends SettingsGroup
    {
        //public final Setting<Boolean> ENABLED = registerSetting("MARKET_BOT_ENABLED", true, Boolean.class);

        public final Setting<Long> UPDATE_TIMER_INTERVAL_MS = registerSetting("MARKET_BOT_UPDATE_TIMER_INTERVAL_MS", 500L, Long.class); // 1 second
        public final Setting<Float> ORDER_BOOK_VOLUME_SCALE = registerSetting("MARKET_BOT_ORDER_BOOK_VOLUME_SCALE", 100f, Float.class); // Scale for the order book volume visualization
        public final Setting<Float> NEAR_MARKET_VOLUME_SCALE = registerSetting("MARKET_BOT_NEAR_MARKET_VOLUME_SCALE", 2f, Float.class); // Scale for the near market volume visualization
        public final Setting<Float> VOLUME_ACCUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_ACCUMULATION_RATE", 0.001f, Float.class); // Rate at which the volume is accumulated
        public final Setting<Float> VOLUME_FAST_ACCUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE", 0.1f, Float.class); // Rate at which the volume is accumulated when the bot is active
        public final Setting<Float> VOLUME_DECUMULATION_RATE = registerSetting("MARKET_BOT_VOLUME_DECUMULATION_RATE", 0.0001f, Float.class); // Rate at which the volume is decumulated


        public MarketBot() { super("MarketBot"); }

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
            BACKEND_INSTANCES.SERVER_EVENTS.STOCKMARKET_DATA_SAVED_TO_FILE.notifyListeners();
        }
        return success;
    }

    @Override
    public boolean loadSettings(String filePath) {
        boolean success = super.loadSettings(filePath);
        if (success) {
            BACKEND_INSTANCES.SERVER_EVENTS.STOCKMARKET_DATA_LOADED_FROM_FILE.notifyListeners();
        }
        return success;
    }
}
