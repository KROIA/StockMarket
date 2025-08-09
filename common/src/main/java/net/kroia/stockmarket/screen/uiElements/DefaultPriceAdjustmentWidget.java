package net.kroia.stockmarket.screen.uiElements;


import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAjustmentFactorsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * This widget takes a list of "MarketFactory.DefaultMarketSetupData" and lets the player ajust the default prices
 * for each market using a mathematical function.
 */
public class DefaultPriceAdjustmentWidget extends Frame {


    public static final class TEXT {
        private static final String NAME = "default_price_adjustment_widget";
        public static final String PREFIX = "gui." + StockMarketMod.MOD_ID + "." + NAME + ".";
        public static final Component FACTOR_LINEAR_LABEL = Component.translatable(PREFIX + "factor_linear_label");
        public static final Component FACTOR_LINEAR_LABEL_TOOLTIP = Component.translatable(PREFIX + "factor_linear_label.tooltip");

        public static final Component FACTOR_QUADRATIC_LABEL = Component.translatable(PREFIX + "factor_quadratic_label");
        public static final Component FACTOR_QUADRATIC_LABEL_TOOLTIP = Component.translatable(PREFIX + "factor_quadratic_label.tooltip");

        public static final Component FACTOR_EXPONENTIAL_LABEL = Component.translatable(PREFIX + "factor_exponential_label");
        public static final Component FACTOR_EXPONENTIAL_LABEL_TOOLTIP = Component.translatable(PREFIX + "factor_exponential_label.tooltip");

        public static final Component CANCEL_BUTTON = Component.translatable(PREFIX + "cancel_button");
        public static final Component APPLY_BUTTON = Component.translatable(PREFIX + "apply_button");
        public static final Component OLD_PRICE_AXIS_LABEL = Component.translatable(PREFIX + "old_price_axis_label");
        public static final Component NEW_PRICE_AXIS_LABEL = Component.translatable(PREFIX + "new_price_axis_label");

    }



    private class FactorEditorWidget extends GuiElement
    {
        private final Label linearLabel;
        private final TextBox linearFactorTextBox;

        private final Label quadraticLabel;
        private final TextBox quadraticFactorTextBox;

        private final Label exponentialLabel;
        private final TextBox exponentialFactorTextBox;

        private DefaultPriceAjustmentFactorsData factors;
        public FactorEditorWidget(DefaultPriceAjustmentFactorsData factors)
        {
            super();
            this.factors = factors;
            Alignment tooltipAlignment = Alignment.LEFT;

            linearLabel = new Label(TEXT.FACTOR_LINEAR_LABEL.getString());
            linearLabel.setHoverTooltipSupplier(TEXT.FACTOR_LINEAR_LABEL_TOOLTIP::getString);
            linearLabel.setHoverTooltipMousePositionAlignment(tooltipAlignment);
            linearLabel.setAlignment(Alignment.RIGHT);
            linearFactorTextBox = new TextBox();
            linearFactorTextBox.setAllowLetters(false);
            linearFactorTextBox.setAllowNumbers(true, true);
            if(this.factors != null)
                linearFactorTextBox.setText(String.valueOf(this.factors.linearFactor));
            linearFactorTextBox.setOnTextChanged(text -> {
                try {
                    if(this.factors != null) {
                        this.factors.linearFactor = Float.parseFloat(text);
                        updatePlot();
                    }
                } catch (NumberFormatException e) {
                    //factors.linearFactor = 1.0f; // Default value if parsing fails
                }
            });


            quadraticLabel = new Label(TEXT.FACTOR_QUADRATIC_LABEL.getString());
            quadraticLabel.setHoverTooltipSupplier(TEXT.FACTOR_QUADRATIC_LABEL_TOOLTIP::getString);
            quadraticLabel.setHoverTooltipMousePositionAlignment(tooltipAlignment);
            quadraticLabel.setAlignment(Alignment.RIGHT);
            quadraticFactorTextBox = new TextBox();
            quadraticFactorTextBox.setAllowLetters(false);
            quadraticFactorTextBox.setAllowNumbers(true, true);
            if(this.factors != null)
                quadraticFactorTextBox.setText(String.valueOf(this.factors.quadraticFactor));
            quadraticFactorTextBox.setOnTextChanged(text -> {
                try {
                    if(this.factors != null) {
                        this.factors.quadraticFactor = Float.parseFloat(text);
                        updatePlot();
                    }
                } catch (NumberFormatException e) {
                    //factors.quadraticFactor = 0.0f; // Default value if parsing fails
                }
            });


            exponentialLabel = new Label(TEXT.FACTOR_EXPONENTIAL_LABEL.getString());
            exponentialLabel.setHoverTooltipSupplier(TEXT.FACTOR_EXPONENTIAL_LABEL_TOOLTIP::getString);
            exponentialLabel.setHoverTooltipMousePositionAlignment(tooltipAlignment);
            exponentialLabel.setAlignment(Alignment.RIGHT);
            exponentialFactorTextBox = new TextBox();
            exponentialFactorTextBox.setAllowLetters(false);
            exponentialFactorTextBox.setAllowNumbers(true, true);
            if(this.factors != null)
                exponentialFactorTextBox.setText(String.valueOf(this.factors.exponentialFactor));
            exponentialFactorTextBox.setOnTextChanged(text -> {
                try {
                    if(this.factors != null) {
                        this.factors.exponentialFactor = Float.parseFloat(text);
                        updatePlot();
                    }
                } catch (NumberFormatException e) {
                    //factors.exponentialFactor = 0.0f; // Default value if parsing fails
                }
            });

            addChild(linearLabel);
            addChild(linearFactorTextBox);
            addChild(quadraticLabel);
            addChild(quadraticFactorTextBox);
            addChild(exponentialLabel);
            addChild(exponentialFactorTextBox);
        }

