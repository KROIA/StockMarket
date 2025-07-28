package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.market.TradingPair;
import org.jetbrains.annotations.Nullable;

public class TradingPairView extends Button {

    private TradingPair tradingPair;



    private final Label arrowLabel;
    private final ItemView itemView;
    //private final Label arrowLabel;
    private final ItemView currencyItemView;

    public TradingPairView() {
        this(null);
    }
    public TradingPairView(TradingPair tradingPair) {
        super("<->");
        arrowLabel = (Label)super.getChilds().get(0);

        this.setLayoutType(Alignment.CENTER);

        itemView = new ItemView();
        //arrowLabel = new Label("<->");
        currencyItemView = new ItemView();

        addChild(itemView);
        //addChild(arrowLabel);
        addChild(currencyItemView);

        /*LayoutHorizontal layout = new LayoutHorizontal();
        layout.stretchX = true;
        layout.stretchY = true;
        layout.padding = 2;
        this.setLayout(layout);*/

        setTradingPair(tradingPair);
        setSize(100, 20); // Default size, can be adjusted
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int height = getHeight();
        itemView.setPosition(0, 0);
        itemView.setSize(height,height);

        arrowLabel.setPosition(height,0);
        arrowLabel.setSize(getWidth()-height*2,height);

        currencyItemView.setPosition(getWidth() - height, 0);
        currencyItemView.setSize(height, height);

    }


    public void setTradingPair(@Nullable TradingPair tradingPair) {
        this.tradingPair = tradingPair;
        if(tradingPair == null)
        {
            itemView.setItemStack(null);
            currencyItemView.setItemStack(null);
            return;
        }
        itemView.setItemStack(tradingPair.getItem().getStack());
        currencyItemView.setItemStack(tradingPair.getCurrency().getStack());
        // Optionally, trigger a re-render or layout change if needed
        //layoutChanged();
    }
    public TradingPair getTradingPair() {
        return tradingPair;
    }


}
