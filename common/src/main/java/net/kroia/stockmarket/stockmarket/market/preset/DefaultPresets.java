package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Hardcoded default presets for first-time JSON generation.
 * Covers nearly every vanilla Minecraft 1.21.1 item, including enchanted books,
 * potions, splash potions, lingering potions, and tipped arrows.
 */
public class DefaultPresets {

    // All 16 dye/wool/concrete/etc. color variants
    private static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    // ── shorthand helpers ──────────────────────────────────────────────

    /** Shorthand for a simple MarketPreset (no components). */
    private static MarketPreset p(String id, float price, float abundance) {
        return new MarketPreset(id, price, abundance);
    }

    /** Generate colored variants by substituting {color} into the pattern. */
    private static List<MarketPreset> coloredPresets(String pattern, float price, float abundance) {
        List<MarketPreset> list = new ArrayList<>();
        for (String color : COLORS) {
            list.add(p(pattern.replace("{color}", color), price, abundance));
        }
        return list;
    }

    /**
     * Extract a MarketPreset from a programmatically-built ItemStack.
     * Uses MarketPreset.serializeItemStack() to capture component data.
     */
    private static MarketPreset fromStack(ItemStack stack, float price, float abundance) {
        JsonObject serialized = MarketPreset.serializeItemStack(stack);
        String itemId = serialized.get("id").getAsString();
        JsonObject components = serialized.has("components") ? serialized.getAsJsonObject("components") : null;
        return new MarketPreset(itemId, components, price, abundance);
    }

    /**
     * Create an enchanted book preset at a specific level.
     * Returns null if the enchantment holder cannot be resolved.
     */
    private static @Nullable MarketPreset enchantedBook(
            Registry<Enchantment> registry, ResourceKey<Enchantment> key,
            int level, float price, float abundance) {
        Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(key);
        if (holder.isEmpty()) return null;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return fromStack(book, price, abundance);
    }

    /**
     * Create a potion / splash potion / lingering potion / tipped arrow preset.
     */
    private static MarketPreset potionPreset(Item potionItem, Holder<Potion> potion, float price, float abundance) {
        ItemStack stack = PotionContents.createItemStack(potionItem, potion);
        return fromStack(stack, price, abundance);
    }

    // ── main generate method ───────────────────────────────────────────

    /**
     * Generates all default preset categories.
     *
     * @param registries RegistryAccess for data-driven registries (enchantments).
     *                   May be null; if null, the EnchantedBook category is skipped.
     * @return list of all preset categories
     */
    public static List<MarketPresetCategory> generate(@Nullable RegistryAccess registries) {
        List<MarketPresetCategory> categories = new ArrayList<>();

        // Simple item categories (no RegistryAccess needed)
        categories.add(commonBlock());
        categories.add(wood());
        categories.add(ore());
        categories.add(food());
        categories.add(crop());
        categories.add(sapling());
        categories.add(flower());
        categories.add(dye());
        categories.add(wool());
        categories.add(terracotta());
        categories.add(concrete());
        categories.add(glass());
        categories.add(candle());
        categories.add(tool());
        categories.add(armor());
        categories.add(combat());
        categories.add(redstone());
        categories.add(mobDrop());
        categories.add(brewing());
        categories.add(music());
        categories.add(transportation());
        categories.add(nether());
        categories.add(end());
        categories.add(ocean());
        categories.add(miscellaneous());
        categories.add(banner());
        categories.add(bed());

        // Registry-dependent categories (enchanted books need data-driven enchantment registry)
        if (registries != null) {
            categories.add(enchantedBooks(registries));
        }

        // Potion categories (use built-in registry, no RegistryAccess needed)
        categories.add(potions());
        categories.add(splashPotions());
        categories.add(lingeringPotions());
        categories.add(tippedArrows());

        return categories;
    }

    // ── 1. CommonBlock ─────────────────────────────────────────────────

