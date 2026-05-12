package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Request to clear the personal order history for the sending player on a specific market.
 * <p>
 * Input:  {@link ItemID} identifying which market's history to clear
 * Output: {@code Boolean} indicating success
 */
public class ClearOrderHistoryRequest extends StockMarketGenericRequest<ItemID, Boolean> {

    @Override
    public String getRequestTypeID() {
        return ClearOrderHistoryRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(ItemID input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null) return CompletableFuture.completedFuture(false);

        UUIDFilter userFilter = new UUIDFilter(playerSender);
        EqualityFilter marketFilter = new EqualityFilter(input.getShort());
        return BACKEND_INSTANCES.ORDER_RECORD_MANAGER.removeHistory(
                Optional.empty(),
                Optional.empty(),
                Optional.of(userFilter),
                Optional.of(marketFilter)
        ).thenApply(unused -> true)
         .exceptionally(ex -> false);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, ItemID input) {
        ItemID.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public ItemID decodeInput(RegistryFriendlyByteBuf buf) {
        return ItemID.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
