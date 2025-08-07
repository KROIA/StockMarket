package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAjustmentFactorsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.screen.uiElements.DefaultPriceAjustmentWidget;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MarketCreationByCategoryScreen extends StockMarketGuiScreen {

    public static final class TEXT {
        public static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_creation_by_category_screen.";
        public static final Component TITLE = Component.translatable(PREFIX + "title");

        public static final Component CATEGORIES_LABEL = Component.translatable(PREFIX + "categories_label");
        public static final Component MARKETS_LABEL = Component.translatable(PREFIX + "markets_label");
        public static final Component AJUST_DEFAULT_PRICES_LABEL = Component.translatable(PREFIX + "adjust_default_prices_label");
        public static final Component ADD_ALL_MARKETS_LABEL = Component.translatable(PREFIX + "add_all_markets_label");
        public static final Component SELECTED_MARKETS_LABEL = Component.translatable(PREFIX + "selected_markets_label");
        public static final Component REMOVE_SELECTED_MARKTS_BUTTON = Component.translatable(PREFIX + "remove_selected_markets_button");
        public static final Component CREATE_SELECTED_MARKTS_BUTTON = Component.translatable(PREFIX + "create_selected_markets_button");



        // Tooltips
        //public static final Component MARKET_ELEMENT_CREATE_CHECK_BOX_TOOLTIP = Component.translatable(PREFIX + "market_element_create_check_box.tooltip");
        public static final Component CREATE_SELECTED_MARKTS_BUTTON_TOOLTIP = Component.translatable(PREFIX + "create_selected_markets_button.tooltip");
        public static final Component ADD_MARKET_BUTTON_TOOLTIP = Component.translatable(PREFIX + "add_market_button.tooltip");
        public static final Component REMOVE_MARKET_BUTTON_TOOLTIP = Component.translatable(PREFIX + "remove_market_button.tooltip");


        // Ask Popup
        //public static final Component ASK_TITLE = Component.translatable(PREFIX + "ask.title");
        //public static final Component ASK_MSG = Component.translatable(PREFIX + "ask.msg");
    }

    public static final class MarketElement extends StockMarketGuiElement {

        private final TradingPairView tradingPairView;
        //private final CheckBox doCreateCheckBox;
        private final Button button;
        private final MarketFactory.DefaultMarketSetupData defaultMarketSetupData;
        private final int defaultBackgroundColor;
        private final int usedBackgroundColor = 0x5500AA00;
        private final Consumer<TradingPair> onButtonClicked;

        public MarketElement(MarketFactory.DefaultMarketSetupData data, String buttonText, String buttonTooltip, Consumer<TradingPair> onButtonClicked)
        {
            super();
            this.onButtonClicked = onButtonClicked;
            defaultMarketSetupData = data;
            tradingPairView = new TradingPairView();
            tradingPairView.setTradingPair(data.tradingPair);
            tradingPairView.setClickable(false);
            tradingPairView.setHoverTooltipSupplier(this::getTooltipText);
            tradingPairView.setHoverTooltipFontScale(0.8f);
            tradingPairView.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
            defaultBackgroundColor = tradingPairView.getIdleColor();
            button = new Button(buttonText);
            //button.setTextFontScale(2f);
            button.setHoverTooltipSupplier(() -> buttonTooltip);
            button.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
            button.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);;
            button.setOnFallingEdge(this::use);
            //doCreateCheckBox = new CheckBox("");
            //doCreateCheckBox.setHoverTooltipSupplier(TEXT.MARKET_ELEMENT_CREATE_CHECK_BOX_TOOLTIP::getString);
            //doCreateCheckBox.setTooltipMousePositionAlignment(Alignment.RIGHT);

            addChild(tradingPairView);
            addChild(button);
            //addChild(doCreateCheckBox);

            setHeight(20);
            setEnableBackground(false);
            setEnableOutline(false);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            tradingPairView.setBounds(0, 0, width - 20, 20);
            button.setBounds(tradingPairView.getRight(), 0, 20, 20);
            //doCreateCheckBox.setBounds(tradingPairView.getRight(), 0, 20, 20);
        }
        public MarketFactory.DefaultMarketSetupData getDefaultMarketSetupData() {
            return defaultMarketSetupData;
        }
        public TradingPair getTradingPair() {
            return defaultMarketSetupData.tradingPair;
        }
        public void markAsUsed(boolean used)
        {
            if (used) {
                tradingPairView.setIdleColor(usedBackgroundColor);
            } else {
                tradingPairView.setIdleColor(defaultBackgroundColor);
            }
        }
        public void use()
        {
            if (onButtonClicked != null) {
                onButtonClicked.accept(tradingPairView.getTradingPair());
                markAsUsed(true);
            }
        }
        public String getTooltipText()
        {
            return tradingPairView.getTradingPair().getShortDescription() + "\n" +
                    defaultMarketSetupData.toString();
        }
        /*public boolean isDoCreate() {
            return doCreateCheckBox.isChecked();
        }*/
    }

    public static final class CategoryElement extends StockMarketGuiElement
    {
        private final MarketFactory.DefaultMarketSetupDataGroup category;

        private final ItemView iconView;
        private final Button button;
        private final List<MarketElement> marketElementList = new ArrayList<>();

        public CategoryElement(MarketFactory.DefaultMarketSetupDataGroup category, Consumer<CategoryElement> onClick, Consumer<TradingPair> onMarketElementAdd) {
            this.category = category;

            this.iconView = new ItemView();
            ItemID iconID = category.getIconItemID();
            if(iconID != null ) {
                this.iconView.setItemStack(iconID.getStack());
            }

            this.button = new Button(category.groupName);
            this.button.setOnFallingEdge(() -> onClick.accept(this));



            for(MarketFactory.DefaultMarketSetupData elementData : category.marketSetupDataList) {
                MarketElement marketElement = new MarketElement(elementData, "+", TEXT.ADD_MARKET_BUTTON_TOOLTIP.getString(), onMarketElementAdd);
                marketElementList.add(marketElement);
            }

            addChild(iconView);
            addChild(button);

            this.setHeight(20);
        }
        public MarketFactory.DefaultMarketSetupDataGroup getCategory() {
            return category;
        }
        public MarketFactory.DefaultMarketSetupData getDefaultMarketSetupData(TradingPair pair) {
            for (MarketElement marketElement : marketElementList) {
                if (marketElement.getTradingPair().equals(pair)) {
                    return marketElement.getDefaultMarketSetupData();
                }
            }
            return null; // Not found
        }
        public List<MarketElement> getMarketElementList() {
            return marketElementList;
        }
        public void markAsUsed(TradingPair pair, boolean used)
        {
            for (MarketElement marketElement : marketElementList) {
                if (marketElement.getTradingPair().equals(pair)) {
                    marketElement.markAsUsed(used);
                    return; // No need to check further
                }
            }
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            int height = getHeight();

            button.setBounds(0, 0, width - height, height);
            iconView.setBounds(button.getRight(), 0, height, height);

        }
        /*public List<MarketFactory.DefaultMarketSetupData> getSelectedMarketSetupData() {
            List<MarketFactory.DefaultMarketSetupData> selectedData = new ArrayList<>();
            for (MarketElement marketElement : marketElementList) {
                if (marketElement.isDoCreate()) {
                    selectedData.add(marketElement.getDefaultMarketSetupData());
                }
            }
            return selectedData;
        }*/
    }
    private final Label categoriesLabel;
    private final ListView categoriesListView;


    private final Label marketsLabel;
    private final ListView marketsListView;
    private final Button addAllMarketsButton;
    private final List<CategoryElement> categories = new ArrayList<>();


    private final List<MarketElement> selectedElements = new ArrayList<>();
    private final List<TradingPair> removeElementsLater = new ArrayList<>();
    private final Label selectedMarketsLabel;
    private final ListView selectedMarketsListView;
    private final Button removeAllMarketsButton;
    private final Button ajustDefaultPricesButton;
    private final Button createSelectedMarketsButton;

    private final Frame defaultContents;



    private final DefaultPriceAjustmentWidget defaultPriceAjustmentWidget;
    private boolean useAjustedPrices = false;

    private final ManagementScreen parentScreen;
    public MarketCreationByCategoryScreen(ManagementScreen parentScreen) {
        super(TEXT.TITLE);
        this.parentScreen = parentScreen;

        defaultContents = new Frame();
        defaultContents.setEnableBackground(false);
        defaultContents.setEnableOutline(false);

        this.categoriesLabel = new Label(TEXT.CATEGORIES_LABEL.getString());
        categoriesLabel.setAlignment(GuiElement.Alignment.CENTER);
        this.categoriesListView = new VerticalListView();
        Layout categoriesLayout = new LayoutVertical();
        categoriesLayout.stretchX = true;
        categoriesListView.setLayout(categoriesLayout);


        this.marketsLabel = new Label(TEXT.MARKETS_LABEL.getString());
        marketsLabel.setAlignment(GuiElement.Alignment.CENTER);
        this.marketsListView = new VerticalListView();
        Layout marketsLayout = new LayoutVertical();
        marketsLayout.stretchX = true;
        marketsListView.setLayout(marketsLayout);
        addAllMarketsButton = new Button(TEXT.ADD_ALL_MARKETS_LABEL.getString());
        addAllMarketsButton.setOnFallingEdge(() -> {
            var childs = marketsListView.getChilds();
            for (GuiElement child : childs) {
                if (child instanceof MarketElement marketElement) {
                    marketElement.use();
                }
            }
        });


        selectedMarketsLabel = new Label(TEXT.SELECTED_MARKETS_LABEL.getString());
        selectedMarketsLabel.setAlignment(GuiElement.Alignment.CENTER);
        selectedMarketsListView = new VerticalListView();
        Layout selectedMarketsLayout = new LayoutVertical();
        selectedMarketsLayout.stretchX = true;
        selectedMarketsListView.setLayout(selectedMarketsLayout);
        removeAllMarketsButton = new Button(TEXT.REMOVE_SELECTED_MARKTS_BUTTON.getString());
        removeAllMarketsButton.setOnFallingEdge(() -> {
            for (MarketElement el : selectedElements) {
                removeElementsLater.add(el.getTradingPair());
            }
        });


        defaultPriceAjustmentWidget = new DefaultPriceAjustmentWidget(this::onApplyPriceAjustments, this::onCancelPriceAjustments);
        defaultPriceAjustmentWidget.setEnabled(false);
        ajustDefaultPricesButton = new Button(TEXT.AJUST_DEFAULT_PRICES_LABEL.getString());
        ajustDefaultPricesButton.setOnFallingEdge(()->{
            getMarketManager().requestDefaultPriceAjustmentFactors((factorsData) -> {
                defaultPriceAjustmentWidget.setFactors(factorsData);
                defaultPriceAjustmentWidget.setDefaultMarketSetupDataList(getSelectedMarkets());
                //removeElement(defaultContents);
                //addElement(defaultPriceAjustmentWidget);
                defaultPriceAjustmentWidget.setEnabled(true);
                defaultContents.setEnabled(false);
                updateLayout(this.getGui());
            });

        });



        createSelectedMarketsButton = new Button(TEXT.CREATE_SELECTED_MARKTS_BUTTON.getString());
        createSelectedMarketsButton.setOnFallingEdge(this::createSelectedMarkets);
        createSelectedMarketsButton.setHoverTooltipSupplier(TEXT.CREATE_SELECTED_MARKTS_BUTTON_TOOLTIP::getString);
        createSelectedMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
        createSelectedMarketsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        defaultContents.addChild(categoriesLabel);
        defaultContents.addChild(categoriesListView);
        defaultContents.addChild(marketsLabel);
        defaultContents.addChild(marketsListView);
        defaultContents.addChild(addAllMarketsButton);
        defaultContents.addChild(selectedMarketsLabel);
        defaultContents.addChild(selectedMarketsListView);
        defaultContents.addChild(removeAllMarketsButton);
        defaultContents.addChild(ajustDefaultPricesButton);
        defaultContents.addChild(createSelectedMarketsButton);

        addElement(defaultContents);
        addElement(defaultPriceAjustmentWidget);

        getMarketManager().requestMarketCategories(this::setCategories);
    }
    @Override
    public void onClose() {
        super.onClose();
        int mousePosX = getMouseX();
        int mousePosY = getMouseY();
        minecraft.setScreen(parentScreen);
        parentScreen.updateTradingItems();
        parentScreen.updateLoadCurrentSettings();
        setMousePos(mousePosX, mousePosY);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        defaultContents.setBounds(0,0,getWidth(),getHeight());
        defaultPriceAjustmentWidget.setBounds(0,0,getWidth(),getHeight());

        categoriesLabel.setBounds(padding, padding, width/3-spacing, 15);
        categoriesListView.setBounds(padding, categoriesLabel.getBottom() + spacing, categoriesLabel.getWidth(), height - categoriesLabel.getBottom() - spacing + padding);

        marketsLabel.setBounds(categoriesListView.getRight() + spacing, padding, categoriesLabel.getWidth(), categoriesLabel.getHeight());
        marketsListView.setBounds(marketsLabel.getLeft(), categoriesListView.getTop(), marketsLabel.getWidth(), categoriesListView.getHeight()-20-spacing);
        addAllMarketsButton.setBounds(marketsListView.getLeft(), marketsListView.getBottom() + spacing, marketsListView.getWidth(), 20);

        selectedMarketsLabel.setBounds(marketsListView.getRight() + spacing, padding, width - marketsLabel.getRight(), marketsLabel.getHeight());
        selectedMarketsListView.setBounds(selectedMarketsLabel.getLeft(), selectedMarketsLabel.getBottom() + spacing, selectedMarketsLabel.getWidth(), categoriesListView.getHeight() - 3*(spacing + 20));
        removeAllMarketsButton.setBounds(selectedMarketsListView.getLeft(), selectedMarketsListView.getBottom() + spacing, selectedMarketsListView.getWidth(), 20);
        ajustDefaultPricesButton.setBounds(removeAllMarketsButton.getLeft(), removeAllMarketsButton.getBottom() + spacing, removeAllMarketsButton.getWidth(), 20);
        createSelectedMarketsButton.setBounds(ajustDefaultPricesButton.getLeft(), ajustDefaultPricesButton.getBottom() + spacing, ajustDefaultPricesButton.getWidth(), 20);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics)
    {
        if(!removeElementsLater.isEmpty()) {
            for (TradingPair toRemove : removeElementsLater) {
                for (MarketElement el : selectedElements) {
                    if (el.getTradingPair().equals(toRemove)) {
                        selectedMarketsListView.removeChild(el);
                        selectedElements.remove(el);

                        for(CategoryElement category : categories) {
                            category.markAsUsed(toRemove, false);
                        }
                        break;
                    }
                }
            }
            removeElementsLater.clear();
        }
        super.renderBackground(guiGraphics);
    }


    private void setCategories(List<MarketFactory.DefaultMarketSetupDataGroup> categories)
    {
        categoriesListView.removeChilds();
        marketsListView.removeChilds();
        this.categories.clear();

        for (MarketFactory.DefaultMarketSetupDataGroup category : categories) {
            CategoryElement categoryElement = new CategoryElement(category, this::onCategoryButtonClicked, this::onMarketElementAdd);
            categoriesListView.addChild(categoryElement);
            this.categories.add(categoryElement);
        }
    }

    private void onCategoryButtonClicked(CategoryElement categoryElement)
    {
        marketsListView.removeChilds();
        for (MarketElement marketElement : categoryElement.getMarketElementList()) {
            marketsListView.addChild(marketElement);
        }
    }
    private void onMarketElementAdd(TradingPair tradingPair)
    {
        for(MarketElement el : selectedElements)
        {
            if( el.getTradingPair().equals(tradingPair)) {
                return; // Element already exists
            }
        }
        MarketFactory.DefaultMarketSetupData data = null;
        for(CategoryElement category : categories) {
            data = category.getDefaultMarketSetupData(tradingPair);
            if (data != null) {
                break;
            }
        }
        MarketElement marketElement = new MarketElement(
                data,
                "-",
                TEXT.REMOVE_MARKET_BUTTON_TOOLTIP.getString(),
                this::onMarketElementRemove);

        selectedMarketsListView.addChild(marketElement);
        selectedElements.add(marketElement);
    }
    private void onMarketElementRemove(TradingPair tradingPair)
    {
        removeElementsLater.add(tradingPair);
    }
    private void createSelectedMarkets()
    {
        List<MarketFactory.DefaultMarketSetupData> selectedMarkets = getSelectedMarkets();


        List<MarketFactory.DefaultMarketSetupData> finalSelectedMarkets;
        if(useAjustedPrices) {
            defaultPriceAjustmentWidget.setDefaultMarketSetupDataList(selectedMarkets);
            finalSelectedMarkets = defaultPriceAjustmentWidget.getAjustedDefaultMarketSetupDataList();
        } else {
            finalSelectedMarkets = selectedMarkets;
        }

        if(finalSelectedMarkets.isEmpty())
            return;

        getMarketManager().requestCreateMarkets(finalSelectedMarkets, (result) -> {
            for(int i=0; i< result.size(); i++) {
                ClientPlayerUtilities.printToConsole("["+i+1+"] Market creation result for " + finalSelectedMarkets.get(i).tradingPair.getShortDescription() + ": " + (result.get(i) ? "Success" : "Failed"));
            }
        });
    }
    private List<MarketFactory.DefaultMarketSetupData> getSelectedMarkets()
    {
        List<MarketFactory.DefaultMarketSetupData> selectedMarkets = new ArrayList<>();
        for (CategoryElement category : categories) {
            for(MarketElement marketElement : category.getMarketElementList()) {
                for(MarketElement selectedElement : selectedElements) {
                    if (selectedElement.getTradingPair().equals(marketElement.getTradingPair())) {
                        selectedMarkets.add(selectedElement.getDefaultMarketSetupData());
                        break; // No need to check further for this market
                    }
                }
            }
        }
        return selectedMarkets;
    }

    private void onApplyPriceAjustments()
    {
        useAjustedPrices = true;
        defaultPriceAjustmentWidget.setEnabled(false);
        defaultContents.setEnabled(true);

        // Save the factors on the server
        DefaultPriceAjustmentFactorsData factorsData = defaultPriceAjustmentWidget.getFactors();
        getMarketManager().updateDefaultPriceAjustmentFactors(factorsData, (r)->{
            defaultPriceAjustmentWidget.setFactors(factorsData);
        });
        //removeElement(defaultPriceAjustmentWidget);
        //addElement(defaultContents);
        updateLayout(this.getGui());
    }
    private void onCancelPriceAjustments()
    {
        useAjustedPrices = false;
        defaultPriceAjustmentWidget.setEnabled(false);
        defaultContents.setEnabled(true);
        //removeElement(defaultPriceAjustmentWidget);
        //addElement(defaultContents);
        updateLayout(this.getGui());
    }
}
