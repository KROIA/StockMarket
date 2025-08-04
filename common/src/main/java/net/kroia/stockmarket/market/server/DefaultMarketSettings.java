package net.kroia.stockmarket.market.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.Map;

public class DefaultMarketSettings {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private static float INFLATION_SCALE = 10;

    // Basic Block prices
    private static int LOG_PRICE = 20;
    private static int PLANK_PRICE = Math.max(1,LOG_PRICE/4);
    private static int STICK_PRICE = Math.max(1,LOG_PRICE/8);
    private static int STONE_PRICE = 20;
    private static int COBBLESTONE_PRICE = Math.max(1,STONE_PRICE/2); // Cobblestone is generally cheaper than stone
    private static int SAND_PRICE = 20;
    private static int DIRT_PRICE = 5; // Dirt is generally cheaper than sand
    private static int GRAVEL_PRICE = 20;
    private static int CLAY_BALL_PRICE = 5;
    private static int WOOL_PRICE = 5;
    private static int GLASS_PRICE = SAND_PRICE;
    private static int OBSIDIAN_PRICE = 100; // Obsidian is more expensive due to its rarity and mining difficulty


    // Ore prices
    private static int COAL_PRICE = 8;
    private static int IRON_PRICE = 30;
    private static int COPPER_PRICE = 20;
    private static int GOLD_PRICE = 100;
    private static int DIAMOND_PRICE = 200;
    private static int EMERALD_PRICE = 300;
    private static int LAPIS_LAZULI_PRICE = 50;
    private static int ANCIENT_DEBRIS_PRICE = 500;
    private static int NETHERITE_SCRAP_PRICE = ANCIENT_DEBRIS_PRICE;
    private static int REDSTONE_DUST_PRICE = 10;
    private static int NETHER_QUARTZ = 10;


    // Plants
    private static int BAMBOO_PRICE = 2;



    // Misc
    private static int PRISMARINE_SHARD_PRICE = 10;
    private static int CHORUS_FRUIT_PRICE = 10;
    private static int HONEYCOMB_PRICE = 10;
    private static int DYE_PRICE = 1;

    private static int ENCHANTMENT_BOOK_PRICE_FACTOR = 10; // Base price for enchanted books
    private static int POTION_PRICE_FACTOR = 10; // Base price for enchanted books


