package net.kroia.stockmarket.screen.custom;

/*
public class BotSettingsScreen extends StockMarketGuiScreen {
    private class BotTargetPriceDisplay extends GuiElement{

        private CandleStickChartOld chart;
        public BotTargetPriceDisplay(CandleStickChartOld cahrt) {
            super();
            this.chart = cahrt;
            enableBackground = false;
        }
        @Override
        protected void render() {
            int y = chart.getChartYPos(settings.targetPrice);
            drawRect(1, y, getWidth()-2, 1, 0xFF0000FF);
        }

        @Override
        protected void layoutChanged() {
            GuiElement parent = getParent();
            if(parent == null)
                return;
            setBounds(0,0,parent.getWidth(),parent.getHeight());
        }
    }

    private static final String NAME = "bot_settings_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+"change_item");
    public static final Component SAVE_BUTTON = Component.translatable(PREFIX+"save_settings");
    public static final Component BOT_USE = Component.translatable(PREFIX+"bot_use");
    public static final Component MARKET_OPEN = Component.translatable(PREFIX+"market_open");
    public static final Component OPEN_BOT_SETUP = Component.translatable(PREFIX+"open_bot_setup");



    private final ServerVolatilityBot.Settings settings;

    private static TradingPair tradingPair;
    private static BotSettingsScreen instance;
    private static long lastTickCount = 0;

    // Gui Elements
    private final CandleStickChartOld candleStickChart;
    private final BotTargetPriceDisplay botTargetPriceDisplay;
    private final OrderbookVolumeChartWidget orderbookVolumeChart;
    private final Button selectItemButton;
    private final Button saveButton;
    private final CheckBox useBotCheckBox;
    private final Button openSetupScreenButton;
    //private final Button manageBankButton;
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

        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.init();
        //RequestTradeItemsPacket.generateRequest();

        // Create Gui Elements
        this.candleStickChart = new CandleStickChartOld();
        this.botTargetPriceDisplay = new BotTargetPriceDisplay(candleStickChart);
        candleStickChart.addChild(botTargetPriceDisplay);
        this.orderbookVolumeChart = new OrderbookVolumeChartWidget();
        selectItemButton = new Button(CHANGE_ITEM_BUTTON.getString(), this::onSelectItemButtonPressed);
        saveButton = new Button(SAVE_BUTTON.getString(), this::onSaveSettings);
        normalButtonColor = saveButton.getOutlineColor();
        unsavedChangesButtonColor = 0xFFfc6603;
        useBotCheckBox = new CheckBox(BOT_USE.getString(), this::onSettingsChanged);
        useBotCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);
        openSetupScreenButton = new Button(OPEN_BOT_SETUP.getString(), this::onOpenSetupScreen);
        //manageBankButton = new Button(BOT_BANK.getString(), this::onManageBankButtonClicked);
        currentItemView = new ItemView();
        setingsListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        layout.padding = 0;
        layout.spacing = 0;
        setingsListView.setLayout(layout);

        marketOpenCheckBox = new CheckBox(MARKET_OPEN.getString(), this::onSettingsChanged);
        marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);

        botSettingsWidget = new BotSettingsWidget(settings, this::onSettingsChanged);
        setingsListView.addChild(botSettingsWidget);



        // Add Gui Elements
        addElement(candleStickChart);
        addElement(orderbookVolumeChart);
        addElement(selectItemButton);
        addElement(saveButton);
        addElement(useBotCheckBox);
        addElement(openSetupScreenButton);
        //addElement(manageBankButton);
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
        useBotCheckBox.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, marketOpenCheckBox.getWidth(), marketOpenCheckBox.getHeight());
        openSetupScreenButton.setBounds(useBotCheckBox.getLeft(), useBotCheckBox.getBottom()+spacing, useBotCheckBox.getWidth(), useBotCheckBox.getHeight());
        //manageBankButton.setBounds(openSetupScreenButton.getLeft(), openSetupScreenButton.getBottom()+spacing, openSetupScreenButton.getWidth(), openSetupScreenButton.getHeight());


        setingsListView.setBounds(x, candleStickChart.getBottom()+spacing, width, height-candleStickChart.getBottom()-spacing+padding);
    }


    @Override
    public void onClose() {
        super.onClose();
        instance = null;
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(itemID);
        tradingPair = null;
        // Unregister the event listener when the screen is closed
        TickEvent.PLAYER_POST.unregister(BotSettingsScreen::onClientTick);

        if(parentScreen != null)
            Minecraft.getInstance().setScreen(parentScreen);
    }

    public static void updatePlotsData() {
        if(instance == null)
            return;
        ClientTradeItem item = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getTradeItem(instance.tradingPair);
        if (item == null) {
            BACKEND_INSTANCES.LOGGER.warn("Trade item not found: " + instance.tradingPair);
            return;
        }

        instance.candleStickChart.setMinMaxPrice(item.getVisualMinPrice(), item.getVisualMaxPrice());
        instance.candleStickChart.setPriceHistory(item.getPriceHistory());
        instance.orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());


    }

    public void setBotSettings(ServerVolatilityBot.Settings settings)
    {
        this.settings.copyFrom(settings);
        botSettingsWidget.setSettings(this.settings);
        saveButton.setOutlineColor(normalButtonColor);
        botExists = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.botExists();
        ClientTradeItem item = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getTradeItem(tradingPair);
        marketOpenCheckBox.setChecked(item.isMarketOpen());
        useBotCheckBox.setChecked(botExists);
    }

    private static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;

        long currentTickCount = System.currentTimeMillis();
        if(currentTickCount - lastTickCount > 1000)
        {
            lastTickCount = currentTickCount;
            if(tradingPair != null && tradingPair.isValid())
            {
                if(!instance.settingsReceived)
                {
                    RequestBotSettingsPacket.sendPacket(tradingPair);

                }
                getMarket().requestBotSettingsData((settingsData -> {
                    instance.onItemSelected(settingsData.tradingPairData.toTradingPair());
                }));
                RequestBotTargetPricePacket.sendPacket(tradingPair);
            }

        }
        if(BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.hasSyncBotSettingsPacketChanged() && !instance.settingsReceived)
        {
            instance.settingsReceived = true;
            if(tradingPair == null)
            {
                tradingPair = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getBotSettingsItemID();
                if(tradingPair == null)
                    return;
                instance.onItemSelected(tradingPair.getStack());
            }
            instance.setBotSettings(BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getBotSettings(tradingPair));
        }
        if(BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.hasSyncBotTargetPricePacketChanged())
        {
            instance.settings.targetPrice = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getBotTargetPrice();
        }
    }

    private void onItemSelected(ItemStack istemStack) {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        tradingPair = new ItemID(istemStack);
        settingsReceived = false;
        botSettingsWidget.clear();
        RequestBotSettingsPacket.sendPacket(tradingPair);
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.subscribeMarketUpdate(tradingPair);
        currentItemView.setItemStack(tradingPair.getStack());
    }

    private void onSelectItemButtonPressed() {
        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getAvailableTradeItemIdList())
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
        boolean marketOpen = marketOpenCheckBox.isChecked();
        boolean doDestroyBot = !useBotCheckBox.isChecked() && botExists;
        boolean doCreateBot = useBotCheckBox.isChecked() && !botExists;
        UpdateBotSettingsPacket.sendPacket(tradingPair, botSettingsWidget.getSettings(), doDestroyBot, doCreateBot, marketOpen);
        saveButton.setOutlineColor(normalButtonColor);
    }


    private void onOpenSetupScreen()
    {
        botSetupScreen = new BotSetupScreen(this::onBotSetupApply, this::onBotSetupCancel, settings);
        Minecraft.getInstance().setScreen(botSetupScreen);
    }

    private void onBotSetupApply()
    {
        Minecraft.getInstance().setScreen(this);
        botSetupScreen = null;
        setBotSettings(settings);
        saveButton.setOutlineColor(unsavedChangesButtonColor);
        useBotCheckBox.setChecked(true);
        onSettingsChanged();
    }
    private void onBotSetupCancel()
    {
        botSetupScreen = null;
        Minecraft.getInstance().setScreen(this);
    }

    private static ClientMarket getMarket()
    {
        return BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getClientMarket(tradingPair);
    }
}
*/