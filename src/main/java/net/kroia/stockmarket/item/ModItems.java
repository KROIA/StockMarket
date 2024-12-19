package net.kroia.stockmarket.item;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.item.custom.software.BankingSoftware;
import net.kroia.stockmarket.item.custom.software.Software;
import net.kroia.stockmarket.item.custom.software.TradingSoftware;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StockMarketMod.MODID);

    public static final RegistryObject<Item> DISPLAY = ITEMS.register("display", () -> new Item(new Item.Properties().tab(ModCreativeModTabs.STOCK_MARKET_TAB)));
    public static final RegistryObject<Item> CIRCUIT_BOARD = ITEMS.register("circuit_board", () -> new Item(new Item.Properties().tab(ModCreativeModTabs.STOCK_MARKET_TAB)));


    // Software
    public static final RegistryObject<Item> SOFTWARE = ITEMS.register(Software.NAME, Software::new);
    public static final RegistryObject<Item> BANKING_SOFTWARE = ITEMS.register(BankingSoftware.NAME, BankingSoftware::new);
    public static final RegistryObject<Item> TRADING_SOFTWARE = ITEMS.register(TradingSoftware.NAME, TradingSoftware::new);

    public static void register(IEventBus eventBus)
    {
        ITEMS.register(eventBus);
    }
}
