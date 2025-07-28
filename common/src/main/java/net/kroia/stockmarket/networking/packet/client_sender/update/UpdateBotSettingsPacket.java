package net.kroia.stockmarket.networking.packet.client_sender.update;

/*
public class UpdateBotSettingsPacket extends StockMarketNetworkPacket {

    ItemID itemID;
    ServerVolatilityBot.Settings settings;

    private boolean destroyBot;
    private boolean createBot;
    private boolean marketOpen;


    private UpdateBotSettingsPacket() {
        super();
    }
    public UpdateBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ItemID itemID, ServerVolatilityBot.Settings settings, boolean destroyBot, boolean createBot, boolean marketOpen)
    {
        UpdateBotSettingsPacket packet = new UpdateBotSettingsPacket();
        packet.itemID = itemID;
        packet.settings = settings;
        packet.destroyBot = destroyBot;
        packet.createBot = createBot;
        packet.marketOpen = marketOpen;
        packet.sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(destroyBot);
        buf.writeBoolean(createBot);
        buf.writeBoolean(marketOpen);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
        destroyBot = buf.readBoolean();
        createBot = buf.readBoolean();
        marketOpen = buf.readBoolean();
        settings = new ServerVolatilityBot.Settings();
        settings.load(buf.readNbt());

    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            // Apply bot settings
            ServerTradingBot bot = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradingBot(itemID);
            if(bot == null && createBot)
            {
                bot = new ServerVolatilityBot();
                bot.setSettings(settings);
                BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setTradingBot(itemID, bot);
            }
            else if(bot != null && destroyBot)
            {
                BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeTradingBot(itemID);
            }
            else {
                if (bot instanceof ServerVolatilityBot volatilityBot) {
                    volatilityBot.setSettings(settings);
                }
            }
            BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setMarketOpen(itemID, marketOpen);
            SyncBotSettingsPacket.sendPacket(sender, itemID);
        }
    }
}
*/