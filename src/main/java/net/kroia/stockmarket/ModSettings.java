package net.kroia.stockmarket;

import java.util.HashMap;
import java.util.Map;

public class ModSettings {
    public static final class Player
    {
        public static final long STARTING_BALANCE = 0;
    }

    public static final class Bank
    {
        public static final boolean NOTIFICATION_USE_SHORT_ITEM_ID = true;
    }

    public static final class Market
    {
        public static final HashMap<String, Integer> TRADABLE_ITEMS;
        static{
            TRADABLE_ITEMS = new HashMap<>();
            TRADABLE_ITEMS.put("minecraft:diamond", 50);
            TRADABLE_ITEMS.put("minecraft:iron_ingot", 10);
            TRADABLE_ITEMS.put("minecraft:gold_ingot", 25);
            TRADABLE_ITEMS.put("minecraft:emerald", 100);
            TRADABLE_ITEMS.put("minecraft:coal", 5);
            //TRADABLE_ITEMS.put("minecraft:quartz", 5);
            //TRADABLE_ITEMS.put("minecraft:obsidian", 10);
            //TRADABLE_ITEMS.put("minecraft:glowstone", 10);
            //TRADABLE_ITEMS.put("minecraft:blaze_rod", 20);
            //TRADABLE_ITEMS.put("minecraft:ender_pearl", 50);
            //TRADABLE_ITEMS.put("minecraft:ghast_tear", 100);
            //TRADABLE_ITEMS.put("minecraft:shulker_shell", 200);
            //TRADABLE_ITEMS.put("minecraft:netherite_ingot", 500);
            //TRADABLE_ITEMS.put("minecraft:ancient_debris", 1000);
            //TRADABLE_ITEMS.put("minecraft:elytra", 5000);
            //TRADABLE_ITEMS.put("minecraft:dragon_egg", 10000);
            //TRADABLE_ITEMS.put("minecraft:enchanted_golden_apple", 1000);
            //TRADABLE_ITEMS.put("minecraft:totem_of_undying", 1000);
        }
    }

    public static final class MarketBot
    {
        public static final boolean ENABLED = true;

        public static final String USER_NAME = "StockMarketBot";
        public static final long STARTING_BALANCE = 1000000;

        public static final HashMap<String, Long> STARTING_STOCKS = new HashMap<>(
            Map.of(
                "minecraft:iron_ingot", 1000L,
                "minecraft:gold_ingot", 1000L,
                "minecraft:diamond", 1000L,
                "minecraft:emerald", 1000L
            )
        );
        public static final int MAX_ORDERS = 50;

        public static final double VOLUME_SCALE = 10;
        public static final double VOLUME_SPREAD = 10;

        public static final class VolatilityBot
        {
            public static final int VOLATILITY = 1;
        }

    }

}