    private static MarketPresetCategory commonBlock() {
        return new MarketPresetCategory("CommonBlock", List.of(
            p("minecraft:stone", 1, 150),
            p("minecraft:cobblestone", 0.5f, 200),
            p("minecraft:mossy_cobblestone", 3, 60),
            p("minecraft:stone_bricks", 2, 120),
            p("minecraft:mossy_stone_bricks", 3, 60),
            p("minecraft:cracked_stone_bricks", 2.5f, 80),
            p("minecraft:chiseled_stone_bricks", 3, 60),
            p("minecraft:smooth_stone", 2, 100),
            p("minecraft:deepslate", 1.5f, 120),
            p("minecraft:cobbled_deepslate", 1, 150),
            p("minecraft:deepslate_bricks", 2.5f, 80),
            p("minecraft:deepslate_tiles", 2.5f, 80),
            p("minecraft:polished_deepslate", 3, 60),
            p("minecraft:bricks", 3, 80),
            p("minecraft:mud_bricks", 2.5f, 80),
            p("minecraft:packed_mud", 2, 100),
            p("minecraft:sandstone", 2, 100),
            p("minecraft:red_sandstone", 2, 80),
            p("minecraft:smooth_sandstone", 2.5f, 80),
            p("minecraft:smooth_red_sandstone", 2.5f, 80),
            p("minecraft:prismarine", 5, 40),
            p("minecraft:prismarine_bricks", 6, 30),
            p("minecraft:dark_prismarine", 7, 25),
            p("minecraft:purpur_block", 5, 30),
            p("minecraft:purpur_pillar", 6, 25),
            p("minecraft:end_stone", 4, 40),
            p("minecraft:end_stone_bricks", 5, 30),
            p("minecraft:blackstone", 3, 60),
            p("minecraft:polished_blackstone", 4, 40),
            p("minecraft:polished_blackstone_bricks", 5, 30),
            p("minecraft:gilded_blackstone", 15, 10),
            p("minecraft:tuff", 1.5f, 100),
            p("minecraft:polished_tuff", 2.5f, 60),
            p("minecraft:tuff_bricks", 3, 50),
            p("minecraft:calcite", 2, 60),
            p("minecraft:dripstone_block", 3, 40),
            p("minecraft:dirt", 0.1f, 200),
            p("minecraft:coarse_dirt", 0.2f, 180),
            p("minecraft:rooted_dirt", 0.5f, 100),
            p("minecraft:grass_block", 0.3f, 180),
            p("minecraft:podzol", 1, 60),
            p("minecraft:mycelium", 2, 40),
            p("minecraft:mud", 0.5f, 120),
            p("minecraft:clay", 3, 60),
            p("minecraft:gravel", 0.5f, 150),
            p("minecraft:sand", 0.3f, 200),
            p("minecraft:red_sand", 0.5f, 120),
            p("minecraft:soul_sand", 2, 80),
            p("minecraft:soul_soil", 2, 80),
            p("minecraft:netherrack", 0.2f, 200),
            p("minecraft:basalt", 1.5f, 100),
            p("minecraft:smooth_basalt", 2, 80),
            p("minecraft:obsidian", 15, 20),
            p("minecraft:crying_obsidian", 12, 20),
            p("minecraft:ice", 2, 80),
            p("minecraft:packed_ice", 4, 40),
            p("minecraft:blue_ice", 8, 20),
            p("minecraft:snow_block", 1, 100),
            p("minecraft:moss_block", 3, 60),
            p("minecraft:sculk", 5, 30)
        ));
    }

    // ── 2. Wood ────────────────────────────────────────────────────────

    private static MarketPresetCategory wood() {
        // Wood types: oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo use _log
        // crimson, warped use _stem
        String[] logWoods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};
        String[] stemWoods = {"crimson", "warped"};

        List<MarketPreset> presets = new ArrayList<>();

        for (String wood : logWoods) {
            presets.add(p("minecraft:" + wood + "_log", 8, 80));
            presets.add(p("minecraft:stripped_" + wood + "_log", 9, 60));
            presets.add(p("minecraft:" + wood + "_planks", 2, 100));
        }
        // Bamboo uses bamboo_block instead of bamboo_log
        presets.add(p("minecraft:bamboo_block", 8, 60));
        presets.add(p("minecraft:stripped_bamboo_block", 9, 40));
        presets.add(p("minecraft:bamboo_planks", 2, 80));

        for (String wood : stemWoods) {
            presets.add(p("minecraft:" + wood + "_stem", 8, 60));
            presets.add(p("minecraft:stripped_" + wood + "_stem", 9, 40));
            presets.add(p("minecraft:" + wood + "_planks", 2, 80));
        }

