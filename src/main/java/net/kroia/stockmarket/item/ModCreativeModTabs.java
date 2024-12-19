package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.block.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final CreativeModeTab STOCK_MARKET_TAB = new CreativeModeTab("stock_market") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModBlocks.STOCK_MARKET_BLOCK.get());
        }
    };
}