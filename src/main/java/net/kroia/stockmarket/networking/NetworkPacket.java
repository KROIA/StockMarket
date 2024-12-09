package net.kroia.stockmarket.networking;

import net.kroia.stockmarket.market.client.ClientMarket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public interface NetworkPacket {


    NetworkPacket fromBytes(FriendlyByteBuf buf);
    void toBytes(FriendlyByteBuf buf);

    default void handle(Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            // HERE WE ARE ON THE CLIENT!
            context.setPacketHandled(this.handleClient());
            return;
        }

        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            context.setPacketHandled(this.handleServer(context.getSender()));
        });
        //context.setPacketHandled(false);
    }

    default boolean handleClient(){
        return false;
    }
    default boolean handleServer(ServerPlayer sender){
        return false;
    }

}

