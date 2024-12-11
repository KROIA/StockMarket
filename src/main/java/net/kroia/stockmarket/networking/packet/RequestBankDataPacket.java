package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.ClientBankManager;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.function.Supplier;

public class RequestBankDataPacket {

    public RequestBankDataPacket() {

    }
    public RequestBankDataPacket(FriendlyByteBuf buf) {

    }

    public static void sendRequest()
    {
        RequestBankDataPacket packet = new RequestBankDataPacket();
        ModMessages.sendToServer(packet);
    }

    public void toBytes(FriendlyByteBuf buf) {

    }
    public void fromBytes(FriendlyByteBuf buf) {

    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server");
            // HERE WE ARE ON THE CLIENT!

            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            ServerPlayer player = context.getSender();
            //ServerBankManager.handlePacket(player,this);
            UpdateBankDataPacket.sendPacket(player);
        });
        context.setPacketHandled(true);
    }
}
