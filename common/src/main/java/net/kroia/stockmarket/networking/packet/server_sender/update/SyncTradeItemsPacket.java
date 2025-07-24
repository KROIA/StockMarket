package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Map;
public class SyncTradeItemsPacket extends NetworkPacket {

    public enum Command
    {
        ADD,
        STILL_AVAILABLE
    }
    //private ArrayList<SyncPricePacket> syncPricePackets;
    private SyncPricePacket syncPricePacket;
    private Command command;
    private ArrayList<ItemID> stillAvailableItems;
    private ItemID baseCurrencyItemID;
    public SyncTradeItemsPacket(ArrayList<ItemID> stillAvailableItems, ItemID baseCurrencyItemID) {
        super();
        this.command = Command.STILL_AVAILABLE;
        this.stillAvailableItems = stillAvailableItems;
        this.baseCurrencyItemID = baseCurrencyItemID;

        //syncPricePackets = new ArrayList<>();
    }
    /*public SyncTradeItemsPacket(ArrayList<SyncPricePacket> syncPricePackets) {
        super();
        this.syncPricePackets = new ArrayList<>();
        this.syncPricePackets.addAll(syncPricePackets);
    }*/
    public SyncTradeItemsPacket(SyncPricePacket syncPricePacket, ItemID baseCurrencyItemID) {
        super();
        this.syncPricePacket = syncPricePacket;
        this.command = Command.ADD;
        this.baseCurrencyItemID = baseCurrencyItemID;
    }

    public SyncTradeItemsPacket(FriendlyByteBuf buf) {
        super(buf);
    }


    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(command);
        if(command == Command.ADD)
        {
            syncPricePacket.toBytes(buf);
        }
        if(command == Command.STILL_AVAILABLE)
        {
            buf.writeInt(stillAvailableItems.size());
            for (ItemID itemID : stillAvailableItems) {
                buf.writeItem(itemID.getStack());
            }
        }
        buf.writeItem(baseCurrencyItemID.getStack());
       /* buf.writeInt(syncPricePackets.size());
        for (SyncPricePacket syncPricePacket : syncPricePackets) {
             syncPricePacket.toBytes(buf);
        }*/
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {

        command = buf.readEnum(Command.class);
        if(command == Command.ADD)
        {
            syncPricePacket = new SyncPricePacket(buf);
        }
        if(command == Command.STILL_AVAILABLE)
        {
            int size = buf.readInt();
            stillAvailableItems = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                stillAvailableItems.add(new ItemID(buf.readItem()));
            }
        }
        baseCurrencyItemID = new ItemID(buf.readItem());

        /*int size = buf.readInt();
        if(syncPricePackets == null)
            syncPricePackets = new ArrayList<>();
        if(size == 0)
            return;
        for (int i = 0; i < size; i++) {
            this.syncPricePackets.add(new SyncPricePacket(buf));
        }*/
    }

    /*public ArrayList<SyncPricePacket> getUpdatePricePackets() {
        return syncPricePackets;
    }*/

    public SyncPricePacket getSyncPricePacket() {
        return syncPricePacket;
    }
    public ArrayList<ItemID> getStillAvailableItems() {
        return stillAvailableItems;
    }
    public Command getCommand() {
        return command;
    }
    public ItemID getBaseCurrencyItemID() {
        return baseCurrencyItemID;
    }

    public static void sendPacket(ServerPlayer player)
    {
        //StockMarketMod.LOGGER.info("[SERVER] Sending SyncTradeItemsPacket");
        Map<ItemID, ServerTradeItem> serverTradeItemMap = ServerMarket.getTradeItems();
        ArrayList<SyncPricePacket> syncPricePackets = new ArrayList<>();
        ArrayList<ItemID> stillAvailableItems = new ArrayList<>();
        for(var entry : serverTradeItemMap.entrySet())
        {
            stillAvailableItems.add(entry.getKey());
        }

        SyncTradeItemsPacket packet = new SyncTradeItemsPacket(stillAvailableItems, ServerMarket.getCurrencyItem());
        packet.command = Command.STILL_AVAILABLE;
        StockMarketNetworking.sendToClient(player, packet);
        for(var entry : serverTradeItemMap.entrySet())
        {
            ServerTradeItem item = entry.getValue();
            StockMarketNetworking.sendToClient(player, new SyncTradeItemsPacket(new SyncPricePacket(item.getItemID(), player.getUUID()), ServerMarket.getCurrencyItem()));
        }
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