        return new MarketPresetCategory("Wood", presets);
    }

    // ── 3. Ore ─────────────────────────────────────────────────────────

    private static MarketPresetCategory ore() {
        return new MarketPresetCategory("Ore", List.of(
            // Raw materials and processed forms
            p("minecraft:coal", 8, 50),
            p("minecraft:raw_iron", 12, 35),
            p("minecraft:raw_copper", 7, 45),
            p("minecraft:raw_gold", 35, 18),
            p("minecraft:iron_ingot", 15, 30),
            p("minecraft:copper_ingot", 9, 40),
            p("minecraft:gold_ingot", 40, 15),
            p("minecraft:diamond", 160, 5),
            p("minecraft:emerald", 100, 8),
            p("minecraft:lapis_lazuli", 8, 30),
            p("minecraft:redstone", 4, 50),
            p("minecraft:quartz", 10, 30),
            p("minecraft:netherite_scrap", 600, 1),
            p("minecraft:netherite_ingot", 2500, 1),
            p("minecraft:amethyst_shard", 10, 20),
            p("minecraft:iron_nugget", 2, 50),
            p("minecraft:gold_nugget", 5, 30),
            p("minecraft:prismarine_shard", 10, 15),
            p("minecraft:prismarine_crystals", 12, 12),
            p("minecraft:glowstone_dust", 6, 30),
            p("minecraft:ancient_debris", 600, 1),
            // Overworld ores
            p("minecraft:coal_ore", 5, 40),
            p("minecraft:iron_ore", 10, 25),
            p("minecraft:copper_ore", 6, 35),
            p("minecraft:gold_ore", 30, 12),
            p("minecraft:diamond_ore", 120, 4),
            p("minecraft:emerald_ore", 80, 5),
            p("minecraft:lapis_ore", 6, 25),
            p("minecraft:redstone_ore", 3, 40),
            // Deepslate ores
            p("minecraft:deepslate_coal_ore", 6, 35),
            p("minecraft:deepslate_iron_ore", 12, 20),
            p("minecraft:deepslate_copper_ore", 7, 30),
            p("minecraft:deepslate_gold_ore", 35, 10),
            p("minecraft:deepslate_diamond_ore", 140, 3),
            p("minecraft:deepslate_emerald_ore", 90, 4),
            p("minecraft:deepslate_lapis_ore", 7, 20),
            p("minecraft:deepslate_redstone_ore", 4, 35),
            // Nether ores
            p("minecraft:nether_gold_ore", 25, 15),
            p("minecraft:nether_quartz_ore", 8, 25)
        ));
    }

    // ── 4. Food ────────────────────────────────────────────────────────

    private static MarketPresetCategory food() {
        return new MarketPresetCategory("Food", List.of(
            p("minecraft:apple", 4, 50),
            p("minecraft:golden_apple", 350, 2),
            p("minecraft:enchanted_golden_apple", 1000, 0.5f),
            p("minecraft:golden_carrot", 10, 20),
            p("minecraft:bread", 6, 50),
            p("minecraft:cookie", 1, 60),
            p("minecraft:cake", 10, 15),
            p("minecraft:pumpkin_pie", 10, 15),
            p("minecraft:melon_slice", 2, 60),
            p("minecraft:dried_kelp", 1, 80),
            p("minecraft:cooked_beef", 6, 40),
            p("minecraft:cooked_porkchop", 6, 40),
            p("minecraft:cooked_chicken", 5, 40),
            p("minecraft:cooked_mutton", 5, 40),
            p("minecraft:cooked_rabbit", 6, 30),
            p("minecraft:cooked_cod", 5, 30),
            p("minecraft:cooked_salmon", 6, 30),
            p("minecraft:baked_potato", 4, 50),
            p("minecraft:beetroot_soup", 6, 20),
            p("minecraft:mushroom_stew", 6, 20),
            p("minecraft:rabbit_stew", 12, 10),
            p("minecraft:suspicious_stew", 8, 15),
            // Raw meats
            p("minecraft:beef", 3, 50),
            p("minecraft:porkchop", 3, 50),
            p("minecraft:chicken", 2, 60),
            p("minecraft:mutton", 2, 50),
            p("minecraft:rabbit", 3, 30),
            p("minecraft:cod", 2, 40),
            p("minecraft:salmon", 3, 35),
            // Misc food
            p("minecraft:rotten_flesh", 1, 80),
            p("minecraft:spider_eye", 3, 25),
            p("minecraft:poisonous_potato", 1, 40),
            p("minecraft:chorus_fruit", 8, 15),
            p("minecraft:sweet_berries", 2, 50),
            p("minecraft:glow_berries", 3, 30),
            p("minecraft:honey_bottle", 8, 20),
            p("minecraft:sugar", 2, 60),
            p("minecraft:tropical_fish", 4, 20),
            p("minecraft:pufferfish", 5, 15)
        ));
    }

    // ── 5. Crop ────────────────────────────────────────────────────────

    private static MarketPresetCategory crop() {
        return new MarketPresetCategory("Crop", List.of(
            p("minecraft:wheat", 2, 80),
            p("minecraft:wheat_seeds", 1, 100),
            p("minecraft:beetroot", 2, 60),
            p("minecraft:beetroot_seeds", 1, 80),
            p("minecraft:carrot", 3, 60),
            p("minecraft:potato", 3, 60),
            p("minecraft:melon", 5, 40),
            p("minecraft:pumpkin", 5, 40),
            p("minecraft:cocoa_beans", 2, 40),
            p("minecraft:sugar_cane", 2, 70),
            p("minecraft:bamboo", 2, 80),
            p("minecraft:cactus", 3, 50),
            p("minecraft:kelp", 1, 80),
            p("minecraft:sea_pickle", 3, 30),
            p("minecraft:nether_wart", 4, 40),
            p("minecraft:chorus_flower", 10, 15),
            p("minecraft:torchflower", 8, 20),
            p("minecraft:pitcher_plant", 8, 20),
            p("minecraft:torchflower_seeds", 5, 30),
            p("minecraft:pitcher_pod", 5, 30),
            p("minecraft:brown_mushroom", 2, 50),
            p("minecraft:red_mushroom", 2, 50)
        ));
    }

    // ── 6. Sapling ─────────────────────────────────────────────────────

    private static MarketPresetCategory sapling() {
        return new MarketPresetCategory("Sapling", List.of(
            p("minecraft:oak_sapling", 3, 60),
            p("minecraft:spruce_sapling", 3, 60),
            p("minecraft:birch_sapling", 3, 60),
            p("minecraft:jungle_sapling", 4, 45),
            p("minecraft:acacia_sapling", 4, 45),
            p("minecraft:dark_oak_sapling", 4, 45),
            p("minecraft:mangrove_propagule", 4, 40),
            p("minecraft:cherry_sapling", 5, 35),
            p("minecraft:azalea", 6, 30),
            p("minecraft:flowering_azalea", 8, 25)
        ));
    }

    // ── 7. Flower ──────────────────────────────────────────────────────

    private static MarketPresetCategory flower() {
        return new MarketPresetCategory("Flower", List.of(
            p("minecraft:dandelion", 1, 80),
            p("minecraft:poppy", 1, 80),
            p("minecraft:blue_orchid", 2, 40),
            p("minecraft:allium", 2, 40),
            p("minecraft:azure_bluet", 2, 40),
            p("minecraft:red_tulip", 2, 40),
            p("minecraft:orange_tulip", 2, 40),
            p("minecraft:white_tulip", 2, 40),
            p("minecraft:pink_tulip", 2, 40),
            p("minecraft:oxeye_daisy", 2, 40),
            p("minecraft:cornflower", 2, 40),
            p("minecraft:lily_of_the_valley", 2, 40),
            p("minecraft:wither_rose", 50, 5),
            p("minecraft:sunflower", 2, 30),
            p("minecraft:lilac", 2, 30),
            p("minecraft:rose_bush", 2, 30),
            p("minecraft:peony", 2, 30),
            p("minecraft:spore_blossom", 5, 15),
            p("minecraft:pink_petals", 2, 30),
            p("minecraft:lily_pad", 2, 40),
            p("minecraft:vine", 1, 60),
            p("minecraft:glow_lichen", 2, 30),
            p("minecraft:hanging_roots", 1, 40),
            p("minecraft:fern", 1, 60),
            p("minecraft:large_fern", 1, 40),
            p("minecraft:dead_bush", 1, 50),
            p("minecraft:tall_grass", 1, 60)
        ));
    }

    // ── 8. Dye ─────────────────────────────────────────────────────────

    private static MarketPresetCategory dye() {
        return new MarketPresetCategory("Dye", coloredPresets("minecraft:{color}_dye", 3, 30));
    }

    // ── 9. Wool ────────────────────────────────────────────────────────

    private static MarketPresetCategory wool() {
        return new MarketPresetCategory("Wool", coloredPresets("minecraft:{color}_wool", 6, 40));
    }

    // ── 10. Terracotta ─────────────────────────────────────────────────

    private static MarketPresetCategory terracotta() {
        List<MarketPreset> presets = new ArrayList<>();
        presets.add(p("minecraft:terracotta", 3, 40));
        presets.addAll(coloredPresets("minecraft:{color}_terracotta", 3, 40));
        return new MarketPresetCategory("Terracotta", presets);
    }

    // ── 11. Concrete ───────────────────────────────────────────────────

    private static MarketPresetCategory concrete() {
        return new MarketPresetCategory("Concrete", coloredPresets("minecraft:{color}_concrete", 3, 40));
    }

    // ── 12. Glass ──────────────────────────────────────────────────────

    private static MarketPresetCategory glass() {
        List<MarketPreset> presets = new ArrayList<>();
        presets.add(p("minecraft:glass", 1, 60));
        presets.addAll(coloredPresets("minecraft:{color}_stained_glass", 1, 60));
        return new MarketPresetCategory("Glass", presets);
    }

    // ── 13. Candle ─────────────────────────────────────────────────────

    private static MarketPresetCategory candle() {
        List<MarketPreset> presets = new ArrayList<>();
        presets.add(p("minecraft:candle", 5, 20));
        presets.addAll(coloredPresets("minecraft:{color}_candle", 5, 20));
        return new MarketPresetCategory("Candle", presets);
    }

    // ── 14. Tool ───────────────────────────────────────────────────────

    private static MarketPresetCategory tool() {
        String[] materials = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
        float[] prices = {5, 8, 25, 45, 300, 3000};
        float[] abundances = {30, 25, 20, 15, 5, 1};
        String[] toolTypes = {"_pickaxe", "_shovel", "_axe", "_hoe", "_sword"};

        List<MarketPreset> presets = new ArrayList<>();
        for (int i = 0; i < materials.length; i++) {
            for (String toolType : toolTypes) {
                presets.add(p("minecraft:" + materials[i] + toolType, prices[i], abundances[i]));
            }
        }

        // Additional tools
        presets.add(p("minecraft:shears", 15, 20));
        presets.add(p("minecraft:fishing_rod", 12, 20));
        presets.add(p("minecraft:flint_and_steel", 15, 15));
        presets.add(p("minecraft:compass", 20, 15));
        presets.add(p("minecraft:clock", 25, 12));
        presets.add(p("minecraft:spyglass", 30, 10));
        presets.add(p("minecraft:brush", 20, 15));
        presets.add(p("minecraft:lead", 10, 20));
        presets.add(p("minecraft:name_tag", 30, 5));
        presets.add(p("minecraft:saddle", 40, 3));
        presets.add(p("minecraft:mace", 2000, 1));

        return new MarketPresetCategory("Tool", presets);
    }

    // ── 15. Armor ──────────────────────────────────────────────────────

    private static MarketPresetCategory armor() {
        // material -> {helmet, chestplate, leggings, boots}
        String[][] armorSets = {
            {"leather", "25", "35", "30", "25"},
            {"chainmail", "30", "45", "40", "30"},
            {"iron", "35", "55", "45", "30"},
            {"golden", "55", "75", "65", "50"},
            {"diamond", "300", "450", "400", "250"},
            {"netherite", "3000", "4500", "4000", "2500"},
        };
        String[] armorTypes = {"_helmet", "_chestplate", "_leggings", "_boots"};
        float[] abundances = {20, 15, 12, 8, 3, 1};

        List<MarketPreset> presets = new ArrayList<>();
        for (int i = 0; i < armorSets.length; i++) {
            String material = armorSets[i][0];
            for (int j = 0; j < armorTypes.length; j++) {
                float price = Float.parseFloat(armorSets[i][j + 1]);
                presets.add(p("minecraft:" + material + armorTypes[j], price, abundances[i]));
            }
        }

        // Special armor
        presets.add(p("minecraft:turtle_helmet", 200, 3));
        presets.add(p("minecraft:shield", 30, 15));
        presets.add(p("minecraft:elytra", 5000, 0.5f));
        presets.add(p("minecraft:wolf_armor", 150, 3));

        return new MarketPresetCategory("Armor", presets);
    }

    // ── 16. Combat ─────────────────────────────────────────────────────

    private static MarketPresetCategory combat() {
        return new MarketPresetCategory("Combat", List.of(
            p("minecraft:bow", 20, 15),
            p("minecraft:crossbow", 30, 10),
            p("minecraft:trident", 500, 2),
            p("minecraft:arrow", 2, 20),
            p("minecraft:spectral_arrow", 8, 10),
            p("minecraft:firework_rocket", 5, 15),
            p("minecraft:firework_star", 8, 10),
            p("minecraft:tnt", 15, 10),
            p("minecraft:end_crystal", 200, 3),
            p("minecraft:totem_of_undying", 500, 2)
        ));
    }

    // ── 17. Redstone ───────────────────────────────────────────────────

    private static MarketPresetCategory redstone() {
        return new MarketPresetCategory("Redstone", List.of(
            p("minecraft:redstone", 4, 50),
            p("minecraft:redstone_torch", 5, 40),
            p("minecraft:redstone_block", 35, 15),
            p("minecraft:repeater", 8, 30),
            p("minecraft:comparator", 12, 25),
            p("minecraft:piston", 15, 20),
            p("minecraft:sticky_piston", 20, 15),
            p("minecraft:observer", 15, 20),
            p("minecraft:dropper", 10, 25),
            p("minecraft:dispenser", 12, 20),
            p("minecraft:hopper", 25, 15),
            p("minecraft:lever", 3, 40),
            p("minecraft:stone_button", 3, 40),
            p("minecraft:oak_button", 3, 40),
            p("minecraft:stone_pressure_plate", 5, 35),
            p("minecraft:oak_pressure_plate", 4, 35),
            p("minecraft:light_weighted_pressure_plate", 30, 10),
            p("minecraft:heavy_weighted_pressure_plate", 15, 15),
            p("minecraft:tripwire_hook", 5, 30),
            p("minecraft:trapped_chest", 12, 20),
            p("minecraft:daylight_detector", 15, 15),
            p("minecraft:note_block", 10, 25),
            p("minecraft:jukebox", 50, 5),
            p("minecraft:target", 12, 15),
            p("minecraft:sculk_sensor", 15, 10),
            p("minecraft:calibrated_sculk_sensor", 25, 5),
            p("minecraft:lightning_rod", 15, 15),
            p("minecraft:bell", 50, 5),
            p("minecraft:lectern", 20, 10)
        ));
    }

    // ── 18. MobDrop ────────────────────────────────────────────────────

    private static MarketPresetCategory mobDrop() {
        return new MarketPresetCategory("MobDrop", List.of(
            p("minecraft:leather", 8, 30),
            p("minecraft:rabbit_hide", 5, 20),
            p("minecraft:bone", 4, 40),
            p("minecraft:bone_meal", 3, 50),
            p("minecraft:feather", 4, 50),
            p("minecraft:string", 4, 40),
            p("minecraft:egg", 2, 50),
            p("minecraft:slime_ball", 10, 15),
            p("minecraft:magma_cream", 15, 10),
            p("minecraft:ender_pearl", 10, 8),
            p("minecraft:blaze_rod", 20, 8),
            p("minecraft:blaze_powder", 12, 10),
            p("minecraft:ghast_tear", 200, 2),
            p("minecraft:phantom_membrane", 15, 10),
            p("minecraft:rabbit_foot", 15, 10),
            p("minecraft:ink_sac", 6, 20),
            p("minecraft:glow_ink_sac", 10, 15),
            p("minecraft:gunpowder", 6, 25),
            p("minecraft:nether_star", 2000, 0.5f),
            p("minecraft:dragon_breath", 300, 1),
            p("minecraft:shulker_shell", 200, 2),
            p("minecraft:wither_skeleton_skull", 100, 2),
            p("minecraft:skeleton_skull", 20, 8),
            p("minecraft:zombie_head", 20, 8),
            p("minecraft:creeper_head", 20, 8),
            p("minecraft:piglin_head", 20, 8),
            p("minecraft:ender_eye", 15, 8),
            p("minecraft:experience_bottle", 10, 10),
            p("minecraft:heart_of_the_sea", 500, 0.5f),
            p("minecraft:nautilus_shell", 30, 5),
            p("minecraft:scute", 15, 10),
            p("minecraft:armadillo_scute", 12, 12),
            p("minecraft:breeze_rod", 30, 5),
            p("minecraft:trial_key", 40, 5),
            p("minecraft:ominous_trial_key", 100, 2),
            p("minecraft:wind_charge", 15, 8),
            p("minecraft:goat_horn", 25, 5)
        ));
    }

    // ── 19. Brewing ────────────────────────────────────────────────────

    private static MarketPresetCategory brewing() {
        return new MarketPresetCategory("Brewing", List.of(
            p("minecraft:glass_bottle", 5, 30),
            p("minecraft:fermented_spider_eye", 10, 20),
            p("minecraft:glistering_melon_slice", 15, 15),
            p("minecraft:brewing_stand", 25, 10),
            p("minecraft:cauldron", 30, 10)
        ));
    }

    // ── 20. Music ──────────────────────────────────────────────────────

    private static MarketPresetCategory music() {
        return new MarketPresetCategory("Music", List.of(
            p("minecraft:music_disc_13", 150, 1),
            p("minecraft:music_disc_cat", 150, 1),
            p("minecraft:music_disc_blocks", 150, 1),
            p("minecraft:music_disc_chirp", 150, 1),
            p("minecraft:music_disc_far", 150, 1),
            p("minecraft:music_disc_mall", 150, 1),
            p("minecraft:music_disc_mellohi", 150, 1),
            p("minecraft:music_disc_stal", 150, 1),
            p("minecraft:music_disc_strad", 150, 1),
            p("minecraft:music_disc_ward", 150, 1),
            p("minecraft:music_disc_11", 200, 0.8f),
            p("minecraft:music_disc_wait", 150, 1),
            p("minecraft:music_disc_otherside", 400, 0.5f),
            p("minecraft:music_disc_5", 300, 0.5f),
            p("minecraft:music_disc_pigstep", 500, 0.5f),
            p("minecraft:music_disc_relic", 350, 0.5f),
            p("minecraft:music_disc_creator", 200, 1),
            p("minecraft:music_disc_creator_music_box", 250, 0.8f),
            p("minecraft:music_disc_precipice", 200, 1)
        ));
    }

    // ── 21. Transportation ─────────────────────────────────────────────

    private static MarketPresetCategory transportation() {
        String[] boatWoods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};

        List<MarketPreset> presets = new ArrayList<>();

        // Boats
        for (String wood : boatWoods) {
            presets.add(p("minecraft:" + wood + "_boat", 15, 20));
        }
        presets.add(p("minecraft:bamboo_raft", 15, 20));

        // Chest boats
        for (String wood : boatWoods) {
            presets.add(p("minecraft:" + wood + "_chest_boat", 20, 15));
        }
        presets.add(p("minecraft:bamboo_chest_raft", 20, 15));

        // Minecarts and rails
        presets.add(p("minecraft:minecart", 20, 15));
        presets.add(p("minecraft:chest_minecart", 25, 12));
        presets.add(p("minecraft:furnace_minecart", 25, 12));
        presets.add(p("minecraft:tnt_minecart", 30, 10));
        presets.add(p("minecraft:hopper_minecart", 50, 8));
        presets.add(p("minecraft:rail", 5, 30));
        presets.add(p("minecraft:powered_rail", 12, 15));
        presets.add(p("minecraft:detector_rail", 10, 15));
        presets.add(p("minecraft:activator_rail", 10, 15));

        return new MarketPresetCategory("Transportation", presets);
    }

    // ── 22. Nether ─────────────────────────────────────────────────────

    private static MarketPresetCategory nether() {
        return new MarketPresetCategory("Nether", List.of(
            p("minecraft:nether_bricks", 5, 30),
            p("minecraft:red_nether_bricks", 6, 25),
            p("minecraft:nether_wart_block", 5, 25),
            p("minecraft:warped_wart_block", 5, 25),
            p("minecraft:shroomlight", 8, 15),
            p("minecraft:glowstone", 10, 20),
            p("minecraft:magma_block", 5, 25),
            p("minecraft:crying_obsidian", 12, 15),
            p("minecraft:respawn_anchor", 100, 5),
            p("minecraft:lodestone", 200, 3),
            p("minecraft:wither_skeleton_skull", 100, 2)
        ));
    }

    // ── 23. End ────────────────────────────────────────────────────────

    private static MarketPresetCategory end() {
        return new MarketPresetCategory("End", List.of(
            p("minecraft:end_stone", 4, 20),
            p("minecraft:end_stone_bricks", 5, 15),
            p("minecraft:purpur_block", 5, 15),
            p("minecraft:purpur_pillar", 6, 12),
            p("minecraft:chorus_plant", 3, 20),
            p("minecraft:chorus_flower", 10, 10),
            p("minecraft:end_rod", 8, 12),
            p("minecraft:dragon_egg", 5000, 0.1f),
            p("minecraft:shulker_box", 250, 2)
        ));
    }

    // ── 24. Ocean ──────────────────────────────────────────────────────

    private static MarketPresetCategory ocean() {
        String[] coralTypes = {"tube", "brain", "bubble", "fire", "horn"};

        List<MarketPreset> presets = new ArrayList<>();
        presets.add(p("minecraft:prismarine", 5, 15));
        presets.add(p("minecraft:prismarine_bricks", 6, 12));
        presets.add(p("minecraft:dark_prismarine", 7, 10));
        presets.add(p("minecraft:sea_lantern", 12, 10));
        presets.add(p("minecraft:conduit", 500, 1));
        presets.add(p("minecraft:sponge", 50, 3));
        presets.add(p("minecraft:wet_sponge", 45, 3));

        // Coral blocks (living)
        for (String coral : coralTypes) {
            presets.add(p("minecraft:" + coral + "_coral_block", 5, 10));
        }
        // Dead coral blocks
        for (String coral : coralTypes) {
            presets.add(p("minecraft:dead_" + coral + "_coral_block", 3, 12));
        }

        presets.add(p("minecraft:turtle_egg", 15, 5));

        return new MarketPresetCategory("Ocean", presets);
    }

    // ── 25. Miscellaneous ──────────────────────────────────────────────

    private static MarketPresetCategory miscellaneous() {
        return new MarketPresetCategory("Miscellaneous", List.of(
            // Paper and books
            p("minecraft:book", 6, 30),
            p("minecraft:paper", 3, 50),
            p("minecraft:map", 10, 20),
            p("minecraft:writable_book", 8, 20),
            // Buckets
            p("minecraft:bucket", 15, 20),
            p("minecraft:water_bucket", 16, 15),
            p("minecraft:lava_bucket", 25, 10),
            p("minecraft:milk_bucket", 18, 15),
            p("minecraft:powder_snow_bucket", 20, 10),
            // Containers and storage
            p("minecraft:bundle", 10, 15),
            p("minecraft:ender_chest", 200, 3),
            p("minecraft:chest", 8, 30),
            p("minecraft:barrel", 8, 25),
            // Workstations
            p("minecraft:crafting_table", 5, 40),
            p("minecraft:furnace", 8, 30),
            p("minecraft:blast_furnace", 20, 15),
            p("minecraft:smoker", 15, 15),
            p("minecraft:stonecutter", 15, 15),
            p("minecraft:grindstone", 12, 15),
            p("minecraft:smithing_table", 15, 12),
            p("minecraft:anvil", 50, 8),
            p("minecraft:enchanting_table", 200, 3),
            p("minecraft:bookshelf", 15, 20),
            // Lighting and decoration
            p("minecraft:campfire", 8, 20),
            p("minecraft:soul_campfire", 10, 15),
            p("minecraft:lantern", 8, 25),
            p("minecraft:soul_lantern", 10, 20),
            p("minecraft:torch", 1, 80),
            p("minecraft:soul_torch", 2, 50),
            // Building utilities
            p("minecraft:ladder", 2, 50),
            p("minecraft:scaffolding", 3, 40),
            p("minecraft:chain", 6, 20),
            p("minecraft:iron_bars", 4, 30),
            p("minecraft:glass_pane", 1, 60),
            // Decorative
            p("minecraft:painting", 10, 15),
            p("minecraft:item_frame", 8, 20),
            p("minecraft:glow_item_frame", 15, 12),
            p("minecraft:flower_pot", 3, 30),
            p("minecraft:armor_stand", 10, 15),
            // Compact blocks
            p("minecraft:bone_block", 12, 20),
            p("minecraft:hay_block", 10, 25),
            p("minecraft:dried_kelp_block", 5, 30),
            p("minecraft:honeycomb_block", 15, 15),
            p("minecraft:honey_block", 20, 10),
            p("minecraft:slime_block", 40, 8),
            // Metal blocks
            p("minecraft:copper_block", 80, 10),
            p("minecraft:exposed_copper", 70, 10),
            p("minecraft:weathered_copper", 60, 10),
            p("minecraft:oxidized_copper", 50, 12),
            p("minecraft:waxed_copper_block", 85, 8),
            p("minecraft:cut_copper", 85, 8),
            p("minecraft:raw_iron_block", 100, 8),
            p("minecraft:raw_copper_block", 60, 10),
            p("minecraft:raw_gold_block", 300, 4),
            p("minecraft:iron_block", 130, 8),
            p("minecraft:gold_block", 350, 4),
            p("minecraft:diamond_block", 1400, 2),
            p("minecraft:emerald_block", 900, 3),
            p("minecraft:lapis_block", 70, 10),
            p("minecraft:netherite_block", 22000, 0.5f),
            p("minecraft:coal_block", 70, 15),
            p("minecraft:amethyst_block", 40, 12),
            // Misc special blocks
            p("minecraft:budding_amethyst", 100, 2),
            p("minecraft:tinted_glass", 15, 15),
            p("minecraft:pointed_dripstone", 5, 20),
            p("minecraft:decorated_pot", 10, 10),
            p("minecraft:suspicious_gravel", 8, 8),
            p("minecraft:suspicious_sand", 8, 8)
        ));
    }

    // ── 26. Banner ─────────────────────────────────────────────────────

    private static MarketPresetCategory banner() {
        return new MarketPresetCategory("Banner", coloredPresets("minecraft:{color}_banner", 10, 15));
    }

    // ── 27. Bed ────────────────────────────────────────────────────────

    private static MarketPresetCategory bed() {
        return new MarketPresetCategory("Bed", coloredPresets("minecraft:{color}_bed", 15, 20));
    }

    // ── 28. EnchantedBook (requires RegistryAccess) ────────────────────

    /**
     * Generates enchanted book presets for all enchantments at max level.
     * Price scales with max level: base 30 + 40 per level.
     */
    private static MarketPresetCategory enchantedBooks(RegistryAccess registries) {
        Registry<Enchantment> reg = registries.registryOrThrow(Registries.ENCHANTMENT);
        List<MarketPreset> presets = new ArrayList<>();

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : reg.entrySet()) {
            try {
                int maxLevel = entry.getValue().getMaxLevel();
                float price = 30 + maxLevel * 40;
                MarketPreset preset = enchantedBook(reg, entry.getKey(), maxLevel, price, 3f);
                if (preset != null) presets.add(preset);
            } catch (Exception ignored) {
                // Skip enchantments that fail to resolve
            }
        }

        // Sort by price for consistent ordering
        presets.sort(Comparator.comparing(MarketPreset::getDefaultPrice));
        return new MarketPresetCategory("EnchantedBook", presets);
    }

    // ── 29. Potion ─────────────────────────────────────────────────────

    /**
     * Generates presets for all regular potions with effects.
     * Skips water, mundane, thick, and awkward potions (no effects).
     */
    private static MarketPresetCategory potions() {
        List<MarketPreset> presets = new ArrayList<>();
        for (Map.Entry<ResourceKey<Potion>, Potion> entry : BuiltInRegistries.POTION.entrySet()) {
            if (entry.getValue().getEffects().isEmpty()) continue;
            try {
                Holder<Potion> holder = BuiltInRegistries.POTION.getHolderOrThrow(entry.getKey());
                presets.add(potionPreset(Items.POTION, holder, 20, 5f));
            } catch (Exception ignored) {
                // Skip potions that fail to resolve
            }
        }
        presets.sort(Comparator.comparing(MarketPreset::getItemId));
        return new MarketPresetCategory("Potion", presets);
    }

    // ── 30. SplashPotion ───────────────────────────────────────────────

    /**
     * Generates presets for all splash potions with effects.
     * Price is 1.5x regular potion.
     */
    private static MarketPresetCategory splashPotions() {
        List<MarketPreset> presets = new ArrayList<>();
        for (Map.Entry<ResourceKey<Potion>, Potion> entry : BuiltInRegistries.POTION.entrySet()) {
            if (entry.getValue().getEffects().isEmpty()) continue;
            try {
                Holder<Potion> holder = BuiltInRegistries.POTION.getHolderOrThrow(entry.getKey());
                presets.add(potionPreset(Items.SPLASH_POTION, holder, 30, 4f));
            } catch (Exception ignored) {
            }
        }
        presets.sort(Comparator.comparing(MarketPreset::getItemId));
        return new MarketPresetCategory("SplashPotion", presets);
    }

    // ── 31. LingeringPotion ────────────────────────────────────────────

    /**
     * Generates presets for all lingering potions with effects.
     * Price is 2x regular potion.
     */
    private static MarketPresetCategory lingeringPotions() {
        List<MarketPreset> presets = new ArrayList<>();
        for (Map.Entry<ResourceKey<Potion>, Potion> entry : BuiltInRegistries.POTION.entrySet()) {
            if (entry.getValue().getEffects().isEmpty()) continue;
            try {
                Holder<Potion> holder = BuiltInRegistries.POTION.getHolderOrThrow(entry.getKey());
                presets.add(potionPreset(Items.LINGERING_POTION, holder, 40, 3f));
            } catch (Exception ignored) {
            }
        }
        presets.sort(Comparator.comparing(MarketPreset::getItemId));
        return new MarketPresetCategory("LingeringPotion", presets);
    }

    // ── 32. TippedArrow ────────────────────────────────────────────────

    /**
     * Generates presets for all tipped arrows with effects.
     * Price is 1.5x regular potion.
     */
    private static MarketPresetCategory tippedArrows() {
        List<MarketPreset> presets = new ArrayList<>();
        for (Map.Entry<ResourceKey<Potion>, Potion> entry : BuiltInRegistries.POTION.entrySet()) {
            if (entry.getValue().getEffects().isEmpty()) continue;
            try {
                Holder<Potion> holder = BuiltInRegistries.POTION.getHolderOrThrow(entry.getKey());
                presets.add(potionPreset(Items.TIPPED_ARROW, holder, 30, 4f));
            } catch (Exception ignored) {
            }
        }
        presets.sort(Comparator.comparing(MarketPreset::getItemId));
        return new MarketPresetCategory("TippedArrow", presets);
    }
}
