package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SyncBotTargetPricePacket extends NetworkPacket {

    int targetPrice;
    public SyncBotTargetPricePacket(int targetPrice) {
        super();
        this.targetPrice = targetPrice;
    }
    public SyncBotTargetPricePacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer receiver, int targetPrice)
    {
        SyncBotTargetPricePacket packet = new SyncBotTargetPricePacket(targetPrice);
        StockMarketNetworking.sendToClient(receiver, packet);
    }

    public int getTargetPrice() {
        return targetPrice;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(targetPrice);

    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        targetPrice = buf.readInt();

    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
