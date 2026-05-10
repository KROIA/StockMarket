package net.kroia.stockmarket.data.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EqualityFilter implements DataFilter{
    private final Number id;

    @Override
    public String getClause(String columnName) {
        return columnName + " = ?";
    }

    @Override
    public int bindParameters(PreparedStatement stmt, int paramIndex) throws SQLException {
        if (id instanceof Long || id instanceof Integer) {
            stmt.setLong(paramIndex, id.longValue());
        } else if (id instanceof Short) {
            stmt.setShort(paramIndex, id.shortValue());
        } else if (id instanceof Double || id instanceof Float) {
            stmt.setDouble(paramIndex, id.doubleValue());
        } else {
            stmt.setLong(paramIndex, id.longValue());
        }
        return paramIndex + 1;
    }

    public EqualityFilter(Number id) {
        if (id == null) throw new IllegalArgumentException("EqualityFilter id must not be null");
        this.id = id;
    }
}
