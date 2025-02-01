package net.kroia.stockmarket.block;

import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.block.custom.TerminalBlock;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.custom.StockMarketBlock;
import net.kroia.stockmarket.item.StockMarketItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class StockMarketBlocks {
    // 1.19.4
    public static final Supplier<RegistrarManager> MANAGER = Suppliers.memoize(() -> RegistrarManager.get(StockMarketMod.MOD_ID));
    private static final Registrar<Block> BLOCKS = MANAGER.get().get(Registries.BLOCK);

    // 1.19.3 or below
    //public static final Supplier<Registries> REGISTRIES = Suppliers.memoize(() -> Registries.get(BankSystemMod.MOD_ID));
    //public static final Registrar<Block> BLOCKS = REGISTRIES.get().get(Registry.BLOCK_KEY);

    private static boolean initialized = false;
    public static void init(){
        // Force the class to be loaded to initialize static fields
        if(initialized) return;
        initialized = true;
    }


    public static final RegistrySupplier<TerminalBlock> STOCK_MARKET_BLOCK = registerBlock(StockMarketBlock.NAME, StockMarketBlock::new);





    public static <T extends Block> RegistrySupplier<T> registerBlock(String name, Supplier<T> block)
    {
        //BankSystemMod.LOGGER.info("Registering Block: " + name);
        RegistrySupplier<T> toReturn = BLOCKS.register(new ResourceLocation(StockMarketMod.MOD_ID, name), block);
        StockMarketItems.registerBlockItem(name, toReturn);
        return toReturn;
    }
}
