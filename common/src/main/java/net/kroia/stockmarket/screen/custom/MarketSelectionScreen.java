package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.screen.uiElements.TradingPairSelectionView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class MarketSelectionScreen extends StockMarketGuiScreen {

    private static final String PREFIX = "gui.";
    private static final String NAME = "trading_pair_selection_screen";

    private static final Component TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".title");




    private final TradingPairSelectionView tradingPairSelectionView;
    private final GuiScreen parentScreen;

    public MarketSelectionScreen(GuiScreen parent, Consumer<TradingPair> onSelected){
        super(TITLE);
        parentScreen = parent;
        tradingPairSelectionView = new TradingPairSelectionView(onSelected);

        addElement(tradingPairSelectionView);
    }

    @Override
    public void onClose()
    {
        super.onClose();
        if(parentScreen != null) {
            minecraft.setScreen(parentScreen);
        }
    }


    @Override
    protected void updateLayout(Gui gui) {
        int padding = 10;
        tradingPairSelectionView.setSize(getWidth()-2*padding, getHeight()-2*padding);
        tradingPairSelectionView.setPosition(padding, padding);
    }

    public TradingPair getSelectedTradingPair() {
        return tradingPairSelectionView.getSelectedTradingPair();
    }
    public void setAvailableTradingPairs(List<TradingPair> tradingPairs)
    {
        tradingPairSelectionView.setAvailableTradingPairs(tradingPairs);
    }


}
