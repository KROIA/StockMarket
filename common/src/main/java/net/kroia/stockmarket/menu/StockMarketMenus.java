package net.kroia.stockmarket.menu;

import com.google.common.base.Suppliers;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.menu.custom.BankTerminalContainerMenu;
import net.kroia.banksystem.screen.custom.BankTerminalScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public class StockMarketMenus {

    // 1.19.4
    public static final Supplier<RegistrarManager> MANAGER = Suppliers.memoize(() -> RegistrarManager.get(StockMarketMod.MOD_ID));
    public static final Registrar<MenuType<?>> MENUS = MANAGER.get().get(Registries.MENU);

    // 1.19.3 or below
    //public static final Supplier<Registries> REGISTRIES = Suppliers.memoize(() -> Registries.get(StockMarketMod.MOD_ID));
    //public static final Registrar<MenuType<?>> MENUS = REGISTRIES.get().get(Registry.MENU_KEY);

    private static boolean initialized = false;
    public static void init(){
        // Force the class to be loaded to initialize static fields
        if(initialized) return;
        initialized = true;
    }

    public static void setupScreens()
    {
        // Register screens
        //MenuRegistry.registerScreenFactory(BANK_TERMINAL_CONTAINER_MENU.get(), BankTerminalScreen::new);
    }


    // Register menus
    //public static final RegistrySupplier<MenuType<BankTerminalContainerMenu>> BANK_TERMINAL_CONTAINER_MENU =
    //        MENUS.register(new ResourceLocation(BankSystemMod.MOD_ID, "bank_terminal_container_menu"), () -> MenuRegistry.ofExtended(BankTerminalContainerMenu::new));



}