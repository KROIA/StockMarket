package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.item.custom.software.TradingSoftware;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class StockMarketItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StockMarketMod.MODID);

    // Software
    public static final RegistryObject<Item> TRADING_SOFTWARE = ITEMS.register(TradingSoftware.NAME, TradingSoftware::new);

    public static void register(IEventBus eventBus)
    {
        ITEMS.register(eventBus);
    }
}
