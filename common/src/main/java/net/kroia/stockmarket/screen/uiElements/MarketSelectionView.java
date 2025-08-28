package net.kroia.stockmarket.screen.uiElements;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MarketSelectionView extends StockMarketGuiElement {

    public static final Component FILTER = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".market_selection_view.filter");
    public static final Component CLEAR_ITEM = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".market_selection_view.clear_item");
    public static final Component CLEAR_CURRENCY = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".market_selection_view.clear_currency");
    public static final Component SELECT_MARKET = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".market_selection_view.select_market");

    private final Consumer<TradingPair> onSelected;
    private TradingPair selectedPair;
    private List<TradingPair> availableTradingPairs;


    private final Label filterLabel;

    private final Button clearItemButton;
    private final ItemView selectedItemView;

    private final Button clearCurrencyButton;
    private final ItemView selectedCurrencyView;


    private final Label selectedMarketLabel;
    private final VerticalListView tradingPairListView;
    private final ItemSelectionView itemSelectionView;
    private final ItemSelectionView currencySelectionView;
    public MarketSelectionView(Consumer<TradingPair> onSelected)
    {
        super(0, 0, 100, 100); // Example dimensions, adjust as needed
        this.onSelected = onSelected;
        selectedPair = TradingPair.createDefault();

        filterLabel = new Label(FILTER.getString());
        filterLabel.setAlignment(Label.Alignment.CENTER);

        clearItemButton = new Button(CLEAR_ITEM.getString(), () -> {
            onItemSelected(null);
        });
        selectedItemView = new ItemView();


        clearCurrencyButton = new Button(CLEAR_CURRENCY.getString(), () -> {
            onCurrencySelected(null);
        });
        selectedCurrencyView = new ItemView();



        selectedMarketLabel = new Label(SELECT_MARKET.getString());
        selectedMarketLabel.setAlignment(Label.Alignment.CENTER);
        tradingPairListView = new VerticalListView();
        LayoutGrid layout = new LayoutGrid();
        layout.stretchX = true;
        layout.columns = 3;
        tradingPairListView.setLayout(layout);

        itemSelectionView = new ItemSelectionView(this::onItemSelected);
        itemSelectionView.clearItems();
        currencySelectionView = new ItemSelectionView(this::onCurrencySelected);
        currencySelectionView.clearItems();


        addChild(filterLabel);
        addChild(clearItemButton);
        addChild(selectedItemView);
        addChild(itemSelectionView);
        addChild(clearCurrencyButton);
        addChild(selectedCurrencyView);
        addChild(currencySelectionView);
        addChild(selectedMarketLabel);
        addChild(tradingPairListView);
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;
        int spacing = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int column1WidthRatio = 100;
        int column2WidthRatio = 100;
        int column3WidthRatio = 150;

        int sumOfRatios = column1WidthRatio + column2WidthRatio + column3WidthRatio;
        int column1Width = (width * column1WidthRatio) / sumOfRatios;
        int column2Width = (width * column2WidthRatio) / sumOfRatios;
        int column3Width = (width * column3WidthRatio) / sumOfRatios;
        if(column3Width < 80)
        {
            column3Width = 80;
            // Adjust the other columns to maintain the ratio
            sumOfRatios = column1WidthRatio + column2WidthRatio + padding;
            column1Width = ((width-column3Width) * column1WidthRatio) / sumOfRatios;
            column2Width = ((width-column3Width) * column2WidthRatio) / sumOfRatios;
        }



        Point pos = new Point(padding, padding);

        filterLabel.setBounds(pos.x, pos.y, column1Width + column2Width, 15);
        pos.y += filterLabel.getHeight() + spacing;
        clearItemButton.setBounds(pos.x, pos.y, column1Width - selectedItemView.getWidth() - spacing, selectedItemView.getHeight());
        selectedItemView.setPosition(clearItemButton.getRight(), clearItemButton.getTop());
        itemSelectionView.setBounds(pos.x, clearItemButton.getBottom() + spacing,
                column1Width - spacing, height - clearItemButton.getBottom());

        pos.x += clearItemButton.getWidth()+selectedItemView.getWidth() + spacing;
        clearCurrencyButton.setBounds(pos.x, pos.y, column2Width - selectedCurrencyView.getWidth(), selectedCurrencyView.getHeight());
        selectedCurrencyView.setPosition(clearCurrencyButton.getRight(), clearCurrencyButton.getTop());
        currencySelectionView.setBounds(pos.x, clearCurrencyButton.getBottom() + spacing,
                column2Width, height - clearCurrencyButton.getBottom());

        pos.x += clearCurrencyButton.getWidth() + selectedCurrencyView.getWidth() + spacing;
        selectedMarketLabel.setBounds(pos.x, filterLabel.getTop(), column3Width, filterLabel.getHeight());
        pos.y += clearItemButton.getHeight() + spacing;
        tradingPairListView.setBounds(pos.x, selectedMarketLabel.getBottom() + spacing, column3Width, height - selectedMarketLabel.getBottom());


        LayoutGrid layout = (LayoutGrid) tradingPairListView.getLayout();
        if(layout != null)
        {
            layout.columns = column3Width/60;
        }
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
        List<TradingPairView> views = new ArrayList<>();
        for(TradingPair pair : availableTradingPairs)
        {
            ItemStack itemStack = selectedItemView.getItemStack();
            ItemStack currencyStack = selectedCurrencyView.getItemStack();

            if(itemStack != null)
            {
                if(!pair.getItem().getStack().equals(itemStack))
                {
                    continue;
                }
            }
            if(currencyStack != null)
            {
                if(!pair.getCurrency().getStack().equals(currencyStack))
                {
                    continue;
                }
            }
            TradingPairView tradingPairView = new TradingPairView(pair);
            tradingPairView.setOnFallingEdge(() -> {
                selectedPair = pair;
                onSelected.accept(pair);
            });
            views.add(tradingPairView);

        }

        views.sort((a, b) -> {
            String itemNameA = ItemUtilities.getItemIDStr(a.getTradingPair().getItem().getStack().getItem());
            // reverse the itemName
            itemNameA = new StringBuilder(itemNameA).reverse().toString();
            String itemNameB = ItemUtilities.getItemIDStr(b.getTradingPair().getItem().getStack().getItem());
            itemNameB = new StringBuilder(itemNameB).reverse().toString();

            int itemCompare = itemNameA.compareTo(itemNameB);
            if(itemCompare != 0)
                return itemCompare;
            String currencyNameA = a.getTradingPair().getCurrency().getName();
            String currencyNameB = b.getTradingPair().getCurrency().getName();
            return currencyNameA.compareTo(currencyNameB);
        });

        for(TradingPairView tradingPairView : views)
            tradingPairListView.addChild(tradingPairView);

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
