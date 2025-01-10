package net.kroia.stockmarket.entity;

import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class StockMarketEntities {

    // 1.19.4
    public static final Supplier<RegistrarManager> MANAGER = Suppliers.memoize(() -> RegistrarManager.get(StockMarketMod.MOD_ID));
    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES = MANAGER.get().get(Registries.BLOCK_ENTITY_TYPE);

    // 1.19.3 or below
    //public static final Supplier<Registries> REGISTRIES = Suppliers.memoize(() -> Registries.get(StockMarketMod.MOD_ID));
    //public static final Registrar<Item> ITEMS = REGISTRIES.get().get(Registry.ITEM_KEY);

    private static boolean initialized = false;
    public static void init(){
        // Force the class to be loaded to initialize static fields
        if(initialized) return;
        initialized = true;
    }

    public static final RegistrySupplier<BlockEntityType<StockMarketBlockEntity>> STOCK_MARKET_BLOCK_ENTITY =
            registerBlockEntity("stock_market_block_entity",
                    () -> BlockEntityType.Builder.of(StockMarketBlockEntity::new, StockMarketBlocks.STOCK_MARKET_BLOCK.get()).build(null));



    public static <T extends BlockEntityType<?>> RegistrySupplier<T> registerBlockEntity(String name, Supplier<T> item)
    {
        //StockMarketMod.LOGGER.info("Registering block entity: " + name);
        return BLOCK_ENTITIES.register(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, name), item);
    }
}