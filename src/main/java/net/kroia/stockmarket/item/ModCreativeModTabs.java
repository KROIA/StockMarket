package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StockMarketMod.MODID);

    public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB = CREATIVE_MODE_TABS.register("tockmarket_tab",
            () -> CreativeModeTab.builder().title(Component.translatable("Stock Market"))
                    .displayItems((pParameters, pOutput) -> {
                       // pOutput.accept(ModItems.SAPPHIRE.get());
                       // pOutput.accept(ModItems.RAW_SAPPHIRE.get());

                        pOutput.accept(Items.DIAMOND);

                       // pOutput.accept(ModBlocks.SAPPHIRE_BLOCK.get());
                       // pOutput.accept(ModBlocks.RAW_SAPPHIRE_BLOCK.get());
                        pOutput.accept(ModBlocks.STOCK_MARKET_BLOCK.get());

                    })
                    .build());


    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}