package net.kroia.stockmarket.networking.packet.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.clientdata.BotSettingsData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class BotSettingsRequest extends StockMarketGenericRequest<ItemID, BotSettingsData> {
    @Override
    public String getRequestTypeID() {
        return BotSettingsRequest.class.getName();
    }

    @Override
    public BotSettingsData handleOnClient(ItemID input) {
        return null;
    }

    @Override
    public BotSettingsData handleOnServer(ItemID input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getBotSettingsData(input);
        }
        return null; // If the player is not an admin, return null
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, ItemID input) {

    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, BotSettingsData output) {

    }

    @Override
    public ItemID decodeInput(FriendlyByteBuf buf) {
        return null;
    }

    @Override
    public BotSettingsData decodeOutput(FriendlyByteBuf buf) {
        return null;
    }
}
