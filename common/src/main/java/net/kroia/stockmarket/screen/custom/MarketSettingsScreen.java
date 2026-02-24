package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

public class MarketSettingsScreen extends StockMarketGuiScreen {
    public final float textFontSize = StockMarketGuiElement.hoverToolTipFontSize;
    public final int elementHeight = 15;
    public final int spacing = 3;
    public final int padding = 3;

    public static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component SAVE_BUTTON = Component.translatable(PREFIX + "save_button");
        public static final Component SAVE_BUTTON_TOOLTIP = Component.translatable(PREFIX + "save_button.tooltip");
        public static final Component BACK_BUTTON = Component.translatable(PREFIX + "back_button");
        public static final Component OPEN_PLUGIN_BROWSER_BUTTON = Component.translatable(PREFIX + "open_plugin_browser_button");
        public static final Component OPEN_PLUGIN_BROWSER_BUTTON_TOOLTIP = Component.translatable(PREFIX + "open_plugin_browser_button.tooltip");


        // GeneralGui
        public static final Component GENERAL_TITLE = Component.translatable(PREFIX + "general.title");
        public static final Component GENERAL_CHART_RESET = Component.translatable(PREFIX + "general.chart_reset");
        public static final Component GENERAL_CHART_RESET_TOOLTIP = Component.translatable(PREFIX + "general.chart_reset.tooltip");
        public static final Component GENERAL_IS_MARKET_OPEN = Component.translatable(PREFIX + "general.is_market_open");
        public static final Component GENERAL_CANDLE_TIME = Component.translatable(PREFIX + "general.candle_time_min");
        public static final Component ITEM_IMBALANCE = Component.translatable(PREFIX + "general.item_imbalance");
        // GeneralGui Tooltips
        public static final Component GENERAL_IS_MARKET_OPEN_TOOLTIP = Component.translatable(PREFIX + "general.is_market_open.tooltip");
        public static final Component ITEM_IMBALANCE_TOOLTIP = Component.translatable(PREFIX + "general.item_imbalance.tooltip");



