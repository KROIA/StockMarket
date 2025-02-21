package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.screen.custom.BankAccountManagementScreen;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.modutilities.gui.screens.ItemSelectionScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateBotSettingsPacket;
import net.kroia.stockmarket.screen.custom.botsetup.BotSetupScreen;
import net.kroia.stockmarket.screen.uiElements.BotSettingsWidget;
import net.kroia.stockmarket.screen.uiElements.CandleStickChart;
import net.kroia.stockmarket.screen.uiElements.OrderbookVolumeChart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.UUID;

public class BotSettingsScreen extends GuiScreen {

    private static final String NAME = "bot_settings_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+"change_item");
    public static final Component SAVE_BUTTON = Component.translatable(PREFIX+"save_settings");
    public static final Component BOT_CREATE = Component.translatable(PREFIX+"bot_create");
    public static final Component BOT_DESTROY = Component.translatable(PREFIX+"bot_destroy");
    public static final Component BOT_BANK = Component.translatable(PREFIX+"bot_bank");
    public static final Component MARKET_OPEN = Component.translatable(PREFIX+"market_open");
    public static final Component OPEN_BOT_SETUP = Component.translatable(PREFIX+"open_bot_setup");



    private final ServerVolatilityBot.Settings settings;

    private static ItemID itemID;
    private static BotSettingsScreen instance;
    private static long lastTickCount = 0;

    // Gui Elements
    private final CandleStickChart candleStickChart;
    private final OrderbookVolumeChart orderbookVolumeChart;
    private final Button selectItemButton;
    private final Button saveButton;
    private final Button createDestroyBotButton;
    private final Button openSetupScreenButton;
    private final Button manageBankButton;
    private final CheckBox marketOpenCheckBox;
    private int normalButtonColor, unsavedChangesButtonColor;
    private final ItemView currentItemView;
    private final ListView setingsListView;
    private final BotSettingsWidget botSettingsWidget;

    private BotSetupScreen botSetupScreen;

    private boolean settingsReceived = false;
    private boolean botExists = false;

    private final Screen parentScreen;
    public BotSettingsScreen(Screen parent) {
        super(TITLE);
        parentScreen = parent;
        instance = this;
        settings = new ServerVolatilityBot.Settings();
        RequestTradeItemsPacket.generateRequest();

        // Create Gui Elements
        this.candleStickChart = new CandleStickChart();
        this.orderbookVolumeChart = new OrderbookVolumeChart();
        selectItemButton = new Button(CHANGE_ITEM_BUTTON.getString(), this::onSelectItemButtonPressed);
        saveButton = new Button(SAVE_BUTTON.getString(), this::onSaveSettings);
        normalButtonColor = saveButton.getOutlineColor();
        unsavedChangesButtonColor = 0xFFfc6603;
        createDestroyBotButton = new Button(BOT_CREATE.getString(), this::onCreateDestroyBot);
        openSetupScreenButton = new Button(OPEN_BOT_SETUP.getString(), this::onOpenSetupScreen);
        manageBankButton = new Button(BOT_BANK.getString(), this::onManageBankButtonClicked);
        currentItemView = new ItemView();
        setingsListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        layout.padding = 0;
        layout.spacing = 0;
        setingsListView.setLayout(layout);

        marketOpenCheckBox = new CheckBox(MARKET_OPEN.getString(), this::onMarketOpenCheckBoxChanged);
        marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);

        botSettingsWidget = new BotSettingsWidget(settings, this::onSettingsChanged);
        setingsListView.addChild(botSettingsWidget);



        // Add Gui Elements
        addElement(candleStickChart);
        addElement(orderbookVolumeChart);
        addElement(selectItemButton);
        addElement(saveButton);
        addElement(createDestroyBotButton);
        addElement(openSetupScreenButton);
        addElement(manageBankButton);
        addElement(currentItemView);
        addElement(setingsListView);
        addElement(marketOpenCheckBox);

