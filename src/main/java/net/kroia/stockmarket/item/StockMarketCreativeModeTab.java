package net.kroia.stockmarket.item;

import net.kroia.banksystem.item.BankSystemCreativeModeTab;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public class StockMarketCreativeModeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StockMarketMod.MODID);

    /*
    public static final RegistryObject<CreativeModeTab> STOCK_MARKET_TAB = CREATIVE_MODE_TABS.register("stockmarket_tab",
            () -> CreativeModeTab.builder().title(Component.translatable("Stock Market"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(StockMarketBlocks.STOCK_MARKET_BLOCK.get());

                        pOutput.accept(StockMarketItems.TRADING_SOFTWARE.get());
                    })
                    .icon(() -> new ItemStack(StockMarketBlocks.STOCK_MARKET_BLOCK.get()))
                    .build());*/


    public static void register(IEventBus eventBus) {
       // CREATIVE_MODE_TABS.register(eventBus);
        BankSystemCreativeModeTab.addDynamicItem(()->new ItemStack(StockMarketItems.TRADING_SOFTWARE.get()));
        BankSystemCreativeModeTab.addDynamicBlock(()-> StockMarketBlocks.STOCK_MARKET_BLOCK.get());

    }
}