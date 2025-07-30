package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TradingPairSelectionView extends GuiElement {

    public static final Component CLEAR_ITEM = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".trading_pair_selection_view.clear_item");
    public static final Component CLEAR_CURRENCY = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".trading_pair_selection_view.clear_currency");
    public static final Component SELECT_PAIR = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".trading_pair_selection_view.select_pair");

    private final Consumer<TradingPair> onSelected;
    private TradingPair selectedPair;
    private List<TradingPair> availableTradingPairs;



    private final Button clearItemButton;
    private final ItemView selectedItemView;

    private final Button clearCurrencyButton;
    private final ItemView selectedCurrencyView;


    private final Label selectItemLabel;
    private final VerticalListView tradingPairListView;
    private final ItemSelectionView itemSelectionView;
    private final ItemSelectionView currencySelectionView;
    public TradingPairSelectionView(Consumer<TradingPair> onSelected)
    {
        super(0, 0, 100, 100); // Example dimensions, adjust as needed
        this.onSelected = onSelected;
        selectedPair = TradingPair.createDefault();


        clearItemButton = new Button(CLEAR_ITEM.getString(), () -> {
            onItemSelected(null);
        });
        selectedItemView = new ItemView();


        clearCurrencyButton = new Button(CLEAR_CURRENCY.getString(), () -> {
            onCurrencySelected(null);
        });
        selectedCurrencyView = new ItemView();



        selectItemLabel = new Label(SELECT_PAIR.getString());
        selectItemLabel.setAlignment(Label.Alignment.CENTER);
        tradingPairListView = new VerticalListView();
        itemSelectionView = new ItemSelectionView(this::onItemSelected);
        currencySelectionView = new ItemSelectionView(this::onCurrencySelected);

        addChild(clearItemButton);
        addChild(selectedItemView);
        addChild(itemSelectionView);
        addChild(clearCurrencyButton);
        addChild(selectedCurrencyView);
        addChild(currencySelectionView);
        addChild(selectItemLabel);
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
        selectItemLabel.setBounds(pos.x, pos.y, column3Width, clearItemButton.getHeight());
        pos.y += clearItemButton.getHeight() + padding;
        tradingPairListView.setBounds(pos.x, pos.y, column3Width, height - clearItemButton.getHeight() - padding);

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

        Map<ItemID, Boolean> itemMap = new HashMap<>();
        Map<ItemID, Boolean> currencyMap = new HashMap<>();
        for(TradingPair pair : tradingPairs)
        {
            itemMap.put(pair.getItem(), true);
            currencyMap.put(pair.getCurrency(), true);
        }

        itemSelectionView.setItems(new ArrayList<>(itemMap.keySet().stream().map(ItemID::getStack).toList()));
        currencySelectionView.setItems(new ArrayList<>(currencyMap.keySet().stream().map(ItemID::getStack).toList()));

        ItemID currentItem = new ItemID(selectedItemView.getItemStack());
        if(currentItem.getStack() != null)
        {
            if(!itemMap.containsKey(currentItem))
            {
                selectedItemView.setItemStack(null);
            }
        }
        ItemID currentCurrency = new ItemID(selectedCurrencyView.getItemStack());
        if(currentCurrency.getStack() != null)
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
