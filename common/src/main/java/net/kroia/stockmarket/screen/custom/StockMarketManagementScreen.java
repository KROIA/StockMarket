package net.kroia.stockmarket.screen.custom;


import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.screens.CreativeModeItemSelectionScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;


public class StockMarketManagementScreen extends GuiScreen {

    private static final String NAME = "management_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    public static final Component TRADING_ITEMS = Component.translatable(PREFIX+ "trading_items");

    public static final Component NEW_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "new_trading_item");
    public static final Component REMOVE_TRADING_ITEM_BUTTON = Component.translatable(PREFIX+ "remove_trading_item");

    public static final Component ASK_TITLE = Component.translatable(PREFIX+ "ask_remove_title");
    public static final Component ASK_MSG = Component.translatable(PREFIX+ "ask_remove_message");
    public static final Component BOT_SETTINGS = Component.translatable(PREFIX+ "bot_settings");



    private ItemID currentTradingItemID;

    private final Button newTradingItemButton;
    private final Button removeTradingItemButton;
    private final ItemSelectionView tradableItemsView;
    private final ItemView currentTradingItemView;

    private final Button botSettingsButton;

    private final Screen parentScreen;
    protected StockMarketManagementScreen(Screen parent) {
        super(TITLE);
        this.parentScreen = parent;
        //RequestPotentialBankItemIDsPacket.sendRequest();

        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : ClientMarket.getAvailableTradeItemIdList())
        {
            itemStacks.add(itemID.getStack());
        }
        tradableItemsView = new ItemSelectionView(itemStacks, this::setCurrentTradingItemID);
        tradableItemsView.setItemLabelText(TRADING_ITEMS.getString());
        tradableItemsView.sortItems();

        newTradingItemButton = new Button(NEW_TRADING_ITEM_BUTTON.getString());
        newTradingItemButton.setOnFallingEdge(() -> {

            Minecraft.getInstance().setScreen(new CreativeModeItemSelectionScreen(this::onNewTradingItemSelected,()->
            {
                minecraft.setScreen(this);
            }));
        });
        removeTradingItemButton = new Button(REMOVE_TRADING_ITEM_BUTTON.getString(), () -> {
            if(currentTradingItemID != null) {
                AskPopupScreen popup = new AskPopupScreen(this, () -> {
                    ClientMarket.requestRemoveTradingItem(currentTradingItemID);
                    setCurrentTradingItemID(null);
                }, () -> {}, ASK_TITLE.getString() + " "+currentTradingItemID + "?", ASK_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(0xFFe8711c, 0xFFe04c12, 0xFFf22718, 0xFF70e815);
                minecraft.setScreen(popup);
            }
        });
        removeTradingItemButton.setIdleColor(0xFFe8711c);
        removeTradingItemButton.setHoverColor(0xFFe04c12);
        removeTradingItemButton.setPressedColor(0xFFe04c12);

        currentTradingItemView = new ItemView();

        botSettingsButton = new Button(BOT_SETTINGS.getString(), () ->
        {
            BotSettingsScreen.openScreen(this);
            ClientMarket.requestBotSettings(currentTradingItemID); // Trigger request for bot settings
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
        currentTradingItemView.setBounds(newTradingItemButton.getRight()+spacing, padding, 20, 20);
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
        ArrayList<ItemID> items = ClientMarket.getAvailableTradeItemIdList();
        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : items)
        {
            //if(itemID.equals(currentTradingItemID))
            {
                itemStacks.add(itemID.getStack());
            }
        }
        tradableItemsView.setItems(itemStacks);
    }


    private void setCurrentTradingItemID(ItemStack itemStack) {
        if(itemStack == null) {
            currentTradingItemID = null;
            currentTradingItemView.setItemStack(null);
            botSettingsButton.setEnabled(false);
        }
        else
        {
            currentTradingItemID = new ItemID(itemStack);
            currentTradingItemView.setItemStack(itemStack);
            ClientMarket.requestBotSettings(currentTradingItemID);
            botSettingsButton.setEnabled(true);
        }
    }

    private void onNewTradingItemSelected(ItemStack itemStack) {
        ItemID itemID = new ItemID(itemStack);
        ClientMarket.requestAllowNewTradingItem(itemID,0);
        setCurrentTradingItemID(itemStack);
    }

    @Override
    public void tick() {
        if(ClientMarket.hasSyncTradeItemsChanged())
        {
            updateTradingItems();
        }
    }
}
