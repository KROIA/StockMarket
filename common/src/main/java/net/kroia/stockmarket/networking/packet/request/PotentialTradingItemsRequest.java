package net.kroia.stockmarket.networking.packet.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class PotentialTradingItemsRequest extends StockMarketGenericRequest<String, List<ItemID>> {
    @Override
    public String getRequestTypeID() {
        return PotentialTradingItemsRequest.class.getName();
    }

    @Override
    public List<ItemID> handleOnClient(String input) {
        return null;
    }

    @Override
    public List<ItemID> handleOnServer(String input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            // Fetch the list of tradable items based on the input string
            return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getPotentialTradingItems(input);
        }
        return List.of(); // Return an empty list if the player is not an admin
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, String input) {
        buf.writeUtf(input); // Encode the input string
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<ItemID> output) {
        if (output != null) {
            buf.writeVarInt(output.size());
            for (ItemID itemID : output) {
                buf.writeItem(itemID.getStack()); // Encode each ItemID as an ItemStack
            }
        } else {
            buf.writeVarInt(0); // Write 0 if the output is null
        }
    }

    @Override
    public String decodeInput(FriendlyByteBuf buf) {
        return buf.readUtf(); // Decode the input string
    }

    @Override
    public List<ItemID> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readVarInt(); // Read the size of the list
        if (size <= 0) {
            return List.of(); // Return an empty list if size is 0 or negative
        }
        List<ItemID> itemList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            itemList.add(new ItemID(buf.readItem())); // Decode each ItemID from the ItemStack
        }
        return itemList;
    }
}
