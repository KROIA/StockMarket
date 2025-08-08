package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

public class TradingPairView extends Button {

    private TradingPair tradingPair;



    private final Label arrowLabel;
    private final ItemView itemView;
    //private final Label arrowLabel;
    private final ItemView currencyItemView;
    private boolean isToggleable = false;
    private boolean isToggled = false;
    public int toggledBackgroundColor = ColorUtilities.getRGB(0x00AA00, 0.5f); // Default green color for toggled state
    public int defaultBackgroundColor = 0xff000000; // Default black color for untoggled state

    public TradingPairView() {
        this(null);
    }
    public TradingPairView(TradingPair tradingPair) {
        super("<->");
        arrowLabel = (Label)super.getChilds().get(0);
        defaultBackgroundColor = getIdleColor();


        this.setLayoutType(Alignment.CENTER);

        itemView = new ItemView();
        itemView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        //arrowLabel = new Label("<->");
        currencyItemView = new ItemView();
        currencyItemView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        addChild(itemView);
        //addChild(arrowLabel);
        addChild(currencyItemView);

        /*LayoutHorizontal layout = new LayoutHorizontal();
        layout.stretchX = true;
        layout.stretchY = true;
        layout.padding = 2;
        this.setLayout(layout);*/

        setTradingPair(tradingPair);
        setSize(60, 20); // Default size, can be adjusted

        setHoverTooltipSupplier(()->{
            if(tradingPair == null) {
                return "";
            }
            return tradingPair.getShortDescription();
        });
    }

    public void setIsToggleable(boolean isToggleable) {
        this.isToggleable = isToggleable;
        if(this.isToggleable)
        {
            setHoverColor(ColorUtilities.getRGB(toggledBackgroundColor, 0.2f));
            setPressedColor(ColorUtilities.getRGB(toggledBackgroundColor, 0.4f));
        }
        if(this.isToggleable && isToggled)
        {
            super.setIdleColor(toggledBackgroundColor);
        }
        else
        {
            super.setIdleColor(defaultBackgroundColor);
        }
    }
    public void setToggled(boolean isToggled) {
        this.isToggled = isToggled;
        if(this.isToggleable && isToggled)
        {
            super.setIdleColor(toggledBackgroundColor);
        }
        else
        {
            super.setIdleColor(defaultBackgroundColor);
        }
    }
    public boolean isToggled() {
        return isToggled;
    }
    public boolean isToggleable() {
        return isToggleable;
    }
    public void setToggledBackgroundColor(int toggledBackgroundColor) {
        this.toggledBackgroundColor = toggledBackgroundColor;

        setHoverColor(ColorUtilities.getRGB(toggledBackgroundColor, 0.2f));
        setPressedColor(ColorUtilities.getRGB(toggledBackgroundColor, 0.4f));
        if(isToggleable && isToggled)
        {
            super.setIdleColor(toggledBackgroundColor);
        }
    }
    public int getToggledBackgroundColor() {
        return toggledBackgroundColor;
    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int height = getHeight();
        int padding = 1;
        int iconSize = height - 2 * padding;
        itemView.setBounds(padding, padding, iconSize, iconSize);
        currencyItemView.setBounds(getWidth() - iconSize - padding, padding, iconSize, iconSize);
        arrowLabel.setBounds(itemView.getRight(), itemView.getTop() ,  currencyItemView.getLeft()-itemView.getRight(), iconSize);

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

    @Override
    public void setOnFallingEdge(Runnable onFallingEdge)
    {
        super.setOnFallingEdge(()->{
            if(isToggleable)
            {
                isToggled = !isToggled;
                if(isToggled)
                {
                    super.setIdleColor(toggledBackgroundColor);
                }
                else
                {
                    super.setIdleColor(defaultBackgroundColor);
                }
            }
            if(onFallingEdge != null)
            {
                onFallingEdge.run();
            }
        });
    }

}
