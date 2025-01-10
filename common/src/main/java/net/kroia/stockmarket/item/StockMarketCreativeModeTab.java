package net.kroia.stockmarket.item;


import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.item.BankSystemCreativeModeTab;
import net.minecraft.world.item.CreativeModeTab;

public class StockMarketCreativeModeTab {

    private static boolean initialized = false;
    public static void init(){
        // Force the class to be loaded to initialize static fields
        if(initialized) return;
        initialized = true;
        //TABS.register();
    }


    public static final CreativeTabRegistry.TabSupplier STOCK_MARKET_TAB = BankSystemCreativeModeTab.BANK_SYSTEM_TAB;

    /*
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(StockMarketMod.MOD_ID, Registries.CREATIVE_MODE_TAB);


    public static final RegistrySupplier<CreativeModeTab> STOCK_MARKET_TAB = TABS.register(
            "stock_market_tab", // Tab ID
            () -> {
                return CreativeTabRegistry.create(
                        Component.translatable(StockMarketMod.MOD_ID+".creative_mode_tab_name"), // Tab Name
                        () -> new ItemStack(BankSystemBlocks.BANK_TERMINAL_BLOCK.get()) // Icon
                );}
    );



    public static <T extends CreativeModeTab> RegistrySupplier<T> registerTab(String name, Supplier<T> tab)
    {
        return TABS.register(new ResourceLocation(StockMarketMod.MOD_ID, name), tab);
    }*/
}