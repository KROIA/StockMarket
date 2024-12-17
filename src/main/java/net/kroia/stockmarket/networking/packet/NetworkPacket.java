package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.banking.bank.ClientBankManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class NetworkPacket {

    public NetworkPacket()
    {

    }
    public NetworkPacket(FriendlyByteBuf buf)
    {
        this.fromBytes(buf);
    }


    public void toBytes(FriendlyByteBuf buf) {

    }

    public void fromBytes(FriendlyByteBuf buf)
    {

    }

    protected void handleOnServer(ServerPlayer sender)
    {

    }
    protected void handleOnClient()
    {

    }


    // Since MC 1.20.2
    public void handle(CustomPayloadEvent.Context context)
    {
        if(context.isClientSide())
        {
            handleOnClient();
        }
        else
        {
            handleOnServer(context.getSender());
        }
        context.setPacketHandled(true);
    }

    // Before MC 1.20.2
    /*public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server_sender or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            handleOnClient();
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            handleOnServer(context.getSender());
        });
        context.setPacketHandled(true);
    }*/
}
