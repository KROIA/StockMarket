package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.networking.PacketManager;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.modutilities.sandbox.SineStream;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.streams.MarketPriceStream;

public class StockMarketNetworking extends PacketManager {
    public static void setBackend(StockMarketModBackend.Instances backend) {
        StockMarketGenericStream.setBackend(backend);
    }

    public static final MarketPriceStream MARKET_PRICE_STREAM = (MarketPriceStream) StreamSystem.register(new MarketPriceStream());


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
