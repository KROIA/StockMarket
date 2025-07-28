package net.kroia.stockmarket.market.server;

/*
public class TradeManager implements ServerSaveable {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    private ItemID itemID;

    private MatchingEngine matchingEngine;
    private ServerTradingBot tradingBot;

    private PriceHistory priceHistory;

    public TradeManager(ServerTradeItem tradeItem, int initialPrice, PriceHistory history)
    {
        this.itemID = tradeItem.getItemID();
        matchingEngine = new MatchingEngine(initialPrice, history);
        priceHistory = history;
    }

    public @Nullable BotSettingsData getBotSettingsData()
    {
        if(tradingBot == null)
            return null;
        if(tradingBot instanceof ServerVolatilityBot volatilityBot)
        {
            return new BotSettingsData(itemID, (ServerVolatilityBot.Settings)volatilityBot.getSettings());
        }
        return null;
    }
    void setBotSettingsData(BotSettingsData botSettingsData)
    {
        if(tradingBot == null)
            return;
        if(tradingBot instanceof ServerVolatilityBot volatilityBot)
        {
            ServerVolatilityBot.Settings settings = botSettingsData.toSettings();
            volatilityBot.setSettings(settings);
        }
    }



    public void onServerTick(MinecraftServer server)
    {
        if(tradingBot != null)
            tradingBot.onServerTick(server);
        matchingEngine.onServerTick(server);
    }

    public void clear()
    {
        if(tradingBot != null)
        {
            ServerTradingBot bot = tradingBot;
            bot.clearOrders();
            removeTradingBot();
        }
        cancelAllOrders();
    }

    public void setTradingBot(ServerTradingBot bot)
    {
        if(!BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ENABLED.get())
        {
            BACKEND_INSTANCES.LOGGER.warn("[TradeManager] Trading bots are disabled");
            return;
        }
        if(bot.getParent()!= null)
        {
            bot.getParent().removeTradingBot();
        }
        if(bot instanceof ServerVolatilityBot volatilityBot)
        {
            ServerVolatilityBot.Settings settings = (ServerVolatilityBot.Settings) volatilityBot.getSettings();
        }

        bot.setParent(this);
        bot.setMatchingEngine(matchingEngine);
        tradingBot = bot;
    }
    public void removeTradingBot()
    {
        if(tradingBot != null) {
            tradingBot.setEnabled(false);
            tradingBot.clearOrders();
            tradingBot.setParent(null);
            tradingBot.setMatchingEngine(null);

        }
        tradingBot = null;
    }
    public boolean hasTradingBot()
    {
        return tradingBot != null;
    }
    public ServerTradingBot getTradingBot()
    {
        return tradingBot;
    }

    public void setMarketOpen(boolean open)
    {
        matchingEngine.setMarketOpen(open);
    }
    public boolean isMarketOpen()
    {
        return matchingEngine.isMarketOpen();
    }

    public void setPriceHistory(PriceHistory priceHistory) {
        this.priceHistory = priceHistory;
    }
    public void setItemID(ItemID itemID) {
        this.itemID = itemID;
    }

    public void addOrder(Order order)
    {
        BACKEND_INSTANCES.LOGGER.info("Adding order: " + order.toString());
        matchingEngine.addOrder(order);
    }
    public boolean cancelOrder(long orderID)
    {
        return matchingEngine.cancelOrder(orderID);
    }

    public void cancelAllOrders(UUID playerUUID)
    {
        matchingEngine.cancelAllOrders(playerUUID);
    }
    public void cancelAllOrders()
    {
        matchingEngine.cancelAllOrders();
    }

    public boolean changeOrderPrice(long orderID, int newPrice)
    {
        return matchingEngine.changeOrderPrice(orderID, newPrice);
    }
    public ArrayList<Order> getOrders()
    {
        return matchingEngine.getOrders();
    }

    public void getOrders(UUID playerUUID, ArrayList<Order> orders)
    {
        matchingEngine.getOrders(playerUUID, orders);
    }

    public ItemID getItemID()
    {
        return itemID;
    }


    public void shiftPriceHistory()
    {
        int currentPrice = matchingEngine.getPrice();
        priceHistory.addPrice(currentPrice, currentPrice, currentPrice, new Timestamp());
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }

    public int getLowPrice() {
        return priceHistory.getLowPrice();
    }

    public int getHighPrice() {
        return priceHistory.getHighPrice();
    }

    public int getCurrentPrice() {
        return matchingEngine.getPrice();
    }

    public int getVolume() {
        return matchingEngine.getTradeVolume();
    }

    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        return matchingEngine.getOrderBookVolume(tiles, minPrice, maxPrice);
    }

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        CompoundTag itemTag = new CompoundTag();
        success &= itemID.save(itemTag);
        tag.put("itemID", itemTag);

        CompoundTag matchingEngineTag = new CompoundTag();
        success &= matchingEngine.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);

        CompoundTag botTag = new CompoundTag();
        if(tradingBot != null)
        {
            success &= tradingBot.save(botTag);
            tag.put("tradingBot", botTag);
        }

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("itemID") ||
                !tag.contains("matchingEngine"))
            return false;
        boolean success = true;

        String oldItemID = tag.getString("itemID");
        if(oldItemID.compareTo("")==0)
        {
            if(itemID == null)
            {
                itemID = new ItemID(tag.getCompound("itemID"));
            }
            else
                success &= itemID.load(tag.getCompound("itemID"));
        }
        else {
            itemID = new ItemID(oldItemID);
        }

        CompoundTag matchingEngineTag = tag.getCompound("matchingEngine");
        success &= matchingEngine.load(matchingEngineTag);

        if(tag.contains("tradingBot"))
        {
            CompoundTag tradingBotTag = tag.getCompound("tradingBot");
            ServerTradingBot bot = ServerTradingBotFactory.loadFromTag(tradingBotTag);
            if(bot != null)
                setTradingBot(bot);
        }

        return success;
    }
}
*/