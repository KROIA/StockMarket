package net.kroia.stockmarket.data.filter;

public class DateFilter implements DataFilter {
    long startTime;
    long endTime;


    @Override
    public String getClause(String columnName) {
        return columnName + " >= " + startTime + " AND " + columnName + " <= " + endTime;
    }

    public DateFilter(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
