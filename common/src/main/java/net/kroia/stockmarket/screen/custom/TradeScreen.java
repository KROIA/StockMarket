// TradeScreen.java
package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.banking.ClientBankManager;
import net.kroia.banksystem.networking.packet.client_sender.request.RequestBankDataPacket;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.kroia.stockmarket.screen.uiElements.CandleStickChart;
import net.kroia.stockmarket.screen.uiElements.OrderListView;
import net.kroia.stockmarket.screen.uiElements.OrderbookVolumeChart;
import net.kroia.stockmarket.screen.uiElements.TradePanel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


public class TradeScreen extends GuiScreen {
    private static final String PREFIX = "gui.";
    private static final String NAME = "trade_screen";

    private static final Component TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".stock_market_block_screen");
    private static final Component ITEM_SELECTION_SCREEN_TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".item_selection_screen");
    public static final Component YOUR_BALANCE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".your_balance");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".change_item");
    public static final Component AMOUNT_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".amount");
    public static final Component MARKET_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".market_order");
    public static final Component LIMIT_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_order");
    public static final Component LIMIT_PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_price");
    public static final Component PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".price");
    public static final Component BUY = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".buy");
    public static final Component SELL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".sell");
    public static final Component CANCEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".cancel");
    public static final Component DIRECTION_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".direction");
    public static final Component FILLED_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".filled");
    public static final Component BACK_BUTTON = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".back");


    public static final int colorGreen = 0x7F00FF00;
    public static final int colorRed = 0x7FFF0000;

    private static String itemID;
    private static ItemStack itemStack;

    static long lastTickCount = 0;
    static StockMarketBlockEntity blockEntity;


    // Gui Elements
    private final CandleStickChart candleStickChart;
    private final OrderbookVolumeChart orderbookVolumeChart;

    private final OrderListView activeOrderListView;

    private final TradePanel tradePanel;

    private static TradeScreen instance;

    public TradeScreen(StockMarketBlockEntity blockEntity) {
        super(TITLE);
        instance = this;
        this.blockEntity = blockEntity;
        this.itemID = blockEntity.getItemID();

        // Create Gui Elements
        this.candleStickChart = new CandleStickChart();
        this.orderbookVolumeChart = new OrderbookVolumeChart();
        this.activeOrderListView = new OrderListView();
        this.tradePanel = new TradePanel(this::onSelectItemButtonPressed,
                                         this::onBuyMarketButtonPressed,
                                         this::onSellMarketButtonPressed,
                                         this::onBuyLimitButtonPressed,
                                         this::onSellLimitButtonPressed);

        tradePanel.setAmount(blockEntity.getAmount());
        tradePanel.setLimitPrice(blockEntity.getPrice());

        // Add Gui Elements
        addElement(candleStickChart);
        addElement(orderbookVolumeChart);
        addElement(activeOrderListView);
        addElement(tradePanel);

        TickEvent.PLAYER_POST.register(TradeScreen::onClientTick);
    }


    @Override
    protected void updateLayout(Gui gui) {
        ClientMarket.init();
        itemStack = ItemUtilities.createItemStackFromId(itemID,1);
        tradePanel.setItemStack(itemStack);
        ClientMarket.subscribeMarketUpdate(itemID);
        RequestBankDataPacket.sendRequest();
        // Register the event listener when the screen is initialized



        int padding = 10;
        int spacing = 4;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int x = padding;
        candleStickChart.setBounds(x, padding, (width * 5) / 8-spacing/2, height/2);
        orderbookVolumeChart.setBounds(candleStickChart.getRight(), padding, width / 8, candleStickChart.getHeight());
        tradePanel.setBounds(orderbookVolumeChart.getRight()+spacing, padding, width/4, height);

        activeOrderListView.setBounds(candleStickChart.getLeft(), candleStickChart.getBottom()+spacing, tradePanel.getLeft()-candleStickChart.getLeft()-spacing, height-candleStickChart.getHeight()-spacing);
    }

    @Override
    public void onClose() {
        super.onClose();
        instance = null;
        // Unregister the event listener when the screen is closed
        TickEvent.PLAYER_POST.unregister(TradeScreen::onClientTick);
        ClientMarket.unsubscribeMarketUpdate(itemID);
        blockEntity.setItemID(itemID);
        blockEntity.setAmount(tradePanel.getAmount());
        blockEntity.setPrice(tradePanel.getLimitPrice());
        UpdateStockMarketBlockEntityPacket.sendPacketToServer(blockEntity.getBlockPos(), blockEntity);
    }

    public static void handlePacket(SyncStockMarketBlockEntityPacket packet) {
        RequestBankDataPacket.sendRequest();
        itemID = packet.getItemID();
        if (instance != null) {
            itemStack = ItemUtilities.createItemStackFromId(itemID,1);
            instance.tradePanel.setItemStack(itemStack);
            instance.tradePanel.setAmount(packet.getAmount());
            instance.tradePanel.setLimitPrice(packet.getPrice());
            ClientMarket.subscribeMarketUpdate(itemID);
        }
    }

    public static String getItemID() {
        return itemID;
    }


    public static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;

        long currentTickCount = System.currentTimeMillis();
        if(currentTickCount - lastTickCount > 1000)
        {
            lastTickCount = currentTickCount;
            RequestBankDataPacket.sendRequest();
        }
    }

    public static void onAvailableTradeItemsChanged() {
        // check if screen is visible
        if (instance.minecraft.screen == instance) {

        }
    }

    public static void updatePlotsData() {
        ClientTradeItem item = ClientMarket.getTradeItem(itemID);
        if (item == null) {
            StockMarketMod.LOGGER.warn("Trade item not found: " + itemID);
            return;
        }

        if(instance == null)
            return;

        instance.candleStickChart.setMinMaxPrice(item.getVisualMinPrice(), item.getVisualMaxPrice());
        instance.candleStickChart.setPriceHistory(item.getPriceHistory());
        instance.orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());
        instance.tradePanel.setCurrentItemBalance(ClientBankManager.getBalance(itemID));
        instance.tradePanel.setCurrentPrice(item.getPrice());
        instance.tradePanel.setCurrentMoneyBalance(ClientBankManager.getBalance());
        instance.activeOrderListView.updateActiveOrders();
    }

    private void onItemSelected(String itemId) {
        ClientMarket.unsubscribeMarketUpdate(itemID);
        this.itemID = itemId;
       // ClientMarket.subscribeMarketUpdate(itemID);
        itemStack = ItemUtilities.createItemStackFromId(itemID,1);
        tradePanel.setItemStack(itemStack);
    }


    private void onSellMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            ClientMarket.createOrder(itemID, -amount);
    }

    private void onBuyMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            ClientMarket.createOrder(itemID, amount);
    }

    private void onSellLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            ClientMarket.createOrder(itemID, -amount, price);
    }

    private void onBuyLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            ClientMarket.createOrder(itemID, amount, price);
    }

    private void onSelectItemButtonPressed() {
        this.minecraft.setScreen(new CustomItemSelectionScreen(
                this,
                ClientMarket.getAvailableTradeItemIdList(),
                this::onItemSelected,
                ITEM_SELECTION_SCREEN_TITLE
        ));
    }
}
