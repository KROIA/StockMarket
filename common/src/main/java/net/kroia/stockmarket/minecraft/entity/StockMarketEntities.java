package net.kroia.stockmarket.minecraft.entity;

import com.google.common.base.Suppliers;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.modutilities.gui.display.client.AbstractDisplayBlockEntityRenderer;
import net.minecraft.client.renderer.RenderType;
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

    //public static final RegistrySupplier<BlockEntityType<StockMarketBlockEntity>> STOCK_MARKET_BLOCK_ENTITY =
    //        registerBlockEntity("stock_market_block_entity",
    //                () -> BlockEntityType.Builder.of(StockMarketBlockEntity::new, StockMarketBlocks.STOCK_MARKET_BLOCK.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<?>> STOCKMARKET_DISPLAY_BLOCK_ENTITY =
            registerBlockEntity("stockmarket_display_block_entity",
                    () -> BlockEntityType.Builder.of(StockMarketDisplayBlockEntity::new, StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get()).build(null));

    public static void registerRenderers() {
        // Architectury API method to register BlockEntityRenderer in a platform-neutral way
        BlockEntityRendererRegistry.register((BlockEntityType<StockMarketDisplayBlockEntity>) STOCKMARKET_DISPLAY_BLOCK_ENTITY.get(), AbstractDisplayBlockEntityRenderer::new);
        RenderTypeRegistry.register(RenderType.cutout(), StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get());
    }

    public static <T extends BlockEntityType<?>> RegistrySupplier<T> registerBlockEntity(String name, Supplier<T> item)
    {
        return BLOCK_ENTITIES.register(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, name), item);
    }
}