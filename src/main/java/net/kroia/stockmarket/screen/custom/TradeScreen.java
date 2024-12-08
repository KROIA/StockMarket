// TradeScreen.java
package net.kroia.stockmarket.screen.custom;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.util.CandleStickChart;
import net.kroia.stockmarket.util.OrderbookVolumeChart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.w3c.dom.css.Rect;

import java.util.ArrayList;

public class TradeScreen extends Screen {
    private class Point {
        public int x;
        public int y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    public class Rectangle {
        public int x;
        public int y;
        public int width;
        public int height;
        public Rectangle(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final Component TITLE = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen");
    private static final Component SELL_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.sell_button");
    private static final Component BUY_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.buy_button");
    private static final Component SELECT_ITEM_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.select_item_button");

    private static CandleStickChart candleStickChart;
    private static OrderbookVolumeChart orderbookVolumeChart = new OrderbookVolumeChart();


    //private final ArrayList<CandleStickChart.CandleData> chartData;
    private int padding;
    private int buttonHeight;
    private int chartWidth;
    private int buttonSectionLeft;
    private Rectangle chartRect;

    private Rectangle selectItemButtonRect;
    private Rectangle amountEditRect;

    // Limit
    private Rectangle limitLabelRect;
    private Rectangle limitPriceEditRect;
    private Rectangle buyLimitButtonRect;
    private Rectangle sellLimitButtonRect;


    // Market
    private Rectangle marketLabelRect;
    private Rectangle buyMarketButtonRect;
    private Rectangle sellMarketButtonRect;

    private final int backgroundColor = 0x7F404040;

    private static String itemID;
    private static ItemStack itemStack;

    private Button sellMarketButton;
    private Button buyMarketButton;
    private Button selectItemButton;

    private EditBox priceBox;
    private EditBox amountBox;

    private Button sellLimitButton;
    private Button buyLimitButton;

    private int targetPrice = 0;
    private int targetAmount = 0;

    static int lastTickPahaseCount = 0;
    static int tickPhaseCount = 0;

    static TradeScreen instance;
    static boolean test = false;

    public TradeScreen(String itemID) {
        super(TITLE);






        TradeScreen.candleStickChart = new CandleStickChart(this);
        instance = this;
        this.itemID = itemID;
        itemStack = getItemStackFromId(itemID);
        //RequestPricePacket.generateRequest(itemID);
               //candleStickChart.setPriceHistory(MarketData.getPriceHistory(itemID));



    }

    @Override
    public void onClose() {
        super.onClose();
        // Unregister the event listener when the screen is closed
        MinecraftForge.EVENT_BUS.unregister(this);
        ClientMarket.unsubscribeMarketUpdate(itemID);
        //SubscribeMarketEventsPacket.generateRequest(itemID, false);
    }

    @Override
    protected void init() {
        super.init();
        ClientMarket.init();

        // Set Layout
        padding = 10;
        int buttonHeight = 20;
        int chartWidth = (this.width*3)/4;
        int buttonSectionLeft = chartWidth+5;
        chartRect = new Rectangle(0, 0, chartWidth, this.height);

        selectItemButtonRect = new Rectangle(buttonSectionLeft, 0, this.width-buttonSectionLeft, buttonHeight);
        amountEditRect = new Rectangle(buttonSectionLeft, buttonHeight, this.width-buttonSectionLeft, buttonHeight);

        // Limit
        int limitYPos = 50;
        limitLabelRect = new Rectangle(buttonSectionLeft, limitYPos, this.width-buttonSectionLeft, buttonHeight);
        limitPriceEditRect = new Rectangle(buttonSectionLeft, limitYPos+buttonHeight, this.width-buttonSectionLeft, buttonHeight);
        buyLimitButtonRect = new Rectangle(buttonSectionLeft, limitYPos+2*buttonHeight, (this.width-buttonSectionLeft)/2, buttonHeight);
        sellLimitButtonRect = new Rectangle(buttonSectionLeft + (this.width-buttonSectionLeft)/2, limitYPos+2*buttonHeight, (this.width-buttonSectionLeft)/2, buttonHeight);

        // Market
        int marketYPos = 150;
        marketLabelRect = new Rectangle(buttonSectionLeft, marketYPos, this.width-buttonSectionLeft, buttonHeight);
        buyMarketButtonRect = new Rectangle(buttonSectionLeft, marketYPos+buttonHeight, (this.width-buttonSectionLeft)/2, buttonHeight);
        sellMarketButtonRect = new Rectangle(buttonSectionLeft + (this.width-buttonSectionLeft)/2, marketYPos+buttonHeight, (this.width-buttonSectionLeft)/2, buttonHeight);



        //int buttonVSpacing = 5;
        //int currentY = 0;

        candleStickChart.setChartView(0, 100, chartRect.x+padding,chartRect.y+padding, chartRect.width-50-2*padding, chartRect.height-2*padding);
        orderbookVolumeChart.setChartView(chartRect.x+chartRect.width-50-padding, chartRect.y+padding, 50, chartRect.height-2*padding);


        sellLimitButton = addRenderableWidget(Button.builder(SELL_BUTTON_TEXT,
                this::onSellLimitButtonPressed).bounds(sellLimitButtonRect.x,sellLimitButtonRect.y,sellLimitButtonRect.width,sellLimitButtonRect.height).build());
        buyLimitButton = addRenderableWidget(Button.builder(BUY_BUTTON_TEXT,
                this::onBuyLimitButtonPressed).bounds(buyLimitButtonRect.x, buyLimitButtonRect.y,buyLimitButtonRect.width,buyLimitButtonRect.height).build());



        // Add a button to open the item selection dialog
        selectItemButton = addRenderableWidget(Button.builder(SELECT_ITEM_BUTTON_TEXT,
                this::onSelectItemButtonPressed).bounds(selectItemButtonRect.x, selectItemButtonRect.y, selectItemButtonRect.width, selectItemButtonRect.height).build());

        this.amountBox = new EditBox(this.font, amountEditRect.x, amountEditRect.y, amountEditRect.width, amountEditRect.height, Component.literal("Enter an integer"));
        this.amountBox.setMaxLength(10); // Max length of input
        this.amountBox.setFilter(input -> input.matches("\\d*")); // Allow only digits
        this.addRenderableWidget(this.amountBox);

        // Add the EditBox to the screen
        this.priceBox = new EditBox(this.font, limitPriceEditRect.x, limitPriceEditRect.y, limitPriceEditRect.width, limitPriceEditRect.height, Component.literal("Enter an integer"));
        this.priceBox.setMaxLength(10); // Max length of input
        this.priceBox.setFilter(input -> input.matches("\\d*")); // Allow only digits
        this.addRenderableWidget(this.priceBox);

        sellMarketButton = addRenderableWidget(Button.builder(SELL_BUTTON_TEXT,
                this::onSellMarketButtonPressed).bounds(sellMarketButtonRect.x, sellMarketButtonRect.y,sellMarketButtonRect.width,sellMarketButtonRect.height).build());
        buyMarketButton = addRenderableWidget(Button.builder(BUY_BUTTON_TEXT,
                this::onBuyMarketButtonPressed).bounds(buyMarketButtonRect.x, buyMarketButtonRect.y,buyMarketButtonRect.width,buyMarketButtonRect.height).build());



        // Register the event listener when the screen is initialized
        MinecraftForge.EVENT_BUS.register(this);
        //SubscribeMarketEventsPacket.generateRequest(itemID, true);

        //updatePlotsData();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        // Ensure we only handle ticks when this screen is active
        /*if(this.minecraft.screen == this && event.phase == TickEvent.Phase.END)
        {
            tickPhaseCount++;
            if(tickPhaseCount - lastTickPahaseCount > 20) {
                lastTickPahaseCount = tickPhaseCount;
                // Update the chart data
                //candleStickChart.setPriceHistory(Market.getPriceHistory(itemID));
                RequestPricePacket.generateRequest(itemID);
            }
        }*/
    }

    public static void onAvailableTradeItemsChanged()
    {
        // check if screen is visible
        if(instance.minecraft.screen == instance)
        {
            ClientMarket.subscribeMarketUpdate(itemID);
            updatePlotsData();

            if(!test) {
                test = true;
                // Create some dummy orders
                ClientMarket.createOrder(itemID, -30, 90);
                ClientMarket.createOrder(itemID, -10, 80);
                ClientMarket.createOrder(itemID, -8, 78);
                ClientMarket.createOrder(itemID, -2, 75);
                ClientMarket.createOrder(itemID, 1, 45);
                ClientMarket.createOrder(itemID, 5, 44);
                ClientMarket.createOrder(itemID, 20, 40);
            }
        }
    }

    public static void updatePlotsData()
    {
        ClientTradeItem item = ClientMarket.getTradeItem(itemID);
        if(item == null)
        {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }
        candleStickChart.setPriceHistory(item.getPriceHistory());
        orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());
    }

    private void onItemSelected(String itemId)
    {
        StockMarketMod.LOGGER.info("Item selected: " + itemId);

        ClientMarket.unsubscribeMarketUpdate(itemID);
        this.itemID = itemId;
        ClientMarket.subscribeMarketUpdate(itemID);
        itemStack = getItemStackFromId(itemID);
        //RequestPricePacket.generateRequest(itemId);
        updatePlotsData();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //TutorialMod.LOGGER.info("Render Chart Screen");
        // Background is typically rendered first
        this.renderBackground(graphics);

        // Render things here before widgets (background textures)
        // You can draw additional things like text, images, etc.

        // Render the widgets (buttons, labels, etc.)
        super.render(graphics, mouseX, mouseY, partialTick);

        // X and Y position on the screen
        int x = this.width / 2;
        int y = 0;



        int price = ClientMarket.getPrice(itemID);
        String priceText = "Price: " + price;
        int itemStackSize = 20;
        // Draw the item
        graphics.renderItem(itemStack, selectItemButtonRect.x, selectItemButtonRect.y);
        int textHeight = this.font.lineHeight;
        graphics.drawString(this.font, "Price: " + price, selectItemButtonRect.x+(selectItemButtonRect.width/2), selectItemButtonRect.y+textHeight/2, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw the label above the EditBox
        //graphics.drawCenteredString(this.font, "Enter an integer:", 0, , 0xFFFFFF);

        // Draw plot background in gray
        graphics.fill(chartRect.x, chartRect.y,
                chartRect.x + chartRect.width, chartRect.y + chartRect.height, backgroundColor);
        candleStickChart.render(graphics);
        //int buySellEdgeIndex = candleStickChart
        orderbookVolumeChart.render(graphics);

        // Draw limit label
        drawText(graphics, limitLabelRect, "Limit Order");

        // Draw market label
        drawText(graphics, marketLabelRect, "Market Order");

        // Example item to render





        /*
        // Coordinates for the line
        int x1 = 50; // Starting x-coordinate
        int y1 = 50; // Starting y-coordinate
        int x2 = 200; // Ending x-coordinate
        int y2 = 150; // Ending y-coordinate

        // Line color (ARGB format)
        int color = 0xFF00FF00; // Fully opaque green

        drawLine(graphics,10,10,150,150, color, 4);*/
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int width) {
        graphics.fill(x1, y1-width/2, x2, y1+width/2, color);
        graphics.fill(x2-width/2, y2, x2+width/2, y1, color);
    }

    private void drawText(GuiGraphics graphics, Rectangle box, String text)
    {
        int textWidth = this.font.width(text);
        int textHeight = this.font.lineHeight;
        int x = box.x + (box.width - textWidth) / 2;
        int y = box.y + (box.height - textHeight) / 2;
        graphics.drawString(this.font, text, x, y, 0xFFFFFF);
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private void onSellMarketButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Sell button pressed");
        saveAmount();
        ClientMarket.createOrder(itemID, -targetAmount);
        //RequestPricePacket.generateRequest(itemID);
    }
    private void onBuyMarketButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Buy button pressed");
        saveAmount();
        ClientMarket.createOrder(itemID, targetAmount);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onSellLimitButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Sell button pressed");
        saveAmount();
        saveLimitPrice();
        ClientMarket.createOrder(itemID, -targetAmount, targetPrice);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onBuyLimitButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Buy button pressed");
        saveAmount();
        saveLimitPrice();
        ClientMarket.createOrder(itemID, targetAmount, targetPrice);
        //RequestPricePacket.generateRequest(itemID);
    }
    private void onSelectItemButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Select item button pressed");

        // Check if the player has operator permissions (level 2 or higher)
        assert this.minecraft.player != null;
        boolean isOperator = this.minecraft.player.hasPermissions(2);

        // Create an empty FeatureFlagSet if needed (you can add relevant flags if necessary)
        FeatureFlagSet featureFlags = FeatureFlagSet.of();

        // Open the ItemSelectionScreen with operator status and feature flags
        this.minecraft.setScreen(new ItemSelectionScreen(
                this.minecraft.player,
                featureFlags,
                isOperator, // Check if player is an operator
                this,
                this::onItemSelected
        ));
    }

