package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.setting.ModSettings;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.setting.SettingsGroup;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class DefaultMarketSettings {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }


    public static final class DefaultPrices extends ModSettings
    {
        public static class PriceGroup extends SettingsGroup
        {
            public static class Price extends Setting<Integer>
            {
                public Price(String settingName, int defaultValue) {
                    super(settingName, defaultValue, Integer.class);
                }
                public Price(String settingName, int defaultValue, Type type) {
                    super(settingName, defaultValue, type);
                }

                @Override
                public String toString()
                {
                    return getName() + ": " + get();
                }
            }
            public PriceGroup(String groupName) {
                super(groupName);
            }
            protected Price registerSetting(String settingName, int defaultValue) {
                Price setting = new Price(settingName, defaultValue);
                getAllSettings().add(setting);
                return setting;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(getName() + ":\n");
                for (Setting<?> setting : getAllSettings()) {
                    sb.append("  ").append(setting.toString()).append("\n");
                }
                return sb.toString();
            }
        }

        CommonBlock COMMON_BLOCK = createGroup(new CommonBlock());
        Ore ORE = createGroup(new Ore());
        Gardening GARDENING = createGroup(new Gardening());
        Food FOOD = createGroup(new Food());
        AnimalLoot ANIMAL_LOOT = createGroup(new AnimalLoot());
        Furniture FURNITURE = createGroup(new Furniture());
        Enchantment ENCHANTMENT = createGroup(new Enchantment());
        Potion POTION = createGroup(new Potion());
        Arrow ARROW = createGroup(new Arrow());
        Misc MISC = createGroup(new Misc());
        Dye DYE = createGroup(new Dye());
        // Add more groups as needed

        public DefaultPrices() {
            super("DefaultPrices");
        }

        public static final class CommonBlock extends PriceGroup
        {
            public final Price LOG = registerSetting("log", 8);
            public final Price PLANK = registerSetting("plank", Math.max(1, LOG.get()/4));
            //public final Price STICK = registerSetting("stick", Math.max(1, LOG.get()/8));
            public final Price STONE = registerSetting("stone", 2);
            public final Price COBBLESTONE = registerSetting("cobblestone", Math.max(1, STONE.get()/2)); // Cobblestone is generally cheaper than stone
            public final Price SAND = registerSetting("sand", 3);
            public final Price DIRT = registerSetting("dirt", 1); // Dirt is generally cheaper than sand
            public final Price GRAVEL = registerSetting("gravel", 3);

            public final Price WOOL = registerSetting("wool", 6);
            public final Price GLASS = registerSetting("glass", SAND.get());
            public final Price OBSIDIAN = registerSetting("obsidian", 12); // Obsidian is more expensive due to its rarity and mining difficulty
            public final Price NETHERRACK = registerSetting("netherrack", 1); // Obsidian is more expensive due to its rarity and mining difficulty

            public CommonBlock() {
                super("CommonBlock");
            }
        }

        public static final class Ore extends PriceGroup
        {
            // Ore prices

            public final Price COAL = registerSetting("coal", 8);
            public final Price IRON = registerSetting("iron", 15);
            public final Price COPPER = registerSetting("copper", 20);
            public final Price GOLD = registerSetting("gold", 40);
            public final Price DIAMOND = registerSetting("diamond", 160);
            public final Price EMERALD = registerSetting("emerald", 100);
            public final Price LAPIS_LAZULI = registerSetting("lapis_lazuli", 8);
            public final Price ANCIENT_DEBRIS = registerSetting("ancient_debris", 600);
            public final Price NETHERITE_SCRAP = registerSetting("netherite_scrap", ANCIENT_DEBRIS.get());
            public final Price REDSTONE_DUST = registerSetting("redstone_dust", 4);
            public final Price NETHER_QUARTZ = registerSetting("nether_quartz", 10);
            public final Price PRISMARINE_SHARD = registerSetting("prismarine_shard", 10);
            public final Price AMETHYST_SHARD = registerSetting("amethyst_shard", 10);



            public Ore() {
                super("Ore");
            }
        }

        public static final class Gardening extends PriceGroup
        {
            public final Price BAMBOO = registerSetting("bamboo", 2);
            public final Price CHORUS_FRUIT = registerSetting("chorus_fruit", 10);
            public final Price DYE = registerSetting("dye", 1);
            public final Price BEEHIVE = registerSetting("beehive", 20);
            public final Price BEE_NEST = registerSetting("bee_nest", 20);
            public final Price SAPLING = registerSetting("sapling", 5);
            public final Price PUMPKIN = registerSetting("pumpkin", 5);

            // Seeds
            public final Price SEED = registerSetting("seed", 2);
            public final Price POTATO = registerSetting("potato", 3);
            public final Price SUGAR_CANE = registerSetting("sugar_cane", 2);
            public final Price COCOA_BEANS = registerSetting("cocoa_beans", 2);
            public final Price WHEAT = registerSetting("wheat", 2);
            public final Price SWEET_BERRIES = registerSetting("sweet_berries", 2);
            public final Price BEETROOT = registerSetting("beetroot", 2);
            public final Price GLOW_BERRIES = registerSetting("glow_berries", 3);



            public Gardening() {
                super("Gardening");
            }
        }

        public static final class Food extends PriceGroup
        {
            // Cooked meets
            public final Price COOKED_BEEF = registerSetting("cooked_beef", 5);
            public final Price COOKED_CHICKEN = registerSetting("cooked_chicken", 5);
            public final Price COOKED_PORKCHOP = registerSetting("cooked_porkchop", 5);
            public final Price COOKED_MUTTON = registerSetting("cooked_mutton", 5);
            public final Price COOKED_RABBIT = registerSetting("cooked_rabbit", 5);
            public final Price COOKED_SALMON = registerSetting("cooked_salmon", 5);
            public final Price COOKED_COD = registerSetting("cooked_cod", 5);
            public final Price BREAD = registerSetting("bread", 6);
            public final Price COOKIE = registerSetting("cookie", 1);
            public final Price CAKE = registerSetting("cake", 10);
            public final Price PUMPKIN_PIE = registerSetting("pumpkin_pie", 10);
            public final Price MUSHROOM_STEW = registerSetting("mushroom_stew", 5);
            public final Price RABBIT_STEW = registerSetting("rabbit_stew", 5);
            public final Price SUSPICIOUS_STEW = registerSetting("suspicious_stew", 5);
            public final Price ENCHANTED_GOLDEN_APPLE = registerSetting("enchanted_golden_apple", 1000);
            public final Price APPLE = registerSetting("apple", 4);
            public final Price CARROT = registerSetting("carrot", 3);
            public final Price GOLDEN_APPLE = registerSetting("golden_apple", 350);
            public final Price GOLDEN_CARROT = registerSetting("golden_carrot", 10);
            public final Price BEETROOT_SOUP = registerSetting("beetroot_soup", 10);
            public final Price BAKED_POTATO = registerSetting("baked_potato", 4);
            public final Price SUGAR = registerSetting("sugar", 2);
            public final Price MELON_SLICE = registerSetting("melon_slice", 2);
            public final Price MUSHROOM = registerSetting("mushroom", 6);



            public Food() {
                super("Food");
            }
        }
        public static final class AnimalLoot extends PriceGroup
        {

            public final Price RABBIT_HIDE = registerSetting("rabbit_hide", 5);
            public final Price LEATHER = registerSetting("leather", RABBIT_HIDE.get()*4);
            public final Price BONE = registerSetting("bone", 6);
            public final Price FEATHER = registerSetting("feather", 4);
            public final Price STRING = registerSetting("string", 4);
            public final Price EGG = registerSetting("egg", 2);
            public final Price SLIME_BALL = registerSetting("slime_ball", 25);
            public final Price ENDER_PEARL = registerSetting("ender_pearl", 10);
            public final Price GHAST_TEAR = registerSetting("ghast_tear", 200);
            public final Price NETHER_STAR = registerSetting("nether_star", 2000);

            // Raw meets
            public final Price RAW_BEEF = registerSetting("raw_beef", 4);
            public final Price RAW_CHICKEN = registerSetting("raw_chicken", 4);
            public final Price RAW_PORKCHOP = registerSetting("raw_porkchop", 4);
            public final Price RAW_MUTTON = registerSetting("raw_mutton", 4);
            public final Price RAW_RABBIT = registerSetting("raw_rabbit", 4);
            public final Price RAW_SALMON = registerSetting("raw_salmon", 4);
            public final Price COD = registerSetting("cod", 4);
            public final Price TROPICAL_FISH = registerSetting("tropical_fish", 4);
            public final Price PUFFER_FISH = registerSetting("puffer_fish", 4);


            public final Price HONEYCOMB = registerSetting("honeycomb", 10);
            public final Price HONEY_BOTTLE = registerSetting("honey_bottle", 10);
            public final Price INK_SAC = registerSetting("ink_sac", 10);
            public final Price SPIDER_EYE = registerSetting("spider_eye", 10);






            public AnimalLoot() {
                super("AnimalLoot");
            }
        }
        public static final class Furniture extends PriceGroup
        {
            /*

            public final Price BED = registerSetting("bed", 20);
            public final Price CHEST = registerSetting("chest", 20);
            public final Price BARREL = registerSetting("barrel", 20);
            public final Price FURNACE = registerSetting("furnace", 20);
            public final Price SMOKER = registerSetting("smoker", 20);
            public final Price BLAST_FURNACE = registerSetting("blast_furnace", 20);
            public final Price CRAFTING_TABLE = registerSetting("crafting_table", 20);
            public final Price ANVIL = registerSetting("anvil", 50);
            public final Price ENCHANTING_TABLE = registerSetting("enchanting_table", 50);
            public final Price BREWING_STAND = registerSetting("brewing_stand", 20);
            public final Price ITEM_FRAME = registerSetting("item_frame", 10);
            public final Price PAINTING = registerSetting("painting", 10);
            public final Price SIGN = registerSetting("sign", 5);
            public final Price BANNER = registerSetting("banner", 10);
            public final Price BOOKSHELF = registerSetting("bookshelf", 20);
            public final Price JUKBOX = registerSetting("jukebox", 50);
            public final Price FLOWER_POT = registerSetting("flower_pot", 5);
            public final Price ARMOR_STAND = registerSetting("armor_stand", 10);
            public final Price COMPOSTER = registerSetting("composter", 5);
            public final Price LECTERN = registerSetting("lectern", 20);
            public final Price SMOKER_BLOCK = registerSetting("smoker_block", 20);
            public final Price CARTOGRAPHY_TABLE = registerSetting("cartography_table", 20);
            public final Price GRINDSTONE = registerSetting("grindstone", 20);
            public final Price LOOM = registerSetting("loom", 20);
            public final Price STONECUTTER = registerSetting("stonecutter", 20);

            */

            public Furniture() {
                super("Furniture");
            }
        }
        public static final class Enchantment extends PriceGroup
        {
            public final Setting<Float> ENCHANTMENT_FACTOR = registerSetting("enchantment_factor", 10.f, Float.class);
            /*public final Price SHARPNESS = registerSetting("sharpness", 30);
            public final Price PROTECTION = registerSetting("protection", 30);
            public final Price EFFICIENCY = registerSetting("efficiency", 20);
            public final Price UNBREAKING = registerSetting("unbreaking", 20);
            public final Price FORTUNE = registerSetting("fortune", 50);
            public final Price LOOTING = registerSetting("looting", 50);
            public final Price POWER = registerSetting("power", 30);
            public final Price FLAME = registerSetting("flame", 30);
            public final Price FORTIFY = registerSetting("fortify", 30);
            public final Price PROTECTION_FALL = registerSetting("protection_fall", 30);
            public final Price THORNS = registerSetting("thorns", 50);
            public final Price AQUA_AFFINITY = registerSetting("aqua_affinity", 20);
            public final Price RESPIRATION = registerSetting("respiration", 20);
            public final Price DEPTH_STRIDER = registerSetting("depth_strider", 20);
            public final Price SOUL_SPEED = registerSetting("soul_speed", 20);
            public final Price SWIFT_SNEAK = registerSetting("swift_sneak", 20);
            public final Price MENDING = registerSetting("mending", 100); // Mending is generally more expensive due to its utility
            public final Price CURSE_OF_BINDING = registerSetting("curse_of_binding", 50);
            public final Price CURSE_OF_VANISHING = registerSetting("curse_of_vanishing", 50);
            public final Price SILK_TOUCH = registerSetting("silk_touch", 50);
            public final Price BANE_OF_ARTHROPODS = registerSetting("bane_of_arthropods", 20);
            public final Price LOYALTY = registerSetting("loyalty", 20);
            public final Price IMPALING = registerSetting("impaling", 30);
            public final Price RIPTIDE = registerSetting("riptide", 30);
            public final Price CHANNELING = registerSetting("channeling", 50);
            public final Price QUICK_CHARGE = registerSetting("quick_charge", 20);
            public final Price PIERCING = registerSetting("piercing", 20);
            public final Price MULTISHOT = registerSetting("multishot", 20);
            public final Price FLAME_ARROW = registerSetting("flame_arrow", 30);
            public final Price CURSE_OF_SHATTERING = registerSetting("curse_of_shattering", 50);
            public final Price CURSE_OF_WEAKNESS = registerSetting("curse_of_weakness", 50);*/

            public Enchantment() {
                super("Enchantment");
            }
        }
        public static final class Potion extends PriceGroup
        {
            public final Setting<Float> POTION_FACTOR = registerSetting("potion_factor", 1.f, Float.class);
            /*public final Price WEAKNESS = registerSetting("weakness", 30);
            public final Price STRENGTH = registerSetting("strength", 30);
            public final Price REGENERATION = registerSetting("regeneration", 30);
            public final Price SPEED = registerSetting("speed", 20);
            public final Price SLOWNESS = registerSetting("slowness", 20);
            public final Price JUMP_BOOST = registerSetting("jump_boost", 20);
            public final Price WATER_BREATHING = registerSetting("water_breathing", 20);
            public final Price FIRE_RESISTANCE = registerSetting("fire_resistance", 30);
            public final Price POISON = registerSetting("poison", 30);
            public final Price NIGHT_VISION = registerSetting("night_vision", 20);
            public final Price HARMING = registerSetting("harming", 30);
            public final Price SLOW_FALLING = registerSetting("slow_falling", 20);*/


            public Potion() {
                super("Potion");
            }
        }
        public static final class Arrow extends PriceGroup
        {
            public final Setting<Float> POTION_FACTOR = registerSetting("potion_factor", 1.f, Float.class);
            /*public final Price WEAKNESS = registerSetting("weakness", 30);
            public final Price STRENGTH = registerSetting("strength", 30);
            public final Price REGENERATION = registerSetting("regeneration", 30);
            public final Price SPEED = registerSetting("speed", 20);
            public final Price SLOWNESS = registerSetting("slowness", 20);
            public final Price JUMP_BOOST = registerSetting("jump_boost", 20);
            public final Price WATER_BREATHING = registerSetting("water_breathing", 20);
            public final Price FIRE_RESISTANCE = registerSetting("fire_resistance", 30);
            public final Price POISON = registerSetting("poison", 30);
            public final Price NIGHT_VISION = registerSetting("night_vision", 20);
            public final Price HARMING = registerSetting("harming", 30);
            public final Price SLOW_FALLING = registerSetting("slow_falling", 20);*/


            public Arrow() {
                super("Arrow");
            }
        }

        public static final class Misc extends PriceGroup
        {
            public final Price BOOK = registerSetting("book", 10);


            public final Price FLINT = registerSetting("flint", 10);
            public final Price BLAZE_POWDER = registerSetting("blaze_powder", 100);

            public final Price GLOWSTONE_DUST = registerSetting("glowstone_dust", 10);
            public final Price STRING = registerSetting("string", 10);
            public final Price CLAY_BALL = registerSetting("clay_ball", 3);
            public final Price CLAY_BRICKETTE = registerSetting("clay_brickette", 4);
            public final Price NETHER_BRICK = registerSetting("nether_brick", 1); // Obsidian is more expensive due to its rarity and mining difficulty





            public Misc() {
                super("Misc");
            }
        }

        public static final class Dye extends PriceGroup
        {
            public final Price DYE = registerSetting("dye", 10);

            public Dye() {
                super("Dye");
            }
        }


        /**
         * Uses the knowledge from the crafting recipes to set the prices for items that can be crafted
         * out of other items which also have a price
         */
        public void balancePrices()
        {


            FOOD.GOLDEN_APPLE.set(FOOD.APPLE.get() + ORE.GOLD.get() * 8);
            FOOD.GOLDEN_CARROT.set(FOOD.CARROT.get() + ORE.GOLD.get());
        }
    }

    public static DefaultPrices getDefaultPrices() {
        return BACKEND_INSTANCES.SERVER_DEFAULT_PRICES;
    }



