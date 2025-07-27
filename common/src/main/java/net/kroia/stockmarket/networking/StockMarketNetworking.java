package net.kroia.stockmarket.networking;


import dev.architectury.networking.NetworkChannel;
import net.kroia.modutilities.networking.INetworkPacket;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.client_sender.request.*;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class StockMarketNetworking {

    public static final NetworkChannel CHANNEL = createChannel();

    private static NetworkChannel createChannel()
    {
        NetworkChannel chanel = NetworkChannel.create(new ResourceLocation(StockMarketMod.MOD_ID, "networking_channel"));
        return chanel;
    }

    public static void setupClientReceiverPackets()
    {
        CHANNEL.register(SyncPricePacket.class, SyncPricePacket::encode, SyncPricePacket::new, SyncPricePacket::receive);
        CHANNEL.register(SyncStockMarketBlockEntityPacket.class, SyncStockMarketBlockEntityPacket::encode, SyncStockMarketBlockEntityPacket::new, SyncStockMarketBlockEntityPacket::receive);
        CHANNEL.register(SyncTradeItemsPacket.class, SyncTradeItemsPacket::encode, SyncTradeItemsPacket::new, SyncTradeItemsPacket::receive);
        CHANNEL.register(SyncOrderPacket.class, SyncOrderPacket::encode, SyncOrderPacket::new, SyncOrderPacket::receive);
        CHANNEL.register(OpenScreenPacket.class, OpenScreenPacket::encode, OpenScreenPacket::new, OpenScreenPacket::receive);
        CHANNEL.register(SyncBotSettingsPacket.class, SyncBotSettingsPacket::encode, SyncBotSettingsPacket::new, SyncBotSettingsPacket::receive);
        CHANNEL.register(SyncBotTargetPricePacket.class, SyncBotTargetPricePacket::encode, SyncBotTargetPricePacket::new, SyncBotTargetPricePacket::receive);

    }
    public static void setupServerReceiverPackets()
    {



        CHANNEL.register(RequestPricePacket.class, RequestPricePacket::encode, RequestPricePacket::new, RequestPricePacket::receive);
        CHANNEL.register(RequestOrderPacket.class, RequestOrderPacket::encode, RequestOrderPacket::new, RequestOrderPacket::receive);
        CHANNEL.register(UpdateSubscribeMarketEventsPacket.class, UpdateSubscribeMarketEventsPacket::encode, UpdateSubscribeMarketEventsPacket::new, UpdateSubscribeMarketEventsPacket::receive);
        CHANNEL.register(RequestTradeItemsPacket.class, RequestTradeItemsPacket::encode, RequestTradeItemsPacket::new, RequestTradeItemsPacket::receive);
        CHANNEL.register(RequestOrderCancelPacket.class, RequestOrderCancelPacket::encode, RequestOrderCancelPacket::new, RequestOrderCancelPacket::receive);
        CHANNEL.register(UpdateStockMarketBlockEntityPacket.class, UpdateStockMarketBlockEntityPacket::encode, UpdateStockMarketBlockEntityPacket::new, UpdateStockMarketBlockEntityPacket::receive);
        CHANNEL.register(RequestBotSettingsPacket.class, RequestBotSettingsPacket::encode, RequestBotSettingsPacket::new, RequestBotSettingsPacket::receive);
        CHANNEL.register(UpdateBotSettingsPacket.class, UpdateBotSettingsPacket::encode, UpdateBotSettingsPacket::new, UpdateBotSettingsPacket::receive);
        CHANNEL.register(RequestOrderChangePacket.class, RequestOrderChangePacket::encode, RequestOrderChangePacket::new, RequestOrderChangePacket::receive);
        CHANNEL.register(RequestManageTradingItemPacket.class, RequestManageTradingItemPacket::encode, RequestManageTradingItemPacket::new, RequestManageTradingItemPacket::receive);
        CHANNEL.register(RequestBotTargetPricePacket.class, RequestBotTargetPricePacket::encode, RequestBotTargetPricePacket::new, RequestBotTargetPricePacket::receive);


    }


    public static void sendToServer(INetworkPacket packet) {
        CHANNEL.sendToServer(packet);
    }
    public static void sendToClient(ServerPlayer receiver, INetworkPacket packet) {
        try {
            CHANNEL.sendToPlayer(receiver, packet);
        } catch (Exception e) {
            StockMarketMod.logError("Failed to send packet to player: " + receiver.getName().getString() + "\n" + e.toString());
        }
    }
}