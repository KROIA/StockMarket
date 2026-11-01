package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinSyncPacket extends StockMarketNetworkPacket {
    public static final Type<PlayerJoinSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "player_join_sync_packet")); // mc<=1.21.10
    //public static final Type<PlayerJoinSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "player_join_sync_packet")); // mc>=1.21.11

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerJoinSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ClientSettings.STREAM_CODEC, p -> p.settings,
            PlayerJoinSyncPacket::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
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


    @Override
    protected void handleOnClient(NetworkManager.PacketContext context)
    {
        BACKEND_CLIENT_INSTANCES.SETTINGS.loadFrom(settings);
    }
}
