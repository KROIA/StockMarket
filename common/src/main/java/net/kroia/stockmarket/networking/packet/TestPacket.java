package net.kroia.stockmarket.networking.packet;

import com.sun.jna.platform.unix.solaris.LibKstat;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.interserver.child.HubConnector;
import net.kroia.stockmarket.networking.interserver.payload.PacketForwardPayload;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.core.RegistryAccess;
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
    protected void handleOnServer(NetworkManager.PacketContext context)
    {
        HubConnector hubConnector = HubConnector.get();
        if(hubConnector != null) {
            // This is a child server
            String targetServer = "";
            RegistryAccess reg = context.registryAccess();
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), reg);
            STREAM_CODEC.encode(buf, this);
            hubConnector.sendToHub(new PacketForwardPayload(
                    context.getPlayer().getUUID(),
                    hubConnector.getServerId(),
                    targetServer,
                    type().id(),
                    buf.array()
            ));
        }
        else
        {
            // This is the hub server

        }
    }

    @Override
    protected void handleOnServer(ChannelHandlerContext context)
    {
        info("Handling redirected TestPacket with message: " + message);
    }

}