    private void saveLimitPrice() {
        // Retrieve the value from the EditBox
        String text = this.priceBox.getValue();

        if (!text.isEmpty()) {
            try {
                this.targetPrice = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                // Handle invalid input (shouldn't happen due to input filter)
                this.targetPrice = 0;
            }
        } else {
            // Handle empty input
            this.targetPrice = 0;
        }
    }
    private void saveAmount() {
        // Retrieve the value from the EditBox
        String text = this.amountBox.getValue();

        if (!text.isEmpty()) {
            try {
                this.targetAmount = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                // Handle invalid input (shouldn't happen due to input filter)
                this.targetAmount = 0;
            }
        } else {
            // Handle empty input
            this.targetAmount = 0;
        }
    }





    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.priceBox.isFocused()) {
            return this.priceBox.keyPressed(keyCode, scanCode, modifiers)
                    || this.priceBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.priceBox.isFocused()) {
            return this.priceBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.priceBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.priceBox);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    public static ItemStack getItemStackFromId(String itemId) {
        // Convert the string ID to a ResourceLocation
        ResourceLocation resourceLocation = new ResourceLocation(itemId);

        // Get the item from the registry
        Item item = BuiltInRegistries.ITEM.get(resourceLocation);

        // Check if the item exists
        if (item == null) {
            throw new IllegalArgumentException("Invalid item ID: " + itemId);
        }

        // Return an ItemStack of the item
        return new ItemStack(item);
    }
}
