package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBankDataPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestBankDataPacket extends NetworkPacket {

    public RequestBankDataPacket() {
        super();

    }
    public RequestBankDataPacket(FriendlyByteBuf buf) {
        super(buf);

    }

    public static void sendRequest()
    {
        RequestBankDataPacket packet = new RequestBankDataPacket();
        ModMessages.sendToServer(packet);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {

    }
    @Override
    public void fromBytes(FriendlyByteBuf buf) {

    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        SyncBankDataPacket.sendPacket(sender);
    }
}
