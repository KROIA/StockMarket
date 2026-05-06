package net.kroia.stockmarket.data.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class UUIDFilter implements DataFilter{
    UUID id;

    @Override
    public String getClause(String columnName) {
        return "";
    }

    @Override
    public int bindParameters(PreparedStatement stmt, int paramIndex) throws SQLException {
        // Single-column variant is not used; no parameters to bind
        return paramIndex;
    }

    public String getClause(String part1, String part2){
        return part1 + " = ? AND " + part2 + " = ?";
    }

    /**
     * Binds UUID most/least significant bits for the two-column clause variant.
     * @param stmt the PreparedStatement to bind values to
     * @param paramIndex the 1-based parameter index to start binding at
     * @return the next available parameter index after binding
     * @throws SQLException if binding fails
     */
    public int bindUUIDParameters(PreparedStatement stmt, int paramIndex) throws SQLException {
        stmt.setLong(paramIndex, id.getMostSignificantBits());
        stmt.setLong(paramIndex + 1, id.getLeastSignificantBits());
        return paramIndex + 2;
    }

    public UUIDFilter(UUID id){
        this.id = id;
    }
}
