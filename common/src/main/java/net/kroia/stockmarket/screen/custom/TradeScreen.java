// TradeScreen.java
package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.TradingViewData;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.kroia.stockmarket.screen.uiElements.CandleStickChart;
import net.kroia.stockmarket.screen.uiElements.OrderListView;
import net.kroia.stockmarket.screen.uiElements.OrderbookVolumeChart;
import net.kroia.stockmarket.screen.uiElements.TradePanel;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;

import java.util.*;


public class TradeScreen extends GuiScreen {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;

    static class ItemSorter implements ItemSelectionView.Sorter {
        @Override
        public void apply(ArrayList<ItemStack> items) {


            Map<String, List<ItemStack>> categorizedItems = new LinkedHashMap<>();

            // Define sorting order (Creative Inventory order)
            List<String> categoryOrder = Arrays.asList(
                    "ores", "food", "building_blocks", "decoration_blocks", "redstone", "transportation",
                    "tools", "combat", "arrows", "brewing", "misc"
            );
            List<String> itemIDContains = Arrays.asList(
                    "log", "planks", "slab", "stairs", "fence", "door", "pressure_plate", "button",
                    "wool", "carpet", "terracotta", "concrete", "glass",
                    "torch", "lantern", "campfire",
                    "furnace", "smoker", "cartography_table", "loom", "smithing_table", "stonecutter",
                    "grindstone", "anvil", "barrel", "beacon", "bell", "brewing_stand", "cauldron",
                    "composter", "enchanting_table", "end_crystal", "end_portal_frame", "fletching_table",
                    "jukebox", "lectern", "note_block", "observer", "piston",
                    "dispenser", "dropper", "hopper", "redstone_lamp", "repeater", "comparator",
                    "daylight_detector", "target", "tripwire_hook", "lever",
                    "rail", "redstone_torch", "redstone_block", "redstone_wire"
            );
            // Initialize category lists
            for (String category : categoryOrder) {
                categorizedItems.put(category, new ArrayList<>());
            }
            for(String itemID : itemIDContains)
            {
                categorizedItems.put(itemID, new ArrayList<>());
            }

            categorizedItems.put("uncategorized", new ArrayList<>()); // Items that don't fit

            // Categorize items
            for (ItemStack stack : items) {
                Item item = stack.getItem();

                boolean isAdded = false;
                for(String itemID : itemIDContains)
                {
                    if(idContains(item, itemID))
                    {
                        categorizedItems.get(itemID).add(stack);
                        isAdded = true;
                        break;
                    }
                }
                if(isAdded)
                    continue;

                if (isFood(item)) {
                    categorizedItems.get("food").add(stack);
                } else if (isEnchantedBook(item)) {
                    categorizedItems.get("misc").add(stack);
                } else if (isPotion(item)) {
                    categorizedItems.get("brewing").add(stack);
                } else if (isOre(item)){
                    categorizedItems.get("ores").add(stack);
                } else if (isArrow(item)){
                    categorizedItems.get("arrows").add(stack);
                }else if (isTool(item)){
                    categorizedItems.get("tools").add(stack);
                }else if (isRedstoneObject(item)){
                    categorizedItems.get("redstone").add(stack);
                }else if (isBuildingBlock(item)) {
                    categorizedItems.get("building_blocks").add(stack);
                } else {
                    categorizedItems.get("uncategorized").add(stack);
                }
            }


            // Sort each category alphabetically by registry name
            for (List<ItemStack> category : categorizedItems.values()) {
                category.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            }

            //HashMap<Item, Boolean> added = new HashMap<>();

            // Merge sorted items back into the original list
            items.clear();
            for(String category : itemIDContains)
            {
                for(ItemStack stack : categorizedItems.get(category))
                {
                    //if(added.containsKey(stack.getItem()))
                    {
                        items.add(stack);
                        //added.put(stack.getItem(), true);
                    }
                }
                categorizedItems.remove(category);
            }
            for (String category : categoryOrder) {
                for(ItemStack stack : categorizedItems.get(category))
                {
                    //if(!added.containsKey(stack.getItem()))
                    {
                        items.add(stack);
                        //added.put(stack.getItem(), true);
                    }
                }
                categorizedItems.remove(category);
            }
            for(ItemStack stack : categorizedItems.get("uncategorized"))
            {
                //if(added.containsKey(stack.getItem()))
                {
                    items.add(stack);
                    //added.put(stack.getItem(), true);
                }
            }
        }
        private static boolean isFood(Item item) {
            return item.isEdible();
        }

