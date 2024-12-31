package net.kroia.stockmarket.forge;

import net.kroia.modutilities.PlatformAbstraction;
import net.kroia.modutilities.UtilitiesPlatform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Objects;

public class UtilitiesPlatformForge implements PlatformAbstraction {
    @Override
    public ItemStack getItemStack(String itemID) {
        return new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemID))));
    }

    @Override
    public String getItemID(Item item) {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)).toString();
    }

    @Override
    public HashMap<String, ItemStack> getAllItems() {
        HashMap<String, ItemStack> items = new HashMap<>();
        ForgeRegistries.ITEMS.forEach(item -> items.put(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)).toString(), new ItemStack(item)));
        return items;
    }

    @Override
    public MinecraftServer getServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public UtilitiesPlatform.Type getPlatformType() {
        return UtilitiesPlatform.Type.FORGE;
    }
}
