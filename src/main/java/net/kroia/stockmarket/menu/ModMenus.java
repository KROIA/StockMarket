package net.kroia.stockmarket.menu;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, StockMarketMod.MODID);
/*
    public static final RegistryObject<MenuType<ChartMenu>> CHART_MENU = MENUS.register("chart_menu",
            () -> IForgeMenuType.create((id, inventory, data) -> new ChartMenu(id, inventory, data)));
*/
    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}