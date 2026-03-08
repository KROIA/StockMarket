package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.networking.PacketManager;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.stream.MarketPriceStream;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;

public class StockMarketNetworking extends PacketManager {
    public static void setBackend(StockMarketModBackend.Instances backend) {
        StockMarketGenericStream.setBackend(backend);
        StockMarketNetworkPacket.setBackend(backend);
        StockMarketGenericRequest.setBackend(backend);
    }

    public static final MarketPriceStream MARKET_PRICE_STREAM = (MarketPriceStream) StreamSystem.register(new MarketPriceStream());
    public static final MarketPriceHistoryRequest MARKET_PRICE_HISTORY_REQUEST = (MarketPriceHistoryRequest) StreamSystem.register(new MarketPriceHistoryRequest());


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

    }

    @Override
    public void setupServerReceiverPackets() {

    }
}
