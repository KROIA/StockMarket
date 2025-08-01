package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MarketCreationScreen extends StockMarketGuiScreen {

    private static final String PREFIX = "gui."+StockMarketMod.MOD_ID+".trading_pair_creation_screen.";
    private static final String NAME = "trading_pair_creation_screen";
    private static final Component TITLE = Component.translatable(PREFIX + "title");
    private static final Component ITEM_SELECTION_VIEW_TITLE = Component.translatable(PREFIX + "item_selection_title");
    private static final Component CURRENCY_SELECTION_VIEW_TITLE = Component.translatable(PREFIX + "currency_selection_title");
    private static final Component CREATE_MAKET_BUTTON = Component.translatable(PREFIX + "create_market_button");

    public static final Component MARKET_OPEN = Component.translatable(PREFIX+"market_open");
    public static final Component ENABLE_VIRTUAL_ORDER_BOOK = Component.translatable(PREFIX+"enable_virtual_order_book");
    public static final Component ENABLE_MARKET_BOT = Component.translatable(PREFIX+"enable_market_bot");
    public static final Component ENABLE_TARGET_PRICE = Component.translatable(PREFIX+"enable_target_price");
    public static final Component ENABLE_VOLUME_TRACKING = Component.translatable(PREFIX+"enable_volume_tracking");
    public static final Component ENABLE_RANDOM_WALK = Component.translatable(PREFIX+"enable_random_walk");


    public static final Component TOOLTIP_OPEN_MARKET_CHECKBOX = Component.translatable(PREFIX+"tooltip_open_market_checkbox");
    public static final Component TOOLTIP_ENABLE_VIRTUAL_ORDER_BOOK_CHECKBOX = Component.translatable(PREFIX+"tooltip_enable_virtual_order_book_checkbox");
    public static final Component TOOLTIP_ENABLE_MARKETBOT_CHECKBOX = Component.translatable(PREFIX+"tooltip_enable_marketbot_checkbox");
    public static final Component TOOLTIP_ENABLE_TARGETPRICE_CHECKBOX = Component.translatable(PREFIX+"tooltip_enable_targetprice_checkbox");
    public static final Component TOOLTIP_ENABLE_VOLUMETRACKING_CHECKBOX = Component.translatable(PREFIX+"tooltip_enable_volumetracking_checkbox");
    public static final Component TOOLTIP_ENABLE_RANDOMWALK_CHECKBOX = Component.translatable(PREFIX+"tooltip_enable_randomwalk_checkbox");
    public static final Component TOOLTIP_CREATE_MARKET_BUTTON = Component.translatable(PREFIX+"tooltip_create_market_button");

    private final List<ItemStack> potentialTradingItems = new ArrayList<>();
    private ItemStack selectedItem = ItemStack.EMPTY;
    private ItemStack selectedCurrency = ItemStack.EMPTY;



    private final Label itemSelectionViewTitle;
    private final ItemView selectedItemView;
    private final ItemSelectionView itemSelectionView;
    private final Label currencySelectionViewTitle;
    private final ItemView getSelectedItemView;
    private final ItemSelectionView currencySelectionView;


    // Market parameters
    private final ListView settingsListView;
    private final TradingPairView currentPairView;
    private final CheckBox openMarketCheckBox;
    private final TextBox initialPriceTextBox;
    private final TextBox candleTimeTextBoxMinutes;

    private final CheckBox enableVirtualOrderBook;
    private final CheckBox enableMarketBot;
    private final CheckBox enableTargetPrice;
    private final CheckBox enableVolumeTracking;
    private final CheckBox enableRandomWalk;
    private final Slider volatilitySlider;
    private final Slider raritySlider;
    private final Slider marketSpeedSlider;



    private final Button createMarketButton;


    private final ManagementScreen parent;
    public MarketCreationScreen(ManagementScreen parent)
    {
        super(TITLE);
        this.parent = parent;


        itemSelectionViewTitle = new Label(ITEM_SELECTION_VIEW_TITLE.getString());
        selectedItemView = new ItemView();
        currencySelectionViewTitle = new Label(CURRENCY_SELECTION_VIEW_TITLE.getString());
        getSelectedItemView = new ItemView();
        itemSelectionView = new ItemSelectionView(this::onItemSelected);
        currencySelectionView = new ItemSelectionView(this::onCurrencySelected);


        settingsListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        settingsListView.setLayout(layout);
        settingsListView.setEnabled(false); // Initially disabled until an item and currency are selected


        currentPairView = new TradingPairView();
        currentPairView.setEnabled(false); // Initially disabled until an item and currency are selected
        //initialPriceLabel = new Label(INITIAL_PRICE_LABEL.getString());
        initialPriceTextBox = new TextBox();
        initialPriceTextBox.setAllowLetters(false);
        initialPriceTextBox.setAllowNumbers(true, false);
        initialPriceTextBox.setOnTextChanged(this::onInitialPriceChanged);
        candleTimeTextBoxMinutes = new TextBox();
        candleTimeTextBoxMinutes.setAllowLetters(false);
        candleTimeTextBoxMinutes.setAllowNumbers(true, false);
        candleTimeTextBoxMinutes.setText("5"); // Default to 5 minutes


        openMarketCheckBox = new CheckBox(MARKET_OPEN.getString());
        openMarketCheckBox.setChecked(false); // Default to open market
        enableVirtualOrderBook = new CheckBox(ENABLE_VIRTUAL_ORDER_BOOK.getString());
        enableVirtualOrderBook.setChecked(true);
        enableMarketBot = new CheckBox(ENABLE_MARKET_BOT.getString());
        enableMarketBot.setChecked(true);

        enableTargetPrice = new CheckBox(ENABLE_TARGET_PRICE.getString());
        enableTargetPrice.setChecked(true); // Default to enabled
        enableVolumeTracking = new CheckBox(ENABLE_VOLUME_TRACKING.getString());
        enableVolumeTracking.setChecked(true); // Default to enabled
        enableRandomWalk = new CheckBox(ENABLE_RANDOM_WALK.getString());
        enableRandomWalk.setChecked(true); // Default to enabled

        volatilitySlider = new HorizontalSlider();
        raritySlider = new HorizontalSlider();
        marketSpeedSlider = new HorizontalSlider();
        volatilitySlider.setSliderValue(0.5);
        raritySlider.setSliderValue(0.5);
        marketSpeedSlider.setSliderValue(0.8);


        enableMarketBot.setOnStateChanged((checked)->{
            enableTargetPrice.setEnabled(checked);
            enableVolumeTracking.setEnabled(checked);
            enableRandomWalk.setEnabled(checked);
            volatilitySlider.setEnabled(checked);
            raritySlider.setEnabled(checked);
            marketSpeedSlider.setEnabled(checked);
        });
        enableVirtualOrderBook.setOnStateChanged((checked)->{
            // If virtual order book is enabled, we can enable the market bot
            enableMarketBot.setEnabled(checked);
            if(!checked) {
                // If virtual order book is disabled, we disable the market bot
                enableMarketBot.setChecked(false);
            }
            enableTargetPrice.setEnabled(checked);
            enableVolumeTracking.setEnabled(checked);
            enableRandomWalk.setEnabled(checked);
            volatilitySlider.setEnabled(checked);
            raritySlider.setEnabled(checked);
            marketSpeedSlider.setEnabled(checked);

        });

        createMarketButton = new Button(CREATE_MAKET_BUTTON.getString(), this::onCreateMarketButtonPressed);
        createMarketButton.setEnabled(false);

        currentPairView.setHoverTooltipSupplier(this::getInitialPriceTooltip);
        currentPairView.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
        initialPriceTextBox.setHoverTooltipSupplier(this::getInitialPriceTooltip);
        candleTimeTextBoxMinutes.setHoverTooltipSupplier(()->{
            int minutes = candleTimeTextBoxMinutes.getInt();
            return StockMarketTextMessages.getTradingPairCreationScreenCandleTimeTooltip(minutes);
        });
        volatilitySlider.setHoverTooltipSupplier(()->{return StockMarketTextMessages.getTradingPairCreationScreenVolatilityTooltip(volatilitySlider.getSliderValue());});
        raritySlider.setHoverTooltipSupplier(()->{return StockMarketTextMessages.getTradingPairCreationScreenRarityTooltip(raritySlider.getSliderValue());});
        marketSpeedSlider.setHoverTooltipSupplier(()->{return StockMarketTextMessages.getTradingPairCreationScreenMarketSpeedTooltip(marketSpeedSlider.getSliderValue(), getMarketSpeedMS());});

        openMarketCheckBox.setHoverTooltipSupplier(TOOLTIP_OPEN_MARKET_CHECKBOX::getString);
        enableVirtualOrderBook.setHoverTooltipSupplier(TOOLTIP_ENABLE_VIRTUAL_ORDER_BOOK_CHECKBOX::getString);
        enableMarketBot.setHoverTooltipSupplier(TOOLTIP_ENABLE_MARKETBOT_CHECKBOX::getString);
        enableTargetPrice.setHoverTooltipSupplier(TOOLTIP_ENABLE_TARGETPRICE_CHECKBOX::getString);
        enableVolumeTracking.setHoverTooltipSupplier(TOOLTIP_ENABLE_VOLUMETRACKING_CHECKBOX::getString);
        enableRandomWalk.setHoverTooltipSupplier(TOOLTIP_ENABLE_RANDOMWALK_CHECKBOX::getString);

        createMarketButton.setHoverTooltipSupplier(TOOLTIP_CREATE_MARKET_BUTTON::getString);



        initialPriceTextBox.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        candleTimeTextBoxMinutes.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        volatilitySlider.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        raritySlider.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        marketSpeedSlider.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        openMarketCheckBox.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        enableVirtualOrderBook.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        enableMarketBot.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        enableTargetPrice.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        enableVolumeTracking.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        enableRandomWalk.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);
        createMarketButton.setTooltipMousePositionAlignment(GuiElement.Alignment.RIGHT);




        addElement(itemSelectionViewTitle);
        addElement(selectedItemView);
        addElement(currencySelectionViewTitle);
        addElement(getSelectedItemView);
        addElement(currencySelectionView);
        addElement(itemSelectionView);

        addElement(settingsListView);
        addElement(currentPairView);
        settingsListView.addChild(initialPriceTextBox);
        settingsListView.addChild(candleTimeTextBoxMinutes);
        settingsListView.addChild(openMarketCheckBox);
        settingsListView.addChild(enableVirtualOrderBook);
        settingsListView.addChild(enableMarketBot);
        settingsListView.addChild(enableTargetPrice);
        settingsListView.addChild(enableVolumeTracking);
        settingsListView.addChild(enableRandomWalk);
        settingsListView.addChild(volatilitySlider);
        settingsListView.addChild(raritySlider);
        settingsListView.addChild(marketSpeedSlider);
        settingsListView.addChild(createMarketButton);
        for(GuiElement element : settingsListView.getChilds())
        {
            element.setHeight(20);
        }



        itemSelectionView.setItems(List.of());
        currencySelectionView.setItems(List.of());
        getMarketManager().requestPotentialTradeItems("", (items) -> {
            potentialTradingItems.clear();
            for (ItemID itemID : items) {
                potentialTradingItems.add(itemID.getStack());
            }
            updatePotentialTradingItems();
        });
    }

    @Override
    public void onClose() {
        super.onClose();
        minecraft.setScreen(parent);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = this.width - padding * 2;
        int height = this.height - padding * 2;

        int elementHeight = 20;

        itemSelectionViewTitle.setBounds(padding, padding, width/3-elementHeight, elementHeight);
        itemSelectionViewTitle.setAlignment(GuiElement.Alignment.CENTER);
        selectedItemView.setBounds(itemSelectionViewTitle.getRight(), itemSelectionViewTitle.getTop(), elementHeight, elementHeight);
        itemSelectionView.setBounds(itemSelectionViewTitle.getLeft(), itemSelectionViewTitle.getBottom()+spacing,
                                    width/3, height - itemSelectionViewTitle.getHeight()-spacing);

        currencySelectionViewTitle.setBounds(itemSelectionView.getRight() + spacing, itemSelectionViewTitle.getTop(), width/3-elementHeight, elementHeight);
        currencySelectionViewTitle.setAlignment(GuiElement.Alignment.CENTER);
        getSelectedItemView.setBounds(currencySelectionViewTitle.getRight(), currencySelectionViewTitle.getTop(), elementHeight, elementHeight);
        currencySelectionView.setBounds(currencySelectionViewTitle.getLeft(), currencySelectionViewTitle.getBottom()+spacing,
                width/3, itemSelectionView.getHeight());

        int x = (2*width)/3 + spacing*3;
        int y = padding;
        int w = width - x + spacing;



        currentPairView.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        settingsListView.setBounds(x, y, w, height - y + spacing);
        /*initialPriceTextBox.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        openMarketCheckBox.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        candleTimeTextBoxMinutes.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        enableMarketBot.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        enableTargetPrice.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        enableVolumeTracking.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        enableRandomWalk.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        volatilitySlider.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        raritySlider.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;
        marketSpeedSlider.setBounds(x, y, w, elementHeight);
        y += elementHeight + spacing;

        createMarketButton.setBounds(x, y, w, elementHeight);*/




    }

    private String getInitialPriceTooltip()
    {
        String itemName;
        String currencyName;
        int price = initialPriceTextBox.getInt();
        if(selectedItem != null && !selectedItem.isEmpty())
            itemName = selectedItem.getHoverName().getString();
        else
            itemName = "Unknown Item";
        if(selectedCurrency != null && !selectedCurrency.isEmpty())
            currencyName = selectedCurrency.getHoverName().getString();
        else
            currencyName = "Unknown Currency";
        return StockMarketTextMessages.getTradingPairCreationScreenInitialPriceTooltip(itemName, currencyName, price);
    }
    private void updatePotentialTradingItems()
    {
        String itemsSearchText = itemSelectionView.getSearchText().toLowerCase();
        String currencySearchText = currencySelectionView.getSearchText().toLowerCase();

        List<ItemStack> itemStacks = new ArrayList<>();
        List<ItemStack> currencyStacks = new ArrayList<>();

        for (ItemStack item : potentialTradingItems) {

            String itemName = item.getHoverName().getString().toLowerCase();
            boolean isSelectedCurrency = selectedCurrency != null && selectedCurrency.equals(item);
            boolean isSelectedItem = selectedItem != null && selectedItem.equals(item);

            if (!isSelectedCurrency && itemName.contains(itemsSearchText)) {
                itemStacks.add(item);
            }
            if (!isSelectedItem && itemName.contains(currencySearchText)) {
                currencyStacks.add(item);
            }
        }

        itemSelectionView.setItems(itemStacks);
        currencySelectionView.setItems(currencyStacks);
    }

    private void onItemSelected(ItemStack itemStack) {
        selectedItem = itemStack;
        selectedItemView.setItemStack(selectedItem);
        currentPairView.setTradingPair(new TradingPair(new ItemID(selectedItem), new ItemID(selectedCurrency)));
        onNewPairCombinationSelected();
    }
    private void onCurrencySelected(ItemStack itemStack) {
        selectedCurrency = itemStack;
        getSelectedItemView.setItemStack(selectedCurrency);
        currentPairView.setTradingPair(new TradingPair(new ItemID(selectedItem), new ItemID(selectedCurrency)));
        onNewPairCombinationSelected();
    }

    private void onInitialPriceChanged(String newText) {
        if (newText.isEmpty()) {
            createMarketButton.setEnabled(false);
            return;
        }

        try {
            double price = Double.parseDouble(newText);
            if (price <= 0) {
                createMarketButton.setEnabled(false);
            } else {
                createMarketButton.setEnabled(true);
            }
        } catch (NumberFormatException e) {
            createMarketButton.setEnabled(false);
        }
    }
    private void onNewPairCombinationSelected()
    {
        if (selectedItem.isEmpty() || selectedCurrency.isEmpty()) {
            settingsListView.setEnabled(false);
            currentPairView.setEnabled(false);
            return; // Ensure both items are selected
        }

        TradingPair tradingPair = new TradingPair(new ItemID(selectedItem), new ItemID(selectedCurrency));
        settingsListView.setEnabled(true);
        currentPairView.setEnabled(true);
        getMarketManager().requestIsTradingPairAllowed(tradingPair, (success)->{
            getMarketManager().requestRecommendedPrice(tradingPair, (price) -> {
                initialPriceTextBox.setText(String.valueOf(price));
            });
            createMarketButton.setEnabled(success);
        });
    }

    private void onCreateMarketButtonPressed()
    {
        TradingPair tradingPair = new TradingPair(new ItemID(selectedItem), new ItemID(selectedCurrency));
        getMarketManager().requestCreateMarket(tradingPair, (success)->{
            if(success)
            {
                selectMarket(tradingPair);

                ServerMarketSettingsData marketSettings;

                if(getSelectedMarket() != null)
                {
                    boolean useVirtualOrderBook = enableVirtualOrderBook.isChecked();
                    boolean useMarketBot = enableMarketBot.isChecked();

                    ServerVolatilityBot.Settings botSettings = new ServerVolatilityBot.Settings();
                    VirtualOrderBook.Settings virtualOrderBookSettings = new VirtualOrderBook.Settings();

                    MarketFactory.DefaultMarketSetupGeneratorData data = new MarketFactory.DefaultMarketSetupGeneratorData();
                    data.defaultPrice = initialPriceTextBox.getInt();
                    data.updateIntervalMS = getMarketSpeedMS();
                    data.volatility = (float)volatilitySlider.getSliderValue();
                    data.rarity = (float)raritySlider.getSliderValue();
                    data.enableTargetPrice = enableTargetPrice.isChecked();
                    data.enableVolumeTracking = enableVolumeTracking.isChecked();
                    data.enableRandomWalk = enableRandomWalk.isChecked();
                    data.tradingPair = tradingPair;

                    boolean marketOpen = openMarketCheckBox.isChecked();
                    int candleTimeMinutes = candleTimeTextBoxMinutes.getInt();




                    if(useMarketBot && useVirtualOrderBook) {


                        MarketFactory.DefaultMarketSetupData setupData = data.generateDefaultMarketSetupData();
                        botSettings = setupData.botSettings;
                        virtualOrderBookSettings = setupData.virtualOrderBookSettings;
                    }

                    marketSettings = new ServerMarketSettingsData(tradingPair, botSettings, virtualOrderBookSettings,
                            marketOpen, 0,   candleTimeMinutes * 60000L);


                    if(!useMarketBot)
                        marketSettings.botSettingsData = null;
                    marketSettings.doCreateBotIfNotExists = useMarketBot;
                    if(!useVirtualOrderBook) {
                        marketSettings.virtualOrderBookSettingsData = null;
                        marketSettings.doDestroyVirtualOrderBookIfExists = true;
                    }
                    marketSettings.doCreateVirtualOrderBookIfNotExists = useVirtualOrderBook;


                    getSelectedMarket().requestSetMarketSettings(marketSettings, (success2) -> {
                        if(success2)
                        {
                            parent.setCurrentTradingPair(tradingPair);
                            parent.updateTradingItems();
                            onClose();
                        }
                        // Handle failure to create market
                        String msg = StockMarketTextMessages.getMarketCreationFailedMessage(tradingPair.getItem().getName(), tradingPair.getCurrency().getName());
                        minecraft.player.sendSystemMessage(Component.literal(msg));
                    });
                }
            }
            else
            {
                // Handle failure to create market
                String msg = StockMarketTextMessages.getMarketCreationFailedMessage(tradingPair.getItem().getName(), tradingPair.getCurrency().getName());
                minecraft.player.sendSystemMessage(Component.literal(msg));
            }
        });
    }


   /* public String getVolatilityTooltip()
    {
        double vol = volatilitySlider.getSliderValue();
        if(vol < 0.2) {
            return "Low";
        }
        else if(vol < 0.4) {
            return "Medium";
        }
        else if(vol < 0.6) {
            return "High";
        }
        else if(vol < 0.8) {
            return "Very High";
        }
        else {
            return "Crypto mode";
        }
    }
    public String getRarityTooltip()
    {
        double rarity = raritySlider.getSliderValue();
        if(rarity < 0.2) {
            return "Common";
        }
        else if(rarity < 0.4) {
            return "Uncommon";
        }
        else if(rarity < 0.6) {
            return "Rare";
        }
        else if(rarity < 0.8) {
            return "Very Rare";
        }
        else {
            return "Legendary";
        }
    }
    public String getMarketSpeedTooltip()
    {
        double speed = marketSpeedSlider.getSliderValue();
        long ms = getMarketSpeedMS();
        if(speed < 0.2) {
            return "Slow "+ms+"ms";
        }
        else if(speed < 0.4) {
            return "Medium "+ms+"ms";
        }
        else if(speed < 0.6) {
            return "Fast "+ms+"ms";
        }
        else if(speed < 0.8) {
            return "Very Fast "+ms+"ms";
        }
        else {
            return "Steroids "+ms+"ms";
        }
    }*/
    public long getMarketSpeedMS()
    {
        double speed = marketSpeedSlider.getSliderValue();
        // map from 100 to 10000
        return (long)((1-speed)*9900+100);
    }
}
