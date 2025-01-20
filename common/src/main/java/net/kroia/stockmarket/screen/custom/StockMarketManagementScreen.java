package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.banking.ClientBankManager;
import net.kroia.banksystem.networking.packet.client_sender.request.RequestPotentialBankItemIDsPacket;
import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.screens.ItemSelectionScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;

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



    private String currentTradingItemID;

    private final Button newTradingItemButton;
    private final Button removeTradingItemButton;
    private final ItemSelectionView tradableItemsView;
    private final ItemView currentTradingItemView;

    private final Button botSettingsButton;

    private final Screen parentScreen;
    protected StockMarketManagementScreen(Screen parent) {
        super(TITLE);
        this.parentScreen = parent;
        RequestPotentialBankItemIDsPacket.sendRequest();

        tradableItemsView = new ItemSelectionView(ClientMarket.getAvailableTradeItemIdList(), this::setCurrentTradingItemID);
        tradableItemsView.setItemLabelText(TRADING_ITEMS.getString());
        tradableItemsView.sortItems();

        newTradingItemButton = new Button(NEW_TRADING_ITEM_BUTTON.getString());
        newTradingItemButton.setOnFallingEdge(() -> {
            //ArrayList<String> items = ClientBankManager.getAllowedItemIDs();
            ArrayList<String> items = ClientBankManager.getPotentialBankItemIDs();
            items.removeAll(BankSystemModSettings.Bank.POTENTIAL_ITEM_BLACKLIST);
            items.removeAll(StockMarketModSettings.Market.NOT_TRADABLE_ITEMS);
            ItemSelectionScreen itemSelectionScreen = new ItemSelectionScreen(this, items, this::onNewTradingItemSelected);
            itemSelectionScreen.sortItems();
            this.minecraft.setScreen(itemSelectionScreen);
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

        botSettingsButton = new Button(BOT_SETTINGS.getString(), () ->{BotSettingsScreen.openScreen(this);});


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
        tradableItemsView.setAllowedItems(ClientMarket.getAvailableTradeItemIdList());
        tradableItemsView.sortItems();
    }


    private void setCurrentTradingItemID(String newItemID) {
        currentTradingItemID = newItemID;

        if(currentTradingItemID == null) {
            currentTradingItemView.setItemStack(null);
            botSettingsButton.setEnabled(false);
        }
        else
        {
            currentTradingItemView.setItemStack(ItemUtilities.createItemStackFromId(currentTradingItemID));
            ClientMarket.getBotSettings(currentTradingItemID);
            botSettingsButton.setEnabled(true);
        }

    }

    private void onNewTradingItemSelected(String itemID) {
        ClientMarket.requestAllowNewTradingItem(itemID,0);
        setCurrentTradingItemID(itemID);
    }

    @Override
    public void tick() {
        if(ClientMarket.hasSyncTradeItemsChanged())
        {
            updateTradingItems();
        }

    }
}
