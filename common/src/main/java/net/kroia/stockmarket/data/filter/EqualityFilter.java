package net.kroia.stockmarket.data.filter;

public class EqualityFilter implements DataFilter{
    private final Object id;


    @Override
    public String getClause(String columnName) {
        return columnName + " = " + id;
    }

    public EqualityFilter(Object id) {
        this.id = id;
    }
}
