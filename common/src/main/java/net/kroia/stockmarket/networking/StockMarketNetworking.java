package net.kroia.stockmarket.networking;


import net.kroia.modutilities.networking.NetworkManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.client_sender.request.*;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;

public class StockMarketNetworking extends NetworkManager {

    public StockMarketNetworking()
    {
        super(StockMarketMod.MOD_ID, "stockmarket_channel");
        setupARRS();
        setupClientReceiverPackets();
        setupServerReceiverPackets();
    }

    @Override
    public void setupClientReceiverPackets()
    {
       register(SyncPricePacket.class, SyncPricePacket::encode, SyncPricePacket::new, SyncPricePacket::receive);
       register(SyncStockMarketBlockEntityPacket.class, SyncStockMarketBlockEntityPacket::encode, SyncStockMarketBlockEntityPacket::new, SyncStockMarketBlockEntityPacket::receive);
       register(SyncTradeItemsPacket.class, SyncTradeItemsPacket::encode, SyncTradeItemsPacket::new, SyncTradeItemsPacket::receive);
       register(SyncOrderPacket.class, SyncOrderPacket::encode, SyncOrderPacket::new, SyncOrderPacket::receive);
       register(OpenScreenPacket.class, OpenScreenPacket::encode, OpenScreenPacket::new, OpenScreenPacket::receive);
       register(SyncBotSettingsPacket.class, SyncBotSettingsPacket::encode, SyncBotSettingsPacket::new, SyncBotSettingsPacket::receive);
       register(SyncBotTargetPricePacket.class, SyncBotTargetPricePacket::encode, SyncBotTargetPricePacket::new, SyncBotTargetPricePacket::receive);

    }

    @Override
    public void setupServerReceiverPackets()
    {



        register(RequestPricePacket.class, RequestPricePacket::encode, RequestPricePacket::new, RequestPricePacket::receive);
        register(RequestOrderPacket.class, RequestOrderPacket::encode, RequestOrderPacket::new, RequestOrderPacket::receive);
        register(UpdateSubscribeMarketEventsPacket.class, UpdateSubscribeMarketEventsPacket::encode, UpdateSubscribeMarketEventsPacket::new, UpdateSubscribeMarketEventsPacket::receive);
        register(RequestTradeItemsPacket.class, RequestTradeItemsPacket::encode, RequestTradeItemsPacket::new, RequestTradeItemsPacket::receive);
        register(RequestOrderCancelPacket.class, RequestOrderCancelPacket::encode, RequestOrderCancelPacket::new, RequestOrderCancelPacket::receive);
        register(UpdateStockMarketBlockEntityPacket.class, UpdateStockMarketBlockEntityPacket::encode, UpdateStockMarketBlockEntityPacket::new, UpdateStockMarketBlockEntityPacket::receive);
        register(RequestBotSettingsPacket.class, RequestBotSettingsPacket::encode, RequestBotSettingsPacket::new, RequestBotSettingsPacket::receive);
        register(UpdateBotSettingsPacket.class, UpdateBotSettingsPacket::encode, UpdateBotSettingsPacket::new, UpdateBotSettingsPacket::receive);
        register(RequestOrderChangePacket.class, RequestOrderChangePacket::encode, RequestOrderChangePacket::new, RequestOrderChangePacket::receive);
        register(RequestManageTradingItemPacket.class, RequestManageTradingItemPacket::encode, RequestManageTradingItemPacket::new, RequestManageTradingItemPacket::receive);
        register(RequestBotTargetPricePacket.class, RequestBotTargetPricePacket::encode, RequestBotTargetPricePacket::new, RequestBotTargetPricePacket::receive);


    }
}