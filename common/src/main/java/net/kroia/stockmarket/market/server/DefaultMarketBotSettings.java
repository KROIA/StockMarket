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
    public static class MinimalMarketData
    {
        public Item item;
        public int defaultPrice;
        public float rarity;
        public float volatility;
        public MinimalMarketData(Item item, int defaultPrice, float rarity, float volatility)
        {
            this.item = item;
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
    }

    public static class MinimalMarketDataCategory
    {
        public final String categoryName;
        public final ArrayList<MinimalMarketData> items;

        public MinimalMarketDataCategory(String categoryName, ArrayList<MinimalMarketData> items) {
            this.categoryName = categoryName;
            this.items = items;
        }
        public MinimalMarketDataCategory(String categoryName) {
            this.categoryName = categoryName;
            this.items = new ArrayList<>();
        }
    }

    // Basic Block prices
    private static final int LOG_PRICE = 20;
    private static final int PLANK_PRICE = Math.max(1,LOG_PRICE/4);
    private static final int STICK_PRICE = Math.max(1,LOG_PRICE/8);
    private static final int STONE_PRICE = 20;
    private static final int SAND_PRICE = 20;
    private static final int GRAVEL_PRICE = 20;
    private static final int CLAY_BALL_PRICE = 5;
    private static final int WOOL_PRICE = 5;


    // Ore prices
    private static final int COAL_PRICE = 8;
    private static final int IRON_PRICE = 30;
    private static final int COPPER_PRICE = 20;
    private static final int GOLD_PRICE = 100;
    private static final int DIAMOND_PRICE = 200;
    private static final int EMERALD_PRICE = 300;
    private static final int LAPIS_LAZULI_PRICE = 50;
    private static final int ANCIENT_DEBRIS_PRICE = 500;
    private static final int NETHERITE_SCRAP_PRICE = ANCIENT_DEBRIS_PRICE;
    private static final int REDSTONE_DUST_PRICE = 10;
    private static final int NETHER_QUARTZ = 10;


    // Plants
    private static final int BAMBOO_PRICE = 2;



    // Misc
    private static final int PRISMARINE_SHARD_PRICE = 10;
    private static final int CHORUS_FRUIT_PRICE = 10;
    private static final int HONEYCOMB_PRICE = 10;
    private static final int DYE_PRICE = 1;



    public static void createDefaultMarketBotSettings() {
        long updateMS = 500;

        StockMarketMod.LOGGER.error("Generating new default bot settings.");

        List<Item> allItems = BuiltInRegistries.ITEM.stream().toList();

        // Create defaults:
        createAndSaveSettings(getOres(), updateMS);
        createAndSaveSettings(getOreBlocks(), updateMS);
        createAndSaveSettings(getLogs(), updateMS);
        createAndSaveSettings(getPlanks(), updateMS);
        createAndSaveSettings(getFences(), updateMS);
        createAndSaveSettings(getDoors(), updateMS);
        createAndSaveSettings(getStairs(), updateMS);
        createAndSaveSettings(getSlaps(), updateMS);
        createAndSaveSettings(getWalls(), updateMS);
        createAndSaveSettings(getTrapDoors(), updateMS);
        createAndSaveSettings(getPressurePlates(), updateMS);
        createAndSaveSettings(getSand(), updateMS);
        //createAndSaveSettings("Clay", getClay(), updateMS);
        createAndSaveSettings(getWool(), updateMS);
        createAndSaveSettings(getCarpet(), updateMS);
        createAndSaveSettings(getTerracotta(), updateMS);
        createAndSaveSettings(getGlazedTerracotta(), updateMS);
        createAndSaveSettings(getConcrete(), updateMS);
        createAndSaveSettings(getConcretePowder(), updateMS);
        //createAndSaveSettings("MiscBlocks", getMiscBlocks(), updateMS);
        //createAndSaveSettings("Misc", getMisc(), updateMS);
        //createAndSaveSettings("Food", getFood(), updateMS);
        //createAndSaveSettings("Dyes", getDyes(), updateMS);
        //createAndSaveSettings("Plants", getPlants(), updateMS);
        //createAndSaveSettings("MiscItems", getMiscItems(), updateMS);




        /*
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

*/

    }





    private static void createAndSaveSettings(MinimalMarketDataCategory category, long updateTimerIntervallMS)
    {
        ArrayList<ServerTradingBotFactory.BotBuilderContainer> container = new ArrayList<>();
        createBotSettings(container, category.items, updateTimerIntervallMS);
        StockMarketDataHandler.saveDefaultBotSettings(container, category.categoryName+".json");
    }
    private static void createBotSettings(ArrayList<ServerTradingBotFactory.BotBuilderContainer> container, ArrayList<MinimalMarketData> category, long updateTimerIntervallMS)
    {
        for (MinimalMarketData data : category) {
            ServerTradingBotFactory.DefaultBotSettings settings = new ServerTradingBotFactory.DefaultBotSettings(data.defaultPrice, data.rarity, data.volatility, updateTimerIntervallMS);
            ServerTradingBotFactory.ItemData itemData = new ServerTradingBotFactory.ItemData(ItemUtilities.getItemID(data.item));
            ServerTradingBotFactory.BotBuilderContainer botContainer = new ServerTradingBotFactory.BotBuilderContainer();
            botContainer.itemData = itemData;
            botContainer.defaultSettings = settings;
            container.add(botContainer);
        }
    }
/*
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
    }*/


    private static MinimalMarketDataCategory getLogs()
    {
        MinimalMarketDataCategory logsCategory = new MinimalMarketDataCategory("Logs");
        logsCategory.items.add(new MinimalMarketData(Items.OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.SPRUCE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.BIRCH_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.JUNGLE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.ACACIA_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.CHERRY_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.DARK_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.MANGROVE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.CRIMSON_STEM, LOG_PRICE, 0.01f, 0.1f));

        // Stripped variants
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_SPRUCE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_BIRCH_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_JUNGLE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_ACACIA_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_CHERRY_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_DARK_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_MANGROVE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.items.add(new MinimalMarketData(Items.STRIPPED_CRIMSON_STEM, LOG_PRICE, 0.01f, 0.1f));

        return logsCategory;
    }
    private static MinimalMarketDataCategory getPlanks()
    {
        MinimalMarketDataCategory planksCategory = new MinimalMarketDataCategory("Planks");
        int plankPrice = PLANK_PRICE;
        planksCategory.items.add(new MinimalMarketData(Items.OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.SPRUCE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.BIRCH_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.JUNGLE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.ACACIA_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.CHERRY_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.DARK_OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.MANGROVE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.BAMBOO_PLANKS, Math.min(BAMBOO_PRICE*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.BAMBOO_MOSAIC, Math.min(BAMBOO_PRICE*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.CRIMSON_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.items.add(new MinimalMarketData(Items.WARPED_PLANKS, plankPrice, 0.01f, 0.1f));
        return planksCategory;
    }
    private static MinimalMarketDataCategory getFences()
    {
        MinimalMarketDataCategory fencesCategory = new MinimalMarketDataCategory("Fences");
        int fencePrice = (PLANK_PRICE*4 + STICK_PRICE*2)/3;
        int fenceDoorPrice = PLANK_PRICE*2+STICK_PRICE*4;
        fencesCategory.items.add(new MinimalMarketData(Items.OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.SPRUCE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.BIRCH_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.JUNGLE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.ACACIA_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.DARK_OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.MANGROVE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.BAMBOO_FENCE, Math.min((BAMBOO_PRICE*9*2+4*STICK_PRICE)/3, fencePrice), 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.CRIMSON_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.WARPED_FENCE, fencePrice, 0.01f, 0.1f));

        fencesCategory.items.add(new MinimalMarketData(Items.OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.SPRUCE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.BIRCH_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.JUNGLE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.ACACIA_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.DARK_OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.MANGROVE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.BAMBOO_FENCE_GATE, Math.min(BAMBOO_PRICE*9+4*STICK_PRICE, fenceDoorPrice), 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.CRIMSON_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.items.add(new MinimalMarketData(Items.WARPED_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        return fencesCategory;
    }
    private static MinimalMarketDataCategory getDoors()
    {
        MinimalMarketDataCategory doorsCategory = new MinimalMarketDataCategory("Doors");
        int doorPrice = (PLANK_PRICE*6)/3;
        doorsCategory.items.add(new MinimalMarketData(Items.IRON_DOOR, IRON_PRICE *2, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.SPRUCE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.BIRCH_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.JUNGLE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.ACACIA_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.CHERRY_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.DARK_OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.MANGROVE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.BAMBOO_DOOR, Math.min(BAMBOO_PRICE*9,doorPrice), 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.CRIMSON_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.items.add(new MinimalMarketData(Items.WARPED_DOOR, doorPrice, 0.01f, 0.1f));
        return doorsCategory;
    }
    private static MinimalMarketDataCategory getTrapDoors()
    {
        MinimalMarketDataCategory trapDoorsCategory = new MinimalMarketDataCategory("TrapDoors");
        int trapDoorPrice = (PLANK_PRICE*6)/2;
        trapDoorsCategory.items.add(new MinimalMarketData(Items.IRON_TRAPDOOR, IRON_PRICE *4, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.SPRUCE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.BIRCH_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.JUNGLE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.ACACIA_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.CHERRY_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.DARK_OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.MANGROVE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.BAMBOO_TRAPDOOR, Math.min(BAMBOO_PRICE*9*3/2,trapDoorPrice), 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.CRIMSON_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.items.add(new MinimalMarketData(Items.WARPED_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        return trapDoorsCategory;
    }
    private static MinimalMarketDataCategory getPressurePlates()
    {
        MinimalMarketDataCategory pressurePlatesCategory = new MinimalMarketDataCategory("PressurePlates");
        int pressurePlatePrice = PLANK_PRICE*2;
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.SPRUCE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.BIRCH_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.JUNGLE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.ACACIA_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.CHERRY_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.DARK_OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.MANGROVE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.BAMBOO_PRESSURE_PLATE, Math.min(BAMBOO_PRICE*9,pressurePlatePrice), 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.CRIMSON_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.WARPED_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));

        pressurePlatesCategory.items.add(new MinimalMarketData(Items.STONE_PRESSURE_PLATE, STONE_PRICE*2, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, IRON_PRICE *2, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, GOLD_PRICE *2, 0.01f, 0.1f));
        pressurePlatesCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, GOLD_PRICE *2, 0.01f, 0.1f));

        return pressurePlatesCategory;
    }
    private static MinimalMarketDataCategory getStairs()
    {
        MinimalMarketDataCategory stairsCategory = new MinimalMarketDataCategory("Stairs");
        int woodStairsPrice = PLANK_PRICE*6/4;
        int stoneStairsPrice = STONE_PRICE;
        stairsCategory.items.add(new MinimalMarketData(Items.OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.SPRUCE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.BIRCH_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.JUNGLE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.ACACIA_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.CHERRY_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.DARK_OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.MANGROVE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.BAMBOO_STAIRS, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.BAMBOO_MOSAIC_STAIRS, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.CRIMSON_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WARPED_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.STONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.COBBLESTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.MOSSY_COBBLESTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.MOSSY_STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.GRANITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_GRANITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.DIORITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_DIORITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.ANDESITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_ANDESITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.COBBLED_DEEPSLATE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_DEEPSLATE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_TILE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.BRICK_STAIRS, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.MUD_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.SMOOTH_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.RED_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.SMOOTH_RED_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.PRISMARINE_STAIRS, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.PRISMARINE_BRICK_STAIRS, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.DARK_PRISMARINE_STAIRS, PRISMARINE_SHARD_PRICE*8, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.RED_NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.END_STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.PURPUR_STAIRS, CHORUS_FRUIT_PRICE*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.QUARTZ_STAIRS, NETHER_QUARTZ*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.SMOOTH_QUARTZ_STAIRS, NETHER_QUARTZ*4, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.EXPOSED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WEATHERED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.OXIDIZED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WAXED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WAXED_EXPOSED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.items.add(new MinimalMarketData(Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        return stairsCategory;
    }
    private static MinimalMarketDataCategory getSlaps()
    {
        MinimalMarketDataCategory slapsCategory = new MinimalMarketDataCategory("Slabs");
        int woodStairsPrice = PLANK_PRICE*6/4;
        int stoneStairsPrice = STONE_PRICE;
        slapsCategory.items.add(new MinimalMarketData(Items.OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SPRUCE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.BIRCH_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.JUNGLE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.ACACIA_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.CHERRY_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.DARK_OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.MANGROVE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.BAMBOO_SLAB, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.BAMBOO_MOSAIC_SLAB, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.CRIMSON_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WARPED_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.STONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.COBBLESTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.MOSSY_COBBLESTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SMOOTH_STONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.MOSSY_STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.GRANITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_GRANITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.DIORITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_DIORITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.ANDESITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_ANDESITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.COBBLED_DEEPSLATE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_DEEPSLATE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_TILE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.BRICK_SLAB, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.MUD_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SMOOTH_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.CUT_STANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.CUT_RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SMOOTH_RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.PRISMARINE_SLAB, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.PRISMARINE_BRICK_SLAB, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.DARK_PRISMARINE_SLAB, PRISMARINE_SHARD_PRICE*8, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.RED_NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.END_STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.PURPUR_SLAB, CHORUS_FRUIT_PRICE*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.QUARTZ_SLAB, NETHER_QUARTZ*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.SMOOTH_QUARTZ_SLAB, NETHER_QUARTZ*4, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.EXPOSED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WEATHERED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.OXIDIZED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WAXED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WAXED_EXPOSED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WAXED_WEATHERED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.items.add(new MinimalMarketData(Items.WAXED_OXIDIZED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        return slapsCategory;
    }
    private static MinimalMarketDataCategory getWalls()
    {
        MinimalMarketDataCategory wallsCategory = new MinimalMarketDataCategory("Walls");
        int wallPrice = STONE_PRICE;
        wallsCategory.items.add(new MinimalMarketData(Items.COBBLESTONE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.MOSSY_COBBLESTONE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.STONE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.MOSSY_STONE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.GRANITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.DIORITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.ANDESITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.COBBLED_DEEPSLATE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.DEEPSLATE_TILE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.POLISHED_DEEPSLATE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.BRICK_WALL, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.MUD_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.SANDSTONE_WALL, SAND_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.RED_SANDSTONE_WALL, SAND_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.PRISMARINE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.NETHER_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.RED_NETHER_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.BLACKSTONE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.POLISHED_BLACKSTONE_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.items.add(new MinimalMarketData(Items.END_STONE_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        return wallsCategory;
    }
    private static MinimalMarketDataCategory getOres()
    {
        MinimalMarketDataCategory oresCategory = new MinimalMarketDataCategory("Ores");
        oresCategory.items.add(new MinimalMarketData(Items.COAL, COAL_PRICE, 0.02f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.IRON_INGOT, IRON_PRICE, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.COPPER_INGOT, COPPER_PRICE, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.GOLD_INGOT, GOLD_PRICE, 0.05f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.REDSTONE, REDSTONE_DUST_PRICE, 0.08f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.LAPIS_LAZULI, LAPIS_LAZULI_PRICE, 0.08f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.DIAMOND, DIAMOND_PRICE, 0.1f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.EMERALD, EMERALD_PRICE, 0.2f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.QUARTZ, NETHER_QUARTZ, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.ANCIENT_DEBRIS, ANCIENT_DEBRIS_PRICE, 0.5f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.NETHERITE_INGOT, ANCIENT_DEBRIS_PRICE, 0.5f, 0.1f));
        return oresCategory;
    }
    private static MinimalMarketDataCategory getOreBlocks()
    {
        MinimalMarketDataCategory oresCategory = new MinimalMarketDataCategory("OreBlocks");
        oresCategory.items.add(new MinimalMarketData(Items.COAL_BLOCK, COAL_PRICE*9, 0.02f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.IRON_BLOCK, IRON_PRICE*9, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.COPPER_BLOCK, COPPER_PRICE*9, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.GOLD_BLOCK, GOLD_PRICE*9, 0.05f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.REDSTONE_BLOCK, REDSTONE_DUST_PRICE*9, 0.08f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.LAPIS_BLOCK, LAPIS_LAZULI_PRICE*9, 0.08f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.DIAMOND_BLOCK, DIAMOND_PRICE*9, 0.1f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.EMERALD_BLOCK, EMERALD_PRICE*9, 0.2f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.QUARTZ_BLOCK, NETHER_QUARTZ*9, 0.03f, 0.1f));
        oresCategory.items.add(new MinimalMarketData(Items.NETHERITE_BLOCK, ANCIENT_DEBRIS_PRICE*9, 0.5f, 0.1f));
        return oresCategory;
    }
    private static MinimalMarketDataCategory getWool()
    {
        MinimalMarketDataCategory woolCategory = new MinimalMarketDataCategory("Wool");
        woolCategory.items.add(new MinimalMarketData(Items.WHITE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.ORANGE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.MAGENTA_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.LIGHT_BLUE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.YELLOW_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.LIME_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.PINK_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.GRAY_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.LIGHT_GRAY_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.CYAN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.PURPLE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.BLUE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.BROWN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.GREEN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.RED_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.items.add(new MinimalMarketData(Items.BLACK_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        return woolCategory;
    }
    private static MinimalMarketDataCategory getCarpet()
    {
        MinimalMarketDataCategory carpetCategory = new MinimalMarketDataCategory("Carpet");
        int carpetPrice = Math.max(1, WOOL_PRICE*2/3);
        carpetCategory.items.add(new MinimalMarketData(Items.WHITE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.ORANGE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.MAGENTA_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.LIGHT_BLUE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.YELLOW_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.LIME_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.PINK_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.GRAY_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.LIGHT_GRAY_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.CYAN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.PURPLE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.BLUE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.BROWN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.GREEN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.RED_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.items.add(new MinimalMarketData(Items.BLACK_CARPET, carpetPrice, 0.01f, 0.1f));
        return carpetCategory;
    }
    private static MinimalMarketDataCategory getTerracotta()
    {
        MinimalMarketDataCategory terracotta = new MinimalMarketDataCategory("Terracotta");
        int terraCottaPrice = CLAY_BALL_PRICE*4;
        terracotta.items.add(new MinimalMarketData(Items.WHITE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.ORANGE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.MAGENTA_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIGHT_BLUE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.YELLOW_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIME_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.PINK_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.GRAY_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIGHT_GRAY_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.CYAN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.PURPLE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BLUE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BROWN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.GREEN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.RED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BLACK_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        return terracotta;
    }

    private static MinimalMarketDataCategory getGlazedTerracotta()
    {
        MinimalMarketDataCategory terracotta = new MinimalMarketDataCategory("GlazedTerracotta");
        int terraCottaPrice = CLAY_BALL_PRICE*4;
        terracotta.items.add(new MinimalMarketData(Items.WHITE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.ORANGE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.MAGENTA_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIGHT_BLUE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.YELLOW_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIME_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.PINK_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.GRAY_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.LIGHT_GRAY_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.CYAN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.PURPLE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BLUE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BROWN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.GREEN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.RED_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.items.add(new MinimalMarketData(Items.BLACK_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        return terracotta;
    }
    private static MinimalMarketDataCategory getConcrete()
    {
        MinimalMarketDataCategory concrete = new MinimalMarketDataCategory("Concrete");
        int concretePrice = GRAVEL_PRICE*4+ SAND_PRICE*4+ DYE_PRICE;
        concrete.items.add(new MinimalMarketData(Items.WHITE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.ORANGE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.MAGENTA_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.LIGHT_BLUE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.YELLOW_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.LIME_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.PINK_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.GRAY_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.LIGHT_GRAY_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.CYAN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.PURPLE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.BLUE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.BROWN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.GREEN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.RED_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.items.add(new MinimalMarketData(Items.BLACK_CONCRETE, concretePrice, 0.02f, 0.1f));
        return concrete;
    }
    private static MinimalMarketDataCategory getConcretePowder()
    {
        MinimalMarketDataCategory concretePowder = new MinimalMarketDataCategory("ConcretePowder");
        int concretePowderPrice = GRAVEL_PRICE*4+ SAND_PRICE*4+ DYE_PRICE;
        concretePowder.items.add(new MinimalMarketData(Items.WHITE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.ORANGE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.MAGENTA_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.LIGHT_BLUE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.YELLOW_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.LIME_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.PINK_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.GRAY_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.LIGHT_GRAY_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.CYAN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.PURPLE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.BLUE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.BROWN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.GREEN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.RED_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.items.add(new MinimalMarketData(Items.BLACK_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        return concretePowder;
    }


    private static MinimalMarketDataCategory getSand()
    {
        MinimalMarketDataCategory sandCategory = new MinimalMarketDataCategory("Sand");
        int sandPrice = SAND_PRICE;
        sandCategory.items.add(new MinimalMarketData(Items.SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.items.add(new MinimalMarketData(Items.RED_SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.items.add(new MinimalMarketData(Items.GRAVEL, sandPrice, 0.02f, 0.1f));
        return sandCategory;
    }



    // Glass blocks
    // Glass panes
    // Dirt/obsidian/gravel/sand/soulSand
    // Arrows
    // Potions
    // Slpash potions
    // Enchanted books
    // Foods
    // dyes





}