        public void setFactors(DefaultPriceAjustmentFactorsData factors) {
            this.factors = factors;
            if(factors == null)
                return;

            linearFactorTextBox.setText(String.valueOf(factors.linearFactor));
            quadraticFactorTextBox.setText(String.valueOf(factors.quadraticFactor));
            exponentialFactorTextBox.setText(String.valueOf(factors.exponentialFactor));
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            //int height = getHeight();
            int labelHeight = 15;

            int labelWidth = width / 2;
            linearLabel.setBounds(0, 0, labelWidth, labelHeight);
            linearFactorTextBox.setBounds(labelWidth, 0, width-labelWidth, labelHeight);

            quadraticLabel.setBounds(0, labelHeight, labelWidth, labelHeight);
            quadraticFactorTextBox.setBounds(labelWidth, labelHeight, width-labelWidth, labelHeight);

            exponentialLabel.setBounds(0, labelHeight * 2, labelWidth, labelHeight);
            exponentialFactorTextBox.setBounds(labelWidth, labelHeight * 2, width-labelWidth, labelHeight);

            setHeight(labelHeight * 3);
        }
    }


    private final List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList = new ArrayList<>();
    private final List<ItemView> inPlotItemViews = new ArrayList<>();
    private final int itemViewWidth = 12; // Width of the item icon in the plot
    private DefaultPriceAjustmentFactorsData factors;

    private final FactorEditorWidget factorEditorWidget;
    private final ListView marketsListView;
    private final Button cancelButton;
    private final Button applyButton;
    private final Plot plot;
    private final Plot.PlotData plotData = new Plot.PlotData();
    private final Runnable onCancel;
    public DefaultPriceAdjustmentWidget(Runnable onApply, Runnable onCancel)
    {
        this(onApply, onCancel, null, null);
    }
    public DefaultPriceAdjustmentWidget(Runnable onApply, Runnable onCancel, DefaultPriceAjustmentFactorsData factors, List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList)
    {
        super();
        this.onCancel = onCancel;
        setEnableBackground(false);
        setEnableOutline(false);
        this.factors = factors;


        factorEditorWidget = new FactorEditorWidget(factors);
        marketsListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        marketsListView.setLayout(layout);
        setDefaultMarketSetupDataList(defaultMarketSetupDataList);


        cancelButton = new Button(TEXT.CANCEL_BUTTON.getString());
        cancelButton.setOnFallingEdge(onCancel);


        applyButton = new Button(TEXT.APPLY_BUTTON.getString());
        applyButton.setOnFallingEdge(onApply);

        plot = new Plot();
        plotData.color = 0xFF037bfc; // Blue color for the plot
        plot.addPlotData(plotData);
        plot.setXAxisLabel(TEXT.OLD_PRICE_AXIS_LABEL.getString());
        plot.setYAxisLabel(TEXT.NEW_PRICE_AXIS_LABEL.getString());
        plot.setxAxisValueConversion("%.1f");
        plot.setyAxisValueConversion("%.1f");


        addChild(factorEditorWidget);
        addChild(marketsListView);
        addChild(cancelButton);
        addChild(applyButton);
        addChild(plot);
    }

