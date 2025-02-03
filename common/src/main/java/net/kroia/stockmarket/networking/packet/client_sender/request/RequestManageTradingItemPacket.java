package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestManageTradingItemPacket extends NetworkPacketC2S {

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_MANAGE_TRADING_ITEM;
    }

    public enum Mode
    {
        ADD_NEW_ITEM,
        REMOVE_ITEM
    }

    Mode mode;

    int startPrice;
    String itemID;


    private RequestManageTradingItemPacket(String itemID, int startPrice, Mode mode)
    {
        super();
        this.itemID = itemID;
        this.startPrice = startPrice;
        this.mode = mode;
    }

    public RequestManageTradingItemPacket(RegistryFriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendRequest(String itemID, int startPrice, Mode mode)
    {
        RequestManageTradingItemPacket packet = new RequestManageTradingItemPacket(itemID, startPrice, mode);
        packet.sendToServer();
    }
    public static void sendRequestAllowNewTradingItem(String itemID, int startPrice)
    {
        sendRequest(itemID, startPrice, Mode.ADD_NEW_ITEM);
    }
    public static void sendRequestRemoveTradingItem(String itemID)
    {
        sendRequest(itemID, 0, Mode.REMOVE_ITEM);
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUtf(itemID);
        buf.writeInt(startPrice);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        mode = buf.readEnum(Mode.class);
        itemID = buf.readUtf();
        startPrice = buf.readInt();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        switch(mode)
        {
            case ADD_NEW_ITEM:
                if (ServerMarket.hasItem(itemID)) {
                    PlayerUtilities.printToClientConsole(sender, StockMarketTextMessages.getMarketplaceAlreadyExistingMessage(itemID));
                }
                else {
                    if (ServerMarket.addTradeItemIfNotExists(itemID, startPrice)) {
                        // Notify all serverPlayers
                        PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceCreatedMessage(itemID));
                    } else {
                        PlayerUtilities.printToClientConsole(sender, StockMarketTextMessages.getMarketplaceIsNotAllowedMessage(itemID));
                    }
                }
                break;
            case REMOVE_ITEM:
                if (ServerMarket.hasItem(itemID)) {
                    ServerMarket.removeTradingItem(itemID);
                    // Notify all serverPlayers
                    PlayerUtilities.printToClientConsole(StockMarketTextMessages.getMarketplaceDeletedMessage(itemID));
                } else {
                    PlayerUtilities.printToClientConsole(sender, StockMarketTextMessages.getMarketplaceNotExistingMessage(itemID));
                }
                break;
        }
        SyncTradeItemsPacket.sendPacket(sender);
    }
}
