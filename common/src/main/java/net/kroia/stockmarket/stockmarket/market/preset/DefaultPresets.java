package net.kroia.stockmarket.stockmarket.market.preset;

import java.util.ArrayList;
import java.util.List;

// Hardcoded default presets for first-time JSON generation
public class DefaultPresets {

    public static List<MarketPresetCategory> generate() {
        List<MarketPresetCategory> categories = new ArrayList<>();
        categories.add(commonBlock());
        categories.add(ore());
        categories.add(food());
        categories.add(gardening());
        categories.add(animalLoot());
        return categories;
    }

    private static MarketPresetCategory commonBlock() {
        float log = 8;
        float plank = log / 4;
        return new MarketPresetCategory("CommonBlock", List.of(
            new MarketPreset("minecraft:oak_log", log, 100),
            new MarketPreset("minecraft:spruce_log", log, 100),
            new MarketPreset("minecraft:birch_log", log, 100),
            new MarketPreset("minecraft:jungle_log", log, 80),
            new MarketPreset("minecraft:acacia_log", log, 80),
            new MarketPreset("minecraft:dark_oak_log", log, 80),
            new MarketPreset("minecraft:mangrove_log", log, 60),
            new MarketPreset("minecraft:cherry_log", log, 60),
            new MarketPreset("minecraft:oak_planks", plank, 120),
            new MarketPreset("minecraft:stone", 1, 150),
            new MarketPreset("minecraft:cobblestone", 0.5f, 200),
            new MarketPreset("minecraft:sand", 0.3f, 200),
            new MarketPreset("minecraft:dirt", 0.3f, 200),
            new MarketPreset("minecraft:gravel", 1, 150),
            new MarketPreset("minecraft:white_wool", 6, 40),
            new MarketPreset("minecraft:glass", 0.3f, 100),
            new MarketPreset("minecraft:obsidian", 12, 20),
            new MarketPreset("minecraft:netherrack", 1, 150)
        ));
    }

    private static MarketPresetCategory ore() {
        return new MarketPresetCategory("Ore", List.of(
            new MarketPreset("minecraft:coal", 8, 50),
            new MarketPreset("minecraft:iron_ingot", 15, 30),
            new MarketPreset("minecraft:copper_ingot", 9, 40),
            new MarketPreset("minecraft:gold_ingot", 40, 15),
            new MarketPreset("minecraft:diamond", 160, 5),
            new MarketPreset("minecraft:emerald", 100, 8),
            new MarketPreset("minecraft:lapis_lazuli", 8, 30),
            new MarketPreset("minecraft:ancient_debris", 600, 1),
            new MarketPreset("minecraft:netherite_scrap", 600, 1),
            new MarketPreset("minecraft:redstone", 4, 50),
            new MarketPreset("minecraft:quartz", 10, 30),
            new MarketPreset("minecraft:prismarine_shard", 10, 15),
            new MarketPreset("minecraft:amethyst_shard", 10, 20)
        ));
    }

    private static MarketPresetCategory food() {
        return new MarketPresetCategory("Food", List.of(
            new MarketPreset("minecraft:cooked_beef", 5, 40),
            new MarketPreset("minecraft:cooked_chicken", 5, 40),
            new MarketPreset("minecraft:cooked_porkchop", 5, 40),
            new MarketPreset("minecraft:cooked_mutton", 5, 40),
            new MarketPreset("minecraft:cooked_rabbit", 5, 30),
            new MarketPreset("minecraft:cooked_salmon", 5, 30),
            new MarketPreset("minecraft:cooked_cod", 5, 30),
            new MarketPreset("minecraft:bread", 6, 50),
            new MarketPreset("minecraft:cookie", 1, 60),
            new MarketPreset("minecraft:cake", 10, 15),
            new MarketPreset("minecraft:pumpkin_pie", 10, 15),
            new MarketPreset("minecraft:apple", 4, 50),
            new MarketPreset("minecraft:golden_apple", 350, 2),
            new MarketPreset("minecraft:enchanted_golden_apple", 1000, 0.5f),
            new MarketPreset("minecraft:golden_carrot", 10, 20),
            new MarketPreset("minecraft:sugar", 2, 60),
            new MarketPreset("minecraft:melon_slice", 2, 60)
        ));
    }

    private static MarketPresetCategory gardening() {
        return new MarketPresetCategory("Gardening", List.of(
            new MarketPreset("minecraft:bamboo", 2, 80),
            new MarketPreset("minecraft:wheat_seeds", 2, 100),
            new MarketPreset("minecraft:wheat", 2, 80),
            new MarketPreset("minecraft:potato", 3, 60),
            new MarketPreset("minecraft:carrot", 3, 60),
            new MarketPreset("minecraft:beetroot", 2, 60),
            new MarketPreset("minecraft:sugar_cane", 2, 70),
            new MarketPreset("minecraft:cocoa_beans", 2, 40),
            new MarketPreset("minecraft:pumpkin", 5, 40),
            new MarketPreset("minecraft:sweet_berries", 2, 50),
            new MarketPreset("minecraft:glow_berries", 3, 30),
            new MarketPreset("minecraft:oak_sapling", 5, 60),
            new MarketPreset("minecraft:chorus_fruit", 10, 15)
        ));
    }

    private static MarketPresetCategory animalLoot() {
        return new MarketPresetCategory("AnimalLoot", List.of(
            new MarketPreset("minecraft:leather", 20, 30),
            new MarketPreset("minecraft:rabbit_hide", 5, 20),
            new MarketPreset("minecraft:bone", 6, 40),
            new MarketPreset("minecraft:feather", 4, 50),
            new MarketPreset("minecraft:string", 4, 40),
            new MarketPreset("minecraft:egg", 2, 50),
            new MarketPreset("minecraft:slime_ball", 25, 10),
            new MarketPreset("minecraft:ender_pearl", 10, 8),
            new MarketPreset("minecraft:ghast_tear", 200, 2),
            new MarketPreset("minecraft:nether_star", 2000, 0.5f),
            new MarketPreset("minecraft:honeycomb", 10, 15),
            new MarketPreset("minecraft:ink_sac", 10, 20),
            new MarketPreset("minecraft:spider_eye", 10, 25),
            new MarketPreset("minecraft:phantom_membrane", 15, 10),
            new MarketPreset("minecraft:rabbit_foot", 15, 10)
        ));
    }
}
