package net.kroia.stockmarket;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        public static final long UPDATE_TIMER_INTERVAL_MS = 500;
        public static final float ORDER_BOOK_VOLUME_SCALE = 100f;
        public static final float NEAR_MARKET_VOLUME_SCALE = 2f;
        public static final float VOLUME_ACCUMULATION_RATE = 0.01f;
        public static final float VOLUME_FAST_ACCUMULATION_RATE = 0.5f;
        public static final float VOLUME_DECUMULATION_RATE = 0.005f;


        private static HashMap<ItemID, ServerTradingBotFactory.BotBuilderContainer> botBuilder;
        public static HashMap<ItemID, ServerTradingBotFactory.BotBuilderContainer> getBotBuilder()
        {
            botBuilder = new HashMap<>();

            float priceScale = 1f;
            long updateMS = 500;
            float volatility = 0.2f;
            botBuilder = new HashMap<>();

            boolean recreatePresets = false;
            Map<ServerTradingBotFactory.ItemData, ServerTradingBotFactory.DefaultBotSettings> allSettings = new HashMap<>();
            try {
                StockMarketMod.LOGGER.info("If you see a exception after here, in the case you have updated the mod to a newer version, you can ignore the exception.");
                allSettings = StockMarketDataHandler.loadDefaultBotSettings();
            } catch (Exception e) {
                recreatePresets = true;
                StockMarketMod.LOGGER.error("Failed to load default bot settings, new settings will be generated: "+e.getMessage());
            }

            if(!recreatePresets) {
                for (Map.Entry<ServerTradingBotFactory.ItemData, ServerTradingBotFactory.DefaultBotSettings> entry : allSettings.entrySet()) {
                    if (entry == null ||
                            entry.getKey() == null ||
                            entry.getKey().getItemID() == null ||
                            entry.getValue() == null ||
                            entry.getValue().getSettings() == null) {
                        recreatePresets = true;
                        allSettings.clear();
                        break;
                    }
                }
            }



            if(allSettings.isEmpty() || recreatePresets)
            {
                StockMarketMod.LOGGER.error("Generating new default bot settings.");
                // Create defaults:
                // Ores
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> ores = new ArrayList<>();
                createBotSetting(ores, "minecraft:coal", (int)(8*priceScale), 0.05f, volatility, updateMS);
                createBotSetting(ores, "minecraft:copper_ingot", (int)(10*priceScale), 0.1f, volatility, updateMS);
                createBotSetting(ores, "minecraft:iron_ingot", (int)(15*priceScale), 0.1f, volatility, updateMS);
                createBotSetting(ores, "minecraft:gold_ingot", (int)(40*priceScale), 0.3f, volatility, updateMS);
                createBotSetting(ores, "minecraft:diamond", (int)(160*priceScale), 0.4f, volatility, updateMS);
                createBotSetting(ores, "minecraft:lapis_lazuli", (int)(8*priceScale), 0.25f, volatility, updateMS);
                createBotSetting(ores, "minecraft:emerald", (int)(100*priceScale), 0.75f, volatility, updateMS);
                createBotSetting(ores, "minecraft:quartz", (int)(10*priceScale), 0.2f, volatility, updateMS);
                createBotSetting(ores, "minecraft:redstone", (int)(4*priceScale), 0.2f, volatility, updateMS);
                createBotSetting(ores, "minecraft:glowstone_dust", (int)(4*priceScale), 0.3f, volatility, updateMS);
                createBotSetting(ores, "minecraft:netherite_scrap", (int)(500*priceScale), 0.9f, volatility, updateMS);
                StockMarketDataHandler.saveDefaultBotSettings(ores, "Ores.json");

                // Building blocks
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> buildingBlocks = new ArrayList<>();
                createBotSetting(buildingBlocks, "minecraft:oak_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:spruce_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:birch_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:jungle_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:acacia_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:dark_oak_log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:sand", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:dirt", (int)(3*priceScale), 0.01f, 0.05f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:gravel", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:clay_ball", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:stone", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:obsidian", (int)(20*priceScale), 0.01f, 0.1f, updateMS);
                createBotSetting(buildingBlocks, "minecraft:glass", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
                StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "BuildingBlocks.json");

                // Foods
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> food = new ArrayList<>();
                createBotSetting(food, "minecraft:apple", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cooked_beef", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cooked_porkchop", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cooked_chicken", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cooked_mutton", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cooked_rabbit", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:bread", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:carrot", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:potato", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:beetroot", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:melon_slice", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:pumpkin_pie", (int)(10*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cookie", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:sweet_berries", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:cake", (int)(20*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(food, "minecraft:chorus_fruit", (int)(20*priceScale), 0.01f, volatility, updateMS);
                StockMarketDataHandler.saveDefaultBotSettings(food, "Food.json");

                // misc
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> misc = new ArrayList<>();
                createBotSetting(misc, "minecraft:bone", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(misc, "minecraft:gunpowder", (int)(5*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(misc, "minecraft:ender_pearl", (int)(50*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(misc, "minecraft:blaze_rod", (int)(20*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(misc, "minecraft:ghast_tear", (int)(100*priceScale), 0.01f, volatility, updateMS);
                createBotSetting(misc, "minecraft:elytra", (int)(5000*priceScale), 0.01f, volatility, updateMS);
                StockMarketDataHandler.saveDefaultBotSettings(misc, "Misc.json");




                // Enchanted Books
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> enchantedBooks = new ArrayList<>();
                for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
                    int maxLevel = enchantment.getMaxLevel();

                    // Create a book for each level from 1 to max level
                    for (int level = 1; level <= maxLevel; level++) {
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        EnchantmentHelper.setEnchantments(Map.of(enchantment, level), book);
                        createBotSetting(enchantedBooks, book, (int)(200*priceScale), 0.001f, volatility, updateMS);
                    }
                }
                StockMarketDataHandler.saveDefaultBotSettings(enchantedBooks, "EnchantedBooks.json");

                // Potions
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> potions = new ArrayList<>();
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> splashPotions = new ArrayList<>();
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> lingeringPotions = new ArrayList<>();
                ArrayList<ServerTradingBotFactory.BotBuilderContainer> tippedArrows = new ArrayList<>();
                for (int i=0; i<BuiltInRegistries.POTION.size(); i++) {
                    Potion potion = BuiltInRegistries.POTION.byId(i);
                    if(potion.getEffects().isEmpty())
                        continue;
                    int worth = calculatePotionWorth(potion);

                    ItemStack potionBottle = new ItemStack(Items.POTION);
                    PotionUtils.setPotion(potionBottle, potion); // Apply potion type
                    createBotSetting(potions, potionBottle, (int)(worth*priceScale), 0.001f, volatility, updateMS);

                    ItemStack splashPotion = new ItemStack(Items.SPLASH_POTION);
                    PotionUtils.setPotion(splashPotion, potion);
                    createBotSetting(splashPotions, splashPotion, (int)(worth*priceScale), 0.001f, volatility, updateMS);

                    ItemStack lingeringPotion = new ItemStack(Items.LINGERING_POTION);
                    PotionUtils.setPotion(lingeringPotion, potion);
                    createBotSetting(lingeringPotions, lingeringPotion, (int)(worth*priceScale), 0.001f, volatility, updateMS);

                    ItemStack tippedArrow = new ItemStack(Items.TIPPED_ARROW);
                    PotionUtils.setPotion(tippedArrow, potion);
                    createBotSetting(tippedArrows, tippedArrow, (int)(worth*priceScale), 0.001f, volatility, updateMS);
                }
                StockMarketDataHandler.saveDefaultBotSettings(potions, "Potions.json");
                StockMarketDataHandler.saveDefaultBotSettings(splashPotions, "SplashPotions.json");
                StockMarketDataHandler.saveDefaultBotSettings(lingeringPotions, "LingeringPotions.json");
                StockMarketDataHandler.saveDefaultBotSettings(tippedArrows, "TippedArrows.json");


                allSettings = StockMarketDataHandler.loadDefaultBotSettings();
            }
            for(Map.Entry<ServerTradingBotFactory.ItemData, ServerTradingBotFactory.DefaultBotSettings> entry : allSettings.entrySet())
            {
                ServerTradingBotFactory.botTableBuilder(botBuilder, entry.getKey(), entry.getValue());
            }
            return botBuilder;
        }

        private static void createBotSetting(ArrayList<ServerTradingBotFactory.BotBuilderContainer> container, String itemID, int defaultPrice, float rarity, float volatility, long updateTimerIntervallMS)
        {
            ServerTradingBotFactory.DefaultBotSettings settings = new ServerTradingBotFactory.DefaultBotSettings(defaultPrice, rarity, volatility, updateTimerIntervallMS);
            ServerTradingBotFactory.ItemData itemData = new ServerTradingBotFactory.ItemData(itemID);
            ServerTradingBotFactory.BotBuilderContainer botContainer = new ServerTradingBotFactory.BotBuilderContainer();
            botContainer.itemData = itemData;
            botContainer.defaultSettings = settings;
            container.add(botContainer);
        }
        private static void createBotSetting(ArrayList<ServerTradingBotFactory.BotBuilderContainer> container, ItemStack itemStack, int defaultPrice, float rarity, float volatility, long updateTimerIntervallMS)
        {
            ServerTradingBotFactory.DefaultBotSettings settings = new ServerTradingBotFactory.DefaultBotSettings(defaultPrice, rarity, volatility, updateTimerIntervallMS);
            ServerTradingBotFactory.ItemData itemData = new ServerTradingBotFactory.ItemData(itemStack);
            ServerTradingBotFactory.BotBuilderContainer botContainer = new ServerTradingBotFactory.BotBuilderContainer();
            botContainer.itemData = itemData;
            botContainer.defaultSettings = settings;
            container.add(botContainer);
        }
        public static int calculatePotionWorth(Potion potion) {
            int score = 0;

            for (MobEffectInstance effect : potion.getEffects()) {
                int amplifier = effect.getAmplifier() + 1; // Amplifier starts at 0, so add 1
                int durationSeconds = effect.getDuration() / 20; // Convert ticks to seconds
                boolean isBeneficial = effect.getEffect().isBeneficial();

                // Base value: Duration in seconds + amplifier bonus
                int effectScore = durationSeconds + (amplifier * 100);

                // Beneficial effects are worth more
                if (isBeneficial) {
                    effectScore *= 2; // Double value for good effects
                } else {
                    effectScore /= 2; // Half value for negative effects
                }

                score += effectScore;
            }

            // Bonus if it has multiple effects
            if (potion.getEffects().size() > 1) {
                score += 50;
            }

            return score;
        }
        public static ServerTradingBotFactory.BotBuilderContainer getBotBuilder(ItemID itemID)
        {
            return getBotBuilder().get(itemID);
        }
    }

}
