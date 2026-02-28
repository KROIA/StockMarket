package net.kroia.stockmarket.data.filter;

public class DateFilter implements DataFilter {
    int startTime;
    int endTime;


    @Override
    public String getClause(String columnName) {
        return columnName + " > " + startTime + " AND " + columnName + " < " + endTime;
    }

    public DateFilter(int startTime, int endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
