package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAjustmentFactorsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.screen.uiElements.DefaultPriceAdjustmentWidget;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
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
        //private final int defaultBackgroundColor;
        //private final int usedBackgroundColor = 0x5500AA00;
        private final BiConsumer<TradingPair, MarketElement> onUseButtonClicked;
        private final BiConsumer<TradingPair, MarketElement> onUnuseButtonClicked;

        public MarketElement(MarketFactory.DefaultMarketSetupData data, @Nullable String buttonText, String buttonTooltip,
                             BiConsumer<TradingPair, MarketElement> onUseButtonClicked, BiConsumer<TradingPair, MarketElement> onUnuseButtonClicked)
        {
            super();
            this.onUseButtonClicked = onUseButtonClicked;
            this.onUnuseButtonClicked = onUnuseButtonClicked;
            setHeight(15);
            defaultMarketSetupData = data;
            tradingPairView = new TradingPairView();
            tradingPairView.setTradingPair(data.tradingPair);
            tradingPairView.setClickable(true);
            tradingPairView.setTextFontScale(0.8f);
            tradingPairView.setHoverTooltipSupplier(this::getTooltipText);
            //tradingPairView.setHoverTooltipFontScale(0.8f);
            tradingPairView.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
            //defaultBackgroundColor = tradingPairView.getIdleColor();

            tradingPairView.setIsToggleable(true);
            tradingPairView.setOnFallingEdge(()->
            {
                if(tradingPairView.isToggled())
                {
                    use();
                }
                else
                {
                    unuse();
                }
            });
            //tradingPairView.setHoverTooltipSupplier(() -> buttonTooltip);
            //tradingPairView.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
            tradingPairView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);;

            if(buttonText != null)
            {
                button = new Button(buttonText);
                button.setHoverTooltipSupplier(() -> buttonTooltip);
                button.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
                button.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);;
                button.setOnFallingEdge(this::use);

                addChild(button);
            }
            else
                button = null;


            addChild(tradingPairView);
            //addChild(button);
            //addChild(doCreateCheckBox);


            setEnableBackground(false);
            setEnableOutline(false);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            int height = getHeight();
            if(button != null) {
                tradingPairView.setBounds(0, 0, width - height, height);
                button.setBounds(tradingPairView.getRight(), 0, height, height);
            }
            else
                tradingPairView.setBounds(0, 0, width, height);
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
            tradingPairView.setToggled(used);
        }
        public void use()
        {
            if (onUseButtonClicked != null) {
                onUseButtonClicked.accept(tradingPairView.getTradingPair(), this);
                //markAsUsed(true);
            }
        }
        public void unuse()
        {
            if (onUnuseButtonClicked != null) {
                onUnuseButtonClicked.accept(tradingPairView.getTradingPair(), this);
                //markAsUsed(false);
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

        public CategoryElement(MarketFactory.DefaultMarketSetupDataGroup category, Consumer<CategoryElement> onClick,
                               BiConsumer<TradingPair, MarketElement> onMarketElementAdd,
                               BiConsumer<TradingPair, MarketElement> onMarketElementRemove) {
            this.category = category;

            this.iconView = new ItemView();
            ItemID iconID = category.getIconItemID();
            if(iconID != null ) {
                this.iconView.setItemStack(iconID.getStack());
            }

            this.button = new Button(category.groupName);
            this.button.setOnFallingEdge(() -> onClick.accept(this));



            for(MarketFactory.DefaultMarketSetupData elementData : category.marketSetupDataList) {
                MarketElement marketElement = new MarketElement(elementData, null, TEXT.ADD_MARKET_BUTTON_TOOLTIP.getString(),
                        onMarketElementAdd, onMarketElementRemove);
                marketElementList.add(marketElement);
            }

            addChild(iconView);
            addChild(button);

            this.setHeight(15);
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
            iconView.setBounds(button.getRight()+1, 1, height-2, height-2);

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

    public static final class DefaultContents extends StockMarketGuiElement{
        private final Label categoriesLabel;
        private final ListView categoriesListView;


        private final Label marketsLabel;
        private final ListView marketsListView;
        private final Button addAllMarketsButton;
        private final List<CategoryElement> categories = new ArrayList<>();


        private final List<MarketElement> selectedElements = new ArrayList<>();
        private final List<MarketElement> removeElementsLater = new ArrayList<>();
        private final Label selectedMarketsLabel;
        private final ListView selectedMarketsListView;
        private final Button removeAllMarketsButton;
        private final Button ajustDefaultPricesButton;
        private final Button createSelectedMarketsButton;

        private String pairAlreadySelectedTooltipMessage = null;
        private final TimerMillis pairAlreadySelectedTooltipTimer = new TimerMillis(false);


        public DefaultContents(Runnable onCreateSelectedMarketsButtonClicked, Runnable onAjustPricesButtonClicked)
        {
            super();
            setEnableBackground(false);
            setEnableOutline(false);
            setTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            this.categoriesLabel = new Label(TEXT.CATEGORIES_LABEL.getString());
            categoriesLabel.setAlignment(GuiElement.Alignment.CENTER);
            this.categoriesListView = new VerticalListView();
            Layout categoriesLayout = new LayoutVertical();
            categoriesLayout.stretchX = true;
            categoriesListView.setLayout(categoriesLayout);


            this.marketsLabel = new Label(TEXT.MARKETS_LABEL.getString());
            marketsLabel.setAlignment(GuiElement.Alignment.CENTER);
            this.marketsListView = new VerticalListView();
            LayoutGrid marketsLayout = new LayoutGrid();
            marketsLayout.stretchX = true;
            marketsLayout.columns = 4;
            marketsLayout.spacing = 2;
            marketsLayout.alignment = Alignment.TOP;
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
            LayoutGrid selectedMarketsLayout = new LayoutGrid();
            selectedMarketsLayout.stretchX = true;
            selectedMarketsLayout.columns = 3;
            selectedMarketsLayout.spacing = 2;
            selectedMarketsLayout.alignment = Alignment.TOP;
            selectedMarketsListView.setLayout(selectedMarketsLayout);
            removeAllMarketsButton = new Button(TEXT.REMOVE_SELECTED_MARKTS_BUTTON.getString());
            removeAllMarketsButton.setOnFallingEdge(() -> {
                for (MarketElement el : selectedElements) {
                    removeElementsLater.add(el);
                }
            });

            ajustDefaultPricesButton = new Button(TEXT.AJUST_DEFAULT_PRICES_LABEL.getString());
            ajustDefaultPricesButton.setOnFallingEdge(onAjustPricesButtonClicked);


            createSelectedMarketsButton = new Button(TEXT.CREATE_SELECTED_MARKTS_BUTTON.getString());
            createSelectedMarketsButton.setOnFallingEdge(onCreateSelectedMarketsButtonClicked);
            createSelectedMarketsButton.setHoverTooltipSupplier(TEXT.CREATE_SELECTED_MARKTS_BUTTON_TOOLTIP::getString);
            createSelectedMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            createSelectedMarketsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            addChild(categoriesLabel);
            addChild(categoriesListView);
            addChild(marketsLabel);
            addChild(marketsListView);
            addChild(addAllMarketsButton);
            addChild(selectedMarketsLabel);
            addChild(selectedMarketsListView);
            addChild(removeAllMarketsButton);
            addChild(ajustDefaultPricesButton);
            addChild(createSelectedMarketsButton);

        }
        @Override
        protected void render() {
            if(pairAlreadySelectedTooltipMessage != null)
            {
                drawTooltip(pairAlreadySelectedTooltipMessage, getMouseX(), getMouseY(), 0x99F5C842, getTooltipBackgroundPadding(), Alignment.RIGHT);
                if(pairAlreadySelectedTooltipTimer.check())
                {
                    pairAlreadySelectedTooltipMessage = null; // Clear the message after showing it
                }
            }
        }

        @Override
        protected void layoutChanged() {
            int padding = 5;
            int spacing = 5;
            int width = getWidth() - padding * 2;
            int height = getHeight() - padding * 2;

            //defaultContents.setBounds(0,0,getWidth(),getHeight());
            //efaultPriceAjustmentWidget.setBounds(0,0,getWidth(),getHeight());

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
        public void renderBackground()
        {
            if(!removeElementsLater.isEmpty()) {
                for (MarketElement toRemove : removeElementsLater) {
                    selectedMarketsListView.removeChild(toRemove);
                    selectedElements.remove(toRemove);

                    for(CategoryElement category : categories) {
                        for(MarketElement marketElement : category.getMarketElementList()) {
                            if (marketElement.defaultMarketSetupData == toRemove.defaultMarketSetupData) {
                                marketElement.markAsUsed(false);
                                break; // No need to check further for this market
                            }
                        }
                    }
                }
                removeElementsLater.clear();
            }
        }


        private void setCategories(List<MarketFactory.DefaultMarketSetupDataGroup> categories)
        {
            categoriesListView.removeChilds();
            marketsListView.removeChilds();
            this.categories.clear();

            for (MarketFactory.DefaultMarketSetupDataGroup category : categories) {
                CategoryElement categoryElement = new CategoryElement(category, this::onCategoryButtonClicked, this::onMarketElementAdd, this::onMarketElementRemove);
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
        private void onMarketElementAdd(TradingPair tradingPair, MarketElement sender)
        {
            for(MarketElement el : selectedElements)
            {
                if(el.getTradingPair().equals(tradingPair)) {
                    pairAlreadySelectedTooltipMessage = "Market for trading pair " + tradingPair.getShortDescription() + " is already selected.";
                    pairAlreadySelectedTooltipTimer.start(2000);
                    //sender.markAsUsed(false);
                    return; // Element already exists
                }
            }
            MarketFactory.DefaultMarketSetupData data = null;
            for(CategoryElement category : categories) {
                for(MarketElement marketElement : category.getMarketElementList()) {
                    if (marketElement.defaultMarketSetupData == sender.defaultMarketSetupData) {
                        data = marketElement.defaultMarketSetupData;
                        break; // No need to check further for this market
                    }
                }
                if (data != null) {
                    break;
                }
            }
            if(data != null) {
                MarketElement marketElement = new MarketElement(
                        data,
                        "-",
                        TEXT.REMOVE_MARKET_BUTTON_TOOLTIP.getString(),
                        this::onMarketElementRemove, this::onMarketElementRemove);

                sender.markAsUsed(true);
                selectedMarketsListView.addChild(marketElement);
                selectedElements.add(marketElement);
            }
        }
        private void onMarketElementRemove(TradingPair tradingPair, MarketElement sender)
        {
            sender.markAsUsed(false);
            for(MarketElement el : selectedElements)
            {
                if(el.defaultMarketSetupData == sender.defaultMarketSetupData) {
                    removeElementsLater.add(el);
                    break; // No need to check further for this market
                }
            }
        }

        private List<MarketFactory.DefaultMarketSetupData> getSelectedMarkets()
        {
            List<MarketFactory.DefaultMarketSetupData> selectedMarkets = new ArrayList<>();
            for (CategoryElement category : categories) {
                for(MarketElement marketElement : category.getMarketElementList()) {
                    for(MarketElement selectedElement : selectedElements) {
                        if(marketElement.defaultMarketSetupData == selectedElement.defaultMarketSetupData)
                        {
                            selectedMarkets.add(selectedElement.defaultMarketSetupData);
                            break; // No need to check further for this market
                        }
                    /*
                    if (selectedElement.getTradingPair().equals(marketElement.getTradingPair())) {
                        selectedMarkets.add(selectedElement.getDefaultMarketSetupData());
                        break; // No need to check further for this market
                    }*/
                    }
                }
            }
            return selectedMarkets;
        }


    }


    private final DefaultContents defaultContents;
    private final DefaultPriceAdjustmentWidget defaultPriceAdjustmentWidget;


    private boolean useAjustedPrices = false;
    private final ManagementScreen parentScreen;
    public MarketCreationByCategoryScreen(ManagementScreen parentScreen) {
        super(TEXT.TITLE);
        this.parentScreen = parentScreen;

        defaultContents = new DefaultContents(this::createSelectedMarkets, this::onAjustPricesButtonClicked);




        defaultPriceAdjustmentWidget = new DefaultPriceAdjustmentWidget(this::onApplyPriceAjustments, this::onCancelPriceAjustments);
        defaultPriceAdjustmentWidget.setEnabled(false);






        addElement(defaultContents);
        addElement(defaultPriceAdjustmentWidget);

        getMarketManager().requestMarketCategories(defaultContents::setCategories);
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
        defaultPriceAdjustmentWidget.setBounds(0,0,getWidth(),getHeight());
    }


    private void onApplyPriceAjustments()
    {
        useAjustedPrices = true;
        defaultPriceAdjustmentWidget.setEnabled(false);
        defaultContents.setEnabled(true);

        // Save the factors on the server
        DefaultPriceAjustmentFactorsData factorsData = defaultPriceAdjustmentWidget.getFactors();
        getMarketManager().updateDefaultPriceAjustmentFactors(factorsData, (r)->{
            defaultPriceAdjustmentWidget.setFactors(factorsData);
        });
        //removeElement(defaultPriceAdjustmentWidget);
        //addElement(defaultContents);
        updateLayout(this.getGui());
    }
    private void onCancelPriceAjustments()
    {
        useAjustedPrices = false;
        defaultPriceAdjustmentWidget.setEnabled(false);
        defaultContents.setEnabled(true);
        //removeElement(defaultPriceAdjustmentWidget);
        //addElement(defaultContents);
        updateLayout(this.getGui());
    }
    private void createSelectedMarkets()
    {
        List<MarketFactory.DefaultMarketSetupData> selectedMarkets = defaultContents.getSelectedMarkets();


        List<MarketFactory.DefaultMarketSetupData> finalSelectedMarkets;
        if(useAjustedPrices) {
            defaultPriceAdjustmentWidget.setDefaultMarketSetupDataList(selectedMarkets);
            finalSelectedMarkets = defaultPriceAdjustmentWidget.getAjustedDefaultMarketSetupDataList();
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
    private void onAjustPricesButtonClicked()
    {
        getMarketManager().requestDefaultPriceAjustmentFactors((factorsData) -> {
            defaultPriceAdjustmentWidget.setFactors(factorsData);
            defaultPriceAdjustmentWidget.setDefaultMarketSetupDataList(defaultContents.getSelectedMarkets());
            //removeElement(defaultContents);
            //addElement(defaultPriceAdjustmentWidget);
            defaultPriceAdjustmentWidget.setEnabled(true);
            defaultContents.setEnabled(false);
            updateLayout(this.getGui());
        });
    }
}
