package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.modutilities.networking.PacketHandler;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class PlayerJoinSyncPacket extends StockMarketNetworkPacket {
    public static final Type<PlayerJoinSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModUtilitiesMod.MOD_ID, "player_join_sync_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerJoinSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ClientSettings.STREAM_CODEC, p -> p.settings,
            PlayerJoinSyncPacket::new
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }



    public final ClientSettings settings;

    public PlayerJoinSyncPacket(ClientSettings settings)
    {
        this.settings = settings;
    }

    public static void send(ServerPlayer player)
    {
        ClientSettings clientSettings = BACKEND_SERVER_INSTANCES.SERVER_SETTINGS.getClientSettings();
        PlayerJoinSyncPacket syncPacket = new PlayerJoinSyncPacket(clientSettings);
        syncPacket.sendToClient(player);
    }



    public static final PacketHandler<PlayerJoinSyncPacket> HANDLER = new PacketHandler<>(){

        @Override
        public void handleServer(PlayerJoinSyncPacket packet, NetworkManager.PacketContext context) {

        }

        @Override
        public void handleClient(PlayerJoinSyncPacket packet, NetworkManager.PacketContext context) {
            BACKEND_CLIENT_INSTANCES.SETTINGS.loadFrom(packet.settings);
        }
    };
}
