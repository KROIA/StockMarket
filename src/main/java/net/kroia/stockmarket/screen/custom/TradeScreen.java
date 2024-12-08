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

import java.util.ArrayList;

public class TradeScreen extends Screen {

    private static final Component TITLE = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen");
    private static final Component SELL_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.sell_button");
    private static final Component BUY_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.buy_button");
    private static final Component SELECT_ITEM_BUTTON_TEXT = Component.translatable("gui."+ StockMarketMod.MODID+".stock_market_block_screen.select_item_button");

    private static final CandleStickChart candleStickChart = new CandleStickChart();
    private static OrderbookVolumeChart orderbookVolumeChart = new OrderbookVolumeChart();

    //private final ArrayList<CandleStickChart.CandleData> chartData;
    private final int chartWidth = 200;
    private final int chartHeight = 100;
    private final int padding = 10;
    private final int chartX = 200;
    private final int chartY = 200;

    private static String itemID;
    private static ItemStack itemStack;

    private Button sellMarketButton;
    private Button buyMarketButton;
    private Button selectItemButton;

    private EditBox priceBox;
    private Button sellLimitButton;
    private Button buyLimitButton;

    private int targetPrice = 0;

    static int lastTickPahaseCount = 0;
    static int tickPhaseCount = 0;

    static TradeScreen instance;
    static boolean test = false;

    public TradeScreen(String itemID) {
        super(TITLE);
        instance = this;
        this.itemID = itemID;
        itemStack = getItemStackFromId(itemID);
        //RequestPricePacket.generateRequest(itemID);
        candleStickChart.setChartView(0, 100, 100,100, chartWidth, chartHeight);
        orderbookVolumeChart.setChartView(chartWidth+100, 100, 50, chartHeight);
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

        int buttonHeight = 20;
        int buttonVSpacing = 5;
        int currentY = 0;
        sellMarketButton = addRenderableWidget(Button.builder(SELL_BUTTON_TEXT,
                this::onSellMarketButtonPressed).bounds(0,currentY,100,buttonHeight).build()); currentY += buttonHeight+buttonVSpacing;
        buyMarketButton = addRenderableWidget(Button.builder(BUY_BUTTON_TEXT,
                this::onBuyMarketButtonPressed).bounds(0,currentY,100,buttonHeight).build()); currentY += buttonHeight+buttonVSpacing;

        // Add a button to open the item selection dialog
        selectItemButton = addRenderableWidget(Button.builder(SELECT_ITEM_BUTTON_TEXT,
                this::onSelectItemButtonPressed).bounds(0, currentY, 150, buttonHeight).build()); currentY += buttonHeight+buttonVSpacing;

        // Add the EditBox to the screen
        this.priceBox = new EditBox(this.font, 0, currentY, 150, buttonHeight, Component.literal("Enter an integer")); currentY += buttonHeight+buttonVSpacing;
        this.priceBox.setMaxLength(10); // Max length of input
        this.priceBox.setFilter(input -> input.matches("\\d*")); // Allow only digits
        this.addRenderableWidget(this.priceBox);

        sellLimitButton = addRenderableWidget(Button.builder(SELL_BUTTON_TEXT,
                this::onSellLimitButtonPressed).bounds(0,currentY,100,buttonHeight).build()); currentY += buttonHeight+buttonVSpacing;
        buyLimitButton = addRenderableWidget(Button.builder(BUY_BUTTON_TEXT,
                this::onBuyLimitButtonPressed).bounds(0,currentY,100,buttonHeight).build()); currentY += buttonHeight+buttonVSpacing;




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

        double price = ClientMarket.getPrice(itemID);
        graphics.drawString(this.font, "Price: " + price, this.width / 2, 5, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw the label above the EditBox
        //graphics.drawCenteredString(this.font, "Enter an integer:", 0, , 0xFFFFFF);

        candleStickChart.render(graphics);
        orderbookVolumeChart.render(graphics);

        // Example item to render


        // X and Y position on the screen
        int x = this.width / 2;
        int y = this.height / 2;

        // Draw the item
        graphics.renderItem(itemStack, x, y);


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


    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private void onSellMarketButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Sell button pressed");
        ClientMarket.createOrder(itemID, -1);
        //RequestPricePacket.generateRequest(itemID);
    }
    private void onBuyMarketButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Buy button pressed");
        ClientMarket.createOrder(itemID, 1);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onSellLimitButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Sell button pressed");
        saveInputValue();
        ClientMarket.createOrder(itemID, -1, targetPrice);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onBuyLimitButtonPressed(Button button)
    {
        StockMarketMod.LOGGER.info("Buy button pressed");
        saveInputValue();
        ClientMarket.createOrder(itemID, 1, targetPrice);
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

    private void saveInputValue() {
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
