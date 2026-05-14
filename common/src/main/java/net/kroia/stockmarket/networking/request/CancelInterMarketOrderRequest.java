package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Request to cancel a pending inter-market order identified by its unique group ID.
 * Returns true if the order was found and canceled, false otherwise.
 */
public class CancelInterMarketOrderRequest extends StockMarketGenericRequest<UUID, Boolean> {

    @Override
    public String getRequestTypeID() {
        return CancelInterMarketOrderRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(UUID interMarketGroupID, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(false);

        ServerMarketManager marketManager = (ServerMarketManager) getServerMarketManager();
        if (marketManager == null)
            return CompletableFuture.completedFuture(false);

        boolean canceled = marketManager.cancelInterMarketOrder(interMarketGroupID, playerSender);
        return CompletableFuture.completedFuture(canceled);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, UUID input) {
        UUIDUtil.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public UUID decodeInput(RegistryFriendlyByteBuf buf) {
        return UUIDUtil.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
