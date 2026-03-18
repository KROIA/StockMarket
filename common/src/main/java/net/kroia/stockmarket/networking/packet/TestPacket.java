package net.kroia.stockmarket.networking.packet;

import dev.architectury.networking.NetworkManager;
import io.netty.channel.ChannelHandlerContext;
import net.kroia.modutilities.networking.server_server.ForwardPacketContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public class TestPacket extends StockMarketNetworkPacket {
    public static final Type<TestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "test_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, p -> p.message,
            TestPacket::new
    );
    @Override
    public Type<? extends StockMarketNetworkPacket> type() {
        return TYPE;
    }

    private final String message;

    public TestPacket(String message)
    {
        this.message = message;
    }

    public static void sendToServer(String message)
    {
        new TestPacket(message).sendToServer();
    }

    @Override
    protected boolean needsRoutingToMaster()
    {
        return true;
    }

    @Override
    protected void handleOnServer(NetworkManager.PacketContext context)
    {
        info("[Server] Handling TestPacket with message: " + message + " without redirecting");
    }

    @Override
    protected void handleOnMaster(ForwardPacketContext context)
    {
        info("[Master] Handling redirected TestPacket with message: " + message);
    }








    @Override
    protected void handleOnSlave(ForwardPacketContext context)
    {
        info("[Slave] Handling redirected TestPacket with message: " + message);
    }
}
