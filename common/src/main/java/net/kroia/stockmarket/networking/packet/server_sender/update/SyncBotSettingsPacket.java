package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class SyncBotSettingsPacket extends NetworkPacket {

    String itemID;
    ServerVolatilityBot.Settings settings;
    boolean botExists;
    private UUID botUUID;

    public SyncBotSettingsPacket() {
        super();
    }
    public SyncBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer receiver, String itemID, UUID botUUID)
    {
        ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
        ServerVolatilityBot.Settings settings = new ServerVolatilityBot.Settings();
        settings.enabled = false;
        SyncBotSettingsPacket packet = new SyncBotSettingsPacket();

        if(bot instanceof ServerVolatilityBot volatilityBot)
        {
            settings = (ServerVolatilityBot.Settings)volatilityBot.getSettings();
            packet.botExists = true;
        }
        packet.itemID = itemID;
        packet.settings = settings;
        packet.botUUID = botUUID;

        StockMarketNetworking.sendToClient(receiver, packet);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
        buf.writeBoolean(botExists);
        buf.writeUUID(botUUID);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        itemID = buf.readUtf();
        botExists = buf.readBoolean();
        botUUID = buf.readUUID();
        CompoundTag tag = buf.readNbt();
        settings = new ServerVolatilityBot.Settings();
        settings.load(tag);
    }

    public ServerVolatilityBot.Settings getSettings() {
        return settings;
    }
    public String getItemID() {
        return itemID;
    }
    public UUID getBotUUID() {
        return botUUID;
    }
    public boolean botExists() {
        return botExists;
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
