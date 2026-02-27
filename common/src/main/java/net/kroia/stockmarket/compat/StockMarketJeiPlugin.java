package net.kroia.stockmarket.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.screen.custom.BankTerminalScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public class StockMarketJeiPlugin implements IModPlugin {


    public StockMarketJeiPlugin() {
    }

    public static void init()
    {
        //JEIIntegration.registerPlugin(new BankSystemJeiPlugin());
    }
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(BankSystemMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Register exclusion areas for your custom screen
        registration.addGuiContainerHandler(BankTerminalScreen.class, new IGuiContainerHandler<BankTerminalScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(BankTerminalScreen screen) {
                // Define the area that JEI should exclude
                return List.of(new Rect2i(0, 0, screen.width, screen.height));
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Optional: Interact with JEI runtime if needed
    }
}