/*










    private static float INFLATION_SCALE = 10;

    // Basic Block prices
    private static int LOG = 20;
    private static int PLANK = Math.max(1,LOG/4);
    private static int STICK = Math.max(1,LOG/8);
    private static int STONE = 20;
    private static int COBBLESTONE = Math.max(1,STONE/2); // Cobblestone is generally cheaper than stone
    private static int SAND = 20;
    private static int DIRT = 5; // Dirt is generally cheaper than sand
    private static int GRAVEL = 20;
    private static int CLAY_BALL = 5;
    private static int WOOL = 5;
    private static int GLASS = SAND;
    private static int OBSIDIAN = 100; // Obsidian is more expensive due to its rarity and mining difficulty


    // Ore prices
    private static int COAL = 8;
    private static int IRON = 30;
    private static int COPPER = 20;
    private static int GOLD = 100;
    private static int DIAMOND = 200;
    private static int EMERALD = 300;
    private static int LAPIS_LAZULI = 50;
    private static int ANCIENT_DEBRIS = 500;
    private static int NETHERITE_SCRAP = ANCIENT_DEBRIS;
    private static int REDSTONE_DUST = 10;
    private static int NETHER_QUARTZ = 10;


    // Plants
    private static int BAMBOO = 2;


    // Animals
    private static int RABBIT_HIDE = 5;
    private static int LEATHER = RABBIT_HIDE*4;




    // Misc
    private static int PRISMARINE_SHARD = 10;
    private static int CHORUS_FRUIT = 10;
    private static int HONEYCOMB = 10;
    private static int DYE = 1;
    private static int BOOK = 10;

    private static int ENCHANTMENT_BOOK_FACTOR = 10; // Base price for enchanted books
    private static int POTION_FACTOR = 10; // Base price for enchanted books
*/





    public static void createDefaultMarketSettingsIfNotExist() {

        //loadOrSaveBasePrices();

        // Create defaults:
        getOres().saveIfNotExists();
        getOreBlocks().saveIfNotExists();
        getLogs().saveIfNotExists();
        getPlanks().saveIfNotExists();
        getFences().saveIfNotExists();
        getDoors().saveIfNotExists();
        getStairs().saveIfNotExists();
        getSlaps().saveIfNotExists();
        getWalls().saveIfNotExists();
        getTrapDoors().saveIfNotExists();
        getPressurePlates().saveIfNotExists();
        getEnchantedBooks().saveIfNotExists();
        getWool().saveIfNotExists();
        getCarpet().saveIfNotExists();
        getTerracotta().saveIfNotExists();
        getGlazedTerracotta().saveIfNotExists();
        getConcrete().saveIfNotExists();
        getConcretePowder().saveIfNotExists();
        getFood().saveIfNotExists();
        getGlassBlocks().saveIfNotExists();
        getGlassPlanes().saveIfNotExists();
        getSplashPotions().saveIfNotExists();
        getPotions().saveIfNotExists();
        getLingeringPotions().saveIfNotExists();
        getTippedArrows().saveIfNotExists();
        getMiscBlocks().saveIfNotExists();
        getGardeningItems().saveIfNotExists();
        getAnimalLootItems().saveIfNotExists();
        getDyes().saveIfNotExists();
        getMiscItems().saveIfNotExists();




    }



    private static MarketFactory.DefaultMarketSetupDataGroup getLogs()
    {
        int logPrice = getDefaultPrices().COMMON_BLOCK.LOG.get();
        MarketFactory.DefaultMarketSetupDataGroup logsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Logs", Items.OAK_LOG);
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_STEM, logPrice, 0.01f, 0.1f));

        // Stripped variants
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_OAK_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_SPRUCE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_BIRCH_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_JUNGLE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_ACACIA_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_CHERRY_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_DARK_OAK_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_MANGROVE_LOG, logPrice, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_CRIMSON_STEM, logPrice, 0.01f, 0.1f));

        return logsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getPlanks()
    {
        MarketFactory.DefaultMarketSetupDataGroup planksCategory = new MarketFactory.DefaultMarketSetupDataGroup("Planks", Items.OAK_PLANKS);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_PLANKS, Math.min(bambooPrice*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC, Math.min(bambooPrice*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_PLANKS, plankPrice, 0.01f, 0.1f));
        return planksCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getFences()
    {
        MarketFactory.DefaultMarketSetupDataGroup fencesCategory = new MarketFactory.DefaultMarketSetupDataGroup("Fences", Items.OAK_FENCE);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int stickPrice = plankPrice/2;
        int fencePrice = (plankPrice*4 + stickPrice*2)/3;
        int fenceDoorPrice = plankPrice*2+stickPrice*4;
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_FENCE, Math.min((bambooPrice*9*2+4*stickPrice)/3, fencePrice), 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_FENCE, fencePrice, 0.01f, 0.1f));

        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_FENCE_GATE, Math.min(bambooPrice*9+4*stickPrice, fenceDoorPrice), 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        return fencesCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getDoors()
    {
        MarketFactory.DefaultMarketSetupDataGroup doorsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Doors", Items.OAK_DOOR);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        int ironPrice = getDefaultPrices().ORE.IRON.get();
        int doorPrice = (plankPrice*6)/3;
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_DOOR, ironPrice *2, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_DOOR, Math.min(bambooPrice*9,doorPrice), 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_DOOR, doorPrice, 0.01f, 0.1f));
        return doorsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getTrapDoors()
    {
        MarketFactory.DefaultMarketSetupDataGroup trapDoorsCategory = new MarketFactory.DefaultMarketSetupDataGroup("TrapDoors", Items.OAK_TRAPDOOR);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        int ironPrice = getDefaultPrices().ORE.IRON.get();
        int trapDoorPrice = (plankPrice*6)/2;
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_TRAPDOOR, ironPrice *4, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_TRAPDOOR, Math.min(bambooPrice*9*3/2,trapDoorPrice), 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        return trapDoorsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getPressurePlates()
    {
        MarketFactory.DefaultMarketSetupDataGroup pressurePlatesCategory = new MarketFactory.DefaultMarketSetupDataGroup("PressurePlates",Items.OAK_PRESSURE_PLATE);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        int stonePrice = getDefaultPrices().COMMON_BLOCK.STONE.get();
        int ironPrice = getDefaultPrices().ORE.IRON.get();
        int goldPrice = getDefaultPrices().ORE.GOLD.get();
        int pressurePlatePrice = plankPrice*2;
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_PRESSURE_PLATE, Math.min(bambooPrice*9,pressurePlatePrice), 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));

        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_PRESSURE_PLATE, stonePrice*2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, ironPrice *2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, goldPrice *2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, goldPrice *2, 0.01f, 0.1f));

        return pressurePlatesCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getStairs()
    {
        MarketFactory.DefaultMarketSetupDataGroup stairsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Stairs", Items.OAK_STAIRS);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        int stonePrice = getDefaultPrices().COMMON_BLOCK.STONE.get();
        int clayBrickettePrice = getDefaultPrices().MISC.CLAY_BRICKETTE.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();
        int prismarineShardPrice = getDefaultPrices().ORE.PRISMARINE_SHARD.get();
        int chorusFruitPrice = getDefaultPrices().GARDENING.CHORUS_FRUIT.get();
        int quartzPrice = getDefaultPrices().ORE.NETHER_QUARTZ.get();
        int copperPrice = getDefaultPrices().ORE.COPPER.get();
        int honeycombPrice = getDefaultPrices().ANIMAL_LOOT.HONEYCOMB.get();
        int woodStairsPrice = plankPrice*6/4;
        int stoneStairsPrice = stonePrice;
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_STAIRS, Math.min(bambooPrice*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC_STAIRS, Math.min(bambooPrice*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLESTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_COBBLESTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRANITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_GRANITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIORITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_DIORITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ANDESITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_ANDESITE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLED_DEEPSLATE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_DEEPSLATE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_TILE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_STAIRS, clayBrickettePrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_STAIRS, sandPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_SANDSTONE_STAIRS, sandPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_STAIRS, sandPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_RED_SANDSTONE_STAIRS, sandPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_STAIRS, prismarineShardPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_BRICK_STAIRS, prismarineShardPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_PRISMARINE_STAIRS, prismarineShardPrice*8, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPUR_STAIRS, chorusFruitPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_STAIRS, quartzPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_QUARTZ_STAIRS, quartzPrice*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_COPPER_STAIRS, copperPrice *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EXPOSED_CUT_COPPER_STAIRS, copperPrice *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WEATHERED_CUT_COPPER_STAIRS, copperPrice *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OXIDIZED_CUT_COPPER_STAIRS, copperPrice *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_CUT_COPPER_STAIRS, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_EXPOSED_CUT_COPPER_STAIRS, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        return stairsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getSlaps()
    {
        MarketFactory.DefaultMarketSetupDataGroup slapsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Slabs", Items.OAK_SLAB);
        int plankPrice = getDefaultPrices().COMMON_BLOCK.PLANK.get();
        int bambooPrice = getDefaultPrices().GARDENING.BAMBOO.get();
        int stonePrice = getDefaultPrices().COMMON_BLOCK.STONE.get();
        int clayBricklettePrice = getDefaultPrices().MISC.CLAY_BRICKETTE.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();
        int prismarineShardPrice = getDefaultPrices().ORE.PRISMARINE_SHARD.get();
        int chorusFruitPrice = getDefaultPrices().GARDENING.CHORUS_FRUIT.get();
        int quartzPrice = getDefaultPrices().ORE.NETHER_QUARTZ.get();
        int copperPrice = getDefaultPrices().ORE.COPPER.get();
        int honeycombPrice = getDefaultPrices().ANIMAL_LOOT.HONEYCOMB.get();
        int woodStairsPrice = plankPrice*6/4;
        int stoneStairsPrice = stonePrice;
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_SLAB, Math.min(bambooPrice*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC_SLAB, Math.min(bambooPrice*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLESTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_COBBLESTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_STONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRANITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_GRANITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIORITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_DIORITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ANDESITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_ANDESITE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLED_DEEPSLATE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_DEEPSLATE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_TILE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_SLAB, clayBricklettePrice*2, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_SANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_STANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_RED_SANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_RED_SANDSTONE_SLAB, sandPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_SLAB, prismarineShardPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_BRICK_SLAB, prismarineShardPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_PRISMARINE_SLAB, prismarineShardPrice*8, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPUR_SLAB, chorusFruitPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_SLAB, quartzPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_QUARTZ_SLAB, quartzPrice*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_COPPER_SLAB, copperPrice *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EXPOSED_CUT_COPPER_SLAB, copperPrice *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WEATHERED_CUT_COPPER_SLAB, copperPrice *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OXIDIZED_CUT_COPPER_SLAB, copperPrice *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_CUT_COPPER_SLAB, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_EXPOSED_CUT_COPPER_SLAB, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_WEATHERED_CUT_COPPER_SLAB, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_OXIDIZED_CUT_COPPER_SLAB, copperPrice *9+honeycombPrice, 0.01f, 0.1f));
        return slapsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getWalls()
    {
        MarketFactory.DefaultMarketSetupDataGroup wallsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Walls",Items.COBBLESTONE_WALL);
        int wallPrice = getDefaultPrices().COMMON_BLOCK.STONE.get();
        int clayBricklettePrice = getDefaultPrices().MISC.CLAY_BRICKETTE.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();
        int prismarineShardPrice = getDefaultPrices().ORE.PRISMARINE_SHARD.get();
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLESTONE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_COBBLESTONE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSSY_STONE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRANITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIORITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ANDESITE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLED_DEEPSLATE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEEPSLATE_TILE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_DEEPSLATE_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_WALL, clayBricklettePrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_WALL, sandPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_WALL, sandPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_WALL, prismarineShardPrice*4, 0.01f, 0.1f));
        return wallsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getOres()
    {
        var ORE = getDefaultPrices().ORE;
        MarketFactory.DefaultMarketSetupDataGroup oresCategory = new MarketFactory.DefaultMarketSetupDataGroup("Ores",Items.IRON_INGOT);
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COAL,              ORE.COAL.get(),           0.02f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_INGOT,        ORE.IRON.get(),           0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COPPER_INGOT,      ORE.COPPER.get(),         0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLD_INGOT,        ORE.GOLD.get(),           0.05f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.REDSTONE,          ORE.REDSTONE_DUST.get(),  0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LAPIS_LAZULI,      ORE.LAPIS_LAZULI.get(),   0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIAMOND,           ORE.DIAMOND.get(),        0.1f, 0.2f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EMERALD,           ORE.EMERALD.get(),        0.2f, 0.2f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ,            ORE.NETHER_QUARTZ.get(),        0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ANCIENT_DEBRIS,    ORE.ANCIENT_DEBRIS.get(), 0.5f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERITE_INGOT,   ORE.NETHERITE_SCRAP.get()*4+ORE.GOLD.get()*4, 0.5f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERITE_SCRAP,   ORE.NETHERITE_SCRAP.get(), 0.5f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.AMETHYST_SHARD,    ORE.AMETHYST_SHARD.get(), 0.5f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_SHARD,  ORE.PRISMARINE_SHARD.get(), 0.5f, 0.1f));
        return oresCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getOreBlocks()
    {
        var ORE = getDefaultPrices().ORE;
        MarketFactory.DefaultMarketSetupDataGroup oresCategory = new MarketFactory.DefaultMarketSetupDataGroup("OreBlocks",Items.IRON_BLOCK);
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COAL_BLOCK,            9*ORE.COAL.get(),             0.02f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_BLOCK,            9*ORE.IRON.get(),                  0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COPPER_BLOCK,          9*ORE.COPPER.get(),              0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLD_BLOCK,            9*ORE.GOLD.get(),                  0.05f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.REDSTONE_BLOCK,        9*ORE.REDSTONE_DUST.get(),     0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LAPIS_BLOCK,           9*ORE.LAPIS_LAZULI.get(),         0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIAMOND_BLOCK,         9*ORE.DIAMOND.get(),            0.1f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EMERALD_BLOCK,         9*ORE.EMERALD.get(),            0.2f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_BLOCK,          9*ORE.NETHER_QUARTZ.get(),             0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERITE_BLOCK,       9*ORE.NETHERITE_SCRAP.get()*4+ORE.GOLD.get()*4, 0.5f, 0.1f));
        return oresCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getWool()
    {
        int woolPrice = getDefaultPrices().COMMON_BLOCK.WOOL.get();
        MarketFactory.DefaultMarketSetupDataGroup woolCategory = new MarketFactory.DefaultMarketSetupDataGroup("Wool",Items.ORANGE_WOOL);
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_WOOL, woolPrice, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_WOOL, woolPrice, 0.01f, 0.1f));
        return woolCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getCarpet()
    {
        MarketFactory.DefaultMarketSetupDataGroup carpetCategory = new MarketFactory.DefaultMarketSetupDataGroup("Carpet",Items.ORANGE_CARPET);
        int woolPrice = getDefaultPrices().COMMON_BLOCK.WOOL.get();
        int carpetPrice = Math.max(1, woolPrice*2/3);
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_CARPET, carpetPrice, 0.01f, 0.1f));
        carpetCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_CARPET, carpetPrice, 0.01f, 0.1f));
        return carpetCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getTerracotta()
    {
        MarketFactory.DefaultMarketSetupDataGroup terracotta = new MarketFactory.DefaultMarketSetupDataGroup("Terracotta",Items.MAGENTA_TERRACOTTA);
        int terraCottaPrice = getDefaultPrices().MISC.CLAY_BALL.get()*4;
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        return terracotta;
    }

    private static MarketFactory.DefaultMarketSetupDataGroup getGlazedTerracotta()
    {
        MarketFactory.DefaultMarketSetupDataGroup terracotta = new MarketFactory.DefaultMarketSetupDataGroup("GlazedTerracotta",Items.MAGENTA_GLAZED_TERRACOTTA);
        int terraCottaPrice = getDefaultPrices().MISC.CLAY_BALL.get()*4;
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        terracotta.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_GLAZED_TERRACOTTA, terraCottaPrice, 0.02f, 0.1f));
        return terracotta;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getConcrete()
    {
        MarketFactory.DefaultMarketSetupDataGroup concrete = new MarketFactory.DefaultMarketSetupDataGroup("Concrete",Items.CYAN_CONCRETE);
        int gravelPrice = getDefaultPrices().COMMON_BLOCK.GRAVEL.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();
        int dyePrice = getDefaultPrices().GARDENING.DYE.get();
        int concretePrice = gravelPrice*4+ sandPrice*4+ dyePrice;
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_CONCRETE, concretePrice, 0.02f, 0.1f));
        concrete.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_CONCRETE, concretePrice, 0.02f, 0.1f));
        return concrete;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getConcretePowder()
    {
        MarketFactory.DefaultMarketSetupDataGroup concretePowder = new MarketFactory.DefaultMarketSetupDataGroup("ConcretePowder",Items.CYAN_CONCRETE_POWDER);
        int gravelPrice = getDefaultPrices().COMMON_BLOCK.GRAVEL.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();
        int dyePrice = getDefaultPrices().GARDENING.DYE.get();
        int concretePowderPrice = gravelPrice*4+ sandPrice*4+ dyePrice;
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        concretePowder.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_CONCRETE_POWDER, concretePowderPrice, 0.02f, 0.1f));
        return concretePowder;
    }


    /*private static MarketFactory.DefaultMarketSetupDataGroup getSand()
    {
        MarketFactory.DefaultMarketSetupDataGroup sandCategory = new MarketFactory.DefaultMarketSetupDataGroup("Sand");
        int sandPrice = SAND;
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAVEL, sandPrice, 0.02f, 0.1f));
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SOUL_SAND, sandPrice, 0.02f, 0.1f));
        return sandCategory;
    }*/

    public static MarketFactory.DefaultMarketSetupDataGroup getGlassBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup glassCategory = new MarketFactory.DefaultMarketSetupDataGroup("GlassBlocks",Items.BLUE_STAINED_GLASS);
        int glassPrice = getDefaultPrices().COMMON_BLOCK.GLASS.get();
        float rarity = 0.02f;
        float volatility = 0.1f;

        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_STAINED_GLASS, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_STAINED_GLASS, glassPrice, rarity, volatility));


        return glassCategory;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getGlassPlanes()
    {
        MarketFactory.DefaultMarketSetupDataGroup glassCategory = new MarketFactory.DefaultMarketSetupDataGroup("GlassPlanes",Items.BLUE_STAINED_GLASS_PANE);
        int glassPrice = (getDefaultPrices().COMMON_BLOCK.GLASS.get()*6)/16;
        float rarity = 0.02f;
        float volatility = 0.1f;

        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_STAINED_GLASS_PANE, glassPrice, rarity, volatility));
        glassCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_STAINED_GLASS_PANE, glassPrice, rarity, volatility));

        return glassCategory;
    }

    private static MarketFactory.DefaultMarketSetupDataGroup getEnchantedBooks()
    {
        MarketFactory.DefaultMarketSetupDataGroup books = new MarketFactory.DefaultMarketSetupDataGroup("EnchantedBook");
        float bookPriceFactor = getDefaultPrices().ENCHANTMENT.ENCHANTMENT_FACTOR.get();

        String searchText =  Items.ENCHANTED_BOOK.getDefaultInstance().getHoverName().getString();
        List<ItemStack> bookItems = ItemUtilities.getSearchCreativeItems(searchText);


        Enchantments.SHARPNESS.getRarity();

        for (ItemStack book : bookItems)
        {
            if (book.getItem() instanceof EnchantedBookItem enchantedBook)
            {
                int price = (int)(ItemPriceCalculator.calculateItemPrice(book) * bookPriceFactor);
                books.add(new MarketFactory.DefaultMarketSetupGeneratorData(book, price, 0.2f, 0.1f));
            }
        }
        if(!books.isEmpty())
        {
            books.setIconItem(books.marketSetupDataList.get(0).tradingPair.getItem().getStack());
        }
        return books;
    }

    private static MarketFactory.DefaultMarketSetupDataGroup getSplashPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup splashPotions = new MarketFactory.DefaultMarketSetupDataGroup("SplashPotions");
        float potionPriceFactor = getDefaultPrices().POTION.POTION_FACTOR.get();

        // Gets "Splash Water Bottle"
        //String searchText =  Items.SPLASH_POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems("");

        for (ItemStack potion : potionItems)
        {
            if (potion.getItem() instanceof SplashPotionItem)
            {
                int price = (int)(ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor);
                splashPotions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        if(!splashPotions.isEmpty())
        {
            splashPotions.setIconItem(splashPotions.marketSetupDataList.get(0).tradingPair.getItem().getStack());
        }
        return splashPotions;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup potions = new MarketFactory.DefaultMarketSetupDataGroup("Potions");
        float potionPriceFactor = getDefaultPrices().POTION.POTION_FACTOR.get();

        //String searchText =  Items.POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems("");

        for (ItemStack potion : potionItems)
        {
            Item item = potion.getItem();
            if ((item instanceof PotionItem) && !(item instanceof SplashPotionItem) && !(item instanceof LingeringPotionItem))
            {
                int price = (int)(ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor);
                potions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        if(!potions.isEmpty())
        {
            potions.setIconItem(potions.marketSetupDataList.get(0).tradingPair.getItem().getStack());
        }
        return potions;
    }


    public static MarketFactory.DefaultMarketSetupDataGroup getLingeringPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup lingeringPotions = new MarketFactory.DefaultMarketSetupDataGroup("LingeringPotions");
        float potionPriceFactor = getDefaultPrices().POTION.POTION_FACTOR.get();

        //String searchText =  Items.LINGERING_POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems("");

        for (ItemStack potion : potionItems)
        {
            if (potion.getItem() instanceof LingeringPotionItem)
            {
                int price = (int)(ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor);
                lingeringPotions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        if(!lingeringPotions.isEmpty())
        {
            lingeringPotions.setIconItem(lingeringPotions.marketSetupDataList.get(0).tradingPair.getItem().getStack());
        }
        return lingeringPotions;
    }


    public static MarketFactory.DefaultMarketSetupDataGroup getTippedArrows()
    {
        MarketFactory.DefaultMarketSetupDataGroup tippedArrows = new MarketFactory.DefaultMarketSetupDataGroup("TippedArrows");
        float arrowPriceFactor = getDefaultPrices().ARROW.POTION_FACTOR.get();

        //String searchText =  Items.TIPPED_ARROW.getDefaultInstance().getHoverName().getString();
        List<ItemStack> arrowItems = ItemUtilities.getSearchCreativeItems("");

        for (ItemStack arrow : arrowItems)
        {
            if (arrow.getItem() instanceof TippedArrowItem)
            {
                int price = (int)(ItemPriceCalculator.calculateItemPrice(arrow) * arrowPriceFactor);
                tippedArrows.add(new MarketFactory.DefaultMarketSetupGeneratorData(arrow, price, 0.2f, 0.1f));
            }
        }
        if(!tippedArrows.isEmpty())
        {
            tippedArrows.setIconItem(tippedArrows.marketSetupDataList.get(0).tradingPair.getItem().getStack());
        }
        return tippedArrows;
    }


    public static MarketFactory.DefaultMarketSetupDataGroup getMiscBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup miscBlocks = new MarketFactory.DefaultMarketSetupDataGroup("MiscBlocks", Items.GRAVEL);

        int obsidianPrice = getDefaultPrices().COMMON_BLOCK.OBSIDIAN.get();
        int dirtPrice = getDefaultPrices().COMMON_BLOCK.DIRT.get();

        int clayBrickettePrice = getDefaultPrices().MISC.CLAY_BRICKETTE.get();
        int cobblestonePrice = getDefaultPrices().COMMON_BLOCK.COBBLESTONE.get();
        int gravelPrice = getDefaultPrices().COMMON_BLOCK.GRAVEL.get();
        int sandPrice = getDefaultPrices().COMMON_BLOCK.SAND.get();

        int stonePrice = getDefaultPrices().COMMON_BLOCK.STONE.get();
        int netherBrickPrice = getDefaultPrices().MISC.NETHER_BRICK.get();
        int netherrackPrice = getDefaultPrices().COMMON_BLOCK.NETHERRACK.get();



        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OBSIDIAN, obsidianPrice, 0.2f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIRT, dirtPrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLESTONE, cobblestonePrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAVEL, gravelPrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SAND, sandPrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SAND, sandPrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SOUL_SAND, sandPrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICKS, clayBrickettePrice*4, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_BRICKS, stonePrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE, stonePrice, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICKS, netherBrickPrice*4, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERRACK, netherrackPrice, 0.02f, 0.1f));




        return miscBlocks;
    }

    /*public static MarketFactory.DefaultMarketSetupDataGroup getFurnitureBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup furniture = new MarketFactory.DefaultMarketSetupDataGroup("FurnitureBlocks");

        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRAFTING_TABLE, PLANK * 4, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHEST, PLANK * 8, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FURNACE, COBBLESTONE * 8, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOKER, COBBLESTONE * 8 + 4*LOG, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLAST_FURNACE, COBBLESTONE * 8+3*STONE+5*IRON, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BOOKSHELF, BOOKSHELF, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ENDER_CHEST, ENDER_CHEST, 0.2f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BREWING_STAND, BREWING_STAND, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BARREL, BARREL, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CAMPFIRE, CAMPFIRE, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMALL_AMETHYST_BUD, AMETHYST_BUD, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MEDIUM_AMETHYST_BUD, AMETHYST_BUD, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LARGE_AMETHYST_BUD, AMETHYST_BUD, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.AMETHYST_CLUSTER, AMETHYST_BUD, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CANDLE, CANDLE, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CARTOGRAPHY_TABLE, CARTOGRAPHY_TABLE, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRINDSTONE, GRINDSTONE, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMITHING_TABLE, SMITHING_TABLE, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUKEBOX, JUKEBOX, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NOTE_BLOCK, NOTE_BLOCK, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COMPOSTER, COMPOSTER, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LOOM, LOOM, 0.02f, 0.1f));

        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHEST_BOAT, CHEST_BOAT, 0.02f, 0.1f));
        furniture.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BARREL, BARREL, 0.02f, 0.1f));


    }*/

    public static MarketFactory.DefaultMarketSetupDataGroup getGardeningItems()
    {
        MarketFactory.DefaultMarketSetupDataGroup gardeningItems = new MarketFactory.DefaultMarketSetupDataGroup("GardeningItems", Items.WHEAT);
        int samplingPrice = getDefaultPrices().GARDENING.SAPLING.get();
        int pumpkinPrice = getDefaultPrices().GARDENING.PUMPKIN.get();
        int beeHivePrice = getDefaultPrices().GARDENING.BEEHIVE.get();
        int beeNestPrice = getDefaultPrices().GARDENING.BEE_NEST.get();
        int seedPrice = getDefaultPrices().GARDENING.SEED.get();
        int potatoPrice = getDefaultPrices().GARDENING.POTATO.get();
        int carrotPrice = getDefaultPrices().FOOD.CARROT.get();
        int sugarCanePrice = getDefaultPrices().GARDENING.SUGAR_CANE.get();
        int cocoaBeansPrice = getDefaultPrices().GARDENING.COCOA_BEANS.get();
        int wheatPrice = getDefaultPrices().GARDENING.WHEAT.get();
        int sweetBerriesPrice = getDefaultPrices().GARDENING.SWEET_BERRIES.get();
        int beetrootPrice = getDefaultPrices().GARDENING.BEETROOT.get();
        int glowBerriesPrice = getDefaultPrices().GARDENING.GLOW_BERRIES.get();


        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_SAPLING, samplingPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEEHIVE, beeHivePrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEE_NEST, beeNestPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PUMPKIN, pumpkinPrice, 0.02f, 0.1f));

        // Seeds
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHEAT_SEEDS, seedPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POTATO, potatoPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CARROT, carrotPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEETROOT_SEEDS, seedPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MELON_SEEDS, seedPrice*2, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PUMPKIN_SEEDS, seedPrice*2, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_WART, seedPrice*2, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SUGAR_CANE, sugarCanePrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COCOA_BEANS, cocoaBeansPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHEAT, wheatPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SWEET_BERRIES, sweetBerriesPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEETROOT, beetrootPrice, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GLOW_BERRIES, glowBerriesPrice, 0.02f, 0.1f));


        /*gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO, BAMBOO, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CACTUS, CACTUS, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POPPY, POPPY, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DANDELION, DANDELION, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_ORCHID, BLUE_ORCHID, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ALLIUM, ALLIUM, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.AZURE_BLUET, AZURE_BLUET, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_TULIP, RED_TULIP, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_TULIP, ORANGE_TULIP, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_TULIP, WHITE_TULIP, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_TULIP, PINK_TULIP, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OXEYE_DAISY, OXEYE_DAISY, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CORNFLOWER, CORNFLOWER, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LILY_OF_THE_VALLEY, LILY_OF_THE_VALLEY, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WITHER_ROSE, WITHER_ROSE, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHORUS_FLOWER, CHORUS_FLOWER, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHORUS_FRUIT, CHORUS_FRUIT, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SEA_PICKLE, SEA_PICKLE, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.TORCHFLOWER, TORCHFLOWER, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PITCHER_PLANT, PITCHER_PLANT, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FLOWERING_AZALEA, FLOWERING_AZALEA, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.AZALEA, AZALEA, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSS_BLOCK, MOSS_BLOCK, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MOSS_CARPET, MOSS_CARPET, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPORE_BLOSSOM, SPORE_BLOSSOM, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FERN, FERN, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DEAD_BUSH, DEAD_BUSH, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.VINE, VINE, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.TALL_GRASS, TALL_GRASS, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LILY_PAD, LILY_PAD, 0.02f, 0.1f));
        gardeningItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SCAFFOLDING, SCAFFOLDING, 0.02f, 0.1f));*/






        return gardeningItems;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getAnimalLootItems()
    {
        MarketFactory.DefaultMarketSetupDataGroup animalLootItems = new MarketFactory.DefaultMarketSetupDataGroup("AnimalLootItems", Items.BONE);
        int leatherPrice = getDefaultPrices().ANIMAL_LOOT.LEATHER.get();
        int rabbitHidePrice = getDefaultPrices().ANIMAL_LOOT.RABBIT_HIDE.get();
        int rawRabbitPrice = getDefaultPrices().ANIMAL_LOOT.RAW_RABBIT.get();
        int porkchopPrice = getDefaultPrices().ANIMAL_LOOT.RAW_PORKCHOP.get();
        int beefPrice = getDefaultPrices().ANIMAL_LOOT.RAW_BEEF.get();
        int chickenPrice = getDefaultPrices().ANIMAL_LOOT.RAW_CHICKEN.get();
        int muttonPrice = getDefaultPrices().ANIMAL_LOOT.RAW_MUTTON.get();
        int featherPrice = getDefaultPrices().ANIMAL_LOOT.FEATHER.get();
        int eggPrice = getDefaultPrices().ANIMAL_LOOT.EGG.get();
        int bonePrice = getDefaultPrices().ANIMAL_LOOT.BONE.get();
        int salmonPrice = getDefaultPrices().ANIMAL_LOOT.RAW_SALMON.get();
        int codPrice = getDefaultPrices().ANIMAL_LOOT.COD.get();
        int tropicalFishPrice = getDefaultPrices().ANIMAL_LOOT.TROPICAL_FISH.get();
        int pufferfishPrice = getDefaultPrices().ANIMAL_LOOT.PUFFER_FISH.get();
        int honeycombPrice = getDefaultPrices().ANIMAL_LOOT.HONEYCOMB.get();
        int honeyBottlePrice = getDefaultPrices().ANIMAL_LOOT.HONEY_BOTTLE.get();
        int inkSacPrice = getDefaultPrices().ANIMAL_LOOT.INK_SAC.get();
        int spiderEyePrice = getDefaultPrices().ANIMAL_LOOT.SPIDER_EYE.get();
        int stringPrice = getDefaultPrices().ANIMAL_LOOT.STRING.get();
        int enderPearlPrice = getDefaultPrices().ANIMAL_LOOT.ENDER_PEARL.get();
        int ghastTearPrice = getDefaultPrices().ANIMAL_LOOT.GHAST_TEAR.get();
        int netherStarPrice = getDefaultPrices().ANIMAL_LOOT.NETHER_STAR.get();
        int slimeBallPrice = getDefaultPrices().ANIMAL_LOOT.SLIME_BALL.get();


        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LEATHER, leatherPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RABBIT_HIDE, rabbitHidePrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RABBIT_FOOT, rawRabbitPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PORKCHOP, porkchopPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEEF, beefPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHICKEN, chickenPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUTTON, muttonPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RABBIT, rawRabbitPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FEATHER, featherPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EGG, eggPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BONE, bonePrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SALMON, salmonPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COD, codPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.TROPICAL_FISH, tropicalFishPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PUFFERFISH, pufferfishPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.HONEYCOMB, honeycombPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.HONEY_BOTTLE, honeyBottlePrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.INK_SAC, inkSacPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPIDER_EYE, spiderEyePrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRING, stringPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ENDER_PEARL, enderPearlPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GHAST_TEAR, ghastTearPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_STAR, netherStarPrice, 0.02f, 0.1f));
        animalLootItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SLIME_BALL, slimeBallPrice, 0.02f, 0.1f));

        return animalLootItems;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getFood() {
        MarketFactory.DefaultMarketSetupDataGroup foodItems = new MarketFactory.DefaultMarketSetupDataGroup("FoodItems", Items.BREAD);

        int applePrice = getDefaultPrices().FOOD.APPLE.get();
        int goldenApplePrice = getDefaultPrices().FOOD.GOLDEN_APPLE.get();
        int enchantedGoldenApplePrice = getDefaultPrices().FOOD.ENCHANTED_GOLDEN_APPLE.get();
        int breadPrice = getDefaultPrices().FOOD.BREAD.get();
        int cookiePrice = getDefaultPrices().FOOD.COOKIE.get();
        int cakePrice = getDefaultPrices().FOOD.CAKE.get();
        int pumpkinPiePrice = getDefaultPrices().FOOD.PUMPKIN_PIE.get();
        int suspiciousStewPrice = getDefaultPrices().FOOD.SUSPICIOUS_STEW.get();
        int mushroomStewPrice = getDefaultPrices().FOOD.MUSHROOM_STEW.get();
        int rabbitStewPrice = getDefaultPrices().FOOD.RABBIT_STEW.get();
        int goldenCarrotPrice = getDefaultPrices().FOOD.GOLDEN_CARROT.get();
        int beetrootSoupPrice = getDefaultPrices().FOOD.BEETROOT_SOUP.get();
        int bakedPotatoPrice = getDefaultPrices().FOOD.BAKED_POTATO.get();
        int sugarPrice = getDefaultPrices().FOOD.SUGAR.get();
        int meloneSlicePrice = getDefaultPrices().FOOD.MELON_SLICE.get();
        int mushroomPrice = getDefaultPrices().FOOD.MUSHROOM.get();



        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.APPLE, applePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLDEN_APPLE, goldenApplePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ENCHANTED_GOLDEN_APPLE, enchantedGoldenApplePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BREAD, breadPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKIE, cookiePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CAKE, cakePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PUMPKIN_PIE, pumpkinPiePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SUSPICIOUS_STEW, suspiciousStewPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUSHROOM_STEW, mushroomStewPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RABBIT_STEW, rabbitStewPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLDEN_CARROT, goldenCarrotPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEETROOT_SOUP, beetrootSoupPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAKED_POTATO, bakedPotatoPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SUGAR, sugarPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MELON_SLICE, meloneSlicePrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_MUSHROOM, mushroomPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_MUSHROOM, mushroomPrice, 0.02f, 0.1f));


        // cooked meets
        int cookedBeefPrice = getDefaultPrices().FOOD.COOKED_BEEF.get();
        int cookedChickenPrice = getDefaultPrices().FOOD.COOKED_CHICKEN.get();
        int cookedMuttonPrice = getDefaultPrices().FOOD.COOKED_MUTTON.get();
        int cookedPorkchopPrice = getDefaultPrices().FOOD.COOKED_PORKCHOP.get();
        int cookedRabbitPrice = getDefaultPrices().FOOD.COOKED_RABBIT.get();
        int cookedSalmonPrice = getDefaultPrices().FOOD.COOKED_SALMON.get();
        int cookedCodPrice = getDefaultPrices().FOOD.COOKED_COD.get();
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_BEEF, cookedBeefPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_CHICKEN, cookedChickenPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_MUTTON, cookedMuttonPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_PORKCHOP, cookedPorkchopPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_RABBIT, cookedRabbitPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_SALMON, cookedSalmonPrice, 0.02f, 0.1f));
        foodItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COOKED_COD, cookedCodPrice, 0.02f, 0.1f));

        return foodItems;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getDyes()
    {
        MarketFactory.DefaultMarketSetupDataGroup dyeItems = new MarketFactory.DefaultMarketSetupDataGroup("Dyes",Items.LIGHT_BLUE_DYE);

        int dyePrice = getDefaultPrices().DYE.DYE.get();
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_DYE, dyePrice, 0.02f, 0.1f));
        dyeItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BONE_MEAL, dyePrice, 0.02f, 0.1f));


        return dyeItems;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getMiscItems()
    {
        MarketFactory.DefaultMarketSetupDataGroup miscItems = new MarketFactory.DefaultMarketSetupDataGroup("MiscItems",Items.ENDER_PEARL);

        int blazePowderPrice = getDefaultPrices().MISC.BLAZE_POWDER.get();

        int glowstoneDustPrice = getDefaultPrices().MISC.GLOWSTONE_DUST.get();
        int bookPrice = getDefaultPrices().MISC.BOOK.get();
        int flintPrice = getDefaultPrices().MISC.FLINT.get();
        int enderPearlPrice = getDefaultPrices().ANIMAL_LOOT.ENDER_PEARL.get();
        int clayBallPrice = getDefaultPrices().MISC.CLAY_BALL.get();
        int netherBrickPrice = getDefaultPrices().MISC.NETHER_BRICK.get();


        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CLAY_BALL, clayBallPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK, netherBrickPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLAZE_ROD, blazePowderPrice*2, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLAZE_POWDER, blazePowderPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GLOWSTONE_DUST, glowstoneDustPrice, 0.02f, 0.1f));
        //miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRING, getDefaultPrices().MISC.STRING.get(), 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ENDER_EYE, enderPearlPrice+blazePowderPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BOOK, bookPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FLINT, flintPrice, 0.02f, 0.1f));
        miscItems.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGMA_CREAM, blazePowderPrice + getDefaultPrices().ANIMAL_LOOT.SLIME_BALL.get(), 0.02f, 0.1f));





        return miscItems;
    }





    public static class ItemPriceCalculator {

        public static float calculateItemPrice(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return 0;

            float basePrice = getBaseItemValue(stack);
            float enchantmentBonus = getEnchantmentValue(stack);
            float potionBonus = getPotionValue(stack);
            float nameBonus = stack.hasCustomHoverName() ? 10 : 0;
            float durabilityBonus = getDurabilityBonus(stack);

            float total = basePrice + enchantmentBonus + potionBonus + nameBonus + durabilityBonus;
            return total * stack.getCount();
        }

        public static float getBaseItemValue(ItemStack stack) {
            // You can assign specific values per item if needed
            if (stack.getItem() == Items.DIAMOND_SWORD) return 100;
            if (stack.getItem() == Items.NETHERITE_CHESTPLATE) return 500;
            if (stack.getItem() == Items.DIRT) return 1;
            return 10; // default fallback
        }

        public static float getEnchantmentValue(ItemStack stack) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            float value = 0;
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                int level = entry.getValue();
                int rarityDivider = entry.getKey().getRarity().ordinal() + 1;
                value += 10 * (float)Math.exp(level*0.58)/(float)rarityDivider;
            }
            return value;
        }

        public static float getPotionValue(ItemStack stack) {
            if (!(stack.getItem() instanceof PotionItem)) return 0;
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
            int value = 0;
            for (MobEffectInstance effect : effects) {
                int amplifier = effect.getAmplifier() + 1;
                int duration = effect.getDuration() / 20; // ticks to seconds
                value += (amplifier * 5 + 2) * (duration / 10); // value grows with amplifier and duration
            }
            return value/10.f;
        }

        public static float getDurabilityBonus(ItemStack stack) {
            if (!stack.isDamageableItem()) return 0;
            int maxDurability = stack.getMaxDamage();
            int currentDurability = maxDurability - stack.getDamageValue();
            return (int)((currentDurability / (double)maxDurability) * 20); // max +20 bonus if fully intact
        }
    }

}

