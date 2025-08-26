package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.screen.uiElements.MarketSelectionView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public class MarketSelectionScreen extends StockMarketGuiScreen {

    private static final String PREFIX = "gui.";
    private static final String NAME = "trading_pair_selection_screen";

    private static final Component TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".title");




    private final MarketSelectionView marketSelectionView;
    private boolean closeOnSelect = true;
    private final Consumer<TradingPair> onSelected;

    public MarketSelectionScreen(Screen parent, @NotNull Consumer<TradingPair> onSelected){
        super(TITLE, parent);
        this.onSelected = onSelected;
        marketSelectionView = new MarketSelectionView(this::onMarketSelected);

        addElement(marketSelectionView);
    }

    public void setCloseOnSelect(boolean closeOnSelect) {
        this.closeOnSelect = closeOnSelect;
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        marketSelectionView.setSize(getWidth()-2*padding, getHeight()-2*padding);
        marketSelectionView.setPosition(padding, padding);
    }

    public TradingPair getSelectedTradingPair() {
        return marketSelectionView.getSelectedTradingPair();
    }
    public void setAvailableTradingPairs(List<TradingPair> tradingPairs)
    {
        marketSelectionView.setAvailableTradingPairs(tradingPairs);
    }

    private void onMarketSelected(TradingPair tradingPair)
    {
        if(closeOnSelect)
            this.close();
        onSelected.accept(tradingPair);
    }

}
