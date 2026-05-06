package net.kroia.stockmarket.data.filter;

public class EqualityFilter implements DataFilter{
    private final Number id;


    @Override
    public String getClause(String columnName) {
        return columnName + " = " + id.toString();
    }

    public EqualityFilter(Number id) {
        if (id == null) throw new IllegalArgumentException("EqualityFilter id must not be null");
        this.id = id;
    }
}
