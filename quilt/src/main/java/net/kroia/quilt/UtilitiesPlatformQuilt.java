package net.kroia.quilt;

import net.kroia.modutilities.PlatformAbstraction;
import net.kroia.modutilities.UtilitiesPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;

public class UtilitiesPlatformQuilt implements PlatformAbstraction {

    private static MinecraftServer minecraftServer;


    public static void setServer(MinecraftServer server) {
        minecraftServer = server;
    }
    @Override
    public ItemStack getItemStack(String itemID) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemID));
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    @Override
    public String getItemID(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public HashMap<String, ItemStack> getAllItems() {
        HashMap<String, ItemStack> itemsMap = new HashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            itemsMap.put(getItemID(item), new ItemStack(item));
        }

        return itemsMap;
    }

    @Override
    public MinecraftServer getServer() {
        if (minecraftServer == null) {
            throw new IllegalStateException("MinecraftServer is not yet initialized.");
        }
        return minecraftServer;
    }

    @Override
    public UtilitiesPlatform.Type getPlatformType()
    {
        return UtilitiesPlatform.Type.QUILT;
    }
}
