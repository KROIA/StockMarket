package net.kroia.stockmarket.data.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface DataFilter {

    /**
     * Returns a SQL WHERE clause fragment with '?' placeholders for parameterized queries.
     * @param columnName the column name to filter on
     * @return SQL clause fragment, e.g. "columnName = ?"
     */
    String getClause(String columnName);

    /**
     * Binds this filter's parameter values to a PreparedStatement starting at the given index.
     * @param stmt the PreparedStatement to bind values to
     * @param paramIndex the 1-based parameter index to start binding at
     * @return the next available parameter index after binding
     * @throws SQLException if binding fails
     */
    int bindParameters(PreparedStatement stmt, int paramIndex) throws SQLException;

}
