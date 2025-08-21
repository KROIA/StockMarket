package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class UpdateOrderHistoryPacket extends StockMarketNetworkPacket {

    public TradingPair pair;
    public Order order;

    public UpdateOrderHistoryPacket(TradingPair pair, Order order){
        super();
        this.pair = pair;
        this.order = order;
    }

    public UpdateOrderHistoryPacket(FriendlyByteBuf buf){
        decode(buf);
    }


    @Override
    public void decode(FriendlyByteBuf buf) {
        pair.decode(buf);
        order.decode(buf);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        pair.encode(buf);
        order.encode(buf);

    }



    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_MARKET_MANAGER.logNewOrderToHistory(pair, order);
    }
}
