package net.kroia.stockmarket.news;

/**
 * Seam between the NewsPlugin's scheduling logic and the delivery/persistence layers
 * (NewsEventSystem plan §3).
 * <p>
 * The NewsPlugin calls {@link #publish(NewsRecord)} exactly once per activated event,
 * at the event's <b>publish moment</b> (which — depending on the sampled announce delay —
 * may be before, at, or after the price-impact start). The plugin itself only builds the
 * {@link NewsRecord}; everything that happens with it is the publisher's job.
 * <p>
 * <b>Implementation contract for T-072 / T-073:</b> the production publisher must
 * <ol>
 *   <li>append the record to the master-side news history (T-072,
 *       {@code DataManager.saveNews/loadNews}, capped ring buffer), and</li>
 *   <li>broadcast it to all clients via {@code NewsPublishedPacket}
 *       (T-073, master → local clients + slave relay).</li>
 * </ol>
 * Install it via {@code NewsPlugin.setPublisher(...)} during master setup. Until then the
 * plugin uses a default implementation that only logs the publish at info level, so the
 * scheduler is fully functional (and testable) before the networking/storage tasks land.
 * <p>
 * Called on the server thread from within the plugin update loop — implementations must
 * not block.
 */
public interface NewsPublisher {

    /**
     * Delivers one published news record.
     *
     * @param record the fully built record (uid, texts, affected markets, impact summary)
     */
    void publish(NewsRecord record);
}
