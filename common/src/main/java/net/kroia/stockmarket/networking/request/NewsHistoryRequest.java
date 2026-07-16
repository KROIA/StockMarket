package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches one page of the master-side news history, newest-first
 * (NewsEventSystem plan §3, task T-073).
 * <p>
 * <b>Not admin-gated:</b> any player may read published news — that is the whole point
 * of the newspaper. Slaves hold no news state; the request is auto-routed slave→master
 * via {@link #needsRoutingToMaster()}, so slave players work transparently.
 * <p>
 * <b>Pagination contract</b> (delegates verbatim to {@link NewsHistory#getPage(long, int)}):
 * <ul>
 *   <li>The response is ordered <b>newest-first</b> (descending {@code newsUid}).</li>
 *   <li>Only records with {@code newsUid} strictly less than {@code beforeUid} are returned.</li>
 *   <li>{@code beforeUid <= 0} (or {@link Long#MAX_VALUE}) means "start at the newest record"
 *       — use {@code 0} for the first page.</li>
 *   <li>{@code maxResults} is clamped server-side to
 *       [{@value NewsHistory#MIN_PAGE_SIZE}, {@value NewsHistory#MAX_PAGE_SIZE}].</li>
 *   <li><b>Next page:</b> pass the {@code newsUid} of the <i>last</i> (oldest) record of the
 *       previous response as the new {@code beforeUid}. An empty response means the end
 *       was reached.</li>
 * </ul>
 * <p>
 * <b>Client entry point (T-074 codes against this):</b> exactly like
 * {@link TransactionHistoryRequest}, use the registered instance on the networking
 * manager — from any screen/element with the client backend:
 * <pre>{@code
 * BACKEND_INSTANCES.NETWORKING.NEWS_HISTORY_REQUEST
 *         .sendRequestToServer(new NewsHistoryRequest.InputData(beforeUid, maxResults))
 *         .thenAccept(response -> updateEntries(response.records()));
 * }</pre>
 * The returned future completes on the client; merge the page with the live
 * {@code ClientNewsCache} records by {@code newsUid} (the cache de-duplicates too).
 */
public class NewsHistoryRequest extends StockMarketGenericRequest<NewsHistoryRequest.InputData, NewsHistoryRequest.OutputData> {

    /**
     * @param beforeUid  exclusive upper {@code newsUid} cursor; {@code <= 0} for the first
     *                   (newest) page, otherwise the uid of the last record of the previous page
     * @param maxResults requested page size (clamped server-side, see class Javadoc)
     */
    public record InputData(long beforeUid, int maxResults) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.beforeUid,
                ByteBufCodecs.VAR_INT, p -> p.maxResults,
                InputData::new
        );
    }

    /**
     * @param records one page of published news, sorted newest-first (descending
     *                {@code newsUid}); empty when the history is exhausted (or on error)
     */
    public record OutputData(List<NewsRecord> records) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.listStreamCodec(NewsRecord.STREAM_CODEC), p -> p.records,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return NewsHistoryRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(List.of());
    }

    /**
     * Answers the page from the master's {@link NewsHistory}
     * ({@code DataManager.getNewsHistory()}). Runs on the master server main thread;
     * the page lookup is an in-memory deque walk, so it completes synchronously.
     */
    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(getDefaultResponse());

        // The DataManager (and with it the news history) only exists on the master.
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.DATA_MANAGER == null)
            return CompletableFuture.completedFuture(getDefaultResponse());

        List<NewsRecord> page = BACKEND_INSTANCES.DATA_MANAGER.getNewsHistory()
                .getPage(input.beforeUid(), input.maxResults());
        return CompletableFuture.completedFuture(new OutputData(page));
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
