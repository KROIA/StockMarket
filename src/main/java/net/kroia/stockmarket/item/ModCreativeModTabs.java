package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static void registerCreativeTabs(CreativeModeTabEvent.Register event) {
        event.registerCreativeModeTab(
                new ResourceLocation(StockMarketMod.MODID, "stockmarket_tab"),
                builder -> builder
                        .icon(() -> new ItemStack(ModBlocks.STOCK_MARKET_BLOCK.get())) // Tab icon
                        .title(Component.translatable("itemGroup.stock_market")) // Translation key
                        .displayItems((parameters, pOutput, a) -> {
                            // Add items to this tab
                            pOutput.accept(ModBlocks.METAL_CASE_BLOCK.get());
                            pOutput.accept(ModBlocks.TERMINAL_BLOCK.get());
                            pOutput.accept(ModBlocks.STOCK_MARKET_BLOCK.get());
                            pOutput.accept(ModBlocks.BANK_TERMINAL_BLOCK.get());


                            pOutput.accept(ModItems.DISPLAY.get());
                            pOutput.accept(ModItems.CIRCUIT_BOARD.get());


                            pOutput.accept(ModItems.SOFTWARE.get());
                            pOutput.accept(ModItems.BANKING_SOFTWARE.get());
                            pOutput.accept(ModItems.TRADING_SOFTWARE.get());
                        })
        );
    }
}