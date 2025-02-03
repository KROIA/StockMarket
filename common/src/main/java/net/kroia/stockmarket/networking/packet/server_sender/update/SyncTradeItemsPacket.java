package net.kroia.stockmarket.networking.packet.server_sender.update;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketS2C;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
public class SyncTradeItemsPacket extends NetworkPacketS2C {
    private ArrayList<SyncPricePacket> syncPricePackets;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.SYNC_TRADE_ITEMS;
    }

    public SyncTradeItemsPacket() {
        super();
        syncPricePackets = new ArrayList<>();
    }



    public SyncTradeItemsPacket(ArrayList<SyncPricePacket> syncPricePackets) {
        super();
        this.syncPricePackets = new ArrayList<>();
        this.syncPricePackets.addAll(syncPricePackets);
    }

    public SyncTradeItemsPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }


    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeInt(syncPricePackets.size());
        for (SyncPricePacket syncPricePacket : syncPricePackets) {
             syncPricePacket.toBytes(buf);
        }
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        if(size == 0)
            return;
        if(syncPricePackets == null)
            syncPricePackets = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            this.syncPricePackets.add(new SyncPricePacket(buf));
        }
    }

    public ArrayList<SyncPricePacket> getUpdatePricePackets() {
        return syncPricePackets;
    }

    public static void sendPacket(ServerPlayer player)
    {
        //StockMarketMod.LOGGER.info("[SERVER] Sending SyncTradeItemsPacket");
        Map<String, ServerTradeItem> serverTradeItemMap = ServerMarket.getTradeItems();
        ArrayList<SyncPricePacket> syncPricePackets = new ArrayList<>();
        int i=0;
        for(var entry : serverTradeItemMap.entrySet())
        {
            ServerTradeItem item = entry.getValue();
            syncPricePackets.add(new SyncPricePacket(item.getItemID(), player.getUUID()));
            i++;
        }

        new SyncTradeItemsPacket(syncPricePackets).sendTo(player);
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
