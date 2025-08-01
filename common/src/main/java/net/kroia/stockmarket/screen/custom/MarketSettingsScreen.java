package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;

public class MarketSettingsScreen extends StockMarketGuiScreen {
    private static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
    }


    private class BotGuiElement extends StockMarketGuiElement
    {

        public BotGuiElement() {
            super();
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {

        }
    }

    public class VirtualOderBookGuiElement extends StockMarketGuiElement
    {

        public VirtualOderBookGuiElement() {
            super();
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {

        }
    }


    GuiScreen parentScreen;
    public MarketSettingsScreen(GuiScreen parent) {
        super(TEXTS.TITLE);
        this.parentScreen = parent;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (parentScreen != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {

    }
}
