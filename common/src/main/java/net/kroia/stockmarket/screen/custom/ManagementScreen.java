package net.kroia.stockmarket.screen.custom;


import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.screen.uiElements.TradingPairSelectionView;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ManagementScreen extends StockMarketGuiScreen {
    private static final String NAME = "management_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    public static final Component NEW_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "new_trading_item");
    public static final Component REMOVE_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "remove_trading_item");

    public static final Component ASK_TITLE = Component.translatable(PREFIX+ "ask_remove_title");
    public static final Component ASK_MSG = Component.translatable(PREFIX+ "ask_remove_message");
    public static final Component MARKET_SETTINGS = Component.translatable(PREFIX+ "market_settings");
    public static final Component TOOLTIP_NEW_TRADING_PAIR = Component.translatable(PREFIX+ "tooltip_new_trading_pair");
    public static final Component TOOLTIP_REMOVE_SELECTED_TRADING_PAIR = Component.translatable(PREFIX+ "tooltip_remove_selected_trading_pair");
    public static final Component TOOLTIP_SELECTED_TRADING_PAIR = Component.translatable(PREFIX+ "tooltip_selected_trading_pair");



    private TradingPair tradingPair;
    private ServerMarketSettingsData currentMarketSettingsData;

    private final Button newTradingPairButton;
    private final Button removeTradingItemButton;
    private final TradingPairSelectionView tradableItemsView;
    private final TradingPairView currentTradingItemView;



    private final Button marketSettingsButton;
    private final CheckBox marketOpenCheckBox;


    private final Screen parentScreen;
    private MarketCreationScreen tradingPairCreationScreen;


    protected ManagementScreen(Screen parent) {
        super(TITLE);
        this.parentScreen = parent;
        tradableItemsView = new TradingPairSelectionView(this::setCurrentTradingPair);
        updateTradingItems();
        
        

        newTradingPairButton = new Button(NEW_TRADING_ITEM_BUTTON.getString());
        newTradingPairButton.setOnFallingEdge(() -> {


            tradingPairCreationScreen = new MarketCreationScreen(this);
            minecraft.setScreen(tradingPairCreationScreen);

        });
        newTradingPairButton.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
        newTradingPairButton.setHoverTooltipSupplier(TOOLTIP_NEW_TRADING_PAIR::getString);
        removeTradingItemButton = new Button(REMOVE_TRADING_ITEM_BUTTON.getString(), () -> {
            if(tradingPair != null) {
                AskPopupScreen popup = new AskPopupScreen(this, () -> 
                {
                    BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveMarket(tradingPair, (success)->{
                        setCurrentTradingPair(null);
                        updateTradingItems();
                    });
                    //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveTradingItem(tradingPair);

                }, () -> {}, ASK_TITLE.getString() + " "+tradingPair.getShortDescription() + "?", ASK_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(0xFFe8711c, 0xFFe04c12, 0xFFf22718, 0xFF70e815);
                minecraft.setScreen(popup);
            }
        });
        removeTradingItemButton.setIdleColor(0xFFe8711c);
        removeTradingItemButton.setHoverColor(0xFFe04c12);
        removeTradingItemButton.setPressedColor(0xFFe04c12);
        removeTradingItemButton.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
        removeTradingItemButton.setHoverTooltipSupplier(TOOLTIP_REMOVE_SELECTED_TRADING_PAIR::getString);

        currentTradingItemView = new TradingPairView();
        currentTradingItemView.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
        currentTradingItemView.setHoverTooltipSupplier(()->{
            return TOOLTIP_SELECTED_TRADING_PAIR.getString() + (tradingPair != null ? tradingPair.getShortDescription() : "None");
        });

        marketSettingsButton = new Button(MARKET_SETTINGS.getString(), () ->
        {
            BACKEND_INSTANCES.LOGGER.warn("NOT_IMPLEMENTED: BotSettingsScreen.openScreen(this, tradingPair));");
            //BotSettingsScreen.openScreen(this);
            //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestBotSettings(tradingPair); // Trigger request for bot settings
            if(getSelectedMarket() != null)
                getSelectedMarket().requestBotSettingsData((botSettingsData -> {
                    if(botSettingsData != null) {
                        //BotSettingsScreen.openScreen(this, botSettingsData);
                    } else {
                        BACKEND_INSTANCES.LOGGER.warn("Bot settings data is null for trading pair: " + tradingPair);
                    }
                }));
        });

        marketOpenCheckBox = new CheckBox("Market Open",this::onMarketOpenCheckBoxChanged);
        marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);


        addElement(tradableItemsView);
        addElement(newTradingPairButton);
        addElement(removeTradingItemButton);
        addElement(currentTradingItemView);
        addElement(marketSettingsButton);
        addElement(marketOpenCheckBox);

        setCurrentTradingPair(null);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;



        tradableItemsView.setBounds(padding, padding, (width*2)/3, height);
        newTradingPairButton.setBounds(tradableItemsView.getRight()+spacing, padding, 20, 20);
        removeTradingItemButton.setBounds(newTradingPairButton.getRight()+spacing, newTradingPairButton.getTop(), width/9-spacing, newTradingPairButton.getHeight());
        currentTradingItemView.setBounds(removeTradingItemButton.getRight()+spacing, removeTradingItemButton.getTop(), width - removeTradingItemButton.getRight()+spacing, removeTradingItemButton.getHeight());

        marketSettingsButton.setBounds(newTradingPairButton.getLeft(), newTradingPairButton.getBottom()+spacing, width/3-spacing, newTradingPairButton.getHeight());
        marketOpenCheckBox.setBounds(marketSettingsButton.getLeft(), marketSettingsButton.getBottom()+spacing, marketSettingsButton.getWidth(), marketSettingsButton.getHeight());

    }


    public static void openScreen()
    {
        openScreen(null);
    }
    public static void openScreen(Screen parent)
    {
        ManagementScreen screen = new ManagementScreen(parent);
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void onClose() {
        super.onClose();
        if(parentScreen != null)
            Minecraft.getInstance().setScreen(parentScreen);
    }


    public void updateTradingItems()
    {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestTradingPairs((tradingPairs -> {
            tradableItemsView.setAvailableTradingPairs(tradingPairs);

            //tradableItemsView.setItemLabelText(TRADING_ITEMS.getString());
            //tradableItemsView.sortItems();
        }));

        /*ArrayList<ItemID> items = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getAvailableTradeItemIdList();
        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : items)
        {
            //if(itemID.equals(tradingPair))
            {
                itemStacks.add(itemID.getStack());
            }
        }
        tradableItemsView.setItems(itemStacks);*/
    }


    public void setCurrentTradingPair(TradingPair tradingPair) {
        if(tradingPair == null) {
            this.tradingPair = null;
            selectMarket(tradingPair);
            currentMarketSettingsData = null;
            currentTradingItemView.setTradingPair(null);
            marketSettingsButton.setEnabled(false);
            marketOpenCheckBox.setEnabled(false);
            setCurrentTradingPairMarketSettings(null);
        }
        else
        {
            this.tradingPair = tradingPair;
            selectMarket(tradingPair);
            currentTradingItemView.setTradingPair(this.tradingPair);
            marketSettingsButton.setEnabled(true);
            marketOpenCheckBox.setEnabled(true);
            marketOpenCheckBox.setChecked(false);
            if(getSelectedMarket() != null)
            {
                getSelectedMarket().requestGetMarketSettings(this::setCurrentTradingPairMarketSettings);
            }
        }
    }

    private void setCurrentTradingPairMarketSettings(ServerMarketSettingsData settingsData) {
        currentMarketSettingsData = settingsData;
        if(settingsData == null)
        {

        }
        else
        {
            marketOpenCheckBox.setChecked(settingsData.marketOpen);
        }
    }

    private void onMarketOpenCheckBoxChanged(Boolean isOpen)
    {
        if(tradingPair == null || currentMarketSettingsData == null)
            return;
        if(currentMarketSettingsData.marketOpen != isOpen) {
            currentMarketSettingsData.marketOpen = isOpen;
            getSelectedMarket().requestSetMarketSettings(currentMarketSettingsData, (success) -> {
                if (success) {
                    BACKEND_INSTANCES.LOGGER.debug("Market open status updated for trading pair: " + tradingPair.getShortDescription() + " to " + isOpen);
                } else {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to update market open status for trading pair: " + tradingPair.getShortDescription());
                }
            });
        }
    }
    @Override
    public void tick() {

    }


}
