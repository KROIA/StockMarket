package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TradingPairSelectionView extends GuiElement {


    private final Consumer<TradingPair> onSelected;
    private TradingPair selectedPair;
    private List<TradingPair> availableTradingPairs;



    private final Button clearItemButton;
    private final ItemView selectedItemView;

    private final Button clearCurrencyButton;
    private final ItemView selectedCurrencyView;

    private final VerticalListView tradingPairListView;
    private final ItemSelectionView itemSelectionView;
    private final ItemSelectionView currencySelectionView;
    public TradingPairSelectionView(Consumer<TradingPair> onSelected)
    {
        super(0, 0, 100, 100); // Example dimensions, adjust as needed
        this.onSelected = onSelected;
        selectedPair = TradingPair.createDefault();


        clearItemButton = new Button("Clear Item", () -> {
            onItemSelected(null);
        });
        selectedItemView = new ItemView();


        clearCurrencyButton = new Button("Clear Currency", () -> {
            onCurrencySelected(null);
        });
        selectedCurrencyView = new ItemView();



        tradingPairListView = new VerticalListView();
        itemSelectionView = new ItemSelectionView(this::onItemSelected);
        currencySelectionView = new ItemSelectionView(this::onCurrencySelected);

        addChild(clearItemButton);
        addChild(selectedItemView);
        addChild(itemSelectionView);
        addChild(clearCurrencyButton);
        addChild(selectedCurrencyView);
        addChild(currencySelectionView);
        addChild(tradingPairListView);
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int column1WidthRatio = 100;
        int column2WidthRatio = 100;
        int column3WidthRatio = 50;

        int sumOfRatios = column1WidthRatio + column2WidthRatio + column3WidthRatio;
        int column1Width = (width * column1WidthRatio) / sumOfRatios;
        int column2Width = (width * column2WidthRatio) / sumOfRatios;
        int column3Width = (width * column3WidthRatio) / sumOfRatios;

        Point pos = new Point(padding, padding);

        clearItemButton.setBounds(pos.x, pos.y, column1Width - selectedItemView.getWidth() - padding, selectedItemView.getHeight());
        selectedItemView.setPosition(clearItemButton.getRight(), clearItemButton.getTop());
        itemSelectionView.setBounds(pos.x, pos.y + clearItemButton.getHeight() + padding,
                column1Width - padding, height - clearItemButton.getHeight() - padding);

        pos.x += clearItemButton.getWidth()+selectedItemView.getWidth() + padding;
        clearCurrencyButton.setBounds(pos.x, pos.y, column2Width - selectedCurrencyView.getWidth(), selectedCurrencyView.getHeight());
        selectedCurrencyView.setPosition(clearCurrencyButton.getRight(), clearCurrencyButton.getTop());
        currencySelectionView.setBounds(pos.x, pos.y + clearCurrencyButton.getHeight() + padding,
                column2Width, height - clearCurrencyButton.getHeight() - padding);

        pos.x += clearCurrencyButton.getWidth() + selectedCurrencyView.getWidth() + padding;
        tradingPairListView.setBounds(pos.x, pos.y, column3Width, height);

        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        tradingPairListView.setLayout(layout);
    }

    public TradingPair getSelectedTradingPair() {
        return selectedPair;
    }

    public void setAvailableTradingPairs(List<TradingPair> tradingPairs) {
        availableTradingPairs = tradingPairs;
        itemSelectionView.clearItems();
        currencySelectionView.clearItems();

        Map<ItemStack, Boolean> itemMap = new HashMap<>();
        Map<ItemStack, Boolean> currencyMap = new HashMap<>();
        for(TradingPair pair : tradingPairs)
        {
            itemMap.put(pair.getItem().getStack(), true);
            currencyMap.put(pair.getCurrency().getStack(), true);
        }

        itemSelectionView.setItems(new ArrayList<>(itemMap.keySet()));
        currencySelectionView.setItems(new ArrayList<>(currencyMap.keySet()));

        ItemStack currentItem = selectedItemView.getItemStack();
        if(currentItem != null)
        {
            if(!itemMap.containsKey(currentItem))
            {
                selectedItemView.setItemStack(null);
            }
        }
        ItemStack currentCurrency = selectedCurrencyView.getItemStack();
        if(currentCurrency != null)
        {
            if(!currencyMap.containsKey(currentCurrency))
            {
                selectedCurrencyView.setItemStack(null);
            }
        }

        applyFilter();
    }

    private void applyFilter()
    {
        tradingPairListView.removeChilds();
        Layout layout = tradingPairListView.getLayout();
        if(layout != null)
            layout.enabled = false;
        List<TradingPairView> tradingPairViews = new ArrayList<>(availableTradingPairs.size());

        for(TradingPair pair : availableTradingPairs)
        {
            ItemStack itemStack = selectedItemView.getItemStack();
            ItemStack currencyStack = selectedCurrencyView.getItemStack();

            if(itemStack != null)
            {
                if(!pair.getItem().getStack().is(itemStack.getItem()))
                {
                    continue;
                }
            }
            if(currencyStack != null)
            {
                if(!pair.getCurrency().getStack().is(currencyStack.getItem()))
                {
                    continue;
                }
            }
            TradingPairView tradingPairView = new TradingPairView(pair);
            tradingPairView.setOnFallingEdge(() -> {
                selectedPair = pair;
                onSelected.accept(pair);
            });
            tradingPairListView.addChild(tradingPairView);
        }
        if(layout != null) {
            layout.enabled = true;
            tradingPairListView.layoutChangedInternal();
        }
    }


    private void onItemSelected(ItemStack itemStack) {
        selectedItemView.setItemStack(itemStack);
        applyFilter();
    }
    private void onCurrencySelected(ItemStack itemStack) {
        selectedCurrencyView.setItemStack(itemStack);
        applyFilter();
    }
}
