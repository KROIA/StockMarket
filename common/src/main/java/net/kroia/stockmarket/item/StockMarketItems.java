package net.kroia.stockmarket.item;

import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.item.BankSystemCreativeModeTab;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.item.custom.software.TradingSoftware;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class StockMarketItems {
    // 1.19.4
    public static final Supplier<RegistrarManager> MANAGER = Suppliers.memoize(() -> RegistrarManager.get(StockMarketMod.MOD_ID));
    public static final Registrar<Item> ITEMS = MANAGER.get().get(Registries.ITEM);

    // 1.19.3 or below
    //public static final Supplier<Registries> REGISTRIES = Suppliers.memoize(() -> Registries.get(BankSystemMod.MOD_ID));
    //public static final Registrar<Item> ITEMS = REGISTRIES.get().get(Registry.ITEM_KEY);

    private static boolean initialized = false;
    public static void init(){
        // Force the class to be loaded to initialize static fields
        if(initialized) return;
        initialized = true;
    }

    // Software
    public static final RegistrySupplier<Item> TRADING_SOFTWARE = registerItem(TradingSoftware.NAME, TradingSoftware::new);


    public static <T extends Item> RegistrySupplier<T> registerItem(String name, Supplier<T> item)
    {
        //BankSystemMod.LOGGER.info("Registering Item: " + name);
        return ITEMS.register(new ResourceLocation(StockMarketMod.MOD_ID, name), item);
    }
    public static <T extends Block> RegistrySupplier<Item> registerBlockItem(String name, RegistrySupplier<T> block)
    {
        //return registerItem(name, () -> new BlockItem(block.get(), new Item.Properties().tab(BankSystemCreativeModeTab.BANK_SYSTEM_TAB))); // 1.19.3 or below
        return registerItem(name, () -> new BlockItem(block.get(), new Item.Properties().arch$tab(StockMarketCreativeModeTab.STOCK_MARKET_TAB)));
    }
}
