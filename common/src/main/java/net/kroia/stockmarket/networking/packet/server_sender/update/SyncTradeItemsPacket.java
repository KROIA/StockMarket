package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
public class SyncTradeItemsPacket extends NetworkPacket {
    private ArrayList<SyncPricePacket> syncPricePackets;

    public SyncTradeItemsPacket() {
        super();
        syncPricePackets = new ArrayList<>();
    }
    public SyncTradeItemsPacket(ArrayList<SyncPricePacket> syncPricePackets) {
        super();
        this.syncPricePackets = new ArrayList<>();
        this.syncPricePackets.addAll(syncPricePackets);
    }

    public SyncTradeItemsPacket(FriendlyByteBuf buf) {
        super(buf);
    }


    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(syncPricePackets.size());
        for (SyncPricePacket syncPricePacket : syncPricePackets) {
             syncPricePacket.toBytes(buf);
        }
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if(syncPricePackets == null)
            syncPricePackets = new ArrayList<>();
        if(size == 0)
            return;
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

        StockMarketNetworking.sendToClient(player, new SyncTradeItemsPacket(syncPricePackets));
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
