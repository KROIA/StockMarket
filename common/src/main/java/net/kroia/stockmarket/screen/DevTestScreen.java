package net.kroia.stockmarket.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;

/**
 * This class is used for development for faster testing of different features
 */
public class DevTestScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".dev_test_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
    }

    public DevTestScreen() {
        super(Texts.TITLE);
    }

    @Override
    protected void updateLayout(Gui gui) {

    }
}
