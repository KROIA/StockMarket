package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.uiElements.PlayerTradesView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PlayerTradesViewScreen extends StockMarketGuiScreen {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".player_trades_view_screen.";
        public static final Component TITLE = Component.translatable(PREFIX + "title");
    }

    private final PlayerTradesView playerTradesView;

    public PlayerTradesViewScreen(Screen parent)
    {
        super(TEXTS.TITLE, parent);

        playerTradesView = new PlayerTradesView();
        addElement(playerTradesView);

    }






    @Override
    protected void updateLayout(Gui gui) {
        // Gets called when the window gets resized

        // Fill the entire screen with the player trades view
        playerTradesView.setBounds(0,0,getWidth(),getHeight());
    }




}
