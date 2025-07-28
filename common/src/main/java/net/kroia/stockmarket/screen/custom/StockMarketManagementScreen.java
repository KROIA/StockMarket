package net.kroia.stockmarket.screen.custom;


import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.screen.uiElements.TradingPairSelectionView;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class StockMarketManagementScreen extends GuiScreen {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    private static final String NAME = "management_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    public static final Component TRADING_ITEMS = Component.translatable(PREFIX+ "trading_items");

    public static final Component NEW_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "new_trading_item");
    public static final Component REMOVE_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "remove_trading_item");

    public static final Component ASK_TITLE = Component.translatable(PREFIX+ "ask_remove_title");
    public static final Component ASK_MSG = Component.translatable(PREFIX+ "ask_remove_message");
    public static final Component BOT_SETTINGS = Component.translatable(PREFIX+ "bot_settings");



    private TradingPair tradingPair;

    private final Button newTradingItemButton;
    private final Button removeTradingItemButton;
    private final TradingPairSelectionView tradableItemsView;
    private final TradingPairView currentTradingItemView;

    private final Button botSettingsButton;

    private final Screen parentScreen;

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    protected StockMarketManagementScreen(Screen parent) {
        super(TITLE);
        this.parentScreen = parent;
        //RequestPotentialBankItemIDsPacket.sendRequest();

        /*ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getAvailableTradeItemIdList())
        {
            itemStacks.add(itemID.getStack());
        }*/
        tradableItemsView = new TradingPairSelectionView(this::setCurrentTradingItemID);

        updateTradingItems();
        
        

        newTradingItemButton = new Button(NEW_TRADING_ITEM_BUTTON.getString());
        newTradingItemButton.setOnFallingEdge(() -> {

            BACKEND_INSTANCES.LOGGER.warn("NOT_IMPLEMENTED: CreativeModeItemSelectionScreen.openScreen(this::onNewTradingItemSelected, () -> minecraft.setScreen(this));");

            TradingPair tradingPair = new TradingPair(new ItemID(Items.DIAMOND.getDefaultInstance()), new ItemID(Items.IRON_INGOT.getDefaultInstance()));
            BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestCreateMarket(tradingPair, (success) -> {
                if(success) {
                    setCurrentTradingItemID(tradingPair);
                    tradableItemsView.setAvailableTradingPairs(List.of(tradingPair));
                } else {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create trading pair: " + tradingPair);
                }
            });

           /* Minecraft.getInstance().setScreen(new CreativeModeItemSelectionScreen(this::onNewTradingItemSelected,()->
            {
                minecraft.setScreen(this);
            }));*/
        });
        removeTradingItemButton = new Button(REMOVE_TRADING_ITEM_BUTTON.getString(), () -> {
            if(tradingPair != null) {
                AskPopupScreen popup = new AskPopupScreen(this, () -> 
                {
                    BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveMarket(tradingPair, (success)->{
                        setCurrentTradingItemID(null);
                    });
                    //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveTradingItem(tradingPair);

                }, () -> {}, ASK_TITLE.getString() + " "+tradingPair + "?", ASK_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(0xFFe8711c, 0xFFe04c12, 0xFFf22718, 0xFF70e815);
                minecraft.setScreen(popup);
            }
        });
        removeTradingItemButton.setIdleColor(0xFFe8711c);
        removeTradingItemButton.setHoverColor(0xFFe04c12);
        removeTradingItemButton.setPressedColor(0xFFe04c12);

        currentTradingItemView = new TradingPairView();

        botSettingsButton = new Button(BOT_SETTINGS.getString(), () ->
        {
            BACKEND_INSTANCES.LOGGER.warn("NOT_IMPLEMENTED: BotSettingsScreen.openScreen(this, tradingPair));");
            //BotSettingsScreen.openScreen(this);
            //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestBotSettings(tradingPair); // Trigger request for bot settings
            if(getMarket() != null)
                getMarket().requestBotSettingsData((botSettingsData -> {
                    if(botSettingsData != null) {
                        //BotSettingsScreen.openScreen(this, botSettingsData);
                    } else {
                        BACKEND_INSTANCES.LOGGER.warn("Bot settings data is null for trading pair: " + tradingPair);
                    }
                }));
        });


        addElement(tradableItemsView);
        addElement(newTradingItemButton);
        addElement(removeTradingItemButton);
        addElement(currentTradingItemView);
        addElement(botSettingsButton);

        setCurrentTradingItemID(null);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 10;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;



        tradableItemsView.setBounds(padding, padding, width/3, height);
        newTradingItemButton.setBounds(tradableItemsView.getRight()+spacing, padding, 150, 20);
        removeTradingItemButton.setBounds(newTradingItemButton.getLeft(), newTradingItemButton.getBottom()+spacing, newTradingItemButton.getWidth(), newTradingItemButton.getHeight());
        currentTradingItemView.setBounds(newTradingItemButton.getRight()+spacing, padding, 60, 20);
        botSettingsButton.setBounds(removeTradingItemButton.getLeft(), removeTradingItemButton.getBottom()+spacing, removeTradingItemButton.getWidth(), removeTradingItemButton.getHeight());
    }


    public static void openScreen()
    {
        openScreen(null);
    }
    public static void openScreen(Screen parent)
    {
        StockMarketManagementScreen screen = new StockMarketManagementScreen(parent);
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


    private void setCurrentTradingItemID(TradingPair tradingPair) {
        if(tradingPair == null) {
            this.tradingPair = null;
            currentTradingItemView.setTradingPair(null);
            botSettingsButton.setEnabled(false);
        }
        else
        {
            this.tradingPair = tradingPair;
            currentTradingItemView.setTradingPair(this.tradingPair);
            ///BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestBotSettings(this.tradingPair);
            botSettingsButton.setEnabled(true);
        }
    }

    private void onNewTradingItemSelected(ItemStack itemStack) {
        //ItemID itemID = new ItemID(itemStack);
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestAllowNewTradingItem(itemID,0);
        //setCurrentTradingItemID(itemStack);
    }

    @Override
    public void tick() {
       //if(BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.hasSyncTradeItemsChanged())
       //{
       //    updateTradingItems();
       //}
    }

    private ClientMarket getMarket()
    {
        return BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getClientMarket(tradingPair);
    }
}
