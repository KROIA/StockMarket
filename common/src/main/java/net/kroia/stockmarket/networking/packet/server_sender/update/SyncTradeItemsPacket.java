package net.kroia.stockmarket.networking.packet.server_sender.update;


import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairListData;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class SyncTradeItemsPacket extends StockMarketNetworkPacket {

    public final TradingPairListData data;
    public SyncTradeItemsPacket(List<TradingPair> tradingPairs) {
        super();
        data = new TradingPairListData(tradingPairs);
    }

    public SyncTradeItemsPacket(FriendlyByteBuf buf) {
        data = TradingPairListData.decode(buf);
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        data.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        //data.decode(buf);
    }


    public static void sendPacket(ServerPlayer player)
    {
        List<TradingPair> tradingPairs = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getTradingPairs();
        SyncTradeItemsPacket packet = new SyncTradeItemsPacket(tradingPairs);
        packet.sendToClient(player);
    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_MARKET_MANAGER.handlePacket(this);
    }
}
