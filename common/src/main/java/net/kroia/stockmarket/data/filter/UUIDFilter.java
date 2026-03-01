package net.kroia.stockmarket.data.filter;

import java.util.UUID;

public class UUIDFilter implements DataFilter{
    UUID id;

    @Override
    public String getClause(String columnName) {
        return "";
    }


    public String getClause(String part1, String part2){
        return part1 + " = " + id.getMostSignificantBits() + " AND " +  part2 + " = " + id.getLeastSignificantBits();
    }

    public UUIDFilter(UUID id){
        this.id = id;
    }
}
