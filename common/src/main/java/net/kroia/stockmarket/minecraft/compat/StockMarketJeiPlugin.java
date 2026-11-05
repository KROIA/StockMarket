package net.kroia.stockmarket.minecraft.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11

@JeiPlugin
public class StockMarketJeiPlugin implements IModPlugin {


    public StockMarketJeiPlugin() {
    }

    public static void init()
    {
        //JEIIntegration.registerPlugin(new BankSystemJeiPlugin());
    }
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "jei_plugin"); // mc<=1.21.10
    //private static final Identifier PLUGIN_UID = Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "jei_plugin"); // mc>=1.21.11

    @Override
    public ResourceLocation getPluginUid() { // mc<=1.21.10
    //public Identifier getPluginUid() { // mc>=1.21.11
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