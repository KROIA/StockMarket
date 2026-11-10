package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.OrderHistoryRequest;
import net.kroia.stockmarket.networking.request.TransactionHistoryRequest;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.InFlightGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
}