        private static boolean isBuildingBlock(Item item) {
            return BuiltInRegistries.BLOCK.containsKey(BuiltInRegistries.ITEM.getKey(item));
        }

        private static boolean isEnchantedBook(Item item) {
            CompoundTag tag = new ItemStack(item).getTag();
            return item instanceof EnchantedBookItem || (tag != null && tag.contains("StoredEnchantments"));
        }

        private static boolean isPotion(Item item) {
            CompoundTag tag = new ItemStack(item).getTag();
            return item instanceof PotionItem || (tag != null && tag.contains("Potion"));
        }
        private static boolean isOre(Item item)
        {
            String itemName = ItemUtilities.getItemIDStr(item);

            if(itemName.contains("ore") || itemName.contains("ingot"))
                return true;
            if(itemName.contains("quartz") || itemName.contains("diamond") || itemName.contains("emerald"))
                return true;

            if(itemName.contains("netherite") || itemName.contains("lapis"))
                return true;
            return itemName.contains("coal") || itemName.contains("gold") || itemName.contains("iron");
        }
        private static boolean isArrow(Item item)
        {
            String itemName = ItemUtilities.getItemIDStr(item);
            return itemName.contains("arrow");
        }
        private static boolean isTool(Item item)
        {
            String itemName = ItemUtilities.getItemIDStr(item);
            if(itemName.contains("pickaxe") || itemName.contains("axe") || itemName.contains("shovel"))
                return true;
            if(itemName.contains("hoe") || itemName.contains("shears") || itemName.contains("sword"))
                return true;
            return false;
        }
        private static boolean isRedstoneObject(Item item)
        {
            String itemName = ItemUtilities.getItemIDStr(item);
            if(itemName.contains("redstone") || itemName.contains("piston") || itemName.contains("observer"))
                return true;
            if(itemName.contains("comparator") || itemName.contains("repeater") || itemName.contains("dispenser"))
                return true;
            return false;
        }
        private static boolean idContains(Item item, String contains)
        {
            String itemName = ItemUtilities.getItemIDStr(item);
            return itemName.contains(contains);
        }



    }
    private static final String PREFIX = "gui.";
    private static final String NAME = "trade_screen";

