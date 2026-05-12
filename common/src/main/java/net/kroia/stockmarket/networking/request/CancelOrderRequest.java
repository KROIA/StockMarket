package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Request to cancel a single pending order in the orderbook.
 * The order is identified by its item market, executor UUID, timestamp, type, start price, and target volume.
 * Returns true if the order was found and cancelled, false otherwise.
 */
public class CancelOrderRequest extends StockMarketGenericRequest<CancelOrderRequest.InputData, Boolean> {

    /**
     * Input data identifying the order to cancel.
     *
     * @param itemID       the market item ID the order belongs to
     * @param time         the timestamp when the order was placed
     * @param typeOrdinal  the ordinal of {@link Order.Type} (e.g. LIMIT=1)
     * @param startPrice   the price at which the order was placed
     * @param targetVolume the target volume of the order
     */
    public record InputData(ItemID itemID, long time, int typeOrdinal, long startPrice, long targetVolume) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.itemID,
                ByteBufCodecs.VAR_LONG, p -> p.time,
                ByteBufCodecs.VAR_INT, p -> p.typeOrdinal,
                ByteBufCodecs.VAR_LONG, p -> p.startPrice,
                ByteBufCodecs.VAR_LONG, p -> p.targetVolume,
                InputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return CancelOrderRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(false);

        IServerMarket market = getServerMarketManager().getMarket(input.itemID);
        if (market == null)
            return CompletableFuture.completedFuture(false);

        // Validate typeOrdinal bounds before accessing the enum array
        Order.Type[] types = Order.Type.values();
        if (input.typeOrdinal < 0 || input.typeOrdinal >= types.length)
            return CompletableFuture.completedFuture(false);

        Order.Type type = types[input.typeOrdinal];
        boolean cancelled = market.cancelOrder(playerSender, input.time, type, input.startPrice, input.targetVolume);
        return CompletableFuture.completedFuture(cancelled);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
