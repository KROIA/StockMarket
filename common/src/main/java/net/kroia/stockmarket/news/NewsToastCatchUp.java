package net.kroia.stockmarket.news;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure decision logic for the <b>join-time news toast catch-up</b> (T-077, plan §6.6).
 * <p>
 * Players who join (or slave players who connect) after an event published never
 * received the live {@code NewsPublishedPacket}. For players who <b>opted in</b> to
 * news toasts ({@code PlayerPreferences.isNewsToastEnabled()}, default off), the
 * client fetches the first {@code NewsHistoryRequest} page right after the player
 * preferences arrive from the server and shows a toast for the most recent headlines
 * — see {@code StockMarketClientHooks.runNewsToastCatchUp} for the orchestration.
 * Non-opted-in players get <b>nothing at all</b> (no fetch, no chat, no sound —
 * user decision, same as the live toast).
 * <p>
 * This class is deliberately free of any Minecraft client imports so the selection
 * rules are unit-testable in the {@code sm_news_client_cache} in-game test suite.
 */
public final class NewsToastCatchUp {

    /**
     * Catch-up window in milliseconds ({@code 10 minutes}): on join, only records
     * <b>strictly younger</b> than this window are considered "recent enough" to be
     * re-announced as a toast. Older news is still available in the newspaper screen,
     * just not pushed. Intentionally a client-side constant and <b>not</b> a mod
     * setting (T-077 user decision — no new settings for polish behavior).
     */
    public static final long CATCH_UP_WINDOW_MS = 10L * 60L * 1000L;

    /**
     * Maximum number of catch-up toasts shown per join ({@value}): even if a busy
     * server published many events within {@link #CATCH_UP_WINDOW_MS}, only the
     * newest few are toasted so the vanilla toast rail is never flooded on join.
     * The full feed is always one newspaper-open away.
     */
    public static final int MAX_CATCH_UP_TOASTS = 3;

    /**
     * Size of the single history page fetched for the catch-up ({@value} records).
     * Small on purpose: the window/cap selection needs only the newest handful of
     * records, and the same page doubles as the initial {@code ClientNewsCache}
     * seed so the newspaper opens instantly populated.
     */
    public static final int CATCH_UP_FETCH_SIZE = 10;

    private NewsToastCatchUp() {
        // static utility — not instantiable
    }

    /**
     * Selects which fetched history records should be shown as catch-up toasts.
     * <p>
     * Rules (in order):
     * <ul>
     *   <li>If the player did not opt in ({@code toastOptedIn == false}) the result is
     *       always empty — non-opted-in players get no notification of any kind.</li>
     *   <li>Only records <b>strictly younger</b> than {@code windowMs} are eligible,
     *       i.e. {@code nowEpochMs - timestampEpochMs < windowMs}. The age comparison
     *       uses the <b>client clock</b> against the master's publish epoch — an
     *       accepted approximation (identical to the T-074 "LIVE" badge); records
     *       stamped in the (client's) future due to clock skew count as brand new.</li>
     *   <li>At most {@code maxToasts} records are selected, preferring the
     *       <b>newest</b> eligible ones.</li>
     * </ul>
     *
     * @param newestFirst  one history page as returned by {@code NewsHistoryRequest},
     *                     sorted newest-first (descending {@code newsUid}); null/empty
     *                     yields an empty result; null elements are skipped
     * @param nowEpochMs   the client's current epoch milliseconds
     *                     ({@code System.currentTimeMillis()})
     * @param windowMs     the catch-up window (production: {@link #CATCH_UP_WINDOW_MS});
     *                     values {@code <= 0} yield an empty result
     * @param maxToasts    the toast cap (production: {@link #MAX_CATCH_UP_TOASTS});
     *                     values {@code <= 0} yield an empty result
     * @param toastOptedIn the player's <b>fetched</b> {@code newsToastEnabled} flag —
     *                     never the pre-fetch default
     * @return the records to toast, <b>oldest-first</b> (so the caller can add them to
     *         the toast rail in chronological order and the newest headline is queued
     *         last); empty, never null
     */
    public static @NotNull List<NewsRecord> selectCatchUpToasts(@Nullable List<NewsRecord> newestFirst,
                                                                long nowEpochMs,
                                                                long windowMs,
                                                                int maxToasts,
                                                                boolean toastOptedIn) {
        if (!toastOptedIn || newestFirst == null || newestFirst.isEmpty()
                || maxToasts <= 0 || windowMs <= 0) {
            return List.of();
        }

        List<NewsRecord> selected = new ArrayList<>(Math.min(maxToasts, newestFirst.size()));
        for (NewsRecord record : newestFirst) {
            if (record == null) continue;
            long age = nowEpochMs - record.getTimestampEpochMs();
            if (age < 0) age = 0; // clock skew: future-stamped records count as brand new
            if (age >= windowMs) continue; // "strictly younger than the window"
            selected.add(record);
            if (selected.size() >= maxToasts) break; // input is newest-first — cap keeps the newest
        }

        Collections.reverse(selected); // oldest-first: the newest headline is toasted last
        return selected;
    }
}