    private static final Component TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".stock_market_block_screen");
    public static final Component YOUR_BALANCE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".your_balance");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".change_item");
    public static final Component AMOUNT_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".amount");
    public static final Component MARKET_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".market_order");
    public static final Component LIMIT_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_order");
    public static final Component LIMIT_PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_price");
    public static final Component PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".price");
    public static final Component BUY = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".buy");
    public static final Component SELL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".sell");
    public static final Component MARKET_CLOSED = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".market_closed");
    public static final Component CANCEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".cancel");
    public static final Component DIRECTION_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".direction");
    public static final Component FILLED_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".filled");

    public static final int colorGreen = 0x7F00FF00;
    public static final int colorRed = 0x7FFF0000;

    private TradingPair tradingPair;
    private boolean marketWasOpen = false;

    static long lastTickCount = 0;
    private StockMarketBlockEntity blockEntity;


    // Gui Elements
    private final CandleStickChart candleStickChart;
    private final OrderbookVolumeChart orderbookVolumeChart;

    private final OrderListView activeOrderListView;

    private final TradePanel tradePanel;
    private static TradeScreen instance;

    private final TimerMillis updateTimer;

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    public TradeScreen(StockMarketBlockEntity blockEntity) {
        this(blockEntity.getTradringPair(), blockEntity.getAmount(), blockEntity.getPrice());
        this.blockEntity = blockEntity;



    }
    public TradeScreen(TradingPair currentPair, int currentAmount, int currentPrice) {
        super(TITLE);
        this.updateTimer = new TimerMillis(true); // Update every second
        updateTimer.start(100);
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.init();
        blockEntity = null;
        instance = this;

        this.tradingPair = currentPair;

        // Create Gui Elements
        this.candleStickChart = new CandleStickChart(this::onOrderChange);
        this.orderbookVolumeChart = new OrderbookVolumeChart();
        this.orderbookVolumeChart.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP);
        this.orderbookVolumeChart.setHoverTooltipSupplier(StockMarketTextMessages::getCandlestickChartTooltipOrderBookVolume);
        this.activeOrderListView = new OrderListView(this::cancelOrder);
        this.tradePanel = new TradePanel(this::onSelectItemButtonPressed,
                this::onBuyMarketButtonPressed,
                this::onSellMarketButtonPressed,
                this::onBuyLimitButtonPressed,
                this::onSellLimitButtonPressed);

        tradePanel.setAmount(currentAmount);
        tradePanel.setLimitPrice(currentPrice);
        tradePanel.setMarketOpen(marketWasOpen);

        // Add Gui Elements
        addElement(candleStickChart);
        addElement(orderbookVolumeChart);
        addElement(activeOrderListView);
        addElement(tradePanel);


        TickEvent.PLAYER_POST.register(TradeScreen::onClientTick);
    }
    public TradeScreen() {
        this(new TradingPair(new ItemID("minecraft:diamond"), new ItemID(BankSystemItems.MONEY.get().getDefaultInstance())), 0, 0);


    }

    public static void openScreen(StockMarketBlockEntity blockEntity)
    {
        TradeScreen screen = new TradeScreen(blockEntity);
        Minecraft.getInstance().setScreen(screen);
    }
    public static void openScreen()
    {
        TradeScreen screen = new TradeScreen();
        Minecraft.getInstance().setScreen(screen);
    }


    @Override
    protected void updateLayout(Gui gui) {
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestTradeItems();
        tradePanel.setTradingPair(tradingPair);
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.subscribeMarketUpdate(tradingPair);
        //RequestBankDataPacket.sendRequest();

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
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        if(blockEntity != null)
        {
            blockEntity.setTradingPair(tradingPair);
            blockEntity.setAmount(tradePanel.getAmount());
            blockEntity.setPrice(tradePanel.getLimitPrice());
            UpdateStockMarketBlockEntityPacket.sendPacketToServer(blockEntity.getBlockPos(), blockEntity);
        }
    }


    public static void handlePacket(SyncStockMarketBlockEntityPacket packet) {
       // RequestBankDataPacket.sendRequest();

        if (instance != null) {
            instance.tradingPair = packet.getTradingPair();
            instance.tradePanel.setTradingPair(instance.tradingPair);
            instance.tradePanel.setAmount(packet.getAmount());
            instance.tradePanel.setLimitPrice(packet.getPrice());
            //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.subscribeMarketUpdate(instance.tradingPair);
        }
    }

    public static TradingPair getTradingPair() {
        if(instance != null)
            return instance.tradingPair;
        return null;
    }


    private static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;


        if(instance.updateTimer.check() && instance.getMarket() != null)
        {
            instance.getMarket().requestTradingViewData(instance.candleStickChart.getMaxCandleCount(), 0,0,500 ,instance::updateView);
        }
        /*long currentTickCount = System.currentTimeMillis();
        if(currentTickCount - lastTickCount > 1000)
        {
            lastTickCount = currentTickCount;
            RequestBankDataPacket.sendRequest();
        }*/
    }

    private void updateView(TradingViewData data)
    {
        if(data == null)
            return;

        candleStickChart.setMinMaxPrice(data.orderBookVolumeData.minPrice, data.orderBookVolumeData.maxPrice);
        PriceHistory history = data.priceHistoryData.toHistory();
        candleStickChart.setPriceHistory(history);
        orderbookVolumeChart.setOrderBookVolume(data.orderBookVolumeData);
        tradePanel.setCurrentItemBalance(data.itemBankData.balance);
        tradePanel.setCurrentMoneyBalance(data.currencyBankData.balance);


        tradePanel.setCurrentPrice(history.getCurrentPrice());
        activeOrderListView.updateActiveOrders(data.openOrdersData);
        candleStickChart.updateOrderDisplay(data.openOrdersData);

        if(marketWasOpen != data.marketIsOpen)
        {
            marketWasOpen = data.marketIsOpen;
            tradePanel.setMarketOpen(data.marketIsOpen);
        }
    }

    //public static void onAvailableTradeItemsChanged() {
    //}

    /*public static void updatePlotsData_static() {
        if(instance == null)
            return;
        instance.updatePlotData();
    }
    public void updatePlotData()
    {
        ClientTradeItem item = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getTradeItem(tradingPair);
        if (item == null) {
            BACKEND_INSTANCES.LOGGER.warn("Trade item not found: " + tradingPair);
            return;
        }

        candleStickChart.setMinMaxPrice(item.getVisualMinPrice(), item.getVisualMaxPrice());
        candleStickChart.setPriceHistory(item.getPriceHistory());
        orderbookVolumeChart.setOrderBookVolume(item.getOrderBookVolume());
        assert Minecraft.getInstance().player != null;
        UUID thisPlayerUUID = Minecraft.getInstance().player.getUUID();
        BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager().requestMinimalBankData(thisPlayerUUID, tradingPair,
                (MinimalBankData data) -> {
                    if(data != null)
                    {
                        tradePanel.setCurrentItemBalance(data.balance);
                    }
                });
        BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager().requestMinimalBankData(thisPlayerUUID, BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getCurrencyItem(),
                (MinimalBankData data) -> {
                    if(data != null)
                    {
                        tradePanel.setCurrentMoneyBalance(data.balance);
                    }
                });
        tradePanel.setCurrentPrice(item.getPrice());
        getMarket().requestPlayerOrderReadDataList((orders)->{
            activeOrderListView.updateActiveOrders(orders);
            candleStickChart.updateOrderDisplay(orders);
        });


        if(marketWasOpen != item.isMarketOpen())
        {
            marketWasOpen = item.isMarketOpen();
            tradePanel.setMarketOpen(item.isMarketOpen());
        }
    }*/

    /*private void onItemSelected(ItemStack itemStack) {
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        this.tradingPair = new ItemID(itemStack);
        tradePanel.setItemStack(itemStack);
    }*/
    private void onItemSelected(TradingPair tradingPair) {
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        this.tradingPair = tradingPair;
        tradePanel.setTradingPair(tradingPair);
        minecraft.setScreen(this);
    }


    private void onSellMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            getMarket().requestCreateMarketOrder(-amount, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.info("Market sell order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create market sell order.");
                }
            });
    }

    private void onBuyMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            getMarket().requestCreateMarketOrder(amount, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.info("Market buy order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create market buy order.");
                }
            });
    }

    private void onSellLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getMarket().requestCreateLimitOrder(-amount, price, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.info("Limit sell order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create limit sell order.");
                }
            });
    }

    private void onBuyLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getMarket().requestCreateLimitOrder(amount, price, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.info("Limit buy order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create limit buy order.");
                }
            });
    }

    private void onSelectItemButtonPressed() {
        /*ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for(ItemID itemID : BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getAvailableTradeItemIdList())
        {
            itemStacks.add(itemID.getStack());
        }
        ItemSelectionScreen screen = new ItemSelectionScreen(
                this,
                itemStacks,
                this::onItemSelected);

        screen.getItemSelectionView().setSorter(new ItemSorter());
        screen.sortItems();
        this.minecraft.setScreen(screen);*/
        TradingPairSelectionScreen screen = new TradingPairSelectionScreen(this, this::onItemSelected);
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestTradingPairs(
                (tradingPairs) -> {
                    screen.setAvailableTradingPairs(tradingPairs);
                    //screen.getItemSelectionView().setSorter(new ItemSorter());
                    //screen.sortItems();

                });
        Minecraft.getInstance().setScreen(screen);
    }

    private void cancelOrder(OrderReadData order)
    {
        getMarket().requestCancelOrder(order.orderID, (success) -> {
            if(success)
            {
                BACKEND_INSTANCES.LOGGER.info("Order cancelled: " + order.orderID);
            }
            else
            {
                BACKEND_INSTANCES.LOGGER.warn("Failed to cancel order: " + order.orderID);
            }
        });
    }
    private void onOrderChange(OrderReadData order, Integer newPrice)
    {
        if(newPrice != null && newPrice >= 0)
        {
            getMarket().requestChangeOrder(order.orderID, newPrice, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.info("Order price changed successfully: " + order.orderID + " to " + newPrice);
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to change order: " + order.orderID+ " to " + newPrice);
                }
            });
        }
        else
        {
            BACKEND_INSTANCES.LOGGER.warn("Invalid new price for order: " + order.orderID);
        }
    }

    private ClientMarket getMarket()
    {
        return BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getClientMarket(tradingPair);
    }
}
