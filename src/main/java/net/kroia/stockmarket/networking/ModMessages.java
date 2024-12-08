package net.kroia.stockmarket.networking;


import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static SimpleChannel INSTANCE;

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(StockMarketMod.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(UpdatePricePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(UpdatePricePacket::new)
                .encoder(UpdatePricePacket::toBytes)
                .consumerMainThread(UpdatePricePacket::handle)
                .add();

        net.messageBuilder(RequestPricePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestPricePacket::new)
                .encoder(RequestPricePacket::toBytes)
                .consumerMainThread(RequestPricePacket::handle)
                .add();

        net.messageBuilder(TransactionRequestPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(TransactionRequestPacket::new)
                .encoder(TransactionRequestPacket::toBytes)
                .consumerMainThread(TransactionRequestPacket::handle)
                .add();

        net.messageBuilder(SubscribeMarketEventsPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SubscribeMarketEventsPacket::new)
                .encoder(SubscribeMarketEventsPacket::toBytes)
                .consumerMainThread(SubscribeMarketEventsPacket::handle)
                .add();

        net.messageBuilder(RequestTradeItemsPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestTradeItemsPacket::new)
                .encoder(RequestTradeItemsPacket::toBytes)
                .consumerMainThread(RequestTradeItemsPacket::handle)
                .add();

        net.messageBuilder(UpdateTradeItemsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(UpdateTradeItemsPacket::new)
                .encoder(UpdateTradeItemsPacket::toBytes)
                .consumerMainThread(UpdateTradeItemsPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}