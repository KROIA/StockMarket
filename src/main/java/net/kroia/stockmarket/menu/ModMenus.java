package net.kroia.stockmarket.menu;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.menu.custom.BankTerminalContainerMenu;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, StockMarketMod.MODID);
/*
    public static final RegistryObject<MenuType<ChartMenu>> CHART_MENU = MENUS.register("chart_menu",
            () -> IForgeMenuType.create((id, inventory, data) -> new ChartMenu(id, inventory, data)));
*/
        /*public static final RegistryObject<MenuType<BankTerminalContainerMenu>> BANK_TERMINAL_CONTAINER_MENU =
                MENUS.register("my_custom_container",
                    () -> IForgeMenuType.create((id, inventory, buffer) -> {
                        BlockPos pos = buffer.readBlockPos();
                        Level level = inventory.player.level();
                        if (level.getBlockEntity(pos) instanceof BankTerminalBlockEntity blockEntity) {
                            return new BankTerminalContainerMenu(id, inventory, blockEntity.getItems());
                        }
                        return null;
                    }));*/

    //public static final RegistryObject<MenuType<BankTerminalContainerMenu>> BANK_TERMINAL_CONTAINER_MENU =
    //        MENUS.register("my_custom_menu", () -> new MenuType<>((id, playerInventory) -> new BankTerminalContainerMenu(id, playerInventory, new SimpleContainer(27))));

    public static final RegistryObject<MenuType<BankTerminalContainerMenu>> BANK_TERMINAL_CONTAINER_MENU = MENUS.register("bank_terminal_container_menu",
            () -> IForgeMenuType.create(BankTerminalContainerMenu::new));


    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}