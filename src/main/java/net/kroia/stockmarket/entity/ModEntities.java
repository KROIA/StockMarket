package net.kroia.stockmarket.entity;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StockMarketMod.MODID);
/*
    public static final RegistryObject<EntityType<RhinoEntity>> RHINO =
            ENTITY_TYPES.register("rhino", () -> EntityType.Builder.of(RhinoEntity::new, MobCategory.CREATURE)
                    .sized(2.5f, 2.5f).build("rhino"));*/

    public static final RegistryObject<BlockEntityType<StockMarketBlockEntity>> STOCK_MARKET_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("stock_market_block_entity",
                    () -> BlockEntityType.Builder.of(StockMarketBlockEntity::new, ModBlocks.STOCK_MARKET_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<BankTerminalBlockEntity>> BANK_TERMINAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("bank_terminal_block_entity",
                    () -> BlockEntityType.Builder.of(BankTerminalBlockEntity::new, ModBlocks.BANK_TERMINAL_BLOCK.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES .register(eventBus);
    }
}