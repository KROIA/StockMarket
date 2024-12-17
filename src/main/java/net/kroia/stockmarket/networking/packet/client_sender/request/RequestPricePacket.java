package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class RequestPricePacket extends NetworkPacket {


    private String itemID;
    public RequestPricePacket(String itemID) {
        super();
        this.itemID = itemID;
    }

    public RequestPricePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
    }
    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.itemID = buf.readUtf();
    }

    public static void generateRequest(String itemID) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestPricePacket for item "+itemID);
        ModMessages.sendToServer(new RequestPricePacket(itemID));
    }

    public String getItemID() {
        return itemID;
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        ServerMarket.handlePacket(sender, this);
    }
}
