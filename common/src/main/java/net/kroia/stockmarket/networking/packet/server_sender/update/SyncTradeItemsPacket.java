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
        //super(buf);
        data = TradingPairListData.decode(buf);
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        data.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        data.decode(buf);
    }


    public static void sendPacket(ServerPlayer player)
    {
        //StockMarketMod.LOGGER.info("[SERVER] Sending SyncTradeItemsPacket");
        /*Map<ItemID, ServerTradeItem> serverTradeItemMap = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradeItems();
        ArrayList<SyncPricePacket> syncPricePackets = new ArrayList<>();
        ArrayList<ItemID> stillAvailableItems = new ArrayList<>();
        for(var entry : serverTradeItemMap.entrySet())
        {
            stillAvailableItems.add(entry.getKey());
        }

        SyncTradeItemsPacket packet = new SyncTradeItemsPacket(stillAvailableItems, BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getCurrencyItem());
        packet.command = Command.STILL_AVAILABLE;
        packet.sendToClient(player);
        for(var entry : serverTradeItemMap.entrySet())
        {
            ServerTradeItem item = entry.getValue();
            new SyncTradeItemsPacket(new SyncPricePacket(item.getItemID(), player.getUUID()), BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getCurrencyItem()).sendToClient(player);
        }*/

        List<TradingPair> tradingPairs = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getTradingPairs();
        SyncTradeItemsPacket packet = new SyncTradeItemsPacket(tradingPairs);
        packet.sendToClient(player);
    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_MARKET_MANAGER.handlePacket(this);
    }
}
