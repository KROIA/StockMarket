package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ItemUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class DefaultMarketSettings {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
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

    private static final int ENCHANTMENT_BOOK_PRICE = 200; // Base price for enchanted books



    public static void createDefaultMarketSettingsIfNotExist() {
        //BACKEND_INSTANCES.LOGGER.info("Generating new default market settings.");

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
        getSand().saveIfNotExists();
        getEnchantedBooks().saveIfNotExists();
        //createAndSaveSettings("Clay", getClay());
        getWool().saveIfNotExists();
        getCarpet().saveIfNotExists();
        getTerracotta().saveIfNotExists();
        getGlazedTerracotta().saveIfNotExists();
        getConcrete().saveIfNotExists();
        getConcretePowder().saveIfNotExists();
        //createAndSaveSettings("MiscBlocks", getMiscBlocks(), updateMS);
        //createAndSaveSettings("Misc", getMisc(), updateMS);
        //createAndSaveSettings("Food", getFood(), updateMS);
        //createAndSaveSettings("Dyes", getDyes(), updateMS);
        //createAndSaveSettings("Plants", getPlants(), updateMS);
        //createAndSaveSettings("MiscItems", getMiscItems(), updateMS);



    }




    private static MarketFactory.DefaultMarketSetupDataGroup getLogs()
    {
        MarketFactory.DefaultMarketSetupDataGroup logsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Logs");
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_STEM, LOG_PRICE, 0.01f, 0.1f));

        // Stripped variants
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_SPRUCE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_BIRCH_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_JUNGLE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_ACACIA_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_CHERRY_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_DARK_OAK_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_MANGROVE_LOG, LOG_PRICE, 0.01f, 0.1f));
        logsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STRIPPED_CRIMSON_STEM, LOG_PRICE, 0.01f, 0.1f));

        return logsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getPlanks()
    {
        MarketFactory.DefaultMarketSetupDataGroup planksCategory = new MarketFactory.DefaultMarketSetupDataGroup("Planks");
        int plankPrice = PLANK_PRICE;
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_PLANKS, Math.min(BAMBOO_PRICE*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC, Math.min(BAMBOO_PRICE*9/2,plankPrice), 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_PLANKS, plankPrice, 0.01f, 0.1f));
        planksCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_PLANKS, plankPrice, 0.01f, 0.1f));
        return planksCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getFences()
    {
        MarketFactory.DefaultMarketSetupDataGroup fencesCategory = new MarketFactory.DefaultMarketSetupDataGroup("Fences");
        int fencePrice = (PLANK_PRICE*4 + STICK_PRICE*2)/3;
        int fenceDoorPrice = PLANK_PRICE*2+STICK_PRICE*4;
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_FENCE, Math.min((BAMBOO_PRICE*9*2+4*STICK_PRICE)/3, fencePrice), 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_FENCE, fencePrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_FENCE, fencePrice, 0.01f, 0.1f));

        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_FENCE_GATE, Math.min(BAMBOO_PRICE*9+4*STICK_PRICE, fenceDoorPrice), 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        fencesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_FENCE_GATE, fenceDoorPrice, 0.01f, 0.1f));
        return fencesCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getDoors()
    {
        MarketFactory.DefaultMarketSetupDataGroup doorsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Doors");
        int doorPrice = (PLANK_PRICE*6)/3;
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_DOOR, IRON_PRICE *2, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_DOOR, Math.min(BAMBOO_PRICE*9,doorPrice), 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_DOOR, doorPrice, 0.01f, 0.1f));
        doorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_DOOR, doorPrice, 0.01f, 0.1f));
        return doorsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getTrapDoors()
    {
        MarketFactory.DefaultMarketSetupDataGroup trapDoorsCategory = new MarketFactory.DefaultMarketSetupDataGroup("TrapDoors");
        int trapDoorPrice = (PLANK_PRICE*6)/2;
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_TRAPDOOR, IRON_PRICE *4, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_TRAPDOOR, Math.min(BAMBOO_PRICE*9*3/2,trapDoorPrice), 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        trapDoorsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_TRAPDOOR, trapDoorPrice, 0.01f, 0.1f));
        return trapDoorsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getPressurePlates()
    {
        MarketFactory.DefaultMarketSetupDataGroup pressurePlatesCategory = new MarketFactory.DefaultMarketSetupDataGroup("PressurePlates");
        int pressurePlatePrice = PLANK_PRICE*2;
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_PRESSURE_PLATE, Math.min(BAMBOO_PRICE*9,pressurePlatePrice), 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRIMSON_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WARPED_PRESSURE_PLATE, pressurePlatePrice, 0.01f, 0.1f));

        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.STONE_PRESSURE_PLATE, STONE_PRICE*2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, IRON_PRICE *2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_WEIGHTED_PRESSURE_PLATE, GOLD_PRICE *2, 0.01f, 0.1f));
        pressurePlatesCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, GOLD_PRICE *2, 0.01f, 0.1f));

        return pressurePlatesCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getStairs()
    {
        MarketFactory.DefaultMarketSetupDataGroup stairsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Stairs");
        int woodStairsPrice = PLANK_PRICE*6/4;
        int stoneStairsPrice = STONE_PRICE;
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_STAIRS, woodStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_STAIRS, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC_STAIRS, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
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
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_STAIRS, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_RED_SANDSTONE_STAIRS, SAND_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_STAIRS, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_BRICK_STAIRS, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_PRISMARINE_STAIRS, PRISMARINE_SHARD_PRICE*8, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_STAIRS, stoneStairsPrice, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPUR_STAIRS, CHORUS_FRUIT_PRICE*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_STAIRS, NETHER_QUARTZ*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_QUARTZ_STAIRS, NETHER_QUARTZ*4, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EXPOSED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WEATHERED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OXIDIZED_CUT_COPPER_STAIRS, COPPER_PRICE *9, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_EXPOSED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        stairsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        return stairsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getSlaps()
    {
        MarketFactory.DefaultMarketSetupDataGroup slapsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Slabs");
        int woodStairsPrice = PLANK_PRICE*6/4;
        int stoneStairsPrice = STONE_PRICE;
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SPRUCE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BIRCH_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.JUNGLE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ACACIA_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHERRY_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_OAK_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MANGROVE_SLAB, woodStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_SLAB, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BAMBOO_MOSAIC_SLAB, Math.min(BAMBOO_PRICE*3*9/4,woodStairsPrice), 0.01f, 0.1f));
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
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_SLAB, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_STANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_RED_SANDSTONE_SLAB, SAND_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_SLAB, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_BRICK_SLAB, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DARK_PRISMARINE_SLAB, PRISMARINE_SHARD_PRICE*8, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_SLAB, stoneStairsPrice, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPUR_SLAB, CHORUS_FRUIT_PRICE*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_SLAB, NETHER_QUARTZ*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOOTH_QUARTZ_SLAB, NETHER_QUARTZ*4, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EXPOSED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WEATHERED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OXIDIZED_CUT_COPPER_SLAB, COPPER_PRICE *9, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_EXPOSED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_WEATHERED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        slapsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WAXED_OXIDIZED_CUT_COPPER_SLAB, COPPER_PRICE *9+HONEYCOMB_PRICE, 0.01f, 0.1f));
        return slapsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getWalls()
    {
        MarketFactory.DefaultMarketSetupDataGroup wallsCategory = new MarketFactory.DefaultMarketSetupDataGroup("Walls");
        int wallPrice = STONE_PRICE;
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
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BRICK_WALL, CLAY_BALL_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MUD_BRICK_WALL, wallPrice, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SANDSTONE_WALL, SAND_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SANDSTONE_WALL, SAND_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PRISMARINE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHER_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_NETHER_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACKSTONE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.POLISHED_BLACKSTONE_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        wallsCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.END_STONE_BRICK_WALL, PRISMARINE_SHARD_PRICE*4, 0.01f, 0.1f));
        return wallsCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getOres()
    {
        MarketFactory.DefaultMarketSetupDataGroup oresCategory = new MarketFactory.DefaultMarketSetupDataGroup("Ores");
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COAL, COAL_PRICE, 0.02f, 0.01f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_INGOT, IRON_PRICE, 0.03f, 0.02f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COPPER_INGOT, COPPER_PRICE, 0.03f, 0.02f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLD_INGOT, GOLD_PRICE, 0.05f, 0.02f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.REDSTONE, REDSTONE_DUST_PRICE, 0.08f, 0.02f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LAPIS_LAZULI, LAPIS_LAZULI_PRICE, 0.08f, 0.01f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIAMOND, DIAMOND_PRICE, 0.1f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EMERALD, EMERALD_PRICE, 0.2f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ, NETHER_QUARTZ, 0.03f, 0.01f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ANCIENT_DEBRIS, ANCIENT_DEBRIS_PRICE, 0.5f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERITE_INGOT, ANCIENT_DEBRIS_PRICE, 0.5f, 0.1f));
        return oresCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getOreBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup oresCategory = new MarketFactory.DefaultMarketSetupDataGroup("OreBlocks");
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COAL_BLOCK, COAL_PRICE*9, 0.02f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_BLOCK, IRON_PRICE*9, 0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COPPER_BLOCK, COPPER_PRICE*9, 0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLD_BLOCK, GOLD_PRICE*9, 0.05f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.REDSTONE_BLOCK, REDSTONE_DUST_PRICE*9, 0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LAPIS_BLOCK, LAPIS_LAZULI_PRICE*9, 0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIAMOND_BLOCK, DIAMOND_PRICE*9, 0.1f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EMERALD_BLOCK, EMERALD_PRICE*9, 0.2f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ_BLOCK, NETHER_QUARTZ*9, 0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.NETHERITE_BLOCK, ANCIENT_DEBRIS_PRICE*9, 0.5f, 0.1f));
        return oresCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getWool()
    {
        MarketFactory.DefaultMarketSetupDataGroup woolCategory = new MarketFactory.DefaultMarketSetupDataGroup("Wool");
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.WHITE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ORANGE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.MAGENTA_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_BLUE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.YELLOW_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIME_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PINK_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAY_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LIGHT_GRAY_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CYAN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.PURPLE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLUE_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BROWN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GREEN_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        woolCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLACK_WOOL, WOOL_PRICE, 0.01f, 0.1f));
        return woolCategory;
    }
    private static MarketFactory.DefaultMarketSetupDataGroup getCarpet()
    {
        MarketFactory.DefaultMarketSetupDataGroup carpetCategory = new MarketFactory.DefaultMarketSetupDataGroup("Carpet");
        int carpetPrice = Math.max(1, WOOL_PRICE*2/3);
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
        MarketFactory.DefaultMarketSetupDataGroup terracotta = new MarketFactory.DefaultMarketSetupDataGroup("Terracotta");
        int terraCottaPrice = CLAY_BALL_PRICE*4;
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
        MarketFactory.DefaultMarketSetupDataGroup terracotta = new MarketFactory.DefaultMarketSetupDataGroup("GlazedTerracotta");
        int terraCottaPrice = CLAY_BALL_PRICE*4;
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
        MarketFactory.DefaultMarketSetupDataGroup concrete = new MarketFactory.DefaultMarketSetupDataGroup("Concrete");
        int concretePrice = GRAVEL_PRICE*4+ SAND_PRICE*4+ DYE_PRICE;
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
        MarketFactory.DefaultMarketSetupDataGroup concretePowder = new MarketFactory.DefaultMarketSetupDataGroup("ConcretePowder");
        int concretePowderPrice = GRAVEL_PRICE*4+ SAND_PRICE*4+ DYE_PRICE;
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


    private static MarketFactory.DefaultMarketSetupDataGroup getSand()
    {
        MarketFactory.DefaultMarketSetupDataGroup sandCategory = new MarketFactory.DefaultMarketSetupDataGroup("Sand");
        int sandPrice = SAND_PRICE;
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.RED_SAND, sandPrice, 0.02f, 0.1f));
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAVEL, sandPrice, 0.02f, 0.1f));
        return sandCategory;
    }

    private static MarketFactory.DefaultMarketSetupDataGroup getEnchantedBooks()
    {
        MarketFactory.DefaultMarketSetupDataGroup books = new MarketFactory.DefaultMarketSetupDataGroup("EnchantedBook");
        int bookPrice = ENCHANTMENT_BOOK_PRICE;

        List<ItemStack> bookItems = ItemUtilities.getSearchCreativeItems("enchanted book");

        for (ItemStack book : bookItems)
        {
            if (book.getItem() instanceof EnchantedBookItem)
            {
                books.add(new MarketFactory.DefaultMarketSetupGeneratorData(book, bookPrice, 0.2f, 0.1f));
            }
        }
        return books;
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
