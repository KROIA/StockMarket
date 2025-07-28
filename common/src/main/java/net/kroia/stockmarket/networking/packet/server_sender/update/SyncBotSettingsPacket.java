package net.kroia.stockmarket.networking.packet.server_sender.update;

/*
public class SyncBotSettingsPacket extends StockMarketNetworkPacket {

    ItemID itemID;
    ServerVolatilityBot.Settings settings;
    boolean botExists;
   // private UUID botUUID;

    public SyncBotSettingsPacket() {
        super();
    }
    public SyncBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer receiver, ItemID itemID)
    {
        ServerTradingBot bot = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradingBot(itemID);
        ServerVolatilityBot.Settings settings = new ServerVolatilityBot.Settings();
        //settings.enabled = false;
        SyncBotSettingsPacket packet = new SyncBotSettingsPacket();

        if(bot instanceof ServerVolatilityBot volatilityBot)
        {
            settings = (ServerVolatilityBot.Settings)volatilityBot.getSettings();
            packet.botExists = true;
        }
        packet.itemID = itemID;
        packet.settings = settings;
        //packet.botUUID = botUUID;

        packet.sendToClient(receiver);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(botExists);
        //buf.writeUUID(botUUID);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
        botExists = buf.readBoolean();
        //botUUID = buf.readUUID();
        CompoundTag tag = buf.readNbt();
        settings = new ServerVolatilityBot.Settings();
        settings.load(tag);
    }

    public ServerVolatilityBot.Settings getSettings() {
        return settings;
    }
    public ItemID getItemID() {
        return itemID;
    }

    public boolean botExists() {
        return botExists;
    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.handlePacket(this);
    }
}
*/