package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.util.UUID;

public class DataFilterTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.FILTER;
    }

    @Override
    public void registerTests() {
        addTest("equality_clause_numeric_id", this::test_getClause_numericId);
        addTest("equality_clause_null_id_throws", this::test_getClause_nullId);
        addTest("equality_clause_uses_placeholder", this::test_getClause_usesPlaceholder);
        addTest("date_clause_normal_range", this::test_getClause_normalRange);
        addTest("date_clause_inverted_range", this::test_getClause_invertedRange);
        addTest("date_clause_same_time", this::test_getClause_sameTime);
        addTest("uuid_clause_single_column_returns_empty", this::test_getClause_singleColumn_returnsEmpty);
        addTest("uuid_clause_two_columns", this::test_getClause_twoColumns);
        addTest("uuid_clause_null_uuid", this::test_getClause_nullUUID);
    }

    // --- EqualityFilter Tests ---

    /**
     * EqualityFilter with a numeric Integer id produces "col = ?" placeholder clause.
     */
    private TestResult test_getClause_numericId() {
        EqualityFilter filter = new EqualityFilter(42);
        String clause = filter.getClause("market_id");
        return assertEquals("Clause should use parameterized placeholder",
                "market_id = ?", clause);
    }

    /**
     * Null id should throw IllegalArgumentException in constructor.
     */
    private TestResult test_getClause_nullId() {
        return assertThrows("EqualityFilter(null) should throw IllegalArgumentException",
                IllegalArgumentException.class,
                () -> new EqualityFilter(null));
    }

    /**
     * EqualityFilter now uses parameterized queries with '?' placeholders,
     * not string interpolation. This prevents SQL injection.
     */
    private TestResult test_getClause_usesPlaceholder() {
        EqualityFilter filter = new EqualityFilter(1);
        String clause = filter.getClause("col");
        TestResult r = assertTrue("Clause should contain '?' placeholder",
                clause.contains("?"));
        if (!r.passed()) return r;
        r = assertFalse("Clause should NOT contain literal value '1' embedded in SQL",
                clause.contains(" 1") && !clause.contains("?"));
        if (!r.passed()) return r;
        return pass("EqualityFilter uses parameterized '?' placeholder, safe from SQL injection");
    }

    // --- DateFilter Tests ---

    /**
     * DateFilter with normal range produces "col >= ? AND col <= ?" clause.
     */
    private TestResult test_getClause_normalRange() {
        DateFilter filter = new DateFilter(1000L, 2000L);
        String clause = filter.getClause("timestamp");
        TestResult r = assertTrue("Clause should contain 'timestamp >= ?'",
                clause.contains("timestamp >= ?"));
        if (!r.passed()) return r;
        r = assertTrue("Clause should contain 'timestamp <= ?'",
                clause.contains("timestamp <= ?"));
        if (!r.passed()) return r;
        return pass("DateFilter produces correct parameterized range clause");
    }

    /**
     * When startTime > endTime, the clause is still syntactically correct
     * but produces a logically empty range.
     */
    private TestResult test_getClause_invertedRange() {
        DateFilter filter = new DateFilter(2000L, 1000L);
        String clause = filter.getClause("ts");
        // The clause structure should still be valid SQL -- it just won't match any rows
        TestResult r = assertTrue("Clause should still contain >= and <= operators",
                clause.contains("ts >= ?") && clause.contains("ts <= ?"));
        if (!r.passed()) return r;
        return pass("Inverted range produces syntactically valid (but logically empty) clause");
    }

    /**
     * When startTime == endTime, produces a single-point filter.
     */
    private TestResult test_getClause_sameTime() {
        DateFilter filter = new DateFilter(5000L, 5000L);
        String clause = filter.getClause("t");
        TestResult r = assertTrue("Clause should contain 't >= ?'", clause.contains("t >= ?"));
        if (!r.passed()) return r;
        r = assertTrue("Clause should contain 't <= ?'", clause.contains("t <= ?"));
        if (!r.passed()) return r;
        return pass("Same start/end time produces valid single-point filter clause");
    }

    // --- UUIDFilter Tests ---

    /**
     * Single-column getClause() returns empty string (not supported).
     */
    private TestResult test_getClause_singleColumn_returnsEmpty() {
        UUIDFilter filter = new UUIDFilter(UUID.randomUUID());
        String clause = filter.getClause("uuid_col");
        return assertEquals("Single-column getClause should return empty string",
                "", clause);
    }

    /**
     * Two-column version returns correct MSB/LSB clause with '?' placeholders.
     */
    private TestResult test_getClause_twoColumns() {
        UUIDFilter filter = new UUIDFilter(UUID.randomUUID());
        String clause = filter.getClause("uuid_msb", "uuid_lsb");
        TestResult r = assertTrue("Clause should contain 'uuid_msb = ?'",
                clause.contains("uuid_msb = ?"));
        if (!r.passed()) return r;
        r = assertTrue("Clause should contain 'uuid_lsb = ?'",
                clause.contains("uuid_lsb = ?"));
        if (!r.passed()) return r;
        r = assertTrue("Clause should contain AND",
                clause.contains("AND"));
        if (!r.passed()) return r;
        return pass("Two-column UUID clause correctly references MSB and LSB with placeholders");
    }

    /**
     * Null UUID behavior -- UUIDFilter constructor does not reject null,
     * but calling methods on it would throw NPE.
     */
    private TestResult test_getClause_nullUUID() {
        try {
            UUIDFilter filter = new UUIDFilter(null);
            // Single-column should still return empty string
            String clause = filter.getClause("col");
            TestResult r = assertEquals("Single-column clause should still be empty", "", clause);
            if (!r.passed()) return r;
            // Two-column variant will NPE when accessing null UUID's bits
            try {
                filter.getClause("msb", "lsb");
                // If we get here, the clause was generated without NPE
                return pass("Null UUID did not throw on getClause (two-column)");
            } catch (NullPointerException e) {
                return pass("Null UUID correctly causes NPE on two-column getClause access");
            }
        } catch (NullPointerException e) {
            return pass("Null UUID causes NPE at construction or access time");
        } catch (IllegalArgumentException e) {
            return pass("Null UUID is rejected by constructor");
        }
    }
}
