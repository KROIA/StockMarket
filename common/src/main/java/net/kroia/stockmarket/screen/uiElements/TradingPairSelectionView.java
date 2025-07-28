package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.TradingPair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TradingPairSelectionView extends GuiElement {


    private final Consumer<TradingPair> onSelected;
    private TradingPair selectedPair;


    private final List<TradingPairView> tradingPairViews = new ArrayList<>();
    public TradingPairSelectionView(Consumer<TradingPair> onSelected)
    {
        super(0, 0, 100, 100); // Example dimensions, adjust as needed
        this.onSelected = onSelected;
        selectedPair = TradingPair.createDefault();

    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {

    }

    public TradingPair getSelectedTradingPair() {
        return selectedPair;
    }

    public void setAvailableTradingPairs(List<TradingPair> tradingPairs) {
        for(TradingPairView view : tradingPairViews) {
            removeChild(view); // Assuming removeChild is a method to remove child elements
        }
        tradingPairViews.clear();
        for (TradingPair pair : tradingPairs) {
            TradingPairView view = new TradingPairView(pair);
            view.setOnFallingEdge(() -> {
                selectedPair = pair;
                onSelected.accept(pair);
            });
            tradingPairViews.add(view);
            addChild(view); // Assuming addChild is a method to add child elements
        }
    }
}
