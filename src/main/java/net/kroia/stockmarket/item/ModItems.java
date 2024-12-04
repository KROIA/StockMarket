package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StockMarketMod.MODID);

    //public static final RegistryObject<Item>
    public static void register(IEventBus eventBus)
    {
        ITEMS.register(eventBus);
    }
}