package net.kroia.stockmarket.data.filter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DateFilter implements DataFilter {
    long startTime;
    long endTime;

    @Override
    public String getClause(String columnName) {
        return columnName + " >= ? AND " + columnName + " <= ?";
    }

    @Override
    public int bindParameters(PreparedStatement stmt, int paramIndex) throws SQLException {
        stmt.setLong(paramIndex, startTime);
        stmt.setLong(paramIndex + 1, endTime);
        return paramIndex + 2;
    }

    public DateFilter(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
