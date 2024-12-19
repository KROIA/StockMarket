// TradeScreen.java
package net.kroia.stockmarket.screen.custom;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.bank.ClientBankManager;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestBankDataPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.screen.uiElements.ColoredButton;
import net.kroia.stockmarket.util.CandleStickChart;
import net.kroia.stockmarket.util.OrderListWidget;
import net.kroia.stockmarket.util.OrderbookVolumeChart;
import net.kroia.stockmarket.util.geometry.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TradeScreen extends Screen {


    private static final Component TITLE = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen");
    private static final Component SELL_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen.sell_button");
    private static final Component BUY_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen.buy_button");
    private static final Component SELECT_ITEM_BUTTON_TEXT = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen.select_item_button");
    private static final Component AMOUNT_BOX_TOOLTIP = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen.amount_box_tooltip");
    private static final Component PRICE_BOX_TOOLTIP = Component.translatable("gui." + StockMarketMod.MODID + ".stock_market_block_screen.price_box_tooltip");


    private final int backgroundColor = 0x7F404040;
    private final int buyButtonHoverColor = 0xFF00FF00;
    private final int sellButtonHoverColor = 0xFFFF0000;
    private final int buyButtonNormalColor = 0xFF008800;
    private final int sellButtonNormalColor = 0xFF880000;

    private static CandleStickChart candleStickChart;
    private static OrderbookVolumeChart orderbookVolumeChart = new OrderbookVolumeChart();
    private static OrderListWidget orderListWidget;

    //private final ArrayList<CandleStickChart.CandleData> chartData;
    private int padding;
    private int buttonHeight;
    private int chartWidth;
    private int buttonSectionLeft;
    private Rectangle chartRect;

    private Rectangle selectItemButtonRect;
    private Rectangle currentBalanceRectTop;
    private Rectangle currentBalanceRectBottom;
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


    private static String itemID;
    private static ItemStack itemStack;

    private ColoredButton sellMarketButton;
    private ColoredButton buyMarketButton;
    private Button selectItemButton;

    private EditBox priceBox;
    private EditBox amountBox;

    private ColoredButton sellLimitButton;
    private ColoredButton buyLimitButton;


    private static int targetPrice = 0;
    private static int targetAmount = 0;

    static long lastTickCount = 0;

    static TradeScreen instance;
    //static boolean test = true;
    static StockMarketBlockEntity blockEntity;

    public TradeScreen(StockMarketBlockEntity blockEntity) {
        super(TITLE);
        this.blockEntity = blockEntity;


        TradeScreen.candleStickChart = new CandleStickChart(this);
        orderListWidget = new OrderListWidget(this);
        instance = this;
        this.itemID = blockEntity.getItemID();
        this.targetAmount = blockEntity.getAmount();
        this.targetPrice = blockEntity.getPrice();


    }

    @Override
    public void onClose() {
        super.onClose();
        // Unregister the event listener when the screen is closed
        MinecraftForge.EVENT_BUS.unregister(this);
        ClientMarket.unsubscribeMarketUpdate(itemID);
        saveAmount();
        saveLimitPrice();
        blockEntity.setItemID(itemID);
        blockEntity.setAmount(targetAmount);
        blockEntity.setPrice(targetPrice);
        UpdateStockMarketBlockEntityPacket.sendPacketToServer(blockEntity.getBlockPos(), blockEntity);
        //UpdateSubscribeMarketEventsPacket.generateRequest(itemID, false);
    }

    public static void handlePacket(SyncStockMarketBlockEntityPacket packet) {
        //blockEntity.setItemID(packet.getItemID());
        //blockEntity.setAmount(packet.getAmount());
        //blockEntity.setPrice(packet.getPrice());
        RequestBankDataPacket.sendRequest();
        itemID = packet.getItemID();
        targetAmount = packet.getAmount();
        targetPrice = packet.getPrice();
        if (instance != null) {
            //instance.init();
            instance.priceBox.setValue(String.valueOf(targetPrice));
            instance.amountBox.setValue(String.valueOf(targetAmount));
            itemStack = StockMarketMod.createItemStackFromId(itemID,1);
            ClientMarket.subscribeMarketUpdate(itemID);
        }

    }

    @Override
    protected void init() {
        super.init();
        ClientMarket.init();

        // Set Layout
        padding = 10;
        int uiElementPadding = 2;
        int buttonHeight = 16;
        int chartWidth = (this.width * 3) / 4;
        int buttonSectionLeft = chartWidth + 5;
        chartRect = new Rectangle(0, 0, chartWidth, this.height / 2);

        currentBalanceRectTop = new Rectangle(buttonSectionLeft, 0, this.width - buttonSectionLeft, buttonHeight);
        currentBalanceRectBottom = new Rectangle(buttonSectionLeft, buttonHeight, this.width - buttonSectionLeft, buttonHeight);
        selectItemButtonRect = new Rectangle(buttonSectionLeft, 2*(buttonHeight + uiElementPadding), this.width - buttonSectionLeft, buttonHeight);

        // Amount
        int amountYPos = 50;
        amountEditRect = new Rectangle(buttonSectionLeft + selectItemButtonRect.width / 2, amountYPos, selectItemButtonRect.width / 2, buttonHeight);

        // Market
        int marketYPos = 80;
        marketLabelRect = new Rectangle(buttonSectionLeft, marketYPos, this.width - buttonSectionLeft, buttonHeight);
        buyMarketButtonRect = new Rectangle(buttonSectionLeft, marketYPos + buttonHeight + uiElementPadding, (this.width - buttonSectionLeft) / 2, buttonHeight);
        sellMarketButtonRect = new Rectangle(buttonSectionLeft + (this.width - buttonSectionLeft) / 2, marketYPos + buttonHeight + uiElementPadding, (this.width - buttonSectionLeft) / 2, buttonHeight);


        // Limit
        int limitYPos = 130;
        limitLabelRect = new Rectangle(buttonSectionLeft, limitYPos, this.width - buttonSectionLeft, buttonHeight);
        limitPriceEditRect = new Rectangle(buttonSectionLeft + selectItemButtonRect.width / 2, limitYPos + buttonHeight + uiElementPadding, selectItemButtonRect.width / 2, buttonHeight);
        buyLimitButtonRect = new Rectangle(buttonSectionLeft, limitYPos + 2 * (buttonHeight + uiElementPadding), (this.width - buttonSectionLeft) / 2, buttonHeight);
        sellLimitButtonRect = new Rectangle(buttonSectionLeft + (this.width - buttonSectionLeft) / 2, limitYPos + 2 * (buttonHeight + uiElementPadding), (this.width - buttonSectionLeft) / 2, buttonHeight);


        //int buttonVSpacing = 5;
        //int currentY = 0;

        candleStickChart.setChartView(chartRect.x + padding, chartRect.y + padding, chartRect.width - 50 - 2 * padding, chartRect.height - 2 * padding);
        orderbookVolumeChart.setChartView(chartRect.x + chartRect.width - 50 - padding, chartRect.y + padding, 50, chartRect.height - 2 * padding);


        sellLimitButton = (ColoredButton) addRenderableWidget(ColoredButton.builder(SELL_BUTTON_TEXT,
                        this::onSellLimitButtonPressed)
                .hoverColor(sellButtonHoverColor)
                .normalColor(sellButtonNormalColor)
                .bounds(sellLimitButtonRect.x, sellLimitButtonRect.y, sellLimitButtonRect.width, sellLimitButtonRect.height).build());


        buyLimitButton = (ColoredButton) addRenderableWidget(ColoredButton.builder(BUY_BUTTON_TEXT,
                        this::onBuyLimitButtonPressed)
                .hoverColor(buyButtonHoverColor)
                .normalColor(buyButtonNormalColor)
                .bounds(buyLimitButtonRect.x, buyLimitButtonRect.y, buyLimitButtonRect.width, buyLimitButtonRect.height).build());


        // Add a button to open the item selection dialog
        selectItemButton = addRenderableWidget(Button.builder(SELECT_ITEM_BUTTON_TEXT,
                this::onSelectItemButtonPressed).bounds(selectItemButtonRect.x, selectItemButtonRect.y, selectItemButtonRect.width, selectItemButtonRect.height).build());

        int editBoxPadding = 3;
        this.amountBox = new EditBox(this.font, amountEditRect.x+editBoxPadding, amountEditRect.y+editBoxPadding, amountEditRect.width - editBoxPadding*2, amountEditRect.height-editBoxPadding*2, Component.literal("Enter an integer"));
        this.amountBox.setMaxLength(10); // Max length of input
        this.amountBox.setFilter(input -> input.matches("\\d*")); // Allow only digits
        this.addRenderableWidget(this.amountBox);


        // Add the EditBox to the screen

        this.priceBox = new EditBox(this.font, limitPriceEditRect.x+editBoxPadding, limitPriceEditRect.y+editBoxPadding, limitPriceEditRect.width - editBoxPadding*2, limitPriceEditRect.height-editBoxPadding*2, Component.literal("Enter an integer"));
        this.priceBox.setMaxLength(10); // Max length of input
        this.priceBox.setFilter(input -> input.matches("\\d*")); // Allow only digits
        this.addRenderableWidget(this.priceBox);

        sellMarketButton = (ColoredButton) addRenderableWidget(ColoredButton.builder(SELL_BUTTON_TEXT,
                        this::onSellMarketButtonPressed)
                .hoverColor(sellButtonHoverColor)
                .normalColor(sellButtonNormalColor)
                .bounds(sellMarketButtonRect.x, sellMarketButtonRect.y, sellMarketButtonRect.width, sellMarketButtonRect.height).build());

        buyMarketButton = (ColoredButton) addRenderableWidget(ColoredButton.builder(BUY_BUTTON_TEXT,
                        this::onBuyMarketButtonPressed)
                .hoverColor(buyButtonHoverColor)
                .normalColor(buyButtonNormalColor)
                .bounds(buyMarketButtonRect.x, buyMarketButtonRect.y, buyMarketButtonRect.width, buyMarketButtonRect.height).build());

        orderListWidget.init(0, chartRect.y + chartRect.height, chartRect.width, this.height - chartRect.height);

        // Register the event listener when the screen is initialized
        MinecraftForge.EVENT_BUS.register(this);
        //UpdateSubscribeMarketEventsPacket.generateRequest(itemID, true);

        //updatePlotsData();
        // Set value of the EditBox
        this.priceBox.setValue(String.valueOf(targetPrice));
        this.amountBox.setValue(String.valueOf(targetAmount));
        itemStack = StockMarketMod.createItemStackFromId(itemID,1);
        ClientMarket.subscribeMarketUpdate(itemID);
        RequestBankDataPacket.sendRequest();
    }

    public static String getItemID() {
        return itemID;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.END)
        {
            long currentTickCount = System.currentTimeMillis();
            if(currentTickCount - lastTickCount > 1000)
            {
                lastTickCount = currentTickCount;
                //StockMarketMod.LOGGER.info("Requesting bank data");
                RequestBankDataPacket.sendRequest();
            }
        }
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

    public static void onAvailableTradeItemsChanged() {
        // check if screen is visible
        if (instance.minecraft.screen == instance) {
            //ClientMarket.subscribeMarketUpdate(itemID);
            //updatePlotsData();

            /*if (!test) {
                test = true;
                // Create some dummy orders
                ClientMarket.createOrder(itemID, -30, 90);
                ClientMarket.createOrder(itemID, -10, 80);
                ClientMarket.createOrder(itemID, -8, 78);
                ClientMarket.createOrder(itemID, -2, 75);
                ClientMarket.createOrder(itemID, 1, 45);
                ClientMarket.createOrder(itemID, 5, 44);
                ClientMarket.createOrder(itemID, 20, 40);
            }*/
        }
    }

    public static void updatePlotsData() {
        ClientTradeItem item = ClientMarket.getTradeItem(itemID);
        if (item == null) {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }

        candleStickChart.setMinMaxPrice(item.getVisualMinPrice(), item.getVisualMaxPrice());
        candleStickChart.setPriceHistory(item.getPriceHistory());
        orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());
        orderListWidget.init();
    }

    private void onItemSelected(String itemId) {
        StockMarketMod.LOGGER.info("Item selected: " + itemId);

        ClientMarket.unsubscribeMarketUpdate(itemID);
        this.itemID = itemId;
        ClientMarket.subscribeMarketUpdate(itemID);
        itemStack = StockMarketMod.createItemStackFromId(itemID,1);
        //RequestPricePacket.generateRequest(itemId);
        //updatePlotsData();
    }

    @Override
    public void renderBackground(PoseStack pGuiGraphics)
    {
        super.renderBackground(pGuiGraphics);
        fill(pGuiGraphics, currentBalanceRectTop.x, currentBalanceRectTop.y, currentBalanceRectBottom.x+currentBalanceRectBottom.width, currentBalanceRectBottom.y+currentBalanceRectBottom.height,
                backgroundColor);

        // Draw plot background in gray
        fill(pGuiGraphics, chartRect.x, chartRect.y,
                chartRect.x + chartRect.width, chartRect.y + chartRect.height, backgroundColor);

        fill(pGuiGraphics, selectItemButtonRect.x, selectItemButtonRect.y, sellLimitButtonRect.x+sellLimitButtonRect.width, sellLimitButtonRect.y+sellLimitButtonRect.height,
                backgroundColor);
    }
    @Override
    public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        //TutorialMod.LOGGER.info("Render Chart Screen");
        // Background is typically rendered first
        this.renderBackground(graphics);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();


        // Render things here before widgets (background textures)
        // You can draw additional things like text, images, etc.

        // Render the widgets (buttons, labels, etc.)
        super.render(graphics, mouseX, mouseY, partialTick);
        // super.render(graphics, mouseX, mouseY, partialTick);

        // X and Y position on the screen
        int x = this.width / 2;
        int y = 0;



        int textHeight = this.font.lineHeight;

        // Draw the title

        drawText(graphics, currentBalanceRectTop, "Your balance");

        itemRenderer.renderGuiItem(graphics, itemStack, currentBalanceRectBottom.x, currentBalanceRectBottom.y);
        Minecraft.getInstance().font.draw(graphics, String.valueOf(ClientBankManager.getBalance(itemID)), currentBalanceRectBottom.x+selectItemButtonRect.height, currentBalanceRectBottom.y+ textHeight / 2, 0xFFFFFF);
        Minecraft.getInstance().font.draw(graphics, "$"+ ClientBankManager.getBalance(), currentBalanceRectBottom.x+currentBalanceRectBottom.width/2, currentBalanceRectBottom.y + textHeight / 2, 0xFFFFFF);

        // Draw the item
        itemRenderer.renderGuiItem(graphics, itemStack, selectItemButtonRect.x, selectItemButtonRect.y);

        int price = ClientMarket.getPrice(itemID);
        Minecraft.getInstance().font.draw(graphics, "Price: "+ price, selectItemButtonRect.x+selectItemButtonRect.height, selectItemButtonRect.y + (float) textHeight/2, 0xFFFFFF);

        Minecraft.getInstance().font.draw(graphics, "Amount:", selectItemButtonRect.x, amountEditRect.y + (float) textHeight / 2, 0xFFFFFF);
        Minecraft.getInstance().font.draw(graphics, "Limit price:", selectItemButtonRect.x, limitPriceEditRect.y + (float) textHeight / 2, 0xFFFFFF);


        // Draw the label above the EditBox
        //graphics.drawCenteredString(this.font, "Enter an integer:", 0, , 0xFFFFFF);


        candleStickChart.render(graphics);
        //int buySellEdgeIndex = candleStickChart
        orderbookVolumeChart.render(graphics);

        // Draw limit label
        drawText(graphics, limitLabelRect, "Limit Order");

        // Draw market label
        drawText(graphics, marketLabelRect, "Market Order");

        orderListWidget.render(graphics, mouseX, mouseY);

        // Draw tooltips
        drawToolTipForElement(graphics, amountBox, mouseX, mouseY, AMOUNT_BOX_TOOLTIP);
        drawToolTipForElement(graphics, priceBox, mouseX, mouseY, PRICE_BOX_TOOLTIP);


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

    private void drawLine(PoseStack graphics, int x1, int y1, int x2, int y2, int color, int width) {
        fill(graphics, x1, y1 - width / 2, x2, y1 + width / 2, color);
        fill(graphics,x2 - width / 2, y2, x2 + width / 2, y1, color);
    }

    private void drawText(PoseStack graphics, Rectangle box, String text) {
        int textWidth = this.font.width(text);
        int textHeight = this.font.lineHeight;
        int x = box.x + (box.width - textWidth) / 2;
        int y = box.y + (box.height - textHeight) / 2;
        Minecraft.getInstance().font.draw(graphics, text, x, y, 0xFFFFFF);
    }

    private void drawToolTipForElement(PoseStack graphics, AbstractWidget widget, int mouseX, int mouseY, Component tooltip) {
        if (widget.isMouseOver(mouseX, mouseY)) {
            // Render a tooltip
            renderTooltip(graphics,
                    tooltip,
                    mouseX,
                    mouseY
            );
        }
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void onSellMarketButtonPressed(Button button) {
        StockMarketMod.LOGGER.info("Sell button pressed");
        saveAmount();
        ClientMarket.createOrder(itemID, -targetAmount);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onBuyMarketButtonPressed(Button button) {
        StockMarketMod.LOGGER.info("Buy button pressed");
        saveAmount();
        ClientMarket.createOrder(itemID, targetAmount);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onSellLimitButtonPressed(Button button) {
        StockMarketMod.LOGGER.info("Sell button pressed");
        saveAmount();
        saveLimitPrice();
        ClientMarket.createOrder(itemID, -targetAmount, targetPrice);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onBuyLimitButtonPressed(Button button) {
        StockMarketMod.LOGGER.info("Buy button pressed");
        saveAmount();
        saveLimitPrice();
        ClientMarket.createOrder(itemID, targetAmount, targetPrice);
        //RequestPricePacket.generateRequest(itemID);
    }

    private void onSelectItemButtonPressed(Button button) {
        StockMarketMod.LOGGER.info("Select item button pressed");

        // Check if the player has operator permissions (level 2 or higher)
        assert this.minecraft.player != null;
        boolean isOperator = this.minecraft.player.hasPermissions(2);

        // Create an empty FeatureFlagSet if needed (you can add relevant flags if necessary)
        FeatureFlagSet featureFlags = FeatureFlagSet.of();

        // Open the ItemSelectionScreen with operator status and feature flags
        /*this.minecraft.setScreen(new ItemSelectionScreen(
                this.minecraft.player,
                featureFlags,
                isOperator, // Check if player is an operator
                this,
                this::onItemSelected,
                ClientMarket.getAvailableTradeItemIdList()
        ));*/

        this.minecraft.setScreen(new CustomItemSelectionScreen(
                this,
                ClientMarket.getAvailableTradeItemIdList(),
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
        // Check if the pressed key matches the inventory key binding
        if (keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            onClose(); // Close the screen
            return true; // Indicate the event was handled
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
        if (button == 0) {
            if (orderListWidget.handleMouseClick(mouseX, mouseY))
                return true;
            if (this.priceBox.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(this.priceBox);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX) {
        // Handle scrolling
        if (orderListWidget.mouseScrolled(pMouseX, pMouseY, pScrollX))
            return true;
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX);
    }
}
