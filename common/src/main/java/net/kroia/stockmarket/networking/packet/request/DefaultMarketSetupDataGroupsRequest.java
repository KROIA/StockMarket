package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class DefaultMarketSetupDataGroupsRequest extends StockMarketGenericRequest<Boolean, List<MarketFactory.DefaultMarketSetupDataGroup>> {
    @Override
    public String getRequestTypeID() {
        return DefaultMarketSetupDataGroupsRequest.class.getSimpleName();
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupDataGroup> handleOnClient(Boolean input) {
        return null;
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupDataGroup> handleOnServer(Boolean input, ServerPlayer sender) {
        if(!playerIsAdmin(sender)) {
            return List.of(); // Only allow admins to request default market setup data groups
        }

        List<MarketFactory.DefaultMarketSetupDataGroup> groups = MarketFactory.DefaultMarketSetupDataGroup.loadAll();
        if (groups.isEmpty()) {
            return List.of(); // No groups found, return null
        }
        return groups; // Return the list of DefaultMarketSetupDataGroups
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Boolean input) {
        buf.writeBoolean(input != null && input); // Encode the input as a boolean
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<MarketFactory.DefaultMarketSetupDataGroup> output) {
        buf.writeBoolean(output != null && !output.isEmpty()); // Write if the output is not null and not empty
        if (output != null && !output.isEmpty()) {
            buf.writeVarInt(output.size()); // Write the size of the list
            for (MarketFactory.DefaultMarketSetupDataGroup group : output) {
                group.encode(buf); // Encode each DefaultMarketSetupDataGroup in the list
            }
        }
    }

    @Override
    public Boolean decodeInput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Read a boolean value from the buffer
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupDataGroup> decodeOutput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null; // If the output is not present, return null
        }
        List<MarketFactory.DefaultMarketSetupDataGroup> groups = new ArrayList<>();
        int size = buf.readVarInt(); // Read the size of the list
        for (int i = 0; i < size; i++) {
            MarketFactory.DefaultMarketSetupDataGroup group = MarketFactory.DefaultMarketSetupDataGroup.decode(buf); // Decode each DefaultMarketSetupDataGroup
            groups.add(group); // Add the decoded group to the list
        }
        return groups;
    }
}