        // Virtual Order Book
        public static final Component VIRTUAL_ORDER_BOOK_TITLE = Component.translatable(PREFIX + "virtual_order_book.title");
        public static final Component VIRTUAL_ORDER_BOOK_ENABLE = Component.translatable(PREFIX + "virtual_order_book.enable");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_SCALE = Component.translatable(PREFIX + "virtual_order_book.volume_scale");
        public static final Component VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE = Component.translatable(PREFIX + "virtual_order_book.near_market_volume_scale");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_accumulation_rate");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_fast_accumulation_rate");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_decumulation_rate");

        // Virtual Order Book Tooltips
        public static final Component VIRTUAL_ORDER_BOOK_ENABLE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.enable.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_scale.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.near_market_volume_scale.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_accumulation_rate.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_fast_accumulation_rate.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_decumulation_rate.tooltip");


        // BotGui
        public static final Component BOT_SETTINGS_TITLE = Component.translatable(PREFIX + "bot_settings.title");
        public static final Component BOT_SETTINGS_ENABLE = Component.translatable(PREFIX + "bot_settings.enable");
        public static final Component BOT_SETTINGS_ENABLE_TARGET_PRICE = Component.translatable(PREFIX + "bot_settings.enable_target_price");
        public static final Component BOT_SETTINGS_ENABLE_VOLUME_TRACKING = Component.translatable(PREFIX + "bot_settings.enable_volume_tracking");
        public static final Component BOT_SETTINGS_ENABLE_RANDOM_WALK = Component.translatable(PREFIX + "bot_settings.enable_random_walk");
        public static final Component BOT_SETTINGS_DEFAULT_PRICE = Component.translatable(PREFIX + "bot_settings.default_price");
        public static final Component BOT_SETTINGS_UPDATE_INTERVAL_MS = Component.translatable(PREFIX + "bot_settings.update_interval_ms");
        public static final Component BOT_SETTINGS_VOLUME_SCALE = Component.translatable(PREFIX + "bot_settings.volume_scale");
        public static final Component BOT_SETTINGS_TARGET_PRICE_STEERING_FAC = Component.translatable(PREFIX + "bot_settings.target_price_steering_fac");
        public static final Component BOT_SETTINGS_VOLUME_STEERING_FAC = Component.translatable(PREFIX + "bot_settings.volume_steering_fac");
        public static final Component BOT_SETTINGS_VOLATILITY = Component.translatable(PREFIX + "bot_settings.volatility");

        // BotGui Tooltips
        public static final Component BOT_SETTINGS_ENABLE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable.tooltip");
        public static final Component BOT_SETTINGS_DEFAULT_PRICE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.default_price.tooltip");
        public static final Component BOT_SETTINGS_UPDATE_INTERVAL_MS_TOOLTIP = Component.translatable(PREFIX + "bot_settings.update_interval_ms.tooltip");
        public static final Component BOT_SETTINGS_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volume_scale.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_TARGET_PRICE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_target_price.tooltip");
        public static final Component BOT_SETTINGS_TARGET_PRICE_STEERING_FAC_TOOLTIP = Component.translatable(PREFIX + "bot_settings.target_price_steering_fac.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_VOLUME_TRACKING_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_volume_tracking.tooltip");
        public static final Component BOT_SETTINGS_VOLUME_STEERING_FAC_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volume_steering_fac.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_RANDOM_WALK_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_random_walk.tooltip");
        public static final Component BOT_SETTINGS_VOLATILITY_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volatility.tooltip");
    }


    private class GeneralGuiElement extends StockMarketGuiElement
    {
        public final Label titleLabel;
        public final Button chartResetButton;
        public final CheckBox isMarketOpenCheckBox;
        public final Label candleTimeMinLabel;
        public final TextBox candleTimeMin;
        public final Label itemImbalanceLabel;
        public final TextBox itemImbalanceTextBox;


        public GeneralGuiElement() {
            super();
            //this.setEnableBackground(false);
            titleLabel = new Label(TEXTS.GENERAL_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            chartResetButton = new Button(TEXTS.GENERAL_CHART_RESET.getString(), () -> {
                if(getSelectedMarket() != null)
                {
                    getSelectedMarket().requestChartReset((result)->{});
                }
            });
            chartResetButton.setHoverTooltipSupplier(TEXTS.GENERAL_CHART_RESET_TOOLTIP::getString);
            chartResetButton.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            isMarketOpenCheckBox = new CheckBox(TEXTS.GENERAL_IS_MARKET_OPEN.getString());
            isMarketOpenCheckBox.setTextAlignment(Alignment.RIGHT);
            candleTimeMinLabel = new Label(TEXTS.GENERAL_CANDLE_TIME.getString());
            candleTimeMinLabel.setAlignment(Alignment.RIGHT);
            candleTimeMin = new TextBox();
            candleTimeMin.setAllowNumbers(true,false);
            candleTimeMin.setAllowLetters(false);
            itemImbalanceLabel = new Label(TEXTS.ITEM_IMBALANCE.getString());
            itemImbalanceLabel.setAlignment(Alignment.RIGHT);
            itemImbalanceTextBox = new TextBox();
            itemImbalanceTextBox.setAllowNumbers(true,false);
            itemImbalanceTextBox.setAllowLetters(false);




            isMarketOpenCheckBox.setHoverTooltipSupplier(TEXTS.GENERAL_IS_MARKET_OPEN_TOOLTIP::getString);
            candleTimeMinLabel.setHoverTooltipSupplier(()-> StockMarketTextMessages.getMarketSettingsScreenCandleTimeTooltip(candleTimeMin.getInt()));
            candleTimeMin.setHoverTooltipSupplier(()-> StockMarketTextMessages.getMarketSettingsScreenCandleTimeTooltip(candleTimeMin.getInt()));
            itemImbalanceLabel.setHoverTooltipSupplier(TEXTS.ITEM_IMBALANCE_TOOLTIP::getString);
            itemImbalanceTextBox.setHoverTooltipSupplier(TEXTS.ITEM_IMBALANCE_TOOLTIP::getString);


            addChild(titleLabel);
            addChild(chartResetButton);
            addChild(isMarketOpenCheckBox);
            addChild(candleTimeMinLabel);
            addChild(candleTimeMin);
            addChild(itemImbalanceLabel);
            addChild(itemImbalanceTextBox);

            for(GuiElement child : getChilds())
            {
                child.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
                child.setHoverTooltipFontScale(textFontSize);
                child.setTextFontScale(textFontSize);
            }

            int targetHeight = (elementHeight) * 5 + padding*2;
            this.setHeight(targetHeight);
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - padding * 2;

            //int height = getHeight() - padding * 2;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight;
            chartResetButton.setBounds(padding, y, width, elementHeight);
            y += elementHeight;
            isMarketOpenCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight;
            candleTimeMinLabel.setBounds(padding, y, width/2, elementHeight);
            candleTimeMin.setBounds(candleTimeMinLabel.getRight(), candleTimeMinLabel.getTop(), width - candleTimeMinLabel.getWidth(), candleTimeMinLabel.getHeight());
            itemImbalanceLabel.setBounds(padding, y + elementHeight, width/2, elementHeight);
            itemImbalanceTextBox.setBounds(itemImbalanceLabel.getRight(), itemImbalanceLabel.getTop(), width - itemImbalanceLabel.getWidth(), itemImbalanceLabel.getHeight());
        }


        public void setMarketOpen(boolean isOpen) {
            isMarketOpenCheckBox.setChecked(isOpen);
        }
        public boolean isMarketOpen() {
            return isMarketOpenCheckBox.isChecked();
        }

        public void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS)
        {
            if(shiftPriceCandleIntervalMS <= 0)
                candleTimeMin.setText("");
            else
                candleTimeMin.setText(String.valueOf(shiftPriceCandleIntervalMS/60000));
        }
        public long getShiftPriceCandleIntervalMS() {
            if(candleTimeMin.getText().isEmpty())
                return 0;
            long value = candleTimeMin.getLong();
            if(value < 1)
                value = 1;
            return value * 60000; // Convert minutes to milliseconds
        }
        public void setItemImbalance(long itemImbalance) {
            itemImbalanceTextBox.setText(String.valueOf(itemImbalance));
        }
        public long getItemImbalance() {
            if(itemImbalanceTextBox.getText().isEmpty())
                return 0;
            long value = itemImbalanceTextBox.getLong();
            return value;
        }
    }


    GuiScreen parentScreen;
    private static MarketSettingsScreen instance = null;
    //private ServerMarketSettingsData serverMarketSettingsData;
    private long oldItemImbalance = 0;
    private final TradingChartWidget tradingChart;
    private final TradingPairView tradingPairView;
    private final Button saveButton;
    private final Button backButton;
    private final Button openPluginBrowserButton;
    private final ListView pluginsListView;
    GeneralGuiElement generalGuiElement;
    PluginBrowserScreen  pluginBrowserScreen = null;
    //private final VirtualOderBookGuiElement virtualOrderBookGuiElement;
    //private final BotGuiElement botGuiElement;
    //private final List<ClientMarketPlugin> plugins = new ArrayList<>();
    Consumer<ServerMarketSettingsData> onSaveCallback;

    private final TimerMillis updateTimer;
    public int priceScaleFactor = 1;
    public MarketSettingsScreen(GuiScreen parent, Consumer<ServerMarketSettingsData> onSave) {
        super(TEXTS.TITLE);
        setGuiScale(0.5f);
        this.parentScreen = parent;
        this.onSaveCallback = onSave;

        tradingChart = new TradingChartWidget();
        //tradingChart.enableBotTargetPriceDisplay(true);

        tradingPairView = new TradingPairView();
        tradingPairView.setClickable(false);

        saveButton = new Button(TEXTS.SAVE_BUTTON.getString(), this::onSaveButtonClicked);
        saveButton.setHoverTooltipSupplier(TEXTS.SAVE_BUTTON_TOOLTIP::getString);
        backButton = new Button(TEXTS.BACK_BUTTON.getString(), this::onClose);
        openPluginBrowserButton = new Button(TEXTS.OPEN_PLUGIN_BROWSER_BUTTON.getString(), this::onOpenPluginBrowserButtonClicked);
        openPluginBrowserButton.setHoverTooltipSupplier(TEXTS.OPEN_PLUGIN_BROWSER_BUTTON_TOOLTIP::getString);


        pluginsListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        pluginsListView.setLayout(layout);

        generalGuiElement = new GeneralGuiElement();
        //botGuiElement = new BotGuiElement();
        //virtualOrderBookGuiElement = new VirtualOderBookGuiElement();


        //pluginsListView.addChild(generalGuiElement);
        //listView.addChild(virtualOrderBookGuiElement);
        //listView.addChild(botGuiElement);


        addElement(generalGuiElement);
        addElement(tradingPairView);
        addElement(saveButton);
        addElement(backButton);
        addElement(openPluginBrowserButton);
        addElement(tradingChart);
        addElement(pluginsListView);

        instance = this;
        this.updateTimer = new TimerMillis(true); // Update every second
        updateTimer.start(100);
        TickEvent.PLAYER_POST.register(MarketSettingsScreen::onClientTick);
    }

    @Override
    public void onClose() {
        TickEvent.PLAYER_POST.unregister(MarketSettingsScreen::onClientTick);
        super.onClose();
        instance = null;
        //for(ClientMarketPlugin plugin : plugins)
        //    plugin.close_internal();
        //plugins.clear();
        if (parentScreen != null) {
            int mousePosX = getMouseX();
            int mousePosY = getMouseY();
            minecraft.setScreen(parentScreen);
            setMousePos(mousePosX, mousePosY);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int chartWidth = (width*2)/3;

        tradingChart.setBounds(padding, padding, chartWidth, height);
        tradingPairView.setBounds(tradingChart.getRight()+spacing, padding, (width-chartWidth)/3-spacing, 20);
        saveButton.setBounds(tradingPairView.getRight()+spacing, tradingPairView.getTop(), (width-chartWidth) / 3 - spacing, 20);
        backButton.setBounds(saveButton.getRight() + spacing, saveButton.getTop(), width - saveButton.getRight(), 20);


        generalGuiElement.setBounds(tradingPairView.getLeft(), tradingPairView.getBottom() + spacing, (width-chartWidth) - spacing, generalGuiElement.getHeight());

        openPluginBrowserButton.setBounds(generalGuiElement.getLeft(), generalGuiElement.getBottom() + spacing, generalGuiElement.getWidth(), 20);
        pluginsListView.setBounds(openPluginBrowserButton.getLeft(), openPluginBrowserButton.getBottom(), openPluginBrowserButton.getWidth(), getHeight() - openPluginBrowserButton.getBottom() - padding);
    }

    private void onSaveButtonClicked()
    {
        ServerMarketSettingsData settings = getSettings();
        if(settings == null)
            return;

        //for(ClientMarketPlugin plugin : plugins)
        //{
        //    plugin.saveSettings();
        //}
        onSaveCallback.accept(settings);
    }
    private void onOpenPluginBrowserButtonClicked()
    {
        TradingPair pair = tradingPairView.getTradingPair();
        if(pair == null)
            return;
        pluginBrowserScreen = new PluginBrowserScreen(pair, this::onPluginBrowserChangesApplyed, this);
        setScreen(pluginBrowserScreen);
    }
    private void onPluginBrowserChangesApplyed()
    {
        getSelectedMarket().requestGetMarketSettings(
                (settingsData -> {
                    if (settingsData != null) {
                        setSettings(settingsData);
                    }
                }));
        if(pluginBrowserScreen != null)
        {
            pluginBrowserScreen.close();
            pluginBrowserScreen = null;
        }
    }

    public void setSettings(ServerMarketSettingsData settings)
    {
        //this.serverMarketSettingsData = settings;
        if(settings != null)
        {
            priceScaleFactor = settings.priceScaleFactor;
            if(settings.tradingPairData != null) {
                TradingPair tradingPair = settings.tradingPairData.toTradingPair();
                selectMarket(tradingPair);
                tradingPairView.setTradingPair(tradingPair);
                generalGuiElement.selectMarket(tradingPair);

                updatePluginsListView();
                //virtualOrderBookGuiElement.selectMarket(tradingPair);
                //botGuiElement.selectMarket(tradingPair);
            }
            generalGuiElement.setShiftPriceCandleIntervalMS(settings.shiftPriceCandleIntervalMS);
            generalGuiElement.setMarketOpen(settings.marketOpen);

           /* if(settings.botSettingsData != null)
                botGuiElement.setBotSettings(settings.botSettingsData.settings);
            else
                botGuiElement.setBotSettings(null);

            if(settings.virtualOrderBookSettingsData != null)
                virtualOrderBookGuiElement.setVirtualOrderBookSettings(settings.virtualOrderBookSettingsData.settings);
            else
                virtualOrderBookGuiElement.setVirtualOrderBookSettings(null);*/
            oldItemImbalance = settings.itemImbalance;
            generalGuiElement.setItemImbalance(settings.itemImbalance);




        }
        else {
            tradingPairView.setTradingPair(null);
            generalGuiElement.setShiftPriceCandleIntervalMS(0);
            generalGuiElement.setMarketOpen(false);
           // botGuiElement.setBotSettings(null);
           // virtualOrderBookGuiElement.setVirtualOrderBookSettings(null);
            oldItemImbalance = 0;
        }





    }
    public ServerMarketSettingsData getSettings()
    {
        if(getSelectedMarket() == null)
            return null;
        ServerMarketSettingsData settings = new ServerMarketSettingsData(
                getSelectedMarket().getTradingPair(),
                null,//botGuiElement.getBotSettings(),
                null,//virtualOrderBookGuiElement.getVirtualOrderBookSettings(),
                generalGuiElement.isMarketOpen(),
                0,
                generalGuiElement.getShiftPriceCandleIntervalMS(),
                priceScaleFactor
        );

        long newItemImbalance = generalGuiElement.getItemImbalance();
        if(oldItemImbalance != newItemImbalance)
        {
            settings.itemImbalance = newItemImbalance;
            settings.overwriteItemImbalance = true;
        }


        settings.doCreateBotIfNotExists = settings.botSettingsData != null;
        settings.doDestroyBotIfExists = settings.botSettingsData == null;
        settings.doCreateVirtualOrderBookIfNotExists = settings.virtualOrderBookSettingsData != null;
        settings.doDestroyVirtualOrderBookIfExists = settings.virtualOrderBookSettingsData == null;

        return settings;
    }

    private static void onClientTick(Player player)
    {
        if(instance == null)
            return;

        if(instance.updateTimer.check() && instance.getSelectedMarket() != null)
        {
            instance.getSelectedMarket().requestTradingViewData(0, instance.tradingChart.getMaxCandleCount(), 0,0,500,true ,instance.tradingChart::updateView);
        }
    }

    private void updatePluginsListView()
    {
        /*for(ClientMarketPlugin plugin : plugins)
            plugin.close_internal();
        plugins.clear();
        pluginsListView.removeChilds();
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestMarketPluginTypes(tradingPair, (pluginTypeList)->
        {
            List<ClientMarketPlugin> plugins = BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.getMarketPlugins(tradingPair);
            for(ClientMarketPlugin plugin : plugins)
            {
                ClientMarketPluginGuiElement element = plugin.getSettingsGuiElement_internal();
                if(element != null)
                {
                    this.plugins.add(plugin);
                    pluginsListView.addChild(element);
                    element.setMoveUpDownCallbacks(this::movePluginUp, this::movePluginDown);
                    element.setChartWidget(tradingChart);
                    plugin.setup_interal();
                }
            }
        });*/
    }

    /*private void movePluginUp(ClientMarketPluginGuiElement guiElement)
    {
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        List<GuiElement> elements = pluginsListView.getChilds();
        int currentIndex = elements.indexOf(guiElement);
        if(currentIndex == -1)
            return;

        List<String> sortedPluginIDs = new ArrayList<>();
        for(int i = 0; i<elements.size(); i++)
        {
            if(i == currentIndex-1 && currentIndex > 0)
            {
                sortedPluginIDs.add(guiElement.getPlugin().getPluginTypeID());
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
                i++;
            }
            else
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
        }

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestSetMarketPluginTypes(tradingPair, sortedPluginIDs, (success)->
        {
            if(!success)
                error("Can't update order of market plugins");
            else
                updatePluginsListView();
        });
    }
    private void movePluginDown(ClientMarketPluginGuiElement guiElement)
    {
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        List<GuiElement> elements = pluginsListView.getChilds();
        int currentIndex = elements.indexOf(guiElement);
        if(currentIndex == -1)
            return;

        List<String> sortedPluginIDs = new ArrayList<>();
        for(int i = 0; i<elements.size(); i++)
        {
            if(i == currentIndex && currentIndex < elements.size() - 1)
            {
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i+1)).getPlugin().getPluginTypeID());
                sortedPluginIDs.add(guiElement.getPlugin().getPluginTypeID());
                i++;
            }
            else
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
        }

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestSetMarketPluginTypes(tradingPair, sortedPluginIDs, (success)->
        {
            if(!success)
                error("Can't update order of market plugins");
            else
                updatePluginsListView();
        });
    }*/

    public static float getInRange(float value, float min, float max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
    public static int getInRange(int value, int min, int max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
    public static long getInRange(long value, long min, long max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
}
