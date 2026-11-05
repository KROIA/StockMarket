package net.kroia.stockmarket.minecraft.block;

import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.minecraft.block.custom.TerminalBlock;
import net.kroia.modutilities.RegistryCompat;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.block.custom.StockMarketBlock;
import net.kroia.stockmarket.minecraft.block.custom.StockMarketDisplayBlock;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

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
    public static final RegistrySupplier<Block> STOCKMARKET_DISPLAY_BLOCK = registerBlock(StockMarketDisplayBlock.NAME,
            // RegistryCompat: no-op on 1.21.1, applies setId on 1.21.2+
            () -> new StockMarketDisplayBlock(RegistryCompat.blockProperties(
                    BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(),
                    ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, StockMarketDisplayBlock.NAME)))); // mc<=1.21.10
                    //Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, StockMarketDisplayBlock.NAME)))); // mc>=1.21.11



    public static <T extends Block> RegistrySupplier<T> registerBlock(String name, Supplier<T> block)
    {
        RegistrySupplier<T> toReturn = BLOCKS.register(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, name), block); // mc<=1.21.10
        //RegistrySupplier<T> toReturn = BLOCKS.register(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, name), block); // mc>=1.21.11
        StockMarketItems.registerBlockItem(name, toReturn);
        return toReturn;
    }
}