    private static JsonElement getBasePricesJson()
    {
        JsonObject basePrices = new JsonObject();
        basePrices.addProperty("INFLATION_SCALE", INFLATION_SCALE);
        basePrices.addProperty("log_price", LOG_PRICE);
        basePrices.addProperty("plank_price", PLANK_PRICE);
        basePrices.addProperty("stick_price", STICK_PRICE);
        basePrices.addProperty("stone_price", STONE_PRICE);
        basePrices.addProperty("cobblestone_price", COBBLESTONE_PRICE);
        basePrices.addProperty("sand_price", SAND_PRICE);
        basePrices.addProperty("dirt_price", DIRT_PRICE);
        basePrices.addProperty("glass_price", GLASS_PRICE);
        basePrices.addProperty("obsidian_price", OBSIDIAN_PRICE);
        basePrices.addProperty("gravel_price", GRAVEL_PRICE);
        basePrices.addProperty("clay_ball_price", CLAY_BALL_PRICE);
        basePrices.addProperty("wool_price", WOOL_PRICE);
        basePrices.addProperty("coal_price", COAL_PRICE);
        basePrices.addProperty("iron_price", IRON_PRICE);
        basePrices.addProperty("copper_price", COPPER_PRICE);
        basePrices.addProperty("gold_price", GOLD_PRICE);
        basePrices.addProperty("diamond_price", DIAMOND_PRICE);
        basePrices.addProperty("emerald_price", EMERALD_PRICE);
        basePrices.addProperty("lapis_lazuli_price", LAPIS_LAZULI_PRICE);
        basePrices.addProperty("ancient_debris_price", ANCIENT_DEBRIS_PRICE);
        basePrices.addProperty("netherite_scrap_price", NETHERITE_SCRAP_PRICE);
        basePrices.addProperty("redstone_dust_price", REDSTONE_DUST_PRICE);
        basePrices.addProperty("nether_quartz_price", NETHER_QUARTZ);
        basePrices.addProperty("bamboo_price", BAMBOO_PRICE);
        basePrices.addProperty("prismarine_shard_price", PRISMARINE_SHARD_PRICE);
        basePrices.addProperty("chorus_fruit_price", CHORUS_FRUIT_PRICE);
        basePrices.addProperty("honeycomb_price", HONEYCOMB_PRICE);
        basePrices.addProperty("dye_price", DYE_PRICE);
        basePrices.addProperty("enchanted_book_price_factor", ENCHANTMENT_BOOK_PRICE_FACTOR);
        basePrices.addProperty("potion_price_factor", POTION_PRICE_FACTOR);
        // Add more prices as needed




        return basePrices;
    }
    private static void readBasePrices(JsonElement json)
    {
        if(json == null)
            return;
        if(!json.isJsonObject())
            return;
        JsonObject jsonObject = json.getAsJsonObject();
        INFLATION_SCALE = jsonObject.has("INFLATION_SCALE") ? jsonObject.get("INFLATION_SCALE").getAsFloat() : INFLATION_SCALE;
        LOG_PRICE = getOrDefaultScaled(jsonObject, "log_price", LOG_PRICE);
        PLANK_PRICE = getOrDefaultScaled(jsonObject, "plank_price", PLANK_PRICE);
        STICK_PRICE = getOrDefaultScaled(jsonObject, "stick_price", STICK_PRICE);
        STONE_PRICE = getOrDefaultScaled(jsonObject, "stone_price", STONE_PRICE);
        COBBLESTONE_PRICE = getOrDefaultScaled(jsonObject, "cobblestone_price", COBBLESTONE_PRICE);
        SAND_PRICE = getOrDefaultScaled(jsonObject, "sand_price", SAND_PRICE);
        DIRT_PRICE = getOrDefaultScaled(jsonObject, "dirt_price", DIRT_PRICE);
        GLASS_PRICE = getOrDefaultScaled(jsonObject, "glass_price", GLASS_PRICE);
        OBSIDIAN_PRICE = getOrDefaultScaled(jsonObject, "obsidian_price", OBSIDIAN_PRICE);
        GRAVEL_PRICE = getOrDefaultScaled(jsonObject, "gravel_price", GRAVEL_PRICE);
        CLAY_BALL_PRICE = getOrDefaultScaled(jsonObject, "clay_ball_price", CLAY_BALL_PRICE);
        WOOL_PRICE = getOrDefaultScaled(jsonObject, "wool_price", WOOL_PRICE);
        COAL_PRICE = getOrDefaultScaled(jsonObject, "coal_price", COAL_PRICE);
        IRON_PRICE = getOrDefaultScaled(jsonObject, "iron_price", IRON_PRICE);
        COPPER_PRICE = getOrDefaultScaled(jsonObject, "copper_price", COPPER_PRICE);
        GOLD_PRICE = getOrDefaultScaled(jsonObject, "gold_price", GOLD_PRICE);
        DIAMOND_PRICE = getOrDefaultScaled(jsonObject, "diamond_price", DIAMOND_PRICE);
        EMERALD_PRICE = getOrDefaultScaled(jsonObject, "emerald_price", EMERALD_PRICE);
        LAPIS_LAZULI_PRICE = getOrDefaultScaled(jsonObject, "lapis_lazuli_price", LAPIS_LAZULI_PRICE);
        ANCIENT_DEBRIS_PRICE = getOrDefaultScaled(jsonObject, "ancient_debris_price", ANCIENT_DEBRIS_PRICE);
        NETHERITE_SCRAP_PRICE = getOrDefaultScaled(jsonObject, "netherite_scrap_price", NETHERITE_SCRAP_PRICE);
        REDSTONE_DUST_PRICE = getOrDefaultScaled(jsonObject, "redstone_dust_price", REDSTONE_DUST_PRICE);
        NETHER_QUARTZ = getOrDefaultScaled(jsonObject, "nether_quartz_price", NETHER_QUARTZ);
        BAMBOO_PRICE = getOrDefaultScaled(jsonObject, "bamboo_price", BAMBOO_PRICE);
        PRISMARINE_SHARD_PRICE = getOrDefaultScaled(jsonObject, "prismarine_shard_price", PRISMARINE_SHARD_PRICE);
        CHORUS_FRUIT_PRICE = getOrDefaultScaled(jsonObject, "chorus_fruit_price", CHORUS_FRUIT_PRICE);
        HONEYCOMB_PRICE = getOrDefaultScaled(jsonObject, "honeycomb_price", HONEYCOMB_PRICE);
        DYE_PRICE = getOrDefaultScaled(jsonObject, "dye_price", DYE_PRICE);
        ENCHANTMENT_BOOK_PRICE_FACTOR = getOrDefaultScaled(jsonObject, "enchanted_book_price_factor", ENCHANTMENT_BOOK_PRICE_FACTOR);
        POTION_PRICE_FACTOR = getOrDefaultScaled(jsonObject, "potion_price_factor", POTION_PRICE_FACTOR);
        // Add more prices as needed

    }
    private static int getOrDefaultScaled(JsonObject obj, String key, int defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isNumber()) {
            return (int) (obj.get(key).getAsInt() * INFLATION_SCALE);
        }
        return (int)(defaultValue * INFLATION_SCALE);
    }



    public static void createDefaultMarketSettingsIfNotExist() {

        loadOrSaveBasePrices();

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


    private static void loadOrSaveBasePrices()
    {
        if(BACKEND_INSTANCES.SERVER_DATA_HANDLER.basePriceFileExists())
        {
            readBasePrices(BACKEND_INSTANCES.SERVER_DATA_HANDLER.loadBasePricesFile());
        }
        else
        {
            BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveBasePricesFile(getBasePricesJson());
            readBasePrices(BACKEND_INSTANCES.SERVER_DATA_HANDLER.loadBasePricesFile());
        }
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
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COAL, COAL_PRICE, 0.02f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.IRON_INGOT, IRON_PRICE, 0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COPPER_INGOT, COPPER_PRICE, 0.03f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GOLD_INGOT, GOLD_PRICE, 0.05f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.REDSTONE, REDSTONE_DUST_PRICE, 0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.LAPIS_LAZULI, LAPIS_LAZULI_PRICE, 0.08f, 0.1f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIAMOND, DIAMOND_PRICE, 0.1f, 0.2f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.EMERALD, EMERALD_PRICE, 0.2f, 0.2f));
        oresCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.QUARTZ, NETHER_QUARTZ, 0.03f, 0.1f));
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
        sandCategory.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SOUL_SAND, sandPrice, 0.02f, 0.1f));
        return sandCategory;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getGlassBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup glassCategory = new MarketFactory.DefaultMarketSetupDataGroup("GlassBlocks");
        int glassPrice = GLASS_PRICE;
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
        MarketFactory.DefaultMarketSetupDataGroup glassCategory = new MarketFactory.DefaultMarketSetupDataGroup("GlassPlanes");
        int glassPrice = (GLASS_PRICE*6)/16;
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
        int bookPriceFactor = ENCHANTMENT_BOOK_PRICE_FACTOR;

        String searchText =  Items.ENCHANTED_BOOK.getDefaultInstance().getHoverName().getString();
        List<ItemStack> bookItems = ItemUtilities.getSearchCreativeItems(searchText);


        Enchantments.SHARPNESS.getRarity();

        for (ItemStack book : bookItems)
        {
            if (book.getItem() instanceof EnchantedBookItem enchantedBook)
            {
                int price = ItemPriceCalculator.calculateItemPrice(book) * bookPriceFactor;
                books.add(new MarketFactory.DefaultMarketSetupGeneratorData(book, price, 0.2f, 0.1f));
            }
        }
        return books;
    }

    private static MarketFactory.DefaultMarketSetupDataGroup getSplashPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup splashPotions = new MarketFactory.DefaultMarketSetupDataGroup("SplashPotions");
        int potionPriceFactor = POTION_PRICE_FACTOR;

        String searchText =  Items.SPLASH_POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems(searchText);

        for (ItemStack potion : potionItems)
        {
            if (potion.getItem() instanceof SplashPotionItem)
            {
                int price = ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor;
                splashPotions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        return splashPotions;
    }

    public static MarketFactory.DefaultMarketSetupDataGroup getPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup potions = new MarketFactory.DefaultMarketSetupDataGroup("Potions");
        int potionPriceFactor = POTION_PRICE_FACTOR;

        String searchText =  Items.POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems(searchText);

        for (ItemStack potion : potionItems)
        {
            if (potion.getItem() instanceof PotionItem)
            {
                int price = ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor;
                potions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        return potions;
    }


    public static MarketFactory.DefaultMarketSetupDataGroup getLingeringPotions()
    {
        MarketFactory.DefaultMarketSetupDataGroup lingeringPotions = new MarketFactory.DefaultMarketSetupDataGroup("LingeringPotions");
        int potionPriceFactor = POTION_PRICE_FACTOR;

        String searchText =  Items.LINGERING_POTION.getDefaultInstance().getHoverName().getString();
        List<ItemStack> potionItems = ItemUtilities.getSearchCreativeItems(searchText);

        for (ItemStack potion : potionItems)
        {
            if (potion.getItem() instanceof LingeringPotionItem)
            {
                int price = ItemPriceCalculator.calculateItemPrice(potion) * potionPriceFactor;
                lingeringPotions.add(new MarketFactory.DefaultMarketSetupGeneratorData(potion, price, 0.2f, 0.1f));
            }
        }
        return lingeringPotions;
    }


    public static MarketFactory.DefaultMarketSetupDataGroup getMiscBlocks()
    {
        MarketFactory.DefaultMarketSetupDataGroup miscBlocks = new MarketFactory.DefaultMarketSetupDataGroup("MiscBlocks");



        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.OBSIDIAN, OBSIDIAN_PRICE, 0.2f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.DIRT, DIRT_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CLAY_BALL, CLAY_BALL_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBBLESTONE, COBBLESTONE_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.GRAVEL, GRAVEL_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SAND, SAND_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SOUL_SAND, SOUL_SAND_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.COBWEB, COBWEB_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BEDROCK, BEDROCK_PRICE, 0.2f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BARRIER, BARRIER_PRICE, 0.2f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BOOKSHELF, BOOKSHELF_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CHEST, CHEST_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.FURNACE, FURNACE_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.CRAFTING_TABLE, CRAFTING_TABLE_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.ENDER_CHEST, ENDER_CHEST_PRICE, 0.2f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BREWING_STAND, BREWING_STAND_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.SMOKER, SMOKER_PRICE, 0.02f, 0.1f));
        miscBlocks.add(new MarketFactory.DefaultMarketSetupGeneratorData(Items.BLAST_FURNACE, BLAST_FURNACE_PRICE, 0.02f, 0.1f));



        return miscBlocks;
    }


    // Dirt/obsidian/gravel/sand/soulSand
    // Arrows
    // Enchanted books
    // Foods
    // dyes





    public static class ItemPriceCalculator {

        public static int calculateItemPrice(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return 0;

            int basePrice = getBaseItemValue(stack);
            int enchantmentBonus = getEnchantmentValue(stack);
            int potionBonus = getPotionValue(stack);
            int nameBonus = stack.hasCustomHoverName() ? 10 : 0;
            int durabilityBonus = getDurabilityBonus(stack);

            int total = basePrice + enchantmentBonus + potionBonus + nameBonus + durabilityBonus;
            return total * stack.getCount();
        }

        public static int getBaseItemValue(ItemStack stack) {
            // You can assign specific values per item if needed
            if (stack.getItem() == Items.DIAMOND_SWORD) return 100;
            if (stack.getItem() == Items.NETHERITE_CHESTPLATE) return 500;
            if (stack.getItem() == Items.DIRT) return 1;
            return 10; // default fallback
        }

        public static int getEnchantmentValue(ItemStack stack) {
            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            int value = 0;
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                int level = entry.getValue();
                int rarityMultiplier = entry.getKey().getRarity().ordinal() + 1; // COMMON = 0, RARE = 2, etc.
                value += 10 * level * rarityMultiplier;
            }
            return value;
        }

        public static int getPotionValue(ItemStack stack) {
            if (!(stack.getItem() instanceof PotionItem)) return 0;
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
            int value = 0;
            for (MobEffectInstance effect : effects) {
                int amplifier = effect.getAmplifier() + 1;
                int duration = effect.getDuration() / 20; // ticks to seconds
                value += (amplifier * 5 + 2) * (duration / 10); // value grows with amplifier and duration
            }
            return value;
        }

        public static int getDurabilityBonus(ItemStack stack) {
            if (!stack.isDamageableItem()) return 0;
            int maxDurability = stack.getMaxDamage();
            int currentDurability = maxDurability - stack.getDamageValue();
            return (int)((currentDurability / (double)maxDurability) * 20); // max +20 bonus if fully intact
        }
    }

}

