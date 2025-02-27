package net.kroia.stockmarket;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.stockmarket.market.server.DefaultMarketBotSettings;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.*;

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

        public static final long UPDATE_TIMER_INTERVAL_MS = 500;
        public static final float ORDER_BOOK_VOLUME_SCALE = 100f;
        public static final float NEAR_MARKET_VOLUME_SCALE = 2f;
        public static final float VOLUME_ACCUMULATION_RATE = 0.01f;
        public static final float VOLUME_FAST_ACCUMULATION_RATE = 0.5f;
        public static final float VOLUME_DECUMULATION_RATE = 0.005f;


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
