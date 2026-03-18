package net.kroia.stockmarket.networking;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.modutilities.networking.client_server.ClientServerPacketManager;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.modutilities.networking.server_server.ForwardPacketHandler;
import net.kroia.modutilities.networking.server_server.ServerServerPacketRegistry;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.networking.packet.PlayerJoinSyncPacket;
import net.kroia.stockmarket.networking.packet.TestPacket;
import net.kroia.stockmarket.networking.request.ActiveOrdersRequest;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.MarketsRequest;
import net.kroia.stockmarket.networking.stream.MarketPriceStream;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class StockMarketNetworking extends ClientServerPacketManager {
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        StockMarketGenericStream.setBackend(backend);
        StockMarketGenericRequest.setBackend(backend);
        StockMarketNetworkPacket.setBackend(backend);
    }
    public static void setBackend(StockMarketModBackend.CommonInstances common) {
        StockMarketNetworkPacket.setBackend(common);
    }
    public static void setBackend(StockMarketModBackend.ClientInstances common) {
        StockMarketNetworkPacket.setBackend(common);
    }

    public final MarketPriceStream MARKET_PRICE_STREAM = (MarketPriceStream) StreamSystem.register(new MarketPriceStream());

    public final MarketPriceHistoryRequest MARKET_PRICE_HISTORY_REQUEST = (MarketPriceHistoryRequest) AsynchronousRequestResponseSystem.register(new MarketPriceHistoryRequest());
    public final MarketsRequest MARKETS_REQUEST = (MarketsRequest) AsynchronousRequestResponseSystem.register(new MarketsRequest());
    public final CreateOrderRequest CREATE_ORDER_REQUEST = (CreateOrderRequest) AsynchronousRequestResponseSystem.register(new CreateOrderRequest());
    public final ActiveOrdersRequest ACTIVE_ORDERS_REQUEST = (ActiveOrdersRequest) AsynchronousRequestResponseSystem.register(new ActiveOrdersRequest());


    public StockMarketNetworking()
    {
        super(BankSystemMod.MOD_ID, "stockmarket_channel");

        setupClientReceiverPackets();
        setupServerReceiverPackets();

        this.setupARRS(); // Setup the Asynchronous Request Response System (ARRS)
        this.setupStreamSystem();
    }

    @Override
    public void setupClientReceiverPackets() {
        registerS2C(PlayerJoinSyncPacket.TYPE, PlayerJoinSyncPacket.STREAM_CODEC);
        registerS2C(OpenUIPacket.TYPE, OpenUIPacket.STREAM_CODEC);
    }

    @Override
    public void setupServerReceiverPackets() {

        registerC2S(TestPacket.TYPE, TestPacket.STREAM_CODEC);

    }



    // Helper function to reduce code size for registration
    public <T extends StockMarketNetworkPacket> void registerS2C(CustomPacketPayload.Type<T> packetType, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
    {
        // All packets use the same handler
        registerS2C(packetType, streamCodec, StockMarketNetworkPacket.HANDLER);
        ServerServerPacketRegistry.register(packetType, streamCodec, StockMarketNetworkPacket.HANDLER);
    }
    public <T extends StockMarketNetworkPacket> void registerC2S(CustomPacketPayload.Type<T> packetType, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)
    {
        // All packets use the same handler
        registerC2S(packetType, streamCodec, StockMarketNetworkPacket.HANDLER);
        ServerServerPacketRegistry.register(packetType, streamCodec, StockMarketNetworkPacket.HANDLER);
    }
}
