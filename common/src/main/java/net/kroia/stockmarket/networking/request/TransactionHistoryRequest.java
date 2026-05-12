package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches all completed trades for a specific market (all players).
 * Provides market-wide transaction transparency.
 */
public class TransactionHistoryRequest extends StockMarketGenericRequest<TransactionHistoryRequest.InputData, TransactionHistoryRequest.OutputData> {

    /**
     * @param itemID     the market to query transactions for
     * @param maxResults maximum number of records to return
     */
    public record InputData(ItemID itemID, int maxResults) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.itemID,
                ByteBufCodecs.VAR_INT, p -> p.maxResults,
                InputData::new
        );
    }

    /**
     * @param records the transaction history records for the requested market, sorted by time descending
     */
    public record OutputData(List<OrderRecordStruct> records) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.listStreamCodec(OrderRecordStruct.STREAM_CODEC), p -> p.records,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return TransactionHistoryRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(List.of());
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(getDefaultResponse());

        // Filter by market itemID (stored as short in the database)
        EqualityFilter marketFilter = new EqualityFilter(input.itemID().getShort());

        return BACKEND_INSTANCES.ORDER_RECORD_MANAGER.getHistory(
                Optional.empty(),               // no date filter
                Optional.empty(),               // no account filter
                Optional.empty(),               // no user filter (all players)
                Optional.of(marketFilter)       // filter by market
        ).thenApply(records -> {
            // Sort by time descending (most recent first), limit results
            records.sort(Comparator.comparingLong(OrderRecordStruct::time).reversed());
            if (records.size() > input.maxResults()) {
                records = records.subList(0, input.maxResults());
            }
            return new OutputData(records);
        });
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
