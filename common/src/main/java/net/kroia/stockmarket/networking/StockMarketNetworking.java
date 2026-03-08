package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.networking.PacketManager;
import net.kroia.modutilities.networking.arrs.AsynchronousRequestResponseSystem;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.PlayerJoinSyncPacket;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.MarketsRequest;
import net.kroia.stockmarket.networking.stream.MarketPriceStream;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;

public class StockMarketNetworking extends PacketManager {
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
    public final MarketPriceHistoryRequest MARKET_PRICE_HISTORY_REQUEST = (MarketPriceHistoryRequest) StreamSystem.register(new MarketPriceHistoryRequest());


    public final MarketsRequest MARKETS_REQUEST = (MarketsRequest) AsynchronousRequestResponseSystem.register(new MarketsRequest());


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
        registerS2C(PlayerJoinSyncPacket.TYPE, PlayerJoinSyncPacket.STREAM_CODEC, PlayerJoinSyncPacket.HANDLER);
    }

    @Override
    public void setupServerReceiverPackets() {

    }
}