    public void setFactors(DefaultPriceAjustmentFactorsData factors)
    {
        this.factors = factors;
        if(factors == null)
            return;

        factorEditorWidget.setFactors(factors);
    }
    public DefaultPriceAjustmentFactorsData getFactors() {
        return factors;
    }
    public void setDefaultMarketSetupDataList(List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList) {
        this.defaultMarketSetupDataList.clear();

        for(int i=0; i<inPlotItemViews.size(); ++i)
        {
            removeChild(inPlotItemViews.get(i));
        }
        inPlotItemViews.clear();
        marketsListView.removeChilds();

        if(defaultMarketSetupDataList == null || defaultMarketSetupDataList.isEmpty())
            return;

        // Insert sorted by default price
        this.defaultMarketSetupDataList.addAll(defaultMarketSetupDataList);
        this.defaultMarketSetupDataList.sort(Comparator.comparingDouble(MarketFactory.DefaultMarketSetupData::getDefaultPrice));


        for(MarketFactory.DefaultMarketSetupData data : this.defaultMarketSetupDataList)
        {
            ItemView itemView = new ItemView(data.tradingPair.getItem().getStack());
            itemView.setSize(itemViewWidth, itemViewWidth);
            addChild(itemView);
            inPlotItemViews.add(itemView);

            TradingPairView tradingPairView = new TradingPairView(data.tradingPair);
            tradingPairView.setHoverTooltipSupplier(()->{
                String itemText = data.tradingPair.getItem().getName();
                String currencyText = data.tradingPair.getCurrency().getName();
                float defaultPrice = data.getDefaultPrice();
                float newDefaultPrice = getAjustedPrice(defaultPrice);
                return StockMarketTextMessages.getAdjustedDefaultPriceTooltip(itemText, currencyText, Bank.getFormattedAmount(defaultPrice, data.priceScaleFactor), Bank.getFormattedAmount(newDefaultPrice, data.priceScaleFactor));
            });
            tradingPairView.setHoverTooltipMousePositionAlignment(Label.Alignment.LEFT);
            tradingPairView.setIsToggleable(true);
            tradingPairView.setToggled(true);
            tradingPairView.setOnFallingEdge(()->
            {
                boolean isToggled = tradingPairView.isToggled();
                itemView.setEnabled(isToggled); // Disable the item view if the trading pair is toggled off
            });
            marketsListView.addChild(tradingPairView);
        }
        updatePlot();
    }
    public List<MarketFactory.DefaultMarketSetupData> getDefaultMarketSetupDataList() {
        return defaultMarketSetupDataList;
    }
    public List<MarketFactory.DefaultMarketSetupData> getAjustedDefaultMarketSetupDataList() {
        if(defaultMarketSetupDataList == null)
            return new ArrayList<>();
        List<MarketFactory.DefaultMarketSetupData> ajustedDataList = new ArrayList<>(defaultMarketSetupDataList.size());

        for(MarketFactory.DefaultMarketSetupData data : defaultMarketSetupDataList)
        {
            MarketFactory.DefaultMarketSetupData ajustedData = new MarketFactory.DefaultMarketSetupData(data);
            // Here you can apply your mathematical function to ajust the prices
            // For example, let's say we want to increase the price by 10%
            ajustedData.setDefaultPrice(getAjustedPrice(data.getDefaultPrice()));
            ajustedDataList.add(ajustedData);
        }
        return ajustedDataList;
    }

