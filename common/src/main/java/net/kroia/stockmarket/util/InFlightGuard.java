package net.kroia.stockmarket.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps a polled ARRS request single-flight: a new request is sent only after the previous one has
 * completed. The guard is released on every outcome, including a failed future (timeout or connection
 * lost), so one lost answer never stops the poll.
 * <p>
 * Not thread-safe: use it from one thread. Client-side ARRS futures complete on the client main thread
 * (answers in the packet handler, timeouts in the client tick), the same thread the GUI polls from.
 */
public final class InFlightGuard {
    private boolean inFlight = false;

    /**
     * @return true while the request started by the last successful {@link #send} call has not completed
     */
    public boolean isInFlight() {
        return inFlight;
    }

    /**
     * Sends a request unless the previous one is still in flight.
     *
     * @param request  sends the request and returns its future; only called when nothing is in flight
     * @param onResult receives the answer; not called when the future fails or completes with {@code null}
     * @param <T>      the response type
     * @return true if the request was sent, false if it was skipped because one is still in flight
     */
    public <T> boolean send(Supplier<CompletableFuture<T>> request, Consumer<T> onResult) {
        if (inFlight)
            return false;
        CompletableFuture<T> future = request.get();
        // Set after request.get(): if sending throws, the guard stays free.
        inFlight = true;
        future.whenComplete((result, error) -> {
            inFlight = false;
            if (error == null && result != null)
                onResult.accept(result);
        });
        return true;
    }
}
