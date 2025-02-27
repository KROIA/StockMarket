package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ItemUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultMarketBotSettings {

    public static void createDefaultMarketBotSettings() {
        float priceScale = 1f;
        long updateMS = 500;
        float volatility = 0.2f;

        StockMarketMod.LOGGER.error("Generating new default bot settings.");

        List<Item> allItems = BuiltInRegistries.ITEM.stream().toList();

        // Create defaults:
        // Ores
        ArrayList<ServerTradingBotFactory.BotBuilderContainer> ores = new ArrayList<>();
        createBotSettingsForItemNameContains(ores, allItems, "coal", (int)(8*priceScale), 0.01f, 0.1f, updateMS);
        createBotSettingsForItemNameContains(ores, allItems, "amethyst", (int)(8*priceScale), 0.01f, 0.1f, updateMS);
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
        createBotSetting(buildingBlocks, "minecraft:dirt", (int)(3*priceScale), 0.01f, 0.05f, updateMS);
        createBotSetting(buildingBlocks, "minecraft:gravel", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        createBotSetting(buildingBlocks, "minecraft:obsidian", (int)(20*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "MiscBlocks.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "log", (int)(10*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Logs.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "planks", (int)(3*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Planks.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "sand", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Sand.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "stone", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Stone.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "glass", (int)(6*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Glass.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "brick", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Bricks.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "wool", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Wool.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "terracotta", (int)(8*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Terracotta.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "concrete", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Concrete.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "fence", (int)(5*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Fences.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "slab", (int)(3*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Slaps.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "stair", (int)(4*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Stairs.json");
        buildingBlocks.clear();
        createBotSettingsForItemNameContains(buildingBlocks, allItems, "wall", (int)(4*priceScale), 0.01f, 0.1f, updateMS);
        StockMarketDataHandler.saveDefaultBotSettings(buildingBlocks, "Walls.json");
        buildingBlocks.clear();



        // Foods
        ArrayList<ServerTradingBotFactory.BotBuilderContainer> food = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.isEdible()) {  // Check if the item has food properties
                ItemStack stack = new ItemStack(item);
                int rarityMultiplyer = 0;
                Rarity rarity = item.getRarity(stack);
                switch(rarity)
                {
                    case COMMON:
                        rarityMultiplyer = 1;
                        break;
                    case UNCOMMON:
                        rarityMultiplyer = 2;
                        break;
                    case RARE:
                        rarityMultiplyer = 3;
                        break;
                    case EPIC:
                        rarityMultiplyer = 4;
                        break;
                }
                FoodProperties foodProperties = item.getFoodProperties();
                int price = (int)(2*foodProperties.getNutrition()*foodProperties.getSaturationModifier()*rarityMultiplyer);
                createBotSetting(food, stack, Math.max(5,price), 0.01f, volatility*0.5f, updateMS);
            }
        }
        StockMarketDataHandler.saveDefaultBotSettings(food, "Food.json");

        // misc
        ArrayList<ServerTradingBotFactory.BotBuilderContainer> misc = new ArrayList<>();
        createBotSetting(misc, "minecraft:bone", (int)(5*priceScale), 0.01f, volatility, updateMS);
        createBotSetting(misc, "minecraft:gunpowder", (int)(5*priceScale), 0.01f, volatility, updateMS);
        createBotSetting(misc, "minecraft:ender_pearl", (int)(50*priceScale), 0.01f, volatility, updateMS);
        createBotSetting(misc, "minecraft:blaze_rod", (int)(20*priceScale), 0.01f, volatility, updateMS);
        createBotSetting(misc, "minecraft:clay_ball", (int)(5*priceScale), 0.01f, volatility, updateMS);
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

    private static void createBotSettingsForItemNameContains(ArrayList<ServerTradingBotFactory.BotBuilderContainer> container, List<Item>allItemsToFilter,
                                                             String contains, int defaultPrice, float rarity, float volatility, long updateTimerIntervallMS)
    {
        for(Item item : allItemsToFilter)
        {
            String itemID = ItemUtilities.getItemID(item);
            if(itemID.contains(contains))
            {
                createBotSetting(container, new ItemStack(item), defaultPrice, rarity, volatility, updateTimerIntervallMS);
            }
        }
    }
    private static int calculatePotionWorth(Potion potion) {
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

}
