package net.kroia.stockmarket.testing;

import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.PlatformAbstraction;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.OfflineTests;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code gradlew :common:test}: the StockMarket test suites with {@code needsGame() == false}, run as JUnit tests
 * without Minecraft, a server or a mod loader (ModUtilities {@link OfflineTests}). Lives in the testing package so the
 * release-mode test source set (common/build.gradle) picks it up.
 */
class StockMarketOfflineTests {

    @TestFactory
    Stream<DynamicTest> offline() {
        // Before register(): some suites build ItemIDs / ItemStacks in static fields, which need the vanilla registries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Production code asks UtilitiesPlatform for the server (e.g. news publishing); offline there is none.
        if (!UtilitiesPlatform.isPlatformSet())
            UtilitiesPlatform.setPlatform(new OfflinePlatform());
        // Without a world BankSystem's ItemID table is empty, so every ItemID is invalid and NBT loads drop it.
        // Seed it as a world would have it: ItemID n = the n-th vanilla item (1..about 1300).
        Map<ItemID, ItemStack> table = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM)
            if (item != Items.AIR) table.put(new ItemID((short) (table.size() + 1)), new ItemStack(item));
        ItemIDManager.replaceState_forTesting(table, Map.of(), table.size() + 1);
        return OfflineTests.run(StockMarketTestRegistration::register);
    }

    /** Platform without a game: no server, items straight from the vanilla built-in registry. */
    private static final class OfflinePlatform implements PlatformAbstraction {
        @Override
        public ItemStack getItemStack(String itemIDStr) {
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemIDStr)).orElse(null); // mc<=1.21.10
            //Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(itemIDStr)).orElse(null); // mc>=1.21.11
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        @Override
        public String getItemIDStr(Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }

        @Override
        public ArrayList<ItemStack> getAllItems() {
            ArrayList<ItemStack> items = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) items.add(new ItemStack(item));
            return items;
        }

        @Override
        public MinecraftServer getServer() {
            return null;
        }

        @Override
        public UtilitiesPlatform.Type getPlatformType() {
            return UtilitiesPlatform.Type.FABRIC; // arbitrary: nothing offline branches on the loader
        }
    }
}
