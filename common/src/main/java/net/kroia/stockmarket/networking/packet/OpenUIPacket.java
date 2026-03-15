package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class OpenUIPacket extends StockMarketNetworkPacket {

    public static final Type<OpenUIPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "open_ui_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUIPacket> STREAM_CODEC = StreamCodec.composite(
            ExtraCodecUtils.enumStreamCodec(OpenUIPacket.GUIType.class), p -> p.guiType,
            OpenUIPacket::new
    );
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum GUIType
    {
        DEVELOPMENT,
    }


    private final GUIType  guiType;

    public OpenUIPacket(GUIType guiType)
    {
        this.guiType = guiType;
    }


    public static void sendToClient(ServerPlayer player, GUIType guiType)
    {
        OpenUIPacket packet = new OpenUIPacket(guiType);
        packet.sendToClient(player);
    }





    @Override
    protected void handleOnClient(NetworkManager.PacketContext context)
    {
        StockMarketClientHooks.openGUI(guiType);
    }
}
