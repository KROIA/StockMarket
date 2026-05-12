package net.kroia.stockmarket.minecraft.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.kroia.banksystem.BankSystemMod;
import net.minecraft.resources.ResourceLocation;

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
        // TODO: BankTerminalScreen no longer extends AbstractContainerScreen, so addGuiContainerHandler
        //  cannot be used. JEI's addGuiScreenHandler uses IScreenHandler (for GUI properties), not
        //  exclusion areas. Re-enable once BankSystem provides a compatible screen type or use
        //  addGlobalGuiHandler if screen-agnostic exclusion is acceptable.
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Optional: Interact with JEI runtime if needed
    }
}