    @Override
    protected void render() {
        super.render();

        if(defaultMarketSetupDataList == null || defaultMarketSetupDataList.isEmpty())
            return;

        graphicsPosePush();
        graphicsTranslate(0,0,160);
        int lastGuiX = 0;
        int yOffset = 0;
        int xOffset = 0;

        int itemViewWidth2 = itemViewWidth / 2; // Half width for centering the item views

        for(int i=0; i<defaultMarketSetupDataList.size(); ++i)
        {
            MarketFactory.DefaultMarketSetupData data = defaultMarketSetupDataList.get(i);
            if(data == null)
                continue;

            Point plotPos = plot.getGuiPosFromXValue(data.getDefaultPrice(), getAjustedPrice(data.getDefaultPrice()));


            ItemView itemView = inPlotItemViews.get(i);
            if(itemView.isEnabled()) {
                int absPosX = plotPos.x + plot.getX() - itemViewWidth2;
                int absPosY = plotPos.y + plot.getY() - itemViewWidth2;

                if (lastGuiX >= absPosX) {
                    yOffset += itemView.getHeight();
                    if (absPosY - yOffset < itemViewWidth2) // Is on top of the chart
                    {
                        yOffset = 0; // Reset yOffset if we exceed the plot height
                        xOffset += itemViewWidth;
                    }
                } else {
                    yOffset = 0; // Reset yOffset if we are moving to a new column
                    xOffset = 0; // Reset xOffset if we are moving to a new column
                }
                //itemView.setItemStack(data.tradingPair.getItem().getStack());
                itemView.setPosition(absPosX + xOffset, absPosY - yOffset);
                if (yOffset > 0 || xOffset > 0) {
                    drawLine(itemView.getX() + itemViewWidth2, itemView.getY() + itemViewWidth2,
                            absPosX + itemViewWidth2, absPosY + itemViewWidth2, 0.5f, 0xFF03fce3); // Draw a line from the item icon to the ajusted price position
                }

                lastGuiX = absPosX + itemViewWidth2;
            }

            //itemView.renderInternal(); // Render the item icon at the ajusted price position

            //drawItem(data.tradingPair.getItem().getStack(), absPosX, absPosY); // Draw the item icon at the ajusted price position
        }
        graphicsPosePop();


        var childs = marketsListView.getChilds();
        for(int i=0; i<childs.size(); ++i)
        {
            GuiElement child = childs.get(i);
            if(child instanceof TradingPairView tradingPairView)
            {
                if(tradingPairView.isMouseOver())
                {

                    for(int j=0; j<defaultMarketSetupDataList.size(); j++)
                    {
                        MarketFactory.DefaultMarketSetupData data = defaultMarketSetupDataList.get(j);
                        if(data.tradingPair.equals(tradingPairView.getTradingPair()))
                        {
                            float defaultPrice = data.getDefaultPrice();
                            float ajustedPrice = getAjustedPrice(defaultPrice);
                            Point plotPos = plot.getGuiPosFromXValue(defaultPrice, ajustedPrice);
                            graphicsPosePush();
                            graphicsTranslate(0,0,170);

                            int absPosX = plotPos.x+plot.getX();
                            int absPosY = plotPos.y+plot.getY();
                            //drawCross(crossX, crossY, 5, 0xFFFF0000); // Draw a red cross at the ajusted price position
                            drawLine(getMouseX(), getMouseY(), absPosX, absPosY, 1.0f, 0xFFFF0000); // Draw a line from the mouse position to the ajusted price position
                            //if(plotPos.y > plot.getY()+plot.getHeight()/2)
                                drawText(defaultPrice + "->"+ ajustedPrice, absPosX, absPosY, 0xFF88ff8c, Alignment.CENTER); // Draw the price text at the ajusted price position
                            //else
                            //    drawText(defaultPrice + "->"+ ajustedPrice, absPosX, absPosY+20, 0xFF88ff8c, Alignment.TOP); // Draw the price text at the ajusted price position

                            graphicsPosePop();
                            return;
                        }
                    }
                }
            }
        }


    }

    @Override
    protected void layoutChanged() {
        int width = getWidth();
        int height = getHeight();
        int padding = StockMarketGuiElement.padding;
        int spacing  = StockMarketGuiElement.spacing;

        int leftSectionWidth = width /4; // Factor editor takes 1/4 of the width

        factorEditorWidget.setBounds(padding, padding, leftSectionWidth, factorEditorWidget.getHeight());
        marketsListView.setBounds(factorEditorWidget.getLeft(), factorEditorWidget.getBottom()+spacing, leftSectionWidth, height - factorEditorWidget.getBottom() - spacing*2 - padding-20);
        cancelButton.setBounds(factorEditorWidget.getLeft(), marketsListView.getBottom() + spacing, leftSectionWidth/2-spacing/2, 20);
        applyButton.setBounds(cancelButton.getRight()+spacing, marketsListView.getBottom() + spacing, leftSectionWidth - cancelButton.getWidth()-spacing, 20);

        plot.setBounds(factorEditorWidget.getRight()+spacing, padding, width - factorEditorWidget.getRight() - spacing - padding, height-2*padding);

    }

    @Override
    protected boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(keyCode == 256) // Escape key
        {
            if(onCancel != null)
                onCancel.run();
            return true; // Consume the event
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    private float getAjustedPrice(float inputPrice)
    {
        if(factors == null)
            return inputPrice; // No factors defined, return original price
        return MarketFactory.getAjustedPriceF(inputPrice, factors.linearFactor, factors.quadraticFactor, factors.exponentialFactor);
    }

    private void updatePlot()
    {
        float maxXValue = getXMaxPrice();

        // Recalculate the plot points based on the ajusted prices
        plotData.yValues.clear();
        int pointCount = 100; // Number of points to plot
        float maxYValueForPlot = 0;
        for(int i=0; i<pointCount; ++i)
        {
            float xValue = (float)i / (pointCount - 1) * maxXValue; // Scale xValue to the range of 0 to maxXValue
            float yValue = getYValue(xValue);
            maxYValueForPlot = Math.max(maxYValueForPlot, yValue);
            plotData.yValues.add(yValue);
        }

        plot.setXRange(0, maxXValue);
        plot.setYRange(0, maxYValueForPlot);

    }

    private float getXMaxPrice()
    {
        float maxPrice = 0;
        for(MarketFactory.DefaultMarketSetupData data : defaultMarketSetupDataList)
        {
            maxPrice = Math.max(maxPrice, data.getDefaultPrice());
        }
        return maxPrice;
    }
    float getYValue(float X)
    {
        return MarketFactory.getAjustedPriceF(X, factors.linearFactor, factors.quadraticFactor, factors.exponentialFactor);
    }
}