        TickEvent.PLAYER_POST.register(BotSettingsScreen::onClientTick);
    }

    public static void openScreen()
    {
        openScreen(null);
    }
    public static void openScreen(Screen parent)
    {
        BotSettingsScreen screen = new BotSettingsScreen(parent);
        Minecraft.getInstance().setScreen(screen);
    }



    @Override
    protected void updateLayout(Gui gui) {
        int padding = 10;
        int spacing = 4;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int x = padding;
        candleStickChart.setBounds(x, padding, (width * 5) / 8-spacing/2, height/2);
        orderbookVolumeChart.setBounds(candleStickChart.getRight(), padding, width / 8, candleStickChart.getHeight());

        currentItemView.setSize(20, 15);
        selectItemButton.setBounds(orderbookVolumeChart.getRight()+spacing, padding, width/4-currentItemView.getWidth()-spacing, currentItemView.getHeight());
        currentItemView.setPosition(selectItemButton.getRight()+spacing, padding);
        saveButton.setBounds(selectItemButton.getLeft(), selectItemButton.getBottom()+spacing, selectItemButton.getWidth()+currentItemView.getWidth(), selectItemButton.getHeight());
        marketOpenCheckBox.setBounds(saveButton.getLeft(), saveButton.getBottom()+spacing, saveButton.getWidth(), saveButton.getHeight());
        createDestroyBotButton.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, marketOpenCheckBox.getWidth(), marketOpenCheckBox.getHeight());
        openSetupScreenButton.setBounds(createDestroyBotButton.getLeft(), createDestroyBotButton.getBottom()+spacing, createDestroyBotButton.getWidth(), createDestroyBotButton.getHeight());
        manageBankButton.setBounds(openSetupScreenButton.getLeft(), openSetupScreenButton.getBottom()+spacing, openSetupScreenButton.getWidth(), openSetupScreenButton.getHeight());


        setingsListView.setBounds(x, candleStickChart.getBottom()+spacing, width, height-candleStickChart.getBottom()-spacing+padding);
    }


    @Override
    public void onClose() {
        super.onClose();
        instance = null;
        ClientMarket.unsubscribeMarketUpdate(itemID);
        itemID = null;
        // Unregister the event listener when the screen is closed
        TickEvent.PLAYER_POST.unregister(BotSettingsScreen::onClientTick);

        if(parentScreen != null)
            Minecraft.getInstance().setScreen(parentScreen);
    }

    public static void updatePlotsData() {
        if(instance == null)
            return;
        ClientTradeItem item = ClientMarket.getTradeItem(instance.itemID);
        if (item == null) {
            StockMarketMod.LOGGER.warn("Trade item not found: " + instance.itemID);
            return;
        }

        instance.candleStickChart.setMinMaxPrice(item.getVisualMinPrice(), item.getVisualMaxPrice());
        instance.candleStickChart.setPriceHistory(item.getPriceHistory());
        instance.orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());


    }

    public void setBotSettings(ServerVolatilityBot.Settings settings)
    {
        this.settings.load(settings);
        botSettingsWidget.setSettings(this.settings);
        saveButton.setOutlineColor(normalButtonColor);
        botExists = ClientMarket.botExists();
        ClientTradeItem item = ClientMarket.getTradeItem(itemID);
        marketOpenCheckBox.setChecked(item.isMarketOpen());
        if(botExists)
        {
            createDestroyBotButton.setLabel(BOT_DESTROY.getString());
        }
        else
        {
            createDestroyBotButton.setLabel(BOT_CREATE.getString());
        }
    }

    private static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;

        long currentTickCount = System.currentTimeMillis();
        if(currentTickCount - lastTickCount > 1000)
        {
            lastTickCount = currentTickCount;
            if(itemID != null && itemID.isValid() && !instance.settingsReceived)
                RequestBotSettingsPacket.sendPacket(itemID);
        }
        if(ClientMarket.hasSyncBotSettingsPacketChanged() && !instance.settingsReceived)
        {
            instance.settingsReceived = true;
            if(itemID == null)
            {
                itemID = ClientMarket.getBotSettingsItemID();
                if(itemID == null)
                    return;
                instance.onItemSelected(itemID.getStack());
            }
            instance.setBotSettings(ClientMarket.getBotSettings(itemID));
        }
    }

    private void onItemSelected(ItemStack istemStack) {
        ClientMarket.unsubscribeMarketUpdate(itemID);
        itemID = new ItemID(istemStack);
        settingsReceived = false;
        botSettingsWidget.clear();
        RequestBotSettingsPacket.sendPacket(itemID);
        ClientMarket.subscribeMarketUpdate(itemID);
        currentItemView.setItemStack(itemID.getStack());
    }

    private void onSelectItemButtonPressed() {
        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : ClientMarket.getAvailableTradeItemIdList())
        {
            itemStacks.add(itemID.getStack());
        }
        ItemSelectionScreen screen = new ItemSelectionScreen(
                this,
                itemStacks,
                this::onItemSelected);
        screen.sortItems();
        this.minecraft.setScreen(screen);
    }

    private void onSettingsChanged() {
        saveButton.setOutlineColor(unsavedChangesButtonColor);
    }
    private void onSaveSettings()
    {
        long itemBalance = 0;
        long moneyBalance = 0;
        boolean setItemBalance = false;
        boolean setMoneyBalance = false;
        boolean createBot = false;
        if(botSetupScreen != null)
        {
            itemBalance = botSetupScreen.getBotItemBalance();
            moneyBalance = botSetupScreen.getBotMoneyBalance();
            setItemBalance = botSetupScreen.getAutoChangeItemBalance();
            setMoneyBalance = botSetupScreen.getAutoChangeMoneyBalance();
            botSetupScreen = null;
            if(!botExists)
                createBot = true;
        }
        boolean marketOpen = marketOpenCheckBox.isChecked();
        if(setItemBalance || setMoneyBalance)
        {
            UpdateBotSettingsPacket.sendPacket(itemID, botSettingsWidget.getSettings(), false, createBot, marketOpen,
                    setItemBalance, itemBalance, setMoneyBalance, moneyBalance);
        }
        else
            UpdateBotSettingsPacket.sendPacket(itemID, botSettingsWidget.getSettings(), false, createBot, marketOpen);
        saveButton.setOutlineColor(normalButtonColor);
    }
    private void onCreateDestroyBot()
    {
        boolean marketOpen = marketOpenCheckBox.isChecked();
        if(botExists)
        {
            UpdateBotSettingsPacket.sendPacket(itemID, botSettingsWidget.getSettings(), true, false, marketOpen);
        }
        else
        {
            UpdateBotSettingsPacket.sendPacket(itemID, botSettingsWidget.getSettings(), false, true, marketOpen);
        }
        saveButton.setOutlineColor(normalButtonColor);
        if(itemID != null && itemID.isValid()) {
            settingsReceived = false;
            RequestBotSettingsPacket.sendPacket(itemID);
        }
    }

    private void onManageBankButtonClicked()
    {
        UUID botUUID = ClientMarket.getBotUUID();
        if(botUUID == null)
            return;
        BankAccountManagementScreen.openScreen(ClientMarket.getBotUUID(), this);
    }
    private void onMarketOpenCheckBoxChanged()
    {
        onSettingsChanged();
    }


    private void onOpenSetupScreen()
    {
        botSetupScreen = new BotSetupScreen(this::onBotSetupApply, this::onBotSetupCancel, settings);
        Minecraft.getInstance().setScreen(botSetupScreen);
    }

    private void onBotSetupApply()
    {
        Minecraft.getInstance().setScreen(this);
        setBotSettings(settings);
        saveButton.setOutlineColor(unsavedChangesButtonColor);
    }
    private void onBotSetupCancel()
    {
        botSetupScreen = null;
        Minecraft.getInstance().setScreen(this);
    }
}
