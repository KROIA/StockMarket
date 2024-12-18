package net.kroia.stockmarket.networking;


import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.client_sender.request.*;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateBankTerminalBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

public class ModMessages {
    private static SimpleChannel INSTANCE;

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MODID, "messages"))
                .networkProtocolVersion(1)
                .clientAcceptedVersions((status, version) -> true)
                .serverAcceptedVersions((status, version) -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(SyncPricePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncPricePacket::new)
                .encoder(SyncPricePacket::toBytes)
                .consumerMainThread(SyncPricePacket::handle)
                .add();

        net.messageBuilder(RequestPricePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestPricePacket::new)
                .encoder(RequestPricePacket::toBytes)
                .consumerMainThread(RequestPricePacket::handle)
                .add();

        net.messageBuilder(RequestOrderPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestOrderPacket::new)
                .encoder(RequestOrderPacket::toBytes)
                .consumerMainThread(RequestOrderPacket::handle)
                .add();

        net.messageBuilder(UpdateSubscribeMarketEventsPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateSubscribeMarketEventsPacket::new)
                .encoder(UpdateSubscribeMarketEventsPacket::toBytes)
                .consumerMainThread(UpdateSubscribeMarketEventsPacket::handle)
                .add();

        net.messageBuilder(RequestTradeItemsPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestTradeItemsPacket::new)
                .encoder(RequestTradeItemsPacket::toBytes)
                .consumerMainThread(RequestTradeItemsPacket::handle)
                .add();

        net.messageBuilder(RequestOrderCancelPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestOrderCancelPacket::new)
                .encoder(RequestOrderCancelPacket::toBytes)
                .consumerMainThread(RequestOrderCancelPacket::handle)
                .add();

        net.messageBuilder(UpdateStockMarketBlockEntityPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateStockMarketBlockEntityPacket::new)
                .encoder(UpdateStockMarketBlockEntityPacket::toBytes)
                .consumerMainThread(UpdateStockMarketBlockEntityPacket::handle)
                .add();

        net.messageBuilder(RequestBankDataPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestBankDataPacket::new)
                .encoder(RequestBankDataPacket::toBytes)
                .consumerMainThread(RequestBankDataPacket::handle)
                .add();

        net.messageBuilder(UpdateBankTerminalBlockEntityPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateBankTerminalBlockEntityPacket::new)
                .encoder(UpdateBankTerminalBlockEntityPacket::toBytes)
                .consumerMainThread(UpdateBankTerminalBlockEntityPacket::handle)
                .add();

        net.messageBuilder(SyncStockMarketBlockEntityPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncStockMarketBlockEntityPacket::new)
                .encoder(SyncStockMarketBlockEntityPacket::toBytes)
                .consumerMainThread(SyncStockMarketBlockEntityPacket::handle)
                .add();

        net.messageBuilder(SyncTradeItemsPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncTradeItemsPacket::new)
                .encoder(SyncTradeItemsPacket::toBytes)
                .consumerMainThread(SyncTradeItemsPacket::handle)
                .add();

        net.messageBuilder(SyncOrderPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncOrderPacket::new)
                .encoder(SyncOrderPacket::toBytes)
                .consumerMainThread(SyncOrderPacket::handle)
                .add();

        net.messageBuilder(SyncBankDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncBankDataPacket::new)
                .encoder(SyncBankDataPacket::toBytes)
                .consumerMainThread(SyncBankDataPacket::handle)
                .add();

    }

    public static <MSG> void sendToServer(MSG message) {
        try{
            INSTANCE.send(message, PacketDistributor.SERVER.noArg());
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("Failed to send message to server_sender: " + e.getMessage());
        }
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        try{
            INSTANCE.send(message, PacketDistributor.PLAYER.with(player));
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("Failed to send message to player: " + e.getMessage());
        }
    }
}