package net.kroia.stockmarket.data;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class DriverShim implements Driver {
    private final Driver driver;

    DriverShim(Driver d) { this.driver = d; }

    public Connection connect(String u, Properties p) throws SQLException { return driver.connect(u, p); }
    public boolean acceptsURL(String u) throws SQLException { return driver.acceptsURL(u); }
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException { return driver.getPropertyInfo(u, p); }
    public int getMajorVersion() { return driver.getMajorVersion(); }
    public int getMinorVersion() { return driver.getMinorVersion(); }
    public boolean jdbcCompliant() { return driver.jdbcCompliant(); }
    public Logger getParentLogger() throws SQLFeatureNotSupportedException { return driver.getParentLogger(); }
}