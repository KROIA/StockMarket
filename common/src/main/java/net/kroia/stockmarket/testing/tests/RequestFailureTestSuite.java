package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.OrderHistoryRequest;
import net.kroia.stockmarket.networking.request.TransactionHistoryRequest;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.InFlightGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T-227: StockMarket's side of ARRS request failures ({@code RequestManager.ENABLE_REQUEST_FAILURES}, set by
 * BankSystem): an unanswered request fails after {@code getTimeoutMs()} with
 * {@link RequestOutcomeUnknownException} instead of staying pending forever.
 * Pure logic, no game, no network.
 */
public class RequestFailureTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.UTIL;
    }

    @Override
    public boolean needsGame() {
        return false; // passes offline in gradlew :common:test
    }

    @Override
    public void registerTests() {
        addTest("history_requests_wait_120s", this::test_historyRequestsWait120s);
        addTest("in_flight_guard_released_after_failure", this::test_inFlightGuardReleasedAfterFailure);
        addTest("in_flight_guard_delivers_answer", this::test_inFlightGuardDeliversAnswer);
        addTest("in_flight_guard_free_when_send_throws", this::test_inFlightGuardFreeWhenSendThrows);
        addTest("async_market_passes_failure_on", this::test_asyncMarketPassesFailureOn);
        addTest("async_market_decodes_answer", this::test_asyncMarketDecodesAnswer);
        addTest("async_market_manager_passes_failure_on", this::test_asyncMarketManagerPassesFailureOn);
    }

    /** The exception a completed future failed with, unwrapped from CompletionException; null if it did not fail. */
    private static Throwable failureOf(CompletableFuture<?> future) {
        if (!future.isDone())
            return null;
        Throwable error = future.handle((value, ex) -> ex).join();
        return (error instanceof CompletionException && error.getCause() != null) ? error.getCause() : error;
    }

    /** The three requests that wait on the master's DB executor get 120 s instead of the 30 s default. */
    private TestResult test_historyRequestsWait120s() {
        TestResult r = assertEquals("MarketPriceHistoryRequest timeout", 120_000L, new MarketPriceHistoryRequest().getTimeoutMs());
        if (!r.passed()) return r;
        r = assertEquals("OrderHistoryRequest timeout", 120_000L, new OrderHistoryRequest().getTimeoutMs());
        if (!r.passed()) return r;
        r = assertEquals("TransactionHistoryRequest timeout", 120_000L, new TransactionHistoryRequest().getTimeoutMs());
        if (!r.passed()) return r;
        return pass("price, order and transaction history wait 120 s");
    }

    /** A request that times out releases the guard (the poll goes on) and never reaches onResult. */
    private TestResult test_inFlightGuardReleasedAfterFailure() {
        InFlightGuard guard = new InFlightGuard();
        CompletableFuture<String> pending = new CompletableFuture<>();
        AtomicInteger sends = new AtomicInteger();
        List<String> results = new ArrayList<>();

        TestResult r = assertTrue("first send goes out", guard.send(() -> { sends.incrementAndGet(); return pending; }, results::add));
        if (!r.passed()) return r;
        r = assertTrue("in flight while unanswered", guard.isInFlight());
        if (!r.passed()) return r;
        r = assertFalse("second send is skipped", guard.send(() -> { sends.incrementAndGet(); return pending; }, results::add));
        if (!r.passed()) return r;
        r = assertEquals("skipped send never calls the request", 1, sends.get());
        if (!r.passed()) return r;

        pending.completeExceptionally(new RequestOutcomeUnknownException("timeout"));
        r = assertFalse("released after the timeout", guard.isInFlight());
        if (!r.passed()) return r;
        r = assertTrue("a failure is not passed on as a result", results.isEmpty());
        if (!r.passed()) return r;
        r = assertTrue("next poll goes out", guard.send(() -> { sends.incrementAndGet(); return new CompletableFuture<String>(); }, results::add));
        if (!r.passed()) return r;
        return assertEquals("two requests sent in total", 2, sends.get());
    }

    /** An answer is delivered once and releases the guard; a null answer is not delivered. */
    private TestResult test_inFlightGuardDeliversAnswer() {
        InFlightGuard guard = new InFlightGuard();
        List<String> results = new ArrayList<>();
        CompletableFuture<String> pending = new CompletableFuture<>();
        guard.send(() -> pending, results::add);
        pending.complete("records");
        TestResult r = assertEquals("answer delivered", List.of("records"), results);
        if (!r.passed()) return r;
        r = assertFalse("released after the answer", guard.isInFlight());
        if (!r.passed()) return r;

        guard.send(() -> CompletableFuture.<String>completedFuture(null), results::add);
        r = assertEquals("null answer not delivered", 1, results.size());
        if (!r.passed()) return r;
        return assertFalse("released after a null answer", guard.isInFlight());
    }

    /** If sending throws (ARRS not set up), the guard must not stay taken. */
    private TestResult test_inFlightGuardFreeWhenSendThrows() {
        InFlightGuard guard = new InFlightGuard();
        try {
            guard.send(() -> { throw new IllegalStateException("RequestManager is not set"); }, ignored -> {});
            return fail("the send exception should reach the caller");
        } catch (IllegalStateException expected) {
            return assertFalse("guard free after a throwing send", guard.isInFlight());
        }
    }

    /**
     * A failed ARRS request fails the AsyncMarket future (it used to stay pending forever) and is sent once:
     * nothing retries a mutating call whose outcome is unknown.
     */
    private TestResult test_asyncMarketPassesFailureOn() {
        AtomicInteger sends = new AtomicInteger();
        AsyncMarket market = AsyncMarket.createForTesting(new ItemID((short) 1), input -> {
            sends.incrementAndGet();
            return CompletableFuture.failedFuture(new RequestOutcomeUnknownException("timeout"));
        });
        CompletableFuture<Boolean> future = market.setMaintenanceAsync(true);
        TestResult r = assertTrue("future failed with the request's exception",
                failureOf(future) instanceof RequestOutcomeUnknownException);
        if (!r.passed()) return r;
        return assertEquals("sent once, no retry", 1, sends.get());
    }

    /** The answer is decoded; an unreachable master (nothing sent) gives the fallback value. */
    private TestResult test_asyncMarketDecodesAnswer() {
        AsyncMarket answering = AsyncMarket.createForTesting(new ItemID((short) 1),
                input -> CompletableFuture.completedFuture(AsyncMarket.OutputData.of(input.function, 1234L)));
        TestResult r = assertEquals("decoded answer", 1234L, answering.getCurrentTimeAsync().join());
        if (!r.passed()) return r;
        AsyncMarket unreachable = AsyncMarket.createForTesting(new ItemID((short) 1), input -> null);
        return assertEquals("fallback when unreachable", 0L, unreachable.getCurrentTimeAsync().join());
    }

    /** Same for the market manager, including getMarketAsync, which maps the answer to a market. */
    private TestResult test_asyncMarketManagerPassesFailureOn() {
        ItemID marketID = new ItemID((short) 1);
        AsyncMarketManager failing = AsyncMarketManager.createForTesting(
                input -> CompletableFuture.failedFuture(new RequestOutcomeUnknownException("timeout")));
        TestResult r = assertTrue("deleteMarketAsync failed",
                failureOf(failing.deleteMarketAsync(marketID)) instanceof RequestOutcomeUnknownException);
        if (!r.passed()) return r;
        r = assertTrue("getMarketAsync failed",
                failureOf(failing.getMarketAsync(marketID)) instanceof RequestOutcomeUnknownException);
        if (!r.passed()) return r;

        AsyncMarketManager answering = AsyncMarketManager.createForTesting(
                input -> CompletableFuture.completedFuture(AsyncMarketManager.OutputData.of(input.function, true)));
        return assertNotNull("getMarketAsync maps true to a market", answering.getMarketAsync(marketID).join());
    }
}
