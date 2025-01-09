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
        CHANNEL.register(SyncPricePacket.class, SyncPricePacket::toBytes, SyncPricePacket::new, SyncPricePacket::receive);
        CHANNEL.register(SyncStockMarketBlockEntityPacket.class, SyncStockMarketBlockEntityPacket::toBytes, SyncStockMarketBlockEntityPacket::new, SyncStockMarketBlockEntityPacket::receive);
        CHANNEL.register(SyncTradeItemsPacket.class, SyncTradeItemsPacket::toBytes, SyncTradeItemsPacket::new, SyncTradeItemsPacket::receive);
        CHANNEL.register(SyncOrderPacket.class, SyncOrderPacket::toBytes, SyncOrderPacket::new, SyncOrderPacket::receive);
        CHANNEL.register(OpenScreenPacket.class, OpenScreenPacket::toBytes, OpenScreenPacket::new, OpenScreenPacket::receive);
        CHANNEL.register(SyncBotSettingsPacket.class, SyncBotSettingsPacket::toBytes, SyncBotSettingsPacket::new, SyncBotSettingsPacket::receive);

    }
    public static void setupServerReceiverPackets()
    {



        CHANNEL.register(RequestPricePacket.class, RequestPricePacket::toBytes, RequestPricePacket::new, RequestPricePacket::receive);
        CHANNEL.register(RequestOrderPacket.class, RequestOrderPacket::toBytes, RequestOrderPacket::new, RequestOrderPacket::receive);
        CHANNEL.register(UpdateSubscribeMarketEventsPacket.class, UpdateSubscribeMarketEventsPacket::toBytes, UpdateSubscribeMarketEventsPacket::new, UpdateSubscribeMarketEventsPacket::receive);
        CHANNEL.register(RequestTradeItemsPacket.class, RequestTradeItemsPacket::toBytes, RequestTradeItemsPacket::new, RequestTradeItemsPacket::receive);
        CHANNEL.register(RequestOrderCancelPacket.class, RequestOrderCancelPacket::toBytes, RequestOrderCancelPacket::new, RequestOrderCancelPacket::receive);
        CHANNEL.register(UpdateStockMarketBlockEntityPacket.class, UpdateStockMarketBlockEntityPacket::toBytes, UpdateStockMarketBlockEntityPacket::new, UpdateStockMarketBlockEntityPacket::receive);
        CHANNEL.register(RequestBotSettingsPacket.class, RequestBotSettingsPacket::toBytes, RequestBotSettingsPacket::new, RequestBotSettingsPacket::receive);
        CHANNEL.register(UpdateBotSettingsPacket.class, UpdateBotSettingsPacket::toBytes, UpdateBotSettingsPacket::new, UpdateBotSettingsPacket::receive);
        CHANNEL.register(RequestOrderChangePacket.class, RequestOrderChangePacket::toBytes, RequestOrderChangePacket::new, RequestOrderChangePacket::receive);


    }


    public static void sendToServer(INetworkPacket packet) {
        CHANNEL.sendToServer(packet);
    }
    public static void sendToClient(ServerPlayer receiver, INetworkPacket packet) {
        CHANNEL.sendToPlayer(receiver, packet);
    }
}